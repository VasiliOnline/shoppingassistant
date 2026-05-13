package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.core.network.createBackendHttpClient
import com.example.shoppingassistant.domain.catalog.BrowseNode
import com.example.shoppingassistant.domain.catalog.BrowseNodeKind
import com.example.shoppingassistant.domain.catalog.BrowseNodeStatus
import com.example.shoppingassistant.domain.catalog.BrowseTargetType
import com.example.shoppingassistant.domain.catalog.GoogleTaxonomyMapping
import com.example.shoppingassistant.domain.catalog.GoogleTaxonomyMappingType
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class Stage20TaxonomyApiRepositoriesTest {
    @Test
    fun listMappings_usesSeedFallbackWhenRemotePayloadIsEmpty() = runBlocking {
        val fallbackRows = listOf(
            GoogleTaxonomyMapping(
                canonicalCode = "TECH.PHONES",
                mappingType = GoogleTaxonomyMappingType.SINGLE,
                googleIds = listOf(267L),
                googlePaths = listOf("Electronics > Communications > Telephony > Mobile Phones"),
            ),
        )

        val result = withJsonServer(
            path = "/api/catalog/google-mappings",
            body = "[]",
        ) { baseUrl ->
            val backendClient = createBackendHttpClient()
            try {
                GoogleTaxonomyMappingApiRepository(
                    backendClient = backendClient,
                    fallback = GoogleTaxonomyMappingRepositoryImpl(fallbackRows),
                    allowSeedFallback = false,
                    versionVerifier = NoopCatalogRuntimeVersionVerifier,
                    baseUrl = baseUrl,
                ).listMappings()
            } finally {
                backendClient.client.close()
            }
        }

        assertEquals(fallbackRows, result)
    }

    @Test
    fun listBrowseNodes_usesSeedFallbackWhenRemotePayloadIsEmpty() = runBlocking {
        val fallbackRows = listOf(
            BrowseNode(
                browseCode = "ROOT.TECH.PHONES",
                parentBrowseCode = "ROOT.TECH",
                nodeKind = BrowseNodeKind.LEAF_LINK,
                title = localizedTextOf(
                    "ru" to "Смартфоны",
                    "en" to "Smartphones",
                ),
                targetCategoryCode = "TECH.PHONES",
                targetType = BrowseTargetType.CATEGORY,
                order = 1,
                status = BrowseNodeStatus.ACTIVE,
            ),
        )

        val result = withJsonServer(
            path = "/api/catalog/browse-nodes",
            body = "[]",
        ) { baseUrl ->
            val backendClient = createBackendHttpClient()
            try {
                BrowseNodeApiRepository(
                    backendClient = backendClient,
                    fallback = BrowseNodeRepositoryImpl(fallbackRows),
                    allowSeedFallback = false,
                    versionVerifier = NoopCatalogRuntimeVersionVerifier,
                    baseUrl = baseUrl,
                ).listBrowseNodes()
            } finally {
                backendClient.client.close()
            }
        }

        assertEquals(fallbackRows, result)
    }

    @Test
    fun listMappings_prefersRemotePayloadWhenBackendReturnsRows() = runBlocking {
        val remoteRows = listOf(
            GoogleTaxonomyMapping(
                canonicalCode = "TECH.LAPTOP",
                mappingType = GoogleTaxonomyMappingType.SINGLE,
                googleIds = listOf(328L),
                googlePaths = listOf("Electronics > Computers > Laptops"),
            ),
        )

        val result = withJsonServer(
            path = "/api/catalog/google-mappings",
            body = json.encodeToString(
                ListSerializer(GoogleTaxonomyMapping.serializer()),
                remoteRows,
            ),
        ) { baseUrl ->
            val backendClient = createBackendHttpClient()
            try {
                GoogleTaxonomyMappingApiRepository(
                    backendClient = backendClient,
                    fallback = GoogleTaxonomyMappingRepositoryImpl(),
                    allowSeedFallback = false,
                    versionVerifier = NoopCatalogRuntimeVersionVerifier,
                    baseUrl = baseUrl,
                ).listMappings()
            } finally {
                backendClient.client.close()
            }
        }

        assertEquals(remoteRows, result)
    }

    private suspend fun <T> withJsonServer(
        path: String,
        body: String,
        block: suspend (baseUrl: String) -> T,
    ): T {
        ServerSocket().use { server ->
            server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
            val worker = Thread {
                server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                    val requestLine = reader.readLine().orEmpty()
                    while (true) {
                        val headerLine = reader.readLine() ?: break
                        if (headerLine.isEmpty()) break
                    }
                    check(requestLine.startsWith("GET $path")) {
                        "Unexpected request line '$requestLine' for '$path'."
                    }

                    val responseBody = body.toByteArray(Charsets.UTF_8)
                    val responseHead = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: application/json\r\n")
                        append("Content-Length: ${responseBody.size}\r\n")
                        append("Connection: close\r\n")
                        append("\r\n")
                    }.toByteArray(Charsets.UTF_8)

                    socket.getOutputStream().use { output ->
                        output.write(responseHead)
                        output.write(responseBody)
                        output.flush()
                    }
                }
            }
            worker.start()
            return try {
                block("http://127.0.0.1:${server.localPort}")
            } finally {
                worker.join(5_000)
            }
        }
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
