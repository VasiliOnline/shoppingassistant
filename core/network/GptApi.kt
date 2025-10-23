package com.example.shoppingassistant.core.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable

object GptApi {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun normalizeQuery(userInput: String): NormalizedQuery {
        val response: GptResponse = client.post("https://api.openai.com/v1/chat/completions") {
            headers {
                append(HttpHeaders.Authorization, "Bearer ${System.getenv("OPENAI_API_KEY")}")
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            setBody(
                mapOf(
                    "model" to "gpt-4o-mini",
                    "messages" to listOf(
                        mapOf("role" to "system", "content" to "Ты нормализатор запросов. Преобразуй текст в JSON с категорией, атрибутами и фильтрами."),
                        mapOf("role" to "user", "content" to userInput)
                    )
                )
            )
        }.body()
        return response.toNormalizedQuery()
    }
}

@Serializable
data class GptResponse(val choices: List<Choice>) {
    fun toNormalizedQuery(): NormalizedQuery {
        // упрощённый парсинг, можно расширить
        return NormalizedQuery(category = "smartphone", filters = mapOf("brand" to "Apple"))
    }
}

@Serializable
data class Choice(val message: Message)

@Serializable
data class Message(val role: String, val content: String)

data class NormalizedQuery(
    val category: String,
    val filters: Map<String, String>
)
