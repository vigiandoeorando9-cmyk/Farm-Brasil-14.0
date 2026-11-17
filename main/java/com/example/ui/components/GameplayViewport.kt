package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.domain.CameraMode
import com.example.domain.ESTABLISHMENTS
import com.example.domain.GameSettings
import com.example.domain.LANDMARKS
import com.example.domain.SoilState
import com.example.domain.WEATHER_META
import com.example.domain.WeatherType
import com.example.domain.WorldPropType
import com.example.domain.WorldState
import com.example.domain.clampGraphicsConfigForDevice
import com.example.domain.getActiveGraphicsConfig
import com.example.domain.findAllSellStationsInWorld
import com.example.domain.getMapById
import com.example.domain.getVehicleDef
import com.example.firebase.RoomMemberDoc
import com.example.simulation.ConstructionPlacementPreview
import com.example.simulation.DiagnosticEngine
import com.example.simulation.StreamingTelemetrySnapshot
import com.example.simulation.WorldStreamingEngine
import com.example.simulation.getTerrainHeightAt
import com.example.simulation.getWorldSpanM
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

@Composable
fun GameplayViewport(
    worldState: WorldState,
    cameraMode: CameraMode,
    remoteMembers: List<RoomMemberDoc>,
    localUid: String?,
    cameraDistanceFactor: Float = 1.0f,
    settings: GameSettings = GameSettings(),
    constructionPreview: ConstructionPlacementPreview? = null,
    onMoveConstructionToWorld: ((Double, Double) -> Unit)? = null,
    onStreamingTelemetry: ((StreamingTelemetrySnapshot) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var panOffsetX by remember(worldState.mapId) { mutableFloatStateOf(0f) }
    var panOffsetZ by remember(worldState.mapId) { mutableFloatStateOf(0f) }
    var orbitYawDeg by remember(cameraMode) { mutableFloatStateOf(0f) }
    var orbitPitchDeg by remember(cameraMode) { mutableFloatStateOf(0f) }
    var pinchZoom by remember { mutableFloatStateOf(1.0f) }
    var lastFrameNanos by remember { mutableLongStateOf(0L) }
    var animClockSec by remember { mutableFloatStateOf(0f) }

    val scratch = remember { ViewportScratch3D() }
    val particlePool = remember { ParticlePool3D(capacity = 320) }
    val lastTelemetryAtMs = remember { longArrayOf(0L) }

    val mapDef = remember(worldState.mapId) { getMapById(worldState.mapId) }
    val gfx = remember(settings) { clampGraphicsConfigForDevice(getActiveGraphicsConfig(settings)) }

    // Damped camera smoothing state (interpolates cleanly across ticks; snaps on mode/vehicle/teleport jumps)
    var smoothCamX by remember { mutableFloatStateOf(0f) }
    var smoothCamY by remember { mutableFloatStateOf(0f) }
    var smoothCamZ by remember { mutableFloatStateOf(0f) }
    var smoothTargetX by remember { mutableFloatStateOf(0f) }
    var smoothTargetY by remember { mutableFloatStateOf(0f) }
    var smoothTargetZ by remember { mutableFloatStateOf(0f) }
    var smoothYawDeg by remember { mutableFloatStateOf(0f) }
    var smoothPitchDeg by remember { mutableFloatStateOf(0f) }
    var camInitDone by remember { androidx.compose.runtime.mutableStateOf(false) }
    var lastCamMode by remember { androidx.compose.runtime.mutableStateOf(cameraMode) }
    var lastVehUid by remember { androidx.compose.runtime.mutableStateOf(worldState.activeVehicleUid) }
    var prevWeather by remember { androidx.compose.runtime.mutableStateOf(worldState.weather) }
    var targetWeather by remember { androidx.compose.runtime.mutableStateOf(worldState.weather) }
    var weatherBlendT by remember { mutableFloatStateOf(1f) }

    val rawPose = computeCameraPose3D(
        worldState = worldState,
        cameraMode = cameraMode,
        cameraDistanceFactor = cameraDistanceFactor,
        fovDeg = settings.cameraFov.toFloat(),
        orbitYawDeg = orbitYawDeg,
        orbitPitchDeg = orbitPitchDeg,
        pinchZoom = pinchZoom,
        panOffsetX = panOffsetX,
        panOffsetZ = panOffsetZ,
    )

    // Advance particles, camera smoothing, and ambient clock strictly with simulation state ticks
    LaunchedEffect(worldState, cameraMode) {
        val now = System.nanoTime()
        val dtSec = if (lastFrameNanos == 0L) {
            0.033f
        } else {
            ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0.008f, 0.08f)
        }
        lastFrameNanos = now
        animClockSec += dtSec
        if (worldState.weather != targetWeather) {
            prevWeather = targetWeather
            targetWeather = worldState.weather
            weatherBlendT = 0f
        } else if (weatherBlendT < 1f) {
            weatherBlendT = (weatherBlendT + dtSec / 3.5f).coerceIn(0f, 1f)
        }
        val atmoTick = computeBlendedSunAtmosphereState(
            hourOfDay = worldState.hourOfDay,
            fromWeather = prevWeather,
            toWeather = targetWeather,
            blendT = weatherBlendT,
            gfx = gfx,
            animClockSec = animClockSec,
        )
        particlePool.stepAndEmit(worldState, dtSec, gfx, atmoTick)

        val dJumpSq = (rawPose.camX - smoothCamX) * (rawPose.camX - smoothCamX) +
            (rawPose.camZ - smoothCamZ) * (rawPose.camZ - smoothCamZ)
        val shouldSnap = !camInitDone || lastCamMode != cameraMode || lastVehUid != worldState.activeVehicleUid || dJumpSq > 144f
        if (shouldSnap) {
            smoothCamX = rawPose.camX
            smoothCamY = rawPose.camY
            smoothCamZ = rawPose.camZ
            smoothTargetX = rawPose.targetX
            smoothTargetY = rawPose.targetY
            smoothTargetZ = rawPose.targetZ
            smoothYawDeg = rawPose.yawDeg
            smoothPitchDeg = rawPose.pitchDeg
            camInitDone = true
            lastCamMode = cameraMode
            lastVehUid = worldState.activeVehicleUid
        } else {
            val posBlend = (dtSec * 11.5f).coerceIn(0.18f, 1f)
            val rotBlend = (dtSec * 12.5f).coerceIn(0.20f, 1f)
            smoothCamX += (rawPose.camX - smoothCamX) * posBlend
            smoothCamY += (rawPose.camY - smoothCamY) * posBlend
            smoothCamZ += (rawPose.camZ - smoothCamZ) * posBlend
            smoothTargetX += (rawPose.targetX - smoothTargetX) * posBlend
            smoothTargetY += (rawPose.targetY - smoothTargetY) * posBlend
            smoothTargetZ += (rawPose.targetZ - smoothTargetZ) * posBlend
            var yawDelta = ((rawPose.yawDeg - smoothYawDeg + 540f) % 360f) - 180f
            smoothYawDeg += yawDelta * rotBlend
            smoothPitchDeg += (rawPose.pitchDeg - smoothPitchDeg) * rotBlend
        }
    }

    val activeVehForShake = worldState.activeVehicleUid?.let { uid -> worldState.vehicles.find { it.uid == uid } }
    val cabShakeY = if (cameraMode == CameraMode.CABINE && activeVehForShake != null &&
        (activeVehForShake.engineRunning == true || abs(activeVehForShake.speedMs) > 0.35)
    ) {
        val vib = sin(animClockSec * 29f) * 0.0035f
        val slipBump = sin(animClockSec * 17f) * (activeVehForShake.wheelSlip.toFloat().coerceIn(0f, 0.8f) * 0.012f)
        vib + slipBump
    } else 0f

    val pose = if (camInitDone) {
        val yawR = smoothYawDeg * (PI / 180.0).toFloat()
        val pitR = smoothPitchDeg * (PI / 180.0).toFloat()
        val sinY = sin(yawR)
        val cosY = cos(yawR)
        val sinP = sin(pitR)
        val cosP = cos(pitR)
        rawPose.copy(
            camX = smoothCamX,
            camY = smoothCamY + cabShakeY,
            camZ = smoothCamZ,
            targetX = smoothTargetX,
            targetY = smoothTargetY + cabShakeY,
            targetZ = smoothTargetZ,
            yawDeg = smoothYawDeg,
            pitchDeg = smoothPitchDeg,
            fwdX = sinY * cosP,
            fwdY = -sinP,
            fwdZ = cosY * cosP,
            rightX = cosY,
            rightZ = -sinY,
            upX = sinY * sinP,
            upY = cosP,
            upZ = cosY * sinP,
        )
    } else rawPose

    var viewportSize by remember { androidx.compose.runtime.mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it }
            .pointerInput(cameraMode, constructionPreview != null) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        val activePreview = constructionPreview
                        val moveCallback = onMoveConstructionToWorld
                        if (activePreview != null && moveCallback != null && viewportSize.width > 8 && viewportSize.height > 8) {
                            val vw = viewportSize.width.toFloat()
                            val vh = viewportSize.height.toFloat()
                            val halfFovR = (pose.fovDeg * 0.5f) * (PI / 180.0).toFloat()
                            val focal = (vh * 0.5f) / tan(halfFovR).coerceAtLeast(0.25f)
                            val camRayX = (tapOffset.x - vw * 0.5f) / focal
                            val camRayY = (vh * 0.5f - tapOffset.y) / focal
                            val dirX = camRayX * pose.rightX + camRayY * pose.upX + pose.fwdX
                            val dirY = camRayY * pose.upY + pose.fwdY
                            val dirZ = camRayX * pose.rightZ + camRayY * pose.upZ + pose.fwdZ
                            if (dirY < -0.04f) {
                                val tHit = (activePreview.groundY.toFloat() - pose.camY) / dirY
                                if (tHit in 2.5f..140f) {
                                    moveCallback((pose.camX + dirX * tHit).toDouble(), (pose.camZ + dirZ * tHit).toDouble())
                                }
                            }
                        }
                    },
                    onDoubleTap = {
                        orbitYawDeg = 0f
                        orbitPitchDeg = 0f
                        pinchZoom = 1.0f
                        panOffsetX = 0f
                        panOffsetZ = 0f
                    }
                )
            }
            .pointerInput(cameraMode, settings.cameraSensitivity) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val sens = settings.cameraSensitivity.toFloat().coerceIn(0.4f, 2.2f)
                    if (abs(zoom - 1f) > 0.002f) {
                        pinchZoom = (pinchZoom * zoom).coerceIn(0.55f, 2.2f)
                    }
                    if (cameraMode == CameraMode.LIVRE) {
                        val yawR = pose.yawDeg * (PI / 180.0).toFloat()
                        val rightX = cos(yawR)
                        val rightZ = -sin(yawR)
                        val fwdX = sin(yawR)
                        val fwdZ = cos(yawR)
                        panOffsetX = (panOffsetX - (pan.x * rightX - pan.y * fwdX) * 0.06f * sens).coerceIn(-220f, 220f)
                        panOffsetZ = (panOffsetZ - (pan.x * rightZ - pan.y * fwdZ) * 0.06f * sens).coerceIn(-220f, 220f)
                    } else {
                        orbitYawDeg = ((orbitYawDeg + pan.x * 0.22f * sens) % 360f)
                        orbitPitchDeg = (orbitPitchDeg + pan.y * 0.14f * sens).coerceIn(-8f, 26f)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w < 8f || h < 8f) return@Canvas
            val frameStartNs = DiagnosticEngine.beginFrame()
            try {

            val halfFovRad = (pose.fovDeg * 0.5f) * (PI / 180.0).toFloat()
            val focalPx = (h * 0.5f) / tan(halfFovRad).coerceAtLeast(0.25f)

            val hour = worldState.hourOfDay.toFloat()
            val atmo = computeBlendedSunAtmosphereState(
                hourOfDay = worldState.hourOfDay,
                fromWeather = prevWeather,
                toWeather = targetWeather,
                blendT = weatherBlendT,
                gfx = gfx,
                animClockSec = animClockSec,
            )
            val cloudCover = atmo.cloudCover
            val rainInt = atmo.rainIntensity

            val horizonScreenY = (h * 0.5f + tan(pose.pitchDeg * (PI / 180.0).toFloat()) * focalPx)
                .coerceIn(h * 0.08f, h * 0.88f)

            // 1. Dynamic 3D Sky & Sun/Moon Dome + Volumetric Sunbeams & Layered Hills
            drawSkyAndAtmosphere(
                w = w,
                h = h,
                horizonY = horizonScreenY,
                hour = hour,
                cloudCover = cloudCover,
                pose = pose,
                focalPx = focalPx,
                mapDef = mapDef,
                gfx = gfx,
                animClockSec = animClockSec,
                atmo = atmo,
            )

            // 2. World Streaming Window (Chunking, LOD0/LOD1/LOD2, Frustum & RAM budget) + 3D Terrain Grid
            val streamingBudget = WorldStreamingEngine.resolveStreamingPlatformBudget(gfx, settings.graphicsPreset)
            val streamingWindow = WorldStreamingEngine.buildActiveStreamingWindow(
                state = worldState,
                camX = pose.camX.toDouble(),
                camZ = pose.camZ.toDouble(),
                fwdX = pose.fwdX.toDouble(),
                fwdZ = pose.fwdZ.toDouble(),
                gfx = gfx,
                preset = settings.graphicsPreset,
            )
            val nowMs = System.currentTimeMillis()
            if (onStreamingTelemetry != null && nowMs - lastTelemetryAtMs[0] >= 480L) {
                lastTelemetryAtMs[0] = nowMs
                onStreamingTelemetry.invoke(streamingWindow.telemetry)
            }

            val maxDrawM = streamingBudget.maxDrawDistanceM.toFloat().coerceIn(115f, 460f)
            val isNight = atmo.isNight
            val twilightDarken = when {
                hour < 5.5f || hour > 20.5f -> 0.42f
                hour < 7.0f -> 0.42f + ((hour - 5.5f) / 1.5f) * 0.58f
                hour > 18.0f -> 1.0f - ((hour - 18.0f) / 2.5f) * 0.58f
                else -> 1.0f
            }.coerceIn(0.38f, 1.0f) * (1f - cloudCover * 0.22f)

            drawDetailedTerrain(
                worldState = worldState,
                mapDef = mapDef,
                gfx = gfx,
                cameraMode = cameraMode,
                pose = pose,
                focalPx = focalPx,
                w = w,
                h = h,
                maxDrawM = maxDrawM,
                isNight = isNight,
                twilightDarken = twilightDarken,
                rainIntensity = rainInt,
                animClockSec = animClockSec,
                scratch = scratch,
                atmo = atmo,
                streamingBudget = streamingBudget,
            )

            // 3. Reflective Water Puddles with Raindrop Ripples, 3D Wheel Rut Strips & GPS AB Line
            val skyReflCol = lerpColor(
                Color(0xFF1F4C63),
                Color(atmo.skyAmbientR.coerceIn(0f, 1f), atmo.skyAmbientG.coerceIn(0f, 1f), atmo.skyAmbientB.coerceIn(0f, 1f)),
                0.55f,
            )
            for (p in (worldState.puddles ?: emptyList())) {
                val dx = p.x.toFloat() - pose.camX
                val dz = p.z.toFloat() - pose.camZ
                if (dx * dx + dz * dz > 110f * 110f) continue
                val py = getTerrainHeightAt(worldState, p.x, p.z).toFloat() + 0.03f
                val r = p.radius.toFloat()
                drawProjectedQuad(
                    (p.x - r).toFloat(), py, (p.z - r * 0.72f).toFloat(),
                    (p.x + r).toFloat(), py, (p.z - r * 0.72f).toFloat(),
                    (p.x + r).toFloat(), py, (p.z + r * 0.72f).toFloat(),
                    (p.x - r).toFloat(), py, (p.z + r * 0.72f).toFloat(),
                    skyReflCol.copy(alpha = 0.56f),
                    pose, focalPx, w, h, scratch,
                )
                // Subtle specular sun glint inside puddle + animated concentric raindrop ripples
                drawProjectedQuad(
                    (p.x - r * 0.42f).toFloat(), py + 0.004f, (p.z - r * 0.25f).toFloat(),
                    (p.x + r * 0.42f).toFloat(), py + 0.004f, (p.z - r * 0.25f).toFloat(),
                    (p.x + r * 0.42f).toFloat(), py + 0.004f, (p.z + r * 0.25f).toFloat(),
                    (p.x - r * 0.42f).toFloat(), py + 0.004f, (p.z + r * 0.25f).toFloat(),
                    Color(atmo.sunR.coerceIn(0f, 1f), atmo.sunG.coerceIn(0f, 1f), atmo.sunB.coerceIn(0f, 1f), 0.14f + atmo.wetness * 0.12f),
                    pose, focalPx, w, h, scratch,
                )
                if (rainInt > 0.15f) {
                    val rippleP = projectRaw(p.x.toFloat(), py + 0.01f, p.z.toFloat(), pose, focalPx, w, h)
                    if (rippleP.visible && rippleP.depth < 70f) {
                        val rpPhase = (animClockSec * 1.45f + (p.x.toFloat() * 0.3f)) % 1f
                        val ringRad = ((r * rpPhase) * focalPx / max(rippleP.depth, 1f)).coerceIn(3f, 28f)
                        drawCircle(
                            color = Color.White.copy(alpha = (1f - rpPhase) * 0.38f * rainInt),
                            radius = ringRad,
                            center = Offset(rippleP.screenX, rippleP.screenY),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4f),
                        )
                    }
                }
            }

            drawPondsWaterOverlay3D(
                worldState = worldState,
                pose = pose,
                focalPx = focalPx,
                w = w,
                h = h,
                animClockSec = animClockSec,
                atmo = atmo,
                scratch = scratch,
            )
            drawEarthworkMarks3D(
                worldState = worldState,
                pose = pose,
                focalPx = focalPx,
                w = w,
                h = h,
                twilightDarken = twilightDarken,
            )
            drawWaterProspectPins3D(
                worldState = worldState,
                pose = pose,
                focalPx = focalPx,
                w = w,
                h = h,
                animClockSec = animClockSec,
                scratch = scratch,
            )

            for (t in worldState.tireTracks.takeLast(260)) {
                val dx = t.x.toFloat() - pose.camX
                val dz = t.z.toFloat() - pose.camZ
                if (dx * dx + dz * dz > 95f * 95f) continue
                val ty = getTerrainHeightAt(worldState, t.x, t.z).toFloat()
                val rad = t.headingDeg.toFloat() * (PI / 180.0).toFloat()
                val rx = cos(rad)
                val rz = -sin(rad)
                val fx = sin(rad)
                val fz = cos(rad)
                val fade = (1.0 - (t.age / 75.0)).toFloat().coerceIn(0.10f, 0.72f)
                val rutCol = Color(
                    red = (t.soilR.toFloat() * twilightDarken).coerceIn(0.04f, 0.70f),
                    green = (t.soilG.toFloat() * twilightDarken).coerceIn(0.03f, 0.55f),
                    blue = (t.soilB.toFloat() * twilightDarken).coerceIn(0.02f, 0.42f),
                    alpha = fade,
                )
                val bermCol = if (t.isMud || t.isSlip) rutCol.copy(alpha = (fade * 0.55f).coerceIn(0.05f, 0.40f)) else null
                val halfLen = if (t.isMud) 0.58f else 0.50f
                drawProjectedWheelRutsStrip(
                    xA = (t.x - fx * halfLen).toFloat(), yA = ty, zA = (t.z - fz * halfLen).toFloat(),
                    xB = (t.x + fx * halfLen).toFloat(), yB = ty, zB = (t.z + fz * halfLen).toFloat(),
                    fx = fx, fz = fz, rx = rx, rz = rz,
                    halfTrack = (t.trackWidthM.toFloat() * 0.46f).coerceIn(0.45f, 1.8f),
                    tireHalfWidth = (t.tireWidthM.toFloat() * 0.52f).coerceIn(0.14f, 0.45f),
                    rutColor = rutCol,
                    bermColor = bermCol,
                    pose = pose, focalPx = focalPx, w = w, h = h, scratch = scratch,
                )
            }

            val activeVeh = worldState.activeVehicleUid?.let { uid -> worldState.vehicles.find { it.uid == uid } }
            activeVeh?.gpsPointA?.let { ptA ->
                val yA = getTerrainHeightAt(worldState, ptA.x, ptA.z).toFloat() + 0.25f
                val pA = projectRaw(ptA.x.toFloat(), yA, ptA.z.toFloat(), pose, focalPx, w, h)
                if (pA.visible) drawCircle(Color(0xFF00E676), 7f, Offset(pA.screenX, pA.screenY))
                activeVeh.gpsPointB?.let { ptB ->
                    val yB = getTerrainHeightAt(worldState, ptB.x, ptB.z).toFloat() + 0.25f
                    drawProjectedLine(ptA.x.toFloat(), yA, ptA.z.toFloat(), ptB.x.toFloat(), yB, ptB.z.toFloat(), Color(0xFF00E676).copy(alpha = 0.72f), 3.0f, pose, focalPx, w, h)
                    val pB = projectRaw(ptB.x.toFloat(), yB, ptB.z.toFloat(), pose, focalPx, w, h)
                    if (pB.visible) drawCircle(Color(0xFFFF5252), 7f, Offset(pB.screenX, pB.screenY))
                }
            }

            // 4. Collect & Depth-Sort All 3D Entities
            val items = scratch.items
            items.clear()
            val objCullM = min(maxDrawM, 290f)
            val objCullSq = objCullM * objCullM
            val detailLevel = gfx.vehicleDetailLevel

            fun addIfInView(x: Double, z: Double, builder: (depth: Float, gy: Double) -> RenderItem3D) {
                val dx = x.toFloat() - pose.camX
                val dz = z.toFloat() - pose.camZ
                if (dx * dx + dz * dz > objCullSq) return
                val gy = getTerrainHeightAt(worldState, x, z)
                val depth = dx * pose.fwdX + (gy.toFloat() - pose.camY) * pose.fwdY + dz * pose.fwdZ
                if (depth > 0.3f) items.add(builder(depth, gy))
            }

            for (b in streamingWindow.visibleBuildings) {
                addIfInView(b.x, b.z) { d, gy ->
                    RenderItem3D(
                        ItemKind3D.BUILDING, d, b.x, gy, b.z,
                        building = b,
                        buildingType = b.type,
                        buildingRotDeg = b.rotationDeg,
                        buildingGateOpen = b.isGateOpen,
                    )
                }
            }
            for (piv in streamingWindow.visiblePivots) {
                val dx = piv.centerX.toFloat() - pose.camX
                val dz = piv.centerZ.toFloat() - pose.camZ
                val pivMax = maxDrawM + piv.radiusM.toFloat() + 12f
                if (dx * dx + dz * dz <= pivMax * pivMax) {
                    val gy = getTerrainHeightAt(worldState, piv.centerX, piv.centerZ)
                    val depth = max(1.0f, dx * pose.fwdX + (gy.toFloat() - pose.camY) * pose.fwdY + dz * pose.fwdZ)
                    items.add(RenderItem3D(ItemKind3D.CENTER_PIVOT, depth, piv.centerX, gy, piv.centerZ, pivot = piv))
                }
            }
            for (pr in streamingWindow.visibleProps) {
                val isTree = pr.type == WorldPropType.TREE ||
                    pr.type == WorldPropType.TREE_PINE ||
                    pr.type == WorldPropType.TREE_EUCALYPTUS ||
                    pr.type == WorldPropType.TREE_FRUIT ||
                    pr.type == WorldPropType.TREE_NATIVE ||
                    pr.type == WorldPropType.TREE_ARAUCARIA ||
                    pr.type == WorldPropType.TREE_CERRADO ||
                    pr.type == WorldPropType.TREE_PALM_AMAZON ||
                    pr.type == WorldPropType.TREE_IPE_AMARELO ||
                    pr.type == WorldPropType.TREE_IPE_ROXO ||
                    pr.type == WorldPropType.TREE_SHADE_MANGUEIRA ||
                    pr.type == WorldPropType.TREE_COQUEIRO ||
                    pr.type == WorldPropType.TREE_CAPOEIRA
                addIfInView(pr.x, pr.z) { d, gy ->
                    RenderItem3D(if (isTree) ItemKind3D.TREE else ItemKind3D.PROP, d, pr.x, gy, pr.z, prop = pr)
                }
            }
            for (imp in worldState.implements) {
                addIfInView(imp.x, imp.z) { d, gy ->
                    RenderItem3D(ItemKind3D.IMPLEMENT, d, imp.x, gy, imp.z, implement = imp)
                }
            }
            for (v in worldState.vehicles) {
                addIfInView(v.x, v.z) { d, gy ->
                    RenderItem3D(ItemKind3D.VEHICLE, d, v.x, gy, v.z, vehicle = v)
                }
            }
            for (rv in (worldState.roadVehicles ?: emptyList())) {
                addIfInView(rv.x, rv.z) { d, gy ->
                    RenderItem3D(ItemKind3D.ROAD_VEH, d, rv.x, gy, rv.z, roadVehicle = rv)
                }
            }
            for (ctr in (worldState.outsourcingContracts ?: emptyList())) {
                if (ctr.status == "EM_OPERACAO" || ctr.status == "PAUSADO_IMPREVISTO") {
                    addIfInView(ctr.vehicleX, ctr.vehicleZ) { d, gy ->
                        RenderItem3D(ItemKind3D.CONTRACTOR, d, ctr.vehicleX, gy, ctr.vehicleZ, headingDeg = ctr.vehicleHeadingDeg)
                    }
                }
            }
            for (npc in (worldState.npcAgents ?: emptyList())) {
                addIfInView(npc.x, npc.z) { d, gy ->
                    RenderItem3D(
                        ItemKind3D.NPC, d, npc.x, gy, npc.z,
                        labelColor = parseSafeColor(npc.shirtHex, Color(0xFF2196F3)),
                        altColor = parseSafeColor(npc.skinHex, Color(0xFFD9A17A)),
                        headingDeg = npc.headingDeg,
                    )
                }
            }
            for (m in remoteMembers) {
                if (m.memberUid == localUid || !m.isApproved) continue
                addIfInView(m.x, m.z) { d, gy ->
                    RenderItem3D(ItemKind3D.TEAMMATE, d, m.x, gy, m.z, labelColor = Color(0xFF29B6F6), headingDeg = m.headingDeg)
                }
            }
            if (worldState.activeVehicleUid == null) {
                val p = worldState.player
                addIfInView(p.x, p.z) { d, gy ->
                    RenderItem3D(
                        ItemKind3D.PLAYER, d, p.x, gy, p.z,
                        labelColor = parseSafeColor(p.appearance?.shirtColorHex, Color(0xFF1E2124)),
                        altColor = parseSafeColor(p.appearance?.hatColorHex, Color(0xFF3A2A20)),
                        headingDeg = p.headingDeg,
                        playerAppearance = p.appearance,
                        actionState = p.actionState ?: "idle",
                        playerTool = p.tool,
                    )
                }
            }

            items.sortByDescending { it.depth }

            for (item in items) {
                when (item.kind) {
                    ItemKind3D.BUILDING -> drawDetailedBuilding3D(item, worldState, pose, focalPx, w, h, twilightDarken, isNight, detailLevel, scratch, atmo, animClockSec)
                    ItemKind3D.TREE -> drawTree3D(item, pose, focalPx, w, h, twilightDarken, animClockSec, detailLevel, scratch, atmo)
                    ItemKind3D.PROP -> drawProp3D(item, pose, focalPx, w, h, twilightDarken, scratch, atmo)
                    ItemKind3D.IMPLEMENT -> item.implement?.let { imp ->
                        drawImplement3D(imp, item.y.toFloat(), gfx, pose, focalPx, w, h, twilightDarken, scratch, atmo, mudFactor = (atmo.wetness * 0.45f))
                    }
                    ItemKind3D.VEHICLE -> item.vehicle?.let { v ->
                        val isCockpit = cameraMode == CameraMode.CABINE && v.uid == worldState.activeVehicleUid
                        val driverAppearance = if (v.uid == worldState.activeVehicleUid) worldState.player.appearance else null
                        val attachedImp = v.attachedImplementUid?.let { iUid -> worldState.implements.find { it.uid == iUid } }
                        drawVehicle3D(v, item.y.toFloat(), isCockpit, isNight, gfx, animClockSec, pose, focalPx, w, h, twilightDarken, scratch, driverAppearance, atmo, attachedImp)
                    }
                    ItemKind3D.ROAD_VEH -> item.roadVehicle?.let { rv ->
                        drawSimpleBoxVehicle3D(rv.x.toFloat(), item.y.toFloat(), rv.z.toFloat(), rv.headingDeg.toFloat(), 2.1f, 1.9f, 4.4f, parseSafeColor(rv.colorHex, Color(0xFF607D8B)), twilightDarken, pose, focalPx, w, h, scratch, atmo)
                    }
                    ItemKind3D.CONTRACTOR -> {
                        drawSimpleBoxVehicle3D(item.x.toFloat(), item.y.toFloat(), item.z.toFloat(), item.headingDeg.toFloat(), 2.5f, 2.8f, 4.6f, Color(0xFFF59E0B), twilightDarken, pose, focalPx, w, h, scratch, atmo)
                    }
                    ItemKind3D.PLAYER, ItemKind3D.NPC, ItemKind3D.TEAMMATE -> {
                        drawCharacter3D(item, pose, focalPx, w, h, twilightDarken, animClockSec, scratch)
                    }
                    ItemKind3D.CENTER_PIVOT -> item.pivot?.let { piv ->
                        drawCenterPivot3D(piv, worldState, pose, focalPx, w, h, twilightDarken, isNight, detailLevel, scratch, atmo, animClockSec)
                    }
                }
            }

            // 4b. Translucent 3D Ghost Preview for Property Construction Mode
            constructionPreview?.let { preview ->
                drawConstructionPreviewGhost3D(
                    preview = preview,
                    worldState = worldState,
                    pose = pose,
                    focalPx = focalPx,
                    w = w,
                    h = h,
                    animClockSec = animClockSec,
                    scratch = scratch,
                )
            }

            // 5. Real-time Particles (Exhaust Smoke, Soil Dust, Mud Splash, Spray Mist, Grain Stream)
            particlePool.render(this, pose, focalPx, w, h)

            // 6. Sculpted 3D Cab Interior Frame, Mirrors, Wiper, Raindrops & Rotating Steering Wheel in CABINE mode
            if (cameraMode == CameraMode.CABINE && activeVeh != null) {
                drawCabInteriorOverlay(activeVeh, settings, atmo, w, h)
            }

            // 7. Rain Streaks, Storm Lightning Veil & Fog Atmospheric Pass
            if (rainInt > 0.1f) {
                drawRect(color = Color(0xFF162430).copy(alpha = 0.22f * rainInt))
                val drops = (rainInt * 78 * gfx.weatherEffectsDensity.toFloat().coerceIn(0.4f, 1.3f)).toInt()
                val phase = ((worldState.hourOfDay * 6000.0) % 1000.0).toInt()
                val windSkew = (atmo.windStrength * 3.5f).coerceIn(2f, 10f)
                for (d in 0 until drops) {
                    val rx = ((d * 97 + phase * 3) % w.toInt().coerceAtLeast(1)).toFloat()
                    val ry = ((d * 173 + phase * 9) % h.toInt().coerceAtLeast(1)).toFloat()
                    drawLine(
                        color = Color(0xFFBBDEFB).copy(alpha = 0.44f),
                        start = Offset(rx, ry),
                        end = Offset(rx - windSkew, ry + 22f),
                        strokeWidth = 1.6f,
                    )
                }
            }
            if (atmo.lightningFlash > 0.25f) {
                drawRect(color = Color(0xFFE3F2FD).copy(alpha = ((atmo.lightningFlash - 0.25f) * 0.26f).coerceIn(0f, 0.22f)))
            }
            val fogVeilAlpha = ((atmo.fogDensity - 0.0045f) / 0.0055f).coerceIn(0f, 1f) * 0.22f
            if (fogVeilAlpha > 0.01f) {
                drawRect(color = Color(atmo.fogColorR, atmo.fogColorG, atmo.fogColorB, fogVeilAlpha))
            }
            } catch (t: Throwable) {
                DiagnosticEngine.recordError("GameplayViewport", "CanvasDraw", t)
            } finally {
                DiagnosticEngine.endFrame(frameStartNs, worldState)
            }
        }
    }
}

