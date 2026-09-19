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
  implementation(libs.work.runtime)
  implementation(libs.room.runtime)
  ksp(libs.room.compiler)

  testImplementation(libs.junit)
  testImplementation(libs.robolectric)

  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
  jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}
