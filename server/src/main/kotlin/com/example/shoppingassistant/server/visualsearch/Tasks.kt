package com.example.shoppingassistant.server.visualsearch

import com.example.shoppingassistant.domain.visualsearch.VisualSearchBindQueryRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBindQueryResponse
import com.example.shoppingassistant.domain.visualsearch.VisualSearchContextReuseRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchContextReuseResponse
import com.example.shoppingassistant.domain.visualsearch.VisualSearchEventBatchRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchEventBatchResponse
import com.example.shoppingassistant.domain.visualsearch.VisualSearchNormalizeDraftRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchNormalizeDraftResponse
import com.example.shoppingassistant.domain.visualsearch.VisualSearchRecoveryPlanRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchRecoveryPlanResponse
import com.example.shoppingassistant.domain.visualsearch.VisualSearchTransportMetadata

interface VisualSearchService {
    suspend fun reuseContext(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchContextReuseRequest,
    ): VisualSearchContextReuseResponse

    suspend fun normalizeDraft(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchNormalizeDraftRequest,
    ): VisualSearchNormalizeDraftResponse

    suspend fun bindQuery(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchBindQueryRequest,
    ): VisualSearchBindQueryResponse

    suspend fun recoveryPlan(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchRecoveryPlanRequest,
    ): VisualSearchRecoveryPlanResponse

    suspend fun ingestEvents(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchEventBatchRequest,
    ): VisualSearchEventBatchResponse
}
