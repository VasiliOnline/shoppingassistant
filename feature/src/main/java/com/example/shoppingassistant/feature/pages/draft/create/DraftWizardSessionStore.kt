package com.example.shoppingassistant.feature.pages.draft.create

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class DraftWizardSession(
    val draftId: String,
    val stepId: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

interface DraftWizardSessionStore {
    suspend fun get(): DraftWizardSession?
    suspend fun set(session: DraftWizardSession)
    suspend fun clear()
}

class DraftWizardSessionStoreImpl(
    context: Context,
) : DraftWizardSessionStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun get(): DraftWizardSession? = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY_SESSION, null) ?: return@withContext null
        runCatching {
            val obj = JSONObject(raw)
            val id = obj.optString("draftId").takeIf { it.isNotBlank() } ?: return@withContext null
            DraftWizardSession(
                draftId = id,
                stepId = obj.optString("stepId").takeIf { it.isNotBlank() },
                updatedAtMillis = obj.optLong("updatedAtMillis").takeIf { it > 0L } ?: System.currentTimeMillis(),
            )
        }.getOrNull()
    }

    override suspend fun set(session: DraftWizardSession) = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("draftId", session.draftId)
            put("stepId", session.stepId)
            put("updatedAtMillis", session.updatedAtMillis)
        }
        prefs.edit().putString(KEY_SESSION, payload.toString()).apply()
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    private companion object {
        private const val PREFS_NAME = "draft_wizard_session"
        private const val KEY_SESSION = "draft.wizard.session"
    }
}
