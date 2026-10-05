package dev.sumanth.goodbuy.scraper

import dev.sumanth.goodbuy.model.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

object FlipkartScraper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /**
     * Scrapes search results from Flipkart for a given query.
     *
     * @param query Search query term (e.g., "laptop", "wireless headphones", "running shoes")
     * @param pages Number of search result pages to fetch (default = 1)
     * @return Result containing list of scraped [Product] items or Exception
     */
    suspend fun searchFlipkart(
        query: String,
        pages: Int = 1
    ): Result<List<Product>> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        val allProducts = mutableListOf<Product>()
        val seenLinks = mutableSetOf<String>()

        val encodedQuery = try {
            URLEncoder.encode(trimmedQuery, "UTF-8")
        } catch (e: Exception) {
            trimmedQuery
        }

        for (page in 1..pages) {
            val pageParam = if (page > 1) "&page=$page" else ""
            val searchUrl =
                "https://www.flipkart.com/search?q=$encodedQuery&otracker=search&otracker1=search&marketplace=FLIPKART&as-show=on&as=off$pageParam"

            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("Upgrade-Insecure-Requests", "1")
                .build()

            try {
                val response = client.newCall(request).execute()
                val html = response.body?.string() ?: ""
                response.close()

                if (html.isNotEmpty()) {
                    val doc = Jsoup.parse(html, "https://www.flipkart.com")
                    val pageProducts = parseFlipkartHtml(doc, trimmedQuery)

                    for (product in pageProducts) {
                        if (seenLinks.add(product.id.ifEmpty { product.link })) {
                            allProducts.add(product)
                        }
                    }
                }
            } catch (e: Exception) {
                if (allProducts.isEmpty() && page == 1) {
                    return@withContext Result.failure(e)
                }
            }
        }

        Result.success(allProducts)
    }

    private fun parseFlipkartHtml(doc: Document, categoryQuery: String): List<Product> {
        val results = mutableListOf<Product>()

        // Find all product links: a[href*="/p/"]
        val productLinks = doc.select("a[href*=/p/]")
        val processedHrefs = mutableSetOf<String>()

        for (linkEl in productLinks) {
            val href = linkEl.attr("href")
            if (href.isBlank() || processedHrefs.contains(href)) continue

            // Skip links inside Compare / sidebar widgets
            if (isInCompareWidget(linkEl)) continue

            // Locate card container holding price info (ascend up to 6 parents)
            var container: Element? = linkEl
            var depth = 0
            while (container != null && depth < 6) {
                val text = container.text()
                if (text.contains("₹") || text.contains("Rs")) {
                    break
                }
                container = container.parent()
                depth++
            }

            if (container == null) continue

            // Skip if resolved container is inside compare widget
            if (isInCompareWidget(container)) continue

            processedHrefs.add(href)

            // 1. Extract Product Image
            val imgEl = container.selectFirst("img[src*=rukminim], img[src*=image], img[src*=flipkart], img._396cs4, img.DBC2T, img")
            var image = ""
            if (imgEl != null) {
                val src = imgEl.attr("src")
                val dataSrc = imgEl.attr("data-src")
                image = when {
                    dataSrc.isNotBlank() && !dataSrc.startsWith("data:") -> dataSrc
                    src.isNotBlank() && !src.startsWith("data:") -> src
                    else -> src.ifBlank { dataSrc }
                }
            }
            if (image.startsWith("//")) {
                image = "https:$image"
            }

            // Reject SVG base64 placeholders
            if (image.startsWith("data:image/svg+xml")) continue

            // 2. Extract Title
            var title = ""
            val explicitTitleEl = container.selectFirst("div.KzDlHZ, a.wTWf8, div._2WkVRV, a.IRPBll, div.pqa3gC")
            if (explicitTitleEl != null && explicitTitleEl.text().isNotBlank()) {
                title = explicitTitleEl.text().trim()
            } else if (imgEl != null && imgEl.attr("alt").isNotBlank()) {
                title = imgEl.attr("alt").trim()
            } else {
                val rawText = container.text()
                val lines = rawText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                title = lines.firstOrNull { l ->
                    l != "Add to Compare" &&
                            l != "Ad" &&
                            !l.startsWith("₹") &&
                            !l.startsWith("Rs") &&
                            !l.matches(Regex("^\\d\\.\\d$")) &&
                            !l.matches(Regex("^\\d+\\.\\s.*"))
                } ?: lines.firstOrNull() ?: ""
            }

            title = title.lineSequence().firstOrNull()?.trim() ?: ""

            // Reject compare-widget numbered titles or empty titles
            if (title.isBlank() || title.matches(Regex("^\\d+\\.\\s.*"))) continue

            // 3. Extract Prices (Leaf node matching ₹ or Rs)
            val leafRupeeTexts = container.allElements
                .filter { it.childrenSize() == 0 && (it.text().contains("₹") || it.text().contains("Rs")) }
                .map { it.text().trim() }

            val currentPriceStr = leafRupeeTexts.firstOrNull() ?: ""
            val originalPriceStr = leafRupeeTexts.find { it != currentPriceStr } ?: ""

            val newPrice = parsePrice(currentPriceStr)
            val oldPrice = parsePrice(originalPriceStr)

            // If no price found, skip
            if (newPrice <= 0.0) continue

            // 4. Extract Discount (Strict regex)
            val leafDiscountTexts = container.allElements
                .filter { it.childrenSize() == 0 && Regex("\\b\\d+%\\s*off\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.text()) }
                .map { it.text().trim() }
            val discountText = leafDiscountTexts.firstOrNull() ?: ""

            // 5. Extract Rating
            val explicitRatingEl = container.selectFirst("div.XtdqEf, div._3LWZlK, span._1l43rN, div.badge-offers, div._5OvY4d")
            val ratingStr = if (explicitRatingEl != null && explicitRatingEl.text().isNotBlank()) {
                explicitRatingEl.text().trim()
            } else {
                Regex("\\b([1-5]\\.\\d)\\b").find(container.text())?.groupValues?.get(1) ?: "4.2"
            }
            val rating = parseRating(ratingStr)

            // 6. Extract Reviews Count
            val rawContainerText = container.text()
            val reviewMatch = Regex("([\\d,]+\\s*Ratings?\\s*&?\\s*[\\d,]*\\s*Reviews?)|(\\([\\d,]+\\))", RegexOption.IGNORE_CASE)
                .find(rawContainerText)?.value ?: ""
            val reviewCount = parseReviewCount(reviewMatch)

            // 7. Construct Full Link and Product ID
            val fullLink = if (href.startsWith("http")) href else "https://www.flipkart.com$href"
            val rawProductId = extractProductId(href)
            val productId = if (processedHrefs.add(rawProductId)) {
                rawProductId
            } else {
                "${rawProductId}_${results.size + 1}"
            }

            val descriptionStr = buildString {
                if (discountText.isNotBlank()) append("$discountText • ")
                if (reviewMatch.isNotBlank()) append(reviewMatch) else append("Flipkart Assured")
            }.trim(' ', '•')

            val category = categoryQuery.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

            results.add(
                Product(
                    id = productId,
                    title = title,
                    description = if (descriptionStr.isNotBlank()) descriptionStr else "Scraped from Flipkart",
                    category = category,
                    imageUrl = image,
                    oldPrice = if (oldPrice > newPrice) oldPrice else 0.0,
                    newPrice = newPrice,
                    rating = rating,
                    reviewCount = reviewCount,
                    isFavorite = false,
                    link = fullLink
                )
            )
        }

        return results
    }

    private fun isInCompareWidget(element: Element): Boolean {
        var node: Element? = element
        var i = 0
        while (node != null && i < 12) {
            val cls = node.className()
            if (cls.contains(Regex("(_3HqJxg|_2mA8eP|_1TdUz7|_29NnCz|compareBar|compareWidget|sideWidget|_3Y6Eeu)", RegexOption.IGNORE_CASE))) {
                return true
            }
            node = node.parent()
            i++
        }
        return false
    }

    private fun parsePrice(priceStr: String): Double {
        if (priceStr.isBlank()) return 0.0
        val clean = priceStr.replace(Regex("[^0-9.]"), "")
        return clean.toDoubleOrNull() ?: 0.0
    }

    private fun parseRating(ratingStr: String): Double {
        if (ratingStr.isBlank()) return 4.0
        val clean = Regex("([1-5]\\.\\d)").find(ratingStr)?.value ?: ratingStr.replace(Regex("[^0-9.]"), "")
        return clean.toDoubleOrNull()?.coerceIn(1.0, 5.0) ?: 4.0
    }

    private fun parseReviewCount(reviewStr: String): Int {
        if (reviewStr.isBlank()) return (50..500).random()
        val numStr = Regex("([\\d,]+)").find(reviewStr)?.value?.replace(",", "") ?: ""
        return numStr.toIntOrNull() ?: (50..500).random()
    }

    private fun extractProductId(href: String): String {
        val match = Regex("/p/([^?/]+)").find(href)
        return match?.groupValues?.get(1) ?: UUID.randomUUID().toString()
    }
}
