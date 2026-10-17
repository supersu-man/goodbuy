package dev.sumanth.goodbuy.scraper

import dev.sumanth.goodbuy.model.Product
import dev.sumanth.goodbuy.util.ScraperUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.Locale
import java.util.UUID

object FlipkartScraper {

    /**
     * Scrapes search results from Flipkart for a given query matching the JS scraper implementation.
     *
     * @param query Search query term (e.g., "watch", "running shoes")
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
        val seenPids = mutableSetOf<String>()
        val encodedQuery = ScraperUtils.encodeQuery(trimmedQuery)

        for (page in 1..pages) {
            val pageParam = if (page > 1) "&page=$page" else ""
            val searchUrl = "https://www.flipkart.com/search?q=$encodedQuery&otracker=search&otracker1=search&marketplace=FLIPKART&as-show=on&as=off$pageParam"
            val referer = "https://www.flipkart.com/"

            val headers = ScraperUtils.getHeaders(referer)
            val requestBuilder = Request.Builder().url(searchUrl)
            headers.forEach { (key, value) -> requestBuilder.header(key, value) }

            try {
                val response = ScraperUtils.httpClient.newCall(requestBuilder.build()).execute()
                val html = response.body?.string() ?: ""
                response.close()

                if (html.isNotEmpty()) {
                    if (response.code == 403 || response.code == 429) {
                        break
                    }

                    val doc = Jsoup.parse(html, "https://www.flipkart.com")
                    val pageProducts = parseFlipkartHtml(doc, trimmedQuery)

                    for (product in pageProducts) {
                        if (seenPids.add(product.id)) {
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
     * Scrapes a single product page on Flipkart (matches flipkart/product-scraper.js).
     */
    suspend fun scrapeFlipkartProduct(pidOrUrl: String): Result<Product> = withContext(Dispatchers.IO) {
        val url = if (pidOrUrl.startsWith("http")) {
            pidOrUrl
        } else {
            "https://www.flipkart.com/item/p/itm?pid=$pidOrUrl"
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
            val title = doc.selectFirst("span.B_NuCI, h1._6EBuvT, h1")?.text()?.trim() ?: ""
            if (title.isBlank()) return@withContext Result.failure(Exception("Product title not found"))

            val priceText = doc.selectFirst("div._30jeq3, div.Nx9bqj")?.text() ?: ""
            val mrpText = doc.selectFirst("div._3I9_wc, div.yRaY8j")?.text() ?: ""

            val newPrice = ScraperUtils.parsePrice(priceText)
            val oldPrice = ScraperUtils.parsePrice(mrpText)

            val ratingText = doc.selectFirst("div._3LWZlK, div.XQDdHH")?.text()?.trim() ?: ""
            val rating = ScraperUtils.parseRating(ratingText, 4.2)

            val reviewsText = doc.selectFirst("span._2_R_DZ, span.Wphh3Z")?.text() ?: ""
            val reviewCount = ScraperUtils.parseReviewCount(reviewsText)

            val imgEl = doc.selectFirst("img._396cs4, img.DBC2T, img[src*=\"rukminim\"]")
            var image = imgEl?.attr("src") ?: imgEl?.attr("data-src") ?: ""
            if (image.startsWith("//")) image = "https:$image"

            val pidMatch = Regex("pid=([A-Za-z0-9]+)").find(url) ?: Regex("pid=([A-Za-z0-9]+)").find(html)
            val pid = pidMatch?.groupValues?.get(1) ?: UUID.randomUUID().toString()

            Result.success(
                Product(
                    id = pid,
                    title = title,
                    description = "Scraped from Flipkart Product Page",
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

    private fun parseFlipkartHtml(doc: Document, categoryQuery: String): List<Product> {
        val results = mutableListOf<Product>()
        val cards = doc.select("div[data-id]")
        val seenPids = mutableSetOf<String>()

        for (el in cards) {
            val dataId = el.attr("data-id")
            if (dataId.isBlank() || seenPids.contains(dataId)) continue

            val cls = el.className()
            if (Regex("(_3HqJxg|_2mA8eP|compareBar|compareWidget)", RegexOption.IGNORE_CASE).containsMatchIn(cls)) continue

            val linkEl = el.selectFirst("a[href*=\"/p/\"]")
            val rawHref = linkEl?.attr("href") ?: ""
            if (rawHref.isBlank()) continue

            val productUrl = if (rawHref.startsWith("http")) rawHref else "https://www.flipkart.com$rawHref"

            // 1. Title & Brand
            var brand = el.selectFirst("div.Fo1I0b, div._2WkVRV, div.syl9yP")?.text()?.trim() ?: ""
            var title = el.selectFirst("a.atJtCj, a.WKTcLC, a.IRPBll, div.KzDlHZ, a.wTWf8, a[title]")?.text()?.trim() ?: ""
            if (title.isBlank()) {
                title = linkEl?.attr("title") ?: el.selectFirst("img[alt]")?.attr("alt") ?: ""
            }
            if (brand.isBlank() && title.isNotBlank()) {
                val firstWord = title.split(" ").firstOrNull() ?: ""
                if (firstWord.length > 1) brand = firstWord
            }
            if (title.isBlank()) continue

            // 2. Prices
            val rupeeTexts = el.allElements
                .filter { it.childrenSize() == 0 && it.text().contains("₹") }
                .map { it.text().trim() }

            var priceNum = 0.0
            var mrpNum = 0.0
            if (rupeeTexts.isNotEmpty()) {
                priceNum = ScraperUtils.parsePrice(rupeeTexts[0])
                mrpNum = if (rupeeTexts.size > 1) ScraperUtils.parsePrice(rupeeTexts[1]) else priceNum
            }
            if (priceNum <= 0.0 && mrpNum <= 0.0) continue

            // 3. Discount
            val discountText = el.selectFirst("div.UkUFwK span, div._3Ay6Sb span")?.text()?.trim() ?: ""
            val discount = if (discountText.isNotBlank()) {
                discountText
            } else if (mrpNum > priceNum && priceNum > 0.0) {
                "${Math.round(((mrpNum - priceNum) / mrpNum) * 100)}% off"
            } else {
                ""
            }

            // 4. Rating
            val ratingText = el.selectFirst("div.XQDdHH, div._3LWZlK")?.text()?.trim() ?: ""
            val rating = ScraperUtils.parseRating(ratingText, 4.2)

            // 5. Review Count
            val ratingCountText = el.selectFirst("span.Wphh3Z, span._2_R_DZ span")?.text() ?: ""
            val reviewCount = ScraperUtils.parseReviewCount(ratingCountText)

            // 6. Image
            val imgEl = el.selectFirst("img[src*=\"rukminim\"], img[src*=\"image\"], img.MZeksS, img._396cs4, img.DBC2T, img")
            var image = imgEl?.attr("src") ?: imgEl?.attr("data-src") ?: ""
            if (image.startsWith("//")) image = "https:$image"

            // 7. Badge
            val badge = el.selectFirst("div.HZ0E6r, div.s4t9tK, div.x-s6XJ")?.text()?.trim() ?: ""

            seenPids.add(dataId)

            val descriptionStr = buildString {
                if (discount.isNotBlank()) append("$discount • ")
                if (badge.isNotBlank()) append("$badge • ")
                if (brand.isNotBlank()) append("Brand: $brand") else append("Flipkart Assured")
            }.trim(' ', '•')

            val category = categoryQuery.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

            results.add(
                Product(
                    id = dataId,
                    title = title,
                    description = descriptionStr,
                    category = category,
                    imageUrl = image,
                    oldPrice = if (mrpNum > priceNum) mrpNum else 0.0,
                    newPrice = if (priceNum > 0.0) priceNum else mrpNum,
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
