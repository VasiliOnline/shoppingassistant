package com.example.shoppingassistant.server.profile

import com.example.shoppingassistant.domain.model.Money
import com.example.shoppingassistant.domain.profile.ProfileListingPreviewItem
import com.example.shoppingassistant.domain.profile.ProfileLookupResult
import com.example.shoppingassistant.domain.profile.ProfilePrivacyUpdatePayload
import com.example.shoppingassistant.domain.profile.ProfilePublicUpdatePayload
import com.example.shoppingassistant.domain.profile.ProfileVerificationBadge
import com.example.shoppingassistant.domain.profile.SellerDeliveryZone
import com.example.shoppingassistant.domain.profile.SellerDeliveryZonesUpdatePayload
import com.example.shoppingassistant.domain.profile.ProfileView
import com.example.shoppingassistant.domain.profile.ProfileViewScope
import com.example.shoppingassistant.domain.profile.deriveShippingCountriesFromDeliveryZones
import com.example.shoppingassistant.domain.profile.hasDeliveryZoneConflict
import com.example.shoppingassistant.domain.profile.legacyShippingCountriesToDeliveryZones
import com.example.shoppingassistant.domain.profile.normalized
import com.example.shoppingassistant.domain.profile.normalizedDeliveryZones
import com.example.shoppingassistant.server.db.AuthUsersTable
import com.example.shoppingassistant.server.db.DatabaseFactory
import com.example.shoppingassistant.server.offers.OfferSourcesTable
import com.example.shoppingassistant.server.offers.OffersTable
import com.example.shoppingassistant.server.offers.ProductsTable
import com.example.shoppingassistant.server.offers.SellerStatsTable
import com.example.shoppingassistant.server.offers.UserPreferencesTable
import com.example.shoppingassistant.server.offers.UserProfilesTable
import com.example.shoppingassistant.server.offers.UserReviewsTable
import com.example.shoppingassistant.server.offers.publicOfferVisibilityOp
import java.net.URI
import java.util.Locale
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class ProfileContractService {

    suspend fun getProfileView(
        targetUserId: Long,
        viewerUserId: Long?,
    ): ProfileLookupResult = DatabaseFactory.dbQuery {
        loadProfileView(
            targetUserId = targetUserId,
            viewerUserId = viewerUserId,
        )
    }

    suspend fun updatePublicProfile(
        userId: Long,
        payload: ProfilePublicUpdatePayload,
    ): ProfileView = DatabaseFactory.dbQuery {
        val displayName = payload.displayName.trim()
        require(displayName.isNotEmpty()) { "DISPLAY_NAME_REQUIRED" }
        require(displayName.length <= 80) { "DISPLAY_NAME_TOO_LONG" }

        val bio = payload.bio
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.also { require(it.length <= 280) { "BIO_TOO_LONG" } }
        val city = payload.city
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.also { require(it.length <= 120) { "CITY_TOO_LONG" } }
        val avatarUrl = payload.avatarUrl
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.also { require(it.length <= 512) { "AVATAR_URL_TOO_LONG" } }
        val website = normalizeWebsite(payload.website)

        ensureProfileRow(userId)

        AuthUsersTable.update(
            where = { (AuthUsersTable.id eq userId) and (AuthUsersTable.isDeleted eq false) },
        ) { row ->
            row[AuthUsersTable.displayName] = displayName
            row[AuthUsersTable.city] = city
            row[AuthUsersTable.avatarUrl] = avatarUrl
        }

        UserProfilesTable.update(
            where = { UserProfilesTable.userId eq userId },
        ) { row ->
            row[UserProfilesTable.displayName] = displayName
            row[UserProfilesTable.city] = city
            row[UserProfilesTable.avatarUrl] = avatarUrl
            row[UserProfilesTable.bio] = bio
            row[UserProfilesTable.website] = website
        }

        val resolved = loadProfileView(
            targetUserId = userId,
            viewerUserId = userId,
        )
        check(resolved is ProfileLookupResult.Found) { "UPDATED_PROFILE_NOT_FOUND" }
        resolved.profile
    }

    suspend fun updatePrivacy(
        userId: Long,
        payload: ProfilePrivacyUpdatePayload,
    ): ProfileView = DatabaseFactory.dbQuery {
        ensureProfileRow(userId)

        UserProfilesTable.update(
            where = { UserProfilesTable.userId eq userId },
        ) { row ->
            row[UserProfilesTable.publicProfileEnabled] = payload.publicProfileEnabled
            row[UserProfilesTable.cityVisible] = payload.cityVisible
        }

        val resolved = loadProfileView(
            targetUserId = userId,
            viewerUserId = userId,
        )
        check(resolved is ProfileLookupResult.Found) { "UPDATED_PROFILE_NOT_FOUND" }
        resolved.profile
    }

    suspend fun updateSellerDeliveryZones(
        userId: Long,
        payload: SellerDeliveryZonesUpdatePayload,
    ): ProfileView = DatabaseFactory.dbQuery {
        ensureProfileRow(userId)
        ensurePreferencesRow(userId)

        val normalizedZones = payload.zones
            .normalizedDeliveryZones()
            .take(MAX_DELIVERY_ZONES)
        require(normalizedZones.size == payload.zones.normalizedDeliveryZones().size) {
            "DELIVERY_ZONES_LIMIT_EXCEEDED"
        }
        require(normalizedZones.map { zone -> zone.id }.distinct().size == normalizedZones.size) {
            "DELIVERY_ZONE_IDS_DUPLICATED"
        }

        val acceptedZones = ArrayList<SellerDeliveryZone>(normalizedZones.size)
        normalizedZones.forEach { zone ->
            validateSellerDeliveryZone(zone)
            require(!hasDeliveryZoneConflict(acceptedZones, zone)) {
                "DELIVERY_ZONE_CONFLICT"
            }
            acceptedZones += zone
        }

        UserPreferencesTable.update(
            where = { UserPreferencesTable.userId eq userId },
        ) { row ->
            row[deliveryZones] = acceptedZones
            row[shippingCountries] = deriveShippingCountriesFromDeliveryZones(acceptedZones)
        }

        val resolved = loadProfileView(
            targetUserId = userId,
            viewerUserId = userId,
        )
        check(resolved is ProfileLookupResult.Found) { "UPDATED_PROFILE_NOT_FOUND" }
        resolved.profile
    }

    private fun loadProfileView(
        targetUserId: Long,
        viewerUserId: Long?,
    ): ProfileLookupResult {
        val row = AuthUsersTable
            .leftJoin(UserProfilesTable, { AuthUsersTable.id }, { UserProfilesTable.userId })
            .leftJoin(UserPreferencesTable, { AuthUsersTable.id }, { UserPreferencesTable.userId })
            .leftJoin(SellerStatsTable, { AuthUsersTable.id }, { SellerStatsTable.userId })
            .selectAll()
            .where {
                (AuthUsersTable.id eq targetUserId) and (AuthUsersTable.isDeleted eq false)
            }
            .singleOrNull()
            ?: return ProfileLookupResult.NotFound

        val isOwner = viewerUserId == targetUserId
        val publicProfileEnabled = row.tryGet(UserProfilesTable.publicProfileEnabled) ?: true
        if (!isOwner && !publicProfileEnabled) {
            return ProfileLookupResult.PrivateUnavailable
        }

        val ratingAggregate = resolveRatingAggregate(
            targetUserId = targetUserId,
            fallbackValue = row.tryGet(SellerStatsTable.ratingValue),
            fallbackCount = row.tryGet(SellerStatsTable.ratingCount),
        )
        val cityVisible = row.tryGet(UserProfilesTable.cityVisible) ?: true
        val city = if (isOwner || cityVisible) {
            row.tryGet(UserProfilesTable.city) ?: row.tryGet(AuthUsersTable.city)
        } else {
            null
        }
        val displayName = row.tryGet(UserProfilesTable.displayName)
            ?: row.tryGet(AuthUsersTable.displayName)
            ?: row[AuthUsersTable.email].substringBefore('@').ifBlank { "Пользователь" }
        val emailVerified = row[AuthUsersTable.emailVerified] || row.tryGet(AuthUsersTable.emailVerifiedAt) != null
        val phoneVerified = row.tryGet(AuthUsersTable.phoneVerifiedAt) != null
        val verificationBadges = buildList {
            if (emailVerified) add(ProfileVerificationBadge.EMAIL_VERIFIED)
            if (phoneVerified) add(ProfileVerificationBadge.PHONE_VERIFIED)
            if (ratingAggregate.count > 0) add(ProfileVerificationBadge.TRUSTED_SELLER)
        }
        val sellerDeliveryZones = row.tryGet(UserPreferencesTable.deliveryZones)
            ?.takeIf { zones -> zones.isNotEmpty() }
            ?: legacyShippingCountriesToDeliveryZones(row.tryGet(UserPreferencesTable.shippingCountries))
        val listingsCount = OffersTable
            .selectAll()
            .where {
                (OffersTable.userId eq targetUserId) and publicOfferVisibilityOp()
            }
            .count()
            .toInt()
        val listingsPreview = loadListingsPreview(targetUserId)

        return ProfileLookupResult.Found(
            profile = ProfileView(
                userId = targetUserId,
                scope = if (isOwner) ProfileViewScope.OWNER else ProfileViewScope.PUBLIC,
                email = if (isOwner) row[AuthUsersTable.email] else null,
                phone = if (isOwner) row.tryGet(AuthUsersTable.phone) else null,
                displayName = displayName,
                bio = row.tryGet(UserProfilesTable.bio),
                website = row.tryGet(UserProfilesTable.website),
                avatarUrl = row.tryGet(UserProfilesTable.avatarUrl) ?: row.tryGet(AuthUsersTable.avatarUrl),
                city = city,
                joinedAtMillis = row.tryGet(AuthUsersTable.createdAt),
                emailVerified = emailVerified,
                phoneVerified = phoneVerified,
                publicProfileEnabled = if (isOwner) publicProfileEnabled else true,
                cityVisible = if (isOwner) cityVisible else true,
                ratingAverage = ratingAggregate.value,
                ratingCount = ratingAggregate.count,
                verificationBadges = verificationBadges,
                listingsCount = listingsCount,
                listingsPreview = listingsPreview,
                sellerDeliveryZones = if (isOwner) sellerDeliveryZones else emptyList(),
            ),
        )
    }

    private fun ensureProfileRow(userId: Long) {
        val exists = UserProfilesTable
            .selectAll()
            .where { UserProfilesTable.userId eq userId }
            .singleOrNull() != null
        if (exists) return

        val authRow = AuthUsersTable
            .selectAll()
            .where { AuthUsersTable.id eq userId }
            .singleOrNull()
            ?: return

        UserProfilesTable.insert { row ->
            row[UserProfilesTable.userId] = userId
            row[UserProfilesTable.displayName] = authRow.tryGet(AuthUsersTable.displayName)
            row[UserProfilesTable.avatarUrl] = authRow.tryGet(AuthUsersTable.avatarUrl)
            row[UserProfilesTable.city] = authRow.tryGet(AuthUsersTable.city)
            row[UserProfilesTable.bio] = null
            row[UserProfilesTable.website] = null
            row[UserProfilesTable.publicProfileEnabled] = true
            row[UserProfilesTable.cityVisible] = true
        }
    }

    private fun ensurePreferencesRow(userId: Long) {
        val exists = UserPreferencesTable
            .selectAll()
            .where { UserPreferencesTable.userId eq userId }
            .singleOrNull() != null
        if (exists) return

        UserPreferencesTable.insert { row ->
            row[UserPreferencesTable.userId] = userId
        }
    }

    private fun resolveRatingAggregate(
        targetUserId: Long,
        fallbackValue: Double?,
        fallbackCount: Int?,
    ): RatingAggregate {
        val reviews = UserReviewsTable
            .selectAll()
            .where { UserReviewsTable.toUserId eq targetUserId }
            .toList()
        if (reviews.isNotEmpty()) {
            val scores = reviews.map { it[UserReviewsTable.score].toDouble() }
            return RatingAggregate(
                value = scores.average(),
                count = scores.size,
            )
        }

        val count = fallbackCount ?: 0
        return RatingAggregate(
            value = fallbackValue?.takeIf { count > 0 },
            count = count,
        )
    }

    private fun loadListingsPreview(targetUserId: Long): List<ProfileListingPreviewItem> {
        val rows = OffersTable
            .innerJoin(ProductsTable, { OffersTable.productId }, { ProductsTable.id })
            .leftJoin(OfferSourcesTable, { OffersTable.id }, { OfferSourcesTable.offerId })
            .selectAll()
            .where {
                (OffersTable.userId eq targetUserId) and publicOfferVisibilityOp()
            }
            .orderBy(OffersTable.updatedAt to SortOrder.DESC, OffersTable.id to SortOrder.DESC)
            .limit(6)
            .toList()

        return rows.map { row ->
            val sourceName = row.tryGet(OfferSourcesTable.domainName)
                ?: row.tryGet(OfferSourcesTable.sourceUrl)?.let(::extractHost)
                ?: row.tryGet(OfferSourcesTable.sourceType)
                    ?.lowercase(Locale.getDefault())
                    ?.replaceFirstChar { char -> char.titlecase(Locale.getDefault()) }

            ProfileListingPreviewItem(
                offerId = row[OffersTable.id].toString(),
                title = row.tryGet(ProductsTable.titleNorm).orEmpty(),
                priceMajor = Money(row[OffersTable.priceCents]).toMajor(),
                currency = row[OffersTable.currency],
                imageUrl = row.tryGet(OffersTable.imageUrls)?.firstOrNull()
                    ?: row.tryGet(ProductsTable.imageUrls)?.firstOrNull(),
                updatedAtMillis = row.tryGet(OffersTable.updatedAt),
                status = row[OffersTable.status],
                sourceName = sourceName,
            )
        }
    }

    private fun normalizeWebsite(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        require(withScheme.length <= 512) { "WEBSITE_TOO_LONG" }
        val uri = runCatching { URI(withScheme) }.getOrNull()
        require(uri?.host?.contains('.') == true) { "WEBSITE_INVALID" }
        return withScheme
    }

    private fun extractHost(url: String): String? =
        runCatching { URI(url).host?.removePrefix("www.") }.getOrNull()

    private fun validateSellerDeliveryZone(zone: SellerDeliveryZone) {
        val normalizedLocation = zone.location.normalized()
        require(normalizedLocation.countryCode.isNotBlank()) { "DELIVERY_ZONE_COUNTRY_REQUIRED" }
        when (zone.scope) {
            com.example.shoppingassistant.domain.profile.DeliveryAreaScope.COUNTRY -> Unit
            com.example.shoppingassistant.domain.profile.DeliveryAreaScope.REGION ->
                require(!normalizedLocation.adminArea.isNullOrBlank()) { "DELIVERY_ZONE_REGION_REQUIRED" }
            com.example.shoppingassistant.domain.profile.DeliveryAreaScope.CITY ->
                require(!normalizedLocation.locality.isNullOrBlank()) { "DELIVERY_ZONE_CITY_REQUIRED" }
            com.example.shoppingassistant.domain.profile.DeliveryAreaScope.POINT ->
                require(
                    !normalizedLocation.addressLine.isNullOrBlank() ||
                        (normalizedLocation.lat != null && normalizedLocation.lon != null),
                ) { "DELIVERY_ZONE_POINT_REQUIRED" }
        }
    }

    private fun <T> ResultRow.tryGet(column: Column<T>): T? =
        runCatching { this[column] }.getOrNull()

    private data class RatingAggregate(
        val value: Double?,
        val count: Int,
    )

    private companion object {
        private const val MAX_DELIVERY_ZONES = 64
    }
}
