package dev.sumanth.goodbuy.scraper

import dev.sumanth.goodbuy.model.Product
import dev.sumanth.goodbuy.util.ScraperUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.Locale

object AmazonScraper {

    /**
     * Scrapes search results from Amazon.in for a given query matching the JS scraper implementation.
     *
     * @param query Search query term (e.g., "watch", "running shoes")
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
        val encodedQuery = ScraperUtils.encodeQuery(trimmedQuery)

        for (page in 1..pages) {
            val pageParam = if (page > 1) "&page=$page" else ""
            val searchUrl = "https://www.amazon.in/s?k=$encodedQuery&i=aps$pageParam"
            val referer = "https://www.amazon.in/s/ref=nb_sb_noss_2?url=search-alias%3Daps&field-keywords=$encodedQuery"

            val headers = ScraperUtils.getHeaders(referer)
            val requestBuilder = Request.Builder().url(searchUrl)
            headers.forEach { (key, value) -> requestBuilder.header(key, value) }

            try {
                val response = ScraperUtils.httpClient.newCall(requestBuilder.build()).execute()
                val html = response.body?.string() ?: ""
                response.close()

                if (html.isNotEmpty()) {
                    if (html.contains("validateCaptcha", ignoreCase = true) || html.contains("Robot Check", ignoreCase = true)) {
                        break
                    }

                    val doc = Jsoup.parse(html, "https://www.amazon.in")
                    val pageProducts = parseAmazonHtml(doc, trimmedQuery)

                    for (product in pageProducts) {
                        if (seenAsins.add(product.id)) {
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

    /**
     * Scrapes a single product page on Amazon.in (matches amazon/product-scraper.js).
     */
    suspend fun scrapeAmazonProduct(asinOrUrl: String): Result<Product> = withContext(Dispatchers.IO) {
        val url = if (asinOrUrl.startsWith("http")) {
            asinOrUrl
        } else {
            val cleanAsin = asinOrUrl.removePrefix("amazon_")
            "https://www.amazon.in/dp/$cleanAsin"
        }

        val headers = ScraperUtils.getHeaders(url)
        val request = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()

        try {
            val response = ScraperUtils.httpClient.newCall(request).execute()
            val html = response.body?.string() ?: ""
            response.close()

            if (html.isBlank()) return@withContext Result.failure(Exception("Empty response"))

            val doc = Jsoup.parse(html, url)
            val asinMatch = Regex("/dp/([A-Za-z0-9]{10})", RegexOption.IGNORE_CASE).find(url) ?: Regex("/dp/([A-Za-z0-9]{10})", RegexOption.IGNORE_CASE).find(html)
            val asin = asinMatch?.groupValues?.get(1) ?: "UNKNOWN"

            val title = doc.selectFirst("#productTitle")?.text()?.trim() ?: doc.title().trim()
            if (title.isBlank()) return@withContext Result.failure(Exception("Product title not found"))

            val brand = doc.selectFirst("#bylineInfo")?.text()?.trim()
                ?.replace(Regex("^Visit the\\s+", RegexOption.IGNORE_CASE), "")
                ?.replace(Regex("\\s+Store$", RegexOption.IGNORE_CASE), "")
                ?.replace(Regex("^Brand:\\s*", RegexOption.IGNORE_CASE), "")
                ?: doc.selectFirst("#brand")?.text()?.trim() ?: ""

            val priceText = doc.selectFirst(".a-price:not(.a-text-price) span.a-offscreen")?.text()
                ?: doc.selectFirst("#apex-pricetopay-accessibility-label")?.text() ?: ""
            val mrpText = doc.selectFirst(".a-text-price span.a-offscreen")?.text() ?: ""

            val newPrice = ScraperUtils.parsePrice(priceText)
            val oldPrice = ScraperUtils.parsePrice(mrpText)

            val ratingText = doc.selectFirst("span.a-icon-alt, i.a-icon-star span")?.text() ?: ""
            val rating = ScraperUtils.parseRating(ratingText)

            val reviewsText = doc.selectFirst("#acrCustomerReviewText")?.text() ?: ""
            val reviewCount = ScraperUtils.parseReviewCount(reviewsText)

            val imgEl = doc.selectFirst("img#landingImage, div#imgTagWrapperId img, img.a-dynamic-image")
            var image = imgEl?.attr("src") ?: ""
            if (image.startsWith("//")) image = "https:$image"

            Result.success(
                Product(
                    id = "amazon_$asin",
                    title = title,
                    description = if (brand.isNotBlank()) "Brand: $brand" else "Scraped from Amazon Product Page",
                    category = "General",
                    imageUrl = image,
                    oldPrice = if (oldPrice > newPrice) oldPrice else 0.0,
                    newPrice = if (newPrice > 0.0) newPrice else oldPrice,
                    rating = rating,
                    reviewCount = reviewCount,
                    link = url
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseAmazonHtml(doc: Document, categoryQuery: String): List<Product> {
        val results = mutableListOf<Product>()
        val cards = doc.select("div[data-asin]")
        val seenAsins = mutableSetOf<String>()

        for (el in cards) {
            val asin = el.attr("data-asin")
            if (asin.isBlank() || asin.length != 10 || seenAsins.contains(asin)) continue
            if (el.hasClass("s-widget-spacing-small") && el.select("h2").isEmpty()) continue

            // 1. Title & Brand
            val titleEl = el.selectFirst("h2 span, h2 a span")
            val title = titleEl?.text()?.trim() ?: ""
            if (title.isBlank()) continue

            var brand = ""
            val titleWords = title.split(" ")
            if (titleWords.isNotEmpty()) {
                brand = titleWords[0].replace(Regex("[^A-Za-z0-9]"), "")
            }

            // 2. Prices
            val priceRaw = el.selectFirst(".a-price:not(.a-text-price) span.a-offscreen")?.text() ?: ""
            val mrpRaw = el.selectFirst(".a-price.a-text-price span.a-offscreen")?.text() ?: ""

            val price = ScraperUtils.parsePrice(priceRaw)
            val mrp = ScraperUtils.parsePrice(mrpRaw).let { if (it > 0.0) it else price }
            if (price <= 0.0 && mrp <= 0.0) continue

            // 3. Discount
            val discount = if (mrp > price && price > 0.0) {
                "${Math.round(((mrp - price) / mrp) * 100)}% off"
            } else {
                ""
            }

            // 4. Rating & Review Count
            val ratingText = el.selectFirst("span.a-icon-alt")?.text() ?: ""
            val ratingMatch = Regex("([\\d.]+)\\s*out of", RegexOption.IGNORE_CASE).find(ratingText)
            val rating = ratingMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 4.3

            val reviewsText = el.selectFirst("span.a-size-base.s-underline-text, a[href*=\"#customerReviews\"] span")?.text() ?: ""
            val reviewCount = ScraperUtils.parseReviewCount(reviewsText)

            // 5. Sponsored status
            val sponsored = el.selectFirst("span.s-label-popover-default, span.puis-sponsored-label-text, span:contains(\"Sponsored\")") != null
            if (sponsored) continue

            // 6. Image
            val imgEl = el.selectFirst("img.s-image")
            var image = imgEl?.attr("src") ?: ""
            if (image.startsWith("//")) image = "https:$image"

            // 7. Canonical Product Link
            val productUrl = "https://www.amazon.in/dp/$asin"
            seenAsins.add(asin)

            val descriptionStr = buildString {
                if (discount.isNotBlank()) append("$discount • ")
                if (brand.isNotBlank()) append("Brand: $brand • ")
                append("Amazon Delivered")
            }.trim(' ', '•')

            val category = categoryQuery.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

            results.add(
                Product(
                    id = "amazon_$asin",
                    title = title,
                    description = descriptionStr,
                    category = category,
                    imageUrl = image,
                    oldPrice = if (mrp > price) mrp else 0.0,
                    newPrice = if (price > 0.0) price else mrp,
                    rating = rating,
                    reviewCount = reviewCount,
                    isFavorite = false,
                    link = productUrl
                )
            )
        }

        return results
    }
}
