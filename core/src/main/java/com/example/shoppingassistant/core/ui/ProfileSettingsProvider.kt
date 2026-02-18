package com.example.shoppingassistant.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.shoppingassistant.domain.profile.ProfileSettings

val LocalProfileSettings = staticCompositionLocalOf { ProfileSettings() }

@Composable
fun ProvideProfileSettings(
    settings: ProfileSettings,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalProfileSettings provides settings, content = content)
}
