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
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.m3)
    implementation(libs.compose.preview)
    debugImplementation(libs.compose.tooling)

    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    implementation(project(":core"))
    implementation(project(":feature"))
}
