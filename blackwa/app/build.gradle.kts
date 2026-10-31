plugins { id("com.android.application") }

android {
    namespace = "com.nees.blackwa"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.nees.blackwa"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    signingConfigs {
        create("own") {
            storeFile = file("/Users/nees/roott/overlay-src/build/key.jks")
            storePassword = "overlay"
            keyAlias = "o"
            keyPassword = "overlay"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("own")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
}
