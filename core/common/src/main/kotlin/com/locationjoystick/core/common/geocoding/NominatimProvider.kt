package com.locationjoystick.core.common.geocoding

import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.util.NominatimSearchClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val TAG = "NominatimProvider"
private const val SEARCH_LIMIT = 5

class NominatimProvider : GeocodingProvider {
    private val searchClient = NominatimSearchClient(::fetchSearch)

    override suspend fun search(query: String): List<GeocodeResult> =
        searchClient.search(query) ?: throw IOException("Nominatim search failed")

    override suspend fun reverse(
        lat: Double,
        lon: Double,
    ): ReverseGeocodeResult? = parseReverseResponse(get("${AppConstants.NominatimConstants.REVERSE_URL}?lat=$lat&lon=$lon&format=json"))

    private suspend fun fetchSearch(query: String): List<GeocodeResult> =
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            parseSearchResponse(get("${AppConstants.NominatimConstants.SEARCH_URL}?q=$encoded&format=json&limit=$SEARCH_LIMIT"))
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
            conn.connectTimeout = AppConstants.NominatimConstants.CONNECT_TIMEOUT_MS
            conn.readTimeout = AppConstants.NominatimConstants.READ_TIMEOUT_MS
            try {
                conn.inputStream.bufferedReader().readText()
            } finally {
                conn.disconnect()
            }
        }
}

internal fun parseSearchResponse(body: String): List<GeocodeResult> {
    val array = JSONArray(body)
    return (0 until minOf(array.length(), SEARCH_LIMIT)).mapNotNull { i ->
        try {
            val obj = array.getJSONObject(i)
            GeocodeResult(
                lat = obj.getDouble("lat"),
                lon = obj.getDouble("lon"),
                displayName = obj.getString("display_name"),
            )
        } catch (_: Exception) {
            null
        }
    }
}

internal fun parseReverseResponse(body: String): ReverseGeocodeResult? {
    val address = JSONObject(body).optJSONObject("address") ?: return null

    fun field(name: String) = address.optString(name).takeIf { it.isNotBlank() }
    return ReverseGeocodeResult(
        locality = field("city") ?: field("town") ?: field("village") ?: field("municipality"),
        region = field("state"),
        country = field("country"),
    )
}
