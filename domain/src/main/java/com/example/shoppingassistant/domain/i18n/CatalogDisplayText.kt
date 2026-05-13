package com.example.shoppingassistant.domain.i18n

import com.example.shoppingassistant.domain.catalog.BrowseNode
import com.example.shoppingassistant.domain.catalog.CatalogValueOption
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.facet.FacetCollection
import com.example.shoppingassistant.domain.facet.FacetDefinition
import com.example.shoppingassistant.domain.facet.FacetPreset

fun Category.displayTitle(locale: String? = null): String =
    title.resolve(locale = locale, fallback = code) ?: code

fun BrowseNode.displayTitle(locale: String? = null): String =
    title.resolve(locale = locale, fallback = browseCode) ?: browseCode

fun FacetDefinition.displayTitle(locale: String? = null): String =
    title.resolve(locale = locale, fallback = facetKey) ?: facetKey

fun FacetPreset.displayTitle(locale: String? = null): String =
    title.resolve(locale = locale, fallback = presetCode) ?: presetCode

fun FacetCollection.displayTitle(locale: String? = null): String =
    title.resolve(locale = locale, fallback = collectionCode) ?: collectionCode

fun CatalogValueOption.displayLabel(locale: String? = null): String =
    resolveLocalizedLabel(
        labels = labels,
        fallback = valueCode,
        locale = locale,
    )

fun resolveLocalizedLabel(
    labels: LocalizedText,
    fallback: String,
    locale: String? = null,
): String = labels.resolve(locale = locale, fallback = fallback) ?: fallback
