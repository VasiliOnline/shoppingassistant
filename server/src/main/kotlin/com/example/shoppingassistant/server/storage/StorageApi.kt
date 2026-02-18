package com.example.shoppingassistant.server.storage

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import java.util.Base64
import org.koin.java.KoinJavaComponent

@Serializable
private data class UploadRequest(
    val filename: String,
    val contentType: String? = null,
    val dataBase64: String,
)

@Serializable
private data class UploadResponse(
    val url: String,
)

fun Route.storageRoutes() {
    post("/api/storage/upload") {
        val request = runCatching { call.receive<UploadRequest>() }.getOrElse {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = mapOf("error" to "BAD_REQUEST", "message" to "Некорректное тело запроса"),
            )
            return@post
        }

        val bytes = runCatching { Base64.getDecoder().decode(request.dataBase64) }.getOrNull()
        if (bytes == null) {
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = mapOf("error" to "INVALID_DATA", "message" to "dataBase64 не декодируется"),
            )
            return@post
        }

        val storage: PhotoStorageService = KoinJavaComponent.get(PhotoStorageService::class.java)
        val url = runCatching {
            storage.save(bytes, request.filename, request.contentType)
        }.getOrElse {
            call.respond(
                status = HttpStatusCode.InternalServerError,
                message = mapOf("error" to "UPLOAD_FAILED", "message" to (it.message ?: "Не удалось сохранить файл")),
            )
            return@post
        }

        call.respond(UploadResponse(url = url))
    }
}
