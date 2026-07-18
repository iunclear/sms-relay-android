plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose")
}

val stableSigningValues = listOf(
    System.getenv("SMSRELAY_KEYSTORE_FILE"),
    System.getenv("SMSRELAY_KEYSTORE_PASSWORD"),
    System.getenv("SMSRELAY_KEY_ALIAS"),
    System.getenv("SMSRELAY_KEY_PASSWORD")
)
val hasStableSigning = stableSigningValues.all { !it.isNullOrBlank() }

if (System.getenv("SMSRELAY_REQUIRE_RELEASE_SIGNING") == "true" && !hasStableSigning) {
    error("Stable release signing credentials are required for this build.")
}

android {
    namespace = "com.iunclear.smsrelay"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.iunclear.smsrelay"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.1.1"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    signingConfigs {
        create("stable") {
            if (hasStableSigning) {
                storeFile = rootProject.file(System.getenv("SMSRELAY_KEYSTORE_FILE"))
                storePassword = System.getenv("SMSRELAY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SMSRELAY_KEY_ALIAS")
                keyPassword = System.getenv("SMSRELAY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (hasStableSigning) signingConfig = signingConfigs.getByName("stable")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasStableSigning) {
                signingConfigs.getByName("stable")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
}
