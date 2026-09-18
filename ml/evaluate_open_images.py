import argparse
import csv
import json
from collections import defaultdict
from pathlib import Path

import numpy as np
import requests
import tensorflow as tf
from PIL import Image


CLASS_DESCRIPTIONS_URL = (
  "https://storage.googleapis.com/openimages/v5/class-descriptions-boxable.csv"
)
VALIDATION_LABELS_URL = (
  "https://storage.googleapis.com/openimages/v5/"
  "validation-annotations-human-imagelabels-boxable.csv"
)
VALIDATION_IMAGE_URL = "https://open-images-dataset.s3.amazonaws.com/validation/{image_id}.jpg"
IMAGE_SIZE = 224
GROUPS = {
  "person": ("non_food", 40, ["Person"]),
  "scenery": ("non_food", 50, ["Tree", "House", "Building"]),
  "terminal_screen": (
    "non_food",
    50,
    ["Laptop", "Computer monitor", "Mobile phone", "Tablet computer", "Television"],
  ),
  "document": ("non_food", 50, ["Book", "Poster"]),
  "beverage": ("food", 50, ["Drink", "Coffee", "Tea", "Juice", "Beer", "Wine"]),
  "food": ("food", 50, ["Food"]),
}
FOOD_CLASS_NAMES = {
  "Food",
  "Fast food",
  "Egg (Food)",
  "Seafood",
  "Drink",
  "Coffee",
  "Tea",
  "Juice",
  "Beer",
  "Wine",
}


def download(url: str, destination: Path) -> None:
  if destination.exists():
    return
  response = requests.get(url, timeout=60)
  response.raise_for_status()
  destination.write_bytes(response.content)


def load_samples(
  work_directory: Path, excluded_image_identifiers: set[str]
) -> list[tuple[str, str, str]]:
  class_descriptions_path = work_directory / "class-descriptions-boxable.csv"
  validation_labels_path = work_directory / "validation-labels.csv"
  download(CLASS_DESCRIPTIONS_URL, class_descriptions_path)
  download(VALIDATION_LABELS_URL, validation_labels_path)

  class_identifiers = {}
  with class_descriptions_path.open(newline="", encoding="utf-8") as file:
    for class_identifier, class_name in csv.reader(file):
      class_identifiers[class_name] = class_identifier

  labels_by_image = defaultdict(set)
  with validation_labels_path.open(newline="", encoding="utf-8") as file:
    for row in csv.DictReader(file):
      if row["Confidence"] == "1":
        labels_by_image[row["ImageID"]].add(row["LabelName"])

  excluded_food_classes = {class_identifiers[name] for name in FOOD_CLASS_NAMES}
  used_images = set()
  samples = []
  for group, (label, count, class_names) in GROUPS.items():
    group_class_identifiers = {class_identifiers[name] for name in class_names}
    for image_id, image_labels in sorted(labels_by_image.items()):
      if (
        image_id in used_images
        or image_id in excluded_image_identifiers
        or not image_labels & group_class_identifiers
      ):
        continue
      if label == "non_food" and image_labels & excluded_food_classes:
        continue
      samples.append((image_id, group, label))
      used_images.add(image_id)
      if sum(sample[1] == group for sample in samples) == count:
        break
  return samples


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
parser.add_argument("--work-directory", type=Path, required=True)
parser.add_argument("--excluded-manifest", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
arguments = parser.parse_args()
arguments.work_directory.mkdir(parents=True, exist_ok=True)
image_directory = arguments.work_directory / "images"
image_directory.mkdir(exist_ok=True)
manifest = json.loads(arguments.excluded_manifest.read_text(encoding="utf-8"))
excluded_image_identifiers = {
  item["image_id"] for item in manifest if "image_id" in item
}
samples = load_samples(arguments.work_directory, excluded_image_identifiers)
interpreter = tf.lite.Interpreter(model_path=str(arguments.model), num_threads=4)
interpreter.allocate_tensors()
results = []
for image_id, group, label in samples:
  image_path = image_directory / f"{image_id}.jpg"
  download(VALIDATION_IMAGE_URL.format(image_id=image_id), image_path)
  probability_quantized = predict(interpreter, image_path)
  results.append(
    {
      "image_id": image_id,
      "group": group,
      "label": label,
      "probability_quantized": probability_quantized,
      "predicted_food": probability_quantized >= arguments.threshold_quantized,
    }
  )

summary = {}
for group, (label, _, _) in GROUPS.items():
  group_results = [result for result in results if result["group"] == group]
  predicted_food_count = sum(result["predicted_food"] for result in group_results)
  summary[group] = {
    "label": label,
    "image_count": len(group_results),
    "predicted_food_count": predicted_food_count,
    "food_rate": predicted_food_count / len(group_results),
  }
report = {
  "dataset": "Open Images V5 validation",
  "selection": (
    "First unique positive image-label annotations by image ID, excluding all "
    "supplemental training image IDs"
  ),
  "threshold_quantized": arguments.threshold_quantized,
  "summary": summary,
  "errors": [result for result in results if result["predicted_food"] != (result["label"] == "food")],
}
arguments.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(json.dumps(report, indent=2))
