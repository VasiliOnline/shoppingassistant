plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.example.shoppingassistant"
version = "0.0.1"

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation(project(":domain"))
}

kotlin {
    jvmToolchain(21)
}
