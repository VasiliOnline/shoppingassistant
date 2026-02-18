package com.example.shoppingassistant.feature.pages.useroffers.tasks

import android.content.Context
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferCardUi
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferHealth
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferPublicationStatus
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Локальная реализация для хранения созданных офферов (SharedPreferences).
 * Сохраняет данные между перезапусками и дедуплицирует по id.
 */
class UserOffersCreatedStoreTask(
    context: Context,
) : UserOffersCreatedStore {
    private val prefs = context.getSharedPreferences("user_offers_created", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow<List<UserOfferCardUi>>(emptyList())
    private val _errorMessage = MutableStateFlow<String?>(null)

    override val items: StateFlow<List<UserOfferCardUi>> = _items
    override val errorMessage: StateFlow<String?> = _errorMessage

    init {
        loadFromStorage()
    }

    override fun add(offer: UserOfferCardUi) {
        val updated = upsert(offer, _items.value)
        updateItems(updated)
    }

    override fun update(offer: UserOfferCardUi) {
        val updated = upsert(offer, _items.value)
        updateItems(updated)
    }

    override fun remove(offerId: String) {
        updateItems(_items.value.filterNot { it.id == offerId })
    }

    override fun replaceId(oldId: String, newId: String) {
        if (oldId == newId) return
        val items = _items.value
        val idx = items.indexOfFirst { it.id == oldId }
        if (idx < 0) return
        val updated = items.toMutableList()
        val replaced = updated[idx].copy(id = newId)
        updated[idx] = replaced
        updateItems(updated)
    }

    override fun replaceAll(items: List<UserOfferCardUi>) {
        updateItems(items)
    }

    override fun clear() {
        updateItems(emptyList())
    }

    private fun updateItems(items: List<UserOfferCardUi>) {
        val deduped = items.distinctBy { it.id }
        _items.value = deduped
        persist(deduped)
    }

    private fun upsert(offer: UserOfferCardUi, items: List<UserOfferCardUi>): List<UserOfferCardUi> {
        val existingIdx = items.indexOfFirst { it.id == offer.id }
        return if (existingIdx >= 0) {
            items.toMutableList().apply { set(existingIdx, offer) }
        } else {
            listOf(offer) + items
        }
    }

    private fun loadFromStorage() {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            val parsed = buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    parseOffer(obj)?.let { add(it) }
                }
            }
            _items.value = parsed
            _errorMessage.value = null
        }.onFailure {
            _errorMessage.value = "Не удалось загрузить сохраненные товары"
        }
    }

    private fun persist(items: List<UserOfferCardUi>) {
        runCatching {
            val array = JSONArray()
            items.forEach { array.put(encodeOffer(it)) }
            prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
            _errorMessage.value = null
        }.onFailure {
            _errorMessage.value = "Не удалось сохранить товары"
        }
    }

    private fun encodeOffer(offer: UserOfferCardUi): JSONObject = JSONObject().apply {
        put("id", offer.id)
        put("title", offer.title)
        put("category", offer.category)
        put("priceMajor", offer.priceMajor)
        put("currency", offer.currency)
        put("status", offer.status.name)
        put("publicationStatus", offer.publicationStatus.name)
        put("coverUrl", offer.coverUrl)
        put("publishedAtMillis", offer.publishedAtMillis)
        put("updatedAtMillis", offer.updatedAtMillis)
        put("sourceUpdatedAtMillis", offer.sourceUpdatedAtMillis)
        put("sourceName", offer.sourceName)
        put("sourceIconUrl", offer.sourceIconUrl)
        put("expiresAtMillis", offer.expiresAtMillis)
        put("completedAtMillis", offer.completedAtMillis)
        put("lastRenewedAtMillis", offer.lastRenewedAtMillis)
        put("viewsCount", offer.viewsCount)
        put("favoritesCount", offer.favoritesCount)
        put("messagesCount", offer.messagesCount)
        put("todayViews", offer.todayViews)
        put("todayContacts", offer.todayContacts)
        put("isPromoted", offer.isPromoted)
        put("health", offer.health?.name)
    }

    private fun parseOffer(obj: JSONObject): UserOfferCardUi? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val title = obj.optString("title").takeIf { it.isNotBlank() } ?: return null
        val status = obj.optString("status")
        val publication = obj.optString("publicationStatus")
        val health = obj.optString("health")
        return UserOfferCardUi(
            id = id,
            title = title,
            category = obj.optString("category").takeIf { it.isNotBlank() },
            priceMajor = obj.optDoubleOrNull("priceMajor"),
            currency = obj.optString("currency").ifBlank { "USD" },
            status = status.toUserOfferStatus(),
            publicationStatus = publication.toPublicationStatus(),
            coverUrl = obj.optString("coverUrl").takeIf { it.isNotBlank() },
            publishedAtMillis = obj.optLongOrNull("publishedAtMillis"),
            updatedAtMillis = obj.optLongOrNull("updatedAtMillis"),
            sourceUpdatedAtMillis = obj.optLongOrNull("sourceUpdatedAtMillis"),
            sourceName = obj.optString("sourceName").takeIf { it.isNotBlank() },
            sourceIconUrl = obj.optString("sourceIconUrl").takeIf { it.isNotBlank() },
            expiresAtMillis = obj.optLongOrNull("expiresAtMillis"),
            completedAtMillis = obj.optLongOrNull("completedAtMillis"),
            lastRenewedAtMillis = obj.optLongOrNull("lastRenewedAtMillis"),
            viewsCount = obj.optIntOrNull("viewsCount"),
            favoritesCount = obj.optIntOrNull("favoritesCount"),
            messagesCount = obj.optIntOrNull("messagesCount"),
            todayViews = obj.optIntOrNull("todayViews"),
            todayContacts = obj.optIntOrNull("todayContacts"),
            isPromoted = obj.optBoolean("isPromoted", false),
            health = health.toHealthStatus(),
        )
    }

    private fun String.toUserOfferStatus(): UserOfferStatus = runCatching {
        UserOfferStatus.valueOf(this)
    }.getOrDefault(UserOfferStatus.ACTIVE)

    private fun String.toPublicationStatus(): UserOfferPublicationStatus = runCatching {
        UserOfferPublicationStatus.valueOf(this)
    }.getOrDefault(UserOfferPublicationStatus.PUBLISHED)

    private fun String.toHealthStatus(): UserOfferHealth? = runCatching {
        if (this.isBlank()) null else UserOfferHealth.valueOf(this)
    }.getOrNull()

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key).takeIf { it > 0 } else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key).takeIf { it >= 0 } else null

    private companion object {
        const val KEY_ITEMS = "items"
    }
}
