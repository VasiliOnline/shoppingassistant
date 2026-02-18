package com.example.shoppingassistant.core.analytics

import android.util.Log

data class ProfileAnalyticsEvent(
    val name: String,
    val attributes: Map<String, String> = emptyMap(),
)

interface ProfileAnalyticsLogger {
    fun log(event: ProfileAnalyticsEvent)
}

class LogcatProfileAnalyticsLogger : ProfileAnalyticsLogger {
    override fun log(event: ProfileAnalyticsEvent) {
        if (event.attributes.isEmpty()) {
            Log.i(TAG, "event=${event.name}")
        } else {
            Log.i(TAG, "event=${event.name} attributes=${event.attributes}")
        }
    }

    private companion object {
        const val TAG = "ProfileAnalytics"
    }
}
