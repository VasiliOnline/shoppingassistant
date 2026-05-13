package com.example.shoppingassistant.feature.pages.main.visualsearch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCaptureMode
import com.example.shoppingassistant.feature.pages.main.state.VISUAL_SEARCH_MAX_CAPTURE_ASSETS
import com.example.shoppingassistant.feature.pages.main.state.VisualSearchSessionState
import com.example.shoppingassistant.feature.ui.state.SystemNoticeTone
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VisualSearchCameraScreen(
    state: VisualSearchSessionState,
    hasCameraPermission: Boolean,
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit,
    onPickGallery: () -> Unit,
    onCaptureModeChange: (VisualSearchCaptureMode) -> Unit,
    onCaptureBitmap: suspend (Bitmap, VisualSearchCaptureMode) -> Unit,
    onSubmitSearch: () -> Unit,
) {
    if (!state.visible) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturePending by remember { mutableStateOf(false) }
    var inlineNotice by remember(state.sessionId) { mutableStateOf<InlineCameraNotice?>(null) }
    var inlineNoticeEventId by remember(state.sessionId) { mutableStateOf(0L) }
    var multiPhotoHintShown by remember(state.sessionId) { mutableStateOf(false) }
    val capturedAssets = state.capturedAssets.takeLast(VISUAL_SEARCH_MAX_CAPTURE_ASSETS)
    val hasCapturedAssets = capturedAssets.isNotEmpty()

    fun showInlineNotice(
        message: String?,
        tone: SystemNoticeTone = SystemNoticeTone.Warning,
    ) {
        val normalized = message?.trim().orEmpty()
        if (normalized.isEmpty()) return
        inlineNotice = InlineCameraNotice(message = normalized, tone = tone)
        inlineNoticeEventId = System.nanoTime()
    }

    BackHandler(onBack = onDismiss)
    LaunchedEffect(inlineNoticeEventId) {
        if (inlineNoticeEventId == 0L) return@LaunchedEffect
        val eventId = inlineNoticeEventId
        delay(INLINE_NOTICE_DURATION_MS)
        if (eventId == inlineNoticeEventId) {
            inlineNotice = null
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage
            ?.takeIf { value -> value.isNotBlank() }
            ?.let { message ->
                showInlineNotice(message, SystemNoticeTone.Warning)
            }
    }
    LaunchedEffect(state.recoveryMessage) {
        state.recoveryMessage
            ?.takeIf { value -> value.isNotBlank() }
            ?.let { message ->
                showInlineNotice(message, SystemNoticeTone.Info)
            }
    }
    LaunchedEffect(capturedAssets.size, state.captureMode) {
        if (state.captureMode != VisualSearchCaptureMode.IMAGE) return@LaunchedEffect
        if (capturedAssets.size == 1 && !multiPhotoHintShown) {
            multiPhotoHintShown = true
            showInlineNotice(
                message = "Добавьте ещё 1-2 фото с других ракурсов. Так точнее определим товар.",
                tone = SystemNoticeTone.Info,
            )
        }
    }

    DisposableEffect(hasCameraPermission, lifecycleOwner) {
        if (!hasCameraPermission) {
            imageCapture = null
            onDispose { }
        } else {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            var boundProvider: ProcessCameraProvider? = null
            providerFuture.addListener(
                {
                    runCatching {
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also { useCase ->
                            useCase.surfaceProvider = previewView.surfaceProvider
                        }
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setJpegQuality(92)
                            .build()
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            capture,
                        )
                        boundProvider = provider
                        imageCapture = capture
                    }.onFailure { error ->
                        imageCapture = null
                        showInlineNotice(
                            message = error.message ?: "Камера сейчас недоступна.",
                            tone = SystemNoticeTone.Error,
                        )
                    }
                },
                mainExecutor,
            )
            onDispose {
                imageCapture = null
                if (providerFuture.isDone) {
                    runCatching { boundProvider ?: providerFuture.get() }
                        .onSuccess { provider -> provider.unbindAll() }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            hasCameraPermission -> {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize(),
                )
                CaptureGuideOverlay(mode = state.captureMode)
            }
            hasCapturedAssets -> {
                AsyncImage(
                    model = state.asset?.localUri ?: capturedAssets.lastOrNull()?.localUri,
                    contentDescription = "Последнее фото",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            else -> Unit
        }

        if (!hasCameraPermission) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
            ) {
                PermissionFallback(
                    onRequestPermission = onRequestPermission,
                    onPickGallery = onPickGallery,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.36f),
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Закрыть",
                                tint = Color.White,
                            )
                        }
                    }
                    CaptureModeIconStrip(
                        selected = state.captureMode,
                        onSelect = { mode ->
                            onCaptureModeChange(mode)
                            showInlineNotice(
                                message = captureGuideText(mode),
                                tone = SystemNoticeTone.Info,
                            )
                        },
                    )
                }

                if (inlineNotice != null) {
                    val backgroundColor = when (inlineNotice?.tone) {
                        SystemNoticeTone.Error -> Color(0xA14A1212)
                        SystemNoticeTone.Warning -> Color(0xA16A3F00)
                        SystemNoticeTone.Info -> Color.Black.copy(alpha = 0.34f)
                        else -> Color.Black.copy(alpha = 0.26f)
                    }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = backgroundColor,
                    ) {
                        Text(
                            text = inlineNotice?.message.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.isSubmitting || capturePending) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.Black.copy(alpha = 0.58f),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.4.dp,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Ищем товар по фото",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White,
                                )
                                Text(
                                    text = "Подбираем лучшие кандидаты и открываем выдачу.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.82f),
                                )
                            }
                        }
                    }
                }

                if (state.captureMode == VisualSearchCaptureMode.IMAGE || hasCapturedAssets) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(VISUAL_SEARCH_MAX_CAPTURE_ASSETS) { index ->
                            val item = capturedAssets.getOrNull(index)
                            CaptureAssetSlot(
                                assetUri = item?.localUri,
                                isSelected = state.asset?.fingerprint == item?.fingerprint,
                                placeholderIndex = index + 1,
                            )
                        }
                    }
                }

                if (hasCameraPermission) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        OutlinedButton(
                            onClick = onPickGallery,
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.size(width = 132.dp, height = 44.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.26f),
                                contentColor = Color.White,
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Галерея",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }

                        Box(
                            modifier = Modifier.size(110.dp, 82.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CaptureButton(
                                enabled = imageCapture != null && !capturePending && !state.isSubmitting,
                                onClick = {
                                    val capture = imageCapture ?: return@CaptureButton
                                    capturePending = true
                                    runCatching {
                                        capture.takePicture(
                                            mainExecutor,
                                            object : ImageCapture.OnImageCapturedCallback() {
                                                override fun onCaptureSuccess(image: ImageProxy) {
                                                    val decodeResult = runCatching {
                                                        image.decodeCapturedBitmap()
                                                    }
                                                    image.close()
                                                    val bitmap = decodeResult.getOrNull()
                                                    if (bitmap == null) {
                                                        capturePending = false
                                                        showInlineNotice(
                                                            message = decodeResult.exceptionOrNull()?.message
                                                                ?: "Не удалось обработать кадр.",
                                                            tone = SystemNoticeTone.Warning,
                                                        )
                                                        return
                                                    }
                                                    scope.launch {
                                                        runCatching {
                                                            onCaptureBitmap(bitmap, state.captureMode)
                                                        }.onFailure { error ->
                                                            showInlineNotice(
                                                                message = error.message ?: "Не удалось обработать кадр.",
                                                                tone = SystemNoticeTone.Warning,
                                                            )
                                                        }
                                                        capturePending = false
                                                    }
                                                }

                                                override fun onError(exception: ImageCaptureException) {
                                                    capturePending = false
                                                    showInlineNotice(
                                                        message = exception.message ?: "Не удалось сделать снимок.",
                                                        tone = SystemNoticeTone.Warning,
                                                    )
                                                }
                                            },
                                        )
                                    }.onFailure { error ->
                                        capturePending = false
                                        showInlineNotice(
                                            message = error.message ?: "Не удалось сделать снимок.",
                                            tone = SystemNoticeTone.Warning,
                                        )
                                    }
                                },
                            )
                        }

                        if (hasCapturedAssets && !state.isSubmitting && !capturePending) {
                            Button(
                                onClick = onSubmitSearch,
                                modifier = Modifier.size(width = 132.dp, height = 44.dp),
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Black.copy(alpha = 0.62f),
                                    contentColor = Color.White,
                                    disabledContainerColor = Color.Black.copy(alpha = 0.36f),
                                    disabledContentColor = Color.White.copy(alpha = 0.65f),
                                ),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                            ) {
                                Text(
                                    text = "Найти",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.size(width = 132.dp, height = 44.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureAssetSlot(
    assetUri: String?,
    isSelected: Boolean,
    placeholderIndex: Int,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = if (assetUri != null) 0.36f else 0.20f),
        modifier = Modifier
            .size(width = 78.dp, height = 78.dp)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) {
                    Color.White.copy(alpha = 0.96f)
                } else {
                    Color.White.copy(alpha = 0.22f)
                },
                shape = RoundedCornerShape(14.dp),
            ),
    ) {
        if (assetUri != null) {
            AsyncImage(
                model = assetUri,
                contentDescription = "Фото товара",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.70f),
                )
                Text(
                    text = "$placeholderIndex/$VISUAL_SEARCH_MAX_CAPTURE_ASSETS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.54f),
                )
            }
        }
    }
}

