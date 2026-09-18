package net.ambitious.android.mealrelay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class FoodClassifierTest {
  @Test
  public void foodFixtureIsAccepted() throws IOException {
    Context context = InstrumentationRegistry.getInstrumentation().getContext();
    try (
        InputStream inputStream = context.getAssets().open("food.jpg");
        FoodClassifier classifier = new FoodClassifier(
            InstrumentationRegistry.getInstrumentation().getTargetContext())
    ) {
      Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
      assertTrue(classifier.isFood(bitmap));
      bitmap.recycle();
    }
  }

  @Test
  public void nonFoodFixtureIsRejected() throws IOException {
    Context context = InstrumentationRegistry.getInstrumentation().getContext();
    try (
        InputStream inputStream = context.getAssets().open("non_food.jpg");
        FoodClassifier classifier = new FoodClassifier(
            InstrumentationRegistry.getInstrumentation().getTargetContext())
    ) {
      Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
      assertFalse(classifier.isFood(bitmap));
      bitmap.recycle();
    }
  }

  @Test
  public void hardwareBitmapIsAccepted() throws IOException {
    Context context = InstrumentationRegistry.getInstrumentation().getContext();
    try (
        InputStream inputStream = context.getAssets().open("food.jpg");
        FoodClassifier classifier = new FoodClassifier(
            InstrumentationRegistry.getInstrumentation().getTargetContext())
    ) {
      ImageDecoder.Source source = ImageDecoder.createSource(
          ByteBuffer.wrap(inputStream.readAllBytes()));
      Bitmap bitmap = ImageDecoder.decodeBitmap(
          source,
          (decoder, information, imageSource) -> {
            decoder.setAllocator(ImageDecoder.ALLOCATOR_HARDWARE);
            decoder.setTargetSize(224, 224);
          });
      assertTrue(bitmap.getConfig() == Bitmap.Config.HARDWARE);
      assertTrue(bitmap.getWidth() == 224 && bitmap.getHeight() == 224);
      assertTrue(classifier.isFood(bitmap));
      bitmap.recycle();
    }
  }
}
