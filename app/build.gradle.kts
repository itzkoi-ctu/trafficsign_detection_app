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
            signingConfig = signingConfigs.getByName("debug")
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
configurations.all {
    resolutionStrategy {
        force("org.tensorflow:tensorflow-lite:2.17.0")
        force("org.tensorflow:tensorflow-lite-support:0.4.4")
    }
    // Chỉ exclude litert-api, KHÔNG exclude tensorflow-lite-api
    exclude(group = "com.google.ai.edge.litert", module = "litert-api")
}

dependencies {
    // TF1 (nếu vẫn cần .pb)
    implementation("org.tensorflow:tensorflow-android:1.13.1")

    // TensorFlow Lite runtime
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    // Nếu cần thêm ops nâng cao:

    // CameraX
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")

    // AndroidX cơ bản
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Các thư viện khác
    implementation(libs.androidx.monitor)
    implementation(libs.androidx.junit.ktx)
    implementation(libs.androidx.room.external.antlr)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.ui.graphics.android)
    testImplementation(libs.junit.junit)
    androidTestImplementation(libs.junit.junit)
}


// === Ép version TFLite để tránh xung đột (rất quan trọng!) ===
configurations.all {
    resolutionStrategy {
        force("org.tensorflow:tensorflow-lite:2.17.0")
    }
}
