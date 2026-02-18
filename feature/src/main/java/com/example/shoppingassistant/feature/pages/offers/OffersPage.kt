package com.example.shoppingassistant.feature.pages.offers

import androidx.compose.runtime.Composable
import com.example.shoppingassistant.feature.pages.offers.tasks.OffersSheetProps
import com.example.shoppingassistant.feature.pages.offers.tasks.OffersSheetTask

/**
 * Оркестратор страницы офферов: собирает нижний лист из отдельных UI-тасок.
 */
@Composable
fun OffersPage(props: OffersSheetProps) {
    OffersSheetTask(props)
}
