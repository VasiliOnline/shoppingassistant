package com.example.shoppingassistant.server.config

data class VisionConfig(
    val dailyLimit: Int = 10,
    val totalLimit: Int = 200,
    val resetIntervalHours: Int = 24,
    val techCost: Int = 1,
    val visualCost: Int = 2,
) {
    companion object {
        fun fromEnv(): VisionConfig {
            val daily = System.getenv("VISION_DAILY_LIMIT")?.toIntOrNull() ?: 10
            val total = System.getenv("VISION_TOTAL_LIMIT")?.toIntOrNull() ?: 200
            val resetHours = System.getenv("VISION_RESET_HOURS")?.toIntOrNull() ?: 24
            val techCost = System.getenv("VISION_COST_TECH")?.toIntOrNull() ?: 1
            val visualCost = System.getenv("VISION_COST_VISUAL")?.toIntOrNull() ?: 2
            return VisionConfig(
                dailyLimit = daily.coerceAtLeast(0),
                totalLimit = total.coerceAtLeast(0),
                resetIntervalHours = resetHours.coerceAtLeast(1),
                techCost = techCost.coerceAtLeast(1),
                visualCost = visualCost.coerceAtLeast(1),
            )
        }
    }
}
