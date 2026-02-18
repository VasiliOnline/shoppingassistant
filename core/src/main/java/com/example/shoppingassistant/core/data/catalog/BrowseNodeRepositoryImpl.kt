package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.domain.catalog.BrowseNode
import com.example.shoppingassistant.domain.catalog.BrowseNodeRepository
import com.example.shoppingassistant.domain.catalog.CatalogSeed

class BrowseNodeRepositoryImpl(
    private val seeded: List<BrowseNode> = CatalogSeed.browseNodes,
) : BrowseNodeRepository {
    private val byCode: Map<String, BrowseNode> = seeded.associateBy { it.browseCode }

    override suspend fun listBrowseNodes(): List<BrowseNode> = seeded

    override suspend fun getBrowseNode(browseCode: String): BrowseNode? = byCode[browseCode]
}
