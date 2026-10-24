plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.rez.notif"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rez.notif"
        // Glass blur needs Android 12+; the target phone (S25 Ultra) runs Android 15+.
        minSdk = 31
        targetSdk = 35
        // CI passes its run number so each build has a higher version.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.7." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        // Only the S25 Ultra's CPU type: keeps the encrypted-database library small.
        ndk { abiFilters += "arm64-v8a" }
    }

    // One fixed debug key in the repo, so every new build installs over the old one
    // (no uninstall, no lost data). Personal app only; this key is not secret.
    signingConfigs {
        getByName("debug") {
            storeFile = file("notif-debug.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Personal app: release is signed with the debug key so it installs directly.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite)
    implementation(libs.tesseract)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
