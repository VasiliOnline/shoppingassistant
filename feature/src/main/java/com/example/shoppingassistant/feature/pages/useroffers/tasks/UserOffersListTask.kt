package com.example.shoppingassistant.feature.pages.useroffers.tasks

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search

import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferAction
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferCardUi
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferQuickFilter
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferStatus
import com.example.shoppingassistant.feature.pages.useroffers.UserOffersLayout
import com.example.shoppingassistant.feature.pages.useroffers.UserOffersSort
import com.example.shoppingassistant.feature.ui.animations.ShimmerTask
import com.example.shoppingassistant.feature.ui.animations.rememberReduceMotionEnabled
import com.example.shoppingassistant.feature.ui.animations.rememberListAnimationsTask
import com.example.shoppingassistant.feature.ui.animations.rememberShimmerTask
import com.example.shoppingassistant.feature.ui.state.model.StateActionType
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun UserOffersControls(
    layout: UserOffersLayout,
    onLayoutChange: (UserOffersLayout) -> Unit,
    sort: UserOffersSort,
    onSortChange: (UserOffersSort) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    resultsCount: Int,
    statusFilters: Set<UserOfferStatus>,
    availableStatuses: List<UserOfferStatus>,
    onStatusToggle: (UserOfferStatus) -> Unit,
    quickFilters: Set<UserOfferQuickFilter>,
    onQuickFilterToggle: (UserOfferQuickFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val quickFilterItems = remember {
        listOf(
            UserOfferQuickFilter.WITH_PHOTO,
            UserOfferQuickFilter.WITHOUT_PHOTO,
            UserOfferQuickFilter.EXPIRING_SOON,
            UserOfferQuickFilter.ON_MODERATION,
            UserOfferQuickFilter.PUBLISH_ERROR,
            UserOfferQuickFilter.PROMOTED,
        )
    }
    val sortOptions = remember {
        listOf(
            SortOption(UserOffersSort.PUBLISHED_AT, "По публикации"),
            SortOption(UserOffersSort.UPDATED_AT, "По обновлению"),
            SortOption(UserOffersSort.PRICE, "По цене"),
            SortOption(UserOffersSort.VIEWS, "По просмотрам"),
            SortOption(UserOffersSort.EXPIRES_AT, "По сроку"),
            SortOption(UserOffersSort.CATEGORY, "По категории"),
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Поиск по названию и категории") },
            singleLine = true,
            leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = if (searchQuery.isNotBlank()) {
                {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(imageVector = Icons.Outlined.Close, contentDescription = "Очистить")
                    }
                }
            } else null,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = "Найдено: $resultsCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (availableStatuses.isNotEmpty()) {
            SectionHeader(title = "Статусы")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                availableStatuses.forEach { status ->
                    FilterChip(
                        selected = status in statusFilters,
                        onClick = { onStatusToggle(status) },
                        label = { Text(statusLabel(status)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        ),
                    )
                }
            }
        }

        SectionHeader(title = "Фильтры")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            quickFilterItems.forEach { filter ->
                FilterChip(
                    selected = filter in quickFilters,
                    onClick = { onQuickFilterToggle(filter) },
                    label = { Text(quickFilterLabel(filter)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                    ),
                )
            }
        }

        SectionHeader(title = "Вид")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = layout == UserOffersLayout.HORIZONTAL,
                onClick = { onLayoutChange(UserOffersLayout.HORIZONTAL) },
                label = { Text("Горизонтально") },
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.ViewWeek, contentDescription = null)
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                ),
            )
            FilterChip(
                selected = layout == UserOffersLayout.VERTICAL,
                onClick = { onLayoutChange(UserOffersLayout.VERTICAL) },
                label = { Text("Вертикально") },
                leadingIcon = {
                    Icon(imageVector = Icons.AutoMirrored.Outlined.ViewList, contentDescription = null)
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                ),
            )
        }

        SectionHeader(title = "Сортировка")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sortOptions.forEach { option ->
                SortChip(
                    selected = sort == option.id,
                    label = option.label,
                    onClick = { onSortChange(option.id) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun SortChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            }
        ),
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.ArrowDownward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

private data class SortOption(
    val id: UserOffersSort,
    val label: String,
)

data class UserOffersEmptyStateUi(
    val title: String,
    val subtitle: String,
    val primaryCta: String?,
    val secondaryCta: String?,
    val onPrimary: (() -> Unit)?,
    val onSecondary: (() -> Unit)?,
    val icon: ImageVector? = null,
    val primaryType: StateActionType = StateActionType.GENERIC,
    val secondaryType: StateActionType = StateActionType.GENERIC,
)

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun UserOffersListBlock(
    offers: List<UserOfferCardUi>,
    layout: UserOffersLayout,
    actionsForOffer: (UserOfferCardUi) -> List<UserOfferAction>,
    onAction: (UserOfferAction, UserOfferCardUi) -> Unit,
    onSwipeAction: (UserOfferAction, UserOfferCardUi) -> Unit,
    onOpen: (UserOfferCardUi) -> Unit,
    onLongPress: (UserOfferCardUi) -> Unit,
    onToggleSelect: (UserOfferCardUi) -> Unit,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    isLoading: Boolean,
    canLoadMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val horizontalCardWidth = screenWidth * 0.66f
    val listState = rememberLazyListState()
    val animations = rememberListAnimationsTask()
    val shimmer = rememberShimmerTask()
    val reduceMotion = rememberReduceMotionEnabled()

    LaunchedEffect(listState, offers.size, canLoadMore) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { index ->
                if (index >= offers.lastIndex - 2) onLoadMore()
            }
    }

    if (isLoading && offers.isEmpty()) {
        ShimmerOffersList(
            layout = layout,
            horizontalCardWidth = horizontalCardWidth,
            shimmer = shimmer,
            modifier = modifier,
        )
        return
    }

    if (layout == UserOffersLayout.HORIZONTAL) {
        LazyRow(
            state = listState,
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            itemsIndexed(items = offers, key = { _, item -> item.id }) { index, offer ->
                val animatedModifier = if (reduceMotion) Modifier else with(animations) {
                    val stagger = staggeredItem(index)
                    val placement = animatedPlacement()
                    stagger.then(placement)
                }
                Box(modifier = Modifier.width(horizontalCardWidth).then(animatedModifier)) {
                    SwipeableOfferCard(
                        offer = offer,
                        enabled = false,
                        onSwipeAction = { onSwipeAction(it, offer) },
                    ) { swipeModifier ->
                        UserOfferCard(
                            offer = offer,
                            layout = layout,
                            actions = actionsForOffer(offer),
                            onAction = { onAction(it, offer) },
                            onOpen = { onOpen(offer) },
                            selectionMode = selectionMode,
                            selected = offer.id in selectedIds,
                            onToggleSelect = { onToggleSelect(offer) },
                            onLongPress = { onLongPress(offer) },
                            modifier = swipeModifier,
                        )
                    }
                }
            }
            if (isLoadingMore) {
                item(key = "loading_more") {
                    LoadMoreIndicator(modifier = Modifier.padding(12.dp))
                }
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            itemsIndexed(items = offers, key = { _, item -> item.id }) { index, offer ->
                val animatedModifier = if (reduceMotion) Modifier else with(animations) {
                    val stagger = staggeredItem(index)
                    val placement = animatedPlacement()
                    stagger.then(placement)
                }
                SwipeableOfferCard(
                    offer = offer,
                    enabled = !selectionMode,
                    onSwipeAction = { onSwipeAction(it, offer) },
                ) { swipeModifier ->
                    UserOfferCard(
                        offer = offer,
                        layout = layout,
                        actions = actionsForOffer(offer),
                        onAction = { onAction(it, offer) },
                        onOpen = { onOpen(offer) },
                        selectionMode = selectionMode,
                        selected = offer.id in selectedIds,
                        onToggleSelect = { onToggleSelect(offer) },
                        onLongPress = { onLongPress(offer) },
                        modifier = swipeModifier.then(animatedModifier),
                    )
                }
            }
            if (isLoadingMore) {
                item(key = "loading_more") {
                    LoadMoreIndicator(modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

@Composable
private fun ShimmerOffersList(
    layout: UserOffersLayout,
    horizontalCardWidth: Dp,
    shimmer: ShimmerTask,
    modifier: Modifier = Modifier,
) {
    val items = List(5) { "shimmer-$it" }
    if (layout == UserOffersLayout.HORIZONTAL) {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            items(items, key = { it }) {
                Card(
                    modifier = Modifier
                        .width(horizontalCardWidth)
                        .height(260.dp)
                        .shimmerModifier(shimmer),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {}
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            items(items, key = { it }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .shimmerModifier(shimmer),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {}
            }
        }
    }
}

@Composable
private fun LoadMoreIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.width(18.dp).height(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Загрузка...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwipeableOfferCard(
    offer: UserOfferCardUi,
    enabled: Boolean,
    onSwipeAction: (UserOfferAction) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val primaryAction = when (offer.status) {
        UserOfferStatus.ACTIVE -> UserOfferAction.PAUSE
        UserOfferStatus.PAUSED -> UserOfferAction.ACTIVATE
        else -> null
    }
    val secondaryAction = if (offer.status == UserOfferStatus.ACTIVE) {
        UserOfferAction.MARK_FINISHED
    } else {
        null
    }
    if (!enabled) {
        content(Modifier)
        return
    }

    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    val secondaryThreshold = threshold * 1.7f
    val maxOffset = threshold * 2.4f

    val dragState = rememberDraggableState { delta ->
        val next = (offset.value + delta).coerceIn(-maxOffset, maxOffset)
        scope.launch { offset.snapTo(next) }
    }

    fun resolveAction(): UserOfferAction? {
        val value = offset.value
        return when {
            value > secondaryThreshold && secondaryAction != null -> secondaryAction
            value > threshold && primaryAction != null -> primaryAction
            value < -threshold -> UserOfferAction.DELETE
            else -> null
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        SwipeBackground(
            offset = offset.value,
            primaryAction = primaryAction,
            secondaryAction = secondaryAction,
            primaryThreshold = threshold,
            secondaryThreshold = secondaryThreshold,
        )
        content(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        val action = resolveAction()
                        scope.launch {
                            offset.animateTo(0f, animationSpec = tween(180))
                        }
                        action?.let { onSwipeAction(it) }
                    },
                )
        )
    }
}

@Composable
private fun SwipeBackground(
    offset: Float,
    primaryAction: UserOfferAction?,
    secondaryAction: UserOfferAction?,
    primaryThreshold: Float,
    secondaryThreshold: Float,
) {
    val action = when {
        offset < 0 -> UserOfferAction.DELETE
        offset > secondaryThreshold && secondaryAction != null -> secondaryAction
        offset > primaryThreshold -> primaryAction
        else -> null
    }
    val label = action?.let { swipeActionLabel(it) } ?: ""
    val color = action?.let { swipeActionColor(it) } ?: Color.Transparent
    if (action == null) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color.copy(alpha = 0.18f)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier
                .align(if (offset < 0) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun Modifier.shimmerModifier(shimmer: ShimmerTask): Modifier = shimmer.run {
    this@shimmerModifier.shimmer(enabled = true)
}

fun statusLabel(status: UserOfferStatus): String = when (status) {
    UserOfferStatus.ACTIVE -> "Активные"
    UserOfferStatus.PAUSED -> "На паузе"
    UserOfferStatus.DRAFT -> "Черновики"
    UserOfferStatus.FINISHED -> "Завершенные"
    UserOfferStatus.ARCHIVED -> "Архив"
}

private fun quickFilterLabel(filter: UserOfferQuickFilter): String = when (filter) {
    UserOfferQuickFilter.WITH_PHOTO -> "С фото"
    UserOfferQuickFilter.WITHOUT_PHOTO -> "Без фото"
    UserOfferQuickFilter.EXPIRING_SOON -> "Истекает скоро"
    UserOfferQuickFilter.ON_MODERATION -> "На модерации"
    UserOfferQuickFilter.PUBLISH_ERROR -> "Ошибка публикации"
    UserOfferQuickFilter.PROMOTED -> "Продвигается"
}

private fun swipeActionLabel(action: UserOfferAction): String = when (action) {
    UserOfferAction.DELETE -> "Удалить"
    UserOfferAction.PAUSE -> "Пауза"
    UserOfferAction.ACTIVATE -> "Активировать"
    UserOfferAction.MARK_FINISHED -> "Продать"
    else -> "Действие"
}

@Composable
private fun swipeActionColor(action: UserOfferAction): Color = when (action) {
    UserOfferAction.DELETE -> MaterialTheme.colorScheme.error
    UserOfferAction.PAUSE -> MaterialTheme.colorScheme.secondary
    UserOfferAction.ACTIVATE -> MaterialTheme.colorScheme.primary
    UserOfferAction.MARK_FINISHED -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
