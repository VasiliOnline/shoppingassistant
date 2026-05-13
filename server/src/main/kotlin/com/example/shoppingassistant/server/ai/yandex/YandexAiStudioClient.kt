package com.example.shoppingassistant.server.ai.yandex

import com.example.shoppingassistant.server.ai.AiTokenUsage
import java.net.URI
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.Base64
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class YandexAiStudioTransportConfig(
    val baseUrl: String,
    val responsesBaseUrl: String? = null,
    val apiKey: String,
    val projectId: String? = null,
)

sealed interface YandexAiStudioContentPart {
    data class Text(
        val text: String,
    ) : YandexAiStudioContentPart

    data class ImageBase64(
        val mimeType: String,
        val base64: String,
        val focusRegion: YandexAiStudioFocusRegion? = null,
        val imageDetail: String? = null,
        val preprocessMaxSidePx: Int? = null,
        val preprocessJpegQuality: Float? = null,
    ) : YandexAiStudioContentPart
}

data class YandexAiStudioFocusRegion(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

data class YandexAiStudioStructuredRequest(
    val transport: YandexAiStudioTransportConfig,
    val model: String,
    val timeoutMs: Long,
    val systemInstruction: String,
    val contentParts: List<YandexAiStudioContentPart>,
    val schemaName: String,
    val schema: JsonObject,
    val promptId: String? = null,
    val temperature: Double? = 0.1,
    val maxCompletionTokens: Int = 900,
    val reasoningEffort: String? = null,
    val strictJsonSchema: Boolean = true,
)

enum class YandexAiStudioInvocationMode {
    CHAT_COMPLETIONS,
    RESPONSES,
}

enum class YandexAiStudioFailureCode {
    INVALID_REQUEST,
    TIMEOUT,
    NETWORK,
    AUTH,
    RATE_LIMITED,
    UPSTREAM_4XX,
    UPSTREAM_5XX,
    MALFORMED_RESPONSE,
    EMPTY_OUTPUT,
}

sealed interface YandexAiStudioStructuredResponse {
    data class Success(
        val responseBody: String,
        val httpStatus: Int,
        val invocationMode: YandexAiStudioInvocationMode,
        val usage: AiTokenUsage? = null,
    ) : YandexAiStudioStructuredResponse

    data class Failure(
        val code: YandexAiStudioFailureCode,
        val httpStatus: Int? = null,
        val message: String? = null,
        val invocationMode: YandexAiStudioInvocationMode,
        val usage: AiTokenUsage? = null,
    ) : YandexAiStudioStructuredResponse
}

interface YandexAiStudioClient {
    suspend fun completeStructuredJson(request: YandexAiStudioStructuredRequest): YandexAiStudioStructuredResponse
}

class DefaultYandexAiStudioClient(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(12_000L))
        .build(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    },
    private val retry429MaxAttempts: Int = 1,
    private val retry5xxMaxAttempts: Int = 1,
    private val retryBaseDelayMs: Long = 800L,
    private val retryMaxDelayMs: Long = 4_000L,
    private val sleepMillis: (Long) -> Unit = { delayMs -> Thread.sleep(delayMs) },
) : YandexAiStudioClient {

    override suspend fun completeStructuredJson(request: YandexAiStudioStructuredRequest): YandexAiStudioStructuredResponse {
        if (request.transport.apiKey.isBlank()) {
            return YandexAiStudioStructuredResponse.Failure(
                code = YandexAiStudioFailureCode.INVALID_REQUEST,
                message = "Missing API key.",
                invocationMode = invocationMode(request),
            )
        }
        if (request.model.isBlank()) {
            return YandexAiStudioStructuredResponse.Failure(
                code = YandexAiStudioFailureCode.INVALID_REQUEST,
                message = "Missing model.",
                invocationMode = invocationMode(request),
            )
        }
        if (request.contentParts.isEmpty()) {
            return YandexAiStudioStructuredResponse.Failure(
                code = YandexAiStudioFailureCode.INVALID_REQUEST,
                message = "Missing content parts.",
                invocationMode = invocationMode(request),
            )
        }

        return if (!request.promptId.isNullOrBlank()) {
            completeViaResponses(request)
        } else {
            completeViaChatCompletions(request)
        }
    }

    private suspend fun completeViaChatCompletions(request: YandexAiStudioStructuredRequest): YandexAiStudioStructuredResponse {
        val body = buildChatCompletionsRequestBody(request)
        val httpResponse = sendRequest(
            request = request,
            url = "${request.transport.baseUrl.trimEnd('/')}/chat/completions",
            body = body,
            invocationMode = YandexAiStudioInvocationMode.CHAT_COMPLETIONS,
        )
        if (httpResponse is YandexAiStudioStructuredResponse.Failure) return httpResponse
        val successResponse = httpResponse as YandexAiStudioStructuredResponse.Success

        val completion = runCatching {
            json.decodeFromString(YandexAiStudioChatCompletionResponse.serializer(), successResponse.responseBody)
        }.getOrElse { error ->
            return YandexAiStudioStructuredResponse.Failure(
                code = YandexAiStudioFailureCode.MALFORMED_RESPONSE,
                httpStatus = successResponse.httpStatus,
                message = error.message,
                invocationMode = YandexAiStudioInvocationMode.CHAT_COMPLETIONS,
            )
        }
        val responseBody = extractMessageContent(completion)
        return if (responseBody == null) {
            YandexAiStudioStructuredResponse.Failure(
                code = YandexAiStudioFailureCode.EMPTY_OUTPUT,
                httpStatus = successResponse.httpStatus,
                message = successResponse.responseBody,
                invocationMode = YandexAiStudioInvocationMode.CHAT_COMPLETIONS,
                usage = successResponse.usage,
            )
        } else {
            YandexAiStudioStructuredResponse.Success(
                responseBody = responseBody,
                httpStatus = successResponse.httpStatus,
                invocationMode = YandexAiStudioInvocationMode.CHAT_COMPLETIONS,
                usage = successResponse.usage,
            )
        }
    }

    private suspend fun completeViaResponses(request: YandexAiStudioStructuredRequest): YandexAiStudioStructuredResponse {
        val body = buildResponsesRequestBody(request)
        val baseUrl = request.transport.responsesBaseUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: request.transport.baseUrl
        val httpResponse = sendRequest(
            request = request,
            url = "${baseUrl.trimEnd('/')}/responses",
            body = body,
            invocationMode = YandexAiStudioInvocationMode.RESPONSES,
        )
        if (httpResponse is YandexAiStudioStructuredResponse.Failure) return httpResponse
        val successResponse = httpResponse as YandexAiStudioStructuredResponse.Success

        val responseBody = extractResponsesText(successResponse.responseBody)
        return if (responseBody == null) {
            YandexAiStudioStructuredResponse.Failure(
                code = YandexAiStudioFailureCode.EMPTY_OUTPUT,
                httpStatus = successResponse.httpStatus,
                message = successResponse.responseBody,
                invocationMode = YandexAiStudioInvocationMode.RESPONSES,
                usage = successResponse.usage,
            )
        } else {
            YandexAiStudioStructuredResponse.Success(
                responseBody = responseBody,
                httpStatus = successResponse.httpStatus,
                invocationMode = YandexAiStudioInvocationMode.RESPONSES,
                usage = successResponse.usage,
            )
        }
    }

    private suspend fun sendRequest(
        request: YandexAiStudioStructuredRequest,
        url: String,
        body: String,
        invocationMode: YandexAiStudioInvocationMode,
    ): YandexAiStudioStructuredResponse = withContext(Dispatchers.IO) {
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMillis(request.timeoutMs))
            .header("Authorization", "Bearer ${request.transport.apiKey}")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .apply {
                request.transport.projectId
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { header("OpenAI-Project", it) }
            }
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        var rateLimitAttempt = 0
        var upstream5xxAttempt = 0
        while (true) {
            val response = try {
                httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString(Charsets.UTF_8),
                )
            } catch (error: HttpTimeoutException) {
                return@withContext YandexAiStudioStructuredResponse.Failure(
                    code = YandexAiStudioFailureCode.TIMEOUT,
                    message = error.message,
                    invocationMode = invocationMode,
                )
            } catch (error: HttpConnectTimeoutException) {
                return@withContext YandexAiStudioStructuredResponse.Failure(
                    code = YandexAiStudioFailureCode.TIMEOUT,
                    message = error.message,
                    invocationMode = invocationMode,
                )
            } catch (error: Exception) {
                return@withContext YandexAiStudioStructuredResponse.Failure(
                    code = YandexAiStudioFailureCode.NETWORK,
                    message = error.message,
                    invocationMode = invocationMode,
                )
            }
            val statusCode = response.statusCode()
            val responseBody = response.body()
            if (statusCode in 200..299) {
                return@withContext YandexAiStudioStructuredResponse.Success(
                    responseBody = responseBody,
                    httpStatus = statusCode,
                    invocationMode = invocationMode,
                    usage = extractTokenUsage(responseBody),
                )
            }
            if (statusCode == 429 && rateLimitAttempt < retry429MaxAttempts) {
                val delayMs = computeRetryDelayMs(
                    attemptIndex = rateLimitAttempt,
                    retryAfterHeader = response.headers().firstValue("Retry-After").orElse(null),
                )
                rateLimitAttempt += 1
                sleepMillis(delayMs)
                continue
            }
            if (statusCode >= 500 && upstream5xxAttempt < retry5xxMaxAttempts) {
                val delayMs = computeRetryDelayMs(
                    attemptIndex = upstream5xxAttempt,
                    retryAfterHeader = response.headers().firstValue("Retry-After").orElse(null),
                )
                upstream5xxAttempt += 1
                sleepMillis(delayMs)
                continue
            }
            return@withContext YandexAiStudioStructuredResponse.Failure(
                code = mapFailureCode(statusCode),
                httpStatus = statusCode,
                message = responseBody,
                invocationMode = invocationMode,
                usage = extractTokenUsage(responseBody),
            )
        }
        error("Unreachable retry loop exit")
    }

    private fun mapFailureCode(statusCode: Int): YandexAiStudioFailureCode = when {
        statusCode == 401 || statusCode == 403 -> YandexAiStudioFailureCode.AUTH
        statusCode == 429 -> YandexAiStudioFailureCode.RATE_LIMITED
        statusCode in 400..499 -> YandexAiStudioFailureCode.UPSTREAM_4XX
        statusCode >= 500 -> YandexAiStudioFailureCode.UPSTREAM_5XX
        else -> YandexAiStudioFailureCode.NETWORK
    }

    private fun computeRetryDelayMs(
        attemptIndex: Int,
        retryAfterHeader: String?,
    ): Long {
        parseRetryAfterMs(retryAfterHeader)?.let { retryAfterMs ->
            return retryAfterMs.coerceIn(0L, retryMaxDelayMs)
        }
        val exponentialWindowMs = min(
            retryMaxDelayMs,
            retryBaseDelayMs * (1L shl attemptIndex.coerceIn(0, 8)),
        )
        return Random.nextLong(
            from = 0L,
            until = exponentialWindowMs.coerceAtLeast(1L) + 1L,
        )
    }

    private fun parseRetryAfterMs(retryAfterHeader: String?): Long? =
        retryAfterHeader
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.toLongOrNull()
            ?.let { seconds -> seconds.coerceAtLeast(0L) * 1_000L }

    private fun invocationMode(request: YandexAiStudioStructuredRequest): YandexAiStudioInvocationMode =
        if (!request.promptId.isNullOrBlank()) {
            YandexAiStudioInvocationMode.RESPONSES
        } else {
            YandexAiStudioInvocationMode.CHAT_COMPLETIONS
        }

    private fun buildChatCompletionsRequestBody(request: YandexAiStudioStructuredRequest): String {
        val normalizedContentParts = normalizeContentParts(request.contentParts)
        val payload = buildJsonObject {
            put("model", request.model)
            request.temperature?.let { temperature -> put("temperature", temperature) }
            request.reasoningEffort
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { effort -> put("reasoning_effort", effort) }
            put("max_completion_tokens", request.maxCompletionTokens)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put("content", request.systemInstruction)
                        },
                    )
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put(
                                "content",
                                buildJsonArray {
                                    normalizedContentParts.forEach { part ->
                                        when (part) {
                                            is YandexAiStudioContentPart.Text -> add(
                                                buildJsonObject {
                                                    put("type", "text")
                                                    put("text", part.text)
                                                },
                                            )

                                            is YandexAiStudioContentPart.ImageBase64 -> add(
                                                buildJsonObject {
                                                    put("type", "image_url")
                                                    put(
                                                        "image_url",
                                                        buildJsonObject {
                                                            put("url", "data:${part.mimeType};base64,${part.base64}")
                                                            part.imageDetail
                                                                ?.trim()
                                                                ?.takeIf { it.isNotEmpty() }
                                                                ?.let { detail -> put("detail", detail) }
                                                        },
                                                    )
                                                },
                                            )
                                        }
                                    }
                                },
                            )
                        },
                    )
                },
            )
            put(
                "response_format",
                buildJsonObject {
                    put("type", "json_schema")
                    put(
                        "json_schema",
                        buildJsonObject {
                            put("name", request.schemaName)
                            put("strict", request.strictJsonSchema)
                            put("schema", request.schema)
                        },
                    )
                },
            )
        }
        return json.encodeToString(JsonObject.serializer(), payload)
    }

    private fun buildResponsesRequestBody(request: YandexAiStudioStructuredRequest): String {
        val normalizedContentParts = normalizeContentParts(request.contentParts)
        val payload = buildJsonObject {
            put("model", request.model)
            request.temperature?.let { temperature -> put("temperature", temperature) }
            put("max_output_tokens", request.maxCompletionTokens)
            put(
                "text",
                buildJsonObject {
                    put(
                        "format",
                        buildJsonObject {
                            put("type", "json_schema")
                            put("name", request.schemaName)
                            put("strict", request.strictJsonSchema)
                            put("schema", request.schema)
                        },
                    )
                },
            )
            put(
                "prompt",
                buildJsonObject {
                    put("id", request.promptId)
                },
            )
            put(
                "input",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put(
                                "content",
                                buildJsonArray {
                                    normalizedContentParts.forEach { part ->
                                        when (part) {
                                            is YandexAiStudioContentPart.Text -> add(
                                                buildJsonObject {
                                                    put("type", "input_text")
                                                    put("text", part.text)
                                                },
                                            )

                                            is YandexAiStudioContentPart.ImageBase64 -> add(
                                                buildJsonObject {
                                                    put("type", "input_image")
                                                    put("image_url", "data:${part.mimeType};base64,${part.base64}")
                                                    part.imageDetail
                                                        ?.trim()
                                                        ?.takeIf { it.isNotEmpty() }
                                                        ?.let { detail -> put("detail", detail) }
                                                },
                                            )
                                        }
                                    }
                                },
                            )
                        },
                    )
                },
            )
        }
        return json.encodeToString(JsonObject.serializer(), payload)
    }

    private fun normalizeContentParts(
        contentParts: List<YandexAiStudioContentPart>,
    ): List<YandexAiStudioContentPart> = contentParts.map { part ->
        when (part) {
            is YandexAiStudioContentPart.Text -> part
            is YandexAiStudioContentPart.ImageBase64 -> YandexAiStudioImagePreprocessor.preprocess(part)
        }
    }

    private fun extractMessageContent(response: YandexAiStudioChatCompletionResponse): String? {
        val content = response.choices.firstOrNull()?.message?.content ?: return null
        return when (content) {
            is JsonPrimitive -> content.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            is JsonArray -> content.firstTextContent()
            else -> null
        }
    }

    private fun extractResponsesText(responseBody: String): String? {
        val root = runCatching {
            json.parseToJsonElement(responseBody) as? JsonObject
        }.getOrNull() ?: return null
        val status = root["status"]?.jsonPrimitive?.contentOrNull?.trim()
        if (status != null && status.equals("failed", ignoreCase = true)) return null
        val output = root["output"] as? JsonArray ?: return null
        val chunks = output.flatMap { item ->
            val message = item as? JsonObject ?: return@flatMap emptyList()
            val content = message["content"] as? JsonArray ?: return@flatMap emptyList()
            content.mapNotNull { part ->
                val obj = part as? JsonObject ?: return@mapNotNull null
                val type = obj["type"]?.jsonPrimitive?.contentOrNull
                if (type != "output_text") return@mapNotNull null
                obj["text"]?.jsonPrimitive?.contentOrNull
            }
        }
        if (chunks.isEmpty()) return null
        return chunks.joinToString(separator = "").trim().takeIf { it.isNotEmpty() }
    }

    private fun extractTokenUsage(responseBody: String): AiTokenUsage? {
        val root = runCatching {
            json.parseToJsonElement(responseBody) as? JsonObject
        }.getOrNull() ?: return null
        val usage = root["usage"] as? JsonObject ?: return null
        val inputTokens = usage["input_tokens"]?.jsonPrimitive?.longOrNull
            ?: usage["prompt_tokens"]?.jsonPrimitive?.longOrNull
        val outputTokens = usage["output_tokens"]?.jsonPrimitive?.longOrNull
            ?: usage["completion_tokens"]?.jsonPrimitive?.longOrNull
        val totalTokens = usage["total_tokens"]?.jsonPrimitive?.longOrNull
        return AiTokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
        ).takeUnless { it.isEmpty }
    }

    private fun JsonArray.firstTextContent(): String? =
        firstNotNullOfOrNull { element ->
            val obj = element as? JsonObject ?: return@firstNotNullOfOrNull null
            obj["text"]?.jsonPrimitive?.contentOrNull
                ?: obj["content"]?.jsonPrimitive?.contentOrNull
        }?.trim()?.takeIf { it.isNotEmpty() }
}

@Serializable
private data class YandexAiStudioChatCompletionResponse(
    val choices: List<YandexAiStudioChatChoice> = emptyList(),
)

@Serializable
private data class YandexAiStudioChatChoice(
    val message: YandexAiStudioChatMessage? = null,
)

@Serializable
private data class YandexAiStudioChatMessage(
    val content: JsonElement? = null,
)
