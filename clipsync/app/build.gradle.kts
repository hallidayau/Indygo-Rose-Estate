plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "au.halliday.clipsync"
    compileSdk = 35

    defaultConfig {
        applicationId = "au.halliday.clipsync"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0.$versionCode"
    }

    // Fixed key committed to the repo so every build can install over the last one.
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("signing/clipsync.keystore")
            storePassword = "clipsync"
            keyAlias = "clipsync"
            keyPassword = "clipsync"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
