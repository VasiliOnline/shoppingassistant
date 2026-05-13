package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogGovernanceOfficialRefreshEndpoint
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceOfficialRefreshParserType
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceOfficialRefreshValueSeed
import com.example.shoppingassistant.server.db.DatabaseFactory
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.json.jsonb
import java.util.Locale

data class CatalogGovernanceOfficialPhoneEndpointOverlay(
    val id: Long? = null,
    val candidateId: Long? = null,
    val categoryCode: String,
    val sourceCode: String,
    val brandCode: String,
    val endpointCode: String,
    val parserType: CatalogGovernanceOfficialRefreshParserType,
    val sourceUri: String,
    val familyCode: String,
    val modelCode: String,
    val modelLabel: String,
    val releaseYear: Int? = null,
    val releaseDate: String? = null,
    val aliases: Map<String, List<String>> = emptyMap(),
    val fixedValues: List<CatalogGovernanceOfficialRefreshValueSeed> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toRefreshEndpoint(): CatalogGovernanceOfficialRefreshEndpoint =
        CatalogGovernanceOfficialRefreshEndpoint(
            endpointCode = endpointCode,
            parserType = parserType,
            sourceUri = sourceUri,
            familyCode = familyCode,
            modelCode = modelCode,
            modelLabel = modelLabel,
            releaseYear = releaseYear,
            releaseDate = releaseDate,
            aliases = aliases,
            fixedValues = fixedValues,
            metadata = metadata,
        )
}

interface CatalogGovernanceOfficialPhoneEndpointOverlayRepository {
    suspend fun listEndpoints(
        categoryCode: String,
        sourceCode: String,
    ): List<CatalogGovernanceOfficialPhoneEndpointOverlay>

    suspend fun upsertEndpoint(
        endpoint: CatalogGovernanceOfficialPhoneEndpointOverlay,
    ): CatalogGovernanceOfficialPhoneEndpointOverlay
}

object NoopCatalogGovernanceOfficialPhoneEndpointOverlayRepository :
    CatalogGovernanceOfficialPhoneEndpointOverlayRepository {
    override suspend fun listEndpoints(
        categoryCode: String,
        sourceCode: String,
    ): List<CatalogGovernanceOfficialPhoneEndpointOverlay> = emptyList()

    override suspend fun upsertEndpoint(
        endpoint: CatalogGovernanceOfficialPhoneEndpointOverlay,
    ): CatalogGovernanceOfficialPhoneEndpointOverlay = endpoint
}

