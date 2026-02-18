package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.constraints.AttributeValueConstraint
import com.example.shoppingassistant.domain.catalog.constraints.CompatibilityRule
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb

private val json = Json { ignoreUnknownKeys = true }

object CategoriesTable : Table("categories") {
    val code = varchar("code", 64)
    val segment = varchar("segment", 16)
    val title = varchar("title", 255).nullable()
    val parentCode = varchar("parent_code", 64).nullable()
    val description = text("description").nullable()

    override val primaryKey = PrimaryKey(code)
}

object AttributeDefsTable : Table("attribute_defs") {
    val code = varchar("code", 64)
    val title = varchar("title", 255)
    val dataType = varchar("data_type", 32)
    val requiredForSearch = bool("required_for_search").default(false)
    val requiredForOffer = bool("required_for_offer").default(false)
    val requiredForExpress = bool("required_for_express").default(false)
    val facetEnabled = bool("facet_enabled").default(false)
    val multiValued = bool("multi_valued").default(false)
    val valueDictCode = varchar("value_dict_code", 64).nullable()

    override val primaryKey = PrimaryKey(code)
}

object CategoryAttributesTable : Table("category_attributes") {
    val categoryCode = varchar("category_code", 64).references(CategoriesTable.code, onDelete = ReferenceOption.CASCADE)
    val attributeCode = varchar("attribute_code", 64).references(AttributeDefsTable.code, onDelete = ReferenceOption.CASCADE)
    val uiOrder = integer("ui_order").default(0)
    val isRequired = bool("is_required").default(false)

    override val primaryKey = PrimaryKey(categoryCode, attributeCode)
}

object AttributeValueDictTable : Table("attribute_value_dict") {
    val attributeCode = varchar("attribute_code", 64).references(AttributeDefsTable.code, onDelete = ReferenceOption.CASCADE)
    val canonicalCode = varchar("canonical_code", 64)
    val canonicalValue = varchar("canonical_value", 255)
    val synonyms = jsonb("synonyms", json, ListSerializer(String.serializer())).nullable()

    override val primaryKey = PrimaryKey(attributeCode, canonicalCode)
}

object CatalogConstraintsTable : Table("catalog_constraints") {
    val id = long("id").autoIncrement()
    val scope = varchar("scope", 16)
    val categoryCode = varchar("category_code", 64).nullable()
    val brand = varchar("brand", 255).nullable()
    val model = varchar("model", 255).nullable()
    val attributeConstraints = jsonb(
        "attribute_constraints",
        json,
        ListSerializer(AttributeValueConstraint.serializer()),
    )
    val compatibilityRules = jsonb(
        "compatibility_rules",
        json,
        ListSerializer(CompatibilityRule.serializer()),
    )

    override val primaryKey = PrimaryKey(id)
}
