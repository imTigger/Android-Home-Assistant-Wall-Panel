package com.tigerworkshop.homepanel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tigerworkshop.homepanel.data.Config
import com.tigerworkshop.homepanel.data.EntityState
import com.tigerworkshop.homepanel.data.Settings
import com.tigerworkshop.homepanel.ha.HomeAssistantClient
import com.tigerworkshop.homepanel.night.NightController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PanelViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = Settings(app)
    private val ha = HomeAssistantClient()

    val config: StateFlow<Config> = settings.flow
    val states = ha.states
    val status = ha.status

    /** Local optimistic brightness overrides so the slider feels instant. */
    private val _pendingBrightness = MutableStateFlow<Map<String, Int>>(emptyMap())
    val pendingBrightness: StateFlow<Map<String, Int>> = _pendingBrightness

    init {
        // Reconnect whenever connection-relevant settings change.
        viewModelScope.launch {
            settings.flow
                .map { it.baseUrl to it.token }
                .distinctUntilChanged()
                .collect { ha.connect(settings.value) }
        }
        // Keep night alarms in sync with the schedule settings.
        viewModelScope.launch {
            settings.flow
                .map { Triple(it.nightEnabled, it.nightStartMinutes, it.nightEndMinutes) }
                .distinctUntilChanged()
                .collect { NightController.schedule(getApplication(), settings.value) }
        }
    }

    fun toggleLight(entityId: String, on: Boolean) = ha.toggleLight(entityId, on)

    fun setBrightnessPct(entityId: String, pct: Int) {
        _pendingBrightness.value = _pendingBrightness.value + (entityId to pct)
        ha.setLightBrightness(entityId, pct)
    }

    fun clearPending(entityId: String) {
        _pendingBrightness.value = _pendingBrightness.value - entityId
    }

    fun saveConfig(transform: (Config) -> Config) = settings.update(transform)

    suspend fun discoverEntities(cfg: Config): Result<List<EntityState>> =
        runCatching { ha.fetchAllStates(cfg) }

    fun reconnect() = ha.connect(settings.value)

    override fun onCleared() {
        ha.disconnect()
        super.onCleared()
    }
}
