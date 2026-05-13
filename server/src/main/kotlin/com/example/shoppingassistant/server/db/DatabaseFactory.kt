package com.example.shoppingassistant.server.db

import com.example.shoppingassistant.domain.model.TypedAttributeValue
import com.example.shoppingassistant.server.ai.AiAgentRegistryOverridesTable
import com.example.shoppingassistant.server.config.DatabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import java.sql.DriverManager
import com.example.shoppingassistant.server.catalog.AttributeDefsTable
import com.example.shoppingassistant.server.catalog.AttributeValueDictTable
import com.example.shoppingassistant.server.catalog.AliasEntriesTable
import com.example.shoppingassistant.server.catalog.BrowseNodesTable
import com.example.shoppingassistant.server.catalog.CategoriesTable
import com.example.shoppingassistant.server.catalog.CategoryAliasesTable
import com.example.shoppingassistant.server.catalog.CategoryAttributesTable
import com.example.shoppingassistant.server.catalog.CatalogConstraintsTable
import com.example.shoppingassistant.server.catalog.CatalogStage4ContractMetaTable
import com.example.shoppingassistant.server.catalog.CatalogStage4DedupTemplatesTable
import com.example.shoppingassistant.server.catalog.CatalogStage4ExecutionMetricsTable
import com.example.shoppingassistant.server.catalog.CatalogStage4ImmutableAttributesTable
import com.example.shoppingassistant.server.catalog.CatalogStage4NormalizationRulesTable
import com.example.shoppingassistant.server.catalog.CatalogReadinessSnapshotsTable
import com.example.shoppingassistant.server.catalog.CatalogReadinessAutomationLeasesTable
import com.example.shoppingassistant.server.catalog.CatalogGovernanceReportsTable
import com.example.shoppingassistant.server.catalog.CatalogGovernanceHookDeliveriesTable
import com.example.shoppingassistant.server.catalog.CatalogGovernanceAliasesTable
import com.example.shoppingassistant.server.catalog.CatalogGovernanceBrandsTable
import com.example.shoppingassistant.server.catalog.CatalogGovernanceDecisionsTable
import com.example.shoppingassistant.server.catalog.CatalogGovernancePublishEventsTable
import com.example.shoppingassistant.server.catalog.CatalogGovernanceProductFamiliesTable
import com.example.shoppingassistant.server.catalog.CatalogGovernanceRefreshRunsTable
import com.example.shoppingassistant.server.catalog.CatalogGovernanceOfficialPhoneEndpointOverlaysTable
import com.example.shoppingassistant.server.catalog.CatalogGovernanceSourceRegistryTable
import com.example.shoppingassistant.server.catalog.CatalogGovernanceSourcesTable
import com.example.shoppingassistant.server.catalog.CatalogGovernanceValueCandidatesTable
import com.example.shoppingassistant.server.catalog.CatalogGovernanceValueCanonTable
import com.example.shoppingassistant.server.catalog.CatalogGovernanceValueObservationsTable
import com.example.shoppingassistant.server.catalog.CatalogGovernanceModelsTable
import com.example.shoppingassistant.server.catalog.CatalogPhoneModelEnrichmentCandidatesTable
import com.example.shoppingassistant.server.catalog.CatalogStage4TypedConstraintsTable
import com.example.shoppingassistant.server.catalog.FacetCollectionsTable
import com.example.shoppingassistant.server.catalog.FacetDefinitionsTable
import com.example.shoppingassistant.server.catalog.FacetPresetsTable
import com.example.shoppingassistant.server.catalog.GoogleTaxonomyMappingsTable
import com.example.shoppingassistant.server.catalog.CatalogSeeder
import com.example.shoppingassistant.server.catalog.CatalogSeedSyncMode
import com.example.shoppingassistant.server.offers.OffersTable
import com.example.shoppingassistant.server.offers.ProductI18nTable
import com.example.shoppingassistant.server.offers.ProductsTable
import com.example.shoppingassistant.server.offers.UserPreferencesTable
import com.example.shoppingassistant.server.offers.UserReviewsTable
import com.example.shoppingassistant.server.offers.UserProfilesTable
import com.example.shoppingassistant.server.offers.SellerStatsTable
import com.example.shoppingassistant.server.offers.AlertsTable
import com.example.shoppingassistant.server.offers.OfferPriceHistoryTable
import com.example.shoppingassistant.server.offers.OfferSourcesTable
import com.example.shoppingassistant.server.auth.DefaultPasswordHasher
import com.example.shoppingassistant.server.subscriptions.SubscriptionNotificationsTable
import com.example.shoppingassistant.server.subscriptions.SubscriptionsEngineStateTable
import com.example.shoppingassistant.server.shortlisting.ShortListingDraftsTable
import com.example.shoppingassistant.server.push.PushTokensTable
import com.example.shoppingassistant.server.tracks.TrackEventsTable
import com.example.shoppingassistant.server.tracks.TracksTable
import com.example.shoppingassistant.server.tracks.top10.TrackTop10SnapshotsTable
import com.example.shoppingassistant.server.vision.VisionUsageTable
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull

