package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.constraints.AttributeValueConstraint
import com.example.shoppingassistant.domain.catalog.constraints.CompatibilityRule
import com.example.shoppingassistant.domain.facet.FacetPresetRule
import com.example.shoppingassistant.domain.i18n.LocalizedText
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.json.jsonb

private val json = Json { ignoreUnknownKeys = true }

object CategoriesTable : Table("categories") {
    val code = varchar("code", 64)
    val segment = varchar("segment", 16)
    val status = varchar("status", 16).default("ACTIVE")
    val titleLocalized = jsonb("title_localized", json, LocalizedText.serializer()).nullable()
    val titleRu = varchar("title_ru", 255).nullable()
    val titleEn = varchar("title_en", 255).nullable()
    val parentCode = varchar("parent_code", 64).nullable()
    val description = text("description").nullable()
    val replacementCode = varchar("replacement_code", 64).nullable()

    override val primaryKey = PrimaryKey(code)
}

object CategoryAliasesTable : Table("category_aliases") {
    val alias = varchar("alias", 255)
    val categoryCode = varchar("category_code", 64).references(CategoriesTable.code, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(alias, categoryCode)
}

object BrowseNodesTable : Table("browse_nodes") {
    val browseCode = varchar("browse_code", 64)
    val parentBrowseCode = varchar("parent_browse_code", 64).nullable()
    val nodeKind = varchar("node_kind", 16)
    val titleKey = varchar("title_key", 128).nullable()
    val titleLocalized = jsonb("title_localized", json, LocalizedText.serializer()).nullable()
    val titleRu = text("title_ru")
    val titleEn = text("title_en").nullable()
    val targetCategoryCode = varchar("target_category_code", 64).nullable()
    val targetType = varchar("target_type", 32).nullable()
    val order = integer("order_index").default(0)
    val availabilityScope = varchar("availability_scope", 32).default("ALL")
    val iconKey = varchar("icon_key", 128).nullable()
    val analyticsKey = varchar("analytics_key", 128).nullable()
    val searchKeywordsRu = jsonb("search_keywords_ru", json, ListSerializer(String.serializer()))
    val status = varchar("status", 16).default("ACTIVE")
    val tags = jsonb("tags", json, ListSerializer(String.serializer()))
    val notes = text("notes").nullable()

    override val primaryKey = PrimaryKey(browseCode)
}

object AliasEntriesTable : Table("alias_entries") {
    val locale = varchar("locale", 16)
    val term = varchar("term", 255)
    val normalizedTerm = varchar("normalized_term", 255)
    val kind = varchar("kind", 32)
    val targetCode = varchar("target_code", 64)
    val weight = integer("weight")
    val matchKind = varchar("match_kind", 16)
    val isBlocked = bool("is_blocked").default(false)
    val aliasSource = varchar("source", 16)
    val notes = text("notes").nullable()

    override val primaryKey = PrimaryKey(locale, normalizedTerm, kind, targetCode)
}

object GoogleTaxonomyMappingsTable : Table("google_taxonomy_mappings") {
    val canonicalCode = varchar("canonical_code", 64).references(CategoriesTable.code, onDelete = ReferenceOption.CASCADE)
    val mappingType = varchar("mapping_type", 16)
    val googleIds = jsonb("google_ids", json, ListSerializer(Long.serializer()))
    val googlePaths = jsonb("google_paths", json, ListSerializer(String.serializer()))
    val notes = text("notes").nullable()

