package com.example.shoppingassistant.feature.ui.state

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

enum class SystemNoticeTone {
    Info,
    Success,
    Warning,
    Error,
}

@Composable
fun SystemNoticeCard(
    body: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: SystemNoticeTone = SystemNoticeTone.Info,
    iconOverride: ImageVector? = null,
    bodyModifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionModifier: Modifier = Modifier,
    footer: String? = null,
    compact: Boolean = false,
    bottomContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val palette = rememberSystemNoticePalette(tone = tone)
    val contentPadding = if (compact) 12.dp else 14.dp
    val iconSize = if (compact) 18.dp else 20.dp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = RectangleShape,
        color = palette.container,
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = iconOverride ?: palette.icon,
                contentDescription = null,
                tint = palette.iconTint,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(iconSize),
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp),
            ) {
                title?.let {
                    Text(
                        text = it,
                        style = if (compact) {
                            MaterialTheme.typography.titleSmall
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        fontWeight = FontWeight.Bold,
                        color = palette.title,
                    )
                }

                Text(
                    text = body,
                    modifier = bodyModifier,
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    color = palette.body,
                )

                footer?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.footer,
                    )
                }

                if (actionLabel != null && onAction != null) {
                    Button(
                        onClick = onAction,
                        modifier = actionModifier.heightIn(min = 38.dp),
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.actionContainer,
                            contentColor = palette.action,
                        ),
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 15.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }

                bottomContent?.invoke(this)
            }
        }
    }
}

@Composable
fun SystemNoticePatternsPreview(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SystemNoticeCard(
            title = "Синяя подсказка",
            body = "Геолокация недоступна. Используем город из профиля, пока вы не разрешите доступ.",
            tone = SystemNoticeTone.Info,
            actionLabel = "Открыть настройки",
            onAction = {},
        )
        SystemNoticeCard(
            title = "Операция выполнена",
            body = "Местоположение подтверждено. Данные актуальны, можно публиковать объявление.",
            tone = SystemNoticeTone.Success,
            footer = "Статус обновится автоматически.",
        )
        SystemNoticeCard(
            title = "Нужно внимание",
            body = "Перед публикацией лучше обновить фото. Проверьте основной ракурс и пример подсказки.",
            tone = SystemNoticeTone.Warning,
        )
        SystemNoticeCard(
            title = "Нужна правка",
            body = "Публикация заблокирована. Исправьте обязательные поля и повторите отправку.",
            tone = SystemNoticeTone.Error,
            actionLabel = "Повторить",
            onAction = {},
        )
    }
}

@Preview(
    name = "System Notice Patterns",
    showBackground = true,
    backgroundColor = 0xFFF7FAFF,
    widthDp = 420,
)
@Composable
private fun SystemNoticePatternsPreviewLight() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF4F7DF9),
            onPrimary = Color.White,
            background = Color(0xFFF7FAFF),
            surface = Color.White,
            surfaceVariant = Color(0xFFF0F3F8),
            onSurface = Color(0xFF0F172A),
            onSurfaceVariant = Color(0xFF475569),
        ),
    ) {
        SystemNoticePatternsPreview()
    }
}

private fun buildHighlightedNoticeText(
    text: String,
    tone: SystemNoticeTone,
    palette: SystemNoticePalette,
) = buildAnnotatedString {
    append(text)

    collectHighlightRanges(
        text = text,
        tone = tone,
    ).forEach { range ->
        addStyle(
            style = SpanStyle(
                background = palette.highlight,
                color = palette.highlightText,
                fontWeight = FontWeight.SemiBold,
            ),
            start = range.first,
            end = range.last + 1,
        )
    }
}