private val json = Json { ignoreUnknownKeys = true }

/**
 * Инициализация подключения к Postgres через Exposed
 * + helper для запуска транзакций в корутинах.
 */
object DatabaseFactory {

    /**
     * Вызывается один раз при старте сервера.
     *
     * Подключаемся к БД и создаём минимальную схему (таблица auth_users).
     */
    fun init(config: DatabaseConfig) {
        runMigrations(config)

        val database = Database.connect(
            url = config.url,
            driver = config.driver,
            user = config.user,
            // Пароль ты задаёшь через переменную окружения DB_PASSWORD.
            password = config.password ?: ""
        )

        // На всякий случай задаём уровень изоляции по умолчанию.
        TransactionManager.manager.defaultIsolationLevel =
            Connection.TRANSACTION_REPEATABLE_READ

        // Для staging/prod по умолчанию отключаем auto-DDL Exposed:
        // схема должна управляться только Flyway.
        val appEnv = (System.getenv("APP_ENV")
            ?: System.getenv("APP_STAGE")
            ?: System.getenv("ENV")
            ?: "local")
            .trim()
            .lowercase()
        val autosyncDefault = when (appEnv) {
            "prod", "production", "staging", "stage" -> "false"
            else -> "true"
        }
        val schemaAutosyncEnabled = (System.getenv("DB_SCHEMA_AUTOSYNC") ?: autosyncDefault)
            .equals("true", ignoreCase = true)
        val catalogSeedSyncDefault = when (appEnv) {
            "local", "dev", "development", "test", "testing", "ci" -> "true"
            else -> "false"
        }
        val catalogSeedSyncEnabled = (System.getenv("CATALOG_SEED_SYNC_ENABLED") ?: catalogSeedSyncDefault)
            .equals("true", ignoreCase = true)
        // Безопасный дефолт: upsert-only. Для полного destructive sync нужен явный opt-in.
        val catalogSeedSyncMode = CatalogSeedSyncMode.fromEnv(System.getenv("CATALOG_SEED_SYNC_MODE"))

        // На этом шаге создаём недостающие таблицы/колонки и приводим данные в порядок.
        transaction(database) {
            if (schemaAutosyncEnabled) {
                // В dev создаём недостающие таблицы/колонки (AuthUsersTable, AuthAuditTable и др.).
                SchemaUtils.createMissingTablesAndColumns(
                    AuthUsersTable,
                    AuthAuditTable,
                    UserProfilesTable,
                    SellerStatsTable,
                    ProductsTable,
                    ProductI18nTable,
                    OffersTable,
                    OfferSourcesTable,
                    UserPreferencesTable,
                    UserReviewsTable,
                    AlertsTable,
                    OfferPriceHistoryTable,
                    SubscriptionNotificationsTable,
                    SubscriptionsEngineStateTable,
                    TracksTable,
                    TrackEventsTable,
                    TrackTop10SnapshotsTable,
                    PushTokensTable,
                    CategoriesTable,
                    CategoryAliasesTable,
                    BrowseNodesTable,
                    AliasEntriesTable,
                    GoogleTaxonomyMappingsTable,
                    AttributeDefsTable,
                    CategoryAttributesTable,
                    AttributeValueDictTable,
                    CatalogConstraintsTable,
                    FacetDefinitionsTable,
                    FacetPresetsTable,
                    FacetCollectionsTable,
                    CatalogStage4ContractMetaTable,
                    CatalogStage4ImmutableAttributesTable,
                    CatalogStage4NormalizationRulesTable,
                    CatalogStage4DedupTemplatesTable,
                    CatalogStage4TypedConstraintsTable,
                    CatalogStage4ExecutionMetricsTable,
                    CatalogReadinessSnapshotsTable,
                    CatalogReadinessAutomationLeasesTable,
                    CatalogGovernanceSourcesTable,
                    CatalogGovernanceSourceRegistryTable,
                    CatalogGovernanceOfficialPhoneEndpointOverlaysTable,
                    CatalogGovernanceRefreshRunsTable,
                    CatalogGovernancePublishEventsTable,
                    CatalogGovernanceBrandsTable,
                    CatalogGovernanceProductFamiliesTable,
                    CatalogGovernanceModelsTable,
                    CatalogGovernanceValueCanonTable,
                    CatalogGovernanceAliasesTable,
                    CatalogGovernanceValueObservationsTable,
                    CatalogGovernanceValueCandidatesTable,
                    CatalogPhoneModelEnrichmentCandidatesTable,
                    CatalogGovernanceDecisionsTable,
                    CatalogGovernanceReportsTable,
                    CatalogGovernanceHookDeliveriesTable,
                    VisionUsageTable,
                    ShortListingDraftsTable,
                    AiAgentRegistryOverridesTable,
                )
                // catalog_preset_events is managed by Flyway as a partitioned table.
                // Exposed auto-DDL attempts to recreate its uniqueness contract and
                // collides with the existing relation names in local/test databases.
            }
            // Если created_at добавился к уже существующим пользователям — заполняем null.
            AuthUsersTable.update({ AuthUsersTable.createdAt.isNull() }) { stmt ->
                stmt[AuthUsersTable.createdAt] = System.currentTimeMillis()
            }

            // Минимальный сид для демо: 1 продукт + 3 оффера.
            // Прокидываем только если таблица пуста и явно разрешено через ENV DEV_SEED_DEMO=true,
            // чтобы не мешать прод-данным.
            val seedEnabled = (System.getenv("DEV_SEED_DEMO") ?: "false")
                .equals("true", ignoreCase = true)
            if (seedEnabled && OffersTable.selectAll().limit(1).empty()) {
                seedDemoOffers()
            }
            if (catalogSeedSyncEnabled) {
                CatalogSeeder.seedIfEmpty(syncMode = catalogSeedSyncMode)
            }
        }
    }

