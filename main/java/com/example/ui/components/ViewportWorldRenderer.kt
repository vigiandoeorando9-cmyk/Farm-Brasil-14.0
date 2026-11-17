package com.example.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.domain.AquiferAvailabilityTier
import com.example.domain.BrazilianMapDef
import com.example.domain.CameraMode
import com.example.domain.DEFAULT_PLAYER_APPEARANCE
import com.example.domain.GraphicsConfig
import com.example.domain.ManualTool
import com.example.domain.SoilState
import com.example.domain.TerrainCell
import com.example.domain.WeedGrowthStage
import com.example.domain.WeedSpecies
import com.example.domain.WorldPropType
import com.example.domain.WorldState
import com.example.domain.getCropById
import com.example.simulation.StreamingPlatformBudget
import com.example.simulation.getTerrainHeightAt
import com.example.simulation.getWorldCellSizeM
import com.example.simulation.getWorldSpanM
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val FURROW_FRACS_HI = floatArrayOf(0.18f, 0.42f, 0.66f, 0.88f)
private val FURROW_FRACS_LO = floatArrayOf(0.25f, 0.58f, 0.84f)

internal fun DrawScope.drawSkyAndAtmosphere(
    w: Float,
    h: Float,
    horizonY: Float,
    hour: Float,
    cloudCover: Float,
    pose: CameraPose3D,
    focalPx: Float,
    mapDef: BrazilianMapDef,
    gfx: GraphicsConfig? = null,
    animClockSec: Float = 0f,
    atmo: SunAtmosphereState? = null,
) {
    val lightingQ = gfx?.lightingQuality ?: 2
    val effAtmo = atmo ?: computeSunAtmosphereState(
        hourOfDay = hour.toDouble(),
        weather = com.example.domain.WeatherType.ENSOLARADO,
        gfx = gfx ?: com.example.domain.GraphicsConfig(
            textureResolutionScale = 0.84, drawDistance = 315.0, shadowQuality = 2, lightingQuality = 2,
            reflectionQuality = 2, vegetationDensity = 0.8, objectDensity = 0.84, waterQuality = 2,
            vehicleDetailLevel = 2, terrainDetailStep = 1, weatherEffectsDensity = 0.9, textureTierLabel = "2K",
            saturation = 1.08, contrast = 1.06, exposure = 1.02, sharpness = 0.54,
        ),
        animClockSec = animClockSec,
    )
    val isSemiArid = !mapDef.hasRiver || mapDef.id in setOf("map_ce", "map_pe", "map_ba", "map_al")
    val isFloodplain = mapDef.id in setOf("map_pa", "map_ro", "map_ac", "map_to", "mod_map_pantanal_sul")
    val flash = effAtmo.lightningFlash
    val skyTop: Color
    val skyHorizon: Color
    when {
        hour < 5.2f || hour > 20.5f -> {
            skyTop = lerpColor(Color(0xFF040914), Color(0xFF3E4C63), flash * 0.65f)
            skyHorizon = lerpColor(Color(0xFF132238), Color(0xFF5D6E85), flash * 0.55f)
        }
        hour < 7.6f -> {
            val t = ((hour - 5.2f) / 2.4f).coerceIn(0f, 1f)
            skyTop = lerpColor(Color(0xFF172742), Color(0xFF2874A6), t)
            skyHorizon = lerpColor(Color(0xFFEC7A46), Color(0xFFBADFF2), t)
        }
        hour > 16.8f -> {
            val t = ((hour - 16.8f) / 3.7f).coerceIn(0f, 1f)
            skyTop = lerpColor(Color(0xFF21669C), Color(0xFF0C1526), t)
            skyHorizon = lerpColor(Color(0xFF9ACCE8), Color(0xFFDF6C37), t)
        }
        else -> {
            val dayTop = if (isSemiArid) Color(0xFF1A73B8) else Color(0xFF175C9E)
            val dayHorizon = if (isSemiArid) Color(0xFFD6E7ED) else Color(0xFFB5DEF5)
            skyTop = lerpColor(lerpColor(dayTop, Color(0xFF42515E), cloudCover * 0.72f), Color(0xFF8396AB), flash * 0.5f)
            skyHorizon = lerpColor(lerpColor(dayHorizon, Color(0xFF7B8B96), cloudCover * 0.68f), Color(0xFF99ACC2), flash * 0.45f)
        }
    }

    val skyMid = lerpColor(skyTop, skyHorizon, 0.54f)
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(skyTop, skyMid, skyHorizon),
            startY = 0f,
            endY = horizonY + 18f,
        ),
        topLeft = Offset.Zero,
        size = Size(w, horizonY + 20f),
    )

    // Starfield on clear or semi-clear nights
    if (effAtmo.nightFactor > 0.35f && cloudCover < 0.78f && horizonY > 24f) {
        val starAlpha = effAtmo.nightFactor * (1f - cloudCover * 0.9f) * 0.78f
        val starCount = if (lightingQ >= 2) 48 else 24
        for (s in 0 until starCount) {
            val sx = (((s * 397 + 71) % 1000) / 1000f) * w
            val sy = (((s * 613 + 29) % 1000) / 1000f) * (horizonY * 0.72f)
            val twinkle = 0.6f + 0.4f * sin(animClockSec * 2.2f + s * 1.7f)
            drawCircle(
                color = Color(0xFFEAF3FF).copy(alpha = starAlpha * twinkle),
                radius = if (s % 5 == 0) 1.8f else 1.1f,
                center = Offset(sx, sy),
            )
        }
    }

    // Projected Sun / Moon with volumetric corona, golden-hour bloom & crepuscular rays
    val bodyDist = 220f
    val bodyProj = projectRaw(
        pose.camX + effAtmo.sunDirX * bodyDist,
        pose.camY + effAtmo.sunDirY * bodyDist,
        pose.camZ + effAtmo.sunDirZ * bodyDist,
        pose, focalPx, w, h,
    )
    if (bodyProj.visible && bodyProj.screenY < horizonY + 28f) {
        if (!effAtmo.isNight) {
            val hazeAlpha = (1f - cloudCover * 0.70f).coerceIn(0.12f, 1f)
            val bloomTint = lerpColor(Color(0xFFFFF59D), Color(0xFFFF8F40), effAtmo.goldenHourFactor * 0.75f)
            if (lightingQ >= 2) {
                drawCircle(bloomTint.copy(alpha = 0.08f * hazeAlpha), radius = 138f, center = Offset(bodyProj.screenX, bodyProj.screenY))
                // Subtle golden-hour crepuscular light rays radiating downward
                if (effAtmo.goldenHourFactor > 0.10f || effAtmo.sunElevationRad < 0.78f) {
                    val rayAlpha = (0.09f + effAtmo.goldenHourFactor * 0.14f) * hazeAlpha
                    for (rayIdx in -3..3) {
                        val spread = rayIdx * 48f
                        drawLine(
                            color = bloomTint.copy(alpha = rayAlpha * (1f - abs(rayIdx) * 0.16f)),
                            start = Offset(bodyProj.screenX, bodyProj.screenY),
                            end = Offset(bodyProj.screenX + spread * 2.4f, min(h * 0.92f, horizonY + 95f)),
                            strokeWidth = 18f - abs(rayIdx) * 2.5f,
                        )
                    }
                }
            }
            drawCircle(bloomTint.copy(alpha = 0.22f * hazeAlpha), radius = 64f, center = Offset(bodyProj.screenX, bodyProj.screenY))
            drawCircle(bloomTint.copy(alpha = 0.42f * hazeAlpha), radius = 34f, center = Offset(bodyProj.screenX, bodyProj.screenY))
            drawCircle(Color(0xFFFFFDE7).copy(alpha = 0.96f * hazeAlpha), radius = 14f, center = Offset(bodyProj.screenX, bodyProj.screenY))
        } else {
            drawCircle(Color(0xFFCEE8FF).copy(alpha = 0.16f), radius = 34f, center = Offset(bodyProj.screenX, bodyProj.screenY))
            drawCircle(Color(0xFFEAF4FF).copy(alpha = 0.85f), radius = 11f, center = Offset(bodyProj.screenX, bodyProj.screenY))
        }
    }

    // Layered procedural cumulus & storm clouds with sun-lit crest and shadowed underside
    val effectiveClouds = if (lightingQ >= 2) max(cloudCover, 0.22f) else cloudCover
    if (effectiveClouds > 0.12f && horizonY > 40f) {
        val cloudCol = when {
            effAtmo.isNight -> Color(0xFF28384A)
            cloudCover > 0.82f -> lerpColor(Color(0xFF7D8A94), Color(0xFFD0DEEC), flash)
            effAtmo.goldenHourFactor > 0.2f -> lerpColor(Color(0xFFFDFBF7), Color(0xFFFFB074), effAtmo.goldenHourFactor * 0.55f)
            else -> Color(0xFFF8FCFF)
        }
        val cloudShade = when {
            effAtmo.isNight -> Color(0xFF17222E)
            cloudCover > 0.82f -> Color(0xFF4C5964)
            else -> Color(0xFFB5C7D6)
        }
        val cloudAlpha = (0.16f + cloudCover * 0.34f).coerceIn(0.16f, 0.58f)
        val drift = (animClockSec * (1.2f + effAtmo.windStrength * 1.4f)) % 280f
        val cloudCount = if (lightingQ >= 3) 9 else 6
        for (c in 0 until cloudCount) {
            val wx = pose.camX + ((c - cloudCount / 2) * 78f) + drift * 0.14f
            val wz = pose.camZ + 155f + (c % 3) * 34f
            val cp = projectRaw(wx, pose.camY + 42f + (c % 3) * 9f, wz, pose, focalPx, w, h)
            if (cp.visible && cp.screenY < horizonY - 4f) {
                val cw = 138f + (c % 3) * 44f
                drawOval(
                    color = cloudShade.copy(alpha = cloudAlpha * 0.78f),
                    topLeft = Offset(cp.screenX - cw * 0.48f, cp.screenY - 5f),
                    size = Size(cw * 0.96f, 24f),
                )
                drawOval(
                    color = cloudCol.copy(alpha = cloudAlpha),
                    topLeft = Offset(cp.screenX - cw * 0.5f, cp.screenY - 15f),
                    size = Size(cw, 26f),
                )
                if (lightingQ >= 2) {
                    drawOval(
                        color = cloudCol.copy(alpha = cloudAlpha * 0.88f),
                        topLeft = Offset(cp.screenX - cw * 0.24f, cp.screenY - 24f),
                        size = Size(cw * 0.54f, 22f),
                    )
                }
            }
        }
    }

    // Distant countryside hills silhouette band along the horizon
    if (lightingQ >= 1 && horizonY in 28f..(h * 0.92f)) {
        val hillBaseCol = lerpColor(
            Color(effAtmo.fogColorR, effAtmo.fogColorG, effAtmo.fogColorB, 1f),
            if (effAtmo.isNight) Color(0xFF0A1412) else Color(0xFF28412C),
            0.34f,
        )
        val ridgeCount = 10
        val segW = w / ridgeCount
        val yawSlide = ((pose.yawDeg % 360f) / 360f) * w
        for (r in 0..ridgeCount) {
            val rxScreen = ((r * segW - yawSlide * 0.35f) % (w + segW * 2f) + (w + segW * 2f)) % (w + segW * 2f) - segW
            val ridgeH = 11f + ((r * 37) % 16).toFloat()
            drawOval(
                color = hillBaseCol.copy(alpha = 0.58f),
                topLeft = Offset(rxScreen - segW * 0.65f, horizonY - ridgeH),
                size = Size(segW * 2.1f, ridgeH * 2.0f),
            )
        }
    }

    // Horizon atmospheric scattering ribbon
    if (lightingQ >= 2) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, skyHorizon.copy(alpha = 0.28f + effAtmo.goldenHourFactor * 0.14f)),
                startY = max(0f, horizonY - 40f),
                endY = horizonY + 10f,
            ),
            topLeft = Offset(0f, max(0f, horizonY - 40f)),
            size = Size(w, 50f),
        )
    }

    // Far horizon ground blend tinted by biome & lightning
    val groundFill = when {
        effAtmo.isNight -> lerpColor(Color(0xFF0D1711), Color(0xFF22302B), flash * 0.4f)
        isSemiArid -> Color(0xFF6B5A3B)
        isFloodplain -> Color(0xFF234D2A)
        else -> mapDef.grassColorRgb.toComposeColor()
    }
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(skyHorizon.copy(alpha = 0.88f), groundFill),
            startY = horizonY,
            endY = h,
        ),
        topLeft = Offset(0f, horizonY),
        size = Size(w, h - horizonY),
    )
}

