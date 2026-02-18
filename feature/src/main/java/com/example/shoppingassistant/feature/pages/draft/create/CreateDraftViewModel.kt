package com.example.shoppingassistant.feature.pages.draft.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shoppingassistant.domain.catalog.CatalogRepository
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategoryProfile
import com.example.shoppingassistant.domain.ugc.draft.CreateDraftOfferTask
import com.example.shoppingassistant.domain.ugc.draft.DeleteDraftOfferTask
import com.example.shoppingassistant.domain.ugc.draft.DraftContacts
import com.example.shoppingassistant.domain.ugc.draft.DraftInputOrigin
import com.example.shoppingassistant.domain.ugc.draft.DraftMedia
import com.example.shoppingassistant.domain.ugc.draft.DraftMediaStatus
import com.example.shoppingassistant.domain.ugc.draft.DraftMediaType
import com.example.shoppingassistant.domain.ugc.draft.DraftPhotoRole
import com.example.shoppingassistant.domain.ugc.draft.DraftOffer
import com.example.shoppingassistant.domain.ugc.draft.DraftOfferSeed
import com.example.shoppingassistant.domain.ugc.draft.DraftPrice
import com.example.shoppingassistant.domain.ugc.draft.DraftPriceType
import com.example.shoppingassistant.domain.ugc.draft.DraftPublishStatus
import com.example.shoppingassistant.domain.ugc.draft.GetDraftOfferTask
import com.example.shoppingassistant.domain.ugc.draft.ListDraftOffersTask
import com.example.shoppingassistant.domain.ugc.draft.SaveDraftOfferTask
import com.example.shoppingassistant.domain.ugc.MirrorByUrlUseCase
import com.example.shoppingassistant.domain.ugc.UgcMirrorResult
import com.example.shoppingassistant.domain.profile.GetProfileCacheTask
import com.example.shoppingassistant.feature.metrics.FlowMetrics
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferCardUi
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferPublicationStatus
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferStatus
import com.example.shoppingassistant.feature.pages.useroffers.tasks.UserOffersCreatedStore
import java.security.MessageDigest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreateDraftViewModel(
    private val createDraftOffer: CreateDraftOfferTask,
    private val saveDraftOffer: SaveDraftOfferTask,
    private val getDraftOffer: GetDraftOfferTask,
    private val deleteDraftOffer: DeleteDraftOfferTask,
    private val listDraftOffers: ListDraftOffersTask,
    private val mirrorByUrl: MirrorByUrlUseCase,
    private val catalogRepository: CatalogRepository,
    private val createdStore: UserOffersCreatedStore,
    private val getProfileCache: GetProfileCacheTask,
    private val sessionStore: DraftWizardSessionStore,
) : ViewModel() {
    private val _state = MutableStateFlow(CreateDraftState())
    val state: StateFlow<CreateDraftState> = _state.asStateFlow()

    private val saveRequests = MutableSharedFlow<DraftOffer>(extraBufferCapacity = 1)
    private var loadJob: Job? = null
    private var activeDraftId: String? = null
    private var cachedDrafts: List<DraftOffer> = emptyList()

    init {
        viewModelScope.launch {
            saveRequests
                .debounce(SAVE_DEBOUNCE_MS)
                .collect { draft -> persistDraft(draft) }
        }
        viewModelScope.launch {
            listDraftOffers()
                .collect { drafts ->
                    cachedDrafts = drafts
                    updateDuplicate(state.value.draft, drafts)
                }
        }
    }

    fun openDraft(draftId: String?, origin: DraftInputOrigin? = null) {
        if (draftId != null && draftId == activeDraftId) return
        if (draftId == null && activeDraftId != null) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val session = if (draftId.isNullOrBlank() && origin == null) {
                sessionStore.get()
            } else {
                null
            }
            val resolvedId = draftId ?: session?.draftId
            val loaded = resolvedId
                ?.let { getDraftOffer(it) }
                ?.takeIf { it.publishStatus != DraftPublishStatus.PUBLISHED }
            val draft = loaded ?: run {
                val seed = DraftOfferSeed(inputOrigin = origin ?: DraftInputOrigin.TEXT)
                createDraftOffer(seed)
            }
            if (draft == null) {
                _state.update { it.copy(isLoading = false, errorMessage = "Черновик не найден") }
                return@launch
            }
            if (loaded == null && session != null) {
                sessionStore.clear()
            }
            activeDraftId = draft.id
            val hydrated = applyProfileDefaults(draft)
            val categoryState = loadCategories()
            val profile = loadCategoryProfile(hydrated.categoryCode)
            val attrsState = buildAttributesState(profile)
            val step = if (origin == null && isDraftEmpty(hydrated)) {
                DraftStep.ENTRY
            } else {
                resolveStep(hydrated, attrsState.required, session?.stepId)
            }
            _state.update {
                it.copy(
                    draft = hydrated,
                    savedDraft = draft,
                    duplicateDraft = null,
                    step = step,
                    subSheet = null,
                    categoryState = categoryState,
                    attributesState = attrsState,
                    isLoading = false,
                    isSaving = false,
                    isDirty = false,
                    errorMessage = null,
                )
            }
            updateDuplicate(hydrated, cachedDrafts)
            sessionStore.set(DraftWizardSession(draftId = hydrated.id, stepId = step.name))
            FlowMetrics.markDraftOpened()
            FlowMetrics.markStepEntered(step.name.lowercase())
        }
    }

    fun closeDraft() {
        val draft = state.value.draft ?: return
        viewModelScope.launch {
            flushAndMaybeDiscard(draft)
            sessionStore.clear()
            activeDraftId = null
        }
    }

    fun discardDraft(onComplete: (() -> Unit)? = null) {
        val draft = state.value.draft ?: return
        viewModelScope.launch {
            deleteDraftOffer(draft.id)
            createdStore.remove(draft.id)
            sessionStore.clear()
            activeDraftId = null
            _state.update { it.copy(draft = null, savedDraft = null, duplicateDraft = null, isDirty = false, isSaving = false) }
            onComplete?.invoke()
        }
    }

    fun setStep(step: DraftStep) {
        updateDraft { it.copy(lastStepId = step.name) }
        _state.update { it.copy(step = step) }
        val draftId = state.value.draft?.id
        if (draftId != null) {
            viewModelScope.launch { sessionStore.set(DraftWizardSession(draftId = draftId, stepId = step.name)) }
        }
        FlowMetrics.markStepEntered(step.name.lowercase())
    }

    fun openSubSheet(sheet: DraftSubSheet) {
        _state.update { it.copy(subSheet = sheet) }
    }

    fun closeSubSheet() {
        _state.update { it.copy(subSheet = null) }
    }

    fun openExistingDraft(draftId: String) {
        val current = state.value.draft
        if (current?.id == draftId) return
        viewModelScope.launch {
            current?.let { flushAndMaybeDiscard(it) }
            activeDraftId = null
            openDraft(draftId, origin = null)
        }
    }

    fun selectOrigin(origin: DraftInputOrigin) {
        updateDraft { it.copy(inputOrigin = origin) }
        val next = when (origin) {
            DraftInputOrigin.PHOTO,
            DraftInputOrigin.VIDEO,
            -> DraftStep.MEDIA
            DraftInputOrigin.LINK -> DraftStep.MEDIA
            DraftInputOrigin.VOICE,
            DraftInputOrigin.TEXT,
            DraftInputOrigin.TEMPLATE,
            -> DraftStep.TITLE_CATEGORY
        }
        setStep(next)
    }

    fun updateTitle(value: String) {
        updateDraft { it.copy(title = value.trimStart()) }
    }

    fun updateDescription(value: String) {
        updateDraft { it.copy(description = value.trim()) }
    }

    fun confirmTitle() {
        val draft = state.value.draft ?: return
        if (draft.title.isNullOrBlank()) return
        FlowMetrics.markTitleConfirmed()
        if (state.value.step == DraftStep.TITLE_CATEGORY) {
            setStep(DraftStep.PRICE)
        }
    }

    fun updateCategory(code: String?) {
        updateDraft { it.copy(categoryCode = code) }
        FlowMetrics.markCategorySelected()
        viewModelScope.launch {
            val profile = loadCategoryProfile(code)
            val attrsState = buildAttributesState(profile)
            _state.update { it.copy(attributesState = attrsState) }
        }
    }

    fun updatePriceInput(raw: String) {
        updateDraft {
            val trimmed = raw.trim()
            val parsed = trimmed.replace(",", ".").toDoubleOrNull()
            val updated = it.price.copy(
                rawInput = raw,
                amountMajor = parsed,
            )
            it.copy(price = updated)
        }
    }

    fun updatePriceType(type: DraftPriceType) {
        updateDraft {
            val updated = when (type) {
                DraftPriceType.FIXED -> it.price.copy(type = type)
                else -> DraftPrice(type = type)
            }
            it.copy(price = updated)
        }
        if (type != DraftPriceType.FIXED && state.value.step == DraftStep.PRICE) {
            confirmPrice()
        }
    }

    fun confirmPrice() {
        val draft = state.value.draft ?: return
        if (!DraftPublishValidator.isPriceReady(draft)) return
        FlowMetrics.markPriceSet()
        if (state.value.step == DraftStep.PRICE) {
            setStep(DraftStep.REVIEW_PUBLISH)
        }
    }

    fun updateCondition(condition: com.example.shoppingassistant.domain.ugc.draft.DraftCondition?) {
        updateDraft { it.copy(condition = condition) }
    }

    fun updateLocation(location: com.example.shoppingassistant.domain.ugc.draft.DraftLocation) {
        updateDraft { it.copy(location = location.copy(isDefault = false)) }
    }

    fun updateDelivery(delivery: com.example.shoppingassistant.domain.ugc.draft.DraftDelivery) {
        updateDraft { it.copy(delivery = delivery) }
    }

    fun updateContacts(contacts: DraftContacts) {
        updateDraft { it.copy(contacts = contacts) }
    }

    fun updateAttribute(key: String, value: String?) {
        updateDraft { draft ->
            val updated = if (value.isNullOrBlank()) {
                draft.attributes - key
            } else {
                draft.attributes + (key to value.trim())
            }
            draft.copy(attributes = updated)
        }
    }

    fun setActiveAttribute(def: com.example.shoppingassistant.domain.catalog.AttributeDef?) {
        if (def == null) {
            _state.update { it.copy(attributesState = it.attributesState.copy(activeAttr = null, activeValues = emptyList())) }
            return
        }
        viewModelScope.launch {
            val values = runCatching { catalogRepository.listAttributeValueDict(def.code) }
                .getOrNull()
                ?.entries
                ?.sortedByDescending { it.rank }
                ?.map { it.canonicalValue }
                .orEmpty()
            _state.update { it.copy(attributesState = it.attributesState.copy(activeAttr = def, activeValues = values)) }
        }
    }

    fun updateMedia(list: List<DraftMedia>) {
        val previousCount = state.value.draft?.media?.size ?: 0
        updateDraft { it.copy(media = list) }
        if (list.size > previousCount) {
            FlowMetrics.markMediaAdded(list.size)
        }
        if (list.isNotEmpty() && state.value.step == DraftStep.MEDIA) {
            setStep(DraftStep.TITLE_CATEGORY)
        }
    }

    fun upsertPhoto(role: DraftPhotoRole, index: Int, media: DraftMedia) {
        updateDraft { draft ->
            val updated = upsertMediaForRole(draft.media, role, index, media)
            draft.copy(media = updated)
        }
    }

    fun removePhoto(role: DraftPhotoRole, index: Int) {
        updateDraft { draft ->
            val updated = removeMediaForRole(draft.media, role, index)
            draft.copy(media = updated)
        }
    }

    fun saveDraftNow(onComplete: (() -> Unit)? = null) {
        val draft = state.value.draft ?: return
        viewModelScope.launch {
            persistDraft(draft)
            onComplete?.invoke()
        }
    }

    fun publishDraft(onSuccess: (() -> Unit)? = null) {
        val draft = state.value.draft ?: return
        val validation = DraftPublishValidator.validate(draft, state.value.attributesState.required)
        if (!validation.isReady) {
            FlowMetrics.markPublishFailed("missing_fields")
            return
        }
        FlowMetrics.markPublishAttempted()
        updateDraft {
            it.copy(
                publishStatus = DraftPublishStatus.PUBLISHING,
                publishError = null,
            )
        }
        viewModelScope.launch {
            updateDraft {
                it.copy(
                    publishStatus = DraftPublishStatus.PUBLISHED,
                    publishError = null,
                )
            }
            FlowMetrics.markPublishSucceeded()
            onSuccess?.invoke()
        }
    }

    fun setCategorySearch(query: String) {
        _state.update { it.copy(categoryState = it.categoryState.copy(searchQuery = query)) }
    }

    private fun updateDraft(block: (DraftOffer) -> DraftOffer) {
        val current = state.value.draft ?: return
        val updated = block(current).let { updated ->
            val fingerprint = computeFingerprint(updated)
            updated.copy(
                updatedAtMillis = System.currentTimeMillis(),
                fingerprint = fingerprint,
            )
        }
        _state.update { it.copy(draft = updated, isSaving = true, isDirty = true) }
        saveRequests.tryEmit(updated)
        updateDuplicate(updated, cachedDrafts)
    }

    private suspend fun persistDraft(draft: DraftOffer) {
        if (isDraftEmpty(draft)) {
            _state.update { it.copy(isSaving = false, isDirty = false) }
            return
        }
        saveDraftOffer(draft)
        createdStore.add(draft.toCardUi())
        _state.update { it.copy(savedDraft = draft, isSaving = false, isDirty = false) }
    }

    private suspend fun flushAndMaybeDiscard(draft: DraftOffer) {
        if (isDraftEmpty(draft)) {
            deleteDraftOffer(draft.id)
            createdStore.remove(draft.id)
            return
        }
        persistDraft(draft)
    }

    private suspend fun loadCategories(): DraftCategoryState {
        val categories = runCatching { catalogRepository.listCategories() }.getOrDefault(emptyList())
        val popular = categories.filter { it.parentCode == null }.take(8)
        return DraftCategoryState(categories = categories, popular = popular)
    }

    private suspend fun loadCategoryProfile(code: String?): CategoryProfile? {
        val trimmed = code?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        return runCatching { catalogRepository.getCategoryProfile(trimmed) }.getOrNull()
    }

    private fun buildAttributesState(profile: CategoryProfile?): DraftAttributesState {
        val defs = profile?.attributes.orEmpty()
        val required = profile?.categoryAttributes
            ?.filter { it.isRequiredForCategory }
            ?.map { it.attributeCode }
            ?.toSet()
            .orEmpty()
        return DraftAttributesState(defs = defs, required = required)
    }

    private suspend fun applyProfileDefaults(draft: DraftOffer): DraftOffer {
        val profile = runCatching { getProfileCache() }.getOrNull() ?: return draft
        val defaultCity = profile.city?.trim()?.takeIf { it.isNotBlank() }
        val defaultPhone = profile.phone?.trim()?.takeIf { it.isNotBlank() }
        var updated = draft
        if (!defaultPhone.isNullOrBlank() && updated.contacts.phone.isNullOrBlank()) {
            updated = updated.copy(contacts = updated.contacts.copy(phone = defaultPhone))
        }
        if (!defaultCity.isNullOrBlank() &&
            updated.location.city.isNullOrBlank() &&
            updated.location.publicLabel.isNullOrBlank() &&
            updated.location.address.isNullOrBlank()
        ) {
            updated = updated.copy(
                location = com.example.shoppingassistant.domain.ugc.draft.DraftLocation(
                    city = defaultCity,
                    publicLabel = defaultCity,
                    isDefault = true,
                )
            )
        }
        return updated
    }

    private fun areRequiredAttributesReady(draft: DraftOffer, requiredAttrs: Set<String>): Boolean {
        if (requiredAttrs.isEmpty()) return true
        return requiredAttrs.all { code -> DraftPublishValidator.isAttributeReady(draft, code) }
    }

    private fun updateDuplicate(draft: DraftOffer?, drafts: List<DraftOffer>) {
        if (draft == null) {
            _state.update { it.copy(duplicateDraft = null) }
            return
        }
        val hasIdentity = !draft.title.isNullOrBlank() ||
            !draft.categoryCode.isNullOrBlank() ||
            draft.attributes.isNotEmpty()
        if (!hasIdentity) {
            _state.update { it.copy(duplicateDraft = null) }
            return
        }
        val fingerprint = draft.fingerprint ?: computeFingerprint(draft)
        val duplicate = drafts.firstOrNull { it.id != draft.id && it.fingerprint == fingerprint }
        _state.update { it.copy(duplicateDraft = duplicate) }
    }

    private fun resolveStep(draft: DraftOffer, requiredAttrs: Set<String>, restoredStepId: String?): DraftStep {
        val fallback = when {
            draft.media.isEmpty() && draft.inputOrigin in mediaOrigins -> DraftStep.MEDIA
            draft.title.isNullOrBlank() -> DraftStep.TITLE_CATEGORY
            !DraftPublishValidator.isPriceReady(draft) -> DraftStep.PRICE
            !areRequiredAttributesReady(draft, requiredAttrs) -> DraftStep.ATTRIBUTES
            !DraftPublishValidator.isLocationReady(draft.location) ->
                DraftStep.LOCATION_DELIVERY
            !DraftPublishValidator.hasContacts(draft.contacts) -> DraftStep.CONTACTS
            else -> DraftStep.REVIEW_PUBLISH
        }
        val saved = restoredStepId
            ?: draft.lastStepId
        val parsed = saved?.let { runCatching { DraftStep.valueOf(it) }.getOrNull() }
        return parsed?.takeIf { stepOrder(it) >= stepOrder(fallback) } ?: fallback
    }

    private fun stepOrder(step: DraftStep): Int = when (step) {
        DraftStep.ENTRY -> 0
        DraftStep.MEDIA -> 1
        DraftStep.TITLE_CATEGORY -> 2
        DraftStep.PRICE -> 3
        DraftStep.ATTRIBUTES -> 4
        DraftStep.LOCATION_DELIVERY -> 5
        DraftStep.CONTACTS -> 6
        DraftStep.REVIEW_PUBLISH -> 7
    }

    private val mediaOrigins = setOf(
        DraftInputOrigin.PHOTO,
        DraftInputOrigin.VIDEO,
    )

    private fun isDraftEmpty(draft: DraftOffer): Boolean {
        val hasTitle = !draft.title.isNullOrBlank()
        val hasCategory = !draft.categoryCode.isNullOrBlank()
        val hasPriceInput = !draft.price.rawInput.isNullOrBlank() ||
            draft.price.type != DraftPriceType.FIXED ||
            draft.price.amountMajor != null
        val hasMedia = draft.media.isNotEmpty()
        val hasAttrs = draft.attributes.isNotEmpty()
        val hasCondition = draft.condition != null
        val hasLocation = !draft.location.isDefault && (
            !draft.location.city.isNullOrBlank() ||
                !draft.location.address.isNullOrBlank() ||
                !draft.location.publicLabel.isNullOrBlank()
            )
        return !(hasTitle || hasCategory || hasPriceInput || hasMedia || hasAttrs || hasCondition || hasLocation)
    }

    private fun computeFingerprint(draft: DraftOffer): String {
        val raw = buildString {
            append(draft.title?.trim()?.lowercase().orEmpty())
            append("|")
            append(draft.categoryCode?.trim()?.lowercase().orEmpty())
            draft.attributes.toSortedMap().forEach { (key, value) ->
                append("|")
                append(key.lowercase())
                append("=")
                append(value.trim().lowercase())
            }
            draft.condition?.let { condition ->
                append("|condition=")
                append(condition.name.lowercase())
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun upsertMediaForRole(
        media: List<DraftMedia>,
        role: DraftPhotoRole,
        index: Int,
        newMedia: DraftMedia,
    ): List<DraftMedia> {
        val (withRole, withoutRole) = media.partition { it.role != null }
        val currentRole = withRole.filter { it.role == role }.toMutableList()
        val sanitized = newMedia.copy(role = role)
        if (index in currentRole.indices) {
            currentRole[index] = sanitized
        } else {
            currentRole.add(sanitized)
        }
        val updated = withRole.filter { it.role != role } + currentRole
        return sortMediaByRole(updated + withoutRole)
    }

    private fun removeMediaForRole(
        media: List<DraftMedia>,
        role: DraftPhotoRole,
        index: Int,
    ): List<DraftMedia> {
        val (withRole, withoutRole) = media.partition { it.role != null }
        val currentRole = withRole.filter { it.role == role }.toMutableList()
        if (index in currentRole.indices) {
            currentRole.removeAt(index)
        }
        val updated = withRole.filter { it.role != role } + currentRole
        return sortMediaByRole(updated + withoutRole)
    }

    private fun sortMediaByRole(media: List<DraftMedia>): List<DraftMedia> {
        if (media.isEmpty()) return media
        val withRole = media.filter { it.role != null }
        val withoutRole = media.filter { it.role == null }
        val grouped = withRole.groupBy { it.role!! }
        val ordered = photoRoleOrder.flatMap { role -> grouped[role].orEmpty() }
        val unknown = withRole.filter { it.role !in photoRoleOrder }
        return ordered + unknown + withoutRole
    }

    private fun DraftOffer.toCardUi(): UserOfferCardUi {
        val title = this.title?.takeIf { it.isNotBlank() } ?: "Без названия"
        val cover = this.media.firstOrNull()?.localUri ?: this.media.firstOrNull()?.remoteUrl
        return UserOfferCardUi(
            id = this.id,
            title = title,
            category = this.categoryCode,
            priceMajor = this.price.amountMajor,
            currency = this.price.currency ?: "USD",
            status = UserOfferStatus.DRAFT,
            publicationStatus = UserOfferPublicationStatus.PUBLISHED,
            coverUrl = cover,
            publishedAtMillis = null,
            updatedAtMillis = this.updatedAtMillis,
            sourceUpdatedAtMillis = this.updatedAtMillis,
        )
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 450L
        val photoRoleOrder = listOf(
            DraftPhotoRole.FRONT,
            DraftPhotoRole.DETAILS,
            DraftPhotoRole.DEFECTS,
        )
    }
}
