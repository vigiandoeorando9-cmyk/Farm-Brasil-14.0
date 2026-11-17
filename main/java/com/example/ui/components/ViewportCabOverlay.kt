package com.example.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.domain.GameSettings
import com.example.domain.OwnedVehicle
import com.example.domain.getVehicleDef
import com.example.domain.resolveBrandVisualPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Renders the immersive 1st-person tractor / combine / truck cab interior overlay
 * in CameraMode.CABINE: tapered A-pillars, side & interior mirrors, sweeping
 * windshield wiper, raindrops on glass, illuminated multi-gauge dash cluster,
 * and brand-accented 3-spoke steering wheel rotating with steerAngleDeg.
 */
internal fun DrawScope.drawCabInteriorOverlay(
    activeVeh: OwnedVehicle,
    settings: GameSettings,
    atmo: SunAtmosphereState,
    w: Float,
    h: Float,
) {
    val def = getVehicleDef(activeVeh.defId)
    val customPaint = activeVeh.customPaintRgb?.takeIf { it.size >= 3 }
        ?.let { com.example.domain.rgb(it[0], it[1], it[2]) }
    val palette = resolveBrandVisualPalette(def, customPaint, activeVeh.rimColorId)
    val brandAccent = palette.decalTextRgb.toComposeColor()

    val pillarDark = Color(0xFF131815)
    val trimMetal = Color(0xFF2B322F)
    val glassHighlight = Color(0xFFB3E5FC)

    // 1. Subtle interior night dome light wash when interiorLightOn == true
    if (activeVeh.interiorLightOn == true && atmo.isNight) {
        drawRect(
            color = Color(0xFFFFE082).copy(alpha = 0.08f),
            topLeft = Offset.Zero,
            size = Size(w, h),
        )
    }

    // 2. Windshield Raindrops when raining
    if (atmo.rainIntensity > 0.10f) {
        val dropCount = (28 * atmo.rainIntensity).toInt().coerceIn(4, 34)
        val tSeed = (atmo.animClockSec * 1.4f).toInt()
        for (i in 0 until dropCount) {
            val rx = (((i * 197 + tSeed * 31) % 92) + 4) / 100f * w
            val ry = (((i * 251 + tSeed * 53) % 72) + 8) / 100f * h
            val rSize = (2.5f + (i % 4) * 1.1f)
            drawCircle(
                color = glassHighlight.copy(alpha = 0.24f),
                radius = rSize,
                center = Offset(rx, ry),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.30f),
                radius = rSize * 0.42f,
                center = Offset(rx - rSize * 0.22f, ry - rSize * 0.22f),
            )
        }
    }

    // 3. Sweeping Windshield Wiper Arm (driven by activeVeh.wiperAngleRad)
    val wiperPivotX = w * 0.50f
    val wiperPivotY = h * 0.80f
    val wiperLen = min(w, h) * 0.54f
    val wiperAngle = if (activeVeh.wipersOn == true) (activeVeh.wiperAngleRad ?: 0.0).toFloat() else -0.95f
    val wTipX = wiperPivotX + sin(wiperAngle) * wiperLen
    val wTipY = wiperPivotY - cos(wiperAngle) * wiperLen
    drawLine(
        color = Color(0xFF111513),
        start = Offset(wiperPivotX, wiperPivotY),
        end = Offset(wTipX, wTipY),
        strokeWidth = 4.5f,
        cap = StrokeCap.Round,
    )
    // Rubber squeegee blade perpendicular near tip
    val bladeHalf = wiperLen * 0.28f
    val bladeDx = cos(wiperAngle) * bladeHalf
    val bladeDy = sin(wiperAngle) * bladeHalf
    val bladeMidX = wiperPivotX + sin(wiperAngle) * (wiperLen * 0.72f)
    val bladeMidY = wiperPivotY - cos(wiperAngle) * (wiperLen * 0.72f)
    drawLine(
        color = Color(0xFF1E2421),
        start = Offset(bladeMidX - bladeDx * 0.2f, bladeMidY - bladeDy * 0.2f),
        end = Offset(wTipX + bladeDx * 0.15f, wTipY + bladeDy * 0.15f),
        strokeWidth = 6.5f,
        cap = StrokeCap.Round,
    )

    // 4. Tapered Cab A-Pillars & Top Roof Visor/Headliner
    val leftPillar = Path().apply {
        moveTo(0f, 0f)
        lineTo(w * 0.068f, 0f)
        lineTo(w * 0.032f, h)
        lineTo(0f, h)
        close()
    }
    drawPath(leftPillar, pillarDark.copy(alpha = 0.86f))
    drawLine(
        color = trimMetal.copy(alpha = 0.7f),
        start = Offset(w * 0.068f, 0f),
        end = Offset(w * 0.032f, h),
        strokeWidth = 2.5f,
    )

    val rightPillar = Path().apply {
        moveTo(w, 0f)
        lineTo(w * 0.932f, 0f)
        lineTo(w * 0.968f, h)
        lineTo(w, h)
        close()
    }
    drawPath(rightPillar, pillarDark.copy(alpha = 0.86f))
    drawLine(
        color = trimMetal.copy(alpha = 0.7f),
        start = Offset(w * 0.932f, 0f),
        end = Offset(w * 0.968f, h),
        strokeWidth = 2.5f,
    )

    // Top cab roof liner & sun visor
    drawRect(
        color = pillarDark.copy(alpha = 0.88f),
        topLeft = Offset.Zero,
        size = Size(w, h * 0.056f),
    )
    drawRect(
        color = trimMetal.copy(alpha = 0.6f),
        topLeft = Offset(0f, h * 0.054f),
        size = Size(w, 2.5f),
    )

    // Interior cab dome light lens + rearview mirror
    val domeColor = if (activeVeh.interiorLightOn == true) Color(0xFFFFF59D) else Color(0xFF37474F)
    drawRoundRect(
        color = domeColor,
        topLeft = Offset(w * 0.482f, h * 0.012f),
        size = Size(w * 0.036f, h * 0.016f),
        cornerRadius = CornerRadius(6f, 6f),
    )
    drawRoundRect(
        color = Color(0xFF1A1F1D),
        topLeft = Offset(w * 0.44f, h * 0.052f),
        size = Size(w * 0.12f, h * 0.038f),
        cornerRadius = CornerRadius(6f, 6f),
    )
    drawRoundRect(
        color = if (atmo.isNight) Color(0xFF15202B) else Color(0xFF456D80).copy(alpha = 0.65f),
        topLeft = Offset(w * 0.446f, h * 0.056f),
        size = Size(w * 0.108f, h * 0.030f),
        cornerRadius = CornerRadius(4f, 4f),
    )

    // 5. Left & Right Exterior Side Mirrors anchored onto A-pillars
    val mirrorSky = if (atmo.isNight) Color(0xFF121E28) else Color(0xFF5D8EA6).copy(alpha = 0.78f)
    // Left mirror bracket + housing
    drawLine(Color(0xFF1A1E1C), Offset(w * 0.052f, h * 0.34f), Offset(w * 0.016f, h * 0.32f), strokeWidth = 4f)
    drawRoundRect(Color(0xFF181C1A), Offset(w * 0.008f, h * 0.28f), Size(w * 0.034f, h * 0.11f), CornerRadius(6f, 6f))
    drawRoundRect(mirrorSky, Offset(w * 0.012f, h * 0.288f), Size(w * 0.026f, h * 0.094f), CornerRadius(4f, 4f))
    // Right mirror bracket + housing
    drawLine(Color(0xFF1A1E1C), Offset(w * 0.948f, h * 0.34f), Offset(w * 0.984f, h * 0.32f), strokeWidth = 4f)
    drawRoundRect(Color(0xFF181C1A), Offset(w * 0.958f, h * 0.28f), Size(w * 0.034f, h * 0.11f), CornerRadius(6f, 6f))
    drawRoundRect(mirrorSky, Offset(w * 0.962f, h * 0.288f), Size(w * 0.026f, h * 0.094f), CornerRadius(4f, 4f))

    // 6. Sculpted Lower Cowl, Illuminated Instrument Cluster & Rotating 3-Spoke Steering Wheel
    if (settings.hudLayout?.showDashGauges != false) {
        val cowlTop = h * 0.815f
        val cowlPath = Path().apply {
            moveTo(0f, h)
            lineTo(w * 0.24f, cowlTop + h * 0.03f)
            lineTo(w * 0.36f, cowlTop)
            lineTo(w * 0.64f, cowlTop)
            lineTo(w * 0.76f, cowlTop + h * 0.03f)
            lineTo(w, h)
            close()
        }
        drawPath(cowlPath, Color(0xFF141816).copy(alpha = 0.88f))
        drawLine(
            color = trimMetal,
            start = Offset(w * 0.36f, cowlTop),
            end = Offset(w * 0.64f, cowlTop),
            strokeWidth = 2.5f,
        )

        val dashW = (w * 0.33f).coerceIn(180f, 340f)
        val dashH = (h * 0.125f).coerceIn(46f, 82f)
        val dashLeft = (w - dashW) * 0.5f
        val dashTop = h - dashH - h * 0.022f
        val isEngineOn = activeVeh.engineRunning == true

        // Instrument bezel panel
        drawRoundRect(
            color = Color(0xFF0E1311).copy(alpha = 0.94f),
            topLeft = Offset(dashLeft, dashTop),
            size = Size(dashW, dashH),
            cornerRadius = CornerRadius(12f, 12f),
        )
        drawRoundRect(
            color = if (isEngineOn) Color(0xFF2E4E42) else Color(0xFF282E2C),
            topLeft = Offset(dashLeft, dashTop),
            size = Size(dashW, dashH),
            cornerRadius = CornerRadius(12f, 12f),
            style = Stroke(width = 2f),
        )

        // RPM bar
        val rpmFrac = ((activeVeh.rpm - 650.0) / 1850.0).toFloat().coerceIn(0.04f, 1f)
        val barH = dashH * 0.16f
        val leftX = dashLeft + dashW * 0.07f
        val barW = dashW * 0.36f
        drawRoundRect(Color(0xFF212A2E), Offset(leftX, dashTop + dashH * 0.22f), Size(barW, barH), CornerRadius(4f, 4f))
        if (isEngineOn) {
            drawRoundRect(
                color = if (rpmFrac > 0.84f) Color(0xFFFF7043) else Color(0xFF10B981),
                topLeft = Offset(leftX, dashTop + dashH * 0.22f),
                size = Size(barW * rpmFrac, barH),
                cornerRadius = CornerRadius(4f, 4f),
            )
        }

        // Fuel bar
        val fuelFrac = (activeVeh.fuelL / max(def.fuelCapacityL, 1.0)).toFloat().coerceIn(0f, 1f)
        val rightX = dashLeft + dashW * 0.57f
        drawRoundRect(Color(0xFF212A2E), Offset(rightX, dashTop + dashH * 0.22f), Size(barW, barH), CornerRadius(4f, 4f))
        drawRoundRect(
            color = if (fuelFrac < 0.18f) Color(0xFFEF5350) else Color(0xFFFBC02D),
            topLeft = Offset(rightX, dashTop + dashH * 0.22f),
            size = Size(barW * fuelFrac, barH),
            cornerRadius = CornerRadius(4f, 4f),
        )

        // Engine Coolant Temp bar
        val tempFrac = (((activeVeh.engineTempC ?: 78.0) - 40.0) / 80.0).toFloat().coerceIn(0.1f, 1f)
        drawRoundRect(Color(0xFF212A2E), Offset(leftX, dashTop + dashH * 0.48f), Size(barW, barH * 0.72f), CornerRadius(3f, 3f))
        drawRoundRect(
            color = if (tempFrac > 0.82f) Color(0xFFEF5350) else Color(0xFF38BDF8),
            topLeft = Offset(leftX, dashTop + dashH * 0.48f),
            size = Size(barW * tempFrac, barH * 0.72f),
            cornerRadius = CornerRadius(3f, 3f),
        )

        // Air Brake / Hydraulic Pressure bar
        val airFrac = ((activeVeh.airBrakePressurePct ?: 92.0) / 100.0).toFloat().coerceIn(0f, 1f)
        drawRoundRect(Color(0xFF212A2E), Offset(rightX, dashTop + dashH * 0.48f), Size(barW, barH * 0.72f), CornerRadius(3f, 3f))
        drawRoundRect(
            color = if (airFrac < 0.35f) Color(0xFFFF7043) else Color(0xFFA3E635),
            topLeft = Offset(rightX, dashTop + dashH * 0.48f),
            size = Size(barW * airFrac, barH * 0.72f),
            cornerRadius = CornerRadius(3f, 3f),
        )

        // Status LEDs row: 4WD, PTO, Cruise, Headlights, Handbrake
        val ledY = dashTop + dashH * 0.76f
        val ledRadius = (dashH * 0.068f).coerceIn(2.6f, 5.0f)
        val ledColors = listOf(
            if (activeVeh.is4wdEngaged == true) Color(0xFF22C55E) else Color(0xFF2B3538),
            if (activeVeh.isPtoEngaged == true) Color(0xFFF59E0B) else Color(0xFF2B3538),
            if (activeVeh.cruiseControlActive == true) Color(0xFF38BDF8) else Color(0xFF2B3538),
            if (activeVeh.headlightsOn || activeVeh.workLightsOn == true) Color(0xFF60A5FA) else Color(0xFF2B3538),
            if (activeVeh.handbrakeOn == true) Color(0xFFEF4444) else Color(0xFF2B3538),
        )
        for (idx in ledColors.indices) {
            val lx = dashLeft + dashW * (0.18f + idx * 0.16f)
            drawCircle(color = ledColors[idx], radius = ledRadius, center = Offset(lx, ledY))
        }

        // Rotating 3-Spoke Steering Wheel above lower cowl center
        val wheelCx = w * 0.50f
        val wheelCy = h * 0.905f
        val wheelR = (min(w, h) * 0.115f).coerceIn(36f, 78f)
        val wheelTurnRad = (activeVeh.steerAngleDeg.toFloat() * 1.6f) * (PI / 180.0).toFloat()
        // Steering column housing
        drawRoundRect(
            color = Color(0xFF1A1F1D),
            topLeft = Offset(wheelCx - wheelR * 0.24f, wheelCy),
            size = Size(wheelR * 0.48f, h - wheelCy + 6f),
            cornerRadius = CornerRadius(8f, 8f),
        )
        // Outer wheel grip shadow & grip ring
        drawCircle(
            color = Color(0xFF0E1110),
            radius = wheelR,
            center = Offset(wheelCx, wheelCy),
            style = Stroke(width = (wheelR * 0.22f).coerceIn(7f, 15f)),
        )
        drawCircle(
            color = Color(0xFF242A27),
            radius = wheelR,
            center = Offset(wheelCx, wheelCy),
            style = Stroke(width = (wheelR * 0.14f).coerceIn(4f, 10f)),
        )
        // 3 spokes rotating smoothly by wheelTurnRad
        val spokeAngles = floatArrayOf(0f, 2.094f, 4.188f)
        for (sa in spokeAngles) {
            val a = sa + wheelTurnRad - (PI.toFloat() * 0.5f)
            drawLine(
                color = Color(0xFF2B322F),
                start = Offset(wheelCx, wheelCy),
                end = Offset(wheelCx + cos(a) * wheelR * 0.92f, wheelCy + sin(a) * wheelR * 0.92f),
                strokeWidth = (wheelR * 0.14f).coerceIn(5f, 10f),
                cap = StrokeCap.Round,
            )
        }
        // Steering top marker band (shifts with steer angle)
        val markerA = wheelTurnRad - (PI.toFloat() * 0.5f)
        drawCircle(
            color = brandAccent,
            radius = (wheelR * 0.085f).coerceIn(3f, 6f),
            center = Offset(wheelCx + cos(markerA) * wheelR, wheelCy + sin(markerA) * wheelR),
        )
        // Center horn boss with brand accent
        drawCircle(
            color = Color(0xFF181C1A),
            radius = wheelR * 0.27f,
            center = Offset(wheelCx, wheelCy),
        )
        drawCircle(
            color = brandAccent,
            radius = wheelR * 0.13f,
            center = Offset(wheelCx, wheelCy),
        )
    }
}
