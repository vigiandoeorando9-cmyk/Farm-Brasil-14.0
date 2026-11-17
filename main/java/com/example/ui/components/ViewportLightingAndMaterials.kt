package com.example.ui.components

import com.example.domain.GraphicsConfig
import com.example.domain.WEATHER_META
import com.example.domain.WeatherType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Material classes for physically-inspired shading across machinery,
 * architecture, soil, water, roads, and foliage.
 */
enum class MaterialType {
    GLOSS_PAINT,
    MATTE_CHASSIS,
    CHROME_STEEL,
    GALVANIZED_METAL,
    RUBBER_TIRE,
    MUD_RUBBER,
    CAB_GLASS,
    WOOD_TIMBER,
    CERAMIC_TILE,
    CONCRETE_STONE,
    SOIL,
    WET_SURFACE,
    WATER,
    FOLIAGE,
}

/**
 * Shared atmospheric, solar, GI, fog, rain-wetness, and wind state
 * calculated once per rendered frame and passed to terrain, sky, buildings,
 * props, vehicles, implements, and particles.
 */
data class SunAtmosphereState(
    val hourOfDay: Float,
    val sunElevationRad: Float,
    val sunAzimuthRad: Float,
    val sunDirX: Float,
    val sunDirY: Float,
    val sunDirZ: Float,
    val sunR: Float,
    val sunG: Float,
    val sunB: Float,
    val sunIntensity: Float,
    val skyAmbientR: Float,
    val skyAmbientG: Float,
    val skyAmbientB: Float,
    val groundBounceR: Float,
    val groundBounceG: Float,
    val groundBounceB: Float,
    val horizonGlowR: Float,
    val horizonGlowG: Float,
    val horizonGlowB: Float,
    val fogColorR: Float,
    val fogColorG: Float,
    val fogColorB: Float,
    val fogDensity: Float,
    val shadowDirX: Float,
    val shadowDirZ: Float,
    val shadowOpacity: Float,
    val shadowLengthFactor: Float,
    val wetness: Float,
    val rainIntensity: Float,
    val cloudCover: Float,
    val windStrength: Float,
    val windPhaseRad: Float,
    val isNight: Boolean,
    val nightFactor: Float,
    val goldenHourFactor: Float,
    val lightningFlash: Float,
    val animClockSec: Float,
    val aoStrength: Float,
    val specularEnabled: Boolean,
    val giLevel: Int,
)

