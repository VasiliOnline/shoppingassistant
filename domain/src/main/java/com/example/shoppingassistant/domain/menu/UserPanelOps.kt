package com.example.shoppingassistant.domain.menu

object UserPanelOps {
    private const val MIN_BOTTOM = 2
    private const val MIN_SIDE = 1

    fun normalize(panel: UserPanel, config: UserPanelConfig): UserPanel {
        val migratedSide = panel.sideActions.size < config.sideSlots
        val side = normalizeSlots(panel.sideActions, config.sideSlots, PanelEdge.SIDE)
        val bottom = normalizeSlots(panel.bottomModes, config.bottomSlots, PanelEdge.BOTTOM)
        val sanitizedSide = clearUnsupported(side, config.supportedActions.toSet())
        val sanitizedBottom = clearUnsupported(bottom, config.supportedModes.toSet())
        val base = panel.copy(
            sideActions = dedupe(sanitizedSide),
            bottomModes = dedupe(sanitizedBottom),
        )
        val positioned = if (migratedSide) shiftSingleSideActionToEnd(base) else base
        val filled = fillMinimums(positioned, config)
        return filled.copy(
            handedness = panel.handedness,
            isEditMode = panel.isEditMode,
        )
    }

    fun applyModeDrop(
        panel: UserPanel,
        modeKey: ModeKey,
        to: PanelSlotRef?,
    ): PanelDropOutcome<ModeKey> {
        if (to != null && to.edge != PanelEdge.BOTTOM) {
            return PanelDropOutcome(panel)
        }
        if (to != null && to.index !in panel.bottomModes.indices) {
            return PanelDropOutcome(panel)
        }
        if (to == null) {
            return clearMode(panel, modeKey)
        }

        val existing = findModeSlot(panel, modeKey)
        val targetKey = panel.bottomModes[to.index].key
        val updated = setModeAt(panel, to.index, modeKey)
        val cleared = if (existing != null && existing.index != to.index) {
            setModeAt(updated, existing.index, null)
        } else {
            updated
        }
        return if (targetKey == null) {
            PanelDropOutcome(cleared)
        } else {
            PanelDropOutcome(cleared, removed = targetKey)
        }
    }

    fun applyActionDrop(
        panel: UserPanel,
        actionKey: ActionKey,
        to: PanelSlotRef?,
    ): PanelDropOutcome<ActionKey> {
        if (to != null && to.edge != PanelEdge.SIDE) {
            return PanelDropOutcome(panel)
        }
        if (to != null && to.index !in panel.sideActions.indices) {
            return PanelDropOutcome(panel)
        }
        if (to == null) {
            return clearAction(panel, actionKey)
        }

        val existing = findActionSlot(panel, actionKey)
        val targetKey = panel.sideActions[to.index].key
        val updated = setActionAt(panel, to.index, actionKey)
        val cleared = if (existing != null && existing.index != to.index) {
            setActionAt(updated, existing.index, null)
        } else {
            updated
        }
        return if (targetKey == null) {
            PanelDropOutcome(cleared)
        } else {
            PanelDropOutcome(cleared, removed = targetKey)
        }
    }

    private fun clearMode(panel: UserPanel, key: ModeKey): PanelDropOutcome<ModeKey> {
        val slot = findModeSlot(panel, key) ?: return PanelDropOutcome(panel)
        val updated = setModeAt(panel, slot.index, null)
        return PanelDropOutcome(updated, removed = key)
    }

    private fun clearAction(panel: UserPanel, key: ActionKey): PanelDropOutcome<ActionKey> {
        val slot = findActionSlot(panel, key) ?: return PanelDropOutcome(panel)
        val updated = setActionAt(panel, slot.index, null)
        return PanelDropOutcome(updated, removed = key)
    }

    private fun <T> normalizeSlots(
        slots: List<PanelSlot<T>>,
        count: Int,
        edge: PanelEdge,
    ): List<PanelSlot<T>> {
        if (count <= 0) return emptyList()
        val normalized = ArrayList<PanelSlot<T>>(count)
        for (index in 0 until count) {
            val existing = slots.getOrNull(index)
            val slotId = slotId(edge, index)
            if (existing == null) {
                normalized += PanelSlot(slotId = slotId)
            } else {
                normalized += existing.copy(slotId = slotId)
            }
        }
        return normalized
    }

