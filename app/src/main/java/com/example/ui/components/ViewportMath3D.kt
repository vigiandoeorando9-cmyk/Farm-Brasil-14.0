package com.example.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.domain.BuildingType
import com.example.domain.CameraMode
import com.example.domain.OwnedImplement
import com.example.domain.OwnedVehicle
import com.example.domain.PlacedBuilding
import com.example.domain.PlacedCenterPivot
import com.example.domain.PlayerAppearanceConfig
import com.example.domain.RgbTriple
import com.example.domain.RoadVehicle
import com.example.domain.WorldProp
import com.example.domain.WorldState
import com.example.domain.getVehicleDef
import com.example.simulation.clampCameraSegmentAgainstBuildings
import com.example.simulation.getTerrainHeightAt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class CameraPose3D(
    val camX: Float,
    val camY: Float,
    val camZ: Float,
    val targetX: Float,
    val targetY: Float,
    val targetZ: Float,
    val yawDeg: Float,
    val pitchDeg: Float,
    val fovDeg: Float,
    val fwdX: Float,
    val fwdY: Float,
    val fwdZ: Float,
    val rightX: Float,
    val rightZ: Float,
    val upX: Float,
    val upY: Float,
    val upZ: Float,
)

data class ProjectedPoint3D(
    val screenX: Float,
    val screenY: Float,
    val depth: Float,
    val visible: Boolean,
)

fun computeCameraPose3D(
    worldState: WorldState,
    cameraMode: CameraMode,
    cameraDistanceFactor: Float = 1.0f,
    fovDeg: Float = 60f,
    orbitYawDeg: Float = 0f,
    orbitPitchDeg: Float = 0f,
    pinchZoom: Float = 1.0f,
    panOffsetX: Float = 0f,
    panOffsetZ: Float = 0f,
): CameraPose3D {
    val activeVeh = worldState.activeVehicleUid?.let { uid ->
        worldState.vehicles.find { it.uid == uid }
    }
    val def = activeVeh?.let { getVehicleDef(it.defId) }
    val vehLength = def?.dimensionsM?.width?.toFloat()?.coerceIn(2.6f, 9.2f) ?: 4.2f
    val vehHeight = def?.dimensionsM?.length?.toFloat()?.coerceIn(1.6f, 4.2f) ?: 2.8f

    val baseFocusX = (activeVeh?.x ?: worldState.player.x).toFloat()
    val baseFocusZ = (activeVeh?.z ?: worldState.player.z).toFloat()
    val baseHeading = (activeVeh?.headingDeg ?: worldState.player.headingDeg).toFloat()
    val groundY = getTerrainHeightAt(worldState, baseFocusX.toDouble(), baseFocusZ.toDouble()).toFloat()

    val zoom = pinchZoom.coerceIn(0.55f, 2.2f)
    val distFactor = cameraDistanceFactor.coerceIn(0.65f, 1.8f)

    val rawYawDeg: Float
    val clampedPitchDeg: Float
    val targetX: Float
    val targetY: Float
    val targetZ: Float
    var camX: Float
    var camY: Float
    var camZ: Float

    when (cameraMode) {
        CameraMode.CABINE -> {
            rawYawDeg = baseHeading + orbitYawDeg.coerceIn(-75f, 75f)
            clampedPitchDeg = (8f + orbitPitchDeg).coerceIn(2f, 26f)
            val radH = rawYawDeg * (PI / 180.0).toFloat()
            val eyeH = if (activeVeh != null) (vehHeight * 0.78f).coerceIn(1.7f, 3.3f) else 1.68f
            val fwdShift = if (activeVeh != null) vehLength * 0.10f else 0.15f
            camX = baseFocusX + sin(radH) * fwdShift
            camY = groundY + eyeH + ((activeVeh?.cabBounceOffsetY ?: 0.0).toFloat())
            camZ = baseFocusZ + cos(radH) * fwdShift
            targetX = camX + sin(radH) * 14f
            targetY = camY - sin(clampedPitchDeg * (PI / 180.0).toFloat()) * 14f
            targetZ = camZ + cos(radH) * 14f
        }
        CameraMode.ORBITAL -> {
            rawYawDeg = baseHeading + orbitYawDeg
            clampedPitchDeg = (24f + orbitPitchDeg).coerceIn(12f, 52f)
            targetX = baseFocusX + panOffsetX * 0.4f
            targetY = groundY + (if (activeVeh != null) vehHeight * 0.45f else 1.2f)
            targetZ = baseFocusZ + panOffsetZ * 0.4f
            val dist = (vehLength * 1.65f + 5.0f) * distFactor / zoom
            val yawR = rawYawDeg * (PI / 180.0).toFloat()
            val pitR = clampedPitchDeg * (PI / 180.0).toFloat()
            val fx = sin(yawR) * cos(pitR)
            val fy = -sin(pitR)
            val fz = cos(yawR) * cos(pitR)
            camX = targetX - fx * dist
            camY = targetY - fy * dist
            camZ = targetZ - fz * dist
        }
        CameraMode.TOPO -> {
            rawYawDeg = orbitYawDeg
            clampedPitchDeg = (52f + orbitPitchDeg * 0.4f).coerceIn(38f, 60f)
            targetX = baseFocusX + panOffsetX
            targetY = groundY + 0.8f
            targetZ = baseFocusZ + panOffsetZ
            val dist = (28f * distFactor / zoom).coerceIn(14f, 52f)
            val yawR = rawYawDeg * (PI / 180.0).toFloat()
            val pitR = clampedPitchDeg * (PI / 180.0).toFloat()
            camX = targetX - sin(yawR) * cos(pitR) * dist
            camY = targetY + sin(pitR) * dist
            camZ = targetZ - cos(yawR) * cos(pitR) * dist
        }
        CameraMode.LIVRE -> {
            rawYawDeg = baseHeading + orbitYawDeg
            clampedPitchDeg = (22f + orbitPitchDeg).coerceIn(10f, 52f)
            targetX = baseFocusX + panOffsetX
            targetY = getTerrainHeightAt(worldState, targetX.toDouble(), (baseFocusZ + panOffsetZ).toDouble()).toFloat() + 1.4f
            targetZ = baseFocusZ + panOffsetZ
            val dist = (15f * distFactor / zoom).coerceIn(7f, 38f)
            val yawR = rawYawDeg * (PI / 180.0).toFloat()
            val pitR = clampedPitchDeg * (PI / 180.0).toFloat()
            camX = targetX - sin(yawR) * cos(pitR) * dist
            camY = targetY + sin(pitR) * dist
            camZ = targetZ - cos(yawR) * cos(pitR) * dist
        }
        CameraMode.TERCEIRA_PESSOA -> {
            rawYawDeg = baseHeading + orbitYawDeg
            clampedPitchDeg = (19f + orbitPitchDeg).coerceIn(11f, 46f)
            val hRad = baseHeading * (PI / 180.0).toFloat()
            val leadAhead = if (activeVeh != null) 1.6f else 1.1f
            targetX = baseFocusX + sin(hRad) * leadAhead
            targetY = groundY + (if (activeVeh != null) vehHeight * 0.50f else 1.25f)
            targetZ = baseFocusZ + cos(hRad) * leadAhead
            val baseDist = if (activeVeh != null) (vehLength * 1.45f + 4.8f).coerceIn(8.8f, 16.5f) else 6.8f
            val dist = (baseDist * distFactor / zoom).coerceIn(5.2f, 26f)
            val yawR = rawYawDeg * (PI / 180.0).toFloat()
            val pitR = clampedPitchDeg * (PI / 180.0).toFloat()
            camX = targetX - sin(yawR) * cos(pitR) * dist
            camY = targetY + sin(pitR) * dist
            camZ = targetZ - cos(yawR) * cos(pitR) * dist
        }
    }

    if (cameraMode == CameraMode.TERCEIRA_PESSOA || cameraMode == CameraMode.ORBITAL || cameraMode == CameraMode.LIVRE) {
        val clamped = clampCameraSegmentAgainstBuildings(
            targetX = targetX,
            targetY = targetY,
            targetZ = targetZ,
            camX = camX,
            camY = camY,
            camZ = camZ,
            state = worldState,
        )
        camX = clamped.first
        camY = clamped.second
        camZ = clamped.third
    }

    if (cameraMode != CameraMode.CABINE) {
        val camGround = getTerrainHeightAt(worldState, camX.toDouble(), camZ.toDouble()).toFloat()
        if (camY < camGround + 1.6f) {
            camY = camGround + 1.6f
        }
    }

    val yawR = rawYawDeg * (PI / 180.0).toFloat()
    val pitR = clampedPitchDeg * (PI / 180.0).toFloat()
    val sinY = sin(yawR)
    val cosY = cos(yawR)
    val sinP = sin(pitR)
    val cosP = cos(pitR)

    return CameraPose3D(
        camX = camX,
        camY = camY,
        camZ = camZ,
        targetX = targetX,
        targetY = targetY,
        targetZ = targetZ,
        yawDeg = rawYawDeg,
        pitchDeg = clampedPitchDeg,
        fovDeg = fovDeg.coerceIn(45f, 82f),
        fwdX = sinY * cosP,
        fwdY = -sinP,
        fwdZ = cosY * cosP,
        rightX = cosY,
        rightZ = -sinY,
        upX = sinY * sinP,
        upY = cosP,
        upZ = cosY * sinP,
    )
}

