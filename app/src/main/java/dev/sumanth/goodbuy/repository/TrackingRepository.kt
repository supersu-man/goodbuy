package dev.sumanth.goodbuy.repository

import android.content.Context
import android.content.SharedPreferences
import dev.sumanth.goodbuy.model.Product
import dev.sumanth.goodbuy.model.TrackedGroup
import dev.sumanth.goodbuy.scraper.AmazonScraper
import dev.sumanth.goodbuy.scraper.FlipkartScraper
import dev.sumanth.goodbuy.scraper.MyntraScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object TrackingRepository {
    private const val PREFS_NAME = "goodbuy_tracking_prefs"
    private const val KEY_PRODUCTS = "tracked_products"
    private const val KEY_GROUPS = "tracked_groups"
    private const val KEY_LAST_REFRESHED_TIME = "last_refreshed_time"

    private val _trackedProducts = MutableStateFlow<List<Product>>(emptyList())
    val trackedProducts: StateFlow<List<Product>> = _trackedProducts.asStateFlow()

    private val _trackedGroups = MutableStateFlow<List<TrackedGroup>>(emptyList())
    val trackedGroups: StateFlow<List<TrackedGroup>> = _trackedGroups.asStateFlow()

    private val _lastRefreshedTime = MutableStateFlow<Long>(0L)
    val lastRefreshedTime: StateFlow<Long> = _lastRefreshedTime.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFromPrefs()
    }

    fun isTracked(productId: String): Boolean {
        return _trackedProducts.value.any { it.id == productId }
    }

    fun clearAll() {
        _trackedProducts.value = emptyList()
        _trackedGroups.value = emptyList()
        saveToPrefs()
    }

    suspend fun checkAndRefreshTrackedPrices(
        minIntervalMillis: Long = 30 * 60 * 1000L,
        force: Boolean = false
    ): Int {
        val currentTracked = _trackedProducts.value
        if (currentTracked.isEmpty()) return 0

        val currentTime = System.currentTimeMillis()
        val lastRefreshed = _lastRefreshedTime.value
        val oldestTracked = currentTracked.minOfOrNull { it.lastTrackedTime } ?: 0L

        val isStale = force || (lastRefreshed == 0L) ||
                (currentTime - lastRefreshed >= minIntervalMillis) ||
                (currentTime - oldestTracked >= minIntervalMillis)

        if (isStale) {
            return refreshTrackedPrices()
        }
        return 0
    }

    suspend fun refreshTrackedPrices(): Int = withContext(Dispatchers.IO) {
        val currentTracked = _trackedProducts.value
        if (currentTracked.isEmpty()) return@withContext 0

        var updatedCount = 0
        val updatedProducts = currentTracked.toMutableList()
        val currentTime = System.currentTimeMillis()

        for (i in updatedProducts.indices) {
            val product = updatedProducts[i]
            try {
                val singleResult = when {
                    product.displayStore.equals("Amazon", ignoreCase = true) || product.link.contains("amazon", ignoreCase = true) || product.id.startsWith("amazon", ignoreCase = true) -> {
                        AmazonScraper.scrapeAmazonProduct(product.link.ifEmpty { product.id })
                    }
                    product.displayStore.equals("Flipkart", ignoreCase = true) || product.link.contains("flipkart", ignoreCase = true) -> {
                        FlipkartScraper.scrapeFlipkartProduct(product.link.ifEmpty { product.id })
                    }
                    product.displayStore.equals("Myntra", ignoreCase = true) || product.link.contains("myntra", ignoreCase = true) || product.id.startsWith("myntra", ignoreCase = true) -> {
                        MyntraScraper.scrapeMyntraProduct(product.link.ifEmpty { product.id })
                    }
                    else -> {
                        val queryTerm = product.title.take(30)
                        val searchResult = ProductRepository.searchAll(queryTerm)
                        searchResult.map { list -> list.find { it.id == product.id || it.link == product.link } ?: list.firstOrNull() ?: product }
                    }
                }

                var productUpdated = false
                singleResult.onSuccess { match ->
                    if (match != null && match.newPrice > 0.0) {
                        if (match.newPrice != product.newPrice) {
                            updatedProducts[i] = product.copy(
                                oldPrice = product.newPrice,
                                newPrice = match.newPrice,
                                rating = if (match.rating > 0) match.rating else product.rating,
                                lastTrackedTime = currentTime
                            )
                            updatedCount++
                            productUpdated = true
                        }
                    }
                }

                if (!productUpdated && product.link.isBlank()) {
                    val queryTerm = product.title.take(30)
                    val searchResult = ProductRepository.searchAll(queryTerm)
                    searchResult.onSuccess { liveProducts ->
                        val match = liveProducts.find { it.id == product.id || it.link == product.link }
                        if (match != null && match.newPrice > 0.0 && match.newPrice != product.newPrice) {
                            updatedProducts[i] = product.copy(
                                oldPrice = product.newPrice,
                                newPrice = match.newPrice,
                                lastTrackedTime = currentTime
                            )
                            updatedCount++
                            productUpdated = true
                        }
                    }
                }

                if (!productUpdated) {
                    updatedProducts[i] = updatedProducts[i].copy(lastTrackedTime = currentTime)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updatedProducts[i] = updatedProducts[i].copy(lastTrackedTime = currentTime)
            }
        }

        _trackedProducts.value = updatedProducts
        _lastRefreshedTime.value = currentTime
        saveToPrefs()
        return@withContext updatedCount
    }

    fun addTrackedProduct(product: Product) {
        val current = _trackedProducts.value.toMutableList()
        if (current.none { it.id == product.id }) {
            val now = System.currentTimeMillis()
            val trackedProduct = product.copy(lastTrackedTime = now)
            current.add(0, trackedProduct)
            _trackedProducts.value = current
            if (_lastRefreshedTime.value == 0L) {
                _lastRefreshedTime.value = now
            }
            saveToPrefs()
        }
    }

    fun removeTrackedProduct(productId: String) {
        val currentProducts = _trackedProducts.value.filterNot { it.id == productId }
        _trackedProducts.value = currentProducts

        // Also remove product from any groups
        val currentGroups = _trackedGroups.value.mapNotNull { group ->
            val updatedIds = group.productIds.filterNot { it == productId }
            if (updatedIds.size >= 2) {
                group.copy(productIds = updatedIds)
            } else {
                null // Disband group if less than 2 products remain
            }
        }
        _trackedGroups.value = currentGroups
        saveToPrefs()
    }

    fun groupProducts(selectedProductIds: List<String>, selectedGroupIds: List<String> = emptyList()): TrackedGroup? {
        val allProductIdsToGroup = mutableSetOf<String>()
        allProductIdsToGroup.addAll(selectedProductIds)

        // Merge product IDs from selected existing groups
        val remainingGroups = _trackedGroups.value.toMutableList()
        selectedGroupIds.forEach { groupId ->
            val existingGroup = remainingGroups.find { it.id == groupId }
            if (existingGroup != null) {
                allProductIdsToGroup.addAll(existingGroup.productIds)
                remainingGroups.removeAll { it.id == groupId }
            }
        }

        if (allProductIdsToGroup.size < 2) return null

        // Derive group name from first product title
        val products = getProductsForIds(allProductIdsToGroup.toList())
        val firstProductTitle = products.firstOrNull()?.title?.take(25) ?: "Product Group"
        val groupName = "$firstProductTitle & more"

        val newGroup = TrackedGroup(
            id = UUID.randomUUID().toString(),
            name = groupName,
            productIds = allProductIdsToGroup.toList()
        )

        remainingGroups.add(0, newGroup)
        _trackedGroups.value = remainingGroups
        saveToPrefs()
        return newGroup
    }

    fun ungroupGroup(groupId: String) {
        val currentGroups = _trackedGroups.value.filterNot { it.id == groupId }
        _trackedGroups.value = currentGroups
        saveToPrefs()
    }

    fun removeProductFromGroup(groupId: String, productId: String) {
        val currentGroups = _trackedGroups.value.mapNotNull { group ->
            if (group.id == groupId) {
                val updatedIds = group.productIds.filterNot { it == productId }
                if (updatedIds.size >= 2) {
                    group.copy(productIds = updatedIds)
                } else {
                    null // Disband group if fewer than 2 products left
                }
            } else {
                group
            }
        }
        _trackedGroups.value = currentGroups
        saveToPrefs()
    }

    fun getProductsForIds(ids: List<String>): List<Product> {
        val idSet = ids.toSet()
        return _trackedProducts.value.filter { it.id in idSet }
    }

    fun getProductsForGroup(group: TrackedGroup): List<Product> {
        return getProductsForIds(group.productIds)
    }

    fun getBestProductForGroup(group: TrackedGroup): Product? {
        val products = getProductsForGroup(group)
        return products.minByOrNull { it.newPrice }
    }

    fun getLeastPriceForGroup(group: TrackedGroup): Double? {
        return getBestProductForGroup(group)?.newPrice
    }

    /**
     * Returns products that are NOT part of any group yet.
     */
    fun getUngroupedTrackedProducts(): List<Product> {
        val groupedProductIds = _trackedGroups.value.flatMap { it.productIds }.toSet()
        return _trackedProducts.value.filterNot { it.id in groupedProductIds }
    }

    private fun loadFromPrefs() {
        val p = prefs ?: return
        _lastRefreshedTime.value = p.getLong(KEY_LAST_REFRESHED_TIME, 0L)
        val productsJson = p.getString(KEY_PRODUCTS, null)
        val groupsJson = p.getString(KEY_GROUPS, null)

        if (!productsJson.isNullOrEmpty()) {
            try {
                val jsonArray = JSONArray(productsJson)
                val productList = mutableListOf<Product>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    productList.add(
                        Product(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            description = obj.optString("description"),
                            category = obj.optString("category"),
                            imageUrl = obj.optString("imageUrl"),
                            oldPrice = obj.optDouble("oldPrice", 0.0),
                            newPrice = obj.optDouble("newPrice", 0.0),
                            rating = obj.optDouble("rating", 0.0),
                            reviewCount = obj.optInt("reviewCount", 0),
                            isFavorite = obj.optBoolean("isFavorite", false),
                            link = obj.optString("link"),
                            lastTrackedTime = obj.optLong("lastTrackedTime", System.currentTimeMillis())
                        )
                    )
                }
                _trackedProducts.value = productList
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!groupsJson.isNullOrEmpty()) {
            try {
                val jsonArray = JSONArray(groupsJson)
                val groupList = mutableListOf<TrackedGroup>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val pIdsArray = obj.optJSONArray("productIds")
                    val pIds = mutableListOf<String>()
                    if (pIdsArray != null) {
                        for (j in 0 until pIdsArray.length()) {
                            pIds.add(pIdsArray.getString(j))
                        }
                    }
                    groupList.add(
                        TrackedGroup(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            productIds = pIds
                        )
                    )
                }
                _trackedGroups.value = groupList
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveToPrefs() {
        val p = prefs ?: return
        try {
            val productsArray = JSONArray()
            _trackedProducts.value.forEach { product ->
                val obj = JSONObject().apply {
                    put("id", product.id)
                    put("title", product.title)
                    put("description", product.description)
                    put("category", product.category)
                    put("imageUrl", product.imageUrl)
                    put("oldPrice", product.oldPrice)
                    put("newPrice", product.newPrice)
                    put("rating", product.rating)
                    put("reviewCount", product.reviewCount)
                    put("isFavorite", product.isFavorite)
                    put("link", product.link)
                    put("lastTrackedTime", product.lastTrackedTime)
                }
                productsArray.put(obj)
            }

            val groupsArray = JSONArray()
            _trackedGroups.value.forEach { group ->
                val obj = JSONObject().apply {
                    put("id", group.id)
                    put("name", group.name)
                    put("productIds", JSONArray(group.productIds))
                }
                groupsArray.put(obj)
            }

            p.edit()
                .putString(KEY_PRODUCTS, productsArray.toString())
                .putString(KEY_GROUPS, groupsArray.toString())
                .putLong(KEY_LAST_REFRESHED_TIME, _lastRefreshedTime.value)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
