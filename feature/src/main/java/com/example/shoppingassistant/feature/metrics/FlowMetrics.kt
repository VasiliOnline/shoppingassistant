package com.example.shoppingassistant.feature.metrics

import android.os.SystemClock
import android.util.Log
import kotlin.random.Random

object FlowMetrics {
    private const val TAG = "FlowMetrics"
    private const val STATE_SAMPLE_RATE = 0.15f

    private var resultsStartMs: Long? = null
    private var resultsTaps: Int = 0

    private var draftStartMs: Long? = null
    private var draftTaps: Int = 0
    private var draftReadyLogged: Boolean = false

    private fun logEvent(name: String, details: String? = null) {
        val message = if (details.isNullOrBlank()) name else "$name $details"
        Log.d(TAG, message)
    }

    fun markEvent(name: String, details: String? = null) {
        logEvent(name, details)
    }

    fun shouldSampleState(): Boolean = Random.nextFloat() < STATE_SAMPLE_RATE

    fun markStateShown(
        sampled: Boolean,
        screen: String,
        type: String,
        subType: String? = null,
        reason: String? = null,
        wasCacheShown: Boolean? = null,
        cacheAgeMs: Long? = null,
    ) {
        if (!sampled) return
        val details = buildString {
            append("screen=$screen type=$type")
            if (!subType.isNullOrBlank()) append(" subType=$subType")
            if (!reason.isNullOrBlank()) append(" reason=$reason")
            if (wasCacheShown != null) append(" was_cache_shown=$wasCacheShown")
            if (cacheAgeMs != null) append(" cache_age_ms=$cacheAgeMs")
        }
        logEvent("state_shown", details)
    }

    fun markStateDuration(
        sampled: Boolean,
        screen: String,
        type: String,
        durationMs: Long,
    ) {
        if (!sampled) return
        logEvent("state_duration_ms", "screen=$screen type=$type duration_ms=$durationMs")
    }

    fun markStateAction(
        screen: String,
        type: String,
    ) {
        logEvent(type, "screen=$screen")
    }

    fun startResults(taps: Int) {
        resultsStartMs = SystemClock.elapsedRealtime()
        resultsTaps = taps.coerceAtLeast(1)
    }

    fun markResultsOpened() {
        val start = resultsStartMs ?: return
        val elapsed = SystemClock.elapsedRealtime() - start
        Log.d(TAG, "time_to_results_open=$elapsed taps_to_results=$resultsTaps")
        resultsStartMs = null
        resultsTaps = 0
    }

    fun startDraft(taps: Int) {
        draftStartMs = SystemClock.elapsedRealtime()
        draftTaps = taps.coerceAtLeast(1)
        draftReadyLogged = false
    }

    fun markCreateSheetOpened() {
        logEvent("open_create_sheet")
    }

    fun markDraftOpened() {
        logEvent("create_draft_opened")
    }

    fun markStepEntered(stepId: String) {
        logEvent("step_entered", "step=$stepId")
    }

    fun markMediaAdded(count: Int) {
        logEvent("media_added", "count=$count")
    }

    fun markTitleConfirmed() {
        logEvent("title_confirmed")
    }

    fun markCategorySelected() {
        logEvent("category_selected")
    }

    fun markPriceSet() {
        logEvent("price_set")
    }

    fun markPublishAttempted() {
        logEvent("publish_attempted")
    }

    fun markPublishSucceeded() {
        logEvent("publish_succeeded")
    }

    fun markPublishFailed(reason: String) {
        logEvent("publish_failed", "reason=$reason")
    }

    fun markCreateAction(source: String, mode: String) {
        logEvent("start_${source.lowercase()}", "mode=${mode.lowercase()}")
    }

    fun markOpenResultsFromSuggestion() {
        logEvent("open_results_from_suggestion")
    }

    fun markOpenResultsFromCategory() {
        logEvent("open_results_from_category")
    }

    fun markOpenResultsFromText() {
        logEvent("open_results_from_text")
    }

    fun markTrackClicked(source: String) {
        logEvent("track_click", "source=${source.lowercase()}")
    }

    fun markDraftSaved() {
        logEvent("draft_saved")
    }

    fun markDraftDiscarded() {
        logEvent("draft_discarded")
    }

    fun markPostPhotoFlowOpen() {
        logEvent("post_photo_flow_open")
    }

    fun markPhotoAdded(step: String, source: String) {
        logEvent("photo_added", "step=$step source=$source")
    }

    fun markPhotoDeleted(undoUsed: Boolean) {
        logEvent("photo_deleted", "undo_used=$undoUsed")
    }

    fun markCoreFieldsCompleted() {
        logEvent("core_fields_completed")
    }

    fun markPreviewOpen() {
        logEvent("preview_open")
    }

    fun markDuplicateWarningShown() {
        logEvent("duplicate_warning_shown")
    }

    fun markNearbyFiltersOpen(source: String) {
        logEvent("nearby_filters_open", "source=$source")
    }

    fun markNearbyFiltersApply(activeCount: Int, summary: String?) {
        val details = buildString {
            append("active_count=$activeCount")
            if (!summary.isNullOrBlank()) append(" summary=${summary.replace(' ', '_')}")
        }
        logEvent("nearby_filters_apply", details)
    }

    fun markNearbyFiltersClearAll() {
        logEvent("nearby_filters_clear_all")
    }

    fun markNearbyFiltersEmptyShown(activeCount: Int) {
        logEvent("nearby_filters_empty_shown", "active_count=$activeCount")
    }

    fun markNearbyFiltersEmptyCta(action: String) {
        logEvent("nearby_filters_empty_cta", "action=$action")
    }

    fun markNearbyFiltersErrorShown(type: String) {
        logEvent("nearby_filters_error_shown", "type=$type")
    }

    fun markNearbyFiltersErrorCta(action: String) {
        logEvent("nearby_filters_error_cta", "action=$action")
    }

    fun markNearbyLocationPermissionPrompt() {
        logEvent("nearby_location_permission_prompt")
    }

    fun markNearbyLocationPermissionResult(granted: Boolean) {
        logEvent("nearby_location_permission_result", "granted=$granted")
    }

    fun markPublishFailedDetailed(reason: String) {
        logEvent("publish_fail", "reason=$reason")
    }

    fun markPublishAttemptedDetailed() {
        logEvent("publish_attempt")
    }

    fun markPublishSucceededDetailed() {
        logEvent("publish_success")
    }

    fun markDraftReady() {
        if (draftReadyLogged) return
        val start = draftStartMs ?: return
        val elapsed = SystemClock.elapsedRealtime() - start
        Log.d(TAG, "time_to_draft_ready=$elapsed taps_to_publish=$draftTaps")
        draftReadyLogged = true
    }

    fun markPublishTapped() {
        val taps = if (draftTaps > 0) draftTaps else 1
        logEvent("publish_click", "taps=$taps")
    }
}
