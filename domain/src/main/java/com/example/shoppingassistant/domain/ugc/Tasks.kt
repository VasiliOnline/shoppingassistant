// Last synced: 2025-12-10 15:32:11
package com.example.shoppingassistant.domain.ugc

/**
 * Результат UGC-зеркала по ссылке.
 *
 * Это сырые нормализованные данные, которые потом будут
 * использованы для автозаполнения Express-объявления / товара.
 */
data class UgcMirrorResult(
    /**
     * Оригинальная ссылка, которую пользователь вставил в поле.
     */
    val sourceUrl: String,

    /**
     * Заголовок / название товара, если удалось распарсить.
     */
    val title: String? = null,

    /**
     * Описание / подзаголовок.
     */
    val description: String? = null,

    /**
     * Цена как строка (сырое значение, без жёсткой привязки к Money).
     */
    val priceRaw: String? = null,

    /**
     * Валюта как строковый код (например, "RUB", "EUR"), если удалось определить.
     */
    val currency: String? = null,

    /**
     * Список URL картинок товара.
     */
    val imageUrls: List<String> = emptyList(),

    /**
     * Произвольные атрибуты, которые мы смогли вытащить со страницы.
     * Например: brand=Apple, model=iPhone 14, color=Blue и т.п.
     */
    val attributes: Map<String, String> = emptyMap(),
)

/**
 * Доменный контракт UGC-зеркала по ссылке.
 *
 * Реализация будет в core/data/ugc/UgcMirrorRemoteRepository,
 * который сходит на backend и вернёт результат.
 */
interface UgcMirrorRepository {

    /**
     * Дёргает backend-UGC зеркало по ссылке и возвращает нормализованные данные.
     *
     * @param url ссылка на внешний маркетплейс/объявление.
     */
    suspend fun mirrorByUrl(url: String): UgcMirrorResult
}

/**
 * Use-case для UGC-зеркала по ссылке.
 *
 * Держим отдельным классом, чтобы легко подключать в DI,
 * прокидывать в ViewModel и т.д.
 */
class MirrorByUrlUseCase(
    private val repository: UgcMirrorRepository,
) {

    suspend operator fun invoke(url: String): UgcMirrorResult {
        return repository.mirrorByUrl(url)
    }
}
