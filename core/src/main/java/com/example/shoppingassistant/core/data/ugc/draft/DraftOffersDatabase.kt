package com.example.shoppingassistant.core.data.ugc.draft

import androidx.room.Database
import androidx.room.RoomDatabase

const val draftOffersDatabaseName: String = "draft_offers.db"

@Database(
    entities = [DraftOfferEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class DraftOffersDatabase : RoomDatabase() {
    abstract fun draftOffersDao(): DraftOffersDao
}
