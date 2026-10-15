package dev.sumanth.goodbuy.repository

import android.content.Context
import android.content.SharedPreferences
import dev.sumanth.goodbuy.model.Product
import dev.sumanth.goodbuy.model.TrackedGroup
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

    private val _trackedProducts = MutableStateFlow<List<Product>>(emptyList())
    val trackedProducts: StateFlow<List<Product>> = _trackedProducts.asStateFlow()

    private val _trackedGroups = MutableStateFlow<List<TrackedGroup>>(emptyList())
    val trackedGroups: StateFlow<List<TrackedGroup>> = _trackedGroups.asStateFlow()

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

    suspend fun refreshTrackedPrices(): Int = withContext(Dispatchers.IO) {
        val currentTracked = _trackedProducts.value
        if (currentTracked.isEmpty()) return@withContext 0

        var updatedCount = 0
        val updatedProducts = currentTracked.toMutableList()

        for (i in updatedProducts.indices) {
            val product = updatedProducts[i]
            val queryTerm = product.title.take(30)
            val searchResult = ProductRepository.searchAll(queryTerm)
            searchResult.onSuccess { liveProducts ->
                val match = liveProducts.find { it.id == product.id || (it.link.isNotBlank() && it.link == product.link) }
                    ?: liveProducts.firstOrNull { it.displayStore == product.displayStore }

                if (match != null && match.newPrice > 0.0 && match.newPrice != product.newPrice) {
                    updatedProducts[i] = product.copy(
                        oldPrice = product.newPrice,
                        newPrice = match.newPrice,
                        rating = if (match.rating > 0) match.rating else product.rating
                    )
                    updatedCount++
                }
            }
        }

        if (updatedCount > 0) {
            _trackedProducts.value = updatedProducts
            saveToPrefs()
        }
        return@withContext updatedCount
    }

    fun addTrackedProduct(product: Product) {
        val current = _trackedProducts.value.toMutableList()
        if (current.none { it.id == product.id }) {
            current.add(0, product)
            _trackedProducts.value = current
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
                            link = obj.optString("link")
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
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