fun projectPoint3D(
    wx: Float,
    wy: Float,
    wz: Float,
    pose: CameraPose3D,
    viewWidthPx: Float,
    viewHeightPx: Float,
): ProjectedPoint3D {
    val dx = wx - pose.camX
    val dy = wy - pose.camY
    val dz = wz - pose.camZ

    val camX = dx * pose.rightX + dz * pose.rightZ
    val camY = dx * pose.upX + dy * pose.upY + dz * pose.upZ
    val camZ = dx * pose.fwdX + dy * pose.fwdY + dz * pose.fwdZ

    val halfFovRad = (pose.fovDeg * 0.5f) * (PI / 180.0).toFloat()
    val focalPx = (viewHeightPx * 0.5f) / tan(halfFovRad).coerceAtLeast(0.25f)

    if (camZ <= 0.45f) {
        return ProjectedPoint3D(0f, 0f, camZ, visible = false)
    }
    val invZ = focalPx / camZ
    return ProjectedPoint3D(
        screenX = viewWidthPx * 0.5f + camX * invZ,
        screenY = viewHeightPx * 0.5f - camY * invZ,
        depth = camZ,
        visible = true,
    )
}

internal enum class ItemKind3D {
    VEHICLE,
    IMPLEMENT,
    BUILDING,
    TREE,
    PROP,
    ROAD_VEH,
    PLAYER,
    NPC,
    TEAMMATE,
    CONTRACTOR,
    CENTER_PIVOT,
}

internal data class RenderItem3D(
    val kind: ItemKind3D,
    val depth: Float,
    val x: Double,
    val y: Double,
    val z: Double,
    val vehicle: OwnedVehicle? = null,
    val implement: OwnedImplement? = null,
    val prop: WorldProp? = null,
    val building: PlacedBuilding? = null,
    val buildingType: BuildingType? = null,
    val buildingRotDeg: Double = 0.0,
    val buildingGateOpen: Boolean = false,
    val roadVehicle: RoadVehicle? = null,
    val labelColor: Color = Color.White,
    val altColor: Color = Color.DarkGray,
    val headingDeg: Double = 0.0,
    val playerAppearance: PlayerAppearanceConfig? = null,
    val pivot: PlacedCenterPivot? = null,
    val actionState: String = "idle",
)

internal class ViewportScratch3D {
    val path = Path()
    val items = ArrayList<RenderItem3D>(160)
    val cX = FloatArray(16)
    val cY = FloatArray(16)
    val cZ = FloatArray(16)
    val pX = FloatArray(32)
    val pY = FloatArray(32)
    val wX = FloatArray(32)
    val wY = FloatArray(32)
    val wZ = FloatArray(32)
    val w2X = FloatArray(32)
    val w2Y = FloatArray(32)
    val w2Z = FloatArray(32)
}


internal fun parseSafeColor(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

internal fun shadeColor(color: Color, factor: Float): Color = Color(
    red = (color.red * factor).coerceIn(0f, 1f),
    green = (color.green * factor).coerceIn(0f, 1f),
    blue = (color.blue * factor).coerceIn(0f, 1f),
    alpha = color.alpha,
)

internal fun shadeAndFog(color: Color, shade: Float, fogBlend: Float, fogColor: Color): Color {
    val r = (color.red * shade).coerceIn(0f, 1f)
    val g = (color.green * shade).coerceIn(0f, 1f)
    val b = (color.blue * shade).coerceIn(0f, 1f)
    return Color(
        red = r + (fogColor.red - r) * fogBlend,
        green = g + (fogColor.green - g) * fogBlend,
        blue = b + (fogColor.blue - b) * fogBlend,
        alpha = color.alpha,
    )
}

internal fun lerpColor(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * k,
        green = a.green + (b.green - a.green) * k,
        blue = a.blue + (b.blue - a.blue) * k,
        alpha = a.alpha + (b.alpha - a.alpha) * k,
    )
}

