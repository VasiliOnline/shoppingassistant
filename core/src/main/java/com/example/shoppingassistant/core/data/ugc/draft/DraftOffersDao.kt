package com.example.shoppingassistant.core.data.ugc.draft

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftOffersDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DraftOfferEntity)

    @Query("SELECT * FROM draft_offers WHERE id = :id LIMIT 1")
    suspend fun get(id: String): DraftOfferEntity?

    @Query("SELECT * FROM draft_offers WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<DraftOfferEntity?>

    @Query("SELECT * FROM draft_offers ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<DraftOfferEntity>>

    @Query("DELETE FROM draft_offers WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM draft_offers")
    suspend fun clear()
}
