plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)             // ← применяем тут
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
    implementation(project(":rank"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.common.jvm)
    ksp(libs.androidx.room.compiler)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    // сюда позже добавим: Ktor/Room/Coroutines и т.п.
    // Ktor client + JSON
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.serialization.kotlinx.json)

    // если где-то напрямую используешь kotlinx.serialization.json.*
    implementation(libs.kotlinx.serialization.json)

    // корутины
    implementation(libs.kotlinx.coroutines.android)

    // ViewModel
    implementation(libs.lifecycle.viewmodel.ktx)
    // Koin + Compose
    implementation("io.insert-koin:koin-android:4.0.0")
    implementation("io.insert-koin:koin-androidx-compose:4.0.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation(project(":domain"))
    testImplementation("junit:junit:4.13.2")
}
