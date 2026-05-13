package com.example.shoppingassistant.feature.pages.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shoppingassistant.core.data.catalog.CatalogGovernanceAdminPublishEvent
import com.example.shoppingassistant.core.data.catalog.CatalogGovernanceAdminReadinessItem
import com.example.shoppingassistant.core.data.catalog.CatalogGovernanceAdminRefreshStatus
import com.example.shoppingassistant.core.data.catalog.CatalogGovernanceAdminRefreshRun
import com.example.shoppingassistant.core.data.catalog.CatalogGovernanceAdminRefreshTriggerResult
import com.example.shoppingassistant.core.data.catalog.CatalogGovernanceAdminRepository
import com.example.shoppingassistant.core.data.catalog.CatalogGovernanceAdminReviewAction
import com.example.shoppingassistant.core.data.catalog.CatalogGovernanceAdminReviewQueueItem
import com.example.shoppingassistant.core.data.catalog.CatalogGovernanceAdminSourceRegistryEntry
import com.example.shoppingassistant.core.data.catalog.DEFAULT_CATALOG_GOVERNANCE_CATEGORY_CODE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CatalogGovernanceAdminUiState(
    val categoryCode: String = DEFAULT_CATALOG_GOVERNANCE_CATEGORY_CODE,
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val readinessInventory: List<CatalogGovernanceAdminReadinessItem> = emptyList(),
    val coverageSummary: CatalogGovernanceCoverageSummary? = null,
    val refreshStatus: CatalogGovernanceAdminRefreshStatus? = null,
    val sources: List<CatalogGovernanceAdminSourceRegistryEntry> = emptyList(),
    val runs: List<CatalogGovernanceAdminRefreshRun> = emptyList(),
    val reviewQueue: List<CatalogGovernanceAdminReviewQueueItem> = emptyList(),
    val publishEvents: List<CatalogGovernanceAdminPublishEvent> = emptyList(),
)

class CatalogGovernanceAdminViewModel(
    private val repository: CatalogGovernanceAdminRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogGovernanceAdminUiState())
    val uiState: StateFlow<CatalogGovernanceAdminUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload(categoryCode: String = uiState.value.categoryCode) {
        val current = uiState.value
        _uiState.value = current.copy(
            categoryCode = categoryCode,
            isLoading = true,
            errorMessage = null,
        )
        viewModelScope.launch {
            runCatching { loadSnapshot(categoryCode) }
                .onSuccess { snapshot ->
                    _uiState.value = snapshot.copy(
                        isLoading = false,
                        isBusy = false,
                        errorMessage = null,
                        statusMessage = current.statusMessage,
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = current.copy(
                        categoryCode = categoryCode,
                        isLoading = false,
                        isBusy = false,
                        errorMessage = throwable.message ?: "Не удалось загрузить governance surface.",
                    )
                }
        }
    }

    fun triggerRefreshAll() {
        runAction(actionLabel = "Refresh") { categoryCode ->
            val result = repository.triggerRefresh(
                categoryCode = categoryCode,
                trigger = "ANDROID_DEBUG_MANUAL",
            )
            "Запущено refresh-run: ${result.runs.size}"
        }
    }

    fun triggerRefreshSource(registryCode: String) {
        val normalizedRegistryCode = registryCode.trim()
        if (normalizedRegistryCode.isEmpty()) return
        runAction(actionLabel = "Refresh $normalizedRegistryCode") { categoryCode ->
            val result = repository.triggerRefresh(
                categoryCode = categoryCode,
                registryCodes = listOf(normalizedRegistryCode),
                trigger = "ANDROID_DEBUG_SOURCE",
            )
            val run = result.runs.firstOrNull()
            if (run == null) {
                "Источник $normalizedRegistryCode не запустился"
            } else {
                "${run.registryCode}: ${run.status}"
            }
        }
    }

    fun triggerRebuild() {
        runAction(actionLabel = "Rebuild") { categoryCode ->
            val result = repository.triggerRebuild(
                categoryCode = categoryCode,
                reason = "ANDROID_DEBUG_REBUILD",
            )
            "Rebuild: ${result.status}"
        }
    }

    fun submitReviewAction(
        candidateId: Long,
        action: CatalogGovernanceAdminReviewAction,
    ) {
        runAction(actionLabel = action.name) { categoryCode ->
            val result = repository.submitReviewAction(
                categoryCode = categoryCode,
                candidateId = candidateId,
                action = action,
                actor = "android_debug",
                reasonCode = "android_${action.name.lowercase()}",
            )
            "${action.name} ${result.candidateId}: ${result.candidateStatus}"
        }
    }

    private fun runAction(
        actionLabel: String,
        block: suspend (categoryCode: String) -> String,
    ) {
        val current = uiState.value
        if (current.isBusy) return
        _uiState.value = current.copy(isBusy = true, errorMessage = null, statusMessage = "$actionLabel...")
        viewModelScope.launch {
            val categoryCode = uiState.value.categoryCode
            runCatching {
                val message = block(categoryCode)
                val snapshot = loadSnapshot(categoryCode)
                snapshot.copy(statusMessage = message)
            }.onSuccess { snapshot ->
                _uiState.value = snapshot.copy(
                    isLoading = false,
                    isBusy = false,
                    errorMessage = null,
                )
            }.onFailure { throwable ->
                _uiState.value = uiState.value.copy(
                    isBusy = false,
                    errorMessage = throwable.message ?: "Action failed",
                    statusMessage = null,
                )
            }
        }
    }

    private suspend fun loadSnapshot(
        categoryCode: String,
    ): CatalogGovernanceAdminUiState {
        val readinessInventory = repository.listReadinessInventory()
        val refreshStatus = repository.getRefreshStatus(categoryCode = categoryCode)
        val sources = repository.listSources(categoryCode = categoryCode)
        val runs = repository.listRefreshRuns(categoryCode = categoryCode, limit = 20)
        val reviewQueue = repository.listReviewQueue(categoryCode = categoryCode, limit = 20)
        val publishEvents = repository.listPublishEvents(categoryCode = categoryCode, limit = 20)
        val coverageSummary = buildCatalogGovernanceCoverageSummary(
            readinessInventory = readinessInventory,
            refreshStatus = refreshStatus,
        )
        return CatalogGovernanceAdminUiState(
            categoryCode = categoryCode,
            readinessInventory = readinessInventory,
            coverageSummary = coverageSummary,
            refreshStatus = refreshStatus,
            sources = sources,
            runs = runs,
            reviewQueue = reviewQueue,
            publishEvents = publishEvents,
        )
    }
}
