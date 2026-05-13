package com.example.shoppingassistant.feature.pages.trackeditems

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.shoppingassistant.domain.catalog.allAttributes
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.shoppingassistant.core.data.suggest.ProductSuggestCandidate
import com.example.shoppingassistant.core.data.suggest.ProductSuggestRepository
import com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.i18n.displayTitle
import com.example.shoppingassistant.domain.profile.GetProfileCacheTask
import com.example.shoppingassistant.domain.ugc.MirrorByUrlUseCase
import com.example.shoppingassistant.domain.ugc.UgcMirrorResult
import com.example.shoppingassistant.domain.tracks.CreateTrackTask
import com.example.shoppingassistant.domain.tracks.TrackCreateInput
import com.example.shoppingassistant.domain.tracks.TrackFilters
import com.example.shoppingassistant.domain.tracks.TrackTargetSpec
import com.example.shoppingassistant.domain.tracks.TrackType
import com.example.shoppingassistant.domain.vision.NormalizeImageUseCase
import com.example.shoppingassistant.domain.model.toRawStringAttributes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get as koinGet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTrackWizardSheet(
    onDismiss: () -> Unit,
    onTrackCreated: (String) -> Unit,
) {
    val createTrackTask: CreateTrackTask = remember { koinGet(CreateTrackTask::class.java) }
    val suggestRepository: ProductSuggestRepository = remember { koinGet(ProductSuggestRepository::class.java) }
    val catalogRepository: CatalogReadRepository = remember { koinGet(CatalogReadRepository::class.java) }
    val catalogTaxonomyRepository: CatalogTaxonomyRepository = remember {
        koinGet(CatalogTaxonomyRepository::class.java)
    }
    val mirrorByUrl: MirrorByUrlUseCase = remember { koinGet(MirrorByUrlUseCase::class.java) }
    val normalizeImage: NormalizeImageUseCase = remember { koinGet(NormalizeImageUseCase::class.java) }
    val getProfileCache: GetProfileCacheTask = remember { koinGet(GetProfileCacheTask::class.java) }

    val leafCategories = rememberLeafCategories(catalogTaxonomyRepository)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<ProductSuggestCandidate>>(emptyList()) }
    var categoryHints by remember { mutableStateOf<List<com.example.shoppingassistant.domain.catalog.Category>>(emptyList()) }
    var targetDraft by remember { mutableStateOf(TrackTargetDraft()) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var normalizing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showTargetEditor by remember { mutableStateOf(false) }
    var countryCode by remember { mutableStateOf("") }
    var validationSpec by remember { mutableStateOf<CatalogCategoryEffectiveSpec?>(null) }
    var validationProfileLoading by remember { mutableStateOf(false) }

    LaunchedEffect(getProfileCache) {
        val profileCountry = runCatching { getProfileCache()?.settings?.countryCode }.getOrNull().orEmpty()
        countryCode = profileCountry.ifBlank { java.util.Locale.getDefault().country }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val base64 = loadImageBase64(context, uri)
        if (base64 == null) {
            message = "Не удалось прочитать фото"
            return@rememberLauncherForActivityResult
        }
        normalizing = true
        scope.launch {
            val normalized = runCatching { normalizeImage(base64) }.getOrNull()
            normalizing = false
            if (normalized == null) {
                message = "Не удалось распознать фото"
                return@launch
            }
            targetDraft = targetDraft.copy(
                type = TrackType.PRODUCT,
                brand = normalized.brand,
                model = normalized.model,
                attributes = normalized.attributes.toRawStringAttributes(),
                autoFilledAttributes = normalized.attributes.keys,
                sourceLabel = "из фото",
            )
            query = listOf(normalized.brand, normalized.model).joinToString(" ").trim()
        }
    }

    LaunchedEffect(query, leafCategories) {
        val q = query.trim()
        if (q.length < 2) {
            suggestions = emptyList()
            categoryHints = emptyList()
            return@LaunchedEffect
        }
        delay(180)
        suggestions = runCatching { suggestRepository.search(q, limit = 8) }.getOrElse { emptyList() }
        categoryHints = leafCategories.filter { category ->
            val haystack = buildList {
                add(category.code)
                addAll(category.title.values)
                add(category.displayTitle(locale = java.util.Locale.getDefault().toLanguageTag()))
            }.joinToString(" ").lowercase()
            haystack.contains(q.lowercase())
        }.take(6)
    }

    LaunchedEffect(targetDraft.type, targetDraft.categoryCode, catalogRepository) {
        val categoryCode = targetDraft.categoryCode?.trim().orEmpty()
        if (categoryCode.isBlank()) {
            validationSpec = null
            validationProfileLoading = false
            return@LaunchedEffect
        }
        validationProfileLoading = true
        validationSpec = runCatching { catalogRepository.getCategoryEffectiveSpec(categoryCode) }.getOrNull()
        validationProfileLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Создать отслеживание", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Цель: товар или категория") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showCategoryPicker = true }) {
                            Icon(Icons.Outlined.Category, contentDescription = "Категория")
                        }
                        IconButton(onClick = { showUrlDialog = true }) {
                            Icon(Icons.Outlined.Link, contentDescription = "URL")
                        }
                        IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = "Фото")
                        }
                    }
                },
            )

            if (normalizing) {
                Text(
                    text = "Нормализуем цель…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (suggestions.isNotEmpty()) {
                Text("Подсказки товаров", style = MaterialTheme.typography.titleSmall)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(suggestions) { item ->
                        SuggestionRow(
                            title = item.title,
                            subtitle = listOfNotNull(item.brand, item.model, item.categoryCode).joinToString(" • "),
                            onClick = {
                                targetDraft = targetDraft.copy(
                                    type = TrackType.PRODUCT,
                                    brand = item.brand.orEmpty(),
                                    model = item.model.orEmpty(),
                                    categoryCode = item.categoryCode,
                                    sourceLabel = "из подсказки",
                                )
                                query = listOfNotNull(item.brand, item.model).joinToString(" ").ifBlank { item.title }
                            },
                        )
                    }
                }
            }

            if (categoryHints.isNotEmpty()) {
                Text("Категории", style = MaterialTheme.typography.titleSmall)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(categoryHints) { category ->
                        SuggestionRow(
                            title = category.displayTitle(locale = java.util.Locale.getDefault().toLanguageTag()),
                            subtitle = category.code,
                            onClick = {
                                targetDraft = targetDraft.copy(
                                    type = TrackType.CATEGORY,
                                    categoryCode = category.code,
                                    sourceLabel = "из подсказки",
                                )
                                query = category.displayTitle(locale = java.util.Locale.getDefault().toLanguageTag())
                            },
                        )
                    }
                }
            }

            TargetPreview(
                target = targetDraft,
                categories = leafCategories,
                onReset = { targetDraft = TrackTargetDraft() },
                onEdit = { showTargetEditor = true },
            )

            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            val missingRequiredCodes = targetDraft.missingRequiredAttributeCodes(validationSpec)
            val missingRequiredMessage = buildMissingRequiredMessage(
                missingCodes = missingRequiredCodes,
                spec = validationSpec,
            )
            if (validationProfileLoading) {
                Text(
                    text = "Проверяем обязательные параметры категории…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (missingRequiredCodes.isNotEmpty() && message == null) {
                Text(
                    text = missingRequiredMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            val baseCanSave = when (targetDraft.type) {
                TrackType.PRODUCT, TrackType.CATEGORY -> !targetDraft.categoryCode.isNullOrBlank()
                else -> false
            }
            val canSave = baseCanSave && !validationProfileLoading && missingRequiredCodes.isEmpty()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Отмена") }
                Button(
                    enabled = canSave && !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            if (missingRequiredCodes.isNotEmpty()) {
                                saving = false
                                message = missingRequiredMessage
                                return@launch
                            }
                            val matchKey = targetDraft.matchKeyOrNull()
                            val titlePreview = targetDraft.previewTitle(leafCategories)
                            val request = TrackCreateInput(
                                type = targetDraft.type,
                                targetSpec = TrackTargetSpec(
                                    categoryCode = targetDraft.categoryCode,
                                    attributes = sanitizeAttributes(targetDraft.attributes),
                                    attributesMulti = targetDraft.attributesMulti,
                                    attributesRange = targetDraft.attributesRange,
                                    matchKey = matchKey,
                                    queryText = targetDraft.queryText ?: query.trim().takeIf { it.isNotBlank() },
                                    schemaVersion = targetDraft.schemaVersion,
                                    taxonomyVersion = targetDraft.taxonomyVersion,
                                    locale = targetDraft.locale,
                                    unboundTokens = targetDraft.unboundTokens,
                                ),
                                filters = TrackFilters(extra = sanitizeAttributes(targetDraft.attributes)),
                                title = titlePreview.takeIf { it.isNotBlank() },
                            )
                            runCatching { createTrackTask(request) }
                                .onSuccess { created ->
                                    saving = false
                                    onTrackCreated(created.id)
                                }
                                .onFailure { error ->
                                    saving = false
                                    message = error.message ?: "Не удалось создать трек"
                                }
                        }
                    },
                ) {
                    Text(if (saving) "Создаём…" else "Готово")
                }
            }
        }
    }

    if (showCategoryPicker) {
        CategoryPickerSheet(
            categories = leafCategories,
            onDismiss = { showCategoryPicker = false },
            onSelect = { category ->
                targetDraft = targetDraft.copy(
                    type = TrackType.CATEGORY,
                    categoryCode = category.code,
                    sourceLabel = null,
                )
                query = category.displayTitle(locale = java.util.Locale.getDefault().toLanguageTag())
                showCategoryPicker = false
            },
        )
    }

    if (showTargetEditor) {
        TrackTargetAttributesWizardSheet(
            mode = TargetWizardMode.CREATE,
            entrypoint = "create.preview_edit",
            initial = targetDraft,
            categories = leafCategories,
            catalogRepository = catalogRepository,
            countryCode = countryCode,
            onDismiss = { showTargetEditor = false },
            onSave = { result ->
                targetDraft = result.draft
                showTargetEditor = false
            },
        )
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Нормализовать URL") },
            text = {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Ссылка") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val rawUrl = urlInput.trim()
                        if (rawUrl.isBlank()) return@TextButton
                        showUrlDialog = false
                        scope.launch {
                            val mirrored = runCatching { mirrorByUrl(rawUrl) }.getOrNull()
                            if (mirrored == null) {
                                message = "Не удалось разобрать ссылку"
                                return@launch
                            }
                            applyMirrorResult(mirrored)?.let { draft ->
                                val withSource = draft.copy(
                                    sourceLabel = "из ссылки",
                                    autoFilledAttributes = draft.attributes.keys,
                                )
                                targetDraft = withSource
                                query = withSource.previewTitle(leafCategories)
                            } ?: run {
                                message = "Не удалось нормализовать URL в цель трека"
                            }
                        }
                    },
                ) { Text("Применить") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text("Отмена") }
            },
        )
    }
}

