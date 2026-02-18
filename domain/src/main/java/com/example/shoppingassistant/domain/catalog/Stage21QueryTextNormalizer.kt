package com.example.shoppingassistant.domain.catalog

internal object Stage21QueryTextNormalizer {
    fun normalize(input: String): String = input
        .trim()
        .lowercase()
        .replace('ё', 'е')
        .replace("[-‐‑‒–—]+".toRegex(), " ")
        .replace("[^\\p{L}\\p{N}\\s]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()

    fun tokenize(text: String): List<String> =
        text.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
}
