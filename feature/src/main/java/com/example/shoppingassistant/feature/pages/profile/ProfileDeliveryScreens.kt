package com.example.shoppingassistant.feature.pages.profile

import android.location.Geocoder
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shoppingassistant.domain.profile.BuyerDeliveryAddress
import com.example.shoppingassistant.domain.profile.DeliveryAddressLocation
import com.example.shoppingassistant.domain.profile.DeliveryAreaScope
import com.example.shoppingassistant.domain.profile.ProfileSettings
import com.example.shoppingassistant.domain.profile.SellerDeliveryZone
import com.example.shoppingassistant.domain.profile.bestLabel
import com.example.shoppingassistant.domain.profile.displayLabel
import com.example.shoppingassistant.domain.profile.hasDeliveryZoneConflict
import com.example.shoppingassistant.domain.profile.normalized
import com.example.shoppingassistant.domain.profile.normalizedDeliveryAddresses
import com.example.shoppingassistant.domain.profile.normalizedDeliveryZones
import com.example.shoppingassistant.domain.profile.withNormalizedDeliveryAddresses
import com.example.shoppingassistant.feature.pages.common.DeliveryAddressSuggestion
import com.example.shoppingassistant.feature.pages.common.findDeliveryAddressSuggestions
import com.example.shoppingassistant.feature.ui.layout.LayoutDefaults
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BuyerDeliveryAddressesScreen(
    settings: ProfileSettings,
    contentPadding: PaddingValues,
    onSettingsChange: (ProfileSettings) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val geocoder = remember(context) { Geocoder(context, Locale.getDefault()) }
    val coroutineScope = rememberCoroutineScope()
    val normalizedSettings = remember(settings) { settings.withNormalizedDeliveryAddresses() }
    val addresses = normalizedSettings.deliveryAddresses.normalizedDeliveryAddresses()
    val activeAddressId = normalizedSettings.activeDeliveryAddressId

    var query by rememberSaveable { mutableStateOf("") }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    val suggestionsState = rememberDeliverySuggestions(geocoder = geocoder, query = query)
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(contentPadding)
            .padding(horizontal = LayoutDefaults.HorizontalPadding, vertical = LayoutDefaults.SectionSpacing),
        verticalArrangement = Arrangement.spacedBy(LayoutDefaults.LargeSectionSpacing),
    ) {
        DeliveryIntroCard(
            title = "Адрес получателя",
            subtitle = "Можно хранить несколько адресов, но активным всегда остаётся только один. Активный адрес используется для фильтра «Есть доставка».",
        )

        DeliverySectionCard(
            title = "Добавить адрес",
            subtitle = "Начните вводить реальный адрес, затем выберите подходящий вариант из подсказок.",
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    notice = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Адрес доставки") },
                singleLine = true,
            )
            DeliveryAutocompleteBlock(
                isLoading = suggestionsState.isLoading,
                suggestions = suggestionsState.suggestions,
                emptyMessage = suggestionsState.emptyMessage,
                onSuggestionClick = { suggestion ->
                    val updated = appendDeliveryAddress(
                        settings = normalizedSettings,
                        suggestion = suggestion,
                    )
                    if (updated == null) {
                        notice = "Этот адрес уже есть в списке."
                        return@DeliveryAutocompleteBlock
                    }
                    onSettingsChange(updated)
                    query = ""
                    notice = "Адрес сохранён."
                },
            )
            notice?.let { message ->
                DeliveryHintText(message)
            }
        }

        DeliverySectionCard(
            title = "Сохранённые адреса",
            subtitle = if (addresses.isEmpty()) {
                "Пока нет адресов. Добавьте хотя бы один активный адрес для точной фильтрации доставки."
            } else {
                "Адреса можно переключать, удалять и использовать как активный адрес получателя."
            },
        ) {
            if (addresses.isEmpty()) {
                DeliveryEmptyState("Список адресов пуст.")
            } else {
                addresses.forEachIndexed { index, address ->
                    SavedBuyerAddressRow(
                        address = address,
                        isActive = address.id == activeAddressId,
                        onMakeActive = {
                            onSettingsChange(
                                normalizedSettings.copy(activeDeliveryAddressId = address.id)
                                    .withNormalizedDeliveryAddresses(),
                            )
                            notice = "Активный адрес обновлён."
                        },
                        onDelete = {
                            val updated = normalizedSettings.copy(
                                deliveryAddresses = addresses.filterNot { item -> item.id == address.id },
                            ).withNormalizedDeliveryAddresses()
                            onSettingsChange(updated)
                            notice = "Адрес удалён."
                        },
                    )
                    if (index != addresses.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    }
                }
            }
        }
    }
}

