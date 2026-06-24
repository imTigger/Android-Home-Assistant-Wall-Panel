package com.tigerworkshop.homepanel.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.LocalDate
import java.time.OffsetDateTime

/** Map a Home Assistant weather condition to an icon. */
fun weatherIcon(condition: String): ImageVector = when (condition) {
    "sunny" -> Icons.Filled.WbSunny
    "clear-night" -> Icons.Filled.NightsStay
    "partlycloudy" -> Icons.Filled.WbCloudy
    "cloudy" -> Icons.Filled.Cloud
    "rainy" -> Icons.Filled.Grain
    "pouring", "snowy-rainy" -> Icons.Filled.Umbrella
    "lightning", "lightning-rainy" -> Icons.Filled.Thunderstorm
    "snowy", "hail" -> Icons.Filled.AcUnit
    "fog" -> Icons.Filled.BlurOn
    "windy", "windy-variant" -> Icons.Filled.Air
    else -> Icons.Filled.Cloud
}

/** A fitting tint for a weather icon (suns yellow, rain blue, snow icy, …). */
fun weatherIconTint(condition: String): Color = when (condition) {
    "sunny" -> Color(0xFFFFC04D)
    "clear-night" -> Color(0xFFC2CCE6)
    "partlycloudy" -> Color(0xFFFFD27A)
    "cloudy", "fog" -> Color(0xFFB8C2D0)
    "rainy", "pouring", "lightning-rainy", "snowy-rainy" -> Color(0xFF6FB7F0)
    "lightning" -> Color(0xFFFFD24D)
    "snowy", "hail" -> Color(0xFFCFE4FF)
    "windy", "windy-variant" -> Color(0xFFAEC4D8)
    else -> Color(0xFFB8C2D0)
}

/** A readable label for a condition. */
fun prettyCondition(condition: String): String = when (condition) {
    "clear-night" -> "Clear"
    "partlycloudy" -> "Partly cloudy"
    "lightning" -> "Thunderstorms"
    "lightning-rainy" -> "Thunderstorms"
    "snowy-rainy" -> "Sleet"
    "windy-variant" -> "Windy"
    "pouring" -> "Heavy rain"
    "" -> ""
    else -> condition.replace('-', ' ').replaceFirstChar { it.uppercase() }
}

/** A vertical gradient (top → bottom) for the panel background, by condition + time. */
fun weatherGradient(condition: String, isNight: Boolean): List<Color> {
    val night = isNight || condition == "clear-night"
    return when {
        condition == "sunny" && !night -> listOf(Color(0xFF1C5A93), Color(0xFF0E2740))
        (condition == "sunny" || condition == "clear-night") -> listOf(Color(0xFF182350), Color(0xFF0A0E1C))
        condition == "partlycloudy" && !night -> listOf(Color(0xFF2B4D70), Color(0xFF0F1D30))
        condition == "partlycloudy" -> listOf(Color(0xFF1C2742), Color(0xFF0A0F1C))
        condition == "cloudy" -> listOf(Color(0xFF36424F), Color(0xFF131A26))
        condition in setOf("rainy", "pouring", "lightning-rainy", "snowy-rainy") ->
            listOf(Color(0xFF26333F), Color(0xFF0C1420))
        condition == "lightning" -> listOf(Color(0xFF2B2B4A), Color(0xFF0B0C18))
        condition in setOf("snowy", "hail") -> listOf(Color(0xFF42505F), Color(0xFF18202B))
        condition == "fog" -> listOf(Color(0xFF3B434D), Color(0xFF141A22))
        condition in setOf("windy", "windy-variant") -> listOf(Color(0xFF2C3E50), Color(0xFF101820))
        else -> listOf(PanelBg, Color(0xFF0E1726))
    }
}

/** "Today" / "Mon" / "Tue"… from an ISO forecast datetime. */
fun forecastDayLabel(datetime: String): String {
    val date = runCatching { OffsetDateTime.parse(datetime).toLocalDate() }
        .recoverCatching { LocalDate.parse(datetime.take(10)) }
        .getOrNull() ?: return ""
    return if (date == LocalDate.now()) "Today"
    else date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
}
