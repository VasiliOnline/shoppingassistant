package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.AliasEntryRepository
import com.example.shoppingassistant.domain.catalog.BrowseNodeRepository
import com.example.shoppingassistant.domain.catalog.CatalogDataVersion
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesRepository
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesRequest
import com.example.shoppingassistant.domain.catalog.CatalogSchemaVersion
import com.example.shoppingassistant.domain.catalog.CatalogHttpContractPaths
import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.CategoryStatus
import com.example.shoppingassistant.domain.catalog.CategoryAliasRepository
import com.example.shoppingassistant.domain.catalog.GoogleTaxonomyMappingRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.java.KoinJavaComponent.getKoin

fun Route.catalogRoutes() {
    val repository: CatalogReadRepository = getKoin().get()
    val taxonomyRepository: CatalogTaxonomyRepository = getKoin().get()
    val liveValuesRepository: CatalogLiveValuesRepository = getKoin().get()
    val readinessHistoryService: CatalogReadinessHistoryService = getKoin().get()
    val readinessGovernanceService: CatalogReadinessGovernanceService = getKoin().get()
    val governanceReportsService: CatalogGovernanceReportsService = getKoin().get()
    val runtimeCompatibilityService: CatalogRuntimeCompatibilityService = getKoin().get()
    val openApiService: CatalogOpenApiService = getKoin().get()
    val readinessInventoryService: CatalogReadinessInventoryService = getKoin().get()
    val categoryAliasRepository: CategoryAliasRepository = getKoin().get()
    val browseNodeRepository: BrowseNodeRepository = getKoin().get()
    val aliasEntryRepository: AliasEntryRepository = getKoin().get()
    val googleTaxonomyRepository: GoogleTaxonomyMappingRepository = getKoin().get()
    val governanceRefreshSurfaceService: CatalogGovernanceRefreshSurfaceService = getKoin().get()

    route(CatalogHttpContractPaths.base) {
        get(relativeCatalogPath(CatalogHttpContractPaths.version)) {
            call.attachCatalogVersionHeader()
            call.respond(
                CatalogVersionResponse(
                    schemaVersion = CatalogSchemaVersion.current,
                    dataVersion = CatalogDataVersion.current,
                    minSupportedClientSchemaVersion = CatalogSchemaVersion.minSupportedClient,
                ),
            )
        }

        get(relativeCatalogPath(CatalogHttpContractPaths.runtimeCompatibility)) {
            call.attachCatalogVersionHeader()
            call.respond(runtimeCompatibilityService.getRuntimeCompatibility())
        }

        get(relativeCatalogPath(CatalogHttpContractPaths.openApi)) {
            call.attachCatalogVersionHeader()
            call.respondText(
                text = openApiService.getDocument(),
                contentType = ContentType.Application.Json,
            )
        }

        get(relativeCatalogPath(CatalogHttpContractPaths.categories)) {
            call.attachCatalogVersionHeader()
            call.respond(taxonomyRepository.listCategories())
        }

        get(relativeCatalogPath(CatalogHttpContractPaths.categoryResolve)) {
            call.attachCatalogVersionHeader()
            val categoryCode = call.parameters["categoryCode"]?.trim().orEmpty()
            if (categoryCode.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "categoryCode is required")
                return@get
            }
            val resolved = resolveCategoryRedirect(taxonomyRepository, categoryCode)
            call.attachCategoryRedirectHeaders(resolved)
            call.respond(
                CatalogCategoryResolveResponse(
                    requestedCode = resolved.requestedCode,
                    resolvedCode = resolved.resolvedCode,
                    redirected = resolved.wasRedirected,
                    redirectChain = resolved.redirectChain,
                ),
            )
        }

        get(relativeCatalogPath(CatalogHttpContractPaths.aliases)) {
            call.attachCatalogVersionHeader()
            call.respond(categoryAliasRepository.listAliases())
        }

        get(relativeCatalogPath(CatalogHttpContractPaths.browseNodes)) {
            call.attachCatalogVersionHeader()
            call.respond(browseNodeRepository.listBrowseNodes())
        }

        get(relativeCatalogPath(CatalogHttpContractPaths.browseNodeByCode)) {
            call.attachCatalogVersionHeader()
            val browseCode = call.parameters["browseCode"]?.trim().orEmpty()
            if (browseCode.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "browseCode is required")
                return@get
            }
            val node = browseNodeRepository.getBrowseNode(browseCode)
            if (node == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(node)
            }
        }

        get(relativeCatalogPath(CatalogHttpContractPaths.aliasEntries)) {
            call.attachCatalogVersionHeader()
            val locale = call.request.queryParameters["locale"]?.trim()?.ifBlank { null }
            val rows = aliasEntryRepository.listAliasEntries(locale = locale)
            call.respond(rows)
        }

        get(relativeCatalogPath(CatalogHttpContractPaths.googleMappings)) {
            call.attachCatalogVersionHeader()
            val categoryCode = call.request.queryParameters["categoryCode"]?.trim()?.ifBlank { null }
            if (categoryCode == null) {
                call.respond(googleTaxonomyRepository.listMappings())
            } else {
                val resolved = resolveCategoryRedirect(taxonomyRepository, categoryCode)
                call.attachCategoryRedirectHeaders(resolved)
                val mapping = googleTaxonomyRepository.getMapping(resolved.resolvedCode)
                call.respond(
                    listOfNotNull(mapping),
                )
            }
        }

        get(relativeCatalogPath(CatalogHttpContractPaths.googleMappingByCategory)) {
            call.attachCatalogVersionHeader()
            val categoryCode = call.parameters["categoryCode"]?.trim().orEmpty()
            if (categoryCode.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "categoryCode is required")
                return@get
            }
            val resolved = resolveCategoryRedirect(taxonomyRepository, categoryCode)
            call.attachCategoryRedirectHeaders(resolved)
            val mapping = googleTaxonomyRepository.getMapping(resolved.resolvedCode)
            if (mapping == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(mapping)
            }
        }

        get(relativeCatalogPath(CatalogHttpContractPaths.effectiveSpec)) {
            call.attachCatalogVersionHeader()
            val categoryCode = call.parameters["categoryCode"]?.trim().orEmpty()
            if (categoryCode.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "categoryCode is required")
                return@get
            }
            val brand = call.request.queryParameters["brand"]?.trim()?.ifBlank { null }
            val model = call.request.queryParameters["model"]?.trim()?.ifBlank { null }
            val resolved = resolveCategoryRedirect(taxonomyRepository, categoryCode)
            call.attachCategoryRedirectHeaders(resolved)
            val spec = repository.getCategoryEffectiveSpec(
                categoryCode = resolved.resolvedCode,
                brand = brand,
                model = model,
            )
            if (spec == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(spec)
            }
        }

        get(relativeCatalogPath(CatalogHttpContractPaths.readiness)) {
            call.attachCatalogVersionHeader()
            call.respond(readinessInventoryService.getInventory())
        }

        get(relativeCatalogPath(CatalogHttpContractPaths.readinessGovernance)) {
            call.attachCatalogVersionHeader()
            call.respond(readinessGovernanceService.getGovernance())
        }

        get(relativeCatalogPath(CatalogHttpContractPaths.readinessGovernanceReports)) {
            call.attachCatalogVersionHeader()
            val limit = call.request.queryParameters["limit"]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.toIntOrNull()
                ?: 20
            if (limit !in 1..100) {
                call.respond(HttpStatusCode.BadRequest, "limit must be between 1 and 100")
                return@get
            }
            call.respond(governanceReportsService.listReports(limit))
        }

        get(relativeCatalogPath(CatalogHttpContractPaths.readinessHistory)) {
            call.attachCatalogVersionHeader()
            val categoryCode = call.parameters["categoryCode"]?.trim().orEmpty()
            if (categoryCode.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "categoryCode is required")
                return@get
            }
            val days = call.request.queryParameters["days"]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.toIntOrNull()
                ?: DEFAULT_READINESS_HISTORY_DAYS
            if (days !in 1..MAX_OPERATIONAL_HISTORY_DAYS) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "days must be between 1 and $MAX_OPERATIONAL_HISTORY_DAYS",
                )
                return@get
            }
            val resolved = resolveCategoryRedirect(taxonomyRepository, categoryCode)
            call.attachCategoryRedirectHeaders(resolved)
            val history = readinessHistoryService.getCategoryHistory(
                categoryCode = resolved.resolvedCode,
                days = days,
            )
            if (history == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(history)
            }
        }

        get(relativeCatalogPath(CatalogHttpContractPaths.liveValues)) {
            call.attachCatalogVersionHeader()
            val request = CatalogLiveValuesRequest(
                categoryCode = call.request.queryParameters["categoryCode"]?.trim()?.ifBlank { null },
                brand = call.request.queryParameters["brand"]?.trim()?.ifBlank { null },
                model = call.request.queryParameters["model"]?.trim()?.ifBlank { null },
                localeTag = call.request.queryParameters["locale"]?.trim()?.ifBlank { null },
                attributeCodes = call.request.queryParameters.getAll("attributeCode")
                    .orEmpty()
                    .flatMap { raw ->
                        raw.split(',')
                            .map { value -> value.trim() }
                            .filter { value -> value.isNotEmpty() }
                    },
            )
            call.respond(liveValuesRepository.getLiveValues(request))
        }

        get(relativeCatalogPath(CatalogHttpContractPaths.governanceSources)) {
            call.attachCatalogVersionHeader()
            val categoryCode = call.request.queryParameters["categoryCode"]
                ?.trim()
                ?.ifBlank { null }
                ?: CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE
            call.respond(governanceRefreshSurfaceService.listSources(categoryCode = categoryCode))
        }

        get(relativeCatalogPath(CatalogHttpContractPaths.governanceRefreshStatus)) {
            call.attachCatalogVersionHeader()
            val categoryCode = call.request.queryParameters["categoryCode"]
                ?.trim()
                ?.ifBlank { null }
                ?: CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE
            call.respond(governanceRefreshSurfaceService.getRefreshStatus(categoryCode = categoryCode))
        }

        get(relativeCatalogPath(CatalogHttpContractPaths.governanceRefreshRuns)) {
            call.attachCatalogVersionHeader()
            val categoryCode = call.request.queryParameters["categoryCode"]
                ?.trim()
                ?.ifBlank { null }
                ?: CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE
            val limit = call.request.queryParameters["limit"]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.toIntOrNull()
                ?: 20
            if (limit !in 1..200) {
                call.respond(HttpStatusCode.BadRequest, "limit must be between 1 and 200")
                return@get
            }
            call.respond(
                governanceRefreshSurfaceService.listRefreshRuns(
                    categoryCode = categoryCode,
                    limit = limit,
                ),
            )
        }

        get(relativeCatalogPath(CatalogHttpContractPaths.governanceModelEnrichmentQueue)) {
            call.attachCatalogVersionHeader()
            val categoryCode = call.request.queryParameters["categoryCode"]
                ?.trim()
                ?.ifBlank { null }
                ?: CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE
            val limit = call.request.queryParameters["limit"]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.toIntOrNull()
                ?: 50
            if (limit !in 1..500) {
                call.respond(HttpStatusCode.BadRequest, "limit must be between 1 and 500")
                return@get
            }
            val statuses = call.request.queryParameters.getAll("status")
                .orEmpty()
                .flatMap { raw ->
                    raw.split(',').map { value -> value.trim() }.filter { value -> value.isNotEmpty() }
                }
                .mapNotNull { rawStatus ->
                    runCatching {
                        CatalogPhoneModelEnrichmentStatus.valueOf(rawStatus.uppercase())
                    }.getOrNull()
                }
                .toSet()
            call.respond(
                governanceRefreshSurfaceService.listModelEnrichmentQueue(
                    categoryCode = categoryCode,
                    statuses = statuses,
                    limit = limit,
                ),
            )
        }

        post(relativeCatalogPath(CatalogHttpContractPaths.governanceModelEnrichmentPromote)) {
            call.attachCatalogVersionHeader()
            val categoryCode = call.request.queryParameters["categoryCode"]
                ?.trim()
                ?.ifBlank { null }
                ?: CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE
            val request = runCatching {
                call.receive<CatalogGovernanceModelEnrichmentPromotionRequest>()
            }.getOrElse {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "Request body must be a valid CatalogGovernanceModelEnrichmentPromotionRequest JSON payload",
                )
                return@post
            }
            call.respond(
                governanceRefreshSurfaceService.promoteModelEnrichmentCandidate(
                    categoryCode = categoryCode,
                    request = request,
                ),
            )
        }

        get(relativeCatalogPath(CatalogHttpContractPaths.governanceReviewQueue)) {
            call.attachCatalogVersionHeader()
            val categoryCode = call.request.queryParameters["categoryCode"]
                ?.trim()
                ?.ifBlank { null }
                ?: CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE
            val limit = call.request.queryParameters["limit"]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.toIntOrNull()
                ?: 20
            if (limit !in 1..200) {
                call.respond(HttpStatusCode.BadRequest, "limit must be between 1 and 200")
                return@get
            }
            call.respond(
                governanceRefreshSurfaceService.listReviewQueue(
                    categoryCode = categoryCode,
                    limit = limit,
                ),
            )
        }

        get(relativeCatalogPath(CatalogHttpContractPaths.governancePublishEvents)) {
            call.attachCatalogVersionHeader()
            val categoryCode = call.request.queryParameters["categoryCode"]
                ?.trim()
                ?.ifBlank { null }
                ?: CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE
            val limit = call.request.queryParameters["limit"]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.toIntOrNull()
                ?: 20
            if (limit !in 1..200) {
                call.respond(HttpStatusCode.BadRequest, "limit must be between 1 and 200")
                return@get
            }
            call.respond(
                governanceRefreshSurfaceService.listPublishEvents(
                    categoryCode = categoryCode,
                    limit = limit,
                ),
            )
        }

        post(relativeCatalogPath(CatalogHttpContractPaths.governanceReviewAction)) {
            call.attachCatalogVersionHeader()
            val categoryCode = call.request.queryParameters["categoryCode"]
                ?.trim()
                ?.ifBlank { null }
                ?: CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE
            val candidateId = call.request.queryParameters["candidateId"]
                ?.trim()
                ?.toLongOrNull()
            if (candidateId == null || candidateId <= 0L) {
                call.respond(HttpStatusCode.BadRequest, "candidateId must be a positive integer")
                return@post
            }
            val action = runCatching {
                CatalogGovernanceReviewAction.valueOf(
                    call.request.queryParameters["action"]
                        ?.trim()
                        ?.uppercase()
                        .orEmpty(),
                )
            }.getOrNull()
            if (action == null) {
                call.respond(HttpStatusCode.BadRequest, "action must be one of APPROVE, REJECT, PROMOTE")
                return@post
            }
            val actor = call.request.queryParameters["actor"]?.trim().orEmpty().ifBlank { "manual_review" }
            val reasonCode = call.request.queryParameters["reasonCode"]?.trim()?.ifBlank { null }
            call.respond(
                governanceRefreshSurfaceService.submitReviewAction(
                    categoryCode = categoryCode,
                    candidateId = candidateId,
                    action = action,
                    actor = actor,
                    reasonCode = reasonCode,
                ),
            )
        }

        post(relativeCatalogPath(CatalogHttpContractPaths.governanceRefresh)) {
            call.attachCatalogVersionHeader()
            val categoryCode = call.request.queryParameters["categoryCode"]
                ?.trim()
                ?.ifBlank { null }
                ?: CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE
            val registryCodes = call.request.queryParameters.getAll("registryCode")
                .orEmpty()
                .flatMap { raw ->
                    raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                }
                .distinct()
            val trigger = call.request.queryParameters["trigger"]?.trim().orEmpty().ifBlank { "MANUAL" }
            call.respond(
                governanceRefreshSurfaceService.triggerRefresh(
                    categoryCode = categoryCode,
                    registryCodes = registryCodes,
                    trigger = trigger,
                ),
            )
        }

        post(relativeCatalogPath(CatalogHttpContractPaths.governanceRebuild)) {
            call.attachCatalogVersionHeader()
            val categoryCode = call.request.queryParameters["categoryCode"]
                ?.trim()
                ?.ifBlank { null }
                ?: CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE
            val reason = call.request.queryParameters["reason"]?.trim().orEmpty().ifBlank { "MANUAL_REBUILD" }
            call.respond(
                governanceRefreshSurfaceService.triggerRebuild(
                    categoryCode = categoryCode,
                    reason = reason,
                ),
            )
        }
    }
}

