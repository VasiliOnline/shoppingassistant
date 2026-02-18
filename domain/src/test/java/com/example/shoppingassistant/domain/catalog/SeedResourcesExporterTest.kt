package com.example.shoppingassistant.domain.catalog

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class SeedResourcesExporterTest {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    @Test
    fun exportSeedResources_whenEnabled() {
        val enabled = System.getProperty(EXPORT_FLAG) == "true" || System.getenv(EXPORT_ENV) == "true"
        if (!enabled) return

        val resourcesRoot = Paths.get("src/main/resources")
        val testResourcesRoot = Paths.get("src/test/resources")
        exportStage1(resourcesRoot)
        exportStage22(resourcesRoot)
        exportBaseline(testResourcesRoot)
    }

    private fun exportStage1(resourcesRoot: Path) {
        val stage11 = resourcesRoot.resolve("taxonomy/stage1/1.1")
        val stage12 = resourcesRoot.resolve("taxonomy/stage1/1.2")

        writeJson(
            stage11.resolve("categories.json"),
            json.encodeToString(ListSerializer(Category.serializer()), CatalogSeed.categories),
        )
        writeJson(
            stage11.resolve("category_aliases.json"),
            json.encodeToString(ListSerializer(CategoryAlias.serializer()), CatalogSeed.categoryAliases),
        )
        writeJson(
            stage11.resolve("browse_nodes.json"),
            json.encodeToString(ListSerializer(BrowseNode.serializer()), CatalogSeed.browseNodes),
        )
        writeJson(
            stage11.resolve("alias_entries.json"),
            json.encodeToString(ListSerializer(AliasEntry.serializer()), CatalogSeed.aliasEntries),
        )
        writeJson(
            stage12.resolve("google_taxonomy_mappings.json"),
            json.encodeToString(ListSerializer(GoogleTaxonomyMapping.serializer()), CatalogSeed.googleMappings),
        )
    }

    private fun exportStage22(resourcesRoot: Path) {
        val stage22Root = resourcesRoot.resolve("taxonomy/stage2/2.2")
        val profilesByL0 = CatalogSeed.profiles.groupBy { it.category.code.substringBefore('.') }
        val constraintsByL0 = CatalogSeed.constraints.groupBy { constraint ->
            constraint.categoryCode?.substringBefore('.') ?: GLOBAL_PACKAGE_CODE
        }
        val globalDicts = CatalogSeed.profiles
            .asSequence()
            .flatMap { it.valueDictionaries.asSequence() }
            .distinctBy { it.attributeCode }
            .toList()

        profilesByL0
            .toSortedMap()
            .forEach { (l0Code, profiles) ->
                val packageDir = stage22Root.resolve(l0Code)
                val l0Lower = l0Code.lowercase()
                writeJson(
                    packageDir.resolve("profiles.$l0Lower.json"),
                    json.encodeToString(ListSerializer(CategoryProfile.serializer()), profiles),
                )
                writeJson(
                    packageDir.resolve("constraints.$l0Lower.json"),
                    json.encodeToString(
                        ListSerializer(com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints.serializer()),
                        constraintsByL0[l0Code].orEmpty(),
                    ),
                )
            }

        val globalDir = stage22Root.resolve(GLOBAL_PACKAGE_CODE)
        writeJson(
            globalDir.resolve("attribute_dicts.global.json"),
            json.encodeToString(ListSerializer(AttributeValueDict.serializer()), globalDicts),
        )
        writeJson(
            globalDir.resolve("constraints.global.json"),
            json.encodeToString(
                ListSerializer(com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints.serializer()),
                constraintsByL0[GLOBAL_PACKAGE_CODE].orEmpty(),
            ),
        )
    }

    private fun exportBaseline(testResourcesRoot: Path) {
        val suiteReport = Stage21GoldenSuiteRunner().run()
        val taxonomyReport = TaxonomyValidator().validate(
            categories = CatalogSeed.categories,
            aliases = CatalogSeed.categoryAliases,
            mappings = CatalogSeed.googleMappings,
            browseNodes = CatalogSeed.browseNodes,
            aliasEntries = CatalogSeed.aliasEntries,
        )

        val baseline = Stage21BaselineSnapshot(
            version = 1,
            isPass = suiteReport.isPass,
            packageReports = suiteReport.packages.map { report ->
                Stage21BaselinePackageSnapshot(
                    packageCode = report.packageCode,
                    total = report.total,
                    hitsAt1 = report.hitsAt1,
                    hitsAt3 = report.hitsAt3,
                    hitsByRequirement = report.hitsByRequirement,
                    recallAt1 = report.recallAt1,
                    recallAt3 = report.recallAt3,
                    passRate = report.passRate,
                    ambiguousShare = report.ambiguousShare,
                    expectedThreshold = report.expectedThreshold,
                )
            },
            taxonomyFailIssues = taxonomyReport.failIssues.size,
            taxonomyWarnIssues = taxonomyReport.warnIssues.size,
        )

        writeJson(
            testResourcesRoot.resolve("taxonomy/stage2/stage21/baseline.json"),
            json.encodeToString(Stage21BaselineSnapshot.serializer(), baseline),
        )
    }

    private fun writeJson(path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content + "\n", StandardCharsets.UTF_8)
    }

    private companion object {
        private const val EXPORT_FLAG = "seed.export"
        private const val EXPORT_ENV = "SEED_EXPORT"
        private const val GLOBAL_PACKAGE_CODE = "_global"
    }
}

@Serializable
data class Stage21BaselineSnapshot(
    val version: Int,
    val isPass: Boolean,
    val packageReports: List<Stage21BaselinePackageSnapshot>,
    val taxonomyFailIssues: Int,
    val taxonomyWarnIssues: Int,
)

@Serializable
data class Stage21BaselinePackageSnapshot(
    val packageCode: String,
    val total: Int,
    val hitsAt1: Int,
    val hitsAt3: Int,
    val hitsByRequirement: Int,
    val recallAt1: Double,
    val recallAt3: Double,
    val passRate: Double,
    val ambiguousShare: Double,
    val expectedThreshold: Double,
)
