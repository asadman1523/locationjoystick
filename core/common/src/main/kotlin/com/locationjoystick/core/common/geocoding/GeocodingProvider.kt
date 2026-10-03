package com.locationjoystick.core.common.geocoding

import com.locationjoystick.core.model.GeocodingProviderId

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
    /** Throws on failure; an empty list means nothing found. */
    suspend fun search(query: String): List<GeocodeResult>

    /** Throws on failure; null means nothing found. */
    suspend fun reverse(
        lat: Double,
        lon: Double,
    ): ReverseGeocodeResult?
}

/** Process-wide entry so every caller shares one provider chain (cache, request spacing, cooldown). */
object Geocoding {
    private val nominatim = NominatimProvider()
    private val photon = PhotonProvider()
    private val ids =
        mapOf<GeocodingProvider, GeocodingProviderId>(
            nominatim to GeocodingProviderId.NOMINATIM,
            photon to GeocodingProviderId.PHOTON,
        )

    /** Providers switched off in Settings; kept current by `LjApplication`. */
    @Volatile
    var disabled: Set<GeocodingProviderId> = emptySet()

    val provider: GeocodingProvider =
        FallbackGeocodingProvider(listOf(nominatim, photon), enabled = { ids[it] !in disabled })
}
