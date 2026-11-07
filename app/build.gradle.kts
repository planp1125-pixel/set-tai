plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.combo.monkeycompanion"
    buildToolsVersion = "37.0.0"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    defaultConfig {
        applicationId = "com.combo.monkeycompanion"
        minSdk = 26
        targetSdk = 35
        versionCode = 86
        versionName = "10.38.10"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    signingConfigs {
        // CI: keystore decoded from repo secrets. Local builds: unchanged (box debug keystore).
        System.getenv("SETTAI_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }?.let { ks ->
            create("ci") {
                storeFile = file(ks)
                storePassword = System.getenv("SETTAI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SETTAI_KEY_ALIAS")
                keyPassword = System.getenv("SETTAI_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("ci") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }
    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
            isDefault = true
        }
        create("play") {
            dimension = "distribution"
        }
    }
    packaging {
        jniLibs { useLegacyPackaging = true }
    }
    // Platform-neutral code (rig, animations, IK climber, mood brain, lines, prompt, action tags,
    // online brains) lives in ../shared and is also compiled by the desktop app.
    sourceSets.getByName("main").kotlin.directories.add("../shared/src/main/kotlin")
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.1")
}

android.testOptions.unitTests.isIncludeAndroidResources = true
android.testOptions.unitTests.all { it.systemProperty("preview.out", "${rootDir}/build/test-out/monkey-preview.png"); it.systemProperty("settai.out", "${rootDir}/build/test-out"); it.systemProperty("settai.root", rootDir.path); it.doFirst { mkdir("${rootDir}/build/test-out") }; it.systemProperty("clip.frames", System.getenv("CLIP_FRAMES") ?: ""); it.systemProperty("golden.record", System.getenv("GOLDEN_RECORD") ?: "") }

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
}