internal fun projectRaw(
    wx: Float,
    wy: Float,
    wz: Float,
    pose: CameraPose3D,
    focalPx: Float,
    viewW: Float,
    viewH: Float,
): ProjectedPoint3D {
    val rx = wx - pose.camX
    val ry = wy - pose.camY
    val rz = wz - pose.camZ
    val cz = rx * pose.fwdX + ry * pose.fwdY + rz * pose.fwdZ
    if (cz <= 0.45f) return ProjectedPoint3D(0f, 0f, cz, false)
    val cx = rx * pose.rightX + rz * pose.rightZ
    val cy = rx * pose.upX + ry * pose.upY + rz * pose.upZ
    val inv = focalPx / cz
    return ProjectedPoint3D(viewW * 0.5f + cx * inv, viewH * 0.5f - cy * inv, cz, true)
}

internal fun DrawScope.drawProjectedLine(
    ax: Float, ay: Float, az: Float,
    bx: Float, by: Float, bz: Float,
    color: Color,
    strokeW: Float,
    pose: CameraPose3D,
    focalPx: Float,
    viewW: Float,
    viewH: Float,
) {
    val p1 = projectRaw(ax, ay, az, pose, focalPx, viewW, viewH)
    val p2 = projectRaw(bx, by, bz, pose, focalPx, viewW, viewH)
    if (p1.visible && p2.visible) {
        drawLine(color = color, start = Offset(p1.screenX, p1.screenY), end = Offset(p2.screenX, p2.screenY), strokeWidth = strokeW)
    }
}

internal fun DrawScope.drawProjectedQuad(
    ax: Float, ay: Float, az: Float,
    bx: Float, by: Float, bz: Float,
    cx: Float, cy: Float, cz: Float,
    dx: Float, dy: Float, dz: Float,
    color: Color,
    pose: CameraPose3D,
    focalPx: Float,
    viewW: Float,
    viewH: Float,
    scratch: ViewportScratch3D,
) {
    val cX = scratch.cX
    val cY = scratch.cY
    val cZ = scratch.cZ

    fun toCam(wx: Float, wy: Float, wz: Float, outIdx: Int) {
        val rx = wx - pose.camX
        val ry = wy - pose.camY
        val rz = wz - pose.camZ
        cX[outIdx] = rx * pose.rightX + rz * pose.rightZ
        cY[outIdx] = rx * pose.upX + ry * pose.upY + rz * pose.upZ
        cZ[outIdx] = rx * pose.fwdX + ry * pose.fwdY + rz * pose.fwdZ
    }
    toCam(ax, ay, az, 0)
    toCam(bx, by, bz, 1)
    toCam(cx, cy, cz, 2)
    toCam(dx, dy, dz, 3)

    val zNear = 0.45f
    var allBehind = true
    var anyBehind = false
    for (i in 0 until 4) {
        if (cZ[i] >= zNear) allBehind = false else anyBehind = true
    }
    if (allBehind) return

    val pX = scratch.pX
    val pY = scratch.pY
    var count = 0
    if (!anyBehind) {
        for (i in 0 until 4) {
            val inv = focalPx / cZ[i]
            pX[i] = viewW * 0.5f + cX[i] * inv
            pY[i] = viewH * 0.5f - cY[i] * inv
        }
        count = 4
    } else {
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            val z1 = cZ[i]
            val z2 = cZ[j]
            val in1 = z1 >= zNear
            val in2 = z2 >= zNear
            if (in1 && in2) {
                val inv2 = focalPx / z2
                pX[count] = viewW * 0.5f + cX[j] * inv2
                pY[count] = viewH * 0.5f - cY[j] * inv2
                count++
            } else if (in1 && !in2) {
                val t = ((zNear - z1) / (z2 - z1)).coerceIn(0f, 1f)
                val ix = cX[i] + (cX[j] - cX[i]) * t
                val iy = cY[i] + (cY[j] - cY[i]) * t
                val inv = focalPx / zNear
                pX[count] = viewW * 0.5f + ix * inv
                pY[count] = viewH * 0.5f - iy * inv
                count++
            } else if (!in1 && in2) {
                val t = ((zNear - z1) / (z2 - z1)).coerceIn(0f, 1f)
                val ix = cX[i] + (cX[j] - cX[i]) * t
                val iy = cY[i] + (cY[j] - cY[i]) * t
                val inv = focalPx / zNear
                pX[count] = viewW * 0.5f + ix * inv
                pY[count] = viewH * 0.5f - iy * inv
                count++
                val inv2 = focalPx / z2
                pX[count] = viewW * 0.5f + cX[j] * inv2
                pY[count] = viewH * 0.5f - cY[j] * inv2
                count++
            }
        }
    }
    if (count < 3) return

    var minX = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (i in 0 until count) {
        if (pX[i] < minX) minX = pX[i]
        if (pX[i] > maxX) maxX = pX[i]
        if (pY[i] < minY) minY = pY[i]
        if (pY[i] > maxY) maxY = pY[i]
    }
    if (maxX < -40f || minX > viewW + 40f || maxY < -40f || minY > viewH + 40f) return

    val path = scratch.path
    path.reset()
    path.moveTo(pX[0], pY[0])
    for (i in 1 until count) {
        path.lineTo(pX[i], pY[i])
    }
    path.close()
    drawPath(path, color = color)
}

internal fun DrawScope.drawProjectedTri(
    ax: Float, ay: Float, az: Float,
    bx: Float, by: Float, bz: Float,
    cx: Float, cy: Float, cz: Float,
    color: Color,
    pose: CameraPose3D,
    focalPx: Float,
    viewW: Float,
    viewH: Float,
    scratch: ViewportScratch3D,
) {
    drawProjectedQuad(ax, ay, az, bx, by, bz, cx, cy, cz, ax, ay, az, color, pose, focalPx, viewW, viewH, scratch)
}

