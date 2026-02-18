package com.example.shoppingassistant.feature.pages.profile.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.shoppingassistant.domain.profile.ProfileSettings
import com.example.shoppingassistant.feature.R
import com.example.shoppingassistant.feature.pages.profile.ProfileEmail
import com.example.shoppingassistant.feature.pages.profile.ProfileState

/**
 * Базовый контейнер профиля в виде всплывающей нижней страницы.
 *
 * - Привязан к низу, занимает 80% ширины и 80% высоты, оставляя видимым фон.
 * - В шапке показывает название страницы и крестик закрытия.
 * - Контентная область отдаётся во внешний слот, чтобы поверх неё можно было класть модалки.
 */

@Composable
fun ProfileSheetContainer(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .shadow(
                    elevation = 18.dp,
                    shape = RectangleShape,
                    clip = false,
                ),
            color = MaterialTheme.colorScheme.surface,
            shape = RectangleShape,
            tonalElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                            ),
                        ),
                    ),
            ) {
                // кромка для жеста
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                            ),
                    )
                }

                // шапка с заголовком и крестиком
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.profile_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    content = content,
                )
            }
        }
    }
}

/**
 * Лист для управления личными данными.
 * Использует готовую задачу ProfilePersonalDataTask и отображает её внутри ProfileSheetContainer.
 */
@Composable
fun ProfilePersonalDataSheet(
    profile: ProfileState.Authorized,
    onClose: () -> Unit,
) {
    // локальные списки email и телефона, чтобы не менять ProfileState напрямую
    val emails: SnapshotStateList<ProfileEmail> = remember(profile.id) {
        mutableStateListOf<ProfileEmail>().apply { addAll(profile.emails) }
    }
    var phone by remember(profile.id) { mutableStateOf(profile.phone.orEmpty()) }

    ProfileSheetContainer(
        title = stringResource(R.string.profile_personal_title),
        onClose = onClose,
    ) {
        ProfilePersonalDataTask(
            emails = emails,
            phone = phone,
            onPhoneChanged = { phone = it },
            onBackClick = onClose,
        )
    }
}

/**
 * Лист для настроек профиля.
 * Использует готовую ProfileSettingsTask и отдаёт изменения наружу.
 */
@Composable
fun ProfileSettingsSheet(
    settings: ProfileSettings,
    onSettingsChange: (ProfileSettings) -> Unit,
    onClose: () -> Unit,
) {
    ProfileSheetContainer(
        title = stringResource(R.string.profile_settings_title),
        onClose = onClose,
    ) {
        ProfileSettingsTask(
            settings = settings,
            onSettingsChange = onSettingsChange,
            onBackClick = onClose,
        )
    }
}