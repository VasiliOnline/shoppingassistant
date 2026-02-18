package com.example.shoppingassistant.server.config

data class FcmConfig(
    val enabled: Boolean,
    val serverKey: String?,
    val endpoint: String,
    val dryRun: Boolean,
) {
    companion object {
        fun fromEnv(): FcmConfig {
            val enabled = (System.getenv("FCM_ENABLED") ?: "false").equals("true", ignoreCase = true)
            val serverKey = System.getenv("FCM_SERVER_KEY")?.trim()?.ifBlank { null }
            val endpoint = System.getenv("FCM_ENDPOINT")?.trim()?.ifBlank { null }
                ?: "https://fcm.googleapis.com/fcm/send"
            val dryRun = (System.getenv("FCM_DRY_RUN") ?: "false").equals("true", ignoreCase = true)

            return FcmConfig(
                enabled = enabled,
                serverKey = serverKey,
                endpoint = endpoint,
                dryRun = dryRun,
            )
        }
    }
}

