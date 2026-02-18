package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.domain.catalog.AliasEntry
import com.example.shoppingassistant.domain.catalog.AliasEntryRepository
import com.example.shoppingassistant.domain.catalog.CatalogSeed

class AliasEntryRepositoryImpl(
    private val seeded: List<AliasEntry> = CatalogSeed.aliasEntries,
) : AliasEntryRepository {

    override suspend fun listAliasEntries(locale: String?): List<AliasEntry> {
        if (locale.isNullOrBlank()) return seeded
        return seeded.filter { it.locale.equals(locale, ignoreCase = true) }
    }
}
