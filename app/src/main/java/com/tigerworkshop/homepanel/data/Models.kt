package com.tigerworkshop.homepanel.data

import org.json.JSONObject

/** A single Home Assistant entity state snapshot. */
data class EntityState(
    val entityId: String,
    val state: String,
    val attributes: JSONObject,
) {
    val domain: String get() = entityId.substringBefore('.')

    val friendlyName: String
        get() = attributes.optString("friendly_name").ifBlank { entityId }

    val isOn: Boolean get() = state.equals("on", ignoreCase = true)

    val isUnavailable: Boolean
        get() = state.equals("unavailable", ignoreCase = true) ||
            state.equals("unknown", ignoreCase = true)

    /** 0..255, or null if the light reports no brightness. */
    val brightness: Int?
        get() = if (attributes.has("brightness") && !attributes.isNull("brightness"))
            attributes.optInt("brightness") else null

    /** Whether this light supports a brightness slider. */
    val supportsBrightness: Boolean
        get() {
            val modes = attributes.optJSONArray("supported_color_modes") ?: return brightness != null
            for (i in 0 until modes.length()) {
                when (modes.optString(i)) {
                    "onoff" -> {}
                    else -> return true // brightness, color_temp, hs, xy, rgb, rgbw... all dimmable
                }
            }
            return false
        }

    val unit: String get() = attributes.optString("unit_of_measurement")

    companion object {
        fun fromJson(obj: JSONObject): EntityState? {
            val id = obj.optString("entity_id").ifBlank { return null }
            return EntityState(
                entityId = id,
                state = obj.optString("state"),
                attributes = obj.optJSONObject("attributes") ?: JSONObject(),
            )
        }
    }
}

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, AUTH_FAILED, ERROR }
