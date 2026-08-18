plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Samsung Health Data SDK guide requires the kotlin-parcelize plugin
    // (SDK model classes such as Permission / DataSource implement Parcelable).
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "com.example.shealthpoc"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.shealthpoc"
        // Samsung Health Data SDK requires Android 10 (API 29) or later.
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // Samsung Health Data SDK requires Java 17 or later.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // === Samsung Health Data SDK =========================================
    // Drop `samsung-health-data-api-<version>.aar` (downloaded from
    // https://developer.samsung.com/health/data/overview.html) into app/libs/.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    // =====================================================================

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.gson)
    // The AAR has no POM, so its Kotlin coroutines requirement is declared here.
    implementation(libs.kotlinx.coroutines.android)
}

// Fail early with an actionable message instead of a wall of "unresolved reference".
val samsungAarPresent: Boolean =
    file("libs").listFiles()?.any { it.isFile && it.extension == "aar" } == true

tasks.named("preBuild") {
    doFirst {
        if (!samsungAarPresent) {
            throw GradleException(
                """
                |
                |  Samsung Health Data SDK AAR not found.
                |
                |  1. Download the SDK from https://developer.samsung.com/health/data/overview.html
                |  2. Unzip it and copy the AAR (e.g. samsung-health-data-api-1.1.0.aar)
                |     into:  app/libs/
                |  3. Re-run the build.
                |
                """.trimMargin()
            )
        }
    }
}
