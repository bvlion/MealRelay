package net.ambitious.android.mealrelay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.IOException;
import java.io.InputStream;
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
}
