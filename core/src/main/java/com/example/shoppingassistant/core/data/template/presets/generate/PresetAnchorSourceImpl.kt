package com.example.shoppingassistant.core.data.template.presets.generate

import com.example.shoppingassistant.core.data.db.ProductDao
import com.example.shoppingassistant.domain.template.presets.generate.PresetAnchor
import com.example.shoppingassistant.domain.template.presets.generate.PresetAnchorSource

class PresetAnchorSourceImpl(
    private val dao: ProductDao,
) : PresetAnchorSource {

    override suspend fun listTopAnchors(limit: Int): List<PresetAnchor> {
        val safeLimit = limit.coerceIn(1, 500)
        return dao.topBrandModels(safeLimit)
            .map { row ->
                PresetAnchor(
                    brand = row.brand,
                    model = row.model,
                    samples = row.count,
                )
            }
    }
}
