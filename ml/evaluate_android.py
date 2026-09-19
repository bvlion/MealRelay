import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry


MAXIMUM_VALIDATION_FALSE_POSITIVE_RATE = 0.005
REMOTE_ROOT = "/data/local/tmp/mealrelay-food-classifier-evaluation"


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
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(content)


parser = argparse.ArgumentParser()
parser.add_argument("--adb", default="adb")
parser.add_argument("--app-apk", type=Path, required=True)
parser.add_argument("--test-apk", type=Path, required=True)
source = parser.add_mutually_exclusive_group(required=True)
source.add_argument("--dataset", type=Path)
source.add_argument("--manifest", type=Path)
parser.add_argument("--model", type=Path)
parser.add_argument("--threshold-quantized", type=int, default=153)
parser.add_argument("--work-directory", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
arguments = parser.parse_args()
arguments.work_directory.mkdir(parents=True, exist_ok=True)

entries = []
manifest_sha256 = None
if arguments.dataset is not None:
  for label in ("non_food", "food"):
    for image_path in sorted((arguments.dataset / label).iterdir()):
      if image_path.is_file():
        entries.append(
          {
            "label": label,
            "group": label,
            "file": image_path.name,
            "path": image_path,
          }
        )
else:
  manifest = json.loads(arguments.manifest.read_text(encoding="utf-8"))
  manifest_sha256 = hashlib.sha256(arguments.manifest.read_bytes()).hexdigest()
  image_directory = arguments.work_directory / "images"
  image_directory.mkdir(exist_ok=True)
  session = requests.Session()
  session.headers["User-Agent"] = (
    "MealRelay-PoC/0.1 (https://github.com/bvlion/MealRelay)"
  )
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
  for item in manifest:
    if item["split"] != "evaluation" or item["label"] not in {"food", "non_food"}:
      raise ValueError(
        f"Unsupported split or label in manifest: {item['split']} / {item['label']}"
      )
    image_path = image_directory / item["file"]
    download(session, item["download_url"], image_path, item["sha256"])
    entries.append(
      {
        "source": item["source"],
        "label": item["label"],
        "group": item["group"],
        "file": item["file"],
        "path": image_path,
      }
    )

with tempfile.TemporaryDirectory(
  prefix="android-evaluation-", dir=arguments.work_directory
) as temporary_directory:
  staging_directory = Path(temporary_directory)
  staging_images = staging_directory / "images"
  staging_images.mkdir()
  manifest_lines = []
  entries_by_staged_file = {}
  for index, entry in enumerate(entries):
    staged_file = f"{index:04d}_{entry['file']}"
    shutil.copy2(entry["path"], staging_images / staged_file)
    manifest_lines.append(f"{entry['label']}\t{entry['group']}\t{staged_file}")
    entries_by_staged_file[staged_file] = entry
  (staging_directory / "manifest.tsv").write_text(
    "\n".join(manifest_lines) + "\n", encoding="utf-8"
  )

  subprocess.run(
    [arguments.adb, "install", "-r", str(arguments.app_apk)], check=True
  )
  subprocess.run(
    [arguments.adb, "install", "-r", str(arguments.test_apk)], check=True
  )
  subprocess.run(
    [
      arguments.adb,
      "shell",
      "run-as",
      "net.ambitious.android.mealrelay",
      "rm",
      "-f",
      "files/food_classifier_evaluation.tsv",
    ],
    check=True,
  )
  subprocess.run([arguments.adb, "shell", "rm", "-rf", REMOTE_ROOT], check=True)
  subprocess.run([arguments.adb, "shell", "mkdir", "-p", REMOTE_ROOT], check=True)
  subprocess.run(
    [arguments.adb, "push", f"{staging_directory}/.", REMOTE_ROOT], check=True
  )

  instrumentation_arguments = [
    arguments.adb,
    "shell",
    "am",
    "instrument",
    "-w",
    "-r",
    "-e",
    "class",
    (
      "net.ambitious.android.mealrelay.FoodClassifierEvaluationTest"
      "#evaluateExternalImagesWithAndroidPreprocessing"
    ),
    "-e",
    "evaluationRoot",
    REMOTE_ROOT,
  ]
  if arguments.model is not None:
    subprocess.run(
      [arguments.adb, "push", str(arguments.model), f"{REMOTE_ROOT}/model.tflite"],
      check=True,
    )
    instrumentation_arguments.extend(
      ["-e", "evaluationModel", f"{REMOTE_ROOT}/model.tflite"]
    )
  instrumentation_arguments.append(
    "net.ambitious.android.mealrelay.test/androidx.test.runner.AndroidJUnitRunner"
  )
  subprocess.run(instrumentation_arguments, check=True)
  completed = subprocess.run(
    [
      arguments.adb,
      "exec-out",
      "run-as",
      "net.ambitious.android.mealrelay",
      "cat",
      "files/food_classifier_evaluation.tsv",
    ],
    check=True,
    capture_output=True,
    text=True,
  )
  subprocess.run([arguments.adb, "shell", "rm", "-rf", REMOTE_ROOT], check=True)

results = []
for line in completed.stdout.splitlines():
  label, group, staged_file, probability = line.split("\t")
  entry = entries_by_staged_file[staged_file]
  results.append(
    {
      "source": entry.get("source"),
      "group": group,
      "label": label,
      "file": entry["file"],
      "probability_quantized": int(probability),
    }
  )

if len(results) != len(entries):
  raise ValueError(
    f"Android evaluation returned {len(results)} rows; expected {len(entries)}"
  )

if arguments.dataset is not None:
  non_food_count = sum(result["label"] == "non_food" for result in results)
  food_count = sum(result["label"] == "food" for result in results)
  selected_threshold = 256
  selected_recall = -1.0
  for threshold in range(257):
    false_positives = sum(
      result["label"] == "non_food"
      and result["probability_quantized"] >= threshold
      for result in results
    )
    true_positives = sum(
      result["label"] == "food" and result["probability_quantized"] >= threshold
      for result in results
    )
    false_positive_rate = false_positives / non_food_count
    food_recall = true_positives / food_count
    if (
      false_positive_rate <= MAXIMUM_VALIDATION_FALSE_POSITIVE_RATE
      and food_recall > selected_recall
    ):
      selected_threshold = threshold
      selected_recall = food_recall
  threshold = selected_threshold
  true_positives = sum(
    result["label"] == "food" and result["probability_quantized"] >= threshold
    for result in results
  )
  false_positives = sum(
    result["label"] == "non_food" and result["probability_quantized"] >= threshold
    for result in results
  )
  predicted_food_count = true_positives + false_positives
  report = {
    "preprocessing": "Android FoodClassifier using a HARDWARE Bitmap",
    "threshold_quantized": threshold,
    "validation": {
      "food_count": food_count,
      "non_food_count": non_food_count,
      "true_positives": true_positives,
      "false_negatives": food_count - true_positives,
      "true_negatives": non_food_count - false_positives,
      "false_positives": false_positives,
      "food_recall": true_positives / food_count,
      "non_food_false_positive_rate": false_positives / non_food_count,
      "food_precision": (
        true_positives / predicted_food_count if predicted_food_count else 0.0
      ),
      "accuracy": (
        true_positives + non_food_count - false_positives
      ) / len(results),
    },
  }
else:
  threshold = arguments.threshold_quantized
  for result in results:
    result["predicted_food"] = result["probability_quantized"] >= threshold
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
    "preprocessing": "Android FoodClassifier using a HARDWARE Bitmap",
    "manifest_sha256": manifest_sha256,
    "threshold_quantized": threshold,
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

arguments.output.parent.mkdir(parents=True, exist_ok=True)
arguments.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(json.dumps(report, indent=2))
