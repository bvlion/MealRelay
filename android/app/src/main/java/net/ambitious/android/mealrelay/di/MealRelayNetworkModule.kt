package net.ambitious.android.mealrelay.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.ambitious.android.mealrelay.MealRelayBackendClient
import net.ambitious.android.mealrelay.MealRelayTokenStore
import net.ambitious.android.mealrelay.BuildConfig
import net.ambitious.android.mealrelay.submission.MealSubmissionSender
import net.ambitious.android.mealrelay.submission.network.TextMealSubmissionFailureClassifier
import net.ambitious.android.mealrelay.submission.network.TextMealSubmissionReadinessChecker
import net.ambitious.android.mealrelay.submission.network.TextMealSubmissionSender
import net.ambitious.android.mealrelay.submission.network.TextMealSubmissionTransport
import net.ambitious.android.mealrelay.submission.network.model.TextMealSubmissionRequestFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MealRelayNetworkModule {
  @Provides
  @Singleton
  fun provideBackendClient(tokenStore: MealRelayTokenStore): MealRelayBackendClient =
    MealRelayBackendClient(tokenStore::read)

  @Provides
  @Singleton
  fun provideMealSubmissionSender(
    tokenStore: MealRelayTokenStore,
    backendClient: MealRelayBackendClient,
  ): MealSubmissionSender = TextMealSubmissionSender(
    TextMealSubmissionReadinessChecker(tokenStore, BuildConfig.MEAL_RELAY_TEXT_ENDPOINT),
    TextMealSubmissionRequestFactory(),
    TextMealSubmissionTransport(backendClient, BuildConfig.MEAL_RELAY_TEXT_ENDPOINT),
    TextMealSubmissionFailureClassifier(),
  )
}
