package net.ambitious.android.mealrelay.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.ambitious.android.mealrelay.MealRelayTokenStore
import net.ambitious.android.mealrelay.BuildConfig
import net.ambitious.android.mealrelay.authorization.network.MealRelayAuthorizationTransport
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
  fun provideAuthorizationTransport(): MealRelayAuthorizationTransport =
    MealRelayAuthorizationTransport(BuildConfig.MEAL_RELAY_AUTH_ENDPOINT)

  @Provides
  @Singleton
  fun provideTextMealSubmissionTransport(tokenStore: MealRelayTokenStore): TextMealSubmissionTransport =
    TextMealSubmissionTransport(tokenStore::read, BuildConfig.MEAL_RELAY_TEXT_ENDPOINT)

  @Provides
  @Singleton
  fun provideMealSubmissionSender(
    tokenStore: MealRelayTokenStore,
    textMealSubmissionTransport: TextMealSubmissionTransport,
  ): MealSubmissionSender = TextMealSubmissionSender(
    TextMealSubmissionReadinessChecker(tokenStore),
    TextMealSubmissionRequestFactory(),
    textMealSubmissionTransport,
    TextMealSubmissionFailureClassifier(),
  )
}
