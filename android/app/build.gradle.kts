import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.hilt)
  alias(libs.plugins.google.services)
}

val mealRelayLocalProperties = Properties().apply {
  val localConfigFile = rootProject.file("mealrelay.local.properties")
  if (localConfigFile.isFile) {
    localConfigFile.inputStream().use { input -> load(input) }
  }
}

fun requiredMealRelayProperty(name: String): String =
  providers.gradleProperty(name).orNull?.takeIf(String::isNotBlank)
    ?: mealRelayLocalProperties.getProperty(name)?.takeIf(String::isNotBlank)
    ?: error("$name must be set with a Gradle property or in mealrelay.local.properties")

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
    val mealRelayOauthClientId = requiredMealRelayProperty("mealRelayOauthClientId")
    val mealRelayAuthEndpoint = requiredMealRelayProperty("mealRelayAuthEndpoint")
    val mealRelayTextEndpoint = requiredMealRelayProperty("mealRelayTextEndpoint")
    val mealRelayImageEndpoint = requiredMealRelayProperty("mealRelayImageEndpoint")
    val mealRelayFirebaseInstallationEndpoint =
      requiredMealRelayProperty("mealRelayFirebaseInstallationEndpoint")
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
    buildConfigField(
      "String",
      "MEAL_RELAY_IMAGE_ENDPOINT",
      "\"$mealRelayImageEndpoint\"",
    )
    buildConfigField(
      "String",
      "MEAL_RELAY_FIREBASE_INSTALLATION_ENDPOINT",
      "\"$mealRelayFirebaseInstallationEndpoint\"",
    )
  }

  testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.messaging)
  implementation(libs.litert)
  implementation(libs.work.runtime)
  implementation(libs.work.runtime.ktx)
  implementation(libs.room.runtime)
  implementation(libs.play.services.auth)
  implementation(libs.activity.ktx)
  implementation(libs.activity.compose)
  implementation(libs.androidx.core)
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
