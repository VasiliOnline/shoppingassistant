package com.example.shoppingassistant.domain.menu

import kotlinx.serialization.Serializable

@Serializable
enum class ModeKey {
    DASHBOARD,
    CHAT,
    SEARCH,
    SUBSCRIPTIONS,
    PROFILE,
}

@Serializable
enum class ActionKey {
    POST,
    MESSAGES,
    NOTIFICATIONS,
}

object UserPanelKeys {
    val supportedModesV1: List<ModeKey> = listOf(
        ModeKey.DASHBOARD,
        ModeKey.CHAT,
        ModeKey.PROFILE,
    )
    val supportedActionsV1: List<ActionKey> = listOf(
        ActionKey.POST,
    )
}

@Serializable
enum class Handedness {
    LEFT,
    RIGHT,
}

@Serializable
enum class PanelEdge {
    SIDE,
    BOTTOM,
}

@Serializable
data class PanelSlot<T>(
    val slotId: String,
    val key: T? = null,
    val enabled: Boolean = true,
)

@Serializable
data class UserPanel(
    val bottomModes: List<PanelSlot<ModeKey>> = emptyList(),
    val sideActions: List<PanelSlot<ActionKey>> = emptyList(),
    val handedness: Handedness = Handedness.RIGHT,
    val isEditMode: Boolean = false,
    val version: Int = CURRENT_VERSION,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

data class UserPanelConfig(
    val sideSlots: Int,
    val bottomSlots: Int,
    val supportedModes: List<ModeKey> = UserPanelKeys.supportedModesV1,
    val supportedActions: List<ActionKey> = UserPanelKeys.supportedActionsV1,
    val defaultModes: List<ModeKey> = supportedModes,
    val defaultActions: List<ActionKey> = supportedActions,
)

data class PanelSlotRef(
    val edge: PanelEdge,
    val index: Int,
)

data class PanelDropOutcome<T>(
    val panel: UserPanel,
    val removed: T? = null,
)
