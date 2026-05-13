package com.example.shoppingassistant.feature.pages.main.visualsearch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualSearchPreflightCategoryRouterTest {

    @Test
    fun route_prioritizes_selected_category() {
        val candidates = VisualSearchPreflightCategoryRouter.route(
            selectedCategoryCode = "TECH.LAPTOPS",
            barcodeValue = null,
            ocrTextHints = emptyList(),
            imageLabelHints = listOf("smartphone"),
            objectLabel = null,
            objectConfidence = null,
        )

        assertEquals("TECH.LAPTOPS", candidates.first().categoryCode)
        assertTrue(candidates.first().confidence!! >= 0.95f)
    }

    @Test
    fun route_maps_charger_signals_to_phone_accessories() {
        val candidates = VisualSearchPreflightCategoryRouter.route(
            selectedCategoryCode = null,
            barcodeValue = null,
            ocrTextHints = listOf("OPPO SUPERVOOC 65W USB-C adapter"),
            imageLabelHints = listOf("charger"),
            objectLabel = "power adapter",
            objectConfidence = 0.76f,
        )

        assertEquals("TECH.PHONE_ACCESSORIES", candidates.first().categoryCode)
    }

    @Test
    fun route_maps_laptop_signals_to_laptops() {
        val candidates = VisualSearchPreflightCategoryRouter.route(
            selectedCategoryCode = null,
            barcodeValue = null,
            ocrTextHints = listOf("Intel Core Ultra Intel Arc Graphics"),
            imageLabelHints = listOf("laptop computer"),
            objectLabel = "laptop",
            objectConfidence = 0.71f,
        )

        assertEquals("TECH.LAPTOPS", candidates.first().categoryCode)
    }

    @Test
    fun route_maps_microwave_signals_to_small_appliances() {
        val candidates = VisualSearchPreflightCategoryRouter.route(
            selectedCategoryCode = null,
            barcodeValue = null,
            ocrTextHints = listOf("bbk microwave"),
            imageLabelHints = listOf("kitchen appliance"),
            objectLabel = "microwave",
            objectConfidence = 0.81f,
        )

        assertEquals("APPL.SMALL", candidates.first().categoryCode)
    }

    @Test
    fun route_maps_computer_mouse_to_pc_components_not_kitchen_when_table_background_is_present() {
        val candidates = VisualSearchPreflightCategoryRouter.route(
            selectedCategoryCode = null,
            barcodeValue = null,
            ocrTextHints = emptyList(),
            imageLabelHints = listOf("tableware", "cutlery", "wooden table", "computer mouse"),
            objectLabel = "wireless mouse",
            objectConfidence = 0.91f,
        )

        assertEquals("TECH.PC_COMPONENTS", candidates.first().categoryCode)
        assertTrue(candidates.none { it.categoryCode == "HOME.KITCHEN_DINING" })
    }

    @Test
    fun route_still_maps_real_cutlery_to_kitchen_dining() {
        val candidates = VisualSearchPreflightCategoryRouter.route(
            selectedCategoryCode = null,
            barcodeValue = null,
            ocrTextHints = emptyList(),
            imageLabelHints = listOf("tableware", "cutlery"),
            objectLabel = "fork",
            objectConfidence = 0.82f,
        )

        assertEquals("HOME.KITCHEN_DINING", candidates.first().categoryCode)
    }

    @Test
    fun route_does_not_promote_broad_cutlery_image_label_without_specific_subject() {
        val candidates = VisualSearchPreflightCategoryRouter.route(
            selectedCategoryCode = null,
            barcodeValue = null,
            ocrTextHints = emptyList(),
            imageLabelHints = listOf("tableware", "cutlery"),
            objectLabel = null,
            objectConfidence = null,
        )

        assertTrue(candidates.none { it.categoryCode == "HOME.KITCHEN_DINING" })
    }

    @Test
    fun route_maps_robot_dog_to_toys_not_pets() {
        val candidates = VisualSearchPreflightCategoryRouter.route(
            selectedCategoryCode = null,
            barcodeValue = null,
            ocrTextHints = listOf("робот собака с пультом управления"),
            imageLabelHints = listOf("toy"),
            objectLabel = "robot dog",
            objectConfidence = 0.83f,
        )

        assertEquals("KIDS.TOYS_GAMES", candidates.first().categoryCode)
        assertTrue(candidates.none { it.categoryCode == "PETS.ACCESSORIES" })
    }

    @Test
    fun route_maps_game_console_to_gaming() {
        val candidates = VisualSearchPreflightCategoryRouter.route(
            selectedCategoryCode = null,
            barcodeValue = null,
            ocrTextHints = listOf("Microsoft Xbox Series X"),
            imageLabelHints = listOf("video game console"),
            objectLabel = "game console",
            objectConfidence = 0.84f,
        )

        assertEquals("TECH.GAMING", candidates.first().categoryCode)
    }
}
