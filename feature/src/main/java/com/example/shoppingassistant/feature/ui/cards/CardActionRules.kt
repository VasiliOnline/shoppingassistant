package com.example.shoppingassistant.feature.ui.cards

enum class OfferOverflowAction {
    Share,
    Hide,
    Report,
    CopyLink,
}

enum class CompactOverflowAction {
    Share,
    Hide,
}

enum class ScenarioOverflowAction {
    Remove,
    Reset,
}

object CardActionRules {
    fun offerOverflowActions(includeCopyLink: Boolean): List<OfferOverflowAction> = buildList {
        add(OfferOverflowAction.Share)
        add(OfferOverflowAction.Hide)
        add(OfferOverflowAction.Report)
        if (includeCopyLink) add(OfferOverflowAction.CopyLink)
    }

    val compactOverflowActions: List<CompactOverflowAction> = listOf(
        CompactOverflowAction.Share,
        CompactOverflowAction.Hide,
    )

    val scenarioOverflowActions: List<ScenarioOverflowAction> = listOf(
        ScenarioOverflowAction.Remove,
        ScenarioOverflowAction.Reset,
    )
}
