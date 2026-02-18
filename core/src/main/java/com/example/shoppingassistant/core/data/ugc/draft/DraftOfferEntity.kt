package com.example.shoppingassistant.core.data.ugc.draft

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "draft_offers")
data class DraftOfferEntity(
    @PrimaryKey
    val id: String,
    val payloadJson: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val publishStatus: String,
)
