package com.example.shoppingassistant.domain.catalog

import java.security.MessageDigest
import java.util.Locale

object CatalogDataVersion {
    val current: String by lazy {
        val stage22DataVersion = Stage22RegistryLoader.loadSnapshot().meta.dataVersion
            .trim()
            .ifEmpty { error("Stage 2.2 registry dataVersion must not be blank.") }
        "$stage22DataVersion+${catalogFingerprint(includeStage21 = true)}"
    }

    internal fun catalogFingerprint(includeStage21: Boolean = true): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun update(text: String) {
            digest.update(text.toByteArray(Charsets.UTF_8))
        }

        fun addResource(path: String, required: Boolean = true) {
            if (!CatalogSeedResourceReader.resourceExists(path)) {
                if (required) error("Catalog fingerprint resource missing: $path")
                update("$path\n<missing>\n")
                return
            }
            update(path)
            update("\n")
            update(CatalogSeedResourceReader.readText(path))
            update("\n")
        }

        addResource(CatalogContractPaths.RESOURCE_PATH)

        addResource("${CatalogContractPaths.stage20Base}/categories.json")
        addResource("${CatalogContractPaths.stage20Base}/category_aliases.json")
        addResource("${CatalogContractPaths.stage20Base}/browse_nodes.json")
        addResource("${CatalogContractPaths.stage20Base}/alias_entries.json")
        addResource("${CatalogContractPaths.stage20Base}/google_taxonomy_mappings.json")

        if (includeStage21) {
            Stage21ContractCatalog.requiredResourcePaths().forEach { path ->
                addResource(path)
            }
        }

        addResource("${CatalogContractPaths.stage22RegistryBase}/registry_meta.json")
        addResource("${CatalogContractPaths.stage22RegistryBase}/data_release_policy.json")
        addResource("${CatalogContractPaths.stage22RegistryBase}/readiness_governance_policy.json")
        addResource("${CatalogContractPaths.stage22RegistryBase}/runtime_compatibility_policy.json")
        addResource("${CatalogContractPaths.stage22RegistryBase}/system_attributes.json")
        addResource("${CatalogContractPaths.stage22RegistryBase}/catalog_runtime_contract.schema.json")
        addResource("${CatalogContractPaths.stage22RegistryBase}/attributes.json")
        addResource("${CatalogContractPaths.stage22RegistryBase}/value_dictionaries.json")
        addResource("${CatalogContractPaths.stage22RegistryBase}/governance_curated_top_categories.json")
        addResource("${CatalogContractPaths.stage22RegistryBase}/package_descriptors.json")
        addResource("${CatalogContractPaths.stage22GlobalBase}/constraints.global.json", required = false)
        addResource("${CatalogContractPaths.stage22GlobalBase}/attribute_dicts.global.json", required = false)

        Stage22RegistryLoader.loadPackageDescriptors()
            .sortedBy { it.l0Code }
            .forEach { descriptor ->
                val basePath = descriptor.basePath.trim().ifEmpty {
                    CatalogContractPaths.stage22DefaultPackageBase(descriptor.l0Code)
                }
                descriptor.profilesFile?.trim()?.takeIf { it.isNotEmpty() }?.let { fileName ->
                    addResource("$basePath/$fileName")
                }
                descriptor.constraintsFile?.trim()?.takeIf { it.isNotEmpty() }?.let { fileName ->
                    addResource("$basePath/$fileName")
                }
                descriptor.valueDictsFile?.trim()?.takeIf { it.isNotEmpty() }?.let { fileName ->
                    addResource("$basePath/$fileName")
                }
                descriptor.sharedProfilesFile?.trim()?.takeIf { it.isNotEmpty() }?.let { fileName ->
                    addResource("$basePath/$fileName")
                }
            }

        addResource("${CatalogContractPaths.stage30Base}/facet_definitions.json")
        addResource("${CatalogContractPaths.stage30Base}/facet_presets.json")
        addResource("${CatalogContractPaths.stage30Base}/facet_collections.json")

        addResource("${CatalogContractPaths.stage40Base}/immutable_attribute_schema.json")
        addResource("${CatalogContractPaths.stage40Base}/normalization_contract.json")
        addResource("${CatalogContractPaths.stage40Base}/dedup_keys.json")
        addResource("${CatalogContractPaths.stage40Base}/typed_constraints.json")
        addResource("${CatalogContractPaths.stage40Base}/facet_presentation_profiles.json", required = false)

        return digest.digest()
            .take(8)
            .joinToString(separator = "") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }
    }
}
