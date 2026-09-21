plugins { id("com.android.application") }

android {
    namespace = "com.nees.maxq"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.nees.maxq"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += listOf("arm64-v8a") }
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
    // DexKit's native library is loaded from the module's own lib dir, so it must be extracted.
    packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    implementation("org.luckypray:dexkit:2.3.0")
}
