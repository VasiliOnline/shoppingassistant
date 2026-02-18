package com.example.shoppingassistant.domain.catalog

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage21BaselineContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun stage21_golden_suite_matches_frozen_baseline() {
        val baseline = readBaseline()
        val current = currentSnapshot()

        assertEquals("baseline version", baseline.version, current.version)
        assertEquals("suite pass flag", baseline.isPass, current.isPass)
        assertEquals("taxonomy fail issues", baseline.taxonomyFailIssues, current.taxonomyFailIssues)
        assertEquals("taxonomy warn issues", baseline.taxonomyWarnIssues, current.taxonomyWarnIssues)
        assertEquals("package count", baseline.packageReports.size, current.packageReports.size)

        val currentByCode = current.packageReports.associateBy { it.packageCode }
        baseline.packageReports.forEach { expected ->
            val actual = currentByCode[expected.packageCode]
            assertTrue("missing package ${expected.packageCode}", actual != null)
            val nonNullActual = actual ?: return@forEach

            assertEquals("${expected.packageCode}: total", expected.total, nonNullActual.total)
            assertEquals("${expected.packageCode}: hitsAt1", expected.hitsAt1, nonNullActual.hitsAt1)
            assertEquals("${expected.packageCode}: hitsAt3", expected.hitsAt3, nonNullActual.hitsAt3)
            assertEquals("${expected.packageCode}: hitsByRequirement", expected.hitsByRequirement, nonNullActual.hitsByRequirement)
            assertEquals("${expected.packageCode}: recallAt1", expected.recallAt1, nonNullActual.recallAt1, 1e-12)
            assertEquals("${expected.packageCode}: recallAt3", expected.recallAt3, nonNullActual.recallAt3, 1e-12)
            assertEquals("${expected.packageCode}: passRate", expected.passRate, nonNullActual.passRate, 1e-12)
            assertEquals("${expected.packageCode}: ambiguousShare", expected.ambiguousShare, nonNullActual.ambiguousShare, 1e-12)
            assertEquals("${expected.packageCode}: expectedThreshold", expected.expectedThreshold, nonNullActual.expectedThreshold, 1e-12)
        }
    }

    private fun readBaseline(): Stage21BaselineSnapshot {
        val path = "taxonomy/stage2/stage21/baseline.json"
        val raw = Stage21BaselineContractTest::class.java.classLoader.getResourceAsStream(path)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Baseline snapshot not found: $path")
        return json.decodeFromString(Stage21BaselineSnapshot.serializer(), raw)
    }

    private fun currentSnapshot(): Stage21BaselineSnapshot {
        val suiteReport = Stage21GoldenSuiteRunner().run()
        val taxonomyReport = TaxonomyValidator().validate(
            categories = CatalogSeed.categories,
            aliases = CatalogSeed.categoryAliases,
            mappings = CatalogSeed.googleMappings,
            browseNodes = CatalogSeed.browseNodes,
            aliasEntries = CatalogSeed.aliasEntries,
        )
        return Stage21BaselineSnapshot(
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
    }
}
