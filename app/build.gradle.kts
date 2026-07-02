import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
}

android {
    namespace = "com.example.menu_recipe_app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.menu_recipe_app"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")

        if (localPropertiesFile.exists()) {
            properties.load(FileInputStream(localPropertiesFile))
        }

        val apiKey = properties.getProperty("RECIPE_API_KEY") ?: "키_없음"

        // 주의: String 값은 양옆에 쌍따옴표(\")가 들어가야 제대로 인식됩니다!
        buildConfigField("String", "RECIPE_API_KEY", "\"$apiKey\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true // BuildConfig 사용 활성화
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("io.coil-kt:coil-compose:2.6.0")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    val room_version = "2.8.4"
    implementation("androidx.room:room-runtime:${room_version}")
    implementation("androidx.room:room-ktx:${room_version}") // 코루틴(비동기) 지원
    ksp("androidx.room:room-compiler:${room_version}") // KSP 컴파일러
    // Retrofit & Gson (네트워크 통신용)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
}