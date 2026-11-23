plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "com.gamershub"
    compileSdk = 35
    // The DFRoot payload (lkm/ + src/main/jni) is aarch64-only: libcxx.S is ARM64 asm,
    // and KernelSU's KMI modules exist for arm64 GKI kernels only. Build and package
    // arm64-v8a and say so in the README instead of shipping a broken universal APK.
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.gamershub"
        minSdk = 26
        targetSdk = 35
        versionCode = 125
        versionName = "6.38.1"
        ndk { abiFilters += listOf("arm64-v8a") }
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debugConfig")
        }
        release {
            // Shrink dead code, but do NOT run the aggressive optimizer and do
            // not obfuscate: both have a history of breaking Shizuku user-service
            // reflection and kotlinx-serialization on real devices (random
            // crashes after Shizuku connects). Stability > ~200 KB of APK size.
            // OFF on purpose. R8 kept eating classes that are only reached on a
            // particular screen (and anything reflective), which showed up as the
            // app closing itself the moment a panel or a settings page opened.
            // A few extra megabytes are a fair trade for not crashing.
            isMinifyEnabled = false
            // Resource shrinking stays OFF on purpose: R8 inlines R.raw.* ids, so a cold
            // shrinker pass decided the intro clips were unused and silently deleted
            // 12 MB of media (v6.7.0 shipped without the intro video). The unused
            // view resources cost a few hundred KB — the intro is not negotiable.
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            // The payload libraries are *executables* named lib*.so. They must be
            // extracted into nativeLibraryDir (the one directory with exec permission)
            // or ExploitRunner's ProcessBuilder fails on API 23+.
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/NOTICE", "META-INF/NOTICE.txt")
        }
    }

    lint {
        // This project is distributed as an APK release via GitHub, not Play.
        // Lint still runs in CI as warnings; it must not abort the release build.
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.savedstate.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.squareup.okhttp)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // Wireless-debugging ADB client (vendored under io.github.muntashirakon.adb,
    // Apache-2.0 arm of its dual licence) needs pairing crypto.
    implementation(libs.bouncycastle.prov)
    // TLS 1.3 keying-material export for pairing. Without this the client falls back to
    // com.android.org.conscrypt, whose exportKeyingMaterial is hidden API and refuses to
    // be called on Android 16 — NoSuchMethodException, pairing dead, "wireless
    // debugging off" on screen no matter what the user does.
    implementation(libs.conscrypt.android)

}
