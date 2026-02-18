package com.example.shoppingassistant.feature.pages.offers.tasks

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.shoppingassistant.core.data.ExplainedItem
import com.example.shoppingassistant.domain.model.ProductDto
import com.example.shoppingassistant.feature.ui.animations.rememberListAnimationsTask
import com.example.shoppingassistant.feature.ui.cards.CardActionRules
import com.example.shoppingassistant.feature.ui.cards.CardBadge
import com.example.shoppingassistant.feature.ui.cards.CardDensity
import com.example.shoppingassistant.feature.ui.cards.CardMediaItem
import com.example.shoppingassistant.feature.ui.cards.CardTrustData
import com.example.shoppingassistant.feature.ui.cards.CompactCard
import com.example.shoppingassistant.feature.ui.cards.CompactCardUi
import com.example.shoppingassistant.feature.ui.cards.OfferCard
import com.example.shoppingassistant.feature.ui.cards.OfferCardUi
import com.example.shoppingassistant.feature.ui.cards.OfferOverflowAction
import com.example.shoppingassistant.feature.ui.cards.formatLocationText
import com.example.shoppingassistant.feature.ui.cards.formatPriceText
import com.example.shoppingassistant.feature.ui.cards.formatRatingText
import com.example.shoppingassistant.feature.ui.cards.formatUpdatedAtText
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.Locale

data class OfferNominations(
    val bestPrice: Set<String> = emptySet(),
    val bestRating: Set<String> = emptySet(),
    val nearest: Set<String> = emptySet(),
)

fun determineOfferNominations(items: List<ExplainedItem>): OfferNominations {
    val withPrice = items.filter { it.dto.price != null }
    val minPrice = withPrice.minOfOrNull { it.dto.price ?: Double.MAX_VALUE }
    val bestPriceIds = if (minPrice != null) {
        withPrice.filter { it.dto.price == minPrice }.map { it.dto.id }.toSet()
    } else emptySet()

    val withRating = items.filter { it.dto.sellerRating != null }
    val maxRating = withRating.maxOfOrNull { it.dto.sellerRating ?: 0.0 }
    val bestRatingIds = if (maxRating != null) {
        withRating.filter { it.dto.sellerRating == maxRating }.map { it.dto.id }.toSet()
    } else emptySet()

    val withDelivery = items.filter { it.dto.deliveryTime != null }
    val minDelivery = withDelivery.minOfOrNull { it.dto.deliveryTime ?: Int.MAX_VALUE }
    val nearestIds = if (minDelivery != null) {
        withDelivery.filter { it.dto.deliveryTime == minDelivery }.map { it.dto.id }.toSet()
    } else emptySet()

    return OfferNominations(
        bestPrice = bestPriceIds,
        bestRating = bestRatingIds,
        nearest = nearestIds,
    )
}

private fun cardDensityForStyle(style: OfferCardStyle): CardDensity = when (style) {
    OfferCardStyle.Flat -> CardDensity.Dense
    OfferCardStyle.Tiles -> CardDensity.Regular
}

private fun buildOfferBadges(
    item: ExplainedItem,
    nominations: OfferNominations,
    activeBadge: OffersBadge,
    rankPosition: Int,
): List<CardBadge> {
    val dto = item.dto
    val scopeLabel = deriveScopeLabel(dto.sellerCountry)
    val badges = mutableListOf<CardBadge>()

    fun addBadge(label: String, priority: Int) {
        if (label.isBlank()) return
        if (badges.any { it.label.equals(label, ignoreCase = true) }) return
        badges.add(CardBadge(label = label, priority = priority))
    }

    if (nominations.bestPrice.contains(dto.id)) {
        addBadge("Лучшая цена $scopeLabel", 3)
    }
    if (activeBadge == OffersBadge.Nearest && nominations.nearest.contains(dto.id)) {
        addBadge("Самый близкий $scopeLabel", 3)
    }
    if (nominations.bestRating.contains(dto.id)) {
        addBadge("Лучший рейтинг $scopeLabel", 3)
    }
    if (rankPosition in 1..3) {
        addBadge("Топ $rankPosition", 2)
    }
    if (activeBadge != OffersBadge.None) {
        item.reasons.take(2).forEach { reason ->
            addBadge(reason, 2)
        }
    }
    buildTechBadges(dto).forEach { label ->
        addBadge(label, 1)
    }

    return badges
}

