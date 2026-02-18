package com.example.shoppingassistant.feature.pages.useroffers.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.shoppingassistant.domain.useroffers.UserOfferPublicationStatus as DomainPublicationStatus
import com.example.shoppingassistant.domain.useroffers.UserOfferStatus as DomainStatus
import com.example.shoppingassistant.domain.useroffers.UserOfferSummary
import com.example.shoppingassistant.domain.useroffers.UserOffersQuery
import com.example.shoppingassistant.domain.useroffers.UserOffersRepository
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferCardUi
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferPublicationStatus
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferStatus
import org.koin.java.KoinJavaComponent.get as koinGet

internal class UserOffersFeedTaskImpl(
    private val repository: UserOffersRepository,
) : UserOffersFeedTask {
    override suspend fun loadPage(query: UserOffersQuery): UserOffersFeedPage {
        val page = repository.list(query)
        return UserOffersFeedPage(
            items = page.items.map { it.toUi() },
            nextCursor = page.nextCursor,
            hasMore = page.hasMore,
        )
    }
}

@Composable
fun rememberUserOffersFeedTask(): UserOffersFeedTask = remember {
    UserOffersFeedTaskImpl(koinGet(UserOffersRepository::class.java))
}

private fun UserOfferSummary.toUi(): UserOfferCardUi = UserOfferCardUi(
    id = id,
    title = title,
    category = category,
    priceMajor = priceMajor,
    currency = currency,
    status = status.toUiStatus(),
    publicationStatus = publicationStatus.toUiPublicationStatus(),
    coverUrl = coverUrl,
    publishedAtMillis = publishedAtMillis,
    updatedAtMillis = updatedAtMillis,
    sourceUpdatedAtMillis = sourceUpdatedAtMillis,
    sourceName = sourceName,
    sourceIconUrl = sourceIconUrl,
    expiresAtMillis = expiresAtMillis,
    completedAtMillis = completedAtMillis,
    lastRenewedAtMillis = lastRenewedAtMillis,
    viewsCount = viewsCount,
    favoritesCount = favoritesCount,
    messagesCount = messagesCount,
    todayViews = todayViews,
    todayContacts = todayContacts,
    isPromoted = isPromoted,
)

private fun DomainStatus.toUiStatus(): UserOfferStatus = when (this) {
    DomainStatus.ACTIVE -> UserOfferStatus.ACTIVE
    DomainStatus.PAUSED -> UserOfferStatus.PAUSED
    DomainStatus.DRAFT -> UserOfferStatus.DRAFT
    DomainStatus.FINISHED -> UserOfferStatus.FINISHED
    DomainStatus.ARCHIVED -> UserOfferStatus.ARCHIVED
}

private fun DomainPublicationStatus.toUiPublicationStatus(): UserOfferPublicationStatus = when (this) {
    DomainPublicationStatus.PUBLISHED -> UserOfferPublicationStatus.PUBLISHED
    DomainPublicationStatus.ON_MODERATION -> UserOfferPublicationStatus.ON_MODERATION
    DomainPublicationStatus.ERROR -> UserOfferPublicationStatus.ERROR
}