@Composable
private fun CaptureModeIconStrip(
    selected: VisualSearchCaptureMode,
    onSelect: (VisualSearchCaptureMode) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.32f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                Pair(VisualSearchCaptureMode.IMAGE, Icons.Outlined.PhotoCamera),
                Pair(VisualSearchCaptureMode.BARCODE, Icons.Outlined.QrCodeScanner),
                Pair(VisualSearchCaptureMode.OCR, Icons.Outlined.DocumentScanner),
            ).forEach { (mode, icon) ->
                val selectedMode = mode == selected
                Surface(
                    shape = CircleShape,
                    color = if (selectedMode) Color.White.copy(alpha = 0.24f) else Color.Transparent,
                ) {
                    IconButton(
                        onClick = { onSelect(mode) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.34f),
        border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.55f)),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(88.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        color = Color.White.copy(alpha = if (enabled) 0.16f else 0.08f),
                        shape = CircleShape,
                    )
                    .border(
                        width = 2.dp,
                        color = Color.White.copy(alpha = if (enabled) 0.92f else 0.45f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            color = if (enabled) Color.White else Color.White.copy(alpha = 0.52f),
                            shape = CircleShape,
                        ),
                )
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(
                            color = Color.Black.copy(alpha = if (enabled) 0.16f else 0.36f),
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun PermissionFallback(
    onRequestPermission: () -> Unit,
    onPickGallery: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.52f),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Для полноэкранной камеры нужен доступ.",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Text(
                    text = "Можно разрешить камеру или сразу выбрать фото из галереи.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onRequestPermission) {
                        Text("Разрешить")
                    }
                    OutlinedButton(onClick = onPickGallery) {
                        Text("Галерея")
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureGuideOverlay(
    mode: VisualSearchCaptureMode,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.78f)
                .height(if (mode == VisualSearchCaptureMode.BARCODE) 148.dp else 320.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.5.dp,
                brush = SolidColor(Color.White.copy(alpha = 0.9f)),
            ),
        ) {}
    }
}

