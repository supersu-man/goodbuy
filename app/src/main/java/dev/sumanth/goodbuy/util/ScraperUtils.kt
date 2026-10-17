package dev.sumanth.goodbuy.util

import okhttp3.OkHttpClient
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object ScraperUtils {

    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36"

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun getHeaders(referer: String = "https://www.google.com"): Map<String, String> {
        return mapOf(
            "accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/jxl,image/avif,image/webp,image/apng,*/*;q=0.8",
            "accept-language" to "en-GB,en;q=0.6",
            "cache-control" to "max-age=0",
            "upgrade-insecure-requests" to "1",
            "user-agent" to USER_AGENT,
            "sec-ch-ua" to "\"Brave\";v=\"155\", \"Chromium\";v=\"155\", \"Not(A:Brand\";v=\"24\"",
            "sec-ch-ua-full-version-list" to "\"Brave\";v=\"155.0.0.0\", \"Chromium\";v=\"155.0.0.0\", \"Not(A:Brand\";v=\"24.0.0.0\"",
            "sec-ch-ua-mobile" to "?0",
            "sec-ch-ua-platform" to "\"Windows\"",
            "sec-ch-ua-platform-version" to "\"19.0.0\"",
            "sec-fetch-dest" to "document",
            "sec-fetch-mode" to "navigate",
            "sec-fetch-site" to "none",
            "referer" to referer
        )
    }

    fun parsePrice(priceStr: String): Double {
        if (priceStr.isBlank()) return 0.0
        val clean = priceStr.replace(Regex("[^0-9.]"), "")
        return clean.toDoubleOrNull() ?: 0.0
    }

    fun parseRating(ratingStr: String, defaultRating: Double = 4.3): Double {
        if (ratingStr.isBlank()) return defaultRating
        val clean = Regex("([1-5]\\.\\d)").find(ratingStr)?.value ?: ratingStr.replace(Regex("[^0-9.]"), "")
        return clean.toDoubleOrNull()?.coerceIn(1.0, 5.0) ?: defaultRating
    }

    fun parseReviewCount(reviewStr: String, defaultCount: Int = (100..1000).random()): Int {
        if (reviewStr.isBlank()) return defaultCount
        val numStr = Regex("([\\d,]+)").find(reviewStr)?.value?.replace(",", "") ?: ""
        return numStr.toIntOrNull() ?: defaultCount
    }

    fun encodeQuery(query: String): String {
        return try {
            URLEncoder.encode(query, "UTF-8")
        } catch (e: Exception) {
            query
        }
    }
}