    override val primaryKey = PrimaryKey(canonicalCode)
}

object AttributeDefsTable : Table("attribute_defs") {
    val code = varchar("code", 64)
    val title = varchar("title", 255)
    val dataType = varchar("data_type", 32)
    val requiredForSearch = bool("required_for_search").default(false)
    val requiredForOffer = bool("required_for_offer").default(false)
    val requiredForExpress = bool("required_for_express").default(false)
    val requiredBy = varchar("required_by", 10).nullable()
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
    val effectiveFrom = varchar("effective_from", 10).nullable()
    val effectiveTo = varchar("effective_to", 10).nullable()
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

object FacetDefinitionsTable : Table("facet_definitions") {
    val facetKey = varchar("facet_key", 64)
    val titleLocalized = jsonb("title_localized", json, LocalizedText.serializer()).nullable()
    val titleRu = varchar("title_ru", 255)
    val titleEn = varchar("title_en", 255).nullable()
    val valueType = varchar("value_type", 32)
    val valueSource = varchar("source", 32)
    val effectiveFrom = varchar("effective_from", 10).nullable()
    val effectiveTo = varchar("effective_to", 10).nullable()
    val appliesToCategoryCodes = jsonb(
        "applies_to_category_codes",
        json,
        ListSerializer(String.serializer()),
    )
    val uiOrder = integer("ui_order").default(0)
    val uiPinned = bool("ui_pinned").default(false)
    val uiHidden = bool("ui_hidden").default(false)
    val uiFormat = varchar("ui_format", 64).nullable()

    override val primaryKey = PrimaryKey(facetKey)
}

object FacetPresetsTable : Table("facet_presets") {
    val presetCode = varchar("preset_code", 64)
    val categoryCode = varchar("category_code", 64).references(CategoriesTable.code, onDelete = ReferenceOption.CASCADE)
    val titleLocalized = jsonb("title_localized", json, LocalizedText.serializer()).nullable()
    val titleRu = varchar("title_ru", 255)
    val titleEn = varchar("title_en", 255).nullable()
    val order = integer("order_index").default(0)
    val effectiveFrom = varchar("effective_from", 10).nullable()
    val effectiveTo = varchar("effective_to", 10).nullable()
    val rules = jsonb(
        "rules",
        json,
        ListSerializer(FacetPresetRule.serializer()),
    )
    val notes = text("notes").nullable()

    override val primaryKey = PrimaryKey(presetCode)
}

object FacetCollectionsTable : Table("facet_collections") {
    val collectionCode = varchar("collection_code", 64)
    val categoryCode = varchar("category_code", 64).references(CategoriesTable.code, onDelete = ReferenceOption.CASCADE)
    val titleLocalized = jsonb("title_localized", json, LocalizedText.serializer()).nullable()
    val titleRu = varchar("title_ru", 255)
    val titleEn = varchar("title_en", 255).nullable()
    val browseCode = varchar("browse_code", 64).nullable()
    val presetCode = varchar("preset_code", 64).nullable()
    val order = integer("order_index").default(0)
    val tags = jsonb("tags", json, ListSerializer(String.serializer()))
    val notes = text("notes").nullable()

    override val primaryKey = PrimaryKey(collectionCode)
}

object CatalogStage4ContractMetaTable : Table("catalog_stage4_contract_meta") {
    val stage = varchar("stage", 8)
    val schemaVersion = varchar("schema_version", 16)
    val stage22DataVersion = varchar("stage22_data_version", 32)
    val stage22SchemaVersion = varchar("stage22_schema_version", 16)
    val stage22GeneratedAt = varchar("stage22_generated_at", 64)
    val stage3Version = varchar("stage3_version", 16)
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(stage)
}

object CatalogStage4ImmutableAttributesTable : Table("catalog_stage4_immutable_attributes") {
    val attributeCode = varchar("attribute_code", 64)
    val valueType = varchar("value_type", 16)
    val valueSetType = varchar("value_set_type", 16)
    val unit = varchar("unit", 32).nullable()
    val isIdentity = bool("is_identity")
    val isFacet = bool("is_facet")
    val normalization = varchar("normalization", 128).nullable()
    val dictionaryRequired = bool("dictionary_required")
    val immutableFingerprint = varchar("immutable_fingerprint", 128)

    override val primaryKey = PrimaryKey(attributeCode)
}

object CatalogStage4NormalizationRulesTable : Table("catalog_stage4_normalization_rules") {
    val attributeCode = varchar("attribute_code", 64)
    val normalization = varchar("normalization", 128)
    val valueSetType = varchar("value_set_type", 16)
    val dictionaryBacked = bool("dictionary_backed")
    val acceptsFreeText = bool("accepts_free_text")
    val canonicalSource = varchar("canonical_source", 255)
    val dedupTokenMode = varchar("dedup_token_mode", 32)

