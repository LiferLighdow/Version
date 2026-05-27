plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.liferlighdow.version"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.liferlighdow.version"
        minSdk = 21
        targetSdk = 37
        versionCode = 9
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // 移除所有外部庫，回歸純淨 Android SDK
}