fun computeSunAtmosphereState(
    hourOfDay: Double,
    weather: WeatherType,
    gfx: GraphicsConfig,
    animClockSec: Float,
): SunAtmosphereState {
    val h = (((hourOfDay % 24.0) + 24.0) % 24.0).toFloat()
    val meta = WEATHER_META[weather] ?: WEATHER_META.getValue(WeatherType.ENSOLARADO)
    val rain = meta.rainIntensity.toFloat()
    val clouds = meta.cloudCover.toFloat()

    val isNight = h < 5.35f || h >= 19.15f
    val nightFactor = when {
        h < 5.1f || h > 19.6f -> 1.0f
        h < 6.4f -> ((6.4f - h) / 1.3f).coerceIn(0f, 1f)
        h > 18.0f -> ((h - 18.0f) / 1.6f).coerceIn(0f, 1f)
        else -> 0.0f
    }

    // Morning & late-afternoon golden-hour curve
    val dawnGlow = if (h in 5.2f..8.3f) {
        sin(((h - 5.2f) / 3.1f) * PI.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val duskGlow = if (h in 16.0f..19.2f) {
        sin(((h - 16.0f) / 3.2f) * PI.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val goldenHour = max(dawnGlow, duskGlow) * (1f - clouds * 0.55f)

    // Solar position in world coordinates: rises East (+X), arches South/Zenith, sets West (-X)
    val sunDayProgress = ((h - 5.5f) / 13.5f).coerceIn(0f, 1f)
    val elevRad = if (!isNight) {
        (0.11f + sin(sunDayProgress * PI.toFloat()) * 1.12f).coerceIn(0.10f, 1.28f)
    } else {
        // Moon key light slightly elevated so night terrain/machines keep 3D relief
        0.58f
    }
    val azimRad = if (!isNight) {
        (sunDayProgress - 0.5f) * 2.55f
    } else {
        0.65f
    }

    val cosE = cos(elevRad)
    val rawLx = sin(azimRad) * cosE
    val rawLy = sin(elevRad)
    val rawLz = cos(azimRad) * cosE
    val invLen = 1f / max(0.001f, sqrt(rawLx * rawLx + rawLy * rawLy + rawLz * rawLz))
    val lx = rawLx * invLen
    val ly = rawLy * invLen
    val lz = rawLz * invLen

    // Lightning pulse for TEMPESTADE
    val stormFlash = if (weather == WeatherType.TEMPESTADE) {
        val cycle = (animClockSec * 0.55f) % 4.3f
        val burst = if (cycle < 0.14f) {
            1f - (cycle / 0.14f)
        } else if (cycle in 0.20f..0.32f) {
            (1f - ((cycle - 0.20f) / 0.12f)) * 0.65f
        } else 0f
        burst * (0.65f + 0.35f * sin(animClockSec * 41f))
    } else 0f

    // Direct Sun / Moon tint
    val baseSunR: Float
    val baseSunG: Float
    val baseSunB: Float
    val baseIntensity: Float
    if (isNight) {
        baseSunR = 0.54f
        baseSunG = 0.66f
        baseSunB = 0.92f
        baseIntensity = 0.30f + stormFlash * 0.95f
    } else {
        baseSunR = 1.00f
        baseSunG = (0.97f - goldenHour * 0.32f).coerceIn(0.55f, 1.0f)
        baseSunB = (0.91f - goldenHour * 0.58f).coerceIn(0.24f, 0.95f)
        baseIntensity = ((0.64f + sin(sunDayProgress * PI.toFloat()) * 0.50f) *
            (1f - rain * 0.52f - clouds * 0.26f) + stormFlash * 0.85f).coerceIn(0.24f, 1.35f)
    }

    // Hemisphere Sky Ambient + Ground Bounce (approximate Global Illumination)
    val giLevel = (gfx.globalIllumLevel ?: 2).coerceIn(0, 3)
    val skyAmbR: Float
    val skyAmbG: Float
    val skyAmbB: Float
    if (isNight) {
        skyAmbR = 0.16f + stormFlash * 0.45f
        skyAmbG = 0.21f + stormFlash * 0.48f
        skyAmbB = 0.34f + stormFlash * 0.55f
    } else if (rain > 0.35f || weather == WeatherType.NUBLADO || weather == WeatherType.TEMPESTADE) {
        skyAmbR = 0.44f + stormFlash * 0.42f
        skyAmbG = 0.49f + stormFlash * 0.45f
        skyAmbB = 0.56f + stormFlash * 0.52f
    } else {
        skyAmbR = (0.42f + goldenHour * 0.28f).coerceIn(0.35f, 0.85f)
        skyAmbG = (0.56f + goldenHour * 0.08f).coerceIn(0.45f, 0.78f)
        skyAmbB = (0.78f - goldenHour * 0.30f).coerceIn(0.38f, 0.90f)
    }

    val groundR = if (isNight) 0.11f else (0.34f + goldenHour * 0.14f - rain * 0.12f).coerceIn(0.15f, 0.52f)
    val groundG = if (isNight) 0.12f else (0.29f + goldenHour * 0.05f - rain * 0.10f).coerceIn(0.14f, 0.44f)
    val groundB = if (isNight) 0.14f else (0.21f - rain * 0.06f).coerceIn(0.10f, 0.32f)

    // Horizon & Fog
    val horR: Float
    val horG: Float
    val horB: Float
    when {
        isNight -> {
            horR = 0.07f + stormFlash * 0.22f
            horG = 0.10f + stormFlash * 0.26f
            horB = 0.18f + stormFlash * 0.34f
        }
        goldenHour > 0.18f -> {
            horR = (0.96f - rain * 0.35f).coerceIn(0.45f, 1.0f)
            horG = (0.64f - rain * 0.18f).coerceIn(0.35f, 0.78f)
            horB = (0.36f + rain * 0.12f).coerceIn(0.24f, 0.58f)
        }
        weather == WeatherType.NEBLINA -> {
            horR = 0.76f; horG = 0.80f; horB = 0.83f
        }
        rain > 0.2f || weather == WeatherType.NUBLADO -> {
            horR = 0.48f + stormFlash * 0.25f
            horG = 0.53f + stormFlash * 0.27f
            horB = 0.58f + stormFlash * 0.32f
        }
        else -> {
            horR = 0.70f; horG = 0.82f; horB = 0.94f
        }
    }

    val fogDens = (meta.fogDensity.toFloat() * (if (isNight) 1.25f else 1.0f)).coerceIn(0.0008f, 0.0115f)

    // Directional projected shadow vector on ground XZ plane (away from sun)
    val xzLen = max(0.001f, sqrt(lx * lx + lz * lz))
    val sDirX = -(lx / xzLen)
    val sDirZ = -(lz / xzLen)
    val shadowStretch = (1f / max(0.38f, sin(elevRad))).coerceIn(0.75f, 2.35f)
    val shadowOp = if (gfx.shadowQuality <= 0) {
        0f
    } else {
        val weatherFade = (1f - rain * 0.58f - clouds * 0.34f).coerceIn(0.22f, 1f)
        val timeFade = if (isNight) 0.42f else 0.92f
        val qScale = when (gfx.shadowQuality) {
            1 -> 0.26f
            2 -> 0.38f
            else -> 0.46f
        }
        (qScale * weatherFade * timeFade).coerceIn(0.12f, 0.50f)
    }

    val wetness = when {
        rain >= 0.7f -> 0.95f
        rain >= 0.2f -> 0.55f + rain * 0.4f
        weather == WeatherType.NEBLINA -> 0.22f
        else -> 0.0f
    }.coerceIn(0f, 1f)

    val windStrength = when (weather) {
        WeatherType.TEMPESTADE -> 1.85f
        WeatherType.CHUVA_INTENSA -> 1.35f
        WeatherType.CHUVA -> 0.95f
        WeatherType.CHUVA_FRACA, WeatherType.NUBLADO -> 0.65f
        WeatherType.NEBLINA -> 0.25f
        else -> 0.42f
    }

    val aoStrength = if (gfx.ambientOcclusion != false) {
        when (gfx.lightingQuality) {
            0 -> 0.12f
            1 -> 0.22f
            2 -> 0.34f
            else -> 0.42f
        }
    } else 0.08f

    return SunAtmosphereState(
        hourOfDay = h,
        sunElevationRad = elevRad,
        sunAzimuthRad = azimRad,
        sunDirX = lx,
        sunDirY = ly,
        sunDirZ = lz,
        sunR = baseSunR,
        sunG = baseSunG,
        sunB = baseSunB,
        sunIntensity = baseIntensity,
        skyAmbientR = skyAmbR,
        skyAmbientG = skyAmbG,
        skyAmbientB = skyAmbB,
        groundBounceR = groundR,
        groundBounceG = groundG,
        groundBounceB = groundB,
        horizonGlowR = horR,
        horizonGlowG = horG,
        horizonGlowB = horB,
        fogColorR = horR,
        fogColorG = horG,
        fogColorB = horB,
        fogDensity = fogDens,
        shadowDirX = sDirX,
        shadowDirZ = sDirZ,
        shadowOpacity = shadowOp,
        shadowLengthFactor = shadowStretch,
        wetness = wetness,
        rainIntensity = rain,
        cloudCover = clouds,
        windStrength = windStrength,
        windPhaseRad = animClockSec * (1.4f + windStrength * 0.9f),
        isNight = isNight,
        nightFactor = nightFactor,
        goldenHourFactor = goldenHour,
        lightningFlash = stormFlash,
        animClockSec = animClockSec,
        aoStrength = aoStrength,
        specularEnabled = (gfx.lightingQuality >= 1 || (gfx.materialQuality ?: 1) >= 2),
        giLevel = giLevel,
    )
}

/**
 * Smoothly blends two weather atmosphere states over transition progress [0f..1f]
 * so cloud cover, rain intensity, fog, wetness, sky bounce, and sun intensity
 * evolve organically instead of snapping instantaneously when weather changes.
 */
fun computeBlendedSunAtmosphereState(
    hourOfDay: Double,
    fromWeather: WeatherType,
    toWeather: WeatherType,
    blendT: Float,
    gfx: GraphicsConfig,
    animClockSec: Float,
): SunAtmosphereState {
    val t = blendT.coerceIn(0f, 1f)
    if (fromWeather == toWeather || t >= 0.995f) {
        return computeSunAtmosphereState(hourOfDay, toWeather, gfx, animClockSec)
    }
    if (t <= 0.005f) {
        return computeSunAtmosphereState(hourOfDay, fromWeather, gfx, animClockSec)
    }
    val s = t * t * (3f - 2f * t)
    val a = computeSunAtmosphereState(hourOfDay, fromWeather, gfx, animClockSec)
    val b = computeSunAtmosphereState(hourOfDay, toWeather, gfx, animClockSec)
    fun mix(va: Float, vb: Float): Float = va + (vb - va) * s

    return b.copy(
        sunR = mix(a.sunR, b.sunR),
        sunG = mix(a.sunG, b.sunG),
        sunB = mix(a.sunB, b.sunB),
        sunIntensity = mix(a.sunIntensity, b.sunIntensity),
        skyAmbientR = mix(a.skyAmbientR, b.skyAmbientR),
        skyAmbientG = mix(a.skyAmbientG, b.skyAmbientG),
        skyAmbientB = mix(a.skyAmbientB, b.skyAmbientB),
        groundBounceR = mix(a.groundBounceR, b.groundBounceR),
        groundBounceG = mix(a.groundBounceG, b.groundBounceG),
        groundBounceB = mix(a.groundBounceB, b.groundBounceB),
        horizonGlowR = mix(a.horizonGlowR, b.horizonGlowR),
        horizonGlowG = mix(a.horizonGlowG, b.horizonGlowG),
        horizonGlowB = mix(a.horizonGlowB, b.horizonGlowB),
        fogColorR = mix(a.fogColorR, b.fogColorR),
        fogColorG = mix(a.fogColorG, b.fogColorG),
        fogColorB = mix(a.fogColorB, b.fogColorB),
        fogDensity = mix(a.fogDensity, b.fogDensity),
        shadowOpacity = mix(a.shadowOpacity, b.shadowOpacity),
        wetness = mix(a.wetness, b.wetness),
        rainIntensity = mix(a.rainIntensity, b.rainIntensity),
        cloudCover = mix(a.cloudCover, b.cloudCover),
        windStrength = mix(a.windStrength, b.windStrength),
        goldenHourFactor = mix(a.goldenHourFactor, b.goldenHourFactor),
        lightningFlash = mix(a.lightningFlash, b.lightningFlash),
    )
}

/**
 * Physically-inspired surface shader returning packed ARGB Int with zero allocations.
 *
 * Uses directional sun N·L, wrap diffusion for foliage/rubber, hemisphere GI
 * (sky overhead + soil ground bounce), surface AO, and per-material Blinn-Phong
 * specular & Fresnel highlights.
 */
fun shadeMaterialFace(
    baseArgb: Int,
    nx: Float,
    ny: Float,
    nz: Float,
    viewDirX: Float,
    viewDirY: Float,
    viewDirZ: Float,
    atmo: SunAtmosphereState,
    material: MaterialType,
    aoFactor: Float = 1.0f,
    mudFactor: Float = 0.0f,
    fogFactor: Float = 0.0f,
): Int {
    val alpha = (baseArgb ushr 24) and 0xFF
    var baseR = ((baseArgb ushr 16) and 0xFF) / 255f
    var baseG = ((baseArgb ushr 8) and 0xFF) / 255f
    var baseB = (baseArgb and 0xFF) / 255f

    // Blend dried/wet farm mud onto vehicle lower fenders, undercarriage, and tires
    if (mudFactor > 0.01f && (material == MaterialType.RUBBER_TIRE || material == MaterialType.MUD_RUBBER ||
            material == MaterialType.GLOSS_PAINT || material == MaterialType.MATTE_CHASSIS)
    ) {
        val m = mudFactor.coerceIn(0f, 0.85f)
        val mudR = if (atmo.wetness > 0.4f) 0.16f else 0.30f
        val mudG = if (atmo.wetness > 0.4f) 0.11f else 0.22f
        val mudB = if (atmo.wetness > 0.4f) 0.07f else 0.14f
        baseR = baseR * (1f - m) + mudR * m
        baseG = baseG * (1f - m) + mudG * m
        baseB = baseB * (1f - m) + mudB * m
    }

    // Wet surfaces darken slightly (higher internal absorption) before reflection boost
    if (atmo.wetness > 0.1f && (material == MaterialType.SOIL || material == MaterialType.WET_SURFACE ||
            material == MaterialType.WOOD_TIMBER || material == MaterialType.CONCRETE_STONE ||
            material == MaterialType.CERAMIC_TILE)
    ) {
        val dark = 1f - atmo.wetness * 0.22f
        baseR *= dark
        baseG *= dark
        baseB *= dark
    }

    val rawDot = nx * atmo.sunDirX + ny * atmo.sunDirY + nz * atmo.sunDirZ
    val wrap = when (material) {
        MaterialType.FOLIAGE -> 0.32f
        MaterialType.CAB_GLASS, MaterialType.WATER -> 0.22f
        MaterialType.SOIL, MaterialType.WOOD_TIMBER -> 0.14f
        else -> 0.10f
    }
    val nDotL = ((rawDot + wrap) / (1f + wrap)).coerceIn(0f, 1.1f)

    val skyHemi = (ny * 0.5f + 0.5f).coerceIn(0f, 1f)
    val giWeight = when (atmo.giLevel) {
        0 -> 0.22f
        1 -> 0.34f
        2 -> 0.44f
        else -> 0.52f
    }
    val ambR = (atmo.groundBounceR * (1f - skyHemi) + atmo.skyAmbientR * skyHemi) * giWeight
    val ambG = (atmo.groundBounceG * (1f - skyHemi) + atmo.skyAmbientG * skyHemi) * giWeight
    val ambB = (atmo.groundBounceB * (1f - skyHemi) + atmo.skyAmbientB * skyHemi) * giWeight

    val dirStrength = when (material) {
        MaterialType.CAB_GLASS -> 0.48f
        MaterialType.WATER -> 0.42f
        else -> 0.78f
    }
    val effAo = (1f - atmo.aoStrength * (1f - aoFactor.coerceIn(0.35f, 1f))).coerceIn(0.45f, 1f)

    var outR = baseR * (ambR + atmo.sunR * atmo.sunIntensity * nDotL * dirStrength) * effAo
    var outG = baseG * (ambG + atmo.sunG * atmo.sunIntensity * nDotL * dirStrength) * effAo
    var outB = baseB * (ambB + atmo.sunB * atmo.sunIntensity * nDotL * dirStrength) * effAo

    // Specular + Fresnel per material
    if (atmo.specularEnabled && rawDot > -0.15f) {
        val hx = atmo.sunDirX + viewDirX
        val hy = atmo.sunDirY + viewDirY
        val hz = atmo.sunDirZ + viewDirZ
        val hLen = max(0.001f, sqrt(hx * hx + hy * hy + hz * hz))
        val nDotH = ((nx * hx + ny * hy + nz * hz) / hLen).coerceIn(0f, 1f)
        val nDotV = abs(nx * viewDirX + ny * viewDirY + nz * viewDirZ).coerceIn(0f, 1f)
        val fresnel = (1f - nDotV).pow(3f)

        val specPower: Float
        val specStrength: Float
        val skyReflectStrength: Float
        when (material) {
            MaterialType.CAB_GLASS -> {
                specPower = 64f
                specStrength = 0.68f
                skyReflectStrength = 0.36f + fresnel * 0.42f
            }
            MaterialType.CHROME_STEEL -> {
                specPower = 48f
                specStrength = 0.62f * (1f - mudFactor * 0.7f)
                skyReflectStrength = 0.24f + fresnel * 0.30f
            }
            MaterialType.GALVANIZED_METAL -> {
                specPower = 26f
                specStrength = 0.36f
                skyReflectStrength = 0.16f + fresnel * 0.22f
            }
            MaterialType.GLOSS_PAINT -> {
                specPower = 36f
                specStrength = (0.34f + atmo.wetness * 0.16f) * (1f - mudFactor * 0.8f)
                skyReflectStrength = 0.08f + fresnel * (0.18f + atmo.wetness * 0.12f)
            }
            MaterialType.WATER, MaterialType.WET_SURFACE -> {
                specPower = 54f
                specStrength = 0.55f
                skyReflectStrength = 0.28f + fresnel * 0.46f
            }
            MaterialType.CERAMIC_TILE -> {
                specPower = 18f
                specStrength = 0.12f + atmo.wetness * 0.22f
                skyReflectStrength = fresnel * (0.06f + atmo.wetness * 0.18f)
            }
            MaterialType.MATTE_CHASSIS -> {
                specPower = 14f
                specStrength = 0.12f + atmo.wetness * 0.14f
                skyReflectStrength = fresnel * (0.04f + atmo.wetness * 0.10f)
            }
            MaterialType.RUBBER_TIRE, MaterialType.MUD_RUBBER -> {
                specPower = 8f
                specStrength = 0.03f + atmo.wetness * 0.12f
                skyReflectStrength = fresnel * atmo.wetness * 0.08f
            }
            MaterialType.SOIL -> {
                specPower = 10f
                specStrength = atmo.wetness * 0.14f
                skyReflectStrength = fresnel * atmo.wetness * 0.10f
            }
            else -> {
                specPower = 10f
                specStrength = atmo.wetness * 0.09f
                skyReflectStrength = 0f
            }
        }

        if (specStrength > 0.005f) {
            val spec = nDotH.pow(specPower) * specStrength * atmo.sunIntensity
            outR += atmo.sunR * spec
            outG += atmo.sunG * spec
            outB += atmo.sunB * spec
        }
        if (skyReflectStrength > 0.005f) {
            val rMix = skyReflectStrength.coerceIn(0f, 0.72f)
            outR = outR * (1f - rMix) + atmo.skyAmbientR * rMix
            outG = outG * (1f - rMix) + atmo.skyAmbientG * rMix
            outB = outB * (1f - rMix) + atmo.skyAmbientB * rMix
        }
    }

    // Distance atmospheric fog blend
    if (fogFactor > 0.005f) {
        val f = fogFactor.coerceIn(0f, 0.92f)
        outR = outR * (1f - f) + atmo.fogColorR * f
        outG = outG * (1f - f) + atmo.fogColorG * f
        outB = outB * (1f - f) + atmo.fogColorB * f
    }

    return packArgb(outR, outG, outB, alpha / 255f)
}

fun packArgb(r: Float, g: Float, b: Float, a: Float = 1.0f): Int {
    val ia = (a.coerceIn(0f, 1f) * 255f).toInt()
    val ir = (r.coerceIn(0f, 1f) * 255f).toInt()
    val ig = (g.coerceIn(0f, 1f) * 255f).toInt()
    val ib = (b.coerceIn(0f, 1f) * 255f).toInt()
    return (ia shl 24) or (ir shl 16) or (ig shl 8) or ib
}

fun computeFootprintShadowOffset(
    objectHeightM: Float,
    atmo: SunAtmosphereState,
): Pair<Float, Float> {
    val span = objectHeightM * 0.65f * atmo.shadowLengthFactor
    return (atmo.shadowDirX * span) to (atmo.shadowDirZ * span)
}
