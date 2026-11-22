plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "ch.kudoz.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ch.kudoz.app"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"

        // Öffentliche Werte (stehen auch im Web-Bundle von kudoz.ch).
        buildConfigField("String", "SUPABASE_URL", "\"https://kclszrnpugbsxlnmczmr.supabase.co\"")
        buildConfigField("String", "SUPABASE_KEY", "\"sb_publishable_9vZL8eI7tQDcDnBqEQTfZA_CnlUlO1r\"")
        buildConfigField("String", "WEB_URL", "\"https://kudoz.ch\"")
        // Firebase (Projekt kudoz-2adb1) – öffentliche Kennungen, ohne google-services-Plugin.
        buildConfigField("String", "FIREBASE_APP_ID", "\"1:27916052408:android:97a4290e2f4664482f914d\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"AIzaSyCm2abOLsU1l4-qMCBgwmc2r_puKKraXDY\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"kudoz-2adb1\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"27916052408\"")
    }

    signingConfigs {
        create("release") {
            val ks = System.getenv("KUDOZ_KEYSTORE_PATH")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("KUDOZ_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KUDOZ_KEY_ALIAS") ?: "kudoz"
                keyPassword = System.getenv("KUDOZ_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("KUDOZ_KEYSTORE_PATH") != null)
                signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.browser:browser:1.8.0")

    implementation(platform("io.github.jan-tennert.supabase:bom:3.0.3"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-android:3.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
}