internal fun soilCellColor(
    cell: TerrainCell,
    baseGrass: Color,
    soilColor: Color,
    mapDef: BrazilianMapDef,
): Color {
    val moist = cell.moisture.toFloat().coerceIn(0f, 1f)
    val compact = cell.compaction.toFloat().coerceIn(0f, 1f)
    val isWet = cell.isIrrigated == true || moist > 0.72f
    val isSemiAridBiome = !mapDef.hasRiver || mapDef.id in setOf("map_ce", "map_pe", "map_ba", "map_al")
    val sandTint = Color(0xFFC2A06E)
    val rawSoil = when {
        isSemiAridBiome && moist < 0.42f && cell.isIrrigated != true -> lerpColor(soilColor, sandTint, 0.36f)
        isWet -> shadeColor(lerpColor(soilColor, Color(0xFF2B1D14), 0.22f), 0.74f)
        else -> soilColor
    }
    return when (cell.soilState) {
        SoilState.AGUA_RIO -> Color(0xFF1A6D8B)
        SoilState.PONTE -> Color(0xFF8C6B4A)
        SoilState.ESTRADA_ASFALTO -> Color(0xFF33393E)
        SoilState.ESTRADA_TERRA -> {
            val mudRoad = lerpColor(Color(0xFF7D5436), Color(0xFF422C1D), (moist - 0.55f).coerceIn(0f, 1f) * 0.85f)
            lerpColor(mudRoad, rawSoil, 0.28f)
        }
        SoilState.FLORESTA_NATIVA, SoilState.AREA_PRESERVACAO ->
            if (!mapDef.hasRiver) Color(0xFF355C2B) else Color(0xFF1A4F25)
        SoilState.PEDRAS_MATO ->
            if (!mapDef.hasRiver) Color(0xFF6A6648) else Color(0xFF535E49)
        SoilState.PASTAGEM -> when {
            isWet -> lerpColor(baseGrass, Color(0xFF2E7D32), 0.52f)
            isSemiAridBiome -> lerpColor(baseGrass, sandTint, 0.28f)
            compact > 0.45f -> lerpColor(baseGrass, rawSoil, compact * 0.24f)
            else -> baseGrass
        }
        SoilState.GRAMA_CORTADA -> {
            val cutGreen = Color(0xFF387B32)
            if (isWet) shadeColor(cutGreen, 0.82f) else cutGreen
        }
        SoilState.ARADO -> {
            val cDark = (1f - compact * 0.14f) * (if (isWet) 0.74f else 0.94f)
            shadeColor(rawSoil, cDark)
        }
        SoilState.SUBSOLADO -> shadeColor(rawSoil, if (isWet) 0.60f else 0.74f)
        SoilState.PALHADA -> {
            val baseStraw = lerpColor(Color(0xFFAF9355), rawSoil, 0.22f)
            if (isWet) shadeColor(lerpColor(baseStraw, Color(0xFF4B3D24), 0.28f), 0.78f) else baseStraw
        }
        SoilState.SEMEADO -> {
            val seeded = Color(
                (rawSoil.red * 0.88f).coerceIn(0f, 1f),
                (rawSoil.green * 0.95f).coerceIn(0f, 1f),
                rawSoil.blue,
            )
            if (isWet) shadeColor(seeded, 0.78f) else seeded
        }
        SoilState.CRESCENDO -> {
            val crop = cell.cropId?.let { getCropById(it) }
            val t = cell.growthProgress.toFloat().coerceIn(0f, 1f)
            val sc = crop?.seedColorRgb?.toComposeColor() ?: Color(0xFF34983A)
            val rc = crop?.ripeColorRgb?.toComposeColor() ?: Color(0xFF8FB839)
            val col = lerpColor(sc, rc, t * 0.65f)
            if (isWet) lerpColor(shadeColor(col, 0.80f), Color(0xFF1F6E38), 0.26f) else col
        }
        SoilState.PRONTO_COLHEITA -> {
            val ripe = cell.cropId?.let { getCropById(it).ripeColorRgb.toComposeColor() } ?: Color(0xFFD5A938)
            if (isWet) shadeColor(ripe, 0.84f) else ripe
        }
        SoilState.ESCAVADO -> {
            val tone = when (cell.soilTone) {
                "LAMA_AGUA", "ARGILA_UMIDA" -> 0.82f
                "SUBSOLO_ARGILA", "SUBSOLO" -> 0.62f
                "TERRA_CAVADA" -> 0.35f
                else -> cell.soilTone?.toFloatOrNull() ?: 0.5f
            }.coerceIn(0f, 1f)
            val claySubsoil = lerpColor(Color(0xFF7B4429), Color(0xFF533322), tone)
            if ((cell.waterDepthM ?: 0.0) > 0.05) lerpColor(claySubsoil, Color(0xFF1F5D72), 0.55f) else claySubsoil
        }
        SoilState.ATERRO_SOLO -> {
            val tone = when (cell.soilTone) {
                "TALUDE_COMPACTADO" -> 0.65f
                "ATERRO_BARRO" -> 0.45f
                else -> cell.soilTone?.toFloatOrNull() ?: 0.3f
            }.coerceIn(0f, 1f)
            lerpColor(Color(0xFF91613C), Color(0xFF6B4229), tone)
        }
        SoilState.TALUDE_BARRAGEM -> Color(0xFF8C7355)
        SoilState.ACUDE_AGUA -> {
            val depthBlend = ((cell.waterDepthM ?: 1.2).toFloat() / 3.5f).coerceIn(0f, 1f)
            lerpColor(Color(0xFF1E7896), Color(0xFF0E4D66), depthBlend)
        }
    }
}

