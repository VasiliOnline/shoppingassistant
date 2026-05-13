package com.example.shoppingassistant.feature.pages.localoffer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.shoppingassistant.feature.ui.state.SystemNoticeCard
import com.example.shoppingassistant.feature.ui.state.SystemNoticeTone
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.launch

@Composable
fun LocalOfferPhotoCaptureScreen(
    hasCameraPermission: Boolean,
    permissionPermanentlyDenied: Boolean,
    busy: Boolean,
    geoPending: Boolean,
    geoCity: String?,
    errorMessage: String?,
    preflightReport: LocalOfferCapturePreflightReport?,
    onDismissIssue: () -> Unit,
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickGallery: () -> Unit,
    onCaptureBitmap: suspend (Bitmap) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
    var cameraError by remember { mutableStateOf<String?>(null) }
    var capturePending by remember { mutableStateOf(false) }

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
                        cameraError = null
                    }.onFailure { error ->
                        imageCapture = null
                        cameraError = error.message ?: "Камера сейчас недоступна."
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
        if (hasCameraPermission) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )
            LocalOfferCaptureFrameOverlay()
        } else {
            LocalOfferCapturePermissionFallback(
                permissionPermanentlyDenied = permissionPermanentlyDenied,
                onRequestPermission = onRequestPermission,
                onOpenSettings = onOpenSettings,
                onPickGallery = onPickGallery,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.Black.copy(alpha = 0.36f),
                    ) {
                        Text(
                            text = "Шаг 1 из 5",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.42f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Сфотографируйте товар",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Покажите товар целиком. Фото из галереи тоже можно использовать.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.82f),
                        )
                        Text(
                            text = when {
                                geoCity != null -> "Покажем объявление рядом с: $geoCity"
                                geoPending -> "Обновляем местоположение в фоне, чтобы объявление сразу привязалось к вашему району."
                                else -> "Местоположение определим автоматически."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                preflightReport?.let { report ->
                    LocalOfferCaptureIssueCard(
                        report = report,
                        onDismiss = onDismissIssue,
                    )
                }

                errorMessage?.let { message ->
                    LocalOfferCaptureMessageCard(
                        title = "Нужно переснять или попробовать снова",
                        body = message,
                    )
                }

                if (cameraError != null) {
                    LocalOfferCaptureMessageCard(
                        title = "Камера недоступна",
                        body = cameraError.orEmpty(),
                    )
                }

                if (busy || capturePending) {
                    LocalOfferCaptureBusyCard(
                        text = if (geoPending) {
                            "Обновляем местоположение и создаём объявление."
                        } else {
                            "Обрабатываем фото и заполняем объявление."
                        },
                    )
                } else {
                    LocalOfferCaptureHintCard(
                        text = if (geoPending) {
                            "Можно снимать сразу. Если местоположение ещё не успеет обновиться, мы продолжим автоматически."
                        } else {
                            "Снимите товар без сильного зума и в хорошем свете."
                        },
                    )
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
                            modifier = Modifier.width(124.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White,
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.65f)),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = null,
                                tint = Color.White,
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Галерея")
                        }

                        LocalOfferCaptureButton(
                            enabled = imageCapture != null && !capturePending && !busy,
                            onClick = {
                                val capture = imageCapture ?: return@LocalOfferCaptureButton
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
                                                    cameraError = decodeResult.exceptionOrNull()?.message
                                                        ?: "Не удалось обработать кадр."
                                                    return
                                                }
                                                scope.launch {
                                                    runCatching {
                                                        onCaptureBitmap(bitmap)
                                                    }.onFailure { error ->
                                                        cameraError = error.message ?: "Не удалось обработать фото."
                                                    }
                                                    capturePending = false
                                                }
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                capturePending = false
                                                cameraError = exception.message ?: "Не удалось сделать снимок."
                                            }
                                        },
                                    )
                                }.onFailure { error ->
                                    capturePending = false
                                    cameraError = error.message ?: "Не удалось сделать снимок."
                                }
                            },
                        )

                        Spacer(modifier = Modifier.width(124.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalOfferCaptureBusyCard(text: String) {
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
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun LocalOfferCaptureHintCard(text: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.36f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun LocalOfferCaptureIssueCard(
    report: LocalOfferCapturePreflightReport,
    onDismiss: () -> Unit,
) {
    SystemNoticeCard(
        title = "Проверьте снимок",
        body = buildString {
            append(report.issues.joinToString("\n") { issue -> "• ${issue.message}" })
            if (report.findings.isNotEmpty()) {
                append("\n\n")
                append(report.findings.joinToString("\n") { finding -> "• $finding" })
            }
        },
        tone = if (report.hasBlockingIssues) SystemNoticeTone.Error else SystemNoticeTone.Warning,
        compact = true,
        actionLabel = "Скрыть",
        onAction = onDismiss,
    )
}

@Composable
private fun LocalOfferCaptureMessageCard(
    title: String,
    body: String,
    tone: SystemNoticeTone = SystemNoticeTone.Error,
) {
    SystemNoticeCard(
        title = title,
        body = body,
        tone = tone,
        compact = true,
    )
}

@Composable
private fun LocalOfferCapturePermissionFallback(
    permissionPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickGallery: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.62f),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Разрешите камеру, чтобы снять товар сразу.",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Text(
                    text = "Если не хотите открывать камеру, можно выбрать фото из галереи.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = if (permissionPermanentlyDenied) onOpenSettings else onRequestPermission,
                    ) {
                        Text(if (permissionPermanentlyDenied) "Настройки" else "Разрешить")
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
private fun LocalOfferCaptureFrameOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.8f)
                .height(360.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent,
            border = BorderStroke(
                width = 1.5.dp,
                brush = SolidColor(Color.White.copy(alpha = 0.9f)),
            ),
        ) {}
    }
}

@Composable
private fun LocalOfferCaptureButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = Color.White,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(82.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(
                        color = if (enabled) Color.White else Color.White.copy(alpha = 0.45f),
                        shape = CircleShape,
                    ),
            )
        }
    }
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

private const val MAX_CAPTURE_SIDE_PX = 2048
private const val CAPTURE_JPEG_QUALITY = 92
