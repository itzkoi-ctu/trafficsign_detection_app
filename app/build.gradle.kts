plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.trafficsignapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.trafficsignapp"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1" // hoặc phiên bản bạn đang dùng
    }
}

dependencies {
    // CameraX
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")

    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.13.0")

    // AppCompat, Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    implementation(platform("androidx.compose:compose-bom:2023.10.01"))

// Các thư viện Compose phổ biến
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation(libs.androidx.monitor)
    implementation(libs.androidx.junit.ktx)
    testImplementation(libs.junit.junit)
    androidTestImplementation(libs.junit.junit)
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation ("org.pytorch:pytorch_android:1.12.1")
    implementation ("org.pytorch:pytorch_android_torchvision:1.12.1")

    implementation ("org.tensorflow:tensorflow-lite-support:0.4.3") // hoặc version mới hơn

}