@Composable
fun MinimapRadar(
    worldState: WorldState,
    modifier: Modifier = Modifier,
) {
    val gridSide = worldState.mapGridSize ?: 128
    val worldSpanM = getWorldSpanM(worldState).toFloat()
    val halfM = worldSpanM / 2f

    Box(
        modifier = modifier
            .size(104.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0E1310).copy(alpha = 0.88f))
            .border(1.5.dp, Color(0xFF52B788).copy(alpha = 0.65f), RoundedCornerShape(14.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun mx(wx: Double): Float = (((wx.toFloat() + halfM) / worldSpanM) * w).coerceIn(2f, w - 2f)
            fun mz(wz: Double): Float = (((wz.toFloat() + halfM) / worldSpanM) * h).coerceIn(2f, h - 2f)

            val step = max(2, gridSide / 32)
            val cw = (step.toFloat() / gridSide) * w + 1f
            for (gz in 0 until gridSide step step) {
                for (gx in 0 until gridSide step step) {
                    val idx = gz * gridSide + gx
                    if (idx >= worldState.cells.size) continue
                    val cell = worldState.cells[idx]
                    val c = when (cell.soilState) {
                        SoilState.AGUA_RIO, SoilState.ACUDE_AGUA -> Color(0xFF217696)
                        SoilState.ESCAVADO, SoilState.ATERRO_SOLO -> Color(0xFF8B5A3C)
                        SoilState.TALUDE_BARRAGEM -> Color(0xFFA08666)
                        SoilState.ESTRADA_ASFALTO, SoilState.ESTRADA_TERRA -> Color(0xFF6B655B)
                        SoilState.PRONTO_COLHEITA -> Color(0xFFE9A825)
                        SoilState.CRESCENDO, SoilState.SEMEADO -> Color(0xFF4CAF50)
                        SoilState.ARADO, SoilState.SUBSOLADO -> Color(0xFF7F3E28)
                        SoilState.AREA_PRESERVACAO, SoilState.FLORESTA_NATIVA -> Color(0xFF153F1F)
                        else -> Color(0xFF244329)
                    }
                    drawRect(
                        color = c,
                        topLeft = Offset((gx.toFloat() / gridSide) * w, (gz.toFloat() / gridSide) * h),
                        size = Size(cw, cw),
                    )
                }
            }

            // Constructed ponds & water prospect points
            for (pond in worldState.ponds.orEmpty()) {
                val prPx = ((pond.radiusM.toFloat() / worldSpanM) * w).coerceIn(3f, 16f)
                drawCircle(Color(0xFF00B4D8).copy(alpha = 0.85f), radius = prPx, center = Offset(mx(pond.x), mz(pond.z)))
            }
            for (pt in worldState.waterProspectPoints.orEmpty()) {
                drawCircle(Color(0xFF22D3EE), radius = 2.4f, center = Offset(mx(pt.x), mz(pt.z)))
            }

            // Placed Center Pivots (circular coverage + active rotating boom)
            for (piv in worldState.centerPivots.orEmpty()) {
                val px = mx(piv.centerX)
                val pz = mz(piv.centerZ)
                val prPx = ((piv.radiusM.toFloat() / worldSpanM) * w).coerceIn(3f, 24f)
                val pivCol = if (piv.isActive && piv.waterReserveL > 0.0) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                drawCircle(
                    color = pivCol.copy(alpha = 0.22f),
                    radius = prPx,
                    center = Offset(px, pz),
                )
                drawCircle(
                    color = pivCol,
                    radius = prPx,
                    center = Offset(px, pz),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f),
                )
                val rad = piv.angleDeg.toFloat() * (PI / 180.0).toFloat()
                drawLine(
                    color = Color.White,
                    start = Offset(px, pz),
                    end = Offset(px + sin(rad) * prPx, pz + cos(rad) * prPx),
                    strokeWidth = 1.4f,
                )
            }

            val lmScaleGrid = if (worldState.worldSpanM != null && (worldState.worldSpanM ?: 0.0) > 0.0) 128 else gridSide
            for (lm in LANDMARKS) {
                drawCircle(
                    color = Color(0xFF52B788),
                    radius = 2.6f,
                    center = Offset(mx(lm.scaledWorldX(lmScaleGrid)), mz(lm.scaledWorldZ(lmScaleGrid))),
                )
            }
            for (poi in worldState.secondaryPois.orEmpty()) {
                drawCircle(
                    color = if (poi.isSellPoint) Color(0xFF38BDF8) else Color(0xFFA3E635),
                    radius = 2.6f,
                    center = Offset(mx(poi.x), mz(poi.z)),
                )
            }
            for (est in findAllSellStationsInWorld(worldState)) {
                drawCircle(
                    color = Color(0xFF29B6F6),
                    radius = 3.0f,
                    center = Offset(mx(est.worldX), mz(est.worldZ)),
                )
            }
            for (v in worldState.vehicles) {
                val isAct = v.uid == worldState.activeVehicleUid
                drawCircle(
                    color = if (isAct) Color(0xFFFFD54F) else Color.White,
                    radius = if (isAct) 4.2f else 2.5f,
                    center = Offset(mx(v.x), mz(v.z)),
                )
            }
            if (worldState.activeVehicleUid == null) {
                drawCircle(
                    color = Color(0xFF52B788),
                    radius = 4.2f,
                    center = Offset(mx(worldState.player.x), mz(worldState.player.z)),
                )
            }
        }
    }
}
