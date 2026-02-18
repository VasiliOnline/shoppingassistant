package com.example.shoppingassistant.domain.model

/**
 * Контракт репозитория офферов (объявлений) для прод-логики поиска.
 * Имплементация будет читать из Postgres и использовать ранжирование.
 */
interface OfferRepository {
    suspend fun searchOffers(criteria: OfferSearchCriteria): List<OfferFull>

    suspend fun searchOffersWithFacets(req: OfferSearchWithFacetsRequest): OfferSearchWithFacetsResponse
}
