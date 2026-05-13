package com.example.shoppingassistant.feature.pages.common

import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.example.shoppingassistant.domain.profile.DeliveryAddressLocation
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class DeliveryAddressSuggestion(
    val location: DeliveryAddressLocation,
    val primaryText: String,
    val secondaryText: String? = null,
)

suspend fun findDeliveryAddressSuggestions(
    geocoder: Geocoder,
    query: String,
    maxResults: Int = 6,
): List<DeliveryAddressSuggestion> = withContext(Dispatchers.IO) {
    val normalizedQuery = query.trim()
    if (normalizedQuery.length < 3) return@withContext emptyList()

    val addresses = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocationName(normalizedQuery, maxResults) { results ->
                    if (continuation.isActive) {
                        continuation.resume(results.orEmpty()) { _, _, _ -> }
                    }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocationName(normalizedQuery, maxResults).orEmpty()
        }
    }.getOrDefault(emptyList())

    addresses
        .mapNotNull(Address::toDeliveryAddressSuggestion)
        .distinctBy { suggestion ->
            listOf(
                suggestion.location.countryCode,
                suggestion.location.adminArea,
                suggestion.location.locality,
                suggestion.location.addressLine,
            ).joinToString("|")
        }
}

private fun Address.toDeliveryAddressSuggestion(): DeliveryAddressSuggestion? {
    val countryCode = countryCode
        ?.trim()
        ?.uppercase(Locale.ROOT)
        ?.takeIf { value -> value.isNotEmpty() }
        ?: return null
    val countryName = countryName?.trim()?.takeIf { value -> value.isNotEmpty() }
    val adminArea = adminArea?.trim()?.takeIf { value -> value.isNotEmpty() }
    val locality = locality?.trim()?.takeIf { value -> value.isNotEmpty() }
        ?: subAdminArea?.trim()?.takeIf { value -> value.isNotEmpty() }
    val addressLine = getAddressLine(0)
        ?.trim()
        ?.takeIf { value -> value.isNotEmpty() }
    val primaryText = listOfNotNull(
        thoroughfare?.trim()?.takeIf { it.isNotEmpty() },
        subThoroughfare?.trim()?.takeIf { it.isNotEmpty() },
    ).joinToString(" ").ifBlank {
        featureName?.trim()?.takeIf { it.isNotEmpty() }
            ?: locality
            ?: adminArea
            ?: countryName
            ?: countryCode
    }
    val secondaryText = listOfNotNull(
        locality?.takeIf { it != primaryText },
        adminArea?.takeIf { it != locality },
        countryName?.takeIf { it != locality && it != adminArea },
    ).joinToString(", ").ifBlank { null }
    val location = DeliveryAddressLocation(
        countryCode = countryCode,
        countryName = countryName,
        adminArea = adminArea,
        locality = locality,
        addressLine = addressLine,
        lat = if (hasLatitude()) latitude else null,
        lon = if (hasLongitude()) longitude else null,
        displayLabel = addressLine ?: listOfNotNull(locality, adminArea, countryName).joinToString(", "),
    )
    return DeliveryAddressSuggestion(
        location = location,
        primaryText = primaryText,
        secondaryText = secondaryText,
    )
}
