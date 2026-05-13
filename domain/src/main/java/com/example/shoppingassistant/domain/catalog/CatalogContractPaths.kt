package com.example.shoppingassistant.domain.catalog

import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
internal data class CatalogContractPathsDocument(
    val schemaVersion: String,
    val stage21Base: String,
    val stage20Base: String,
    val stage22Base: String,
    val stage30Base: String,
    val stage40Base: String,
)

internal object CatalogContractPaths {
    const val RESOURCE_PATH: String = "taxonomy/catalog_contract_paths.json"

    private val document: CatalogContractPathsDocument by lazy { loadAndValidate() }

    val schemaVersion: String
        get() = document.schemaVersion
    val stage20Base: String
        get() = document.stage20Base
    val stage21Base: String
        get() = document.stage21Base
    val stage22Base: String
        get() = document.stage22Base
    val stage30Base: String
        get() = document.stage30Base
    val stage40Base: String
        get() = document.stage40Base

    val stage22RegistryBase: String
        get() = "$stage22Base/_registry"
    val stage22GlobalBase: String
        get() = "$stage22Base/_global"

    fun stage22DefaultPackageBase(l0Code: String): String {
        val normalizedL0 = l0Code.trim().uppercase(Locale.ROOT)
        require(normalizedL0.isNotEmpty()) { "l0Code must not be blank for stage22 package base." }
        return "$stage22Base/$normalizedL0"
    }

    private fun loadAndValidate(): CatalogContractPathsDocument {
        val raw = CatalogSeedResourceReader.readJson(
            resourcePath = RESOURCE_PATH,
            deserializer = CatalogContractPathsDocument.serializer(),
        )
        val normalized = raw.copy(
            schemaVersion = raw.schemaVersion.trim(),
            stage21Base = normalizePath(raw.stage21Base),
            stage20Base = normalizePath(raw.stage20Base),
            stage22Base = normalizePath(raw.stage22Base),
            stage30Base = normalizePath(raw.stage30Base),
            stage40Base = normalizePath(raw.stage40Base),
        )

        val semver = Regex("""^\d+\.\d+\.\d+$""")
        check(semver.matches(normalized.schemaVersion)) {
            "catalog_contract_paths.schemaVersion must be semantic version (x.y.z), got '${normalized.schemaVersion}'."
        }

        listOf(
            "stage21Base" to normalized.stage21Base,
            "stage20Base" to normalized.stage20Base,
            "stage22Base" to normalized.stage22Base,
            "stage30Base" to normalized.stage30Base,
            "stage40Base" to normalized.stage40Base,
        ).forEach { (field, value) ->
            check(value.startsWith("taxonomy/")) {
                "catalog_contract_paths.$field must start with 'taxonomy/', got '$value'."
            }
        }

        check(CatalogSeedResourceReader.resourceExists("${normalized.stage20Base}/categories.json")) {
            "catalog_contract_paths.stage20Base missing categories.json under '${normalized.stage20Base}'."
        }
        Stage21ContractCatalog.requiredResourcePaths(normalized.stage21Base).forEach { path ->
            check(CatalogSeedResourceReader.resourceExists(path)) {
                "catalog_contract_paths.stage21Base missing required stage21 resource '$path'."
            }
        }
        check(CatalogSeedResourceReader.resourceExists("${normalized.stage22Base}/_registry/registry_meta.json")) {
            "catalog_contract_paths.stage22Base missing _registry/registry_meta.json under '${normalized.stage22Base}'."
        }
        check(CatalogSeedResourceReader.resourceExists("${normalized.stage30Base}/facet_definitions.json")) {
            "catalog_contract_paths.stage30Base missing facet_definitions.json under '${normalized.stage30Base}'."
        }
        check(CatalogSeedResourceReader.resourceExists("${normalized.stage40Base}/immutable_attribute_schema.json")) {
            "catalog_contract_paths.stage40Base missing immutable_attribute_schema.json under '${normalized.stage40Base}'."
        }

        return normalized
    }

    private fun normalizePath(raw: String): String =
        raw.trim().trimEnd('/').also { path ->
            require(path.isNotEmpty()) { "catalog_contract_paths contains blank base path." }
        }
}
