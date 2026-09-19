plugins {
  alias(libs.plugins.android.application)
}

android {
  namespace = "net.ambitious.android.mealrelay"
  compileSdk = 37

  defaultConfig {
    applicationId = "net.ambitious.android.mealrelay"
    minSdk = 37
    targetSdk = 37
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
  implementation(libs.litert)

  testImplementation(libs.junit)
  testImplementation(libs.mockito)
  testImplementation(libs.robolectric)

  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
  jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}
