package com.example.shoppingassistant.domain.catalog

/**
 * Репозиторий подсчётов объявлений по категориям (локальная БД/кеш).
 */
interface CategoryOfferCountsRepository {
    suspend fun getCounts(categoryCodes: List<String>): Map<String, Int>
}

class GetCategoryOfferCountsTask(
    private val repository: CategoryOfferCountsRepository,
) {
    suspend operator fun invoke(categoryCodes: List<String>): Map<String, Int> =
        repository.getCounts(categoryCodes)
}
