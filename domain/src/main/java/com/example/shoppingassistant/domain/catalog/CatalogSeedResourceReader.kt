package com.example.shoppingassistant.domain.catalog

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

internal object CatalogSeedResourceReader {
    val json: Json = Json { ignoreUnknownKeys = true }

    fun resourceExists(resourcePath: String): Boolean =
        candidatePaths(resourcePath).any { candidate ->
            CatalogSeedResourceReader::class.java.classLoader.getResource(candidate) != null
        }

    fun readText(resourcePath: String): String {
        val resolvedPath = candidatePaths(resourcePath).firstOrNull { candidate ->
            CatalogSeedResourceReader::class.java.classLoader.getResource(candidate) != null
        } ?: error("Seed resource not found: $resourcePath")
        val stream = CatalogSeedResourceReader::class.java.classLoader.getResourceAsStream(resolvedPath)
            ?: error("Seed resource not found: $resourcePath")
        return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    fun <T> readJson(
        resourcePath: String,
        deserializer: DeserializationStrategy<T>,
    ): T {
        val content = readText(resourcePath)
        return try {
            json.decodeFromString(deserializer, content)
        } catch (error: Exception) {
            throw parseException(resourcePath = resourcePath, content = content, error = error)
        }
    }

    private fun parseException(
        resourcePath: String,
        content: String,
        error: Exception,
    ): IllegalStateException {
        if (error !is SerializationException && error !is IllegalArgumentException) {
            return IllegalStateException("Failed to parse seed resource '$resourcePath': ${error.message}", error)
        }

        val message = error.message.orEmpty()
        val offset = Regex("""offset\s+(\d+)""")
            .find(message)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        val lineColumn = offset?.let { toLineColumn(content = content, offset = it) }
        val lineHint = lineColumn?.let { "(line ${it.first}, column ${it.second}) " }.orEmpty()

        return IllegalStateException(
            "Failed to parse seed resource '$resourcePath' ${lineHint.trimEnd()}: $message",
            error,
        )
    }

    private fun candidatePaths(resourcePath: String): List<String> {
        val normalizedAliases = resourcePath
            .replace("/_registry/", "/registry/")
            .replace("/_global/", "/global/")
        return buildList {
            add(resourcePath)
            if (normalizedAliases != resourcePath) {
                add(normalizedAliases)
            }
        }
    }

    private fun toLineColumn(
        content: String,
        offset: Int,
    ): Pair<Int, Int> {
        val boundedOffset = offset.coerceIn(0, content.length)
        var line = 1
        var column = 1
        for (index in 0 until boundedOffset) {
            if (content[index] == '\n') {
                line += 1
                column = 1
            } else {
                column += 1
            }
        }
        return line to column
    }
}