internal fun shadeFaceColor(
    baseColor: Color,
    nx: Float,
    ny: Float,
    nz: Float,
    cx: Float,
    cy: Float,
    cz: Float,
    pose: CameraPose3D,
    fallbackShade: Float,
    light: Float,
    atmo: SunAtmosphereState?,
    material: MaterialType,
    aoFactor: Float = 1.0f,
    mudFactor: Float = 0.0f,
    fogFactor: Float = 0.0f,
): Color {
    if (atmo == null) {
        return shadeColor(baseColor, fallbackShade * light)
    }
    val vx = pose.camX - cx
    val vy = pose.camY - cy
    val vz = pose.camZ - cz
    val vLen = max(0.001f, sqrt(vx * vx + vy * vy + vz * vz))
    val argb = packArgb(baseColor.red, baseColor.green, baseColor.blue, baseColor.alpha)
    val shaded = shadeMaterialFace(
        baseArgb = argb,
        nx = nx,
        ny = ny,
        nz = nz,
        viewDirX = vx / vLen,
        viewDirY = vy / vLen,
        viewDirZ = vz / vLen,
        atmo = atmo,
        material = material,
        aoFactor = aoFactor,
        mudFactor = mudFactor,
        fogFactor = fogFactor,
    )
    return Color(shaded)
}

internal fun DrawScope.drawProjectedSunShadow(
    cx: Float,
    groundY: Float,
    cz: Float,
    halfW: Float,
    halfL: Float,
    heightM: Float,
    fx: Float,
    fz: Float,
    rx: Float,
    rz: Float,
    atmo: SunAtmosphereState,
    pose: CameraPose3D,
    focalPx: Float,
    viewW: Float,
    viewH: Float,
    scratch: ViewportScratch3D,
    contactOpacityScale: Float = 1.0f,
) {
    if (atmo.shadowOpacity <= 0.02f) return
    val baseY = groundY + 0.03f
    val (sxOff, szOff) = computeFootprintShadowOffset(heightM, atmo)
    val dirOp = (atmo.shadowOpacity * 0.72f * contactOpacityScale).coerceIn(0.04f, 0.42f)
    val contactOp = (atmo.shadowOpacity * 1.12f * contactOpacityScale).coerceIn(0.08f, 0.54f)

    // Cast directional shadow footprint shifted along sun projection vector
    val scx = cx + sxOff * 0.52f
    val scz = cz + szOff * 0.52f
    val sX0 = scx - rx * (halfW * 1.04f) - fx * (halfL * 1.04f)
    val sZ0 = scz - rz * (halfW * 1.04f) - fz * (halfL * 1.04f)
    val sX1 = scx + rx * (halfW * 1.04f) - fx * (halfL * 1.04f)
    val sZ1 = scz + rz * (halfW * 1.04f) - fz * (halfL * 1.04f)
    val sX2 = scx + rx * (halfW * 1.04f) + fx * (halfL * 1.04f)
    val sZ2 = scz + rz * (halfW * 1.04f) + fz * (halfL * 1.04f)
    val sX3 = scx - rx * (halfW * 1.04f) + fx * (halfL * 1.04f)
    val sZ3 = scz - rz * (halfW * 1.04f) + fz * (halfL * 1.04f)
    drawProjectedQuad(
        sX0, baseY, sZ0,
        sX1, baseY, sZ1,
        sX2, baseY, sZ2,
        sX3, baseY, sZ3,
        Color(0.04f, 0.05f, 0.08f, dirOp),
        pose, focalPx, viewW, viewH, scratch,
    )

    // Crisp ambient occlusion contact shadow directly underneath chassis/base
    val cX0 = cx - rx * (halfW * 0.94f) - fx * (halfL * 0.94f)
    val cZ0 = cz - rz * (halfW * 0.94f) - fz * (halfL * 0.94f)
    val cX1 = cx + rx * (halfW * 0.94f) - fx * (halfL * 0.94f)
    val cZ1 = cz + rz * (halfW * 0.94f) - fz * (halfL * 0.94f)
    val cX2 = cx + rx * (halfW * 0.94f) + fx * (halfL * 0.94f)
    val cZ2 = cz + rz * (halfW * 0.94f) + fz * (halfL * 0.94f)
    val cX3 = cx - rx * (halfW * 0.94f) + fx * (halfL * 0.94f)
    val cZ3 = cz - rz * (halfW * 0.94f) + fz * (halfL * 0.94f)
    drawProjectedQuad(
        cX0, baseY + 0.008f, cZ0,
        cX1, baseY + 0.008f, cZ1,
        cX2, baseY + 0.008f, cZ2,
        cX3, baseY + 0.008f, cZ3,
        Color(0.02f, 0.03f, 0.05f, contactOp),
        pose, focalPx, viewW, viewH, scratch,
    )
}

internal fun DrawScope.drawProjectedGroundGlow(
    cx: Float,
    groundY: Float,
    cz: Float,
    nearHalfW: Float,
    farHalfW: Float,
    reachM: Float,
    fx: Float,
    fz: Float,
    rx: Float,
    rz: Float,
    glowColor: Color,
    pose: CameraPose3D,
    focalPx: Float,
    viewW: Float,
    viewH: Float,
    scratch: ViewportScratch3D,
) {
    val y = groundY + 0.05f
    val x0 = cx - rx * nearHalfW
    val z0 = cz - rz * nearHalfW
    val x1 = cx + rx * nearHalfW
    val z1 = cz + rz * nearHalfW
    val x2 = cx + fx * reachM + rx * farHalfW
    val z2 = cz + fz * reachM + rz * farHalfW
    val x3 = cx + fx * reachM - rx * farHalfW
    val z3 = cz + fz * reachM - rz * farHalfW
    drawProjectedQuad(x0, y, z0, x1, y, z1, x2, y, z2, x3, y, z3, glowColor, pose, focalPx, viewW, viewH, scratch)
}

