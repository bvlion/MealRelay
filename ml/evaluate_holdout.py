import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
import requests
import tensorflow as tf
from PIL import Image
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry


IMAGE_SIZE = 224


def download(
  session: requests.Session,
  url: str,
  destination: Path,
  expected_sha256: str,
) -> None:
  if destination.exists():
    content = destination.read_bytes()
  else:
    response = session.get(url, timeout=60)
    response.raise_for_status()
    content = response.content

  actual_sha256 = hashlib.sha256(content).hexdigest()
  if actual_sha256 != expected_sha256:
    raise ValueError(
      f"SHA-256 mismatch for {destination.name}: "
      f"expected {expected_sha256}, got {actual_sha256}"
    )
  if not destination.exists():
    destination.write_bytes(content)


def predict(interpreter: tf.lite.Interpreter, image_path: Path) -> int:
  input_details = interpreter.get_input_details()[0]
  output_details = interpreter.get_output_details()[0]
  with Image.open(image_path) as source_image:
    image = source_image.convert("RGB")
    image.thumbnail((IMAGE_SIZE, IMAGE_SIZE))
    model_image = Image.new("RGB", (IMAGE_SIZE, IMAGE_SIZE))
    model_image.paste(
      image,
      ((IMAGE_SIZE - image.width) // 2, (IMAGE_SIZE - image.height) // 2),
    )
  interpreter.set_tensor(
    input_details["index"], np.asarray(model_image, dtype=np.uint8)[np.newaxis, ...]
  )
  interpreter.invoke()
  return int(interpreter.get_tensor(output_details["index"])[0][0])


parser = argparse.ArgumentParser()
parser.add_argument("--model", type=Path, required=True)
parser.add_argument("--threshold-quantized", type=int, required=True)
parser.add_argument("--manifest", type=Path, required=True)
parser.add_argument("--work-directory", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
arguments = parser.parse_args()
arguments.work_directory.mkdir(parents=True, exist_ok=True)
image_directory = arguments.work_directory / "images"
image_directory.mkdir(exist_ok=True)
manifest = json.loads(arguments.manifest.read_text(encoding="utf-8"))
session = requests.Session()
session.headers["User-Agent"] = "MealRelay-PoC/0.1 (https://github.com/bvlion/MealRelay)"
session.mount(
  "https://",
  HTTPAdapter(
    max_retries=Retry(
      total=5,
      backoff_factor=2,
      status_forcelist=[429, 500, 502, 503, 504],
      respect_retry_after_header=True,
    )
  ),
)
interpreter = tf.lite.Interpreter(model_path=str(arguments.model), num_threads=4)
interpreter.allocate_tensors()
results = []
for item in manifest:
  if item["split"] != "evaluation" or item["label"] not in {"food", "non_food"}:
    raise ValueError(
      f"Unsupported split or label in manifest: {item['split']} / {item['label']}"
    )
  image_path = image_directory / item["file"]
  download(session, item["download_url"], image_path, item["sha256"])
  probability_quantized = predict(interpreter, image_path)
  results.append(
    {
      "source": item["source"],
      "group": item["group"],
      "label": item["label"],
      "file": item["file"],
      "probability_quantized": probability_quantized,
      "predicted_food": probability_quantized >= arguments.threshold_quantized,
    }
  )

summary = {}
for group in sorted({result["group"] for result in results}):
  group_results = [result for result in results if result["group"] == group]
  predicted_food_count = sum(result["predicted_food"] for result in group_results)
  summary[group] = {
    "label": group_results[0]["label"],
    "image_count": len(group_results),
    "predicted_food_count": predicted_food_count,
    "food_rate": predicted_food_count / len(group_results),
  }

non_food_results = [result for result in results if result["label"] == "non_food"]
food_results = [result for result in results if result["label"] == "food"]
false_positive_count = sum(result["predicted_food"] for result in non_food_results)
true_positive_count = sum(result["predicted_food"] for result in food_results)
predicted_food_count = false_positive_count + true_positive_count
report = {
  "manifest_sha256": hashlib.sha256(arguments.manifest.read_bytes()).hexdigest(),
  "threshold_quantized": arguments.threshold_quantized,
  "overall": {
    "non_food_count": len(non_food_results),
    "false_positives": false_positive_count,
    "non_food_false_positive_rate": false_positive_count / len(non_food_results),
    "food_count": len(food_results),
    "true_positives": true_positive_count,
    "food_recall": true_positive_count / len(food_results),
    "food_precision": true_positive_count / predicted_food_count,
    "accuracy": (
      len(non_food_results) - false_positive_count + true_positive_count
    ) / len(results),
  },
  "summary": summary,
  "errors": [
    result
    for result in results
    if result["predicted_food"] != (result["label"] == "food")
  ],
}
arguments.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(json.dumps(report, indent=2))
