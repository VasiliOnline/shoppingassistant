package com.example.shoppingassistant.core.data.templatehistory.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TemplateHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TemplateHistoryEntity)

    @Query("""
        SELECT * FROM template_history
        ORDER BY usedAtMillis DESC
        LIMIT :limit
    """)
    suspend fun listRecent(limit: Int): List<TemplateHistoryEntity>

    @Query("""
        DELETE FROM template_history
        WHERE templateId IN (:ids)
    """)
    suspend fun deleteByIds(ids: List<String>)
}
