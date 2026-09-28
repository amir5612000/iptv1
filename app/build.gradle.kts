plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mypanel.iptv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mypanel.iptv"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // آدرس پنل و کلید API را می‌توانید هنگام بیلد در گیتهاب تنظیم کنید (اختیاری)
        // اگر خالی باشد، کاربر آن‌ها را داخل اپ وارد می‌کند.
        buildConfigField("String", "DEFAULT_PANEL_URL", "\"${System.getenv("PANEL_URL") ?: ""}\"")
        buildConfigField("String", "DEFAULT_API_KEY", "\"${System.getenv("PANEL_KEY") ?: ""}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=androidx.media3.common.util.UnstableApi"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-datasource:$media3")

    implementation("io.coil-kt:coil:2.6.0")
}