private fun buildMissingRequiredMessage(
    missingCodes: Set<String>,
    spec: CatalogCategoryEffectiveSpec?,
): String {
    if (missingCodes.isEmpty()) return ""
    val titleByCode = spec?.allAttributes()
        .orEmpty()
        .associate { def -> def.code.trim().lowercase() to def.title }
    val labels = missingCodes
        .map { code ->
            val normalized = code.trim().lowercase()
            titleByCode[normalized]?.takeIf { it.isNotBlank() } ?: code
        }
        .sorted()
    val preview = labels.take(3).joinToString(", ")
    return if (labels.size <= 3) {
        "Заполните обязательные параметры: $preview"
    } else {
        "Заполните обязательные параметры: $preview и ещё ${labels.size - 3}"
    }
}

@Composable
private fun TargetPreview(
    target: TrackTargetDraft,
    categories: List<com.example.shoppingassistant.domain.catalog.Category>,
    onReset: () -> Unit,
    onEdit: () -> Unit,
) {
    val title = target.previewTitle(categories)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (title.isBlank()) "Цель не выбрана" else title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!target.sourceLabel.isNullOrBlank()) {
                Text(
                    text = target.sourceLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onReset) { Text("Сбросить") }
                TextButton(onClick = onEdit) { Text("Редактировать") }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPickerSheet(
    categories: List<com.example.shoppingassistant.domain.catalog.Category>,
    onDismiss: () -> Unit,
    onSelect: (com.example.shoppingassistant.domain.catalog.Category) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filtered = remember(categories, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) categories
        else categories.filter {
            buildList {
                add(it.code)
                addAll(it.title.values)
                add(it.displayTitle(locale = java.util.Locale.getDefault().toLanguageTag()))
            }.joinToString(" ").lowercase().contains(q)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Выбрать категорию", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Поиск") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(filtered) { category ->
                    SuggestionRow(
                        title = category.displayTitle(locale = java.util.Locale.getDefault().toLanguageTag()),
                        subtitle = category.code,
                        onClick = { onSelect(category) },
                    )
                }
            }
        }
    }
}

