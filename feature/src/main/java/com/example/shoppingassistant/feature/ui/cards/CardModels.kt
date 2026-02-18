package com.example.shoppingassistant.feature.ui.cards

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

@Immutable
data class CardMediaItem(
    val url: String,
    val contentDescription: String? = null,
)

@Immutable
data class CardBadge(
    val label: String,
    val priority: Int = 0,
)

@Immutable
data class CardTrustData(
    val updatedAtText: String? = null,
    val sourceText: String? = null,
    val ratingText: String? = null,
)

@Immutable
data class OfferCardUi(
    val id: String,
    val title: String,
    val priceText: String,
    val media: List<CardMediaItem>,
    val photoCount: Int,
    val locationText: String? = null,
    val isSaved: Boolean,
    val badges: List<CardBadge> = emptyList(),
    val trust: CardTrustData? = null,
    val onOpenDetails: () -> Unit,
    val onToggleSave: () -> Unit,
    val onOverflowAction: (OfferOverflowAction) -> Unit,
    val onOverflowOpen: (() -> Unit)? = null,
    val onPhotoPeekOpen: ((Int) -> Unit)? = null,
    val onPhotoPeekSwipe: ((Int) -> Unit)? = null,
    val onPhotoPeekOpenDetails: ((Int) -> Unit)? = null,
)

@Immutable
data class CompactCardUi(
    val id: String,
    val title: String,
    val priceText: String,
    val media: List<CardMediaItem>,
    val photoCount: Int,
    val badge: CardBadge? = null,
    val isSaved: Boolean = false,
    val showSave: Boolean = false,
    val onOpenDetails: () -> Unit,
    val onToggleSave: (() -> Unit)? = null,
    val onOverflowAction: ((CompactOverflowAction) -> Unit)? = null,
    val onOverflowOpen: (() -> Unit)? = null,
)

@Immutable
data class ScenarioCardUi(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val progressText: String? = null,
    val updatedAtText: String? = null,
    val onContinue: () -> Unit,
    val onOverflowAction: (ScenarioOverflowAction) -> Unit,
    val onOverflowOpen: (() -> Unit)? = null,
)
