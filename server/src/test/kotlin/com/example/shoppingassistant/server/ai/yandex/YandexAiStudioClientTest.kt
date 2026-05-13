package com.example.shoppingassistant.server.ai.yandex

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

class YandexAiStudioClientTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun complete_structured_json_uses_responses_api_when_prompt_id_is_present() = runBlocking {
        var capturedPath: String? = null
        var capturedBody: String? = null
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/responses") { exchange ->
            capturedPath = exchange.requestURI.path
            capturedBody = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val response = """
                {
                  "status": "completed",
                  "output": [
                    {
                      "content": [
                        { "type": "output_text", "text": "{\"title\":\"mouse\"}" }
                      ]
                    }
                  ]
                }
            """.trimIndent()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.toByteArray(Charsets.UTF_8).size.toLong())
            exchange.responseBody.use { body ->
                body.write(response.toByteArray(Charsets.UTF_8))
            }
        }
        server.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val client = DefaultYandexAiStudioClient()
            val result = client.completeStructuredJson(
                YandexAiStudioStructuredRequest(
                    transport = YandexAiStudioTransportConfig(
                        baseUrl = "$baseUrl/chat",
                        responsesBaseUrl = baseUrl,
                        apiKey = "token",
                        projectId = "folder-id",
                    ),
                    model = "gpt://folder-id/gemma-3-27b-it/latest",
                    timeoutMs = 5_000,
                    systemInstruction = "ignored in prompt mode",
                    contentParts = listOf(
                        YandexAiStudioContentPart.Text("hello"),
                        YandexAiStudioContentPart.ImageBase64(
                            mimeType = "image/png",
                            base64 = "ZmFrZQ==",
                        ),
                    ),
                    schemaName = "unused_schema_name",
                    schema = buildJsonObject {
                        put("type", "object")
                    },
                    promptId = "prompt-123",
                ),
            )

            assertTrue(result is YandexAiStudioStructuredResponse.Success)
            result as YandexAiStudioStructuredResponse.Success
            assertEquals("""{"title":"mouse"}""", result.responseBody)
            assertEquals(YandexAiStudioInvocationMode.RESPONSES, result.invocationMode)
            assertEquals("/responses", capturedPath)
            val requestJson = json.parseToJsonElement(assertNotNull(capturedBody)).jsonObject
            assertEquals("prompt-123", requestJson["prompt"]?.jsonObject?.get("id")?.jsonPrimitive?.content)
            assertEquals(
                "json_schema",
                requestJson["text"]
                    ?.jsonObject
                    ?.get("format")
                    ?.jsonObject
                    ?.get("type")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "input_text",
                requestJson["input"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.get("content")
                    ?.jsonArray
                    ?.get(0)
                    ?.jsonObject
                    ?.get("type")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "input_image",
                requestJson["input"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.get("content")
                    ?.jsonArray
                    ?.get(1)
                    ?.jsonObject
                    ?.get("type")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertTrue(
                requestJson["input"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.get("content")
                    ?.jsonArray
                    ?.get(1)
                    ?.jsonObject
                    ?.get("image_url")
                    ?.jsonPrimitive
                    ?.content
                    ?.startsWith("data:image/png;base64,") == true,
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun complete_structured_json_concatenates_responses_output_text_chunks() = runBlocking {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/responses") { exchange ->
            val response = """
                {
                  "status": "completed",
                  "usage": {
                    "input_tokens": 321,
                    "output_tokens": 45,
                    "total_tokens": 366
                  },
                  "output": [
                    {
                      "content": [
                        { "type": "output_text", "text": "{\"hypotheses\":[" },
                        { "type": "output_text", "text": "{\"rank\":1}]}"} 
                      ]
                    }
                  ]
                }
            """.trimIndent()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.toByteArray(Charsets.UTF_8).size.toLong())
            exchange.responseBody.use { body ->
                body.write(response.toByteArray(Charsets.UTF_8))
            }
        }
        server.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val client = DefaultYandexAiStudioClient()
            val result = client.completeStructuredJson(
                YandexAiStudioStructuredRequest(
                    transport = YandexAiStudioTransportConfig(
                        baseUrl = "$baseUrl/chat",
                        responsesBaseUrl = baseUrl,
                        apiKey = "token",
                        projectId = "folder-id",
                    ),
                    model = "gpt://folder-id/gemma-3-27b-it/latest",
                    timeoutMs = 5_000,
                    systemInstruction = "ignored in prompt mode",
                    contentParts = listOf(
                        YandexAiStudioContentPart.Text("hello"),
                    ),
                    schemaName = "unused_schema_name",
                    schema = buildJsonObject { put("type", "object") },
                    promptId = "prompt-123",
                ),
            )

            assertTrue(result is YandexAiStudioStructuredResponse.Success)
            result as YandexAiStudioStructuredResponse.Success
            assertEquals("""{"hypotheses":[{"rank":1}]}""", result.responseBody)
            assertEquals(YandexAiStudioInvocationMode.RESPONSES, result.invocationMode)
            assertEquals(321, result.usage?.inputTokens)
            assertEquals(45, result.usage?.outputTokens)
            assertEquals(366, result.usage?.totalTokens)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun complete_structured_json_retries_once_after_429_and_then_succeeds() = runBlocking {
        var attemptCount = 0
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/chat/completions") { exchange ->
            attemptCount += 1
            val response = if (attemptCount == 1) {
                exchange.responseHeaders.add("Retry-After", "0")
                """{"error":{"message":"rate limit"}}"""
            } else {
                """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\"category_code\":\"TECH.PHONES\"}"
                      }
                    }
                  ]
                }
                """.trimIndent()
            }
            val status = if (attemptCount == 1) 429 else 200
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, response.toByteArray(Charsets.UTF_8).size.toLong())
            exchange.responseBody.use { body ->
                body.write(response.toByteArray(Charsets.UTF_8))
            }
        }
        server.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val client = DefaultYandexAiStudioClient(
                retry429MaxAttempts = 1,
                retry5xxMaxAttempts = 0,
                sleepMillis = {},
            )
            val result = client.completeStructuredJson(
                YandexAiStudioStructuredRequest(
                    transport = YandexAiStudioTransportConfig(
                        baseUrl = baseUrl,
                        apiKey = "token",
                    ),
                    model = "gemini-2.5-flash-lite",
                    timeoutMs = 5_000,
                    systemInstruction = "return json",
                    contentParts = listOf(
                        YandexAiStudioContentPart.Text("hello"),
                    ),
                    schemaName = "unused_schema_name",
                    schema = buildJsonObject { put("type", "object") },
                ),
            )

            assertTrue(result is YandexAiStudioStructuredResponse.Success)
            result as YandexAiStudioStructuredResponse.Success
            assertEquals("""{"category_code":"TECH.PHONES"}""", result.responseBody)
            assertEquals(2, attemptCount)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun complete_structured_json_can_send_openai_gpt5_request_without_temperature() = runBlocking {
        var capturedBody: String? = null
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/chat/completions") { exchange ->
            capturedBody = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val response = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\"category_code\":\"TECH.PHONES\"}"
                      }
                    }
                  ]
                }
            """.trimIndent()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.toByteArray(Charsets.UTF_8).size.toLong())
            exchange.responseBody.use { body ->
                body.write(response.toByteArray(Charsets.UTF_8))
            }
        }
        server.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val client = DefaultYandexAiStudioClient()
            val result = client.completeStructuredJson(
                YandexAiStudioStructuredRequest(
                    transport = YandexAiStudioTransportConfig(
                        baseUrl = baseUrl,
                        apiKey = "openai-token",
                    ),
                    model = "gpt-5-mini",
                    timeoutMs = 5_000,
                    systemInstruction = "return json",
                    contentParts = listOf(
                        YandexAiStudioContentPart.Text("hello"),
                    ),
                    schemaName = "unused_schema_name",
                    schema = buildJsonObject { put("type", "object") },
                    temperature = null,
                    reasoningEffort = "minimal",
                    strictJsonSchema = false,
                ),
            )

            assertTrue(result is YandexAiStudioStructuredResponse.Success)
            val requestJson = json.parseToJsonElement(assertNotNull(capturedBody)).jsonObject
            assertEquals("gpt-5-mini", requestJson["model"]?.jsonPrimitive?.content)
            assertEquals("minimal", requestJson["reasoning_effort"]?.jsonPrimitive?.content)
            assertTrue(requestJson["temperature"] == null)
            assertEquals("json_schema", requestJson["response_format"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
            assertEquals(
                "false",
                requestJson["response_format"]
                    ?.jsonObject
                    ?.get("json_schema")
                    ?.jsonObject
                    ?.get("strict")
                    ?.jsonPrimitive
                    ?.content,
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun complete_structured_json_sends_openai_image_detail_when_configured() = runBlocking {
        var capturedBody: String? = null
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/chat/completions") { exchange ->
            capturedBody = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val response = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\"category_code\":\"TECH.PHONES\"}"
                      }
                    }
                  ]
                }
            """.trimIndent()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.toByteArray(Charsets.UTF_8).size.toLong())
            exchange.responseBody.use { body ->
                body.write(response.toByteArray(Charsets.UTF_8))
            }
        }
        server.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val client = DefaultYandexAiStudioClient()
            val result = client.completeStructuredJson(
                YandexAiStudioStructuredRequest(
                    transport = YandexAiStudioTransportConfig(
                        baseUrl = baseUrl,
                        apiKey = "openai-token",
                    ),
                    model = "gpt-5.4-mini",
                    timeoutMs = 5_000,
                    systemInstruction = "return json",
                    contentParts = listOf(
                        YandexAiStudioContentPart.ImageBase64(
                            mimeType = "image/jpeg",
                            base64 = "ZmFrZQ==",
                            imageDetail = "low",
                        ),
                    ),
                    schemaName = "unused_schema_name",
                    schema = buildJsonObject { put("type", "object") },
                    temperature = null,
                    reasoningEffort = null,
                    strictJsonSchema = false,
                ),
            )

            assertTrue(result is YandexAiStudioStructuredResponse.Success)
            val imageUrl = json.parseToJsonElement(assertNotNull(capturedBody))
                .jsonObject["messages"]
                ?.jsonArray
                ?.get(1)
                ?.jsonObject
                ?.get("content")
                ?.jsonArray
                ?.get(0)
                ?.jsonObject
                ?.get("image_url")
                ?.jsonObject
            assertEquals("low", imageUrl?.get("detail")?.jsonPrimitive?.content)
        } finally {
            server.stop(0)
        }
    }
}
