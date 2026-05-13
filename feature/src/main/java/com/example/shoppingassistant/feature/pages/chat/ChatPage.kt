package com.example.shoppingassistant.feature.pages.chat

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.shoppingassistant.feature.metrics.FlowMetrics
import com.example.shoppingassistant.feature.ui.layout.LayoutDefaults
import com.example.shoppingassistant.feature.ui.layout.ScreenRoot
import com.example.shoppingassistant.feature.ui.state.SystemNoticeCard
import com.example.shoppingassistant.feature.ui.state.SystemNoticeTone
import kotlinx.coroutines.launch

private const val OFFER_OPEN_COOLDOWN_MS = 800L

private data class ResolvedOfferOpenTarget(
    val normalizedUrl: String,
    val urlType: OfferOpenUrlType,
)

private data class OfferOpenFallbackState(
    val reason: OfferOpenFailureReason,
    val resolvedUrl: String?,
    val copyUrl: String?,
)

/**
 * Экран чата: AppBar, мини-карточка оффера, список сообщений и поле ввода.
 * Поддерживает deeplink в карточке оффера с нормализацией ошибок и fallback-действиями.
 */
@Composable
fun ChatPage(
    props: ChatProps,
    applySafeInsets: Boolean = true,
    extraBottomPadding: Dp = LayoutDefaults.ContentBottomSpacing,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val demoMessages = remember {
        listOf(
            ChatMessage(id = "1", text = "Здравствуйте! Актуально?", fromMe = false),
            ChatMessage(id = "2", text = "Здравствуйте! Да, товар в наличии.", fromMe = true),
        )
    }
    var input by remember { mutableStateOf("") }
    val rawExternalUrl = remember(props.externalUrl) { props.externalUrl?.trim()?.takeIf { it.isNotEmpty() } }
    val rawRedirectUrl = remember(props.redirectUrl) { props.redirectUrl?.trim()?.takeIf { it.isNotEmpty() } }
    val rawDeeplinkUrl = remember(props.deeplinkUrl) { props.deeplinkUrl?.trim()?.takeIf { it.isNotEmpty() } }
    val rawPreferredUrl = remember(rawRedirectUrl, rawDeeplinkUrl, rawExternalUrl) {
        rawRedirectUrl ?: rawDeeplinkUrl ?: rawExternalUrl
    }
    val resolvedTarget = remember(rawRedirectUrl, rawDeeplinkUrl, rawExternalUrl) {
        resolvePreferredOpenTarget(
            rawRedirectUrl = rawRedirectUrl,
            rawDeeplinkUrl = rawDeeplinkUrl,
            rawExternalUrl = rawExternalUrl,
        )
    }
    val offerHost = remember(resolvedTarget) {
        extractExternalHost(resolvedTarget?.normalizedUrl) ?: "none"
    }
    val hasRawDeeplink = rawPreferredUrl != null
    var fallbackState by remember(props.offerId, rawPreferredUrl) {
        mutableStateOf<OfferOpenFallbackState?>(null)
    }
    var lastOpenTapAtMs by remember(props.offerId) { mutableStateOf(0L) }

    fun markOpenAttempt(
        source: OfferOpenFlowSource,
        channel: OfferOpenChannel,
        urlType: OfferOpenUrlType?,
    ) {
        FlowMetrics.markEvent(
            "offer_open_attempt",
            buildOpenMetricsDetails(
                props = props,
                source = source,
                hasRawDeeplink = hasRawDeeplink,
                host = offerHost,
                channel = channel,
                urlType = urlType,
            ),
        )
    }

    fun markOpenResult(
        source: OfferOpenFlowSource,
        result: String,
        channel: OfferOpenChannel,
        urlType: OfferOpenUrlType?,
        latencyMs: Long?,
        error: OfferOpenFailureReason? = null,
        fallbackReason: OfferOpenFailureReason? = null,
    ) {
        FlowMetrics.markEvent(
            "offer_open_result",
            buildOpenMetricsDetails(
                props = props,
                source = source,
                hasRawDeeplink = hasRawDeeplink,
                host = offerHost,
                channel = channel,
                urlType = urlType,
                result = result,
                error = error,
                fallbackReason = fallbackReason,
                latencyMs = latencyMs,
            ),
        )
    }

    fun markFallback(action: String, reason: OfferOpenFailureReason?) {
        val details = buildString {
            append("screen=chat")
            append(" action=${metricToken(action)}")
            append(" offer_id=${metricToken(props.offerId)}")
            append(" query_session_id=${metricToken(props.searchSessionId)}")
            append(" position=${props.offerPosition ?: -1}")
            append(" has_deeplink=$hasRawDeeplink")
            append(" host=${metricToken(offerHost)}")
            if (reason != null) append(" reason=${reason.code}")
        }
        FlowMetrics.markEvent("offer_open_fallback", details)
    }

    fun copyLink(url: String, reason: OfferOpenFailureReason?) {
        markFallback(action = "copy_link", reason = reason)
        scope.launch {
            val clipData = ClipData.newPlainText("offer_link", url)
            clipboard.setClipEntry(ClipEntry(clipData))
        }
        Toast.makeText(context, "Ссылка скопирована.", Toast.LENGTH_SHORT).show()
    }

    fun reportOpenIssue(reason: OfferOpenFailureReason?) {
        markFallback(action = "report", reason = reason)
        Toast.makeText(context, "Спасибо, мы проверим проблему открытия.", Toast.LENGTH_SHORT).show()
    }

    fun openOffer(source: OfferOpenFlowSource, forceBrowserChooser: Boolean = false) {
        val nowMs = SystemClock.elapsedRealtime()
        if (source == OfferOpenFlowSource.PRIMARY && nowMs - lastOpenTapAtMs < OFFER_OPEN_COOLDOWN_MS) {
            markFallback(action = "cooldown_hit", reason = OfferOpenFailureReason.BLOCKED_POLICY)
            Toast.makeText(context, "Подождите перед повторным открытием.", Toast.LENGTH_SHORT).show()
            return
        }
        lastOpenTapAtMs = nowMs

        val requestedChannel = if (forceBrowserChooser) {
            OfferOpenChannel.EXTERNAL_BROWSER
        } else {
            OfferOpenChannel.NATIVE_APP_LINK
        }
        val urlType = resolvedTarget?.urlType
        markOpenAttempt(source = source, channel = requestedChannel, urlType = urlType)

        val launchStartedAtMs = SystemClock.elapsedRealtime()
        var usedChannel = requestedChannel
        var failure: OfferOpenFailureReason? = null
        var fallbackReason: OfferOpenFailureReason? = null

        val target = resolvedTarget
        when {
            target == null -> {
                failure = if (rawPreferredUrl == null) {
                    OfferOpenFailureReason.URL_MISSING
                } else {
                    OfferOpenFailureReason.INVALID_URL
                }
            }
            requiresNetwork(target.normalizedUrl) && !isNetworkAvailable(context) -> {
                failure = OfferOpenFailureReason.OFFLINE
            }
            else -> {
                val primaryFailure = launchOfferIntent(
                    context = context,
                    normalizedUrl = target.normalizedUrl,
                    channel = requestedChannel,
                )
                if (primaryFailure == null) {
                    // success path: nothing to do
                } else if (requestedChannel == OfferOpenChannel.NATIVE_APP_LINK && isHttpUrl(target.normalizedUrl)) {
                    usedChannel = OfferOpenChannel.EXTERNAL_BROWSER
                    val browserFailure = launchOfferIntent(
                        context = context,
                        normalizedUrl = target.normalizedUrl,
                        channel = usedChannel,
                    )
                    if (browserFailure == null) {
                        fallbackReason = primaryFailure
                    } else {
                        failure = browserFailure
                        fallbackReason = primaryFailure
                    }
                } else {
                    failure = primaryFailure
                }
            }
        }

        val latencyMs = SystemClock.elapsedRealtime() - launchStartedAtMs
        if (failure == null) {
            fallbackState = null
            val resultType = if (fallbackReason != null) "fallback" else "success"
            markOpenResult(
                source = source,
                result = resultType,
                channel = usedChannel,
                urlType = urlType,
                latencyMs = latencyMs,
                fallbackReason = fallbackReason,
            )
        } else {
            fallbackState = OfferOpenFallbackState(
                reason = failure,
                resolvedUrl = resolvedTarget?.normalizedUrl,
                copyUrl = resolvedTarget?.normalizedUrl ?: rawPreferredUrl,
            )
            val canFallback = !fallbackState?.copyUrl.isNullOrBlank()
            val resultType = if (canFallback) "fallback" else "error"
            markOpenResult(
                source = source,
                result = resultType,
                channel = usedChannel,
                urlType = urlType,
                latencyMs = latencyMs,
                error = if (resultType == "error") failure else null,
                fallbackReason = if (resultType == "fallback") (fallbackReason ?: failure) else null,
            )
            Toast.makeText(context, openErrorText(failure), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(props.offerId, props.searchSessionId, props.offerPosition, hasRawDeeplink, offerHost) {
        FlowMetrics.markEvent(
            "offer_page_shown",
            buildString {
                append("screen=chat")
                append(" offer_id=${metricToken(props.offerId)}")
                append(" query_session_id=${metricToken(props.searchSessionId)}")
                append(" position=${props.offerPosition ?: -1}")
                append(" has_deeplink=$hasRawDeeplink")
                append(" host=${metricToken(offerHost)}")
            },
        )
    }

    ScreenRoot(
        modifier = Modifier.fillMaxSize(),
        applySafeInsets = applySafeInsets,
        extraBottomPadding = extraBottomPadding,
    ) { contentPadding ->
        val bottomInset = (contentPadding.calculateBottomPadding() - extraBottomPadding).coerceAtLeast(0.dp)
        Scaffold(
            topBar = {
                ChatTopBar(
                    sellerName = props.sellerName,
                    sellerStatus = props.sellerStatus,
                    onBack = props.onBack,
                )
            },
            bottomBar = {
                Box(modifier = Modifier.padding(bottom = bottomInset)) {
                    ChatInputBar(
                        value = input,
                        onValueChange = { input = it },
                        onSend = {
                            input = ""
                        },
                    )
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(bottom = contentPadding.calculateBottomPadding())
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(LayoutDefaults.SectionSpacing),
            ) {
                MiniOfferCard(
                    title = props.offerTitle,
                    price = props.offerPrice,
                    sourceName = props.sourceName ?: extractExternalHost(resolvedTarget?.normalizedUrl),
                    onOpenOffer = { openOffer(source = OfferOpenFlowSource.PRIMARY) },
                )

                fallbackState?.let { state ->
                    OfferOpenFallbackCard(
                        reason = state.reason,
                        hasUrl = !state.copyUrl.isNullOrBlank(),
                        onRetry = {
                            markFallback(action = "retry", reason = state.reason)
                            openOffer(source = OfferOpenFlowSource.RETRY)
                        },
                        onOpenBrowser = if (state.resolvedUrl.isNullOrBlank()) {
                            null
                        } else {
                            {
                                markFallback(action = "open_browser", reason = state.reason)
                                openOffer(
                                    source = OfferOpenFlowSource.BROWSER,
                                    forceBrowserChooser = true,
                                )
                            }
                        },
                        onCopyLink = if (state.copyUrl.isNullOrBlank()) {
                            null
                        } else {
                            {
                                copyLink(
                                    url = state.copyUrl,
                                    reason = state.reason,
                                )
                            }
                        },
                        onReport = { reportOpenIssue(state.reason) },
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(demoMessages) { message ->
                        ChatBubble(message)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    sellerName: String,
    sellerStatus: String?,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = sellerName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                sellerStatus?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Назад",
                )
            }
        },
        actions = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Chat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}

@SuppressLint("DefaultLocale")
@Composable
private fun MiniOfferCard(
    title: String,
    price: Double?,
    sourceName: String?,
    onOpenOffer: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = "https://picsum.photos/seed/${title.hashCode()}/360/240",
            contentDescription = "Фото товара",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(MaterialTheme.shapes.small),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = price?.let { String.format("%,.0f ₽", it) } ?: "Цена уточняется",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
            sourceName?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "Источник: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(onClick = onOpenOffer) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Открыть оффер")
            }
        }
    }
}

@Composable
private fun OfferOpenFallbackCard(
    reason: OfferOpenFailureReason,
    hasUrl: Boolean,
    onRetry: () -> Unit,
    onOpenBrowser: (() -> Unit)?,
    onCopyLink: (() -> Unit)?,
    onReport: () -> Unit,
) {
    SystemNoticeCard(
        title = "Не удалось открыть оффер",
        body = openErrorText(reason),
        tone = SystemNoticeTone.Error,
        modifier = Modifier.fillMaxWidth(),
        bottomContent = {
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Повторить")
            }
            if (hasUrl && onOpenBrowser != null) {
                OutlinedButton(
                    onClick = onOpenBrowser,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Открыть в браузере")
                }
            }
            if (hasUrl && onCopyLink != null) {
                OutlinedButton(
                    onClick = onCopyLink,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Скопировать ссылку")
                }
            }
            TextButton(
                onClick = onReport,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Сообщить о проблеме")
            }
        },
    )
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val bg = if (message.fromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (message.fromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(bg)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = fg,
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Напишите продавцу…") },
            singleLine = true,
        )
        TextButton(
            onClick = onSend,
            enabled = value.isNotBlank(),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Send,
                contentDescription = "Отправить",
            )
        }
    }
}

private fun launchOfferIntent(
    context: Context,
    normalizedUrl: String,
    channel: OfferOpenChannel,
): OfferOpenFailureReason? {
    val uri = runCatching { Uri.parse(normalizedUrl) }.getOrNull() ?: return OfferOpenFailureReason.INVALID_URL
    val scheme = uri.scheme?.lowercase()
    if ((scheme != "http" && scheme != "https") || uri.host.isNullOrBlank()) {
        return OfferOpenFailureReason.INVALID_URL
    }

    val baseIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val launchIntent = if (channel == OfferOpenChannel.EXTERNAL_BROWSER) {
        Intent.createChooser(baseIntent, "Открыть оффер").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    } else {
        baseIntent
    }

    val canOpen = launchIntent.resolveActivity(context.packageManager) != null
    if (!canOpen) return OfferOpenFailureReason.NO_HANDLER

    return try {
        context.startActivity(launchIntent)
        null
    } catch (_: ActivityNotFoundException) {
        OfferOpenFailureReason.NO_HANDLER
    } catch (_: SecurityException) {
        OfferOpenFailureReason.BLOCKED_POLICY
    } catch (_: Throwable) {
        OfferOpenFailureReason.UNKNOWN
    }
}

private fun openErrorText(reason: OfferOpenFailureReason): String = when (reason) {
    OfferOpenFailureReason.URL_MISSING -> "Для этого оффера нет внешней ссылки."
    OfferOpenFailureReason.INVALID_URL -> "Ссылка оффера повреждена или имеет неверный формат."
    OfferOpenFailureReason.NO_HANDLER -> "На устройстве нет приложения, которое может открыть эту ссылку."
    OfferOpenFailureReason.BLOCKED_POLICY -> "Открытие ссылки заблокировано политикой безопасности."
    OfferOpenFailureReason.OFFLINE -> "Нет сети. Проверьте подключение и попробуйте снова."
    OfferOpenFailureReason.TIMEOUT -> "Не удалось открыть оффер вовремя. Попробуйте снова."
    OfferOpenFailureReason.UNKNOWN -> "Не удалось открыть оффер из-за системной ошибки."
}

private fun resolvePreferredOpenTarget(
    rawRedirectUrl: String?,
    rawDeeplinkUrl: String?,
    rawExternalUrl: String?,
): ResolvedOfferOpenTarget? {
    val candidates = listOf(
        rawRedirectUrl to OfferOpenUrlType.REDIRECT,
        rawDeeplinkUrl to OfferOpenUrlType.DEEPLINK,
        rawExternalUrl to OfferOpenUrlType.DEEPLINK,
    )
    candidates.forEach { (rawUrl, urlType) ->
        val normalized = normalizeExternalUrl(rawUrl) ?: return@forEach
        return ResolvedOfferOpenTarget(
            normalizedUrl = normalized,
            urlType = urlType,
        )
    }
    return null
}

private fun buildOpenMetricsDetails(
    props: ChatProps,
    source: OfferOpenFlowSource,
    hasRawDeeplink: Boolean,
    host: String,
    channel: OfferOpenChannel?,
    urlType: OfferOpenUrlType?,
    result: String? = null,
    fallbackReason: OfferOpenFailureReason? = null,
    error: OfferOpenFailureReason? = null,
    latencyMs: Long? = null,
): String = buildString {
    append("screen=chat")
    append(" source=${source.code}")
    append(" offer_id=${metricToken(props.offerId)}")
    append(" query_session_id=${metricToken(props.searchSessionId)}")
    append(" position=${props.offerPosition ?: -1}")
    append(" has_deeplink=$hasRawDeeplink")
    append(" open_channel=${channel?.code ?: "none"}")
    append(" url_type=${urlType?.code ?: "none"}")
    append(" host=${metricToken(host)}")
    append(" source_name=${metricToken(props.sourceName)}")
    if (!result.isNullOrBlank()) append(" result=${metricToken(result)}")
    if (fallbackReason != null) append(" fallback_reason=${fallbackReason.code}")
    if (error != null) append(" error_code=${error.code}")
    if (latencyMs != null) append(" latency_ms=${latencyMs.coerceAtLeast(0L)}")
}

private fun metricToken(value: String?): String {
    val cleaned = value?.trim()?.replace("\\s+".toRegex(), "_").orEmpty()
    return if (cleaned.isEmpty()) "none" else cleaned
}

private fun isHttpUrl(normalizedUrl: String): Boolean {
    val uri = runCatching { Uri.parse(normalizedUrl) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    return scheme == "http" || scheme == "https"
}

private fun requiresNetwork(normalizedUrl: String): Boolean = isHttpUrl(normalizedUrl)

private fun isNetworkAvailable(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return true
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
