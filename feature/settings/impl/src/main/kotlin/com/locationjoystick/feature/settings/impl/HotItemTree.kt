package com.locationjoystick.feature.settings.impl

/** A single selectable item in a hot-items tree (location or route). */
data class HotItemEntry(
    val id: String,
    val name: String,
)

/**
 * Pre-computed tree structure for a set of hot items grouped by country → city.
 * Built once in [SettingsViewModel] from the live hot lists (HotLocationsRepository, HotRoutesRepository).
 */
data class HotItemTree(
    val allIds: Set<String>,
    val byCountry: Map<String, Map<String, List<HotItemEntry>>>,
) {
    companion object {
        val Empty = HotItemTree(emptySet(), emptyMap())
    }
}
