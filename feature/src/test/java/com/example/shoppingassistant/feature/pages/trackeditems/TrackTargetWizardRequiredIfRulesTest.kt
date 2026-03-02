package com.example.shoppingassistant.feature.pages.trackeditems

import com.example.shoppingassistant.domain.catalog.AttributeCondition
import com.example.shoppingassistant.domain.catalog.AttributeConditionOp
import com.example.shoppingassistant.domain.catalog.AttributeDataType
import com.example.shoppingassistant.domain.catalog.AttributeDef
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategoryProfile
import com.example.shoppingassistant.domain.catalog.CategorySegment
import com.example.shoppingassistant.domain.catalog.RequiredIfRule
import com.example.shoppingassistant.domain.tracks.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackTargetWizardRequiredIfRulesTest {

    @Test
    fun product_without_category_is_invalid_even_when_brand_model_present() {
        val draft = TrackTargetDraft(
            type = TrackType.PRODUCT,
            brand = "Acme",
            model = "X1",
            categoryCode = null,
        )

        assertFalse(draft.isValid(profile = null))
    }

    @Test
    fun product_with_category_is_valid_without_brand_model_when_required_attributes_filled() {
        val draft = TrackTargetDraft(
            type = TrackType.PRODUCT,
            categoryCode = "TECH.PHONES",
            attributes = mapOf("condition" to "new"),
        )
        val profile = profileWithRules()

        assertTrue(draft.isValid(profile))
    }

    @Test
    fun missingRequiredAttributeCodes_returns_missing_when_requiredIf_triggered() {
        val draft = TrackTargetDraft(
            type = TrackType.CATEGORY,
            categoryCode = "TECH.PHONES",
            attributes = mapOf(
                "condition" to "used",
            ),
        )
        val profile = profileWithRules(
            RequiredIfRule(
                requiredAttributeCode = "battery_health_percent",
                whenAll = listOf(
                    AttributeCondition(
                        attributeCode = "condition",
                        op = AttributeConditionOp.EQUALS_ANY,
                        values = listOf("used"),
                    ),
                ),
            ),
        )

        val missing = draft.missingRequiredAttributeCodes(profile)
        assertEquals(setOf("battery_health_percent"), missing)
        assertFalse(draft.isValid(profile))
    }

    @Test
    fun missingRequiredAttributeCodes_doesNotRequire_when_condition_not_met() {
        val draft = TrackTargetDraft(
            type = TrackType.CATEGORY,
            categoryCode = "TECH.PHONES",
            attributes = mapOf(
                "condition" to "new",
            ),
        )
        val profile = profileWithRules(
            RequiredIfRule(
                requiredAttributeCode = "battery_health_percent",
                whenAll = listOf(
                    AttributeCondition(
                        attributeCode = "condition",
                        op = AttributeConditionOp.EQUALS_ANY,
                        values = listOf("used"),
                    ),
                ),
            ),
        )

        val missing = draft.missingRequiredAttributeCodes(profile)
        assertTrue(missing.isEmpty())
        assertTrue(draft.isValid(profile))
    }

    @Test
    fun missingRequiredAttributeCodes_treats_blank_value_as_missing_for_requiredIf() {
        val draft = TrackTargetDraft(
            type = TrackType.CATEGORY,
            categoryCode = "TECH.PHONES",
            attributes = mapOf(
                "condition" to "used",
                "battery_health_percent" to "   ",
            ),
        )
        val profile = profileWithRules(
            RequiredIfRule(
                requiredAttributeCode = "battery_health_percent",
                whenAll = listOf(
                    AttributeCondition(
                        attributeCode = "condition",
                        op = AttributeConditionOp.EQUALS_ANY,
                        values = listOf("used"),
                    ),
                ),
            ),
        )

        val missing = draft.missingRequiredAttributeCodes(profile)
        assertEquals(setOf("battery_health_percent"), missing)
    }

    @Test
    fun requiredIf_startsWithAny_negative_case_does_not_trigger_requirement() {
        val draft = TrackTargetDraft(
            type = TrackType.CATEGORY,
            categoryCode = "TECH.PHONES",
            attributes = mapOf(
                "seller_tag" to "basic",
            ),
        )
        val profile = profileWithRules(
            RequiredIfRule(
                requiredAttributeCode = "seller_proof",
                whenAll = listOf(
                    AttributeCondition(
                        attributeCode = "seller_tag",
                        op = AttributeConditionOp.STARTS_WITH_ANY,
                        values = listOf("pro_"),
                    ),
                ),
            ),
        )

        val missing = draft.missingRequiredAttributeCodes(profile)
        assertTrue(missing.isEmpty())
    }

    private fun profileWithRules(vararg rules: RequiredIfRule): CategoryProfile =
        CategoryProfile(
            category = Category(
                code = "TECH.PHONES",
                segment = CategorySegment.TECH,
            ),
            attributes = listOf(
                AttributeDef(
                    code = "condition",
                    title = "Condition",
                    dataType = AttributeDataType.STRING,
                ),
                AttributeDef(
                    code = "battery_health_percent",
                    title = "Battery Health",
                    dataType = AttributeDataType.STRING,
                ),
                AttributeDef(
                    code = "seller_tag",
                    title = "Seller Tag",
                    dataType = AttributeDataType.STRING,
                ),
                AttributeDef(
                    code = "seller_proof",
                    title = "Seller Proof",
                    dataType = AttributeDataType.STRING,
                ),
            ),
            categoryAttributes = emptyList(),
            requiredIfRules = rules.toList(),
        )
}
