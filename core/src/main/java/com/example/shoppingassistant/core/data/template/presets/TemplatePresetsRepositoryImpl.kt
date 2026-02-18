package com.example.shoppingassistant.core.data.template.presets

import com.example.shoppingassistant.domain.template.TemplateIdTask
import com.example.shoppingassistant.domain.template.presets.TemplatePreset
import com.example.shoppingassistant.domain.template.presets.TemplatePresetSpec
import com.example.shoppingassistant.domain.template.presets.TemplatePresetSource
import com.example.shoppingassistant.domain.template.presets.TemplatePresetsRepository
import com.example.shoppingassistant.domain.template.presets.TemplatePresetsSeed

class TemplatePresetsRepositoryImpl(
    private val idTask: TemplateIdTask,
    seed: List<TemplatePresetSpec> = TemplatePresetsSeed.popular,
) : TemplatePresetsRepository {

    private val presets: List<TemplatePreset> = seed
        .distinctBy { spec ->
            val canonical = spec.snapshot
            listOf(
                spec.source.name,
                canonical.anchorType.name,
                canonical.anchorId,
                canonical.categoryCode.orEmpty(),
                canonical.attrs.joinToString("|") { "${it.key}=${it.value}" },
            ).joinToString("::")
        }
        .map { spec ->
            TemplatePreset(
                presetId = idTask.computeId(spec.snapshot),
                source = spec.source,
                snapshot = spec.snapshot,
                title = spec.title,
                rank = spec.rank,
            )
        }
    private val generated: LinkedHashMap<String, TemplatePreset> = linkedMapOf()

    override suspend fun listPresets(
        source: TemplatePresetSource?,
        limit: Int,
    ): List<TemplatePreset> {
        val safeLimit = limit.coerceIn(1, 500)
        val all = presets + generated.values
        val filtered = if (source == null) all else all.filter { it.source == source }
        return filtered
            .sortedWith(compareByDescending<TemplatePreset> { it.rank }.thenBy { it.title.orEmpty() })
            .take(safeLimit)
    }

    override suspend fun upsertGenerated(presets: List<TemplatePreset>) {
        presets.forEach { preset ->
            generated[preset.presetId] = preset
        }
    }
}