    /**
     * Универсальный helper для репозиториев:
     *
     * suspend fun <T> dbQuery { ... }
     *
     * Позволяет вызывать Exposed в suspend-функциях, не блокируя основной поток.
     */
    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) {
            block()
        }

    /**
     * Старые локальные Docker-БД создавались до реального Flyway-runner и могут
     * не иметь flyway_schema_history при уже существующей схеме. Для таких баз
     * считаем legacy baseline = V23 и докатываем V24+ миграции, где появились
     * readiness tables и title_localized JSONB.
     *
     * Для пустой базы Flyway выполнит весь набор V1..N как обычно.
     */
    private fun runMigrations(config: DatabaseConfig) {
        upgradeLegacyFlywaySchemaHistory(config)

        Flyway.configure()
            .dataSource(config.url, config.user, config.password ?: "")
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion(MigrationVersion.fromVersion("23"))
            .load()
            .migrate()
    }

    private fun upgradeLegacyFlywaySchemaHistory(config: DatabaseConfig) {
        DriverManager.getConnection(config.url, config.user, config.password ?: "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("drop table if exists flyway_schema_history_legacy_bootstrap cascade")
            }

            val legacyColumns = linkedSetOf(
                "type",
                "script",
                "checksum",
                "installed_by",
                "execution_time",
            )
            val existingColumns = connection.prepareStatement(
                """
                select column_name
                from information_schema.columns
                where table_name = 'flyway_schema_history'
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { rs ->
                    buildSet {
                        while (rs.next()) {
                            add(rs.getString(1).trim().lowercase())
                        }
                    }
                }
            }
            if (existingColumns.isEmpty()) return

            val appliedVersions = mutableSetOf<String>()
            var hasUnderscoreDescriptions = false
            connection.prepareStatement(
                """
                select version, description
                from flyway_schema_history
                where success = true
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { rs ->
                    while (rs.next()) {
                        rs.getString("version")?.trim()?.takeIf { it.isNotEmpty() }?.let(appliedVersions::add)
                        if (rs.getString("description")?.contains('_') == true) {
                            hasUnderscoreDescriptions = true
                        }
                    }
                }
            }

            val legacyMissingVersions = setOf("1", "2", "3", "5", "7", "8").any { it !in appliedVersions }
            val needsRebaseline =
                legacyColumns.any { it !in existingColumns } ||
                    hasUnderscoreDescriptions ||
                    legacyMissingVersions

            if (!needsRebaseline) return

            connection.createStatement().use { statement ->
                statement.execute("drop table if exists flyway_schema_history_legacy_bootstrap cascade")
                statement.execute("drop table if exists flyway_schema_history cascade")
            }
        }
    }
}

