package com.example.shoppingassistant.server.localoffer

import com.example.shoppingassistant.server.db.AuthUsersTable
import com.example.shoppingassistant.server.offers.OffersTable
import com.example.shoppingassistant.server.shortlisting.ShortListingDraftsTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object LocalOfferSessionsTable : Table("local_offer_sessions") {
    val sessionId = varchar("session_id", 64)
    val userId = long("user_id").references(AuthUsersTable.id, onDelete = ReferenceOption.CASCADE)
    val currentDraftId = varchar("current_draft_id", 64)
        .references(ShortListingDraftsTable.id, onDelete = ReferenceOption.SET_NULL)
        .nullable()
        .uniqueIndex()
    val state = varchar("state", 32)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(sessionId)

    init {
        index(false, userId, updatedAt)
    }
}

object LocalOfferGeoSnapshotsTable : Table("local_offer_geo_snapshots") {
    val id = varchar("id", 64)
    val sessionId = varchar("session_id", 64).references(LocalOfferSessionsTable.sessionId, onDelete = ReferenceOption.CASCADE)
    val userId = long("user_id").references(AuthUsersTable.id, onDelete = ReferenceOption.CASCADE)
    val draftId = varchar("draft_id", 64)
        .references(ShortListingDraftsTable.id, onDelete = ReferenceOption.SET_NULL)
        .nullable()
    val consentState = varchar("consent_state", 16)
    val status = varchar("status", 32)
    val capturedAtMillis = long("captured_at_millis")
    val expiresAtMillis = long("expires_at_millis")
    val accuracyMeters = double("accuracy_meters")
    val countryCode = varchar("country_code", 8)
    val adminArea = varchar("admin_area", 255).nullable()
    val city = varchar("city", 255)
    val lat = double("lat").nullable()
    val lon = double("lon").nullable()
    val geoSource = varchar("source", 32)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    val deleted = bool("deleted").default(false)

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, userId, sessionId, deleted, updatedAt)
        index(false, userId, draftId, deleted, updatedAt)
    }
}

object LocalOfferPublishCommandsTable : Table("local_offer_publish_commands") {
    val id = varchar("id", 64)
    val draftId = varchar("draft_id", 64).references(ShortListingDraftsTable.id, onDelete = ReferenceOption.CASCADE)
    val userId = long("user_id").references(AuthUsersTable.id, onDelete = ReferenceOption.CASCADE)
    val revision = integer("revision")
    val effectiveSpecVersion = varchar("effective_spec_version", 128)
    val geoSnapshotId = varchar("geo_snapshot_id", 64).references(LocalOfferGeoSnapshotsTable.id, onDelete = ReferenceOption.RESTRICT)
    val state = varchar("state", 32)
    val offerId = long("offer_id").references(OffersTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val failureCode = varchar("failure_code", 128).nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, userId, draftId, updatedAt)
        index(false, userId, state, updatedAt)
    }
}
