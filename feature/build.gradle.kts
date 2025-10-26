plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.shoppingassistant.feature"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.m3)
    implementation(libs.compose.preview)
    implementation(libs.ui)
    implementation(libs.material3)
    implementation(libs.ui.text)
    implementation(libs.foundation)
    implementation(libs.animation.core)
    implementation(libs.foundation.layout)
    debugImplementation(libs.compose.tooling)
    implementation("androidx.compose.material:material-icons-extended") // ← добавить
    implementation(libs.navigation.compose)
    implementation(project(":core"))
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.insert-koin:koin-android:4.0.0")
    implementation("io.insert-koin:koin-androidx-compose:4.0.0") // <-- ЭТО ВАЖНО

}
