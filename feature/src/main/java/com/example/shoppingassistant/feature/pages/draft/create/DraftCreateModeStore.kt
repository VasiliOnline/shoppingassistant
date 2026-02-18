package com.example.shoppingassistant.feature.pages.draft.create

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class DraftCreateMode {
    PHOTO,
    MANUAL,
}

interface DraftCreateModeStore {
    suspend fun get(): DraftCreateMode?
    suspend fun set(mode: DraftCreateMode)
}

class DraftCreateModeStoreImpl(
    context: Context,
) : DraftCreateModeStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun get(): DraftCreateMode? = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY_MODE, null) ?: return@withContext null
        runCatching { DraftCreateMode.valueOf(raw) }.getOrNull()
    }

    override suspend fun set(mode: DraftCreateMode) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    private companion object {
        private const val PREFS_NAME = "draft_create_mode"
        private const val KEY_MODE = "draft.create.mode"
    }
}
