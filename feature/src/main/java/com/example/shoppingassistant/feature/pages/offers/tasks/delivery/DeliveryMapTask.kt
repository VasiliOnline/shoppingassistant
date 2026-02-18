package com.example.shoppingassistant.feature.pages.offers.tasks.delivery

import android.graphics.Paint
import androidx.annotation.Px
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.VectorProperty
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.shoppingassistant.feature.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.util.Locale
import kotlin.math.min

private data class DeliveryGeoSnapshot(
    val rawShippingCodes: Set<String>,
    val geoState: GeoState,
)

@Composable
private fun rememberDeliveryGeoState(
    shippingCountries: List<String>,
): DeliveryGeoSnapshot {
    val rawShippingCodes = remember(shippingCountries) { normalizeCountryCodes(shippingCountries) }
    var geoState by remember(shippingCountries) { mutableStateOf<GeoState>(GeoState.Loading) }
    val context = LocalContext.current

    LaunchedEffect(shippingCountries) {
        geoState = runCatching { loadGeoCountries(context) }
            .fold(
                onSuccess = { GeoState.Ready(it.first, it.second) },
                onFailure = { GeoState.Error(it) },
            )
    }

    return DeliveryGeoSnapshot(rawShippingCodes, geoState)
}

/** Превью‑карта доставки. Плашка имеет прозрачный фон, а значок раскрытия
 * находится в правом верхнем углу. Развёрнутая карта отображается в диалоге. */
