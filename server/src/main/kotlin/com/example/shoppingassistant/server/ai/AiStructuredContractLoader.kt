package com.example.shoppingassistant.server.ai

import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

data class AiStructuredContract(
    val systemPrompt: String,
    val userPromptTemplate: String,
    val schema: JsonObject,
)

object AiStructuredContractLoader {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, AiStructuredContract>()

    fun load(contractName: String): AiStructuredContract =
        cache.getOrPut(contractName) {
            val basePath = "ai/contracts/$contractName"
            AiStructuredContract(
                systemPrompt = readText("$basePath/system_prompt.txt"),
                userPromptTemplate = readText("$basePath/user_prompt.txt"),
                schema = readJson("$basePath/schema.json"),
            )
        }

    fun renderTemplate(
        template: String,
        bindings: Map<String, String>,
    ): String =
        bindings.entries.fold(template) { current, (key, value) ->
            current.replace("{{${key}}}", value)
        }.trim()

    private fun readText(resourcePath: String): String =
        javaClass.classLoader.getResourceAsStream(resourcePath)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { reader -> reader.readText().trim() }
            ?: error("AI contract resource not found: $resourcePath")

    private fun readJson(resourcePath: String): JsonObject =
        json.parseToJsonElement(readText(resourcePath)).jsonObject
}
