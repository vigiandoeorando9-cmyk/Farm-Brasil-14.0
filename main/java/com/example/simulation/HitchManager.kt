package com.example.simulation

import com.example.domain.HITCH_LABELS
import com.example.domain.HitchType
import com.example.domain.ImplementCategory
import com.example.domain.ImplementCouplingSpec
import com.example.domain.ImplementDef
import com.example.domain.OwnedImplement
import com.example.domain.OwnedVehicle
import com.example.domain.Vec2
import com.example.domain.VehicleDef
import com.example.domain.WorldState
import com.example.domain.getImplementDef
import com.example.domain.getVehicleDef
import com.example.domain.getVehicleHitchPointOffset
import com.example.domain.resolveImplementCouplingSpec
import com.example.domain.resolveVehicleTuning
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

const val HITCH_MAX_STOPPED_SPEED_MS = 0.55

enum class HitchBlockReason {
    NO_VEHICLE,
    VEHICLE_MOVING,
    VEHICLE_BUSY,
    IMPLEMENT_ALREADY_ATTACHED,
    INCOMPATIBLE_HITCH,
    TOO_FAR,
    MISALIGNED,
    PLAYER_TOO_FAR,
    NONE,
}

data class HitchCandidateEvaluation(
    val vehicle: OwnedVehicle,
    val vehicleDef: VehicleDef,
    val implement: OwnedImplement,
    val implementDef: ImplementDef,
    val spec: ImplementCouplingSpec,
    val vehicleHitchPoint: Vec2,
    val implementPinPoint: Vec2,
    val distanceM: Double,
    val angleDiffDeg: Double,
    val canHitchNow: Boolean,
    val blockReason: HitchBlockReason,
    val statusMessage: String,
    val warningMessage: String? = null,
)

data class UnhitchCandidateEvaluation(
    val vehicle: OwnedVehicle,
    val vehicleDef: VehicleDef,
    val implement: OwnedImplement,
    val implementDef: ImplementDef,
    val connectionPoint: Vec2,
    val canUnhitchNow: Boolean,
    val blockReason: HitchBlockReason,
    val statusMessage: String,
    val playerDistanceM: Double? = null,
)

data class OnFootHitchEvaluation(
    val hitchCandidate: HitchCandidateEvaluation? = null,
    val unhitchCandidate: UnhitchCandidateEvaluation? = null,
    val playerDistanceToPinM: Double = 999.0,
    val playerInRange: Boolean = false,
)

fun computeVehicleHitchWorldPoint(veh: OwnedVehicle, hitchType: HitchType): Vec2 {
    val vDef = getVehicleDef(veh.defId)
    val offset = getVehicleHitchPointOffset(vDef, hitchType)
    val rad = veh.headingDeg * PI / 180.0
    return Vec2(
        x = veh.x + sin(rad) * offset,
        z = veh.z + cos(rad) * offset,
    )
}

fun computeImplementPinWorldPoint(imp: OwnedImplement): Vec2 {
    val iDef = getImplementDef(imp.defId)
    val spec = resolveImplementCouplingSpec(iDef)
    val rad = imp.headingDeg * PI / 180.0
    val dir = if (spec.isFrontMount) -1.0 else 1.0
    return Vec2(
        x = imp.x + sin(rad) * (spec.tongueOffsetM * dir),
        z = imp.z + cos(rad) * (spec.tongueOffsetM * dir),
    )
}

fun normalizeHitchAngleDeg(deg: Double): Double {
    var a = deg % 360.0
    if (a > 180.0) a -= 360.0
    if (a < -180.0) a += 360.0
    return a
}

fun isImplementAttachedAnywhere(state: WorldState, imp: OwnedImplement): Boolean {
    if (imp.attachedToVehicleUid != null) return true
    return state.vehicles.any { it.attachedImplementUid == imp.uid }
}