class DatabaseCatalogGovernanceOfficialPhoneEndpointOverlayRepository :
    CatalogGovernanceOfficialPhoneEndpointOverlayRepository {

    override suspend fun listEndpoints(
        categoryCode: String,
        sourceCode: String,
    ): List<CatalogGovernanceOfficialPhoneEndpointOverlay> = DatabaseFactory.dbQuery {
        CatalogGovernanceOfficialPhoneEndpointOverlaysTable.selectAll()
            .apply {
                andWhere {
                    CatalogGovernanceOfficialPhoneEndpointOverlaysTable.categoryCode eq categoryCode.trim().uppercase(Locale.ROOT)
                }
                andWhere {
                    CatalogGovernanceOfficialPhoneEndpointOverlaysTable.sourceCode eq sourceCode.trim().uppercase(Locale.ROOT)
                }
            }
            .orderBy(CatalogGovernanceOfficialPhoneEndpointOverlaysTable.updatedAt to SortOrder.DESC)
            .map { row -> row.toOverlay() }
    }

    override suspend fun upsertEndpoint(
        endpoint: CatalogGovernanceOfficialPhoneEndpointOverlay,
    ): CatalogGovernanceOfficialPhoneEndpointOverlay = DatabaseFactory.dbQuery {
        val normalized = endpoint.normalize()
        val existing = CatalogGovernanceOfficialPhoneEndpointOverlaysTable.selectAll()
            .singleOrNull { row ->
                row[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.categoryCode] == normalized.categoryCode &&
                    row[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.sourceCode] == normalized.sourceCode &&
                    row[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.endpointCode] == normalized.endpointCode
            }
        if (existing == null) {
            CatalogGovernanceOfficialPhoneEndpointOverlaysTable.insert { stmt ->
                stmt[candidateId] = normalized.candidateId
                stmt[categoryCode] = normalized.categoryCode
                stmt[sourceCode] = normalized.sourceCode
                stmt[brandCode] = normalized.brandCode
                stmt[endpointCode] = normalized.endpointCode
                stmt[parserType] = normalized.parserType.name
                stmt[sourceUri] = normalized.sourceUri
                stmt[familyCode] = normalized.familyCode
                stmt[modelCode] = normalized.modelCode
                stmt[modelLabel] = normalized.modelLabel
                stmt[releaseYear] = normalized.releaseYear
                stmt[releaseDate] = normalized.releaseDate
                stmt[aliases] = normalized.aliases
                stmt[fixedValues] = normalized.fixedValues
                stmt[metadata] = normalized.metadata
                stmt[createdBy] = normalized.createdBy
                stmt[createdAt] = normalized.createdAt
                stmt[updatedAt] = normalized.updatedAt
            }.resultedValues!!.single().toOverlay()
        } else {
            CatalogGovernanceOfficialPhoneEndpointOverlaysTable.update({
                CatalogGovernanceOfficialPhoneEndpointOverlaysTable.id eq existing[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.id]
            }) { stmt ->
                stmt[candidateId] = normalized.candidateId
                stmt[categoryCode] = normalized.categoryCode
                stmt[sourceCode] = normalized.sourceCode
                stmt[brandCode] = normalized.brandCode
                stmt[endpointCode] = normalized.endpointCode
                stmt[parserType] = normalized.parserType.name
                stmt[sourceUri] = normalized.sourceUri
                stmt[familyCode] = normalized.familyCode
                stmt[modelCode] = normalized.modelCode
                stmt[modelLabel] = normalized.modelLabel
                stmt[releaseYear] = normalized.releaseYear
                stmt[releaseDate] = normalized.releaseDate
                stmt[aliases] = normalized.aliases
                stmt[fixedValues] = normalized.fixedValues
                stmt[metadata] = normalized.metadata
                stmt[createdBy] = normalized.createdBy
                stmt[createdAt] = normalized.createdAt
                stmt[updatedAt] = normalized.updatedAt
            }
            CatalogGovernanceOfficialPhoneEndpointOverlaysTable.selectAll()
                .single { row ->
                    row[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.id] ==
                        existing[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.id]
                }
                .toOverlay()
        }
    }
}

object CatalogGovernanceOfficialPhoneEndpointOverlaysTable :
    Table("catalog_governance_official_phone_endpoint_overlays") {
    private val json = Json { ignoreUnknownKeys = true }

    val id = long("id").autoIncrement()
    val candidateId = long("candidate_id")
        .references(CatalogPhoneModelEnrichmentCandidatesTable.id, onDelete = ReferenceOption.SET_NULL)
        .nullable()
    val categoryCode = varchar("category_code", 64).references(CategoriesTable.code, onDelete = ReferenceOption.CASCADE)
    val sourceCode = varchar("source_code", 64)
    val brandCode = varchar("brand_code", 64).references(CatalogGovernanceBrandsTable.code, onDelete = ReferenceOption.CASCADE)
    val endpointCode = varchar("endpoint_code", 96)
    val parserType = varchar("parser_type", 64)
    val sourceUri = text("source_uri")
    val familyCode = varchar("family_code", 64).references(CatalogGovernanceProductFamiliesTable.code, onDelete = ReferenceOption.CASCADE)
    val modelCode = varchar("model_code", 96).references(CatalogGovernanceModelsTable.code, onDelete = ReferenceOption.CASCADE)
    val modelLabel = varchar("model_label", 255)
    val releaseYear = integer("release_year").nullable()
    val releaseDate = varchar("release_date", 32).nullable()
    val aliases = jsonb(
        "aliases",
        json,
        MapSerializer(String.serializer(), ListSerializer(String.serializer())),
    )
    val fixedValues = jsonb(
        "fixed_values",
        json,
        ListSerializer(CatalogGovernanceOfficialRefreshValueSeed.serializer()),
    )
    val metadata = jsonb(
        "metadata",
        json,
        MapSerializer(String.serializer(), String.serializer()),
    )
    val createdBy = varchar("created_by", 128)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(
            "ux_catalog_governance_official_phone_endpoint_overlays_source_endpoint",
            categoryCode,
            sourceCode,
            endpointCode,
        )
        index(false, categoryCode, sourceCode)
        index(false, brandCode, modelCode)
        index(false, candidateId)
        index(false, updatedAt)
    }
}

