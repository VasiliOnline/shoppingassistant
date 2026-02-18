package com.example.shoppingassistant.domain.tracks

import kotlinx.serialization.Serializable

@Serializable
data class Freshness(
    val computedAt: Long,
    val ageSec: Int,
    val ttlSec: Int,
    val state: FreshnessState,
)

@Serializable
enum class FreshnessState { FRESH, STALE, EXPIRED }
