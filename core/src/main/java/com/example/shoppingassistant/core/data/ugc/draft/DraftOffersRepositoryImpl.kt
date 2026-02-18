package com.example.shoppingassistant.core.data.ugc.draft

import com.example.shoppingassistant.domain.ugc.draft.DraftOffer
import com.example.shoppingassistant.domain.ugc.draft.DraftOfferSeed
import com.example.shoppingassistant.domain.ugc.draft.DraftOffersRepository
import com.example.shoppingassistant.domain.ugc.draft.DraftPublishStatus
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DraftOffersRepositoryImpl(
    private val dao: DraftOffersDao,
) : DraftOffersRepository {

    override suspend fun create(seed: DraftOfferSeed): DraftOffer {
        val now = System.currentTimeMillis()
        val draft = DraftOffer(
            id = "draft-${UUID.randomUUID()}",
            createdAtMillis = now,
            updatedAtMillis = now,
            inputOrigin = seed.inputOrigin,
            title = seed.title,
            categoryCode = seed.categoryCode,
            price = seed.price,
            publishStatus = DraftPublishStatus.DRAFT,
        )
        upsert(draft)
        return draft
    }

    override suspend fun upsert(draft: DraftOffer) {
        val payload = DraftOfferJsonCodec.encode(draft)
        dao.upsert(
            DraftOfferEntity(
                id = draft.id,
                payloadJson = payload,
                createdAtMillis = draft.createdAtMillis,
                updatedAtMillis = draft.updatedAtMillis,
                publishStatus = draft.publishStatus.name,
            )
        )
    }

    override suspend fun get(id: String): DraftOffer? =
        dao.get(id)?.let { DraftOfferJsonCodec.decode(it.payloadJson) }

    override fun observe(id: String): Flow<DraftOffer?> =
        dao.observe(id).map { entity -> entity?.let { DraftOfferJsonCodec.decode(it.payloadJson) } }

    override fun observeAll(): Flow<List<DraftOffer>> =
        dao.observeAll().map { list ->
            list.mapNotNull { DraftOfferJsonCodec.decode(it.payloadJson) }
        }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    override suspend fun clear() {
        dao.clear()
    }
}
