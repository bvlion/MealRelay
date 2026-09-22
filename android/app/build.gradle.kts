plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.hilt)
}

room {
  schemaDirectory("$projectDir/schemas")
}

android {
  namespace = "net.ambitious.android.mealrelay"
  compileSdk = 37

  buildFeatures {
    buildConfig = true
    compose = true
  }

  defaultConfig {
    applicationId = "net.ambitious.android.mealrelay"
    minSdk = 37
    targetSdk = 37
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    val mealRelayOauthClientId = requireNotNull(
      providers.gradleProperty("mealRelayOauthClientId").orNull?.takeIf(String::isNotBlank),
    ) { "mealRelayOauthClientId must be set" }
    val mealRelayAuthEndpoint = requireNotNull(
      providers.gradleProperty("mealRelayAuthEndpoint").orNull?.takeIf(String::isNotBlank),
    ) { "mealRelayAuthEndpoint must be set" }
    val mealRelayTextEndpoint = providers.gradleProperty("mealRelayTextEndpoint").orElse("").get()
    buildConfigField(
      "String",
      "MEAL_RELAY_OAUTH_CLIENT_ID",
      "\"$mealRelayOauthClientId\"",
    )
    buildConfigField(
      "String",
      "MEAL_RELAY_AUTH_ENDPOINT",
      "\"$mealRelayAuthEndpoint\"",
    )
    buildConfigField(
      "String",
      "MEAL_RELAY_TEXT_ENDPOINT",
      "\"$mealRelayTextEndpoint\"",
    )
  }

  testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
  implementation(libs.litert)
  implementation(libs.work.runtime)
  implementation(libs.work.runtime.ktx)
  implementation(libs.room.runtime)
  implementation(libs.play.services.auth)
  implementation(libs.activity.ktx)
  implementation(libs.activity.compose)
  implementation(libs.lifecycle.runtime.ktx)
  implementation(libs.lifecycle.viewmodel.compose)
  implementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.coroutines.play.services)
  implementation(libs.retrofit)
  implementation(libs.retrofit.converter.gson)
  implementation(libs.okhttp)
  implementation(libs.tink.android)
  implementation(libs.hilt.android)
  implementation(libs.androidx.hilt.work)
  ksp(libs.room.compiler)
  ksp(libs.hilt.compiler)
  ksp(libs.androidx.hilt.compiler)

  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.okhttp.tls)

  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
  jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}