private fun captureGuideText(mode: VisualSearchCaptureMode): String = when (mode) {
    VisualSearchCaptureMode.IMAGE -> "Наведите камеру на товар и сделайте снимок."
    VisualSearchCaptureMode.BARCODE -> "Держите штрихкод внутри рамки, чтобы открыть точную выдачу."
    VisualSearchCaptureMode.OCR -> "Заполните кадр названием, брендом или моделью."
}

@OptIn(ExperimentalGetImage::class)
private fun ImageProxy.decodeCapturedBitmap(): Bitmap? {
    val rawBitmap = when (format) {
        ImageFormat.JPEG -> decodeJpegFromPlane(this)
        ImageFormat.YUV_420_888 -> decodeYuv420888Bitmap(this)
        else -> decodeJpegFromPlane(this) ?: decodeYuv420888Bitmap(this)
    } ?: return null
    return rawBitmap.rotateIfNeeded(imageInfo.rotationDegrees)
}

private fun decodeJpegFromPlane(image: ImageProxy): Bitmap? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer.duplicate()
    if (!buffer.hasRemaining()) return null
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return decodeJpegSampled(bytes, MAX_CAPTURE_SIDE_PX)
}

@OptIn(ExperimentalGetImage::class)
private fun decodeYuv420888Bitmap(image: ImageProxy): Bitmap? {
    val mediaImage = image.image ?: return null
    val nv21 = yuv420888ToNv21(image)
    if (nv21.isEmpty()) return null
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, mediaImage.width, mediaImage.height, null)
    val stream = ByteArrayOutputStream()
    val wrote = yuvImage.compressToJpeg(
        Rect(0, 0, mediaImage.width, mediaImage.height),
        CAPTURE_JPEG_QUALITY,
        stream,
    )
    if (!wrote) return null
    return decodeJpegSampled(stream.toByteArray(), MAX_CAPTURE_SIDE_PX)
}

