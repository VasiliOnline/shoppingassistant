plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.shoppingassistant.core"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true } // на будущее для core/ui компонентов
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    // сюда позже добавим: Ktor/Room/Coroutines и т.п.
}
