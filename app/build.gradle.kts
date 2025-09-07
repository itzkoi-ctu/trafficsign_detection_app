plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.trafficsignapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.trafficsignapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        // Nếu từng gặp lỗi thiếu .so, mở dòng sau:
         ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }


    buildFeatures {
        viewBinding = true
        // compose = true   // CHỈ bật nếu bạn thật sự dùng UI Compose
    }

    androidResources {
        noCompress += listOf("tflite", "pt", "bin", "pb", "mp3", "wav", "zip")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Nếu dùng Compose, mở khối này:
    // composeOptions { kotlinCompilerExtensionVersion = "1.5.1" }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    // CameraX
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")

    // TF1 cho SSD .pb (TensorFlowInferenceInterface)
    implementation("org.tensorflow:tensorflow-android:1.13.1")

    // Giữ 1 bản TFLite duy nhất (ví dụ 2.12.0)
    implementation("org.tensorflow:tensorflow-lite:2.12.0")
    // (xóa mọi dòng tensorflow-lite khác, ví dụ 2.13.0, để tránh xung đột)

    // AndroidX cơ bản
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:latest.release")

    // Nếu DÙNG Compose UI thì mới thêm:
    // implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    // implementation("androidx.compose.ui:ui")
    // implementation("androidx.compose.material3:material3")
    // implementation("androidx.compose.ui:ui-tooling-preview")
    // debugImplementation("androidx.compose.ui:ui-tooling")

    // (Giữ lại các dependency từ version catalog `libs.*` nếu project bạn đang dùng)
    implementation(libs.androidx.monitor)
    implementation(libs.androidx.junit.ktx)
    implementation(libs.androidx.room.external.antlr)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit.junit)
    androidTestImplementation(libs.junit.junit)
}
