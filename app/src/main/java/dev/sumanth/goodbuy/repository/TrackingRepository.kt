package dev.sumanth.goodbuy.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.sumanth.goodbuy.db.AppDatabase
import dev.sumanth.goodbuy.db.PriceHistoryEntity
import dev.sumanth.goodbuy.db.toDomainModel
import dev.sumanth.goodbuy.db.toEntity
import dev.sumanth.goodbuy.model.Product
import dev.sumanth.goodbuy.model.TrackedGroup
import dev.sumanth.goodbuy.scraper.AmazonScraper
import dev.sumanth.goodbuy.scraper.FlipkartScraper
import dev.sumanth.goodbuy.scraper.MyntraScraper
import dev.sumanth.goodbuy.util.NotificationHelper
import dev.sumanth.goodbuy.worker.PriceCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

object TrackingRepository {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var database: AppDatabase? = null
    private var appContextRef: Context? = null

    private val _trackedProducts = MutableStateFlow<List<Product>>(emptyList())
    val trackedProducts: StateFlow<List<Product>> = _trackedProducts.asStateFlow()

    private val _trackedGroups = MutableStateFlow<List<TrackedGroup>>(emptyList())
    val trackedGroups: StateFlow<List<TrackedGroup>> = _trackedGroups.asStateFlow()

    private val _lastRefreshedTime = MutableStateFlow(0L)
    val lastRefreshedTime: StateFlow<Long> = _lastRefreshedTime.asStateFlow()

    fun init(context: Context) {
        val appContext = context.applicationContext
        appContextRef = appContext
        if (database != null) {
            schedulePriceCheck(appContext)
            return
        }
        val db = AppDatabase.getDatabase(appContext)
        database = db

        schedulePriceCheck(appContext)

        scope.launch {
            db.productDao().getTrackedProducts().collect { _trackedProducts.value = it.map { e -> e.toDomainModel() } }
        }
        scope.launch {
            db.groupDao().getTrackedGroups().collect { _trackedGroups.value = it.map { e -> e.toDomainModel() } }
        }
    }

