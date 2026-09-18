import argparse
import hashlib
import json
from pathlib import Path

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry


OPEN_IMAGES_SOURCE = "Open Images V5 validation"
WIKIMEDIA_COMMONS_SOURCE = "Wikimedia Commons"


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
parser.add_argument("--dataset", type=Path, required=True)
parser.add_argument("--manifest", type=Path, required=True)
parser.add_argument("--work-directory", type=Path, required=True)
arguments = parser.parse_args()
arguments.work_directory.mkdir(parents=True, exist_ok=True)
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

for item in manifest:
  if item["source"] == OPEN_IMAGES_SOURCE and item["split"] == "training":
    if item["group"] == "screen":
      label = "non_food"
    elif item["group"] in {"beverage", "food"}:
      label = "food"
    else:
      raise ValueError(f"Unsupported Open Images group: {item['group']}")
    destination = arguments.dataset / "training" / label / item["file"]
  elif item["source"] == WIKIMEDIA_COMMONS_SOURCE:
    if item["split"] == "training":
      destination = arguments.dataset / "training" / "food" / item["file"]
    elif item["split"] == "evaluation":
      destination = arguments.work_directory / "packaged_evaluation" / item["file"]
    else:
      raise ValueError(f"Unsupported split in manifest: {item['split']}")
  else:
    raise ValueError(
      f"Unsupported source or split in manifest: {item['source']} / {item['split']}"
    )

  download(session, item["download_url"], destination, item["sha256"])

(arguments.work_directory / "supplemental_manifest.json").write_text(
  json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
)
print(json.dumps({"verified_images": len(manifest)}, indent=2))