private fun seedDemoOffers() {
    val now = System.currentTimeMillis()
    val passwordHasher = DefaultPasswordHasher()

    val demoEmail = "demo@sassistant.app"
    val existingDemoUser = AuthUsersTable
        .selectAll()
        .where { AuthUsersTable.email eq demoEmail }
        .singleOrNull()

    val demoUserId = existingDemoUser?.get(AuthUsersTable.id) ?: AuthUsersTable.insert {
        it[email] = demoEmail
        // Сохраняем пароль в том же формате, что и рабочая авторизация (BCrypt сейчас).
        it[password] = passwordHasher.hash("demo123")
        it[displayName] = "Demo Seller"
        it[city] = "Moscow"
        it[emailVerified] = false
        it[phone] = null
        it[avatarUrl] = null
        it[photoUrls] = emptyList()
        it[createdAt] = now
    }.resultedValues!!.single()[AuthUsersTable.id]

    UserProfilesTable.insert {
        it[UserProfilesTable.userId] = demoUserId
        it[displayName] = "Demo Seller"
        it[countryCode] = "RU"
        it[city] = "Moscow"
    }
    UserPreferencesTable.insert {
        it[this.userId] = demoUserId
        it[badges] = listOf("TRUSTED", "FAST_SHIPPING")
        it[shippingCountries] = listOf("RU", "BY", "KZ")
        it[ratingValue] = 4.7
        it[ratingCount] = 128
    }
    SellerStatsTable.insert {
        it[this.userId] = demoUserId
        it[ratingValue] = 4.7
        it[ratingCount] = 128
    }

    val productId = ProductsTable.insert {
        it[brand] = "Apple"
        it[model] = "iPhone 15 Pro"
        it[titleNorm] = "Apple iPhone 15 Pro 128GB"
        it[imageUrls] = listOf(
            "https://images.unsplash.com/photo-1529618160092-2f8ccc8e087b",
            "https://images.unsplash.com/photo-1526170367222-4c45c0c3320d",
        )
        it[specs] = mapOf(
            "storage" to TypedAttributeValue.Text("128GB"),
            "color" to TypedAttributeValue.Text("Black"),
            "condition" to TypedAttributeValue.Text("New"),
        )
        it[description] = "Demo seeded product for local testing."
        it[updatedAt] = now
    }.resultedValues!!.single()[ProductsTable.id]

    ProductI18nTable.insert {
        it[this.productId] = productId
        it[lang] = "ru"
        it[title] = "iPhone 15 Pro 128 ГБ (демо)"
        it[description] = "Демо-товар для локального тестирования."
    }

    data class DemoOffer(
        val priceCents: Long,
        val color: String,
        val city: String,
        val lat: Double,
        val lon: Double,
    )

    val offers = listOf(
        DemoOffer(79900L, "Black", "Москва", 55.7558, 37.6176),
        DemoOffer(82900L, "Blue", "Санкт-Петербург", 59.9311, 30.3609),
        DemoOffer(76900L, "Natural Titanium", "Казань", 55.7961, 49.1064),
    )

    offers.forEachIndexed { idx, offer ->
        OffersTable.insert {
            it[this.productId] = productId
            it[this.userId] = demoUserId
            it[this.priceCents] = offer.priceCents
            it[this.currency] = "RUB"
            it[this.attributes] = mapOf(
                "storage" to TypedAttributeValue.Text("128GB"),
                "color" to TypedAttributeValue.Text(offer.color),
                "condition" to TypedAttributeValue.Text("new"),
            )
            it[this.description] = "Демо-оффер #${idx + 1} (${offer.color}, ${offer.city})"
            it[this.imageUrls] = listOf(
                "https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?auto=format&fit=crop&w=800&q=80"
            )
            it[this.condition] = "new"
            it[this.deliveryChannel] = "pickup"
            it[this.lat] = offer.lat
            it[this.lon] = offer.lon
            it[this.status] = "ACTIVE"
            it[this.updatedAt] = now
        }
    }
}