internal suspend fun CatalogGovernanceOfficialPhoneEndpointOverlayRepository.listRefreshEndpoints(
    categoryCode: String,
    sourceCode: String,
): List<CatalogGovernanceOfficialRefreshEndpoint> =
    listEndpoints(categoryCode = categoryCode, sourceCode = sourceCode)
        .map { overlay -> overlay.toRefreshEndpoint() }

private fun CatalogGovernanceOfficialPhoneEndpointOverlay.normalize():
    CatalogGovernanceOfficialPhoneEndpointOverlay =
    copy(
        categoryCode = categoryCode.trim().uppercase(Locale.ROOT),
        sourceCode = sourceCode.trim().uppercase(Locale.ROOT),
        brandCode = brandCode.trim().uppercase(Locale.ROOT),
        endpointCode = endpointCode.trim().uppercase(Locale.ROOT),
        sourceUri = sourceUri.trim(),
        familyCode = familyCode.trim().uppercase(Locale.ROOT),
        modelCode = modelCode.trim().uppercase(Locale.ROOT),
        modelLabel = modelLabel.trim(),
        releaseDate = releaseDate?.trim()?.ifEmpty { null },
        aliases = aliases.entries
            .mapNotNull { (locale, values) ->
                val normalizedLocale = locale.trim().lowercase(Locale.ROOT)
                    .takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                val normalizedValues = values
                    .mapNotNull { value -> value.trim().takeIf { it.isNotEmpty() } }
                    .distinct()
                if (normalizedValues.isEmpty()) return@mapNotNull null
                normalizedLocale to normalizedValues
            }
            .toMap(LinkedHashMap()),
        fixedValues = fixedValues
            .mapNotNull { value ->
                val attributeCode = value.attributeCode.trim().lowercase(Locale.ROOT)
                val rawValue = value.rawValue.trim()
                if (attributeCode.isBlank() || rawValue.isBlank()) {
                    null
                } else {
                    CatalogGovernanceOfficialRefreshValueSeed(
                        attributeCode = attributeCode,
                        rawValue = rawValue,
                        aliases = value.aliases,
                    )
                }
            },
        metadata = metadata
            .mapKeys { (key, _) -> key.trim() }
            .filterKeys { key -> key.isNotEmpty() }
            .mapValues { (_, value) -> value.trim() }
            .filterValues { value -> value.isNotEmpty() },
        createdBy = createdBy.trim().ifEmpty { "system" },
    )

private fun ResultRow.toOverlay(): CatalogGovernanceOfficialPhoneEndpointOverlay =
    CatalogGovernanceOfficialPhoneEndpointOverlay(
        id = this[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.id],
        candidateId = this[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.candidateId],
        categoryCode = this[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.categoryCode],
        sourceCode = this[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.sourceCode],
        brandCode = this[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.brandCode],
        endpointCode = this[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.endpointCode],
        parserType = CatalogGovernanceOfficialRefreshParserType.valueOf(
            this[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.parserType],
        ),
        sourceUri = this[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.sourceUri],
        familyCode = this[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.familyCode],
        modelCode = this[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.modelCode],
        modelLabel = this[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.modelLabel],
        releaseYear = this[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.releaseYear],
        releaseDate = this[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.releaseDate],
        aliases = this[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.aliases],
        fixedValues = this[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.fixedValues],
        metadata = this[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.metadata],
        createdBy = this[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.createdBy],
        createdAt = this[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.createdAt],
        updatedAt = this[CatalogGovernanceOfficialPhoneEndpointOverlaysTable.updatedAt],
    )