internal fun DrawScope.drawDetailedTerrain(
    worldState: WorldState,
    mapDef: BrazilianMapDef,
    gfx: GraphicsConfig,
    cameraMode: CameraMode,
    pose: CameraPose3D,
    focalPx: Float,
    w: Float,
    h: Float,
    maxDrawM: Float,
    isNight: Boolean,
    twilightDarken: Float,
    rainIntensity: Float,
    animClockSec: Float,
    scratch: ViewportScratch3D,
    atmo: SunAtmosphereState? = null,
    streamingBudget: StreamingPlatformBudget? = null,
) {
    val gridSide = worldState.mapGridSize ?: 128
    val cellSizeM = getWorldCellSizeM(worldState).toFloat()
    val worldSpanM = getWorldSpanM(worldState).toFloat()
    val worldHalfM = worldSpanM * 0.5f
    val effectiveMaxDrawM = if (streamingBudget != null) {
        min(maxDrawM, streamingBudget.maxDrawDistanceM.toFloat())
    } else {
        maxDrawM
    }
    val stepMid = if (gfx.terrainDetailStep >= 2) 3 else 2
    val stepFar = max(stepMid + 1, if (streamingBudget?.isDesktopPc == true) 3 else 4)
    val baseGrass = mapDef.grassColorRgb.toComposeColor()
    val soilColor = mapDef.soilColorRgb.toComposeColor()
    val cells = worldState.cells
    val vegDensity = gfx.vegetationDensity.toFloat().coerceIn(0f, 1.0f)

    val camFwdLenXZ = hypot(pose.fwdX, pose.fwdZ).coerceAtLeast(0.001f)
    val camDirX = pose.fwdX / camFwdLenXZ
    val camDirZ = pose.fwdZ / camFwdLenXZ

    val camGx = floor(((pose.camX + worldHalfM) / cellSizeM).toDouble()).toInt().coerceIn(0, gridSide - 2)
    val camGz = floor(((pose.camZ + worldHalfM) / cellSizeM).toDouble()).toInt().coerceIn(0, gridSide - 2)

    val radiusCells = (effectiveMaxDrawM / cellSizeM).toInt()
    val gxMin = max(0, camGx - radiusCells)
    val gxMax = min(gridSide - 2, camGx + radiusCells)
    val gzMin = max(0, camGz - radiusCells)
    val gzMax = min(gridSide - 2, camGz + radiusCells)

    val nearPad = if (cameraMode == CameraMode.CABINE) 9 else 7
    val nearGxMin = max(gxMin, floor(((pose.targetX + worldHalfM) / cellSizeM).toDouble()).toInt() - nearPad)
    val nearGxMax = min(gxMax, floor(((pose.targetX + worldHalfM) / cellSizeM).toDouble()).toInt() + nearPad)
    val nearGzMin = max(gzMin, floor(((pose.targetZ + worldHalfM) / cellSizeM).toDouble()).toInt() - nearPad)
    val nearGzMax = min(gzMax, floor(((pose.targetZ + worldHalfM) / cellSizeM).toDouble()).toInt() + nearPad)

    val midRadiusM = min(effectiveMaxDrawM, streamingBudget?.lod1RadiusM?.toFloat() ?: 145f)
    val fogCol = if (atmo != null) {
        Color(atmo.fogColorR, atmo.fogColorG, atmo.fogColorB, 1f)
    } else if (isNight) {
        Color(0xFF101926)
    } else {
        Color(0xFFA9C4D4)
    }

    fun drawTerrainRing(
        rGxMin: Int,
        rGxMax: Int,
        rGzMin: Int,
        rGzMax: Int,
        step: Int,
        skipInnerNear: Boolean,
        minDistSq: Float = 0f,
        maxRingDistSq: Float = effectiveMaxDrawM * effectiveMaxDrawM,
    ) {
        val alignedGzMin = (rGzMin / step) * step
        val alignedGxMin = (rGxMin / step) * step
        val zSeq = if (camDirZ >= 0f) {
            (rGzMax downTo alignedGzMin step step)
        } else {
            (alignedGzMin..rGzMax step step)
        }

        for (gz in zSeq) {
            val gz1 = min(gridSide - 1, gz + step)
            val wz0 = -worldHalfM + gz * cellSizeM
            val wz1 = -worldHalfM + gz1 * cellSizeM

            val leftRange = (alignedGxMin..min(camGx, rGxMax) step step)
            val rightRange = (rGxMax downTo max(alignedGxMin, camGx + step) step step)

            for (pass in 0..1) {
                val xSeq = if (pass == 0) leftRange else rightRange
                for (gx in xSeq) {
                    if (skipInnerNear && gx in nearGxMin..nearGxMax && gz in nearGzMin..nearGzMax) continue
                    val gx1 = min(gridSide - 1, gx + step)
                    val wx0 = -worldHalfM + gx * cellSizeM
                    val wx1 = -worldHalfM + gx1 * cellSizeM
                    val centerX = (wx0 + wx1) * 0.5f
                    val centerZ = (wz0 + wz1) * 0.5f
                    val relX = centerX - pose.camX
                    val relZ = centerZ - pose.camZ
                    val distSq = relX * relX + relZ * relZ
                    if (distSq > maxRingDistSq || distSq < minDistSq) continue
                    if (relX * camDirX + relZ * camDirZ < -step * cellSizeM * 2.2f) continue

                    val idx00 = gz * gridSide + gx
                    val idx10 = gz * gridSide + gx1
                    val idx01 = gz1 * gridSide + gx
                    val idx11 = gz1 * gridSide + gx1
                    if (idx00 < 0 || idx11 >= cells.size) continue

                    val c00 = cells[idx00]
                    val c10 = cells[idx10]
                    val c01 = cells[idx01]
                    val c11 = cells[idx11]
                    val h00 = c00.height.toFloat()
                    val h10 = c10.height.toFloat()
                    val h01 = c01.height.toFloat()
                    val h11 = c11.height.toFloat()

                    val span = step * cellSizeM
                    val dhdx = ((h10 - h00) + (h11 - h01)) * 0.5f / span
                    val dhdz = ((h01 - h00) + (h11 - h10)) * 0.5f / span

                    var baseCellCol = soilCellColor(c00, baseGrass, soilColor, mapDef)
                    if (gfx.textureResolutionScale >= 0.65) {
                        val grainNoise = (((gx * 73856093) xor (gz * 19349663)) and 15) / 15f
                        val grainShade = 0.93f + grainNoise * 0.14f
                        baseCellCol = shadeColor(baseCellCol, grainShade)
                    }

                    // Riverbed / pond water Fresnel reflection & wet surfaces
                    if (c00.soilState == SoilState.AGUA_RIO || c00.soilState == SoilState.ACUDE_AGUA || (c00.waterDepthM ?: 0.0) > 0.12) {
                        val ripple = 0.5f + 0.5f * sin(animClockSec * 2.6f + gx * 0.72f + gz * 0.54f)
                        val skyRef = if (c00.soilState == SoilState.ACUDE_AGUA) Color(0xFF5EC2E6) else Color(0xFF4BA0C4)
                        val waterBlend = if (gfx.waterQuality >= 1) ripple * 0.30f else 0.12f
                        baseCellCol = lerpColor(baseCellCol, skyRef, waterBlend)
                    } else if (c00.soilState == SoilState.ESTRADA_ASFALTO && rainIntensity > 0.15f && gfx.reflectionQuality >= 1) {
                        val fresnelWet = (rainIntensity * 0.45f).coerceIn(0f, 0.52f)
                        baseCellCol = lerpColor(baseCellCol, Color(0xFF607B8E), fresnelWet)
                    } else if (c00.soilState == SoilState.ESTRADA_TERRA && (rainIntensity > 0.25f || c00.moisture > 0.72)) {
                        val wetDirt = max(rainIntensity * 0.5f, ((c00.moisture.toFloat() - 0.62f) * 1.4f)).coerceIn(0f, 0.55f)
                        baseCellCol = lerpColor(baseCellCol, Color(0xFF3E2E24), wetDirt)
                    }

                    val distM = sqrt(distSq)
                    val fogBlend = ((distM - effectiveMaxDrawM * 0.40f) / (effectiveMaxDrawM * 0.60f)).coerceIn(0f, 0.78f)
                    val litColor = if (atmo != null) {
                        // Compute real terrain surface normal (-dhdx, 1, -dhdz) and shade per material
                        val invN = 1f / sqrt(dhdx * dhdx + 1f + dhdz * dhdz)
                        val nx = -dhdx * invN
                        val ny = invN
                        val nz = -dhdz * invN
                        val mat = when {
                            c00.soilState == SoilState.AGUA_RIO || c00.soilState == SoilState.ACUDE_AGUA || (c00.waterDepthM ?: 0.0) > 0.12 -> MaterialType.WATER
                            c00.soilState == SoilState.ESTRADA_ASFALTO && rainIntensity > 0.18f -> MaterialType.WET_SURFACE
                            c00.soilState == SoilState.PASTAGEM || c00.soilState == SoilState.CRESCENDO ||
                                c00.soilState == SoilState.FLORESTA_NATIVA || c00.soilState == SoilState.AREA_PRESERVACAO -> MaterialType.FOLIAGE
                            else -> MaterialType.SOIL
                        }
                        val midH = (h00 + h10 + h01 + h11) * 0.25f
                        val vx = pose.camX - centerX
                        val vy = pose.camY - midH
                        val vz = pose.camZ - centerZ
                        val vInv = 1f / max(0.01f, sqrt(vx * vx + vy * vy + vz * vz))
                        Color(
                            shadeMaterialFace(
                                baseArgb = packArgb(baseCellCol.red, baseCellCol.green, baseCellCol.blue, 1f),
                                nx = nx, ny = ny, nz = nz,
                                viewDirX = vx * vInv, viewDirY = vy * vInv, viewDirZ = vz * vInv,
                                atmo = atmo, material = mat,
                                aoFactor = (1f - (c00.compaction.toFloat() * 0.10f)).coerceIn(0.84f, 1f),
                                fogFactor = fogBlend,
                            ),
                        )
                    } else {
                        val slopeShade = (0.94f - dhdx * 0.36f + dhdz * 0.24f).coerceIn(0.65f, 1.18f) * twilightDarken
                        shadeAndFog(baseCellCol, slopeShade, fogBlend, fogCol)
                    }

                    drawProjectedQuad(
                        wx0, h00, wz0,
                        wx1, h10, wz0,
                        wx1, h11, wz1,
                        wx0, h01, wz1,
                        color = litColor,
                        pose = pose,
                        focalPx = focalPx,
                        viewW = w,
                        viewH = h,
                        scratch = scratch,
                    )

                    if (!skipInnerNear && distM < 62f) {
                        val midH = (h00 + h10 + h01 + h11) * 0.25f
                        when (c00.soilState) {
                            SoilState.AGUA_RIO, SoilState.ACUDE_AGUA -> {
                                if (gfx.waterQuality >= 1 && ((gx + gz) and 1) == 0) {
                                    val shim = 0.35f + 0.35f * sin(animClockSec * 2.8f + gx * 0.9f + gz * 0.4f)
                                    val glintCol = Color(0xFFC8EFFF).copy(alpha = shim * 0.54f * twilightDarken)
                                    drawProjectedLine(
                                        wx0 + 0.4f, midH + 0.03f, wz0 + cellSizeM * 0.38f,
                                        wx1 - 0.4f, midH + 0.03f, wz0 + cellSizeM * 0.50f,
                                        glintCol, 2.2f, pose, focalPx, w, h,
                                    )
                                    drawProjectedLine(
                                        wx0 + 0.7f, midH + 0.03f, wz0 + cellSizeM * 0.72f,
                                        wx1 - 0.6f, midH + 0.03f, wz0 + cellSizeM * 0.80f,
                                        glintCol.copy(alpha = glintCol.alpha * 0.75f), 1.8f, pose, focalPx, w, h,
                                    )
                                }
                            }
                            SoilState.ESCAVADO, SoilState.ATERRO_SOLO, SoilState.TALUDE_BARRAGEM -> {
                                val ribCol = if (c00.soilState == SoilState.TALUDE_BARRAGEM) {
                                    Color(0xFFD6C098).copy(alpha = 0.32f * twilightDarken)
                                } else {
                                    Color(0xFF3E2214).copy(alpha = 0.36f * twilightDarken)
                                }
                                drawProjectedLine(wx0 + 0.25f, midH + 0.03f, wz0 + cellSizeM * 0.32f, wx1 - 0.25f, midH + 0.03f, wz0 + cellSizeM * 0.44f, ribCol, 2.2f, pose, focalPx, w, h)
                                if ((c00.waterDepthM ?: 0.0) > 0.08) {
                                    val seepGlow = Color(0xFF4FC3F7).copy(alpha = 0.42f * twilightDarken)
                                    drawProjectedLine(wx0 + 0.35f, midH + 0.05f, wz0 + cellSizeM * 0.55f, wx1 - 0.35f, midH + 0.05f, wz0 + cellSizeM * 0.62f, seepGlow, 2.4f, pose, focalPx, w, h)
                                }
                            }
                            SoilState.ESTRADA_TERRA -> {
                                if (gfx.textureResolutionScale >= 0.68) {
                                    val rutCol = if (c00.moisture > 0.70 || rainIntensity > 0.35f) {
                                        Color(0xFF2B1C14).copy(alpha = 0.42f * twilightDarken)
                                    } else {
                                        Color(0xFF4A3122).copy(alpha = 0.28f * twilightDarken)
                                    }
                                    drawProjectedLine(
                                        wx0 + 0.2f, midH + 0.03f, wz0 + cellSizeM * 0.30f,
                                        wx1 - 0.2f, midH + 0.03f, wz0 + cellSizeM * 0.30f,
                                        rutCol, 3.0f, pose, focalPx, w, h,
                                    )
                                    drawProjectedLine(
                                        wx0 + 0.2f, midH + 0.03f, wz0 + cellSizeM * 0.70f,
                                        wx1 - 0.2f, midH + 0.03f, wz0 + cellSizeM * 0.70f,
                                        rutCol, 3.0f, pose, focalPx, w, h,
                                    )
                                    if (rainIntensity > 0.30f && gfx.reflectionQuality >= 1 && ((gx + gz) and 1) == 0) {
                                        val wetSheen = Color(0xFF6B8596).copy(alpha = 0.34f * rainIntensity)
                                        drawProjectedLine(wx0 + 0.5f, midH + 0.035f, wz0 + cellSizeM * 0.30f, wx1 - 0.5f, midH + 0.035f, wz0 + cellSizeM * 0.30f, wetSheen, 1.8f, pose, focalPx, w, h)
                                    }
                                }
                                if (vegDensity >= 0.38f && distM < 34f && (((gx * 7 + gz * 13) and 3) == 0)) {
                                    val wind = atmo?.windStrength ?: 0.45f
                                    val sway = sin(animClockSec * (1.7f + wind) + wx0 * 0.4f) * 0.08f * wind
                                    val marginCol = shadeColor(baseGrass, 0.96f * twilightDarken)
                                    val mx = wx0 + cellSizeM * 0.12f
                                    val mz = wz0 + cellSizeM * 0.88f
                                    drawProjectedLine(mx, midH, mz, mx + sway - 0.14f, midH + 0.34f, mz, marginCol, 1.9f, pose, focalPx, w, h)
                                    drawProjectedLine(mx, midH, mz, mx + sway + 0.12f, midH + 0.40f, mz, marginCol, 2.0f, pose, focalPx, w, h)
                                }
                            }
                            SoilState.ESTRADA_ASFALTO -> {
                                val hasRoadZ = (gz > 0 && cells[(gz - 1) * gridSide + gx].soilState == SoilState.ESTRADA_ASFALTO) ||
                                    (gz + 1 < gridSide && cells[(gz + 1) * gridSide + gx].soilState == SoilState.ESTRADA_ASFALTO)
                                val hasRoadX = (gx > 0 && cells[gz * gridSide + (gx - 1)].soilState == SoilState.ESTRADA_ASFALTO) ||
                                    (gx + 1 < gridSide && cells[gz * gridSide + (gx + 1)].soilState == SoilState.ESTRADA_ASFALTO)
                                if (hasRoadZ && !hasRoadX && (gz % 2 == 0)) {
                                    drawProjectedLine(
                                        wx0 + cellSizeM * 0.5f, midH + 0.03f, wz0 + 0.6f,
                                        wx0 + cellSizeM * 0.5f, midH + 0.03f, wz1 - 0.6f,
                                        Color(0xFFFFD54F).copy(alpha = 0.80f * twilightDarken),
                                        2.6f, pose, focalPx, w, h,
                                    )
                                } else if (hasRoadX && (gx % 2 == 0)) {
                                    drawProjectedLine(
                                        wx0 + 0.6f, midH + 0.03f, wz0 + cellSizeM * 0.5f,
                                        wx1 - 0.6f, midH + 0.03f, wz0 + cellSizeM * 0.5f,
                                        Color(0xFFFFD54F).copy(alpha = 0.80f * twilightDarken),
                                        2.6f, pose, focalPx, w, h,
                                    )
                                }
                                if (gfx.textureResolutionScale >= 0.78) {
                                    val edgeCol = Color(0xFFE0E6E2).copy(alpha = 0.36f * twilightDarken)
                                    if (hasRoadZ && !hasRoadX) {
                                        drawProjectedLine(wx0 + 0.35f, midH + 0.03f, wz0, wx0 + 0.35f, midH + 0.03f, wz1, edgeCol, 1.5f, pose, focalPx, w, h)
                                        drawProjectedLine(wx1 - 0.35f, midH + 0.03f, wz0, wx1 - 0.35f, midH + 0.03f, wz1, edgeCol, 1.5f, pose, focalPx, w, h)
                                    } else if (hasRoadX) {
                                        drawProjectedLine(wx0, midH + 0.03f, wz0 + 0.35f, wx1, midH + 0.03f, wz0 + 0.35f, edgeCol, 1.5f, pose, focalPx, w, h)
                                        drawProjectedLine(wx0, midH + 0.03f, wz1 - 0.35f, wx1, midH + 0.03f, wz1 - 0.35f, edgeCol, 1.5f, pose, focalPx, w, h)
                                    }
                                }
                            }
                            SoilState.ARADO, SoilState.SUBSOLADO, SoilState.SEMEADO -> {
                                val troughCol = if (c00.soilState == SoilState.SUBSOLADO) {
                                    Color(0xFF1F120A).copy(alpha = 0.44f)
                                } else {
                                    Color(0xFF28180E).copy(alpha = 0.32f)
                                }
                                val crestCol = shadeColor(baseCellCol, 1.20f).copy(alpha = 0.38f)
                                val furrowFracs = if (gfx.textureResolutionScale >= 0.78) FURROW_FRACS_HI else FURROW_FRACS_LO
                                for (f in furrowFracs) {
                                    val fzPos = wz0 + cellSizeM * f
                                    drawProjectedLine(wx0 + 0.25f, midH + 0.035f, fzPos, wx1 - 0.25f, midH + 0.035f, fzPos, troughCol, if (c00.soilState == SoilState.SUBSOLADO) 2.8f else 2.3f, pose, focalPx, w, h)
                                    drawProjectedLine(wx0 + 0.25f, midH + 0.052f, fzPos + 0.16f, wx1 - 0.25f, midH + 0.052f, fzPos + 0.16f, crestCol, 1.5f, pose, focalPx, w, h)
                                }
                            }
                            SoilState.PALHADA -> {
                                if (gfx.textureResolutionScale >= 0.65) {
                                    val strawCol = Color(0xFFD6B96F).copy(alpha = 0.48f * twilightDarken)
                                    val strawShade = Color(0xFFA78A46).copy(alpha = 0.36f * twilightDarken)
                                    drawProjectedLine(wx0 + 0.3f, midH + 0.04f, wz0 + cellSizeM * 0.26f, wx1 - 0.3f, midH + 0.04f, wz0 + cellSizeM * 0.32f, strawCol, 2.5f, pose, focalPx, w, h)
                                    drawProjectedLine(wx0 + 0.4f, midH + 0.04f, wz0 + cellSizeM * 0.52f, wx1 - 0.3f, midH + 0.04f, wz0 + cellSizeM * 0.48f, strawShade, 2.0f, pose, focalPx, w, h)
                                    drawProjectedLine(wx0 + 0.4f, midH + 0.04f, wz0 + cellSizeM * 0.74f, wx1 - 0.4f, midH + 0.04f, wz0 + cellSizeM * 0.69f, strawCol, 2.2f, pose, focalPx, w, h)
                                }
                            }
                            SoilState.CRESCENDO, SoilState.PRONTO_COLHEITA -> {
                                if (vegDensity >= 0.28f && distM < 46f && c00.cropId != null) {
                                    drawCellCrops3D(
                                        cell = c00,
                                        wx0 = wx0,
                                        wz0 = wz0,
                                        cellSizeM = cellSizeM,
                                        midY = midH,
                                        baseCol = baseCellCol,
                                        twilight = twilightDarken,
                                        vegDensity = vegDensity,
                                        animClockSec = animClockSec,
                                        windStrength = atmo?.windStrength ?: 0.45f,
                                        pose = pose,
                                        focalPx = focalPx,
                                        w = w,
                                        h = h,
                                    )
                                }
                            }
                            SoilState.GRAMA_CORTADA -> {
                                if (vegDensity >= 0.32f && distM < 28f && ((gx + gz) and 1) == 0) {
                                    val cutCol = shadeColor(baseCellCol, 0.90f * twilightDarken)
                                    val bx = wx0 + cellSizeM * 0.45f
                                    val bz = wz0 + cellSizeM * 0.52f
                                    drawProjectedLine(bx - 0.16f, midH, bz, bx + 0.16f, midH, bz, cutCol, 1.8f, pose, focalPx, w, h)
                                }
                            }
                            SoilState.PASTAGEM -> {
                                if (vegDensity >= 0.42f && distM < 32f && ((gx + gz) and 1) == 0) {
                                    val wind = atmo?.windStrength ?: 0.5f
                                    val sway = sin(animClockSec * (1.8f + wind) + wx0 * 0.5f) * 0.09f * wind
                                    val tuftCol = shadeColor(baseCellCol, 1.16f * twilightDarken)
                                    val bx = wx0 + cellSizeM * 0.45f
                                    val bz = wz0 + cellSizeM * 0.52f
                                    val grassH = c00.grassHeightM.toFloat().coerceIn(0.18f, 0.65f)
                                    drawProjectedLine(bx, midH, bz, bx - 0.18f + sway, midH + grassH * 0.85f, bz, tuftCol, 2.0f, pose, focalPx, w, h)
                                    drawProjectedLine(bx, midH, bz, bx + 0.04f + sway, midH + grassH, bz, tuftCol, 2.2f, pose, focalPx, w, h)
                                    drawProjectedLine(bx, midH, bz, bx + 0.22f + sway, midH + grassH * 0.80f, bz, tuftCol, 1.8f, pose, focalPx, w, h)
                                }
                            }
                            else -> Unit
                        }

                        // Plantas daninhas individualizadas (Buva, Caruru, Capim-Amargoso) dentro das lavouras e pastos
                        if (vegDensity >= 0.25f && distM < 40f && (c00.weedLevel > 0.04 || c00.weedSpecies != null)) {
                            drawCellWeeds3D(
                                cell = c00,
                                wx0 = wx0,
                                wz0 = wz0,
                                cellSizeM = cellSizeM,
                                midY = midH,
                                twilight = twilightDarken,
                                animClockSec = animClockSec,
                                windStrength = atmo?.windStrength ?: 0.45f,
                                pose = pose,
                                focalPx = focalPx,
                                w = w,
                                h = h,
                            )
                        }
                    }
                }
            }
        }
    }

    if (maxDrawM > midRadiusM + 20f) {
        drawTerrainRing(gxMin, gxMax, gzMin, gzMax, stepFar, skipInnerNear = true, minDistSq = midRadiusM * midRadiusM, maxRingDistSq = maxDrawM * maxDrawM)
    }
    drawTerrainRing(gxMin, gxMax, gzMin, gzMax, stepMid, skipInnerNear = true, minDistSq = 0f, maxRingDistSq = midRadiusM * midRadiusM)
    drawTerrainRing(nearGxMin, nearGxMax, nearGzMin, nearGzMax, step = 1, skipInnerNear = false)
}

