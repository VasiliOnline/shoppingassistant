package com.example.shoppingassistant.core.data.db

import androidx.room.TypeConverter

/**
 * Простой конвертер: список URL картинок храним одной строкой (через |).
 * Можно заменить на JSON позже.
 */
object Converters {
    @TypeConverter
    @JvmStatic
    fun fromUrls(urls: List<String>?): String? = urls?.joinToString("|")

    @TypeConverter
    @JvmStatic
    fun toUrls(raw: String?): List<String>? = raw?.takeIf { it.isNotBlank() }?.split("|")
}
