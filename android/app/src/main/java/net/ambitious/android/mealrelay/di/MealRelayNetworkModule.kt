package net.ambitious.android.mealrelay.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.ambitious.android.mealrelay.MealRelayTokenStore
import net.ambitious.android.mealrelay.BuildConfig
import net.ambitious.android.mealrelay.authorization.network.MealRelayAuthorizationTransport
import net.ambitious.android.mealrelay.submission.MealSubmissionSender
import net.ambitious.android.mealrelay.submission.MealSubmissionSenderRouter
import net.ambitious.android.mealrelay.submission.network.ImageMealSubmissionFailureClassifier
import net.ambitious.android.mealrelay.submission.network.ImageMealSubmissionImageSource
import net.ambitious.android.mealrelay.submission.network.ImageMealSubmissionReadinessChecker
import net.ambitious.android.mealrelay.submission.network.ImageMealSubmissionSender
import net.ambitious.android.mealrelay.submission.network.ImageMealSubmissionTransport
import net.ambitious.android.mealrelay.submission.network.TextMealSubmissionFailureClassifier
import net.ambitious.android.mealrelay.submission.network.TextMealSubmissionReadinessChecker
import net.ambitious.android.mealrelay.submission.network.TextMealSubmissionSender
import net.ambitious.android.mealrelay.submission.network.TextMealSubmissionTransport
import net.ambitious.android.mealrelay.submission.network.model.TextMealSubmissionRequestFactory
import net.ambitious.android.mealrelay.submission.network.model.ImageMealSubmissionRequestFactory
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
  fun provideImageMealSubmissionTransport(tokenStore: MealRelayTokenStore): ImageMealSubmissionTransport =
    ImageMealSubmissionTransport(tokenStore::read, BuildConfig.MEAL_RELAY_IMAGE_ENDPOINT)

  @Provides
  @Singleton
  fun provideMealSubmissionSender(
    tokenStore: MealRelayTokenStore,
    textMealSubmissionTransport: TextMealSubmissionTransport,
    imageMealSubmissionTransport: ImageMealSubmissionTransport,
    imageMealSubmissionImageSource: ImageMealSubmissionImageSource,
  ): MealSubmissionSender = MealSubmissionSenderRouter(
    TextMealSubmissionSender(
      TextMealSubmissionReadinessChecker(tokenStore, BuildConfig.MEAL_RELAY_TEXT_ENDPOINT),
      TextMealSubmissionRequestFactory(),
      textMealSubmissionTransport,
      TextMealSubmissionFailureClassifier(),
    ),
    ImageMealSubmissionSender(
      ImageMealSubmissionReadinessChecker(tokenStore, BuildConfig.MEAL_RELAY_IMAGE_ENDPOINT),
      imageMealSubmissionImageSource,
      ImageMealSubmissionRequestFactory(),
      imageMealSubmissionTransport,
      ImageMealSubmissionFailureClassifier(),
    ),
  )
}
