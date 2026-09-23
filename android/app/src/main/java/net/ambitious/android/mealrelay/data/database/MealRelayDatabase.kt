package net.ambitious.android.mealrelay.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import net.ambitious.android.mealrelay.data.photo.PhotoProcessingDao
import net.ambitious.android.mealrelay.data.photo.PhotoResultEntity
import net.ambitious.android.mealrelay.data.photo.PhotoScanStateEntity
import net.ambitious.android.mealrelay.data.submission.MealSubmissionDao
import net.ambitious.android.mealrelay.data.submission.MealSubmissionEntity

@Database(
  entities = [PhotoResultEntity::class, PhotoScanStateEntity::class, MealSubmissionEntity::class],
  version = 2,
  exportSchema = true,
)
abstract class MealRelayDatabase : RoomDatabase() {
  abstract fun photoProcessingDao(): PhotoProcessingDao
  abstract fun mealSubmissionDao(): MealSubmissionDao
}
