package net.ambitious.android.mealrelay.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.ambitious.android.mealrelay.photo.AndroidPhotoEnrollmentScheduler
import net.ambitious.android.mealrelay.photo.PhotoEnrollmentScheduler
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MealRelayPhotoModule {
  @Binds
  @Singleton
  abstract fun bindPhotoEnrollmentScheduler(
    scheduler: AndroidPhotoEnrollmentScheduler,
  ): PhotoEnrollmentScheduler
}