private fun buildOfferCardUi(
    item: ExplainedItem,
    isSaved: Boolean,
    badges: List<CardBadge>,
    onToggleSave: () -> Unit,
    onOpenDetails: () -> Unit,
    onOverflowAction: (OfferOverflowAction) -> Unit,
): OfferCardUi {
    val dto = item.dto
    val title = dto.title.ifBlank { dto.brand ?: "Offer" }
    val trust = CardTrustData(
        updatedAtText = formatUpdatedAtText(dto.updatedAt),
        sourceText = dto.sourceName?.takeIf { it.isNotBlank() },
        ratingText = formatRatingText(dto.sellerRating),
    )
    return OfferCardUi(
        id = dto.id,
        title = title,
        priceText = formatPriceText(dto.price),
        media = dto.imageUrls.map { url -> CardMediaItem(url = url, contentDescription = title) },
        photoCount = dto.imageUrls.size,
        locationText = formatLocationText(dto.sellerCity, dto.sellerCountry),
        isSaved = isSaved,
        badges = badges,
        trust = trust,
        onOpenDetails = onOpenDetails,
        onToggleSave = onToggleSave,
        onOverflowAction = onOverflowAction,
    )
}

private fun buildCompactCardUi(
    item: ExplainedItem,
    badge: CardBadge?,
    onOpenDetails: () -> Unit,
): CompactCardUi {
    val dto = item.dto
    val title = dto.title.ifBlank { dto.brand ?: "Offer" }
    return CompactCardUi(
        id = dto.id,
        title = title,
        priceText = formatPriceText(dto.price),
        media = dto.imageUrls.map { url -> CardMediaItem(url = url, contentDescription = title) },
        photoCount = dto.imageUrls.size,
        badge = badge,
        isSaved = false,
        showSave = false,
        onOpenDetails = onOpenDetails,
    )
}

private val PhotoHeight = 112.dp

@Composable
fun OffersListHorizontal(
    items: List<ExplainedItem>,
    onSelect: (ExplainedItem) -> Unit,
    onAction: (OfferCardMenuAction, ExplainedItem) -> Unit,
    onHide: (ExplainedItem, Float, SwipeResult) -> Unit,
    nominations: OfferNominations,
    activeBadge: OffersBadge,
    cardStyle: OfferCardStyle,
    savedIds: Set<String> = emptySet(),
    onToggleSave: (ExplainedItem) -> Unit = {},
    onOverflow: (ExplainedItem, OfferOverflowAction) -> Unit = { _, _ -> },
    showLoadMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
) {
    val animations = rememberListAnimationsTask()
    val listModifier = if (cardStyle == OfferCardStyle.Flat) {
        Modifier.background(MaterialTheme.colorScheme.surface)
    } else {
        Modifier
    }
    val density = cardDensityForStyle(cardStyle)

    LazyRow(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .then(listModifier),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        itemsIndexed(items = items, key = { _, it -> it.dto.id }) { index, item ->
            AnimatedVisibility(
                visible = true,
                enter = animations.itemEnter(),
                exit = animations.itemExit(),
            ) {
                DismissibleOfferCard(
                    form = OfferCardForm.Horizontal,
                    onHide = { progress, result -> onHide(item, progress, result) },
                    modifier = Modifier,
                ) { swipeModifier ->
                    val badges = buildOfferBadges(
                        item = item,
                        nominations = nominations,
                        activeBadge = activeBadge,
                        rankPosition = index + 1,
                    )
                    val topBadge = badges.maxByOrNull { it.priority }
                    val ui = buildCompactCardUi(
                        item = item,
                        badge = topBadge,
                        onOpenDetails = { onSelect(item) },
                    )
                    CompactCard(
                        ui = ui,
                        modifier = swipeModifier.width(260.dp),
                        density = density,
                    )
                }
            }
        }

        if (showLoadMore) {
            item(key = "load_more") {
                LoadMoreCard(
                    horizontal = true,
                    isLoading = isLoadingMore,
                    onClick = onLoadMore,
                )
            }
        }
    }
}

