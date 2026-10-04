package com.locationjoystick.core.data

import android.content.Context
import com.locationjoystick.core.common.constants.AppConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A curated hot location entry. The identity key is [name] + [city] (the favorite ID is
 * derived from both): renaming either is a remove + add. [country] and [city] also drive
 * UI grouping.
 */
data class HotLocation(
    val name: String,
    val lat: Double,
    val lon: Double,
    val country: String,
    val city: String,
)

/**
 * Serves the hot-location list: the last good copy fetched from the wiki, else the bundled
 * seed. Refreshes at most every 24h (see docs/features/favorites.md).
 */
@Singleton
class HotLocationsRepository
    internal constructor(
        private val cacheFile: File,
        seedJson: () -> String,
    ) {
        @Inject
        constructor(
            @ApplicationContext context: Context,
        ) : this(
            File(context.filesDir, AppConstants.HotLocationsConstants.CACHE_FILE_NAME),
            {
                context.assets
                    .open(AppConstants.HotLocationsConstants.ASSET_FILE_NAME)
                    .bufferedReader()
                    .use { it.readText() }
            },
        )

        internal var client: OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(AppConstants.HotLocationsConstants.CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(AppConstants.HotLocationsConstants.READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .build()

        internal var url: String = AppConstants.HotLocationsConstants.URL

        private val _locations =
            MutableStateFlow(
                runCatching { parseHotLocations(cacheFile.readText()) }.getOrNull()
                    ?: runCatching { parseHotLocations(seedJson()) }.getOrNull()
                    ?: emptyList(),
            )
        val locations: StateFlow<List<HotLocation>> = _locations

        /** True only when a fresh, valid list was fetched and stored. */
        suspend fun refreshIfStale(): Boolean =
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val hasCache = cacheFile.exists()
                // ponytail: file mtime as clock; move to DataStore keys if a second consumer needs the timestamp.
                if (hasCache && now - cacheFile.lastModified() < AppConstants.HotLocationsConstants.CHECK_INTERVAL_MS) {
                    return@withContext false
                }
                val fetched =
                    runCatching {
                        val request =
                            Request
                                .Builder()
                                .url(url)
                                .header("User-Agent", AppConstants.UpdateCheckConstants.userAgent())
                                .build()
                        client.newCall(request).execute().use { resp ->
                            if (!resp.isSuccessful) return@use null
                            val body = resp.body?.string() ?: return@use null
                            parseHotLocations(body)?.let { body to it }
                        }
                    }.getOrNull()
                if (fetched == null) {
                    // A failed check also waits 24h; with no cache a retry happens next launch.
                    if (hasCache) cacheFile.setLastModified(now)
                    return@withContext false
                }
                val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
                tmp.writeText(fetched.first)
                if (!tmp.renameTo(cacheFile)) {
                    tmp.delete()
                    return@withContext false
                }
                _locations.value = fetched.second
                true
            }
    }

/** Parses the published hot-locations JSON; null unless the body is a valid, non-empty schema-1 list. */
internal fun parseHotLocations(body: String): List<HotLocation>? =
    runCatching {
        val root = JSONObject(body)
        if (root.getInt("schema") != 1) return@runCatching null
        val items = root.getJSONArray("locations")
        List(items.length()) { i ->
            val o = items.getJSONObject(i)
            HotLocation(o.getString("name"), o.getDouble("lat"), o.getDouble("lon"), o.getString("country"), o.getString("city"))
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()
