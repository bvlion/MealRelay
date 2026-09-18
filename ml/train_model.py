import argparse
import json
from pathlib import Path

import tensorflow as tf


IMAGE_SIZE = 224
BATCH_SIZE = 32
RANDOM_SEED = 20260918


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
report = {
  "model": "MobileNetV3Small",
  "image_size": IMAGE_SIZE,
  "quantization": "full integer uint8 input and output",
}
(arguments.output / "training.json").write_text(
  json.dumps(report, indent=2) + "\n", encoding="utf-8"
)
print(json.dumps(report, indent=2))
