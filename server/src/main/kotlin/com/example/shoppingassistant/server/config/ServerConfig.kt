// Last synced: 2025-11-20 13:36
package com.example.shoppingassistant.server.config

/**
 * Конфигурация подключения к Postgres.
 *
 * Значения по умолчанию:
 *  - хост: localhost
 *  - порт: 5432
 *  - имя БД: shoppingassistant
 *  - пользователь: Boss
 *
 * Пароль БД из кода не хардкодим — читаем только из переменной окружения DB_PASSWORD.
 */
data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String?,
    val driver: String = "org.postgresql.Driver",
) {
    companion object {
        /**
         * Читаем конфиг БД из переменных окружения:
         *
         *  - DB_HOST — хост Postgres (по умолчанию "localhost")
         *  - DB_PORT — порт Postgres (по умолчанию 5432)
         *  - DB_NAME — имя базы (по умолчанию "shoppingassistant")
         *  - DB_USER — имя пользователя (по умолчанию "Boss")
         *  - DB_PASSWORD — пароль (обязателен на проде, локально можно временно без него)
         */
        fun fromEnv(): DatabaseConfig {
            val host = envValue("DB_HOST") ?: "localhost"
            val port = envValue("DB_PORT")?.toIntOrNull() ?: 5432
            val name = envValue("DB_NAME") ?: "shoppingassistant"
            val user = envValue("DB_USER") ?: "Boss"
            val password = envValue("DB_PASSWORD")
                ?: throw IllegalStateException("DB_PASSWORD must be provided for production")

            val url = "jdbc:postgresql://$host:$port/$name"

            return DatabaseConfig(
                url = url,
                user = user,
                password = password,
            )
        }
    }
}

/**
 * Базовая конфигурация сервера.
 *
 * Пока здесь:
 *  - порт и хост Ktor-сервера;
 *  - список разрешённых CORS-оригинов;
 *  - конфиг подключения к Postgres.
 */
data class ServerConfig(
    val port: Int = 8080,
    val host: String = "0.0.0.0",
    val corsOrigins: List<String> = emptyList(),
    val db: DatabaseConfig,
    val enableHsts: Boolean = false,
) {
    companion object {

        /**
         * Читаем конфиг из переменных окружения:
         *
         *  - APP_PORT — порт сервера (Int, по умолчанию 8081)
         *  - APP_HOST — интерфейс (обычно "0.0.0.0")
         *  - APP_CORS_ORIGINS — список origin через запятую
         *
         *  Параметры БД читаем через [DatabaseConfig.fromEnv].
         */
        fun fromEnv(): ServerConfig {
            val port = envValue("APP_PORT")?.toIntOrNull() ?: 8081
            val host = envValue("APP_HOST") ?: "0.0.0.0"
            val cors = envValue("APP_CORS_ORIGINS")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            val enableHsts = parseBooleanEnv("ENABLE_HSTS", defaultValue = false)

            val dbConfig = DatabaseConfig.fromEnv()

            return ServerConfig(
                port = port,
                host = host,
                corsOrigins = cors,
                db = dbConfig,
                enableHsts = enableHsts,
            )
        }
    }
}