    override val primaryKey = PrimaryKey(attributeCode)
}

object CatalogStage4DedupTemplatesTable : Table("catalog_stage4_dedup_templates") {
    val entity = varchar("entity", 64)
    val templateExpr = varchar("template_expr", 255)
    val fieldNames = jsonb("fields", json, ListSerializer(String.serializer()))
    val description = text("description")

    override val primaryKey = PrimaryKey(entity)
}

object CatalogStage4TypedConstraintsTable : Table("catalog_stage4_typed_constraints") {
    val attributeCode = varchar("attribute_code", 64)
    val valueType = varchar("value_type", 16)
    val enumOnly = bool("enum_only").default(false)
    val expectedUnit = varchar("expected_unit", 32).nullable()
    val regexPattern = varchar("regex_pattern", 255).nullable()
    val minValue = double("min_value").nullable()
    val maxValue = double("max_value").nullable()
    val requiredIf = jsonb(
        "required_if",
        json,
        ListSerializer(com.example.shoppingassistant.domain.catalog.Stage40RequiredIfRule.serializer()),
    )
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(attributeCode)
}

object CatalogStage4ExecutionMetricsTable : Table("catalog_stage4_execution_metrics") {
    val id = long("id").autoIncrement()
    val metricDate = date("metric_date")
    val stream = varchar("stream", 64)
    val normalizedCount = integer("normalized_count")
    val droppedCount = integer("dropped_count")
    val logicalDedupCount = integer("logical_dedup_count")
    val unknownAttributeCount = integer("unknown_attribute_count")
    val reasonCodes = jsonb("reason_codes", json, ListSerializer(String.serializer()))
    val metadata = jsonb(
        "metadata",
        json,
        MapSerializer(String.serializer(), String.serializer()),
    )
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, metricDate, stream)
        index(false, createdAt)
    }
}

object CatalogReadinessSnapshotsTable : Table("catalog_readiness_snapshots") {
    val snapshotDate = date("snapshot_date")
    val categoryCode = varchar("category_code", 64).references(CategoriesTable.code, onDelete = ReferenceOption.CASCADE)
    val readiness = varchar("readiness", 16)
    val editorialReadiness = varchar("editorial_readiness", 16)
    val operationalReadiness = varchar("operational_readiness", 16)
    val completenessGatePassed = bool("completeness_gate_passed").default(false)
    val blockingIssues = jsonb("blocking_issues", json, ListSerializer(String.serializer()))
    val editorialBlockingIssues = jsonb("editorial_blocking_issues", json, ListSerializer(String.serializer()))
    val operationalBlockingIssues = jsonb("operational_blocking_issues", json, ListSerializer(String.serializer()))
    val operationalSampleCount = integer("operational_sample_count").default(0)
    val operationalDroppedRate = double("operational_dropped_rate").default(0.0)
    val operationalUnknownAttributeRate = double("operational_unknown_attribute_rate").default(0.0)
    val operationalRequiredMissingRate = double("operational_required_missing_rate").default(0.0)
    val operationalLowConfidenceRate = double("operational_low_confidence_rate").default(0.0)
    val dataVersion = varchar("data_version", 64)
    val schemaVersion = varchar("schema_version", 16)
    val capturedAt = long("captured_at")

    override val primaryKey = PrimaryKey(snapshotDate, categoryCode)

    init {
        index(false, categoryCode, snapshotDate)
        index(false, dataVersion, snapshotDate)
        index(false, capturedAt)
    }
}

object CatalogReadinessAutomationLeasesTable : Table("catalog_readiness_automation_leases") {
    val leaseKey = varchar("lease_key", 128)
    val ownerId = varchar("owner_id", 128)
    val acquiredAt = long("acquired_at")
    val lastHeartbeatAt = long("last_heartbeat_at").default(0L)
    val leaseExpiresAt = long("lease_expires_at")

    override val primaryKey = PrimaryKey(leaseKey)

    init {
        index(false, leaseExpiresAt)
    }
}

