package com.example.shoppingassistant.domain.catalog

import kotlinx.serialization.Serializable

@Serializable
enum class BrowseTargetType {
    CATEGORY,
    SEARCH_PRESET,
}

@Serializable
enum class BrowseNodeStatus {
    ACTIVE,
    HIDDEN,
}

@Serializable
enum class BrowseNodeKind {
    ROOT,
    GROUP,
    LEAF_LINK,
}

@Serializable
data class BrowseNode(
    val browseCode: String,
    val parentBrowseCode: String? = null,
    val nodeKind: BrowseNodeKind = BrowseNodeKind.GROUP,
    val titleKey: String? = null,
    val titleRu: String,
    val titleEn: String? = null,
    val targetCategoryCode: String? = null,
    val targetType: BrowseTargetType? = null,
    val order: Int = 0,
    val availabilityScope: String = "ALL",
    val iconKey: String? = null,
    val analyticsKey: String? = null,
    val searchKeywordsRu: List<String> = emptyList(),
    val status: BrowseNodeStatus = BrowseNodeStatus.ACTIVE,
    val tags: List<String> = emptyList(),
    val notes: String? = null,
)

interface BrowseNodeRepository {
    suspend fun listBrowseNodes(): List<BrowseNode>
    suspend fun getBrowseNode(browseCode: String): BrowseNode?
}

class GetBrowseNodesTask(
    private val repository: BrowseNodeRepository,
) {
    suspend operator fun invoke(): List<BrowseNode> = repository.listBrowseNodes()
}

class GetBrowseNodeTask(
    private val repository: BrowseNodeRepository,
) {
    suspend operator fun invoke(browseCode: String): BrowseNode? = repository.getBrowseNode(browseCode)
}