private fun collectHighlightRanges(
    text: String,
    tone: SystemNoticeTone,
): List<IntRange> {
    val phrases = linkedSetOf<String>()
    phrases += highlightPhrasesForTone(tone)
    phrases += inferDynamicHighlightPhrases(text)

    val candidates = phrases
        .asSequence()
        .map { it.trim() }
        .filter { it.length >= 3 }
        .flatMap { phrase ->
            Regex(pattern = Regex.escape(phrase), options = setOf(RegexOption.IGNORE_CASE))
                .findAll(text)
                .map { match -> match.range }
        }
        .sortedWith(compareBy<IntRange>({ it.first }, { -(it.last - it.first) }))
        .toList()

    val accepted = mutableListOf<IntRange>()
    candidates.forEach { candidate ->
        val overlaps = accepted.any { existing ->
            candidate.first <= existing.last && existing.first <= candidate.last
        }
        if (!overlaps) {
            accepted += candidate
        }
    }
    return accepted
}

private fun highlightPhrasesForTone(tone: SystemNoticeTone): List<String> = when (tone) {
    SystemNoticeTone.Info -> listOf(
        "геолокация",
        "местоположение",
        "город из профиля",
        "разрешите доступ",
        "открыть настройки",
        "обновится автоматически",
        "несколько секунд",
        "категория",
    )
    SystemNoticeTone.Success -> listOf(
        "данные актуальны",
        "можно публиковать",
        "подтверждено",
        "публикация принята",
        "объявление создано",
        "опубликовано",
        "на модерацию",
    )
    SystemNoticeTone.Warning -> listOf(
        "проверьте",
        "лучше обновить",
        "ещё стоит проверить",
        "перед публикацией",
        "нужная роль",
        "пример",
        "требует внимания",
    )
    SystemNoticeTone.Error -> listOf(
        "нужна правка",
        "исправьте",
        "заблокирована",
        "не удалось",
        "устарело",
        "нужно обновить",
        "проверьте снимок",
        "обязательные поля",
    )
}

private fun inferDynamicHighlightPhrases(text: String): Set<String> {
    val phrases = linkedSetOf<String>()

    Regex("(?m)(?<=: )[^\\n]{2,48}")
        .findAll(text)
        .forEach { match ->
            val phrase = match.value.trim().trimEnd('.', ':')
            if (phrase.countWords() in 1..6 && '•' !in phrase) {
                phrases += phrase
            }
        }

    Regex("(?m)^(?![•\\-])(?!.*[:.!?]$)[A-Za-zА-Яа-яЁё0-9][^\\n]{1,28}$")
        .findAll(text)
        .forEach { match ->
            val phrase = match.value.trim()
            if (phrase.countWords() in 1..4) {
                phrases += phrase
            }
        }

    return phrases
}

private fun String.countWords(): Int =
    trim().split(Regex("\\s+")).count { it.isNotBlank() }

