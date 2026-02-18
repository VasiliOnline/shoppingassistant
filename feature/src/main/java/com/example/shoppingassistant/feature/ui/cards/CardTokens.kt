package com.example.shoppingassistant.feature.ui.cards

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class CardDensity {
    Regular,
    Dense,
}

@Immutable
data class CardLayoutMetrics(
    val contentPadding: Dp,
    val rowGap: Dp,
    val mediaAspectRatio: Float,
    val cornerRadius: Dp,
    val badgeHeight: Dp,
    val iconSize: Dp,
    val iconHitTarget: Dp,
)

object CardTokens {
    private val ContentPaddingRegular = 12.dp
    private val ContentPaddingDense = 10.dp
    private val RowGap = 6.dp
    private val CornerRadius = 16.dp
    private val BadgeHeight = 24.dp
    private val IconSize = 22.dp
    private val IconHitTarget = 48.dp

    const val MediaAspectRatio: Float = 4f / 3f

    fun metrics(density: CardDensity): CardLayoutMetrics = CardLayoutMetrics(
        contentPadding = when (density) {
            CardDensity.Regular -> ContentPaddingRegular
            CardDensity.Dense -> ContentPaddingDense
        },
        rowGap = RowGap,
        mediaAspectRatio = MediaAspectRatio,
        cornerRadius = CornerRadius,
        badgeHeight = BadgeHeight,
        iconSize = IconSize,
        iconHitTarget = IconHitTarget,
    )
}
