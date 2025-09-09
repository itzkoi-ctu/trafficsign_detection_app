plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.trafficsignapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.trafficsignapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildFeatures {
        viewBinding = true
        // compose = true // nếu bạn có dùng Jetpack Compose
    }

    androidResources {
        noCompress += listOf("tflite", "pt", "pb", "bin", "mp3", "wav", "zip")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Ngăn xung đột .so nếu nhiều lib native cùng tên
    packagingOptions {
        jniLibs {
            pickFirsts += listOf(
                "**/libtensorflowlite_jni.so",
                "**/libtensorflowlite_select_tf_ops_jni.so",
                "**/libtensorflow_inference.so"
            )
        }
    }
}

dependencies {
    // === TF1 (.pb) ===
    implementation("org.tensorflow:tensorflow-android:1.13.1")

    // === TensorFlow Lite 2.x (.tflite) ===
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    // (Nếu model dùng op nâng cao như RNN, SelectOps…)
    // implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.17.0")

    // === ONNX Runtime (nếu dùng thêm onnx) ===
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.18.0")

    // === CameraX ===
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")

    // === AndroidX cơ bản ===
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // === Nếu có dùng Jetpack Compose ===
    // implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    // implementation("androidx.compose.ui:ui")
    // implementation("androidx.compose.material3:material3")
    // debugImplementation("androidx.compose.ui:ui-tooling")

    // === Các thư viện trong version catalog (nếu dùng) ===
    implementation(libs.androidx.monitor)
    implementation(libs.androidx.junit.ktx)
    implementation(libs.androidx.room.external.antlr)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit.junit)
    androidTestImplementation(libs.junit.junit)
}

// === Ép version TFLite để tránh xung đột (rất quan trọng!) ===
configurations.all {
    resolutionStrategy {
        force("org.tensorflow:tensorflow-lite:2.17.0")
    }
}