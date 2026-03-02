package com.example.shoppingassistant.feature.pages.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatTasksTest {

    @Test
    fun normalizeExternalUrl_returns_null_for_empty_input() {
        assertNull(normalizeExternalUrl(null))
        assertNull(normalizeExternalUrl(""))
        assertNull(normalizeExternalUrl("   "))
    }

    @Test
    fun normalizeExternalUrl_adds_https_when_scheme_missing() {
        val normalized = normalizeExternalUrl("example.com/item/123")
        assertEquals("https://example.com/item/123", normalized)
    }

    @Test
    fun normalizeExternalUrl_rejects_non_http_scheme() {
        assertNull(normalizeExternalUrl("ftp://example.com/file"))
    }

    @Test
    fun normalizeExternalUrl_rejects_invalid_host() {
        assertNull(normalizeExternalUrl("not-a-valid-host/path"))
    }

    @Test
    fun normalizeExternalUrl_allows_localhost() {
        val normalized = normalizeExternalUrl("http://localhost:8080/offers/1")
        assertEquals("http://localhost:8080/offers/1", normalized)
    }

    @Test
    fun extractExternalHost_returns_host_without_www_prefix() {
        val host = extractExternalHost("https://www.example.com/item")
        assertEquals("example.com", host)
    }
}
