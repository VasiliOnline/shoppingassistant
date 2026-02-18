package com.example.shoppingassistant.server.storage

/**
 * Контракты backend-хранилища фото.
 */
interface PhotoStorageService {
    /**
     * Сохраняет фото и возвращает публичный URL или путь.
     */
    suspend fun save(bytes: ByteArray, filename: String, contentType: String? = null): String
}
