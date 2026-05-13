package com.example.shoppingassistant.domain.catalog

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class CatalogFacetPresentationProfilesDocument(
    val schemaVersion: String,
    val profiles: List<CatalogFacetPresentationProfileSeed> = emptyList(),
)

@Serializable
data class CatalogFacetPresentationProfileSeed(
    val profileCode: String,
    val categoryPrefixes: List<String>,
    val hiddenSystemFacetKeys: List<String> = emptyList(),
    val hiddenTypedFacetKeys: List<String> = emptyList(),
    val pinnedSystemFacetKeys: List<String> = emptyList(),
    val mainTypedFacetKeys: List<String> = emptyList(),
    val additionalTypedFacetKeys: List<String> = emptyList(),
    val orderedTypedFacetKeys: List<String> = emptyList(),
    val liveOnlyTypedFacetKeys: List<String> = emptyList(),
    val noticePriorityTypedFacetKeys: List<String> = emptyList(),
    val suppressedAutoAppliedTypedFacetKeys: List<String> = emptyList(),
    val requiresBrandContextTypedFacetKeys: List<String> = emptyList(),
)

data class CatalogFacetPresentationProfile(
    val profileCode: String,
    val categoryPrefixes: Set<String>,
    val hiddenSystemFacetKeys: Set<String>,
    val hiddenTypedFacetKeys: Set<String>,
    val pinnedSystemFacetKeys: List<String>,
    val mainTypedFacetKeys: List<String>,
    val additionalTypedFacetKeys: List<String>,
    val orderedTypedFacetKeys: List<String>,
    val liveOnlyTypedFacetKeys: Set<String>,
    val noticePriorityTypedFacetKeys: List<String>,
    val suppressedAutoAppliedTypedFacetKeys: Set<String>,
    val requiresBrandContextTypedFacetKeys: Set<String>,
)

object CatalogFacetPresentationProfiles {
    private val resourcePath: String
        get() = "${CatalogContractPaths.stage40Base}/facet_presentation_profiles.json"

    val snapshot: CatalogFacetPresentationProfilesDocument by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = resourcePath,
            deserializer = CatalogFacetPresentationProfilesDocument.serializer(),
        )
    }

    fun resolve(categoryCode: String?): CatalogFacetPresentationProfile? {
        val normalizedCategoryCode = categoryCode
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { value -> value.isNotEmpty() }
            ?: return null
        val matches = snapshot.profiles
            .map { it.toNormalizedProfile() }
            .filter { profile ->
                profile.categoryPrefixes.any { prefix ->
                    prefix == "*" ||
                        normalizedCategoryCode == prefix ||
                        normalizedCategoryCode.startsWith("$prefix.")
                }
            }
            .sortedBy { profile ->
                profile.categoryPrefixes.maxOfOrNull { prefix -> prefix.length } ?: 0
            }
        if (matches.isEmpty()) return null
        return matches.reduce { acc, profile -> acc.merge(profile) }
    }

    private fun CatalogFacetPresentationProfileSeed.toNormalizedProfile(): CatalogFacetPresentationProfile =
        CatalogFacetPresentationProfile(
            profileCode = profileCode.trim(),
            categoryPrefixes = categoryPrefixes
                .mapNotNull { prefix ->
                    prefix.trim()
                        .uppercase(Locale.ROOT)
                        .takeIf { value -> value.isNotEmpty() }
                }
                .toCollection(LinkedHashSet()),
            hiddenSystemFacetKeys = normalizeFacetKeySet(hiddenSystemFacetKeys),
            hiddenTypedFacetKeys = normalizeFacetKeySet(hiddenTypedFacetKeys),
            pinnedSystemFacetKeys = normalizeFacetKeyList(pinnedSystemFacetKeys),
            mainTypedFacetKeys = normalizeFacetKeyList(mainTypedFacetKeys),
            additionalTypedFacetKeys = normalizeFacetKeyList(additionalTypedFacetKeys),
            orderedTypedFacetKeys = normalizeFacetKeyList(orderedTypedFacetKeys),
            liveOnlyTypedFacetKeys = normalizeFacetKeySet(liveOnlyTypedFacetKeys),
            noticePriorityTypedFacetKeys = normalizeFacetKeyList(noticePriorityTypedFacetKeys),
            suppressedAutoAppliedTypedFacetKeys = normalizeFacetKeySet(suppressedAutoAppliedTypedFacetKeys),
            requiresBrandContextTypedFacetKeys = normalizeFacetKeySet(requiresBrandContextTypedFacetKeys),
        )

    private fun CatalogFacetPresentationProfile.merge(
        other: CatalogFacetPresentationProfile,
    ): CatalogFacetPresentationProfile =
        CatalogFacetPresentationProfile(
            profileCode = other.profileCode.ifBlank { profileCode },
            categoryPrefixes = categoryPrefixes + other.categoryPrefixes,
            hiddenSystemFacetKeys = hiddenSystemFacetKeys + other.hiddenSystemFacetKeys,
            hiddenTypedFacetKeys = hiddenTypedFacetKeys + other.hiddenTypedFacetKeys,
            pinnedSystemFacetKeys = mergeFacetKeyLists(pinnedSystemFacetKeys, other.pinnedSystemFacetKeys),
            mainTypedFacetKeys = mergeFacetKeyLists(mainTypedFacetKeys, other.mainTypedFacetKeys),
            additionalTypedFacetKeys = mergeFacetKeyLists(additionalTypedFacetKeys, other.additionalTypedFacetKeys),
            orderedTypedFacetKeys = mergeFacetKeyLists(orderedTypedFacetKeys, other.orderedTypedFacetKeys),
            liveOnlyTypedFacetKeys = liveOnlyTypedFacetKeys + other.liveOnlyTypedFacetKeys,
            noticePriorityTypedFacetKeys = mergeFacetKeyLists(
                noticePriorityTypedFacetKeys,
                other.noticePriorityTypedFacetKeys,
            ),
            suppressedAutoAppliedTypedFacetKeys =
                suppressedAutoAppliedTypedFacetKeys + other.suppressedAutoAppliedTypedFacetKeys,
            requiresBrandContextTypedFacetKeys = other.requiresBrandContextTypedFacetKeys + requiresBrandContextTypedFacetKeys,
        )

    private fun normalizeFacetKeySet(rawKeys: List<String>): Set<String> =
        rawKeys
            .mapNotNull { key ->
                key.trim()
                    .lowercase(Locale.ROOT)
                    .takeIf { value -> value.isNotEmpty() }
            }
            .toCollection(LinkedHashSet())

    private fun normalizeFacetKeyList(rawKeys: List<String>): List<String> =
        rawKeys
            .mapNotNull { key ->
                key.trim()
                    .lowercase(Locale.ROOT)
                    .takeIf { value -> value.isNotEmpty() }
            }
            .distinct()

    private fun mergeFacetKeyLists(
        base: List<String>,
        override: List<String>,
    ): List<String> = (override + base).distinct()
}