internal fun DrawScope.drawProjectedWheelRutsStrip(
    xA: Float, yA: Float, zA: Float,
    xB: Float, yB: Float, zB: Float,
    fx: Float, fz: Float,
    rx: Float, rz: Float,
    halfTrack: Float,
    tireHalfWidth: Float,
    rutColor: Color,
    bermColor: Color?,
    pose: CameraPose3D,
    focalPx: Float,
    w: Float,
    h: Float,
    scratch: ViewportScratch3D,
) {
    val hw = tireHalfWidth.coerceIn(0.12f, 0.65f)
    for (side in -1..1 step 2) {
        val sx = rx * halfTrack * side
        val sz = rz * halfTrack * side
        val bx = rx * hw
        val bz = rz * hw
        val ax0 = xA + sx - bx
        val az0 = zA + sz - bz
        val ax1 = xA + sx + bx
        val az1 = zA + sz + bz
        val bx0 = xB + sx - bx + fx * 0.12f
        val bz0 = zB + sz - bz + fz * 0.12f
        val bx1 = xB + sx + bx + fx * 0.12f
        val bz1 = zB + sz + bz + fz * 0.12f
        drawProjectedQuad(
            ax0, yA + 0.025f, az0,
            ax1, yA + 0.025f, az1,
            bx1, yB + 0.025f, bz1,
            bx0, yB + 0.025f, bz0,
            rutColor, pose, focalPx, w, h, scratch,
        )
        if (bermColor != null) {
            val edgeOff = hw * 1.15f
            val eSign = side.toFloat()
            val ex = rx * edgeOff * eSign
            val ez = rz * edgeOff * eSign
            drawProjectedLine(
                xA + sx + ex, yA + 0.03f, zA + sz + ez,
                xB + sx + ex, yB + 0.03f, zB + sz + ez,
                bermColor, 2.2f, pose, focalPx, w, h,
            )
        }
    }
}

internal fun DrawScope.drawRotatedBox3D(
    cx: Float,
    cy: Float,
    cz: Float,
    halfW: Float,
    halfH: Float,
    halfL: Float,
    fx: Float,
    fz: Float,
    rx: Float,
    rz: Float,
    color: Color,
    light: Float,
    pose: CameraPose3D,
    focalPx: Float,
    w: Float,
    h: Float,
    scratch: ViewportScratch3D,
    atmo: SunAtmosphereState? = null,
    material: MaterialType = MaterialType.MATTE_CHASSIS,
    mudFactor: Float = 0f,
    fogFactor: Float = 0f,
) {
    val x0 = cx - rx * halfW - fx * halfL
    val z0 = cz - rz * halfW - fz * halfL
    val x1 = cx + rx * halfW - fx * halfL
    val z1 = cz + rz * halfW - fz * halfL
    val x2 = cx + rx * halfW + fx * halfL
    val z2 = cz + rz * halfW + fz * halfL
    val x3 = cx - rx * halfW + fx * halfL
    val z3 = cz - rz * halfW + fz * halfL
    val yBot = cy - halfH
    val yTop = cy + halfH

    val dCamX = pose.camX - cx
    val dCamZ = pose.camZ - cz
    val frontFaceDot = dCamX * fx + dCamZ * fz
    val rightFaceDot = dCamX * rx + dCamZ * rz

    if (halfH > 0.02f) {
        if (rightFaceDot > 0f) {
            val col = shadeFaceColor(color, rx, 0.05f, rz, cx, cy, cz, pose, 0.85f, light, atmo, material, 0.92f, mudFactor, fogFactor)
            drawProjectedQuad(x1, yBot, z1, x2, yBot, z2, x2, yTop, z2, x1, yTop, z1, col, pose, focalPx, w, h, scratch)
        } else {
            val col = shadeFaceColor(color, -rx, 0.05f, -rz, cx, cy, cz, pose, 0.72f, light, atmo, material, 0.88f, mudFactor, fogFactor)
            drawProjectedQuad(x3, yBot, z3, x0, yBot, z0, x0, yTop, z0, x3, yTop, z3, col, pose, focalPx, w, h, scratch)
        }
        if (frontFaceDot > 0f) {
            val col = shadeFaceColor(color, fx, 0.05f, fz, cx, cy, cz, pose, 0.94f, light, atmo, material, 0.96f, mudFactor, fogFactor)
            drawProjectedQuad(x2, yBot, z2, x3, yBot, z3, x3, yTop, z3, x2, yTop, z2, col, pose, focalPx, w, h, scratch)
        } else {
            val col = shadeFaceColor(color, -fx, 0.05f, -fz, cx, cy, cz, pose, 0.78f, light, atmo, material, 0.90f, mudFactor, fogFactor)
            drawProjectedQuad(x0, yBot, z0, x1, yBot, z1, x1, yTop, z1, x0, yTop, z0, col, pose, focalPx, w, h, scratch)
        }
    }
    if (pose.camY >= yTop - 0.15f) {
        val topCol = shadeFaceColor(color, 0f, 1f, 0f, cx, yTop, cz, pose, 1.07f, light, atmo, material, 1.0f, mudFactor * 0.25f, fogFactor)
        drawProjectedQuad(x0, yTop, z0, x1, yTop, z1, x2, yTop, z2, x3, yTop, z3, topCol, pose, focalPx, w, h, scratch)
    }
}

/**
 * Full 3D oriented box supporting chassis pitch & roll + heading yaw.
 */