fun evaluateVehicleHitchPair(
    state: WorldState,
    veh: OwnedVehicle,
    imp: OwnedImplement,
): HitchCandidateEvaluation {
    val vDef = getVehicleDef(veh.defId)
    val iDef = getImplementDef(imp.defId)
    val spec = resolveImplementCouplingSpec(iDef)
    val vehHitchPt = computeVehicleHitchWorldPoint(veh, spec.defaultHitchType)
    val impPinPt = computeImplementPinWorldPoint(imp)
    val distM = hypot(vehHitchPt.x - impPinPt.x, vehHitchPt.z - impPinPt.z)
    val angleDiff = abs(normalizeHitchAngleDeg(veh.headingDeg - imp.headingDeg))

    val tuning = resolveVehicleTuning(veh, vDef)
    val compat = checkMachineImplementCompatibility(vDef, iDef, tuning.effectiveHp)

    val busyVehicle = veh.attachedImplementUid != null
    val busyImp = isImplementAttachedAnywhere(state, imp)
    val moving = abs(veh.speedMs) > HITCH_MAX_STOPPED_SPEED_MS

    val (reason, msg) = when {
        busyVehicle -> HitchBlockReason.VEHICLE_BUSY to
            "O veículo já possui um implemento engatado. Desengate antes."
        busyImp -> HitchBlockReason.IMPLEMENT_ALREADY_ATTACHED to
            "${iDef.model} já está engatado em outra máquina."
        compat.blockMessage != null -> HitchBlockReason.INCOMPATIBLE_HITCH to
            compat.blockMessage
        moving -> HitchBlockReason.VEHICLE_MOVING to
            "Pare o veículo (${abs(veh.speedMs * 3.6).roundToInt()} km/h) antes de engatar."
        distM > spec.maxHitchDistanceM -> HitchBlockReason.TOO_FAR to
            "Aproxime mais o ponto de engate (${"%.1f".format(distM)}m / máx ${"%.1f".format(spec.maxHitchDistanceM)}m)."
        angleDiff > spec.maxAngleDeg -> HitchBlockReason.MISALIGNED to
            "Alinhe o engate com o implemento (desvio ${angleDiff.roundToInt()}° / máx ${spec.maxAngleDeg.roundToInt()}°)."
        else -> HitchBlockReason.NONE to
            "Pronto para engatar ${iDef.model} (${HITCH_LABELS[iDef.hitchType] ?: iDef.hitchType.name})."
    }

    return HitchCandidateEvaluation(
        vehicle = veh,
        vehicleDef = vDef,
        implement = imp,
        implementDef = iDef,
        spec = spec,
        vehicleHitchPoint = vehHitchPt,
        implementPinPoint = impPinPt,
        distanceM = distM,
        angleDiffDeg = angleDiff,
        canHitchNow = reason == HitchBlockReason.NONE,
        blockReason = reason,
        statusMessage = msg,
        warningMessage = compat.warningMessage,
    )
}

fun findBestVehicleHitchCandidate(
    state: WorldState,
    veh: OwnedVehicle,
    searchRadiusM: Double = 8.5,
): HitchCandidateEvaluation? {
    if (veh.attachedImplementUid != null) return null
    val evaluations = state.implements
        .filter { !isImplementAttachedAnywhere(state, it) }
        .map { imp -> evaluateVehicleHitchPair(state, veh, imp) }
        .filter { it.distanceM <= searchRadiusM }

    if (evaluations.isEmpty()) return null

    // Priority: valid ready candidates -> compatible candidates -> closest
    return evaluations
        .sortedWith(
            compareByDescending<HitchCandidateEvaluation> { it.canHitchNow }
                .thenByDescending { it.blockReason != HitchBlockReason.INCOMPATIBLE_HITCH }
                .thenBy { it.distanceM }
        )
        .firstOrNull()
}

fun evaluateVehicleUnhitch(
    state: WorldState,
    veh: OwnedVehicle,
): UnhitchCandidateEvaluation? {
    val impUid = veh.attachedImplementUid ?: return null
    val imp = state.implements.find { it.uid == impUid } ?: return null
    val vDef = getVehicleDef(veh.defId)
    val iDef = getImplementDef(imp.defId)
    val spec = resolveImplementCouplingSpec(iDef)
    val connPt = computeVehicleHitchWorldPoint(veh, spec.defaultHitchType)
    val stopped = abs(veh.speedMs) <= HITCH_MAX_STOPPED_SPEED_MS

    val (reason, msg) = if (!stopped) {
        HitchBlockReason.VEHICLE_MOVING to
            "Pare o veículo completamente antes de desengatar ${iDef.model}."
    } else {
        HitchBlockReason.NONE to "Pronto para desengatar ${iDef.model}."
    }

    return UnhitchCandidateEvaluation(
        vehicle = veh,
        vehicleDef = vDef,
        implement = imp,
        implementDef = iDef,
        connectionPoint = connPt,
        canUnhitchNow = stopped,
        blockReason = reason,
        statusMessage = msg,
    )
}

