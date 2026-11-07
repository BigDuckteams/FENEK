import com.android.build.api.artifact.SingleArtifact
import java.net.URI
import java.nio.file.Files
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Puts the models of the "fat" build into a generated assets folder (`models/<id>/<file>`), from the list
 * in `bundled-models.json`. Downloads go to a cache in the Gradle user home, outside `build/`, so that only
 * the very first build downloads them (about 1.4 GB) and `clean` does not throw them away; the assets
 * folder gets hard links to the cached files (or copies, where links are not possible). A download of the
 * wrong size is rejected.
 */
abstract class FetchBundledModels : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        @Suppress("UNCHECKED_CAST")
        val models = (groovy.json.JsonSlurper().parse(manifest.get().asFile) as Map<String, Any>)["models"] as List<Map<String, Any>>
        for (model in models) {
            val id = model["id"] as String
            @Suppress("UNCHECKED_CAST")
            for (file in model["files"] as List<Map<String, Any>>) {
                val name = file["name"] as String
                val bytes = (file["bytes"] as Number).toLong()
                val urls = (file["urls"] as List<*>).map { it as String }
                val cached = File(cacheDir.get().asFile, "$id/$name")
                if (!(cached.isFile && cached.length() == bytes)) {
                    cached.parentFile.mkdirs()
                    download(urls, cached, bytes)
                }
                val target = File(outputDir.get().asFile, "models/$id/$name")
                target.parentFile.mkdirs()
                target.delete()
                try {
                    Files.createLink(target.toPath(), cached.toPath())
                } catch (e: Exception) {
                    cached.copyTo(target, overwrite = true)
                }
            }
        }
    }

    private fun download(urls: List<String>, target: File, bytes: Long) {
        var lastError: Exception? = null
        for (url in urls) {
            val part = File(target.path + ".part")
            try {
                logger.lifecycle("Downloading $url (${bytes / 1_000_000} MB)")
                val connection = URI(url).toURL().openConnection().apply {
                    connectTimeout = 30_000
                    readTimeout = 60_000
                }
                connection.getInputStream().use { input -> part.outputStream().use { output -> input.copyTo(output, 1 shl 20) } }
                check(part.length() == bytes) { "${target.name}: ${part.length()} bytes instead of $bytes" }
                check(part.renameTo(target)) { "Cannot move $part to $target" }
                return
            } catch (e: Exception) {
                lastError = e
                part.delete()
                logger.warn("Cannot download $url: ${e.message}")
            }
        }
        throw GradleException("Cannot download ${target.name} from any mirror", lastError)
    }
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

// llama.cpp (the on-device language model runtime) is built from source with the NDK. It is not
// committed: the pinned release is fetched once into src/main/cpp/llama.cpp (see CMakeLists.txt there).
val llamaCppTag = "v0.9.0"
val llamaCppDir = file("src/main/cpp/llama.cpp")
if (!File(llamaCppDir, "CMakeLists.txt").exists()) {
    val url = "https://github.com/ggml-org/llama.cpp/archive/refs/tags/$llamaCppTag.zip"
    logger.lifecycle("Downloading $url")
    val archive = layout.buildDirectory.file("llama.cpp-$llamaCppTag.zip").get().asFile
    archive.parentFile.mkdirs()
    URI(url).toURL().openStream().use { input -> archive.outputStream().use { output -> input.copyTo(output) } }
    val staging = File(llamaCppDir.path + ".part").apply { deleteRecursively(); mkdirs() }
    ZipFile(archive).use { zip ->
        for (entry in zip.entries().asSequence()) {
            // The archive has one top folder (llama.cpp-<version>); its content goes straight into the target.
            val relative = entry.name.substringAfter('/', "")
            if (relative.isEmpty()) continue
            val target = File(staging, relative)
            check(target.canonicalPath.startsWith(staging.canonicalPath)) { "Unsafe path in the archive: ${entry.name}" }
            if (entry.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile.mkdirs()
                zip.getInputStream(entry).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            }
        }
    }
    check(staging.renameTo(llamaCppDir)) { "Cannot move $staging to $llamaCppDir" }
    archive.delete()
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

        externalNativeBuild {
            cmake {
                // llama.cpp is slow without optimization, whatever the variant of the app is.
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=c++_shared")
                // The language model needs a 64-bit phone; the 32-bit build simply goes without it.
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
        }
    }

    ndkVersion = "29.0.14206865"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Two universal APKs (every processor type in one file), which differ in where the models come from.
    flavorDimensions += "distribution"
    productFlavors {
        // Downloads the models on the first launch: a network is needed once.
        create("online") {
            dimension = "distribution"
            buildConfigField("boolean", "BUNDLED_MODELS", "false")
        }
        // Carries the default set of models inside the APK (about 1.4 GB): works with no network from the start.
        create("fat") {
            dimension = "distribution"
            buildConfigField("boolean", "BUNDLED_MODELS", "true")
        }
    }

    // Release APKs are signed with the local debug key when there is one, so that they install on a phone
    // at once (this is not a key for Google Play; it is the public Android default).
    val debugKeystore = File(System.getProperty("user.home"), ".android/debug.keystore")
    signingConfigs {
        if (debugKeystore.isFile) {
            create("demo") {
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
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
            signingConfigs.findByName("demo")?.let { signingConfig = it }
        }
    }

    // Model files are read as they are: compressing 1.4 GB would only make the first launch slower.
    androidResources {
        noCompress += listOf("onnx", "gguf")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
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

val releaseApkCopies = mutableListOf<TaskProvider<Copy>>()

androidComponents {
    beforeVariants { variant ->
        // The fat build is a release artifact: a debug variant would only download 1.4 GB for nothing.
        if (variant.flavorName == "fat" && variant.buildType == "debug") variant.enable = false
    }
    onVariants { variant ->
        val capitalized = variant.name.replaceFirstChar { it.uppercase() }
        if (variant.flavorName == "fat") {
            val fetch = tasks.register<FetchBundledModels>("fetch${capitalized}Models") {
                manifest.set(layout.projectDirectory.file("bundled-models.json"))
                cacheDir.set(File(gradle.gradleUserHomeDir, "fenek-models"))
                outputDir.set(layout.buildDirectory.dir("bundled-models/${variant.name}"))
            }
            variant.sources.assets?.addGeneratedSourceDirectory(fetch, FetchBundledModels::outputDir)
        }
        if (variant.buildType == "release") {
            releaseApkCopies += tasks.register<Copy>("copy${capitalized}Apk") {
                from(variant.artifacts.get(SingleArtifact.APK)) { include("*.apk") }
                into(rootProject.layout.projectDirectory.dir("dist"))
                rename { "FENEK-${variant.flavorName}-universal.apk" }
            }
        }
    }
}

// ./gradlew releaseApks: both universal release APKs, ready to install, in dist/.
tasks.register("releaseApks") {
    group = "build"
    description = "Builds FENEK-online-universal.apk and FENEK-fat-universal.apk into dist/"
    dependsOn(releaseApkCopies)
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