@Composable
fun SellerDeliveryZonesScreen(
    zones: List<SellerDeliveryZone>,
    contentPadding: PaddingValues,
    onSaveZones: suspend (List<SellerDeliveryZone>) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val geocoder = remember(context) { Geocoder(context, Locale.getDefault()) }
    val coroutineScope = rememberCoroutineScope()
    val normalizedZones = remember(zones) { zones.normalizedDeliveryZones() }

    var query by rememberSaveable { mutableStateOf("") }
    var selectedSuggestion by remember { mutableStateOf<DeliveryAddressSuggestion?>(null) }
    var selectedScope by remember { mutableStateOf<DeliveryAreaScope?>(null) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val suggestionsState = rememberDeliverySuggestions(geocoder = geocoder, query = query)
    val scrollState = rememberScrollState()

    val availableScopes = remember(selectedSuggestion) {
        selectedSuggestion?.let { suggestion ->
            deliveryScopesForLocation(suggestion.location)
        }.orEmpty()
    }

    LaunchedEffect(availableScopes) {
        if (selectedScope !in availableScopes) {
            selectedScope = availableScopes.firstOrNull()
        }
    }

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(contentPadding)
            .padding(horizontal = LayoutDefaults.HorizontalPadding, vertical = LayoutDefaults.SectionSpacing),
        verticalArrangement = Arrangement.spacedBy(LayoutDefaults.LargeSectionSpacing),
    ) {
        DeliveryIntroCard(
            title = "Зоны доставки продавца",
            subtitle = "Добавляйте зоны в разрезе страны, региона, города или точки. Вложенные и пересекающиеся зоны не допускаются.",
        )

        DeliverySectionCard(
            title = "Добавить зону",
            subtitle = "Подберите адрес через подсказки, затем выберите уровень зоны: страна, регион, город или конкретная точка.",
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    selectedSuggestion = null
                    notice = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Найти адрес или населённый пункт") },
                singleLine = true,
            )
            DeliveryAutocompleteBlock(
                isLoading = suggestionsState.isLoading,
                suggestions = suggestionsState.suggestions,
                emptyMessage = suggestionsState.emptyMessage,
                onSuggestionClick = { suggestion ->
                    selectedSuggestion = suggestion
                    selectedScope = deliveryScopesForLocation(suggestion.location).firstOrNull()
                    notice = null
                },
            )

            selectedSuggestion?.let { suggestion ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DeliverySelectedSuggestionCard(suggestion = suggestion)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        availableScopes.forEach { scope ->
                            FilterChip(
                                selected = selectedScope == scope,
                                onClick = { selectedScope = scope },
                                label = { Text(scope.deliveryScopeLabel()) },
                            )
                        }
                    }
                    TextButton(
                        enabled = !isSaving && selectedScope != null,
                        onClick = {
                            val scope = selectedScope ?: return@TextButton
                            val candidate = SellerDeliveryZone(
                                id = UUID.randomUUID().toString(),
                                scope = scope,
                                location = suggestion.location.normalized(),
                            )
                            if (hasDeliveryZoneConflict(normalizedZones, candidate)) {
                                notice = "Эта зона уже покрыта другой зоной доставки."
                                return@TextButton
                            }
                            coroutineScope.launch {
                                isSaving = true
                                runCatching {
                                    onSaveZones((normalizedZones + candidate).normalizedDeliveryZones())
                                }.onSuccess {
                                    query = ""
                                    selectedSuggestion = null
                                    selectedScope = null
                                    notice = "Зона доставки сохранена."
                                }.onFailure { throwable ->
                                    notice = throwable.message ?: "Не удалось сохранить зону доставки."
                                }
                                isSaving = false
                            }
                        },
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        } else {
                            Icon(imageVector = Icons.Outlined.AddLocationAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Добавить зону")
                    }
                }
            }

            notice?.let { message ->
                DeliveryHintText(message)
            }
        }

        DeliverySectionCard(
            title = "Добавленные зоны",
            subtitle = if (normalizedZones.isEmpty()) {
                "Пока нет зон доставки. Если список пуст, выдача считает доставку неограниченной."
            } else {
                "Список зон, которые уже участвуют в фильтрации выдачи."
            },
        ) {
            if (normalizedZones.isEmpty()) {
                DeliveryEmptyState("Зоны доставки ещё не добавлены.")
            } else {
                normalizedZones.forEachIndexed { index, zone ->
                    SavedSellerZoneRow(
                        zone = zone,
                        onDelete = {
                            coroutineScope.launch {
                                isSaving = true
                                runCatching {
                                    onSaveZones(normalizedZones.filterNot { item -> item.id == zone.id })
                                }.onSuccess {
                                    notice = "Зона доставки удалена."
                                }.onFailure { throwable ->
                                    notice = throwable.message ?: "Не удалось удалить зону доставки."
                                }
                                isSaving = false
                            }
                        },
                    )
                    if (index != normalizedZones.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberDeliverySuggestions(
    geocoder: Geocoder,
    query: String,
): DeliverySuggestionsState {
    val state by produceState(
        initialValue = DeliverySuggestionsState(),
        geocoder,
        query,
    ) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.length < 3) {
            value = DeliverySuggestionsState(
                suggestions = emptyList(),
                isLoading = false,
                emptyMessage = if (normalizedQuery.isEmpty()) {
                    "Введите минимум 3 символа, чтобы увидеть реальные адреса."
                } else {
                    null
                },
            )
            return@produceState
        }
        value = DeliverySuggestionsState(isLoading = true)
        delay(280L)
        val suggestions = findDeliveryAddressSuggestions(
            geocoder = geocoder,
            query = normalizedQuery,
        )
        value = DeliverySuggestionsState(
            suggestions = suggestions,
            isLoading = false,
            emptyMessage = if (suggestions.isEmpty()) {
                "Подходящих адресов не найдено. Уточните запрос."
            } else {
                null
            },
        )
    }
    return state
}

@Composable
private fun DeliveryIntroCard(
    title: String,
    subtitle: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeliverySectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun DeliveryAutocompleteBlock(
    isLoading: Boolean,
    suggestions: List<DeliveryAddressSuggestion>,
    emptyMessage: String?,
    onSuggestionClick: (DeliveryAddressSuggestion) -> Unit,
) {
    when {
        isLoading -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Ищем реальные адреса…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        suggestions.isNotEmpty() -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { suggestion ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSuggestionClick(suggestion) },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = suggestion.primaryText,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                            )
                            suggestion.secondaryText?.let { secondary ->
                                Text(
                                    text = secondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        emptyMessage != null -> DeliveryHintText(emptyMessage)
    }
}

@Composable
private fun SavedBuyerAddressRow(
    address: BuyerDeliveryAddress,
    isActive: Boolean,
    onMakeActive: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Outlined.CheckCircle else Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = address.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = address.location.bestLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isActive) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("Активный адрес") },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        disabledLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (!isActive) {
                TextButton(onClick = onMakeActive) {
                    Text("Сделать активным")
                }
            }
            TextButton(onClick = onDelete) {
                Icon(imageVector = Icons.Outlined.DeleteOutline, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Удалить")
            }
        }
    }
}

@Composable
private fun SavedSellerZoneRow(
    zone: SellerDeliveryZone,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = zone.scope.deliveryScopeIcon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = zone.scope.deliveryScopeLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = zone.displayLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDelete) {
            Icon(imageVector = Icons.Outlined.DeleteOutline, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Удалить")
        }
    }
}

