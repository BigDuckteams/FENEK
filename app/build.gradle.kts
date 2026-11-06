import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// sherpa-onnx (on-device ASR and speaker diarization on top of onnxruntime) is published
// as an AAR on GitHub Releases, not on Maven, so it is fetched once into app/libs.
val sherpaOnnxVersion = libs.versions.sherpaOnnx.get()
val sherpaOnnxAar = file("libs/sherpa-onnx-$sherpaOnnxVersion.aar")
if (!sherpaOnnxAar.exists()) {
    val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/" +
        "v$sherpaOnnxVersion/sherpa-onnx-$sherpaOnnxVersion.aar"
    logger.lifecycle("Downloading $url")
    sherpaOnnxAar.parentFile.mkdirs()
    val partial = File(sherpaOnnxAar.path + ".part")
    URI(url).toURL().openStream().use { input ->
        partial.outputStream().use { output -> input.copyTo(output) }
    }
    check(partial.renameTo(sherpaOnnxAar)) { "Cannot move $partial to $sherpaOnnxAar" }
}

android {
    namespace = "app.fenek"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.fenek"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"
    }

    // A small APK per processor type (a phone needs only one) plus a universal APK.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // Keep the ~25 MB per-ABI native libraries compressed inside the APK.
            useLegacyPackaging = true
            // There are no 32-bit x86 phones; keep the universal APK to the three ABIs above.
            excludes += "lib/x86/**"
        }
    }
}

dependencies {
    implementation(files(sherpaOnnxAar))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
