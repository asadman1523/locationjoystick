package com.locationjoystick.core.common.geocoding

/** One forward-geocoding hit in the app's own shape, independent of the provider that returned it. */
data class GeocodeResult(
    val lat: Double,
    val lon: Double,
    val displayName: String,
)

/** Reverse-geocoding answer in the app's own shape; blank parts are null. */
data class ReverseGeocodeResult(
    val locality: String?,
    val region: String?,
    val country: String?,
)

/**
 * Front for a geocoding service. Each implementation owns its URLs, headers, rate limiting and
 * JSON, and maps the response to the unified types above.
 */
interface GeocodingProvider {
    /** Returns an empty list on failure. */
    suspend fun search(query: String): List<GeocodeResult>

    /** Returns null when nothing was found or on failure. */
    suspend fun reverse(
        lat: Double,
        lon: Double,
    ): ReverseGeocodeResult?
}

/** Process-wide entry so every caller shares one provider (cache and request spacing). */
object Geocoding {
    val provider: GeocodingProvider = NominatimProvider()
}
