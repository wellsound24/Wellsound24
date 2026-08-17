plugins {
    id("com.android.application")
}

android {
    namespace = "com.wellsound24.wellhondaobd"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wellsound24.wellhondaobd"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
