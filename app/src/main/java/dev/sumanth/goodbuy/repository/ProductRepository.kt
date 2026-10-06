package dev.sumanth.goodbuy.repository

import dev.sumanth.goodbuy.model.Product
import dev.sumanth.goodbuy.scraper.AmazonScraper
import dev.sumanth.goodbuy.scraper.FlipkartScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

object ProductRepository {
    val sampleCategories = listOf("All", "Electronics", "Footwear", "Fashion", "Accessories", "Home")

    val sampleProducts = listOf(
        Product(
            id = "1",
            title = "Wireless Noise Cancelling Headphones",
            description = "High fidelity audio with industry-leading active noise cancellation.",
            category = "Electronics",
            imageUrl = "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=600&auto=format&fit=crop&q=80",
            oldPrice = 299.99,
            newPrice = 199.99,
            rating = 4.8,
            reviewCount = 342,
            isFavorite = true
        ),
        Product(
            id = "2",
            title = "Classic Leather Chronograph Watch",
            description = "Genuine leather strap with water resistant stainless steel case.",
            category = "Accessories",
            imageUrl = "https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=600&auto=format&fit=crop&q=80",
            oldPrice = 180.00,
            newPrice = 129.50,
            rating = 4.6,
            reviewCount = 189
        ),
        Product(
            id = "3",
            title = "Pro Running Sports Shoes",
            description = "Lightweight breathable mesh design engineered for ultimate performance.",
            category = "Footwear",
            imageUrl = "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600&auto=format&fit=crop&q=80",
            oldPrice = 150.00,
            newPrice = 89.99,
            rating = 4.9,
            reviewCount = 512,
            isFavorite = true
        ),
        Product(
            id = "4",
            title = "Minimalist Ceramic Coffee Mug",
            description = "Ergonomic matte finish ceramic cup ideal for coffee or hot tea.",
            category = "Home",
            imageUrl = "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?w=600&auto=format&fit=crop&q=80",
            oldPrice = 24.99,
            newPrice = 14.99,
            rating = 4.5,
            reviewCount = 94
        ),
        Product(
            id = "5",
            title = "Smart Fitness Watch Band V2",
            description = "Track heart rate, sleep quality, daily steps and workouts with long battery life.",
            category = "Electronics",
            imageUrl = "https://images.unsplash.com/photo-1579586337278-3befd40fd17a?w=600&auto=format&fit=crop&q=80",
            oldPrice = 99.00,
            newPrice = 59.99,
            rating = 4.4,
            reviewCount = 276
        ),
        Product(
            id = "6",
            title = "Denim Casual Jacket",
            description = "Premium cotton denim jacket tailored for stylish everyday wear.",
            category = "Fashion",
            imageUrl = "https://images.unsplash.com/photo-1551028719-00167b16eac5?w=600&auto=format&fit=crop&q=80",
            oldPrice = 110.00,
            newPrice = 74.99,
            rating = 4.7,
            reviewCount = 142
        ),
        Product(
            id = "7",
            title = "Portable Bluetooth Speaker",
            description = "Compact waterproof outdoor speaker with deep bass and 12-hour playback.",
            category = "Electronics",
            imageUrl = "https://images.unsplash.com/photo-1608043152269-423dbba4e7e1?w=600&auto=format&fit=crop&q=80",
            oldPrice = 79.99,
            newPrice = 49.99,
            rating = 4.7,
            reviewCount = 420
        ),
        Product(
            id = "8",
            title = "Polarized UV Sunglasses",
            description = "Stylish lightweight sunglasses offering 100% UV protection.",
            category = "Accessories",
            imageUrl = "https://images.unsplash.com/photo-1511499767150-a48a237f0083?w=600&auto=format&fit=crop&q=80",
            oldPrice = 65.00,
            newPrice = 39.99,
            rating = 4.3,
            reviewCount = 88
        )
    )

    suspend fun searchFlipkart(query: String): Result<List<Product>> {
        return FlipkartScraper.searchFlipkart(query)
    }

    suspend fun searchAmazon(query: String): Result<List<Product>> {
        return AmazonScraper.searchAmazon(query)
    }

    suspend fun searchAll(query: String): Result<List<Product>> = withContext(Dispatchers.IO) {
        val flipkartDeferred = async { FlipkartScraper.searchFlipkart(query) }
        val amazonDeferred = async { AmazonScraper.searchAmazon(query) }

        val flipkartResult = flipkartDeferred.await()
        val amazonResult = amazonDeferred.await()

        val products = mutableListOf<Product>()
        var flipkartCount = 0
        var amazonCount = 0

        flipkartResult.onSuccess {
            flipkartCount = it.size
            products.addAll(it)
        }
        amazonResult.onSuccess {
            amazonCount = it.size
            products.addAll(it)
        }

        if (products.isEmpty() && flipkartResult.isFailure && amazonResult.isFailure) {
            Result.failure(flipkartResult.exceptionOrNull() ?: amazonResult.exceptionOrNull() ?: Exception("Search failed"))
        } else {
            Result.success(products)
        }
    }
}
