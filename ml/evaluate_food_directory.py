import argparse
import json
from pathlib import Path

import numpy as np
import tensorflow as tf
from PIL import Image


IMAGE_SIZE = 224


parser = argparse.ArgumentParser()
parser.add_argument("--model", type=Path, required=True)
parser.add_argument("--threshold-quantized", type=int, required=True)
parser.add_argument("--images", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
arguments = parser.parse_args()
interpreter = tf.lite.Interpreter(model_path=str(arguments.model), num_threads=4)
interpreter.allocate_tensors()
input_details = interpreter.get_input_details()[0]
output_details = interpreter.get_output_details()[0]
results = []
for image_path in sorted(arguments.images.glob("*.jpg")):
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
  probability_quantized = int(interpreter.get_tensor(output_details["index"])[0][0])
  results.append(
    {
      "file": image_path.name,
      "probability_quantized": probability_quantized,
      "predicted_food": probability_quantized >= arguments.threshold_quantized,
    }
  )
accepted_count = sum(result["predicted_food"] for result in results)
report = {
  "image_count": len(results),
  "predicted_food_count": accepted_count,
  "food_recall": accepted_count / len(results),
  "threshold_quantized": arguments.threshold_quantized,
  "errors": [result for result in results if not result["predicted_food"]],
}
arguments.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(json.dumps(report, indent=2))
