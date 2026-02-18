package com.example.shoppingassistant.feature.pages.useroffers.backlog

internal class UserOffersBacklogTaskImpl : UserOffersBacklogTask {
    override fun items(): List<UserOffersBacklogItem> = listOf(
        UserOffersBacklogItem(
            id = "p0-data-source",
            priority = UserOffersBacklogPriority.P0,
            title = "Connect real data source for My Goods",
            details = listOf(
                "Feed comes from repository, not only local created list",
                "Support load, refresh, paging, pull-to-refresh, incremental updates",
            ),
        ),
        UserOffersBacklogItem(
            id = "p0-server-pagination",
            priority = UserOffersBacklogPriority.P0,
            title = "Server pagination strategy",
            details = listOf(
                "Infinite scroll uses cursor or offset, dedupe, end-of-list handling",
                "Handle empty or partial pages, retries, and guard against duplicate loads",
            ),
        ),
        UserOffersBacklogItem(
            id = "p0-local-sync-queue",
            priority = UserOffersBacklogPriority.P0,
            title = "Sync locally created items with server",
            details = listOf(
                "Local store becomes a queue: created -> syncing -> synced or failed",
                "Map tempId to serverId, prevent duplicates and conflicts",
            ),
        ),
        UserOffersBacklogItem(
            id = "p0-card-actions-real",
            priority = UserOffersBacklogPriority.P0,
            title = "Make card actions real operations",
            details = listOf(
                "Pause/activate, delete, renew, duplicate, archive/finish, promote/unpromote, share",
                "UI must call backend and reflect success or error, not only local status changes",
            ),
        ),
        UserOffersBacklogItem(
            id = "p0-change-price",
            priority = UserOffersBacklogPriority.P0,
            title = "Implement Change Price",
            details = listOf(
                "Validate format, currency, min and max",
                "States: updating -> success/failed; optimistic update with rollback on error",
                "For bulk: batch updates with per-item errors",
            ),
        ),
        UserOffersBacklogItem(
            id = "p0-bulk-partial-success",
            priority = UserOffersBacklogPriority.P0,
            title = "Bulk operations with partial success",
            details = listOf(
                "Support partial success: show failures and retry only failed",
                "Progress indicator, cancel, and retries",
            ),
        ),
        UserOffersBacklogItem(
            id = "p0-screen-state-model",
            priority = UserOffersBacklogPriority.P0,
            title = "Screen state model",
            details = listOf(
                "Loading, Content, Empty, Error, Unauthorized, Offline",
                "Separate empty from filters vs empty from no items",
            ),
        ),
        UserOffersBacklogItem(
            id = "p0-error-handling",
            priority = UserOffersBacklogPriority.P0,
            title = "Unified error handler and retry UX",
            details = listOf(
                "Short snackbar plus detailed dialog or screen",
                "Handle network, timeout, 401/403/429/5xx, validation",
            ),
        ),
        UserOffersBacklogItem(
            id = "p0-safety-idempotency",
            priority = UserOffersBacklogPriority.P0,
            title = "Safety for destructive actions",
            details = listOf(
                "Confirm delete/archive/bulk, provide undo where possible",
                "Block repeated taps and use idempotency keys",
            ),
        ),
        UserOffersBacklogItem(
            id = "p0-i18n-formatting",
            priority = UserOffersBacklogPriority.P0,
            title = "i18n and formatting",
            details = listOf(
                "Move UI strings to resources",
                "Format price, date, and numbers by Locale",
            ),
        ),
        UserOffersBacklogItem(
            id = "p1-server-filters-sort",
            priority = UserOffersBacklogPriority.P1,
            title = "Server-side filters and sorting",
            details = listOf(
                "Params: status (active/paused/draft/archived), moderation (published/on_moderation/error), promo, price range",
                "Params: publish, update, and expire dates",
            ),
        ),
        UserOffersBacklogItem(
            id = "p1-facet-counters",
            priority = UserOffersBacklogPriority.P1,
            title = "Facet counters for tabs and filters",
            details = listOf(
                "Active (123) and Completed (45) must be consistent and server-backed",
                "Counts per status for quick chips",
            ),
        ),
        UserOffersBacklogItem(
            id = "p1-persist-preferences",
            priority = UserOffersBacklogPriority.P1,
            title = "Persist user preferences",
            details = listOf(
                "Layout, sort, and filters saved in DataStore and restored",
            ),
        ),
        UserOffersBacklogItem(
            id = "p1-offline-stability",
            priority = UserOffersBacklogPriority.P1,
            title = "Offline stability",
            details = listOf(
                "Cache last successful feed",
                "Queue offline actions with queued state and sync on network return",
            ),
        ),
        UserOffersBacklogItem(
            id = "p1-offer-details",
            priority = UserOffersBacklogPriority.P1,
            title = "Deep offer details screen",
            details = listOf(
                "Open from card: views, messages, change history, moderation, promotion, visibility settings",
            ),
        ),
        UserOffersBacklogItem(
            id = "p1-moderation-ux",
            priority = UserOffersBacklogPriority.P1,
            title = "Moderation UX",
            details = listOf(
                "For ERROR show reason and fix CTA",
                "For ON_MODERATION show wait state and time estimate",
            ),
        ),
        UserOffersBacklogItem(
            id = "p1-stats-scoring",
            priority = UserOffersBacklogPriority.P1,
            title = "Stats like large platforms",
            details = listOf(
                "Views/favorites/messages, today and delta vs yesterday/week",
                "Health should come from server scoring, not heuristics",
            ),
        ),
        UserOffersBacklogItem(
            id = "p1-smart-search",
            priority = UserOffersBacklogPriority.P1,
            title = "Smart search for My Goods",
            details = listOf(
                "Search by title, category, id, keywords",
                "Suggestions/history and quick reset of filters",
            ),
        ),
        UserOffersBacklogItem(
            id = "p1-bulk-selection",
            priority = UserOffersBacklogPriority.P1,
            title = "Bulk selection improvements",
            details = listOf(
                "Select all and deselect all",
                "Select all results with confirmation",
                "Quick bulk chips: pause, archive, renew",
            ),
        ),
        UserOffersBacklogItem(
            id = "p1-real-export",
            priority = UserOffersBacklogPriority.P1,
            title = "Real export",
            details = listOf(
                "CSV/JSON or server file link, share via system",
                "Access rights and masking of sensitive data",
            ),
        ),
        UserOffersBacklogItem(
            id = "p1-notifications",
            priority = UserOffersBacklogPriority.P1,
            title = "Notifications and reactivation",
            details = listOf(
                "Push/in-app: moderation done, expires soon, views drop, publish error",
            ),
        ),
        UserOffersBacklogItem(
            id = "p2-onboarding",
            priority = UserOffersBacklogPriority.P2,
            title = "My Goods onboarding",
            details = listOf(
                "Hints for gestures, layout, bulk mode, filters",
            ),
        ),
        UserOffersBacklogItem(
            id = "p2-empty-states",
            priority = UserOffersBacklogPriority.P2,
            title = "Empty states with personality",
            details = listOf(
                "Per tab: no active -> create CTA, no completed -> explanation",
                "Empty due to filters -> reset filters",
            ),
        ),
        UserOffersBacklogItem(
            id = "p2-growth-scenarios",
            priority = UserOffersBacklogPriority.P2,
            title = "Growth scenarios",
            details = listOf(
                "Suggestions: add photo, refine category, price out of market, low views",
                "Prompt promotion or renewal",
            ),
        ),
        UserOffersBacklogItem(
            id = "p2-swipe-config",
            priority = UserOffersBacklogPriority.P2,
            title = "Configurable swipe actions",
            details = listOf(
                "User picks 2-3 primary actions",
            ),
        ),
        UserOffersBacklogItem(
            id = "p2-pinned-items",
            priority = UserOffersBacklogPriority.P2,
            title = "Pinned or favorites in My Goods",
            details = listOf(
                "Pin important items at top",
            ),
        ),
        UserOffersBacklogItem(
            id = "p2-change-history",
            priority = UserOffersBacklogPriority.P2,
            title = "Change history",
            details = listOf(
                "Log price, status, renew, moderation",
            ),
        ),
        UserOffersBacklogItem(
            id = "p2-ab-flags",
            priority = UserOffersBacklogPriority.P2,
            title = "A/B flags",
            details = listOf(
                "Toggle new metrics, new layout, new bulk UX",
            ),
        ),
        UserOffersBacklogItem(
            id = "p2-tests",
            priority = UserOffersBacklogPriority.P2,
            title = "Tests",
            details = listOf(
                "Unit: filters, sorting, status mapping",
                "UI: tabs, bulk, errors, offline",
                "Integration: repository and cache",
            ),
        ),
        UserOffersBacklogItem(
            id = "p2-observability",
            priority = UserOffersBacklogPriority.P2,
            title = "Observability",
            details = listOf(
                "Metrics: load time, create/open/edit conversion, operation errors, bulk success",
                "Logs with requestId correlation",
            ),
        ),
    )
}

fun userOffersBacklogTask(): UserOffersBacklogTask = UserOffersBacklogTaskImpl()
