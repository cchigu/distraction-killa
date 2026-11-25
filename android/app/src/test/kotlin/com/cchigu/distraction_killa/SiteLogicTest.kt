package com.cchigu.distraction_killa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteLogicTest {

    @Test
    fun domainOf_normalizes() {
        assertEquals("youtube.com", UrlReader.domainOf("https://www.youtube.com/watch?v=1"))
        assertEquals("youtube.com", UrlReader.domainOf("m.youtube.com"))
        assertEquals("reddit.com", UrlReader.domainOf("reddit.com/r/all"))
        assertEquals("news.ycombinator.com", UrlReader.domainOf("news.ycombinator.com"))
        assertEquals("example.co.uk", UrlReader.domainOf("HTTP://Example.co.uk:8080/x"))
        assertEquals("m.me", UrlReader.domainOf("m.me")) // don't strip down to a bare TLD
    }

    @Test
    fun domainOf_rejectsNonUrls() {
        assertNull(UrlReader.domainOf("Search or enter address"))
        assertNull(UrlReader.domainOf("about:blank"))
        assertNull(UrlReader.domainOf("cats"))
        assertNull(UrlReader.domainOf(""))
        assertNull(UrlReader.domainOf("1.5"))
    }

    @Test
    fun credit_splitsBrowserTimeBySite() {
        val totals = HashMap<String, Long>()
        val visits = listOf(
            SiteLog.Visit(50, "reddit.com"),   // opened before the span starts
            SiteLog.Visit(130, "youtube.com"),
            SiteLog.Visit(160, ""),            // new tab: not counted
            SiteLog.Visit(170, "reddit.com"),
            SiteLog.Visit(500, "x.com"),       // after the span: ignored
        )
        SiteTime.credit(listOf(100L to 200L, 300L to 320L), visits, totals)
        assertEquals(mapOf("reddit.com" to 30L + 30L + 20L, "youtube.com" to 30L), totals)
    }

    @Test
    fun credit_unknownSiteBeforeFirstVisit() {
        val totals = HashMap<String, Long>()
        SiteTime.credit(listOf(0L to 100L), listOf(SiteLog.Visit(40, "x.com")), totals)
        assertEquals(mapOf("x.com" to 60L), totals)
    }
}
