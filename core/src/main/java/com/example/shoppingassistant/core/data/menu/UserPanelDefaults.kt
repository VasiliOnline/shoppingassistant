package com.example.shoppingassistant.core.data.menu

import com.example.shoppingassistant.domain.menu.Handedness
import com.example.shoppingassistant.domain.menu.PanelSlot
import com.example.shoppingassistant.domain.menu.UserPanel
import com.example.shoppingassistant.domain.menu.UserPanelConfig
import com.example.shoppingassistant.domain.menu.UserPanelKeys

object UserPanelDefaults {
    val config = UserPanelConfig(
        sideSlots = 3,
        bottomSlots = 3,
        supportedModes = UserPanelKeys.supportedModesV1,
        supportedActions = UserPanelKeys.supportedActionsV1,
    )

    fun defaultPanel(): UserPanel = UserPanel(
        sideActions = (0 until config.sideSlots).map { index ->
            // Place default actions closer to the bottom corner by filling from the end.
            val startIndex = (config.sideSlots - config.defaultActions.size).coerceAtLeast(0)
            val key = config.defaultActions.getOrNull(index - startIndex)
            PanelSlot(slotId = "side-$index", key = key)
        },
        bottomModes = (0 until config.bottomSlots).map { index ->
            val key = config.defaultModes.getOrNull(index)
            PanelSlot(slotId = "bottom-$index", key = key)
        },
        handedness = Handedness.RIGHT,
        isEditMode = false,
    )
}
