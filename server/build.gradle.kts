plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

group = "com.example.shoppingassistant"
version = "0.0.1"

val ktorVersion = "3.0.0"
val logbackVersion = "1.5.21"
// Используем ту же версию Koin, что и в Android-модулях
val koinVersion = "4.0.0"

dependencies {
    implementation(project(":rank"))
    implementation("io.lettuce:lettuce-core:6.5.0.RELEASE")

    implementation("org.postgresql:postgresql:42.7.1")

    implementation("org.jetbrains.exposed:exposed-core:0.56.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.56.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.56.0")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.56.0")
    implementation("org.jetbrains.exposed:exposed-json:0.56.0")

    implementation(kotlin("stdlib"))

    // Ktor server
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-hsts-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // Логирование
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // Пароли
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("com.sun.mail:jakarta.mail:2.0.1")

    // Koin для JVM/Ktor — отсюда берутся:
    //  - org.koin.core.context.startKoin
    //  - org.koin.dsl.module
    //  - org.koin.java.KoinJavaComponent
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation(project(":domain"))

    // Тесты
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("org.testcontainers:postgresql:1.21.4")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.example.shoppingassistant.server.ServerMainKt")
}
