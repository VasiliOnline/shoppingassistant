package com.example.shoppingassistant.feature.pages.useroffers.bulk

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferBulkAction

interface UserOffersBulkTask {
    @Composable
    fun ActionsBar(
        selectedCount: Int,
        showActivate: Boolean,
        showPause: Boolean,
        onAction: (UserOfferBulkAction) -> Unit,
        onClear: () -> Unit,
        modifier: Modifier = Modifier,
    )

    @Composable
    fun PriceSheet(
        visible: Boolean,
        selectedCount: Int,
        priceInput: String,
        onPriceInputChange: (String) -> Unit,
        onApply: () -> Unit,
        onDismiss: () -> Unit,
    )
}
