package com.vitorfreitas.getyourhelp.location

import com.vitorfreitas.getyourhelp.data.Coordinates
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

// Distances and place names. Plain Kotlin (no Android classes), covered by unit tests.

/** Mean radius of the Earth in kilometres. */
private const val EARTH_RADIUS_KM = 6371.0088

/** Straight-line distance between two points in kilometres, using the haversine formula. */
fun distanceKm(from: Coordinates, to: Coordinates): Double {
    val dLat = Math.toRadians(to.latitude - from.latitude)
    val dLon = Math.toRadians(to.longitude - from.longitude)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(from.latitude)) * cos(Math.toRadians(to.latitude)) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_KM * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

/** Distance between two points, or null if either one is unknown. */
fun distanceKmOrNull(from: Coordinates?, to: Coordinates?): Double? =
    if (from == null || to == null) null else distanceKm(from, to)

/**
 * A distance as the app shows it: "Under 100 m", "400 m", "1.2 km" or "12 km".
 * Task positions are approximate (see [approximate]), so anything under 1 km is rounded to 100 m.
 */
fun formatDistance(km: Double, locale: Locale = Locale.getDefault()): String {
    val hundredsOfMetres = (km * 10).roundToInt()
    return when {
        hundredsOfMetres < 1 -> "Under 100 m"
        hundredsOfMetres < 10 -> "${hundredsOfMetres * 100} m"
        km < 9.95 -> String.format(locale, "%.1f km", km)
        else -> "${km.roundToInt()} km"
    }
}

/**
 * The point rounded to 3 decimal places, about 100 m. Tasks are public, so this is what gets
 * saved: close enough for distances and the map, but not a house.
 */
fun Coordinates.approximate(): Coordinates = Coordinates(roundTo3Decimals(latitude), roundTo3Decimals(longitude))

private fun roundTo3Decimals(value: Double): Double = Math.round(value * 1000.0) / 1000.0

/**
 * A short area name built from the parts of a geocoder address, e.g. "Ranelagh, Dublin" or
 * "Mountain View, California". Street names and house numbers are never used. Null if nothing is known.
 */
fun areaLabel(subLocality: String?, locality: String?, subAdminArea: String?, adminArea: String?): String? =
    listOf(subLocality, locality ?: subAdminArea, adminArea)
        .mapNotNull { part -> part?.trim()?.takeIf { it.isNotEmpty() } }
        .distinct()
        .take(2)
        .joinToString(", ")
        .ifEmpty { null }
