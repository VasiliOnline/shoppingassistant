package com.example.shoppingassistant.core.data.templatehistory.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "template_history",
    indices = [
        Index("usedAtMillis"),
    ],
)
data class TemplateHistoryEntity(
    @PrimaryKey val templateId: String,
    val snapshotJson: String,
    val usedAtMillis: Long,
)

