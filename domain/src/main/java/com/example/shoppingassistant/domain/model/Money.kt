package com.example.shoppingassistant.domain.model

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

@Serializable
@JvmInline
value class Money(val minor: Long) { // cents/копейки
    fun toMajor(): Double = minor / 100.0

    companion object {
        const val MINOR_SCALE: Int = 2
        const val ISO_CURRENCY_CODE_LENGTH: Int = 3

        fun fromMajor(major: Double): Money =
            requireNotNull(fromMajorOrNull(major)) { "Invalid money major value: $major" }

        fun fromMajorOrNull(major: Double): Money? {
            if (!major.isFinite()) return null
            return runCatching {
                val rounded = BigDecimal.valueOf(major)
                    .setScale(MINOR_SCALE, RoundingMode.HALF_UP)
                Money(rounded.movePointRight(MINOR_SCALE).longValueExact())
            }.getOrNull()
        }

        fun normalizeCurrencyCode(raw: String?): String? =
            raw?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.length == ISO_CURRENCY_CODE_LENGTH }
    }
}
