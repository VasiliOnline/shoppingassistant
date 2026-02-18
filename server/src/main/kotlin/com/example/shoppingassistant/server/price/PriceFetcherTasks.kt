package com.example.shoppingassistant.server.price

data class PriceFetcherRunResult(
    val attempted: Int,
    val updated: Int,
    val skipped: Int,
    val blocked: Int,
    val failed: Int,
)

interface PriceFetcherRunner {
    suspend fun runOnce(): PriceFetcherRunResult
}
