package com.example.shoppingassistant.domain.storage

/**
 * Доменный контракт хранения фото.
 */
interface PhotoStorageRepository {
    /**
     * Загружает фото и возвращает публичный URL.
     */
    suspend fun uploadPhoto(bytes: ByteArray, filename: String, contentType: String? = null): String
}

class UploadPhotoUseCase(
    private val repository: PhotoStorageRepository,
) {
    suspend operator fun invoke(bytes: ByteArray, filename: String, contentType: String? = null): String =
        repository.uploadPhoto(bytes, filename, contentType)
}
