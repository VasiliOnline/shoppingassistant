package com.example.shoppingassistant.core.data

import com.example.shoppingassistant.domain.model.Normalization

/**
 * Фасад к core.model.Normalization + щадящие строковые утилиты для отображения.
 * Не тянет UI/DB-типы.
 */
object Normalizer {
    /** Машинный ключ: для индексов/поиска. */
    fun key(raw: String): String =
        Normalization.key(raw)

    /** Нормализация key/value атрибутов. */
    fun normalizeAttrs(attrs: Map<String,String>): Map<String,String> =
        Normalization.normalizeAttrs(attrs)

    /** Щадящая нормализация бренда для UI. */
    fun normBrand(raw: String?): String? = raw
        ?.trim()
        ?.replace("\\s+".toRegex(), " ")
        ?.lowercase()
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    /** Щадящая нормализация модели для UI. */
    fun normModel(raw: String?): String? = raw
        ?.trim()
        ?.replace("\\s+".toRegex(), " ")
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
