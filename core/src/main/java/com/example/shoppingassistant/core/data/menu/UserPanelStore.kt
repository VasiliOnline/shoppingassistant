package com.example.shoppingassistant.core.data.menu

import com.example.shoppingassistant.domain.menu.UserPanel
import com.example.shoppingassistant.domain.menu.UserPanelOps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPanelStore(
    private val storage: UserPanelStorage,
) {
    private val _panel = MutableStateFlow(UserPanelDefaults.defaultPanel())
    val panel: StateFlow<UserPanel> = _panel.asStateFlow()

    suspend fun load() {
        _panel.value = storage.get()
    }

    suspend fun update(panel: UserPanel) {
        val normalized = UserPanelOps.normalize(panel, UserPanelDefaults.config)
        storage.set(normalized)
        _panel.value = normalized
    }

    suspend fun reset() {
        storage.reset()
        val defaults = UserPanelDefaults.defaultPanel()
        _panel.value = UserPanelOps.normalize(defaults, UserPanelDefaults.config)
    }
}
