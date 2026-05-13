package com.example.shoppingassistant.core.data.link

import com.example.shoppingassistant.domain.ingest.IngestStatus
import com.example.shoppingassistant.domain.ingest.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkTemplateMapperImplTest {

    private val mapper = LinkTemplateMapperImpl()

    @Test
    fun map_passes_parser_metadata_to_tracked_offer_input() {
        val mapped = mapper.map(
            template = sampleTemplate(
                categoryCode = "FOOD.GROCERIES",
                categoryConfidence = 0.72,
                parserVersion = "avito-2.1",
            ),
            selectedFilters = emptyMap(),
            userId = "42",
        )

        assertEquals("FOOD.GROCERIES", mapped.categoryCode)
        assertEquals(0.72, mapped.categoryConfidence ?: -1.0, 0.0001)
        assertEquals("avito-2.1", mapped.parserVersion)
    }

    @Test
    fun map_sets_null_category_code_for_blank_template_category() {
        val mapped = mapper.map(
            template = sampleTemplate(
                categoryCode = "   ",
                categoryConfidence = 0.1,
                parserVersion = "avito-2.1",
            ),
            selectedFilters = emptyMap(),
            userId = "42",
        )

        assertNull(mapped.categoryCode)
        assertEquals(0.1, mapped.categoryConfidence ?: -1.0, 0.0001)
        assertEquals("avito-2.1", mapped.parserVersion)
    }

    private fun sampleTemplate(
        categoryCode: String,
        categoryConfidence: Double,
        parserVersion: String,
    ): LinkTemplateRaw = LinkTemplateRaw(
        title = "Sample",
        brand = "Brand",
        model = "Model",
        categoryCode = categoryCode,
        categoryConfidence = categoryConfidence,
        parserVersion = parserVersion,
        price = 10.0,
        currency = "RUB",
        imageUrls = listOf("https://example.com/a.jpg"),
        attributes = mapOf("condition" to "new"),
        ingestStatus = IngestStatus.OK,
        sourceMeta = LinkSourceMeta(
            sourceType = SourceType.AVITO,
            sourceId = "avito",
            url = "https://www.avito.ru/test",
        ),
    )
}
