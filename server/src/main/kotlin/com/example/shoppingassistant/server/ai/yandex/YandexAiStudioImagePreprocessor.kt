package com.example.shoppingassistant.server.ai.yandex

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.plugins.jpeg.JPEGImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal object YandexAiStudioImagePreprocessor {
    private const val TARGET_MAX_SIDE_PX = 768
    private const val JPEG_QUALITY = 0.74f
    private const val MIN_TARGET_MAX_SIDE_PX = 384
    private const val MAX_TARGET_MAX_SIDE_PX = 1280
    private const val MIN_JPEG_QUALITY = 0.45f
    private const val MAX_JPEG_QUALITY = 0.92f
    private const val FOCUS_REGION_PADDING = 0.08f
    private const val SMALL_JPEG_MAX_BYTES = 180_000

    fun preprocess(
        part: YandexAiStudioContentPart.ImageBase64,
    ): YandexAiStudioContentPart.ImageBase64 {
        val targetMaxSidePx = part.preprocessMaxSidePx
            ?.coerceIn(MIN_TARGET_MAX_SIDE_PX, MAX_TARGET_MAX_SIDE_PX)
            ?: TARGET_MAX_SIDE_PX
        val jpegQuality = part.preprocessJpegQuality
            ?.coerceIn(MIN_JPEG_QUALITY, MAX_JPEG_QUALITY)
            ?: JPEG_QUALITY
        val originalBytes = runCatching { Base64.getDecoder().decode(part.base64) }.getOrNull() ?: return part
        val sourceImage = runCatching {
            ImageIO.read(ByteArrayInputStream(originalBytes))
        }.getOrNull() ?: return part

        val cropBounds = resolveCropBounds(
            width = sourceImage.width,
            height = sourceImage.height,
            focusRegion = part.focusRegion,
        )
        val croppedImage = cropBounds?.let { bounds ->
            sourceImage.getSubimage(bounds.left, bounds.top, bounds.width, bounds.height)
        } ?: sourceImage
        val rgbImage = toRgbImage(croppedImage)
        val resizedImage = resizeIfNeeded(rgbImage, targetMaxSidePx)

        val longestSide = max(sourceImage.width, sourceImage.height)
        val noCropApplied = cropBounds == null
        val alreadySmallJpeg = noCropApplied &&
            part.mimeType.equals("image/jpeg", ignoreCase = true) &&
            longestSide <= targetMaxSidePx &&
            originalBytes.size <= SMALL_JPEG_MAX_BYTES
        if (alreadySmallJpeg) {
            return part.copy(focusRegion = null)
        }

        val encodedBytes = encodeJpeg(resizedImage, jpegQuality) ?: return part
        return YandexAiStudioContentPart.ImageBase64(
            mimeType = "image/jpeg",
            base64 = Base64.getEncoder().encodeToString(encodedBytes),
            imageDetail = part.imageDetail,
            preprocessMaxSidePx = part.preprocessMaxSidePx,
            preprocessJpegQuality = part.preprocessJpegQuality,
        )
    }

    private fun resolveCropBounds(
        width: Int,
        height: Int,
        focusRegion: YandexAiStudioFocusRegion?,
    ): CropBounds? {
        val region = focusRegion ?: return null
        val paddedLeft = (region.left - FOCUS_REGION_PADDING).coerceIn(0f, 1f)
        val paddedTop = (region.top - FOCUS_REGION_PADDING).coerceIn(0f, 1f)
        val paddedRight = (region.left + region.width + FOCUS_REGION_PADDING).coerceIn(0f, 1f)
        val paddedBottom = (region.top + region.height + FOCUS_REGION_PADDING).coerceIn(0f, 1f)

        val leftPx = (paddedLeft * width).roundToInt().coerceIn(0, width - 1)
        val topPx = (paddedTop * height).roundToInt().coerceIn(0, height - 1)
        val rightPx = (paddedRight * width).roundToInt().coerceIn(leftPx + 1, width)
        val bottomPx = (paddedBottom * height).roundToInt().coerceIn(topPx + 1, height)
        val cropWidth = (rightPx - leftPx).coerceAtLeast(1)
        val cropHeight = (bottomPx - topPx).coerceAtLeast(1)
        if (cropWidth >= width && cropHeight >= height) return null
        if (cropWidth * cropHeight >= (width * height * 0.97f)) return null
        return CropBounds(
            left = leftPx,
            top = topPx,
            width = cropWidth,
            height = cropHeight,
        )
    }

    private fun toRgbImage(source: BufferedImage): BufferedImage {
        if (source.type == BufferedImage.TYPE_INT_RGB) return source
        val converted = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        val graphics = converted.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, converted.width, converted.height)
            graphics.drawImage(source, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        return converted
    }

    private fun resizeIfNeeded(source: BufferedImage, targetMaxSidePx: Int): BufferedImage {
        val longestSide = max(source.width, source.height)
        if (longestSide <= targetMaxSidePx) return source
        val scale = targetMaxSidePx.toDouble() / longestSide.toDouble()
        val targetWidth = max(1, (source.width * scale).roundToInt())
        val targetHeight = max(1, (source.height * scale).roundToInt())
        val resized = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = resized.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null)
        } finally {
            graphics.dispose()
        }
        return resized
    }

    private fun encodeJpeg(image: BufferedImage, jpegQuality: Float): ByteArray? {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").asSequence().firstOrNull() ?: return null
        val output = ByteArrayOutputStream()
        val imageOutput = MemoryCacheImageOutputStream(output)
        return try {
            writer.output = imageOutput
            val params = JPEGImageWriteParam(null).apply {
                compressionMode = JPEGImageWriteParam.MODE_EXPLICIT
                compressionQuality = jpegQuality
            }
            writer.write(null, IIOImage(image, null, null), params)
            imageOutput.flush()
            output.toByteArray()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { imageOutput.close() }
            runCatching { output.close() }
            writer.dispose()
        }
    }

    private data class CropBounds(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
    )
}
