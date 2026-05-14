package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.i18n.localizedTextOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class TaxonomyValidatorTest {
    private val validator = TaxonomyValidator()

    @Test
    fun canonicalSeed_isValid() {
        val report = validator.validate(
            categories = CatalogSeed.categories,
            aliases = CatalogSeed.categoryAliases,
            mappings = CatalogSeed.googleMappings,
            browseNodes = CatalogSeed.browseNodes,
            aliasEntries = CatalogSeed.aliasEntries,
        )
        assertTrue(report.summary(), report.isValid)
    }

    @Test
    fun duplicateCategoryCode_detected() {
        val categories = baseCategories() + baseCategories().first()
        val report = validator.validate(categories, baseAliases(), baseMappings())
        assertContainsIssue(report, "CATEGORY_CODE_DUPLICATE")
    }

    @Test
    fun missingParent_detected() {
        val categories = listOf(
            category("TECH", null, CategorySegment.TECH),
            category("TECH.PHONES", "TECH.MISSING", CategorySegment.TECH),
        )
        val report = validator.validate(categories, baseAliases(), baseMappings())
        assertContainsIssue(report, "CATEGORY_PARENT_MISSING")
    }

    @Test
    fun depthExceedsThree_detected() {
        val categories = listOf(
            category("TECH", null, CategorySegment.TECH),
            category("TECH.PHONES", "TECH", CategorySegment.TECH),
            category("TECH.PHONES.PREMIUM", "TECH.PHONES", CategorySegment.TECH),
            category("TECH.PHONES.PRO", "TECH.PHONES.PREMIUM", CategorySegment.TECH),
        )
        val mappings = listOf(
            mapping("TECH", 222L, "Electronics"),
            mapping("TECH.PHONES", 267L, "Electronics > Communications > Telephony > Mobile Phones"),
            mapping("TECH.PHONES.PREMIUM", 267L, "Electronics > Communications > Telephony > Mobile Phones"),
            mapping("TECH.PHONES.PRO", 267L, "Electronics > Communications > Telephony > Mobile Phones"),
        )

        val report = validator.validate(categories, aliases = emptyList(), mappings = mappings)
        assertContainsIssue(report, "CATEGORY_DEPTH_EXCEEDED")
    }

    @Test
    fun cycle_detected() {
        val categories = listOf(
            category("TECH", "TECH.PHONES", CategorySegment.TECH),
            category("TECH.PHONES", "TECH", CategorySegment.TECH),
        )
        val report = validator.validate(categories, aliases = emptyList(), mappings = baseMappings())
        assertContainsIssue(report, "CATEGORY_CYCLE")
    }

    @Test
    fun categoryWithoutAnyDisplayTitle_detected() {
        val categories = listOf(
            category("TECH", null, CategorySegment.TECH).copy(title = localizedTextOf("ru" to "  ")),
            category("TECH.PHONES", "TECH", CategorySegment.TECH),
        )

        val report = validator.validate(categories, aliases = emptyList(), mappings = baseMappings())

        assertContainsIssue(report, "CATEGORY_TITLE_BLANK")
    }

    @Test
    fun deprecatedCategory_withoutReplacement_detected() {
        val categories = listOf(
            category("TECH", null, CategorySegment.TECH),
            category(
                code = "TECH.PHONES",
                parentCode = "TECH",
                segment = CategorySegment.TECH,
                status = CategoryStatus.DEPRECATED,
            ),
        )
        val report = validator.validate(categories, aliases = emptyList(), mappings = baseMappings())
        assertContainsIssue(report, "CATEGORY_DEPRECATED_REPLACEMENT_MISSING")
    }

    @Test
    fun replacementCycle_detected() {
        val categories = listOf(
            category(
                code = "TECH",
                parentCode = null,
                segment = CategorySegment.TECH,
                status = CategoryStatus.DEPRECATED,
                replacementCode = "TECH.PHONES",
            ),
            category(
                code = "TECH.PHONES",
                parentCode = "TECH",
                segment = CategorySegment.TECH,
                status = CategoryStatus.DEPRECATED,
                replacementCode = "TECH",
            ),
        )
        val mappings = listOf(
            mapping("TECH", 222L, "Electronics"),
            mapping("TECH.PHONES", 267L, "Electronics > Communications > Telephony > Mobile Phones"),
        )

        val report = validator.validate(categories, aliases = emptyList(), mappings = mappings)
        assertContainsIssue(report, "CATEGORY_REPLACEMENT_CYCLE")
    }

    @Test
    fun replacementForActiveCategory_detected() {
        val categories = listOf(
            category(
                code = "TECH",
                parentCode = null,
                segment = CategorySegment.TECH,
                status = CategoryStatus.ACTIVE,
                replacementCode = "TECH.PHONES",
            ),
            category("TECH.PHONES", "TECH", CategorySegment.TECH),
        )
        val report = validator.validate(categories, aliases = emptyList(), mappings = baseMappings())
        assertContainsIssue(report, "CATEGORY_REPLACEMENT_FOR_NON_DEPRECATED")
    }

    @Test
    fun invalidCodeFormat_detected() {
        val categories = listOf(
            category("TECH", null, CategorySegment.TECH),
            category("tech.phones", "TECH", CategorySegment.TECH),
        )
        val mappings = listOf(
            mapping("TECH", 222L, "Electronics"),
            mapping("tech.phones", 267L, "Electronics > Communications > Telephony > Mobile Phones"),
        )

        val report = validator.validate(categories, aliases = emptyList(), mappings = mappings)
        assertContainsIssue(report, "CATEGORY_CODE_FORMAT")
    }

    @Test
    fun aliasDuplicateAfterNormalization_detected() {
        val aliases = listOf(
            CategoryAlias(alias = "Телефон", categoryCode = "TECH.PHONES"),
            CategoryAlias(alias = " телефон ", categoryCode = "TECH.PHONES"),
        )
        val report = validator.validate(baseCategories(), aliases, baseMappings())
        assertContainsIssue(report, "ALIAS_DUPLICATE")
    }

    @Test
    fun aliasAmbiguous_detected() {
        val aliases = listOf(
            CategoryAlias(alias = "телефон", categoryCode = "TECH"),
            CategoryAlias(alias = "телефон", categoryCode = "TECH.PHONES"),
        )
        val report = validator.validate(baseCategories(), aliases, baseMappings())
        assertContainsIssue(report, "ALIAS_AMBIGUOUS")
    }

    @Test
    fun aliasCategoryMissing_detected() {
        val aliases = listOf(CategoryAlias(alias = "телефон", categoryCode = "UNKNOWN"))
        val report = validator.validate(baseCategories(), aliases, baseMappings())
        assertContainsIssue(report, "ALIAS_CATEGORY_MISSING")
    }

    @Test
    fun mappingCardinalityMismatch_detected() {
        val mappings = listOf(
            mapping("TECH", 222L, "Electronics"),
            GoogleTaxonomyMapping(
                canonicalCode = "TECH.PHONES",
                mappingType = GoogleTaxonomyMappingType.SINGLE,
                googleIds = listOf(267L),
                googlePaths = listOf(
                    "Electronics > Communications > Telephony > Mobile Phones",
                    "Electronics > Video",
                ),
            ),
        )
        val report = validator.validate(baseCategories(), baseAliases(), mappings)
        assertContainsIssue(report, "MAPPING_CARDINALITY_MISMATCH")
    }

    @Test
    fun mappingSingleWithMultipleIds_detected() {
        val mappings = listOf(
            mapping("TECH", 222L, "Electronics"),
            GoogleTaxonomyMapping(
                canonicalCode = "TECH.PHONES",
                mappingType = GoogleTaxonomyMappingType.SINGLE,
                googleIds = listOf(267L, 386L),
                googlePaths = listOf(
                    "Electronics > Communications > Telephony > Mobile Phones",
                    "Electronics > Video",
                ),
            ),
        )
        val report = validator.validate(baseCategories(), baseAliases(), mappings)
        assertContainsIssue(report, "MAPPING_SINGLE_INVALID")
    }

    @Test
    fun mappingMissingForCategory_detected() {
        val mappings = listOf(mapping("TECH", 222L, "Electronics"))
        val report = validator.validate(baseCategories(), baseAliases(), mappings)
        assertContainsIssue(report, "MAPPING_MISSING")
    }

    @Test
    fun browseParentMissing_detected() {
        val brokenBrowse = listOf(
            BrowseNode(
                browseCode = "NODE.TECH.PHONES",
                parentBrowseCode = "ROOT.UNKNOWN",
                title = localizedTextOf("ru" to "Смартфоны"),
                targetCategoryCode = "TECH.PHONES",
                targetType = BrowseTargetType.CATEGORY,
                order = 1,
            ),
        )
        val report = validator.validate(
            categories = baseCategories(),
            aliases = baseAliases(),
            mappings = baseMappings(),
            browseNodes = brokenBrowse,
            aliasEntries = baseAliasEntries(),
        )
        assertContainsIssue(report, "BROWSE_PARENT_MISSING")
    }

    @Test
    fun browseTargetNotLeaf_detected() {
        val browse = listOf(
            BrowseNode(
                browseCode = "ROOT.TECH",
                title = localizedTextOf("ru" to "Электроника"),
                targetCategoryCode = "TECH",
                targetType = BrowseTargetType.CATEGORY,
                order = 1,
            ),
        )
        val report = validator.validate(
            categories = baseCategories(),
            aliases = baseAliases(),
            mappings = baseMappings(),
            browseNodes = browse,
            aliasEntries = baseAliasEntries(),
        )
        assertContainsIssue(report, "BROWSE_TARGET_NOT_LEAF")
    }

    @Test
    fun browseCycle_detected() {
        val browse = listOf(
            BrowseNode(
                browseCode = "ROOT.TECH",
                parentBrowseCode = "NODE.TECH.PHONES",
                title = localizedTextOf("ru" to "Электроника"),
                order = 1,
            ),
            BrowseNode(
                browseCode = "NODE.TECH.PHONES",
                parentBrowseCode = "ROOT.TECH",
                title = localizedTextOf("ru" to "Смартфоны"),
                targetCategoryCode = "TECH.PHONES",
                targetType = BrowseTargetType.CATEGORY,
                order = 1,
            ),
        )
        val report = validator.validate(
            categories = baseCategories(),
            aliases = baseAliases(),
            mappings = baseMappings(),
            browseNodes = browse,
            aliasEntries = baseAliasEntries(),
        )
        assertContainsIssue(report, "BROWSE_CYCLE")
    }

    @Test
    fun aliasEntryTargetMissing_detected() {
        val aliasEntries = listOf(
            AliasEntry(
                locale = "ru-RU",
                term = "телефон",
                kind = AliasKind.CATEGORY,
                targetCode = "TECH.PHONES",
                weight = 90,
            ),
            AliasEntry(
                locale = "ru-RU",
                term = "несуществующая цель",
                kind = AliasKind.CATEGORY,
                targetCode = "UNKNOWN.CODE",
                weight = 50,
            ),
        )
        val report = validator.validate(
            categories = baseCategories(),
            aliases = baseAliases(),
            mappings = baseMappings(),
            browseNodes = baseBrowseNodes(),
            aliasEntries = aliasEntries,
        )
        assertContainsIssue(report, "ALIAS_ENTRY_TARGET_MISSING")
    }

    @Test
    fun aliasEntryDuplicateLocaleTerm_detected() {
        val aliasEntries = listOf(
            AliasEntry(
                locale = "ru-RU",
                term = "телефон",
                kind = AliasKind.CATEGORY,
                targetCode = "TECH.PHONES",
                weight = 90,
            ),
            AliasEntry(
                locale = "ru-RU",
                term = "телефон",
                kind = AliasKind.CATEGORY,
                targetCode = "TECH.PHONES",
                weight = 80,
            ),
        )
        val report = validator.validate(
            categories = baseCategories(),
            aliases = baseAliases(),
            mappings = baseMappings(),
            browseNodes = baseBrowseNodes(),
            aliasEntries = aliasEntries,
        )
        assertContainsIssue(report, "ALIAS_ENTRY_DUPLICATE")
    }

    @Test
    fun aliasEntryCollision_detected() {
        val categories = listOf(
            category(code = "TECH", parentCode = null, segment = CategorySegment.TECH),
            category(code = "TECH.PHONES", parentCode = "TECH", segment = CategorySegment.TECH),
            category(code = "TECH.TV_HOME_THEATER", parentCode = "TECH", segment = CategorySegment.TECH),
        )
        val mappings = listOf(
            mapping("TECH", 222L, "Electronics"),
            mapping("TECH.PHONES", 267L, "Electronics > Communications > Telephony > Mobile Phones"),
            mapping("TECH.TV_HOME_THEATER", 229L, "Electronics > Video > Televisions"),
        )
        val aliasEntries = listOf(
            AliasEntry(
                locale = "ru-RU",
                term = "экран",
                normalizedTerm = "экран",
                kind = AliasKind.CATEGORY,
                targetCode = "TECH.PHONES",
                weight = 70,
            ),
            AliasEntry(
                locale = "ru-RU",
                term = "экран",
                normalizedTerm = "экран",
                kind = AliasKind.CATEGORY,
                targetCode = "TECH.TV_HOME_THEATER",
                weight = 70,
            ),
        )

        val report = validator.validate(
            categories = categories,
            aliases = emptyList(),
            mappings = mappings,
            browseNodes = baseBrowseNodes(),
            aliasEntries = aliasEntries,
        )
        assertContainsIssue(report, "ALIAS_ENTRY_COLLISION")
    }

    @Test
    fun coverageLeafUnreachable_detected() {
        val browseRootsOnly = listOf(
            BrowseNode(
                browseCode = "ROOT.TECH",
                title = localizedTextOf("ru" to "Электроника"),
                order = 1,
            ),
        )
        val report = validator.validate(
            categories = baseCategories(),
            aliases = emptyList(),
            mappings = baseMappings(),
            browseNodes = browseRootsOnly,
            aliasEntries = emptyList(),
        )
        assertFalse(report.summary(), report.coverage.isEmpty())
        assertContainsIssue(report, "COVERAGE_LEAF_UNREACHABLE")
    }

    @Test
    fun genericAlias_warnOnly() {
        val aliasEntries = listOf(
            AliasEntry(
                locale = "ru-RU",
                term = "телефон",
                kind = AliasKind.CATEGORY,
                targetCode = "TECH.PHONES",
                weight = 95,
                matchKind = AliasMatchKind.EXACT,
            ),
        )
        val report = validator.validate(
            categories = baseCategories(),
            aliases = baseAliases(),
            mappings = baseMappings(),
            browseNodes = baseBrowseNodes(),
            aliasEntries = aliasEntries,
        )
        assertContainsIssue(report, "ALIAS_ENTRY_GENERIC_TERM")
        assertTrue(report.summary(), report.isValid)
    }

    @Test
    fun hardGenericAlias_failsValidation() {
        val aliasEntries = listOf(
            AliasEntry(
                locale = "ru-RU",
                term = "товар",
                kind = AliasKind.CATEGORY,
                targetCode = "TECH.PHONES",
                weight = 50,
                matchKind = AliasMatchKind.EXACT,
            ),
        )
        val report = validator.validate(
            categories = baseCategories(),
            aliases = baseAliases(),
            mappings = baseMappings(),
            browseNodes = baseBrowseNodes(),
            aliasEntries = aliasEntries,
        )
        assertContainsIssue(report, "ALIAS_ENTRY_GENERIC_HARD")
        assertFalse(report.summary(), report.isValid)
    }

    @Test
    fun report_containsStatsAndInfoSection() {
        val aliasEntries = listOf(
            AliasEntry(
                locale = "ru-RU",
                term = "пицца",
                kind = AliasKind.BROWSE,
                targetCode = "ROOT.TECH",
                weight = 30,
                matchKind = AliasMatchKind.PREFIX,
            ),
            AliasEntry(
                locale = "en-US",
                term = "pizza",
                kind = AliasKind.BROWSE,
                targetCode = "ROOT.TECH",
                weight = 30,
                matchKind = AliasMatchKind.PREFIX,
            ),
        )
        val report = validator.validate(
            categories = baseCategories(),
            aliases = baseAliases(),
            mappings = baseMappings(),
            browseNodes = baseBrowseNodes(),
            aliasEntries = aliasEntries,
        )

        assertEquals(2, report.stats.categoriesCount)
        assertEquals(2, report.stats.browseNodesCount)
        assertEquals(2, report.stats.aliasEntriesCount)
        assertEquals(1, report.stats.maxChildren)
        assertEquals(0, report.stats.collisionCount)
        assertTrue(report.stats.aliasLocales.containsKey("ru-ru"))
        assertTrue(report.stats.aliasLocales.containsKey("en-us"))
        assertTrue(report.stats.genericAliasTop.isNotEmpty())
        assertContainsIssue(report, "ALIAS_ENTRY_GENERIC_TOP")
    }

    @Test
    fun aliasEntryLocaleOutsideAllowList_detected() {
        val aliasEntries = listOf(
            AliasEntry(
                locale = "fr-FR",
                term = "telephone",
                kind = AliasKind.CATEGORY,
                targetCode = "TECH.PHONES",
                weight = 60,
                matchKind = AliasMatchKind.EXACT,
            ),
        )
        val report = validator.validate(
            categories = baseCategories(),
            aliases = baseAliases(),
            mappings = baseMappings(),
            browseNodes = baseBrowseNodes(),
            aliasEntries = aliasEntries,
        )
        assertContainsIssue(report, "ALIAS_ENTRY_LOCALE_NOT_ALLOWED")
    }

    @Test
    fun languageOnlyGovernanceAliasLocales_areAccepted() {
        val aliasEntries = listOf(
            AliasEntry(
                locale = "en",
                term = "apple",
                normalizedTerm = "apple",
                kind = AliasKind.BRAND,
                targetCode = "APPLE",
                weight = 98,
                matchKind = AliasMatchKind.EXACT,
                source = AliasSource.LEARNED,
            ),
            AliasEntry(
                locale = "ru",
                term = "128 гб",
                normalizedTerm = "128 гб",
                kind = AliasKind.ATTRIBUTE_HINT,
                targetCode = "memory_gb",
                weight = 95,
                matchKind = AliasMatchKind.TOKEN,
                source = AliasSource.LEARNED,
            ),
        )
        val report = validator.validate(
            categories = baseCategories(),
            aliases = baseAliases(),
            mappings = baseMappings(),
            browseNodes = baseBrowseNodes(),
            aliasEntries = aliasEntries,
        )

        assertTrue(report.summary(), report.failIssues.isEmpty())
        assertDoesNotContainIssue(report, "ALIAS_ENTRY_LOCALE_UNSUPPORTED")
        assertDoesNotContainIssue(report, "ALIAS_ENTRY_LOCALE_NOT_ALLOWED")
        assertDoesNotContainIssue(report, "ALIAS_ENTRY_TARGET_MISSING")
        assertDoesNotContainIssue(report, "ALIAS_ENTRY_TARGET_KIND_MISMATCH")
    }

    @Test
    fun plusSemanticsInGovernanceAliases_areAccepted() {
        val aliasEntries = listOf(
            AliasEntry(
                locale = "en",
                term = "snap 8+ gen1",
                normalizedTerm = "snap 8 plus gen1",
                kind = AliasKind.ATTRIBUTE_HINT,
                targetCode = "chipset_family",
                weight = 95,
                matchKind = AliasMatchKind.TOKEN,
                source = AliasSource.LEARNED,
            ),
            AliasEntry(
                locale = "ru",
                term = "7+ ген 2",
                normalizedTerm = "7 plus ген 2",
                kind = AliasKind.ATTRIBUTE_HINT,
                targetCode = "chipset_family",
                weight = 95,
                matchKind = AliasMatchKind.TOKEN,
                source = AliasSource.LEARNED,
            ),
        )
        val report = validator.validate(
            categories = baseCategories(),
            aliases = baseAliases(),
            mappings = baseMappings(),
            browseNodes = baseBrowseNodes(),
            aliasEntries = aliasEntries,
        )

        assertTrue(report.summary(), report.failIssues.isEmpty())
        assertDoesNotContainIssue(report, "ALIAS_ENTRY_TERM_NOT_NORMALIZED")
    }

    private fun baseCategories(): List<Category> = listOf(
        category(code = "TECH", parentCode = null, segment = CategorySegment.TECH),
        category(code = "TECH.PHONES", parentCode = "TECH", segment = CategorySegment.TECH),
    )

    private fun baseAliases(): List<CategoryAlias> =
        listOf(CategoryAlias(alias = "телефон", categoryCode = "TECH.PHONES"))

    private fun baseBrowseNodes(): List<BrowseNode> = listOf(
        BrowseNode(
            browseCode = "ROOT.TECH",
            title = localizedTextOf("ru" to "Электроника"),
            order = 1,
        ),
        BrowseNode(
            browseCode = "NODE.TECH.PHONES",
            parentBrowseCode = "ROOT.TECH",
            title = localizedTextOf("ru" to "Смартфоны"),
            targetCategoryCode = "TECH.PHONES",
            targetType = BrowseTargetType.CATEGORY,
            order = 1,
        ),
    )

    private fun baseAliasEntries(): List<AliasEntry> = listOf(
        AliasEntry(
            locale = "ru-RU",
            term = "телефон",
            kind = AliasKind.CATEGORY,
            targetCode = "TECH.PHONES",
            weight = 90,
        ),
    )

    private fun baseMappings(): List<GoogleTaxonomyMapping> = listOf(
        mapping("TECH", 222L, "Electronics"),
        mapping("TECH.PHONES", 267L, "Electronics > Communications > Telephony > Mobile Phones"),
    )

    private fun category(
        code: String,
        parentCode: String?,
        segment: CategorySegment,
        status: CategoryStatus = CategoryStatus.ACTIVE,
        replacementCode: String? = null,
    ): Category = Category(
        code = code,
        parentCode = parentCode,
        segment = segment,
        title = localizedTextOf("ru" to code),
        status = status,
        replacementCode = replacementCode,
    )

    private fun mapping(
        canonicalCode: String,
        id: Long,
        path: String,
    ): GoogleTaxonomyMapping = GoogleTaxonomyMapping(
        canonicalCode = canonicalCode,
        mappingType = GoogleTaxonomyMappingType.SINGLE,
        googleIds = listOf(id),
        googlePaths = listOf(path),
    )

    private fun assertContainsIssue(report: TaxonomyValidationReport, issueCode: String) {
        val hasIssue = report.issues.any { it.code == issueCode }
        assertTrue(report.summary(), hasIssue)
    }

    private fun assertDoesNotContainIssue(report: TaxonomyValidationReport, issueCode: String) {
        val hasIssue = report.issues.any { it.code == issueCode }
        assertFalse(report.summary(), hasIssue)
    }
}
