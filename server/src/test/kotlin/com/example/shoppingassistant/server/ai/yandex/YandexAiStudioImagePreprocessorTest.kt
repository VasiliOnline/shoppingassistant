package com.example.shoppingassistant.server.ai.yandex

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

class YandexAiStudioImagePreprocessorTest {

    @Test
    fun preprocess_resizes_large_photo_and_converts_it_to_jpeg() {
        val sourceImage = BufferedImage(591, 1280, BufferedImage.TYPE_INT_RGB).apply {
            val graphics = createGraphics()
            try {
                graphics.color = Color(235, 180, 90)
                graphics.fillRect(0, 0, width, height)
            } finally {
                graphics.dispose()
            }
        }
        val encodedPng = encode(sourceImage, "png")

        val result = YandexAiStudioImagePreprocessor.preprocess(
            YandexAiStudioContentPart.ImageBase64(
                mimeType = "image/png",
                base64 = Base64.getEncoder().encodeToString(encodedPng),
            ),
        )

        val decoded = decode(result)
        assertEquals("image/jpeg", result.mimeType)
        assertEquals(355, decoded.width)
        assertEquals(768, decoded.height)
    }

    @Test
    fun preprocess_applies_focus_region_crop_before_resize() {
        val sourceImage = BufferedImage(1000, 500, BufferedImage.TYPE_INT_RGB).apply {
            val graphics = createGraphics()
            try {
                graphics.color = Color.RED
                graphics.fillRect(0, 0, 500, 500)
                graphics.color = Color.BLUE
                graphics.fillRect(500, 0, 500, 500)
            } finally {
                graphics.dispose()
            }
        }
        val encodedPng = encode(sourceImage, "png")

        val result = YandexAiStudioImagePreprocessor.preprocess(
            YandexAiStudioContentPart.ImageBase64(
                mimeType = "image/png",
                base64 = Base64.getEncoder().encodeToString(encodedPng),
                focusRegion = YandexAiStudioFocusRegion(
                    left = 0f,
                    top = 0f,
                    width = 0.25f,
                    height = 1f,
                ),
            ),
        )

        val decoded = decode(result)
        val center = Color(decoded.getRGB(decoded.width / 2, decoded.height / 2))
        assertEquals("image/jpeg", result.mimeType)
        assertTrue(center.red > center.blue * 2, "Expected cropped image to stay focused on red region.")
        assertTrue(decoded.width < sourceImage.width)
    }

    @Test
    fun preprocess_honors_custom_max_side_for_fast_visual_search_path() {
        val sourceImage = BufferedImage(960, 1280, BufferedImage.TYPE_INT_RGB).apply {
            val graphics = createGraphics()
            try {
                graphics.color = Color(120, 160, 210)
                graphics.fillRect(0, 0, width, height)
            } finally {
                graphics.dispose()
            }
        }
        val encodedPng = encode(sourceImage, "png")

        val result = YandexAiStudioImagePreprocessor.preprocess(
            YandexAiStudioContentPart.ImageBase64(
                mimeType = "image/png",
                base64 = Base64.getEncoder().encodeToString(encodedPng),
                imageDetail = "low",
                preprocessMaxSidePx = 512,
                preprocessJpegQuality = 0.62f,
            ),
        )

        val decoded = decode(result)
        assertEquals("image/jpeg", result.mimeType)
        assertEquals("low", result.imageDetail)
        assertEquals(384, decoded.width)
        assertEquals(512, decoded.height)
    }

    private fun encode(image: BufferedImage, format: String): ByteArray =
        ByteArrayOutputStream().use { output ->
            val encoded = ImageIO.write(image, format, output)
            assertTrue(encoded)
            output.toByteArray()
        }

    private fun decode(part: YandexAiStudioContentPart.ImageBase64): BufferedImage {
        val decodedBytes = Base64.getDecoder().decode(part.base64)
        val image = ImageIO.read(ByteArrayInputStream(decodedBytes))
        return assertNotNull(image)
    }
}
