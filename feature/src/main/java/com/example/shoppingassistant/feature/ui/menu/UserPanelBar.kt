package com.example.shoppingassistant.feature.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shoppingassistant.domain.menu.ActionKey
import com.example.shoppingassistant.domain.menu.Handedness
import com.example.shoppingassistant.domain.menu.ModeKey
import com.example.shoppingassistant.domain.menu.UserPanel
import com.example.shoppingassistant.domain.profile.BottomBarStyle
import com.example.shoppingassistant.feature.ui.layout.LayoutDefaults

private val SideActionStep = 56.dp

@Composable
fun UserPanelBar(
    panel: UserPanel,
    modeCatalog: List<PanelItemDescriptor<ModeKey>>,
    actionCatalog: List<PanelItemDescriptor<ActionKey>>,
    currentMode: ModeKey,
    badges: Map<ActionKey, Int> = emptyMap(),
    onModeClick: (ModeKey) -> Unit,
    onActionClick: (ActionKey) -> Unit,
    onEnterEdit: () -> Unit,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    bottomBarStyle: BottomBarStyle = BottomBarStyle.SOLID,
) {
    val panelAlpha = alpha.coerceIn(0f, 1f)
    val railButtonAlignment = if (panel.handedness == Handedness.LEFT) Alignment.BottomStart else Alignment.BottomEnd
    val railButtonPadding = if (panel.handedness == Handedness.LEFT) PaddingValues(start = 12.dp) else PaddingValues(end = 12.dp)
    val sideActionBaseline = LayoutDefaults.BottomNavHeight + 12.dp

    Box(modifier = modifier.fillMaxSize()) {
        // Side actions as floating icons near the bottom corner.
        panel.sideActions.forEachIndexed { index, slot ->
            val key = slot.key ?: return@forEachIndexed
            val descriptor = actionCatalog.firstOrNull { it.key == key } ?: return@forEachIndexed
            val enabled = slot.enabled && descriptor.isAvailable
            val offsetSteps = (panel.sideActions.lastIndex - index).coerceAtLeast(0)
            val yOffset = SideActionStep * offsetSteps

            RailActionItem(
                descriptor = descriptor,
                enabled = enabled,
                badgeCount = badges[key] ?: 0,
                onClick = { if (enabled) onActionClick(key) },
                onLongPress = {},
                modifier = Modifier
                    .align(railButtonAlignment)
                    .padding(railButtonPadding)
                    .padding(bottom = sideActionBaseline)
                    .offset(y = -yOffset)
                    .alpha(panelAlpha),
            )
        }

        BottomNavBar(
            panel = panel,
            modeCatalog = modeCatalog,
            currentMode = currentMode,
            onModeClick = onModeClick,
            alpha = panelAlpha,
            style = bottomBarStyle,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .height(LayoutDefaults.BottomNavHeight)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun BottomNavBar(
    panel: UserPanel,
    modeCatalog: List<PanelItemDescriptor<ModeKey>>,
    currentMode: ModeKey,
    onModeClick: (ModeKey) -> Unit,
    alpha: Float,
    style: BottomBarStyle,
    modifier: Modifier = Modifier,
) {
    val backgroundModifier = when (style) {
        BottomBarStyle.BLUR -> Modifier.blur(14.dp)
        else -> Modifier
    }
    val backgroundColor = when (style) {
        BottomBarStyle.SOLID -> MaterialTheme.colorScheme.surface
        BottomBarStyle.TRANSPARENT -> Color.Transparent
        BottomBarStyle.BLUR -> MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        BottomBarStyle.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
    }

    Box(modifier = modifier.alpha(alpha)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(backgroundModifier)
                .background(backgroundColor),
        )

        NavigationBar(
            modifier = Modifier.fillMaxSize(),
            tonalElevation = 0.dp,
            containerColor = Color.Transparent,
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            panel.bottomModes.forEach { slot ->
                val key = slot.key ?: return@forEach
                val descriptor = modeCatalog.firstOrNull { it.key == key } ?: return@forEach
                val enabled = slot.enabled && descriptor.isAvailable
                NavigationBarItem(
                    selected = key == currentMode,
                    onClick = { if (enabled) onModeClick(key) },
                    icon = {
                        Icon(
                            imageVector = descriptor.icon,
                            contentDescription = descriptor.title,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    label = {
                        Text(
                            text = descriptor.title,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    alwaysShowLabel = true,
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun RailActionItem(
    descriptor: PanelItemDescriptor<ActionKey>,
    enabled: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val badgeLabel = when {
        badgeCount <= 0 -> null
        badgeCount > 99 -> "99+"
        else -> badgeCount.toString()
    }
    Box(
        modifier = modifier
            .size(48.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
                enabled = enabled,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (badgeLabel != null) {
            BadgedBox(
                badge = {
                    Badge {
                        Text(text = badgeLabel, fontSize = 10.sp, maxLines = 1)
                    }
                },
            ) {
                Icon(
                    imageVector = descriptor.icon,
                    contentDescription = descriptor.title,
                    tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        } else {
            Icon(
                imageVector = descriptor.icon,
                contentDescription = descriptor.title,
                tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
