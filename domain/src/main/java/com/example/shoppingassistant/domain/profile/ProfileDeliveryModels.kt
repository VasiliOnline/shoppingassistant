package com.example.shoppingassistant.domain.profile

import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
enum class DeliveryAreaScope {
    COUNTRY,
    REGION,
    CITY,
    POINT,
}

@Serializable
data class DeliveryAddressLocation(
    val countryCode: String,
    val countryName: String? = null,
    val adminArea: String? = null,
    val locality: String? = null,
    val addressLine: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val displayLabel: String? = null,
)

@Serializable
data class SellerDeliveryZone(
    val id: String,
    val scope: DeliveryAreaScope,
    val location: DeliveryAddressLocation,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

@Serializable
data class BuyerDeliveryAddress(
    val id: String,
    val label: String,
    val location: DeliveryAddressLocation,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

fun ProfileSettings.activeDeliveryAddress(): BuyerDeliveryAddress? {
    val addresses = deliveryAddresses.normalizedDeliveryAddresses()
    if (addresses.isEmpty()) return null
    val activeId = activeDeliveryAddressId
        ?.trim()
        ?.takeIf { value -> value.isNotEmpty() }
    return addresses.firstOrNull { address -> address.id == activeId } ?: addresses.first()
}

fun ProfileSettings.withNormalizedDeliveryAddresses(): ProfileSettings {
    val normalizedAddresses = deliveryAddresses.normalizedDeliveryAddresses()
    val normalizedActiveId = normalizedAddresses
        .firstOrNull { address -> address.id == activeDeliveryAddressId }
        ?.id
        ?: normalizedAddresses.firstOrNull()?.id
    return copy(
        deliveryAddresses = normalizedAddresses,
        activeDeliveryAddressId = normalizedActiveId,
    )
}

fun List<SellerDeliveryZone>.normalizedDeliveryZones(): List<SellerDeliveryZone> =
    asSequence()
        .map(SellerDeliveryZone::normalized)
        .filter { zone -> zone.id.isNotBlank() && zone.location.countryCode.isNotBlank() }
        .distinctBy { zone -> zone.id }
        .toList()

fun List<BuyerDeliveryAddress>.normalizedDeliveryAddresses(): List<BuyerDeliveryAddress> =
    asSequence()
        .map { address ->
            address.copy(
                id = address.id.trim(),
                label = address.label.trim(),
                location = address.location.normalized(),
            )
        }
        .filter { address -> address.id.isNotBlank() && address.label.isNotBlank() && address.location.countryCode.isNotBlank() }
        .distinctBy { address -> address.id }
        .toList()

fun DeliveryAddressLocation.normalized(): DeliveryAddressLocation =
    copy(
        countryCode = countryCode.trim().uppercase(Locale.ROOT),
        countryName = countryName?.trim()?.takeIf { it.isNotEmpty() },
        adminArea = adminArea?.trim()?.takeIf { it.isNotEmpty() },
        locality = locality?.trim()?.takeIf { it.isNotEmpty() },
        addressLine = addressLine?.trim()?.takeIf { it.isNotEmpty() },
        displayLabel = displayLabel?.trim()?.takeIf { it.isNotEmpty() },
    )

fun DeliveryAddressLocation.bestLabel(): String =
    displayLabel?.trim()?.takeIf { it.isNotEmpty() }
        ?: listOfNotNull(
            addressLine?.trim()?.takeIf { it.isNotEmpty() },
            locality?.trim()?.takeIf { it.isNotEmpty() },
            adminArea?.trim()?.takeIf { it.isNotEmpty() },
            countryName?.trim()?.takeIf { it.isNotEmpty() },
            countryCode.trim().takeIf { it.isNotEmpty() },
        ).joinToString(", ")

fun SellerDeliveryZone.displayLabel(): String {
    val location = location.normalized()
    return when (scope) {
        DeliveryAreaScope.COUNTRY -> location.countryName ?: location.countryCode
        DeliveryAreaScope.REGION -> listOfNotNull(
            location.adminArea,
            location.countryName ?: location.countryCode,
        ).joinToString(", ")
        DeliveryAreaScope.CITY -> listOfNotNull(
            location.locality ?: location.adminArea,
            location.adminArea?.takeIf { area -> area != location.locality },
            location.countryName ?: location.countryCode,
        ).joinToString(", ")
        DeliveryAreaScope.POINT -> location.bestLabel()
    }
}

fun deriveShippingCountriesFromDeliveryZones(zones: List<SellerDeliveryZone>): List<String> =
    zones.asSequence()
        .map { zone -> zone.location.countryCode.trim().uppercase(Locale.ROOT) }
        .filter { code -> code.isNotEmpty() }
        .distinct()
        .toList()

fun legacyShippingCountriesToDeliveryZones(tokens: List<String>?): List<SellerDeliveryZone> =
    tokens.orEmpty()
        .asSequence()
        .mapNotNull(::parseLegacyShippingCountryCode)
        .distinct()
        .map { code ->
            SellerDeliveryZone(
                id = "legacy-country-$code",
                scope = DeliveryAreaScope.COUNTRY,
                location = DeliveryAddressLocation(
                    countryCode = code,
                    displayLabel = Locale("", code).getDisplayCountry(Locale.getDefault()).ifBlank { code },
                ),
            )
        }
        .toList()

fun hasDeliveryZoneConflict(
    existing: List<SellerDeliveryZone>,
    candidate: SellerDeliveryZone,
): Boolean {
    val normalizedCandidate = candidate.normalized()
    return existing
        .asSequence()
        .map(SellerDeliveryZone::normalized)
        .any { zone -> zone.id != normalizedCandidate.id && zone.overlaps(normalizedCandidate) }
}

fun SellerDeliveryZone.overlaps(other: SellerDeliveryZone): Boolean {
    val left = normalized()
    val right = other.normalized()
    if (left.location.countryCode != right.location.countryCode) return false

    if (left.scope == DeliveryAreaScope.COUNTRY || right.scope == DeliveryAreaScope.COUNTRY) {
        return true
    }

    val leftRegion = left.location.adminArea.normalizedZoneToken()
    val rightRegion = right.location.adminArea.normalizedZoneToken()
    val leftCity = left.location.locality.normalizedZoneToken()
    val rightCity = right.location.locality.normalizedZoneToken()
    val leftPoint = left.location.addressLine.normalizedZoneToken()
    val rightPoint = right.location.addressLine.normalizedZoneToken()

    return when {
        left.scope == DeliveryAreaScope.REGION && right.scope == DeliveryAreaScope.REGION ->
            leftRegion != null && leftRegion == rightRegion

        left.scope == DeliveryAreaScope.REGION || right.scope == DeliveryAreaScope.REGION -> {
            val region = leftRegion ?: rightRegion
            region != null && region == leftRegion && region == rightRegion
        }

        left.scope == DeliveryAreaScope.CITY && right.scope == DeliveryAreaScope.CITY ->
            leftCity != null && leftCity == rightCity

        left.scope == DeliveryAreaScope.CITY || right.scope == DeliveryAreaScope.CITY -> {
            val city = leftCity ?: rightCity
            city != null && city == leftCity && city == rightCity
        }

        else -> {
            when {
                leftPoint != null && rightPoint != null && leftPoint == rightPoint -> true
                left.location.lat != null &&
                    left.location.lon != null &&
                    right.location.lat != null &&
                    right.location.lon != null ->
                    approximatelySamePoint(
                        leftLat = left.location.lat,
                        leftLon = left.location.lon,
                        rightLat = right.location.lat,
                        rightLon = right.location.lon,
                    )
                else -> false
            }
        }
    }
}

fun SellerDeliveryZone.normalized(): SellerDeliveryZone =
    copy(
        id = id.trim(),
        location = location.normalized(),
    )

private fun String?.normalizedZoneToken(): String? =
    this?.trim()?.lowercase(Locale.ROOT)?.takeIf { value -> value.isNotEmpty() }

private fun parseLegacyShippingCountryCode(rawToken: String): String? {
    val normalizedToken = rawToken
        .trim()
        .lowercase(Locale.ROOT)
        .takeIf { token -> token.isNotEmpty() }
        ?: return null
    if (normalizedToken in GLOBAL_DELIVERABILITY_TOKENS) return null

    val directCode = rawToken.trim().uppercase(Locale.ROOT)
    if (directCode.length == 2 && directCode.all(Char::isLetter)) {
        return directCode
    }

    return Locale.getISOCountries()
        .firstOrNull { code ->
            val locale = Locale("", code)
            normalizedToken == locale.getDisplayCountry(Locale.ENGLISH).trim().lowercase(Locale.ROOT) ||
                normalizedToken == locale.getDisplayCountry(Locale("ru", "RU")).trim().lowercase(Locale.ROOT)
        }
}

private val GLOBAL_DELIVERABILITY_TOKENS = setOf(
    "world",
    "worldwide",
    "global",
    "all",
    "any",
    "весь мир",
    "все страны",
    "любой",
    "везде",
)

private fun approximatelySamePoint(
    leftLat: Double,
    leftLon: Double,
    rightLat: Double,
    rightLon: Double,
): Boolean {
    val latDiff = kotlin.math.abs(leftLat - rightLat)
    val lonDiff = kotlin.math.abs(leftLon - rightLon)
    return latDiff <= 0.001 && lonDiff <= 0.001
}
