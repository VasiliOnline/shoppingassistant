package com.example.shoppingassistant.feature.ui.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

fun selectTrustLabel(trust: CardTrustData?): String? {
    if (trust == null) return null
    return when {
        !trust.updatedAtText.isNullOrBlank() -> trust.updatedAtText
        !trust.sourceText.isNullOrBlank() -> trust.sourceText
        !trust.ratingText.isNullOrBlank() -> trust.ratingText
        else -> null
    }
}

@Composable
fun CardTrustSignal(
    text: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = !text.isNullOrBlank(),
        enter = fadeIn(animationSpec = tween(140)),
        exit = fadeOut(animationSpec = tween(140)),
        modifier = modifier,
    ) {
        Text(
            text = text.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
