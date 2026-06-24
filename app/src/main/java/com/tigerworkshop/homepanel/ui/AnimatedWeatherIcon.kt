package com.tigerworkshop.homepanel.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.cos
import kotlin.math.sin

// Soft, low-saturation pastel palette (frosted-glass weather style).
private val SunCore = Color(0xFFFFF6D8)
private val SunMid = Color(0xFFFFE19A)
private val SunEdge = Color(0xFFFFCB72)
private val Ray = Color(0xFFFFDE9A)
private val CloudLight = Color(0xFFEDF1F8)
private val CloudShade = Color(0xFFD3DCEA)
private val RainColor = Color(0xFFA7C8E6)
private val SnowColor = Color(0xFFF2F7FD)
private val BoltColor = Color(0xFFFFD27E)
private val MoonColor = Color(0xFFEEF1F8)

/**
 * An original, animated weather glyph (sun, cloud, rain, snow, bolt, moon…)
 * drawn on a Canvas. Continuous animations run while it is visible.
 */
@Composable
fun AnimatedWeatherIcon(condition: String, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "wx")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(11000, easing = LinearEasing)), label = "rot")
    val pulse by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )
    val drift by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift",
    )
    val fall by t.animateFloat(0f, 1f, infiniteRepeatable(tween(950, easing = LinearEasing)), label = "fall")
    val snowFall by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing)), label = "snow")
    val flash by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 2800
                0f at 0; 0f at 1700; 1f at 1820; 0.1f at 1980; 0.7f at 2120; 0f at 2300
            },
        ),
        label = "flash",
    )

    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        when (condition) {
            "sunny" -> drawSun(rot, pulse)
            "clear-night" -> drawMoon(pulse)
            "partlycloudy" -> {
                drawSun(rot, pulse, scale = 0.62f, at = Offset(center.x - size.minDimension * 0.16f, center.y - size.minDimension * 0.16f))
                drawCloud(drift, scale = 0.92f, dy = size.minDimension * 0.10f)
            }
            "cloudy" -> drawCloud(drift)
            "fog" -> { drawCloud(drift, dy = -size.minDimension * 0.06f); drawFog(drift) }
            "rainy", "snowy-rainy" -> { drawCloud(drift); drawRain(fall, 4) }
            "pouring" -> { drawCloud(drift); drawRain(fall, 6) }
            "lightning-rainy" -> { drawCloud(drift); drawRain(fall, 4); drawBolt(flash) }
            "lightning" -> { drawCloud(drift); drawBolt(flash) }
            "snowy", "hail" -> { drawCloud(drift); drawSnow(snowFall, 5) }
            "windy", "windy-variant" -> drawWind(drift)
            else -> drawCloud(drift)
        }
    }
}

private fun DrawScope.drawSun(rot: Float, pulse: Float, scale: Float = 1f, at: Offset = center) {
    val s = size.minDimension * scale
    val core = s * 0.21f * (0.97f + 0.05f * pulse)
    val rayIn = s * 0.31f
    val rayOut = s * 0.42f * (0.97f + 0.05f * pulse)

    // large, very soft halo
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0x33FFE3A0), Color(0x14FFE3A0), Color(0x00FFE3A0)),
            center = at, radius = s * 0.62f,
        ),
        radius = s * 0.62f, center = at,
    )
    rotate(rot, pivot = at) {
        for (i in 0 until 8) {
            val a = Math.toRadians((i * 45).toDouble())
            val sx = at.x + (cos(a) * rayIn).toFloat()
            val sy = at.y + (sin(a) * rayIn).toFloat()
            val ex = at.x + (cos(a) * rayOut).toFloat()
            val ey = at.y + (sin(a) * rayOut).toFloat()
            drawLine(Ray.copy(alpha = 0.85f), Offset(sx, sy), Offset(ex, ey), strokeWidth = s * 0.04f, cap = StrokeCap.Round)
        }
    }
    drawCircle(
        Brush.radialGradient(listOf(SunCore, SunMid, SunEdge), center = at, radius = core),
        radius = core, center = at,
    )
}

private fun DrawScope.drawCloud(drift: Float, scale: Float = 1f, dy: Float = 0f) {
    val s = size.minDimension
    val bob = (drift - 0.5f) * s * 0.05f
    translate(bob, dy) {
        val cx = center.x
        val by = center.y + s * 0.08f
        val w = s * scale
        // soft halo behind the cloud
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0x1FFFFFFF), Color(0x00FFFFFF)),
                center = Offset(cx, by - w * 0.02f), radius = w * 0.5f,
            ),
            radius = w * 0.5f, center = Offset(cx, by - w * 0.02f),
        )
        // soft drop circles + rounded base, drawn shade first then highlight on top
        fun blob(c: Color) {
            drawCircle(c, w * 0.17f, Offset(cx - w * 0.18f, by + w * 0.02f))
            drawCircle(c, w * 0.23f, Offset(cx, by - w * 0.06f))
            drawCircle(c, w * 0.18f, Offset(cx + w * 0.19f, by + w * 0.01f))
            drawRoundRect(
                c,
                topLeft = Offset(cx - w * 0.32f, by),
                size = Size(w * 0.64f, w * 0.18f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.09f, w * 0.09f),
            )
        }
        translate(0f, s * 0.02f) { blob(CloudShade) }
        blob(CloudLight)
    }
}