/**
 * Таблица пользователей для модуля авторизации.
 *
 * Поля отражают доменную модель AuthUser (id, email, displayName, phone, avatarUrl, city)
 * + отдельное поле password для пароля (позже можно заменить на hash/salt).
 */
object AuthUsersTable : Table("auth_users") {

    val id = long("id").autoIncrement()

    val email = varchar("email", length = 255).uniqueIndex()

    // Пока просто строка пароля (для MVP).
    // В будущем можно добавить hash + salt.
    val password = varchar("password", length = 255)

    val displayName = varchar("display_name", length = 255).nullable()
    val phone = varchar("phone", length = 64).nullable()
    val pendingPhone = varchar("pending_phone", length = 64).nullable()
    val pendingPhoneRequestedAt = long("pending_phone_requested_at").nullable()
    val phoneVerifiedAt = long("phone_verified_at").nullable()
    val avatarUrl = varchar("avatar_url", length = 512).nullable()
    val city = varchar("city", length = 255).nullable()
    val emailVerified = bool("email_verified").default(false)
    val emailVerifiedAt = long("email_verified_at").nullable()
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }
    val photoUrls = jsonb("photo_urls", json, ListSerializer(String.serializer())).nullable()
    val isDeleted = bool("is_deleted").default(false)
    val deletionRequestedAt = long("deletion_requested_at").nullable()
    val deletionAfter = long("deletion_after").nullable()
    val deletionRestoreToken = varchar("deletion_restore_token", length = 255).nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * Таблица аудита событий авторизации.
 */
object AuthAuditTable : Table("auth_audit") {
    val id = long("id").autoIncrement()
    val event = varchar("event", 64)
    val userId = long("user_id").nullable()
    val email = varchar("email", 255).nullable()
    val ip = varchar("ip", 64).nullable()
    val userAgent = varchar("user_agent", 512).nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
