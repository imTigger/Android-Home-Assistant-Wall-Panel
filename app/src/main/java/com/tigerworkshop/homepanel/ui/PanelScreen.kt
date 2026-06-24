package com.tigerworkshop.homepanel.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tigerworkshop.homepanel.PanelViewModel
import com.tigerworkshop.homepanel.data.Config
import com.tigerworkshop.homepanel.data.ConnectionStatus
import com.tigerworkshop.homepanel.data.EntityState
import com.tigerworkshop.homepanel.data.ForecastEntry
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun PanelScreen(vm: PanelViewModel, onOpenSettings: () -> Unit) {
    val states by vm.states.collectAsStateLifecycleSafe()
    val status by vm.status.collectAsStateLifecycleSafe()
    val config by vm.config.collectAsStateLifecycleSafe()
    val pending by vm.pendingBrightness.collectAsStateLifecycleSafe()

    val forecast by vm.forecast.collectAsStateLifecycleSafe()

    var sheetId by remember { mutableStateOf<String?>(null) }

    // Back closes the brightness sheet if open; otherwise swallow it to keep the panel in front.
    BackHandler { if (sheetId != null) sheetId = null }

    val weather = config.weatherEntity.takeIf { it.isNotBlank() }?.let { states[it] }
    val condition = weather?.state.orEmpty()
    val isNight = condition == "clear-night" || java.time.LocalTime.now().hour.let { it < 6 || it >= 20 }
    val bgColors = if (config.weatherDynamicBg && weather != null) {
        weatherGradient(condition, isNight)
    } else {
        listOf(PanelBg, Color(0xFF0E1726))
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(bgColors))) {
            Box(Modifier.fillMaxSize().padding(20.dp)) {
                Column(Modifier.fillMaxSize()) {
                    HeaderBar(config, states, weather)
                    if (weather != null && config.weatherShowForecast && forecast.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        ForecastStrip(forecast)
                    }
                    Spacer(Modifier.height(18.dp))
                    if (!config.isConfigured) {
                        NotConfigured(onOpenSettings)
                    } else {
                        LightsGrid(config, states, pending, vm, onLongPress = { sheetId = it })
                    }
                }
            }
            // Status + settings, floating bottom-right.
            Surface(
                color = PanelSurface.copy(alpha = 0.55f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                ) {
                    StatusDot(status)
                    GearButton(onOpenSettings)
                }
            }
        }

        val id = sheetId
        if (id != null) {
            val entity = states[id]
            val name = config.lights.firstOrNull { it.entityId == id }?.name?.ifBlank { null }
                ?: entity?.friendlyName ?: id.substringAfter('.').replace('_', ' ')
            BrightnessOverlay(
                name = name,
                entity = entity,
                pendingPct = pending[id],
                onSetPct = { pct -> vm.setBrightnessPct(id, pct) },
                onSettled = { vm.clearPending(id) },
                onToggle = { on -> vm.toggleLight(id, on); if (!on) vm.clearPending(id) },
                onClose = { sheetId = null },
            )
        }
    }
}

@Composable
private fun HeaderBar(
    config: Config,
    states: Map<String, EntityState>,
    weather: EntityState?,
) {
    val temp = states[config.tempEntity]
    val humidity = states[config.humidityEntity]

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            ClockBlock(Modifier.weight(1f))
            if (weather != null) {
                WeatherNow(weather)
            }
        }
        if (temp != null || humidity != null) {
            Spacer(Modifier.height(14.dp))
            Row { ClimateChips(temp, humidity) }
        }
    }
}

