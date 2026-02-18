package com.example.shoppingassistant.feature.pages.trackeditems

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.shoppingassistant.domain.tracks.TrackEvent
import com.example.shoppingassistant.domain.tracks.TrackEventType
import com.example.shoppingassistant.feature.ui.cards.formatUpdatedAtText
import com.example.shoppingassistant.feature.ui.components.GoodyChip
import org.koin.androidx.compose.koinViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TrackEventsPage(
    trackId: String,
    onBack: () -> Unit,
    viewModel: TrackEventsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(trackId) {
        viewModel.load(trackId)
    }

    LaunchedEffect(state.actionMessage) {
        val message = state.actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeActionMessage()
    }

    val unreadCount = (state.events as? TrackEventsLoadState.Content)
        ?.items
        ?.count { !it.isRead }
        ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.trackTitle.ifBlank { "События" },
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
                    if (unreadCount > 0) {
                        TextButton(onClick = { viewModel.markAllRead() }) {
                            Text("Прочитать все")
                        }
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
            when (val load = state.events) {
                TrackEventsLoadState.Loading -> {
                    repeat(4) { EventSkeleton() }
                }
                TrackEventsLoadState.Empty -> {
                    EmptyEventsBlock()
                }
                is TrackEventsLoadState.Error -> {
                    ErrorEventsBlock(message = load.message, onRetry = { viewModel.load(trackId) })
                }
                is TrackEventsLoadState.Content -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(load.items, key = { it.id }) { event ->
                            EventRow(event = event, onClick = { viewModel.markRead(event) })
                        }
                        if (state.canLoadMore) {
                            item {
                                Button(
                                    onClick = { viewModel.loadMore() },
                                    enabled = !state.isLoadingMore,
                                    modifier = Modifier.fillMaxWidth().height(44.dp),
                                ) {
                                    Text(if (state.isLoadingMore) "Загрузка…" else "Показать ещё")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(
    event: TrackEvent,
    onClick: () -> Unit,
) {
    val updatedAt = formatUpdatedAtText(event.createdAt)
    val typeLabel = eventTypeLabel(event.type)

    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!event.isRead) {
                    GoodyChip(label = "Новое", selected = true, onClick = onClick)
                }
            }
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            event.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!updatedAt.isNullOrBlank()) {
                Text(
                    text = updatedAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun eventTypeLabel(type: TrackEventType): String = when (type) {
    TrackEventType.PRICE_DROP -> "Снижение цены"
    TrackEventType.NEW_OFFER -> "Новое предложение"
    TrackEventType.TARGET_HIT -> "Совпадение"
    TrackEventType.OTHER -> "Событие"
}

@Composable
private fun EmptyEventsBlock() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Нет событий", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Мы покажем обновления, когда появятся новые предложения.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorEventsBlock(message: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Не удалось загрузить", style = MaterialTheme.typography.titleMedium)
        Text(text = message, style = MaterialTheme.typography.bodySmall)
        Button(onClick = onRetry, modifier = Modifier.height(44.dp)) {
            Text("Повторить")
        }
    }
}

@Composable
private fun EventSkeleton() {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp),
    ) {}
}