    private fun fillMinimums(panel: UserPanel, config: UserPanelConfig): UserPanel {
        if (config.sideSlots == 0 && config.bottomSlots == 0) return panel
        val usedModes = HashSet<ModeKey>()
        val usedActions = HashSet<ActionKey>()
        panel.bottomModes.mapNotNull { it.key }.forEach { usedModes.add(it) }
        panel.sideActions.mapNotNull { it.key }.forEach { usedActions.add(it) }

        val minSide = minSlots(config.sideSlots, MIN_SIDE)
        val minBottom = minSlots(config.bottomSlots, MIN_BOTTOM)

        val supportedActions = config.supportedActions.toSet()
        val supportedModes = config.supportedModes.toSet()
        val actionDefaults = filterDefaults(config.defaultActions, supportedActions)
        val modeDefaults = filterDefaults(config.defaultModes, supportedModes)
        val side = fillEdge(panel.sideActions.toMutableList(), actionDefaults, usedActions, minSide)
        val bottom = fillEdge(panel.bottomModes.toMutableList(), modeDefaults, usedModes, minBottom)

        return panel.copy(sideActions = side, bottomModes = bottom)
    }

    private fun <T> fillEdge(
        items: MutableList<PanelSlot<T>>,
        defaults: List<T>,
        used: MutableSet<T>,
        minCount: Int,
    ): List<PanelSlot<T>> {
        var count = items.count { it.key != null }
        if (count >= minCount) return items
        defaults.forEach { key ->
            if (count >= minCount) return@forEach
            if (used.contains(key)) return@forEach
            val emptyIndex = items.indexOfFirst { it.key == null }
            if (emptyIndex < 0) return@forEach
            items[emptyIndex] = items[emptyIndex].copy(key = key)
            used.add(key)
            count += 1
        }
        return items
    }

    private fun minSlots(slots: Int, minRequired: Int): Int {
        if (slots <= 0) return 0
        return if (slots < minRequired) slots else minRequired
    }

    private fun <T> dedupe(slots: List<PanelSlot<T>>): List<PanelSlot<T>> {
        val seen = HashSet<T>()
        return slots.map { slot ->
            val key = slot.key ?: return@map slot
            if (seen.add(key)) slot else slot.copy(key = null)
        }
    }

    private fun slotId(edge: PanelEdge, index: Int): String =
        if (edge == PanelEdge.SIDE) "side-$index" else "bottom-$index"

    private fun findModeSlot(panel: UserPanel, key: ModeKey): PanelSlotRef? {
        val index = panel.bottomModes.indexOfFirst { it.key == key }
        return if (index >= 0) PanelSlotRef(PanelEdge.BOTTOM, index) else null
    }

    private fun findActionSlot(panel: UserPanel, key: ActionKey): PanelSlotRef? {
        val index = panel.sideActions.indexOfFirst { it.key == key }
        return if (index >= 0) PanelSlotRef(PanelEdge.SIDE, index) else null
    }

    private fun setModeAt(panel: UserPanel, index: Int, key: ModeKey?): UserPanel {
        if (index !in panel.bottomModes.indices) return panel
        val updated = panel.bottomModes.toMutableList()
        updated[index] = updated[index].copy(key = key)
        return panel.copy(bottomModes = updated)
    }

    private fun setActionAt(panel: UserPanel, index: Int, key: ActionKey?): UserPanel {
        if (index !in panel.sideActions.indices) return panel
        val updated = panel.sideActions.toMutableList()
        updated[index] = updated[index].copy(key = key)
        return panel.copy(sideActions = updated)
    }

    private fun <T> clearUnsupported(
        slots: List<PanelSlot<T>>,
        supported: Set<T>,
    ): List<PanelSlot<T>> {
        if (supported.isEmpty()) return slots
        return slots.map { slot ->
            val key = slot.key ?: return@map slot
            if (supported.contains(key)) slot else slot.copy(key = null)
        }
    }

    private fun <T> filterDefaults(
        defaults: List<T>,
        supported: Set<T>,
    ): List<T> {
        if (supported.isEmpty()) return defaults
        return defaults.filter { supported.contains(it) }
    }

    private fun shiftSingleSideActionToEnd(panel: UserPanel): UserPanel {
        val items = panel.sideActions
        if (items.size < 2) return panel
        val existing = items.withIndex().filter { it.value.key != null }
        if (existing.size != 1) return panel
        val fromIndex = existing.single().index
        val toIndex = items.lastIndex
        if (fromIndex == toIndex) return panel
        val key = items[fromIndex].key ?: return panel
        if (items[toIndex].key != null) return panel
        val updated = items.toMutableList()
        updated[toIndex] = updated[toIndex].copy(key = key)
        updated[fromIndex] = updated[fromIndex].copy(key = null)
        return panel.copy(sideActions = updated)
    }
}
