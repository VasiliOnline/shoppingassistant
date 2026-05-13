package com.example.shoppingassistant.server.vision

import com.example.shoppingassistant.domain.catalog.CatalogAttributeSpec
import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategoryStatus
import com.example.shoppingassistant.domain.catalog.allAttributes
import com.example.shoppingassistant.domain.i18n.displayLabel
import com.example.shoppingassistant.domain.i18n.displayTitle
import com.example.shoppingassistant.domain.model.Normalization
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import com.example.shoppingassistant.domain.search.SearchTextNormalizer
import com.example.shoppingassistant.domain.vision.VisionAttributeCandidate
import com.example.shoppingassistant.domain.vision.VisionBindOutcome
import com.example.shoppingassistant.domain.vision.VisionCategoryCandidate
import com.example.shoppingassistant.domain.vision.VisionNextAction
import com.example.shoppingassistant.domain.vision.VisionNormalizeRequest
import com.example.shoppingassistant.domain.vision.VisionNormalizeResult
import com.example.shoppingassistant.domain.vision.VisionPhotoInput
import com.example.shoppingassistant.domain.vision.VisionPhotoRole
import com.example.shoppingassistant.domain.vision.VisionRawExtraction
import com.example.shoppingassistant.domain.vision.VisionSource
import com.example.shoppingassistant.server.ai.AiNormalizationFlow
import com.example.shoppingassistant.server.ai.AiNormalizationOrchestrator
import com.example.shoppingassistant.server.ai.AiStructuredContractLoader
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioContentPart
import com.example.shoppingassistant.server.catalog.CatalogAiCategoryHintResolver
import com.example.shoppingassistant.server.config.ListingVisionAiConfig
import java.util.Base64
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface VisionAiPhotoNormalizer {
    suspend fun normalize(request: VisionNormalizeRequest): VisionNormalizeResult?
}

