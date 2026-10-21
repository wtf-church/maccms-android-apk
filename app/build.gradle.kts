plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.maccms.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.maccms.mobile"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        ndk {
            // 手机真机（arm64/armv7）与模拟器（x86_64）均可安装
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        // 固定调试签名，保证 Docker/多次构建的 APK 可以覆盖安装
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // 写入最小占位 .so 使 APK 的 native-code 显式包含目标 ABI
    val abiDir = layout.buildDirectory.dir("generated/jniLibs/debug")
    tasks.matching { it.name == "preDebugBuild" }.configureEach {
        doLast {
            listOf("arm64-v8a", "armeabi-v7a", "x86_64").forEach { abi ->
                val out = abiDir.get().file("$abi/libplaceholder.so").asFile
                out.parentFile.mkdirs()
                out.writeBytes(byteArrayOf(0x7F, 0x45, 0x4C, 0x46, 0x01, 0x01, 0x01, 0x00))
            }
        }
    }

    sourceSets {
        getByName("main") { jniLibs.srcDir(layout.buildDirectory.dir("generated/jniLibs/debug")) }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packagingOptions {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.0")
    implementation("androidx.media3:media3-ui:1.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
