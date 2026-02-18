// Last synced: 2025-11-14 18:50
package com.example.shoppingassistant.core.data.db

import android.content.ContentValues
import androidx.room.Database
import androidx.room.OnConflictStrategy
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.example.shoppingassistant.domain.catalog.CatalogSeed

/**
 * Основная база данных приложения.
 *
 * productDatabaseName — имя файла базы, используемое в DI.
 */
const val productDatabaseName: String = "products.db"

@Database(
    entities = [ProductEntity::class, ProductAttributeEntity::class, CategoryOfferCountEntity::class],
    version = 4, // was 3
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun categoryOfferCountsDao(): CategoryOfferCountsDao

    companion object {

        /**
         * Простой сид: несколько товаров и атрибутов для отладки.
         * Вызывается Room при создании базы.
         */
        val SeedCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)

                // Быстрый сид — напрямую SQL, чтобы не ждать Room транзакций

                // --- SEED: iPhone 16 (2 конфигурации) ---
                db.insert(
                    "products",
                    OnConflictStrategy.REPLACE,
                    ContentValues().apply {
                        put("id", 1)
                        put("brand", "Apple")
                        put("model", "iPhone 16")
                        put("categoryCode", "TECH.PHONES")
                        put("title", "Apple iPhone 16 256GB Blue")
                        put("priceCents", 11_999_000L)
                        put("currency", "RUB")
                        put("sellerRating", 4.8)
                        put("deliveryDays", 3)
                        put("sourceUrl", "https://example.com/item/1")
                        put(
                            "imageUrls",
                            "https://example.com/img/a.jpg|https://example.com/img/b.jpg",
                        )
                    },
                )

                db.insert(
                    "product_attributes",
                    OnConflictStrategy.REPLACE,
                    ContentValues().apply {
                        put("productId", 1)
                        put("key", "memory")
                        put("value", "256GB")
                    },
                )

                db.insert(
                    "product_attributes",
                    OnConflictStrategy.REPLACE,
                    ContentValues().apply {
                        put("productId", 1)
                        put("key", "color")
                        put("value", "blue")
                    },
                )

                db.insert(
                    "products",
                    OnConflictStrategy.REPLACE,
                    ContentValues().apply {
                        put("id", 2)
                        put("brand", "Apple")
                        put("model", "iPhone 16")
                        put("categoryCode", "TECH.PHONES")
                        put("title", "Apple iPhone 16 512GB Black")
                        put("priceCents", 13_999_000L)
                        put("currency", "RUB")
                        put("sellerRating", 4.6)
                        put("deliveryDays", 5)
                        put("sourceUrl", "https://example.com/item/2")
                        put("imageUrls", "https://example.com/img/c.jpg")
                    },
                )

                db.insert(
                    "product_attributes",
                    OnConflictStrategy.REPLACE,
                    ContentValues().apply {
                        put("productId", 2)
                        put("key", "memory")
                        put("value", "512GB")
                    },
                )

                db.insert(
                    "product_attributes",
                    OnConflictStrategy.REPLACE,
                    ContentValues().apply {
                        put("productId", 2)
                        put("key", "color")
                        put("value", "black")
                    },
                )
// --- SEED: iPhone 15 (2 конфигурации) ---
                db.insert(
                    "products",
                    OnConflictStrategy.REPLACE,
                    ContentValues().apply {
                        put("id", 5)
                        put("brand", "Apple")
                        put("model", "iPhone 15")
                        put("categoryCode", "TECH.PHONES")
                        put("title", "Apple iPhone 15 128GB Blue")
                        put("priceCents", 9_999_000L)
                        put("currency", "RUB")
                        put("sellerRating", 4.7)
                        put("deliveryDays", 4)
                        put("sourceUrl", "https://example.com/item/5")
                        put("imageUrls", "https://example.com/img/15a.jpg")
                    },
                )
                db.insert(
                    "product_attributes",
                    OnConflictStrategy.REPLACE,
                    ContentValues().apply {
                        put("productId", 5)
                        put("key", "memory")
                        put("value", "128GB")
                    },
                )
                db.insert(
                    "product_attributes",
                    OnConflictStrategy.REPLACE,
                    ContentValues().apply {
                        put("productId", 5)
                        put("key", "color")
                        put("value", "blue")
                    },
                )

                db.insert(
                    "products",
                    OnConflictStrategy.REPLACE,
                    ContentValues().apply {
                        put("id", 6)
                        put("brand", "Apple")
                        put("model", "iPhone 15")
                        put("categoryCode", "TECH.PHONES")
                        put("title", "Apple iPhone 15 256GB Black")
                        put("priceCents", 10_999_000L)
                        put("currency", "RUB")
                        put("sellerRating", 4.6)
                        put("deliveryDays", 5)
                        put("sourceUrl", "https://example.com/item/6")
                        put("imageUrls", "https://example.com/img/15b.jpg")
                    },
                )
                db.insert(
                    "product_attributes",
                    OnConflictStrategy.REPLACE,
                    ContentValues().apply {
                        put("productId", 6)
                        put("key", "memory")
                        put("value", "256GB")
                    },
                )
                db.insert(
                    "product_attributes",
                    OnConflictStrategy.REPLACE,
                    ContentValues().apply {
                        put("productId", 6)
                        put("key", "color")
                        put("value", "black")
                    },
                )

                // --- SEED: iPhone 17 ---
                db.insert(
                    "products",
                    OnConflictStrategy.REPLACE,
                    ContentValues().apply {
                        put("id", 10)
                        put("brand", "Apple")
                        put("model", "iPhone 17")
                        put("categoryCode", "TECH.PHONES")
                        put("title", "Apple iPhone 17 256GB Blue")
                        put("priceCents", 12_999_000L)
                        put("currency", "RUB")
                        put("sellerRating", 4.7)
                        put("deliveryDays", 3)
                        put("sourceUrl", "https://example.com/item/10")
                        put("imageUrls", "https://example.com/img/17a.jpg")
                    },
                )

                db.insert(
                    "product_attributes",
                    OnConflictStrategy.REPLACE,
                    ContentValues().apply {
                        put("productId", 10)
                        put("key", "memory")
                        put("value", "256GB")
                    },
                )

                db.insert(
                    "product_attributes",
                    OnConflictStrategy.REPLACE,
                    ContentValues().apply {
                        put("productId", 10)
                        put("key", "color")
                        put("value", "blue")
                    },
                )

                db.insert(
                    "products",
                    OnConflictStrategy.REPLACE,
                    ContentValues().apply {
                        put("id", 11)
                        put("brand", "Apple")
                        put("model", "iPhone 17")
                        put("categoryCode", "TECH.PHONES")
                        put("title", "Apple iPhone 17 512GB Black")
                        put("priceCents", 14_999_000L)
                        put("currency", "RUB")
                        put("sellerRating", 4.6)
                        put("deliveryDays", 4)
                        put("sourceUrl", "https://example.com/item/11")
                        put("imageUrls", "https://example.com/img/17b.jpg")
                    },
                )

                db.insert(
                    "product_attributes",
                    OnConflictStrategy.REPLACE,
                    ContentValues().apply {
                        put("productId", 11)
                        put("key", "memory")
                        put("value", "512GB")
                    },
                )

                db.insert(
                    "product_attributes",
                    OnConflictStrategy.REPLACE,
                    ContentValues().apply {
                        put("productId", 11)
                        put("key", "color")
                        put("value", "black")
                    },
                )

                val now = System.currentTimeMillis()
                CatalogSeed.categories.forEach { category ->
                    db.insert(
                        "category_offer_counts",
                        OnConflictStrategy.REPLACE,
                        ContentValues().apply {
                            put("categoryCode", category.code)
                            put("offersCount", 0)
                            put("updatedAtMillis", now)
                        },
                    )
                }
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS category_offer_counts (
                        categoryCode TEXT NOT NULL PRIMARY KEY,
                        offersCount INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    ALTER TABLE products ADD COLUMN categoryCode TEXT
                """.trimIndent())
            }
        }
    }
}

