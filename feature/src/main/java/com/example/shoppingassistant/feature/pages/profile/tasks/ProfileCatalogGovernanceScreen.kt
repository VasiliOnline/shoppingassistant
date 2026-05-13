package com.example.shoppingassistant.feature.pages.profile.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shoppingassistant.core.data.catalog.CatalogGovernanceAdminReviewAction
import com.example.shoppingassistant.feature.pages.profile.CatalogGovernanceAdminUiState
import com.example.shoppingassistant.feature.pages.profile.CatalogGovernanceAdminViewModel
import com.example.shoppingassistant.feature.ui.layout.LayoutDefaults
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun ProfileCatalogGovernanceScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val viewModel: CatalogGovernanceAdminViewModel = koinViewModel()
    val ui by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    horizontal = LayoutDefaults.HorizontalPadding,
                    vertical = LayoutDefaults.SectionSpacing,
                ),
                verticalArrangement = Arrangement.spacedBy(LayoutDefaults.SectionSpacing),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(LayoutDefaults.CardInnerPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "TECH.PHONES governance / refresh",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Здесь можно запустить official refresh, увидеть review queue и publish log без ручных HTTP-вызовов.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = viewModel::triggerRefreshAll,
                            enabled = !ui.isBusy,
                        ) {
                            Text("Refresh sources")
                        }
                        OutlinedButton(
                            onClick = viewModel::triggerRebuild,
                            enabled = !ui.isBusy,
                        ) {
                            Text("Rebuild")
                        }
                        OutlinedButton(
                            onClick = { viewModel.reload() },
                            enabled = !ui.isBusy,
                        ) {
                            Text("Reload")
                        }
                    }
                    ui.statusMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    ui.errorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            ui.refreshStatus?.let { status ->
                GovernanceSectionCard(
                    title = "Scheduler loop",
                    subtitle = "Autonomous official refresh configuration for ${status.effectiveCategoryCode}",
                ) {
                    GovernanceLine(
                        title = if (status.enabled) "Enabled" else "Disabled",
                        subtitle = listOfNotNull(
                            "configured=${status.configuredCategoryCode}",
                            "interval=${formatDuration(status.pollIntervalMs)}",
                            "trigger=${status.trigger}",
                        ).joinToString(" · "),
                        meta = listOfNotNull(
                            "connectors=${status.connectorTypes.joinToString()}",
                            status.registryCodeAllowlist.takeIf { it.isNotEmpty() }
                                ?.joinToString(prefix = "allowlist=", separator = ","),
                        ).joinToString(" · ").ifBlank { "connectors=none" },
                    )
                    GovernanceLine(
                        title = "Matched sources: ${status.matchedSourceCount}/${status.availableSourceCount}",
                        subtitle = status.matchedRegistryCodes.joinToString().ifBlank { "No matching sources" },
                        meta = listOfNotNull(
                            status.skippedReason?.let { "skipped=$it" },
                            status.latestRunStatus?.let { "last=$it" },
                            status.latestRunPublishStatus?.let { "publish=$it" },
                            status.latestRunFinishedAt?.let(::formatTimestamp),
                        ).joinToString(" · "),
                    )
                }
            }

            ui.coverageSummary?.let { summary ->
                GovernanceSectionCard(
                    title = "Coverage control",
                    subtitle = "Какие ветки сейчас требуют покрытия или обновления",
                ) {
                    GovernanceLine(
                        title = "Всего веток: ${summary.totalCategories}",
                        subtitle = "coverage=${summary.categoriesNeedingCoverage} · refresh=${summary.categoriesNeedingRefresh}",
                        meta = "automated_refresh=${summary.automatedRefreshCategories}",
                    )
                    val attentionItems = summary.items.filter { it.attentionLevel != "OK" }
                    if (attentionItems.isEmpty()) {
                        GovernanceLine(
                            title = "Критичных веток нет",
                            subtitle = "Все видимые ветки сейчас без attention-сигналов.",
                            meta = "manual branches still rely on readiness data",
                        )
                    } else {
                        attentionItems.forEach { item ->
                            GovernanceLine(
                                title = "${item.categoryTitle} · ${item.attentionLevel}",
                                subtitle = "readiness=${item.readiness} · refresh=${item.refreshMode}",
                                meta = buildString {
                                    append(item.attentionReasons.joinToString(" · "))
                                    if (item.latestRefreshFinishedAt != null) {
                                        append(" · last=")
                                        append(formatTimestamp(item.latestRefreshFinishedAt))
                                    }
                                },
                            )
                        }
                    }
                }
            }

            GovernanceSectionCard(
                title = "Sources (${ui.sources.size})",
                subtitle = "Registered official/curated sources for ${ui.categoryCode}",
            ) {
                ui.sources.forEach { source ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            GovernanceLine(
                                title = source.displayName,
                                subtitle = "${source.connectorType} · ${source.registryCode}",
                                meta = listOfNotNull(
                                    source.tier,
                                    source.sourceUri,
                                    if (source.autoPublish) "auto-publish" else "manual publish",
                                    source.metadata["officialBrandCode"]?.let { "brand=$it" },
                                    source.metadata["endpointCount"]?.let { "endpoints=$it" },
                                ).joinToString(" · "),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.triggerRefreshSource(source.registryCode) },
                                    enabled = !ui.isBusy && source.enabled,
                                ) {
                                    Text("Refresh source")
                                }
                            }
                        }
                    }
                }
            }

            GovernanceSectionCard(
                title = "Refresh runs (${ui.runs.size})",
                subtitle = "Latest refresh executions",
            ) {
                ui.runs.forEach { run ->
                    GovernanceLine(
                        title = "${run.registryCode} · ${run.status}",
                        subtitle = "models=${run.modelsSynced}, values=${run.canonicalValuesSynced}, aliases=${run.aliasesSynced}",
                        meta = listOfNotNull(
                            "trigger=${run.trigger}",
                            run.publishStatus?.let { "publish=$it" },
                            run.finishedAt?.let(::formatTimestamp),
                        ).joinToString(" · "),
                    )
                }
            }

            GovernanceSectionCard(
                title = "Review queue (${ui.reviewQueue.size})",
                subtitle = "Approve / reject / promote suspicious candidates",
            ) {
                ui.reviewQueue.forEach { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "${item.attributeCode}: ${item.normalizedValue}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = listOfNotNull(
                                    "score=${"%.2f".format(Locale.US, item.totalScore)}",
                                    "recommendation=${item.recommendation}",
                                    item.existingTargetCode?.let { "target=$it" },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            item.primaryCandidateId?.let { candidateId ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.submitReviewAction(
                                                candidateId = candidateId,
                                                action = CatalogGovernanceAdminReviewAction.APPROVE,
                                            )
                                        },
                                        enabled = !ui.isBusy,
                                    ) {
                                        Text("Approve")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.submitReviewAction(
                                                candidateId = candidateId,
                                                action = CatalogGovernanceAdminReviewAction.REJECT,
                                            )
                                        },
                                        enabled = !ui.isBusy,
                                    ) {
                                        Text("Reject")
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.submitReviewAction(
                                                candidateId = candidateId,
                                                action = CatalogGovernanceAdminReviewAction.PROMOTE,
                                            )
                                        },
                                        enabled = !ui.isBusy,
                                    ) {
                                        Text("Promote")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            GovernanceSectionCard(
                title = "Publish log (${ui.publishEvents.size})",
                subtitle = "Latest serving/runtime publication events",
            ) {
                ui.publishEvents.forEach { event ->
                    GovernanceLine(
                        title = "${event.eventType} · ${event.status}",
                        subtitle = listOfNotNull(
                            event.artifactType,
                            event.entityRef,
                            event.details,
                        ).joinToString(" · "),
                        meta = formatTimestamp(event.createdAt),
                    )
                }
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Назад")
            }
        }

        if (ui.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun GovernanceSectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LayoutDefaults.CardInnerPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun GovernanceLine(
    title: String,
    subtitle: String,
    meta: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = meta,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatTimestamp(epochMillis: Long): String =
    SimpleDateFormat("dd.MM HH:mm", Locale.forLanguageTag("ru")).format(Date(epochMillis))

private fun formatDuration(durationMs: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
    return buildString {
        if (hours > 0) append("${hours}h")
        if (minutes > 0 || length == 0) {
            if (length > 0) append(" ")
            append("${minutes}m")
        }
    }
}
