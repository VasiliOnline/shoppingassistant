package com.example.shoppingassistant.domain.template.presets.generate

import com.example.shoppingassistant.domain.template.presets.TemplatePreset
import kotlinx.serialization.Serializable

@Serializable
data class PresetAnchor(
    val brand: String,
    val model: String,
    val samples: Int = 0,
)

/**
 * Source of top anchors (brand/model) for preset generation.
 */
interface PresetAnchorSource {
    suspend fun listTopAnchors(limit: Int): List<PresetAnchor>
}

@Serializable
data class GenerateTemplatePresetsRequest(
    val categoryCode: String,
    val limit: Int = 50,
    val maxAttributes: Int = 4,
    val minSamples: Int = 1,
)

/**
 * Generates presets from observed data and stores them into presets repository.
 */
interface GenerateTemplatePresetsTask {
    suspend fun generate(request: GenerateTemplatePresetsRequest): List<TemplatePreset>
}
