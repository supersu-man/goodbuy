package dev.sumanth.goodbuy

import dev.sumanth.goodbuy.scraper.AmazonScraper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmazonScraperTest {

    @Test
    fun testEmptyQueryReturnsEmptyList() = runBlocking {
        val result = AmazonScraper.searchAmazon("")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isEmpty() == true)
    }

    @Test
    fun testLiveAmazonSearch() = runBlocking {
        val result = AmazonScraper.searchAmazon("laptop")
        assertTrue("Expected search request to succeed", result.isSuccess)
        val products = result.getOrNull()
        assertNotNull(products)
        if (!products.isNullOrEmpty()) {
            val first = products.first()
            assertTrue("Product title should not be blank", first.title.isNotBlank())
            assertTrue("Product price should be > 0", first.newPrice > 0.0)
            assertTrue("Product link should not be blank", first.link.isNotBlank())
        }
    }
}