private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
    if (image.planes.size < 3) return ByteArray(0)
    val width = image.width
    val height = image.height
    if (width <= 0 || height <= 0) return ByteArray(0)
    val nv21 = ByteArray(width * height * 3 / 2)
    copyLumaPlane(image.planes[0], width, height, nv21)
    copyChromaPlanes(image.planes[1], image.planes[2], width, height, nv21)
    return nv21
}

private fun copyLumaPlane(
    plane: ImageProxy.PlaneProxy,
    width: Int,
    height: Int,
    output: ByteArray,
) {
    val buffer = plane.buffer.duplicate()
    var outputOffset = 0
    for (row in 0 until height) {
        val rowStart = row * plane.rowStride
        if (plane.pixelStride == 1) {
            buffer.position(rowStart)
            buffer.get(output, outputOffset, width)
            outputOffset += width
        } else {
            for (column in 0 until width) {
                output[outputOffset++] = buffer.get(rowStart + column * plane.pixelStride)
            }
        }
    }
}

private fun copyChromaPlanes(
    uPlane: ImageProxy.PlaneProxy,
    vPlane: ImageProxy.PlaneProxy,
    width: Int,
    height: Int,
    output: ByteArray,
) {
    val chromaWidth = width / 2
    val chromaHeight = height / 2
    val uBuffer = uPlane.buffer.duplicate()
    val vBuffer = vPlane.buffer.duplicate()
    var outputOffset = width * height
    for (row in 0 until chromaHeight) {
        val uRowStart = row * uPlane.rowStride
        val vRowStart = row * vPlane.rowStride
        for (column in 0 until chromaWidth) {
            val uIndex = uRowStart + column * uPlane.pixelStride
            val vIndex = vRowStart + column * vPlane.pixelStride
            output[outputOffset++] = vBuffer.get(vIndex)
            output[outputOffset++] = uBuffer.get(uIndex)
        }
    }
}

private fun decodeJpegSampled(
    jpegBytes: ByteArray,
    maxSidePx: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    val longestSide = maxOf(bounds.outWidth, bounds.outHeight)
    while (longestSide / sampleSize > maxSidePx) {
        sampleSize *= 2
    }
    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, decodeOptions)
}

private fun Bitmap.rotateIfNeeded(rotationDegrees: Int): Bitmap {
    if (rotationDegrees == 0) return this
    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private data class InlineCameraNotice(
    val message: String,
    val tone: SystemNoticeTone,
)

private const val INLINE_NOTICE_DURATION_MS = 4_000L
private const val MAX_CAPTURE_SIDE_PX = 2048
private const val CAPTURE_JPEG_QUALITY = 92
