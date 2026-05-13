package com.example.shoppingassistant.feature.pages.trackeditems

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.shoppingassistant.domain.tracks.FreshnessState
import com.example.shoppingassistant.domain.tracks.RankedOffer
import com.example.shoppingassistant.domain.tracks.SourceStamp
import com.example.shoppingassistant.domain.tracks.Track
import com.example.shoppingassistant.domain.tracks.TrackFilters
import com.example.shoppingassistant.domain.tracks.TrackOfferSort
import com.example.shoppingassistant.domain.tracks.TrackTop10
import com.example.shoppingassistant.feature.ui.cards.CardBadge
import com.example.shoppingassistant.feature.ui.cards.CardBadgesRow
import com.example.shoppingassistant.feature.ui.cards.CardDensity
import com.example.shoppingassistant.feature.ui.cards.formatPriceText
import com.example.shoppingassistant.feature.ui.cards.formatUpdatedAtText
import com.example.shoppingassistant.feature.ui.components.GoodyChip
import com.example.shoppingassistant.feature.ui.state.SystemNoticeCard
import com.example.shoppingassistant.feature.ui.state.SystemNoticeTone
import org.koin.androidx.compose.koinViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TrackTop10Page(
    trackId: String,
    onBack: () -> Unit,
    onEditTrack: (String) -> Unit = {},
    viewModel: TrackTop10ViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSources by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()

    LaunchedEffect(trackId) {
        viewModel.load(trackId)
    }

    DisposableEffect(lifecycleOwner, trackId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.load(trackId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.actionMessage) {
        val message = state.actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeActionMessage()
    }

    LaunchedEffect(trackId, listState, state.offersCanLoadMore, state.offersLoading, state.offers.size) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val total = layout.totalItemsCount
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible to total
        }.collect { (lastVisible, total) ->
            if (total <= 0) return@collect
            if (state.offersCanLoadMore && !state.offersLoading && lastVisible >= total - 4) {
                viewModel.loadMoreOffers(trackId)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.track?.title ?: "Результаты трека",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh(trackId) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Обновить")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val top10Snapshot = when (val load = state.top10) {
                is Top10LoadState.Data -> load.top10
                is Top10LoadState.Empty -> load.top10
                else -> null
            }
            val isRefreshing = when (val load = state.top10) {
                is Top10LoadState.Data -> load.isRefreshing
                is Top10LoadState.Empty -> load.isRefreshing
                else -> false
            }
            val lastError = when (val load = state.top10) {
                is Top10LoadState.Data -> load.lastError
                is Top10LoadState.Empty -> load.lastError
                else -> null
            }

            state.track?.let { track ->
                Top10Subheader(
                    track = track,
                    top10 = top10Snapshot,
                    freshness = state.freshness,
                    isRefreshing = isRefreshing,
                    lastError = lastError,
                    onSourcesClick = { showSources = true },
                    onEditTrack = { onEditTrack(trackId) },
                )
            }
            if (state.isOffline && top10Snapshot != null) {
                OfflineWarning()
            }

            when (val load = state.top10) {
                Top10LoadState.Loading -> repeat(3) { OfferSkeleton() }
                is Top10LoadState.FatalError -> {
                    ErrorTop10Block(message = load.message, onRetry = { viewModel.load(trackId) })
                }
                is Top10LoadState.Data, is Top10LoadState.Empty -> {
                    when (state.freshness?.state) {
                        FreshnessState.STALE -> StaleWarning(onRefresh = { viewModel.refresh(trackId) })
                        FreshnessState.EXPIRED -> ExpiredWarning(onRefresh = { viewModel.refresh(trackId) })
                        else -> Unit
                    }

                    val topItems = when (load) {
                        is Top10LoadState.Data -> load.top10.items
                        is Top10LoadState.Empty -> load.top10.items
                        else -> emptyList()
                    }

                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item { Text("ТОП", style = MaterialTheme.typography.titleSmall) }
                        if (topItems.isEmpty()) {
                            item { EmptyTop10Block() }
                        } else {
                            items(topItems) { offer ->
                                RankedOfferCard(offer = offer)
                            }
                        }

                        item {
                            OffersSectionHeader(
                                sort = state.offersSort,
                                onSortSelected = { selected -> viewModel.setOffersSort(trackId, selected) },
                            )
                        }

                        if (state.offers.isEmpty() && state.offersLoading) {
                            items(3) { OfferSkeleton() }
                        } else if (state.offers.isEmpty()) {
                            item {
                                Text(
                                    text = "Нет предложений по выбранной цели",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            items(state.offers) { offer ->
                                RankedOfferCard(offer = offer)
                            }
                        }

                        if (state.offersLoading && state.offers.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Догружаем предложения…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (!state.offersError.isNullOrBlank()) {
                            item {
                                ErrorTop10Block(
                                    message = state.offersError ?: "Не удалось загрузить предложения",
                                    onRetry = { viewModel.loadMoreOffers(trackId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSources) {
        val stamps = (state.top10 as? Top10LoadState.Data)?.top10?.sourceStamps
            ?: (state.top10 as? Top10LoadState.Empty)?.top10?.sourceStamps
            ?: emptyList()
        SourcesSheet(items = stamps, onDismiss = { showSources = false })
    }
}

@Composable
private fun OffersSectionHeader(
    sort: TrackOfferSort,
    onSortSelected: (TrackOfferSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Все предложения", style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = { expanded = true }) {
            Text(sort.toLabel())
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TrackOfferSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.toLabel()) },
                    onClick = {
                        expanded = false
                        onSortSelected(option)
                    },
                )
            }
        }
    }
}

private fun TrackOfferSort.toLabel(): String = when (this) {
    TrackOfferSort.PRICE_ASC -> "Цена"
    TrackOfferSort.NEWEST -> "Новизна"
    TrackOfferSort.SELLER_RATING_DESC -> "Рейтинг продавца"
}

@Composable
private fun Top10Subheader(
    track: Track,
    top10: TrackTop10?,
    freshness: com.example.shoppingassistant.domain.tracks.Freshness?,
    isRefreshing: Boolean,
    lastError: String?,
    onSourcesClick: () -> Unit,
    onEditTrack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val updatedAt = top10?.computedAt?.let { formatUpdatedAtText(it) }
            Text(
                text = updatedAt?.let { "Обновлено $it" } ?: "Нет обновлений",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onSourcesClick) {
                Icon(Icons.Outlined.Info, contentDescription = "Источники")
            }
        }
        if (isRefreshing) {
            Text(
                text = "Обновляется…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!lastError.isNullOrBlank()) {
            Text(
                text = lastError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (freshness != null) {
            Text(
                text = "TTL: ${freshness.ttlSec / 60} мин",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Источники: ${top10?.sourceStamps?.size ?: 0}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val chips = buildTrackFilterChips(track.filters)
        if (chips.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditTrack() },
            ) {
                chips.forEach { label ->
                    GoodyChip(label = label, selected = true, onClick = onEditTrack)
                }
            }
        }
        top10?.explanation?.summary?.takeIf { it.isNotBlank() }?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun buildTrackFilterChips(filters: TrackFilters): List<String> {
    val base = listOfNotNull(
        filters.region?.trim()?.takeIf { it.isNotBlank() },
        filters.delivery?.trim()?.takeIf { it.isNotBlank() },
        filters.condition?.trim()?.takeIf { it.isNotBlank() },
        filters.seller?.trim()?.takeIf { it.isNotBlank() },
    )
    val extras = filters.extra.entries
        .mapNotNull { entry ->
            val key = entry.key.trim()
            val value = entry.value.trim()
            if (key.isBlank() || value.isBlank()) null else "$key: $value"
        }
        .sortedBy { it.lowercase() }
        .take(2)
    return base + extras
}

@Composable
private fun RankedOfferCard(offer: RankedOffer) {
    val context = LocalContext.current
    val priceText = formatPriceText(offer.price.toMajor())
    val deliveryText = offer.delivery?.let { delivery ->
        val price = delivery.price?.toMajor()?.let { formatPriceText(it) }
        val eta = delivery.etaDays?.let { "$it дн." }
        listOfNotNull(price, eta).joinToString(" • ").ifBlank { null }
    }
    val trustText = offer.trustScore?.let { "Trust ${"%.1f".format(it)}" }
    val distanceText = offer.distanceKm?.let { "${"%.1f".format(it)} км" }
    val badges = offer.badges.map { CardBadge(label = it.label, priority = it.priority) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = priceText, style = MaterialTheme.typography.titleMedium)
            if (!deliveryText.isNullOrBlank()) {
                Text(
                    text = deliveryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Продавец ${offer.sellerId}", style = MaterialTheme.typography.bodySmall)
                trustText?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
                distanceText?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
            }
            CardBadgesRow(badges = badges, maxCount = 3, density = CardDensity.Dense)
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(offer.deeplink))
                    context.startActivity(intent)
                },
            ) {
                Text("Перейти")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SourcesSheet(items: List<SourceStamp>, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Источники", style = MaterialTheme.typography.titleMedium)
            if (items.isEmpty()) {
                Text(text = "Нет данных об источниках", style = MaterialTheme.typography.bodySmall)
            } else {
                items.forEach { stamp ->
                    val updatedAt = formatUpdatedAtText(stamp.fetchedAt)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = stamp.sourceName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "TTL ${stamp.ttlSec} сек • ${updatedAt ?: "нет времени"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTop10Block() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Нет подходящих предложений", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Попробуйте ослабить фильтры или обновить список.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorTop10Block(message: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Не удалось загрузить", style = MaterialTheme.typography.titleMedium)
        Text(text = message, style = MaterialTheme.typography.bodySmall)
        Button(onClick = onRetry, modifier = Modifier.height(44.dp)) {
            Text("Повторить")
        }
    }
}

@Composable
private fun StaleWarning(onRefresh: () -> Unit) {
    SystemNoticeCard(
        body = "Данные могли устареть",
        tone = SystemNoticeTone.Warning,
        compact = true,
        modifier = Modifier.fillMaxWidth(),
        actionLabel = "Обновить",
        onAction = onRefresh,
    )
}

@Composable
private fun OfflineWarning() {
    SystemNoticeCard(
        body = "Офлайн: показан последний кеш",
        tone = SystemNoticeTone.Info,
        compact = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ExpiredWarning(onRefresh: () -> Unit) {
    SystemNoticeCard(
        body = "Данные устарели",
        tone = SystemNoticeTone.Error,
        compact = true,
        modifier = Modifier.fillMaxWidth(),
        actionLabel = "Обновить",
        onAction = onRefresh,
    )
}

@Composable
private fun OfferSkeleton() {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp),
    ) {}
}