object CatalogGovernanceSourcesTable : Table("catalog_governance_sources") {
    val id = long("id").autoIncrement()
    val sourceCode = varchar("source_code", 64)
    val externalRef = varchar("external_ref", 255).nullable()
    val displayName = varchar("display_name", 255)
    val tier = varchar("tier", 32)
    val defaultLocale = varchar("default_locale", 16).nullable()
    val marketCode = varchar("market_code", 16).nullable()
    val sourceVersion = varchar("source_version", 64).nullable()
    val sourceUri = text("source_uri").nullable()
    val checksum = varchar("checksum", 128).nullable()
    val metadata = jsonb(
        "metadata",
        json,
        MapSerializer(String.serializer(), String.serializer()),
    )
    val capturedAt = long("captured_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, sourceCode, capturedAt)
        index(false, sourceCode, externalRef)
        index(false, defaultLocale, marketCode)
    }
}

object CatalogGovernanceSourceRegistryTable : Table("catalog_governance_source_registry") {
    val registryCode = varchar("registry_code", 96)
    val categoryCode = varchar("category_code", 64).references(CategoriesTable.code, onDelete = ReferenceOption.CASCADE)
    val connectorType = varchar("connector_type", 48)
    val sourceCode = varchar("source_code", 64)
    val externalRef = varchar("external_ref", 255).nullable()
    val displayName = varchar("display_name", 255)
    val tier = varchar("tier", 32)
    val defaultLocale = varchar("default_locale", 16).nullable()
    val marketCode = varchar("market_code", 16).nullable()
    val sourceUri = text("source_uri").nullable()
    val enabled = bool("enabled").default(true)
    val autoPublish = bool("auto_publish").default(true)
    val metadata = jsonb(
        "metadata",
        json,
        MapSerializer(String.serializer(), String.serializer()),
    )
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(registryCode)

    init {
        index(false, categoryCode, enabled)
        index(false, sourceCode)
    }
}

object CatalogGovernanceRefreshRunsTable : Table("catalog_governance_refresh_runs") {
    val id = long("id").autoIncrement()
    val registryCode = varchar("registry_code", 96).references(CatalogGovernanceSourceRegistryTable.registryCode, onDelete = ReferenceOption.CASCADE)
    val categoryCode = varchar("category_code", 64).references(CategoriesTable.code, onDelete = ReferenceOption.CASCADE)
    val trigger = varchar("trigger_name", 48)
    val status = varchar("status", 24)
    val sourceSnapshotId = long("source_snapshot_id").references(CatalogGovernanceSourcesTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val brandsSynced = integer("brands_synced").default(0)
    val familiesSynced = integer("families_synced").default(0)
    val modelsSynced = integer("models_synced").default(0)
    val canonicalValuesSynced = integer("canonical_values_synced").default(0)
    val aliasesSynced = integer("aliases_synced").default(0)
    val candidatesDetected = integer("candidates_detected").default(0)
    val reviewQueueSize = integer("review_queue_size").default(0)
    val publishStatus = varchar("publish_status", 24).nullable()
    val errorMessage = text("error_message").nullable()
    val metadata = jsonb(
        "metadata",
        json,
        MapSerializer(String.serializer(), String.serializer()),
    )
    val startedAt = long("started_at")
    val finishedAt = long("finished_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, categoryCode, startedAt)
        index(false, registryCode, startedAt)
        index(false, status, startedAt)
    }
}

object CatalogGovernancePublishEventsTable : Table("catalog_governance_publish_events") {
    val id = long("id").autoIncrement()
    val refreshRunId = long("refresh_run_id").references(CatalogGovernanceRefreshRunsTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val categoryCode = varchar("category_code", 64).references(CategoriesTable.code, onDelete = ReferenceOption.SET_NULL).nullable()
    val eventType = varchar("event_type", 48)
    val artifactType = varchar("artifact_type", 48)
    val entityRef = varchar("entity_ref", 255).nullable()
    val status = varchar("status", 24)
    val details = text("details").nullable()
    val metadata = jsonb(
        "metadata",
        json,
        MapSerializer(String.serializer(), String.serializer()),
    )
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, categoryCode, createdAt)
        index(false, refreshRunId, createdAt)
        index(false, eventType, createdAt)
    }
}

