import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

// Load local signing config from key.properties (gitignored).
// In CI, env vars take precedence over the file — no file needed on GitHub Actions.
val keyProps = Properties().also { props ->
    val f = rootProject.file("key.properties")
    if (f.exists()) props.load(f.inputStream())
}

android {
    namespace = "com.velo.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.velo.app"
        minSdk = 26
        targetSdk = 35
        // CI injects VERSION_NAME from the git tag (v1.2.3 → 1.2.3).
        // Local builds fall back to the string below — bump it to match your next tag.
        val rawVersion = (System.getenv("VERSION_NAME") ?: "1.0.1").trimStart('v')
        versionName = rawVersion
        // Encode semver → integer: 1.2.3 → 10203, 1.10.0 → 11000 (minor/patch max 99)
        val parts = rawVersion.split(".").map { it.toIntOrNull() ?: 0 }
        versionCode = (parts.getOrElse(0) { 0 } * 10000) +
                      (parts.getOrElse(1) { 0 } * 100) +
                       parts.getOrElse(2) { 0 }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        ndk {
            // x86/x86_64 are emulator-only ABIs — real Android devices use arm.
            // Dropping them cuts APK size by ~50%. Use AAB for Play Store (Google handles per-ABI delivery).
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

        // Device-verification endpoint — change here instead of in DeviceTracker.kt.
        // Can be overridden per buildType (e.g. a staging URL in debug).
        buildConfigField(
            "String",
            "NETLIFY_VERIFY_ENDPOINT",
            "\"https://gleeful-paprenjak-a451e2.netlify.app/.netlify/functions/verifyDevice\""
        )
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        create("release") {
            // CI: reads from env vars set by the GitHub Actions workflow.
            // Local: falls back to key.properties (gitignored — see key.properties.example).
            val storePath = System.getenv("KEYSTORE_PATH") ?: keyProps.getProperty("storeFile", "")
            if (storePath.isNotEmpty()) storeFile = file(storePath)
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: keyProps.getProperty("storePassword", "")
            keyAlias      = System.getenv("KEY_ALIAS")         ?: keyProps.getProperty("keyAlias", "")
            keyPassword   = System.getenv("KEY_PASSWORD")      ?: keyProps.getProperty("keyPassword", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions { 
        jvmTarget = "21"
        freeCompilerArgs += listOf("-Xannotation-default-target=param-property")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

}

dependencies {
    // Compose UI — BOM 2025.02.00 (latest stable)
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    // Activity + Navigation
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.8.9")

    // Core AndroidX
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Android 16 — Predictive Back full support
    implementation("androidx.activity:activity:1.10.1")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.56")
    ksp("com.google.dagger:hilt-compiler:2.56")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // WorkManager (background downloads)
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Room 2.7.0 — KSP2 compatible (fixes 'unexpected jvm signature V')
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")

    // Media3 (ExoPlayer + session for system media controls)
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media:media:1.7.1") // Required for MediaSessionCompat.Token

    // DataStore (settings)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // yt-dlp wrapper with 16KB Android 15 + Python 3.11+ support (removes libandroid-support.so dependency)
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Image loading (thumbnails)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
