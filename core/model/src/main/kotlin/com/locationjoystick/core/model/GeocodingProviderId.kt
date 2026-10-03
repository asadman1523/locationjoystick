package com.locationjoystick.core.model

/** Geocoding services the user can switch on or off in Settings. */
enum class GeocodingProviderId {
    NOMINATIM,
    PHOTON,
    ;

    companion object {
        /** Lenient parse for persisted/imported values — unknown names yield null. */
        fun fromName(name: String?): GeocodingProviderId? = entries.firstOrNull { it.name == name }
    }
}