object CatalogGovernanceBrandsTable : Table("catalog_governance_brands") {
    val code = varchar("code", 64)
    val labels = jsonb("labels", json, LocalizedText.serializer()).nullable()
    val normalizedKey = varchar("normalized_key", 255)
    val status = varchar("status", 16).default("ACTIVE")
    val primaryCategoryCode = varchar("primary_category_code", 64).references(CategoriesTable.code, onDelete = ReferenceOption.SET_NULL).nullable()
    val primarySegment = varchar("primary_segment", 16).nullable()
    val metadata = jsonb(
        "metadata",
        json,
        MapSerializer(String.serializer(), String.serializer()),
    )
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(code)

    init {
        uniqueIndex("ux_catalog_governance_brands_normalized_key", normalizedKey)
        index(false, primaryCategoryCode)
    }
}

object CatalogGovernanceProductFamiliesTable : Table("catalog_governance_product_families") {
    val code = varchar("code", 64)
    val brandCode = varchar("brand_code", 64).references(CatalogGovernanceBrandsTable.code, onDelete = ReferenceOption.CASCADE)
    val labels = jsonb("labels", json, LocalizedText.serializer()).nullable()
    val normalizedKey = varchar("normalized_key", 255)
    val prettyModelPrefix = varchar("pretty_model_prefix", 255)
    val variantTokens = jsonb("variant_tokens", json, ListSerializer(String.serializer()))
    val accessoryBlockers = jsonb("accessory_blockers", json, ListSerializer(String.serializer()))
    val defaultCategoryCode = varchar("default_category_code", 64).references(CategoriesTable.code, onDelete = ReferenceOption.SET_NULL).nullable()
    val status = varchar("status", 16).default("ACTIVE")
    val metadata = jsonb(
        "metadata",
        json,
        MapSerializer(String.serializer(), String.serializer()),
    )
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(code)

    init {
        uniqueIndex("ux_catalog_governance_product_families_brand_normalized", brandCode, normalizedKey)
        index(false, defaultCategoryCode)
    }
}

object CatalogGovernanceModelsTable : Table("catalog_governance_models") {
    val code = varchar("code", 96)
    val brandCode = varchar("brand_code", 64).references(CatalogGovernanceBrandsTable.code, onDelete = ReferenceOption.CASCADE)
    val familyCode = varchar("family_code", 64).references(CatalogGovernanceProductFamiliesTable.code, onDelete = ReferenceOption.SET_NULL).nullable()
    val labels = jsonb("labels", json, LocalizedText.serializer()).nullable()
    val normalizedKey = varchar("normalized_key", 255)
    val defaultCategoryCode = varchar("default_category_code", 64).references(CategoriesTable.code, onDelete = ReferenceOption.SET_NULL).nullable()
    val releaseYear = integer("release_year").nullable()
    val status = varchar("status", 16).default("ACTIVE")
    val metadata = jsonb(
        "metadata",
        json,
        MapSerializer(String.serializer(), String.serializer()),
    )
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(code)

    init {
        uniqueIndex("ux_catalog_governance_models_brand_normalized", brandCode, normalizedKey)
        index(false, familyCode)
        index(false, defaultCategoryCode)
    }
}

object CatalogGovernanceValueCanonTable : Table("catalog_governance_value_canon") {
    val id = long("id").autoIncrement()
    val attributeCode = varchar("attribute_code", 64).references(AttributeDefsTable.code, onDelete = ReferenceOption.CASCADE)
    val canonicalCode = varchar("canonical_code", 64)
    val canonicalValue = varchar("canonical_value", 255)
    val labels = jsonb("labels", json, LocalizedText.serializer()).nullable()
    val canonicalLocale = varchar("canonical_locale", 16).nullable()
    val normalizedValue = varchar("normalized_value", 255)
    val categoryCode = varchar("category_code", 64).references(CategoriesTable.code, onDelete = ReferenceOption.SET_NULL).nullable()
    val brandCode = varchar("brand_code", 64).references(CatalogGovernanceBrandsTable.code, onDelete = ReferenceOption.SET_NULL).nullable()
    val familyCode = varchar("family_code", 64).references(CatalogGovernanceProductFamiliesTable.code, onDelete = ReferenceOption.SET_NULL).nullable()
    val modelCode = varchar("model_code", 96).references(CatalogGovernanceModelsTable.code, onDelete = ReferenceOption.SET_NULL).nullable()
    val status = varchar("status", 16).default("ACTIVE")
    val metadata = jsonb(
        "metadata",
        json,
        MapSerializer(String.serializer(), String.serializer()),
    )
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, attributeCode, canonicalCode)
        index(false, attributeCode, normalizedValue)
        index(false, attributeCode, canonicalLocale)
        index(false, categoryCode, brandCode, familyCode, modelCode)
    }
}