class YandexListingVisionAiNormalizer(
    private val catalogRepository: CatalogReadRepository,
    private val catalogTaxonomyRepository: CatalogTaxonomyRepository,
    private val config: ListingVisionAiConfig,
    private val orchestrator: AiNormalizationOrchestrator,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    },
) : VisionAiPhotoNormalizer {

    private val aiContract = AiStructuredContractLoader.load("listing_offer")
    private val categoryHintResolver = CatalogAiCategoryHintResolver(catalogTaxonomyRepository)

    override suspend fun normalize(request: VisionNormalizeRequest): VisionNormalizeResult? {
        if (!config.yandexReady) return null
        val selectedPhotos = selectPhotos(request.photos)
        if (selectedPhotos.isEmpty()) return null
        val groundingPacket = buildGroundingPacket(request)
        val contentParts = buildContentParts(request, groundingPacket, selectedPhotos)
        return orchestrator.executeStructuredJson(
            flow = AiNormalizationFlow.LISTING_OFFER,
            systemInstruction = buildSystemInstruction(),
            contentParts = contentParts,
            schemaName = "listing_photo_normalize_draft",
            schema = aiContract.schema,
        ) { responseBody, target ->
            val payload = runCatching {
                json.decodeFromString(ListingVisionPayload.serializer(), responseBody)
            }.getOrNull() ?: return@executeStructuredJson null
            val chosenCategoryCode = resolveChosenCategoryCode(
                payload = payload,
                groundingPacket = groundingPacket,
                locale = request.locale,
                requestedCategoryCode = request.categoryHint,
            )
            val effectiveSpec = chosenCategoryCode?.let { categoryCode ->
                catalogRepository.getCategoryEffectiveSpec(
                    categoryCode = categoryCode,
                    brand = payload.brand?.trim(),
                    model = payload.model?.trim(),
                )
            }
            val attributeProjection = payload.attributes.toAttributeProjection(effectiveSpec?.allAttributes().orEmpty())
            val normalizedQuery = payload.toNormalizedQuery(attributeProjection.acceptedAttributes)
            val missingRequiredKeys = resolveMissingKeys(effectiveSpec?.allAttributes().orEmpty(), normalizedQuery)
            val bindOutcome = VisionBindOutcome(
                rawCategoryHint = payload.categoryHint ?: payload.categoryCode ?: request.categoryHint,
                resolvedCategoryCode = chosenCategoryCode,
                acceptedAttributeCodes = attributeProjection.acceptedAttributes.keys
                    .map(Normalization::attributeKey)
                    .filter { code -> code.isNotEmpty() }
                    .distinct()
                    .sorted(),
                unresolvedAttributeCodes = attributeProjection.unresolvedAttributes
                    .map { candidate -> Normalization.attributeKey(candidate.code) }
                    .filter { code -> code.isNotEmpty() }
                    .distinct()
                    .sorted(),
                missingRequiredKeys = missingRequiredKeys,
            )
            val nextAction = resolveNextAction(payload, selectedPhotos, missingRequiredKeys)
            val categoryCandidates = buildCategoryCandidates(
                chosenCategoryCode = chosenCategoryCode,
                locale = request.locale,
                groundingPacket = groundingPacket,
                fallbackHint = payload.categoryHint ?: payload.categoryCode ?: request.categoryHint,
            )
            val title = payload.title
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: listOfNotNull(
                    payload.brand?.trim()?.takeIf { it.isNotEmpty() },
                    payload.model?.trim()?.takeIf { it.isNotEmpty() },
                ).joinToString(" ").ifBlank { null }
                ?: chosenCategoryCode?.let { code ->
                    categoryCandidates.firstOrNull { candidate -> candidate.code.equals(code, ignoreCase = true) }?.title
                }
            val warnings = buildList {
                if (payload.needsRetake) {
                    add(payload.retakeReason?.trim()?.takeIf { it.isNotEmpty() } ?: "RETAKE_RECOMMENDED")
                }
                if (chosenCategoryCode == null) {
                    add("CATEGORY_UNRESOLVED")
                }
                if (attributeProjection.unresolvedAttributes.isNotEmpty()) {
                    add("PROFILE_ATTR_UNRESOLVED")
                }
                if (target.usesSavedAgent) {
                    add("AI_AGENT_${target.key.uppercase(Locale.ROOT)}")
                }
            }.distinct()
            val usedSources = buildSet {
                add(VisionSource.VISUAL)
                if (selectedPhotos.any { photo -> photo.role in techRoles }) {
                    add(VisionSource.TECH_OCR)
                }
            }

            if (normalizedQuery == null && chosenCategoryCode == null && categoryCandidates.isEmpty() && nextAction == null) {
                return@executeStructuredJson null
            }

            VisionNormalizeResult(
                normalizedQuery = normalizedQuery,
                categoryCode = chosenCategoryCode,
                categoryCandidates = categoryCandidates,
                rawExtraction = payload.toRawExtraction(),
                bindOutcome = bindOutcome,
                title = title,
                missingRequiredKeys = missingRequiredKeys,
                confidence = payload.confidence?.coerceIn(0f, 1f) ?: payload.categoryConfidence?.coerceIn(0f, 1f),
                usedSources = usedSources,
                warnings = warnings,
                errors = emptyList(),
                nextAction = nextAction,
            )
        }?.value
    }

    private suspend fun buildGroundingPacket(request: VisionNormalizeRequest): ListingVisionGroundingPacket {
        val locale = request.locale?.trim()?.ifEmpty { null } ?: "ru"
        val categoryShortlist = resolveRequestedCategoryShortlist(request)
        val requestedCategoryCode = categoryShortlist.firstOrNull() ?: request.categoryHint?.trim()?.takeIf { it.isNotEmpty() }
        val candidateCategories = buildCategoryCards(
            categoryCodes = categoryShortlist,
            locale = locale,
        )
        return ListingVisionGroundingPacket(
            locale = locale,
            requestedCategoryCode = requestedCategoryCode,
            parseFrontBackOnly = request.parseFrontBackOnly,
            hints = request.hints.values
                .map { value -> normalizeHint(value) }
                .filter { value -> value.isNotEmpty() }
                .distinct()
                .take(8),
            candidateCategories = candidateCategories,
        )
    }

    private suspend fun resolveRequestedCategoryShortlist(
        request: VisionNormalizeRequest,
    ): List<String> {
        val resolved = LinkedHashSet<String>()
        resolveCategoryCode(request.categoryHint)?.let(resolved::add)
        request.hints.values.forEach { hint ->
            if (resolved.size >= config.shortlistCategoryLimit) return@forEach
            resolveCategoryCode(hint)?.let(resolved::add)
        }
        return resolved.toList()
    }

    private suspend fun buildCategoryCards(
        categoryCodes: List<String>,
        locale: String,
    ): List<ListingVisionCategoryCard> {
        if (categoryCodes.isEmpty()) return emptyList()
        val categories = activeCategories()
        val picked = categoryCodes
            .mapNotNull { code ->
                categories.firstOrNull { category -> category.code.equals(code, ignoreCase = true) }
            }
            .distinctBy { category -> category.code }
            .take(config.shortlistCategoryLimit)
        return picked.map { category -> buildCategoryCard(category, locale) }
    }

    private suspend fun buildCategoryCard(
        category: Category,
        locale: String,
    ): ListingVisionCategoryCard {
        val effectiveSpec = catalogRepository.getCategoryEffectiveSpec(category.code)
        val attributes = effectiveSpec?.allAttributes()
            ?.take(config.categoryAttributeLimit)
            ?.map { spec ->
                ListingVisionAttributeCard(
                    code = spec.code,
                    title = spec.title,
                    dataType = spec.dataType.name.lowercase(Locale.ROOT),
                    allowedValues = spec.options
                        .take(6)
                        .map { option -> option.displayLabel(locale) }
                        .filter { value -> value.isNotBlank() },
                )
            }
            .orEmpty()
        return ListingVisionCategoryCard(
            code = category.code.uppercase(Locale.ROOT),
            title = category.displayTitle(locale),
            segment = category.segment.name,
            parentCode = category.parentCode,
            attributes = attributes,
        )
    }

    private fun buildSystemInstruction(): String =
        buildString {
            append(aiContract.systemPrompt.trim())
            config.yandexSystemPrompt
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { customPrompt ->
                    appendLine()
                    appendLine()
                    appendLine("Project-specific rules:")
                    append(customPrompt)
                }
        }.trim()

    private fun buildContentParts(
        request: VisionNormalizeRequest,
        groundingPacket: ListingVisionGroundingPacket,
        photos: List<SelectedVisionPhoto>,
    ): List<YandexAiStudioContentPart> =
        buildList {
            add(
                YandexAiStudioContentPart.Text(
                    AiStructuredContractLoader.renderTemplate(
                        template = aiContract.userPromptTemplate,
                        bindings = mapOf(
                            "grounding_packet" to json.encodeToString(
                                ListingVisionGroundingPacket.serializer(),
                                groundingPacket,
                            ),
                            "seller_hints" to request.hints.entries
                                .joinToString(separator = "\n") { (key, value) -> "- $key: ${value.trim()}" }
                                .ifBlank { "- none" },
                        ),
                    ),
                ),
            )
            photos.forEachIndexed { index, photo ->
                add(
                    YandexAiStudioContentPart.Text(
                        "Photo ${index + 1}. role=${photo.role.name.lowercase(Locale.ROOT)}",
                    ),
                )
                add(
                    YandexAiStudioContentPart.ImageBase64(
                        mimeType = photo.mimeType,
                        base64 = photo.base64,
                    ),
                )
            }
        }

    private suspend fun resolveChosenCategoryCode(
        payload: ListingVisionPayload,
        groundingPacket: ListingVisionGroundingPacket,
        locale: String?,
        requestedCategoryCode: String?,
    ): String? {
        val allowedCodes = groundingPacket.candidateCategories
            .map { category -> category.code.uppercase(Locale.ROOT) }
            .toSet()
        val payloadCode = payload.categoryCode
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() }
        if (payloadCode != null) {
            if (allowedCodes.isEmpty()) {
                resolveCategoryCode(payloadCode)?.let { return it }
            } else if (payloadCode in allowedCodes) {
                return payloadCode
            }
        }

        matchCategoryHintToCode(
            rawHint = payload.categoryHint ?: payload.categoryCode,
            locale = locale,
            allowedCodes = allowedCodes,
        )?.let { return it }

        return resolveCategoryCode(requestedCategoryCode)
    }

    private suspend fun buildCategoryCandidates(
        chosenCategoryCode: String?,
        locale: String?,
        groundingPacket: ListingVisionGroundingPacket,
        fallbackHint: String?,
    ): List<VisionCategoryCandidate> {
        if (groundingPacket.candidateCategories.isNotEmpty()) {
            return groundingPacket.candidateCategories
                .sortedWith(
                    compareByDescending<ListingVisionCategoryCard> { card ->
                        card.code.equals(chosenCategoryCode, ignoreCase = true)
                    }.thenBy { card -> card.code },
                )
                .take(3)
                .mapIndexed { index, card ->
                    VisionCategoryCandidate(
                        code = card.code,
                        title = card.title,
                        score = when {
                            card.code.equals(chosenCategoryCode, ignoreCase = true) -> 0.92f
                            index == 0 -> 0.74f
                            index == 1 -> 0.62f
                            else -> 0.5f
                        },
                    )
                }
        }

        val seedText = chosenCategoryCode ?: fallbackHint ?: return emptyList()
        val categories = activeCategories()
        val matched = scoreCategories(
            categories = categories,
            seedText = seedText,
            locale = locale,
        )
            .take(3)
            .mapIndexed { index, category ->
                VisionCategoryCandidate(
                    code = category.code,
                    title = category.displayTitle(locale),
                    score = when {
                        category.code.equals(chosenCategoryCode, ignoreCase = true) -> 0.88f
                        index == 0 -> 0.7f
                        index == 1 -> 0.58f
                        else -> 0.48f
                    },
                )
            }

        if (chosenCategoryCode == null || matched.any { candidate -> candidate.code.equals(chosenCategoryCode, ignoreCase = true) }) {
            return matched
        }
        val chosen = categories.firstOrNull { category -> category.code.equals(chosenCategoryCode, ignoreCase = true) }
            ?: return matched
        return listOf(
            VisionCategoryCandidate(
                code = chosen.code,
                title = chosen.displayTitle(locale),
                score = 0.88f,
            ),
        ) + matched.take(2)
    }

    private fun resolveMissingKeys(
        attributeSpecs: List<CatalogAttributeSpec>,
        normalizedQuery: NormalizedQuery?,
    ): List<String> {
        if (normalizedQuery == null) return emptyList()
        val required = attributeSpecs
            .filter { spec -> spec.requiredForExpress || spec.requiredForOffer || spec.requiredForCategory }
            .map { spec -> Normalization.attributeKey(spec.code) }
            .filter { code -> code.isNotEmpty() }
            .toMutableSet()
        val present = buildSet {
            normalizedQuery.attributes.keys.forEach { key ->
                val normalized = Normalization.attributeKey(key)
                if (normalized.isNotEmpty()) add(normalized)
            }
            if (normalizedQuery.brand.isNotBlank()) add("brand")
            if (normalizedQuery.model.isNotBlank()) add("model")
        }
        return required.filterNot { code -> code in present }.sorted()
    }

    private fun resolveNextAction(
        payload: ListingVisionPayload,
        selectedPhotos: List<SelectedVisionPhoto>,
        missingRequiredKeys: List<String>,
    ): VisionNextAction? {
        payload.nextAction.toVisionNextActionOrNull()?.let { return it }
        if (payload.needsRetake) {
            return if (selectedPhotos.any { photo -> photo.role in techRoles }) {
                VisionNextAction.RETAKE_CLEAR_TEXT
            } else {
                VisionNextAction.RETAKE_PHOTO
            }
        }
        if (missingRequiredKeys.isEmpty()) return null
        return if (selectedPhotos.none { photo -> photo.role in techRoles }) {
            VisionNextAction.ADD_TECH_PHOTO
        } else {
            null
        }
    }

    private fun ListingVisionPayload.toNormalizedQuery(
        attributes: Map<String, TypedAttributeValue>,
    ): NormalizedQuery? {
        val brand = brand?.trim().orEmpty()
        val model = model?.trim().orEmpty()
        if (brand.isBlank() && model.isBlank() && attributes.isEmpty()) return null
        return NormalizedQuery(
            brand = brand,
            model = model,
            attributes = attributes,
        )
    }

    private fun List<ListingVisionAttributePayload>.toAttributeProjection(
        attributeSpecs: List<CatalogAttributeSpec>,
    ): ListingAttributeProjection {
        val accepted = LinkedHashMap<String, TypedAttributeValue>()
        val unresolved = mutableListOf<VisionAttributeCandidate>()
        val enforceProfile = attributeSpecs.isNotEmpty()
        forEach { payload ->
            val normalizedCode = Normalization.attributeKey(payload.code)
            if (normalizedCode.isEmpty()) return@forEach
            val spec = attributeSpecs.firstOrNull { spec ->
                Normalization.attributeKey(spec.code) == normalizedCode
            }
            val typedValue = payload.toTypedValue()
            when {
                spec != null && typedValue != null -> accepted[spec.code] = typedValue
                !enforceProfile && typedValue != null -> accepted[normalizedCode] = typedValue
                else -> unresolved += payload.toCandidate()
            }
        }
        return ListingAttributeProjection(
            acceptedAttributes = accepted,
            unresolvedAttributes = unresolved,
        )
    }

    private fun ListingVisionAttributePayload.toTypedValue(): TypedAttributeValue? =
        when (kind?.trim()?.lowercase(Locale.ROOT)) {
            "bool",
            "boolean",
            -> bool?.let(TypedAttributeValue::Bool)

            "number",
            "int",
            "decimal",
            -> number?.let(TypedAttributeValue::Number)

            else -> when {
                bool != null -> TypedAttributeValue.Bool(bool)
                number != null -> TypedAttributeValue.Number(number)
                !text.isNullOrBlank() -> TypedAttributeValue.Text(text.trim())
                else -> null
            }
        }

    private fun ListingVisionPayload.toRawExtraction(): VisionRawExtraction =
        VisionRawExtraction(
            categoryHint = categoryHint ?: categoryCode,
            brand = brand?.trim()?.takeIf { it.isNotEmpty() },
            model = model?.trim()?.takeIf { it.isNotEmpty() },
            title = title?.trim()?.takeIf { it.isNotEmpty() },
            reasonCodes = reasonCodes.distinct(),
            attributes = attributes.map { payload -> payload.toCandidate() },
        )

    private fun ListingVisionAttributePayload.toCandidate(): VisionAttributeCandidate =
        VisionAttributeCandidate(
            code = code.trim(),
            kind = kind?.trim()?.takeIf { it.isNotEmpty() },
            text = text?.trim()?.takeIf { it.isNotEmpty() },
            number = number,
            bool = bool,
            confidence = confidence?.coerceIn(0f, 1f),
        )

    private suspend fun matchCategoryHintToCode(
        rawHint: String?,
        locale: String?,
        allowedCodes: Set<String> = emptySet(),
    ): String? = categoryHintResolver.resolveCategoryCode(
        rawHint = rawHint,
        locale = locale,
        allowedCodes = allowedCodes,
    )

    private fun scoreCategories(
        categories: List<Category>,
        seedText: String,
        locale: String?,
    ): List<Category> {
        val normalizedSeed = normalizeHint(seedText)
        if (normalizedSeed.isEmpty()) return emptyList()
        val hintTokens = SearchTextNormalizer.tokens(normalizedSeed)
            .map { token -> token.lowercase(Locale.ROOT) }
            .toSet()
        return categories
            .map { category ->
                val title = category.displayTitle(locale)
                val normalizedTitle = normalizeHint(title)
                val titleTokens = SearchTextNormalizer.tokens(normalizedTitle)
                    .map { token -> token.lowercase(Locale.ROOT) }
                    .toSet()
                val overlap = titleTokens.intersect(hintTokens).size
                val exactBoost = when {
                    normalizedTitle == normalizedSeed -> 100
                    normalizedTitle.contains(normalizedSeed) || normalizedSeed.contains(normalizedTitle) -> 24
                    else -> 0
                }
                category to (exactBoost + overlap * 12)
            }
            .filter { (_, score) -> score > 0 }
            .sortedWith(compareByDescending<Pair<Category, Int>> { it.second }.thenBy { it.first.code })
            .map { (category, _) -> category }
    }

    private suspend fun activeCategories(): List<Category> =
        catalogTaxonomyRepository.listCategories()
            .filter { category -> category.status == CategoryStatus.ACTIVE }

    private suspend fun resolveCategoryCode(categoryCode: String?): String? {
        val normalized = categoryCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(Locale.ROOT) ?: return null
        val resolution = runCatching {
            catalogTaxonomyRepository.resolveCategoryCode(normalized)
        }.getOrNull()
        return if (resolution != null &&
            resolution.wasRedirected &&
            !resolution.cycleDetected &&
            resolution.unresolvedTarget == null
        ) {
            resolution.resolvedCode.uppercase(Locale.ROOT)
        } else if (resolution != null &&
            !resolution.cycleDetected &&
            resolution.unresolvedTarget == null
        ) {
            resolution.resolvedCode.uppercase(Locale.ROOT)
        } else {
            null
        }
    }

    private fun selectPhotos(photos: List<VisionPhotoInput>): List<SelectedVisionPhoto> {
        val chosen = buildList {
            rolePriority.forEach { role ->
                photos.firstOrNull { photo ->
                    photo.role == role && photo.base64.isNotBlank()
                }?.let(::add)
            }
            photos.filter { photo ->
                photo.base64.isNotBlank() && none { existing -> existing === photo }
            }.forEach(::add)
        }
            .take(config.maxPhotosPerRequest)
        return chosen.map { photo ->
            SelectedVisionPhoto(
                role = photo.role,
                base64 = photo.base64,
                mimeType = detectMimeType(photo.base64),
            )
        }
    }

    private fun detectMimeType(base64: String): String {
        val bytes = runCatching { Base64.getDecoder().decode(base64) }.getOrNull()
            ?: return "image/jpeg"
        return when {
            bytes.size >= 4 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte() ->
                "image/png"

            bytes.size >= 3 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte() ->
                "image/jpeg"

            bytes.size >= 12 &&
                bytes[0] == 'R'.code.toByte() &&
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() &&
                bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() &&
                bytes[9] == 'E'.code.toByte() &&
                bytes[10] == 'B'.code.toByte() &&
                bytes[11] == 'P'.code.toByte() ->
                "image/webp"

            else -> "image/jpeg"
        }
    }

    private fun normalizeHint(raw: String?): String =
        raw?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(SearchTextNormalizer::normalizeKey)
            .orEmpty()

    private fun String?.toVisionNextActionOrNull(): VisionNextAction? =
        this?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.uppercase(Locale.ROOT)
            ?.let { raw -> runCatching { VisionNextAction.valueOf(raw) }.getOrNull() }

    private companion object {
        private val rolePriority = listOf(
            VisionPhotoRole.FRONT,
            VisionPhotoRole.BACK,
            VisionPhotoRole.TECH_1,
            VisionPhotoRole.TECH_2,
            VisionPhotoRole.LEFT,
            VisionPhotoRole.RIGHT,
            VisionPhotoRole.TOP,
            VisionPhotoRole.BOTTOM,
        )
        private val techRoles = setOf(
            VisionPhotoRole.TECH_1,
            VisionPhotoRole.TECH_2,
        )
    }
}

