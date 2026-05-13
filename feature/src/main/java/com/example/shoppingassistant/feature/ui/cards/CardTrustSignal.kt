package com.example.shoppingassistant.feature.ui.cards

fun selectTrustLabel(trust: CardTrustData?): String? {
    if (trust == null) return null
    return when {
        !trust.updatedAtText.isNullOrBlank() -> trust.updatedAtText
        !trust.sourceText.isNullOrBlank() -> trust.sourceText
        !trust.ratingText.isNullOrBlank() -> trust.ratingText
        else -> null
    }
}
