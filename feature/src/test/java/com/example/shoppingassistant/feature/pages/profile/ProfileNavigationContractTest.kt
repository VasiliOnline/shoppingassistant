package com.example.shoppingassistant.feature.pages.profile

import com.example.shoppingassistant.feature.navigation.AppRoutes
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileNavigationContractTest {

    @Test
    fun builds_owner_profile_route_without_user_id() {
        assertEquals("profile", AppRoutes.profile())
    }

    @Test
    fun builds_public_profile_route_with_user_id() {
        assertEquals("profile?userId=42", AppRoutes.profile(42))
    }

    @Test
    fun exposes_profile_route_with_optional_user_id_argument() {
        assertEquals("profile?userId={userId}", AppRoutes.ProfileRoute)
    }
}
