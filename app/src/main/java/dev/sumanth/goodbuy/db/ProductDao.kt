package dev.sumanth.goodbuy.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM tracked_products ORDER BY lastTrackedTime DESC")
    fun getTrackedProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM tracked_products")
    suspend fun getTrackedProductsList(): List<ProductEntity>

    @Query("SELECT * FROM tracked_products WHERE id = :productId LIMIT 1")
    suspend fun getProductById(productId: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Query("DELETE FROM tracked_products WHERE id = :productId")
    suspend fun deleteProductById(productId: String)

    @Query("DELETE FROM tracked_products")
    suspend fun deleteAllProducts()
}
