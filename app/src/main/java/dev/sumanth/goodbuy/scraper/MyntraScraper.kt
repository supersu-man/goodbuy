package dev.sumanth.goodbuy.scraper

import dev.sumanth.goodbuy.model.Product
import dev.sumanth.goodbuy.util.ScraperUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.Locale
import java.util.UUID

object MyntraScraper {

    /**
     * Scrapes search results from Myntra for a given query.
     *
     * @param query Search query term (e.g., "shirt", "watch", "shoes")
     * @param pages Number of search result pages to fetch (default = 1)
     * @return Result containing list of scraped [Product] items or Exception
     */
    suspend fun searchMyntra(
        query: String,
        pages: Int = 1
    ): Result<List<Product>> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        val allProducts = mutableListOf<Product>()
        val seenIds = mutableSetOf<String>()
        val encodedQuery = ScraperUtils.encodeQuery(trimmedQuery)

        val slug = trimmedQuery
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .replace(Regex("^-+|-+$"), "")

        for (page in 1..pages) {
            val pageParam = if (page > 1) "&p=$page" else ""
            val searchUrl = "https://www.myntra.com/$slug?rawQuery=$encodedQuery$pageParam"
            val referer = "https://www.myntra.com/"

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

                    val doc = Jsoup.parse(html, "https://www.myntra.com")
                    val pageProducts = parseMyntraHtml(html, doc, trimmedQuery)

                    for (product in pageProducts) {
                        if (seenIds.add(product.id.ifEmpty { product.link })) {
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
     * Scrapes a single product page on Myntra (matches myntra/product-scraper.js).
     */
    suspend fun scrapeMyntraProduct(idOrUrl: String): Result<Product> = withContext(Dispatchers.IO) {
        val url = if (idOrUrl.startsWith("http")) {
            idOrUrl
        } else {
            val cleanId = idOrUrl.removePrefix("myntra_")
            "https://www.myntra.com/$cleanId"
        }

        val referer = "https://www.myntra.com/"
        val headers = ScraperUtils.getHeaders(referer)
        val request = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()

        try {
            val response = ScraperUtils.httpClient.newCall(request).execute()
            val html = response.body?.string() ?: ""
            response.close()

            if (html.isBlank()) return@withContext Result.failure(Exception("Empty response"))

            val doc = Jsoup.parse(html, url)

            // Try window.__myx pdpData
            val myxRegex = Regex("<script>\\s*window\\.__myx\\s*=\\s*(\\{[\\s\\S]*?\\})\\s*</script>")
            val match = myxRegex.find(html)
            if (match != null) {
                try {
                    val jsonStr = match.groupValues[1]
                    val json = JSONObject(jsonStr)
                    if (json.has("pdpData")) {
                        val pdp = json.getJSONObject("pdpData")
                        val id = pdp.optString("id", "")
                        val name = pdp.optString("name", "")
                        val brandObj = pdp.optJSONObject("brand")
                        val brand = brandObj?.optString("name", "") ?: ""
                        val title = if (brand.isNotBlank() && !name.startsWith(brand, ignoreCase = true)) "$brand $name" else name

                        val priceObj = pdp.optJSONObject("price")
                        val discounted = priceObj?.optDouble("discounted", 0.0) ?: pdp.optDouble("price", 0.0)
                        val mrp = priceObj?.optDouble("mrp", discounted) ?: pdp.optDouble("mrp", discounted)

                        val ratingsObj = pdp.optJSONObject("ratings")
                        val avgRating = ratingsObj?.optDouble("averageRating", 4.3) ?: 4.3
                        val totalCount = ratingsObj?.optInt("totalCount", 100) ?: 100

                        val albumImages = pdp.optJSONObject("media")?.optJSONArray("albums")?.optJSONObject(0)?.optJSONArray("images")
                        var primaryImage = ""
                        if (albumImages != null && albumImages.length() > 0) {
                            primaryImage = albumImages.getJSONObject(0).optString("imageURL", albumImages.getJSONObject(0).optString("src", ""))
                        }
                        if (primaryImage.startsWith("//")) primaryImage = "https:$primaryImage"

                        if (title.isNotBlank()) {
                            return@withContext Result.success(
                                Product(
                                    id = "myntra_$id",
                                    title = title,
                                    description = "Scraped from Myntra Product Page",
                                    category = "Fashion",
                                    imageUrl = primaryImage,
                                    oldPrice = if (mrp > discounted) mrp else 0.0,
                                    newPrice = if (discounted > 0.0) discounted else mrp,
                                    rating = avgRating.coerceIn(1.0, 5.0),
                                    reviewCount = totalCount,
                                    link = url
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }

            // Fallback DOM
            val title = doc.selectFirst("h1.pdp-title, h1.pdp-name")?.text()?.trim() ?: doc.title()
            val brand = doc.selectFirst("h1.pdp-brand")?.text()?.trim() ?: ""
            val fullTitle = if (brand.isNotBlank() && !title.startsWith(brand, ignoreCase = true)) "$brand $title" else title

            val priceText = doc.selectFirst("span.pdp-price")?.text() ?: ""
            val mrpText = doc.selectFirst("span.pdp-mrp")?.text() ?: ""
            val newPrice = ScraperUtils.parsePrice(priceText)
            val oldPrice = ScraperUtils.parsePrice(mrpText)

            val imgEl = doc.selectFirst("div.image-grid-image img, div.pdp-image img")
            var image = imgEl?.attr("src") ?: ""
            if (image.startsWith("//")) image = "https:$image"

            Result.success(
                Product(
                    id = "myntra_" + UUID.randomUUID().toString(),
                    title = fullTitle,
                    description = "Scraped from Myntra Product Page",
                    category = "Fashion",
                    imageUrl = image,
                    oldPrice = if (oldPrice > newPrice) oldPrice else 0.0,
                    newPrice = if (newPrice > 0.0) newPrice else oldPrice,
                    rating = 4.3,
                    reviewCount = 100,
                    link = url
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseMyntraHtml(html: String, doc: Document, categoryQuery: String): List<Product> {
        val results = mutableListOf<Product>()

        // 1. Try window.__myx extraction
        val myxRegex = Regex("<script>\\s*window\\.__myx\\s*=\\s*(\\{[\\s\\S]*?\\})\\s*</script>")
        val match = myxRegex.find(html)
        if (match != null) {
            try {
                val jsonStr = match.groupValues[1]
                val json = JSONObject(jsonStr)
                if (json.has("searchData")) {
                    val searchData = json.getJSONObject("searchData")
                    if (searchData.has("results")) {
                        val resultsObj = searchData.getJSONObject("results")
                        if (resultsObj.has("products")) {
                            val productsArr = resultsObj.getJSONArray("products")
                            for (i in 0 until productsArr.length()) {
                                val p = productsArr.getJSONObject(i)
                                val productId = p.optString("productId", p.optString("id", UUID.randomUUID().toString()))
                                val brand = p.optString("brand", "")
                                val productTitle = p.optString("product", p.optString("additionalInfo", ""))
                                val title = if (brand.isNotBlank() && !productTitle.startsWith(brand, ignoreCase = true)) {
                                    "$brand $productTitle"
                                } else {
                                    productTitle.ifBlank { brand }
                                }
                                if (title.isBlank()) continue

                                val price = p.optDouble("price", 0.0)
                                val mrp = p.optDouble("mrp", price)
                                val discount = p.optString("discountDisplayLabel", "")
                                val rating = p.optDouble("rating", 4.3)
                                val ratingCount = p.optInt("ratingCount", 100)

                                val imagesArr = p.optJSONArray("images")
                                var primaryImage = ""
                                if (imagesArr != null && imagesArr.length() > 0) {
                                    val firstImg = imagesArr.getJSONObject(0)
                                    primaryImage = firstImg.optString("src", "")
                                }
                                if (primaryImage.startsWith("//")) {
                                    primaryImage = "https:$primaryImage"
                                }

                                val landingPageUrl = p.optString("landingPageUrl", "")
                                val fullLink = if (landingPageUrl.startsWith("http")) {
                                    landingPageUrl
                                } else if (landingPageUrl.isNotBlank()) {
                                    "https://www.myntra.com/$landingPageUrl"
                                } else {
                                    "https://www.myntra.com/"
                                }

                                val descriptionStr = buildString {
                                    if (discount.isNotBlank()) append("$discount • ")
                                    append("Myntra Verified")
                                }.trim(' ', '•')

                                val category = categoryQuery.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

                                results.add(
                                    Product(
                                        id = "myntra_$productId",
                                        title = title,
                                        description = descriptionStr,
                                        category = category,
                                        imageUrl = primaryImage,
                                        oldPrice = if (mrp > price) mrp else 0.0,
                                        newPrice = if (price > 0.0) price else mrp,
                                        rating = rating.coerceIn(1.0, 5.0),
                                        reviewCount = ratingCount,
                                        isFavorite = false,
                                        link = fullLink
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (results.isNotEmpty()) return results

        // 2. Jsoup DOM fallback
        val cards = doc.select("li.product-base, div.product-base")
        for (card in cards) {
            val linkEl = card.selectFirst("a[href]")
            val href = linkEl?.attr("href") ?: ""
            val fullLink = if (href.startsWith("http")) href else "https://www.myntra.com/${href.trimStart('/')}"

            val idMatch = Regex("/(\\d+)/buy").find(href)
            val productId = idMatch?.groupValues?.get(1) ?: UUID.randomUUID().toString()

            val brand = card.selectFirst("h3.product-brand")?.text()?.trim() ?: ""
            val prodName = card.selectFirst("h4.product-product")?.text()?.trim() ?: ""
            val title = if (brand.isNotBlank() && !prodName.startsWith(brand, ignoreCase = true)) "$brand $prodName" else (prodName.ifBlank { brand })
            if (title.isBlank()) continue

            val priceText = card.selectFirst("span.product-discountedPrice, div.product-price span")?.text() ?: ""
            val mrpText = card.selectFirst("span.product-strike")?.text() ?: ""
            val discountText = card.selectFirst("span.product-discountPercentage")?.text()?.trim() ?: ""

            val newPrice = ScraperUtils.parsePrice(priceText)
            val oldPrice = ScraperUtils.parsePrice(mrpText)
            if (newPrice <= 0.0 && oldPrice <= 0.0) continue

            val ratingText = card.selectFirst("div.product-ratingsContainer span")?.text() ?: ""
            val rating = ScraperUtils.parseRating(ratingText)

            val ratingCountText = card.selectFirst("div.product-ratingsCount")?.text() ?: ""
            val reviewCount = ScraperUtils.parseReviewCount(ratingCountText)

            val imgEl = card.selectFirst("picture.img-responsive img, img.img-responsive")
            var image = imgEl?.attr("src") ?: ""
            if (image.startsWith("//")) image = "https:$image"

            val descriptionStr = buildString {
                if (discountText.isNotBlank()) append("$discountText • ")
                append("Myntra Fashion")
            }.trim(' ', '•')

            val category = categoryQuery.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

            results.add(
                Product(
                    id = "myntra_$productId",
                    title = title,
                    description = descriptionStr,
                    category = category,
                    imageUrl = image,
                    oldPrice = if (oldPrice > newPrice) oldPrice else 0.0,
                    newPrice = if (newPrice > 0.0) newPrice else oldPrice,
                    rating = rating,
                    reviewCount = reviewCount,
                    isFavorite = false,
                    link = fullLink
                )
            )
        }

        return results
    }
}
