package com.example.shoppingassistant.feature.pages.results

import android.net.Uri
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.OfferSort
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
}

@Serializable
data class ResultsPayload(
    val query: NormalizedQuery? = null,
    val queryText: String = "",
    val categoryCode: String? = null,
    val facetCollectionCode: String? = null,
    val facetPresetCode: String? = null,
    val querySessionId: String? = null,
    val location: String? = null,
    val radiusKm: Int? = null,
    val conditions: List<String> = emptyList(),
    val sort: OfferSort = OfferSort.RANK,
    val mode: ResultsMode = ResultsMode.Offers,
    val origin: ResultsOrigin = ResultsOrigin.Text,
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
