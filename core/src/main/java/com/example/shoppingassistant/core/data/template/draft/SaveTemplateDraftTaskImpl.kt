package com.example.shoppingassistant.core.data.template.draft

import com.example.shoppingassistant.domain.template.TemplateHistoryEntry
import com.example.shoppingassistant.domain.template.TemplateHistoryRepository
import com.example.shoppingassistant.domain.template.draft.SaveTemplateDraftTask

class SaveTemplateDraftTaskImpl(
    private val repository: TemplateHistoryRepository,
) : SaveTemplateDraftTask {

    override suspend fun save(entry: TemplateHistoryEntry) {
        repository.upsert(entry)
    }
}