@Composable
fun OffersListVertical(
    items: List<ExplainedItem>,
    onSelect: (ExplainedItem) -> Unit,
    onAction: (OfferCardMenuAction, ExplainedItem) -> Unit,
    onHide: (ExplainedItem, Float, SwipeResult) -> Unit,
    nominations: OfferNominations,
    activeBadge: OffersBadge,
    cardStyle: OfferCardStyle,
    savedIds: Set<String> = emptySet(),
    onToggleSave: (ExplainedItem) -> Unit = {},
    onOverflow: (ExplainedItem, OfferOverflowAction) -> Unit = { _, _ -> },
    photoPeekEnabled: Boolean = false,
    showLoadMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
) {
    val animations = rememberListAnimationsTask()
    val listModifier = if (cardStyle == OfferCardStyle.Flat) {
        Modifier.background(MaterialTheme.colorScheme.surface)
    } else {
        Modifier
    }
    val density = cardDensityForStyle(cardStyle)

    LazyColumn(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .then(listModifier),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        itemsIndexed(items = items, key = { _, it -> it.dto.id }) { index, item ->
            AnimatedVisibility(
                visible = true,
                enter = animations.itemEnter(),
                exit = animations.itemExit(),
            ) {
                DismissibleOfferCard(
                    form = OfferCardForm.Vertical,
                    onHide = { progress, result -> onHide(item, progress, result) },
                    modifier = Modifier,
                ) { swipeModifier ->
                    val badges = buildOfferBadges(
                        item = item,
                        nominations = nominations,
                        activeBadge = activeBadge,
                        rankPosition = index + 1,
                    )
                    val ui = buildOfferCardUi(
                        item = item,
                        isSaved = savedIds.contains(item.dto.id),
                        badges = badges,
                        onToggleSave = { onToggleSave(item) },
                        onOpenDetails = { onSelect(item) },
                        onOverflowAction = { action -> onOverflow(item, action) },
                    )
                    val overflowActions = CardActionRules.offerOverflowActions(
                        includeCopyLink = !item.dto.externalUrl.isNullOrBlank(),
                    )
                    OfferCard(
                        ui = ui,
                        modifier = swipeModifier.fillMaxWidth(),
                        density = density,
                        photoPeekEnabled = photoPeekEnabled,
                        overflowActions = overflowActions,
                    )
                }
            }
        }

        if (showLoadMore) {
            item(key = "load_more") {
                LoadMoreCard(
                    horizontal = false,
                    isLoading = isLoadingMore,
                    onClick = onLoadMore,
                )
            }
        }
    }
}

private enum class OfferCardForm { Horizontal, Vertical }
enum class SwipeResult { Hide, Favorite }

