package com.locationjoystick.core.data

import android.content.Context
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.RouteType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A curated hot route. Identity is [name] + [city] (see [RouteRepository.idForRoute]). */
data class HotRoute(
    val name: String,
    val country: String,
    val city: String,
    val routeType: RouteType,
    val waypoints: List<LatLng>,
)

/**
 * Serves the hot-route list: the last good copy fetched from the wiki, else the bundled seed.
 * Shares [WikiJsonCache] with [HotLocationsRepository] (see docs/features/routes.md).
 */
@Singleton
class HotRoutesRepository
    internal constructor(
        cacheFile: File,
        seedJson: () -> String,
    ) {
        @Inject
        constructor(
            @ApplicationContext context: Context,
        ) : this(
            File(context.filesDir, AppConstants.HotRoutesConstants.CACHE_FILE_NAME),
            {
                context.assets
                    .open(AppConstants.HotRoutesConstants.ASSET_FILE_NAME)
                    .bufferedReader()
                    .use { it.readText() }
            },
        )

        private val cache = WikiJsonCache(cacheFile, seedJson, ::parseHotRoutes, AppConstants.HotRoutesConstants.URL)

        internal var url: String
            get() = cache.url
            set(v) {
                cache.url = v
            }

        val routes: StateFlow<List<HotRoute>> = cache.items

        /** True only when a fresh, valid list was fetched and stored. */
        suspend fun refreshIfStale(): Boolean = cache.refreshIfStale()
    }

/** Parses the published hot-routes JSON; null unless the body is a valid, non-empty schema-1 list of routes with >= 2 waypoints. */
internal fun parseHotRoutes(body: String): List<HotRoute>? =
    runCatching {
        val root = JSONObject(body)
        if (root.getInt("schema") != 1) return@runCatching null
        val items = root.getJSONArray("routes")
        List(items.length()) { i ->
            val o = items.getJSONObject(i)
            val pts = o.getJSONArray("waypoints")
            require(pts.length() >= 2)
            HotRoute(
                name = o.getString("name"),
                country = o.getString("country"),
                city = o.getString("city"),
                routeType = RouteType.valueOf(o.getString("type")).also { require(it != RouteType.TELEPORT) },
                waypoints =
                    List(pts.length()) { j ->
                        val p = pts.getJSONArray(j)
                        require(p.length() == 2)
                        LatLng(p.getDouble(0), p.getDouble(1))
                    },
            )
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()