@Composable
private fun WeatherNow(weather: EntityState) {
    val condition = weather.state
    val temp = weather.attributes.optDouble("temperature").takeIf { !it.isNaN() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 10.dp),
    ) {
        Column(horizontalAlignment = Alignment.End) {
            if (temp != null) {
                Text("${temp.roundToInt()}°", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Text(prettyCondition(condition), fontSize = 14.sp, color = TextSecondary)
        }
        Spacer(Modifier.width(10.dp))
        Icon(weatherIcon(condition), null, tint = weatherIconTint(condition), modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun ForecastStrip(forecast: List<ForecastEntry>) {
    Surface(color = PanelSurface.copy(alpha = 0.45f), shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            forecast.take(5).forEach { f ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(forecastDayLabel(f.datetime), fontSize = 13.sp, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    Icon(weatherIcon(f.condition), null, tint = weatherIconTint(f.condition), modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        f.tempHigh?.let { "${it.roundToInt()}°" } ?: "–",
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary,
                    )
                    f.tempLow?.let {
                        Text("${it.roundToInt()}°", fontSize = 13.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClimateChips(temp: EntityState?, humidity: EntityState?) {
    temp?.let {
        ClimateChip(Icons.Outlined.Thermostat, it.state, it.unit.ifBlank { "°" }, AccentCool)
        Spacer(Modifier.width(12.dp))
    }
    humidity?.let {
        ClimateChip(Icons.Outlined.WaterDrop, it.state, it.unit.ifBlank { "%" }, AccentCool)
        Spacer(Modifier.width(12.dp))
    }
}

@Composable
private fun GearButton(onOpenSettings: () -> Unit) {
    IconButton(onClick = onOpenSettings) {
        Icon(Icons.Filled.Settings, "Settings", tint = TextSecondary, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun ClockBlock(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }
    val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))
    Column(modifier) {
        Text(time, fontSize = 64.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, softWrap = false)
        Text(date, fontSize = 18.sp, color = TextSecondary, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun ClimateChip(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, unit: String, tint: Color) {
    Surface(color = PanelSurface.copy(alpha = 0.5f), shape = RoundedCornerShape(18.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(unit, fontSize = 16.sp, color = TextSecondary, modifier = Modifier.padding(start = 2.dp))
        }
    }
}

@Composable
private fun StatusDot(status: ConnectionStatus) {
    val color = when (status) {
        ConnectionStatus.CONNECTED -> Color(0xFF66BB6A)
        ConnectionStatus.CONNECTING -> Accent
        ConnectionStatus.AUTH_FAILED -> Color(0xFFEF5350)
        else -> Color(0xFFEF5350)
    }
    Box(
        Modifier
            .padding(horizontal = 8.dp)
            .size(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color),
    )
}

@Composable
private fun LightsGrid(
    config: Config,
    states: Map<String, EntityState>,
    pending: Map<String, Int>,
    vm: PanelViewModel,
    onLongPress: (String) -> Unit,
) {
    val entries = config.configuredLights
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val columns = (if (landscape) config.columnsLandscape else config.columnsPortrait)
        .coerceAtLeast(1)

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(entries, key = { it.entityId }) { entry ->
            val id = entry.entityId
            val entity = states[id]
            val name = entry.name.ifBlank {
                entity?.friendlyName ?: id.substringAfter('.').replace('_', ' ')
            }
            LightTile(
                displayName = name,
                entity = entity,
                pendingPct = pending[id],
                onToggle = { on -> vm.toggleLight(id, on); if (!on) vm.clearPending(id) },
                onBrightness = { pct -> vm.setBrightnessPct(id, pct) },
                onBrightnessSettled = { vm.clearPending(id) },
                onLongPress = { onLongPress(id) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LightTile(
    displayName: String,
    entity: EntityState?,
    pendingPct: Int?,
    onToggle: (Boolean) -> Unit,
    onBrightness: (Int) -> Unit,
    onBrightnessSettled: () -> Unit,
    onLongPress: () -> Unit,
) {
    val on = entity?.isOn == true
    val name = displayName
    val unavailable = entity == null || entity.isUnavailable
    val dimmable = entity != null && entity.supportsBrightness

    val bg by animateColorAsState(
        if (on) PanelSurfaceOn.copy(alpha = 0.85f) else PanelSurface.copy(alpha = 0.55f), label = "tilebg",
    )
    val iconTint by animateColorAsState(if (on) Accent else TextSecondary, label = "icon")

    val actualPct = entity?.brightness?.let { (it * 100 + 127) / 255 } ?: 100
    val shownPct = pendingPct ?: if (on) actualPct else 0
    val fraction = (shownPct / 100f).coerceIn(0f, 1f)

    val stateLabel = when {
        unavailable -> "unavailable"
        dimmable && on -> "$shownPct%"
        on -> "On"
        else -> "Off"
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .combinedClickable(
                enabled = !unavailable,
                onClick = { onToggle(!on) },
                onLongClick = if (dimmable) onLongPress else null,
            ),
    ) {
        Row(Modifier.fillMaxSize().padding(16.dp)) {
            // Left: icon + name + state (fixed positions, no shift on toggle)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Icon(
                    if (on) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                    null,
                    tint = iconTint,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (unavailable) TextSecondary else TextPrimary,
                    maxLines = 2,
                )
                Text(stateLabel, fontSize = 15.sp, color = TextSecondary)
            }

            // Right: vertical brightness bar (only when a dimmable light is on)
            if (dimmable && on) {
                Spacer(Modifier.width(16.dp))
                VerticalBrightnessSlider(
                    fraction = fraction,
                    enabled = !unavailable,
                    onFraction = { f -> onBrightness((f * 100).toInt().coerceIn(1, 100)) },
                    onSettled = onBrightnessSettled,
                    modifier = Modifier.fillMaxHeight().width(22.dp),
                )
            }
        }
    }
}

/** Full-screen brightness control shown on long-press of a dimmable light. */
@Composable
private fun BrightnessOverlay(
    name: String,
    entity: EntityState?,
    pendingPct: Int?,
    onSetPct: (Int) -> Unit,
    onSettled: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val on = entity?.isOn == true
    val actualPct = entity?.brightness?.let { (it * 100 + 127) / 255 } ?: 100
    val shownPct = pendingPct ?: if (on) actualPct else 0
    val fraction = (shownPct / 100f).coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF20B1220)) // near-opaque scrim
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Text("$shownPct%", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = if (on) Accent else TextSecondary)
            Spacer(Modifier.height(28.dp))
            VerticalBrightnessSlider(
                fraction = fraction,
                enabled = true,
                onFraction = { f -> onSetPct((f * 100).toInt().coerceIn(1, 100)) },
                onSettled = onSettled,
                modifier = Modifier.height(420.dp).width(120.dp),
                trackColor = PanelSurfaceHi,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { onToggle(!on) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (on) PanelSurfaceHi else Accent,
                    contentColor = if (on) TextPrimary else Color(0xFF1A1205),
                ),
            ) { Text(if (on) "Turn off" else "Turn on", fontWeight = FontWeight.Bold) }
        }
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Icon(Icons.Filled.Close, "Close", tint = TextSecondary, modifier = Modifier.size(32.dp))
        }
    }
}

/** A vertical brightness bar that fills from the bottom; tap or drag to set. */
@Composable
private fun VerticalBrightnessSlider(
    fraction: Float,
    enabled: Boolean,
    onFraction: (Float) -> Unit,
    onSettled: () -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = PanelBg,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .clip(shape)
            .background(trackColor)
            .then(
                if (enabled) Modifier.pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onFraction((1f - offset.y / size.height).coerceIn(0f, 1f))
                        onSettled()
                    }
                } else Modifier,
            )
            .then(
                if (enabled) Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures(onDragEnd = { onSettled() }) { change, _ ->
                        onFraction((1f - change.position.y / size.height).coerceIn(0f, 1f))
                    }
                } else Modifier,
            ),
    ) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(fraction)
                .clip(shape)
                .background(Accent),
        )
    }
}

@Composable
private fun NotConfigured(onOpenSettings: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Lightbulb, null, tint = Accent, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("Welcome to Home Panel", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text("Tap to add your Home Assistant server and lights.", fontSize = 16.sp, color = TextSecondary)
            Spacer(Modifier.height(20.dp))
            Surface(
                color = Accent,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.pointerInput(Unit) { detectTapToggle { onOpenSettings() } },
            ) {
                Text(
                    "Open settings",
                    color = Color(0xFF1A1205),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                )
            }
        }
    }
}

/** Tap detector that ignores the small drags a finger makes on a panel. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectTapToggle(onTap: () -> Unit) {
    detectTapGestures(onTap = { onTap() })
}