private fun DrawScope.drawCellCrops3D(
    cell: TerrainCell,
    wx0: Float,
    wz0: Float,
    cellSizeM: Float,
    midY: Float,
    baseCol: Color,
    twilight: Float,
    vegDensity: Float,
    animClockSec: Float,
    windStrength: Float,
    pose: CameraPose3D,
    focalPx: Float,
    w: Float,
    h: Float,
) {
    val cropId = cell.cropId ?: return
    val crop = getCropById(cropId)
    val ripe = cell.soilState == SoilState.PRONTO_COLHEITA
    val prog = if (ripe) 1.0f else cell.growthProgress.toFloat().coerceIn(0.14f, 1.0f)
    // 5-stage morphological development:
    // 1: sprouting (<0.25), 2: seedling (0.25..0.48), 3: vegetative (0.48..0.72), 4: flowering/grain-fill (0.72..0.92), 5: mature/ripe
    val stage = when {
        ripe || prog >= 0.92f -> 5
        prog >= 0.72f -> 4
        prog >= 0.48f -> 3
        prog >= 0.25f -> 2
        else -> 1
    }
    val growH = (crop.maxHeightMeters.toFloat().coerceIn(0.28f, 2.5f)) * (0.22f + prog * 0.78f)
    val youngGreen = shadeColor(lerpColor(crop.seedColorRgb.toComposeColor(), baseCol, 0.28f), twilight)
    val matureCol = shadeColor(crop.ripeColorRgb.toComposeColor(), twilight)
    val stalkCol = when (stage) {
        1, 2 -> youngGreen
        3 -> shadeColor(lerpColor(youngGreen, Color(0xFF2E7D32), 0.35f), twilight)
        4 -> shadeColor(lerpColor(youngGreen, matureCol, 0.48f), twilight)
        else -> matureCol
    }
    val count = when {
        stage <= 1 -> if (vegDensity > 0.65f) 3 else 2
        vegDensity > 0.72f -> 4
        else -> 2
    }
    val sway = sin(animClockSec * (2.0f + windStrength * 0.8f) + wx0 * 0.35f + wz0 * 0.25f) * (0.06f + windStrength * 0.04f) * prog

    for (i in 0 until count) {
        val fracX = 0.22f + (i % 2) * 0.52f
        val fracZ = 0.24f + (i / 2) * 0.50f
        val sx = wx0 + cellSizeM * fracX
        val sz = wz0 + cellSizeM * fracZ
        val topX = sx + sway
        val topY = midY + growH

        val pBot = projectRaw(sx, midY, sz, pose, focalPx, w, h)
        val pTop = projectRaw(topX, topY, sz, pose, focalPx, w, h)
        if (!pBot.visible || !pTop.visible) continue

        if (stage == 1) {
            // Stage 1: delicate twin cotyledon leaves emerging from furrow
            val sproutCol = shadeColor(Color(0xFF7CB342), twilight)
            drawLine(sproutCol, Offset(pBot.screenX, pBot.screenY), Offset(pTop.screenX, pTop.screenY), strokeWidth = 2.0f, cap = StrokeCap.Round)
            drawLine(sproutCol, Offset(pTop.screenX, pTop.screenY), Offset(pTop.screenX - 3.5f, pTop.screenY - 2f), strokeWidth = 1.6f, cap = StrokeCap.Round)
            drawLine(sproutCol, Offset(pTop.screenX, pTop.screenY), Offset(pTop.screenX + 3.5f, pTop.screenY - 2f), strokeWidth = 1.6f, cap = StrokeCap.Round)
            continue
        }

        when {
            cropId == "milho" || cropId == "sorgo" || cropId == "cana" -> {
                val stemW = 2.2f + stage * 0.35f
                drawLine(stalkCol, Offset(pBot.screenX, pBot.screenY), Offset(pTop.screenX, pTop.screenY), strokeWidth = stemW, cap = StrokeCap.Round)
                val midX = (pBot.screenX + pTop.screenX) * 0.5f
                val midScY = (pBot.screenY + pTop.screenY) * 0.52f
                val leafSpan = 4.5f + stage * 1.1f
                drawLine(stalkCol, Offset(midX, midScY), Offset(midX - leafSpan, midScY - 4f), strokeWidth = 2.0f, cap = StrokeCap.Round)
                drawLine(stalkCol, Offset(midX, midScY), Offset(midX + leafSpan, midScY - 3.5f), strokeWidth = 2.0f, cap = StrokeCap.Round)
                if (stage >= 4) {
                    val tasselCol = if (stage >= 5) Color(0xFFFFCA28) else Color(0xFFDCE775)
                    drawCircle(tasselCol.copy(alpha = 0.92f), radius = if (stage >= 5) 3.4f else 2.2f, center = Offset(pTop.screenX, pTop.screenY))
                }
            }
            cropId == "algodao" -> {
                drawLine(Color(0xFF4E342E).copy(alpha = 0.85f), Offset(pBot.screenX, pBot.screenY), Offset(pTop.screenX, pTop.screenY), strokeWidth = 2.5f)
                when (stage) {
                    2, 3 -> drawCircle(youngGreen, radius = 3.2f + stage * 0.5f, center = Offset(pTop.screenX, pTop.screenY))
                    4 -> {
                        drawCircle(youngGreen, radius = 4.4f, center = Offset(pTop.screenX, pTop.screenY))
                        drawCircle(Color(0xFFF8BBD0), radius = 2.2f, center = Offset(pTop.screenX + 2f, pTop.screenY - 2f))
                    }
                    else -> {
                        drawCircle(Color(0xFFFAFAFA), radius = 4.4f, center = Offset(pTop.screenX, pTop.screenY))
                        drawCircle(Color(0xFFF5F5F5), radius = 3.2f, center = Offset(pTop.screenX - 4f, pTop.screenY + 3.5f))
                    }
                }
            }
            cropId == "girassol" -> {
                drawLine(Color(0xFF33691E), Offset(pBot.screenX, pBot.screenY), Offset(pTop.screenX, pTop.screenY), strokeWidth = 2.8f)
                if (stage >= 3) {
                    val petalR = if (stage >= 5) 5.4f else if (stage == 4) 4.2f else 2.8f
                    drawCircle(if (stage >= 4) Color(0xFFFDD835) else Color(0xFF7CB342), radius = petalR, center = Offset(pTop.screenX, pTop.screenY))
                    if (stage >= 4) {
                        drawCircle(Color(0xFF5D4037), radius = petalR * 0.42f, center = Offset(pTop.screenX, pTop.screenY))
                    }
                }
            }
            cropId == "cafe" -> {
                drawLine(Color(0xFF3E2723), Offset(pBot.screenX, pBot.screenY), Offset(pTop.screenX, pTop.screenY), strokeWidth = 3.0f)
                drawCircle(Color(0xFF1B5E20), radius = (3.5f + stage * 0.8f), center = Offset(pTop.screenX, pTop.screenY))
                if (stage == 4) {
                    drawCircle(Color(0xFFFFFFFF), radius = 1.9f, center = Offset(pTop.screenX - 2f, pTop.screenY - 1f))
                    drawCircle(Color(0xFFFFFFFF), radius = 1.9f, center = Offset(pTop.screenX + 3f, pTop.screenY + 1f))
                } else if (stage >= 5) {
                    drawCircle(Color(0xFFD32F2F), radius = 2.2f, center = Offset(pTop.screenX - 2f, pTop.screenY - 1f))
                    drawCircle(Color(0xFFD32F2F), radius = 2.2f, center = Offset(pTop.screenX + 3f, pTop.screenY + 1f))
                }
            }
            cropId == "rosas_familiares" -> {
                drawLine(Color(0xFF2E6B30), Offset(pBot.screenX, pBot.screenY), Offset(pTop.screenX, pTop.screenY), strokeWidth = 2.4f)
                drawCircle(Color(0xFF388E3C), radius = 3.2f, center = Offset(pTop.screenX, pTop.screenY + 2f))
                if (stage >= 4) {
                    val budCol = if (stage >= 5) Color(0xFFE11D48) else Color(0xFFF472B6)
                    drawCircle(budCol, radius = if (stage >= 5) 3.4f else 2.2f, center = Offset(pTop.screenX, pTop.screenY - 1.5f))
                }
            }
            cropId in setOf("trigo", "arroz", "aveia", "cevada", "centeio", "triticale", "milheto") -> {
                drawLine(stalkCol, Offset(pBot.screenX, pBot.screenY), Offset(pTop.screenX, pTop.screenY), strokeWidth = 2.2f, cap = StrokeCap.Round)
                drawLine(stalkCol, Offset(pBot.screenX - 4f, pBot.screenY), Offset(pTop.screenX - 3f, pTop.screenY + 2f), strokeWidth = 1.9f, cap = StrokeCap.Round)
                if (stage >= 4) {
                    val headCol = if (stage >= 5) Color(0xFFF4D03F) else Color(0xFFAED581)
                    drawCircle(headCol, radius = if (stage >= 5) 2.9f else 2.1f, center = Offset(pTop.screenX, pTop.screenY))
                }
            }
            else -> {
                drawLine(stalkCol, Offset(pBot.screenX, pBot.screenY), Offset(pTop.screenX, pTop.screenY), strokeWidth = 2.5f, cap = StrokeCap.Round)
                drawCircle(stalkCol, radius = (2.2f + stage * 0.55f), center = Offset(pTop.screenX, pTop.screenY))
                if (stage >= 4) {
                    val podCol = if (stage >= 5) Color(0xFFE6C25A) else Color(0xFFAED581)
                    drawCircle(podCol, radius = 1.9f, center = Offset(pTop.screenX + 2.2f, pTop.screenY + 1.2f))
                }
            }
        }
    }
}

