import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.example.shoppingassistant"
version = "1.0.0"

// Чистый JVM-модуль для домена (общие модели и use-case’ы, без Android-зависимостей)
kotlin {
    jvmToolchain(21)
}

dependencies {
    // Стандартная библиотека Kotlin
    implementation(kotlin("stdlib"))

    // Сериализация, если доменные модели помечены @Serializable
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Корутинный core (для suspend-функций и Flow в домене, если будут)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Юнит-тесты
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnit()
    val enforceGoldenGate = (
        findProperty("stage21.tech.golden.enforce")?.toString()
            ?: System.getenv("STAGE21_TECH_GOLDEN_ENFORCE")
            ?: "false"
        )
    systemProperty("stage21.tech.golden.enforce", enforceGoldenGate)
}

tasks.named<ProcessResources>("processResources").configure {
    from("src/main/resources") {
        include("taxonomy/stage2/2.2/_registry/**")
        include("taxonomy/stage2/2.2/_global/**")
        includeEmptyDirs = false
        eachFile {
            path = path
                .replace("taxonomy/stage2/2.2/_registry/", "taxonomy/stage2/2.2/registry/")
                .replace("taxonomy/stage2/2.2/_global/", "taxonomy/stage2/2.2/global/")
        }
    }
}
