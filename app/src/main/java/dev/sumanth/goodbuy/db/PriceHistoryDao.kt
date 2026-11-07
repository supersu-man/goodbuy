package dev.sumanth.goodbuy.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceHistory(entry: PriceHistoryEntity)

    @Query("SELECT * FROM price_history WHERE productId = :productId ORDER BY timestamp ASC")
    fun getHistoryForProduct(productId: String): Flow<List<PriceHistoryEntity>>

    @Query("DELETE FROM price_history WHERE productId = :productId")
    suspend fun deleteHistoryForProduct(productId: String)

    @Query("DELETE FROM price_history")
    suspend fun deleteAllHistory()
}
