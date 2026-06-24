package com.tigerworkshop.homepanel.ha

import android.util.Log
import com.tigerworkshop.homepanel.data.Config
import com.tigerworkshop.homepanel.data.ConnectionStatus
import com.tigerworkshop.homepanel.data.EntityState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Maintains a live connection to Home Assistant via the WebSocket API.
 * Exposes entity states as a StateFlow and sends light service calls.
 * Automatically reconnects on failure.
 */
class HomeAssistantClient {

    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // websocket: keep open
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _states = MutableStateFlow<Map<String, EntityState>>(emptyMap())
    val states: StateFlow<Map<String, EntityState>> = _states

    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status

    private val msgId = AtomicInteger(1)
    private var webSocket: WebSocket? = null
    @Volatile private var config: Config? = null
    @Volatile private var generation = 0 // bump to invalidate stale reconnects
    private var getStatesId = -1

    /** Connect (or reconnect) with the given config. Safe to call repeatedly. */
    fun connect(cfg: Config) {
        if (!cfg.isConfigured) {
            disconnect()
            _status.value = ConnectionStatus.DISCONNECTED
            return
        }
        generation++
        config = cfg
        openSocket(generation)
    }

    fun disconnect() {
        generation++
        webSocket?.close(1000, "client closing")
        webSocket = null
    }

    private fun openSocket(gen: Int) {
        val cfg = config ?: return
        _status.value = ConnectionStatus.CONNECTING
        val request = Request.Builder().url(cfg.webSocketUrl).build()
        webSocket = http.newWebSocket(request, Listener(gen, cfg))
    }

    private fun scheduleReconnect(gen: Int) {
        scope.launch {
            kotlinx.coroutines.delay(3000)
            if (gen == generation) openSocket(generation)
        }
    }

    private inner class Listener(val gen: Int, val cfg: Config) : WebSocketListener() {
        override fun onMessage(ws: WebSocket, text: String) {
            if (gen != generation) return
            try {
                val msg = JSONObject(text)
                when (msg.optString("type")) {
                    "auth_required" -> ws.send(
                        JSONObject()
                            .put("type", "auth")
                            .put("access_token", cfg.token)
                            .toString()
                    )
                    "auth_ok" -> {
                        _status.value = ConnectionStatus.CONNECTED
                        getStatesId = msgId.getAndIncrement()
                        ws.send(JSONObject().put("id", getStatesId).put("type", "get_states").toString())
                        ws.send(
                            JSONObject()
                                .put("id", msgId.getAndIncrement())
                                .put("type", "subscribe_events")
                                .put("event_type", "state_changed")
                                .toString()
                        )
                    }
                    "auth_invalid" -> {
                        _status.value = ConnectionStatus.AUTH_FAILED
                        ws.close(1000, "auth failed")
                    }
                    "result" -> {
                        if (msg.optInt("id") == getStatesId && msg.optBoolean("success")) {
                            ingestStates(msg.optJSONArray("result"))
                        }
                    }
                    "event" -> handleEvent(msg.optJSONObject("event"))
                }
            } catch (e: Exception) {
                Log.w(TAG, "parse error", e)
            }
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            if (gen != generation) return
            Log.w(TAG, "ws failure: ${t.message}")
            _status.value = ConnectionStatus.ERROR
            scheduleReconnect(gen)
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            if (gen != generation) return
            _status.value = ConnectionStatus.DISCONNECTED
            scheduleReconnect(gen)
        }
    }

    private fun ingestStates(arr: JSONArray?) {
        arr ?: return
        val map = HashMap<String, EntityState>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            EntityState.fromJson(obj)?.let { map[it.entityId] = it }
        }
        _states.value = map
    }

    private fun handleEvent(event: JSONObject?) {
        event ?: return
        val data = event.optJSONObject("data") ?: return
        val newState = data.optJSONObject("new_state")
        val entityId = data.optString("entity_id")
        val current = _states.value.toMutableMap()
        if (newState == null) {
            current.remove(entityId)
        } else {
            EntityState.fromJson(newState)?.let { current[it.entityId] = it }
        }
        _states.value = current
    }

    // ---- Commands ----

    fun callService(domain: String, service: String, entityId: String, serviceData: JSONObject? = null) {
        val ws = webSocket ?: return
        val msg = JSONObject()
            .put("id", msgId.getAndIncrement())
            .put("type", "call_service")
            .put("domain", domain)
            .put("service", service)
            .put("target", JSONObject().put("entity_id", entityId))
        if (serviceData != null) msg.put("service_data", serviceData)
        ws.send(msg.toString())
    }

    /** Toggle any on/off entity (light, switch, fan, input_boolean…) using its own domain. */
    fun toggleLight(entityId: String, on: Boolean) {
        val domain = entityId.substringBefore('.')
        callService(domain, if (on) "turn_on" else "turn_off", entityId)
    }

    /** brightnessPct: 1..100 */
    fun setLightBrightness(entityId: String, brightnessPct: Int) {
        callService("light", "turn_on", entityId, JSONObject().put("brightness_pct", brightnessPct.coerceIn(1, 100)))
    }

    /** One-shot REST fetch of all states, used by the settings entity picker. */
    suspend fun fetchAllStates(cfg: Config): List<EntityState> {
        val url = cfg.baseUrl.trim().trimEnd('/') + "/api/states"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${cfg.token}")
            .header("Content-Type", "application/json")
            .build()
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body?.string() ?: "[]"
                val arr = JSONArray(body)
                buildList {
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { EntityState.fromJson(it)?.let(::add) }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "HAClient"
    }
}
