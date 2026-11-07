package dev.sumanth.goodbuy.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "price_history")
data class PriceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val historyId: Long = 0,
    val productId: String,
    val price: Double,
    val timestamp: Long = System.currentTimeMillis()
)