internal fun DrawScope.drawTiltedBox3D(
    cx: Float,
    cy: Float,
    cz: Float,
    halfW: Float,
    halfH: Float,
    halfL: Float,
    fx: Float,
    fz: Float,
    rx: Float,
    rz: Float,
    pitchDeg: Float,
    rollDeg: Float,
    color: Color,
    light: Float,
    pose: CameraPose3D,
    focalPx: Float,
    w: Float,
    h: Float,
    scratch: ViewportScratch3D,
    taperTopFactor: Float = 1.0f,
    atmo: SunAtmosphereState? = null,
    material: MaterialType = MaterialType.MATTE_CHASSIS,
    mudFactor: Float = 0f,
    fogFactor: Float = 0f,
) {
    val pitchR = pitchDeg * (PI / 180.0).toFloat()
    val rollR = rollDeg * (PI / 180.0).toFloat()
    val sinP = sin(pitchR)
    val cosP = cos(pitchR)
    val sinR = sin(rollR)
    val cosR = cos(rollR)

    val fX = fx * cosP
    val fY = sinP
    val fZ = fz * cosP

    val rX = rx * cosR
    val rY = -sinR
    val rZ = rz * cosR

    val uX = -fx * sinP + rx * sinR
    val uY = (cosP * cosR).coerceAtLeast(0.5f)
    val uZ = -fz * sinP + rz * sinR

    val topScale = taperTopFactor.coerceIn(0.45f, 1.2f)
    val wx = scratch.wX
    val wy = scratch.wY
    val wz = scratch.wZ

    for (layer in 0..1) {
        val signY = if (layer == 0) -1f else 1f
        val hw = if (layer == 1) halfW * topScale else halfW
        val hl = if (layer == 1) halfL * topScale else halfL
        val hh = halfH * signY
        val base = layer * 4

        wx[base + 0] = cx - rX * hw - fX * hl + uX * hh
        wy[base + 0] = cy - rY * hw - fY * hl + uY * hh
        wz[base + 0] = cz - rZ * hw - fZ * hl + uZ * hh

        wx[base + 1] = cx + rX * hw - fX * hl + uX * hh
        wy[base + 1] = cy + rY * hw - fY * hl + uY * hh
        wz[base + 1] = cz + rZ * hw - fZ * hl + uZ * hh

        wx[base + 2] = cx + rX * hw + fX * hl + uX * hh
        wy[base + 2] = cy + rY * hw + fY * hl + uY * hh
        wz[base + 2] = cz + rZ * hw + fZ * hl + uZ * hh

        wx[base + 3] = cx - rX * hw + fX * hl + uX * hh
        wy[base + 3] = cy - rY * hw + fY * hl + uY * hh
        wz[base + 3] = cz - rZ * hw + fZ * hl + uZ * hh
    }

    val dCamX = pose.camX - cx
    val dCamY = pose.camY - cy
    val dCamZ = pose.camZ - cz
    val frontFaceDot = dCamX * fX + dCamY * fY + dCamZ * fZ
    val rightFaceDot = dCamX * rX + dCamY * rY + dCamZ * rZ
    val topFaceDot = dCamX * uX + dCamY * uY + dCamZ * uZ

    if (rightFaceDot > 0f) {
        val c = shadeFaceColor(color, rX, rY, rZ, cx, cy, cz, pose, 0.85f, light, atmo, material, 0.94f, mudFactor, fogFactor)
        drawProjectedQuad(wx[1], wy[1], wz[1], wx[2], wy[2], wz[2], wx[6], wy[6], wz[6], wx[5], wy[5], wz[5], c, pose, focalPx, w, h, scratch)
    } else {
        val c = shadeFaceColor(color, -rX, -rY, -rZ, cx, cy, cz, pose, 0.72f, light, atmo, material, 0.90f, mudFactor, fogFactor)
        drawProjectedQuad(wx[3], wy[3], wz[3], wx[0], wy[0], wz[0], wx[4], wy[4], wz[4], wx[7], wy[7], wz[7], c, pose, focalPx, w, h, scratch)
    }
    if (frontFaceDot > 0f) {
        val c = shadeFaceColor(color, fX, fY, fZ, cx, cy, cz, pose, 0.95f, light, atmo, material, 0.96f, mudFactor, fogFactor)
        drawProjectedQuad(wx[2], wy[2], wz[2], wx[3], wy[3], wz[3], wx[7], wy[7], wz[7], wx[6], wy[6], wz[6], c, pose, focalPx, w, h, scratch)
    } else {
        val c = shadeFaceColor(color, -fX, -fY, -fZ, cx, cy, cz, pose, 0.78f, light, atmo, material, 0.92f, mudFactor, fogFactor)
        drawProjectedQuad(wx[0], wy[0], wz[0], wx[1], wy[1], wz[1], wx[5], wy[5], wz[5], wx[4], wy[4], wz[4], c, pose, focalPx, w, h, scratch)
    }
    if (topFaceDot > -0.15f) {
        val c = shadeFaceColor(color, uX, uY, uZ, cx, cy + halfH, cz, pose, 1.08f, light, atmo, material, 1.0f, mudFactor * 0.2f, fogFactor)
        drawProjectedQuad(wx[4], wy[4], wz[4], wx[5], wy[5], wz[5], wx[6], wy[6], wz[6], wx[7], wy[7], wz[7], c, pose, focalPx, w, h, scratch)
    }
}

/**
 * Multi-sided extruded lugged agricultural wheel with outer tire tread,
 * inner colored deep-dish rim, wheel lugs/bolts, mud coating, and subtle contact squish.
 */