    private fun schedulePriceCheck(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<PriceCheckWorker>(
            30, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "GoodBuyPriceCheckWork",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }

    fun isTracked(productId: String): Boolean = _trackedProducts.value.any { it.id == productId }

    fun clearAll() {
        database?.let { db ->
            scope.launch {
                db.productDao().deleteAllProducts()
                db.groupDao().deleteAllGroups()
                db.priceHistoryDao().deleteAllHistory()
                _lastRefreshedTime.update { 0L }
            }
        } ?: run {
            _trackedProducts.value = emptyList()
            _trackedGroups.value = emptyList()
            _lastRefreshedTime.value = 0L
        }
    }

    suspend fun checkAndRefreshTrackedPrices(minIntervalMillis: Long = 1800000L, force: Boolean = false): Int {
        val current = _trackedProducts.value
        if (current.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val last = _lastRefreshedTime.value
        val oldest = current.minOfOrNull { it.lastTrackedTime } ?: 0L

        if (force || last == 0L || now - last >= minIntervalMillis || now - oldest >= minIntervalMillis) {
            return refreshTrackedPrices()
        }
        return 0
    }

    suspend fun refreshTrackedPrices(): Int = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext 0
        val current = _trackedProducts.value
        if (current.isEmpty()) return@withContext 0

        var updatedCount = 0
        val now = System.currentTimeMillis()

        for (product in current) {
            try {
                val result = when {
                    product.displayStore.equals("Amazon", true) || product.link.contains("amazon", true) || product.id.startsWith("amazon", true) ->
                        AmazonScraper.scrapeAmazonProduct(product.link.ifEmpty { product.id })
                    product.displayStore.equals("Flipkart", true) || product.link.contains("flipkart", true) ->
                        FlipkartScraper.scrapeFlipkartProduct(product.link.ifEmpty { product.id })
                    product.displayStore.equals("Myntra", true) || product.link.contains("myntra", true) || product.id.startsWith("myntra", true) ->
                        MyntraScraper.scrapeMyntraProduct(product.link.ifEmpty { product.id })
                    else -> {
                        val search = ProductRepository.searchAll(product.title.take(30))
                        search.map { list -> list.find { it.id == product.id || it.link == product.link } ?: list.firstOrNull() ?: product }
                    }
                }

                var updated = false
                result.onSuccess { match ->
                    if (match != null && match.newPrice > 0.0 && match.newPrice != product.newPrice) {
                        val oldPrice = product.newPrice
                        val newPrice = match.newPrice
                        val newProd = product.copy(oldPrice = oldPrice, newPrice = newPrice, rating = if (match.rating > 0) match.rating else product.rating, lastTrackedTime = now)
                        db.productDao().insertProduct(newProd.toEntity())
                        db.priceHistoryDao().insertPriceHistory(PriceHistoryEntity(productId = product.id, price = newPrice, timestamp = now))
                        updatedCount++
                        updated = true

                        if (newPrice < oldPrice) {
                            appContextRef?.let { ctx ->
                                NotificationHelper.showPriceDropNotification(ctx, product.title, oldPrice, newPrice, product.id)
                            }
                        }
                    }
                }

                if (!updated && product.link.isBlank()) {
                    ProductRepository.searchAll(product.title.take(30)).onSuccess { live ->
                        val match = live.find { it.id == product.id || it.link == product.link }
                        if (match != null && match.newPrice > 0.0 && match.newPrice != product.newPrice) {
                            val oldPrice = product.newPrice
                            val newPrice = match.newPrice
                            val newProd = product.copy(oldPrice = oldPrice, newPrice = newPrice, lastTrackedTime = now)
                            db.productDao().insertProduct(newProd.toEntity())
                            db.priceHistoryDao().insertPriceHistory(PriceHistoryEntity(productId = product.id, price = newPrice, timestamp = now))
                            updatedCount++
                            updated = true

                            if (newPrice < oldPrice) {
                                appContextRef?.let { ctx ->
                                    NotificationHelper.showPriceDropNotification(ctx, product.title, oldPrice, newPrice, product.id)
                                }
                            }
                        }
                    }
                }

                if (!updated) {
                    db.productDao().insertProduct(product.copy(lastTrackedTime = now).toEntity())
                }
            } catch (e: Exception) {
                e.printStackTrace()
                db.productDao().insertProduct(product.copy(lastTrackedTime = now).toEntity())
            }
        }
        _lastRefreshedTime.update { now }
        updatedCount
    }

    fun addTrackedProduct(product: Product) {
        val now = System.currentTimeMillis()
        val tracked = product.copy(lastTrackedTime = now)
        database?.let { db ->
            scope.launch {
                db.productDao().insertProduct(tracked.toEntity())
                db.priceHistoryDao().insertPriceHistory(PriceHistoryEntity(productId = product.id, price = product.newPrice, timestamp = now))
                _lastRefreshedTime.update { if (it == 0L) now else it }
            }
        } ?: run {
            _trackedProducts.update { it.filterNot { p -> p.id == product.id } + tracked }
            _lastRefreshedTime.update { if (it == 0L) now else it }
        }
    }

    fun removeTrackedProduct(productId: String) {
        database?.let { db ->
            scope.launch {
                db.productDao().deleteProductById(productId)
                db.priceHistoryDao().deleteHistoryForProduct(productId)
                db.groupDao().getTrackedGroupsList().map { it.toDomainModel() }.forEach { group ->
                    val updatedIds = group.productIds.filterNot { it == productId }
                    if (updatedIds.size >= 2) {
                        db.groupDao().insertGroup(group.copy(productIds = updatedIds).toEntity())
                    } else {
                        db.groupDao().deleteGroupById(group.id)
                    }
                }
            }
        } ?: run {
            _trackedProducts.update { current -> current.filterNot { it.id == productId } }
            _trackedGroups.update { groups ->
                groups.mapNotNull { group ->
                    val updatedIds = group.productIds.filterNot { it == productId }
                    if (updatedIds.size >= 2) group.copy(productIds = updatedIds) else null
                }
            }
        }
    }

    fun groupProducts(selectedProductIds: List<String>, selectedGroupIds: List<String> = emptyList()): TrackedGroup? {
        val allIds = selectedProductIds.toMutableSet()
        val currentGroups = _trackedGroups.value.toMutableList()
        selectedGroupIds.forEach { gId ->
            currentGroups.find { it.id == gId }?.let { existing ->
                allIds.addAll(existing.productIds)
                currentGroups.removeAll { it.id == gId }
                database?.let { db -> scope.launch { db.groupDao().deleteGroupById(gId) } }
            }
        }

        if (allIds.size < 2) return null
        val products = getProductsForIds(allIds.toList())
        val name = "${products.firstOrNull()?.title?.take(25) ?: "Product Group"} & more"
        val newGroup = TrackedGroup(id = UUID.randomUUID().toString(), name = name, productIds = allIds.toList())

        database?.let { db ->
            scope.launch { db.groupDao().insertGroup(newGroup.toEntity()) }
        } ?: run {
            _trackedGroups.update { groups -> groups.filterNot { it.id in selectedGroupIds } + newGroup }
        }
        return newGroup
    }

    fun ungroupGroup(groupId: String) {
        database?.let { db -> scope.launch { db.groupDao().deleteGroupById(groupId) } }
            ?: run { _trackedGroups.update { groups -> groups.filterNot { it.id == groupId } } }
    }

    fun removeProductFromGroup(groupId: String, productId: String) {
        database?.let { db ->
            scope.launch {
                db.groupDao().getTrackedGroupsList().find { it.id == groupId }?.toDomainModel()?.let { group ->
                    val updatedIds = group.productIds.filterNot { it == productId }
                    if (updatedIds.size >= 2) {
                        db.groupDao().insertGroup(group.copy(productIds = updatedIds).toEntity())
                    } else {
                        db.groupDao().deleteGroupById(groupId)
                    }
                }
            }
        } ?: run {
            _trackedGroups.update { groups ->
                groups.mapNotNull { group ->
                    if (group.id == groupId) {
                        val updatedIds = group.productIds.filterNot { it == productId }
                        if (updatedIds.size >= 2) group.copy(productIds = updatedIds) else null
                    } else group
                }
            }
        }
    }

    fun getProductsForIds(ids: List<String>): List<Product> {
        val idSet = ids.toSet()
        return _trackedProducts.value.filter { it.id in idSet }
    }

    fun getProductsForGroup(group: TrackedGroup): List<Product> = getProductsForIds(group.productIds)

    fun getBestProductForGroup(group: TrackedGroup): Product? = getProductsForGroup(group).minByOrNull { it.newPrice }

    fun getLeastPriceForGroup(group: TrackedGroup): Double? = getBestProductForGroup(group)?.newPrice

    fun getUngroupedTrackedProducts(): List<Product> {
        val groupedIds = _trackedGroups.value.flatMap { it.productIds }.toSet()
        return _trackedProducts.value.filterNot { it.id in groupedIds }
    }

    fun getPriceHistory(productId: String): Flow<List<PriceHistoryEntity>> {
        return database?.priceHistoryDao()?.getHistoryForProduct(productId) ?: error("Database not initialized")
    }
}
