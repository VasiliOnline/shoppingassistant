package com.example.shoppingassistant.domain.catalog

import kotlinx.serialization.Serializable

@Serializable
enum class AliasKind {
    CATEGORY,
    BROWSE,
    BRAND,
    ATTRIBUTE_HINT,
}

@Serializable
enum class AliasMatchKind {
    EXACT,
    PREFIX,
    TOKEN,
    FUZZY,
}

@Serializable
enum class AliasSource {
    SEED,
    ANALYTICS,
    MANUAL,
    LEARNED,
}

@Serializable
data class AliasEntry(
    val locale: String = "ru-RU",
    val term: String,
    val normalizedTerm: String = term,
    val kind: AliasKind,
    val targetCode: String,
    val weight: Int = 50,
    val matchKind: AliasMatchKind = AliasMatchKind.EXACT,
    val isBlocked: Boolean = false,
    val source: AliasSource = AliasSource.MANUAL,
    val notes: String? = null,
)

interface AliasEntryRepository {
    suspend fun listAliasEntries(locale: String? = null): List<AliasEntry>
}

class GetAliasEntriesTask(
    private val repository: AliasEntryRepository,
) {
    suspend operator fun invoke(locale: String? = null): List<AliasEntry> =
        repository.listAliasEntries(locale = locale)
}
