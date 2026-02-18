package com.example.shoppingassistant.core.config

object IngestReplayConfig {
    val enabled: Boolean = false
    val sampleRate: Double = 0.05
    val maxEntries: Int = 200
    val maxBodyBytes: Int = 600_000
}
