package com.example.shoppingassistant.feature.pages.draft

import android.net.Uri
import com.example.shoppingassistant.domain.model.NormalizedQuery
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class DraftSource {
    Photo,
    Voice,
    Link,
    Text,
}

@Serializable
enum class DraftMode {
    Create,
    Search,
}

@Serializable
enum class DraftPhotoInput {
    None,
    Camera,
    Gallery,
}

@Serializable
data class DraftPayload(
    val source: DraftSource,
    val mode: DraftMode = DraftMode.Create,
    val photoInput: DraftPhotoInput = DraftPhotoInput.None,
    val linkUrl: String = "",
    val query: NormalizedQuery? = null,
    val queryText: String = "",
    val categoryCode: String? = null,
)

object DraftRoutes {
    const val Route = "draft"
    const val ArgPayload = "payload"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun build(payload: DraftPayload): String {
        val raw = json.encodeToString(payload)
        return "$Route?$ArgPayload=${Uri.encode(raw)}"
    }

    fun parse(raw: String?): DraftPayload? {
        if (raw.isNullOrBlank()) return null
        val decoded = Uri.decode(raw)
        return runCatching { json.decodeFromString<DraftPayload>(decoded) }
            .getOrNull()
    }
}
