package com.example.shoppingassistant.feature.pages.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.IndeterminateCheckBox
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.example.shoppingassistant.domain.i18n.displayTitle
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.shoppingassistant.domain.catalog.BrowseNode
import com.example.shoppingassistant.domain.catalog.BrowseNodeStatus
import com.example.shoppingassistant.domain.catalog.BrowseTargetType
import com.example.shoppingassistant.domain.catalog.GetBrowseNodesTask
import com.example.shoppingassistant.feature.pages.main.state.MainPageViewModel
import com.example.shoppingassistant.feature.ui.layout.AppTopBar
import com.example.shoppingassistant.feature.ui.layout.LayoutDefaults
import com.example.shoppingassistant.feature.ui.layout.ScreenRoot
import java.util.Locale
import org.koin.java.KoinJavaComponent.get as koinGet

@Composable
fun FeedCategoriesPage(
    viewModel: MainPageViewModel,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    applySafeInsets: Boolean = true,
    extraBottomPadding: Dp = LayoutDefaults.ContentBottomSpacing,
    initialSelectedCodes: Set<String>? = null,
    onApplySelected: ((Set<String>) -> Unit)? = null,
    isModal: Boolean = false,
) {
    val state by viewModel.state.collectAsState()
    val getBrowseNodesTask: GetBrowseNodesTask = remember { koinGet(GetBrowseNodesTask::class.java) }

    var browseNodes by remember { mutableStateOf<List<BrowseNode>>(emptyList()) }
    var pathBrowseCodes by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedCodes by remember {
        mutableStateOf(initialSelectedCodes ?: state.nearbyCategoryChips.map { it.code }.toSet())
    }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        browseNodes = runCatching { getBrowseNodesTask() }
            .getOrElse { emptyList() }
            .filter { it.status == BrowseNodeStatus.ACTIVE }
    }

    LaunchedEffect(initialSelectedCodes) {
        initialSelectedCodes?.let { selectedCodes = it }
    }

    LaunchedEffect(state.nearbyCategoryChips) {
        if (initialSelectedCodes == null) {
            selectedCodes = state.nearbyCategoryChips.map { it.code }.toSet()
        }
    }

    val browseByCode = remember(browseNodes) { browseNodes.associateBy { it.browseCode } }
    val childrenByParent = remember(browseNodes) {
        browseNodes.groupBy { normalizeParentCode(it.parentBrowseCode) }
    }
    val descendantsByBrowseCode = remember(childrenByParent, browseByCode) {
        buildBrowseCategoryTargetsIndex(childrenByParent = childrenByParent, browseByCode = browseByCode)
    }

    val currentParent = pathBrowseCodes.lastOrNull()
    val normalizedQuery = searchQuery.trim().lowercase()
    val searchActive = normalizedQuery.isNotBlank()
    val localeTag = Locale.getDefault().toLanguageTag()
    val list = if (searchActive) {
        browseNodes
            .filter { node -> node.matchesSearch(normalizedQuery, locale = localeTag) }
            .sortedWith(compareBy<BrowseNode> { it.order }.thenBy { it.displayTitle(locale = localeTag) })
    } else {
        childrenByParent[currentParent].orEmpty()
            .sortedWith(compareBy<BrowseNode> { it.order }.thenBy { it.displayTitle(locale = localeTag) })
    }
    val sections = remember(list, searchActive, localeTag) { buildBrowseSections(list, searchActive, localeTag) }

    val selectedCount = selectedCodes.size
    val canNavigateUp = pathBrowseCodes.isNotEmpty()
    val handleBack: (() -> Unit)? = if (canNavigateUp || !isModal) {
        {
            if (pathBrowseCodes.isNotEmpty()) {
                pathBrowseCodes = pathBrowseCodes.dropLast(1)
            } else {
                onBack()
            }
        }
    } else null
    val handleClose: (() -> Unit)? = if (isModal && !canNavigateUp) onClose else null

    ScreenRoot(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        applySafeInsets = applySafeInsets,
        extraBottomPadding = extraBottomPadding,
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                title = "Категории для новых предложений",
                onBack = handleBack,
                onClose = handleClose,
                applySafeInsets = !applySafeInsets,
            )

            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = { Text("Поиск по разделам") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                    disabledIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LayoutDefaults.HorizontalPadding)
                    .padding(top = LayoutDefaults.SectionSpacing, bottom = 6.dp),
                shape = RoundedCornerShape(18.dp),
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    start = LayoutDefaults.HorizontalPadding,
                    end = LayoutDefaults.HorizontalPadding,
                    top = LayoutDefaults.SectionSpacing,
                ),
            ) {
                sections.forEach { section ->
                    if (!section.title.isNullOrBlank()) {
                        item(key = "section:${section.title}") {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    items(section.nodes, key = { it.browseCode }) { node ->
                        val hasChildren = childrenByParent[node.browseCode].orEmpty().isNotEmpty()
                        val canNavigate = hasChildren && !searchActive
                        val categoryTargets = descendantsByBrowseCode[node.browseCode].orEmpty()
                        val selectedHits = categoryTargets.count { selectedCodes.contains(it) }
                        val isSelected = categoryTargets.isNotEmpty() && selectedHits == categoryTargets.size
                        val isPartial = categoryTargets.isNotEmpty() && selectedHits in 1 until categoryTargets.size

                        FeedBrowseRow(
                            node = node,
                            hasChildren = canNavigate,
                            isSelected = isSelected,
                            isPartial = isPartial,
                            onToggle = {
                                if (categoryTargets.isEmpty()) return@FeedBrowseRow
                                selectedCodes = if (isSelected) {
                                    selectedCodes - categoryTargets
                                } else {
                                    selectedCodes + categoryTargets
                                }
                            },
                            onOpen = {
                                if (canNavigate) {
                                    pathBrowseCodes = pathBrowseCodes + node.browseCode
                                }
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LayoutDefaults.HorizontalPadding)
                    .padding(top = LayoutDefaults.SectionSpacing, bottom = contentPadding.calculateBottomPadding()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Выбрано: $selectedCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = { selectedCodes = emptySet() },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Сбросить") }
                Button(
                    onClick = {
                        if (onApplySelected != null) {
                            onApplySelected(selectedCodes)
                        } else {
                            viewModel.updateFeedCategories(selectedCodes)
                        }
                        onClose()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Готово") }
            }
        }
    }
}

@Composable
private fun FeedBrowseRow(
    node: BrowseNode,
    hasChildren: Boolean,
    isSelected: Boolean,
    isPartial: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = when {
        isSelected -> colors.primaryContainer
        isPartial -> colors.secondaryContainer.copy(alpha = 0.7f)
        else -> colors.surfaceVariant.copy(alpha = 0.6f)
    }
    val contentColor = when {
        isSelected -> colors.onPrimaryContainer
        isPartial -> colors.onSecondaryContainer
        else -> colors.onSurface
    }
    val indicatorColor = when {
        isSelected -> colors.primary
        isPartial -> colors.secondary
        else -> colors.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = background,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (hasChildren) onOpen() else onToggle() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            ) {
                val icon = when {
                    isSelected -> Icons.Outlined.CheckBox
                    isPartial -> Icons.Outlined.IndeterminateCheckBox
                    else -> Icons.Outlined.CheckBoxOutlineBlank
                }
                Icon(imageVector = icon, contentDescription = null, tint = indicatorColor)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Label,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )

            Text(
                text = node.displayTitle(locale = java.util.Locale.getDefault().toLanguageTag()),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            if (hasChildren) {
                IconButton(
                    onClick = onOpen,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "Подразделы",
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private data class BrowseSection(
    val title: String?,
    val nodes: List<BrowseNode>,
)

private fun normalizeParentCode(parentBrowseCode: String?): String? =
    parentBrowseCode?.trim()?.takeIf { it.isNotEmpty() }

private fun buildBrowseCategoryTargetsIndex(
    childrenByParent: Map<String?, List<BrowseNode>>,
    browseByCode: Map<String, BrowseNode>,
): Map<String, Set<String>> {
    val result = mutableMapOf<String, Set<String>>()

    fun collect(browseCode: String): Set<String> {
        val cached = result[browseCode]
        if (cached != null) return cached
        val node = browseByCode[browseCode]
        val ownTarget = if (node?.targetType == BrowseTargetType.CATEGORY) {
            node.targetCategoryCode?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            null
        }
        val childrenTargets = childrenByParent[browseCode].orEmpty().flatMap { child ->
            collect(child.browseCode)
        }
        val all = linkedSetOf<String>()
        ownTarget?.let { all += it }
        all += childrenTargets
        result[browseCode] = all
        return all
    }

    browseByCode.keys.forEach { browseCode -> collect(browseCode) }
    return result
}

private fun buildBrowseSections(
    list: List<BrowseNode>,
    searchActive: Boolean,
    locale: String,
): List<BrowseSection> {
    if (list.isEmpty()) return emptyList()
    if (searchActive || list.size <= 10) return listOf(BrowseSection(title = null, nodes = list))

    val sorted = list.sortedWith(compareBy<BrowseNode> { it.order }.thenBy { it.displayTitle(locale = locale) })
    val popularSize = minOf(6, sorted.size)
    val popular = sorted.take(popularSize)
    val rest = sorted.drop(popularSize)

    val sections = mutableListOf<BrowseSection>()
    if (popular.isNotEmpty()) {
        sections += BrowseSection(title = "Популярное", nodes = popular)
    }
    val grouped = rest.groupBy { node ->
        val first = node.displayTitle(locale = locale).trim().firstOrNull()?.uppercaseChar()
        when {
            first == null -> "#"
            first in 'A'..'Z' -> first.toString()
            first in 'А'..'Я' || first == 'Ё' -> first.toString()
            else -> "#"
        }
    }.toSortedMap()

    grouped.forEach { (bucket, nodes) ->
        sections += BrowseSection(title = bucket, nodes = nodes.sortedBy { it.displayTitle(locale = locale) })
    }

    return sections
}

private fun BrowseNode.matchesSearch(query: String, locale: String): Boolean {
    if (query.isBlank()) return true
    if (title.values.any { label -> label.lowercase().contains(query) }) return true
    val displayTitleNormalized = displayTitle(locale = locale).lowercase()
    if (displayTitleNormalized.contains(query)) return true
    if (browseCode.lowercase().contains(query)) return true
    if (targetCategoryCode?.lowercase()?.contains(query) == true) return true
    return searchKeywordsRu.any { keyword -> keyword.lowercase().contains(query) }
}
