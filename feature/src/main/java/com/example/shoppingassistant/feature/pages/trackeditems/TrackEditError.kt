package com.example.shoppingassistant.feature.pages.trackeditems

import com.example.shoppingassistant.domain.tracks.TrackId
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.TimeoutCancellationException

sealed interface TrackEditError {
    data object Offline : TrackEditError
    data object Timeout : TrackEditError
    data class ConflictDedup(val existingId: TrackId?) : TrackEditError
    data class Validation(val message: String) : TrackEditError
    data class Unknown(val message: String? = null) : TrackEditError
}

object TrackEditErrorMapper {
    fun fromThrowable(error: Throwable): TrackEditError = when (error) {
        is UnknownHostException -> TrackEditError.Offline
        is SocketTimeoutException, is TimeoutCancellationException -> TrackEditError.Timeout
        is IOException -> TrackEditError.Offline
        else -> TrackEditError.Unknown(error.message)
    }

    fun message(error: TrackEditError, fallback: String): String = when (error) {
        TrackEditError.Offline -> "Нет подключения к сети"
        TrackEditError.Timeout -> "Слишком долго без ответа"
        is TrackEditError.Validation -> error.message
        is TrackEditError.ConflictDedup -> "Такой трек уже существует"
        is TrackEditError.Unknown -> error.message ?: fallback
    }
}
