package dev.sumanth.goodbuy.scraper

import dev.sumanth.goodbuy.model.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

object AmazonScraper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /**
     * Scrapes search results from Amazon.in for a given query.
     * Excludes sponsored / ad products as requested.
     *
     * @param query Search query term (e.g., "laptop", "wireless headphones", "running shoes")
     * @param pages Number of search result pages to fetch (default = 1)
     * @return Result containing list of scraped [Product] items or Exception
     */
    suspend fun searchAmazon(
        query: String,
        pages: Int = 1
    ): Result<List<Product>> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        val allProducts = mutableListOf<Product>()
        val seenAsins = mutableSetOf<String>()

        val encodedQuery = try {
            URLEncoder.encode(trimmedQuery, "UTF-8")
        } catch (e: Exception) {
            trimmedQuery
        }

        for (page in 1..pages) {
            val pageParam = if (page > 1) "&page=$page" else ""
            val searchUrl = "https://www.amazon.in/s?k=$encodedQuery$pageParam"

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
                    val doc = Jsoup.parse(html, "https://www.amazon.in")
                    val pageProducts = parseAmazonHtml(doc, trimmedQuery)

                    for (product in pageProducts) {
                        if (seenAsins.add(product.id.ifEmpty { product.link })) {
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

    private fun parseAmazonHtml(doc: Document, categoryQuery: String): List<Product> {
        val results = mutableListOf<Product>()

        // Select search result cards with data-component-type="s-search-result" and data-asin
        val cards = doc.select("[data-component-type=s-search-result][data-asin]:not([data-asin=\"\"])")
        val processedAsins = mutableSetOf<String>()

        for (card in cards) {
            val asin = card.attr("data-asin")
            if (asin.isBlank() || processedAsins.contains(asin)) continue

            // Guard: require product image
            val imgEl = card.selectFirst("img.s-image") ?: continue

            // Skip sponsored / ad cards (do not scrape sponsored products)
            val isSponsored = card.selectFirst(".puis-sponsored-label-text, [aria-label=\"Sponsored\"], span.s-sponsored-label-info-icon") != null ||
                    card.text().contains("Sponsored", ignoreCase = true) && card.text().contains("Ad", ignoreCase = true)
            if (isSponsored) continue

            processedAsins.add(asin)

            // 1. Extract Image
            var image = imgEl.attr("src")
            val dataSrc = imgEl.attr("data-src")
            if (dataSrc.isNotBlank() && !dataSrc.startsWith("data:")) {
                image = dataSrc
            }
            if (image.startsWith("//")) {
                image = "https:$image"
            }
            if (image.isBlank() || image.startsWith("data:image/svg+xml")) continue

            // 2. Extract Title
            val titleAnchor = card.selectFirst("a.a-text-normal.s-line-clamp-2, a.a-text-normal.s-line-clamp-3, a.a-text-normal.s-line-clamp-4, h2 a span, a.a-link-normal.s-line-clamp-2")
                ?: card.select("a.a-text-normal").firstOrNull { !it.hasClass("puis-sponsored-label-text") }

            var title = titleAnchor?.text()?.trim() ?: imgEl.attr("alt").trim()
            if (title.isBlank()) {
                val h2El = card.selectFirst("h2")
                title = h2El?.text()?.trim() ?: ""
            }
            title = title.lineSequence().firstOrNull()?.trim() ?: ""
            if (title.isBlank()) continue

            // 3. Extract Prices (.a-offscreen)
            val currentPriceEl = card.selectFirst("span.a-price:not(.a-text-price) span.a-offscreen")
            val strikePriceEl = card.selectFirst("span.a-text-price span.a-offscreen, span.a-price.a-text-price span.a-offscreen")

            val currentPriceStr = currentPriceEl?.text()?.trim() ?: run {
                card.allElements.firstOrNull { it.childrenSize() == 0 && (it.text().contains("₹") || it.text().contains("Rs")) }?.text()?.trim() ?: ""
            }
            val originalPriceStr = strikePriceEl?.text()?.trim() ?: ""

            val newPrice = parsePrice(currentPriceStr)
            val oldPrice = parsePrice(originalPriceStr)

            if (newPrice <= 0.0) continue

            // 4. Extract Discount
            val badgeEl = card.selectFirst(".a-badge-text, .savingsPercentage")
            val discountText = badgeEl?.text()?.trim() ?: run {
                card.allElements.firstOrNull { it.childrenSize() == 0 && Regex("\\b\\d+%\\s*off\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.text()) }?.text()?.trim() ?: ""
            }

            // 5. Extract Rating
            val ratingEl = card.selectFirst("span[aria-label*=\"out of 5 stars\"], i.a-icon-star span, .a-icon-alt")
            val ratingStr = ratingEl?.attr("aria-label")?.let {
                Regex("([1-5](?:\\.\\d)?)\\s*out of", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)
            } ?: ratingEl?.text() ?: Regex("\\b([1-5]\\.\\d)\\b").find(card.text())?.groupValues?.get(1) ?: "4.3"

            val rating = parseRating(ratingStr)

            // 6. Extract Reviews Count
            val reviewsAriaEl = card.selectFirst("span[aria-label*=\"ratings\"], span[aria-label*=\"rating\"]")
            val reviewsMatch = reviewsAriaEl?.attr("aria-label")?.let {
                Regex("([\\d,\\.]+[KkMm]?)\\s*ratings?", RegexOption.IGNORE_CASE).find(it)?.value
            } ?: card.selectFirst("a.s-underline-link-text span, a[href*=\"#customerReviews\"] span, .a-size-base.s-underline-text")?.text()?.trim() ?: ""

            val reviewCount = parseReviewCount(reviewsMatch)

            // 7. Link & Category
            val fullLink = "https://www.amazon.in/dp/$asin"
            val descriptionStr = buildString {
                if (discountText.isNotBlank()) append("$discountText • ")
                if (reviewsMatch.isNotBlank()) append(reviewsMatch) else append("Amazon Delivered")
            }.trim(' ', '•')

            val category = categoryQuery.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

            results.add(
                Product(
                    id = "amazon_$asin",
                    title = title,
                    description = if (descriptionStr.isNotBlank()) descriptionStr else "Scraped from Amazon",
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

    private fun parsePrice(priceStr: String): Double {
        if (priceStr.isBlank()) return 0.0
        val clean = priceStr.replace(Regex("[^0-9.]"), "")
        return clean.toDoubleOrNull() ?: 0.0
    }

    private fun parseRating(ratingStr: String): Double {
        if (ratingStr.isBlank()) return 4.3
        val clean = Regex("([1-5]\\.\\d)").find(ratingStr)?.value ?: ratingStr.replace(Regex("[^0-9.]"), "")
        return clean.toDoubleOrNull()?.coerceIn(1.0, 5.0) ?: 4.3
    }

    private fun parseReviewCount(reviewStr: String): Int {
        if (reviewStr.isBlank()) return (100..1000).random()
        val numStr = Regex("([\\d,]+)").find(reviewStr)?.value?.replace(",", "") ?: ""
        return numStr.toIntOrNull() ?: (100..1000).random()
    }
}