fun evaluateOnFootHitch(
    state: WorldState,
    maxPlayerInteractDistM: Double = 3.6,
    maxDiscoveryDistM: Double = 8.5,
): OnFootHitchEvaluation? {
    val px = state.player.x
    val pz = state.player.z

    // 1. Check nearby attached connection points for manual unhitching
    var bestUnhitch: UnhitchCandidateEvaluation? = null
    var bestUnhitchDist = maxDiscoveryDistM
    for (v in state.vehicles) {
        val eval = evaluateVehicleUnhitch(state, v) ?: continue
        val pDist = hypot(eval.connectionPoint.x - px, eval.connectionPoint.z - pz)
        if (pDist < bestUnhitchDist) {
            bestUnhitchDist = pDist
            val inRange = pDist <= maxPlayerInteractDistM
            val canNow = eval.canUnhitchNow && inRange
            val msg = when {
                !inRange -> "Caminhe até o engate de ${eval.implementDef.model} (${"%.1f".format(pDist)}m)."
                !eval.canUnhitchNow -> eval.statusMessage
                else -> "Pressione [ENGATAR/DESENGATAR] para desconectar ${eval.implementDef.model}."
            }
            bestUnhitch = eval.copy(
                canUnhitchNow = canNow,
                blockReason = if (!inRange) HitchBlockReason.PLAYER_TOO_FAR else eval.blockReason,
                statusMessage = msg,
                playerDistanceM = pDist,
            )
        }
    }
    if (bestUnhitch != null && (bestUnhitch.playerDistanceM ?: 99.0) <= maxPlayerInteractDistM) {
        return OnFootHitchEvaluation(
            unhitchCandidate = bestUnhitch,
            playerDistanceToPinM = bestUnhitch.playerDistanceM ?: bestUnhitchDist,
            playerInRange = true,
        )
    }

    // 2. Check nearby free implements and nearby free stopped vehicles
    var bestEval: HitchCandidateEvaluation? = null
    var bestPlayerDist = maxDiscoveryDistM

    for (imp in state.implements) {
        if (isImplementAttachedAnywhere(state, imp)) continue
        val pinPt = computeImplementPinWorldPoint(imp)
        val pDist = hypot(pinPt.x - px, pinPt.z - pz)
        if (pDist > maxDiscoveryDistM) continue

        // Find best matching vehicle for this implement
        val vehMatch = state.vehicles
            .filter { it.attachedImplementUid == null }
            .map { v -> evaluateVehicleHitchPair(state, v, imp) }
            .sortedWith(
                compareByDescending<HitchCandidateEvaluation> { it.canHitchNow }
                    .thenByDescending { it.blockReason != HitchBlockReason.INCOMPATIBLE_HITCH }
                    .thenBy { it.distanceM }
            )
            .firstOrNull() ?: continue

        // Only surface this pair if vehicle hitch is reasonably near implement (< 9m)
        if (vehMatch.distanceM > 9.0) continue

        val playerOk = pDist <= maxPlayerInteractDistM
        val adjusted = if (!playerOk && vehMatch.canHitchNow) {
            vehMatch.copy(
                canHitchNow = false,
                blockReason = HitchBlockReason.PLAYER_TOO_FAR,
                statusMessage = "Chegue mais perto do ponto de engate (${"%.1f".format(pDist)}m).",
            )
        } else {
            vehMatch
        }

        if (bestEval == null ||
            (adjusted.canHitchNow && !bestEval.canHitchNow) ||
            (adjusted.canHitchNow == bestEval.canHitchNow && pDist < bestPlayerDist)
        ) {
            bestEval = adjusted
            bestPlayerDist = pDist
        }
    }

    if (bestEval != null) {
        return OnFootHitchEvaluation(
            hitchCandidate = bestEval,
            playerDistanceToPinM = bestPlayerDist,
            playerInRange = bestPlayerDist <= maxPlayerInteractDistM,
        )
    }

    if (bestUnhitch != null) {
        return OnFootHitchEvaluation(
            unhitchCandidate = bestUnhitch,
            playerDistanceToPinM = bestUnhitchDist,
            playerInRange = bestUnhitchDist <= maxPlayerInteractDistM,
        )
    }

    return null
}