object CatalogGovernanceAliasesTable : Table("catalog_governance_aliases") {
    val id = long("id").autoIncrement()
    val locale = varchar("locale", 16)
    val marketCode = varchar("market_code", 16).nullable()
    val aliasText = varchar("alias_text", 255)
    val normalizedAlias = varchar("normalized_alias", 255)
    val targetKind = varchar("target_kind", 32)
    val targetCode = varchar("target_code", 128)
    val attributeCode = varchar("attribute_code", 64).references(AttributeDefsTable.code, onDelete = ReferenceOption.SET_NULL).nullable()
    val categoryCode = varchar("category_code", 64).references(CategoriesTable.code, onDelete = ReferenceOption.SET_NULL).nullable()
    val brandCode = varchar("brand_code", 64).references(CatalogGovernanceBrandsTable.code, onDelete = ReferenceOption.SET_NULL).nullable()
    val familyCode = varchar("family_code", 64).references(CatalogGovernanceProductFamiliesTable.code, onDelete = ReferenceOption.SET_NULL).nullable()
    val modelCode = varchar("model_code", 96).references(CatalogGovernanceModelsTable.code, onDelete = ReferenceOption.SET_NULL).nullable()
    val sourceSnapshotId = long("source_snapshot_id").references(CatalogGovernanceSourcesTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val confidence = double("confidence").default(1.0)
    val status = varchar("status", 16).default("ACTIVE")
    val metadata = jsonb(
        "metadata",
        json,
        MapSerializer(String.serializer(), String.serializer()),
    )
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, locale, marketCode, normalizedAlias)
        index(false, targetKind, targetCode)
        index(false, attributeCode, categoryCode, brandCode, familyCode, modelCode)
    }
}

