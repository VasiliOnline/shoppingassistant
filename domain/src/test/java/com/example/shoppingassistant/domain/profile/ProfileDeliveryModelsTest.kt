package com.example.shoppingassistant.domain.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDeliveryModelsTest {

    @Test
    fun region_zone_conflicts_with_city_inside_same_region() {
        val region = SellerDeliveryZone(
            id = "region",
            scope = DeliveryAreaScope.REGION,
            location = DeliveryAddressLocation(
                countryCode = "RU",
                adminArea = "Moscow",
            ),
        )
        val city = SellerDeliveryZone(
            id = "city",
            scope = DeliveryAreaScope.CITY,
            location = DeliveryAddressLocation(
                countryCode = "RU",
                adminArea = "Moscow",
                locality = "Moscow",
            ),
        )

        assertTrue(hasDeliveryZoneConflict(listOf(region), city))
        assertTrue(hasDeliveryZoneConflict(listOf(city), region))
    }

    @Test
    fun point_zone_conflicts_with_city_inside_same_locality() {
        val city = SellerDeliveryZone(
            id = "city",
            scope = DeliveryAreaScope.CITY,
            location = DeliveryAddressLocation(
                countryCode = "RU",
                adminArea = "Moscow",
                locality = "Moscow",
            ),
        )
        val point = SellerDeliveryZone(
            id = "point",
            scope = DeliveryAreaScope.POINT,
            location = DeliveryAddressLocation(
                countryCode = "RU",
                adminArea = "Moscow",
                locality = "Moscow",
                addressLine = "Tverskaya 1",
            ),
        )

        assertTrue(hasDeliveryZoneConflict(listOf(city), point))
    }

    @Test
    fun legacy_shipping_tokens_are_converted_to_country_zones() {
        val zones = legacyShippingCountriesToDeliveryZones(
            listOf("RU", "Россия", "worldwide"),
        )

        assertEquals(1, zones.size)
        assertEquals(DeliveryAreaScope.COUNTRY, zones.first().scope)
        assertEquals("RU", zones.first().location.countryCode)
    }

    @Test
    fun normalized_delivery_addresses_keep_existing_active_id() {
        val settings = ProfileSettings(
            deliveryAddresses = listOf(
                BuyerDeliveryAddress(
                    id = "home",
                    label = "Дом",
                    location = DeliveryAddressLocation(countryCode = "ru", locality = "Moscow"),
                ),
                BuyerDeliveryAddress(
                    id = "office",
                    label = "Офис",
                    location = DeliveryAddressLocation(countryCode = "kz", locality = "Almaty"),
                ),
            ),
            activeDeliveryAddressId = "office",
        )

        val normalized = settings.withNormalizedDeliveryAddresses()

        assertEquals("office", normalized.activeDeliveryAddressId)
        assertEquals("KZ", normalized.activeDeliveryAddress()?.location?.countryCode)
        assertNotNull(normalized.activeDeliveryAddress())
    }
}
