package com.example.shoppingassistant.feature.pages.main.visualsearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.shoppingassistant.domain.visualsearch.VisualSearchIntent
import com.example.shoppingassistant.domain.visualsearch.VisualSearchRecoveryActionType
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSelectionMode
import com.example.shoppingassistant.feature.pages.main.state.CategoryChipUi
import com.example.shoppingassistant.feature.pages.main.state.VisualSearchRegionUi
import com.example.shoppingassistant.feature.pages.main.state.VisualSearchSessionState
import com.example.shoppingassistant.feature.pages.main.state.VisualSearchSessionStep

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VisualSearchSheet(
    state: VisualSearchSessionState,
    categoryOptions: List<CategoryChipUi>,
    onDismiss: () -> Unit,
    onPickCamera: () -> Unit,
    onPickGallery: () -> Unit,
    onIntentChange: (VisualSearchIntent) -> Unit,
    onSelectionModeChange: (VisualSearchSelectionMode) -> Unit,
    onRegionPick: (VisualSearchRegionUi?) -> Unit,
    onCategoryPick: (CategoryChipUi) -> Unit,
    onSubmit: () -> Unit,
    onRecoveryAction: (VisualSearchRecoveryActionType) -> Unit,
) {
    if (!state.visible || state.step == VisualSearchSessionStep.Source) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isManualRefinement = state.step == VisualSearchSessionStep.Review
    val isRecoveryOnly = state.step == VisualSearchSessionStep.Recovery
    val secondaryRecoveryActions = state.recoveryActions.filterNot {
        it.type == VisualSearchRecoveryActionType.RETAKE_PHOTO
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = when {
                            isRecoveryOnly -> "Переснимите предмет крупнее"
                            isManualRefinement -> "Уточнить поиск по фото"
                            else -> "Поиск по фото"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            isRecoveryOnly ->
                                "Держите в кадре только товар, без лишнего фона. После нового снимка сразу откроем выдачу."
                            isManualRefinement ->
                                "Можно подсказать, что именно искать, и сузить выдачу."
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Закрыть",
                    )
                }
            }

            state.asset?.let { asset ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AsyncImage(
                            model = asset.localUri,
                            contentDescription = "Фото для поиска",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }

            combinedVisualSearchMessage(state)?.let { message ->
                MessageCard(
                    title = if (isRecoveryOnly) "Что поправить" else message.first,
                    text = message.second,
                    accent = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                    textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            state.insight?.let { insight ->
                val title = insight.title?.trim().takeUnless { it.isNullOrBlank() }
                val subtitle = insight.subtitle?.trim().takeUnless { it.isNullOrBlank() }
                if (title != null || subtitle != null || insight.hintLabels.isNotEmpty()) {
                    MessageCard(
                        title = "Мы думаем, что это",
                        text = buildString {
                            title?.let { append(it) }
                            if (subtitle != null) {
                                if (isNotEmpty()) append('\n')
                                append(subtitle)
                            }
                        }.ifBlank { "Собрали первую гипотезу по кадру." },
                        accent = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (!isRecoveryOnly && insight.hintLabels.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            insight.hintLabels.forEach { hint ->
                                FilterChip(
                                    selected = false,
                                    onClick = {},
                                    enabled = false,
                                    label = { Text(hint, maxLines = 1) },
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onPickCamera,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text("Переснять", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onPickGallery,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text("Заменить фото", maxLines = 1)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Что искать",
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VisualSearchIntent.entries.forEach { intent ->
                        FilterChip(
                            selected = state.intent == intent,
                            onClick = { onIntentChange(intent) },
                            label = {
                                Text(
                                    text = intentLabel(intent),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }

            if (isManualRefinement && state.captureMode != com.example.shoppingassistant.domain.visualsearch.VisualSearchCaptureMode.BARCODE) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "На чём сфокусироваться",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VisualSearchSelectionMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.selectionMode == mode,
                                onClick = { onSelectionModeChange(mode) },
                                label = {
                                    Text(
                                        text = selectionModeLabel(mode),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                    if (state.selectionMode == VisualSearchSelectionMode.MANUAL_CROP) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            manualRegionPresets().forEach { preset ->
                                FilterChip(
                                    selected = state.selectedRegion?.label == preset.label,
                                    onClick = { onRegionPick(preset) },
                                    label = {
                                        Text(
                                            text = preset.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    state.selectedRegion?.let { region ->
                        Text(
                            text = "Выбран предмет: ${region.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (isManualRefinement) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Категория",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Поможет быстрее сузить выдачу, если фото получилось неоднозначным.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        categoryOptions.forEach { chip ->
                            FilterChip(
                                selected = state.selectedCategoryCode == chip.code,
                                onClick = { onCategoryPick(chip) },
                                label = {
                                    Text(
                                        text = chip.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                    if (categoryOptions.isEmpty()) {
                        Text(
                            text = "Категории пока не готовы. Можно продолжать по гипотезе из фото.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (isRecoveryOnly && secondaryRecoveryActions.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    secondaryRecoveryActions.forEach { action ->
                        OutlinedButton(onClick = { onRecoveryAction(action.type) }) {
                            Text(action.label, maxLines = 1)
                        }
                    }
                }
            }

            if (isRecoveryOnly) {
                Button(
                    onClick = { onRecoveryAction(VisualSearchRecoveryActionType.RETAKE_PHOTO) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Переснять крупнее", maxLines = 1)
                }
            } else {
                Button(
                    onClick = onSubmit,
                    enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (state.isSubmitting) "Обновляем выдачу..." else "Показать результаты",
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    title: String,
    text: String,
    accent: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = accent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.86f),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
        }
    }
}

private fun intentLabel(intent: VisualSearchIntent): String = when (intent) {
    VisualSearchIntent.EXACT_SAME -> "Точно такой"
    VisualSearchIntent.SIMILAR -> "Похожий"
    VisualSearchIntent.PART_ACCESSORY -> "Деталь или аксессуар"
    VisualSearchIntent.IDENTIFY_FIRST -> "Сначала определить"
}

private fun selectionModeLabel(mode: VisualSearchSelectionMode): String = when (mode) {
    VisualSearchSelectionMode.WHOLE_FRAME -> "Весь кадр"
    VisualSearchSelectionMode.AUTO_TARGET -> "Автовыбор"
    VisualSearchSelectionMode.MANUAL_CROP -> "Выбрать объект"
}

private fun combinedVisualSearchMessage(
    state: VisualSearchSessionState,
): Pair<String, String>? {
    val body = listOfNotNull(state.errorMessage, state.recoveryMessage)
        .joinToString(" ")
        .trim()
    if (body.isBlank()) return null
    val title = if (state.step == VisualSearchSessionStep.Recovery) {
        "Нужно немного уточнить фото"
    } else {
        "Поиск можно сделать точнее"
    }
    return title to body
}

private fun manualRegionPresets(): List<VisualSearchRegionUi> = listOf(
    VisualSearchRegionUi(
        label = "Объект",
        left = 0.15f,
        top = 0.10f,
        width = 0.70f,
        height = 0.80f,
    ),
    VisualSearchRegionUi(
        label = "Центр",
        left = 0.20f,
        top = 0.20f,
        width = 0.60f,
        height = 0.60f,
    ),
    VisualSearchRegionUi(
        label = "Детали",
        left = 0.30f,
        top = 0.14f,
        width = 0.40f,
        height = 0.34f,
    ),
)
