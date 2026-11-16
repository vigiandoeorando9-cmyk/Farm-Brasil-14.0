package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.domain.ALL_CROPS
import com.example.domain.ALL_IMPLEMENTS
import com.example.domain.ALL_VEHICLES
import com.example.domain.FieldInfoPanelConfig
import com.example.domain.FieldPanelCorner
import com.example.domain.FieldPanelSize
import com.example.domain.FieldPanelViewMode
import com.example.domain.GameSettings
import com.example.domain.HitchMode
import com.example.domain.HitchType
import com.example.domain.ImplementCategory
import com.example.domain.OwnedImplement
import com.example.domain.PivotSizeTier
import com.example.domain.PlacedCenterPivot
import com.example.domain.SoilState
import com.example.domain.VehicleCategory
import com.example.domain.getImplementDef
import com.example.domain.getVehicleDef
import com.example.domain.resolveImplementCouplingSpec
import com.example.simulation.ControlInput
import com.example.simulation.CropCalendarStatus
import com.example.simulation.FieldConditionState
import com.example.simulation.FieldGrowthStage
import com.example.simulation.FieldOwnerKind
import com.example.simulation.HitchBlockReason
import com.example.simulation.MultiplayerSyncCodec
import com.example.simulation.analyzeFieldPlot
import com.example.simulation.computeImplementPinWorldPoint
import com.example.simulation.computeVehicleHitchWorldPoint
import com.example.simulation.createNewWorld
import com.example.simulation.detectFieldPlotWithHysteresis
import com.example.simulation.evaluateOnFootHitch
import com.example.simulation.evaluateVehicleHitchPair
import com.example.simulation.evaluateVehicleUnhitch
import com.example.simulation.executeValidatedHitch
import com.example.simulation.executeValidatedUnhitch
import com.example.simulation.getWorldCellSizeM
import com.example.simulation.getWorldSpanM
import com.example.simulation.performManualHitchOrUnhitchInWorld
import com.example.simulation.stepHitchAndInteractionAnimations
import com.example.simulation.stepSimulation
import com.example.storage.loadSettings
import com.example.storage.saveSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HitchAndFieldRealismTest {

    private companion object {
        val testJson = Json { ignoreUnknownKeys = true }
    }

    private fun alignedImplementNearTractor(
        tractorX: Double,
        tractorZ: Double,
        headingDeg: Double,
        impDefId: String,
        uid: String = "imp_test",
        distanceOffsetM: Double = 0.25,
    ): OwnedImplement {
        val impDef = getImplementDef(impDefId)
        val spec = resolveImplementCouplingSpec(impDef)
        val rad = headingDeg * PI / 180.0
        val tractorDummy = createNewWorld(0, "F", "map_pr", 250000.0, "Padrão", 64).vehicles.first()
            .copy(x = tractorX, z = tractorZ, headingDeg = headingDeg)
        val hitchPt = computeVehicleHitchWorldPoint(tractorDummy, spec.defaultHitchType)
        val dir = if (spec.isFrontMount) 1.0 else -1.0
        val centerDistFromPin = spec.tongueOffsetM + distanceOffsetM
        return OwnedImplement(
            uid = uid,
            defId = impDefId,
            x = hitchPt.x + sin(rad) * centerDistFromPin * dir,
            z = hitchPt.z + cos(rad) * centerDistFromPin * dir,
            headingDeg = headingDeg,
            fillL = impDef.capacityL * 0.8,
        )
    }

    @Test
    fun noAutoHitchWhenApproaching_andValidationChecksAllFiveConditions() {
        val base = createNewWorld(0, "Fazenda Teste", "map_pr", 250000.0, "Padrão", 64)
        val tractorDef = ALL_VEHICLES.first { it.category == VehicleCategory.TRATOR && it.horsepower >= 140.0 }
        val planterDef = ALL_IMPLEMENTS.first { it.category == ImplementCategory.PLANTADEIRA && it.requiredHp <= tractorDef.horsepower }

        val tractor = base.vehicles.first().copy(
            uid = "veh_t1",
            defId = tractorDef.id,
            x = 0.0,
            z = 0.0,
            headingDeg = 0.0,
            speedMs = 0.0,
            attachedImplementUid = null,
            engineRunning = true,
        )
        val imp = alignedImplementNearTractor(0.0, 0.0, 0.0, planterDef.id, "imp_p1", distanceOffsetM = 0.3)

        var world = base.copy(
            activeVehicleUid = tractor.uid,
            vehicles = listOf(tractor),
            implements = listOf(imp),
        )

        // Stepping simulation near implement MUST NOT auto-hitch
        repeat(10) {
            world = stepSimulation(world, ControlInput(), dtSec = 0.05)
        }
        assertThat(world.vehicles.first().attachedImplementUid).isNull()
        assertThat(world.implements.first().attachedToVehicleUid).isNull()

        // 1. Valid stopped & aligned pair
        val validEval = evaluateVehicleHitchPair(world, world.vehicles.first(), world.implements.first())
        assertThat(validEval.canHitchNow).isTrue()
        assertThat(validEval.blockReason).isEqualTo(HitchBlockReason.NONE)

        // 2. Vehicle moving check
        val movingEval = evaluateVehicleHitchPair(world, tractor.copy(speedMs = 3.2), imp)
        assertThat(movingEval.canHitchNow).isFalse()
        assertThat(movingEval.blockReason).isEqualTo(HitchBlockReason.VEHICLE_MOVING)

        // 3. Too far check
        val farEval = evaluateVehicleHitchPair(world, tractor, imp.copy(z = imp.z - 8.0))
        assertThat(farEval.canHitchNow).isFalse()
        assertThat(farEval.blockReason).isEqualTo(HitchBlockReason.TOO_FAR)

        // 4. Misaligned check (pin stays near hitch, implement body rotated 120 deg)
        val pin = computeImplementPinWorldPoint(imp)
        val planterSpec = resolveImplementCouplingSpec(planterDef)
        val misRad = 120.0 * PI / 180.0
        val misalignedImp = imp.copy(
            x = pin.x - sin(misRad) * planterSpec.tongueOffsetM,
            z = pin.z - cos(misRad) * planterSpec.tongueOffsetM,
            headingDeg = 120.0,
        )
        val angledEval = evaluateVehicleHitchPair(world, tractor, misalignedImp)
        assertThat(angledEval.canHitchNow).isFalse()
        assertThat(angledEval.blockReason).isEqualTo(HitchBlockReason.MISALIGNED)

        // 5. Incompatible hitch (FIFTH_WHEEL semireboque on standard tractor)
        val fifthWheelImp = ALL_IMPLEMENTS.firstOrNull { it.hitchType == HitchType.FIFTH_WHEEL }
        if (fifthWheelImp != null) {
            val fwOwned = alignedImplementNearTractor(0.0, 0.0, 0.0, fifthWheelImp.id, "imp_fw")
            val incompatEval = evaluateVehicleHitchPair(world, tractor, fwOwned)
            assertThat(incompatEval.canHitchNow).isFalse()
            assertThat(incompatEval.blockReason).isEqualTo(HitchBlockReason.INCOMPATIBLE_HITCH)
        }

        // 6. Vehicle already busy check
        val busyEval = evaluateVehicleHitchPair(world, tractor.copy(attachedImplementUid = "other_imp"), imp)
        assertThat(busyEval.canHitchNow).isFalse()
        assertThat(busyEval.blockReason).isEqualTo(HitchBlockReason.VEHICLE_BUSY)
    }

    @Test
    fun manualHitchAndUnhitch_animatesLocksAndPreservesImplementPoseOnUnhitch() {
        val base = createNewWorld(0, "Fazenda Engate", "map_pr", 250000.0, "Padrão", 64)
        val tractorDef = ALL_VEHICLES.first { it.category == VehicleCategory.TRATOR && it.horsepower >= 140.0 }
        val sprayerDef = ALL_IMPLEMENTS.first { it.category == ImplementCategory.PULVERIZADOR && it.requiredHp <= tractorDef.horsepower }

        val tractor = base.vehicles.first().copy(
            uid = "veh_h1",
            defId = tractorDef.id,
            x = 24.0,
            z = -18.0,
            headingDeg = 35.0,
            speedMs = 0.0,
            attachedImplementUid = null,
            engineRunning = true,
        )
        val imp = alignedImplementNearTractor(tractor.x, tractor.z, tractor.headingDeg, sprayerDef.id, "imp_s1", 0.25)

        var world = base.copy(
            activeVehicleUid = tractor.uid,
            vehicles = listOf(tractor),
            implements = listOf(imp),
        )

        val eval = evaluateVehicleHitchPair(world, tractor, imp)
        assertThat(eval.canHitchNow).isTrue()
        world = executeValidatedHitch(world, eval, onFoot = false)

        assertThat(world.vehicles.first().attachedImplementUid).isEqualTo(imp.uid)
        assertThat(world.vehicles.first().hitchLockState).isEqualTo("HITCHING")
        assertThat(world.implements.first().isHitchLocked).isTrue()
        assertThat(world.implements.first().hitchAnimProgress).isEqualTo(0.0)

        // Advance animations -> progress reaches 1.0 and lock becomes LOCKED
        repeat(15) {
            world = stepHitchAndInteractionAnimations(world, 0.05)
        }
        assertThat(world.implements.first().hitchAnimProgress).isWithin(0.01).of(1.0)
        assertThat(world.vehicles.first().hitchLockState).isEqualTo("LOCKED")

        // Cannot unhitch while moving
        val movingUnhitch = evaluateVehicleUnhitch(world, world.vehicles.first().copy(speedMs = 2.5))!!
        assertThat(movingUnhitch.canUnhitchNow).isFalse()

        // Unhitch while stopped preserves exact world position and orientation
        val beforePose = world.implements.first()
        val stoppedUnhitch = evaluateVehicleUnhitch(world, world.vehicles.first())!!
        assertThat(stoppedUnhitch.canUnhitchNow).isTrue()
        world = executeValidatedUnhitch(world, stoppedUnhitch, onFoot = false)

        val afterImp = world.implements.first()
        assertThat(world.vehicles.first().attachedImplementUid).isNull()
        assertThat(afterImp.attachedToVehicleUid).isNull()
        assertThat(afterImp.isHitchLocked).isFalse()
        assertThat(afterImp.x).isWithin(0.001).of(beforePose.x)
        assertThat(afterImp.z).isWithin(0.001).of(beforePose.z)
        assertThat(afterImp.headingDeg).isWithin(0.001).of(beforePose.headingDeg)
    }

    @Test
    fun onFootManualHitchAndUnhitch_validatesPlayerDistanceAndInteractionAnim() {
        val base = createNewWorld(0, "Fazenda A Pé", "map_pr", 250000.0, "Padrão", 64)
        val tractorDef = ALL_VEHICLES.first { it.category == VehicleCategory.TRATOR && it.horsepower >= 140.0 }
        val plowDef = ALL_IMPLEMENTS.first { it.category == ImplementCategory.ARADO && it.requiredHp <= tractorDef.horsepower }

        val tractor = base.vehicles.first().copy(
            uid = "veh_ped",
            defId = tractorDef.id,
            x = 0.0,
            z = 0.0,
            headingDeg = 0.0,
            speedMs = 0.0,
            attachedImplementUid = null,
        )
        val imp = alignedImplementNearTractor(0.0, 0.0, 0.0, plowDef.id, "imp_plow", 0.25)
        val pinPt = computeImplementPinWorldPoint(imp)

        // Player too far (6m away from pin)
        val farWorld = base.copy(
            activeVehicleUid = null,
            vehicles = listOf(tractor),
            implements = listOf(imp),
            player = base.player.copy(x = pinPt.x + 5.5, z = pinPt.z),
        )
        val farEval = evaluateOnFootHitch(farWorld)
        assertThat(farEval).isNotNull()
        assertThat(farEval!!.playerInRange).isFalse()
        assertThat(farEval.hitchCandidate!!.canHitchNow).isFalse()
        assertThat(farEval.hitchCandidate!!.blockReason).isEqualTo(HitchBlockReason.PLAYER_TOO_FAR)

        // Player walks beside the coupling point (1.2m away)
        var nearWorld = farWorld.copy(
            player = farWorld.player.copy(x = pinPt.x + 1.2, z = pinPt.z),
        )
        val nearEval = evaluateOnFootHitch(nearWorld)!!
        assertThat(nearEval.playerInRange).isTrue()
        assertThat(nearEval.hitchCandidate!!.canHitchNow).isTrue()

        nearWorld = performManualHitchOrUnhitchInWorld(nearWorld)
        assertThat(nearWorld.vehicles.first().attachedImplementUid).isEqualTo(imp.uid)
        assertThat(nearWorld.player.actionState).isEqualTo("interact")
        assertThat(nearWorld.player.interactionAnimTimerSec).isGreaterThan(0.5)

        // Complete interaction animation
        repeat(20) { nearWorld = stepHitchAndInteractionAnimations(nearWorld, 0.05) }
        assertThat(nearWorld.player.actionState).isEqualTo("idle")

        // Now manual on-foot unhitch near coupling point
        val unhitchEval = evaluateOnFootHitch(nearWorld)!!
        assertThat(unhitchEval.unhitchCandidate).isNotNull()
        assertThat(unhitchEval.unhitchCandidate!!.canUnhitchNow).isTrue()
        nearWorld = performManualHitchOrUnhitchInWorld(nearWorld)
        assertThat(nearWorld.vehicles.first().attachedImplementUid).isNull()
        assertThat(nearWorld.implements.first().attachedToVehicleUid).isNull()
    }

    @Test
    fun allImplementCategoriesCompatibilityAndReverseTrailerKinematics() {
        val base = createNewWorld(0, "Fazenda Frota", "map_pr", 500000.0, "Padrão", 64)
        val heavyTractorDef = ALL_VEHICLES.first { it.category == VehicleCategory.TRATOR && it.horsepower >= 280.0 }
        val tractor = base.vehicles.first().copy(uid = "veh_heavy", defId = heavyTractorDef.id, x = 0.0, z = 0.0, headingDeg = 0.0, speedMs = 0.0, attachedImplementUid = null)

        val categories = listOf(
            ImplementCategory.PLANTADEIRA,
            ImplementCategory.PULVERIZADOR,
            ImplementCategory.ARADO,
            ImplementCategory.GRADE,
            ImplementCategory.CARRETA,
            ImplementCategory.DISTRIBUIDOR,
            ImplementCategory.SUBSOLADOR,
            ImplementCategory.CULTIVADOR,
            ImplementCategory.ROCADEIRA,
        )
        for (cat in categories) {
            val impDef = ALL_IMPLEMENTS.first { it.category == cat && it.hitchType != HitchType.FIFTH_WHEEL }
            val spec = resolveImplementCouplingSpec(impDef)
            assertThat(spec.tongueOffsetM).isGreaterThan(0.5)
            val imp = alignedImplementNearTractor(0.0, 0.0, 0.0, impDef.id, "imp_${cat.name}", 0.2)
            val eval = evaluateVehicleHitchPair(base, tractor, imp)
            assertThat(eval.canHitchNow).isTrue()
        }

        // Reverse trailer kinematics: steering while reversing induces realistic trailer articulation
        val trailerDef = ALL_IMPLEMENTS.first { it.category == ImplementCategory.CARRETA && it.hitchType == HitchType.DRAWBAR }
        val trailer = alignedImplementNearTractor(0.0, 0.0, 0.0, trailerDef.id, "imp_rev", 0.0)
            .copy(attachedToVehicleUid = tractor.uid, hitchAnimProgress = 1.0, isHitchLocked = true)
        val reversingTractor = tractor.copy(
            engineRunning = true,
            handbrakeOn = false,
            gear = -1,
            speedMs = -2.5,
            steerAngleDeg = 22.0,
            attachedImplementUid = trailer.uid,
        )
        var reverseWorld = base.copy(
            activeVehicleUid = reversingTractor.uid,
            vehicles = listOf(reversingTractor),
            implements = listOf(trailer),
        )

        repeat(20) {
            reverseWorld = stepSimulation(reverseWorld, ControlInput(moveX = 1.0, brake = 1.0), dtSec = 0.05)
        }
        val finalVeh = reverseWorld.vehicles.first()
        val finalTrailer = reverseWorld.implements.first()
        val vehHitch = computeVehicleHitchWorldPoint(finalVeh, HitchType.DRAWBAR)
        val pinPt = computeImplementPinWorldPoint(finalTrailer)
        assertThat(hypot(vehHitch.x - pinPt.x, vehHitch.z - pinPt.z)).isLessThan(0.55)
        assertThat(abs(finalTrailer.drawbarYawDeg)).isGreaterThan(0.2)
        assertThat(abs(finalTrailer.drawbarYawDeg)).isAtMost(resolveImplementCouplingSpec(trailerDef).maxArticulationDeg + 0.1)
    }

    @Test
    fun fieldInfoAnalyzer_hysteresisOwnershipConditionsNpk9StagesAndCalendar() {
        val base = createNewWorld(0, "Fazenda Precisão", "map_pr", 250000.0, "Padrão", 64)
        val plot = base.plots.first { !it.isPreservation }
        val plotCellsIdx = base.cells.indices.filter { base.cells[it].plotId == plot.id }
        assertThat(plotCellsIdx).isNotEmpty()
        val sampleCell = base.cells[plotCellsIdx.first()]
        val cellSize = getWorldCellSizeM(base)
        val worldHalf = getWorldSpanM(base) / 2.0
        val cellWx = sampleCell.gx * cellSize - worldHalf + cellSize * 0.5
        val cellWz = sampleCell.gz * cellSize - worldHalf + cellSize * 0.5

        // Hysteresis stability right at border vs far away
        val detectedInside = detectFieldPlotWithHysteresis(base, cellWx, cellWz, null)
        assertThat(detectedInside).isEqualTo(plot.id)
        val keptWithHysteresis = detectFieldPlotWithHysteresis(base, cellWx, cellWz, plot.id)
        assertThat(keptWithHysteresis).isEqualTo(plot.id)

        // Modify plot cells for rich agronomic telemetry + active Center Pivot
        val soja = ALL_CROPS.first { it.id == "soja" }
        val targetMonthInWindow = soja.plantingMonths.first()
        val offSeasonMonth = (1..12).first { !soja.plantingMonths.contains(it) }

        val updatedCells = base.cells.toMutableList()
        for (idx in plotCellsIdx) {
            updatedCells[idx] = updatedCells[idx].copy(
                soilState = SoilState.CRESCENDO,
                cropId = "soja",
                growthProgress = 0.55, // Stage 5: Crescimento intermediário
                fertilizedStages = 1,
                nitrogen = 0.48,
                phosphorus = 0.62,
                potassium = 0.54,
                phLevel = 5.3, // low pH -> Calcário Necessário
                limedDay = null,
                weedLevel = 0.25, // >= 0.10 -> Herbicida Necessário
                moisture = 0.68,
                compaction = 0.22,
            )
        }

        val activePivot = PlacedCenterPivot(
            uid = "piv_1",
            modelId = "piv_med",
            name = "Pivô Talhão ${plot.id}",
            sizeTier = PivotSizeTier.MEDIO,
            plotId = plot.id,
            centerX = cellWx,
            centerZ = cellWz,
            radiusM = 60.0,
            spanCount = 2,
            isActive = true,
            waterReserveL = 20000.0,
        )

        val worldInSeason = base.copy(
            month = targetMonthInWindow,
            cells = updatedCells,
            centerPivots = listOf(activePivot),
            plots = base.plots.map {
                if (it.id == plot.id) it.copy(owned = true, ownerPlayerUid = "player_A", ownerPlayerName = "João") else it
            },
        )

        val planter = OwnedImplement(
            uid = "imp_plant",
            defId = ALL_IMPLEMENTS.first { it.category == ImplementCategory.PLANTADEIRA }.id,
            x = cellWx,
            z = cellWz,
            headingDeg = 0.0,
        )

        val teleOwn = analyzeFieldPlot(
            state = worldInSeason,
            plotId = plot.id,
            localPlayerUid = "player_A",
            activeVehicle = worldInSeason.vehicles.first(),
            attachedImplement = planter,
        )!!
        assertThat(teleOwn.ownerKind).isEqualTo(FieldOwnerKind.OWN_PROPERTY)
        assertThat(teleOwn.growth!!.stage).isEqualTo(FieldGrowthStage.CRESCIMENTO_INTERMEDIARIO)
        assertThat(teleOwn.growth!!.growthPct).isEqualTo(55)
        assertThat(teleOwn.activePivotIrrigating).isTrue()
        assertThat(teleOwn.soilConditions.first { it.key == "calcario" }.status).isEqualTo(FieldConditionState.NECESSARIO)
        assertThat(teleOwn.soilConditions.first { it.key == "herbicida" }.status).isEqualTo(FieldConditionState.NECESSARIO)
        assertThat(teleOwn.soilConditions.first { it.key == "irrigacao" }.status).isEqualTo(FieldConditionState.EM_ANDAMENTO)
        assertThat(teleOwn.fertilization.firstPassApplied).isTrue()
        assertThat(teleOwn.fertilization.secondPassApplied).isFalse()
        assertThat(teleOwn.fertilization.nitrogenPct).isEqualTo(48)
        assertThat(teleOwn.operationalAdvisory).isNotNull()

        // Other player, NPC, and Available for purchase badges
        val teleOther = analyzeFieldPlot(worldInSeason, plot.id, localPlayerUid = "player_B")!!
        assertThat(teleOther.ownerKind).isEqualTo(FieldOwnerKind.OTHER_PLAYER)
        assertThat(teleOther.ownerSubtitle).isEqualTo("João")

        val worldNpc = worldInSeason.copy(
            plots = worldInSeason.plots.map {
                if (it.id == plot.id) it.copy(owned = false, leased = false, ownerPlayerUid = null, ownerNpcName = "Fazenda Santa Clara") else it
            },
        )
        assertThat(analyzeFieldPlot(worldNpc, plot.id)!!.ownerKind).isEqualTo(FieldOwnerKind.NPC_PROPERTY)

        val worldForSale = worldInSeason.copy(
            plots = worldInSeason.plots.map {
                if (it.id == plot.id) it.copy(owned = false, leased = false, ownerPlayerUid = null, ownerNpcName = null) else it
            },
        )
        assertThat(analyzeFieldPlot(worldForSale, plot.id)!!.ownerKind).isEqualTo(FieldOwnerKind.AVAILABLE_FOR_PURCHASE)

        // Calendar out-of-season check when plot is unplanted
        val unplantedCells = updatedCells.map {
            if (it.plotId == plot.id) it.copy(soilState = SoilState.ARADO, cropId = null, growthProgress = 0.0, isHarrowed = false) else it
        }
        val worldOutOfSeason = worldInSeason.copy(
            month = offSeasonMonth,
            cells = unplantedCells,
            vehicles = worldInSeason.vehicles.map { it.copy(selectedCropId = "soja") },
        )
        val teleOff = analyzeFieldPlot(worldOutOfSeason, plot.id, localPlayerUid = "player_A", activeVehicle = worldOutOfSeason.vehicles.first())!!
        assertThat(teleOff.calendarStatus).isEqualTo(CropCalendarStatus.FORA_DA_EPOCA)
        assertThat(teleOff.soilConditions.first { it.key == "gradagem" }.status).isEqualTo(FieldConditionState.NECESSARIA)

        // Harvested (Stage 9/9 Colhido) check
        val harvestedCells = updatedCells.map {
            if (it.plotId == plot.id) it.copy(soilState = SoilState.PALHADA, cropId = null, lastCropId = "soja", growthProgress = 0.0) else it
        }
        val teleHarvested = analyzeFieldPlot(worldInSeason.copy(cells = harvestedCells), plot.id)!!
        assertThat(teleHarvested.growth!!.stage).isEqualTo(FieldGrowthStage.COLHIDO)
        assertThat(teleHarvested.growth!!.stage.stageNumber).isEqualTo(9)
    }

    @Test
    fun multiplayerSyncCodecAndSettingsPersistence_roundTripCleanly() {
        val base = createNewWorld(0, "Fazenda MP", "map_pr", 250000.0, "Padrão", 64)
        val veh = base.vehicles.first().copy(uid = "veh_sync", attachedImplementUid = null)
        val imp = base.implements.first().copy(uid = "imp_sync", attachedToVehicleUid = null)
        var synced = base.copy(vehicles = listOf(veh), implements = listOf(imp))

        // Hitch encode & apply
        val hitchMsg = MultiplayerSyncCodec.encodeHitch(veh.uid, imp.uid, 12.5, -8.0, 90.0)
        assertThat(hitchMsg.length).isLessThan(240)
        synced = MultiplayerSyncCodec.applySyncMessage(synced, "HITCH", hitchMsg.removePrefix("__SYNC__:HITCH:"))
        assertThat(synced.vehicles.first().attachedImplementUid).isEqualTo(imp.uid)
        assertThat(synced.implements.first().attachedToVehicleUid).isEqualTo(veh.uid)
        assertThat(synced.implements.first().x).isWithin(0.05).of(12.5)

        // Unhitch encode & apply
        val unhitchMsg = MultiplayerSyncCodec.encodeUnhitch(veh.uid, imp.uid, 15.0, -9.0, 45.0)
        synced = MultiplayerSyncCodec.applySyncMessage(synced, "UNHITCH", unhitchMsg.removePrefix("__SYNC__:UNHITCH:"))
        assertThat(synced.vehicles.first().attachedImplementUid).isNull()
        assertThat(synced.implements.first().attachedToVehicleUid).isNull()

        // Plot ownership & Time sync
        val plotId = synced.plots.first().id
        val ownMsg = MultiplayerSyncCodec.encodePlotOwnership(plotId, "u_remote", "Carlos Agro", owned = true, leased = false)
        synced = MultiplayerSyncCodec.applySyncMessage(synced, "PLOT_OWN", ownMsg.removePrefix("__SYNC__:PLOT_OWN:"))
        val targetPlot = synced.plots.first { it.id == plotId }
        assertThat(targetPlot.ownerPlayerUid).isEqualTo("u_remote")
        assertThat(targetPlot.ownerPlayerName).isEqualTo("Carlos Agro")

        val timeMsg = MultiplayerSyncCodec.encodeTimeMonth(14, 10)
        synced = MultiplayerSyncCodec.applySyncMessage(synced, "TIME_MONTH", timeMsg.removePrefix("__SYNC__:TIME_MONTH:"))
        assertThat(synced.day).isEqualTo(14)
        assertThat(synced.month).isEqualTo(10)

        // Settings persistence + backward-compat JSON check
        val app = ApplicationProvider.getApplicationContext<Application>()
        val customSettings = GameSettings(
            hitchMode = HitchMode.ASK_EACH_TIME,
            fieldPanelConfig = FieldInfoPanelConfig(
                enabled = true,
                corner = FieldPanelCorner.BOTTOM_END,
                size = FieldPanelSize.GRANDE,
                defaultViewMode = FieldPanelViewMode.EXPANDIDO,
            ),
        )
        saveSettings(app, customSettings)
        val reloaded = loadSettings(app)
        assertThat(reloaded.hitchMode).isEqualTo(HitchMode.ASK_EACH_TIME)
        assertThat(reloaded.fieldPanelConfig.corner).isEqualTo(FieldPanelCorner.BOTTOM_END)
        assertThat(reloaded.fieldPanelConfig.size).isEqualTo(FieldPanelSize.GRANDE)
        assertThat(reloaded.fieldPanelConfig.defaultViewMode).isEqualTo(FieldPanelViewMode.EXPANDIDO)

        val legacyJson = """{"soundEnabled":true,"controlOpacity":0.9}"""
        val parsedLegacy = testJson.decodeFromString(GameSettings.serializer(), legacyJson)
        assertThat(parsedLegacy.hitchMode).isEqualTo(HitchMode.IN_VEHICLE_BUTTON)
        assertThat(parsedLegacy.fieldPanelConfig.enabled).isTrue()
    }
}
