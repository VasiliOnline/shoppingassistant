// Last synced: 2025-11-15 19:44 (modified by GPT step)
// GPT task: InputRow UI — slower typewriter, cleaned texts, underscore typing, bigger text
package com.example.shoppingassistant.feature.pages.main.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.shoppingassistant.feature.pages.main.suggest.CategoryAnchorSuggest
import com.example.shoppingassistant.feature.pages.main.suggest.HistoryTemplateSuggest
import com.example.shoppingassistant.feature.pages.main.suggest.InfoSuggest
import com.example.shoppingassistant.feature.pages.main.suggest.MainSuggestItem
import com.example.shoppingassistant.feature.pages.main.suggest.AutoPresetSuggest
import com.example.shoppingassistant.feature.pages.main.suggest.AutoTemplateSuggest
import com.example.shoppingassistant.feature.pages.main.suggest.PresetTemplateSuggest
import com.example.shoppingassistant.feature.pages.main.suggest.ProductAnchorSuggest
import com.example.shoppingassistant.feature.pages.main.suggest.SectionHeaderSuggest
import com.example.shoppingassistant.feature.pages.main.suggest.TextFixSuggest
import com.example.shoppingassistant.domain.template.status.TemplateStatus

enum class SuggestionAction {
    Search,
    Track,
    Create,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputRow(
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    suggestions: List<MainSuggestItem>,
    onSuggestionClick: (MainSuggestItem) -> Unit,
    onSuggestionAction: ((MainSuggestItem, SuggestionAction) -> Unit)? = null,
    hideSuggestions: Boolean,
    hasSelection: Boolean,
    onPhotoClick: () -> Unit,
    onLinkClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onClear: (() -> Unit)? = null,
    onFocusChange: (Boolean) -> Unit = {},
    showMediaActions: Boolean = true,
    showInlineMediaActions: Boolean = showMediaActions,
    showPhotoAction: Boolean = showInlineMediaActions,
    showLinkAction: Boolean = showInlineMediaActions,
    showVoiceAction: Boolean = showInlineMediaActions,
    showHubSuggestionsWhenEmpty: Boolean = false,
    showHubSuggestionsWhenUnfocused: Boolean = false,
    hubSuggestionScale: Float = 1f,
    showInlineHubActionsWhenEmpty: Boolean = false,
    hidePlaceholderWhenEmptyWithHubActions: Boolean = false,
    inputFontWeight: FontWeight = FontWeight.Normal,
    enforceFocus: Boolean = false,
    autoFocus: Boolean = false,
    placeholder: String = "Искать товары, еду, услуги…",
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = input,
                selection = TextRange(input.length),
            ),
        )
    }

    LaunchedEffect(isFocused, enforceFocus) {
        if (enforceFocus && !isFocused) {
            focusRequester.requestFocus()
            return@LaunchedEffect
        }
        if (!enforceFocus && isFocused) {
            focusManager.clearFocus(force = true)
            return@LaunchedEffect
        }
        onFocusChange(isFocused)
    }

    LaunchedEffect(input) {
        if (input != textFieldValue.text) {
            textFieldValue = TextFieldValue(
                text = input,
                selection = TextRange(input.length),
            )
        }
    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            textFieldValue = textFieldValue.copy(selection = TextRange(textFieldValue.text.length))
        }
    }

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            BoxWithConstraints {
                val showClear = onClear != null && input.isNotBlank()
                val showInlineHubActions = showInlineHubActionsWhenEmpty &&
                    input.isBlank() &&
                    !isFocused
                val visibleInlineActionCount = buildList {
                    if (showClear) add(Unit)
                    if (showInlineMediaActions && showPhotoAction) add(Unit)
                    if (showInlineMediaActions && showLinkAction) add(Unit)
                    if (showInlineMediaActions && showVoiceAction) add(Unit)
                }.size
                val actionCount = if (showInlineHubActions) {
                    3
                } else {
                    visibleInlineActionCount
                }
                val actionSlotWidth = if (showInlineHubActions) {
                    (maxWidth * 0.68f).coerceAtLeast(176.dp)
                } else {
                    maxOf(44.dp * actionCount, maxWidth * 0.22f)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(start = 8.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (showInlineHubActions) 8.dp else 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Поиск",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clickable { focusRequester.requestFocus() }
                            .padding(start = 4.dp, end = 6.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { nextValue ->
                                val previousText = textFieldValue.text
                                textFieldValue = nextValue
                                if (nextValue.text != previousText) {
                                    onInputChange(nextValue.text)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = inputFontWeight,
                            ),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Search,
                                keyboardType = KeyboardType.Text,
                            ),
                            keyboardActions = KeyboardActions(onSearch = { onSubmit(textFieldValue.text) }),
                            interactionSource = interaction,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (textFieldValue.text.isBlank()) {
                                        val showPlaceholder = placeholder.isNotBlank() &&
                                            !(showInlineHubActions && hidePlaceholderWhenEmptyWithHubActions)
                                        if (showPlaceholder) {
                                            Text(
                                                text = placeholder,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    innerTextField()
                                }
                            },
                        )
                    }

                    if (showInlineHubActions) {
                        HubInlineActionsStrip(
                            modifier = Modifier
                                .width(actionSlotWidth)
                                .height(44.dp),
                            onPhotoClick = onPhotoClick,
                            onLinkClick = onLinkClick,
                            onVoiceClick = onVoiceClick,
                        )
                    } else if (showInlineMediaActions || showClear) {
                        Row(
                            modifier = Modifier.widthIn(min = actionSlotWidth),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            if (showClear) {
                                SearchBarAction(
                                    icon = Icons.Outlined.Close,
                                    contentDescription = "Очистить",
                                    onClick = { onClear?.invoke() },
                                )
                            }
                            if (showInlineMediaActions) {
                                if (showPhotoAction) {
                                    SearchBarAction(
                                        icon = Icons.Outlined.CameraAlt,
                                        contentDescription = "Поиск по фото",
                                        onClick = onPhotoClick,
                                    )
                                }
                                if (showLinkAction) {
                                    SearchBarAction(
                                        icon = Icons.Outlined.Link,
                                        contentDescription = "Поиск по ссылке",
                                        onClick = onLinkClick,
                                    )
                                }
                                if (showVoiceAction) {
                                    SearchBarAction(
                                        icon = Icons.Outlined.Mic,
                                        contentDescription = "Голос в текст",
                                        onClick = onVoiceClick,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val showHubSuggestions = showHubSuggestionsWhenEmpty &&
            input.isBlank() &&
            (isFocused || showHubSuggestionsWhenUnfocused)
        if (showHubSuggestions) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                QuickInputAction(
                    text = "Найти по фотографии",
                    icon = Icons.Outlined.CameraAlt,
                    scale = hubSuggestionScale,
                    onClick = onPhotoClick,
                )
                QuickInputAction(
                    text = "Искать по ссылке",
                    icon = Icons.Outlined.Link,
                    scale = hubSuggestionScale,
                    onClick = onLinkClick,
                )
                QuickInputAction(
                    text = "Искать по голосу",
                    icon = Icons.Outlined.Mic,
                    scale = hubSuggestionScale,
                    onClick = onVoiceClick,
                )
            }
        }

        DropdownSuggestions(
            expanded = suggestions.isNotEmpty() &&
                !hideSuggestions &&
                !hasSelection &&
                isFocused,
            items = suggestions,
            onClick = onSuggestionClick,
            onAction = onSuggestionAction,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HubInlineActionsStrip(
    modifier: Modifier = Modifier,
    onPhotoClick: () -> Unit,
    onLinkClick: () -> Unit,
    onVoiceClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HubInlineActionCell(
                icon = Icons.Outlined.CameraAlt,
                contentDescription = "Поиск по фото",
                onClick = onPhotoClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp, topEnd = 6.dp, bottomEnd = 6.dp),
            )
            HubInlineActionCell(
                icon = Icons.Outlined.Link,
                contentDescription = "Поиск по ссылке",
                onClick = onLinkClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            )
            HubInlineActionCell(
                icon = Icons.Outlined.Mic,
                contentDescription = "Поиск голосом",
                onClick = onVoiceClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 14.dp, bottomEnd = 14.dp),
            )
        }
    }
}

@Composable
private fun HubInlineActionCell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SearchBarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun QuickInputAction(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    scale: Float = 1f,
    onClick: () -> Unit,
) {
    val safeScale = scale.coerceIn(0.85f, 1f)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp * safeScale),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp * safeScale),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * safeScale,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * safeScale,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DropdownSuggestions(
    expanded: Boolean,
    items: List<MainSuggestItem>,
    onClick: (MainSuggestItem) -> Unit,
    onAction: ((MainSuggestItem, SuggestionAction) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = expanded, modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEach { suggestion ->
                    val isClickable = suggestion !is InfoSuggest && suggestion !is SectionHeaderSuggest
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isClickable) { onClick(suggestion) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            when (suggestion) {
                                is SectionHeaderSuggest -> {
                                    Text(
                                        text = suggestion.text,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                is TextFixSuggest -> {
                                    suggestion.caption?.let { cap ->
                                        Text(
                                            text = cap,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        text = suggestion.fixedText,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                is ProductAnchorSuggest -> {
                                    Text(
                                        text = suggestion.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    suggestion.caption?.let { cap ->
                                        Text(
                                            text = cap,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                is AutoPresetSuggest -> {
                                    Text(
                                        text = suggestion.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    suggestion.caption?.let { cap ->
                                        Text(
                                            text = cap,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                is AutoTemplateSuggest -> {
                                    suggestion.caption?.let { cap ->
                                        Text(
                                            text = cap,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        text = suggestion.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val reasons = suggestion.reasons.joinToString(" · ")
                                    if (reasons.isNotBlank()) {
                                        Text(
                                            text = reasons,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                is PresetTemplateSuggest -> {
                                    Text(
                                        text = suggestion.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    suggestion.caption?.let { cap ->
                                        Text(
                                            text = cap,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                is CategoryAnchorSuggest -> {
                                    val text = suggestion.count?.let { count ->
                                        "${suggestion.breadcrumb} {$count}"
                                    } ?: suggestion.breadcrumb
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                is HistoryTemplateSuggest -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = suggestion.text,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        TemplateStatusBadge(status = suggestion.status)
                                    }
                                    suggestion.caption?.let { cap ->
                                        Text(
                                            text = cap,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                is InfoSuggest -> {
                                    Text(
                                        text = suggestion.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
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
private fun TemplateStatusBadge(status: TemplateStatus) {
    val colors = MaterialTheme.colorScheme
    val label = when (status) {
        TemplateStatus.DRAFT -> "Черновик"
        TemplateStatus.VALID -> "Готово"
        TemplateStatus.LOCKED_FOR_ACTIONS -> "Готово"
    }
    val background = when (status) {
        TemplateStatus.DRAFT -> colors.tertiary.copy(alpha = 0.18f)
        TemplateStatus.VALID -> colors.primary.copy(alpha = 0.18f)
        TemplateStatus.LOCKED_FOR_ACTIONS -> colors.primary.copy(alpha = 0.18f)
    }
    val foreground = when (status) {
        TemplateStatus.DRAFT -> colors.tertiary
        TemplateStatus.VALID -> colors.primary
        TemplateStatus.LOCKED_FOR_ACTIONS -> colors.primary
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = background,
        modifier = Modifier.padding(start = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
