package com.example.shoppingassistant.core.data.templatesubscriptions.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface TemplateSubscriptionsDao {

    @Query("SELECT COUNT(*) FROM template_subscriptions WHERE templateId = :templateId")
    suspend fun countById(templateId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubscription(entity: TemplateSubscriptionEntity)

    @Query("DELETE FROM template_subscription_triggers WHERE templateId = :templateId")
    suspend fun deleteTriggers(templateId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTriggers(items: List<TemplateSubscriptionTriggerEntity>)

    @Transaction
    suspend fun upsertWithTriggers(
        entity: TemplateSubscriptionEntity,
        triggers: List<TemplateSubscriptionTriggerEntity>,
    ) {
        upsertSubscription(entity)
        deleteTriggers(entity.templateId)
        if (triggers.isNotEmpty()) insertTriggers(triggers)
    }

    @Transaction
    @Query("""
        SELECT * FROM template_subscriptions
        ORDER BY updatedAtMillis DESC
    """)
    suspend fun listWithTriggers(): List<TemplateSubscriptionWithTriggers>

    @Query("DELETE FROM template_subscriptions WHERE templateId = :templateId")
    suspend fun deleteById(templateId: String)
}

