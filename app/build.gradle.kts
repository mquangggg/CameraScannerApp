plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.camerascanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.camerascanner"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("com.vanniktech:android-image-cropper:4.5.0")
    implementation("com.github.barteksc:pdfium-android:1.8.2")
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation ("androidx.exifinterface:exifinterface:1.3.6")


    // Thư viện CameraX cho các chức năng camera
    implementation("androidx.camera:camera-camera2:1.3.3")
    implementation("androidx.camera:camera-lifecycle:1.3.3")
    implementation("androidx.camera:camera-view:1.3.3")
    // CameraX core library
    implementation ("androidx.camera:camera-core:1.3.3")    // CameraX Extensions (nếu bạn cần các tính năng mở rộng như HDR, Night Mode, v.v.)
    implementation ("androidx.camera:camera-extensions:1.3.3") // Hoặc phiên bản mới nhất, ổn định

    // ML Kit để nhận diện đối tượng (hoặc nhận dạng văn bản nếu đó là mục đích chính của bạn)
    implementation("com.google.mlkit:object-detection:17.0.1")
    implementation ("com.github.bumptech.glide:glide:4.12.0")
}