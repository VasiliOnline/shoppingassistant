package com.example.shoppingassistant.core.usecase

import com.example.shoppingassistant.core.data.offers.OfferAlertsRemoteDataSource
import com.example.shoppingassistant.domain.auth.AuthRepository

/**
 * Use-case создания алерта на снижение цены оффера.
 * Возвращает типизированный результат, чтобы UI мог подсказать про авторизацию/ошибку.
 */
class CreateOfferPriceAlertUseCase(
    private val alertsRemote: OfferAlertsRemoteDataSource,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        offerId: String,
        percentDrop: Int,
        deliveryChannel: String? = null,
        minIntervalMinutes: Int? = null,
    ): OfferAlertResult {
        val token = authRepository.currentToken() ?: return OfferAlertResult.Unauthorized
        return runCatching {
            alertsRemote.createPriceDropAlert(
                offerId = offerId,
                percentDrop = percentDrop,
                deliveryChannel = deliveryChannel,
                minIntervalMinutes = minIntervalMinutes,
                bearerToken = token,
            )
            OfferAlertResult.Success
        }.getOrElse { throwable ->
            OfferAlertResult.Error(throwable.message ?: "Ошибка сети")
        }
    }
}

sealed class OfferAlertResult {
    data object Success : OfferAlertResult()
    data object Unauthorized : OfferAlertResult()
    data class Error(val reason: String) : OfferAlertResult()
}
