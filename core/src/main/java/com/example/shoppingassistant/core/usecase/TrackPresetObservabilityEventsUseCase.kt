package com.example.shoppingassistant.core.usecase

import com.example.shoppingassistant.core.data.offers.OfferRemoteDataSource
import com.example.shoppingassistant.domain.auth.AuthRepository
import com.example.shoppingassistant.domain.model.PresetObservabilityBatchRequest
import com.example.shoppingassistant.domain.model.PresetObservabilityBatchResponse
import com.example.shoppingassistant.domain.model.PresetObservabilityEvent

class TrackPresetObservabilityEventsUseCase(
    private val remote: OfferRemoteDataSource,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        events: List<PresetObservabilityEvent>,
    ): PresetObservabilityBatchResponse {
        if (events.isEmpty()) {
            return PresetObservabilityBatchResponse(
                acceptedCount = 0,
                dedupedCount = 0,
                rejectedCount = 0,
            )
        }
        val bearer = authRepository.currentToken()
        return remote.submitPresetObservabilityBatch(
            req = PresetObservabilityBatchRequest(events = events),
            bearerToken = bearer,
        )
    }
}
