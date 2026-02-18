package com.example.shoppingassistant.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CategoryOfferCountsDao {

    @Query("""
        SELECT categoryCode, offersCount
        FROM category_offer_counts
        WHERE categoryCode IN (:codes)
    """)
    suspend fun getCounts(codes: List<String>): List<CategoryOfferCountRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CategoryOfferCountEntity>)
}

data class CategoryOfferCountRow(
    val categoryCode: String,
    val offersCount: Int,
)
