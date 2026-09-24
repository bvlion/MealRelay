package net.ambitious.android.mealrelay.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.ambitious.android.mealrelay.ui.settings.AndroidUnusedAppRestrictionsStatusProvider
import net.ambitious.android.mealrelay.ui.settings.UnusedAppRestrictionsStatusProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MealRelaySettingsModule {
  @Binds
  @Singleton
  abstract fun bindUnusedAppRestrictionsStatusProvider(
    provider: AndroidUnusedAppRestrictionsStatusProvider,
  ): UnusedAppRestrictionsStatusProvider
}
