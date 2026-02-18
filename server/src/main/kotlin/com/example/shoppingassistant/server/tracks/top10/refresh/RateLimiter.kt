package com.example.shoppingassistant.server.tracks.top10.refresh

interface RateLimiter {
    suspend fun acquire(sourceKey: String): Boolean
}

class NoopRateLimiter : RateLimiter {
    override suspend fun acquire(sourceKey: String): Boolean = true
}
