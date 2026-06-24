package com.tigerworkshop.homepanel.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A configured light/switch with an optional custom display name. */
data class LightEntry(val entityId: String, val name: String = "")

/** Persisted configuration for the panel. Backed by SharedPreferences. */
class Settings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("home_panel", Context.MODE_PRIVATE)

    private val _flow = MutableStateFlow(read())
    val flow: StateFlow<Config> = _flow

    val value: Config get() = _flow.value

    fun update(transform: (Config) -> Config) {
        val next = transform(_flow.value)
        write(next)
        _flow.value = next
    }

    private fun read(): Config = Config(
        baseUrl = prefs.getString(K_URL, DEFAULT_URL) ?: DEFAULT_URL,
        token = prefs.getString(K_TOKEN, "") ?: "",
        lights = readLights(),
        tempEntity = prefs.getString(K_TEMP, "") ?: "",
        humidityEntity = prefs.getString(K_HUMIDITY, "") ?: "",
        nightEnabled = prefs.getBoolean(K_NIGHT_ENABLED, true),
        nightStartMinutes = prefs.getInt(K_NIGHT_START, 23 * 60),
        nightEndMinutes = prefs.getInt(K_NIGHT_END, 7 * 60),
        columnsLandscape = prefs.getInt(K_COLS_LAND, 3),
        columnsPortrait = prefs.getInt(K_COLS_PORT, 2),
        weatherEntity = prefs.getString(K_WEATHER, "") ?: "",
        weatherDynamicBg = prefs.getBoolean(K_WEATHER_BG, true),
        weatherShowForecast = prefs.getBoolean(K_WEATHER_FORECAST, true),
        animationsEnabled = prefs.getBoolean(K_ANIMATIONS, true),
    )

    private fun readLights(): List<LightEntry> {
        val json = prefs.getString(K_LIGHTS_JSON, null)
        if (json != null) {
            return runCatching {
                val arr = JSONArray(json)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val id = obj.optString("id")
                        if (id.isNotBlank()) add(LightEntry(id, obj.optString("name")))
                    }
                }
            }.getOrDefault(emptyList())
        }
        // Migration from the old fixed light_0..3 keys.
        return (0 until 4)
            .mapNotNull { prefs.getString("light_$it", "") }
            .filter { it.isNotBlank() }
            .map { LightEntry(it, "") }
    }

    private fun write(c: Config) {
        val arr = JSONArray()
        c.lights.filter { it.entityId.isNotBlank() }.forEach {
            arr.put(JSONObject().put("id", it.entityId.trim()).put("name", it.name.trim()))
        }
        prefs.edit().apply {
            putString(K_URL, c.baseUrl.trim().trimEnd('/'))
            putString(K_TOKEN, c.token.trim())
            putString(K_LIGHTS_JSON, arr.toString())
            putString(K_TEMP, c.tempEntity.trim())
            putString(K_HUMIDITY, c.humidityEntity.trim())
            putBoolean(K_NIGHT_ENABLED, c.nightEnabled)
            putInt(K_NIGHT_START, c.nightStartMinutes)
            putInt(K_NIGHT_END, c.nightEndMinutes)
            putInt(K_COLS_LAND, c.columnsLandscape)
            putInt(K_COLS_PORT, c.columnsPortrait)
            putString(K_WEATHER, c.weatherEntity.trim())
            putBoolean(K_WEATHER_BG, c.weatherDynamicBg)
            putBoolean(K_WEATHER_FORECAST, c.weatherShowForecast)
            putBoolean(K_ANIMATIONS, c.animationsEnabled)
        }.apply()
    }

    companion object {
        private const val DEFAULT_URL = "http://10.0.0.5:8123"
        private const val K_URL = "base_url"
        private const val K_TOKEN = "token"
        private const val K_LIGHTS_JSON = "lights_json"
        private const val K_TEMP = "temp_entity"
        private const val K_HUMIDITY = "humidity_entity"
        private const val K_NIGHT_ENABLED = "night_enabled"
        private const val K_NIGHT_START = "night_start"
        private const val K_NIGHT_END = "night_end"
        private const val K_COLS_LAND = "cols_landscape"
        private const val K_COLS_PORT = "cols_portrait"
        private const val K_WEATHER = "weather_entity"
        private const val K_WEATHER_BG = "weather_dynamic_bg"
        private const val K_WEATHER_FORECAST = "weather_show_forecast"
        private const val K_ANIMATIONS = "animations_enabled"
    }
}

data class Config(
    val baseUrl: String,
    val token: String,
    val lights: List<LightEntry>,
    val tempEntity: String,
    val humidityEntity: String,
    val nightEnabled: Boolean,
    val nightStartMinutes: Int,
    val nightEndMinutes: Int,
    val columnsLandscape: Int = 3,
    val columnsPortrait: Int = 2,
    val weatherEntity: String = "",
    val weatherDynamicBg: Boolean = true,
    val weatherShowForecast: Boolean = true,
    val animationsEnabled: Boolean = true,
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()
    val configuredLights: List<LightEntry> get() = lights.filter { it.entityId.isNotBlank() }

    /** Convert http(s) base URL to a ws(s) websocket endpoint. */
    val webSocketUrl: String
        get() = baseUrl.trim().trimEnd('/')
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") + "/api/websocket"
}

fun minutesToHhmm(mins: Int): String {
    val h = (mins / 60) % 24
    val m = mins % 60
    return "%02d:%02d".format(h, m)
}
