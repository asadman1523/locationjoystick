package com.locationjoystick.core.common.geocoding

import com.locationjoystick.core.common.constants.AppConstants
import kotlinx.coroutines.CancellationException

/**
 * Tries [providers] in order. A provider that throws is skipped for
 * [AppConstants.GeocodingConstants.PROVIDER_COOLDOWN_MS]; when all are cooling down, all are tried.
 * Cooldown state is in memory and shared by search and reverse.
 */
class FallbackGeocodingProvider(
    private val providers: List<GeocodingProvider>,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : GeocodingProvider {
    private val downUntil = mutableMapOf<GeocodingProvider, Long>()

    override suspend fun search(query: String): List<GeocodeResult> = attempt(emptyList()) { it.search(query) }

    override suspend fun reverse(
        lat: Double,
        lon: Double,
    ): ReverseGeocodeResult? = attempt<ReverseGeocodeResult?>(null) { it.reverse(lat, lon) }

    private suspend fun <T> attempt(
        default: T,
        call: suspend (GeocodingProvider) -> T,
    ): T {
        for (provider in candidates()) {
            try {
                return call(provider).also { markUp(provider) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                markDown(provider)
            }
        }
        return default
    }

    @Synchronized
    private fun candidates(): List<GeocodingProvider> {
        val now = nowMs()
        return providers.filter { (downUntil[it] ?: 0L) <= now }.ifEmpty { providers }
    }

    @Synchronized
    private fun markUp(provider: GeocodingProvider) {
        downUntil.remove(provider)
    }

    @Synchronized
    private fun markDown(provider: GeocodingProvider) {
        downUntil[provider] = nowMs() + AppConstants.GeocodingConstants.PROVIDER_COOLDOWN_MS
    }
}
