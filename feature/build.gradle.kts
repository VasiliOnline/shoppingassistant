plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    //alias(libs.plugins.ksp)
}

// Workaround for Windows file lock on `feature/build/.../classes.jar` (e.g. IDE/AV scanning).
layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("feature_alt"))

android {
    namespace = "com.example.shoppingassistant.feature"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildToolsVersion = "36.0.0"
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
    implementation(libs.androidx.benchmark.traceprocessor)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.unit)
    implementation(libs.play.services.analytics.impl)
    implementation(libs.ui.graphics)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.ink.brush)
    implementation(libs.androidx.ui)
    implementation(libs.room.ktx)
    implementation(libs.androidx.compose.ui.ui.graphics)
    implementation(libs.androidx.material3)
    debugImplementation(libs.compose.tooling)
    implementation("androidx.compose.material:material-icons-extended") // ← добавить
    implementation(libs.navigation.compose)
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(project(":rank"))
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.insert-koin:koin-android:4.0.0")
    implementation("io.insert-koin:koin-androidx-compose:4.0.0") // <-- ЭТО ВАЖНО

    // JSON сериализация для экранов (IngestPage и пр.)
    implementation(libs.kotlinx.serialization.json)

    // Детализированная карта доставки (MapLibre GL)
    implementation("org.maplibre.gl:android-sdk:11.5.0")

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.common.jvm)
    implementation(libs.lottie.compose)
    //ksp(libs.androidx.room.compiler)

    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)


    implementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