@Composable
private fun DeliverySelectedSuggestionCard(
    suggestion: DeliveryAddressSuggestion,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = suggestion.primaryText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            suggestion.secondaryText?.let { secondary ->
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeliveryHintText(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DeliveryEmptyState(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun appendDeliveryAddress(
    settings: ProfileSettings,
    suggestion: DeliveryAddressSuggestion,
): ProfileSettings? {
    val normalizedLocation = suggestion.location.normalized()
    val existing = settings.deliveryAddresses.normalizedDeliveryAddresses()
    if (existing.any { address -> sameDeliveryLocation(address.location, normalizedLocation) }) {
        return null
    }
    val nextAddress = BuyerDeliveryAddress(
        id = UUID.randomUUID().toString(),
        label = normalizedLocation.bestLabel(),
        location = normalizedLocation,
    )
    val nextSettings = settings.copy(
        deliveryAddresses = existing + nextAddress,
        activeDeliveryAddressId = settings.activeDeliveryAddressId ?: nextAddress.id,
    )
    return nextSettings.withNormalizedDeliveryAddresses()
}

private fun sameDeliveryLocation(
    left: DeliveryAddressLocation,
    right: DeliveryAddressLocation,
): Boolean {
    val normalizedLeft = left.normalized()
    val normalizedRight = right.normalized()
    return normalizedLeft.countryCode == normalizedRight.countryCode &&
        normalizedLeft.adminArea == normalizedRight.adminArea &&
        normalizedLeft.locality == normalizedRight.locality &&
        normalizedLeft.addressLine == normalizedRight.addressLine
}

private fun deliveryScopesForLocation(location: DeliveryAddressLocation): List<DeliveryAreaScope> = buildList {
    add(DeliveryAreaScope.COUNTRY)
    if (!location.adminArea.isNullOrBlank()) add(DeliveryAreaScope.REGION)
    if (!location.locality.isNullOrBlank()) add(DeliveryAreaScope.CITY)
    if (!location.addressLine.isNullOrBlank() || (location.lat != null && location.lon != null)) {
        add(DeliveryAreaScope.POINT)
    }
}

private fun DeliveryAreaScope.deliveryScopeLabel(): String = when (this) {
    DeliveryAreaScope.COUNTRY -> "Страна"
    DeliveryAreaScope.REGION -> "Регион"
    DeliveryAreaScope.CITY -> "Город"
    DeliveryAreaScope.POINT -> "Точка"
}

private fun DeliveryAreaScope.deliveryScopeIcon() = when (this) {
    DeliveryAreaScope.COUNTRY -> Icons.Outlined.Map
    DeliveryAreaScope.REGION -> Icons.Outlined.Place
    DeliveryAreaScope.CITY -> Icons.Outlined.LocationCity
    DeliveryAreaScope.POINT -> Icons.Outlined.LocationOn
}

private data class DeliverySuggestionsState(
    val suggestions: List<DeliveryAddressSuggestion> = emptyList(),
    val isLoading: Boolean = false,
    val emptyMessage: String? = null,
)