fun executeValidatedHitch(
    state: WorldState,
    eval: HitchCandidateEvaluation,
    onFoot: Boolean,
): WorldState {
    if (!eval.canHitchNow) {
        return state.copy(statusAlert = eval.statusMessage)
    }
    val veh = eval.vehicle
    val imp = eval.implement
    val spec = eval.spec
    val hitchPt = eval.vehicleHitchPoint

    // Snap implement position smoothly to aligned hitch geometry, preserving valid pitch/roll terrain contact
    val relAngle = normalizeHitchAngleDeg(imp.headingDeg - veh.headingDeg)
        .coerceIn(-spec.maxArticulationDeg, spec.maxArticulationDeg)
    val coupledHeading = if (spec.isFrontMount) {
        veh.headingDeg
    } else {
        (veh.headingDeg + relAngle + 360.0) % 360.0
    }
    val impRad = coupledHeading * PI / 180.0
    val (alignedX, alignedZ) = if (spec.isFrontMount) {
        (hitchPt.x + sin(impRad) * spec.tongueOffsetM) to (hitchPt.z + cos(impRad) * spec.tongueOffsetM)
    } else {
        (hitchPt.x - sin(impRad) * spec.tongueOffsetM) to (hitchPt.z - cos(impRad) * spec.tongueOffsetM)
    }

    val nextPlayer = if (onFoot) {
        val targetYaw = kotlin.math.atan2(hitchPt.x - state.player.x, hitchPt.z - state.player.z) * 180.0 / PI
        state.player.copy(
            headingDeg = (targetYaw + 360.0) % 360.0,
            speedMs = 0.0,
            actionState = "interact",
            interactionAnimTimerSec = 0.85,
            interactionTargetLabel = eval.implementDef.model,
        )
    } else {
        state.player
    }

    val warnMsg = eval.warningMessage
    val successText = buildString {
        append(eval.implementDef.model)
        append(if (onFoot) " engatado manualmente a pé em " else " engatado em ")
        append(eval.vehicleDef.model)
        if (warnMsg != null) append(" — Aviso: $warnMsg") else append("!")
    }

    return state.copy(
        vehicles = state.vehicles.map { v ->
            if (v.uid == veh.uid) {
                v.copy(
                    attachedImplementUid = imp.uid,
                    isPtoEngaged = false,
                    ptoRpm = 0.0,
                    hitchLockState = "HITCHING",
                )
            } else v
        },
        implements = state.implements.map { i ->
            if (i.uid == imp.uid) {
                i.copy(
                    x = alignedX,
                    z = alignedZ,
                    headingDeg = coupledHeading,
                    drawbarYawDeg = normalizeHitchAngleDeg(coupledHeading - veh.headingDeg),
                    attachedToVehicleUid = veh.uid,
                    isHitchLocked = true,
                    hitchAnimProgress = 0.0,
                    workingHitchType = eval.implementDef.hitchType,
                    compatibilityWarning = warnMsg,
                )
            } else i
        },
        player = nextPlayer,
        statusAlert = successText,
    )
}

fun executeValidatedUnhitch(
    state: WorldState,
    eval: UnhitchCandidateEvaluation,
    onFoot: Boolean,
): WorldState {
    if (!eval.canUnhitchNow) {
        return state.copy(statusAlert = eval.statusMessage)
    }
    val veh = eval.vehicle
    val imp = eval.implement

    val nextPlayer = if (onFoot) {
        val targetYaw = kotlin.math.atan2(eval.connectionPoint.x - state.player.x, eval.connectionPoint.z - state.player.z) * 180.0 / PI
        state.player.copy(
            headingDeg = (targetYaw + 360.0) % 360.0,
            speedMs = 0.0,
            actionState = "interact",
            interactionAnimTimerSec = 0.85,
            interactionTargetLabel = eval.implementDef.model,
        )
    } else {
        state.player
    }

    return state.copy(
        vehicles = state.vehicles.map { v ->
            if (v.uid == veh.uid) {
                v.copy(
                    attachedImplementUid = null,
                    isPtoEngaged = false,
                    ptoRpm = 0.0,
                    hitchLockState = "UNHITCHING",
                )
            } else v
        },
        implements = state.implements.map { i ->
            if (i.uid == imp.uid) {
                i.copy(
                    // Explicitly preserve current world coordinates & orientation
                    x = imp.x,
                    z = imp.z,
                    headingDeg = imp.headingDeg,
                    pitchDeg = imp.pitchDeg,
                    rollDeg = imp.rollDeg,
                    attachedToVehicleUid = null,
                    isHitchLocked = false,
                    hitchAnimProgress = 0.0,
                    isLowered = false,
                    isActive = false,
                    liftProgress = 1.0,
                    compatibilityWarning = null,
                )
            } else i
        },
        player = nextPlayer,
        statusAlert = if (onFoot) {
            "${eval.implementDef.model} desengatado manualmente no local."
        } else {
            "${eval.implementDef.model} desengatado com segurança."
        },
    )
}