object CatalogGovernanceValueObservationsTable : Table("catalog_governance_value_observations") {
    val id = long("id").autoIncrement()
    val attributeCode = varchar("attribute_code", 64).references(AttributeDefsTable.code, onDelete = ReferenceOption.CASCADE)
    val locale = varchar("locale", 16).nullable()
    val marketCode = varchar("market_code", 16).nullable()
    val rawValue = varchar("raw_value", 255)
    val normalizedValue = varchar("normalized_value", 255)
    val categoryCode = varchar("category_code", 64).references(CategoriesTable.code, onDelete = ReferenceOption.SET_NULL).nullable()
    val brandCode = varchar("brand_code", 64).references(CatalogGovernanceBrandsTable.code, onDelete = ReferenceOption.SET_NULL).nullable()
    val familyCode = varchar("family_code", 64).references(CatalogGovernanceProductFamiliesTable.code, onDelete = ReferenceOption.SET_NULL).nullable()
    val modelCode = varchar("model_code", 96).references(CatalogGovernanceModelsTable.code, onDelete = ReferenceOption.SET_NULL).nullable()
    val sourceSnapshotId = long("source_snapshot_id").references(CatalogGovernanceSourcesTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val observedCount = integer("observed_count").default(1)
    val sampleRefs = jsonb("sample_refs", json, ListSerializer(String.serializer()))
    val metadata = jsonb(
        "metadata",
        json,
        MapSerializer(String.serializer(), String.serializer()),
    )
    val firstSeenAt = long("first_seen_at")
    val lastSeenAt = long("last_seen_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, attributeCode, locale, marketCode, normalizedValue)
        index(false, categoryCode, brandCode, familyCode, modelCode)
        index(false, sourceSnapshotId)
    }
}

object CatalogGovernanceValueCandidatesTable : Table("catalog_governance_value_candidates") {
    val id = long("id").autoIncrement()
    val attributeCode = varchar("attribute_code", 64).references(AttributeDefsTable.code, onDelete = ReferenceOption.CASCADE)
    val locale = varchar("locale", 16).nullable()
    val marketCode = varchar("market_code", 16).nullable()
    val rawValue = varchar("raw_value", 255)
    val normalizedValue = varchar("normalized_value", 255)
    val proposedCanonicalCode = varchar("proposed_canonical_code", 64).nullable()
    val proposedCanonicalValue = varchar("proposed_canonical_value", 255).nullable()
    val proposedLabels = jsonb("proposed_labels", json, LocalizedText.serializer()).nullable()
    val proposedCanonicalLocale = varchar("proposed_canonical_locale", 16).nullable()
    val categoryCode = varchar("category_code", 64).references(CategoriesTable.code, onDelete = ReferenceOption.SET_NULL).nullable()
    val brandCode = varchar("brand_code", 64).references(CatalogGovernanceBrandsTable.code, onDelete = ReferenceOption.SET_NULL).nullable()
    val familyCode = varchar("family_code", 64).references(CatalogGovernanceProductFamiliesTable.code, onDelete = ReferenceOption.SET_NULL).nullable()
    val modelCode = varchar("model_code", 96).references(CatalogGovernanceModelsTable.code, onDelete = ReferenceOption.SET_NULL).nullable()
    val sourceSnapshotId = long("source_snapshot_id").references(CatalogGovernanceSourcesTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val candidateStatus = varchar("candidate_status", 24).default("NEW")
    val autoConfidence = double("auto_confidence").default(0.0)
    val evidenceCount = integer("evidence_count").default(0)
    val metadata = jsonb(
        "metadata",
        json,
        MapSerializer(String.serializer(), String.serializer()),
    )
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, attributeCode, candidateStatus)
        index(false, attributeCode, locale, marketCode, normalizedValue)
        index(false, categoryCode, brandCode, familyCode, modelCode)
    }
}

object CatalogGovernanceDecisionsTable : Table("catalog_governance_decisions") {
    val id = long("id").autoIncrement()
    val entityKind = varchar("entity_kind", 32)
    val entityRef = varchar("entity_ref", 255)
    val action = varchar("action", 32)
    val reasonCode = varchar("reason_code", 64)
    val actor = varchar("actor", 128)
    val payload = jsonb(
        "payload",
        json,
        MapSerializer(String.serializer(), String.serializer()),
    )
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, entityKind, entityRef)
        index(false, createdAt)
    }
}

object CatalogGovernanceReportsTable : Table("catalog_governance_reports") {
    val reportType = varchar("report_type", 32)
    val reportDate = date("report_date")
    val generatedAt = varchar("generated_at", 64)
    val windowStartDate = date("window_start_date")
    val windowEndDate = date("window_end_date")
    val totalCategories = integer("total_categories")
    val readyCategories = integer("ready_categories")
    val betaCategories = integer("beta_categories")
    val internalCategories = integer("internal_categories")
    val categoriesWithBlockingIssues = jsonb("categories_with_blocking_issues", json, ListSerializer(String.serializer()))
    val dataVersion = varchar("data_version", 64)
    val schemaVersion = varchar("schema_version", 16)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(reportType, reportDate)

    init {
        index(false, reportDate, reportType)
        index(false, createdAt)
    }
}

object CatalogGovernanceHookDeliveriesTable : Table("catalog_governance_hook_deliveries") {
    val id = long("id").autoIncrement()
    val hookCode = varchar("hook_code", 128)
    val trigger = varchar("trigger_name", 64)
    val transport = varchar("transport", 32)
    val target = text("target").nullable()
    val status = varchar("status", 64)
    val attemptedAt = varchar("attempted_at", 64)
    val responseStatus = integer("response_status").nullable()
    val details = text("details").nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, hookCode, trigger)
        index(false, attemptedAt)
    }
}
