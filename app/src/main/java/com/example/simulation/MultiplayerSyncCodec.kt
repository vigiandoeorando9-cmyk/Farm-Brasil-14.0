package com.example.simulation

import com.example.domain.SoilState
import com.example.domain.WorldState
import kotlin.math.roundToInt

const val MP_SYNC_HEADER = "__SYNC__:"

object MultiplayerSyncCodec {
    fun encodeHitch(vehUid: String, impUid: String, x: Double, z: Double, headingDeg: Double): String =
        "${MP_SYNC_HEADER}HITCH:$vehUid|$impUid|${"%.2f".format(java.util.Locale.US, x)}|${"%.2f".format(java.util.Locale.US, z)}|${"%.1f".format(java.util.Locale.US, headingDeg)}".take(238)

    fun encodeUnhitch(vehUid: String, impUid: String, x: Double, z: Double, headingDeg: Double): String =
        "${MP_SYNC_HEADER}UNHITCH:$vehUid|$impUid|${"%.2f".format(java.util.Locale.US, x)}|${"%.2f".format(java.util.Locale.US, z)}|${"%.1f".format(java.util.Locale.US, headingDeg)}".take(238)

    fun encodePlotOwnership(
        plotId: Int,
        ownerUid: String,
        ownerName: String,
        owned: Boolean,
        leased: Boolean,
    ): String {
        val safeName = ownerName.replace("|", " ").take(40)
        return "${MP_SYNC_HEADER}PLOT_OWN:$plotId|$ownerUid|$safeName|${if (owned) 1 else 0}|${if (leased) 1 else 0}".take(238)
    }

    fun encodeTimeMonth(day: Int, month: Int): String =
        "${MP_SYNC_HEADER}TIME_MONTH:$day|$month"

    fun encodePlotAgronomySummary(
        plotId: Int,
        soilState: SoilState,
        cropId: String?,
        growthPct: Int,
        fertStages: Int,
        phX10: Int,
        moistPct: Int,
        weedPct: Int,
        isHarrowed: Boolean?,
        limedDay: Int?,
        plowedDay: Int?,
    ): String {
        val cropToken = (cropId ?: "").replace("|", "").take(24)
        val har = when (isHarrowed) {
            true -> 1
            false -> 0
            null -> -1
        }
        return "${MP_SYNC_HEADER}FIELD_SYNC:$plotId|${soilState.ordinal}|$cropToken|$growthPct|$fertStages|$phX10|$moistPct|$weedPct|$har|${limedDay ?: 0}|${plowedDay ?: 0}".take(238)
    }

