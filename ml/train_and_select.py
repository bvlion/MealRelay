import argparse
import json
from pathlib import Path

import numpy as np
import tensorflow as tf


IMAGE_SIZE = 224
BATCH_SIZE = 32
RANDOM_SEED = 20260918
MAXIMUM_VALIDATION_FALSE_POSITIVE_RATE = 0.005


def load_dataset(directory: Path, is_training: bool) -> tf.data.Dataset:
  dataset = tf.keras.utils.image_dataset_from_directory(
    directory,
    labels="inferred",
    label_mode="binary",
    class_names=["non_food", "food"],
    batch_size=BATCH_SIZE,
    image_size=(IMAGE_SIZE, IMAGE_SIZE),
    pad_to_aspect_ratio=True,
    shuffle=is_training,
    seed=RANDOM_SEED,
  )
  return dataset.prefetch(tf.data.AUTOTUNE)


def create_model() -> tf.keras.Model:
  augmentation = tf.keras.Sequential(
    [
      tf.keras.layers.RandomFlip("horizontal", seed=RANDOM_SEED),
      tf.keras.layers.RandomRotation(0.05, fill_mode="constant", seed=RANDOM_SEED),
      tf.keras.layers.RandomZoom(0.1, fill_mode="constant", seed=RANDOM_SEED),
    ],
    name="augmentation",
  )
  backbone = tf.keras.applications.MobileNetV3Small(
    input_shape=(IMAGE_SIZE, IMAGE_SIZE, 3),
    include_top=False,
    include_preprocessing=True,
    weights="imagenet",
  )
  backbone.trainable = False

  inputs = tf.keras.Input(shape=(IMAGE_SIZE, IMAGE_SIZE, 3), name="image")
  values = augmentation(inputs)
  values = backbone(values, training=False)
  values = tf.keras.layers.GlobalAveragePooling2D()(values)
  values = tf.keras.layers.Dropout(0.2, seed=RANDOM_SEED)(values)
  outputs = tf.keras.layers.Dense(1, activation="sigmoid", name="food_probability")(values)
  return tf.keras.Model(inputs, outputs)


def quantize_model(model: tf.keras.Model, calibration: tf.data.Dataset) -> bytes:
  converter = tf.lite.TFLiteConverter.from_keras_model(model)
  converter.optimizations = [tf.lite.Optimize.DEFAULT]

  def representative_dataset():
    for images, _ in calibration.unbatch().batch(1).take(300):
      yield [tf.cast(images, tf.float32)]

  converter.representative_dataset = representative_dataset
  converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
  converter.inference_input_type = tf.uint8
  converter.inference_output_type = tf.uint8
  return converter.convert()


def predict_tflite(model_path: Path, dataset: tf.data.Dataset) -> tuple[np.ndarray, np.ndarray]:
  interpreter = tf.lite.Interpreter(model_path=str(model_path), num_threads=4)
  interpreter.allocate_tensors()
  input_details = interpreter.get_input_details()[0]
  output_details = interpreter.get_output_details()[0]
  input_scale, input_zero_point = input_details["quantization"]
  output_scale, output_zero_point = output_details["quantization"]
  labels = []
  probabilities = []

  for images, batch_labels in dataset.unbatch().batch(1):
    quantized_input = np.clip(
      np.rint(images.numpy() / input_scale + input_zero_point), 0, 255
    ).astype(np.uint8)
    interpreter.set_tensor(input_details["index"], quantized_input)
    interpreter.invoke()
    quantized_output = interpreter.get_tensor(output_details["index"])
    probabilities.append(float((quantized_output[0][0] - output_zero_point) * output_scale))
    labels.append(int(batch_labels.numpy()[0][0]))

  return np.asarray(labels), np.asarray(probabilities)


def choose_threshold(labels: np.ndarray, probabilities: np.ndarray) -> float:
  non_food_count = int(np.sum(labels == 0))
  candidates = np.unique(np.concatenate(([0.0], probabilities, [1.0])))
  selected_threshold = 1.0
  selected_recall = -1.0
  for threshold in candidates:
    predictions = probabilities >= threshold
    false_positive_rate = np.sum(predictions & (labels == 0)) / non_food_count
    food_recall = np.sum(predictions & (labels == 1)) / np.sum(labels == 1)
    if false_positive_rate <= MAXIMUM_VALIDATION_FALSE_POSITIVE_RATE and food_recall > selected_recall:
      selected_threshold = float(threshold)
      selected_recall = float(food_recall)
  return selected_threshold


def calculate_metrics(
  labels: np.ndarray, probabilities: np.ndarray, threshold: float
) -> dict[str, float | int]:
  predictions = probabilities >= threshold
  true_positives = int(np.sum(predictions & (labels == 1)))
  false_negatives = int(np.sum(~predictions & (labels == 1)))
  true_negatives = int(np.sum(~predictions & (labels == 0)))
  false_positives = int(np.sum(predictions & (labels == 0)))
  return {
    "threshold": threshold,
    "true_positives": true_positives,
    "false_negatives": false_negatives,
    "true_negatives": true_negatives,
    "false_positives": false_positives,
    "food_recall": true_positives / (true_positives + false_negatives),
    "non_food_false_positive_rate": false_positives / (true_negatives + false_positives),
    "food_precision": true_positives / (true_positives + false_positives),
    "accuracy": (true_positives + true_negatives) / len(labels),
  }


parser = argparse.ArgumentParser()
parser.add_argument("--dataset", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
arguments = parser.parse_args()

tf.keras.utils.set_random_seed(RANDOM_SEED)
training_dataset = load_dataset(arguments.dataset / "training", is_training=True)
validation_dataset = load_dataset(arguments.dataset / "validation", is_training=False)
model = create_model()
model.compile(
  optimizer=tf.keras.optimizers.Adam(learning_rate=1e-3),
  loss=tf.keras.losses.BinaryCrossentropy(),
  metrics=[tf.keras.metrics.BinaryAccuracy(), tf.keras.metrics.AUC()],
)
model.fit(
  training_dataset,
  validation_data=validation_dataset,
  epochs=8,
  verbose=2,
  callbacks=[
    tf.keras.callbacks.EarlyStopping(
      monitor="val_loss", patience=2, restore_best_weights=True
    )
  ],
)

arguments.output.mkdir(parents=True, exist_ok=True)
model_path = arguments.output / "food_classifier_int8.tflite"
model_path.write_bytes(quantize_model(model, validation_dataset))
validation_labels, validation_probabilities = predict_tflite(model_path, validation_dataset)
threshold = choose_threshold(validation_labels, validation_probabilities)
interpreter = tf.lite.Interpreter(model_path=str(model_path))
interpreter.allocate_tensors()
output_scale, output_zero_point = interpreter.get_output_details()[0]["quantization"]
report = {
  "model": "MobileNetV3Small",
  "image_size": IMAGE_SIZE,
  "quantization": "full integer uint8 input and output",
  "threshold_quantized": int(round(threshold / output_scale + output_zero_point)),
  "validation": calculate_metrics(validation_labels, validation_probabilities, threshold),
}
(arguments.output / "selection.json").write_text(
  json.dumps(report, indent=2) + "\n", encoding="utf-8"
)
print(json.dumps(report, indent=2))
