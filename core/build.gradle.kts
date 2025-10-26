plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization) // ← нужен для @Serializable и ktor-json
}

android {
    namespace = "com.example.shoppingassistant.core"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true } // на будущее для core/ui компонентов

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
    // сюда позже добавим: Ktor/Room/Coroutines и т.п.
    // Ktor client + JSON
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // если где-то напрямую используешь kotlinx.serialization.json.*
    implementation(libs.kotlinx.serialization.json)

    // корутины
    implementation(libs.kotlinx.coroutines.android)

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    // Koin + Compose
    implementation("io.insert-koin:koin-android:4.0.0")
    implementation("io.insert-koin:koin-androidx-compose:4.0.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
