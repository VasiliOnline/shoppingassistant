package com.example.shoppingassistant.feature.pages.offers

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.shoppingassistant.core.usecase.CreateOfferPriceAlertUseCase
import com.example.shoppingassistant.core.usecase.GetOfferDetailsUseCase
import com.example.shoppingassistant.core.usecase.OfferAlertResult
import com.example.shoppingassistant.core.usecase.OfferDetailsScreenData
import com.example.shoppingassistant.domain.model.OfferDetailState
import com.example.shoppingassistant.domain.model.OfferDetailsPage
import com.example.shoppingassistant.domain.model.OfferFull
import com.example.shoppingassistant.domain.model.OfferRelatedOffer
import com.example.shoppingassistant.feature.metrics.FlowMetrics
import com.example.shoppingassistant.feature.navigation.AppRoutes
import com.example.shoppingassistant.feature.pages.chat.normalizeExternalUrl
import com.example.shoppingassistant.feature.ui.cards.formatLocationText
import com.example.shoppingassistant.feature.ui.cards.formatPriceText
import com.example.shoppingassistant.feature.ui.cards.formatUpdatedAtText
import com.example.shoppingassistant.feature.ui.layout.AppTopBar
import com.example.shoppingassistant.feature.ui.layout.LayoutDefaults
import com.example.shoppingassistant.feature.ui.layout.ScreenRoot
import com.example.shoppingassistant.feature.ui.state.StateHost
import com.example.shoppingassistant.feature.ui.state.model.EmptyReason
import com.example.shoppingassistant.feature.ui.state.model.LoadingPhase
import com.example.shoppingassistant.feature.ui.state.model.ScreenState
import com.example.shoppingassistant.feature.ui.state.model.StateAction
import com.example.shoppingassistant.feature.ui.state.model.StateActionType
import com.example.shoppingassistant.feature.ui.images.OfferImageTask
import com.example.shoppingassistant.feature.ui.images.rememberOfferImageTask
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get as koinGet
import java.util.Locale

private const val DEFAULT_PRICE_ALERT_DROP_PERCENT = 10
private const val SECTION_ITEM_OFFSET = 2
private val HERO_HEIGHT: Dp = 240.dp

