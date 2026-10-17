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
    val lastTrackedTime: Long = System.currentTimeMillis(),
) {
    val discountPercentage: Int
        get() = if ((oldPrice > newPrice) && (oldPrice > 0)) {
            (((oldPrice - newPrice) / oldPrice) * 100).toInt()
        } else {
            0
        }

    val displayStore: String
        get() = when {
            link.contains("amazon", ignoreCase = true) -> "Amazon"
            link.contains("flipkart", ignoreCase = true) -> "Flipkart"
            link.contains("myntra", ignoreCase = true) -> "Myntra"
            id.startsWith("amazon", ignoreCase = true) -> "Amazon"
            id.startsWith("flipkart", ignoreCase = true) -> "Flipkart"
            id.startsWith("myntra", ignoreCase = true) -> "Myntra"
            else -> "Amazon"
        }
}
