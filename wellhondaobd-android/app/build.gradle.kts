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
        versionCode = 3
        versionName = "1.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