private data class OfferSection(
    val key: String,
    val title: String,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OfferPage(
    offerId: String,
    querySessionId: String? = null,
    position: Int? = null,
    navController: NavHostController? = null,
    onBack: (() -> Unit)? = null,
    onEditOffer: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    applySafeInsets: Boolean = true,
    extraBottomPadding: Dp = LayoutDefaults.ContentBottomSpacing,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val getOfferDetails: GetOfferDetailsUseCase = remember {
        koinGet(GetOfferDetailsUseCase::class.java)
    }
    val createOfferPriceAlert: CreateOfferPriceAlertUseCase = remember {
        koinGet(CreateOfferPriceAlertUseCase::class.java)
    }
    val imageTask = rememberOfferImageTask()
    val listState = rememberLazyListState()

    var screenData by remember(offerId) { mutableStateOf<OfferDetailsScreenData?>(null) }
    var isLoading by remember(offerId) { mutableStateOf(true) }
    var errorMessage by remember(offerId) { mutableStateOf<String?>(null) }

    suspend fun loadOffer() {
        isLoading = true
        errorMessage = null
        screenData = null
        runCatching { getOfferDetails(offerId) }
            .onSuccess { result ->
                screenData = result
                if (result != null) {
                    FlowMetrics.markEvent(
                        "offer_page_view",
                        buildString {
                            append("offer_id=${result.page.offer.id}")
                            append(" state=${result.page.detailState.name.lowercase(Locale.ROOT)}")
                            append(" owner_view=${result.viewerOwnsOffer}")
                            append(" query_session=${querySessionId ?: "none"}")
                            append(" position=${position ?: -1}")
                        },
                    )
                }
            }
            .onFailure {
                errorMessage = it.message ?: "Не удалось загрузить оффер"
            }
        isLoading = false
    }

    LaunchedEffect(offerId) {
        loadOffer()
    }

    val page = screenData?.page
    val viewerOwnsOffer = screenData?.viewerOwnsOffer == true
    val sections = remember(page) { page?.let(::buildSections).orEmpty() }
    val selectedSectionIndex by remember(sections, listState) {
        derivedStateOf {
            if (sections.isEmpty()) {
                0
            } else {
                (listState.firstVisibleItemIndex - SECTION_ITEM_OFFSET)
                    .coerceIn(0, sections.lastIndex)
            }
        }
    }

    fun navigateBack() {
        when {
            onBack != null -> onBack()
            navController != null -> navController.popBackStack()
        }
    }

    fun openExternalSource(rawUrl: String?) {
        val normalized = normalizeExternalUrl(rawUrl)
        if (normalized == null) {
            Toast.makeText(context, "Ссылка на источник недоступна", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val canOpen = intent.resolveActivity(context.packageManager) != null
            if (canOpen) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Не найдено приложение для открытия ссылки", Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            Toast.makeText(context, "Не удалось открыть источник", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareOffer(rawUrl: String?) {
        val shareText = normalizeExternalUrl(rawUrl) ?: page?.offer?.product?.title ?: return
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(intent, "Поделиться"))
        }.onFailure {
            Toast.makeText(context, "Не удалось поделиться оффером", Toast.LENGTH_SHORT).show()
        }
    }

    fun openChat() {
        val currentPage = page ?: return
        val offer = currentPage.offer
        val route = AppRoutes.chat(
            offerId = offer.id,
            sellerName = offer.seller.name,
            offerTitle = offer.product.title,
            price = offer.price.toMajor().toString(),
            status = offer.status.name.lowercase(Locale.ROOT),
            externalUrl = currentPage.provenance.sourceUrl,
            redirectUrl = currentPage.provenance.canonicalUrl,
            deeplinkUrl = currentPage.provenance.sourceUrl ?: currentPage.provenance.canonicalUrl,
            sourceName = currentPage.provenance.sourceName,
            querySessionId = querySessionId,
            position = position,
        )
        navController?.navigate(route)
    }

    fun createPriceAlert() {
        val currentPage = page ?: return
        scope.launch {
            when (val result = createOfferPriceAlert(currentPage.offer.id, DEFAULT_PRICE_ALERT_DROP_PERCENT)) {
                OfferAlertResult.Success ->
                    Toast.makeText(context, "Отслеживание цены включено", Toast.LENGTH_SHORT).show()
                OfferAlertResult.Unauthorized ->
                    Toast.makeText(context, "Войдите, чтобы включить отслеживание цены", Toast.LENGTH_SHORT).show()
                is OfferAlertResult.Error ->
                    Toast.makeText(context, result.reason, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val retryLoad = { scope.launch { loadOffer() }; Unit }

    val screenState = when {
        isLoading -> ScreenState.Loading(LoadingPhase.INITIAL)
        errorMessage != null -> ScreenState.Error(
            title = "Не удалось открыть оффер",
            message = errorMessage ?: "Ошибка загрузки",
            primaryAction = StateAction(
                label = "Повторить",
                onAction = retryLoad,
                type = StateActionType.RETRY,
            ),
        )
        page == null -> ScreenState.Empty(
            reason = EmptyReason.NO_OFFERS,
            title = "Оффер не найден",
            message = "Запрошенное объявление больше недоступно.",
            primaryAction = StateAction(
                label = "Назад",
                onAction = ::navigateBack,
            ),
        )
        else -> ScreenState.Content()
    }

    ScreenRoot(
        modifier = modifier,
        applySafeInsets = applySafeInsets,
        extraBottomPadding = extraBottomPadding,
        topBar = {
            AppTopBar(
                title = page?.offer?.product?.title ?: "Оффер",
                onBack = ::navigateBack,
            )
        },
    ) { contentPadding ->
        StateHost(
            state = screenState,
            contentPadding = contentPadding,
            screenName = "offer_page",
            modifier = Modifier.fillMaxWidth(),
        ) { innerPadding ->
            val currentPage = page ?: return@StateHost
            LazyColumn(
                state = listState,
                contentPadding = innerPadding,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item("summary") {
                    OfferSummaryBlock(
                        imageTask = imageTask,
                        page = currentPage,
                        viewerOwnsOffer = viewerOwnsOffer,
                        onEditOffer = { onEditOffer?.invoke(currentPage.offer.id) },
                        onOpenChat = ::openChat,
                        onOpenSource = {
                            openExternalSource(currentPage.provenance.sourceUrl ?: currentPage.provenance.canonicalUrl)
                        },
                        onTrackPrice = ::createPriceAlert,
                        onShare = {
                            shareOffer(currentPage.provenance.sourceUrl ?: currentPage.provenance.canonicalUrl)
                        },
                        onReport = {
                            Toast.makeText(context, "Жалоба отправлена на проверку", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                if (sections.isNotEmpty()) {
                    stickyHeader("offer-sections") {
                        OfferSectionNavigation(
                            sections = sections,
                            selectedIndex = selectedSectionIndex,
                            onSelect = { index ->
                                scope.launch {
                                    listState.animateScrollToItem(index + SECTION_ITEM_OFFSET)
                                }
                            },
                        )
                    }
                }
                items(sections, key = { it.key }) { section ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = LayoutDefaults.HorizontalPadding),
                    ) {
                        when (section.key) {
                            "seller" -> OfferSellerSection(
                                offer = currentPage.offer,
                                onOpenProfile = currentPage.offer.seller.id
                                    .toLongOrNull()
                                    ?.takeIf { it > 0L }
                                    ?.let { sellerId ->
                                        { navController?.navigate(AppRoutes.profile(sellerId)) }
                                    },
                            )
                            "attributes" -> OfferAttributesSection(currentPage.offer)
                            "description" -> OfferDescriptionSection(currentPage.offer)
                            "source" -> OfferSourceSection(
                                page = currentPage,
                                viewerOwnsOffer = viewerOwnsOffer,
                                onOpenSource = {
                                    openExternalSource(currentPage.provenance.sourceUrl ?: currentPage.provenance.canonicalUrl)
                                },
                            )
                            "related" -> OfferRelatedSection(
                                imageTask = imageTask,
                                relatedOffers = currentPage.relatedOffers,
                                onOpenRelated = { relatedOfferId ->
                                    navController?.navigate(
                                        AppRoutes.offer(
                                            offerId = relatedOfferId,
                                            querySessionId = querySessionId,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildSections(page: OfferDetailsPage): List<OfferSection> = buildList {
    add(OfferSection(key = "seller", title = "Продавец"))
    if (resolveAttributeRows(page.offer).isNotEmpty()) {
        add(OfferSection(key = "attributes", title = "Характеристики"))
    }
    if (resolveDescription(page.offer) != null) {
        add(OfferSection(key = "description", title = "Описание"))
    }
    add(OfferSection(key = "source", title = "Источник"))
    if (page.relatedOffers.isNotEmpty()) {
        add(OfferSection(key = "related", title = "Похожие"))
    }
}

@Composable
private fun OfferSummaryBlock(
    imageTask: OfferImageTask,
    page: OfferDetailsPage,
    viewerOwnsOffer: Boolean,
    onEditOffer: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenSource: () -> Unit,
    onTrackPrice: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LayoutDefaults.HorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OfferHeroGallery(
            imageTask = imageTask,
            title = page.offer.product.title,
            imageUrls = (page.offer.imageUrls + page.offer.product.imageUrls).distinct(),
        )
        OfferDecisionSummaryCard(
            page = page,
            viewerOwnsOffer = viewerOwnsOffer,
        )
        OfferPrimaryActions(
            page = page,
            viewerOwnsOffer = viewerOwnsOffer,
            onEditOffer = onEditOffer,
            onOpenChat = onOpenChat,
            onOpenSource = onOpenSource,
            onTrackPrice = onTrackPrice,
            onShare = onShare,
            onReport = onReport,
        )
    }
}

@Composable
private fun OfferHeroGallery(
    imageTask: OfferImageTask,
    title: String,
    imageUrls: List<String>,
) {
    if (imageUrls.isEmpty()) {
        imageTask.Render(
            imageUrl = null,
            placeholderKey = title,
            placeholderLabel = title,
            contentDescription = "Изображение оффера",
            modifier = Modifier
                .fillMaxWidth()
                .height(HERO_HEIGHT),
            shape = RoundedCornerShape(28.dp),
        )
        return
    }

    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(imageUrls) { imageUrl ->
            imageTask.Render(
                imageUrl = imageUrl,
                placeholderKey = "$title-$imageUrl",
                placeholderLabel = title,
                contentDescription = title,
                modifier = Modifier
                    .width(300.dp)
                    .height(HERO_HEIGHT),
                shape = RoundedCornerShape(28.dp),
            )
        }
    }
}

@Composable
private fun OfferDecisionSummaryCard(
    page: OfferDetailsPage,
    viewerOwnsOffer: Boolean,
) {
    val offer = page.offer
    val location = formatLocationText(offer.seller.city, offer.seller.countryCode)
    val updatedAtText = formatUpdatedAtText(page.provenance.lastSyncedAtMillis ?: offer.updatedAt)
    Surface(
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatePill(
                    label = detailStateLabel(
                        detailState = page.detailState,
                        viewerOwnsOffer = viewerOwnsOffer,
                    ),
                )
                page.provenance.sourceName
                    ?.takeIf { it.isNotBlank() }
                    ?.let { sourceName -> StatePill(label = sourceName) }
            }

            Text(
                text = offer.product.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = formatPriceText(offer.price.toMajor(), offer.currency),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            val facts = buildList<String> {
                location?.let(::add)
                updatedAtText?.let { add("Обновлено $it") }
                offer.seller.rating?.let { add("Рейтинг ${String.format(Locale.getDefault(), "%.1f", it.value)}") }
            }
            facts.forEach { fact ->
                Text(
                    text = fact,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OfferPrimaryActions(
    page: OfferDetailsPage,
    viewerOwnsOffer: Boolean,
    onEditOffer: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenSource: () -> Unit,
    onTrackPrice: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (viewerOwnsOffer) {
                androidx.compose.material3.Button(
                    onClick = onEditOffer,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Редактировать")
                }
            } else if (page.capabilities.canChat) {
                androidx.compose.material3.Button(
                    onClick = onOpenChat,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Написать")
                }
            }

            if (page.capabilities.canOpenSource) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onOpenSource,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (viewerOwnsOffer) "Открыть источник" else "Перейти к источнику")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!viewerOwnsOffer && page.capabilities.canTrackPrice) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onTrackPrice,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Следить за ценой")
                }
            }
            androidx.compose.material3.TextButton(
                onClick = onShare,
                modifier = Modifier.weight(1f),
            ) {
                Text("Поделиться")
            }
            androidx.compose.material3.TextButton(
                onClick = onReport,
                modifier = Modifier.weight(1f),
            ) {
                Text("Пожаловаться")
            }
        }
    }
}

@Composable
private fun OfferSectionNavigation(
    sections: List<OfferSection>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = LayoutDefaults.HorizontalPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(sections.indices.toList()) { index ->
                val selected = index == selectedIndex
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.clickable { onSelect(index) },
                ) {
                    Text(
                        text = sections[index].title,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OfferSellerSection(
    offer: OfferFull,
    onOpenProfile: (() -> Unit)? = null,
) {
    SectionCard(title = "Продавец") {
        val seller = offer.seller
        Text(
            text = seller.name,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        seller.rating?.let { rating ->
            Text(
                text = "Рейтинг ${String.format(Locale.getDefault(), "%.1f", rating.value)} • ${rating.count} отзывов",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        formatLocationText(seller.city, seller.countryCode)?.let { location ->
            DetailRow(label = "Локация", value = location)
        }
        if (seller.preferences.badges.isNotEmpty()) {
            DetailRow(
                label = "Бейджи",
                value = seller.preferences.badges.joinToString(", ") { badge ->
                    badge.name.replace('_', ' ').lowercase(Locale.ROOT)
                },
            )
        }
        if (seller.preferences.shippingCountries.isNotEmpty()) {
            DetailRow(
                label = "Доставка",
                value = seller.preferences.shippingCountries.joinToString(", "),
            )
        }
        if (onOpenProfile != null) {
            androidx.compose.material3.OutlinedButton(onClick = onOpenProfile) {
                Text("Открыть профиль продавца")
            }
        }
    }
}

@Composable
private fun OfferAttributesSection(offer: OfferFull) {
    val attributes = resolveAttributeRows(offer)
    SectionCard(title = "Характеристики") {
        attributes.forEachIndexed { index, row ->
            if (index > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            }
            DetailRow(label = row.first, value = row.second)
        }
    }
}

@Composable
private fun OfferDescriptionSection(offer: OfferFull) {
    val description = resolveDescription(offer) ?: return
    SectionCard(title = "Описание") {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun OfferSourceSection(
    page: OfferDetailsPage,
    viewerOwnsOffer: Boolean,
    onOpenSource: () -> Unit,
) {
    SectionCard(title = "Источник") {
        Text(
            text = detailStateDescription(page.detailState, viewerOwnsOffer),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        page.provenance.sourceName?.let { DetailRow(label = "Источник", value = it) }
        page.provenance.sourceDomain?.let { DetailRow(label = "Домен", value = it) }
        page.provenance.sourceType?.let { DetailRow(label = "Тип", value = it) }
        formatUpdatedAtText(page.provenance.lastSyncedAtMillis)
            ?.let { DetailRow(label = "Последняя синхронизация", value = it) }

        if (!page.provenance.sourceUrl.isNullOrBlank() || !page.provenance.canonicalUrl.isNullOrBlank()) {
            androidx.compose.material3.OutlinedButton(onClick = onOpenSource) {
                Text("Открыть первоисточник")
            }
        }
    }
}

@Composable
private fun OfferRelatedSection(
    imageTask: OfferImageTask,
    relatedOffers: List<OfferRelatedOffer>,
    onOpenRelated: (String) -> Unit,
) {
    SectionCard(title = "Похожие предложения") {
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(relatedOffers) { related ->
                OfferRelatedCard(
                    imageTask = imageTask,
                    related = related,
                    onClick = { onOpenRelated(related.id) },
                )
            }
        }
    }
}

@Composable
private fun OfferRelatedCard(
    imageTask: OfferImageTask,
    related: OfferRelatedOffer,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            imageTask.Render(
                imageUrl = related.imageUrl,
                placeholderKey = related.id,
                placeholderLabel = related.title,
                contentDescription = related.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp),
                shape = RoundedCornerShape(16.dp),
            )
            Text(
                text = related.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = formatPriceText(related.price.toMajor(), related.currency),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            related.sourceName?.let { sourceName ->
                Text(
                    text = sourceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            content()
        }
    }
}

@Composable
private fun StatePill(label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun detailStateLabel(
    detailState: OfferDetailState,
    viewerOwnsOffer: Boolean,
): String = when {
    viewerOwnsOffer -> "Ваш оффер"
    detailState == OfferDetailState.NATIVE_LOCAL -> "Локальное размещение"
    detailState == OfferDetailState.EXTERNAL_CLAIMED -> "Источник подтверждён"
    else -> "Внешний источник"
}

private fun detailStateDescription(
    detailState: OfferDetailState,
    viewerOwnsOffer: Boolean,
): String = when {
    viewerOwnsOffer -> "Вы просматриваете своё объявление. Показываем owner-view без buyer CTA."
    detailState == OfferDetailState.NATIVE_LOCAL ->
        "Объявление размещено внутри Goody Goods и использует локальный источник истины."
    detailState == OfferDetailState.EXTERNAL_CLAIMED ->
        "Оффер связан с внешним источником, но подтверждён владельцем внутри Goody Goods. Можно и написать продавцу, и открыть первоисточник."
    else ->
        "Это нормализованная копия внешнего объявления. Внутренний чат не обещаем: переходите к первоисточнику."
}

private fun resolveDescription(offer: OfferFull): String? =
    offer.description?.trim()?.takeIf { it.isNotEmpty() }
        ?: offer.product.description?.trim()?.takeIf { it.isNotEmpty() }

private fun resolveAttributeRows(offer: OfferFull): List<Pair<String, String>> {
    val ignoredKeys = setOf(
        "external_url",
        "externalUrl",
        "source_url",
        "sourceUrl",
        "redirect_url",
        "redirectUrl",
        "deeplink_url",
        "deeplinkUrl",
        "track_url",
        "trackUrl",
        "distance_km",
        "distanceKm",
        "distance",
    )
    return (offer.product.specs + offer.attributes)
        .filterKeys { key -> ignoredKeys.none { ignored -> ignored.equals(key, ignoreCase = true) } }
        .entries
        .sortedBy { it.key.lowercase(Locale.ROOT) }
        .map { entry ->
            entry.key.toHumanLabel() to entry.value.asRawString()
        }
}

private fun String.toHumanLabel(): String =
    replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .replaceFirstChar { symbol ->
            if (symbol.isLowerCase()) {
                symbol.titlecase(Locale.getDefault())
            } else {
                symbol.toString()
            }
        }
