package net.ambitious.android.mealrelay.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.ambitious.android.mealrelay.submission.SubmissionClock
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MealSubmissionModule {
  @Provides
  @Singleton
  fun provideLocalClock(): Clock = Clock.systemUTC()

  @Provides
  fun provideSubmissionClock(): SubmissionClock = SubmissionClock(System::currentTimeMillis)
}
