plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.speech2text"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.speech2text"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
		externalNativeBuild {
            cmake {
                cppFlags += ""
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // NDK configuration for Whisper.cpp
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }
	externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("com.alphacephei:vosk-android:0.3.47")
	testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}