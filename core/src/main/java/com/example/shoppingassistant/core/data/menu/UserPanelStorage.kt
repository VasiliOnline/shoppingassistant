package com.example.shoppingassistant.core.data.menu

import android.content.Context
import com.example.shoppingassistant.domain.menu.ActionKey
import com.example.shoppingassistant.domain.menu.Handedness
import com.example.shoppingassistant.domain.menu.ModeKey
import com.example.shoppingassistant.domain.menu.PanelSlot
import com.example.shoppingassistant.domain.menu.UserPanel
import com.example.shoppingassistant.domain.menu.UserPanelOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface UserPanelStorage {
    suspend fun get(): UserPanel
    suspend fun set(panel: UserPanel)
    suspend fun reset()
}

class UserPanelStorageImpl(
    context: Context,
) : UserPanelStorage {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun get(): UserPanel = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY_PANEL, null)
        val parsed = raw?.let {
            runCatching { json.decodeFromString(UserPanel.serializer(), it) }.getOrNull()
        }
        val selected = parsed?.takeIf { it.version == UserPanel.CURRENT_VERSION }
            ?: migrateLegacy(raw)
            ?: UserPanelDefaults.defaultPanel()
        UserPanelOps.normalize(selected, UserPanelDefaults.config)
    }

    override suspend fun set(panel: UserPanel) = withContext(Dispatchers.IO) {
        val normalized = UserPanelOps.normalize(panel, UserPanelDefaults.config)
        val payload = json.encodeToString(UserPanel.serializer(), normalized.copy(version = UserPanel.CURRENT_VERSION))
        prefs.edit().putString(KEY_PANEL, payload).apply()
    }

    override suspend fun reset() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_PANEL).apply()
    }

    private fun migrateLegacy(raw: String?): UserPanel? {
        if (raw.isNullOrBlank()) return null
        val legacy = runCatching { json.decodeFromString(LegacyMenuLayout.serializer(), raw) }.getOrNull()
            ?: return null
        return UserPanel(
            sideActions = (0 until UserPanelDefaults.config.sideSlots).map { index ->
                val key = legacy.right.getOrNull(index)?.toActionKey()
                PanelSlot(slotId = "side-$index", key = key)
            },
            bottomModes = (0 until UserPanelDefaults.config.bottomSlots).map { index ->
                val key = legacy.bottom.getOrNull(index)?.toModeKey()
                PanelSlot(slotId = "bottom-$index", key = key)
            },
            handedness = Handedness.RIGHT,
            isEditMode = false,
        )
    }

    private fun LegacyMenuActionId.toActionKey(): ActionKey? = when (this) {
        LegacyMenuActionId.POST -> ActionKey.POST
        LegacyMenuActionId.MESSAGES -> ActionKey.MESSAGES
        LegacyMenuActionId.NOTIFICATIONS -> ActionKey.NOTIFICATIONS
        else -> null
    }

    private fun LegacyMenuActionId.toModeKey(): ModeKey? = when (this) {
        LegacyMenuActionId.DASHBOARD -> ModeKey.DASHBOARD
        LegacyMenuActionId.SEARCH -> ModeKey.SEARCH
        LegacyMenuActionId.SUBSCRIPTIONS -> ModeKey.SUBSCRIPTIONS
        LegacyMenuActionId.PROFILE -> ModeKey.PROFILE
        else -> null
    }

    @Serializable
    private enum class LegacyMenuActionId {
        DASHBOARD,
        SEARCH,
        POST,
        PROFILE,
        MESSAGES,
        NOTIFICATIONS,
        SUBSCRIPTIONS,
    }

    @Serializable
    private data class LegacyMenuLayout(
        val right: List<LegacyMenuActionId?> = emptyList(),
        val bottom: List<LegacyMenuActionId?> = emptyList(),
        val version: Int = 1,
    )

    private companion object {
        private const val PREFS_NAME = "menu_layout.prefs"
        private const val KEY_PANEL = "menu.layout"
    }
}
