package com.example.shoppingassistant.domain.template.draft

import com.example.shoppingassistant.domain.template.TemplateHistoryEntry

/**
 * Autosave drafts into template history.
 */
interface SaveTemplateDraftTask {
    suspend fun save(entry: TemplateHistoryEntry)
}