private fun DrawScope.drawCellWeeds3D(
    cell: TerrainCell,
    wx0: Float,
    wz0: Float,
    cellSizeM: Float,
    midY: Float,
    twilight: Float,
    animClockSec: Float,
    windStrength: Float,
    pose: CameraPose3D,
    focalPx: Float,
    w: Float,
    h: Float,
) {
    val species = cell.weedSpecies ?: return
    val stage = cell.weedStage
    val weedLvl = cell.weedLevel.toFloat().coerceIn(0.05f, 1.0f)
    val plantCount = when {
        weedLvl > 0.45f -> 3
        weedLvl > 0.18f -> 2
        else -> 1
    }
    val sway = sin(animClockSec * (2.2f + windStrength * 1.1f) + wx0 * 0.45f + wz0 * 0.35f) * (0.05f + windStrength * 0.05f)

    for (p in 0 until plantCount) {
        val offX = 0.28f + (p % 2) * 0.42f + (p * 0.11f)
        val offZ = 0.32f + (p / 2) * 0.36f + (p * 0.09f)
        val px = wx0 + cellSizeM * offX
        val pz = wz0 + cellSizeM * offZ

        when (species) {
            WeedSpecies.BUVA -> {
                // BUVA (Conyza spp.): Haste ereta alta, folhas estreitas, capítulos florais em penacho no ápice
                val buvaH = when (stage) {
                    WeedGrowthStage.PLANTULA -> 0.22f
                    WeedGrowthStage.VEGETATIVO -> 0.55f
                    WeedGrowthStage.FLORESCIMENTO -> 0.95f
                }
                val stemCol = shadeColor(Color(0xFF557750), twilight)
                val leafCol = shadeColor(Color(0xFF6B8762), twilight)
                val flowerCol = shadeColor(Color(0xFFEDE8BD), twilight)

                val botP = projectRaw(px, midY, pz, pose, focalPx, w, h)
                val topP = projectRaw(px + sway, midY + buvaH, pz, pose, focalPx, w, h)
                if (!botP.visible || !topP.visible) continue

                // Caule central reto
                drawLine(stemCol, Offset(botP.screenX, botP.screenY), Offset(topP.screenX, topP.screenY), strokeWidth = 2.0f, cap = StrokeCap.Round)

                // Folhas lineares estreitas laterais
                val midScX = (botP.screenX + topP.screenX) * 0.5f
                val midScY = (botP.screenY + topP.screenY) * 0.5f
                drawLine(leafCol, Offset(midScX, midScY), Offset(midScX - 4.5f, midScY - 2.0f), strokeWidth = 1.5f, cap = StrokeCap.Round)
                drawLine(leafCol, Offset(midScX, midScY), Offset(midScX + 4.5f, midScY - 1.8f), strokeWidth = 1.5f, cap = StrokeCap.Round)

                if (stage == WeedGrowthStage.FLORESCIMENTO) {
                    // Penacho floral apical ramificado
                    drawLine(flowerCol, Offset(topP.screenX, topP.screenY), Offset(topP.screenX - 4f, topP.screenY - 5f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                    drawLine(flowerCol, Offset(topP.screenX, topP.screenY), Offset(topP.screenX + 4f, topP.screenY - 5f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                    drawLine(flowerCol, Offset(topP.screenX, topP.screenY), Offset(topP.screenX, topP.screenY - 7f), strokeWidth = 2.4f, cap = StrokeCap.Round)
                }
            }
            WeedSpecies.CARURU -> {
                // CARURU (Amaranthus spp.): Caule espesso avermelhado na base, folhas largas ovaladas e espigas densas
                val caruruH = when (stage) {
                    WeedGrowthStage.PLANTULA -> 0.18f
                    WeedGrowthStage.VEGETATIVO -> 0.48f
                    WeedGrowthStage.FLORESCIMENTO -> 0.82f
                }
                val stemCol = shadeColor(Color(0xFF7A2E3E), twilight)
                val leafCol = shadeColor(Color(0xFF2E6325), twilight)
                val spikeCol = shadeColor(Color(0xFF883545), twilight)

                val botP = projectRaw(px, midY, pz, pose, focalPx, w, h)
                val topP = projectRaw(px + sway * 0.7f, midY + caruruH, pz, pose, focalPx, w, h)
                if (!botP.visible || !topP.visible) continue

                // Caule espesso e robusto
                drawLine(stemCol, Offset(botP.screenX, botP.screenY), Offset(topP.screenX, topP.screenY), strokeWidth = 2.8f, cap = StrokeCap.Round)

                // Folhas largas pecioladas
                val leafY1 = botP.screenY * 0.65f + topP.screenY * 0.35f
                val leafX1 = botP.screenX * 0.65f + topP.screenX * 0.35f
                drawLine(leafCol, Offset(leafX1, leafY1), Offset(leafX1 - 6.5f, leafY1 - 2.5f), strokeWidth = 2.5f, cap = StrokeCap.Round)
                drawLine(leafCol, Offset(leafX1, leafY1), Offset(leafX1 + 6.5f, leafY1 - 2.2f), strokeWidth = 2.5f, cap = StrokeCap.Round)

                if (stage == WeedGrowthStage.FLORESCIMENTO) {
                    // Espiga densa cilíndrica apical
                    drawLine(spikeCol, Offset(topP.screenX, topP.screenY), Offset(topP.screenX, topP.screenY - 9f), strokeWidth = 3.8f, cap = StrokeCap.Round)
                    drawLine(spikeCol, Offset(topP.screenX - 1.5f, topP.screenY - 2f), Offset(topP.screenX - 4.5f, topP.screenY - 6f), strokeWidth = 2.5f, cap = StrokeCap.Round)
                    drawLine(spikeCol, Offset(topP.screenX + 1.5f, topP.screenY - 2f), Offset(topP.screenX + 4.5f, topP.screenY - 6f), strokeWidth = 2.5f, cap = StrokeCap.Round)
                }
            }
            WeedSpecies.CAPIM_AMARGOSO -> {
                // CAPIM-AMARGOSO (Digitaria insularis): Touceira cespitosa densa, colmos múltiplos e panículas plumosas
                val capimH = when (stage) {
                    WeedGrowthStage.PLANTULA -> 0.16f
                    WeedGrowthStage.VEGETATIVO -> 0.40f
                    WeedGrowthStage.FLORESCIMENTO -> 0.70f
                }
                val clumpCol = shadeColor(Color(0xFF4C7A32), twilight)
                val bladeCol = shadeColor(Color(0xFF7AA347), twilight)
                val plumeCol = shadeColor(Color(0xFFEBEBE2), twilight)

                val botP = projectRaw(px, midY, pz, pose, focalPx, w, h)
                val topMid = projectRaw(px + sway * 1.2f, midY + capimH, pz, pose, focalPx, w, h)
                val topL = projectRaw(px - 0.16f + sway, midY + capimH * 0.88f, pz, pose, focalPx, w, h)
                val topR = projectRaw(px + 0.16f + sway, midY + capimH * 0.85f, pz, pose, focalPx, w, h)
                if (!botP.visible || !topMid.visible) continue

                // Folhas em leque arqueadas saindo da mesma coroa
                drawLine(clumpCol, Offset(botP.screenX, botP.screenY), Offset(topL.screenX, topL.screenY), strokeWidth = 2.0f, cap = StrokeCap.Round)
                drawLine(bladeCol, Offset(botP.screenX, botP.screenY), Offset(topMid.screenX, topMid.screenY), strokeWidth = 2.2f, cap = StrokeCap.Round)
                drawLine(clumpCol, Offset(botP.screenX, botP.screenY), Offset(topR.screenX, topR.screenY), strokeWidth = 2.0f, cap = StrokeCap.Round)

                if (stage == WeedGrowthStage.FLORESCIMENTO) {
                    // Panículas sedosas plumosas esbranquiçadas
                    drawLine(plumeCol, Offset(topMid.screenX, topMid.screenY), Offset(topMid.screenX + 2.5f + sway * 8f, topMid.screenY - 6f), strokeWidth = 2.4f, cap = StrokeCap.Round)
                    drawLine(plumeCol, Offset(topL.screenX, topL.screenY), Offset(topL.screenX - 2.5f + sway * 6f, topL.screenY - 5f), strokeWidth = 2.0f, cap = StrokeCap.Round)
                    drawLine(plumeCol, Offset(topR.screenX, topR.screenY), Offset(topR.screenX + 3.0f + sway * 6f, topR.screenY - 5f), strokeWidth = 2.0f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

internal fun DrawScope.drawTree3D(
    item: RenderItem3D,
    pose: CameraPose3D,
    focalPx: Float,
    w: Float,
    h: Float,
    light: Float,
    animClockSec: Float,
    detailLevel: Int,
    scratch: ViewportScratch3D,
    atmo: SunAtmosphereState? = null,
) {
    val sizeVar = ((item.prop?.uid?.hashCode() ?: 0) and 3) * 0.12f
    val scale = (0.95f + sizeVar).coerceIn(0.85f, 1.35f)
    val tx = item.x.toFloat()
    val ty = item.y.toFloat()
    val tz = item.z.toFloat()
    val pType = item.prop?.type
    val segs = if (detailLevel >= 1) 12 else 8
    val wind = atmo?.windStrength ?: 0.5f
    val sway = sin(animClockSec * (1.3f + wind * 0.6f) + tx * 0.2f) * (0.07f + wind * 0.04f)

    if (atmo != null) {
        drawProjectedSunShadow(
            cx = tx, groundY = ty, cz = tz,
            halfW = 1.45f * scale, halfL = 1.45f * scale, heightM = 4.4f * scale,
            fx = 0f, fz = 1f, rx = 1f, rz = 0f,
            atmo = atmo, pose = pose, focalPx = focalPx, viewW = w, viewH = h, scratch = scratch,
        )
    } else {
        drawRotatedBox3D(tx + 0.65f, ty + 0.03f, tz + 0.65f, 1.35f * scale, 0.01f, 1.35f * scale, 0f, 1f, 1f, 0f, Color.Black.copy(alpha = 0.28f), 1f, pose, focalPx, w, h, scratch)
    }

    when (pType) {
        WorldPropType.TREE_ARAUCARIA -> {
            val trunkH = 4.1f * scale
            drawCylinderY3D(tx, ty, ty + trunkH, tz, 0.24f * scale, Color(0xFF533A2E), light, segs, pose, focalPx, w, h, scratch, atmo, MaterialType.WOOD_TIMBER)
            drawEllipsoidDome3D(tx + sway, ty + trunkH + 0.20f * scale, tz, 2.30f * scale, 0.62f * scale, Color(0xFF184A2C), light, segs, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
            drawEllipsoidDome3D(tx + sway, ty + trunkH + 0.72f * scale, tz, 1.45f * scale, 0.45f * scale, Color(0xFF21623B), light, segs, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
        }
        WorldPropType.TREE_CERRADO -> {
            val trunkH = 1.85f * scale
            drawCylinderY3D(tx, ty, ty + trunkH, tz, 0.25f * scale, Color(0xFF6B4E35), light, segs, pose, focalPx, w, h, scratch, atmo, MaterialType.WOOD_TIMBER)
            drawEllipsoidDome3D(tx + sway, ty + trunkH + 0.75f * scale, tz, 1.82f * scale, 0.95f * scale, Color(0xFF4B702D), light, segs, pose, focalPx, w, h, scratch, atmo = atmo, material = MaterialType.FOLIAGE)
            drawEllipsoidDome3D(tx + 0.35f * scale + sway, ty + trunkH + 1.25f * scale, tz, 1.25f * scale, 0.72f * scale, Color(0xFF60883B), light, segs, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
        }
        WorldPropType.TREE_PALM_AMAZON -> {
            val trunkH = 4.3f * scale
            drawCylinderY3D(tx, ty, ty + trunkH, tz, 0.17f * scale, Color(0xFF6D5544), light, segs, pose, focalPx, w, h, scratch, atmo, MaterialType.WOOD_TIMBER)
            drawEllipsoidDome3D(tx + sway, ty + trunkH + 0.32f * scale, tz, 1.95f * scale, 0.55f * scale, Color(0xFF277B3B), light, segs, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
            drawEllipsoidDome3D(tx + sway, ty + trunkH + 0.52f * scale, tz, 1.15f * scale, 0.38f * scale, Color(0xFF2E8C45), light, segs, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
        }
        WorldPropType.TREE_PINE -> {
            val trunkH = 2.1f * scale
            drawCylinderY3D(tx, ty, ty + trunkH, tz, 0.22f * scale, Color(0xFF5D4037), light, segs, pose, focalPx, w, h, scratch, atmo, MaterialType.WOOD_TIMBER)
            drawConeY3D(tx + sway, ty + trunkH * 0.7f, ty + trunkH + 2.6f * scale, tz, 1.55f * scale, Color(0xFF194B2B), light, segs, pose, focalPx, w, h, scratch, atmo, MaterialType.FOLIAGE)
            drawConeY3D(tx + sway, ty + trunkH + 1.4f * scale, ty + trunkH + 3.8f * scale, tz, 1.05f * scale, Color(0xFF226139), light, segs, pose, focalPx, w, h, scratch, atmo, MaterialType.FOLIAGE)
        }
        WorldPropType.TREE_IPE_AMARELO -> {
            val trunkH = 2.6f * scale
            drawCylinderY3D(tx, ty, ty + trunkH, tz, 0.24f * scale, Color(0xFF5D4037), light, segs, pose, focalPx, w, h, scratch, atmo, MaterialType.WOOD_TIMBER)
            drawEllipsoidDome3D(tx + sway, ty + trunkH + 0.95f * scale, tz, 1.85f * scale, 1.15f * scale, Color(0xFFE5B526), light, segs, pose, focalPx, w, h, scratch, atmo = atmo, material = MaterialType.FOLIAGE)
            drawEllipsoidDome3D(tx + 0.35f * scale + sway, ty + trunkH + 1.45f * scale, tz - 0.25f * scale, 1.32f * scale, 0.78f * scale, Color(0xFFF6CE46), light, segs, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
        }
        WorldPropType.TREE_IPE_ROXO -> {
            val trunkH = 2.5f * scale
            drawCylinderY3D(tx, ty, ty + trunkH, tz, 0.23f * scale, Color(0xFF5A3F32), light, segs, pose, focalPx, w, h, scratch, atmo, MaterialType.WOOD_TIMBER)
            drawEllipsoidDome3D(tx + sway, ty + trunkH + 0.92f * scale, tz, 1.78f * scale, 1.12f * scale, Color(0xFFA3348E), light, segs, pose, focalPx, w, h, scratch, atmo = atmo, material = MaterialType.FOLIAGE)
            drawEllipsoidDome3D(tx - 0.32f * scale + sway, ty + trunkH + 1.42f * scale, tz + 0.20f * scale, 1.24f * scale, 0.74f * scale, Color(0xFFC854B4), light, segs, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
        }
        WorldPropType.TREE_SHADE_MANGUEIRA -> {
            val trunkH = 2.4f * scale
            drawCylinderY3D(tx, ty, ty + trunkH, tz, 0.34f * scale, Color(0xFF4D3428), light, segs, pose, focalPx, w, h, scratch, atmo, MaterialType.WOOD_TIMBER)
            drawEllipsoidDome3D(tx + sway, ty + trunkH + 1.05f * scale, tz, 2.55f * scale, 1.35f * scale, Color(0xFF1F5A26), light, segs, pose, focalPx, w, h, scratch, atmo = atmo, material = MaterialType.FOLIAGE)
            drawEllipsoidDome3D(tx + 0.65f * scale + sway, ty + trunkH + 1.45f * scale, tz + 0.35f * scale, 1.70f * scale, 0.95f * scale, Color(0xFF2D7434), light, segs, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
        }
        WorldPropType.TREE_COQUEIRO -> {
            val trunkH = 5.2f * scale
            drawCylinderY3D(tx, ty, ty + trunkH, tz, 0.16f * scale, Color(0xFF6B5A49), light, segs, pose, focalPx, w, h, scratch, atmo, MaterialType.WOOD_TIMBER)
            drawEllipsoidDome3D(tx + sway * 0.5f, ty + trunkH - 0.20f * scale, tz, 0.32f * scale, 0.25f * scale, Color(0xFF738A3A), light, segs, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
            drawEllipsoidDome3D(tx + sway, ty + trunkH + 0.35f * scale, tz, 2.70f * scale, 0.65f * scale, Color(0xFF1B6A2E), light, segs, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
            drawEllipsoidDome3D(tx + sway, ty + trunkH + 0.65f * scale, tz, 1.80f * scale, 0.45f * scale, Color(0xFF27823C), light, segs, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
        }
        WorldPropType.TREE_CAPOEIRA -> {
            val trunkH = 2.8f * scale
            drawCylinderY3D(tx - 0.22f * scale, ty, ty + trunkH, tz - 0.15f * scale, 0.16f * scale, Color(0xFF4A382A), light, segs, pose, focalPx, w, h, scratch, atmo, MaterialType.WOOD_TIMBER)
            drawCylinderY3D(tx + 0.20f * scale, ty, ty + trunkH * 0.88f, tz + 0.18f * scale, 0.14f * scale, Color(0xFF564334), light, segs, pose, focalPx, w, h, scratch, atmo, MaterialType.WOOD_TIMBER)
            drawEllipsoidDome3D(tx + sway, ty + trunkH + 0.55f * scale, tz, 2.45f * scale, 1.25f * scale, Color(0xFF1D5428), light, segs, pose, focalPx, w, h, scratch, atmo = atmo, material = MaterialType.FOLIAGE)
            drawEllipsoidDome3D(tx + 0.45f * scale + sway, ty + trunkH + 1.15f * scale, tz - 0.30f * scale, 1.65f * scale, 0.85f * scale, Color(0xFF2E6E36), light, segs, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
        }
        else -> {
            val trunkH = if (pType == WorldPropType.TREE_EUCALYPTUS) 3.5f * scale else 2.2f * scale
            drawCylinderY3D(tx, ty, ty + trunkH, tz, 0.24f * scale, Color(0xFF5D4037), light, segs, pose, focalPx, w, h, scratch, atmo, MaterialType.WOOD_TIMBER)
            val leafCol = when (pType) {
                WorldPropType.TREE_EUCALYPTUS -> Color(0xFF336947)
                WorldPropType.TREE_FRUIT -> Color(0xFF2D7A32)
                else -> Color(0xFF1E622A)
            }
            val domeRY = if (pType == WorldPropType.TREE_EUCALYPTUS) 1.85f * scale else 1.42f * scale
            drawEllipsoidDome3D(tx + sway, ty + trunkH + 0.95f * scale, tz, 1.58f * scale, domeRY, leafCol, light, segs, pose, focalPx, w, h, scratch, atmo = atmo, material = MaterialType.FOLIAGE)
            drawEllipsoidDome3D(tx + sway - 0.35f * scale, ty + trunkH + 1.45f * scale, tz + 0.25f * scale, 1.05f * scale, 0.88f * scale, leafCol.copy(green = min(1f, leafCol.green * 1.12f)), light, segs, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
        }
    }
}

internal fun DrawScope.drawProp3D(
    item: RenderItem3D,
    pose: CameraPose3D,
    focalPx: Float,
    w: Float,
    h: Float,
    light: Float,
    scratch: ViewportScratch3D,
    atmo: SunAtmosphereState? = null,
) {
    val pr = item.prop ?: return
    val px = item.x.toFloat()
    val py = item.y.toFloat()
    val pz = item.z.toFloat()
    when (pr.type) {
        WorldPropType.CACTUS_MANDACARU -> {
            val col = Color(0xFF3E7C3A)
            if (atmo != null) {
                drawProjectedSunShadow(px, py, pz, 0.50f, 0.50f, 1.7f, 0f, 1f, 1f, 0f, atmo, pose, focalPx, w, h, scratch)
            } else {
                drawRotatedBox3D(px, py + 0.02f, pz, 0.48f, 0.01f, 0.48f, 0f, 1f, 1f, 0f, Color.Black.copy(alpha = 0.25f), 1f, pose, focalPx, w, h, scratch)
            }
            drawCylinderY3D(px, py, py + 1.70f, pz, 0.17f, col, light, 10, pose, focalPx, w, h, scratch, atmo, MaterialType.FOLIAGE)
            drawRotatedBox3D(px - 0.34f, py + 0.92f, pz, 0.22f, 0.11f, 0.12f, 0f, 1f, 1f, 0f, col, light, pose, focalPx, w, h, scratch, atmo, MaterialType.FOLIAGE)
            drawCylinderY3D(px - 0.48f, py + 0.92f, py + 1.60f, pz, 0.12f, col, light, 8, pose, focalPx, w, h, scratch, atmo, MaterialType.FOLIAGE)
            drawRotatedBox3D(px + 0.34f, py + 0.78f, pz, 0.22f, 0.11f, 0.12f, 0f, 1f, 1f, 0f, col, light, pose, focalPx, w, h, scratch, atmo, MaterialType.FOLIAGE)
            drawCylinderY3D(px + 0.48f, py + 0.78f, py + 1.50f, pz, 0.12f, col, light, 8, pose, focalPx, w, h, scratch, atmo, MaterialType.FOLIAGE)
        }
        WorldPropType.BANANA_CLUMP -> {
            val stemCol = Color(0xFF4A6B32)
            val leafCol = Color(0xFF3C8838)
            for (dx in listOf(-0.26f, 0.24f, 0f)) {
                val dz = if (dx == 0f) 0.25f else -0.15f
                drawCylinderY3D(px + dx, py, py + 1.85f, pz + dz, 0.12f, stemCol, light, 8, pose, focalPx, w, h, scratch, atmo, MaterialType.FOLIAGE)
                drawEllipsoidDome3D(px + dx, py + 2.05f, pz + dz, 0.95f, 0.35f, leafCol, light, 8, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
            }
        }
        WorldPropType.FLOWER_BED_AZALEA -> {
            drawEllipsoidDome3D(px, py + 0.38f, pz, 0.85f, 0.38f, Color(0xFF28692F), light, 10, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
            drawEllipsoidDome3D(px - 0.25f, py + 0.56f, pz, 0.45f, 0.24f, Color(0xFFD61F78), light, 8, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
            drawEllipsoidDome3D(px + 0.28f, py + 0.52f, pz + 0.12f, 0.42f, 0.22f, Color(0xFFE84A96), light, 8, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
        }
        WorldPropType.ORNAMENTAL_HEDGE -> {
            drawRotatedBox3D(px, py + 0.42f, pz, 1.35f, 0.42f, 0.42f, 0f, 1f, 1f, 0f, Color(0xFF2B6830), light, pose, focalPx, w, h, scratch, atmo, MaterialType.FOLIAGE)
        }
        WorldPropType.COFFEE_SHRUB -> {
            drawEllipsoidDome3D(px, py + 0.55f, pz, 0.62f, 0.52f, Color(0xFF1D5628), light, 10, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
            drawRotatedBox3D(px + 0.20f, py + 0.72f, pz + 0.12f, 0.09f, 0.07f, 0.09f, 0f, 1f, 1f, 0f, Color(0xFFC62828), light, pose, focalPx, w, h, scratch, atmo, MaterialType.FOLIAGE)
        }
        WorldPropType.ROSE_CLUSTER -> {
            drawEllipsoidDome3D(px, py + 0.42f, pz, 0.54f, 0.38f, Color(0xFF28662D), light, 10, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
            drawEllipsoidDome3D(px, py + 0.68f, pz, 0.26f, 0.16f, Color(0xFFE11D48), light, 8, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
        }
        WorldPropType.BUSH, WorldPropType.PLANT_CLUSTER -> {
            drawEllipsoidDome3D(px, py + 0.35f, pz, 0.62f, 0.36f, Color(0xFF2D6A32), light, 10, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
        }
        WorldPropType.BALE_ROUND -> {
            if (atmo != null) drawProjectedSunShadow(px, py, pz, 0.65f, 0.65f, 1.15f, 0f, 1f, 1f, 0f, atmo, pose, focalPx, w, h, scratch)
            drawCylinderWheel3D(px, py + 0.58f, pz, 0.58f, 0.55f, 0f, 0f, 1f, 1f, 0f, Color(0xFFD7AD46), Color(0xFFC59932), light, 14, pose, focalPx, w, h, scratch, atmo = atmo, drawBolts = false)
        }
        WorldPropType.BALE_RECT, WorldPropType.SILAGE_BAG -> {
            val col = if (pr.type == WorldPropType.SILAGE_BAG) Color(0xFFE8ECEF) else Color(0xFFD7AD46)
            if (atmo != null) drawProjectedSunShadow(px, py, pz, 0.62f, 0.96f, 0.95f, 0f, 1f, 1f, 0f, atmo, pose, focalPx, w, h, scratch)
            drawRotatedBox3D(px, py + 0.45f, pz, 0.55f, 0.45f, 0.92f, 0f, 1f, 1f, 0f, col, light, pose, focalPx, w, h, scratch, atmo, MaterialType.WOOD_TIMBER)
        }
        WorldPropType.UTILITY_POLE -> {
            if (atmo != null) drawProjectedSunShadow(px, py, pz, 0.38f, 0.38f, 5.8f, 0f, 1f, 1f, 0f, atmo, pose, focalPx, w, h, scratch)
            drawCylinderY3D(px, py, py + 5.8f, pz, 0.14f, Color(0xFF5D4037), light, 10, pose, focalPx, w, h, scratch, atmo, MaterialType.WOOD_TIMBER)
            drawRotatedBox3D(px, py + 5.45f, pz, 0.95f, 0.09f, 0.09f, 0f, 1f, 1f, 0f, Color(0xFF4E342E), light, pose, focalPx, w, h, scratch, atmo, MaterialType.WOOD_TIMBER)
            // Streetlamp arm & warm yard luminaire glow at night
            drawRotatedBox3D(px + 0.55f, py + 5.65f, pz, 0.48f, 0.05f, 0.05f, 0f, 1f, 1f, 0f, Color(0xFF78909C), light, pose, focalPx, w, h, scratch, atmo, MaterialType.GALVANIZED_METAL)
            val nightGlow = atmo?.isNight == true || light < 0.65f
            val lampCol = if (nightGlow) Color(0xFFFFEA70) else Color(0xFFCFD8DC)
            drawRotatedBox3D(px + 0.96f, py + 5.56f, pz, 0.16f, 0.08f, 0.12f, 0f, 1f, 1f, 0f, lampCol, 1.0f, pose, focalPx, w, h, scratch)
            if (nightGlow) {
                drawProjectedGroundGlow(
                    cx = px + 0.96f, groundY = py, cz = pz - 2.6f,
                    nearHalfW = 2.6f, farHalfW = 2.6f, reachM = 5.2f,
                    fx = 0f, fz = 1f, rx = 1f, rz = 0f,
                    glowColor = Color(0xFFFFD54F).copy(alpha = 0.20f),
                    pose = pose, focalPx = focalPx, viewW = w, viewH = h, scratch = scratch,
                )
            }
        }
        WorldPropType.LOG -> {
            drawCylinderWheel3D(px, py + 0.24f, pz, 0.24f, 1.32f, 0f, 1f, 0f, 0f, 1f, Color(0xFF6D4C41), Color(0xFFA1785B), light, 10, pose, focalPx, w, h, scratch, atmo = atmo, drawBolts = false)
        }
        WorldPropType.WEED_BUVA -> {
            drawCylinderY3D(px, py, py + 0.72f, pz, 0.04f, Color(0xFF536F52), light, 8, pose, focalPx, w, h, scratch, atmo, MaterialType.FOLIAGE)
            drawEllipsoidDome3D(px, py + 0.45f, pz, 0.24f, 0.35f, Color(0xFF5D7B58), light, 8, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
            drawEllipsoidDome3D(px, py + 0.75f, pz, 0.14f, 0.12f, Color(0xFFD6D3B6), light, 8, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
        }
        WorldPropType.WEED_CARURU -> {
            drawCylinderY3D(px, py, py + 0.78f, pz, 0.06f, Color(0xFF6E332E), light, 8, pose, focalPx, w, h, scratch, atmo, MaterialType.FOLIAGE)
            drawEllipsoidDome3D(px, py + 0.48f, pz, 0.32f, 0.36f, Color(0xFF436538), light, 8, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
            drawCylinderY3D(px, py + 0.78f, py + 0.98f, pz, 0.08f, Color(0xFF5E272E), light, 8, pose, focalPx, w, h, scratch, atmo, MaterialType.FOLIAGE)
        }
        WorldPropType.WEED_CAPIM_AMARGOSO -> {
            drawEllipsoidDome3D(px, py + 0.42f, pz, 0.44f, 0.48f, Color(0xFF75853B), light, 8, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
            drawEllipsoidDome3D(px, py + 0.72f, pz, 0.28f, 0.22f, Color(0xFFE2E6D4), light, 8, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
        }
        WorldPropType.TALL_GRASS_PASTURE -> {
            drawEllipsoidDome3D(px, py + 0.45f, pz, 0.58f, 0.55f, Color(0xFF40722A), light, 10, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
            drawEllipsoidDome3D(px + 0.15f, py + 0.65f, pz - 0.12f, 0.38f, 0.32f, Color(0xFF5B923E), light, 8, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
        }
        WorldPropType.RURAL_DRUM_BARREL -> {
            if (atmo != null) drawProjectedSunShadow(px, py, pz, 0.35f, 0.35f, 0.95f, 0f, 1f, 1f, 0f, atmo, pose, focalPx, w, h, scratch)
            drawCylinderY3D(px, py, py + 0.92f, pz, 0.29f, Color(0xFF1565C0), light, 12, pose, focalPx, w, h, scratch, atmo, MaterialType.GLOSS_PAINT)
            drawRotatedBox3D(px, py + 0.92f, pz, 0.30f, 0.02f, 0.30f, 0f, 1f, 1f, 0f, Color(0xFF0D47A1), light, pose, focalPx, w, h, scratch, atmo, MaterialType.GALVANIZED_METAL)
        }
        WorldPropType.RURAL_WATER_TANK -> {
            if (atmo != null) drawProjectedSunShadow(px, py, pz, 0.72f, 0.72f, 1.85f, 0f, 1f, 1f, 0f, atmo, pose, focalPx, w, h, scratch)
            drawRotatedBox3D(px, py + 0.35f, pz, 0.75f, 0.35f, 0.75f, 0f, 1f, 1f, 0f, Color(0xFF78909C), light, pose, focalPx, w, h, scratch, atmo, MaterialType.CONCRETE_STONE)
            drawCylinderY3D(px, py + 0.70f, py + 1.85f, pz, 0.62f, Color(0xFF0277BD), light, 16, pose, focalPx, w, h, scratch, atmo, MaterialType.GLOSS_PAINT)
            drawRotatedBox3D(px, py + 1.87f, pz, 0.65f, 0.04f, 0.65f, 0f, 1f, 1f, 0f, Color(0xFF01579B), light, pose, focalPx, w, h, scratch)
        }
        WorldPropType.WOOD_PILE -> {
            if (atmo != null) drawProjectedSunShadow(px, py, pz, 0.65f, 1.15f, 0.75f, 0f, 1f, 1f, 0f, atmo, pose, focalPx, w, h, scratch)
            drawRotatedBox3D(px, py + 0.38f, pz, 0.65f, 0.38f, 1.15f, 0f, 1f, 1f, 0f, Color(0xFF5D4037), light, pose, focalPx, w, h, scratch, atmo, MaterialType.WOOD_TIMBER)
            for (offsetZ in listOf(-0.65f, 0f, 0.65f)) {
                drawCylinderWheel3D(px, py + 0.22f, pz + offsetZ, 0.20f, 0.30f, 0f, 1f, 0f, 0f, 1f, Color(0xFF6D4C41), Color(0xFFA1785B), light, 8, pose, focalPx, w, h, scratch, atmo = atmo, drawBolts = false)
                drawCylinderWheel3D(px, py + 0.52f, pz + offsetZ, 0.18f, 0.30f, 0f, 1f, 0f, 0f, 1f, Color(0xFF6D4C41), Color(0xFFA1785B), light, 8, pose, focalPx, w, h, scratch, atmo = atmo, drawBolts = false)
            }
        }
        WorldPropType.TREE_STUMP -> {
            drawCylinderY3D(px, py, py + 0.38f, pz, 0.32f, Color(0xFF4E342E), light, 10, pose, focalPx, w, h, scratch, atmo, MaterialType.WOOD_TIMBER)
            drawRotatedBox3D(px, py + 0.39f, pz, 0.30f, 0.01f, 0.30f, 0f, 1f, 1f, 0f, Color(0xFFBCAAA4), light, pose, focalPx, w, h, scratch, atmo, MaterialType.WOOD_TIMBER)
        }
        WorldPropType.RURAL_FENCE_GATE -> {
            if (atmo != null) drawProjectedSunShadow(px, py, pz, 0.15f, 1.65f, 1.45f, 0f, 1f, 1f, 0f, atmo, pose, focalPx, w, h, scratch)
            drawCylinderY3D(px, py, py + 1.55f, pz - 1.55f, 0.12f, Color(0xFFE0E0E0), light, 8, pose, focalPx, w, h, scratch, atmo, MaterialType.CONCRETE_STONE)
            drawCylinderY3D(px, py, py + 1.55f, pz + 1.55f, 0.12f, Color(0xFFE0E0E0), light, 8, pose, focalPx, w, h, scratch, atmo, MaterialType.CONCRETE_STONE)
            for (step in 1..4) {
                val barY = py + 0.28f * step
                drawRotatedBox3D(px, barY, pz, 0.06f, 0.07f, 1.48f, 0f, 1f, 1f, 0f, Color(0xFF8D6E63), light, pose, focalPx, w, h, scratch, atmo, MaterialType.WOOD_TIMBER)
            }
            drawRotatedBox3D(px, py + 0.72f, pz, 0.05f, 0.06f, 1.25f, 0.35f, 0.93f, 0.93f, -0.35f, Color(0xFF795548), light, pose, focalPx, w, h, scratch, atmo, MaterialType.WOOD_TIMBER)
        }
        WorldPropType.ROAD_DITCH -> {
            drawRotatedBox3D(px, py + 0.08f, pz, 0.45f, 0.08f, 1.85f, 0f, 1f, 1f, 0f, Color(0xFF6B4528), light, pose, focalPx, w, h, scratch, atmo, MaterialType.DIRT_MUD)
            drawEllipsoidDome3D(px + 0.25f, py + 0.15f, pz, 0.22f, 0.16f, Color(0xFF386822), light, 8, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.FOLIAGE)
        }
        else -> {
            drawEllipsoidDome3D(px, py + 0.26f, pz, 0.52f, 0.34f, Color(0xFF7B8580), light, 10, pose, focalPx, w, h, scratch, rings = 2, atmo = atmo, material = MaterialType.CONCRETE_STONE)
        }
    }
}

internal fun DrawScope.drawCharacter3D(
    item: RenderItem3D,
    pose: CameraPose3D,
    focalPx: Float,
    w: Float,
    h: Float,
    light: Float,
    animClockSec: Float,
    scratch: ViewportScratch3D,
) {
    val px = item.x.toFloat()
    val py = item.y.toFloat()
    val pz = item.z.toFloat()
    val rad = item.headingDeg.toFloat() * (PI / 180.0).toFloat()
    val fx = sin(rad)
    val fz = cos(rad)
    val rx = cos(rad)
    val rz = -sin(rad)

    val app = item.playerAppearance
    val isStyledPlayer = item.kind == ItemKind3D.PLAYER
    val cfg = app ?: DEFAULT_PLAYER_APPEARANCE

    val shirtCol = if (isStyledPlayer) parseSafeColor(cfg.shirtColorHex, Color(0xFF1E2124)) else item.labelColor
    val pantsCol = if (isStyledPlayer) parseSafeColor(cfg.pantsColorHex, Color(0xFF363D2A)) else Color(0xFF263E57)
    val bootCol = if (isStyledPlayer) parseSafeColor(cfg.bootColorHex, Color(0xFF5C4332)) else Color(0xFF3E2723)
    val skinCol = if (isStyledPlayer) parseSafeColor(cfg.skinColorHex, Color(0xFFE4B898)) else item.altColor
    val hairCol = if (isStyledPlayer) parseSafeColor(cfg.hairColorHex, Color(0xFF5A2A22)) else Color(0xFF2B2118)
    val collarCol = parseSafeColor(cfg.collarHex, Color(0xFFDDD5C5))
    val glovesCol = cfg.glovesHex?.let { parseSafeColor(it, Color(0xFFE8E8E4)) }
    val outfitStyle = cfg.outfitStyle ?: "jacket_shearling"
    val hairStyle = cfg.hairStyle ?: "bob"
    val glassesStyle = cfg.glassesStyle ?: "sunglasses"
    val beardStyle = cfg.beardStyle ?: "none"

    // Ground shadow
    drawRotatedBox3D(px, py + 0.02f, pz, 0.36f, 0.01f, 0.36f, fx, fz, rx, rz, Color.Black.copy(alpha = 0.32f), 1f, pose, focalPx, w, h, scratch)

    val legSwing = sin(animClockSec * 6f) * 0.07f
    val armSwing = -legSwing * 0.8f

    // Boots (tall knee-high black riding boots for preset_3 flannel_plaid; ankle shoes otherwise)
    val isTallBoot = isStyledPlayer && outfitStyle == "flannel_plaid"
    val bootH = if (isTallBoot) 0.24f else 0.09f
    drawRotatedBox3D(px - rx * 0.11f + fx * legSwing, py + bootH, pz - rz * 0.11f + fz * legSwing, 0.085f, bootH, 0.12f, fx, fz, rx, rz, bootCol, light, pose, focalPx, w, h, scratch)
    drawRotatedBox3D(px + rx * 0.11f - fx * legSwing, py + bootH, pz + rz * 0.11f - fz * legSwing, 0.085f, bootH, 0.12f, fx, fz, rx, rz, bootCol, light, pose, focalPx, w, h, scratch)

    // Upper Legs / Pants
    val legBot = bootH * 2f
    val legH = (0.86f - legBot) * 0.5f
    val legMidY = legBot + legH
    drawRotatedBox3D(px - rx * 0.105f + fx * legSwing, py + legMidY, pz - rz * 0.105f + fz * legSwing, 0.092f, legH, 0.105f, fx, fz, rx, rz, pantsCol, light, pose, focalPx, w, h, scratch)
    drawRotatedBox3D(px + rx * 0.105f - fx * legSwing, py + legMidY, pz + rz * 0.105f - fz * legSwing, 0.092f, legH, 0.105f, fx, fz, rx, rz, pantsCol, light, pose, focalPx, w, h, scratch)

    // Torso / Jacket / Overcoat / Hoodie
    when {
        isStyledPlayer && outfitStyle == "overcoat" -> {
            // Long knee-length light-gray coat + dark lapel
            drawTiltedBox3D(px, py + 1.00f, pz, 0.25f, 0.36f, 0.16f, fx, fz, rx, rz, 0f, 0f, shirtCol, light, pose, focalPx, w, h, scratch, taperTopFactor = 0.92f)
            drawRotatedBox3D(px + fx * 0.04f, py + 1.33f, pz + fz * 0.04f, 0.17f, 0.06f, 0.15f, fx, fz, rx, rz, collarCol, light, pose, focalPx, w, h, scratch)
        }
        isStyledPlayer && outfitStyle == "flannel_plaid" -> {
            // Red/brown plaid flannel jacket + tan shearling collar + inner placket
            drawTiltedBox3D(px, py + 1.12f, pz, 0.23f, 0.27f, 0.15f, fx, fz, rx, rz, 0f, 0f, shirtCol, light, pose, focalPx, w, h, scratch, taperTopFactor = 0.94f)
            drawRotatedBox3D(px + fx * 0.152f, py + 1.12f, pz + fz * 0.152f, 0.06f, 0.23f, 0.015f, fx, fz, rx, rz, Color(0xFF4E2A22), light, pose, focalPx, w, h, scratch)
            drawRotatedBox3D(px + fx * 0.03f, py + 1.37f, pz + fz * 0.03f, 0.19f, 0.055f, 0.15f, fx, fz, rx, rz, collarCol, light, pose, focalPx, w, h, scratch)
        }
        isStyledPlayer && outfitStyle == "hoodie" -> {
            // Dusty rose hoodie + hood collar + pocket
            drawTiltedBox3D(px, py + 1.12f, pz, 0.22f, 0.27f, 0.15f, fx, fz, rx, rz, 0f, 0f, shirtCol, light, pose, focalPx, w, h, scratch, taperTopFactor = 0.92f)
            drawRotatedBox3D(px - fx * 0.04f, py + 1.37f, pz - fz * 0.04f, 0.18f, 0.06f, 0.15f, fx, fz, rx, rz, collarCol, light, pose, focalPx, w, h, scratch)
        }
        else -> {
            // Jacket shearling (preset_1 Clara): black jacket + white shirt center front + cream fleece collar
            drawTiltedBox3D(px, py + 1.12f, pz, 0.22f, 0.27f, 0.15f, fx, fz, rx, rz, 0f, 0f, shirtCol, light, pose, focalPx, w, h, scratch, taperTopFactor = 0.94f)
            if (isStyledPlayer) {
                drawRotatedBox3D(px + fx * 0.152f, py + 1.11f, pz + fz * 0.152f, 0.08f, 0.24f, 0.015f, fx, fz, rx, rz, Color(0xFFEBEAE4), light, pose, focalPx, w, h, scratch)
                drawRotatedBox3D(px + fx * 0.03f, py + 1.37f, pz + fz * 0.03f, 0.19f, 0.055f, 0.15f, fx, fz, rx, rz, collarCol, light, pose, focalPx, w, h, scratch)
            }
        }
    }

    // Left & Right Arms + Hands (reaching forward with coupling motion during "interact")
    val handCol = glovesCol ?: skinCol
    if (item.actionState == "interact") {
        val reachPulse = 0.24f + abs(sin(animClockSec * 9f)) * 0.12f
        val armLiftY = py + 1.12f
        drawRotatedBox3D(px - rx * 0.22f + fx * reachPulse, armLiftY, pz - rz * 0.22f + fz * reachPulse, 0.065f, 0.065f, 0.22f, fx, fz, rx, rz, shirtCol, light, pose, focalPx, w, h, scratch)
        drawRotatedBox3D(px + rx * 0.22f + fx * reachPulse, armLiftY, pz + rz * 0.22f + fz * reachPulse, 0.065f, 0.065f, 0.22f, fx, fz, rx, rz, shirtCol, light, pose, focalPx, w, h, scratch)
        drawRotatedBox3D(px - rx * 0.20f + fx * (reachPulse + 0.22f), armLiftY - 0.03f, pz - rz * 0.20f + fz * (reachPulse + 0.22f), 0.055f, 0.055f, 0.06f, fx, fz, rx, rz, handCol, light, pose, focalPx, w, h, scratch)
        drawRotatedBox3D(px + rx * 0.20f + fx * (reachPulse + 0.22f), armLiftY - 0.03f, pz + rz * 0.20f + fz * (reachPulse + 0.22f), 0.055f, 0.055f, 0.06f, fx, fz, rx, rz, handCol, light, pose, focalPx, w, h, scratch)
    } else {
        drawRotatedBox3D(px - rx * 0.28f - fx * armSwing, py + 1.12f, pz - rz * 0.28f - fz * armSwing, 0.065f, 0.24f, 0.065f, fx, fz, rx, rz, shirtCol, light, pose, focalPx, w, h, scratch)
        drawRotatedBox3D(px + rx * 0.28f + fx * armSwing, py + 1.12f, pz + rz * 0.28f + fz * armSwing, 0.065f, 0.24f, 0.065f, fx, fz, rx, rz, shirtCol, light, pose, focalPx, w, h, scratch)
        drawRotatedBox3D(px - rx * 0.28f - fx * armSwing, py + 0.85f, pz - rz * 0.28f - fz * armSwing, 0.055f, 0.06f, 0.055f, fx, fz, rx, rz, handCol, light, pose, focalPx, w, h, scratch)
        drawRotatedBox3D(px + rx * 0.28f + fx * armSwing, py + 0.85f, pz + rz * 0.28f + fz * armSwing, 0.055f, 0.06f, 0.055f, fx, fz, rx, rz, handCol, light, pose, focalPx, w, h, scratch)
    }

    // Rounded Head
    drawEllipsoidDome3D(px, py + 1.54f, pz, 0.135f, 0.145f, skinCol, light, 10, pose, focalPx, w, h, scratch, rings = 2)

    // Beard (Sr. Sebastiao)
    if (isStyledPlayer && beardStyle == "full_gray") {
        drawRotatedBox3D(px + fx * 0.08f, py + 1.46f, pz + fz * 0.08f, 0.105f, 0.055f, 0.07f, fx, fz, rx, rz, hairCol, light, pose, focalPx, w, h, scratch)
    }

    // Glasses (Clara sunglasses / Sr. Sebastiao reading glasses)
    if (isStyledPlayer && glassesStyle == "sunglasses") {
        drawRotatedBox3D(px + fx * 0.135f - rx * 0.045f, py + 1.56f, pz + fz * 0.135f - rz * 0.045f, 0.036f, 0.025f, 0.015f, fx, fz, rx, rz, Color(0xFF1A1412), light, pose, focalPx, w, h, scratch)
        drawRotatedBox3D(px + fx * 0.135f + rx * 0.045f, py + 1.56f, pz + fz * 0.135f + rz * 0.045f, 0.036f, 0.025f, 0.015f, fx, fz, rx, rz, Color(0xFF1A1412), light, pose, focalPx, w, h, scratch)
    } else if (isStyledPlayer && glassesStyle == "reading") {
        drawRotatedBox3D(px + fx * 0.135f, py + 1.56f, pz + fz * 0.135f, 0.095f, 0.018f, 0.014f, fx, fz, rx, rz, Color(0xFFCFD8DC), light, pose, focalPx, w, h, scratch)
    }

    // Hair / Optional Hat
    val hatStyle = if (isStyledPlayer) cfg.hatStyle else "bone"
    when {
        hatStyle != "sem" -> {
            val hatCol = if (isStyledPlayer) parseSafeColor(cfg.hatColorHex, Color(0xFFD7B46A)) else Color(0xFF2E7D32)
            drawEllipsoidDome3D(px, py + 1.66f, pz, 0.25f, 0.055f, hatCol, light, 10, pose, focalPx, w, h, scratch, rings = 2)
            drawEllipsoidDome3D(px, py + 1.72f, pz, 0.14f, 0.08f, hatCol, light, 10, pose, focalPx, w, h, scratch, rings = 2)
        }
        hairStyle == "bob" -> {
            drawEllipsoidDome3D(px, py + 1.63f, pz, 0.155f, 0.08f, hairCol, light, 10, pose, focalPx, w, h, scratch, rings = 2)
            drawRotatedBox3D(px - rx * 0.13f, py + 1.52f, pz - rz * 0.13f, 0.035f, 0.13f, 0.12f, fx, fz, rx, rz, hairCol, light, pose, focalPx, w, h, scratch)
            drawRotatedBox3D(px + rx * 0.13f, py + 1.52f, pz + rz * 0.13f, 0.035f, 0.13f, 0.12f, fx, fz, rx, rz, hairCol, light, pose, focalPx, w, h, scratch)
        }
        hairStyle == "shoulder_bangs" -> {
            drawEllipsoidDome3D(px, py + 1.63f, pz, 0.155f, 0.085f, hairCol, light, 10, pose, focalPx, w, h, scratch, rings = 2)
            drawRotatedBox3D(px + fx * 0.09f, py + 1.61f, pz + fz * 0.09f, 0.11f, 0.035f, 0.04f, fx, fz, rx, rz, hairCol, light, pose, focalPx, w, h, scratch)
            drawRotatedBox3D(px - rx * 0.135f, py + 1.49f, pz - rz * 0.135f, 0.035f, 0.16f, 0.13f, fx, fz, rx, rz, hairCol, light, pose, focalPx, w, h, scratch)
            drawRotatedBox3D(px + rx * 0.135f, py + 1.49f, pz + rz * 0.135f, 0.035f, 0.16f, 0.13f, fx, fz, rx, rz, hairCol, light, pose, focalPx, w, h, scratch)
        }
        else -> {
            // Clean short crop (Sr. Sebastiao / Lucas)
            drawEllipsoidDome3D(px, py + 1.64f, pz, 0.145f, 0.065f, hairCol, light, 10, pose, focalPx, w, h, scratch, rings = 2)
        }
    }

    // Handheld Water Prospecting Probe (ManualTool.SONDA_AGUA) held out in front
    if (isStyledPlayer && item.playerTool == ManualTool.SONDA_AGUA) {
        val probeX = px + rx * 0.28f + fx * 0.42f
        val probeZ = pz + rz * 0.28f + fz * 0.42f
        val bobY = sin(animClockSec * 3.8f) * 0.03f
        // Main handheld acoustic geophone control unit
        drawRotatedBox3D(probeX, py + 1.06f + bobY, probeZ, 0.08f, 0.06f, 0.16f, fx, fz, rx, rz, Color(0xFF0284C7), light, pose, focalPx, w, h, scratch)
        // Extended sensor rod dipping toward terrain
        val tipX = probeX + fx * 0.46f
        val tipZ = probeZ + fz * 0.46f
        drawProjectedLine(probeX, py + 1.04f + bobY, probeZ, tipX, py + 0.24f + bobY, tipZ, Color(0xFFE2E8F0), 3.0f, pose, focalPx, w, h)
        drawCylinderY3D(tipX, py + 0.16f + bobY, py + 0.28f + bobY, tipZ, 0.09f, Color(0xFF00E5FF), 1.0f, 8, pose, focalPx, w, h, scratch)
        // Pulsing sonar ground wave ring around tip
        val tipProj = projectRaw(tipX, py + 0.06f, tipZ, pose, focalPx, w, h)
        if (tipProj.visible && tipProj.depth < 40f) {
            val pulse = (animClockSec * 1.8f) % 1f
            val rPx = ((0.2f + pulse * 1.1f) * focalPx / max(tipProj.depth, 1f)).coerceIn(3f, 26f)
            drawCircle(
                color = Color(0xFF22D3EE).copy(alpha = (1f - pulse) * 0.65f),
                radius = rPx,
                center = Offset(tipProj.screenX, tipProj.screenY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.0f),
            )
        }
    }
}

internal fun DrawScope.drawEarthworkMarks3D(
    worldState: WorldState,
    pose: CameraPose3D,
    focalPx: Float,
    w: Float,
    h: Float,
    twilightDarken: Float,
) {
    val marks = worldState.earthworkMarks ?: return
    if (marks.isEmpty()) return
    for (m in marks.takeLast(200)) {
        val dx = m.x.toFloat() - pose.camX
        val dz = m.z.toFloat() - pose.camZ
        if (dx * dx + dz * dz > 115f * 115f) continue
        val gy = getTerrainHeightAt(worldState, m.x, m.z).toFloat() + 0.03f
        val rad = m.headingDeg.toFloat() * (PI / 180.0).toFloat()
        val fx = sin(rad)
        val fz = cos(rad)
        val rx = cos(rad)
        val rz = -sin(rad)
        val hw = (m.widthM.toFloat() * 0.45f).coerceIn(0.3f, 2.2f)
        val hl = (m.lengthM.toFloat() * 0.50f).coerceIn(0.4f, 2.4f)
        val alpha = (1.0 - (m.ageSec / 180.0)).toFloat().coerceIn(0.18f, 0.72f)
        val markCol = when (m.kind) {
            "CAÇAMBA", "BUCKET", "SPOIL" -> Color(0xFF5D3421).copy(alpha = alpha * twilightDarken)
            "LAMINA", "BLADE", "BERM" -> Color(0xFF8A6543).copy(alpha = alpha * twilightDarken)
            "ESTEIRA", "TRACK", "TIRE" -> Color(0xFF3E291D).copy(alpha = alpha * twilightDarken)
            "PERFURACAO", "DRILL_BORE" -> Color(0xFF1F2937).copy(alpha = 0.85f * twilightDarken)
            else -> Color(0xFF4B3525).copy(alpha = alpha * twilightDarken)
        }
        when (m.kind) {
            "ESTEIRA", "TRACK", "TIRE" -> {
                drawProjectedLine(m.x.toFloat() - rx * hw - fx * hl, gy, m.z.toFloat() - rz * hw - fz * hl, m.x.toFloat() - rx * hw + fx * hl, gy, m.z.toFloat() - rz * hw + fz * hl, markCol, 4.2f, pose, focalPx, w, h)
                drawProjectedLine(m.x.toFloat() + rx * hw - fx * hl, gy, m.z.toFloat() + rz * hw - fz * hl, m.x.toFloat() + rx * hw + fx * hl, gy, m.z.toFloat() + rz * hw + fz * hl, markCol, 4.2f, pose, focalPx, w, h)
            }
            "PERFURACAO", "DRILL_BORE" -> {
                val p = projectRaw(m.x.toFloat(), gy + 0.05f, m.z.toFloat(), pose, focalPx, w, h)
                if (p.visible && p.depth < 80f) {
                    val rr = (0.45f * focalPx / max(p.depth, 1f)).coerceIn(3f, 18f)
                    drawCircle(markCol, radius = rr, center = Offset(p.screenX, p.screenY))
                    drawCircle(Color(0xFF0EA5E9), radius = rr * 0.5f, center = Offset(p.screenX, p.screenY))
                }
            }
            else -> {
                drawProjectedLine(m.x.toFloat() - rx * hw, gy, m.z.toFloat() - rz * hw, m.x.toFloat() + rx * hw, gy, m.z.toFloat() + rz * hw, markCol, 5.2f, pose, focalPx, w, h)
            }
        }
    }
}

internal fun DrawScope.drawPondsWaterOverlay3D(
    worldState: WorldState,
    pose: CameraPose3D,
    focalPx: Float,
    w: Float,
    h: Float,
    animClockSec: Float,
    atmo: SunAtmosphereState,
    scratch: ViewportScratch3D,
) {
    val ponds = worldState.ponds ?: return
    if (ponds.isEmpty()) return
    val skyReflCol = lerpColor(
        Color(0xFF0E6C8F),
        Color(atmo.skyAmbientR.coerceIn(0f, 1f), atmo.skyAmbientG.coerceIn(0f, 1f), atmo.skyAmbientB.coerceIn(0f, 1f)),
        0.48f,
    )
    for (pond in ponds) {
        val dx = pond.x.toFloat() - pose.camX
        val dz = pond.z.toFloat() - pose.camZ
        if (dx * dx + dz * dz > 190f * 190f) continue
        val groundMid = getTerrainHeightAt(worldState, pond.x, pond.z).toFloat()
        val r = pond.radiusM.toFloat().coerceIn(4f, 42f)
        // Translucent dynamic water surface with subtle dike collar & sun glint
        val fillFrac = (pond.waterVolumeL / pond.maxCapacityL.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
        if (fillFrac <= 0.01f) continue
        val waterY = groundMid + 0.16f + (fillFrac * 0.35f)
        val effR = r * (0.45f + 0.52f * fillFrac)
        val waterAlpha = (0.52f + pond.waterQualityPct.toFloat() / 360f).coerceIn(0.45f, 0.80f)
        drawProjectedQuad(
            (pond.x - effR).toFloat(), waterY, (pond.z - effR).toFloat(),
            (pond.x + effR).toFloat(), waterY, (pond.z - effR).toFloat(),
            (pond.x + effR).toFloat(), waterY, (pond.z + effR).toFloat(),
            (pond.x - effR).toFloat(), waterY, (pond.z + effR).toFloat(),
            skyReflCol.copy(alpha = waterAlpha),
            pose, focalPx, w, h, scratch,
        )
        // Center glint & wave ripples + occasional fish surface splash rings
        val centerP = projectRaw(pond.x.toFloat(), waterY + 0.02f, pond.z.toFloat(), pose, focalPx, w, h)
        if (centerP.visible && centerP.depth < 140f) {
            val wavePhase = (animClockSec * 0.9f + (pond.x.toFloat() * 0.1f)) % 1f
            val waveRad = ((effR * 0.68f * (0.35f + 0.65f * wavePhase)) * focalPx / max(centerP.depth, 1f)).coerceIn(4f, 64f)
            drawCircle(
                color = Color(0xFFE0F7FA).copy(alpha = (1f - wavePhase) * 0.34f),
                radius = waveRad,
                center = Offset(centerP.screenX, centerP.screenY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6f),
            )
            if (pond.piscicultureEnabled && pond.fishStocks.isNotEmpty()) {
                val fishPhase = (animClockSec * 1.7f + (pond.z.toFloat() * 0.2f)) % 1f
                val fishRad = ((effR * 0.30f * fishPhase) * focalPx / max(centerP.depth, 1f)).coerceIn(2f, 22f)
                drawCircle(
                    color = Color(0xFFA7F3D0).copy(alpha = (1f - fishPhase) * 0.45f),
                    radius = fishRad,
                    center = Offset(centerP.screenX + 10f, centerP.screenY - 4f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4f),
                )
            }
        }
    }
}

internal fun DrawScope.drawWaterProspectPins3D(
    worldState: WorldState,
    pose: CameraPose3D,
    focalPx: Float,
    w: Float,
    h: Float,
    animClockSec: Float,
    scratch: ViewportScratch3D,
) {
    val pins = worldState.waterProspectPoints ?: return
    if (pins.isEmpty()) return
    for (pt in pins) {
        val dx = pt.x.toFloat() - pose.camX
        val dz = pt.z.toFloat() - pose.camZ
        if (dx * dx + dz * dz > 160f * 160f) continue
        val gy = getTerrainHeightAt(worldState, pt.x, pt.z).toFloat()
        val pinCol = when (pt.availabilityTier) {
            AquiferAvailabilityTier.MUITO_ALTA -> Color(0xFF00E5FF)
            AquiferAvailabilityTier.ALTA -> Color(0xFF10B981)
            AquiferAvailabilityTier.MEDIA -> Color(0xFFF59E0B)
            AquiferAvailabilityTier.BAIXA -> Color(0xFFF87171)
        }
        drawCylinderY3D(pt.x.toFloat(), gy, gy + 1.45f, pt.z.toFloat(), 0.045f, Color(0xFFE2E8F0), 1f, 6, pose, focalPx, w, h, scratch)
        drawEllipsoidDome3D(pt.x.toFloat(), gy + 1.50f, pt.z.toFloat(), 0.16f, 0.16f, pinCol, 1f, 8, pose, focalPx, w, h, scratch, rings = 2)
        val p = projectRaw(pt.x.toFloat(), gy + 0.05f, pt.z.toFloat(), pose, focalPx, w, h)
        if (p.visible && p.depth < 90f) {
            val pulse = 0.5f + 0.5f * sin(animClockSec * 4.5f)
            val radPx = ((0.6f + 0.35f * pulse) * focalPx / max(p.depth, 1f)).coerceIn(3f, 22f)
            drawCircle(
                color = pinCol.copy(alpha = 0.65f),
                radius = radPx,
                center = Offset(p.screenX, p.screenY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.0f),
            )
        }
    }
}

