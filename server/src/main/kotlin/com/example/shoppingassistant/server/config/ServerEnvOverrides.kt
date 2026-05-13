package com.example.shoppingassistant.server.config

import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import java.util.Properties

internal object ServerEnvOverrides {
    private val repoRoot: Path? by lazy { locateRepoRoot() }
    private val fileValues: Map<String, String> by lazy { loadFileValues() }

    fun value(key: String): String? =
        System.getenv(key)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: fileValues[key]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

    fun booleanValue(key: String, defaultValue: Boolean): Boolean {
        val normalized = value(key)?.lowercase(Locale.ROOT)
        return when (normalized) {
            "1",
            "true",
            "yes",
            "y",
            "on",
            -> true

            "0",
            "false",
            "no",
            "n",
            "off",
            -> false

            else -> defaultValue
        }
    }

    private fun locateRepoRoot(): Path? {
        var current: Path? = Paths.get("").toAbsolutePath().normalize()
        repeat(6) {
            val candidate = current ?: return@repeat
            if (Files.exists(candidate.resolve("settings.gradle.kts")) || Files.exists(candidate.resolve(".git"))) {
                current = candidate
                return candidate
            }
            current = candidate.parent
        }
        return null
    }

    private fun loadFileValues(): Map<String, String> {
        val root = repoRoot ?: return emptyMap()
        val merged = linkedMapOf<String, String>()
        listOf(
            ".env",
            "env.local",
            "local.env",
            ".env.local",
            "visual-search.env",
            "visual-search.local.env",
            "secrets.env",
            "local.properties",
        ).forEach { fileName ->
            val path = root.resolve(fileName)
            if (!Files.isRegularFile(path)) return@forEach
            val values = if (fileName.endsWith(".properties", ignoreCase = true)) {
                parsePropertiesFile(path)
            } else {
                parseDotEnvFile(path)
            }
            values.forEach { (key, value) ->
                merged[key] = value
            }
        }
        return merged
    }

    private fun parsePropertiesFile(path: Path): Map<String, String> {
        val properties = Properties()
        Files.newBufferedReader(path).use { reader ->
            properties.load(reader)
        }
        return properties.stringPropertyNames()
            .associateWith { key -> properties.getProperty(key) }
    }

    private fun parseDotEnvFile(path: Path): Map<String, String> {
        val merged = linkedMapOf<String, String>()
        Files.readAllLines(path).forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val cleaned = if (line.startsWith("export ")) line.removePrefix("export ").trim() else line
            val separatorIndex = cleaned.indexOf('=')
            if (separatorIndex <= 0) return@forEach
            val key = cleaned.substring(0, separatorIndex).trim()
            if (key.isEmpty()) return@forEach
            val rawValue = cleaned.substring(separatorIndex + 1).trim()
            val value = rawValue
                .removeSurrounding("\"")
                .removeSurrounding("'")
            if (value.isNotEmpty()) {
                merged[key] = value
            }
        }
        return merged
    }
}

internal fun envValue(key: String): String? = ServerEnvOverrides.value(key)

internal fun envValue(primaryKey: String, fallbackKey: String? = null): String? =
    envValue(primaryKey) ?: fallbackKey?.let(::envValue)

internal fun parseListEnv(
    primaryKey: String,
    fallbackKey: String? = null,
): List<String> = envValue(primaryKey, fallbackKey)
    ?.split(',', ';', '\n', '\r')
    ?.mapNotNull { token ->
        val normalized = token.trim()
        if (normalized.isEmpty()) {
            null
        } else {
            normalized
                .substringAfter(':', missingDelimiterValue = normalized)
                .trim()
                .takeIf { value -> value.isNotEmpty() }
        }
    }
    ?.distinct()
    .orEmpty()

internal fun parseBooleanEnv(
    key: String,
    defaultValue: Boolean,
): Boolean = ServerEnvOverrides.booleanValue(key, defaultValue)

internal fun parseBooleanEnv(
    primaryKey: String,
    fallbackKey: String? = null,
    defaultValue: Boolean,
): Boolean {
    val normalized = envValue(primaryKey, fallbackKey)?.lowercase(Locale.ROOT)
    return when (normalized) {
        "1",
        "true",
        "yes",
        "y",
        "on",
        -> true

        "0",
        "false",
        "no",
        "n",
        "off",
        -> false

        else -> defaultValue
    }
}
