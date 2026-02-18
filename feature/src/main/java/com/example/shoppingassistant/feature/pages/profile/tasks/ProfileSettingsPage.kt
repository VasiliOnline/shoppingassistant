package com.example.shoppingassistant.feature.pages.profile.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shoppingassistant.core.data.menu.UserPanelStore
import com.example.shoppingassistant.domain.menu.Handedness
import com.example.shoppingassistant.domain.menu.UserPanel
import com.example.shoppingassistant.domain.profile.BottomBarStyle
import com.example.shoppingassistant.domain.profile.ProfileSettings
import com.example.shoppingassistant.domain.profile.ProfileThemePreference
import com.example.shoppingassistant.feature.R
import com.example.shoppingassistant.feature.ui.layout.LayoutDefaults
import com.example.shoppingassistant.feature.ui.menu.UserPanelEditor
import com.example.shoppingassistant.feature.ui.menu.defaultActionCatalog
import com.example.shoppingassistant.feature.ui.menu.defaultModeCatalog
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get as koinGet

/**
 * Страница «Настройки» для профиля. Заменяет модальное окно настроек,
 * размещая все параметры на отдельном экране с единым стилем.
 *
 * @param settings Текущие настройки профиля.
 * @param onSettingsChange Обратный вызов для уведомления об изменении настроек.
 * @param onBackClick Вызывается при нажатии на стрелку «Назад».
 */
