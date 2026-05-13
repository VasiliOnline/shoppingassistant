package com.example.shoppingassistant.feature.pages.common

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.abs
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class DeviceLocationSnapshot(
    val city: String?,
    val addressLine: String?,
    val countryCode: String?,
    val lat: Double?,
    val lon: Double?,
)

@SuppressLint("MissingPermission")
suspend fun resolveDeviceLocation(context: Context): DeviceLocationSnapshot {
    return withContext(Dispatchers.IO) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext DeviceLocationSnapshot(null, null, null, null, null)

        val providers = runCatching { locationManager.getProviders(true) }
            .getOrDefault(emptyList())
            .ifEmpty { listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER) }

        val currentLocation: Location? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            providers.firstNotNullOfOrNull { provider ->
                withTimeoutOrNull(2_500L) {
                    suspendCancellableCoroutine { cont ->
                        runCatching {
                            locationManager.getCurrentLocation(
                                provider,
                                null,
                                ContextCompat.getMainExecutor(context),
                            ) { location ->
                                if (cont.isActive) cont.resume(location) { _, _, _ -> }
                            }
                        }.onFailure {
                            if (cont.isActive) cont.resume(null) { _, _, _ -> }
                        }
                    }
                }
            }
        } else {
            null
        }

        val lastLocation: Location? = providers
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.takeIf { location ->
                val ageMillis = System.currentTimeMillis() - location.time
                ageMillis in 0..(2 * 60 * 1000L)
            }

        val resolvedLocation = currentLocation ?: lastLocation
            ?: return@withContext DeviceLocationSnapshot(null, null, null, null, null)

        val geocoder = Geocoder(context, Locale.getDefault())
        val address = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(
                        resolvedLocation.latitude,
                        resolvedLocation.longitude,
                        1,
                    ) { addresses ->
                        if (cont.isActive) {
                            cont.resume(addresses.firstOrNull()) { _, _, _ -> }
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(
                    resolvedLocation.latitude,
                    resolvedLocation.longitude,
                    1,
                )?.firstOrNull()
            }
        }.getOrNull()

        val city = address?.locality ?: address?.subAdminArea ?: address?.adminArea
        val street = address?.thoroughfare?.trim()?.takeIf { it.isNotBlank() }
        val house = address?.subThoroughfare?.trim()?.takeIf { it.isNotBlank() }
        val addressLine = listOfNotNull(street, house).joinToString(" ").ifBlank {
            address?.featureName?.trim()?.takeIf { it.isNotBlank() }
        }
        val countryCode = address?.countryCode
        if (isLikelyEmulatorPlaceholderLocation(resolvedLocation.latitude, resolvedLocation.longitude, city, addressLine)) {
            return@withContext DeviceLocationSnapshot(null, null, null, null, null)
        }
        DeviceLocationSnapshot(
            city = city,
            addressLine = addressLine,
            countryCode = countryCode,
            lat = resolvedLocation.latitude,
            lon = resolvedLocation.longitude,
        )
    }
}

private fun isLikelyEmulatorPlaceholderLocation(
    lat: Double,
    lon: Double,
    city: String?,
    addressLine: String?,
): Boolean {
    val isEmulator =
        Build.FINGERPRINT.startsWith("generic", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
            Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)
    if (!isEmulator) return false

    val nearGoogleplex = abs(lat - 37.4219983) <= 0.002 && abs(lon - (-122.084)) <= 0.002
    val label = listOfNotNull(city, addressLine).joinToString(" ").lowercase(Locale.ROOT)
    val hasGoogleplexText = label.contains("mountain view") || label.contains("amphitheatre")
    return nearGoogleplex && hasGoogleplexText
}
