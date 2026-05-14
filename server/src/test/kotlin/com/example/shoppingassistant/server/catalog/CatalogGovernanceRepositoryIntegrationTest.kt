package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.AliasEntry
import com.example.shoppingassistant.domain.catalog.CatalogAliasCanon
import com.example.shoppingassistant.domain.catalog.CatalogAttributeValueCanon
import com.example.shoppingassistant.domain.catalog.CatalogCanonicalModelRegistry
import com.example.shoppingassistant.domain.catalog.CatalogCanonicalProductFamilyRegistry
import com.example.shoppingassistant.domain.catalog.AliasMatchKind
import com.example.shoppingassistant.domain.catalog.AliasSource
import com.example.shoppingassistant.domain.catalog.CatalogBrandCanon
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceAliasTargetKind
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCandidateStatus
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceDecision
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceDecisionAction
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceDecisionEntityKind
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceScope
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceSourceSnapshot
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceSourceTier
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCuratedSeed
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesRequest
import com.example.shoppingassistant.domain.catalog.CatalogModelCanon
import com.example.shoppingassistant.domain.catalog.CatalogProductFamilyCanon
import com.example.shoppingassistant.domain.catalog.CatalogValueCandidate
import com.example.shoppingassistant.domain.catalog.CatalogValueObservation
import com.example.shoppingassistant.domain.catalog.AliasKind
import com.example.shoppingassistant.domain.catalog.CategorySegment
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import com.example.shoppingassistant.server.db.DatabaseFactory
import com.example.shoppingassistant.server.db.AuthUsersTable
import com.example.shoppingassistant.server.config.CatalogGovernanceRefreshConfig
import com.example.shoppingassistant.server.offers.OffersTable
import com.example.shoppingassistant.server.offers.ProductsTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.AfterClass
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CatalogGovernanceRepositoryIntegrationTest {

    @Test
    fun governanceRepository_roundTrip_persistsMultilingualCanonAndEvidence() = runBlocking {
        requireDocker()

        val repository = CatalogGovernanceRepositoryImpl()
        val now = System.currentTimeMillis()

        DatabaseFactory.dbQuery {
            CatalogGovernanceDecisionsTable.deleteWhere { CatalogGovernanceDecisionsTable.actor eq "test-runner" }
            CatalogGovernanceValueCandidatesTable.deleteWhere { CatalogGovernanceValueCandidatesTable.modelCode eq "TEST_IPHONE_16_PRO" }
            CatalogGovernanceValueObservationsTable.deleteWhere { CatalogGovernanceValueObservationsTable.modelCode eq "TEST_IPHONE_16_PRO" }
            CatalogGovernanceAliasesTable.deleteWhere { CatalogGovernanceAliasesTable.targetCode eq "TEST_ORANGE" }
            CatalogGovernanceValueCanonTable.deleteWhere { CatalogGovernanceValueCanonTable.canonicalCode eq "TEST_ORANGE" }
            CatalogGovernanceModelsTable.deleteWhere { CatalogGovernanceModelsTable.code eq "TEST_IPHONE_16_PRO" }
            CatalogGovernanceProductFamiliesTable.deleteWhere { CatalogGovernanceProductFamiliesTable.code eq "TEST_APPLE_IPHONE" }
            CatalogGovernanceBrandsTable.deleteWhere { CatalogGovernanceBrandsTable.code eq "TEST_APPLE" }
            CatalogGovernanceSourcesTable.deleteWhere { CatalogGovernanceSourcesTable.sourceCode eq "test-source" }
        }

        val source = repository.upsertSourceSnapshot(
            CatalogGovernanceSourceSnapshot(
                sourceCode = "test-source",
                externalRef = "dataset/colors",
                displayName = "Test Source",
                tier = CatalogGovernanceSourceTier.AUTHORITATIVE,
                defaultLocale = "en-US",
                marketCode = "us",
                sourceVersion = "2026.03",
                sourceUri = "https://example.test/colors",
                checksum = "sha256:test",
                metadata = mapOf("channel" to "integration-test"),
                capturedAt = now,
            ),
        )
        assertEquals("en-us", source.defaultLocale)
        assertEquals("US", source.marketCode)
        assertNotNull(source.id)

        val brand = repository.upsertBrand(
            CatalogBrandCanon(
                code = "TEST_APPLE",
                labels = localizedTextOf("en" to "Apple", "ru" to "Эпл"),
                normalizedKey = "test apple",
                primaryCategoryCode = "TECH.PHONES",
                primarySegment = CategorySegment.TECH,
                metadata = mapOf("source" to "integration-test"),
                createdAt = now,
                updatedAt = now,
            ),
        )

        val family = repository.upsertProductFamily(
            CatalogProductFamilyCanon(
                code = "TEST_APPLE_IPHONE",
                brandCode = brand.code,
                labels = localizedTextOf("en" to "iPhone", "ru" to "Айфон"),
                normalizedKey = "iphone",
                prettyModelPrefix = "iPhone",
                defaultCategoryCode = "TECH.PHONES",
                metadata = mapOf("source" to "integration-test"),
                createdAt = now,
                updatedAt = now,
            ),
        )

        val model = repository.upsertModel(
            CatalogModelCanon(
                code = "TEST_IPHONE_16_PRO",
                brandCode = brand.code,
                familyCode = family.code,
                labels = localizedTextOf("en" to "iPhone 16 Pro", "ru" to "Айфон 16 Про"),
                normalizedKey = "iphone 16 pro",
                defaultCategoryCode = "TECH.PHONES",
                releaseYear = 2026,
                metadata = mapOf("source" to "integration-test"),
                createdAt = now,
                updatedAt = now,
            ),
        )

        val canonScope = CatalogGovernanceScope(
            categoryCode = "TECH.PHONES",
            brandCode = brand.code,
            familyCode = family.code,
            modelCode = model.code,
        )

        val canonicalValue = repository.upsertAttributeValueCanon(
            CatalogAttributeValueCanon(
                attributeCode = "integration_test_color",
                canonicalCode = "TEST_ORANGE",
                canonicalValue = "Orange",
                labels = localizedTextOf("en" to "Orange", "ru" to "Оранжевый"),
                canonicalLocale = "en",
                normalizedValue = "orange",
                scope = canonScope,
                metadata = mapOf("palette" to "launch"),
                createdAt = now,
                updatedAt = now,
            ),
        )

        val alias = repository.upsertAlias(
            CatalogAliasCanon(
                locale = "ru-RU",
                marketCode = "ru",
                aliasText = "оранжевый",
                normalizedAlias = "оранжевый",
                targetKind = CatalogGovernanceAliasTargetKind.ATTRIBUTE_VALUE,
                targetCode = canonicalValue.canonicalCode,
                attributeCode = canonicalValue.attributeCode,
                scope = canonScope,
                sourceSnapshotId = source.id,
                confidence = 0.96,
                metadata = mapOf("kind" to "editorial"),
                createdAt = now,
                updatedAt = now,
            ),
        )

        val firstObservation = repository.recordObservation(
            CatalogValueObservation(
                attributeCode = "integration_test_color",
                locale = "ru-RU",
                marketCode = "ru",
                rawValue = "оранжевый",
                normalizedValue = "оранжевый",
                scope = canonScope,
                sourceSnapshotId = source.id,
                observedCount = 2,
                sampleRefs = listOf("offer-1"),
                metadata = mapOf("feed" to "seller-a"),
                firstSeenAt = now,
                lastSeenAt = now,
            ),
        )
        val mergedObservation = repository.recordObservation(
            CatalogValueObservation(
                attributeCode = "integration_test_color",
                locale = "ru-RU",
                marketCode = "ru",
                rawValue = "оранжевый",
                normalizedValue = "оранжевый",
                scope = canonScope,
                sourceSnapshotId = source.id,
                observedCount = 3,
                sampleRefs = listOf("offer-2"),
                metadata = mapOf("feed" to "seller-b"),
                firstSeenAt = now - 50,
                lastSeenAt = now + 100,
            ),
        )

        val candidate = repository.submitCandidate(
            CatalogValueCandidate(
                attributeCode = "integration_test_color",
                locale = "ru-RU",
                marketCode = "ru",
                rawValue = "титаново-оранжевый",
                normalizedValue = "титаново оранжевый",
                proposedCanonicalCode = canonicalValue.canonicalCode,
                proposedCanonicalValue = canonicalValue.canonicalValue,
                proposedLabels = localizedTextOf("en" to "Orange", "ru" to "Оранжевый"),
                proposedCanonicalLocale = "en",
                scope = canonScope,
                sourceSnapshotId = source.id,
                status = CatalogGovernanceCandidateStatus.REVIEWING,
                autoConfidence = 0.74,
                evidenceCount = 4,
                metadata = mapOf("source" to "query-mining"),
                createdAt = now,
                updatedAt = now,
            ),
        )

        val decision = repository.recordDecision(
            CatalogGovernanceDecision(
                entityKind = CatalogGovernanceDecisionEntityKind.VALUE_CANDIDATE,
                entityRef = "test.candidate.${candidate.id}",
                action = CatalogGovernanceDecisionAction.APPROVE,
                reasonCode = "integration_test",
                actor = "test-runner",
                payload = mapOf("candidateId" to candidate.id.toString()),
                createdAt = now,
            ),
        )

        assertEquals("ru-ru", alias.locale)
        assertEquals("RU", alias.marketCode)
        assertEquals(5, mergedObservation.observedCount)
        assertEquals(setOf("offer-1", "offer-2"), mergedObservation.sampleRefs.toSet())
        assertEquals(now - 50, mergedObservation.firstSeenAt)
        assertEquals(now + 100, mergedObservation.lastSeenAt)
        assertEquals("en", candidate.proposedCanonicalLocale)
        assertEquals("Orange", candidate.proposedLabels.resolve(locale = "en"))
        assertNotNull(decision.id)

        val brands = repository.listBrands()
        val families = repository.listProductFamilies(brand.code)
        val models = repository.listModels(brand.code, family.code)
        val values = repository.listCanonicalValues("integration_test_color", canonScope)
        val candidates = repository.listCandidates(attributeCode = "integration_test_color")

        assertTrue(brands.any { it.code == brand.code })
        assertTrue(families.any { it.code == family.code })
        assertTrue(models.any { it.code == model.code })
        assertTrue(values.any { it.canonicalCode == canonicalValue.canonicalCode && it.canonicalLocale == "en" })
        assertTrue(candidates.any { it.id == candidate.id && it.locale == "ru-ru" })
        assertTrue(firstObservation.id == mergedObservation.id, "observations with same linguistic scope must merge")
    }

    @Test
    fun servingProjection_buildsAndSyncsArtifactsFromGovernanceCore() = runBlocking {
        requireDocker()

        val repository = CatalogGovernanceRepositoryImpl()
        val service = CatalogGovernanceServingProjectionService(repository = repository)
        val now = System.currentTimeMillis()

        DatabaseFactory.dbQuery {
            AliasEntriesTable.deleteAll()
            AttributeValueDictTable.deleteAll()
            CatalogGovernanceAliasesTable.deleteWhere {
                CatalogGovernanceAliasesTable.targetCode inList listOf("TEST_APPLE", "TEST_APPLE_IPHONE", "TEST_TITANIUM_ORANGE")
            }
            CatalogGovernanceValueCanonTable.deleteWhere { CatalogGovernanceValueCanonTable.canonicalCode eq "TEST_TITANIUM_ORANGE" }
            CatalogGovernanceProductFamiliesTable.deleteWhere { CatalogGovernanceProductFamiliesTable.code eq "TEST_APPLE_IPHONE" }
            CatalogGovernanceBrandsTable.deleteWhere { CatalogGovernanceBrandsTable.code eq "TEST_APPLE" }
            CatalogGovernanceSourcesTable.deleteWhere { CatalogGovernanceSourcesTable.sourceCode eq "projection-source" }
        }

        val source = repository.upsertSourceSnapshot(
            CatalogGovernanceSourceSnapshot(
                sourceCode = "projection-source",
                externalRef = "phones/families",
                displayName = "Projection Source",
                tier = CatalogGovernanceSourceTier.AUTHORITATIVE,
                defaultLocale = "en-US",
                marketCode = "us",
                capturedAt = now,
            ),
        )
        val brand = repository.upsertBrand(
            CatalogBrandCanon(
                code = "TEST_APPLE",
                labels = localizedTextOf("en" to "Apple", "ru" to "Эпл"),
                normalizedKey = "test apple",
                primaryCategoryCode = "TECH.PHONES",
                primarySegment = CategorySegment.TECH,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val family = repository.upsertProductFamily(
            CatalogProductFamilyCanon(
                code = "TEST_APPLE_IPHONE",
                brandCode = brand.code,
                labels = localizedTextOf("en" to "iPhone", "ru" to "Айфон"),
                normalizedKey = "iphone",
                prettyModelPrefix = "iPhone",
                variantTokens = listOf("pro", "max"),
                accessoryBlockers = listOf("case", "чехол"),
                defaultCategoryCode = "TECH.PHONES",
                createdAt = now,
                updatedAt = now,
            ),
        )
        repository.upsertAttributeValueCanon(
            CatalogAttributeValueCanon(
                attributeCode = "integration_test_color",
                canonicalCode = "TEST_TITANIUM_ORANGE",
                canonicalValue = "Test Titanium Orange",
                labels = localizedTextOf("en" to "Test Titanium Orange", "ru" to "Тестовый титановый оранжевый"),
                canonicalLocale = "en",
                normalizedValue = "test titanium orange",
                scope = CatalogGovernanceScope(
                    categoryCode = "TECH.PHONES",
                    brandCode = brand.code,
                    familyCode = family.code,
                ),
                createdAt = now,
                updatedAt = now,
            ),
        )
        repository.upsertAlias(
            CatalogAliasCanon(
                locale = "ru-RU",
                aliasText = "эпл",
                normalizedAlias = "эпл",
                targetKind = CatalogGovernanceAliasTargetKind.BRAND,
                targetCode = brand.code,
                sourceSnapshotId = source.id,
                confidence = 0.97,
                createdAt = now,
                updatedAt = now,
            ),
        )
        repository.upsertAlias(
            CatalogAliasCanon(
                locale = "ru-RU",
                aliasText = "айфон",
                normalizedAlias = "айфон",
                targetKind = CatalogGovernanceAliasTargetKind.PRODUCT_FAMILY,
                targetCode = family.code,
                sourceSnapshotId = source.id,
                confidence = 0.98,
                createdAt = now,
                updatedAt = now,
            ),
        )
        repository.upsertAlias(
            CatalogAliasCanon(
                locale = "ru-RU",
                aliasText = "тестовый титановый оранжевый",
                normalizedAlias = "тестовый титановый оранжевый",
                targetKind = CatalogGovernanceAliasTargetKind.ATTRIBUTE_VALUE,
                targetCode = "TEST_TITANIUM_ORANGE",
                attributeCode = "integration_test_color",
                scope = CatalogGovernanceScope(
                    categoryCode = "TECH.PHONES",
                    brandCode = brand.code,
                    familyCode = family.code,
                ),
                sourceSnapshotId = source.id,
                confidence = 0.95,
                createdAt = now,
                updatedAt = now,
            ),
        )

        val artifacts = service.buildServingArtifacts()
        val projectedColor = artifacts.valueDictionaries
            .firstOrNull { it.attributeCode == "integration_test_color" }
            ?.entries
            ?.firstOrNull { it.canonicalCode == "TEST_TITANIUM_ORANGE" }
        val projectedFamily = artifacts.productFamilies.families
            .firstOrNull { it.familyCode == family.code }
        val projectedBrandAlias = artifacts.aliasEntries.firstOrNull { entry ->
            entry.kind == AliasKind.BRAND &&
                entry.targetCode == brand.code &&
                entry.term.equals("эпл", ignoreCase = true)
        }

        assertNotNull(projectedBrandAlias)
        assertNotNull(projectedColor)
        assertTrue("тестовый титановый оранжевый" in projectedColor.synonyms)
        assertNotNull(projectedFamily)
        assertEquals("Apple", projectedFamily.brandCanonical)
        assertTrue("эпл" in projectedFamily.brandAliases)
        assertTrue("айфон" in projectedFamily.familyAliases)
        assertEquals(listOf("pro", "max"), projectedFamily.variantTokens)
        assertEquals(listOf("case", "чехол"), projectedFamily.accessoryBlockers)

        service.syncDatabaseServingArtifacts(syncMode = CatalogSeedSyncMode.UPSERT_ONLY)

        val syncedAliasEntries = TaxonomyRepositoryImpl().listAliasEntries(locale = "ru-RU")
        val syncedDictRowCount = DatabaseFactory.dbQuery {
            AttributeValueDictTable.selectAll()
                .map { row -> row[AttributeValueDictTable.canonicalCode] }
                .count { code -> code == "TEST_TITANIUM_ORANGE" }
        }

        assertTrue(
            syncedAliasEntries.any { entry ->
                entry.kind == AliasKind.BRAND &&
                    entry.targetCode == brand.code &&
                    entry.term.equals("эпл", ignoreCase = true)
            },
        )
        assertEquals(1, syncedDictRowCount)
    }

    @Test
    fun promotionWorkflow_promotesCandidateIntoCanonicalAndAlias() = runBlocking {
        requireDocker()

        val repository = CatalogGovernanceRepositoryImpl()
        val invalidator = RecordingCatalogGovernanceRuntimeInvalidator()
        val service = CatalogGovernanceServingProjectionService(
            repository = repository,
            runtimeInvalidatorProvider = { invalidator },
        )
        val now = System.currentTimeMillis()

        DatabaseFactory.dbQuery {
            CatalogGovernanceDecisionsTable.deleteWhere { CatalogGovernanceDecisionsTable.reasonCode eq "test_promotion" }
            CatalogGovernanceAliasesTable.deleteWhere {
                CatalogGovernanceAliasesTable.targetCode inList listOf("PROMO_ORANGE", "TEST_TITANIUM_ORANGE")
            }
            CatalogGovernanceValueCanonTable.deleteWhere {
                CatalogGovernanceValueCanonTable.canonicalCode inList listOf("PROMO_ORANGE", "TEST_TITANIUM_ORANGE")
            }
            CatalogGovernanceValueCandidatesTable.deleteWhere {
                CatalogGovernanceValueCandidatesTable.proposedCanonicalCode eq "PROMO_ORANGE"
            }
            CatalogGovernanceValueObservationsTable.deleteWhere {
                CatalogGovernanceValueObservationsTable.normalizedValue eq "test orange titanium"
            }
            CatalogGovernanceSourcesTable.deleteWhere { CatalogGovernanceSourcesTable.sourceCode eq "promotion-source" }
        }

        val source = repository.upsertSourceSnapshot(
            CatalogGovernanceSourceSnapshot(
                sourceCode = "promotion-source",
                externalRef = "phones/colors",
                displayName = "Promotion Source",
                tier = CatalogGovernanceSourceTier.AUTHORITATIVE,
                defaultLocale = "en-US",
                marketCode = "us",
                capturedAt = now,
            ),
        )

        repository.recordObservation(
            CatalogValueObservation(
                attributeCode = "integration_test_color",
                locale = "ru-RU",
                marketCode = "ru",
                rawValue = "тестовый титаново-оранжевый",
                normalizedValue = "test orange titanium",
                scope = CatalogGovernanceScope(categoryCode = "TECH.PHONES"),
                sourceSnapshotId = source.id,
                observedCount = 4,
                sampleRefs = listOf("offer-100"),
                firstSeenAt = now,
                lastSeenAt = now,
            ),
        )

        val candidate = repository.submitCandidate(
            CatalogValueCandidate(
                attributeCode = "integration_test_color",
                locale = "ru-RU",
                marketCode = "ru",
                rawValue = "тестовый титаново-оранжевый",
                normalizedValue = "test orange titanium",
                proposedCanonicalCode = "PROMO_ORANGE",
                proposedCanonicalValue = "Promo Orange Test",
                proposedLabels = localizedTextOf("en" to "Promo Orange Test", "ru" to "Промо оранжевый тест"),
                proposedCanonicalLocale = "en",
                scope = CatalogGovernanceScope(categoryCode = "TECH.PHONES"),
                sourceSnapshotId = source.id,
                status = CatalogGovernanceCandidateStatus.REVIEWING,
                autoConfidence = 0.91,
                evidenceCount = 4,
                createdAt = now,
                updatedAt = now,
            ),
        )

        val assessment = service.assessPromotionCandidates(attributeCode = "integration_test_color")
            .firstOrNull { it.candidate.id == candidate.id }
        assertNotNull(assessment)
        assertTrue(assessment.eligible, assessment.reasons.joinToString(","))

        val promotion = service.promoteCandidate(
            candidateId = candidate.id ?: error("candidate id must be assigned"),
            actor = "test-runner",
            reasonCode = "test_promotion",
        )

        val promotedCandidates = repository.listCandidates(
            attributeCode = "integration_test_color",
            status = CatalogGovernanceCandidateStatus.PROMOTED,
        )
        val promotedCanon = repository.listCanonicalValues(
            attributeCode = "integration_test_color",
            scope = CatalogGovernanceScope(categoryCode = "TECH.PHONES"),
        ).firstOrNull { it.canonicalCode == "PROMO_ORANGE" }
        val promotedAlias = repository.listAliases(
            locale = "ru-RU",
            targetKind = CatalogGovernanceAliasTargetKind.ATTRIBUTE_VALUE,
            attributeCode = "integration_test_color",
        ).firstOrNull { it.targetCode == "PROMO_ORANGE" }
        val syncedDictRows = DatabaseFactory.dbQuery {
            AttributeValueDictTable.selectAll()
                .count { row -> row[AttributeValueDictTable.attributeCode] == "integration_test_color" && row[AttributeValueDictTable.canonicalCode] == "PROMO_ORANGE" }
        }

        assertEquals("PROMO_ORANGE", promotion.canonicalValue.canonicalCode)
        assertEquals(CatalogGovernanceDecisionAction.PROMOTE, promotion.decision.action)
        assertTrue(promotedCandidates.any { it.id == candidate.id })
        assertNotNull(promotedCanon)
        assertNotNull(promotedAlias)
        assertEquals("тестовый титаново-оранжевый", promotedAlias.aliasText)
        assertEquals(1, syncedDictRows)
        assertEquals(1, invalidator.events.size)
        assertEquals("serving_artifacts_synced", invalidator.events.single().reason)

        val projectedColor = service.buildServingArtifacts()
            .valueDictionaries
            .firstOrNull { it.attributeCode == "integration_test_color" }
            ?.entries
            ?.firstOrNull { it.canonicalCode == "PROMO_ORANGE" }
        assertNotNull(projectedColor)
        assertTrue("тестовый титаново-оранжевый" in projectedColor.synonyms)
    }

    @Test
    fun syncServingAliasEntries_upsertOnly_replacesStaleNormalizedTermForSameLogicalAlias() = runBlocking {
        requireDocker()

        val locale = "ru-RU"
        val term = "автокресло 0+"
        val targetCode = "TEST_CHILD_SEAT_0_PLUS"

        DatabaseFactory.dbQuery {
            AliasEntriesTable.deleteWhere {
                (AliasEntriesTable.locale eq locale) and
                    (AliasEntriesTable.term eq term) and
                    (AliasEntriesTable.kind eq AliasKind.ATTRIBUTE_HINT.name) and
                    (AliasEntriesTable.targetCode eq targetCode)
            }
            AliasEntriesTable.insertIgnore { stmt ->
                stmt[AliasEntriesTable.locale] = locale
                stmt[AliasEntriesTable.term] = term
                stmt[AliasEntriesTable.normalizedTerm] = "автокресло 0"
                stmt[AliasEntriesTable.kind] = AliasKind.ATTRIBUTE_HINT.name
                stmt[AliasEntriesTable.targetCode] = targetCode
                stmt[AliasEntriesTable.weight] = 80
                stmt[AliasEntriesTable.matchKind] = AliasMatchKind.EXACT.name
                stmt[AliasEntriesTable.isBlocked] = false
                stmt[AliasEntriesTable.aliasSource] = AliasSource.MANUAL.name
                stmt[AliasEntriesTable.notes] = "stale normalization"
            }

            CatalogSeeder.syncServingAliasEntries(
                aliasEntries = listOf(
                    AliasEntry(
                        locale = locale,
                        term = term,
                        normalizedTerm = "автокресло 0 plus",
                        kind = AliasKind.ATTRIBUTE_HINT,
                        targetCode = targetCode,
                        weight = 80,
                        matchKind = AliasMatchKind.EXACT,
                        source = AliasSource.MANUAL,
                        notes = "fresh normalization",
                    ),
                ),
                syncMode = CatalogSeedSyncMode.UPSERT_ONLY,
            )
        }

        val syncedRows = DatabaseFactory.dbQuery {
            AliasEntriesTable.selectAll()
                .filter { row ->
                    row[AliasEntriesTable.locale] == locale &&
                        row[AliasEntriesTable.term] == term &&
                        row[AliasEntriesTable.kind] == AliasKind.ATTRIBUTE_HINT.name &&
                        row[AliasEntriesTable.targetCode] == targetCode
                }
                .map { row -> row[AliasEntriesTable.normalizedTerm] to row[AliasEntriesTable.notes] }
        }

        assertEquals(1, syncedRows.size)
        assertEquals("автокресло 0 plus", syncedRows.single().first)
        assertEquals("fresh normalization", syncedRows.single().second)
    }

    @Test
    fun runtimeRegistry_usesGovernanceProviderAfterInstallation() = runBlocking {
        requireDocker()

        val repository = CatalogGovernanceRepositoryImpl()
        val service = CatalogGovernanceServingProjectionService(repository = repository)
        val provider = GovernanceCatalogCanonicalProductFamilyRegistryProvider(
            projectionService = service,
            cacheTtlMs = 0L,
        )
        val now = System.currentTimeMillis()

        DatabaseFactory.dbQuery {
            CatalogGovernanceAliasesTable.deleteWhere { CatalogGovernanceAliasesTable.targetCode eq "TEST_GOOGLE_PIXEL" }
            CatalogGovernanceProductFamiliesTable.deleteWhere { CatalogGovernanceProductFamiliesTable.code eq "TEST_GOOGLE_PIXEL" }
            CatalogGovernanceBrandsTable.deleteWhere { CatalogGovernanceBrandsTable.code eq "TEST_GOOGLE" }
            CatalogGovernanceSourcesTable.deleteWhere { CatalogGovernanceSourcesTable.sourceCode eq "family-runtime-source" }
        }

        val source = repository.upsertSourceSnapshot(
            CatalogGovernanceSourceSnapshot(
                sourceCode = "family-runtime-source",
                externalRef = "phones/google",
                displayName = "Family Runtime Source",
                tier = CatalogGovernanceSourceTier.AUTHORITATIVE,
                defaultLocale = "ru-RU",
                marketCode = "ru",
                capturedAt = now,
            ),
        )
        val brand = repository.upsertBrand(
            CatalogBrandCanon(
                code = "TEST_GOOGLE",
                labels = localizedTextOf("en" to "Google", "ru" to "Гугл"),
                normalizedKey = "test google",
                primaryCategoryCode = "TECH.PHONES",
                primarySegment = CategorySegment.TECH,
                createdAt = now,
                updatedAt = now,
            ),
        )
        repository.upsertProductFamily(
            CatalogProductFamilyCanon(
                code = "TEST_GOOGLE_PIXEL",
                brandCode = brand.code,
                labels = localizedTextOf("en" to "Pixel", "ru" to "Пиксель"),
                normalizedKey = "pixel",
                prettyModelPrefix = "Pixel",
                variantTokens = listOf("pro"),
                accessoryBlockers = listOf("case"),
                defaultCategoryCode = "TECH.PHONES",
                createdAt = now,
                updatedAt = now,
            ),
        )
        repository.upsertAlias(
            CatalogAliasCanon(
                locale = "ru-RU",
                aliasText = "тестпиксель",
                normalizedAlias = "тестпиксель",
                targetKind = CatalogGovernanceAliasTargetKind.PRODUCT_FAMILY,
                targetCode = "TEST_GOOGLE_PIXEL",
                sourceSnapshotId = source.id,
                confidence = 0.99,
                createdAt = now,
                updatedAt = now,
            ),
        )

        val beforeInstall = CatalogCanonicalProductFamilyRegistry.matchQuery("тестпиксель 9 pro 256гб")
        try {
            provider.installIntoRuntime()
            val afterInstall = CatalogCanonicalProductFamilyRegistry.matchQuery("тестпиксель 9 pro 256гб")

            assertTrue(beforeInstall == null || beforeInstall.familyCode != "TEST_GOOGLE_PIXEL")
            assertNotNull(afterInstall)
            assertEquals("TEST_GOOGLE_PIXEL", afterInstall.familyCode)
            assertEquals("TECH.PHONES", afterInstall.defaultCategoryCode)
            assertEquals("Google", afterInstall.brandCanonical)
        } finally {
            CatalogCanonicalProductFamilyRegistry.resetProvider()
        }
    }

    @Test
    fun runtimeModelRegistry_usesGovernanceProviderAfterInstallation() = runBlocking {
        requireDocker()

        val repository = CatalogGovernanceRepositoryImpl()
        val provider = GovernanceCatalogCanonicalModelRegistryProvider(
            repository = repository,
            cacheTtlMs = 0L,
        )
        val now = System.currentTimeMillis()

        DatabaseFactory.dbQuery {
            CatalogGovernanceAliasesTable.deleteWhere {
                CatalogGovernanceAliasesTable.targetCode inList listOf("TEST_GOOGLE", "TEST_GOOGLE_PIXEL", "TEST_PIXEL_9_PRO")
            }
            CatalogGovernanceModelsTable.deleteWhere { CatalogGovernanceModelsTable.code eq "TEST_PIXEL_9_PRO" }
            CatalogGovernanceProductFamiliesTable.deleteWhere { CatalogGovernanceProductFamiliesTable.code eq "TEST_GOOGLE_PIXEL" }
            CatalogGovernanceBrandsTable.deleteWhere { CatalogGovernanceBrandsTable.code eq "TEST_GOOGLE" }
            CatalogGovernanceSourcesTable.deleteWhere { CatalogGovernanceSourcesTable.sourceCode eq "model-runtime-source" }
        }

        val source = repository.upsertSourceSnapshot(
            CatalogGovernanceSourceSnapshot(
                sourceCode = "model-runtime-source",
                externalRef = "phones/google/models",
                displayName = "Model Runtime Source",
                tier = CatalogGovernanceSourceTier.AUTHORITATIVE,
                defaultLocale = "ru-RU",
                marketCode = "ru",
                capturedAt = now,
            ),
        )
        val brand = repository.upsertBrand(
            CatalogBrandCanon(
                code = "TEST_GOOGLE",
                labels = localizedTextOf("en" to "Google", "ru" to "Гугл"),
                normalizedKey = "test google",
                primaryCategoryCode = "TECH.PHONES",
                primarySegment = CategorySegment.TECH,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val family = repository.upsertProductFamily(
            CatalogProductFamilyCanon(
                code = "TEST_GOOGLE_PIXEL",
                brandCode = brand.code,
                labels = localizedTextOf("en" to "Pixel", "ru" to "Пиксель"),
                normalizedKey = "pixel",
                prettyModelPrefix = "Pixel",
                variantTokens = listOf("pro"),
                accessoryBlockers = listOf("case"),
                defaultCategoryCode = "TECH.PHONES",
                createdAt = now,
                updatedAt = now,
            ),
        )
        repository.upsertModel(
            CatalogModelCanon(
                code = "TEST_PIXEL_9_PRO",
                brandCode = brand.code,
                familyCode = family.code,
                labels = localizedTextOf("en" to "Pixel 9 Pro", "ru" to "Пиксель 9 Про"),
                normalizedKey = "pixel 9 pro",
                defaultCategoryCode = "TECH.PHONES",
                releaseYear = 2024,
                createdAt = now,
                updatedAt = now,
            ),
        )
        repository.upsertAlias(
            CatalogAliasCanon(
                locale = "ru-RU",
                aliasText = "тестпиксель 9 про",
                normalizedAlias = "тестпиксель 9 про",
                targetKind = CatalogGovernanceAliasTargetKind.MODEL,
                targetCode = "TEST_PIXEL_9_PRO",
                sourceSnapshotId = source.id,
                confidence = 0.99,
                createdAt = now,
                updatedAt = now,
            ),
        )

        val beforeInstall = CatalogCanonicalModelRegistry.matchQuery("тестпиксель 9 про 256гб")
        try {
            provider.installIntoRuntime()
            val afterInstall = CatalogCanonicalModelRegistry.matchQuery("тестпиксель 9 про 256гб")

            assertTrue(beforeInstall == null || beforeInstall.modelCode != "TEST_PIXEL_9_PRO")
            assertNotNull(afterInstall)
            assertEquals("TEST_PIXEL_9_PRO", afterInstall?.modelCode)
            assertEquals("TECH.PHONES", afterInstall?.defaultCategoryCode)
            assertEquals("Google", afterInstall?.brandCanonical)
        } finally {
            CatalogCanonicalModelRegistry.resetProvider()
        }
    }

    @Test
    fun liveValuesRepository_returns_governance_scoped_known_values_without_live_offers() = runBlocking {
        requireDocker()

        val repository = CatalogGovernanceRepositoryImpl()
        val modelProvider = GovernanceCatalogCanonicalModelRegistryProvider(
            repository = repository,
            cacheTtlMs = 0L,
        )
        val now = System.currentTimeMillis()

        DatabaseFactory.dbQuery {
            CatalogGovernanceAliasesTable.deleteWhere {
                CatalogGovernanceAliasesTable.targetCode inList listOf("TEST_IPHONE_18_PRO", "TEST_COSMIC_BLUE")
            }
            CatalogGovernanceValueCanonTable.deleteWhere { CatalogGovernanceValueCanonTable.canonicalCode eq "TEST_COSMIC_BLUE" }
            CatalogGovernanceModelsTable.deleteWhere { CatalogGovernanceModelsTable.code eq "TEST_IPHONE_18_PRO" }
            CatalogGovernanceProductFamiliesTable.deleteWhere { CatalogGovernanceProductFamiliesTable.code eq "TEST_APPLE_IPHONE" }
            CatalogGovernanceBrandsTable.deleteWhere { CatalogGovernanceBrandsTable.code eq "TEST_APPLE" }
            CatalogGovernanceSourcesTable.deleteWhere { CatalogGovernanceSourcesTable.sourceCode eq "live-values-runtime-source" }
        }

        val source = repository.upsertSourceSnapshot(
            CatalogGovernanceSourceSnapshot(
                sourceCode = "live-values-runtime-source",
                externalRef = "phones/apple/models",
                displayName = "Live Values Runtime Source",
                tier = CatalogGovernanceSourceTier.AUTHORITATIVE,
                defaultLocale = "ru-RU",
                marketCode = "ru",
                capturedAt = now,
            ),
        )
        val brand = repository.upsertBrand(
            CatalogBrandCanon(
                code = "TEST_APPLE",
                labels = localizedTextOf("en" to "Apple", "ru" to "Эпл"),
                normalizedKey = "test apple",
                primaryCategoryCode = "TECH.PHONES",
                primarySegment = CategorySegment.TECH,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val family = repository.upsertProductFamily(
            CatalogProductFamilyCanon(
                code = "TEST_APPLE_IPHONE",
                brandCode = brand.code,
                labels = localizedTextOf("en" to "iPhone", "ru" to "Айфон"),
                normalizedKey = "iphone",
                prettyModelPrefix = "iPhone",
                defaultCategoryCode = "TECH.PHONES",
                createdAt = now,
                updatedAt = now,
            ),
        )
        repository.upsertModel(
            CatalogModelCanon(
                code = "TEST_IPHONE_18_PRO",
                brandCode = brand.code,
                familyCode = family.code,
                labels = localizedTextOf("en" to "iPhone 18 Pro", "ru" to "Айфон 18 Про"),
                normalizedKey = "iphone 18 pro",
                defaultCategoryCode = "TECH.PHONES",
                releaseYear = 2026,
                createdAt = now,
                updatedAt = now,
            ),
        )
        repository.upsertAlias(
            CatalogAliasCanon(
                locale = "ru-RU",
                aliasText = "айфон 18 про",
                normalizedAlias = "айфон 18 про",
                targetKind = CatalogGovernanceAliasTargetKind.MODEL,
                targetCode = "TEST_IPHONE_18_PRO",
                sourceSnapshotId = source.id,
                confidence = 0.99,
                createdAt = now,
                updatedAt = now,
            ),
        )
        repository.upsertAttributeValueCanon(
            CatalogAttributeValueCanon(
                attributeCode = "color",
                canonicalCode = "TEST_COSMIC_BLUE",
                canonicalValue = "Cosmic Blue",
                labels = localizedTextOf("en" to "Cosmic Blue", "ru" to "Космический голубой"),
                canonicalLocale = "en",
                normalizedValue = "cosmic blue",
                scope = CatalogGovernanceScope(
                    categoryCode = "TECH.PHONES",
                    brandCode = brand.code,
                    familyCode = family.code,
                    modelCode = "TEST_IPHONE_18_PRO",
                ),
                createdAt = now,
                updatedAt = now,
            ),
        )
        repository.upsertAlias(
            CatalogAliasCanon(
                locale = "ru-RU",
                aliasText = "космический голубой",
                normalizedAlias = "космический голубой",
                targetKind = CatalogGovernanceAliasTargetKind.ATTRIBUTE_VALUE,
                targetCode = "TEST_COSMIC_BLUE",
                attributeCode = "color",
                scope = CatalogGovernanceScope(
                    categoryCode = "TECH.PHONES",
                    brandCode = brand.code,
                    familyCode = family.code,
                    modelCode = "TEST_IPHONE_18_PRO",
                ),
                sourceSnapshotId = source.id,
                confidence = 0.99,
                createdAt = now,
                updatedAt = now,
            ),
        )

        try {
            modelProvider.installIntoRuntime()
            val liveValuesRepository = CatalogLiveValuesRepositoryImpl(governanceRepository = repository)

            val snapshot = liveValuesRepository.getLiveValues(
                com.example.shoppingassistant.domain.catalog.CatalogLiveValuesRequest(
                    categoryCode = "TECH.PHONES",
                    brand = "Apple",
                    model = "iPhone 18 Pro",
                    localeTag = "ru",
                    attributeCodes = listOf("model", "color"),
                ),
            )

            assertTrue(snapshot.valuesByAttributeCode["color"].orEmpty().isEmpty())
            assertEquals(listOf("Айфон 18 Про"), snapshot.knownValuesByAttributeCode["model"])
            assertEquals(listOf("Космический голубой"), snapshot.knownValuesByAttributeCode["color"])
            assertTrue(
                snapshot.knownValueAliasesByAttributeCode["model"]
                    .orEmpty()["Айфон 18 Про"]
                    .orEmpty()
                    .contains("айфон 18 про"),
            )
            assertTrue(
                snapshot.knownValueAliasesByAttributeCode["color"]
                    .orEmpty()["Космический голубой"]
                    .orEmpty()
                    .contains("космический голубой"),
            )

            val categoryScopedSnapshot = liveValuesRepository.getLiveValues(
                com.example.shoppingassistant.domain.catalog.CatalogLiveValuesRequest(
                    categoryCode = "TECH.PHONES",
                    localeTag = "ru",
                    attributeCodes = listOf("model"),
                ),
            )

            assertTrue(categoryScopedSnapshot.brandOptions.contains("Эпл"))
            assertTrue(categoryScopedSnapshot.modelOptions.contains("Айфон 18 Про"))
            assertTrue(categoryScopedSnapshot.knownValuesByAttributeCode["model"].orEmpty().contains("Айфон 18 Про"))
            assertTrue(
                categoryScopedSnapshot.knownValueAliasesByAttributeCode["model"]
                    .orEmpty()["Айфон 18 Про"]
                    .orEmpty()
                    .contains("айфон 18 про"),
            )
        } finally {
            CatalogCanonicalModelRegistry.resetProvider()
        }
    }

    @Test
    fun curatedSeedSync_importsTopCategoryCoverageIntoGovernance() = runBlocking {
        requireDocker()

        val repository = CatalogGovernanceRepositoryImpl()
        val invalidator = RecordingCatalogGovernanceRuntimeInvalidator()
        val projectionService = CatalogGovernanceServingProjectionService(
            repository = repository,
            runtimeInvalidatorProvider = { invalidator },
        )
        val syncService = CatalogGovernanceCuratedSeedSyncService(
            repository = repository,
            projectionService = projectionService,
        )

        val report = syncService.syncTopCategoryCoverage(syncMode = CatalogSeedSyncMode.UPSERT_ONLY)

        val brands = repository.listBrands()
        val families = repository.listProductFamilies()
        val models = repository.listModels()
        val colorValues = repository.listCanonicalValuesAnyScope("color")

        assertTrue(report.packsSynced >= 1)
        assertTrue(report.brandsSynced >= 10)
        assertTrue(report.familiesSynced >= 20)
        assertTrue(report.modelsSynced >= 50)
        assertTrue(report.canonicalValuesSynced >= 30)
        assertTrue(brands.any { it.code == "APPLE" })
        assertTrue(families.any { it.code == "APPLE_MACBOOK_AIR" })
        assertTrue(families.any { it.code == "AMAZON_KINDLE" })
        assertTrue(families.any { it.code == "XIAOMI_REDMI" })
        assertTrue(models.any { it.code == "IPHONE_16_PRO" })
        assertTrue(models.any { it.code == "IPHONE_14_PRO_MAX" })
        assertTrue(models.any { it.code == "GALAXY_S24_ULTRA" })
        assertTrue(models.any { it.code == "GALAXY_S24_PLUS" })
        assertTrue(models.any { it.code == "PIXEL_7_PRO" })
        assertTrue(models.any { it.code == "HUAWEI_P60" })
        assertTrue(models.any { it.code == "HUAWEI_P60_PRO" })
        assertTrue(models.any { it.code == "XIAOMI_13T" })
        assertTrue(models.any { it.code == "REDMI_NOTE_13_PRO_PLUS" })
        assertTrue(models.any { it.code == "POCO_F6_PRO" })
        assertTrue(models.any { it.code == "NOTHING_PHONE_1" })
        assertTrue(colorValues.any { it.canonicalCode == "GRAY" })
        assertTrue(colorValues.any { it.canonicalCode == "DESERT_TITANIUM" })
        assertTrue(repository.listCanonicalValuesAnyScope("memory_gb").any { it.canonicalCode == "256" })
        assertTrue(repository.listCanonicalValuesAnyScope("ram_gb").any { it.canonicalCode == "12" })
        assertTrue(repository.listCanonicalValuesAnyScope("dual_sim").any { it.canonicalCode == "TRUE" })
        assertTrue(repository.listCanonicalValuesAnyScope("refresh_rate_hz").any { it.canonicalCode == "120" })
        assertTrue(repository.listCanonicalValuesAnyScope("chipset_family").any { it.canonicalCode == "SNAPDRAGON_8_GEN_3" })
        assertTrue(
            report.artifacts.productFamilies.families.any { family ->
                family.familyCode == "SONY_BRAVIA" && family.defaultCategoryCode == "TECH.TV_HOME_THEATER"
            },
        )
        assertTrue(invalidator.events.any { it.reason == "serving_artifacts_synced" })
    }

    @Test
    fun refreshSurface_bootstrapsPhonesSources_and_recordsRunsAndPublishEvents() = runBlocking {
        requireDocker()

        val repository = CatalogGovernanceRepositoryImpl()
        val invalidator = RecordingCatalogGovernanceRuntimeInvalidator()
        val projectionService = CatalogGovernanceServingProjectionService(
            repository = repository,
            runtimeInvalidatorProvider = { invalidator },
        )
        val syncService = CatalogGovernanceCuratedSeedSyncService(
            repository = repository,
            projectionService = projectionService,
        )
        val refreshRepository = DatabaseCatalogGovernanceRefreshRepository()
        val workflowService = CatalogGovernanceWorkflowService(repository = repository)
        val surfaceService = CatalogGovernanceRefreshSurfaceServiceImpl(
            refreshRepository = refreshRepository,
            governanceRepository = repository,
            workflowService = workflowService,
            projectionService = projectionService,
            curatedSeedSyncService = syncService,
            phoneModelEnrichmentService = NoopCatalogPhoneModelEnrichmentService,
            refreshConfig = CatalogGovernanceRefreshConfig(
                enabled = true,
                pollIntervalMs = 60_000L,
                categoryCode = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
                connectorTypes = setOf(CatalogGovernanceRefreshConnectorType.OFFICIAL_PHONE_WEB_SOURCE),
                registryCodeAllowlist = emptySet(),
                trigger = "SCHEDULED",
            ),
            connectors = listOf(
                CatalogGovernanceCuratedSeedPackConnector(),
                CatalogGovernanceOfficialPhonesConnector(
                    fetcher = object : CatalogGovernanceOfficialPageFetcher {
                        override suspend fun fetchText(uri: String): String =
                            when (uri) {
                                "https://www.apple.com/shop/buy-iphone/iphone-16" ->
                                    """{"name":"iPhone 16 128GB Ultramarine"}{"name":"iPhone 16 256GB Teal"}{"name":"iPhone 16 Plus 128GB Pink"}"""

                                "https://www.apple.com/shop/buy-iphone/iphone-17-pro" ->
                                    """{"name":"iPhone 17 Pro 256GB Deep Blue"}{"name":"iPhone 17 Pro 512GB Cosmic Orange"}{"name":"iPhone 17 Pro Max 256GB Silver"}"""

                                "https://www.samsung.com/us/smartphones/galaxy-s24/buy/" ->
                                    "\"productTitle\":\"Galaxy S24 128GB (Unlocked)\" What colors do Galaxy S24 and S24+ come in? Galaxy S24 and S24+ come in Cobalt Violet, Amber Yellow, Onyx Black and Marble Gray."

                                "https://www.samsung.com/us/smartphones/galaxy-s24-ultra/buy/" ->
                                    "aria-label=\"256GB\" aria-label=\"512GB\" aria-label=\"1TB\" aria-label=\"Color Variant: Titanium Black\""

                                "https://support.google.com/pixelphone/answer/7158570?hl=en" ->
                                    """Pixel 9</a><div><table><p><strong>Memory</strong></p><ul><li>12 GB RAM</li></ul><p><strong>Storage</strong></p><ul><li>128 GB / 256 GB</li></ul><tr><th><strong>Colors</strong></th><td><ul><li>Obsidian</li><li>Porcelain</li></ul></td></tr><p>Google Tensor G4</p><p>1-120Hz</p></table>Pixel 9 Pro</a><div><table><p><strong>Memory</strong></p><ul><li>16 GB RAM</li></ul><p><strong>Storage</strong></p><ul><li>128 GB / 256 GB / 512 GB / 1 TB</li></ul><tr><th><strong>Colors</strong></th><td><ul><li>Obsidian</li><li>Hazel</li></ul></td></tr><p>Google Tensor G4</p><p>1-120Hz</p></table>Pixel 9 Pro XL</a><div><table><p><strong>Memory</strong></p><ul><li>16 GB RAM</li></ul><p><strong>Storage</strong></p><ul><li>128 GB / 256 GB / 512 GB / 1 TB</li></ul><tr><th><strong>Colors</strong></th><td><ul><li>Obsidian</li><li>Rose Quartz</li></ul></td></tr><p>Google Tensor G4</p><p>1-120Hz</p></table>"""

                                else -> error("Missing test HTML for '$uri'.")
                            }
                    },
                ),
            ),
        )

        val sources = surfaceService.ensurePhonesSourceRegistry()
        syncService.syncTopCategoryCoverage()
        val appleLikeSource = sources.firstOrNull { it.sourceCode == "APPLE_OFFICIAL_PHONES" }
        assertNotNull(appleLikeSource)

        val refresh = surfaceService.triggerRefresh(
            categoryCode = "TECH.PHONES",
            registryCodes = listOf(appleLikeSource.registryCode),
            trigger = "MANUAL_TEST",
        )

        assertEquals("TECH.PHONES", refresh.categoryCode)
        assertEquals(1, refresh.runs.size)
        assertEquals("COMPLETED", refresh.runs.single().status)
        assertEquals("PUBLISHED", refresh.runs.single().publishStatus)
        assertEquals("APPLE_OFFICIAL_PHONES", refresh.runs.single().metadata["officialSourceCode"])

        val persistedRuns = refreshRepository.listRefreshRuns(categoryCode = "TECH.PHONES", limit = 10)
        val persistedEvents = refreshRepository.listPublishEvents(categoryCode = "TECH.PHONES", limit = 10)

        assertTrue(persistedRuns.any { it.registryCode == appleLikeSource.registryCode })
        assertTrue(
            persistedEvents.any { event ->
                event.eventType == "REFRESH_SYNC" &&
                    event.status == CatalogGovernancePublishStatus.PUBLISHED
            },
        )
        assertTrue(invalidator.events.any { it.reason == "serving_artifacts_synced" })
    }

    @Test
    fun refreshSurface_review_actions_approve_promote_and_reject_candidates() = runBlocking {
        requireDocker()

        val repository = CatalogGovernanceRepositoryImpl()
        val invalidator = RecordingCatalogGovernanceRuntimeInvalidator()
        val projectionService = CatalogGovernanceServingProjectionService(
            repository = repository,
            runtimeInvalidatorProvider = { invalidator },
        )
        val syncService = CatalogGovernanceCuratedSeedSyncService(
            repository = repository,
            projectionService = projectionService,
        )
        val refreshRepository = DatabaseCatalogGovernanceRefreshRepository()
        val workflowService = CatalogGovernanceWorkflowService(repository = repository)
        val surfaceService = CatalogGovernanceRefreshSurfaceServiceImpl(
            refreshRepository = refreshRepository,
            governanceRepository = repository,
            workflowService = workflowService,
            projectionService = projectionService,
            curatedSeedSyncService = syncService,
            phoneModelEnrichmentService = NoopCatalogPhoneModelEnrichmentService,
            refreshConfig = CatalogGovernanceRefreshConfig(
                enabled = true,
                pollIntervalMs = 60_000L,
                categoryCode = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
                connectorTypes = setOf(CatalogGovernanceRefreshConnectorType.OFFICIAL_PHONE_WEB_SOURCE),
                registryCodeAllowlist = emptySet(),
                trigger = "SCHEDULED",
            ),
            connectors = emptyList(),
        )
        syncService.syncTopCategoryCoverage()
        val now = System.currentTimeMillis()
        val source = repository.upsertSourceSnapshot(
            CatalogGovernanceSourceSnapshot(
                sourceCode = "review-surface-test",
                externalRef = "phones/manual-review",
                displayName = "Review Surface Test",
                tier = CatalogGovernanceSourceTier.AUTHORITATIVE,
                defaultLocale = "en-US",
                marketCode = "US",
                capturedAt = now,
            ),
        )
        val reviewCandidate = repository.submitCandidate(
            CatalogValueCandidate(
                attributeCode = "color",
                locale = "en-US",
                marketCode = "US",
                rawValue = "Aurora Blue Review",
                normalizedValue = "aurora blue review",
                proposedCanonicalCode = "AURORA_BLUE_REVIEW",
                proposedCanonicalValue = "Aurora Blue Review",
                proposedLabels = localizedTextOf("en" to "Aurora Blue Review"),
                proposedCanonicalLocale = "en",
                scope = CatalogGovernanceScope(categoryCode = "TECH.PHONES"),
                sourceSnapshotId = source.id,
                status = CatalogGovernanceCandidateStatus.REVIEWING,
                autoConfidence = 0.41,
                evidenceCount = 5,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val rejectCandidate = repository.submitCandidate(
            CatalogValueCandidate(
                attributeCode = "color",
                locale = "en-US",
                marketCode = "US",
                rawValue = "Rejected Gray Review",
                normalizedValue = "rejected gray review",
                proposedCanonicalCode = "REJECTED_GRAY_REVIEW",
                proposedCanonicalValue = "Rejected Gray Review",
                proposedLabels = localizedTextOf("en" to "Rejected Gray Review"),
                proposedCanonicalLocale = "en",
                scope = CatalogGovernanceScope(categoryCode = "TECH.PHONES"),
                sourceSnapshotId = source.id,
                status = CatalogGovernanceCandidateStatus.REVIEWING,
                autoConfidence = 0.33,
                evidenceCount = 4,
                createdAt = now,
                updatedAt = now,
            ),
        )

        val approveResponse = surfaceService.submitReviewAction(
            categoryCode = "TECH.PHONES",
            candidateId = reviewCandidate.id ?: error("candidate id missing"),
            action = CatalogGovernanceReviewAction.APPROVE,
            actor = "test-reviewer",
            reasonCode = "manual_quality_gate",
        )
        assertEquals("APPROVED", approveResponse.candidateStatus)
        assertEquals("APPROVE", approveResponse.decisionAction)

        val promoteResponse = surfaceService.submitReviewAction(
            categoryCode = "TECH.PHONES",
            candidateId = reviewCandidate.id ?: error("candidate id missing"),
            action = CatalogGovernanceReviewAction.PROMOTE,
            actor = "test-reviewer",
            reasonCode = "manual_publish",
        )
        assertEquals("PROMOTED", promoteResponse.candidateStatus)
        assertEquals("PROMOTE", promoteResponse.decisionAction)
        assertEquals("AURORA_BLUE_REVIEW", promoteResponse.canonicalCode)

        val rejectResponse = surfaceService.submitReviewAction(
            categoryCode = "TECH.PHONES",
            candidateId = rejectCandidate.id ?: error("candidate id missing"),
            action = CatalogGovernanceReviewAction.REJECT,
            actor = "test-reviewer",
            reasonCode = "noise_value",
        )
        assertEquals("REJECTED", rejectResponse.candidateStatus)
        assertEquals("REJECT", rejectResponse.decisionAction)

        val persistedCandidates = repository.listCandidates()
        assertEquals(
            CatalogGovernanceCandidateStatus.PROMOTED,
            persistedCandidates.first { it.id == reviewCandidate.id }.status,
        )
        assertEquals(
            CatalogGovernanceCandidateStatus.REJECTED,
            persistedCandidates.first { it.id == rejectCandidate.id }.status,
        )
        assertTrue(repository.listCanonicalValuesAnyScope("color").any { it.canonicalCode == "AURORA_BLUE_REVIEW" })

        val publishEvents = refreshRepository.listPublishEvents(categoryCode = "TECH.PHONES", limit = 20)
        assertTrue(publishEvents.any { it.eventType == "REVIEW_APPROVE" && it.entityRef == reviewCandidate.id.toString() })
        assertTrue(publishEvents.any { it.eventType == "REVIEW_PROMOTE" && it.entityRef == reviewCandidate.id.toString() })
        assertTrue(publishEvents.any { it.eventType == "REVIEW_REJECT" && it.entityRef == rejectCandidate.id.toString() })
        assertTrue(invalidator.events.any { it.reason == "serving_artifacts_synced" })
    }

    @Test
    fun refreshSurface_liveOfficialPhones_sourceBySource_smoke() = runBlocking {
        requireDocker()
        requireLiveOfficialRefreshEnabled()

        val repository = CatalogGovernanceRepositoryImpl()
        val invalidator = RecordingCatalogGovernanceRuntimeInvalidator()
        val projectionService = CatalogGovernanceServingProjectionService(
            repository = repository,
            runtimeInvalidatorProvider = { invalidator },
        )
        val syncService = CatalogGovernanceCuratedSeedSyncService(
            repository = repository,
            projectionService = projectionService,
        )
        val refreshRepository = DatabaseCatalogGovernanceRefreshRepository()
        val workflowService = CatalogGovernanceWorkflowService(repository = repository)
        val surfaceService = CatalogGovernanceRefreshSurfaceServiceImpl(
            refreshRepository = refreshRepository,
            governanceRepository = repository,
            workflowService = workflowService,
            projectionService = projectionService,
            curatedSeedSyncService = syncService,
            phoneModelEnrichmentService = NoopCatalogPhoneModelEnrichmentService,
            refreshConfig = CatalogGovernanceRefreshConfig(
                enabled = true,
                pollIntervalMs = 60_000L,
                categoryCode = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
                connectorTypes = setOf(CatalogGovernanceRefreshConnectorType.OFFICIAL_PHONE_WEB_SOURCE),
                registryCodeAllowlist = emptySet(),
                trigger = "SCHEDULED",
            ),
            connectors = listOf(
                CatalogGovernanceCuratedSeedPackConnector(),
                CatalogGovernanceOfficialPhonesConnector(
                    fetcher = HttpCatalogGovernanceOfficialPageFetcher(),
                ),
            ),
        )
        syncService.syncTopCategoryCoverage()
        val sources = surfaceService.ensurePhonesSourceRegistry()
            .filter { it.connectorType == CatalogGovernanceRefreshConnectorType.OFFICIAL_PHONE_WEB_SOURCE.name }
            .filter { it.sourceCode in setOf("APPLE_OFFICIAL_PHONES", "SAMSUNG_OFFICIAL_PHONES", "GOOGLE_OFFICIAL_PHONES") }
            .sortedBy { it.sourceCode }
        assertEquals(3, sources.size)

        val runsBySource = linkedMapOf<String, CatalogGovernanceRefreshRunResponse>()
        sources.forEach { source ->
            val refresh = surfaceService.triggerRefresh(
                categoryCode = "TECH.PHONES",
                registryCodes = listOf(source.registryCode),
                trigger = "LIVE_SOURCE_SMOKE",
            )
            val run = refresh.runs.single()
            runsBySource[source.sourceCode] = run
            println(
                "LIVE_OFFICIAL_REFRESH source=${source.sourceCode} " +
                    "status=${run.status} publish=${run.publishStatus} " +
                    "models=${run.modelsSynced} values=${run.canonicalValuesSynced} " +
                    "aliases=${run.aliasesSynced} reviewQueue=${run.reviewQueueSize}",
            )
        }

        assertEquals(
            setOf("APPLE_OFFICIAL_PHONES", "SAMSUNG_OFFICIAL_PHONES", "GOOGLE_OFFICIAL_PHONES"),
            runsBySource.keys.toSet(),
        )
        assertTrue(runsBySource.values.all { it.status == CatalogGovernanceRefreshRunStatus.COMPLETED.name })
        assertTrue(runsBySource.values.all { it.publishStatus == CatalogGovernancePublishStatus.PUBLISHED.name })
        assertTrue(runsBySource.values.all { it.modelsSynced > 0 })
        assertTrue(runsBySource.values.all { it.canonicalValuesSynced > 0 })
        assertTrue(runsBySource.values.all { it.aliasesSynced > 0 })

        val publishEvents = refreshRepository.listPublishEvents(categoryCode = "TECH.PHONES", limit = 50)
        assertTrue(
            publishEvents.any { it.eventType == "REFRESH_SYNC" && it.entityRef == sources.first { source -> source.sourceCode == "APPLE_OFFICIAL_PHONES" }.registryCode },
        )
        assertTrue(
            publishEvents.any { it.eventType == "REFRESH_SYNC" && it.entityRef == sources.first { source -> source.sourceCode == "SAMSUNG_OFFICIAL_PHONES" }.registryCode },
        )
        assertTrue(
            publishEvents.any { it.eventType == "REFRESH_SYNC" && it.entityRef == sources.first { source -> source.sourceCode == "GOOGLE_OFFICIAL_PHONES" }.registryCode },
        )
        assertTrue(invalidator.events.any { it.reason == "serving_artifacts_synced" })
    }

    @Test
    fun refreshSurface_liveOfficialPhones_publishes_runtime_known_values_and_aliases_for_results_facets() = runBlocking {
        requireDocker()
        requireLiveOfficialRefreshEnabled()

        val repository = CatalogGovernanceRepositoryImpl()
        val invalidator = RecordingCatalogGovernanceRuntimeInvalidator()
        val projectionService = CatalogGovernanceServingProjectionService(
            repository = repository,
            runtimeInvalidatorProvider = { invalidator },
        )
        val syncService = CatalogGovernanceCuratedSeedSyncService(
            repository = repository,
            projectionService = projectionService,
        )
        val refreshRepository = DatabaseCatalogGovernanceRefreshRepository()
        val workflowService = CatalogGovernanceWorkflowService(repository = repository)
        val surfaceService = CatalogGovernanceRefreshSurfaceServiceImpl(
            refreshRepository = refreshRepository,
            governanceRepository = repository,
            workflowService = workflowService,
            projectionService = projectionService,
            curatedSeedSyncService = syncService,
            phoneModelEnrichmentService = NoopCatalogPhoneModelEnrichmentService,
            refreshConfig = CatalogGovernanceRefreshConfig(
                enabled = true,
                pollIntervalMs = 60_000L,
                categoryCode = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
                connectorTypes = setOf(CatalogGovernanceRefreshConnectorType.OFFICIAL_PHONE_WEB_SOURCE),
                registryCodeAllowlist = emptySet(),
                trigger = "SCHEDULED",
            ),
            connectors = listOf(
                CatalogGovernanceCuratedSeedPackConnector(),
                CatalogGovernanceOfficialPhonesConnector(
                    fetcher = HttpCatalogGovernanceOfficialPageFetcher(),
                ),
            ),
        )
        val liveValuesRepository = CatalogLiveValuesRepositoryImpl(governanceRepository = repository)
        syncService.syncTopCategoryCoverage()
        val sourcesByCode = surfaceService.ensurePhonesSourceRegistry()
            .filter { it.connectorType == CatalogGovernanceRefreshConnectorType.OFFICIAL_PHONE_WEB_SOURCE.name }
            .filter { it.sourceCode in setOf("APPLE_OFFICIAL_PHONES", "SAMSUNG_OFFICIAL_PHONES", "GOOGLE_OFFICIAL_PHONES") }
            .associateBy { it.sourceCode }

        suspend fun refreshSource(sourceCode: String): CatalogGovernanceRefreshRunResponse {
            val source = sourcesByCode[sourceCode]
                ?: error("Missing official source '$sourceCode'.")
            val response = surfaceService.triggerRefresh(
                categoryCode = "TECH.PHONES",
                registryCodes = listOf(source.registryCode),
                trigger = "LIVE_RUNTIME_VALUES_SMOKE",
            )
            return response.runs.single()
        }

        suspend fun loadSnapshot(
            brand: String,
            model: String,
            attributeCodes: List<String>,
        ) = liveValuesRepository.getLiveValues(
            CatalogLiveValuesRequest(
                categoryCode = "TECH.PHONES",
                brand = brand,
                model = model,
                localeTag = "en",
                attributeCodes = attributeCodes,
            ),
        )

        val appleRun = refreshSource("APPLE_OFFICIAL_PHONES")
        assertEquals(CatalogGovernanceRefreshRunStatus.COMPLETED.name, appleRun.status)
        assertEquals(CatalogGovernancePublishStatus.PUBLISHED.name, appleRun.publishStatus)
        val appleSnapshot = loadSnapshot(
            brand = "Apple",
            model = "iPhone 17 Pro",
            attributeCodes = listOf(
                "model",
                "color",
                "memory_gb",
                "os_family",
                "release_year",
                "screen_size_inch",
                "wireless_charging",
                "esim_support",
                "ip_rating",
            ),
        )
        assertTrue(appleSnapshot.knownValuesByAttributeCode["model"].orEmpty().contains("iPhone 17 Pro"))
        assertTrue(
            appleSnapshot.knownValueAliasesByAttributeCode["model"]
                .orEmpty()["iPhone 17 Pro"]
                .orEmpty()
                .contains("iphone17pro"),
        )
        assertTrue(appleSnapshot.knownValuesByAttributeCode["memory_gb"].orEmpty().isNotEmpty())
        assertTrue(appleSnapshot.knownValuesByAttributeCode["release_year"].orEmpty().contains("2026"))
        assertTrue(appleSnapshot.knownValuesByAttributeCode["screen_size_inch"].orEmpty().contains("6.3"))
        assertTrue(appleSnapshot.knownValuesByAttributeCode["wireless_charging"].orEmpty().contains("true"))
        assertTrue(appleSnapshot.knownValuesByAttributeCode["esim_support"].orEmpty().contains("true"))
        assertTrue(appleSnapshot.knownValuesByAttributeCode["ip_rating"].orEmpty().contains("IP68"))
        assertTrue(
            appleSnapshot.knownValueAliasesByAttributeCode["os_family"]
                .orEmpty()["iOS"]
                .orEmpty()
                .any { alias -> alias.equals("iphone os", ignoreCase = true) },
        )
        assertTrue(
            appleSnapshot.knownValueAliasesByAttributeCode["esim_support"]
                .orEmpty()["true"]
                .orEmpty()
                .any { alias -> alias.equals("esim", ignoreCase = true) },
        )

        val samsungRun = refreshSource("SAMSUNG_OFFICIAL_PHONES")
        assertEquals(CatalogGovernanceRefreshRunStatus.COMPLETED.name, samsungRun.status)
        assertEquals(CatalogGovernancePublishStatus.PUBLISHED.name, samsungRun.publishStatus)
        val samsungSnapshot = loadSnapshot(
            brand = "Samsung",
            model = "Galaxy S24+",
            attributeCodes = listOf(
                "model",
                "color",
                "memory_gb",
                "network_type",
                "release_year",
                "screen_size_inch",
                "battery_mah",
                "wired_charging_w",
                "wireless_charging",
                "esim_support",
                "ip_rating",
            ),
        )
        assertTrue(samsungSnapshot.knownValuesByAttributeCode["model"].orEmpty().contains("Galaxy S24+"))
        assertTrue(
            samsungSnapshot.knownValueAliasesByAttributeCode["model"]
                .orEmpty()["Galaxy S24+"]
                .orEmpty()
                .any { alias -> alias.equals("s24+", ignoreCase = true) },
        )
        assertTrue(samsungSnapshot.knownValuesByAttributeCode["color"].orEmpty().isNotEmpty())
        assertTrue(samsungSnapshot.knownValuesByAttributeCode["release_year"].orEmpty().contains("2024"))
        assertTrue(samsungSnapshot.knownValuesByAttributeCode["screen_size_inch"].orEmpty().contains("6.7"))
        assertTrue(samsungSnapshot.knownValuesByAttributeCode["battery_mah"].orEmpty().contains("4900"))
        assertTrue(samsungSnapshot.knownValuesByAttributeCode["wired_charging_w"].orEmpty().contains("45"))
        assertTrue(samsungSnapshot.knownValuesByAttributeCode["wireless_charging"].orEmpty().contains("true"))
        assertTrue(samsungSnapshot.knownValuesByAttributeCode["esim_support"].orEmpty().contains("true"))
        assertTrue(samsungSnapshot.knownValuesByAttributeCode["ip_rating"].orEmpty().contains("IP68"))
        assertTrue(
            samsungSnapshot.knownValuesByAttributeCode["network_type"]
                .orEmpty()
                .any { value -> value.equals("5G", ignoreCase = true) },
        )

        val googleRun = refreshSource("GOOGLE_OFFICIAL_PHONES")
        assertEquals(CatalogGovernanceRefreshRunStatus.COMPLETED.name, googleRun.status)
        assertEquals(CatalogGovernancePublishStatus.PUBLISHED.name, googleRun.publishStatus)
        val googleSnapshot = loadSnapshot(
            brand = "Google",
            model = "Pixel 9 Pro",
            attributeCodes = listOf(
                "model",
                "chipset_family",
                "color",
                "memory_gb",
                "release_year",
                "screen_size_inch",
                "battery_mah",
                "wired_charging_w",
                "wireless_charging",
                "esim_support",
                "ip_rating",
            ),
        )
        assertTrue(googleSnapshot.knownValuesByAttributeCode["model"].orEmpty().contains("Pixel 9 Pro"))
        assertTrue(
            googleSnapshot.knownValueAliasesByAttributeCode["model"]
                .orEmpty()["Pixel 9 Pro"]
                .orEmpty()
                .contains("pixel9pro"),
        )
        assertTrue(
            googleSnapshot.knownValuesByAttributeCode["chipset_family"]
                .orEmpty()
                .any { value -> value.equals("Tensor G4", ignoreCase = true) },
        )
        assertTrue(
            googleSnapshot.knownValueAliasesByAttributeCode["chipset_family"]
                .orEmpty()["Tensor G4"]
                .orEmpty()
                .contains("googletensorg4"),
        )
        assertTrue(googleSnapshot.knownValuesByAttributeCode["release_year"].orEmpty().contains("2024"))
        assertTrue(googleSnapshot.knownValuesByAttributeCode["screen_size_inch"].orEmpty().contains("6.3"))
        assertTrue(googleSnapshot.knownValuesByAttributeCode["battery_mah"].orEmpty().contains("4700"))
        assertTrue(googleSnapshot.knownValuesByAttributeCode["wired_charging_w"].orEmpty().contains("27"))
        assertTrue(googleSnapshot.knownValuesByAttributeCode["wireless_charging"].orEmpty().contains("true"))
        assertTrue(googleSnapshot.knownValuesByAttributeCode["esim_support"].orEmpty().contains("true"))
        assertTrue(googleSnapshot.knownValuesByAttributeCode["ip_rating"].orEmpty().contains("IP68"))

        assertTrue(invalidator.events.any { it.reason == "serving_artifacts_synced" })
    }

    @Test
    fun refreshSurface_officialNextWave_publishes_runtime_known_values_and_aliases_for_results_facets() = runBlocking {
        requireDocker()

        val repository = CatalogGovernanceRepositoryImpl()
        val invalidator = RecordingCatalogGovernanceRuntimeInvalidator()
        val projectionService = CatalogGovernanceServingProjectionService(
            repository = repository,
            runtimeInvalidatorProvider = { invalidator },
        )
        val syncService = CatalogGovernanceCuratedSeedSyncService(
            repository = repository,
            projectionService = projectionService,
        )
        val refreshRepository = DatabaseCatalogGovernanceRefreshRepository()
        val workflowService = CatalogGovernanceWorkflowService(repository = repository)
        val surfaceService = CatalogGovernanceRefreshSurfaceServiceImpl(
            refreshRepository = refreshRepository,
            governanceRepository = repository,
            workflowService = workflowService,
            projectionService = projectionService,
            curatedSeedSyncService = syncService,
            phoneModelEnrichmentService = NoopCatalogPhoneModelEnrichmentService,
            refreshConfig = CatalogGovernanceRefreshConfig(
                enabled = true,
                pollIntervalMs = 60_000L,
                categoryCode = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
                connectorTypes = setOf(CatalogGovernanceRefreshConnectorType.OFFICIAL_PHONE_WEB_SOURCE),
                registryCodeAllowlist = emptySet(),
                trigger = "SCHEDULED",
            ),
            connectors = listOf(
                CatalogGovernanceCuratedSeedPackConnector(),
                CatalogGovernanceOfficialPhonesConnector(
                    fetcher = FakeOfficialPageFetcher(
                        mapOf(
                            "https://www.mi.com/global/product/xiaomi-14/specs/" to XIAOMI_14_HTML,
                            "https://www.mi.com/global/product/poco-x7-pro/specs/" to POCO_X7_PRO_HTML,
                            "https://www.mi.com/global/product/redmi-note-14-pro-plus-5g/specs/" to REDMI_NOTE_14_PRO_PLUS_HTML,
                            "https://www.oneplus.com/us/13/specs" to ONEPLUS_13_HTML,
                            "https://www.oneplus.com/us/12/specs" to ONEPLUS_12_HTML,
                            "https://www.oneplus.com/global/nord-4/specs" to ONEPLUS_NORD_4_HTML,
                            "https://nothing.tech/products/phone-2" to NOTHING_PHONE_2_HTML,
                            "https://checkout.nothing.tech/pages/phone-2a" to NOTHING_PHONE_2A_HTML,
                            "https://intl.nothing.tech/products/phone-1" to NOTHING_PHONE_1_HTML,
                        ),
                    ),
                ),
            ),
        )
        val liveValuesRepository = CatalogLiveValuesRepositoryImpl(governanceRepository = repository)
        syncService.syncTopCategoryCoverage()
        val sourcesByCode = surfaceService.ensurePhonesSourceRegistry()
            .filter { it.connectorType == CatalogGovernanceRefreshConnectorType.OFFICIAL_PHONE_WEB_SOURCE.name }
            .filter { it.sourceCode in setOf("XIAOMI_OFFICIAL_PHONES", "ONEPLUS_OFFICIAL_PHONES", "NOTHING_OFFICIAL_PHONES") }
            .associateBy { it.sourceCode }

        suspend fun refreshSource(sourceCode: String): CatalogGovernanceRefreshRunResponse {
            val source = sourcesByCode[sourceCode]
                ?: error("Missing official source '$sourceCode'.")
            val response = surfaceService.triggerRefresh(
                categoryCode = "TECH.PHONES",
                registryCodes = listOf(source.registryCode),
                trigger = "NEXT_WAVE_RUNTIME_VALUES_SMOKE",
            )
            return response.runs.single().also { run ->
                println(
                    "NEXT_WAVE_REFRESH source=$sourceCode " +
                        "status=${run.status} publish=${run.publishStatus} " +
                        "models=${run.modelsSynced} values=${run.canonicalValuesSynced} " +
                        "aliases=${run.aliasesSynced} reviewQueue=${run.reviewQueueSize} " +
                        "error=${run.errorMessage}",
                )
            }
        }

        suspend fun loadSnapshot(
            brand: String,
            model: String,
            attributeCodes: List<String>,
        ) = liveValuesRepository.getLiveValues(
            CatalogLiveValuesRequest(
                categoryCode = "TECH.PHONES",
                brand = brand,
                model = model,
                localeTag = "en",
                attributeCodes = attributeCodes,
            ),
        )

        val xiaomiRun = refreshSource("XIAOMI_OFFICIAL_PHONES")
        assertEquals(CatalogGovernanceRefreshRunStatus.COMPLETED.name, xiaomiRun.status)
        assertEquals(CatalogGovernancePublishStatus.PUBLISHED.name, xiaomiRun.publishStatus)
        val xiaomiSnapshot = loadSnapshot(
            brand = "Xiaomi",
            model = "Xiaomi 14",
            attributeCodes = listOf(
                "model",
                "color",
                "memory_gb",
                "release_year",
                "screen_size_inch",
                "battery_mah",
                "wired_charging_w",
                "wireless_charging",
                "esim_support",
                "ip_rating",
            ),
        )
        assertTrue(xiaomiSnapshot.knownValuesByAttributeCode["model"].orEmpty().contains("Xiaomi 14"))
        assertTrue(
            xiaomiSnapshot.knownValueAliasesByAttributeCode["model"]
                .orEmpty()["Xiaomi 14"]
                .orEmpty()
                .contains("xiaomi14"),
        )
        assertTrue(xiaomiSnapshot.knownValuesByAttributeCode["color"].orEmpty().contains("Green"))
        assertTrue(
            xiaomiSnapshot.knownValueAliasesByAttributeCode["color"]
                .orEmpty()["Green"]
                .orEmpty()
                .any { alias -> alias.equals("jade green", ignoreCase = true) },
        )
        assertTrue(xiaomiSnapshot.knownValuesByAttributeCode["release_year"].orEmpty().contains("2024"))
        assertTrue(xiaomiSnapshot.knownValuesByAttributeCode["screen_size_inch"].orEmpty().contains("6.36"))
        assertTrue(xiaomiSnapshot.knownValuesByAttributeCode["battery_mah"].orEmpty().contains("4610"))
        assertTrue(xiaomiSnapshot.knownValuesByAttributeCode["wired_charging_w"].orEmpty().contains("90"))
        assertTrue(xiaomiSnapshot.knownValuesByAttributeCode["wireless_charging"].orEmpty().contains("true"))
        assertTrue(xiaomiSnapshot.knownValuesByAttributeCode["esim_support"].orEmpty().contains("true"))
        assertTrue(xiaomiSnapshot.knownValuesByAttributeCode["ip_rating"].orEmpty().contains("IP68"))

        val oneplusRun = refreshSource("ONEPLUS_OFFICIAL_PHONES")
        assertEquals(CatalogGovernanceRefreshRunStatus.COMPLETED.name, oneplusRun.status)
        assertEquals(CatalogGovernancePublishStatus.PUBLISHED.name, oneplusRun.publishStatus)
        val oneplusSnapshot = loadSnapshot(
            brand = "OnePlus",
            model = "OnePlus 13",
            attributeCodes = listOf(
                "model",
                "color",
                "memory_gb",
                "chipset_family",
                "battery_mah",
                "wired_charging_w",
                "wireless_charging",
                "esim_support",
                "ip_rating",
            ),
        )
        assertTrue(oneplusSnapshot.knownValuesByAttributeCode["model"].orEmpty().contains("OnePlus 13"))
        assertTrue(
            oneplusSnapshot.knownValueAliasesByAttributeCode["model"]
                .orEmpty()["OnePlus 13"]
                .orEmpty()
                .contains("oneplus13"),
        )
        assertTrue(
            oneplusSnapshot.knownValuesByAttributeCode["chipset_family"]
                .orEmpty()
                .any { value -> value.equals("Snapdragon 8 Elite", ignoreCase = true) },
        )
        assertTrue(oneplusSnapshot.knownValuesByAttributeCode["color"].orEmpty().contains("Blue"))
        assertTrue(
            oneplusSnapshot.knownValueAliasesByAttributeCode["color"]
                .orEmpty()["Blue"]
                .orEmpty()
                .any { alias -> alias.equals("midnight ocean", ignoreCase = true) },
        )
        assertTrue(oneplusSnapshot.knownValuesByAttributeCode["battery_mah"].orEmpty().contains("6000"))
        assertTrue(oneplusSnapshot.knownValuesByAttributeCode["wired_charging_w"].orEmpty().contains("80"))
        assertTrue(oneplusSnapshot.knownValuesByAttributeCode["wireless_charging"].orEmpty().contains("true"))
        assertTrue(oneplusSnapshot.knownValuesByAttributeCode["esim_support"].orEmpty().contains("true"))
        assertTrue(oneplusSnapshot.knownValuesByAttributeCode["ip_rating"].orEmpty().contains("IP68"))

        val nothingRun = refreshSource("NOTHING_OFFICIAL_PHONES")
        assertEquals(CatalogGovernanceRefreshRunStatus.COMPLETED.name, nothingRun.status)
        assertEquals(CatalogGovernancePublishStatus.PUBLISHED.name, nothingRun.publishStatus)
        val nothingSnapshot = loadSnapshot(
            brand = "Nothing",
            model = "Nothing Phone (2)",
            attributeCodes = listOf(
                "model",
                "color",
                "memory_gb",
                "ram_gb",
                "battery_mah",
                "wired_charging_w",
                "refresh_rate_hz",
            ),
        )
        assertTrue(nothingSnapshot.knownValuesByAttributeCode["model"].orEmpty().contains("Nothing Phone (2)"))
        assertTrue(
            nothingSnapshot.knownValueAliasesByAttributeCode["model"]
                .orEmpty()["Nothing Phone (2)"]
                .orEmpty()
                .contains("nothing2"),
        )
        assertTrue(nothingSnapshot.knownValuesByAttributeCode["memory_gb"].orEmpty().contains("512"))
        assertTrue(nothingSnapshot.knownValuesByAttributeCode["color"].orEmpty().contains("Gray"))
        assertTrue(
            nothingSnapshot.knownValueAliasesByAttributeCode["color"]
                .orEmpty()["Gray"]
                .orEmpty()
                .any { alias -> alias.equals("dark grey", ignoreCase = true) },
        )
        val nothing2aSnapshot = loadSnapshot(
            brand = "Nothing",
            model = "Nothing Phone (2a)",
            attributeCodes = listOf("battery_mah", "wired_charging_w"),
        )
        assertTrue(nothing2aSnapshot.knownValuesByAttributeCode["battery_mah"].orEmpty().contains("5000"))
        assertTrue(nothing2aSnapshot.knownValuesByAttributeCode["wired_charging_w"].orEmpty().contains("45"))
        val nothing1Snapshot = loadSnapshot(
            brand = "Nothing",
            model = "Nothing Phone (1)",
            attributeCodes = listOf("ram_gb", "refresh_rate_hz"),
        )
        assertTrue(nothing1Snapshot.knownValuesByAttributeCode["ram_gb"].orEmpty().contains("8"))
        assertTrue(nothing1Snapshot.knownValuesByAttributeCode["refresh_rate_hz"].orEmpty().contains("120"))

        assertTrue(invalidator.events.any { it.reason == "serving_artifacts_synced" })
    }

    @Test
    fun refreshSurface_self_heals_runtime_contract_before_syncing_richer_phone_values() = runBlocking {
        requireDocker()

        val repository = CatalogGovernanceRepositoryImpl()
        val invalidator = RecordingCatalogGovernanceRuntimeInvalidator()
        val projectionService = CatalogGovernanceServingProjectionService(
            repository = repository,
            runtimeInvalidatorProvider = { invalidator },
        )
        val syncService = CatalogGovernanceCuratedSeedSyncService(
            repository = repository,
            projectionService = projectionService,
        )
        val refreshRepository = DatabaseCatalogGovernanceRefreshRepository()
        val workflowService = CatalogGovernanceWorkflowService(repository = repository)
        val surfaceService = CatalogGovernanceRefreshSurfaceServiceImpl(
            refreshRepository = refreshRepository,
            governanceRepository = repository,
            workflowService = workflowService,
            projectionService = projectionService,
            curatedSeedSyncService = syncService,
            phoneModelEnrichmentService = NoopCatalogPhoneModelEnrichmentService,
            refreshConfig = CatalogGovernanceRefreshConfig(
                enabled = true,
                pollIntervalMs = 60_000L,
                categoryCode = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
                connectorTypes = setOf(CatalogGovernanceRefreshConnectorType.OFFICIAL_PHONE_WEB_SOURCE),
                registryCodeAllowlist = emptySet(),
                trigger = "SCHEDULED",
            ),
            connectors = listOf(
                CatalogGovernanceCuratedSeedPackConnector(),
                CatalogGovernanceOfficialPhonesConnector(
                    fetcher = FakeOfficialPageFetcher(
                        mapOf(
                            "https://www.samsung.com/us/smartphones/galaxy-s24/buy/" to SAMSUNG_GALAXY_S24_PLUS_HTML,
                            "https://www.samsung.com/us/smartphones/galaxy-s24-ultra/buy/" to """
                                Samsung Galaxy S24 Ultra Specs
                                Titanium Black Titanium Gray Titanium Violet Titanium Yellow
                                12GB+256GB, 12GB+512GB, 12GB+1TB
                            """.trimIndent(),
                        ),
                    ),
                ),
            ),
        )
        val catalogRepository = CatalogRepositoryImpl()
        val liveValuesRepository = CatalogLiveValuesRepositoryImpl(governanceRepository = repository)

        DatabaseFactory.dbQuery {
            val richerAttributeCodes = listOf(
                "release_year",
                "screen_size_inch",
                "battery_mah",
                "wired_charging_w",
                "wireless_charging",
                "esim_support",
                "ip_rating",
            )
            CatalogGovernanceValueCanonTable.deleteWhere {
                CatalogGovernanceValueCanonTable.attributeCode inList richerAttributeCodes
            }
            CategoryAttributesTable.deleteWhere {
                (CategoryAttributesTable.categoryCode eq "TECH.PHONES") and
                    (CategoryAttributesTable.attributeCode inList richerAttributeCodes)
            }
            CatalogStage4TypedConstraintsTable.deleteWhere {
                CatalogStage4TypedConstraintsTable.attributeCode inList richerAttributeCodes
            }
            CatalogStage4NormalizationRulesTable.deleteWhere {
                CatalogStage4NormalizationRulesTable.attributeCode inList richerAttributeCodes
            }
            CatalogStage4ImmutableAttributesTable.deleteWhere {
                CatalogStage4ImmutableAttributesTable.attributeCode inList richerAttributeCodes
            }
            AttributeValueDictTable.deleteWhere {
                AttributeValueDictTable.attributeCode inList richerAttributeCodes
            }
            AttributeDefsTable.deleteWhere {
                AttributeDefsTable.code inList richerAttributeCodes
            }
        }

        val samsungSource = surfaceService.ensurePhonesSourceRegistry()
            .first { it.sourceCode == "SAMSUNG_OFFICIAL_PHONES" }

        val response = surfaceService.triggerRefresh(
            categoryCode = "TECH.PHONES",
            registryCodes = listOf(samsungSource.registryCode),
            trigger = "SELF_HEAL_RUNTIME_CONTRACT",
        )
        val samsungRun = response.runs.single()

        assertEquals(CatalogGovernanceRefreshRunStatus.COMPLETED.name, samsungRun.status)
        assertEquals(CatalogGovernancePublishStatus.PUBLISHED.name, samsungRun.publishStatus)

        val effectiveCodes = catalogRepository.getCategoryEffectiveSpec("TECH.PHONES")
            ?.attributes
            .orEmpty()
            .map { it.code }
            .toSet()
        assertTrue("release_year" in effectiveCodes)
        assertTrue("screen_size_inch" in effectiveCodes)
        assertTrue("battery_mah" in effectiveCodes)
        assertTrue("wired_charging_w" in effectiveCodes)
        assertTrue("wireless_charging" in effectiveCodes)
        assertTrue("esim_support" in effectiveCodes)
        assertTrue("ip_rating" in effectiveCodes)

        val samsungSnapshot = liveValuesRepository.getLiveValues(
            CatalogLiveValuesRequest(
                categoryCode = "TECH.PHONES",
                brand = "Samsung",
                model = "Galaxy S24+",
                localeTag = "en",
                attributeCodes = listOf(
                    "release_year",
                    "screen_size_inch",
                    "battery_mah",
                    "wired_charging_w",
                    "wireless_charging",
                    "esim_support",
                    "ip_rating",
                ),
            ),
        )
        assertTrue(samsungSnapshot.knownValuesByAttributeCode["release_year"].orEmpty().contains("2024"))
        assertTrue(samsungSnapshot.knownValuesByAttributeCode["screen_size_inch"].orEmpty().contains("6.7"))
        assertTrue(samsungSnapshot.knownValuesByAttributeCode["battery_mah"].orEmpty().contains("4900"))
        assertTrue(samsungSnapshot.knownValuesByAttributeCode["wired_charging_w"].orEmpty().contains("45"))
        assertTrue(samsungSnapshot.knownValuesByAttributeCode["wireless_charging"].orEmpty().contains("true"))
        assertTrue(samsungSnapshot.knownValuesByAttributeCode["esim_support"].orEmpty().contains("true"))
        assertTrue(samsungSnapshot.knownValuesByAttributeCode["ip_rating"].orEmpty().contains("IP68"))
    }

    private class RecordingCatalogGovernanceRuntimeInvalidator : CatalogGovernanceRuntimeInvalidator {
        val events = mutableListOf<CatalogGovernanceRuntimeInvalidationEvent>()

        override fun invalidate(event: CatalogGovernanceRuntimeInvalidationEvent) {
            events += event
        }
    }

    private fun requireDocker() {
        if (!dockerAvailable) {
            if (strictIntegration) {
                error("Docker is required for server integration tests when strict mode is enabled.")
            }
            Assume.assumeTrue("Docker is required for server integration tests.", dockerAvailable)
        }
    }

    private fun requireLiveOfficialRefreshEnabled() {
        val liveOfficialRefreshEnabled =
            System.getenv("CATALOG_GOVERNANCE_LIVE_REFRESH_SMOKE")
                ?.equals("true", ignoreCase = true) == true
        Assume.assumeTrue(
            "Enable CATALOG_GOVERNANCE_LIVE_REFRESH_SMOKE=true to run live official phones refresh smoke tests.",
            liveOfficialRefreshEnabled,
        )
    }

    private class FakeOfficialPageFetcher(
        private val htmlByUri: Map<String, String>,
    ) : CatalogGovernanceOfficialPageFetcher {
        override suspend fun fetchText(uri: String): String =
            htmlByUri[uri] ?: error("Missing fake official HTML for '$uri'.")
    }

    private companion object {
        private var dockerAvailable: Boolean = false
        private val strictIntegration: Boolean by lazy {
            System.getenv("SERVER_IT_STRICT")?.equals("true", ignoreCase = true) == true ||
                System.getenv("CI")?.equals("true", ignoreCase = true) == true
        }
        private var container: PostgreSQLContainer<*>? = null

        private const val XIAOMI_14_HTML = """
            Xiaomi 14 Specs
            Black White Jade Green
            12GB+256GB, 12GB+512GB
        """

        private const val POCO_X7_PRO_HTML = """
            POCO X7 Pro Specs
            Black Green Yellow
            12GB+256GB, 12GB+512GB
        """

        private const val REDMI_NOTE_14_PRO_PLUS_HTML = """
            Redmi Note 14 Pro+ 5G Specs
            Black Purple Blue
            12+256GB, 12+512GB
        """

        private const val SAMSUNG_GALAXY_S24_PLUS_HTML = """
            Samsung Galaxy S24+ Specs
            Cobalt Violet Amber Yellow Onyx Black Marble Gray
            12GB+256GB, 12GB+512GB
        """

        private const val ONEPLUS_13_HTML = """
            OnePlus 13 Specs
            Midnight Ocean Arctic Dawn Black Eclipse
            12GB+256GB, 16GB+512GB
        """

        private const val ONEPLUS_12_HTML = """
            OnePlus 12 Specs
            12GB+256GB, 16GB+512GB
        """

        private const val ONEPLUS_NORD_4_HTML = """
            OnePlus Nord 4 Specs
            Mercurial Silver Oasis Green Obsidian Midnight
            12GB+256GB, 16GB+512GB
        """

        private const val NOTHING_PHONE_2_HTML = """
            https://nothing.tech/products/phone-2?Colour=White&amp;Capacity=8%2B128GB
            https://nothing.tech/products/phone-2?Colour=White&amp;Capacity=12%2B256GB
            https://nothing.tech/products/phone-2?Colour=Dark+Grey&amp;Capacity=12%2B512GB
        """

        private const val NOTHING_PHONE_2A_HTML = """
            Black_Glyphs Milk_Glyphs
            8+128GB 12+256GB
        """

        private const val NOTHING_PHONE_1_HTML = """
            https://intl.nothing.tech/products/phone-1?colour=Black&amp;capacity=8%2B128GB
            https://intl.nothing.tech/products/phone-1?colour=White&amp;capacity=8%2B256GB
            https://intl.nothing.tech/products/phone-1?colour=White&amp;capacity=12%2B256GB
        """

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            dockerAvailable = runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)
            if (!dockerAvailable) {
                if (strictIntegration) {
                    error("Docker is required for server integration tests in strict mode.")
                }
                return
            }

            val postgresImage = DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                .asCompatibleSubstituteFor("postgres")
            val postgres = PostgreSQLContainer(postgresImage)
                .withDatabaseName("shoppingassistant_test")
                .withUsername("test")
                .withPassword("test")
            postgres.start()
            container = postgres

            val database = Database.connect(
                url = postgres.jdbcUrl,
                driver = "org.postgresql.Driver",
                user = postgres.username,
                password = postgres.password,
            )
            TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_REPEATABLE_READ

            transaction(database) {
                SchemaUtils.createMissingTablesAndColumns(
                    AuthUsersTable,
                    ProductsTable,
                    OffersTable,
                    CategoriesTable,
                    AttributeDefsTable,
                    CategoryAttributesTable,
                    AliasEntriesTable,
                    AttributeValueDictTable,
                    CatalogStage4ContractMetaTable,
                    CatalogStage4ImmutableAttributesTable,
                    CatalogStage4NormalizationRulesTable,
                    CatalogStage4DedupTemplatesTable,
                    CatalogStage4TypedConstraintsTable,
                    CatalogGovernanceSourcesTable,
                    CatalogGovernanceSourceRegistryTable,
                    CatalogGovernanceRefreshRunsTable,
                    CatalogGovernancePublishEventsTable,
                    CatalogGovernanceBrandsTable,
                    CatalogGovernanceProductFamiliesTable,
                    CatalogGovernanceModelsTable,
                    CatalogGovernanceValueCanonTable,
                    CatalogGovernanceAliasesTable,
                    CatalogGovernanceValueObservationsTable,
                    CatalogGovernanceValueCandidatesTable,
                    CatalogGovernanceDecisionsTable,
                )

                seedCuratedGovernanceBackbone()
            }
        }

        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            container?.stop()
            container = null
        }

        private fun seedCuratedGovernanceBackbone() {
            val curatedSnapshot = CatalogGovernanceCuratedSeed.snapshot
            val categoryCodes = buildSet {
                add("TECH.PHONES")
                curatedSnapshot.packs.forEach { pack ->
                    pack.brands.mapNotNullTo(this) { it.primaryCategoryCode }
                    pack.families.mapNotNullTo(this) { it.defaultCategoryCode }
                }
            }
            categoryCodes.forEach { categoryCode ->
                CategoriesTable.insertIgnore { stmt ->
                    stmt[code] = categoryCode
                    stmt[segment] = "TECH"
                    stmt[status] = "ACTIVE"
                    stmt[titleLocalized] = localizedTextOf("en" to categoryCode, "ru" to categoryCode)
                    stmt[titleRu] = categoryCode
                    stmt[titleEn] = categoryCode
                    stmt[parentCode] = null
                    stmt[description] = null
                    stmt[replacementCode] = null
                }
            }

            val attributeCodes = buildSet {
                add("color")
                add("integration_test_color")
                add("release_year")
                add("screen_size_inch")
                add("battery_mah")
                add("wired_charging_w")
                add("wireless_charging")
                add("esim_support")
                add("ip_rating")
                curatedSnapshot.packs.forEach { pack ->
                    pack.canonicalValues.mapTo(this) { it.attributeCode }
                }
            }
            attributeCodes.forEach { attributeCode ->
                AttributeDefsTable.insertIgnore { stmt ->
                    stmt[code] = attributeCode
                    stmt[title] = attributeCode.replace('_', ' ')
                    stmt[dataType] = "STRING"
                    stmt[requiredForSearch] = false
                    stmt[requiredForOffer] = false
                    stmt[requiredForExpress] = false
                    stmt[requiredBy] = null
                    stmt[facetEnabled] = true
                    stmt[multiValued] = false
                    stmt[valueDictCode] = attributeCode
                }
            }
        }
    }
}
