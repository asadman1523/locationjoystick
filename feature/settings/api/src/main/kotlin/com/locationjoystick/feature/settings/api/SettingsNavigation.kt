package com.locationjoystick.feature.settings.api

import androidx.navigation.NavController
import androidx.navigation.NavOptions

const val SETTINGS_ROUTE = "settings"

/** Settings opened straight on Menus > Capture (the Capture screen sends users here until setup is done). */
const val SETTINGS_CAPTURE_ROUTE = "settings/capture"

fun NavController.navigateToSettings(navOptions: NavOptions? = null) {
    navigate(SETTINGS_ROUTE, navOptions)
}
