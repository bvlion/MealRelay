package net.ambitious.android.mealrelay.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.ambitious.android.mealrelay.data.database.MealRelayDatabase
import net.ambitious.android.mealrelay.data.photo.PhotoProcessingDao
import net.ambitious.android.mealrelay.data.submission.MealSubmissionDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MealRelayDatabaseModule {
  @Provides
  @Singleton
  fun provideDatabase(@ApplicationContext context: Context): MealRelayDatabase = Room.databaseBuilder(
    context,
    MealRelayDatabase::class.java,
    "meal_relay.db",
  ).build()

  @Provides
  fun providePhotoProcessingDao(database: MealRelayDatabase): PhotoProcessingDao = database.photoProcessingDao()

  @Provides
  fun provideMealSubmissionDao(database: MealRelayDatabase): MealSubmissionDao = database.mealSubmissionDao()
}
