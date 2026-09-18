package net.ambitious.android.mealrelay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.tensorflow.lite.Interpreter;

public final class FoodClassifier implements AutoCloseable {
  private static final String MODEL_ASSET_NAME = "food_classifier_int8.tflite";
  private static final int IMAGE_SIZE = 224;
  private static final int FOOD_PROBABILITY_THRESHOLD_QUANTIZED = 164;

  private final Interpreter interpreter;
  private final ByteBuffer inputBuffer = ByteBuffer.allocateDirect(IMAGE_SIZE * IMAGE_SIZE * 3)
      .order(ByteOrder.nativeOrder());
  private final byte[][] output = new byte[1][1];

  public FoodClassifier(Context context) throws IOException {
    try (InputStream inputStream = context.getAssets().open(MODEL_ASSET_NAME)) {
      byte[] modelBytes = inputStream.readAllBytes();
      ByteBuffer modelBuffer = ByteBuffer.allocateDirect(modelBytes.length)
          .order(ByteOrder.nativeOrder());
      modelBuffer.put(modelBytes);
      modelBuffer.rewind();
      interpreter = new Interpreter(modelBuffer, new Interpreter.Options().setNumThreads(2));
    }
  }

  public synchronized boolean isFood(Bitmap bitmap) {
    if (bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
      throw new IllegalArgumentException("Bitmap must contain a readable image");
    }

    float scale = Math.min(
        (float) IMAGE_SIZE / bitmap.getWidth(),
        (float) IMAGE_SIZE / bitmap.getHeight());
    int scaledWidth = Math.max(1, Math.round(bitmap.getWidth() * scale));
    int scaledHeight = Math.max(1, Math.round(bitmap.getHeight() * scale));
    Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true);
    Bitmap modelBitmap = Bitmap.createBitmap(IMAGE_SIZE, IMAGE_SIZE, Bitmap.Config.ARGB_8888);
    modelBitmap.eraseColor(Color.BLACK);
    Canvas canvas = new Canvas(modelBitmap);
    canvas.drawBitmap(
        scaledBitmap,
        (IMAGE_SIZE - scaledWidth) / 2.0f,
        (IMAGE_SIZE - scaledHeight) / 2.0f,
        null);

    int[] pixels = new int[IMAGE_SIZE * IMAGE_SIZE];
    modelBitmap.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE);
    inputBuffer.rewind();
    for (int pixel : pixels) {
      inputBuffer.put((byte) Color.red(pixel));
      inputBuffer.put((byte) Color.green(pixel));
      inputBuffer.put((byte) Color.blue(pixel));
    }
    inputBuffer.rewind();
    interpreter.run(inputBuffer, output);

    if (scaledBitmap != bitmap) {
      scaledBitmap.recycle();
    }
    modelBitmap.recycle();
    return Byte.toUnsignedInt(output[0][0]) >= FOOD_PROBABILITY_THRESHOLD_QUANTIZED;
  }

  @Override
  public void close() {
    interpreter.close();
  }
}