@Composable
private fun DismissibleOfferCard(
    form: OfferCardForm,
    onHide: (Float, SwipeResult) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var direction by remember { mutableStateOf<SwipeResult?>(null) }
    var dragging by remember { mutableStateOf(false) }

    val thresholdPx = with(LocalDensity.current) { 120.dp.toPx() }
    val progress = (abs(offset.value) / thresholdPx).coerceIn(0f, 1.2f)
    val orientation = when (form) {
        OfferCardForm.Vertical -> Orientation.Horizontal
        OfferCardForm.Horizontal -> Orientation.Vertical
    }

    fun resetDragState() {
        direction = null
        dragging = false
    }

    suspend fun animateBack() {
        offset.animateTo(0f, animationSpec = spring())
        resetDragState()
    }

    val dragState = rememberDraggableState { rawDelta ->
        val currentDir = direction ?: run {
            val decided = when (form) {
                OfferCardForm.Vertical -> if (rawDelta < 0) SwipeResult.Hide else SwipeResult.Favorite
                OfferCardForm.Horizontal -> if (rawDelta > 0) SwipeResult.Hide else SwipeResult.Favorite
            }
            direction = decided
            dragging = true
            decided
        }

        val filteredDelta = when (form) {
            OfferCardForm.Vertical -> when (currentDir) {
                SwipeResult.Hide -> minOf(rawDelta, 0f)
                SwipeResult.Favorite -> maxOf(rawDelta, 0f)
            }
            OfferCardForm.Horizontal -> when (currentDir) {
                SwipeResult.Hide -> maxOf(rawDelta, 0f)
                SwipeResult.Favorite -> minOf(rawDelta, 0f)
            }
        }

        val clamped = (offset.value + filteredDelta)
            .coerceIn(-thresholdPx * 2.2f, thresholdPx * 2.2f)
        scope.launch { offset.snapTo(clamped) }
    }

    Box(
        modifier = modifier
            .offset {
                when (form) {
                    OfferCardForm.Horizontal -> IntOffset(0, offset.value.roundToInt())
                    OfferCardForm.Vertical -> IntOffset(offset.value.roundToInt(), 0)
                }
            }
            .draggable(
                state = dragState,
                orientation = orientation,
                onDragStarted = {
                    direction = null
                    dragging = true
                },
                onDragStopped = {
                    val activeDir = direction
                    val dist = abs(offset.value)
                    if (activeDir != null && dist > thresholdPx) {
                        val finalProgress = progress.coerceIn(0f, 1f)
                        when (activeDir) {
                            SwipeResult.Hide -> {
                                val target = when (form) {
                                    OfferCardForm.Vertical -> -thresholdPx * 2.6f
                                    OfferCardForm.Horizontal -> thresholdPx * 2.6f
                                }
                                scope.launch {
                                    offset.animateTo(target, animationSpec = tween(220))
                                    onHide(finalProgress, activeDir)
                                    offset.snapTo(0f)
                                    resetDragState()
                                }
                            }
                            SwipeResult.Favorite -> {
                                scope.launch {
                                    onHide(finalProgress, activeDir)
                                    animateBack()
                                }
                            }
                        }
                    } else {
                        scope.launch { animateBack() }
                    }
                },
            ),
    ) {
        content(
            Modifier.graphicsLayer {
                if (dragging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val blurRadius = 16f * progress.coerceIn(0f, 1f)
                    renderEffect = RenderEffect
                        .createBlurEffect(
                            blurRadius,
                            blurRadius,
                            Shader.TileMode.CLAMP,
                        )
                        .asComposeRenderEffect()
                } else {
                    renderEffect = null
                }
            },
        )

        val activeDir = direction
        if (dragging && activeDir != null) {
            val baseColor = if (activeDir == SwipeResult.Hide) Color(0xFFFF6B6B) else Color(0xFF4CAF50)
            val edgeAlpha = (0.25f + progress.coerceIn(0f, 1f) * 0.25f).coerceIn(0f, 0.45f)
            val edgeWidth = 36.dp

            Box(modifier = Modifier.matchParentSize()) {
                when (form) {
                    OfferCardForm.Vertical -> {
                        val brush = if (activeDir == SwipeResult.Hide) {
                            Brush.horizontalGradient(
                                colors = listOf(baseColor.copy(alpha = edgeAlpha), Color.Transparent),
                            )
                        } else {
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, baseColor.copy(alpha = edgeAlpha)),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(edgeWidth)
                                .background(brush)
                                .align(if (activeDir == SwipeResult.Hide) Alignment.CenterStart else Alignment.CenterEnd),
                        )
                    }
                    OfferCardForm.Horizontal -> {
                        val brush = if (activeDir == SwipeResult.Hide) {
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, baseColor.copy(alpha = edgeAlpha)),
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(baseColor.copy(alpha = edgeAlpha), Color.Transparent),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(edgeWidth)
                                .background(brush)
                                .align(if (activeDir == SwipeResult.Hide) Alignment.BottomCenter else Alignment.TopCenter),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadMoreCard(
    horizontal: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sizeModifier = if (horizontal) {
        Modifier.width(220.dp).height(PhotoHeight)
    } else {
        Modifier
            .fillMaxWidth()
            .height(56.dp)
    }

    Card(
        modifier = modifier
            .then(sizeModifier)
            .clickable(enabled = !isLoading) { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (horizontal) 6.dp else 2.dp,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isLoading) "Загружаем..." else "Показать ещё",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun buildTechBadges(dto: ProductDto): List<String> {
    val result = mutableListOf<String>()
    val text = listOfNotNull(dto.title, dto.brand, dto.model)
        .joinToString(" ")
        .lowercase()

    val colors = listOf(
        "черный" to "Чёрный",
        "чёрный" to "Чёрный",
        "black" to "Чёрный",
        "белый" to "Белый",
        "white" to "Белый",
        "синий" to "Синий",
        "blue" to "Синий",
        "красный" to "Красный",
        "red" to "Красный",
        "зеленый" to "Зелёный",
        "зелёный" to "Зелёный",
        "green" to "Зелёный",
        "серый" to "Серый",
        "grey" to "Серый",
        "gray" to "Серый",
    )
    colors.firstOrNull { (keyword, _) -> text.contains(keyword) }
        ?.let { (_, label) -> result.add(label) }

    val memoryRegex = Regex("""(\d+)\s*(гб|gb)""", RegexOption.IGNORE_CASE)
    memoryRegex.find(text)?.let { match ->
        val value = match.groupValues[1]
        result.add("$value ГБ")
    }

    val condition = when {
        text.contains("б/у") || text.contains("бу") || text.contains("used") -> "Б/у"
        text.contains("новый") || text.contains("new") -> "Новый"
        else -> null
    }
    if (condition != null) result.add(condition)

    val weightRegex = Regex("""(\d+(?:[.,]\d+)?)\s*(кг|kg)""", RegexOption.IGNORE_CASE)
    weightRegex.find(text)?.let { match ->
        val value = match.groupValues[1].replace(',', '.')
        val unit = match.groupValues[2].lowercase()
        val normalizedUnit = if (unit == "kg") "кг" else unit
        result.add("$value $normalizedUnit")
    }

    dto.deliveryTime?.let { days ->
        if (days > 0) {
            result.add("Доставка ~ $days дн.")
        }
    }

    dto.sellerBadges
        .filterNot { it.equals("VERIFIED", ignoreCase = true) }
        .mapNotNull { normalizeBadgeLabel(it) }
        .forEach { result.add(it) }

    return result.distinct()
}

private fun normalizeBadgeLabel(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val cleaned = raw
        .replace('_', ' ')
        .trim()
    if (cleaned.isEmpty()) return null

    val locale = Locale.getDefault()
    return cleaned.replaceFirstChar { ch ->
        if (ch.isLowerCase()) {
            ch.titlecase(locale)
        } else {
            ch.toString()
        }
    }
}

private fun deriveScopeLabel(countryCode: String?): String {
    val country = countryNameFull(countryCode)
    return if (!country.isNullOrBlank()) {
        "в $country"
    } else {
        "в мире"
    }
}

private fun countryNameFull(country: String?): String? {
    if (country.isNullOrBlank()) return null
    val locale = Locale.getDefault()
    return try {
        Locale("", country).getDisplayCountry(locale).takeIf { it.isNotBlank() }
            ?: Locale("", country).displayCountry.takeIf { it.isNotBlank() }
            ?: country
    } catch (_: Exception) {
        country
    }
}