fun performManualHitchOrUnhitchInWorld(state: WorldState): WorldState {
    val activeUid = state.activeVehicleUid
    if (activeUid != null) {
        val veh = state.vehicles.find { it.uid == activeUid } ?: return state
        if (veh.attachedImplementUid != null) {
            val unhitchEval = evaluateVehicleUnhitch(state, veh) ?: return state
            return executeValidatedUnhitch(state, unhitchEval, onFoot = false)
        }
        val eval = findBestVehicleHitchCandidate(state, veh, searchRadiusM = 12.0)
            ?: return state.copy(statusAlert = "Nenhum implemento próximo para engatar. Aproxime a barra de tração.")
        return executeValidatedHitch(state, eval, onFoot = false)
    }

    // On-foot manual hitch / unhitch near coupling point
    val onFoot = evaluateOnFootHitch(state)
        ?: return state.copy(statusAlert = "Aproxime-se do ponto de engate entre o veículo estacionado e o implemento.")
    if (onFoot.unhitchCandidate != null) {
        return executeValidatedUnhitch(state, onFoot.unhitchCandidate, onFoot = true)
    }
    val hitchCand = onFoot.hitchCandidate
        ?: return state.copy(statusAlert = "Nenhum engate compatível próximo.")
    return executeValidatedHitch(state, hitchCand, onFoot = true)
}

fun stepHitchAndInteractionAnimations(state: WorldState, dt: Double): WorldState {
    var impChanged = false
    val finishedVehUids = mutableSetOf<String>()
    val nextImplements = state.implements.map { imp ->
        val prog = imp.hitchAnimProgress ?: 1.0
        if (prog < 1.0) {
            impChanged = true
            val nextProg = min(1.0, prog + dt * 2.2)
            if (nextProg >= 1.0 && imp.attachedToVehicleUid != null) {
                finishedVehUids.add(imp.attachedToVehicleUid)
            }
            imp.copy(hitchAnimProgress = nextProg)
        } else imp
    }

    val vehChanged = state.vehicles.any {
        it.hitchLockState == "HITCHING" && (it.attachedImplementUid != null && finishedVehUids.contains(it.uid) || it.attachedImplementUid == null) ||
            (it.hitchLockState == "UNHITCHING" && it.attachedImplementUid == null)
    }
    val nextVehicles = if (vehChanged) {
        state.vehicles.map { v ->
            when {
                v.hitchLockState == "HITCHING" && v.attachedImplementUid != null && finishedVehUids.contains(v.uid) ->
                    v.copy(hitchLockState = "LOCKED")
                v.hitchLockState == "UNHITCHING" && v.attachedImplementUid == null ->
                    v.copy(hitchLockState = null)
                else -> v
            }
        }
    } else state.vehicles

    val timer = state.player.interactionAnimTimerSec ?: 0.0
    var nextPlayer = state.player
    var playerChanged = false
    if (timer > 0.0) {
        val nextTimer = max(0.0, timer - dt)
        val shouldClearState = nextTimer <= 0.0 && state.player.actionState == "interact"
        nextPlayer = state.player.copy(
            interactionAnimTimerSec = nextTimer,
            actionState = if (shouldClearState) {
                if (state.player.carryingPropUid != null) "carry" else "idle"
            } else {
                state.player.actionState
            },
            interactionTargetLabel = if (shouldClearState) null else state.player.interactionTargetLabel,
        )
        playerChanged = true
    }

    return if (!impChanged && !vehChanged && !playerChanged) {
        state
    } else {
        state.copy(
            implements = if (impChanged) nextImplements else state.implements,
            vehicles = nextVehicles,
            player = nextPlayer,
        )
    }
}
