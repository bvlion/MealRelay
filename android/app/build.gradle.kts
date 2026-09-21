plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
}

room {
  schemaDirectory("$projectDir/schemas")
}

android {
  namespace = "net.ambitious.android.mealrelay"
  compileSdk = 37

  buildFeatures {
    buildConfig = true
  }

  defaultConfig {
    applicationId = "net.ambitious.android.mealrelay"
    minSdk = 37
    targetSdk = 37
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    buildConfigField(
      "String",
      "MEAL_RELAY_OAUTH_CLIENT_ID",
      "\"${providers.gradleProperty("mealRelayOauthClientId").orElse("").get()}\"",
    )
    buildConfigField(
      "String",
      "MEAL_RELAY_AUTH_ENDPOINT",
      "\"${providers.gradleProperty("mealRelayAuthEndpoint").orElse("").get()}\"",
    )
    buildConfigField(
      "String",
      "MEAL_RELAY_IMAGE_ENDPOINT",
      "\"${providers.gradleProperty("mealRelayImageEndpoint").orElse("").get()}\"",
    )
    buildConfigField(
      "String",
      "MEAL_RELAY_TEXT_ENDPOINT",
      "\"${providers.gradleProperty("mealRelayTextEndpoint").orElse("").get()}\"",
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
  implementation(libs.lifecycle.runtime.ktx)
  implementation(libs.coroutines.play.services)
  implementation(libs.retrofit)
  implementation(libs.retrofit.converter.gson)
  implementation(libs.okhttp)
  implementation(libs.tink.android)
  ksp(libs.room.compiler)

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
