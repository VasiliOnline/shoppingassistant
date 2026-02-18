package com.example.shoppingassistant.feature.pages.main.suggest

import com.example.shoppingassistant.domain.template.TemplateHistoryEntry
import com.example.shoppingassistant.domain.template.presets.TemplatePreset
import com.example.shoppingassistant.domain.template.status.TemplateStatus

sealed interface MainSuggestItem {
    val stableId: String
}

data class ProductAnchorSuggest(
    val productId: Long,
    val text: String,
    val caption: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val categoryCode: String? = null,
) : MainSuggestItem {
    override val stableId: String = "product:$productId"
}

data class CategoryAnchorSuggest(
    val categoryCode: String,
    val breadcrumb: String,
    val count: Int? = null,
    val matchedAlias: String? = null,
) : MainSuggestItem {
    override val stableId: String = "category:$categoryCode"
}

data class HistoryTemplateSuggest(
    val entry: TemplateHistoryEntry,
    val text: String,
    val caption: String? = null,
    val status: TemplateStatus = TemplateStatus.DRAFT,
    val firstErrorKey: String? = null,
) : MainSuggestItem {
    override val stableId: String = "history:${entry.snapshot.templateId}"
}

data class PresetTemplateSuggest(
    val preset: TemplatePreset,
    val text: String,
    val caption: String? = null,
) : MainSuggestItem {
    override val stableId: String = "preset:${preset.presetId}"
}

data class SectionHeaderSuggest(
    val text: String,
) : MainSuggestItem {
    override val stableId: String = "section:${text.hashCode()}"
}

data class AutoPresetSuggest(
    val text: String,
    val caption: String? = null,
) : MainSuggestItem {
    override val stableId: String = "autopreset:${text.hashCode()}"
}

data class AutoTemplateSuggest(
    val text: String,
    val caption: String? = null,
    val reasons: List<String> = emptyList(),
    val brand: String,
    val model: String,
    val modelLine: String? = null,
    val categoryCode: String? = null,
    val categoryBreadcrumb: String? = null,
) : MainSuggestItem {
    override val stableId: String = "autotemplate:${text.hashCode()}"
}

data class TextFixSuggest(
    val fixedText: String,
    val caption: String? = null,
) : MainSuggestItem {
    override val stableId: String = "textfix:${fixedText.hashCode()}"
}

data class InfoSuggest(
    val text: String,
) : MainSuggestItem {
    override val stableId: String = "info:${text.hashCode()}"
}

interface MainSuggestEngine {
    suspend fun suggest(queryText: String): List<MainSuggestItem>
}