    fun applySyncMessage(state: WorldState, kind: String, payload: String): WorldState {
        return when (kind) {
            "HITCH" -> {
                val parts = payload.split("|")
                if (parts.size < 5) return state
                val vehUid = parts[0]
                val impUid = parts[1]
                val x = parts[2].toDoubleOrNull() ?: return state
                val z = parts[3].toDoubleOrNull() ?: return state
                val hdg = parts[4].toDoubleOrNull() ?: return state
                state.copy(
                    vehicles = state.vehicles.map { v ->
                        if (v.uid == vehUid) v.copy(attachedImplementUid = impUid, hitchLockState = "LOCKED") else v
                    },
                    implements = state.implements.map { i ->
                        if (i.uid == impUid) i.copy(x = x, z = z, headingDeg = hdg, attachedToVehicleUid = vehUid, isHitchLocked = true, hitchAnimProgress = 1.0) else i
                    },
                )
            }
            "UNHITCH" -> {
                val parts = payload.split("|")
                if (parts.size < 5) return state
                val vehUid = parts[0]
                val impUid = parts[1]
                val x = parts[2].toDoubleOrNull() ?: return state
                val z = parts[3].toDoubleOrNull() ?: return state
                val hdg = parts[4].toDoubleOrNull() ?: return state
                state.copy(
                    vehicles = state.vehicles.map { v ->
                        if (v.uid == vehUid && v.attachedImplementUid == impUid) v.copy(attachedImplementUid = null, isPtoEngaged = false, ptoRpm = 0.0, hitchLockState = null) else v
                    },
                    implements = state.implements.map { i ->
                        if (i.uid == impUid) i.copy(x = x, z = z, headingDeg = hdg, attachedToVehicleUid = null, isHitchLocked = false, isLowered = false, isActive = false, liftProgress = 1.0) else i
                    },
                )
            }
            "PLOT_OWN" -> {
                val parts = payload.split("|")
                if (parts.size < 5) return state
                val plotId = parts[0].toIntOrNull() ?: return state
                val ownerUid = parts[1]
                val ownerName = parts[2]
                val owned = parts[3] == "1"
                val leased = parts[4] == "1"
                state.copy(
                    plots = state.plots.map { p ->
                        if (p.id == plotId) {
                            p.copy(
                                owned = owned,
                                leased = leased,
                                ownerPlayerUid = ownerUid,
                                ownerPlayerName = ownerName,
                            )
                        } else p
                    },
                )
            }
            "TIME_MONTH" -> {
                val parts = payload.split("|")
                if (parts.size < 2) return state
                val day = parts[0].toIntOrNull() ?: return state
                val month = parts[1].toIntOrNull()?.coerceIn(1, 12) ?: return state
                state.copy(day = day, month = month)
            }
            "FIELD_SYNC" -> {
                val parts = payload.split("|")
                if (parts.size < 11) return state
                val plotId = parts[0].toIntOrNull() ?: return state
                val soilOrd = parts[1].toIntOrNull() ?: return state
                val soilState = SoilState.entries.getOrNull(soilOrd) ?: return state
                val cropId = parts[2].ifBlank { null }
                val growthPct = (parts[3].toIntOrNull() ?: 0).coerceIn(0, 100)
                val fertStages = (parts[4].toIntOrNull() ?: 0).coerceIn(0, 3)
                val ph = ((parts[5].toIntOrNull() ?: 60) / 10.0).coerceIn(4.5, 7.5)
                val moist = ((parts[6].toIntOrNull() ?: 65) / 100.0).coerceIn(0.1, 1.0)
                val weed = ((parts[7].toIntOrNull() ?: 0) / 100.0).coerceIn(0.0, 1.0)
                val isHarrowed: Boolean? = when (parts[8].toIntOrNull()) {
                    1 -> true
                    0 -> false
                    else -> null
                }
                val limedDay = parts[9].toIntOrNull()?.takeIf { it > 0 }
                val plowedDay = parts[10].toIntOrNull()?.takeIf { it > 0 }
                state.copy(
                    cells = state.cells.map { c ->
                        if (c.plotId == plotId) {
                            c.copy(
                                soilState = soilState,
                                cropId = cropId,
                                growthProgress = growthPct / 100.0,
                                fertilizedStages = fertStages,
                                phLevel = ph,
                                moisture = moist,
                                weedLevel = weed,
                                isHarrowed = isHarrowed,
                                limedDay = limedDay ?: c.limedDay,
                                plowedDay = plowedDay ?: c.plowedDay,
                            )
                        } else c
                    },
                )
            }
            else -> state
        }
    }

    fun buildPlotSummaryFromTelemetry(telemetry: FieldPanelTelemetry): String {
        val soilOrd = telemetry.dominantSoilState
        val cropId = telemetry.growth?.takeIf { it.isActiveCrop }?.cropId
        val growthPct = telemetry.growth?.growthPct ?: 0
        val fertStages = when {
            telemetry.fertilization.secondPassApplied -> 2
            telemetry.fertilization.firstPassApplied -> 1
            else -> 0
        }
        val phX10 = (telemetry.avgPh * 10.0).roundToInt()
        val harrowFlag = telemetry.soilConditions.find { it.key == "gradagem" }?.let {
            when (it.status) {
                FieldConditionState.REALIZADA -> true
                FieldConditionState.NECESSARIA -> false
                else -> null
            }
        }
        return encodePlotAgronomySummary(
            plotId = telemetry.plot.id,
            soilState = soilOrd,
            cropId = cropId,
            growthPct = growthPct,
            fertStages = fertStages,
            phX10 = phX10,
            moistPct = telemetry.avgMoisturePct,
            weedPct = telemetry.avgWeedPct,
            isHarrowed = harrowFlag,
            limedDay = if (telemetry.soilConditions.find { it.key == "calcario" }?.status == FieldConditionState.APLICADO) 1 else null,
            plowedDay = if (telemetry.soilConditions.find { it.key == "aradura" }?.status == FieldConditionState.REALIZADA) 1 else null,
        )
    }
}