internal fun DrawScope.drawCylinderWheel3D(
    cx: Float,
    cy: Float,
    cz: Float,
    radius: Float,
    halfWidth: Float,
    spinDeg: Float,
    fx: Float,
    fz: Float,
    rx: Float,
    rz: Float,
    tireColor: Color,
    rimColor: Color,
    light: Float,
    segments: Int,
    pose: CameraPose3D,
    focalPx: Float,
    w: Float,
    h: Float,
    scratch: ViewportScratch3D,
    atmo: SunAtmosphereState? = null,
    mudFactor: Float = 0f,
    drawBolts: Boolean = true,
) {
    val segs = segments.coerceIn(8, 20)
    val spinRad = spinDeg * (PI / 180.0).toFloat()
    val wX = scratch.wX
    val wY = scratch.wY
    val wZ = scratch.wZ
    val rX = scratch.w2X
    val rY = scratch.w2Y
    val rZ = scratch.w2Z

    val rimR = radius * 0.56f
    val hubR = radius * 0.22f
    val sideDot = (pose.camX - cx) * rx + (pose.camZ - cz) * rz
    val outerSign = if (sideDot >= 0f) 1f else -1f
    val innerSign = -outerSign
    val tireMat = if (mudFactor > 0.35f) MaterialType.MUD_RUBBER else MaterialType.RUBBER_TIRE

    for (i in 0 until segs) {
        val a = spinRad + (i.toFloat() / segs.toFloat()) * (2f * PI.toFloat())
        val c = cos(a)
        val s = sin(a)
        val offF = c * radius
        // Gentle tire bottom load squish at ground contact
        val squish = if (s < -0.72f) (-0.72f - s) * 0.14f else 0f
        val offU = (s + squish) * radius
        wX[i] = cx + fx * offF
        wY[i] = cy + offU
        wZ[i] = cz + fz * offF

        val rOffF = c * rimR
        val rOffU = s * rimR
        rX[i] = cx + fx * rOffF
        rY[i] = cy + rOffU
        rZ[i] = cz + fz * rOffF
    }

    // Tread faces around circumference
    for (i in 0 until segs) {
        val j = (i + 1) % segs
        val midY = ((wY[i] + wY[j]) * 0.5f - cy) / max(radius, 0.1f)
        val midF = (((wX[i] + wX[j]) * 0.5f - cx) * fx + ((wZ[i] + wZ[j]) * 0.5f - cz) * fz) / max(radius, 0.1f)
        val normalDotCam = (pose.camY - cy) * midY +
            ((pose.camX - cx) * fx + (pose.camZ - cz) * fz) * midF
        if (normalDotCam > -0.25f) {
            val treadShade = if (i % 2 == 0) 0.94f else 0.72f
            val nx = fx * midF
            val nz = fz * midF
            val treadCol = shadeFaceColor(
                tireColor, nx, midY, nz, cx, cy, cz, pose, treadShade, light,
                atmo, tireMat, aoFactor = if (midY < -0.2f) 0.72f else 0.96f, mudFactor = mudFactor,
            )
            drawProjectedQuad(
                wX[i] + rx * halfWidth * innerSign, wY[i], wZ[i] + rz * halfWidth * innerSign,
                wX[j] + rx * halfWidth * innerSign, wY[j], wZ[j] + rz * halfWidth * innerSign,
                wX[j] + rx * halfWidth * outerSign, wY[j], wZ[j] + rz * halfWidth * outerSign,
                wX[i] + rx * halfWidth * outerSign, wY[i], wZ[i] + rz * halfWidth * outerSign,
                treadCol, pose, focalPx, w, h, scratch,
            )
        }
    }

    // Visible outer sidewall ring
    val outX = rx * halfWidth * outerSign
    val outZ = rz * halfWidth * outerSign
    val sideCol = shadeFaceColor(
        tireColor, rx * outerSign, 0.1f, rz * outerSign, cx, cy, cz, pose, 0.88f, light,
        atmo, tireMat, aoFactor = 0.88f, mudFactor = mudFactor * 0.85f,
    )
    for (i in 0 until segs) {
        val j = (i + 1) % segs
        drawProjectedQuad(
            rX[i] + outX, rY[i], rZ[i] + outZ,
            wX[i] + outX, wY[i], wZ[i] + outZ,
            wX[j] + outX, wY[j], wZ[j] + outZ,
            rX[j] + outX, rY[j], rZ[j] + outZ,
            sideCol, pose, focalPx, w, h, scratch,
        )
    }

    // Recessed deep-dish rim + hub + lug bolts
    val rimOffX = rx * (halfWidth * 0.86f) * outerSign
    val rimOffZ = rz * (halfWidth * 0.86f) * outerSign
    val rimFaceLit = shadeFaceColor(
        rimColor, rx * outerSign, 0.12f, rz * outerSign, cx, cy, cz, pose, 1.0f, light,
        atmo, MaterialType.GLOSS_PAINT, aoFactor = 0.96f, mudFactor = mudFactor * 0.28f,
    )
    val rimFaceAlt = shadeFaceColor(
        rimColor, rx * outerSign * 0.85f, -0.08f, rz * outerSign * 0.85f, cx, cy, cz, pose, 0.90f, light,
        atmo, MaterialType.GLOSS_PAINT, aoFactor = 0.90f, mudFactor = mudFactor * 0.34f,
    )
    for (i in 0 until segs) {
        val j = (i + 1) % segs
        drawProjectedTri(
            cx + rimOffX, cy, cz + rimOffZ,
            rX[i] + rimOffX, rY[i], rZ[i] + rimOffZ,
            rX[j] + rimOffX, rY[j], rZ[j] + rimOffZ,
            if (i % 2 == 0) rimFaceLit else rimFaceAlt,
            pose, focalPx, w, h, scratch,
        )
    }
    // Central hub cap / planetary reduction + lug ring
    val hubOffX = rx * (halfWidth * 1.03f) * outerSign
    val hubOffZ = rz * (halfWidth * 1.03f) * outerSign
    val hubP = projectRaw(cx + hubOffX, cy, cz + hubOffZ, pose, focalPx, w, h)
    val rimEdgeP = projectRaw(cx + hubOffX + fx * hubR, cy, cz + hubOffZ + fz * hubR, pose, focalPx, w, h)
    if (hubP.visible && rimEdgeP.visible) {
        val pxRadius = max(2.2f, hypot(rimEdgeP.screenX - hubP.screenX, rimEdgeP.screenY - hubP.screenY))
        val hubCol = shadeFaceColor(
            rimColor, rx * outerSign, 0.2f, rz * outerSign, cx, cy, cz, pose, 0.78f, light,
            atmo, MaterialType.CHROME_STEEL, aoFactor = 0.92f, mudFactor = mudFactor * 0.22f,
        )
        drawCircle(color = hubCol, radius = pxRadius, center = Offset(hubP.screenX, hubP.screenY))
        if (drawBolts && hubP.depth < 24f && pxRadius >= 4.2f) {
            val boltColor = Color(0xFF1E2228).copy(alpha = 0.88f)
            val boltR = radius * 0.36f
            val boltPx = (pxRadius * 0.24f).coerceIn(1.2f, 3.0f)
            for (b in 0 until 6) {
                val ba = spinRad + b * (PI.toFloat() / 3f)
                val bx = cx + rimOffX + fx * (cos(ba) * boltR)
                val by = cy + sin(ba) * boltR
                val bz = cz + rimOffZ + fz * (cos(ba) * boltR)
                val bp = projectRaw(bx, by, bz, pose, focalPx, w, h)
                if (bp.visible) {
                    drawCircle(color = boltColor, radius = boltPx, center = Offset(bp.screenX, bp.screenY))
                }
            }
        }
    }
}

internal fun DrawScope.drawCylinderY3D(
    cx: Float,
    yBot: Float,
    yTop: Float,
    cz: Float,
    radius: Float,
    color: Color,
    light: Float,
    segments: Int,
    pose: CameraPose3D,
    focalPx: Float,
    w: Float,
    h: Float,
    scratch: ViewportScratch3D,
    atmo: SunAtmosphereState? = null,
    material: MaterialType = MaterialType.GALVANIZED_METAL,
    fogFactor: Float = 0f,
) {
    val segs = segments.coerceIn(8, 20)
    val wX = scratch.wX
    val wZ = scratch.wZ
    for (i in 0 until segs) {
        val a = (i.toFloat() / segs.toFloat()) * (2f * PI.toFloat())
        wX[i] = cx + cos(a) * radius
        wZ[i] = cz + sin(a) * radius
    }
    val midY = (yBot + yTop) * 0.5f
    for (i in 0 until segs) {
        val j = (i + 1) % segs
        val midX = (wX[i] + wX[j]) * 0.5f - cx
        val midZ = (wZ[i] + wZ[j]) * 0.5f - cz
        val dCam = (pose.camX - cx) * midX + (pose.camZ - cz) * midZ
        if (dCam > -0.15f * radius) {
            val nx = midX / max(radius, 0.05f)
            val nz = midZ / max(radius, 0.05f)
            val sunDot = (nx * 0.6f + nz * 0.5f).coerceIn(-0.4f, 0.5f)
            val col = shadeFaceColor(
                color, nx, 0.08f, nz, cx, midY, cz, pose,
                0.85f + sunDot * 0.32f, light, atmo, material,
                aoFactor = 0.94f, fogFactor = fogFactor,
            )
            drawProjectedQuad(
                wX[i], yBot, wZ[i],
                wX[j], yBot, wZ[j],
                wX[j], yTop, wZ[j],
                wX[i], yTop, wZ[i],
                col, pose, focalPx, w, h, scratch,
            )
        }
    }
}

