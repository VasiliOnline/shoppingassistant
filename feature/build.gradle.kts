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
    debugImplementation(libs.compose.tooling)
    implementation("androidx.compose.material:material-icons-extended") // ← добавить
    implementation(libs.navigation.compose)
    implementation(project(":core"))
}
