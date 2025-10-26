plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    //alias(libs.plugins.ksp) // подключим, когда понадобится Room/генерация
}

android {
    namespace = "com.example.shoppingassistant"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.shoppingassistant"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.m3)
    implementation(libs.compose.preview)
    implementation(libs.ui)
    implementation(libs.foundation.layout)
    implementation(libs.foundation)
    implementation(libs.volley)
    debugImplementation(libs.compose.tooling)

    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation("androidx.compose.material:material-icons-extended") // ← добавить
    implementation("androidx.compose.foundation:foundation")
    implementation(project(":core"))
    implementation(project(":feature"))

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    // Koin + Compose
    implementation("io.insert-koin:koin-android:4.0.0")
    implementation("io.insert-koin:koin-androidx-compose:4.0.0")
    implementation("io.coil-kt:coil:2.7.0")
    implementation("io.coil-kt:coil-compose:2.7.0")



}
