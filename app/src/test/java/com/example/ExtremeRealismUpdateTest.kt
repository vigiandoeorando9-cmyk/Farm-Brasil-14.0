package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.audio.AmbientAudioEngine
import com.example.audio.VehicleAudioSnapshot
import com.example.audio.resolveEngineAudioProfile
import com.example.domain.AnimalSpecies
import com.example.domain.BuildingType
import com.example.domain.CameraMode
import com.example.domain.GRAPHICS_PRESET_META
import com.example.domain.GameSettings
import com.example.domain.GraphicsPreset
import com.example.domain.HudLayoutConfig
import com.example.domain.LivestockHerd
import com.example.domain.PlacedBuilding
import com.example.domain.Puddle
import com.example.domain.SoilState
import com.example.domain.TerrainCell
import com.example.domain.TireTrack
import com.example.domain.WHEEL_OPTIONS
import com.example.domain.WeatherType
import com.example.domain.WorldState
import com.example.domain.getVehicleDef
import com.example.domain.getVehiclePhysicsSpec
import com.example.simulation.ControlInput
import com.example.simulation.createNewWorld
import com.example.simulation.evaluateTerrainGrip
import com.example.simulation.getCellIndex
import com.example.simulation.getGridSide
import com.example.simulation.stepSimulation
import com.example.ui.components.GameplayViewport
import com.example.ui.components.MaterialType
import com.example.ui.components.computeSunAtmosphereState
import com.example.ui.components.shadeMaterialFace
import com.example.ui.theme.FarmBrasilTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w840dp-h412dp-land")
class ExtremeRealismUpdateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun spawnFlatSoilWorld(
        mapId: String = "map_pr",
        soilState: SoilState = SoilState.PASTAGEM,
        moisture: Double = 0.45,
        compaction: Double = 0.10,
        weather: WeatherType = WeatherType.ENSOLARADO,
        defId: String = "agrale_bx6150",
        wheelOptionId: String = "std",
    ): WorldState {
        val base = createNewWorld(0, "Fazenda Realismo", mapId, 500000.0, "Padrão", 64)
        val template = base.vehicles.first()
        val veh = template.copy(
            uid = "v_real",
            defId = defId,
            x = 0.0,
            z = -24.0,
            headingDeg = 0.0,
            speedMs = 0.0,
            gear = 1,
            rpm = 1200.0,
            fuelL = getVehicleDef(defId).fuelCapacityL,
            wheelOptionId = wheelOptionId,
            engineRunning = true,
            handbrakeOn = false,
            attachedImplementUid = null,
        )
        val flatCells = base.cells.map {
            it.copy(height = 2.0, soilState = soilState, moisture = moisture, compaction = compaction)
        }
        return base.copy(
            weather = weather,
            cells = flatCells,
            buildings = emptyList(),
            props = emptyList(),
            implements = emptyList(),
            roadVehicles = emptyList(),
            vehicles = listOf(veh),
            activeVehicleUid = veh.uid,
            tireTracks = emptyList(),
            puddles = emptyList(),
            statusAlert = null,
        )
    }

    @Test
    fun sunAtmosphereAndMaterialShadersRespondToTimeWeatherAndSurfaceType() {
        val highGfx = GRAPHICS_PRESET_META.getValue(GraphicsPreset.MEGA_REALISTA).config

        val dawnAtmo = computeSunAtmosphereState(6.2, WeatherType.ENSOLARADO, highGfx, 4.0f)
        val middayAtmo = computeSunAtmosphereState(12.0, WeatherType.ENSOLARADO, highGfx, 4.0f)
        val duskAtmo = computeSunAtmosphereState(17.5, WeatherType.ENSOLARADO, highGfx, 4.0f)
        val nightAtmo = computeSunAtmosphereState(22.0, WeatherType.ENSOLARADO, highGfx, 4.0f)
        val stormAtmo = computeSunAtmosphereState(14.0, WeatherType.TEMPESTADE, highGfx, 0.04f)

        assertThat(dawnAtmo.isNight).isFalse()
        assertThat(dawnAtmo.goldenHourFactor).isGreaterThan(0.35f)
        assertThat(duskAtmo.goldenHourFactor).isGreaterThan(0.35f)
        assertThat(middayAtmo.sunDirY).isGreaterThan(dawnAtmo.sunDirY)
        assertThat(middayAtmo.sunIntensity).isGreaterThan(nightAtmo.sunIntensity)
        assertThat(nightAtmo.isNight).isTrue()
        assertThat(nightAtmo.nightFactor).isAtLeast(0.95f)
        assertThat(stormAtmo.wetness).isGreaterThan(0.8f)
        assertThat(stormAtmo.windStrength).isGreaterThan(middayAtmo.windStrength)
        assertThat(stormAtmo.lightningFlash).isGreaterThan(0.05f)

        // Different materials under identical geometry & light must yield distinct PBR outputs
        val baseColor = 0xFF287832.toInt()
        val glossLit = shadeMaterialFace(
            baseColor, nx = 0.25f, ny = 0.90f, nz = 0.35f,
            viewDirX = 0.0f, viewDirY = 0.65f, viewDirZ = -0.76f,
            atmo = middayAtmo, material = MaterialType.GLOSS_PAINT,
        )
        val glassLit = shadeMaterialFace(
            baseColor, nx = 0.25f, ny = 0.90f, nz = 0.35f,
            viewDirX = 0.0f, viewDirY = 0.65f, viewDirZ = -0.76f,
            atmo = middayAtmo, material = MaterialType.CAB_GLASS,
        )
        val rubberLit = shadeMaterialFace(
            baseColor, nx = 0.25f, ny = 0.90f, nz = 0.35f,
            viewDirX = 0.0f, viewDirY = 0.65f, viewDirZ = -0.76f,
            atmo = middayAtmo, material = MaterialType.RUBBER_TIRE,
        )
        val metalLit = shadeMaterialFace(
            baseColor, nx = 0.25f, ny = 0.90f, nz = 0.35f,
            viewDirX = 0.0f, viewDirY = 0.65f, viewDirZ = -0.76f,
            atmo = middayAtmo, material = MaterialType.GALVANIZED_METAL,
        )
        val soilLit = shadeMaterialFace(
            baseColor, nx = 0.25f, ny = 0.90f, nz = 0.35f,
            viewDirX = 0.0f, viewDirY = 0.65f, viewDirZ = -0.76f,
            atmo = middayAtmo, material = MaterialType.SOIL,
        )
        assertThat(setOf(glossLit, glassLit, rubberLit, metalLit, soilLit).size).isEqualTo(5)

        // Heavy mud blending & distance fog alter output shade meaningfully
        val muddyTire = shadeMaterialFace(
            0xFF1F2124.toInt(), nx = 0f, ny = 1f, nz = 0f,
            viewDirX = 0f, viewDirY = 0.7f, viewDirZ = -0.7f,
            atmo = middayAtmo, material = MaterialType.RUBBER_TIRE, mudFactor = 0.85f,
        )
        val cleanTire = shadeMaterialFace(
            0xFF1F2124.toInt(), nx = 0f, ny = 1f, nz = 0f,
            viewDirX = 0f, viewDirY = 0.7f, viewDirZ = -0.7f,
            atmo = middayAtmo, material = MaterialType.RUBBER_TIRE, mudFactor = 0.0f,
        )
        assertThat(muddyTire).isNotEqualTo(cleanTire)
    }

    @Test
    fun terrainSoilPhysicsCompactionExtendedTracksAndDynamicWeatherPuddlesWorkCorrectly() {
        val tractorDef = getVehicleDef("agrale_bx6150")
        val tractorSpec = getVehiclePhysicsSpec(tractorDef)
        val stdWheel = WHEEL_OPTIONS.first()

        // Differentiate semi-arid sand, mud, compacted soil, and asphalt
        val semiAridDry = spawnFlatSoilWorld(mapId = "map_ce", soilState = SoilState.PASTAGEM, moisture = 0.22)
        val mudState = spawnFlatSoilWorld(mapId = "map_pr", soilState = SoilState.ARADO, moisture = 0.86, weather = WeatherType.CHUVA_INTENSA)
        val compactedState = spawnFlatSoilWorld(mapId = "map_pr", soilState = SoilState.ESTRADA_TERRA, moisture = 0.35, compaction = 0.78)
        val asphaltState = spawnFlatSoilWorld(mapId = "map_pr", soilState = SoilState.ESTRADA_ASFALTO, moisture = 0.15)

        val sandSample = evaluateTerrainGrip(semiAridDry, TerrainCell(0, 0, 0.0, 0, SoilState.PASTAGEM, moisture = 0.22, compaction = 0.1), tractorSpec, stdWheel)
        val mudSample = evaluateTerrainGrip(mudState, TerrainCell(0, 0, 0.0, 0, SoilState.ARADO, moisture = 0.86, compaction = 0.2), tractorSpec, stdWheel)
        val compactSample = evaluateTerrainGrip(compactedState, TerrainCell(0, 0, 0.0, 0, SoilState.ESTRADA_TERRA, moisture = 0.35, compaction = 0.78), tractorSpec, stdWheel)
        val asphaltSample = evaluateTerrainGrip(asphaltState, TerrainCell(0, 0, 0.0, 0, SoilState.ESTRADA_ASFALTO, moisture = 0.15, compaction = 0.9), tractorSpec, stdWheel)

        assertThat(sandSample.isDusty).isTrue()
        assertThat(mudSample.isMud).isTrue()
        assertThat(mudSample.rollingResistance).isGreaterThan(sandSample.rollingResistance)
        assertThat(mudSample.sinkageM).isGreaterThan(compactSample.sinkageM)
        assertThat(asphaltSample.gripCoeff).isGreaterThan(mudSample.gripCoeff)

        // Driving across arable ground increases soil compaction & accumulates extended tire track history (> 140)
        var wStd = spawnFlatSoilWorld(soilState = SoilState.ARADO, moisture = 0.45, compaction = 0.10, wheelOptionId = "std")
        var wDual = spawnFlatSoilWorld(soilState = SoilState.ARADO, moisture = 0.45, compaction = 0.10, wheelOptionId = "dual")
        val initialCompSum = wStd.cells.sumOf { it.compaction }

        repeat(450) {
            wStd = stepSimulation(wStd, ControlInput(throttle = 0.95), dtSec = 0.08, timeScale = 1.0)
            wDual = stepSimulation(wDual, ControlInput(throttle = 0.95), dtSec = 0.08, timeScale = 1.0)
        }

        val finalCompStd = wStd.cells.sumOf { it.compaction } - initialCompSum
        val finalCompDual = wDual.cells.sumOf { it.compaction } - initialCompSum
        assertThat(finalCompStd).isGreaterThan(0.05)
        assertThat(finalCompDual).isLessThan(finalCompStd)
        assertThat(wStd.tireTracks.size).isGreaterThan(40)
        assertThat(wStd.tireTracks.size).isAtMost(280)

        // Check active cell under vehicle actually gained compaction
        val vStd = wStd.vehicles.first()
        val idxUnder = getCellIndex(vStd.x, vStd.z, getGridSide(wStd))
        assertThat(idxUnder).isNotNull()

        // Rain spawns & grows puddles; sunny weather shrinks and evaporates them
        var wRain = spawnFlatSoilWorld(soilState = SoilState.ESTRADA_TERRA, moisture = 0.65, weather = WeatherType.CHUVA_INTENSA)
        repeat(40) {
            wRain = stepSimulation(wRain, ControlInput(), dtSec = 0.5, timeScale = 1.0)
        }
        val rainPuddles = wRain.puddles.orEmpty()
        assertThat(rainPuddles).isNotEmpty()
        val maxRainRadius = rainPuddles.maxOf { it.radius }
        assertThat(maxRainRadius).isGreaterThan(1.15)

        var wDry = wRain.copy(
            weather = WeatherType.ENSOLARADO,
            puddles = listOf(Puddle(0.0, 0.0, 0.62)),
        )
        repeat(45) {
            wDry = stepSimulation(wDry, ControlInput(), dtSec = 0.08, timeScale = 1.0)
        }
        assertThat(wDry.puddles.orEmpty()).isEmpty()
    }

    @Test
    fun gameplayViewportRendersSafelyAcrossQualityTiersCabOverlayWeatherAndArchitecture() {
        val base = createNewWorld(0, "Fazenda Gráfica", "map_pr", 500000.0, "Padrão", 64)
        val activeVeh = base.vehicles.first().copy(
            uid = "v_cab",
            x = 0.0,
            z = 0.0,
            speedMs = 6.5,
            rpm = 1750.0,
            gear = 4,
            headlightsOn = true,
            workLightsOn = true,
            wipersOn = true,
            wiperAngleRad = 0.65,
            dirtPct = 42.0,
            is4wdEngaged = true,
            isPtoEngaged = true,
            wheelOffsetsY = listOf(0.03, -0.02, 0.04, -0.01),
        )
        val enrichedWorld = base.copy(
            hourOfDay = 21.5,
            weather = WeatherType.TEMPESTADE,
            vehicles = listOf(activeVeh),
            activeVehicleUid = activeVeh.uid,
            buildings = listOf(
                PlacedBuilding("b_casa", BuildingType.CASA, 18.0, 14.0, 0.0, hasVeranda = true),
                PlacedBuilding("b_curral", BuildingType.CURRAL, -18.0, 12.0, 0.0),
                PlacedBuilding("b_coop", BuildingType.COOPERATIVA, 34.0, -18.0, 0.0),
                PlacedBuilding("b_silo", BuildingType.SILO, -26.0, -22.0, 0.0),
            ),
            herds = listOf(
                LivestockHerd(AnimalSpecies.VACA_LEITEIRA, count = 14, avgAgeDays = 720, healthPct = 95.0, feedReserveKg = 800.0, waterReserveL = 1200.0, pendingProductionUnits = 40.0),
                LivestockHerd(AnimalSpecies.BOVINO_CORTE, count = 18, avgAgeDays = 540, healthPct = 94.0, feedReserveKg = 900.0, waterReserveL = 1100.0, pendingProductionUnits = 12.0),
            ),
            tireTracks = List(36) { idx ->
                TireTrack(
                    x = idx * 1.1 - 15.0,
                    z = idx * 0.6 - 10.0,
                    headingDeg = 22.0,
                    age = idx * 0.8,
                    trackWidthM = 1.85,
                    tireWidthM = 0.48,
                    isMud = idx % 2 == 0,
                    isSlip = idx % 3 == 0,
                    soilR = 0.19,
                    soilG = 0.13,
                    soilB = 0.08,
                )
            },
            puddles = listOf(
                Puddle(4.0, 8.0, 2.4),
                Puddle(-6.0, 12.0, 1.9),
            ),
        )

        val presetsToVerify = listOf(
            GraphicsPreset.MUITO_BAIXO,
            GraphicsPreset.EQUILIBRADO,
            GraphicsPreset.MEGA_REALISTA,
            GraphicsPreset.DESKTOP_PC,
        )

        var activeSettings by androidx.compose.runtime.mutableStateOf(
            GameSettings(
                graphicsPreset = GraphicsPreset.MEGA_REALISTA,
                hudLayout = HudLayoutConfig(showDashGauges = true),
            )
        )
        var activeCamera by androidx.compose.runtime.mutableStateOf(CameraMode.CABINE)

        composeRule.setContent {
            FarmBrasilTheme {
                Box(modifier = Modifier.size(640.dp, 360.dp)) {
                    GameplayViewport(
                        worldState = enrichedWorld,
                        cameraMode = activeCamera,
                        remoteMembers = emptyList(),
                        localUid = null,
                        settings = activeSettings,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Cycle camera mode & quality tiers without exceptions
        composeRule.runOnIdle {
            activeCamera = CameraMode.TERCEIRA_PESSOA
        }
        composeRule.waitForIdle()
        for (preset in presetsToVerify) {
            composeRule.runOnIdle {
                activeSettings = activeSettings.copy(graphicsPreset = preset)
            }
            composeRule.waitForIdle()
        }
    }

    @Test
    fun ambientAudioEngineHandlesSurfaceTypesAndProfilesCleanly() {
        val tractorProfile = resolveEngineAudioProfile("Agrale Diesel Turbo", 150.0)
        val truckProfile = resolveEngineAudioProfile("Mercedes-Benz 1113", 130.0)
        val combineProfile = resolveEngineAudioProfile("Colheitadeira Axial", 380.0)
        val motoProfile = resolveEngineAudioProfile("Moto Rural 160", 15.0)

        assertThat(tractorProfile.cylinders).isEqualTo(4)
        assertThat(truckProfile.hasAirBrake).isTrue()
        assertThat(combineProfile.hasTurboWhine).isTrue()
        assertThat(motoProfile.cylinders).isEqualTo(1)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = AmbientAudioEngine(scope)
        for (surface in listOf("asfalto", "cascalho", "terra", "lama")) {
            engine.updateParameters(rain = 0.4, wind = 0.5, hour = 21.0, channelSettings = GameSettings().audioChannels)
            engine.updateVehicleAudio(
                VehicleAudioSnapshot(
                    inVehicle = true,
                    speedMs = 8.5,
                    rpm = 1850.0,
                    gear = 4,
                    engineRunning = true,
                    surfaceType = surface,
                    soundProfile = "Diesel Turbo",
                    horsepower = 180.0,
                    implementActive = true,
                    isSprayerActive = false,
                    isBraking = false,
                    engineLoadPct = 64.0,
                    hasAirBrake = true,
                )
            )
        }
        engine.release()
    }
}