internal fun DrawScope.drawConeY3D(
    cx: Float,
    yBase: Float,
    yTip: Float,
    cz: Float,
    radius: Float,
    color: Color,
    light: Float,
    segments: Int,
    pose: CameraPose3D,
    focalPx: Float,
    w: Float,
    h: Float,
    scratch: ViewportScratch3D,
    atmo: SunAtmosphereState? = null,
    material: MaterialType = MaterialType.GALVANIZED_METAL,
    fogFactor: Float = 0f,
) {
    val segs = segments.coerceIn(8, 20)
    val wX = scratch.wX
    val wZ = scratch.wZ
    for (i in 0 until segs) {
        val a = (i.toFloat() / segs.toFloat()) * (2f * PI.toFloat())
        wX[i] = cx + cos(a) * radius
        wZ[i] = cz + sin(a) * radius
    }
    val slopeY = max(0.3f, (radius / max(0.25f, abs(yTip - yBase))) * 0.55f)
    for (i in 0 until segs) {
        val j = (i + 1) % segs
        val midX = (wX[i] + wX[j]) * 0.5f - cx
        val midZ = (wZ[i] + wZ[j]) * 0.5f - cz
        val nx = (midX / max(radius, 0.05f)) * 0.85f
        val nz = (midZ / max(radius, 0.05f)) * 0.85f
        val sunDot = (nx * 0.6f + nz * 0.5f).coerceIn(-0.4f, 0.5f)
        val col = shadeFaceColor(
            color, nx, slopeY.coerceAtMost(0.75f), nz, cx, (yBase + yTip) * 0.5f, cz, pose,
            0.94f + sunDot * 0.25f, light, atmo, material, aoFactor = 0.98f, fogFactor = fogFactor,
        )
        drawProjectedTri(
            wX[i], yBase, wZ[i],
            wX[j], yBase, wZ[j],
            cx, yTip, cz,
            col, pose, focalPx, w, h, scratch,
        )
    }
}

/**
 * Smooth multi-ring rounded ellipsoid dome for rounded tree canopies, shrubs, heads & organic shapes.
 */
internal fun DrawScope.drawEllipsoidDome3D(
    cx: Float,
    cy: Float,
    cz: Float,
    radiusXZ: Float,
    radiusY: Float,
    color: Color,
    light: Float,
    segments: Int,
    pose: CameraPose3D,
    focalPx: Float,
    w: Float,
    h: Float,
    scratch: ViewportScratch3D,
    rings: Int = 3,
    atmo: SunAtmosphereState? = null,
    material: MaterialType = MaterialType.FOLIAGE,
    fogFactor: Float = 0f,
) {
    val segs = segments.coerceIn(8, 16)
    val rSteps = rings.coerceIn(2, 4)
    val rx1 = scratch.wX
    val rz1 = scratch.wZ
    val rx2 = scratch.w2X
    val rz2 = scratch.w2Z

    for (rIdx in 0 until rSteps) {
        val phi0 = -PI.toFloat() * 0.35f + (rIdx.toFloat() / rSteps) * (PI.toFloat() * 0.80f)
        val phi1 = -PI.toFloat() * 0.35f + ((rIdx + 1).toFloat() / rSteps) * (PI.toFloat() * 0.80f)
        val y0 = cy + sin(phi0) * radiusY
        val y1 = cy + sin(phi1) * radiusY
        val rad0 = cos(phi0).coerceAtLeast(0.12f) * radiusXZ
        val rad1 = cos(phi1).coerceAtLeast(0.06f) * radiusXZ
        val ringLit = 0.82f + (rIdx.toFloat() / rSteps) * 0.26f
        val ny = sin((phi0 + phi1) * 0.5f).coerceIn(-0.4f, 0.95f)
        val aoRing = (0.72f + (rIdx.toFloat() / rSteps) * 0.28f).coerceIn(0.68f, 1.0f)

        for (i in 0 until segs) {
            val a = (i.toFloat() / segs.toFloat()) * (2f * PI.toFloat())
            val ca = cos(a)
            val sa = sin(a)
            rx1[i] = cx + ca * rad0
            rz1[i] = cz + sa * rad0
            rx2[i] = cx + ca * rad1
            rz2[i] = cz + sa * rad1
        }
        for (i in 0 until segs) {
            val j = (i + 1) % segs
            val midX = ((rx1[i] + rx1[j]) * 0.5f - cx) / max(radiusXZ, 0.1f)
            val midZ = ((rz1[i] + rz1[j]) * 0.5f - cz) / max(radiusXZ, 0.1f)
            val dCam = (pose.camX - cx) * midX + (pose.camZ - cz) * midZ + (pose.camY - cy) * 0.35f
            if (dCam > -0.25f) {
                val sunDot = (midX * 0.55f + midZ * 0.45f).coerceIn(-0.35f, 0.45f)
                val col = shadeFaceColor(
                    color, midX * 0.85f, ny, midZ * 0.85f, cx, cy, cz, pose,
                    ringLit + sunDot * 0.25f, light, atmo, material,
                    aoFactor = aoRing, fogFactor = fogFactor,
                )
                drawProjectedQuad(
                    rx1[i], y0, rz1[i],
                    rx1[j], y0, rz1[j],
                    rx2[j], y1, rz2[j],
                    rx2[i], y1, rz2[i],
                    col, pose, focalPx, w, h, scratch,
                )
            }
        }
    }
}

private fun hypot(dx: Float, dy: Float): Float = sqrt(dx * dx + dy * dy)