@Composable
private fun rememberSystemNoticePalette(tone: SystemNoticeTone): SystemNoticePalette {
    val darkTheme = isSystemInDarkTheme()
    return when (tone) {
        SystemNoticeTone.Info ->
            if (darkTheme) {
                SystemNoticePalette(
                    icon = Icons.Outlined.Info,
                    container = Color(0xFF24304A),
                    border = Color.Transparent,
                    iconContainer = Color.Transparent,
                    iconTint = Color(0xFF9BB6FF),
                    title = Color(0xFFF5F7FF),
                    body = Color(0xFFD7DDF1),
                    footer = Color(0xFFCBD3E8),
                    action = Color.White,
                    actionContainer = Color(0xFF32405E),
                    highlight = Color(0xFF32405E),
                    highlightText = Color.White,
                )
            } else {
                SystemNoticePalette(
                    icon = Icons.Outlined.Info,
                    container = Color(0xFFEFF3F8),
                    border = Color.Transparent,
                    iconContainer = Color.Transparent,
                    iconTint = Color(0xFF5F6368),
                    title = Color(0xFF202124),
                    body = Color(0xFF4B5563),
                    footer = Color(0xFF6B7280),
                    action = Color(0xFF294C79),
                    actionContainer = Color(0xFFF7FAFD),
                    highlight = Color(0xFFE8EDF3),
                    highlightText = Color(0xFF202124),
                )
            }

        SystemNoticeTone.Success ->
            if (darkTheme) {
                SystemNoticePalette(
                    icon = Icons.Outlined.CheckCircleOutline,
                    container = Color(0xFF213228),
                    border = Color.Transparent,
                    iconContainer = Color.Transparent,
                    iconTint = Color(0xFF7BD38A),
                    title = Color(0xFFF4FFF5),
                    body = Color(0xFFD7E7DA),
                    footer = Color(0xFFC8D7CC),
                    action = Color.White,
                    actionContainer = Color(0xFF2D4335),
                    highlight = Color(0xFF2D4335),
                    highlightText = Color.White,
                )
            } else {
                SystemNoticePalette(
                    icon = Icons.Outlined.CheckCircleOutline,
                    container = Color(0xFFEDF6EE),
                    border = Color.Transparent,
                    iconContainer = Color.Transparent,
                    iconTint = Color(0xFF4E7D57),
                    title = Color(0xFF202124),
                    body = Color(0xFF4F5D52),
                    footer = Color(0xFF6C7A6F),
                    action = Color(0xFF355F3E),
                    actionContainer = Color(0xFFF7FBF7),
                    highlight = Color(0xFFE5F0E6),
                    highlightText = Color(0xFF202124),
                )
            }

        SystemNoticeTone.Warning ->
            if (darkTheme) {
                SystemNoticePalette(
                    icon = Icons.Outlined.WarningAmber,
                    container = Color(0xFF4E3E16),
                    border = Color.Transparent,
                    iconContainer = Color.Transparent,
                    iconTint = Color(0xFFFFD166),
                    title = Color(0xFFFFFAF0),
                    body = Color(0xFFF0E3BE),
                    footer = Color(0xFFE4D6AE),
                    action = Color.White,
                    actionContainer = Color(0xFF65501D),
                    highlight = Color(0xFF65501D),
                    highlightText = Color.White,
                )
            } else {
                SystemNoticePalette(
                    icon = Icons.Outlined.WarningAmber,
                    container = Color(0xFFFEF2DE),
                    border = Color.Transparent,
                    iconContainer = Color.Transparent,
                    iconTint = Color(0xFF8A6A1F),
                    title = Color(0xFF202124),
                    body = Color(0xFF5C5134),
                    footer = Color(0xFF7A6D4D),
                    action = Color(0xFF6F561A),
                    actionContainer = Color(0xFFFFFAEF),
                    highlight = Color(0xFFFFEEC8),
                    highlightText = Color(0xFF202124),
                )
            }

        SystemNoticeTone.Error ->
            if (darkTheme) {
                SystemNoticePalette(
                    icon = Icons.Outlined.ErrorOutline,
                    container = Color(0xFF4A252C),
                    border = Color.Transparent,
                    iconContainer = Color.Transparent,
                    iconTint = Color(0xFFFF8A92),
                    title = Color(0xFFFFF2F4),
                    body = Color(0xFFF1D5D8),
                    footer = Color(0xFFE5C7CB),
                    action = Color.White,
                    actionContainer = Color(0xFF613138),
                    highlight = Color(0xFF613138),
                    highlightText = Color.White,
                )
            } else {
                SystemNoticePalette(
                    icon = Icons.Outlined.ErrorOutline,
                    container = Color(0xFFF9E6E8),
                    border = Color.Transparent,
                    iconContainer = Color.Transparent,
                    iconTint = Color(0xFF9B3D49),
                    title = Color(0xFF202124),
                    body = Color(0xFF5B4548),
                    footer = Color(0xFF7B6468),
                    action = Color(0xFF7C3540),
                    actionContainer = Color(0xFFFEF5F6),
                    highlight = Color(0xFFF6DDE0),
                    highlightText = Color(0xFF202124),
                )
            }
    }
}

@Immutable
private data class SystemNoticePalette(
    val icon: ImageVector,
    val container: Color,
    val border: Color,
    val iconContainer: Color,
    val iconTint: Color,
    val title: Color,
    val body: Color,
    val footer: Color,
    val action: Color,
    val actionContainer: Color,
    val highlight: Color,
    val highlightText: Color,
)
