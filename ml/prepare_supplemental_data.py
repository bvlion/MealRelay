import argparse
import csv
import hashlib
import json
import time
from collections import defaultdict
from pathlib import Path

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry


OPEN_IMAGES_CLASS_DESCRIPTIONS_URL = (
  "https://storage.googleapis.com/openimages/v5/class-descriptions-boxable.csv"
)
OPEN_IMAGES_VALIDATION_LABELS_URL = (
  "https://storage.googleapis.com/openimages/v5/"
  "validation-annotations-human-imagelabels-boxable.csv"
)
OPEN_IMAGES_IMAGE_URL = (
  "https://open-images-dataset.s3.amazonaws.com/validation/{image_id}.jpg"
)
OPEN_IMAGES_GROUPS = {
  "screen": (
    "non_food",
    ["Laptop", "Computer monitor", "Mobile phone", "Tablet computer", "Television"],
  ),
  "beverage": ("food", ["Drink", "Coffee", "Tea", "Juice", "Beer", "Wine"]),
  "food": ("food", ["Food"]),
}
OPEN_IMAGES_EXCLUDED_FOOD_CLASS_NAMES = {
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
COMMONS_GROUPS = {
  "packaged_food": ("Category:Packaged food", 30, 20),
  "canned_food": ("Category:Canned food", 30, 20),
  "packaged_sandwiches": ("Category:Packaged sandwiches", 10, 7),
  "packaged_sushi": ("Category:Packaged sushi", 30, 20),
}


def download(session: requests.Session, url: str, destination: Path) -> None:
  if destination.exists():
    return
  response = session.get(url, timeout=60)
  response.raise_for_status()
  destination.write_bytes(response.content)


parser = argparse.ArgumentParser()
parser.add_argument("--dataset", type=Path, required=True)
parser.add_argument("--work-directory", type=Path, required=True)
arguments = parser.parse_args()
arguments.work_directory.mkdir(parents=True, exist_ok=True)
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

class_descriptions_path = arguments.work_directory / "class-descriptions-boxable.csv"
validation_labels_path = arguments.work_directory / "validation-labels.csv"
download(session, OPEN_IMAGES_CLASS_DESCRIPTIONS_URL, class_descriptions_path)
download(session, OPEN_IMAGES_VALIDATION_LABELS_URL, validation_labels_path)
class_identifiers = {}
with class_descriptions_path.open(newline="", encoding="utf-8") as file:
  for class_identifier, class_name in csv.reader(file):
    class_identifiers[class_name] = class_identifier
labels_by_image = defaultdict(set)
with validation_labels_path.open(newline="", encoding="utf-8") as file:
  for row in csv.DictReader(file):
    if row["Confidence"] == "1":
      labels_by_image[row["ImageID"]].add(row["LabelName"])
excluded_food_classes = {
  class_identifiers[name] for name in OPEN_IMAGES_EXCLUDED_FOOD_CLASS_NAMES
}
manifest = []
for group, (label, class_names) in OPEN_IMAGES_GROUPS.items():
  group_class_identifiers = {class_identifiers[name] for name in class_names}
  candidates = []
  for image_id, image_labels in sorted(labels_by_image.items()):
    if not image_labels & group_class_identifiers:
      continue
    if label == "non_food" and image_labels & excluded_food_classes:
      continue
    candidates.append(image_id)
  for image_id in candidates[50:150]:
    destination = (
      arguments.dataset / "training" / label / f"open_images_{group}_{image_id}.jpg"
    )
    download(session, OPEN_IMAGES_IMAGE_URL.format(image_id=image_id), destination)
    manifest.append(
      {
        "source": "Open Images V5 validation",
        "group": group,
        "split": "training",
        "image_id": image_id,
        "url": OPEN_IMAGES_IMAGE_URL.format(image_id=image_id),
        "file": destination.name,
      }
    )

commons_evaluation_directory = arguments.work_directory / "packaged_evaluation"
commons_evaluation_directory.mkdir(exist_ok=True)
for group, (category, training_count, evaluation_count) in COMMONS_GROUPS.items():
  response = session.get(
    "https://commons.wikimedia.org/w/api.php",
    params={
      "action": "query",
      "generator": "categorymembers",
      "gcmtitle": category,
      "gcmtype": "file",
      "gcmlimit": "50",
      "prop": "imageinfo",
      "iiprop": "url|extmetadata",
      "iiurlwidth": "400",
      "format": "json",
    },
    timeout=60,
  )
  response.raise_for_status()
  pages = sorted(
    (
      page
      for page in response.json()["query"]["pages"].values()
      if page.get("imageinfo") and page["imageinfo"][0].get("thumburl")
    ),
    key=lambda page: page["title"],
  )
  for index, page in enumerate(pages[: training_count + evaluation_count]):
    split = "training" if index < training_count else "evaluation"
    image_information = page["imageinfo"][0]
    title_hash = hashlib.sha256(page["title"].encode()).hexdigest()[:16]
    file_name = f"commons_{group}_{title_hash}.jpg"
    destination = (
      arguments.dataset / "training" / "food" / file_name
      if split == "training"
      else commons_evaluation_directory / file_name
    )
    download(session, image_information["thumburl"], destination)
    metadata = image_information.get("extmetadata", {})
    manifest.append(
      {
        "source": "Wikimedia Commons",
        "group": group,
        "split": split,
        "title": page["title"],
        "url": image_information["descriptionurl"],
        "license": metadata.get("LicenseShortName", {}).get("value"),
        "license_url": metadata.get("LicenseUrl", {}).get("value"),
        "artist": metadata.get("Artist", {}).get("value"),
        "file": destination.name,
      }
    )
    time.sleep(0.25)

(arguments.work_directory / "supplemental_manifest.json").write_text(
  json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
)
print(json.dumps({"downloaded_images": len(manifest)}, indent=2))
