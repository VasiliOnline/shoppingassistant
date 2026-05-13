package com.example.shoppingassistant.feature.pages.localoffer

import android.content.Context
import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftIds
import com.example.shoppingassistant.domain.localoffer.LocalOfferFlowStep
import com.example.shoppingassistant.domain.localoffer.LocalOfferGeoConsentState
import com.example.shoppingassistant.domain.localoffer.LocalOfferGeoFreshnessState
import com.example.shoppingassistant.domain.localoffer.LocalOfferGeoSnapshot
import com.example.shoppingassistant.domain.localoffer.LocalOfferGeoStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class LocalOfferFlowSession(
    val sessionId: String,
    val correlationId: String,
    val draftId: String? = null,
    val geoSnapshot: LocalOfferGeoSnapshot? = null,
    val flowStep: LocalOfferFlowStep? = null,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

interface LocalOfferFlowSessionStore {
    suspend fun get(): LocalOfferFlowSession?
    suspend fun set(session: LocalOfferFlowSession)
    suspend fun clear()
}

class LocalOfferFlowSessionStoreImpl(
    context: Context,
) : LocalOfferFlowSessionStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun get(): LocalOfferFlowSession? = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY_SESSION, null) ?: return@withContext null
        runCatching {
            val payload = JSONObject(raw)
            val sessionId = payload.optString("sessionId").takeIf { it.isNotBlank() } ?: return@withContext null
            val correlationId = payload.optString("correlationId").takeIf { it.isNotBlank() } ?: return@withContext null
            LocalOfferFlowSession(
                sessionId = sessionId,
                correlationId = correlationId,
                draftId = LocalOfferDraftIds.migrateLegacyToCanonicalOrNull(
                    payload.optString("draftId").takeIf { it.isNotBlank() },
                ),
                geoSnapshot = payload.optJSONObject("geoSnapshot")?.toGeoSnapshot(),
                flowStep = payload.optFlowStep("flowStep")
                    ?: payload.optFlowStep("stepId"),
                updatedAtMillis = payload.optLong("updatedAtMillis").takeIf { it > 0L } ?: System.currentTimeMillis(),
            )
        }.getOrNull()
    }

    override suspend fun set(session: LocalOfferFlowSession) = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("sessionId", session.sessionId)
            put("correlationId", session.correlationId)
            put("draftId", session.draftId)
            put("flowStep", session.flowStep?.name)
            put("updatedAtMillis", session.updatedAtMillis)
            put("geoSnapshot", session.geoSnapshot?.toJson())
        }
        prefs.edit().putString(KEY_SESSION, payload.toString()).apply()
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    private fun JSONObject.toGeoSnapshot(): LocalOfferGeoSnapshot? {
        val geoSnapshotId = optString("geoSnapshotId").takeIf { it.isNotBlank() } ?: return null
        val sessionId = optString("sessionId").takeIf { it.isNotBlank() } ?: return null
        val consentState = optEnum<LocalOfferGeoConsentState>("consentState") ?: return null
        val status = optEnum<LocalOfferGeoStatus>("status") ?: return null
        val freshnessState = optEnum<LocalOfferGeoFreshnessState>("freshnessState")
            ?: LocalOfferGeoFreshnessState.EXPIRED
        val capturedAtMillis = optLong("capturedAtMillis").takeIf { it > 0L } ?: return null
        val expiresAtMillis = optLong("expiresAtMillis").takeIf { it > 0L } ?: capturedAtMillis
        val city = optString("city").takeIf { it.isNotBlank() } ?: return null
        val countryCode = optString("countryCode").takeIf { it.isNotBlank() } ?: return null
        return LocalOfferGeoSnapshot(
            geoSnapshotId = geoSnapshotId,
            sessionId = sessionId,
            draftId = optString("draftId").takeIf { it.isNotBlank() },
            consentState = consentState,
            status = status,
            capturedAtMillis = capturedAtMillis,
            expiresAtMillis = expiresAtMillis,
            freshnessState = freshnessState,
            accuracyMeters = optDoubleOrNull("accuracyMeters") ?: 0.0,
            countryCode = countryCode,
            adminArea = optString("adminArea").takeIf { it.isNotBlank() },
            city = city,
            lat = optDoubleOrNull("lat"),
            lon = optDoubleOrNull("lon"),
            source = optString("source").ifBlank { "unknown" },
        )
    }

    private inline fun <reified T : Enum<T>> JSONObject.optEnum(key: String): T? {
        val raw = optString(key).takeIf { it.isNotBlank() } ?: return null
        return runCatching { enumValueOf<T>(raw) }.getOrNull()
    }

    private fun JSONObject.optFlowStep(key: String): LocalOfferFlowStep? {
        val raw = optString(key).takeIf { it.isNotBlank() } ?: return null
        return runCatching { LocalOfferFlowStep.valueOf(raw) }.getOrNull()
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

    private fun LocalOfferGeoSnapshot.toJson(): JSONObject = JSONObject().apply {
        put("geoSnapshotId", geoSnapshotId)
        put("sessionId", sessionId)
        put("draftId", draftId)
        put("consentState", consentState.name)
        put("status", status.name)
        put("capturedAtMillis", capturedAtMillis)
        put("expiresAtMillis", expiresAtMillis)
        put("freshnessState", freshnessState.name)
        put("accuracyMeters", accuracyMeters)
        put("countryCode", countryCode)
        put("adminArea", adminArea)
        put("city", city)
        put("lat", lat)
        put("lon", lon)
        put("source", source)
    }

    private companion object {
        private const val PREFS_NAME = "local_offer_flow_session"
        private const val KEY_SESSION = "local.offer.flow.session"
    }
}
