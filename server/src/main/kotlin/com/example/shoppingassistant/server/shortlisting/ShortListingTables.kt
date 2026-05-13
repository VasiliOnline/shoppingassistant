package com.example.shoppingassistant.server.shortlisting

import com.example.shoppingassistant.domain.shortlisting.ShortListingCategoryCandidate
import com.example.shoppingassistant.domain.shortlisting.ShortListingEvidenceTask
import com.example.shoppingassistant.domain.shortlisting.ShortListingFieldValue
import com.example.shoppingassistant.domain.shortlisting.ShortListingIdentitySignature
import com.example.shoppingassistant.domain.shortlisting.ShortListingProfileGate
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishLifecycleStatus
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishLocals
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishReadiness
import com.example.shoppingassistant.domain.shortlisting.ShortListingVisionResult
import com.example.shoppingassistant.server.db.AuthUsersTable
import com.example.shoppingassistant.server.offers.OffersTable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb

private val shortListingJson = Json { ignoreUnknownKeys = true }

object ShortListingDraftsTable : Table("short_listing_drafts") {
    val id = varchar("id", 64)
    val sessionId = varchar("session_id", 64).uniqueIndex()
    val userId = long("user_id").references(AuthUsersTable.id, onDelete = ReferenceOption.CASCADE)
    val stage = varchar("stage", 32)
    val requestedCategoryCode = varchar("requested_category_code", 64).nullable()
    val resolvedCategoryCode = varchar("resolved_category_code", 64).nullable()
    val candidateCategory = jsonb(
        "candidate_category",
        shortListingJson,
        ShortListingCategoryCandidate.serializer(),
    ).nullable()
    val confirmedCategoryCode = varchar("confirmed_category_code", 64).nullable()
    val media = jsonb(
        "media",
        shortListingJson,
        ListSerializer(com.example.shoppingassistant.domain.shortlisting.ShortListingMediaReceipt.serializer()),
    )
    val predictedFields = jsonb(
        "predicted_fields",
        shortListingJson,
        MapSerializer(String.serializer(), ShortListingFieldValue.serializer()),
    )
    val confirmedUserFields = jsonb(
        "confirmed_user_fields",
        shortListingJson,
        MapSerializer(String.serializer(), ShortListingFieldValue.serializer()),
    )
    val missingRequiredFields = jsonb(
        "missing_required_fields",
        shortListingJson,
        ListSerializer(String.serializer()),
    )
    val evidenceTasks = jsonb(
        "evidence_tasks",
        shortListingJson,
        ListSerializer(ShortListingEvidenceTask.serializer()),
    )
    val publishLocals = jsonb(
        "publish_locals",
        shortListingJson,
        ShortListingPublishLocals.serializer(),
    )
    val publishReadiness = jsonb(
        "publish_readiness",
        shortListingJson,
        ShortListingPublishReadiness.serializer(),
    )
    val profileGate = jsonb(
        "profile_gate",
        shortListingJson,
        ShortListingProfileGate.serializer(),
    )
    val identitySignature = jsonb(
        "identity_signature",
        shortListingJson,
        ShortListingIdentitySignature.serializer(),
    ).nullable()
    val lastVisionResult = jsonb(
        "last_vision_result",
        shortListingJson,
        ShortListingVisionResult.serializer(),
    ).nullable()
    val publishedOfferId = long("published_offer_id").references(OffersTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val lifecycleStatus = varchar("lifecycle_status", 32).nullable()
    val expiresAtMillis = long("expires_at_millis").nullable()
    val safeToExit = bool("safe_to_exit").default(false)
    val revision = integer("revision").default(1)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    val deleted = bool("deleted").default(false)

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, userId, deleted, updatedAt)
        index(false, userId, stage)
        index(false, userId, publishedOfferId)
        index(false, userId, resolvedCategoryCode)
        index(false, userId, lifecycleStatus)
    }
}

internal fun ShortListingPublishLifecycleStatus.toDbValue(): String = name
