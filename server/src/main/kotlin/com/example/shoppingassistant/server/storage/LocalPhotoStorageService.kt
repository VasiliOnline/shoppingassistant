package com.example.shoppingassistant.server.storage

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.UUID

/**
 * Простое файловое хранилище на локальном диске.
 */
class LocalPhotoStorageService(
    private val baseDir: Path = Path.of("uploads"),
) : PhotoStorageService {
    init {
        Files.createDirectories(baseDir)
    }

    override suspend fun save(bytes: ByteArray, filename: String, contentType: String?): String {
        val safeName = filename.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = baseDir.resolve("${Instant.now().epochSecond}_${UUID.randomUUID()}_$safeName")
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        // Возвращаем путь; отдача файлов на клиенте настраивается отдельно (static content/NGINX)
        return target.toAbsolutePath().toString()
    }
}
