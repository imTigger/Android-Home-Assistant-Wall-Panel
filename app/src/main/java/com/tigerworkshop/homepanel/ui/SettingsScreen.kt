package com.tigerworkshop.homepanel.ui

import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tigerworkshop.homepanel.PanelViewModel
import com.tigerworkshop.homepanel.data.EntityState
import com.tigerworkshop.homepanel.data.LightEntry
import com.tigerworkshop.homepanel.data.minutesToHhmm
import com.tigerworkshop.homepanel.night.NightController
import com.tigerworkshop.homepanel.night.PanelDeviceAdminReceiver
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: PanelViewModel, onClose: () -> Unit) {
    val cfg by vm.config.collectAsStateLifecycleSafe()
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(cfg.baseUrl) }
    var token by remember { mutableStateOf(cfg.token) }
    var lights by remember { mutableStateOf(cfg.lights) }
    var tempEntity by remember { mutableStateOf(cfg.tempEntity) }
    var humidityEntity by remember { mutableStateOf(cfg.humidityEntity) }
    var nightEnabled by remember { mutableStateOf(cfg.nightEnabled) }
    var nightStart by remember { mutableStateOf(cfg.nightStartMinutes) }
    var nightEnd by remember { mutableStateOf(cfg.nightEndMinutes) }
    var colsLandscape by remember { mutableStateOf(cfg.columnsLandscape) }
    var colsPortrait by remember { mutableStateOf(cfg.columnsPortrait) }
    var weatherEntity by remember { mutableStateOf(cfg.weatherEntity) }
    var weatherBg by remember { mutableStateOf(cfg.weatherDynamicBg) }
    var weatherForecast by remember { mutableStateOf(cfg.weatherShowForecast) }
    var showScanner by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Int?>(null) }

    var lightOptions by remember { mutableStateOf<List<EntityState>>(emptyList()) }
    var sensorOptions by remember { mutableStateOf<List<EntityState>>(emptyList()) }
    var weatherOptions by remember { mutableStateOf<List<EntityState>>(emptyList()) }
    var loadMsg by remember { mutableStateOf<String?>(null) }

    fun persist() {
        vm.saveConfig {
            it.copy(
                baseUrl = url, token = token,
                lights = lights.filter { e -> e.entityId.isNotBlank() },
                tempEntity = tempEntity, humidityEntity = humidityEntity,
                nightEnabled = nightEnabled, nightStartMinutes = nightStart, nightEndMinutes = nightEnd,
                columnsLandscape = colsLandscape, columnsPortrait = colsPortrait,
                weatherEntity = weatherEntity, weatherDynamicBg = weatherBg,
                weatherShowForecast = weatherForecast,
            )
        }
    }

    BackHandler { if (showScanner) showScanner = false else { persist(); onClose() } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { persist(); onClose() }) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(Modifier.height(16.dp))

            SectionTitle("Home Assistant")
            PanelTextField(url, { url = it }, "Server URL (http://10.0.0.5:8123)")
            Spacer(Modifier.height(10.dp))
            PanelTextField(token, { token = it }, "Long-lived access token") {
                IconButton(onClick = { showScanner = true }) {
                    Icon(Icons.Filled.QrCodeScanner, "Scan QR", tint = Accent)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        loadMsg = "Loading…"
                        scope.launch {
                            val result = vm.discoverEntities(cfg.copy(baseUrl = url, token = token))
                            result.onSuccess { list ->
                                val tileDomains = setOf(
                                    "light", "switch", "fan", "input_boolean",
                                    "automation", "scene", "script",
                                )
                                lightOptions = list.filter { it.domain in tileDomains }.sortedBy { it.friendlyName }
                                sensorOptions = list.filter { it.domain == "sensor" }.sortedBy { it.friendlyName }
                                weatherOptions = list.filter { it.domain == "weather" }.sortedBy { it.friendlyName }
                                loadMsg = "Loaded ${lightOptions.size} tiles, ${sensorOptions.size} sensors"
                            }.onFailure { loadMsg = "Failed: ${it.message}" }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF1A1205)),
                ) { Text("Load entities", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                loadMsg?.let { Text(it, color = TextSecondary, fontSize = 14.sp) }
            }

            Spacer(Modifier.height(22.dp))
            SectionTitle("Tiles")
            lights.forEachIndexed { i, entry ->
                LightRow(
                    index = i,
                    entry = entry,
                    options = lightOptions,
                    canMoveUp = i > 0,
                    canMoveDown = i < lights.size - 1,
                    onEntity = { id ->
                        lights = lights.toMutableList().also { it[i] = it[i].copy(entityId = id) }
                    },
                    onName = { n ->
                        lights = lights.toMutableList().also { it[i] = it[i].copy(name = n) }
                    },
                    onMoveUp = {
                        if (i > 0) lights = lights.toMutableList().also { it.add(i - 1, it.removeAt(i)) }
                    },
                    onMoveDown = {
                        if (i < lights.size - 1) lights = lights.toMutableList().also { it.add(i + 1, it.removeAt(i)) }
                    },
                    onRemove = { pendingDelete = i },
                )
                Spacer(Modifier.height(12.dp))
            }
            Button(
                onClick = { lights = lights + LightEntry("", "") },
                colors = ButtonDefaults.buttonColors(containerColor = PanelSurfaceHi, contentColor = TextPrimary),
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add tile")
            }

            Spacer(Modifier.height(22.dp))
            SectionTitle("Climate")
            EntityAutoComplete("Temperature sensor", tempEntity, sensorOptions) { tempEntity = it }
            Spacer(Modifier.height(10.dp))
            EntityAutoComplete("Humidity sensor", humidityEntity, sensorOptions) { humidityEntity = it }

            Spacer(Modifier.height(22.dp))
            SectionTitle("Weather")
            EntityAutoComplete("Weather entity (e.g. Met.no)", weatherEntity, weatherOptions) { weatherEntity = it }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Dynamic weather background", color = TextPrimary, fontSize = 16.sp)
                    Text(
                        "Tint the panel background to match conditions",
                        color = TextSecondary, fontSize = 13.sp,
                    )
                }
                Switch(checked = weatherBg, onCheckedChange = { weatherBg = it })
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Show forecast", color = TextPrimary, fontSize = 16.sp)
                    Text("Display the multi-day forecast strip", color = TextSecondary, fontSize = 13.sp)
                }
                Switch(checked = weatherForecast, onCheckedChange = { weatherForecast = it })
            }

            Spacer(Modifier.height(22.dp))
            SectionTitle("Layout")
            ColumnStepper("Columns (landscape)", colsLandscape, 1..6) { colsLandscape = it }
            Spacer(Modifier.height(10.dp))
            ColumnStepper("Columns (portrait)", colsPortrait, 1..6) { colsPortrait = it }

            Spacer(Modifier.height(22.dp))
            SectionTitle("Night mode")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Turn screen off at night", color = TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Switch(checked = nightEnabled, onCheckedChange = { nightEnabled = it })
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeBox("Sleep at", nightStart, Modifier.weight(1f)) { nightStart = it }
                TimeBox("Wake at", nightEnd, Modifier.weight(1f)) { nightEnd = it }
            }
            Spacer(Modifier.height(12.dp))
            DeviceAdminRow()

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { persist(); vm.reconnect(); onClose() },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF1A1205)),
            ) { Text("Save & connect", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            Spacer(Modifier.height(40.dp))
        }

        if (showScanner) {
            QrScannerScreen(
                onResult = { scanned ->
                    token = scanned.trim()
                    showScanner = false
                },
                onClose = { showScanner = false },
            )
        }

        pendingDelete?.let { idx ->
            val entry = lights.getOrNull(idx)
            val label = entry?.name?.ifBlank { null }
                ?: lightOptions.firstOrNull { it.entityId == entry?.entityId }?.friendlyName
                ?: entry?.entityId?.ifBlank { null }
                ?: "this light"
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Remove tile?") },
                text = { Text("Remove “$label” from the panel?") },
                confirmButton = {
                    TextButton(onClick = {
                        if (idx < lights.size) {
                            lights = lights.toMutableList().also { it.removeAt(idx) }
                        }
                        pendingDelete = null
                    }) { Text("Remove", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = TextSecondary) }
                },
                containerColor = PanelSurfaceHi,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = AccentCool, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun LightRow(
    index: Int,
    entry: LightEntry,
    options: List<EntityState>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEntity: (String) -> Unit,
    onName: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val hint = options.firstOrNull { it.entityId == entry.entityId }?.friendlyName ?: ""
    Column(
        Modifier
            .fillMaxWidth()
            .background(PanelSurface, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tile ${index + 1}", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.KeyboardArrowUp, "Move up",
                    tint = if (canMoveUp) Accent else TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.KeyboardArrowDown, "Move down",
                    tint = if (canMoveDown) Accent else TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, "Remove", tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        EntityAutoComplete("Entity", entry.entityId, options, onSelect = onEntity)
        Spacer(Modifier.height(8.dp))
        PanelTextField(entry.name, onName, if (hint.isNotBlank()) "Custom name (default: $hint)" else "Custom name (optional)")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanelTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        trailingIcon = trailingIcon,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaultsPanel(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutlinedTextFieldDefaultsPanel() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = Accent,
    unfocusedBorderColor = TextSecondary,
    focusedLabelColor = Accent,
    unfocusedLabelColor = TextSecondary,
    cursorColor = Accent,
)

/** Editable, filterable entity selector (autocomplete). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntityAutoComplete(
    label: String,
    selectedId: String,
    options: List<EntityState>,
    onSelect: (String) -> Unit,
) {
    fun labelFor(id: String): String =
        options.firstOrNull { it.entityId == id }?.let { "${it.friendlyName} · ${it.entityId}" }
            ?: id

    var expanded by remember { mutableStateOf(false) }
    var query by remember(selectedId, options) { mutableStateOf(labelFor(selectedId)) }

    val filtered = remember(query, options, selectedId) {
        val current = labelFor(selectedId)
        if (query.isBlank() || query == current) options
        else options.filter {
            it.friendlyName.contains(query, ignoreCase = true) ||
                it.entityId.contains(query, ignoreCase = true)
        }
    }.take(60)

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        TextField(
            value = query,
            onValueChange = { query = it; expanded = true },
            label = { Text(label) },
            singleLine = true,
            trailingIcon = {
                if (selectedId.isNotBlank()) {
                    IconButton(onClick = { onSelect(""); query = "" }) {
                        Icon(Icons.Filled.Close, "Clear", tint = TextSecondary)
                    }
                } else {
                    Icon(Icons.Filled.ArrowDropDown, null, tint = TextSecondary)
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = PanelSurfaceHi,
                unfocusedContainerColor = PanelSurfaceHi,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedLabelColor = Accent,
                unfocusedLabelColor = TextSecondary,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, true).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (options.isEmpty()) {
                DropdownMenuItem(text = { Text("Load entities first") }, onClick = { expanded = false })
            }
            filtered.forEach { opt ->
                DropdownMenuItem(
                    text = { Text("${opt.friendlyName} · ${opt.entityId}") },
                    onClick = {
                        onSelect(opt.entityId)
                        query = "${opt.friendlyName} · ${opt.entityId}"
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ColumnStepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelSurface, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, color = TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
        IconButton(
            onClick = { if (value > range.first) onChange(value - 1) },
            enabled = value > range.first,
        ) {
            Icon(Icons.Filled.Remove, "Less", tint = if (value > range.first) Accent else TextSecondary)
        }
        Text(
            "$value",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(40.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(
            onClick = { if (value < range.last) onChange(value + 1) },
            enabled = value < range.last,
        ) {
            Icon(Icons.Filled.Add, "More", tint = if (value < range.last) Accent else TextSecondary)
        }
    }
}

@Composable
private fun TimeBox(label: String, minutes: Int, modifier: Modifier = Modifier, onSet: (Int) -> Unit) {
    val context = LocalContext.current
    Column(
        modifier
            .background(PanelSurface, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            minutesToHhmm(minutes),
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, h, m -> onSet(h * 60 + m) },
                    minutes / 60, minutes % 60, true,
                ).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = PanelSurfaceHi, contentColor = TextPrimary),
        ) { Text("Change") }
    }
}

@Composable
private fun DeviceAdminRow() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(NightController.isDeviceAdminActive(context)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        active = NightController.isDeviceAdminActive(context)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                active = NightController.isDeviceAdminActive(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelSurface, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Screen-off permission", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (active) "✓ Granted — night mode can turn the screen off"
                else "Needed so night mode can power the screen off",
                color = if (active) Color(0xFF66BB6A) else TextSecondary,
                fontSize = 13.sp,
            )
        }
        if (!active) {
            Button(
                onClick = {
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, PanelDeviceAdminReceiver.componentName(context))
                        .putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Allows Home Panel to turn the screen off at night.",
                        )
                    launcher.launch(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF1A1205)),
            ) { Text("Grant", fontWeight = FontWeight.Bold) }
        }
    }
}
