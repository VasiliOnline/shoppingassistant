package com.example.shoppingassistant.ui.navigation

import android.content.res.Resources
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


/**
 * EdgeGestureMenu — полупрозрачное меню, которое открывается "угловым" жестом
 * (снизу-справа → к центру). При открытии показываем горизонтальные блоки (кнопки разделов).
 * Это НЕ нижняя вкладка; это именно жест, как ты хотел.
 */
import androidx.compose.animation.core.*

import androidx.compose.foundation.border

import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Text
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer


data class EdgeItem(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun EdgeGestureMenuV4(
    items: List<EdgeItem> = listOf(
        EdgeItem("chat",          "Чат",         Icons.Outlined.Chat),
        EdgeItem("myItems",       "Мои товары",  Icons.Outlined.Inventory2),
        EdgeItem("subscriptions", "Подписки",    Icons.Outlined.Star),
        EdgeItem("profile",       "Профиль",     Icons.Outlined.Person),
    ),
    bottomStartZone: Dp = 96.dp,              // стартуем жест «снизу»
    rowHeight: Dp = 72.dp,                    // высота строки меню
    panelWidthFraction: Float = 0.88f,
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit
) {
    var dragging by remember { mutableStateOf(false) }
    var pointerY by remember { mutableStateOf(0f) }            // текущее положение пальца (для подсветки)
    val screenH = Resources.getSystem().displayMetrics.heightPixels.toFloat()

    // геометрия панели (центр экрана, высота = N * rowHeight)
    val rowsPx = with(androidx.compose.ui.platform.LocalDensity.current) { (rowHeight * items.size).toPx() }
    val panelTopY = (screenH - rowsPx) / 2f
    val panelBottomY = panelTopY + rowsPx

    fun indexByPointer(y: Float): Int {
        val clamped = y.coerceIn(panelTopY + 1, panelBottomY - 1)
        val rel = (clamped - panelTopY) / rowsPx
        return (rel * items.size).toInt().coerceIn(0, items.size - 1)
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start ->
                        val h = size.height.toFloat()
                        val zonePx = bottomStartZone.toPx()
                        if (start.y >= h - zonePx) {
                            dragging = true
                            pointerY = start.y
                        }
                    },
                    onDrag = { change, drag ->
                        if (dragging) {
                            change.consume()
                            pointerY = change.position.y
                        }
                    },
                    onDragEnd = {
                        if (dragging) {
                            val chosen = indexByPointer(pointerY)
                            onNavigate(items[chosen].route)
                        }
                        dragging = false
                    },
                    onDragCancel = { dragging = false }
                )
            }
    ) {
        // основной контент
        content()

        if (dragging) {
            // затемнение задника
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
            )

            // стеклянная панель по центру
            val panelShape = RoundedCornerShape(22.dp)
            Box(
                Modifier
                    .fillMaxWidth(panelWidthFraction)
                    .height(rowHeight * items.size)
                    .align(Alignment.Center)
                    .blur(18.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.28f), panelShape)
            )

            // строки меню
            Column(
                Modifier
                    .fillMaxWidth(panelWidthFraction)
                    .height(rowHeight * items.size)
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp)
            ) {
                val selected = indexByPointer(pointerY)
                items.forEachIndexed { i, it ->
                    val active = i == selected

                    // анимируем цвета текста/иконки и лёгкий «скейл» строки
                    val txtColor by animateColorAsState(
                        targetValue = if (active) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                        animationSpec = tween(180), label = "menuTextColor"
                    )
                    val iconColor by animateColorAsState(
                        targetValue = txtColor,
                        animationSpec = tween(180), label = "menuIconColor"
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (active) 1.04f else 1.0f,
                        animationSpec = tween(180), label = "menuScale"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .graphicsLayer { this.scaleX = scale; this.scaleY = scale }
                            .background(
                                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                else Color.Transparent,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                onNavigate(it.route)
                                dragging = false
                            }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = it.icon,
                            contentDescription = it.title,
                            tint = iconColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = it.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = txtColor
                        )
                    }
                }

            }
        }
    }
}

/** Определяем, какая «строка» активна, по Y-координате пальца и текущей высоте панели */
private fun currentVerticalItem(pointerY: Float, progress: Float, count: Int): Int {
    val screenH = Resources.getSystem().displayMetrics.heightPixels.toFloat()
    if (count <= 0 || progress <= 0f) return 0
    val panelTopY = screenH * (1f - 0.66f * progress)             // панель растёт вверх (max 66% экрана)
    val clampY = pointerY.coerceIn(panelTopY, screenH - 1f)
    val rel = (clampY - panelTopY) / (screenH - panelTopY)         // 0..1 внутри панели
    return (rel * count).toInt().coerceIn(0, count - 1)
}


// вычисление «активной» вкладки по положению пальца и ширине панели
private fun currentTabByPointer(pointerX: Float, progress: Float, tabs: Int): Int {
    if (progress <= 0f) return 0
    val panelWidth = progress // доля ширины экрана
    val xRel = (pointerX / (Resources.getSystem().displayMetrics.widthPixels)) / panelWidth
    return xRel.coerceIn(0f, 0.999f).times(tabs).toInt().coerceIn(0, tabs - 1)
}



@Composable
private fun MenuButton(text: String, onClick: () -> Unit) {
    ElevatedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(text)
    }
}
