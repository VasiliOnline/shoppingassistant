package com.example.shoppingassistant.core.data.templatesubscriptions.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "template_subscriptions",
    indices = [
        Index("updatedAtMillis"),
    ],
)
data class TemplateSubscriptionEntity(
    @PrimaryKey val templateId: String,
    val snapshotJson: String,
    val minAlertIntervalMinutes: Int,
    val isActive: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "template_subscription_triggers",
    foreignKeys = [
        ForeignKey(
            entity = TemplateSubscriptionEntity::class,
            parentColumns = ["templateId"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("templateId"), Index(value = ["templateId", "type"])],
)
data class TemplateSubscriptionTriggerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: String,
    val type: String,
    val numberValue: Double?,
    val moneyMinor: Long?,
    val currency: String?,
)

data class TemplateSubscriptionWithTriggers(
    @Embedded val subscription: TemplateSubscriptionEntity,
    @Relation(
        parentColumn = "templateId",
        entityColumn = "templateId",
    )
    val triggers: List<TemplateSubscriptionTriggerEntity>,
)

