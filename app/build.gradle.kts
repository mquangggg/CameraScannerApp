plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.camerascanner"
    compileSdk = 35
    android {
        // ... các cấu hình khác của bạn

        packaging {
            jniLibs {
                // Thay vì 'excludes.add()', hãy sử dụng 'pickFirst()'
                pickFirsts.add("lib/arm64-v8a/libc++_shared.so")
                pickFirsts.add("lib/armeabi-v7a/libc++_shared.so")
                pickFirsts.add("lib/x86/libc++_shared.so")
                pickFirsts.add("lib/x86_64/libc++_shared.so")
                // Thêm bất kỳ ABI nào khác mà bạn đang build cho
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.camerascanner"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    implementation(libs.recyclerview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("com.vanniktech:android-image-cropper:4.5.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation ("androidx.exifinterface:exifinterface:1.4.1")


    // Thư viện CameraX cho các chức năng camera
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation ("androidx.camera:camera-core:1.4.2")    // CameraX Extensions (nếu bạn cần các tính năng mở rộng như HDR, Night Mode, v.v.)
    implementation ("androidx.camera:camera-extensions:1.4.2") // Hoặc phiên bản mới nhất, ổn định

    // ML Kit để nhận diện đối tượng (hoặc nhận dạng văn bản nếu đó là mục đích chính của bạn)

    implementation ("com.github.bumptech.glide:glide:4.12.0")

    implementation("org.opencv:opencv:4.11.0")
    implementation("com.github.yukuku:ambilwarna:2.0.1")
}