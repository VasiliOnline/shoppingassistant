package com.example.shoppingassistant.feature.pages.localoffer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftSession
import com.example.shoppingassistant.domain.localoffer.LocalOfferEvidenceBlockingLevel
import com.example.shoppingassistant.domain.localoffer.LocalOfferFlowStep
import com.example.shoppingassistant.domain.localoffer.LocalOfferGeoFreshnessState
import com.example.shoppingassistant.domain.localoffer.LocalOfferIncomingPhoto
import com.example.shoppingassistant.domain.localoffer.LocalOfferIssue
import com.example.shoppingassistant.domain.localoffer.LocalOfferNode
import com.example.shoppingassistant.domain.localoffer.LocalOfferPhotoRole
import com.example.shoppingassistant.domain.localoffer.LocalOfferPreviewResponse
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublicationState
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishOutcome
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishPreflightResponse
import com.example.shoppingassistant.domain.ugc.draft.DraftInputOrigin
import com.example.shoppingassistant.domain.ugc.draft.DraftMedia
import com.example.shoppingassistant.feature.pages.common.DeviceLocationSnapshot
import com.example.shoppingassistant.feature.pages.common.resolveDeviceLocation
import com.example.shoppingassistant.feature.pages.draft.create.DraftMediaImporter
import com.example.shoppingassistant.feature.ui.state.SystemNoticeCard
import com.example.shoppingassistant.feature.ui.state.SystemNoticeTone
import java.io.File
import java.net.URI
import java.util.LinkedHashMap
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalOfferSheet(
    visible: Boolean,
    draftId: String?,
    origin: DraftInputOrigin? = null,
    onDismiss: () -> Unit,
    viewModel: LocalOfferViewModel = koinViewModel(),
) {
    if (!visible) return

    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importer = remember { DraftMediaImporter(context) }
    var pendingRole by rememberSaveable { mutableStateOf<LocalOfferPhotoRole?>(null) }
    var capturePreflightReport by remember { mutableStateOf<LocalOfferCapturePreflightReport?>(null) }
    var geoRequestAutoTriggered by rememberSaveable(visible, draftId, origin) { mutableStateOf(false) }
    var cameraPermissionAutoTriggered by rememberSaveable(visible, draftId, origin) { mutableStateOf(false) }
    var captureScreenPermissionRequest by remember { mutableStateOf(false) }
    var cameraPermissionPermanentlyDenied by rememberSaveable(visible, draftId, origin) { mutableStateOf(false) }
    var hasCameraPermission by rememberSaveable(visible, draftId, origin) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var pendingPhotoSubmission by remember { mutableStateOf<PendingLocalOfferPhotoSubmission?>(null) }
    lateinit var submitPhoto: suspend (DraftMedia?, LocalOfferPhotoRole, String) -> Unit

    suspend fun confirmDeviceLocation(snapshot: DeviceLocationSnapshot?) {
        val resolved = snapshot ?: run {
            viewModel.showGeoUnavailableBlockedState()
            return
        }
        val city = resolved.city?.trim().takeIf { !it.isNullOrEmpty() } ?: run {
            viewModel.showGeoUnavailableBlockedState()
            return
        }
        viewModel.confirmDeviceGeoSnapshot(
            city = city,
            adminArea = resolved.addressLine,
            countryCode = resolved.countryCode,
            lat = resolved.lat,
            lon = resolved.lon,
        )
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val role = pendingRole ?: recommendedLocalOfferPhotoRole(state.draftEnvelope?.draft)
        pendingRole = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val media = importer.importPhoto(uri)
            submitPhoto(media, role, "Не удалось импортировать фото.")
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        val role = pendingRole ?: recommendedLocalOfferPhotoRole(state.draftEnvelope?.draft)
        pendingRole = null
        if (bitmap == null) return@rememberLauncherForActivityResult
        scope.launch {
            val media = importer.importBitmap(bitmap)
            submitPhoto(media, role, "Не удалось обработать снимок.")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        if (granted) {
            cameraPermissionPermanentlyDenied = false
        }
        val requestedFromCaptureScreen = captureScreenPermissionRequest
        captureScreenPermissionRequest = false
        val deniedPermanent = if (!granted) {
            val activity = context.findActivity()
            activity?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.CAMERA,
                )
            } == true
        } else {
            false
        }
        if (deniedPermanent) {
            cameraPermissionPermanentlyDenied = true
        }
        when {
            granted && requestedFromCaptureScreen -> Unit
            granted -> cameraLauncher.launch(null)
            deniedPermanent -> {
                pendingRole = null
                if (!requestedFromCaptureScreen) {
                    Toast.makeText(context, "Доступ к камере отключён. Откройте настройки приложения.", Toast.LENGTH_LONG).show()
                    context.openAppSettings()
                }
            }
            else -> {
                pendingRole = null
                if (!requestedFromCaptureScreen) {
                    Toast.makeText(context, "Для камеры нужен доступ.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val deniedPermanent = if (!granted) {
            val activity = context.findActivity()
            activity?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            } == true
        } else {
            false
        }
        when {
            granted -> scope.launch { confirmDeviceLocation(resolveDeviceLocation(context)) }
            deniedPermanent -> {
                viewModel.showGeoAccessBlockedState(permanentlyDenied = true)
            }
            else -> {
                viewModel.showGeoAccessBlockedState(permanentlyDenied = false)
            }
        }
    }

    val requestDeviceLocation: () -> Unit = {
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        if (permission == PackageManager.PERMISSION_GRANTED) {
            scope.launch { confirmDeviceLocation(resolveDeviceLocation(context)) }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    submitPhoto = submitPhotoHandler@{ media, role, errorMessage ->
        if (media == null) {
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            return@submitPhotoHandler
        }
        val preflight = analyzeLocalOfferCapturePreflight(context, media, role)
        capturePreflightReport = preflight.takeIf { report -> report.issues.isNotEmpty() }
        if (preflight.hasBlockingIssues) {
            Toast.makeText(context, "Снимок лучше переснять до загрузки.", Toast.LENGTH_LONG).show()
            return@submitPhotoHandler
        }
        val photo = media.toIncomingPhoto(role)
        if (photo == null) {
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            return@submitPhotoHandler
        }
        if (state.geoSnapshot?.freshnessState == LocalOfferGeoFreshnessState.FRESH) {
            viewModel.addPhoto(photo)
        } else {
            pendingPhotoSubmission = PendingLocalOfferPhotoSubmission(photo)
            requestDeviceLocation()
        }
    }

    fun openGallery(role: LocalOfferPhotoRole) {
        pendingRole = role
        galleryLauncher.launch("image/*")
    }

    fun openCamera(role: LocalOfferPhotoRole) {
        pendingRole = role
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permission == PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
            cameraLauncher.launch(null)
        } else {
            captureScreenPermissionRequest = false
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun requestCaptureScreenCameraAccess() {
        if (cameraPermissionPermanentlyDenied) {
            context.openAppSettings()
        } else {
            captureScreenPermissionRequest = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val screen = resolveLocalOfferRenderedScreen(state)
    val screenDescriptor = describeLocalOfferScreen(state)

    LaunchedEffect(visible, draftId, origin) {
        if (visible) {
            capturePreflightReport = null
            geoRequestAutoTriggered = false
            cameraPermissionAutoTriggered = false
            captureScreenPermissionRequest = false
            cameraPermissionPermanentlyDenied = false
            hasCameraPermission =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            pendingPhotoSubmission = null
            viewModel.openDraft(draftId = draftId, origin = origin)
        }
    }

    LaunchedEffect(
        visible,
        state.activeStep,
        state.blockedState,
        hasCameraPermission,
        cameraPermissionAutoTriggered,
    ) {
        if (!visible) return@LaunchedEffect
        if (screen != LocalOfferRenderedScreen.PHOTO) return@LaunchedEffect
        if (state.blockedState != null) return@LaunchedEffect
        if (hasCameraPermission) return@LaunchedEffect
        if (cameraPermissionAutoTriggered) return@LaunchedEffect
        cameraPermissionAutoTriggered = true
        requestCaptureScreenCameraAccess()
    }

    LaunchedEffect(
        visible,
        state.activeStep,
        state.geoSnapshot,
        state.blockedState,
        state.loading,
        state.submitting,
        geoRequestAutoTriggered,
        hasCameraPermission,
    ) {
        if (!visible) return@LaunchedEffect
        if (geoRequestAutoTriggered) return@LaunchedEffect
        if (state.loading || state.submitting || state.blockedState != null) return@LaunchedEffect
        if (state.activeStep != LocalOfferFlowStep.PRIMARY_PHOTO_CAPTURE) return@LaunchedEffect
        if (!hasCameraPermission) return@LaunchedEffect
        if (state.geoSnapshot != null) return@LaunchedEffect
        geoRequestAutoTriggered = true
        requestDeviceLocation()
    }

    LaunchedEffect(
        state.geoSnapshot?.geoSnapshotId,
        state.geoSnapshot?.freshnessState,
        pendingPhotoSubmission,
    ) {
        val pendingPhoto = pendingPhotoSubmission ?: return@LaunchedEffect
        if (state.geoSnapshot?.freshnessState != LocalOfferGeoFreshnessState.FRESH) return@LaunchedEffect
        pendingPhotoSubmission = null
        viewModel.addPhoto(pendingPhoto.photo)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (screen == LocalOfferRenderedScreen.PHOTO) {
                LocalOfferPhotoCaptureScreen(
                    hasCameraPermission = hasCameraPermission,
                    permissionPermanentlyDenied = cameraPermissionPermanentlyDenied,
                    busy = state.submitting || state.loading || pendingPhotoSubmission != null,
                    geoPending = state.geoSnapshot?.freshnessState != LocalOfferGeoFreshnessState.FRESH,
                    geoCity = state.geoSnapshot?.city,
                    errorMessage = state.errorMessage,
                    preflightReport = capturePreflightReport,
                    onDismissIssue = {
                        capturePreflightReport = null
                        viewModel.clearError()
                    },
                    onDismiss = onDismiss,
                    onRequestPermission = ::requestCaptureScreenCameraAccess,
                    onOpenSettings = context::openAppSettings,
                    onPickGallery = { openGallery(LocalOfferPhotoRole.FRONT) },
                    onCaptureBitmap = { bitmap ->
                        val media = importer.importBitmap(bitmap)
                        submitPhoto(media, LocalOfferPhotoRole.FRONT, "Не удалось обработать снимок.")
                    },
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    LocalOfferStageHeader(
                        descriptor = screenDescriptor,
                        onDismiss = onDismiss,
                    )
                    LinearProgressIndicator(
                        progress = { screenDescriptor.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        state.errorMessage?.let { message ->
                            MessageCard(
                                title = "Нужна правка",
                                body = message,
                                tone = MessageTone.Error,
                                actionLabel = "Скрыть",
                                onAction = viewModel::clearError,
                            )
                        }

                        capturePreflightReport?.let { report ->
                            CapturePreflightCard(
                                report = report,
                                onDismiss = { capturePreflightReport = null },
                            )
                        }

                        AnimatedContent(
                            targetState = screen,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "LocalOfferStageContent",
                        ) { renderedScreen ->
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                when (renderedScreen) {
                                    LocalOfferRenderedScreen.LOADING -> {
                                        MessageCard(
                                            title = "Подготавливаем объявление",
                                            body = "Проверяем местоположение и восстанавливаем ваши данные.",
                                            tone = MessageTone.Neutral,
                                        )
                                    }

                                    LocalOfferRenderedScreen.BLOCKED -> {
                                        BlockedStateCard(
                                            blockedState = state.blockedState,
                                            onDismissBlockedState = viewModel::dismissBlockedState,
                                            onPrimaryAction = {
                                                when (state.blockedState?.action) {
                                                    LocalOfferBlockedAction.REQUEST_DEVICE_LOCATION -> requestDeviceLocation()
                                                    LocalOfferBlockedAction.OPEN_APP_SETTINGS -> context.openAppSettings()
                                                    null -> Unit
                                                }
                                            },
                                            onStartNew = viewModel::startNewDraft,
                                        )
                                    }

                                    LocalOfferRenderedScreen.RESULT -> {
                                        PublishResultCard(
                                            state = state,
                                            onDismissResult = viewModel::dismissPublishResult,
                                            onStartNew = viewModel::startNewDraft,
                                            onClose = onDismiss,
                                        )
                                    }

                                    LocalOfferRenderedScreen.PHOTO -> Unit

                                    LocalOfferRenderedScreen.FILLING -> {
                                        state.geoSnapshot?.let { snapshot ->
                                            GeoSummaryCard(
                                                city = snapshot.city,
                                                freshness = snapshot.freshnessState,
                                                onRefreshGeo = { requestDeviceLocation() },
                                            )
                                        }
                                        AiNormalizationCard(
                                            draft = state.draftEnvelope?.draft,
                                            busy = state.submitting || state.publishing || state.loading,
                                        )
                                    }

                                    LocalOfferRenderedScreen.REVIEW -> {
                                        state.geoSnapshot?.let { snapshot ->
                                            GeoSummaryCard(
                                                city = snapshot.city,
                                                freshness = snapshot.freshnessState,
                                                onRefreshGeo = { requestDeviceLocation() },
                                            )
                                        }
                                        state.draftEnvelope?.draft?.let { draft ->
                                            val recommendedRole = recommendedLocalOfferPhotoRole(draft)
                                            DraftReviewCard(
                                                state = state,
                                                draft = draft,
                                                reviewInsights = state.reviewInsights,
                                                recommendedRole = recommendedRole,
                                                onOpenCamera = { openCamera(recommendedRole) },
                                                onOpenGallery = { openGallery(recommendedRole) },
                                                onFieldChange = viewModel::updateField,
                                                onCategoryChange = viewModel::updateCategory,
                                                onPriceChange = viewModel::updatePrice,
                                                onCurrencyChange = viewModel::updateCurrency,
                                                onTtlChange = viewModel::updateTtlDays,
                                                onDeliveryChange = viewModel::updateDelivery,
                                                onDescriptionChange = viewModel::updateDescription,
                                                onSaveReview = viewModel::saveReview,
                                                onOpenPreview = viewModel::openPreview,
                                            )
                                        }
                                    }

                                    LocalOfferRenderedScreen.PREVIEW -> {
                                        state.geoSnapshot?.let { snapshot ->
                                            GeoSummaryCard(
                                                city = snapshot.city,
                                                freshness = snapshot.freshnessState,
                                                onRefreshGeo = { requestDeviceLocation() },
                                            )
                                        }
                                        val preview = state.preview
                                        if (preview == null) {
                                            MessageCard(
                                                title = "Готовим карточку объявления",
                                                body = "Мы ещё собираем итоговый вид объявления. Попробуйте обновить через секунду.",
                                                tone = MessageTone.Neutral,
                                                actionLabel = "Обновить",
                                                onAction = viewModel::openPreview,
                                            )
                                        } else {
                                            PreviewCard(
                                                preview = preview,
                                                busy = state.submitting || state.publishing,
                                                onBackToReview = viewModel::backToReview,
                                                onRefreshPreflight = viewModel::refreshPreflight,
                                            )
                                        }
                                    }

                                    LocalOfferRenderedScreen.PREFLIGHT -> {
                                        state.geoSnapshot?.let { snapshot ->
                                            GeoSummaryCard(
                                                city = snapshot.city,
                                                freshness = snapshot.freshnessState,
                                                onRefreshGeo = { requestDeviceLocation() },
                                            )
                                        }
                                        state.preflight?.let { preflight ->
                                            PreflightCard(
                                                preflight = preflight,
                                                busy = state.submitting || state.publishing,
                                                onBackToPreview = viewModel::backToPreview,
                                                onPublish = viewModel::publish,
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
    }
}

private enum class LocalOfferRenderedScreen {
    LOADING,
    PHOTO,
    FILLING,
    REVIEW,
    PREVIEW,
    PREFLIGHT,
    BLOCKED,
    RESULT,
}

private data class LocalOfferScreenDescriptor(
    val badge: String,
    val title: String,
    val subtitle: String,
    val progress: Float,
)

private data class PendingLocalOfferPhotoSubmission(
    val photo: LocalOfferIncomingPhoto,
)

@Composable
private fun LocalOfferStageHeader(
    descriptor: LocalOfferScreenDescriptor,
    onDismiss: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = descriptor.badge,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = descriptor.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = descriptor.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Закрыть")
                }
            }
        }
    }
}

@Composable
private fun BlockedStateCard(
    blockedState: LocalOfferBlockedState?,
    onDismissBlockedState: () -> Unit,
    onPrimaryAction: () -> Unit,
    onStartNew: () -> Unit,
) {
    val state = blockedState ?: return
    val hasPrimaryAction = state.action != null && state.actionLabel != null
    MessageCard(
        title = state.title,
        body = buildString {
            append(state.body)
            if (state.issues.isNotEmpty()) {
                append("\n\n")
                append(state.issues.joinToString("\n") { issue -> "• ${issue.message}" })
            }
        },
        tone = MessageTone.Error,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (hasPrimaryAction) {
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier.weight(1f),
            ) {
                Text(state.actionLabel!!)
            }
        }
        if (!hasPrimaryAction) {
            OutlinedButton(
                onClick = onDismissBlockedState,
                modifier = Modifier.weight(1f),
            ) {
                Text("Понятно")
            }
        }
        OutlinedButton(
            onClick = onStartNew,
            modifier = Modifier.weight(1f),
        ) {
            Text("Начать заново")
        }
    }
}

@Composable
private fun CapturePreflightCard(
    report: LocalOfferCapturePreflightReport,
    onDismiss: () -> Unit,
) {
    val tone = if (report.hasBlockingIssues) MessageTone.Error else MessageTone.Warning
    MessageCard(
        title = "Проверьте снимок",
        body = buildString {
            append(report.issues.joinToString("\n") { issue -> "• ${issue.message}" })
            if (report.findings.isNotEmpty()) {
                append("\n\n")
                append(report.findings.joinToString("\n") { finding -> "• $finding" })
            }
        },
        tone = tone,
        actionLabel = "Скрыть",
        onAction = onDismiss,
    )
}

@Composable
private fun AiNormalizationCard(
    draft: LocalOfferDraftSession?,
    busy: Boolean,
) {
    MessageCard(
        title = "Заполняем объявление",
        body = buildString {
            append("Смотрим фото и пытаемся сами заполнить категорию, название и основные характеристики.")
            draft?.let { session ->
                val candidateCategory = session.candidateCategory
                if (candidateCategory != null) {
                    append("\nУже есть версия категории: ${candidateCategory.title ?: candidateCategory.code}")
                }
            }
            append("\nОбычно это занимает несколько секунд. Экран обновится сам.")
        },
        tone = MessageTone.Neutral,
        footer = if (busy) "Обновляем данные..." else null,
    )
}

@Composable
private fun GeoGateCard(
    busy: Boolean,
    onUseCurrentLocation: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Сначала определим местоположение",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Локальное объявление привязано к вашему текущему местоположению. Разрешите доступ к геолокации, и мы продолжим автоматически.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onUseCurrentLocation,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Определить местоположение")
            }
        }
    }
}

@Composable
private fun GeoSummaryCard(
    city: String,
    freshness: LocalOfferGeoFreshnessState,
    onRefreshGeo: () -> Unit,
) {
    MessageCard(
        title = "Местоположение подтверждено",
        body = buildString {
            append(city)
            append(
                when (freshness) {
                    LocalOfferGeoFreshnessState.FRESH -> "\nДанные актуальны."
                    LocalOfferGeoFreshnessState.STALE -> "\nДанные лучше обновить перед публикацией."
                    LocalOfferGeoFreshnessState.EXPIRED -> "\nМестоположение устарело, его нужно обновить."
                },
            )
        },
        tone = when (freshness) {
            LocalOfferGeoFreshnessState.FRESH -> MessageTone.Success
            LocalOfferGeoFreshnessState.STALE -> MessageTone.Warning
            LocalOfferGeoFreshnessState.EXPIRED -> MessageTone.Error
        },
        actionLabel = "Обновить местоположение",
        onAction = onRefreshGeo,
    )
}

@Composable
private fun EntryCard(
    origin: DraftInputOrigin?,
    geoCity: String?,
    geoPending: Boolean,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Сделайте первый снимок",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when {
                    origin == DraftInputOrigin.LINK -> "Снимите товар, и мы попробуем сами определить категорию и заполнить карточку."
                    else -> "Снимите товар, а мы попробуем заполнить объявление автоматически."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenGallery,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Галерея")
                }
                Button(
                    onClick = onOpenCamera,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Камера")
                }
            }
            Text(
                text = when {
                    geoCity != null -> "Объявление будет показано рядом с: $geoCity"
                    geoPending -> "Местоположение обновляем автоматически в фоне."
                    else -> "Местоположение определим автоматически."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PublishResultCard(
    state: LocalOfferUiState,
    onDismissResult: () -> Unit,
    onStartNew: () -> Unit,
    onClose: () -> Unit,
) {
    val result = state.publishResult ?: return
    val (title, body, tone) = when (result.outcome) {
        LocalOfferPublishOutcome.PUBLISHED -> Triple(
            "Публикация принята",
            buildString {
                append(
                    when (result.publicationState) {
                        LocalOfferPublicationState.PENDING_REVIEW -> "Объявление создано и ушло на модерацию."
                        LocalOfferPublicationState.LIVE -> "Объявление уже опубликовано."
                        LocalOfferPublicationState.REJECTED -> "Объявление отправлено, но его нужно будет проверить вручную."
                        LocalOfferPublicationState.REMOVED -> "Объявление создано, но сейчас недоступно для показа."
                        null -> "Публикация завершена."
                    },
                )
            },
            MessageTone.Success,
        )
        LocalOfferPublishOutcome.BLOCKED -> Triple(
            "Публикация заблокирована",
            result.issue?.message ?: "Публикация не прошла финальную проверку.",
            MessageTone.Error,
        )
    }
    MessageCard(
        title = title,
        body = body,
        tone = tone,
        actionLabel = if (result.outcome == LocalOfferPublishOutcome.PUBLISHED) "Закрыть" else "Назад к проверке",
        onAction = {
            onDismissResult()
            if (result.outcome == LocalOfferPublishOutcome.PUBLISHED) {
                onClose()
            }
        },
        footer = if (result.outcome == LocalOfferPublishOutcome.PUBLISHED) "Можно сразу создать ещё одно объявление." else null,
    )
    if (result.outcome == LocalOfferPublishOutcome.PUBLISHED) {
        OutlinedButton(onClick = onStartNew, modifier = Modifier.fillMaxWidth()) {
            Text("Создать ещё")
        }
    }
}

private enum class MessageTone {
    Neutral,
    Success,
    Warning,
    Error,
}

private data class CategoryOptionUi(
    val code: String,
    val title: String?,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftReviewCard(
    state: LocalOfferUiState,
    draft: LocalOfferDraftSession,
    reviewInsights: LocalOfferReviewInsightsUi?,
    recommendedRole: LocalOfferPhotoRole,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onFieldChange: (String, String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onPriceChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onTtlChange: (String) -> Unit,
    onDeliveryChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSaveReview: () -> Unit,
    onOpenPreview: () -> Unit,
) {
    val categoryOptions = buildCategoryOptions(draft, state)
    val orderedFields = orderedLocalOfferFieldCodes(state, draft)
    val busy = state.submitting || state.publishing

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Проверьте объявление",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Мы подставили, что смогли распознать по фото. При необходимости поправьте перед публикацией.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (draft.aiRefreshPending) {
                MessageCard(
                    title = "Добавляем детали по фото",
                    body = "Новые данные ещё подтягиваются. Поля и проверка перед публикацией обновятся автоматически.",
                    tone = MessageTone.Neutral,
                )
            }

            if (draft.primaryIssues().isNotEmpty()) {
                MessageCard(
                    title = "Что ещё нужно проверить",
                    body = draft.primaryIssues().joinToString("\n") { issue -> "• $issue" },
                    tone = MessageTone.Warning,
                )
            }

            reviewInsights?.let { insights ->
                ReviewInsightsCard(
                    title = "Что распознано по фото",
                    lines = insights.recognized.map { fact -> "${fact.label}: ${fact.value}" },
                    tone = MessageTone.Neutral,
                )
                ReviewInsightsCard(
                    title = "Что принято в профиль",
                    lines = insights.accepted.map { fact -> "${fact.label}: ${fact.value}" },
                    tone = MessageTone.Success,
                )
                ReviewInsightsCard(
                    title = "Что оставили неопределённым",
                    lines = insights.unresolved,
                    tone = MessageTone.Warning,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Категория",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    categoryOptions.forEach { option ->
                        FilterChip(
                            selected = option.code == state.selectedCategoryCode,
                            onClick = { onCategoryChange(option.code) },
                            label = { Text(option.title ?: option.code) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Фото",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Лучше добавить: ${recommendedRole.label()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onOpenGallery,
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                    ) {
                        Text("Добавить из галереи")
                    }
                    Button(
                        onClick = onOpenCamera,
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                    ) {
                        Text("Снять фото")
                    }
                }
                draft.evidenceTasks.forEach { task ->
                    MessageCard(
                        title = task.headline,
                        body = buildString {
                            append(task.explanation ?: task.reasonCode)
                            task.photoRole?.let { append("\nНужная роль: ${it.label()}") }
                            task.exampleHint?.let { append("\nПример: $it") }
                        },
                        tone = if (task.blockingLevel == LocalOfferEvidenceBlockingLevel.PUBLISH_BLOCKING) {
                            MessageTone.Error
                        } else {
                            MessageTone.Warning
                        },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Поля объявления",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                orderedFields.forEach { code ->
                    OutlinedTextField(
                        value = state.fieldInputs[code].orEmpty(),
                        onValueChange = { value -> onFieldChange(code, value) },
                        label = { Text(code.humanizeFieldCode()) },
                        singleLine = code !in multilineFieldCodes,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Цена и условия",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = state.priceInput,
                    onValueChange = onPriceChange,
                    label = { Text("Цена") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = state.currencyInput,
                        onValueChange = onCurrencyChange,
                        label = { Text("Валюта") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.ttlDaysInput,
                        onValueChange = onTtlChange,
                        label = { Text("Срок размещения, дней") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = state.deliveryInput,
                    onValueChange = onDeliveryChange,
                    label = { Text("Способ передачи") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.descriptionInput,
                    onValueChange = onDescriptionChange,
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onSaveReview,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                ) {
                    Text(if (state.submitting) "Сохраняем..." else "Сохранить")
                }
                Button(
                    onClick = onOpenPreview,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                ) {
                    Text("Посмотреть объявление")
                }
            }
        }
    }
}

@Composable
private fun ReviewInsightsCard(
    title: String,
    lines: List<String>,
    tone: MessageTone,
) {
    if (lines.isEmpty()) return
    MessageCard(
        title = title,
        body = lines.joinToString("\n") { line -> "• $line" },
        tone = tone,
    )
}

@Composable
private fun PreviewCard(
    preview: LocalOfferPreviewResponse,
    busy: Boolean,
    onBackToReview: () -> Unit,
    onRefreshPreflight: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Как будет выглядеть объявление",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = preview.hero.title, style = MaterialTheme.typography.titleSmall)
            preview.hero.priceLabel?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = buildString {
                    preview.hero.city?.let { append("Город: $it\n") }
                }.trim(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (preview.summary.blockingIssues.isNotEmpty()) {
                MessageCard(
                    title = "Что мешает продолжить",
                    body = preview.summary.blockingIssues.joinToString("\n") { issue -> "• ${issue.message}" },
                    tone = MessageTone.Error,
                )
            }
            if (preview.summary.nonBlockingNotes.isNotEmpty()) {
                MessageCard(
                    title = "Что стоит проверить",
                    body = preview.summary.nonBlockingNotes.joinToString("\n") { issue -> "• ${issue.message}" },
                    tone = MessageTone.Warning,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onBackToReview,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Назад")
                }
                Button(
                    onClick = onRefreshPreflight,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Проверить перед публикацией")
                }
            }
        }
    }
}

@Composable
private fun PreflightCard(
    preflight: LocalOfferPublishPreflightResponse,
    busy: Boolean,
    onBackToPreview: () -> Unit,
    onPublish: () -> Unit,
) {
    val tone = when {
        preflight.blockingIssues.isNotEmpty() -> MessageTone.Error
        preflight.warnings.isNotEmpty() -> MessageTone.Warning
        else -> MessageTone.Success
    }
    MessageCard(
        title = "Проверка перед публикацией",
        body = buildString {
            if (preflight.blockingIssues.isNotEmpty()) {
                append("Исправьте перед публикацией:")
                preflight.blockingIssues.forEach { issue ->
                    append("\n• ${issue.message}")
                }
            }
            if (preflight.warnings.isNotEmpty()) {
                if (preflight.blockingIssues.isNotEmpty()) {
                    append("\n\n")
                }
                append("Ещё стоит проверить:")
                preflight.warnings.forEach { issue ->
                    append("\n• ${issue.message}")
                }
            }
            if (preflight.blockingIssues.isEmpty() && preflight.warnings.isEmpty()) {
                append("Можно публиковать.")
            }
        },
        tone = tone,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onBackToPreview,
            enabled = !busy,
            modifier = Modifier.weight(1f),
        ) {
            Text("Назад")
        }
        Button(
            onClick = onPublish,
            enabled = !busy && preflight.publishAllowed,
            modifier = Modifier.weight(1f),
        ) {
            Text(if (busy) "Публикую..." else "Опубликовать")
        }
    }
}

@Composable
private fun MessageCard(
    title: String,
    body: String,
    tone: MessageTone,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    footer: String? = null,
) {
    SystemNoticeCard(
        title = title,
        body = body,
        tone = tone.asSystemNoticeTone(),
        actionLabel = actionLabel,
        onAction = onAction,
        footer = footer,
    )
}

private fun buildCategoryOptions(
    draft: LocalOfferDraftSession,
    state: LocalOfferUiState,
): List<CategoryOptionUi> {
    val options = LinkedHashMap<String, CategoryOptionUi>()
    state.selectedCategoryCode?.let { code ->
        options[code] = CategoryOptionUi(code = code, title = draft.candidateCategory?.title)
    }
    draft.confirmedCategoryCode?.let { code ->
        options.putIfAbsent(code, CategoryOptionUi(code = code, title = draft.candidateCategory?.title))
    }
    draft.candidateCategory?.let { candidate ->
        options[candidate.code] = CategoryOptionUi(code = candidate.code, title = candidate.title)
    }
    draft.resolvedCategoryCode?.let { code ->
        options.putIfAbsent(code, CategoryOptionUi(code = code, title = draft.candidateCategory?.title))
    }
    return options.values.toList()
}

private fun resolveLocalOfferRenderedScreen(state: LocalOfferUiState): LocalOfferRenderedScreen = when {
    state.loading -> LocalOfferRenderedScreen.LOADING
    state.blockedState != null -> LocalOfferRenderedScreen.BLOCKED
    state.publishResult != null || state.activeStep == LocalOfferFlowStep.PUBLISHED -> LocalOfferRenderedScreen.RESULT
    else -> when (state.activeStep) {
        LocalOfferFlowStep.GEO_CONSENT_GATE -> LocalOfferRenderedScreen.PHOTO
        LocalOfferFlowStep.PRIMARY_PHOTO_CAPTURE -> LocalOfferRenderedScreen.PHOTO
        LocalOfferFlowStep.AI_NORMALIZATION_RUN -> LocalOfferRenderedScreen.FILLING
        LocalOfferFlowStep.DRAFT_REVIEW -> LocalOfferRenderedScreen.REVIEW
        LocalOfferFlowStep.PREVIEW -> LocalOfferRenderedScreen.PREVIEW
        LocalOfferFlowStep.PUBLISH_PREFLIGHT -> LocalOfferRenderedScreen.PREFLIGHT
        LocalOfferFlowStep.BLOCKED_STATE -> LocalOfferRenderedScreen.BLOCKED
        LocalOfferFlowStep.PUBLISHED -> LocalOfferRenderedScreen.RESULT
    }
}

private fun describeLocalOfferScreen(state: LocalOfferUiState): LocalOfferScreenDescriptor {
    val stepIndex = when {
        state.loading -> 1
        state.blockedState != null && state.geoSnapshot == null -> 1
        state.blockedState != null && state.preflight != null -> 5
        state.blockedState != null && state.preview != null -> 4
        state.blockedState != null && state.draftEnvelope != null -> 3
        state.publishResult != null || state.activeStep == LocalOfferFlowStep.PUBLISHED -> 5
        else -> when (state.activeStep) {
            LocalOfferFlowStep.GEO_CONSENT_GATE,
            LocalOfferFlowStep.PRIMARY_PHOTO_CAPTURE,
                -> 1
            LocalOfferFlowStep.AI_NORMALIZATION_RUN -> 2
            LocalOfferFlowStep.DRAFT_REVIEW -> 3
            LocalOfferFlowStep.PREVIEW -> 4
            LocalOfferFlowStep.PUBLISH_PREFLIGHT,
            LocalOfferFlowStep.PUBLISHED,
            LocalOfferFlowStep.BLOCKED_STATE,
                -> 5
        }
    }
    val title = when {
        state.loading -> "Новое объявление"
        state.blockedState != null -> state.blockedState.title
        state.publishResult != null -> "Публикация"
        else -> when (state.activeStep) {
            LocalOfferFlowStep.GEO_CONSENT_GATE,
            LocalOfferFlowStep.PRIMARY_PHOTO_CAPTURE,
                -> "Добавьте фото товара"
            LocalOfferFlowStep.AI_NORMALIZATION_RUN -> "Заполняем объявление"
            LocalOfferFlowStep.DRAFT_REVIEW -> "Проверьте детали"
            LocalOfferFlowStep.PREVIEW -> "Как увидят объявление"
            LocalOfferFlowStep.PUBLISH_PREFLIGHT -> "Перед публикацией"
            LocalOfferFlowStep.BLOCKED_STATE -> state.blockedState?.title ?: "Нужна правка"
            LocalOfferFlowStep.PUBLISHED -> "Объявление готово"
        }
    }
    val subtitle = when {
        state.loading -> "Проверяем местоположение и восстанавливаем текущую сессию."
        state.blockedState != null -> state.blockedState.body
        state.publishResult != null -> "Финальный шаг размещения."
        else -> when (state.activeStep) {
            LocalOfferFlowStep.GEO_CONSENT_GATE,
            LocalOfferFlowStep.PRIMARY_PHOTO_CAPTURE,
                -> "Снимите товар камерой или выберите фото из галереи."
            LocalOfferFlowStep.AI_NORMALIZATION_RUN -> "Смотрим фото, определяем категорию и подтягиваем основные характеристики."
            LocalOfferFlowStep.DRAFT_REVIEW -> "Проверьте, что мы распознали по фото, и поправьте только при необходимости."
            LocalOfferFlowStep.PREVIEW -> "Почти готово. Посмотрите, как будет выглядеть карточка."
            LocalOfferFlowStep.PUBLISH_PREFLIGHT -> "Ещё одна короткая проверка перед публикацией."
            LocalOfferFlowStep.BLOCKED_STATE -> state.blockedState?.body ?: "Нужно исправить проблему, чтобы продолжить."
            LocalOfferFlowStep.PUBLISHED -> "Объявление создано. Дальше всё зависит от результата проверки."
        }
    }
    return LocalOfferScreenDescriptor(
        badge = "Шаг $stepIndex из 5",
        title = title,
        subtitle = subtitle,
        progress = (stepIndex.toFloat() / 5f).coerceIn(0f, 1f),
    )
}

private fun com.example.shoppingassistant.domain.ugc.draft.DraftMedia.toIncomingPhoto(
    role: LocalOfferPhotoRole,
): LocalOfferIncomingPhoto? {
    val local = localUri?.takeIf { it.isNotBlank() } ?: return null
    val bytes = runCatching { File(URI(local)).readBytes() }.getOrNull() ?: return null
    return LocalOfferIncomingPhoto(
        role = role,
        filename = "localoffer_${role.name.lowercase()}.jpg",
        contentType = "image/jpeg",
        dataBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
    )
}

private fun LocalOfferPhotoRole.label(): String = when (this) {
    LocalOfferPhotoRole.FRONT -> "основной ракурс"
    LocalOfferPhotoRole.BACK -> "задняя сторона"
    LocalOfferPhotoRole.LEFT -> "левый бок"
    LocalOfferPhotoRole.RIGHT -> "правый бок"
    LocalOfferPhotoRole.TOP -> "вид сверху"
    LocalOfferPhotoRole.BOTTOM -> "нижняя сторона"
    LocalOfferPhotoRole.TECH_1 -> "маркировка или штрихкод"
    LocalOfferPhotoRole.TECH_2 -> "дополнительная маркировка"
}

private fun localOfferSubtitle(origin: DraftInputOrigin?): String = when (origin) {
    DraftInputOrigin.LINK -> "Сделайте фото товара, а мы постараемся сами заполнить объявление."
    DraftInputOrigin.VOICE -> "Сделайте фото товара, а мы постараемся сами заполнить объявление."
    DraftInputOrigin.TEXT -> "Добавьте фото товара, а дальше объявление заполнится автоматически."
    else -> "Сделайте фото товара, а мы постараемся сами заполнить объявление."
}

private fun MessageTone.asSystemNoticeTone(): SystemNoticeTone = when (this) {
    MessageTone.Neutral -> SystemNoticeTone.Info
    MessageTone.Success -> SystemNoticeTone.Success
    MessageTone.Warning -> SystemNoticeTone.Warning
    MessageTone.Error -> SystemNoticeTone.Error
}

private fun String.humanizeFieldCode(): String =
    split('_', '-', '.')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
        }
        .ifBlank { this }

private val multilineFieldCodes = setOf("description")

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
