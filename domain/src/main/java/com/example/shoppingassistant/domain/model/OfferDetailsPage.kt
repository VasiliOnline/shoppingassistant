package com.example.shoppingassistant.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class OfferDetailsPage(
    val offer: OfferFull,
    val categoryCode: String? = null,
    val detailState: OfferDetailState = OfferDetailState.NATIVE_LOCAL,
    val provenance: OfferProvenance = OfferProvenance(),
    val capabilities: OfferDetailCapabilities = OfferDetailCapabilities(),
    val relatedOffers: List<OfferRelatedOffer> = emptyList(),
)

@Serializable
enum class OfferDetailState {
    NATIVE_LOCAL,
    EXTERNAL_UNCLAIMED,
    EXTERNAL_CLAIMED,
}

@Serializable
data class OfferProvenance(
    val sourceName: String? = null,
    val sourceUrl: String? = null,
    val canonicalUrl: String? = null,
    val sourceDomain: String? = null,
    val sourceIconUrl: String? = null,
    val sourceType: String? = null,
    val lastSyncedAtMillis: Long? = null,
)

@Serializable
data class OfferDetailCapabilities(
    val canChat: Boolean = false,
    val canOpenSource: Boolean = false,
    val canTrackPrice: Boolean = false,
    val canShare: Boolean = true,
    val canReport: Boolean = true,
)

@Serializable
data class OfferRelatedOffer(
    val id: String,
    val title: String,
    val price: Money,
    val currency: String,
    val imageUrl: String? = null,
    val sourceName: String? = null,
    val updatedAtMillis: Long? = null,
)