private fun DrawScope.drawRain(fall: Float, count: Int) {
    val s = size.minDimension
    val top = center.y + s * 0.20f
    val bottom = center.y + s * 0.44f
    for (i in 0 until count) {
        val x = center.x + (i - (count - 1) / 2f) * s * 0.13f
        val p = (fall + i * 0.27f) % 1f
        val y = top + p * (bottom - top)
        drawLine(
            RainColor.copy(alpha = 0.75f * (1f - p * 0.6f)),
            Offset(x + s * 0.015f, y), Offset(x - s * 0.015f, y + s * 0.10f),
            strokeWidth = s * 0.032f, cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawSnow(fall: Float, count: Int) {
    val s = size.minDimension
    val top = center.y + s * 0.18f
    val bottom = center.y + s * 0.46f
    for (i in 0 until count) {
        val p = (fall + i * 0.2f) % 1f
        val x = center.x + (i - (count - 1) / 2f) * s * 0.12f + (sin((p * 6.283f) + i).toFloat()) * s * 0.03f
        val y = top + p * (bottom - top)
        drawCircle(SnowColor.copy(alpha = (1f - p * 0.4f)), radius = s * 0.032f, center = Offset(x, y))
    }
}

private fun DrawScope.drawBolt(flash: Float) {
    val s = size.minDimension
    val cx = center.x
    val by = center.y + s * 0.14f
    val p = Path().apply {
        moveTo(cx + s * 0.06f, by)
        lineTo(cx - s * 0.10f, by + s * 0.18f)
        lineTo(cx - s * 0.01f, by + s * 0.18f)
        lineTo(cx - s * 0.08f, by + s * 0.36f)
        lineTo(cx + s * 0.12f, by + s * 0.12f)
        lineTo(cx + s * 0.02f, by + s * 0.12f)
        close()
    }
    drawPath(p, BoltColor.copy(alpha = 0.6f + 0.4f * flash))
    if (flash > 0.05f) {
        drawCircle(Color(0xFFFFE9B0).copy(alpha = 0.14f * flash), radius = s * 0.5f, center = center)
    }
}

private fun DrawScope.drawFog(drift: Float) {
    val s = size.minDimension
    for (i in 0 until 4) {
        val y = center.y + s * 0.06f + i * s * 0.11f
        val off = (sin(drift * 6.283f + i).toFloat()) * s * 0.07f
        drawLine(
            CloudShade.copy(alpha = 0.9f),
            Offset(center.x - s * 0.30f + off, y), Offset(center.x + s * 0.30f + off, y),
            strokeWidth = s * 0.05f, cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawWind(drift: Float) {
    val s = size.minDimension
    val off = (drift - 0.5f) * s * 0.12f
    val stroke = Stroke(width = s * 0.06f, cap = StrokeCap.Round)
    listOf(-0.12f, 0.06f, 0.22f).forEachIndexed { i, fy ->
        val y = center.y + fy * s
        val p = Path().apply {
            moveTo(center.x - s * 0.32f + off, y)
            quadraticBezierTo(
                center.x + s * 0.30f + off, y - s * 0.10f,
                center.x + s * 0.18f + off, y - s * 0.16f,
            )
            quadraticBezierTo(
                center.x + s * 0.08f + off, y - s * 0.20f,
                center.x + s * 0.10f + off, y - s * 0.05f,
            )
        }
        drawPath(p, CloudLight.copy(alpha = if (i == 1) 1f else 0.7f), style = stroke)
    }
}

private fun DrawScope.drawMoon(pulse: Float) {
    val s = size.minDimension
    val r = s * 0.30f
    val c = center
    drawCircle(
        Brush.radialGradient(listOf(Color(0x55FFFFFF), Color(0x00FFFFFF)), center = c, radius = r * 1.9f),
        radius = r * (1.8f + 0.06f * pulse), center = c,
    )
    drawCircle(MoonColor, radius = r, center = c)
    // carve the crescent (needs the offscreen compositing layer set on the Canvas)
    drawCircle(
        Color.Black, radius = r * 0.92f,
        center = Offset(c.x + r * 0.52f, c.y - r * 0.16f),
        blendMode = BlendMode.Clear,
    )
}
