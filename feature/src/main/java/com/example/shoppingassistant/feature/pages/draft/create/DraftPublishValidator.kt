package com.example.shoppingassistant.feature.pages.draft.create

import com.example.shoppingassistant.domain.ugc.draft.DraftContacts
import com.example.shoppingassistant.domain.ugc.draft.DraftLocation
import com.example.shoppingassistant.domain.ugc.draft.DraftOffer
import com.example.shoppingassistant.domain.ugc.draft.DraftPriceType

enum class DraftMissingField {
    MEDIA,
    TITLE,
    PRICE,
    CATEGORY,
    ATTRIBUTES,
    LOCATION,
    CONTACTS,
    POLICY,
}

data class DraftPublishValidation(
    val missing: List<DraftMissingField>,
) {
    val isReady: Boolean = missing.isEmpty()
}

object DraftPublishValidator {
    private const val CONDITION_ATTR = "condition"

    fun validate(draft: DraftOffer?, requiredAttrs: Set<String>): DraftPublishValidation {
        if (draft == null) return DraftPublishValidation(emptyList())
        val missing = mutableListOf<DraftMissingField>()
        if (draft.media.isEmpty()) missing += DraftMissingField.MEDIA
        if (draft.title.isNullOrBlank()) missing += DraftMissingField.TITLE
        if (!isPriceReady(draft)) missing += DraftMissingField.PRICE
        if (draft.categoryCode.isNullOrBlank()) missing += DraftMissingField.CATEGORY
        if (!areRequiredAttributesReady(draft, requiredAttrs)) missing += DraftMissingField.ATTRIBUTES
        if (!isLocationReady(draft.location)) missing += DraftMissingField.LOCATION
        if (!hasContacts(draft.contacts)) missing += DraftMissingField.CONTACTS
        if (draft.policyState.restrictedCategory) missing += DraftMissingField.POLICY
        return DraftPublishValidation(missing)
    }

    fun hasContacts(contacts: DraftContacts): Boolean =
        contacts.chatEnabled || contacts.phoneEnabled

    fun isPriceReady(draft: DraftOffer): Boolean =
        when (draft.price.type) {
            DraftPriceType.FIXED -> draft.price.amountMajor != null
            DraftPriceType.NEGOTIABLE,
            DraftPriceType.FREE,
            DraftPriceType.EXCHANGE,
            -> true
        }

    fun isLocationReady(location: DraftLocation): Boolean =
        !location.publicLabel.isNullOrBlank() ||
            !location.city.isNullOrBlank() ||
            !location.address.isNullOrBlank()

    fun isAttributeReady(draft: DraftOffer, code: String): Boolean =
        when (code) {
            CONDITION_ATTR -> draft.condition != null
            else -> !draft.attributes[code].isNullOrBlank()
        }

    private fun areRequiredAttributesReady(draft: DraftOffer, requiredAttrs: Set<String>): Boolean {
        if (requiredAttrs.isEmpty()) return true
        return requiredAttrs.all { code -> isAttributeReady(draft, code) }
    }
}
