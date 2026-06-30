import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ahoura.asha_scanner_ip"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.ahoura.asha_scanner_ip"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val properties = Properties()
            val propertiesFile = rootProject.file("local.properties")
            if (propertiesFile.exists()) {
                properties.load(propertiesFile.inputStream())
            }

            // Priority: Env variables (for CI) > local.properties (for Local)
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: "release-key.jks"
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD") ?: properties.getProperty("keystore.password")
            val alias = System.getenv("KEY_ALIAS") ?: properties.getProperty("key.alias")
            val kPassword = System.getenv("KEY_PASSWORD") ?: properties.getProperty("key.password")

            val keyFile = file(keystorePath)
            if (keyFile.exists() && keystorePassword != null && alias != null && kPassword != null) {
                storeFile = keyFile
                storePassword = keystorePassword
                keyAlias = alias
                keyPassword = kPassword
            }
        }
    }

    buildTypes {
        release {
            val releaseSigningConfig = signingConfigs.getByName("release")
            if (releaseSigningConfig.storeFile != null) {
                signingConfig = releaseSigningConfig
            }
            // R8 full mode: shrink + obfuscate + optimize code, and strip unused
            // resources (was disabled, inflating the APK and skipping runtime opts).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.lottie.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
