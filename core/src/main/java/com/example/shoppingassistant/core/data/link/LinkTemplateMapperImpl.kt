package com.example.shoppingassistant.core.data.link

import com.example.shoppingassistant.domain.ingest.SourceType
import com.example.shoppingassistant.domain.offers.OfferCategory
import com.example.shoppingassistant.domain.offers.TrackedOfferInput
import com.example.shoppingassistant.domain.offers.TrackedOfferSource
import com.example.shoppingassistant.domain.model.Normalization

/**
 * Чистый маппер LinkTemplateRaw + selectedFilters → TrackedOfferInput.
 */
class LinkTemplateMapperImpl : LinkTemplateMapperTask {

    override fun map(
        template: LinkTemplateRaw,
        selectedFilters: Map<String, String>,
        userId: String,
        photoUrls: List<String>,
    ): TrackedOfferInput {
        val price = selectedFilters["price"]?.toDoubleOrNull() ?: template.price ?: 0.0
        val currency = selectedFilters["currency"] ?: template.currency ?: "RUB"
        val brand = selectedFilters["brand"] ?: template.brand
        val model = selectedFilters["model"] ?: template.model
        val attrs = Normalization.normalizeAttrs(selectedFilters - listOf("price", "currency", "brand", "model"))
        val imageUrls = (photoUrls + template.imageUrls)
            .filter { it.isNotBlank() }
            .distinct()

        return TrackedOfferInput(
            userId = userId,
            title = selectedFilters["title"]
                ?: template.title
                ?: listOfNotNull(brand, model).joinToString(" ").ifBlank { "Предложение" },
            category = template.category.takeIf { it != OfferCategory.OTHER } ?: OfferCategory.TECH,
            brand = brand,
            model = model,
            priceValue = price,
            currency = currency,
            imageUrls = imageUrls,
            attributes = template.attributes + attrs,
            source = TrackedOfferSource(
                sourceType = template.sourceMeta.sourceType,
                sourceId = template.sourceMeta.sourceId,
                url = template.sourceMeta.url,
                canonicalUrl = template.sourceMeta.canonicalUrl,
                listingId = template.sourceMeta.listingId,
                domainName = template.sourceMeta.domainName,
                sourceIconUrl = template.sourceMeta.sourceIconUrl,
            ),
        )
    }
}
