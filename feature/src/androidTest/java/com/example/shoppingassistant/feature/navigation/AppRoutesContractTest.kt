package com.example.shoppingassistant.feature.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppRoutesContractTest {

    @Test
    fun chat_route_encodes_redirect_and_deeplink_arguments() {
        val route = AppRoutes.chat(
            offerId = "offer-123",
            sellerName = "Seller Name",
            offerTitle = "Offer title",
            price = "12990.0",
            status = "online",
            externalUrl = "https://example.com/offers/123",
            redirectUrl = "https://redirect.example/offer/123?utm_source=results&utm_campaign=a b",
            deeplinkUrl = "https://deeplink.example/offers/123?from=app&x=1",
            sourceName = "example.com",
            querySessionId = "qs-123",
            position = 5,
        )

        assertTrue(
            route.contains(
                "${AppRoutes.ArgRedirectUrl}=https%3A%2F%2Fredirect.example%2Foffer%2F123%3Futm_source%3Dresults%26utm_campaign%3Da%20b",
            ),
        )
        assertTrue(
            route.contains(
                "${AppRoutes.ArgDeeplinkUrl}=https%3A%2F%2Fdeeplink.example%2Foffers%2F123%3Ffrom%3Dapp%26x%3D1",
            ),
        )
    }

    @Test
    fun chat_route_keeps_empty_values_for_optional_arguments() {
        val route = AppRoutes.chat(
            offerId = "offer-1",
            sellerName = "Seller",
            offerTitle = "Title",
            price = null,
            status = null,
            externalUrl = null,
            redirectUrl = null,
            deeplinkUrl = null,
            sourceName = null,
            querySessionId = null,
            position = null,
        )

        assertTrue(route.contains("${AppRoutes.ArgExternalUrl}="))
        assertTrue(route.contains("${AppRoutes.ArgRedirectUrl}="))
        assertTrue(route.contains("${AppRoutes.ArgDeeplinkUrl}="))
    }
}
