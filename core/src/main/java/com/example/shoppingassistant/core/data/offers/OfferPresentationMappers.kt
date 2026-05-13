package com.example.shoppingassistant.core.data.offers

import com.example.shoppingassistant.core.data.Normalizer
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.OfferFull
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.model.OfferSource
import com.example.shoppingassistant.domain.model.ProductDto
import com.example.shoppingassistant.domain.model.asFloatOrNull
import com.example.shoppingassistant.domain.model.asTextOrNull
import java.net.URI

data class OfferOpenUrls(
    val externalUrl: String?,
    val redirectUrl: String?,
    val deeplinkUrl: String?,
)

fun OfferSearchCriteria.toNormalizedQuery(): NormalizedQuery =
    NormalizedQuery(
        brand = brand ?: brands.firstOrNull().orEmpty(),
        model = model ?: "",
        attributes = attributes,
    )

fun OfferFull.toProductDto(): ProductDto {
    val offerOpenUrls = resolveOfferOpenUrls()
    val externalUrl = offerOpenUrls.externalUrl
    return ProductDto(
        id = id,
        title = product.title,
        brand = product.brand,
        brandId = product.brand?.let { Normalizer.key(it) }?.takeIf { it.isNotBlank() },
        model = product.model,
        price = price.toMajor(),
        deliveryTime = null,
        sellerRating = seller.rating?.value,
        sellerRatingCount = seller.rating?.count,
        sellerCountry = seller.countryCode,
        sellerCity = seller.city,
        sellerBadges = seller.preferences.badges.map { it.name },
        sellerShippingCountries = seller.preferences.shippingCountries,
        sellerName = seller.name,
        sellerAvatarUrl = seller.avatarUrl,
        sellerType = null,
        source = if (externalUrl == null) OfferSource.EXPRESS else OfferSource.EXTERNAL,
        sourceName = sourceNameFromUrl(externalUrl) ?: "Express",
        externalUrl = externalUrl,
        redirectUrl = offerOpenUrls.redirectUrl,
        deeplinkUrl = offerOpenUrls.deeplinkUrl,
        imageUrls = (imageUrls + product.imageUrls).filter { it.isNotBlank() }.distinct(),
        updatedAt = updatedAt ?: product.updatedAt,
        trustScore = null,
        distanceKm = distanceKmOrNull(),
        attributes = product.specs,
    )
}

fun OfferFull.resolveOfferOpenUrls(): OfferOpenUrls {
    fun pick(keys: List<String>): String? {
        keys.forEach { key ->
            val direct = attributes[key]?.asTextOrNull()?.trim()?.takeIf { value -> value.isNotEmpty() }
                ?: attributes.entries
                    .firstOrNull { (attrKey, _) -> attrKey.equals(key, ignoreCase = true) }
                    ?.value
                    ?.asTextOrNull()
                    ?.trim()
                    ?.takeIf { value -> value.isNotEmpty() }
            if (direct != null) return direct
        }
        return null
    }

    val redirectUrl = pick(listOf("redirect_url", "redirectUrl", "track_url", "trackUrl"))
    val deeplinkUrl = pick(
        listOf(
            "deeplink_url",
            "deeplinkUrl",
            "deeplink",
            "external_url",
            "externalUrl",
            "source_url",
            "sourceUrl",
            "url",
        ),
    )
    val externalUrl = redirectUrl ?: deeplinkUrl

    return OfferOpenUrls(
        externalUrl = externalUrl,
        redirectUrl = redirectUrl,
        deeplinkUrl = deeplinkUrl,
    )
}

private fun sourceNameFromUrl(url: String?): String? {
    val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { URI(raw).host?.removePrefix("www.") }.getOrNull()
}

private fun OfferFull.distanceKmOrNull(): Float? {
    val raw = attributes["distance_km"]
        ?: attributes["distanceKm"]
        ?: attributes["distance"]
        ?: return null
    return raw.asFloatOrNull()
}
