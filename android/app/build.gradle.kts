plugins {
  id("com.android.application")
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
}

dependencies {
  implementation("com.google.ai.edge.litert:litert:1.4.2")

  androidTestImplementation("androidx.test.ext:junit:1.3.0")
  androidTestImplementation("androidx.test:runner:1.7.0")
}
