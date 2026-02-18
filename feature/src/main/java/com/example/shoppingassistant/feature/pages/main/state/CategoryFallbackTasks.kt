package com.example.shoppingassistant.feature.pages.main.state

import com.example.shoppingassistant.domain.vision.VisionCategoryCandidate

data class CategoryFallbackState(
    val visible: Boolean = false,
    val candidates: List<VisionCategoryCandidate> = emptyList(),
    val message: String? = null,
)
