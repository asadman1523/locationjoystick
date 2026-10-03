package com.locationjoystick.core.common.geocoding

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.NominatimSearchClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val TAG = "PhotonProvider"
private const val SEARCH_LIMIT = 5

/**
 * Keyless OSM geocoder (photon.komoot.io), reachable where Nominatim is not. Reuses the search
 * cache and >=1.1 s request spacing. Never sends `lang`: only default/de/en/fr are accepted.
 */
class PhotonProvider : GeocodingProvider {
    private val searchClient = NominatimSearchClient(::fetchSearch)

    override suspend fun search(query: String): List<GeocodeResult> = searchClient.search(query) ?: emptyList()

    override suspend fun reverse(
        lat: Double,
        lon: Double,
    ): ReverseGeocodeResult? =
        try {
            parsePhotonReverseResponse(get("${AppConstants.PhotonConstants.BASE_URL}/reverse?lat=$lat&lon=$lon&limit=1"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Reverse geocode failed", e)
            null
        }

    private suspend fun fetchSearch(query: String): List<GeocodeResult> =
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            parsePhotonSearchResponse(get("${AppConstants.PhotonConstants.BASE_URL}/api?q=$encoded&limit=$SEARCH_LIMIT"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            throw e
        }

    private suspend fun get(url: String): String =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "locationjoystick/1.0")
            conn.connectTimeout = AppConstants.PhotonConstants.CONNECT_TIMEOUT_MS
            conn.readTimeout = AppConstants.PhotonConstants.READ_TIMEOUT_MS
            try {
                conn.inputStream.bufferedReader().readText()
            } finally {
                conn.disconnect()
            }
        }
}

private fun JSONObject.field(name: String) = optString(name).takeIf { it.isNotBlank() }

/** Photon returns address parts separately; rebuild "name, street no, city, state, country" without repeats. */
private fun photonDisplayName(props: JSONObject): String {
    val street = listOfNotNull(props.field("street"), props.field("housenumber")).joinToString(" ")
    return listOfNotNull(props.field("name"), street, props.field("city"), props.field("state"), props.field("country"))
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(", ")
}

internal fun parsePhotonSearchResponse(body: String): List<GeocodeResult> {
    val features = JSONObject(body).getJSONArray("features")
    return (0 until minOf(features.length(), SEARCH_LIMIT)).mapNotNull { i ->
        try {
            val feature = features.getJSONObject(i)
            val coords = feature.getJSONObject("geometry").getJSONArray("coordinates")
            val name = photonDisplayName(feature.getJSONObject("properties"))
            if (name.isEmpty()) {
                null
            } else {
                GeocodeResult(lat = coords.getDouble(1), lon = coords.getDouble(0), displayName = name)
            }
        } catch (_: Exception) {
            null
        }
    }
}

internal fun parsePhotonReverseResponse(body: String): ReverseGeocodeResult? {
    val features = JSONObject(body).optJSONArray("features") ?: return null
    val props = features.optJSONObject(0)?.optJSONObject("properties") ?: return null
    return ReverseGeocodeResult(
        locality = props.field("city") ?: props.field("locality") ?: props.field("district"),
        region = props.field("state"),
        country = props.field("country"),
    )
}