@Composable
fun ProfileSettingsPage(
    settings: ProfileSettings,
    onSettingsChange: (ProfileSettings) -> Unit,
    onBackClick: () -> Unit,
) {
    // Локальные состояния для каждого параметра
    var language by remember(settings.languageCode) { mutableStateOf(settings.languageCode) }
    var theme by remember(settings.theme) { mutableStateOf(settings.theme) }
    var hideUndeliverable by remember(settings.hideUndeliverable) { mutableStateOf(settings.hideUndeliverable) }
    var bottomBarStyle by remember(settings.bottomBarStyle) { mutableStateOf(settings.bottomBarStyle) }
    val scope = rememberCoroutineScope()
    val userPanelStore: UserPanelStore = remember { koinGet(UserPanelStore::class.java) }
    val userPanel by userPanelStore.panel.collectAsState()
    var panelEditorVisible by remember { mutableStateOf(false) }
    var panelDraft by remember { mutableStateOf<UserPanel?>(null) }
    val modeCatalog = remember { defaultModeCatalog(isLoggedIn = true) }
    val actionCatalog = remember { defaultActionCatalog() }

    // При изменении локальных состояний - отправляем новое состояние наружу,
    // но избегаем лишних обновлений при совпадении с текущими настройками.
    LaunchedEffect(language, theme, hideUndeliverable, bottomBarStyle, settings) {
        val updated = settings.copy(
            languageCode = language,
            theme = theme,
            hideUndeliverable = hideUndeliverable,
            bottomBarStyle = bottomBarStyle,
        )
        if (updated != settings) {
            onSettingsChange(updated)
        }
    }
    LaunchedEffect(Unit) {
        userPanelStore.load()
    }

    val scrollState = rememberScrollState()

    fun openPanelEditor() {
        panelDraft = userPanel.copy(isEditMode = true)
        panelEditorVisible = true
    }

    fun updatePanelDraft(updated: UserPanel) {
        panelDraft = updated.copy(isEditMode = true)
    }

    fun commitPanelEdit() {
        val updated = (panelDraft ?: userPanel).copy(isEditMode = false)
        scope.launch { userPanelStore.update(updated) }
        panelEditorVisible = false
        panelDraft = null
    }

    fun cancelPanelEdit() {
        panelEditorVisible = false
        panelDraft = null
    }

    fun resetPanelEdit() {
        panelDraft = com.example.shoppingassistant.core.data.menu.UserPanelDefaults.defaultPanel().copy(isEditMode = true)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    horizontal = LayoutDefaults.HorizontalPadding,
                    vertical = LayoutDefaults.SectionSpacing,
                ),
            verticalArrangement = Arrangement.spacedBy(LayoutDefaults.LargeSectionSpacing),
        ) {
            // Заголовок с кнопкой назад
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.profile_back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.profile_settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Карточка с настройками
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = LayoutDefaults.CardInnerPadding,
                            vertical = LayoutDefaults.LargeSectionSpacing,
                        ),
                    verticalArrangement = Arrangement.spacedBy(LayoutDefaults.LargeSectionSpacing),
                ) {
                    SettingsBlock(title = stringResource(R.string.profile_settings_language)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LanguageChip(
                                label = stringResource(R.string.profile_settings_language_ru),
                                selected = language.equals("RU", ignoreCase = true),
                                onClick = { language = "RU" },
                            )
                            LanguageChip(
                                label = stringResource(R.string.profile_settings_language_en),
                                selected = language.equals("EN", ignoreCase = true),
                                onClick = { language = "EN" },
                            )
                            LanguageChip(
                                label = stringResource(R.string.profile_settings_language_system),
                                selected = language.isBlank(),
                                onClick = { language = "" },
                            )
                        }
                    }
                    SettingsBlock(title = stringResource(R.string.profile_settings_theme)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ThemeChip(
                                icon = Icons.Outlined.LightMode,
                                label = stringResource(R.string.profile_settings_theme_light),
                                selected = theme == ProfileThemePreference.LIGHT,
                                onClick = { theme = ProfileThemePreference.LIGHT },
                            )
                            ThemeChip(
                                icon = Icons.Outlined.DarkMode,
                                label = stringResource(R.string.profile_settings_theme_dark),
                                selected = theme == ProfileThemePreference.DARK,
                                onClick = { theme = ProfileThemePreference.DARK },
                            )
                            ThemeChip(
                                icon = Icons.Outlined.Settings,
                                label = stringResource(R.string.profile_settings_theme_system),
                                selected = theme == ProfileThemePreference.SYSTEM,
                                onClick = { theme = ProfileThemePreference.SYSTEM },
                            )
                        }
                    }
                SettingsBlock(
                    title = stringResource(R.string.profile_settings_hide_undeliverable_title),
                    subtitle = stringResource(R.string.profile_settings_hide_undeliverable_subtitle),
                ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = if (hideUndeliverable) {
                                    stringResource(R.string.profile_settings_filter_on)
                                } else {
                                    stringResource(R.string.profile_settings_filter_off)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        Switch(
                            checked = hideUndeliverable,
                            onCheckedChange = { hideUndeliverable = it },
                        )
                    }
                }

                SettingsBlock(
                    title = "Bottom menu style",
                    subtitle = "Choose the bottom navigation background.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BottomBarChip(
                            label = "Solid",
                            selected = bottomBarStyle == BottomBarStyle.SOLID,
                            onClick = { bottomBarStyle = BottomBarStyle.SOLID },
                        )
                        BottomBarChip(
                            label = "Blur",
                            selected = bottomBarStyle == BottomBarStyle.BLUR,
                            onClick = { bottomBarStyle = BottomBarStyle.BLUR },
                        )
                        BottomBarChip(
                            label = "Transparent",
                            selected = bottomBarStyle == BottomBarStyle.TRANSPARENT,
                            onClick = { bottomBarStyle = BottomBarStyle.TRANSPARENT },
                        )
                        BottomBarChip(
                            label = "Color",
                            selected = bottomBarStyle == BottomBarStyle.PRIMARY,
                            onClick = { bottomBarStyle = BottomBarStyle.PRIMARY },
                        )
                    }
                }

                SettingsBlock(
                    title = "Navigation panel",
                    subtitle = "Manage quick actions and the side rail position.",
                ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Rail on right",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Switch(
                                checked = userPanel.handedness == Handedness.RIGHT,
                                onCheckedChange = { enabled ->
                                    val next = if (enabled) Handedness.RIGHT else Handedness.LEFT
                                    scope.launch { userPanelStore.update(userPanel.copy(handedness = next)) }
                                },
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { openPanelEditor() }) {
                                Text(text = "Настроить панель")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = { scope.launch { userPanelStore.reset() } }) {
                                Text(text = "Сбросить панель")
                            }
                        }
                    }
                }
            }
        }

        if (panelEditorVisible) {
            UserPanelEditor(
                panel = panelDraft ?: userPanel,
                config = com.example.shoppingassistant.core.data.menu.UserPanelDefaults.config,
                modeCatalog = modeCatalog,
                actionCatalog = actionCatalog,
                onPanelChange = ::updatePanelDraft,
                onDone = ::commitPanelEdit,
                onCancel = ::cancelPanelEdit,
                onReset = ::resetPanelEdit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SettingsBlock(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

@Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = null,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            selectedLabelColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun ThemeChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            selectedLabelColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun BottomBarChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            selectedLabelColor = MaterialTheme.colorScheme.primary,
        ),
    )
}
