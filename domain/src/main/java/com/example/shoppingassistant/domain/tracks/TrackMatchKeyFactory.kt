package com.example.shoppingassistant.domain.tracks

object TrackMatchKeyFactory {
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")
    private val spaces = Regex("\\s+")

    fun normalizeBrandModel(brand: String?, model: String?): Pair<String, String>? {
        val normalizedBrand = normalizePart(brand)
        val normalizedModel = normalizePart(model)
        if (normalizedBrand.isNullOrBlank() || normalizedModel.isNullOrBlank()) return null
        return normalizedBrand to normalizedModel
    }

    fun fromBrandModel(brand: String?, model: String?): String? {
        val (normalizedBrand, normalizedModel) = normalizeBrandModel(brand, model) ?: return null
        return "bm:$normalizedBrand|$normalizedModel"
    }

    fun parse(matchKey: String?): Pair<String, String>? {
        val value = matchKey?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        val normalized = value.removePrefix("bm:")
        val parts = normalized.split("|", limit = 2)
        if (parts.size != 2) return null
        val brand = normalizePart(parts[0]) ?: return null
        val model = normalizePart(parts[1]) ?: return null
        return brand to model
    }

    fun normalizePart(raw: String?): String? {
        val prepared = raw
            ?.lowercase()
            ?.replace(nonWord, " ")
            ?.replace(spaces, " ")
            ?.trim()
            .orEmpty()
        return prepared.takeIf { it.isNotBlank() }
    }
}