private fun relativeCatalogPath(path: String): String =
    path.removePrefix(CatalogHttpContractPaths.base)

private data class ResolvedCategoryRedirect(
    val requestedCode: String,
    val resolvedCode: String,
    val wasRedirected: Boolean,
    val redirectChain: List<String>,
)

private suspend fun resolveCategoryRedirect(
    repository: CatalogTaxonomyRepository,
    requestedCode: String,
): ResolvedCategoryRedirect {
    val normalizedRequested = requestedCode.trim()
    val resolution = runCatching { repository.resolveCategoryCode(normalizedRequested) }.getOrNull()
    val canRedirect = resolution != null &&
        resolution.wasRedirected &&
        !resolution.cycleDetected &&
        resolution.unresolvedTarget == null
    val resolvedCode = if (canRedirect) {
        resolution!!.resolvedCode
    } else {
        normalizedRequested
    }
    return ResolvedCategoryRedirect(
        requestedCode = normalizedRequested,
        resolvedCode = resolvedCode,
        wasRedirected = canRedirect,
        redirectChain = resolution?.redirectChain.orEmpty(),
    )
}

private fun ApplicationCall.attachCategoryRedirectHeaders(
    redirect: ResolvedCategoryRedirect,
) {
    response.headers.append(CATALOG_CATEGORY_REQUESTED_CODE_HEADER, redirect.requestedCode)
    response.headers.append(CATALOG_CATEGORY_RESOLVED_CODE_HEADER, redirect.resolvedCode)
    response.headers.append(CATALOG_CATEGORY_REDIRECTED_HEADER, redirect.wasRedirected.toString())
}

private const val DEFAULT_READINESS_HISTORY_DAYS = 30

