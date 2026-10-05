package dev.sumanth.goodbuy

import dev.sumanth.goodbuy.scraper.FlipkartScraper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlipkartScraperTest {

    @Test
    fun testEmptyQueryReturnsEmptyList() = runBlocking {
        val result = FlipkartScraper.searchFlipkart("")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isEmpty() == true)
    }

    @Test
    fun testLiveFlipkartSearch() = runBlocking {
        // Live network search test for query "laptop"
        val result = FlipkartScraper.searchFlipkart("laptop")
        assertTrue("Expected search request to succeed", result.isSuccess)
        val products = result.getOrNull()
        assertNotNull(products)
        if (!products.isNullOrEmpty()) {
            val first = products.first()
            assertTrue("Product title should not be blank", first.title.isNotBlank())
            assertTrue("Product price should be > 0", first.newPrice > 0.0)
            assertTrue("Product image or link should be present", first.imageUrl.isNotBlank() || first.link.isNotBlank())
        }
    }
}
