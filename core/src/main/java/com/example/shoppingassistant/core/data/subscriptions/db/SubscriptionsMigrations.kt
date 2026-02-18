package com.example.shoppingassistant.core.data.subscriptions.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val SUBSCRIPTIONS_MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN ownerKey TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE subscription_notifications ADD COLUMN ownerKey TEXT NOT NULL DEFAULT ''")

        // Owner-scope ввели только сейчас: старые записи без ownerKey потенциально небезопасны.
        // Для продакшена лучше очистить их, чем смешивать данные разных пользователей.
        db.execSQL("DELETE FROM subscription_conditions")
        db.execSQL("DELETE FROM subscriptions")
        db.execSQL("DELETE FROM subscription_notifications")

        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_subscriptions_ownerKey_scope_input " +
                "ON subscriptions(ownerKey, scope, input)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_subscriptions_ownerKey_updatedAtMillis " +
                "ON subscriptions(ownerKey, updatedAtMillis)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_subscription_notifications_ownerKey_createdAtMillis " +
                "ON subscription_notifications(ownerKey, createdAtMillis)"
        )
    }
}