private fun sanitizeAttributes(values: Map<String, String>): Map<String, String> {
    if (values.isEmpty()) return emptyMap()
    return values.entries
        .mapNotNull { (key, value) ->
            val normalizedKey = key.trim()
            val normalizedValue = value.trim()
            if (normalizedKey.isBlank() || normalizedValue.isBlank()) null
            else normalizedKey to normalizedValue
        }
        .sortedBy { it.first.lowercase() }
        .toMap(LinkedHashMap())
}

private fun loadImageBase64(context: Context, uri: Uri): String? {
    val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() ?: return null
    stream.use {
        val bytes = runCatching { it.readBytes() }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}

private fun applyMirrorResult(result: UgcMirrorResult): TrackTargetDraft? {
    val attrs = result.attributes
        .mapKeys { it.key.trim() }
        .mapValues { it.value.trim() }
        .filterKeys { it.isNotBlank() }
        .filterValues { it.isNotBlank() }
    val brand = attrs.entries.firstOrNull { it.key.equals("brand", ignoreCase = true) }?.value
    val model = attrs.entries.firstOrNull { it.key.equals("model", ignoreCase = true) }?.value
    val categoryCode = attrs.entries.firstOrNull {
        it.key.equals("categoryCode", ignoreCase = true) ||
            it.key.equals("category", ignoreCase = true)
    }?.value

    val attrsWithoutCategory = attrs - listOf("categoryCode", "category")
    val attrsWithoutCategoryAndBrandModel = attrsWithoutCategory - listOf("brand", "model")

    if (!brand.isNullOrBlank() && !model.isNullOrBlank()) {
        return TrackTargetDraft(
            type = TrackType.PRODUCT,
            brand = brand,
            model = model,
            categoryCode = categoryCode,
            attributes = attrsWithoutCategoryAndBrandModel,
        )
    }
    if (!categoryCode.isNullOrBlank()) {
        if (attrsWithoutCategoryAndBrandModel.isNotEmpty()) {
            return TrackTargetDraft(
                type = TrackType.PRODUCT,
                categoryCode = categoryCode,
                attributes = attrsWithoutCategoryAndBrandModel,
            )
        }
        return TrackTargetDraft(
            type = TrackType.CATEGORY,
            categoryCode = categoryCode,
            attributes = attrsWithoutCategory,
        )
    }
    val fromTitle = result.title.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (fromTitle.size >= 2) {
        return TrackTargetDraft(
            type = TrackType.PRODUCT,
            brand = fromTitle.first(),
            model = fromTitle.drop(1).joinToString(" "),
            attributes = attrs,
        )
    }
    return null
}


