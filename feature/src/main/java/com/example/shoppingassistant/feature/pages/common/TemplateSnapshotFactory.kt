package com.example.shoppingassistant.feature.pages.common

import com.example.shoppingassistant.domain.catalog.CatalogDataVersion
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.toRawStringAttributes
import com.example.shoppingassistant.domain.model.toTypedAttributesGuess
import com.example.shoppingassistant.domain.template.TemplateAnchorType
import com.example.shoppingassistant.domain.template.TemplateIdTask
import com.example.shoppingassistant.domain.template.TemplateSnapshot
import com.example.shoppingassistant.domain.template.TemplateSnapshotAttr
import com.example.shoppingassistant.domain.template.TemplateSnapshotData
import com.example.shoppingassistant.domain.template.TemplateSnapshotMode
import java.util.Locale

fun normalizedQueryFromSnapshot(data: TemplateSnapshotData): NormalizedQuery {
    val attrs = data.attrs.associate { it.key to it.value }
    val brand = attrs["brand"].orEmpty()
    val model = attrs["model"].orEmpty()
    val extraAttrs = attrs.filterKeys { key -> key != "brand" && key != "model" }
    return NormalizedQuery(
        brand = brand,
        model = model,
        attributes = extraAttrs.toTypedAttributesGuess(),
    )
}

fun buildSnapshotFromQuery(
    query: NormalizedQuery?,
    queryText: String,
    categoryCode: String?,
    templateIdTask: TemplateIdTask,
    mode: TemplateSnapshotMode = TemplateSnapshotMode.SEARCH,
): TemplateSnapshot? {
    val safeText = queryText.trim()
    val anchorType = if (!categoryCode.isNullOrBlank()) {
        TemplateAnchorType.CATEGORY
    } else {
        TemplateAnchorType.PRODUCT
    }
    val anchorId = when (anchorType) {
        TemplateAnchorType.CATEGORY -> categoryCode.orEmpty()
        TemplateAnchorType.PRODUCT -> safeText
    }.trim()

    if (anchorId.isBlank()) return null

    val attrs = buildList {
        query?.brand?.takeIf { it.isNotBlank() }?.let { add(TemplateSnapshotAttr("brand", it)) }
        query?.model?.takeIf { it.isNotBlank() }?.let { add(TemplateSnapshotAttr("model", it)) }
        query?.attributes?.toRawStringAttributes()?.forEach { (key, value) ->
            if (key != "category" && value.isNotBlank()) {
                add(TemplateSnapshotAttr(key, value))
            }
        }
    }

    val data = TemplateSnapshotData(
        anchorType = anchorType,
        anchorId = anchorId,
        categoryCode = categoryCode,
        attrs = attrs.sortedBy { it.key.lowercase() },
        freeText = safeText.takeIf { it.isNotBlank() && it != anchorId },
        mode = mode,
        schemaVersion = 1,
        taxonomyVersion = CatalogDataVersion.current,
        locale = Locale.getDefault().toLanguageTag().takeIf { it.isNotBlank() },
    )
    val id = templateIdTask.computeId(data)
    return TemplateSnapshot(data = data, templateId = id)
}
