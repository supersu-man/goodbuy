package dev.sumanth.goodbuy.model

data class Product(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val imageUrl: String,
    val oldPrice: Double,
    val newPrice: Double,
    val rating: Double,
    val reviewCount: Int,
    val isFavorite: Boolean = false,
    val link: String = "",
) {
    val discountPercentage: Int
        get() = if ((oldPrice > newPrice) && (oldPrice > 0)) {
            (((oldPrice - newPrice) / oldPrice) * 100).toInt()
        } else {
            0
        }
}
