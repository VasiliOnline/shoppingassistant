package com.example.shoppingassistant.feature.pages.useroffers.state

import androidx.compose.runtime.Composable
import com.example.shoppingassistant.feature.pages.useroffers.UserOffersStateHandle

/**
 * Контракт состояния страницы "Мои товары".
 */
interface UserOffersStateTask {
    @Composable
    fun stateHandle(): UserOffersStateHandle
}
