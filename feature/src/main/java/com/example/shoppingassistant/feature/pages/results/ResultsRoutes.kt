package com.example.shoppingassistant.feature.pages.results

import android.net.Uri
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.OfferSort
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBinderStatus
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBoundCandidate
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCandidateValue
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCaptureMode
import com.example.shoppingassistant.domain.visualsearch.VisualSearchChip
import com.example.shoppingassistant.domain.visualsearch.VisualSearchIntent
import com.example.shoppingassistant.domain.visualsearch.VisualSearchRouteKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.util.UUID

@Serializable
enum class ResultsMode {
    Offers,
    Categories,
}

@Serializable
enum class ResultsOrigin {
    Text,
    Suggestion,
    Category,
    Photo,
}

@Serializable
data class ResultsVisualContext(
    val visualSessionId: String? = null,
    val binderStatus: VisualSearchBinderStatus? = null,
    val routeKind: VisualSearchRouteKind? = null,
    val qualityApproved: Boolean = false,
    val captureMode: VisualSearchCaptureMode? = null,
    val intent: VisualSearchIntent? = null,
    val previewTitle: String? = null,
    val previewSubtitle: String? = null,
    val chips: List<VisualSearchChip> = emptyList(),
    val modelCandidates: List<VisualSearchCandidateValue> = emptyList(),
    val rankedCandidates: List<VisualSearchBoundCandidate> = emptyList(),
    val selectedCandidateRank: Int? = null,
    val exactRoute: Boolean = false,
    val reusableFingerprint: String? = null,
    val photoUris: List<String> = emptyList(),
)

@Serializable
data class ResultsPayload(
    val query: NormalizedQuery? = null,
    val queryText: String = "",
    val categoryCode: String? = null,
    val facetCollectionCode: String? = null,
    val facetPresetCode: String? = null,
    val sellerId: Long? = null,
    val sellerName: String? = null,
    val querySessionId: String? = null,
    val location: String? = null,
    val radiusKm: Int? = null,
    val conditions: List<String> = emptyList(),
    val sort: OfferSort = OfferSort.RANK,
    val mode: ResultsMode = ResultsMode.Offers,
    val origin: ResultsOrigin = ResultsOrigin.Text,
    val visualContext: ResultsVisualContext? = null,
)

object ResultsRoutes {
    const val Route = "results"
    const val ArgPayload = "payload"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun build(payload: ResultsPayload): String {
        val effectivePayload = if (payload.querySessionId.isNullOrBlank()) {
            payload.copy(querySessionId = "qs-${UUID.randomUUID()}")
        } else {
            payload
        }
        val raw = json.encodeToString(effectivePayload)
        return "$Route?$ArgPayload=${Uri.encode(raw)}"
    }

    fun parse(raw: String?): ResultsPayload {
        if (raw.isNullOrBlank()) return ResultsPayload()
        val decoded = Uri.decode(raw)
        return runCatching { json.decodeFromString<ResultsPayload>(decoded) }
            .getOrElse { ResultsPayload() }
    }
}
