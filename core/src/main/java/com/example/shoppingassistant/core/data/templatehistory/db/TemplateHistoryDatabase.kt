package com.example.shoppingassistant.core.data.templatehistory.db

import androidx.room.Database
import androidx.room.RoomDatabase

const val templateHistoryDatabaseName: String = "template_history.db"

@Database(
    entities = [TemplateHistoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TemplateHistoryDatabase : RoomDatabase() {
    abstract fun templateHistoryDao(): TemplateHistoryDao
}