@Composable
internal fun DeliveryMapPreview(
    shippingCountries: List<String>,
    modifier: Modifier = Modifier,
) {
    val geo = rememberDeliveryGeoState(shippingCountries)
    val rawShippingCodes = geo.rawShippingCodes
    val geoState = geo.geoState
    var showFull by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        when (val state = geoState) {
            GeoState.Loading -> Text(
                text = "Загружаем карту…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is GeoState.Error -> ShippingCountriesFallback(
                shippingCodes = rawShippingCodes,
                modifier = Modifier.fillMaxWidth(),
            )
            is GeoState.Ready -> {
                val shippingCodes = resolveShippingCodes(shippingCountries, state.countries, rawShippingCodes)
                if (state.countries.isEmpty()) {
                    ShippingCountriesFallback(
                        shippingCodes = shippingCodes,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    GeoJsonMap(
                        countries = state.countries,
                        shippingCodes = shippingCodes,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp),
                        allowZoom = false,
                    )
                }
            }
        }
        IconButton(
            onClick = { showFull = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.OpenInFull,
                contentDescription = "Открыть карту",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showFull) {
        DeliveryMapDialogContent(
            shippingCountries = shippingCountries,
            geo = geo,
            onDismiss = { showFull = false },
        )
    }
}

@Composable
internal fun DeliveryMapDialog(
    shippingCountries: List<String>,
    onDismiss: () -> Unit,
) {
    val geo = rememberDeliveryGeoState(shippingCountries)
    DeliveryMapDialogContent(
        shippingCountries = shippingCountries,
        geo = geo,
        onDismiss = onDismiss,
    )
}

@Composable
private fun DeliveryMapDialogContent(
    shippingCountries: List<String>,
    geo: DeliveryGeoSnapshot,
    onDismiss: () -> Unit,
) {
    val rawShippingCodes = geo.rawShippingCodes
    val geoState = geo.geoState

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Карта доставки",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                when (val state = geoState) {
                    is GeoState.Ready -> {
                        val shippingCodes = resolveShippingCodes(
                            shippingCountries,
                            state.countries,
                            rawShippingCodes,
                        )
                        GeoJsonMap(
                            countries = state.countries,
                            shippingCodes = shippingCodes,
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.66f),
                            allowZoom = true,
                        )
                    }
                    GeoState.Loading -> {
                        Text(
                            text = "Загружаем карту…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is GeoState.Error -> {
                        ShippingCountriesFallback(
                            shippingCodes = rawShippingCodes,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
//
@Composable
private fun GeoJsonMap(
    countries: List<GeoCountry>,
    shippingCodes: Set<String>,
    modifier: Modifier = Modifier,
    allowZoom: Boolean = false,
) {
    val padding = 12.dp
    val paddingPx = with(LocalDensity.current) { padding.toPx() }
    val oceanBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface.copy(alpha = 0.08f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
        ),
    )
    val landFill = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val outline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val shippingFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    val labelColor = MaterialTheme.colorScheme.onSurface

    var scale by remember(shippingCodes, allowZoom) { mutableStateOf(if (allowZoom) 1.4f else 1.1f) }
    var pan by remember(shippingCodes, allowZoom) { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    val allPoints = remember(countries) { countries.flatMap { country -> country.polygons.flatten() } }
    if (allPoints.isEmpty()) return

    val minLon = allPoints.minOf { it.first }
    val maxLon = allPoints.maxOf { it.first }
    val minLat = allPoints.minOf { it.second }
    val maxLat = allPoints.maxOf { it.second }

    val lonRange = (maxLon - minLon).takeIf { it != 0.0 } ?: return
    val latRange = (maxLat - minLat).takeIf { it != 0.0 } ?: return

    fun baseScale(size: Size): Float {
        val availableWidth = (size.width - paddingPx * 2).coerceAtLeast(1f)
        val availableHeight = (size.height - paddingPx * 2).coerceAtLeast(1f)
        return min(
            availableWidth / lonRange.toFloat(),
            availableHeight / latRange.toFloat(),
        )
    }

    LaunchedEffect(canvasSize, countries, shippingCodes, allowZoom) {
        if (canvasSize.width == 0f || canvasSize.height == 0f) return@LaunchedEffect
        val worldScale = baseScale(canvasSize)
        val shippingPoints = countries.filter { shippingCodes.contains(it.code) }.flatMap { it.polygons.flatten() }
        if (shippingPoints.isNotEmpty()) {
            val shipMinLon = shippingPoints.minOf { it.first }
            val shipMaxLon = shippingPoints.maxOf { it.first }
            val shipMinLat = shippingPoints.minOf { it.second }
            val shipMaxLat = shippingPoints.maxOf { it.second }
            val shipLonRange = (shipMaxLon - shipMinLon).takeIf { it != 0.0 } ?: lonRange
            val shipLatRange = (shipMaxLat - shipMinLat).takeIf { it != 0.0 } ?: latRange
            val desiredScalePx = min(
                (canvasSize.width - paddingPx * 2) / shipLonRange.toFloat(),
                (canvasSize.height - paddingPx * 2) / shipLatRange.toFloat(),
            ) * 0.9f
            val desiredScale = (desiredScalePx / worldScale).coerceIn(1.1f, 6f)
            val baseWithZoom = worldScale * desiredScale
            val worldLonCenter = minLon + lonRange / 2.0
            val worldLatCenter = minLat + latRange / 2.0
            val shipLonCenter = shipMinLon + shipLonRange / 2.0
            val shipLatCenter = shipMinLat + shipLatRange / 2.0
            scale = desiredScale
            pan = Offset(
                ((worldLonCenter - shipLonCenter) * baseWithZoom).toFloat(),
                ((shipLatCenter - worldLatCenter) * baseWithZoom).toFloat(),
            )
        } else {
            scale = if (allowZoom) 1.2f else 1.0f
            pan = Offset.Zero
        }
    }
    fun clampPan(currentPan: Offset, baseScale: Float, size: Size): Offset {
        val contentWidth = (lonRange * baseScale).toFloat()
        val contentHeight = (latRange * baseScale).toFloat()
        val center = Offset(size.width / 2f, size.height / 2f)
        val halfWidth = contentWidth / 2f
        val halfHeight = contentHeight / 2f

        val overscroll = 12f
        val minPanX = paddingPx - (center.x - halfWidth) - overscroll
        val maxPanX = size.width - paddingPx - (center.x + halfWidth) + overscroll
        val minPanY = paddingPx - (center.y - halfHeight) - overscroll
        val maxPanY = size.height - paddingPx - (center.y + halfHeight) + overscroll

        // Нормализуем диапазон, чтобы min ≤ max; если нет — меняем местами.
        val safeMinPanX = kotlin.math.min(minPanX, maxPanX)
        val safeMaxPanX = kotlin.math.max(minPanX, maxPanX)
        val safeMinPanY = kotlin.math.min(minPanY, maxPanY)
        val safeMaxPanY = kotlin.math.max(minPanY, maxPanY)

        return Offset(
            x = currentPan.x.coerceIn(safeMinPanX, safeMaxPanX),
            y = currentPan.y.coerceIn(safeMinPanY, safeMaxPanY),
        )
    }


    val gestureModifier = if (allowZoom) {
        Modifier.pointerInput(shippingCodes, canvasSize) {
            detectTransformGestures { _, panChange, zoomChange, _ ->
                val worldScale = baseScale(canvasSize)
                val newScale = (scale * zoomChange).coerceIn(1f, 6f)
                val appliedPanChange = if (newScale > 1.05f) panChange else Offset.Zero
                val baseWithZoom = worldScale * newScale
                val clamped = clampPan(pan + appliedPanChange, baseWithZoom, canvasSize)
                scale = newScale
                pan = clamped
            }
        }
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier
            .aspectRatio(1.9f)
            .clip(RoundedCornerShape(12.dp))
            .background(brush = oceanBrush)
            .onSizeChanged { canvasSize = it.toSize() }
            .then(gestureModifier),
    ) {
        val base = baseScale(size)
        val baseScaleZoomed = base * scale
        val centerOffset = Offset(size.width / 2f, size.height / 2f)
        val appliedPan = clampPan(pan, baseScaleZoomed, size)

        fun toOffset(point: Pair<Double, Double>): Offset {
            val (lon, lat) = point
            val x = (lon - minLon - lonRange / 2) * baseScaleZoomed + centerOffset.x + appliedPan.x
            val y = (maxLat - lat - latRange / 2) * baseScaleZoomed + centerOffset.y + appliedPan.y
            return Offset(x.toFloat(), y.toFloat())
        }

        countries.forEach { country ->
            val isShipping = shippingCodes.contains(country.code)
            val fillColor = if (isShipping) shippingFill else landFill
            val path = Path()
            country.polygons.forEach { polygon ->
                if (polygon.isEmpty()) return@forEach
                val first = toOffset(polygon.first())
                path.moveTo(first.x, first.y)
                polygon.drop(1).forEach { coord ->
                    val point = toOffset(coord)
                    path.lineTo(point.x, point.y)
                }
                path.close()
            }
            drawPath(path = path, color = fillColor, style = Fill)
            drawPath(path = path, color = outline, style = Stroke(width = 1.dp.toPx()))
        }

        if (allowZoom) {
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    color = labelColor.toArgb()
                    textSize = 12.dp.toPx()
                    isAntiAlias = true
                }
                countries.filter { shippingCodes.contains(it.code) }.forEach { country ->
                    val center = toOffset(country.centroid)
                    canvas.nativeCanvas.drawText(
                        country.name,
                        center.x,
                        center.y,
                        paint,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShippingCountriesFallback(
    shippingCodes: Set<String>,
    modifier: Modifier = Modifier,
) {
    val text = if (shippingCodes.isEmpty()) {
        "Нет данных о доставке"
    } else {
        "Доставка в: " + shippingCodes.joinToString(", ") { countryNameByCode(it) }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
        modifier = modifier,
    )
}

private sealed interface GeoState {
    data object Loading : GeoState
    data class Ready(val countries: List<GeoCountry>, val debug: String) : GeoState
    data class Error(val throwable: Throwable) : GeoState
}

private data class GeoCountry(
    val code: String,
    val name: String,
    val polygons: List<List<Pair<Double, Double>>>,
    val centroid: Pair<Double, Double>,
)

private fun normalizeCountryCodes(raw: List<String>): Set<String> {
    return raw.mapNotNull { item ->
        item.trim()
            .takeIf { it.length >= 2 }
            ?.take(2)
            ?.uppercase(Locale.US)
    }.toSet()
}

private fun resolveShippingCodes(
    raw: List<String>,
    countries: List<GeoCountry>,
    normalized: Set<String>,
): Set<String> {
    if (raw.isEmpty()) return normalized
    val lookup = countries.associateBy { it.code }
    val nameMap = countries.associateBy { it.name.lowercase(Locale.US) }
    val displayMap = countries.associateBy { countryNameByCode(it.code).lowercase(Locale.US) }

    val resolvedFromNames = raw.mapNotNull { item ->
        val trimmed = item.trim()
        val norm = trimmed.takeIf { it.length >= 2 }?.take(2)?.uppercase(Locale.US)
        val byCode = norm?.let { lookup[it] }?.code
        if (byCode != null) return@mapNotNull byCode

        val lower = trimmed.lowercase(Locale.US)
        nameMap[lower]?.code
            ?: displayMap[lower]?.code
    }
    return (normalized + resolvedFromNames).toSet()
}

private suspend fun loadGeoCountries(context: android.content.Context): Pair<List<GeoCountry>, String> {
    cachedGeoCountries?.let { return it to "cache=${it.size}" }
    val debug = StringBuilder()

    val raw = withContext(Dispatchers.IO) { readFromRaw(context) }
    if (raw != null) {
        debug.append("raw=${raw.size} ")
        if (raw.isNotEmpty()) {
            cachedGeoCountries = raw
            return raw to debug.toString()
        }
    } else {
        debug.append("raw=null ")
    }

    val net = withContext(Dispatchers.IO) { runCatching { fetchFromNetwork() }.getOrElse { emptyList() } }
    if (net.isNotEmpty()) {
        debug.append("net=${net.size}")
        cachedGeoCountries = net
        return net to debug.toString()
    } else {
        debug.append("net=0 ")
    }

    val synthetic = syntheticWorldGeo()
    debug.append("fallback=${synthetic.size}")
    cachedGeoCountries = synthetic
    return synthetic to debug.toString()
}

private fun readFromRaw(context: android.content.Context): List<GeoCountry>? {
    return runCatching {
        context.resources.openRawResource(R.raw.world_countries)
            .use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            }
    }.mapCatching { json -> parseGeoJson(json) }
        .getOrNull()
}

private fun fetchFromNetwork(): List<GeoCountry> {
    val json = URL(WORLD_GEOJSON_URL).openStream().bufferedReader().use { it.readText() }
    return parseGeoJson(json)
}

private fun parseGeoJson(json: String): List<GeoCountry> {
    val root = JSONObject(json)
    val features = root.optJSONArray("features") ?: return emptyList()
    val result = mutableListOf<GeoCountry>()
    for (i in 0 until features.length()) {
        val feature = features.optJSONObject(i) ?: continue
        val properties = feature.optJSONObject("properties")
        val id = feature.optString("id").takeIf { it.isNotBlank() }
        val iso = properties?.optString("ISO_A2")?.takeIf { it.isNotBlank() }
            ?: id?.takeIf { it.isNotBlank() }
            ?: properties?.optString("name")?.takeIf { it.isNotBlank() }
            ?: continue
        val isoUp = iso.uppercase(Locale.US)
        val name = properties?.optString("ADMIN")
            ?.takeIf { it.isNotBlank() }
            ?: properties?.optString("name")
            ?.takeIf { it.isNotBlank() }
            ?: isoUp

        val geometry = feature.optJSONObject("geometry") ?: continue
        val type = geometry.optString("type")
        val coords = geometry.opt("coordinates") ?: continue

        val polygons = when (type) {
            "Polygon" -> parsePolygon(coords)
            "MultiPolygon" -> parseMultiPolygon(coords)
            else -> emptyList()
        }.filter { it.size >= 3 }

        if (polygons.isEmpty()) continue
        val centroid = computeCentroid(polygons)
        result += GeoCountry(
            code = isoUp,
            name = name,
            polygons = polygons,
            centroid = centroid,
        )
    }
    return result
}

private fun parsePolygon(raw: Any): List<List<Pair<Double, Double>>> {
    val array = raw as? JSONArray ?: return emptyList()
    val rings = mutableListOf<List<Pair<Double, Double>>>()
    for (i in 0 until array.length()) {
        val ringArray = array.optJSONArray(i) ?: continue
        val ring = mutableListOf<Pair<Double, Double>>()
        for (j in 0 until ringArray.length()) {
            val point = ringArray.optJSONArray(j) ?: continue
            ring += point.optDouble(0) to point.optDouble(1)
        }
        if (ring.size >= 3) rings += ring
    }
    return rings
}

private fun parseMultiPolygon(raw: Any): List<List<Pair<Double, Double>>> {
    val array = raw as? JSONArray ?: return emptyList()
    val rings = mutableListOf<List<Pair<Double, Double>>>()
    for (i in 0 until array.length()) {
        val poly = array.optJSONArray(i) ?: continue
        for (j in 0 until poly.length()) {
            val ringArray = poly.optJSONArray(j) ?: continue
            val ring = mutableListOf<Pair<Double, Double>>()
            for (k in 0 until ringArray.length()) {
                val point = ringArray.optJSONArray(k) ?: continue
                ring += point.optDouble(0) to point.optDouble(1)
            }
            if (ring.size >= 3) rings += ring
        }
    }
    return rings
}

private fun computeCentroid(polygons: List<List<Pair<Double, Double>>>): Pair<Double, Double> {
    var count = 0
    var lonSum = 0.0
    var latSum = 0.0
    polygons.forEach { poly ->
        poly.forEach { (lon, lat) ->
            lonSum += lon
            latSum += lat
            count++
        }
    }
    return if (count == 0) 0.0 to 0.0 else (lonSum / count) to (latSum / count)
}

private fun countryNameByCode(code: String): String {
    return try {
        Locale("", code).displayCountry.takeIf { it.isNotBlank() } ?: code
    } catch (_: Exception) {
        code
    }
}

private const val WORLD_GEOJSON_URL =
    "https://raw.githubusercontent.com/johan/world.geo.json/master/countries.geo.json"

@Volatile
private var cachedGeoCountries: List<GeoCountry>? = null

private fun syntheticWorldGeo(): List<GeoCountry> {
    fun box(code: String, name: String, lon1: Double, lon2: Double, lat1: Double, lat2: Double): GeoCountry {
        val poly = listOf(
            lon1 to lat1,
            lon2 to lat1,
            lon2 to lat2,
            lon1 to lat2,
            lon1 to lat1,
        )
        val centroid = ((lon1 + lon2) / 2.0) to ((lat1 + lat2) / 2.0)
        return GeoCountry(code, name, listOf(poly), centroid)
    }
    return listOf(
        box("NA", "North America", -170.0, -50.0, 5.0, 72.0),
        box("SA", "South America", -82.0, -35.0, -55.0, 12.0),
        box("EU", "Europe", -25.0, 40.0, 35.0, 72.0),
        box("AF", "Africa", -20.0, 55.0, -35.0, 37.0),
        box("AS", "Asia", 40.0, 180.0, -10.0, 80.0),
        box("OC", "Oceania", 110.0, 180.0, -50.0, 0.0),
        box("AN", "Antarctica", -180.0, 180.0, -90.0, -60.0),
        box("RU", "Russia", 30.0, 180.0, 45.0, 80.0),
        box("BY", "Belarus", 23.0, 33.0, 51.0, 57.0),
        box("KZ", "Kazakhstan", 46.0, 88.0, 41.0, 55.0),
    )
}
