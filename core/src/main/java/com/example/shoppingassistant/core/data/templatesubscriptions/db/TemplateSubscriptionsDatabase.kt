package com.example.shoppingassistant.core.data.templatesubscriptions.db

import androidx.room.Database
import androidx.room.RoomDatabase

const val templateSubscriptionsDatabaseName: String = "template_subscriptions.db"

@Database(
    entities = [
        TemplateSubscriptionEntity::class,
        TemplateSubscriptionTriggerEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TemplateSubscriptionsDatabase : RoomDatabase() {
    abstract fun templateSubscriptionsDao(): TemplateSubscriptionsDao
}

