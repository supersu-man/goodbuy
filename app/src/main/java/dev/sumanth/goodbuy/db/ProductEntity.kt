package dev.sumanth.goodbuy.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.sumanth.goodbuy.model.Product

@Entity(tableName = "tracked_products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val category: String,
    val imageUrl: String,
    val oldPrice: Double,
    val newPrice: Double,
    val rating: Double,
    val reviewCount: Int,
    val isFavorite: Boolean,
    val link: String,
    val lastTrackedTime: Long
)

fun ProductEntity.toDomainModel(): Product {
    return Product(
        id = id,
        title = title,
        description = description,
        category = category,
        imageUrl = imageUrl,
        oldPrice = oldPrice,
        newPrice = newPrice,
        rating = rating,
        reviewCount = reviewCount,
        isFavorite = isFavorite,
        link = link,
        lastTrackedTime = lastTrackedTime
    )
}

fun Product.toEntity(): ProductEntity {
    return ProductEntity(
        id = id,
        title = title,
        description = description,
        category = category,
        imageUrl = imageUrl,
        oldPrice = oldPrice,
        newPrice = newPrice,
        rating = rating,
        reviewCount = reviewCount,
        isFavorite = isFavorite,
        link = link,
        lastTrackedTime = lastTrackedTime
    )
}
