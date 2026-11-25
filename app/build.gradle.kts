plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val dataUrl = (project.findProperty("dataUrl") as String?) ?: ""

android {
    namespace = "com.floatx.gameturbo"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.floatx.gameturbo"
        minSdk = 29
        targetSdk = 35
        versionCode = 8
        versionName = "1.5.1"
        buildConfigField("String", "DATA_URL", "\"$dataUrl\"")
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
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    // Liquid glass (blur backdrop)
    implementation("dev.chrisbanes.haze:haze:1.5.0")
    // Shizuku
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