private data class SelectedVisionPhoto(
    val role: VisionPhotoRole,
    val base64: String,
    val mimeType: String,
)

private data class ListingAttributeProjection(
    val acceptedAttributes: Map<String, TypedAttributeValue>,
    val unresolvedAttributes: List<VisionAttributeCandidate>,
)

@Serializable
private data class ListingVisionGroundingPacket(
    val locale: String,
    val requestedCategoryCode: String? = null,
    val parseFrontBackOnly: Boolean = false,
    val hints: List<String> = emptyList(),
    val candidateCategories: List<ListingVisionCategoryCard> = emptyList(),
)

@Serializable
private data class ListingVisionCategoryCard(
    val code: String,
    val title: String,
    val segment: String,
    val parentCode: String? = null,
    val attributes: List<ListingVisionAttributeCard> = emptyList(),
)

@Serializable
private data class ListingVisionAttributeCard(
    val code: String,
    val title: String,
    val dataType: String,
    val allowedValues: List<String> = emptyList(),
)

@Serializable
private data class ListingVisionPayload(
    @SerialName("needs_retake")
    val needsRetake: Boolean = false,
    @SerialName("retake_reason")
    val retakeReason: String? = null,
    val confidence: Float? = null,
    val title: String? = null,
    @SerialName("category_code")
    val categoryCode: String? = null,
    @SerialName("category_hint")
    val categoryHint: String? = null,
    @SerialName("category_confidence")
    val categoryConfidence: Float? = null,
    val brand: String? = null,
    val model: String? = null,
    @SerialName("next_action")
    val nextAction: String? = null,
    @SerialName("reason_codes")
    val reasonCodes: List<String> = emptyList(),
    val attributes: List<ListingVisionAttributePayload> = emptyList(),
)

@Serializable
private data class ListingVisionAttributePayload(
    val code: String,
    val kind: String? = null,
    val text: String? = null,
    val number: Double? = null,
    val bool: Boolean? = null,
    val confidence: Float? = null,
)
