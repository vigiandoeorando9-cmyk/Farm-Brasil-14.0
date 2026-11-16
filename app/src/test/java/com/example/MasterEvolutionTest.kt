package com.example

import com.example.domain.ESTABLISHMENTS
import com.example.domain.GameSettings
import com.example.domain.NpcAgentState
import com.example.domain.RoadVehicle
import com.example.domain.SoilState
import com.example.domain.WEATHER_META
import com.example.domain.WeatherType
import com.example.simulation.computeWeighbridgeUnitPriceBrl
import com.example.simulation.createNewWorld
import com.example.simulation.getCellIndex
import com.example.simulation.getGridSide
import com.example.simulation.sellProductAtWeighbridge
import com.example.simulation.stepNpcAgentsSimulation
import com.example.simulation.stepRoadTrafficVehicles
import com.example.state.MP_SYNC_PREFIX
import com.example.state.computeMpReconnectBackoffMs
import com.example.state.parseSharedSyncEvent
import com.example.ui.components.computeBlendedSunAtmosphereState
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MasterEvolutionTest {

    @Test
    fun blendedAtmosphere_interpolatesRainCloudsFogAndWetnessSmoothly() {
        val gfx = GameSettings().customGraphics
        val at0 = computeBlendedSunAtmosphereState(
            hourOfDay = 12.0,
            fromWeather = WeatherType.ENSOLARADO,
            toWeather = WeatherType.TEMPESTADE,
            blendT = 0.0f,
            gfx = gfx,
            animClockSec = 0.5f,
        )
        val atMid = computeBlendedSunAtmosphereState(
            hourOfDay = 12.0,
            fromWeather = WeatherType.ENSOLARADO,
            toWeather = WeatherType.TEMPESTADE,
            blendT = 0.5f,
            gfx = gfx,
            animClockSec = 0.5f,
        )
        val at1 = computeBlendedSunAtmosphereState(
            hourOfDay = 12.0,
            fromWeather = WeatherType.ENSOLARADO,
            toWeather = WeatherType.TEMPESTADE,
            blendT = 1.0f,
            gfx = gfx,
            animClockSec = 0.5f,
        )

        assertThat(at0.rainIntensity).isWithin(0.01f).of(WEATHER_META.getValue(WeatherType.ENSOLARADO).rainIntensity.toFloat())
        assertThat(at1.rainIntensity).isWithin(0.01f).of(WEATHER_META.getValue(WeatherType.TEMPESTADE).rainIntensity.toFloat())
        assertThat(atMid.rainIntensity).isGreaterThan(at0.rainIntensity)
        assertThat(atMid.rainIntensity).isLessThan(at1.rainIntensity)

        assertThat(atMid.cloudCover).isGreaterThan(at0.cloudCover)
        assertThat(atMid.cloudCover).isLessThan(at1.cloudCover)

        assertThat(atMid.fogDensity).isGreaterThan(at0.fogDensity)
        assertThat(atMid.fogDensity).isLessThan(at1.fogDensity)

        assertThat(atMid.wetness).isGreaterThan(at0.wetness)
        assertThat(atMid.wetness).isLessThan(at1.wetness)
    }

    @Test
    fun roadTraffic_brakesForPlayerVehiclesOtherTrafficSlowsInStormAndTurnsAtBoundary() {
        val base = createNewWorld(0, "Fazenda Tráfego", "map_pr", 250000.0, "Padrão", 64)
        val rvAheadBase = RoadVehicle(
            uid = "rv_test_1",
            x = 0.0,
            z = 0.0,
            headingDeg = 0.0, // moving toward +Z
            speedMs = 11.0,
            colorHex = "#E53935",
            kind = "truck",
        )

        // 1) Stops (zero displacement) when on-foot player stands 6m ahead in lane
        val stateWithPlayerAhead = base.copy(
            activeVehicleUid = null,
            player = base.player.copy(x = 0.2, z = 6.0),
            vehicles = emptyList(),
        )
        val stoppedForPlayer = stepRoadTrafficVehicles(listOf(rvAheadBase), stateWithPlayerAhead, worldHalf = 300.0, dt = 0.05)!!.first()
        assertThat(stoppedForPlayer.x).isEqualTo(rvAheadBase.x)
        assertThat(stoppedForPlayer.z).isEqualTo(rvAheadBase.z)

        // 2) Stops (zero displacement) when tractor is ahead in lane
        val tractor = base.vehicles.first().copy(x = -0.3, z = 8.0)
        val stateWithVehAhead = base.copy(
            activeVehicleUid = tractor.uid,
            vehicles = listOf(tractor),
        )
        val stoppedForTractor = stepRoadTrafficVehicles(listOf(rvAheadBase), stateWithVehAhead, worldHalf = 300.0, dt = 0.05)!!.first()
        assertThat(stoppedForTractor.x).isEqualTo(rvAheadBase.x)
        assertThat(stoppedForTractor.z).isEqualTo(rvAheadBase.z)

        // 3) Stops when another road vehicle is ahead in the same lane while lead advances
        val leadRv = rvAheadBase.copy(uid = "rv_lead", x = 0.0, z = 7.5)
        val clearWorld = base.copy(
            activeVehicleUid = null,
            player = base.player.copy(x = 120.0, z = 120.0),
            vehicles = emptyList(),
        )
        val twoVehiclesStepped = stepRoadTrafficVehicles(listOf(rvAheadBase, leadRv), clearWorld, worldHalf = 300.0, dt = 0.05)!!
        val trailing = twoVehiclesStepped.first { it.uid == "rv_test_1" }
        val leading = twoVehiclesStepped.first { it.uid == "rv_lead" }
        assertThat(trailing.z).isEqualTo(rvAheadBase.z)
        assertThat(leading.z).isGreaterThan(leadRv.z)

        // 4) Slows down in TEMPESTADE vs ENSOLARADO
        val sunnyDist = stepRoadTrafficVehicles(listOf(rvAheadBase), clearWorld.copy(weather = WeatherType.ENSOLARADO), worldHalf = 300.0, dt = 0.1)!!.first().z - rvAheadBase.z
        val stormDist = stepRoadTrafficVehicles(listOf(rvAheadBase), clearWorld.copy(weather = WeatherType.TEMPESTADE), worldHalf = 300.0, dt = 0.1)!!.first().z - rvAheadBase.z
        assertThat(stormDist).isLessThan(sunnyDist)

        // 5) Reverses heading cleanly near map bounds
        val edgeRv = rvAheadBase.copy(x = 0.0, z = 289.5, headingDeg = 0.0)
        val turned = stepRoadTrafficVehicles(listOf(edgeRv), clearWorld, worldHalf = 300.0, dt = 0.1)!!.first()
        assertThat(turned.headingDeg).isWithin(0.01).of(180.0)
    }

    @Test
    fun npcSimulation_adaptsToMudAsphaltNightAndRiver() {
        val base = createNewWorld(0, "Fazenda NPCs", "map_pr", 250000.0, "Padrão", 64)
        val gridSide = getGridSide(base)
        val cells = base.cells.toMutableList()

        // Mark (0, 0) cell as muddy unpaved road, (20, 0) cell as asphalt, (0, 12) as river
        val mudIdx = getCellIndex(0.0, 0.0, gridSide)!!
        cells[mudIdx] = cells[mudIdx].copy(soilState = SoilState.ESTRADA_TERRA, moisture = 0.92)
        val asphaltIdx = getCellIndex(20.0, 0.0, gridSide)!!
        cells[asphaltIdx] = cells[asphaltIdx].copy(soilState = SoilState.ESTRADA_ASFALTO, moisture = 0.1)
        val riverIdx = getCellIndex(0.0, 4.0, gridSide)!!
        cells[riverIdx] = cells[riverIdx].copy(soilState = SoilState.AGUA_RIO)

        val mudNpc = NpcAgentState(
            uid = "npc_mud",
            name = "Teste Lama",
            role = "trabalhador_rural",
            x = 0.0,
            z = 0.0,
            headingDeg = 90.0,
            speedMs = 0.0,
            gaitPhase = 0.0,
            targetX = 40.0,
            targetZ = 0.0,
            homeX = 0.0,
            homeZ = 0.0,
            activity = "Patrulha",
            surfaceType = "lama",
            dialogLine = "Olá",
            skinHex = "#D2A07A",
            shirtHex = "#3A6EA5",
            pantsHex = "#30343B",
            state = "walking",
            pauseTimerSec = 0.0,
        )
        val asphaltNpc = mudNpc.copy(
            uid = "npc_asphalt",
            x = 20.0,
            z = 0.0,
            targetX = 60.0,
            targetZ = 0.0,
            homeX = 20.0,
            homeZ = 0.0,
            surfaceType = "asfalto",
        )
        val riverNpc = mudNpc.copy(
            uid = "npc_river",
            x = 0.0,
            z = 3.96,
            targetX = 0.0,
            targetZ = 20.0,
            homeX = 0.0,
            homeZ = -10.0,
            headingDeg = 0.0,
        )

        val dayState = base.copy(
            hourOfDay = 10.0,
            weather = WeatherType.ENSOLARADO,
            cells = cells,
            npcAgents = listOf(mudNpc, asphaltNpc, riverNpc),
            vehicles = emptyList(),
            buildings = emptyList(),
            props = emptyList(),
        )
        val dayStepped = stepNpcAgentsSimulation(dayState, 0.05)
        val resMud = dayStepped.first { it.uid == "npc_mud" }
        val resAsphalt = dayStepped.first { it.uid == "npc_asphalt" }
        val resRiver = dayStepped.first { it.uid == "npc_river" }

        assertThat(resMud.speedMs).isGreaterThan(0.0)
        assertThat(resMud.speedMs).isLessThan(resAsphalt.speedMs)
        assertThat(resRiver.z).isLessThan(riverNpc.z)
        assertThat(cells[getCellIndex(resRiver.x, resRiver.z, gridSide)!!].soilState).isNotEqualTo(SoilState.AGUA_RIO)

        // At night (22h), NPC near home rests idle
        val nightHomeNpc = mudNpc.copy(uid = "npc_night", x = 0.2, z = 0.2, homeX = 0.0, homeZ = 0.0)
        val nightStepped = stepNpcAgentsSimulation(
            dayState.copy(hourOfDay = 22.0, npcAgents = listOf(nightHomeNpc)),
            0.05,
        )
        assertThat(nightStepped.first().speedMs).isEqualTo(0.0)
        assertThat(nightStepped.first().state).isEqualTo("idle")
    }

    @Test
    fun weighbridgePartialSales_computePriceLeaveAccurateStockAndRecordTicket() {
        val est = ESTABLISHMENTS.first()
        val initialStockKg = 10_000.0
        val base = createNewWorld(0, "Fazenda Economia", "map_pr", 10_000.0, "Padrão", 64).let {
            it.copy(
                inventoryKg = it.inventoryKg + ("soja" to initialStockKg),
                marketMultiplier = it.marketMultiplier + ("soja" to 1.10),
            )
        }

        val unitPrice = computeWeighbridgeUnitPriceBrl(base, est, "soja")
        assertThat(unitPrice).isGreaterThan(0.0)

        // Sell 25% (2,500 kg)
        val after25 = sellProductAtWeighbridge(base, est.id, "soja", initialStockKg * 0.25)
        assertThat(after25.inventoryKg["soja"]).isWithin(0.01).of(7_500.0)
        assertThat(after25.moneyBrl).isGreaterThan(base.moneyBrl)
        assertThat(after25.lastWeighbridgeTicket).isNotNull()
        assertThat(after25.lastWeighbridgeTicket?.netKg).isWithin(0.01).of(2_500.0)
        assertThat(after25.ledger.last().amountBrl).isWithin(0.01).of(after25.moneyBrl - base.moneyBrl)

        // Sell 50% of remaining stock (3,750 kg)
        val remAfter25 = after25.inventoryKg["soja"]!!
        val after50 = sellProductAtWeighbridge(after25, est.id, "soja", remAfter25 * 0.50)
        assertThat(after50.inventoryKg["soja"]).isWithin(0.01).of(3_750.0)
        assertThat(after50.moneyBrl).isGreaterThan(after25.moneyBrl)
        assertThat(after50.lastWeighbridgeTicket?.netKg).isWithin(0.01).of(3_750.0)
    }

    @Test
    fun multiplayerHelpers_backoffAndSyncEventParsingAreDeterministic() {
        assertThat(computeMpReconnectBackoffMs(0)).isEqualTo(1_500L)
        assertThat(computeMpReconnectBackoffMs(1)).isEqualTo(3_000L)
        assertThat(computeMpReconnectBackoffMs(2)).isEqualTo(6_000L)
        assertThat(computeMpReconnectBackoffMs(3)).isEqualTo(12_000L)
        assertThat(computeMpReconnectBackoffMs(4)).isEqualTo(24_000L)
        assertThat(computeMpReconnectBackoffMs(10)).isEqualTo(24_000L)
        assertThat(computeMpReconnectBackoffMs(-2)).isEqualTo(1_500L)

        val parsedWeather = parseSharedSyncEvent("${MP_SYNC_PREFIX}WEATHER:CHUVA")
        assertThat(parsedWeather).isEqualTo("WEATHER" to "CHUVA")

        assertThat(parseSharedSyncEvent("Olá pessoal!")).isNull()
        assertThat(parseSharedSyncEvent("${MP_SYNC_PREFIX}INVALID")).isNull()
        assertThat(parseSharedSyncEvent("${MP_SYNC_PREFIX}:CHUVA")).isNull()
        assertThat(parseSharedSyncEvent("${MP_SYNC_PREFIX}WEATHER:")).isNull()
    }
}
