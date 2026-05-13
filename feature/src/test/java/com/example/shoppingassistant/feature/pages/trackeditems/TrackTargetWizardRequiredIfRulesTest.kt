package com.example.shoppingassistant.feature.pages.trackeditems

import com.example.shoppingassistant.domain.catalog.AttributeCondition
import com.example.shoppingassistant.domain.catalog.AttributeConditionOp
import com.example.shoppingassistant.domain.catalog.AttributeDataType
import com.example.shoppingassistant.domain.catalog.CatalogAttributeSpec
import com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.CatalogCategoryReadiness
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.Stage22ValueSetType
import com.example.shoppingassistant.domain.catalog.Stage22ValueType
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

        assertFalse(draft.isValid(spec = null))
    }

    @Test
    fun product_with_category_is_valid_without_brand_model_when_required_attributes_filled() {
        val draft = TrackTargetDraft(
            type = TrackType.PRODUCT,
            categoryCode = "TECH.PHONES",
            attributes = mapOf("condition" to "new"),
        )
        val spec = specWithRules()

        assertTrue(draft.isValid(spec))
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
        val spec = specWithRules(
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

        val missing = draft.missingRequiredAttributeCodes(spec)
        assertEquals(setOf("battery_health_percent"), missing)
        assertFalse(draft.isValid(spec))
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
        val spec = specWithRules(
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

        val missing = draft.missingRequiredAttributeCodes(spec)
        assertTrue(missing.isEmpty())
        assertTrue(draft.isValid(spec))
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
        val spec = specWithRules(
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

        val missing = draft.missingRequiredAttributeCodes(spec)
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
        val spec = specWithRules(
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

        val missing = draft.missingRequiredAttributeCodes(spec)
        assertTrue(missing.isEmpty())
    }

    private fun specWithRules(vararg rules: RequiredIfRule): CatalogCategoryEffectiveSpec =
        CatalogCategoryEffectiveSpec(
            category = Category(
                code = "TECH.PHONES",
                segment = CategorySegment.TECH,
            ),
            readiness = CatalogCategoryReadiness.READY,
            attributes = listOf(
                CatalogAttributeSpec(
                    code = "condition",
                    title = "Condition",
                    dataType = AttributeDataType.STRING,
                    valueType = Stage22ValueType.STRING,
                    valueSetType = Stage22ValueSetType.OPEN,
                ),
                CatalogAttributeSpec(
                    code = "battery_health_percent",
                    title = "Battery Health",
                    dataType = AttributeDataType.STRING,
                    valueType = Stage22ValueType.STRING,
                    valueSetType = Stage22ValueSetType.OPEN,
                ),
                CatalogAttributeSpec(
                    code = "seller_tag",
                    title = "Seller Tag",
                    dataType = AttributeDataType.STRING,
                    valueType = Stage22ValueType.STRING,
                    valueSetType = Stage22ValueSetType.OPEN,
                ),
                CatalogAttributeSpec(
                    code = "seller_proof",
                    title = "Seller Proof",
                    dataType = AttributeDataType.STRING,
                    valueType = Stage22ValueType.STRING,
                    valueSetType = Stage22ValueSetType.OPEN,
                ),
            ),
            requiredIfRules = rules.toList(),
        )
}
