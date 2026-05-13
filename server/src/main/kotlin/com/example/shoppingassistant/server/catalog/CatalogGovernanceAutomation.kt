package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogGovernanceLoader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.datetime.Clock

internal const val GOVERNANCE_TRIGGER_DAILY_SNAPSHOT_CAPTURED = "DAILY_SNAPSHOT_CAPTURED"
internal const val GOVERNANCE_TRIGGER_WEEKLY_SUMMARY_CAPTURED = "WEEKLY_SUMMARY_CAPTURED"

@Serializable
internal data class CatalogGovernanceInventorySummary(
    val totalCategories: Int,
    val readyCategories: Int,
    val betaCategories: Int,
    val internalCategories: Int,
    val categoriesWithBlockingIssues: List<String>,
)

@Serializable
internal data class CatalogGovernanceHookEvent(
    val trigger: String,
    val emittedAt: String,
    val snapshotDate: String,
    val dataVersion: String,
    val schemaVersion: String,
    val inventory: CatalogGovernanceInventorySummary,
    val report: CatalogGovernanceReportResponse? = null,
)

internal interface CatalogGovernanceHookExecutor {
    suspend fun dispatch(event: CatalogGovernanceHookEvent)
}

internal class CatalogGovernanceHookExecutorImpl(
    private val deliveryRepository: CatalogGovernanceHookDeliveryRepository,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val targetResolver: (String) -> String? = { key -> System.getenv(key) },
) : CatalogGovernanceHookExecutor {
    private val json = Json { prettyPrint = false; encodeDefaults = true }

    override suspend fun dispatch(event: CatalogGovernanceHookEvent) {
        val hooks = CatalogGovernanceLoader.loadSnapshot().readinessPolicy.governanceHooks
            .filter { hook -> hook.trigger.equals(event.trigger, ignoreCase = true) }
        if (hooks.isEmpty()) return

        val payload = json.encodeToString(event)
        hooks.forEach { hook ->
            deliveryRepository.recordDelivery(
                deliverHook(
                    hookCode = hook.code,
                    trigger = hook.trigger,
                    transport = hook.transport,
                    targetEnvVar = hook.targetEnvVar,
                    payload = payload,
                ),
            )
        }
    }

    private suspend fun deliverHook(
        hookCode: String,
        trigger: String,
        transport: String,
        targetEnvVar: String,
        payload: String,
    ): CatalogGovernanceHookDeliveryResponse {
        val attemptedAt = Clock.System.now().toString()
        val target = targetResolver(targetEnvVar)?.trim()?.takeIf { value -> value.isNotEmpty() }
        if (target == null) {
            return CatalogGovernanceHookDeliveryResponse(
                hookCode = hookCode,
                trigger = trigger,
                transport = transport,
                target = null,
                status = "SKIPPED_UNCONFIGURED",
                attemptedAt = attemptedAt,
                details = "Environment variable '$targetEnvVar' is not configured.",
            )
        }

        return when (transport.trim().uppercase()) {
            "JSONL_FILE" -> appendJsonLine(
                hookCode = hookCode,
                trigger = trigger,
                transport = transport,
                target = target,
                payload = payload,
                attemptedAt = attemptedAt,
            )

            "HTTP_JSON" -> postJson(
                hookCode = hookCode,
                trigger = trigger,
                transport = transport,
                target = target,
                payload = payload,
                attemptedAt = attemptedAt,
            )

            else -> CatalogGovernanceHookDeliveryResponse(
                hookCode = hookCode,
                trigger = trigger,
                transport = transport,
                target = target,
                status = "FAILED_UNSUPPORTED_TRANSPORT",
                attemptedAt = attemptedAt,
                details = "Unsupported governance hook transport '$transport'.",
            )
        }
    }

    private suspend fun appendJsonLine(
        hookCode: String,
        trigger: String,
        transport: String,
        target: String,
        payload: String,
        attemptedAt: String,
    ): CatalogGovernanceHookDeliveryResponse =
        withContext(Dispatchers.IO) {
            runCatching {
                val path = Path.of(target)
                path.parent?.let { parent -> Files.createDirectories(parent) }
                Files.writeString(
                    path,
                    payload + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND,
                )
            }.fold(
                onSuccess = {
                    CatalogGovernanceHookDeliveryResponse(
                        hookCode = hookCode,
                        trigger = trigger,
                        transport = transport,
                        target = target,
                        status = "DELIVERED",
                        attemptedAt = attemptedAt,
                    )
                },
                onFailure = { error ->
                    CatalogGovernanceHookDeliveryResponse(
                        hookCode = hookCode,
                        trigger = trigger,
                        transport = transport,
                        target = target,
                        status = "FAILED",
                        attemptedAt = attemptedAt,
                        details = error.message?.take(1000),
                    )
                },
            )
        }

    private suspend fun postJson(
        hookCode: String,
        trigger: String,
        transport: String,
        target: String,
        payload: String,
        attemptedAt: String,
    ): CatalogGovernanceHookDeliveryResponse =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = HttpRequest.newBuilder(URI.create(target))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build()
                httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            }.fold(
                onSuccess = { response ->
                    CatalogGovernanceHookDeliveryResponse(
                        hookCode = hookCode,
                        trigger = trigger,
                        transport = transport,
                        target = target,
                        status = if (response.statusCode() in 200..299) "DELIVERED" else "FAILED",
                        attemptedAt = attemptedAt,
                        responseStatus = response.statusCode(),
                        details = response.body().takeIf { it.isNotBlank() }?.take(1000),
                    )
                },
                onFailure = { error ->
                    CatalogGovernanceHookDeliveryResponse(
                        hookCode = hookCode,
                        trigger = trigger,
                        transport = transport,
                        target = target,
                        status = "FAILED",
                        attemptedAt = attemptedAt,
                        details = error.message?.take(1000),
                    )
                },
            )
        }
}
