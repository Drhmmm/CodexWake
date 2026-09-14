import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    id("com.android.application")

}

abstract class PrepareJapaneseModels : DefaultTask() {
    @get:Input abstract val bashExecutable: Property<String>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @get:InputFile abstract val fetchScript: RegularFileProperty
    @get:InputFile abstract val checksums: RegularFileProperty
    @get:Inject abstract val execOperations: ExecOperations

    @TaskAction
    fun prepare() {
        execOperations.exec {
            commandLine(bashExecutable.get(), fetchScript.get().asFile.invariantSeparatorsPath,
                outputDirectory.get().dir("ja").asFile.invariantSeparatorsPath)
        }
    }
}

val prepareJapaneseModels by tasks.registering(PrepareJapaneseModels::class) {
    group = "build setup"
    description = "Fetch and verify the pinned offline Japanese recognition models"
    bashExecutable.set(providers.environmentVariable("BASH").orElse("bash"))
    fetchScript.set(rootProject.layout.projectDirectory.file("tools/fetch-japanese-deps.sh"))
    checksums.set(layout.projectDirectory.file("src/main/assets/ja/SHA256SUMS"))
    outputDirectory.set(layout.buildDirectory.dir("generated/japaneseAssets"))
}

android {
    namespace = "com.desmond.gptwake"
    // Keep the upstream SDK and service behavior unchanged.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.desmond.gptwake"
        minSdk = 32
        targetSdk = 36
        versionCode = 7
        versionName = "1.1.1-codex.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    androidResources {
        noCompress += listOf("onnx", "ort", "txt", "phone")
    }

    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file("tools/fixtures/japanese"))

    buildFeatures {
        viewBinding = false

    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        getByName("debug") {
            // Not in git. Its only purpose is a stable debug signature so `adb install -r` keeps
            // working across rebuilds on a test device; a fresh clone simply falls back to the
            // SDK's own debug key.
            val ks = rootProject.file("debug.keystore")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "android"
                keyAlias = "probe"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Deliberately left unsigned. CI signs with apksigner using the release keystore held
            // in repository secrets, so no signing material ever lives in this repo.
        }
    }

    packaging {
        resources.merges += listOf("META-INF/LICENSE.md", "META-INF/CONTRIBUTORS.md")
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(prepareJapaneseModels,
        PrepareJapaneseModels::outputDirectory)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Kotlin API and native .so both come from the same sherpa-onnx v1.13.4 AAR.
    // The Kotlin Gradle plugin supplies kotlin-stdlib; pinning it here would risk a
    // version skew against the compiler.
    implementation(files("libs/sherpa-onnx-1.13.4-classes.jar"))
    // Offline Japanese kanji readings; includes the IPADIC dictionary in the APK.
    implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")

    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.core:core:1.17.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("org.mockito:mockito-core:5.23.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")


}
