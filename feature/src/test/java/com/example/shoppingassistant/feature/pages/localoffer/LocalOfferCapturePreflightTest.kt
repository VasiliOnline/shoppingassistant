package com.example.shoppingassistant.feature.pages.localoffer

import com.example.shoppingassistant.domain.localoffer.LocalOfferPhotoRole
import com.example.shoppingassistant.feature.pages.common.CommercePhotoCaptureInsights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalOfferCapturePreflightTest {

    @Test
    fun evaluate_blocks_small_tech_photo() {
        val report = LocalOfferCapturePreflightEvaluator.evaluate(
            signals = LocalOfferCaptureSignals(
                width = 640,
                height = 480,
                byteSize = 96_000L,
                detailScore = 12.0,
            ),
            role = LocalOfferPhotoRole.TECH_1,
        )

        assertTrue(report.hasBlockingIssues)
        assertTrue(report.issues.any { issue -> issue.code == "image_too_small" })
    }

    @Test
    fun evaluate_warns_on_soft_blur_for_primary_photo() {
        val report = LocalOfferCapturePreflightEvaluator.evaluate(
            signals = LocalOfferCaptureSignals(
                width = 1440,
                height = 1080,
                byteSize = 180_000L,
                detailScore = 6.2,
            ),
            role = LocalOfferPhotoRole.FRONT,
        )

        assertEquals(false, report.hasBlockingIssues)
        assertTrue(report.issues.any { issue -> issue.code == "image_blurred" })
    }

    @Test
    fun evaluate_warns_when_tech_photo_has_no_label_or_barcode() {
        val report = LocalOfferCapturePreflightEvaluator.evaluate(
            signals = LocalOfferCaptureSignals(
                width = 1600,
                height = 1200,
                byteSize = 220_000L,
                detailScore = 12.5,
            ),
            role = LocalOfferPhotoRole.TECH_1,
            insights = CommercePhotoCaptureInsights(
                barcodeValue = null,
                recognizedText = null,
                textHints = emptyList(),
            ),
        )

        assertEquals(false, report.hasBlockingIssues)
        assertTrue(report.issues.any { issue -> issue.code == "tech_label_not_detected" })
    }

    @Test
    fun evaluate_gallery_screenshot_of_clear_object_is_not_blocked_as_too_small() {
        val report = LocalOfferCapturePreflightEvaluator.evaluate(
            signals = LocalOfferCaptureSignals(
                width = 616,
                height = 1125,
                byteSize = 140_000L,
                detailScore = 12.0,
            ),
            role = LocalOfferPhotoRole.FRONT,
            insights = CommercePhotoCaptureInsights(
                objectLabel = "computer mouse",
                objectConfidence = 0.94f,
                subjectCoverage = 0.24f,
                subjectCenteredness = 0.88f,
            ),
        )

        assertEquals(false, report.hasBlockingIssues)
        assertTrue(report.issues.none { issue -> issue.code == "image_too_small" })
        assertTrue(report.issues.any { issue -> issue.code == "image_low_resolution" })
    }

    @Test
    fun evaluate_blocks_when_subject_is_too_far_from_camera() {
        val report = LocalOfferCapturePreflightEvaluator.evaluate(
            signals = LocalOfferCaptureSignals(
                width = 701,
                height = 1452,
                byteSize = 210_000L,
                detailScore = 11.5,
            ),
            role = LocalOfferPhotoRole.FRONT,
            insights = CommercePhotoCaptureInsights(
                objectLabel = "television",
                objectConfidence = 0.78f,
                subjectCoverage = 0.05f,
                subjectCenteredness = 0.32f,
            ),
        )

        assertTrue(report.hasBlockingIssues)
        assertTrue(report.issues.any { issue -> issue.code == "object_too_far" })
        assertTrue(report.issues.none { issue -> issue.code == "image_too_small" })
    }
}
