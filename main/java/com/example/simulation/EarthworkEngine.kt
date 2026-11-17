package com.example.simulation

import com.example.domain.EarthworkMark
import com.example.domain.OwnedVehicle
import com.example.domain.PlotZoneType
import com.example.domain.SoilState
import com.example.domain.TerrainCell
import com.example.domain.VehicleBodyStyle
import com.example.domain.VehicleCategory
import com.example.domain.VehicleDef
import com.example.domain.WorldState
import com.example.domain.getEarthCapacityM3
import com.example.domain.getVehicleDef
import com.example.domain.isEarthmovingVehicle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private fun canModifyEarthAtCell(state: WorldState, cell: TerrainCell, cellWx: Double, cellWz: Double): String? {
    if (cell.soilState == SoilState.AREA_PRESERVACAO) {
        return "Escavação bloqueada em Área de Preservação Ambiental (APP)."
    }
    if (cell.soilState == SoilState.ESTRADA_ASFALTO || cell.soilState == SoilState.PONTE) {
        return "Não é permitido escavar rodovia asfaltada ou ponte."
    }
    if (cell.soilState == SoilState.AGUA_RIO) {
        return "Não é permitido escavar diretamente dentro do leito do rio."
    }
    if (cell.plotId > 0) {
        val plot = state.plots.find { it.id == cell.plotId }
        if (plot != null) {
            if (plot.isPreservation || plot.zoneType == PlotZoneType.PRESERVACAO) {
                return "Escavação bloqueada na reserva ambiental (${plot.name})."
            }
            if (!plot.owned && !plot.leased) {
                return "Compre ou arrende '${plot.name}' antes de movimentar solo nesta área."
            }
        }
    }
    for (b in state.buildings) {
        if (b.type.name == "CAMINHO_TERRA" || b.type.name == "PATIO") continue
        if (hypot(b.x - cellWx, b.z - cellWz) < 6.5) {
            return "Área muito próxima de construção existente."
        }
    }
    return null
}

fun computeEarthworkTipPosition(veh: OwnedVehicle, def: VehicleDef): Pair<Double, Double> {
    val rad = (veh.headingDeg * PI) / 180.0
    val swingRad = (((veh.swingAngleDeg ?: 0.0) + veh.headingDeg) * PI) / 180.0
    return when (def.bodyStyle) {
        VehicleBodyStyle.EXCAVATOR -> {
            val reach = (3.4 + (veh.armAngleDeg ?: 20.0) * 0.045 - (veh.boomAngleDeg ?: 28.0) * 0.025).coerceIn(2.6, 6.2)
            (veh.x + sin(swingRad) * reach) to (veh.z + cos(swingRad) * reach)
        }
        VehicleBodyStyle.BACKHOE -> {
            val reach = if ((veh.armAngleDeg ?: 18.0) > 25.0) -3.4 else 3.2
            (veh.x + sin(rad) * reach) to (veh.z + cos(rad) * reach)
        }
        VehicleBodyStyle.LOADER, VehicleBodyStyle.DOZER -> {
            (veh.x + sin(rad) * 3.1) to (veh.z + cos(rad) * 3.1)
        }
        VehicleBodyStyle.GRADER -> {
            (veh.x + sin(rad) * 0.6) to (veh.z + cos(rad) * 0.6)
        }
        VehicleBodyStyle.TRUCK -> {
            (veh.x - sin(rad) * 3.6) to (veh.z - cos(rad) * 3.6)
        }
        else -> (veh.x + sin(rad) * 2.5) to (veh.z + cos(rad) * 2.5)
    }
}

fun adjustEarthworkHydraulicInWorld(
    state: WorldState,
    boomDeltaDeg: Double = 0.0,
    armDeltaDeg: Double = 0.0,
    bucketDeltaDeg: Double = 0.0,
    swingDeltaDeg: Double = 0.0,
    bladeDeltaM: Double = 0.0,
    dumpBedDeltaDeg: Double = 0.0,
): WorldState {
    val activeUid = state.activeVehicleUid ?: return state
    val veh = state.vehicles.find { it.uid == activeUid } ?: return state
    val def = getVehicleDef(veh.defId)
    if (!isEarthmovingVehicle(def)) return state

    val nextBoom = ((veh.boomAngleDeg ?: 28.0) + boomDeltaDeg).coerceIn(-10.0, 68.0)
    val nextArm = ((veh.armAngleDeg ?: 20.0) + armDeltaDeg).coerceIn(-25.0, 75.0)
    val nextBucket = ((veh.bucketCurlDeg ?: 15.0) + bucketDeltaDeg).coerceIn(-35.0, 85.0)
    val nextSwing = (((veh.swingAngleDeg ?: 0.0) + swingDeltaDeg) % 360.0).let {
        if (it > 180.0) it - 360.0 else if (it < -180.0) it + 360.0 else it
    }
    val nextBlade = ((veh.bladeHeightM ?: 0.25) + bladeDeltaM).coerceIn(-0.35, 1.15)
    val nextBed = ((veh.dumpBedAngleDeg ?: 0.0) + dumpBedDeltaDeg).coerceIn(0.0, 48.0)

    return state.copy(
        vehicles = state.vehicles.map {
            if (it.uid == activeUid) {
                it.copy(
                    boomAngleDeg = nextBoom,
                    armAngleDeg = nextArm,
                    bucketCurlDeg = nextBucket,
                    swingAngleDeg = nextSwing,
                    bladeHeightM = nextBlade,
                    dumpBedAngleDeg = nextBed,
                    hydraulicWorkTimerSec = 0.55,
                )
            } else it
        },
    )
}

fun dumpEarthFromMachineInWorld(state: WorldState): WorldState {
    val prepared = ensureWaterAndFishWorldInitialized(state)
    val activeUid = prepared.activeVehicleUid ?: return prepared
    val veh = prepared.vehicles.find { it.uid == activeUid } ?: return prepared
    val def = getVehicleDef(veh.defId)
    if (!isEarthmovingVehicle(def)) {
        return prepared.copy(statusAlert = "${def.model} não transporta terra escavada.")
    }
    val carried = veh.carriedEarthM3 ?: 0.0
    if (carried <= 0.15) {
        return prepared.copy(statusAlert = "Caçamba/báscula de ${def.model} está vazia.")
    }

    // If excavator or loader is near a dump truck, transfer soil directly into dump truck!
    if (def.bodyStyle == VehicleBodyStyle.EXCAVATOR ||
        def.bodyStyle == VehicleBodyStyle.BACKHOE ||
        def.bodyStyle == VehicleBodyStyle.LOADER
    ) {
        val nearbyDump = prepared.vehicles.firstOrNull { other ->
            if (other.uid == veh.uid) return@firstOrNull false
            val oDef = getVehicleDef(other.defId)
            val oCap = getEarthCapacityM3(oDef)
            oDef.bodyStyle == VehicleBodyStyle.TRUCK &&
                isEarthmovingVehicle(oDef) &&
                ((other.carriedEarthM3 ?: 0.0) < oCap * 0.95) &&
                hypot(other.x - veh.x, other.z - veh.z) <= 12.5
        }
        if (nearbyDump != null) {
            val oDef = getVehicleDef(nearbyDump.defId)
            val oCap = getEarthCapacityM3(oDef)
            val oCur = nearbyDump.carriedEarthM3 ?: 0.0
            val move = min(carried, max(0.0, oCap - oCur))
            if (move > 0.1) {
                return prepared.copy(
                    vehicles = prepared.vehicles.map {
                        when (it.uid) {
                            veh.uid -> it.copy(
                                carriedEarthM3 = max(0.0, carried - move),
                                bucketCurlDeg = 55.0,
                                hydraulicWorkTimerSec = 0.85,
                            )
                            nearbyDump.uid -> it.copy(carriedEarthM3 = oCur + move)
                            else -> it
                        }
                    },
                    statusAlert = "${String.format(java.util.Locale.US, "%.1f", move)} m³ de terra carregados no ${oDef.model}!",
                )
            }
        }
    }

    // Otherwise dump onto ground at dump point to form spoil mound or dam berm
    val (tx, tz) = computeEarthworkTipPosition(veh, def)
    val gridSide = getGridSide(prepared)
    val cellSize = getWorldCellSizeM(prepared)
    val halfSpan = getWorldSpanM(prepared) * 0.5
    val centerCoords = worldToGridCoordsForState(prepared, tx, tz)
    val radiusCells = if (def.bodyStyle == VehicleBodyStyle.TRUCK) 2 else 1

    val nearPond = prepared.ponds.orEmpty().any { p ->
        val d = hypot(p.centerX - tx, p.centerZ - tz)
        d in (p.radiusM * 0.78)..(p.radiusM + 8.0)
    }

    val nextCells = prepared.cells.toMutableList()
    var modified = 0
    for (dz in -radiusCells..radiusCells) {
        for (dx in -radiusCells..radiusCells) {
            val gx = centerCoords.first + dx
            val gz = centerCoords.second + dz
            if (gx !in 0 until gridSide || gz !in 0 until gridSide) continue
            val idx = gz * gridSide + gx
            val c = nextCells[idx]
            val cwx = gx * cellSize - halfSpan + cellSize * 0.5
            val cwz = gz * cellSize - halfSpan + cellSize * 0.5
            if (canModifyEarthAtCell(prepared, c, cwx, cwz) != null) continue
            val falloff = 1.0 / (1.0 + hypot(dx.toDouble(), dz.toDouble()) * 0.75)
            val gainH = (carried * 0.08 * falloff).coerceIn(0.08, 0.85)
            nextCells[idx] = c.copy(
                height = min(14.0, c.height + gainH),
                spoilHeightM = (c.spoilHeightM ?: 0.0) + gainH,
                soilState = if (nearPond) SoilState.TALUDE_BARRAGEM else SoilState.ATERRO_SOLO,
                cropId = null,
                growthProgress = 0.0,
                soilTone = if (nearPond) "TALUDE_COMPACTADO" else "ATERRO_BARRO",
            )
            modified++
        }
    }

    if (modified == 0) {
        return prepared.copy(statusAlert = "Local bloqueado para despejar material escavado.")
    }

    val dumpMark = EarthworkMark(
        uid = "ewm_dump_${prepared.day}_${(prepared.earthworkMarks?.size ?: 0) + 1}",
        x = tx,
        z = tz,
        headingDeg = veh.headingDeg,
        radiusM = if (def.bodyStyle == VehicleBodyStyle.TRUCK) 2.8 else 1.8,
        kind = if (nearPond) "BERM" else "SPOIL",
        soilTone = if (nearPond) "TALUDE_COMPACTADO" else "ATERRO_BARRO",
        ageSec = 0.0,
    )

    val nextPonds = if (nearPond) {
        prepared.ponds.orEmpty().map { p ->
            if (hypot(p.centerX - tx, p.centerZ - tz) <= p.radiusM + 8.0) {
                p.copy(dikeIntegrityPct = min(100.0, p.dikeIntegrityPct + carried * 1.4))
            } else p
        }
    } else prepared.ponds

    return prepared.copy(
        cells = nextCells,
        ponds = nextPonds,
        vehicles = prepared.vehicles.map {
            if (it.uid == activeUid) {
                it.copy(
                    carriedEarthM3 = 0.0,
                    dumpBedAngleDeg = if (def.bodyStyle == VehicleBodyStyle.TRUCK) 42.0 else it.dumpBedAngleDeg,
                    bucketCurlDeg = if (def.bodyStyle != VehicleBodyStyle.TRUCK) 58.0 else it.bucketCurlDeg,
                    hydraulicWorkTimerSec = 1.0,
                )
            } else it
        },
        earthworkMarks = (prepared.earthworkMarks.orEmpty() + dumpMark).takeLast(240),
        statusAlert = if (nearPond) {
            "Material descarregado em talude/margem de açude (${String.format(java.util.Locale.US, "%.1f", carried)} m³)."
        } else {
            "Monte de terra formado (${String.format(java.util.Locale.US, "%.1f", carried)} m³)."
        },
    )
}

fun executeEarthworkActionInWorld(state: WorldState): WorldState {
    val prepared = ensureWaterAndFishWorldInitialized(state)
    val activeUid = prepared.activeVehicleUid
        ?: return prepared.copy(statusAlert = "Embarque em uma máquina de escavação ou terraplanagem.")
    val veh = prepared.vehicles.find { it.uid == activeUid } ?: return prepared
    val def = getVehicleDef(veh.defId)

    if (def.category == VehicleCategory.PERFURATRIZ) {
        return performDrillingRigSoundingInWorld(prepared)
    }
    if (!isEarthmovingVehicle(def)) {
        return prepared.copy(statusAlert = "${def.model} não possui implemento de escavação/terraplanagem.")
    }
    if (veh.engineRunning == false) {
        return prepared.copy(statusAlert = "Ligue o motor diesel de ${def.model} antes de operar o hidráulico.")
    }

    if (def.bodyStyle == VehicleBodyStyle.TRUCK) {
        return dumpEarthFromMachineInWorld(prepared)
    }

    val cap = getEarthCapacityM3(def)
    val carried = veh.carriedEarthM3 ?: 0.0
    if ((def.bodyStyle == VehicleBodyStyle.EXCAVATOR ||
            def.bodyStyle == VehicleBodyStyle.BACKHOE ||
            def.bodyStyle == VehicleBodyStyle.LOADER) &&
        carried >= cap * 0.90
    ) {
        return dumpEarthFromMachineInWorld(prepared)
    }

    val (tx, tz) = computeEarthworkTipPosition(veh, def)
    val gridSide = getGridSide(prepared)
    val cellSize = getWorldCellSizeM(prepared)
    val halfSpan = getWorldSpanM(prepared) * 0.5
    val centerCoords = worldToGridCoordsForState(prepared, tx, tz)
    val aq = findNearestAquiferZone(prepared, tx, tz)

    val nextCells = prepared.cells.toMutableList()
    var touched = 0
    var scoopedM3 = 0.0
    var hitWaterTable = false
    var blockReason: String? = null

    when (def.bodyStyle) {
        VehicleBodyStyle.EXCAVATOR, VehicleBodyStyle.BACKHOE -> {
            val radiusCells = if (def.bodyStyle == VehicleBodyStyle.EXCAVATOR) 1 else 1
            val biteDepth = if (def.bodyStyle == VehicleBodyStyle.EXCAVATOR) 0.58 else 0.38
            for (dz in -radiusCells..radiusCells) {
                for (dx in -radiusCells..radiusCells) {
                    val gx = centerCoords.first + dx
                    val gz = centerCoords.second + dz
                    if (gx !in 0 until gridSide || gz !in 0 until gridSide) continue
                    val idx = gz * gridSide + gx
                    val c = nextCells[idx]
                    val cwx = gx * cellSize - halfSpan + cellSize * 0.5
                    val cwz = gz * cellSize - halfSpan + cellSize * 0.5
                    val deny = canModifyEarthAtCell(prepared, c, cwx, cwz)
                    if (deny != null) {
                        blockReason = deny
                        continue
                    }
                    val radialWeight = if (dx == 0 && dz == 0) 1.0 else 0.55
                    val delta = biteDepth * radialWeight
                    val curExc = c.excavationDepthM ?: 0.0
                    val nextExc = (curExc + delta).coerceAtMost(6.5)
                    val actualDelta = nextExc - curExc
                    if (actualDelta <= 0.01) continue
                    val curSpoil = c.spoilHeightM ?: 0.0
                    val aqDepth = aq?.waterTableDepthM ?: 3.2
                    val seepWater = if (nextExc >= aqDepth && (aq?.remainingCapacityL ?: 0.0) > 500.0) {
                        hitWaterTable = true
                        min(nextExc - 0.15, (c.waterDepthM ?: 0.0) + 0.18)
                    } else c.waterDepthM

                    val tone = when {
                        (seepWater ?: 0.0) > 0.05 -> "LAMA_AGUA"
                        nextExc >= 1.2 -> "SUBSOLO_ARGILA"
                        else -> "TERRA_CAVADA"
                    }
                    nextCells[idx] = c.copy(
                        height = max(-6.5, c.height - actualDelta),
                        excavationDepthM = nextExc,
                        spoilHeightM = max(0.0, curSpoil - actualDelta * 0.5),
                        waterDepthM = seepWater,
                        waterTableDepthM = aqDepth,
                        aquiferAvailability = aq?.availability?.name,
                        soilState = if ((seepWater ?: 0.0) > 0.45) SoilState.ACUDE_AGUA else SoilState.ESCAVADO,
                        soilTone = tone,
                        cropId = null,
                        growthProgress = 0.0,
                    )
                    scoopedM3 += actualDelta * 1.45
                    touched++
                }
            }
            if (touched == 0) {
                return prepared.copy(statusAlert = blockReason ?: "Área inválida ou profundidade máxima atingida aqui.")
            }
            val nextCarried = min(cap, carried + max(0.6, scoopedM3))
            val mark = EarthworkMark(
                uid = "ewm_bkt_${prepared.day}_${(prepared.earthworkMarks?.size ?: 0) + 1}",
                x = tx,
                z = tz,
                headingDeg = veh.headingDeg,
                radiusM = if (def.bodyStyle == VehicleBodyStyle.EXCAVATOR) 1.9 else 1.3,
                kind = "BUCKET",
                soilTone = if (hitWaterTable) "LAMA_AGUA" else "SUBSOLO_ARGILA",
                ageSec = 0.0,
            )
            val waterMsg = if (hitWaterTable) " • Lençol freático atingido (${aq?.availability?.label ?: "Água"})!" else ""
            return prepared.copy(
                cells = nextCells,
                vehicles = prepared.vehicles.map {
                    if (it.uid == activeUid) {
                        it.copy(
                            carriedEarthM3 = nextCarried,
                            boomAngleDeg = 14.0,
                            armAngleDeg = 42.0,
                            bucketCurlDeg = -22.0,
                            hydraulicWorkTimerSec = 0.9,
                            fuelL = max(0.0, it.fuelL - 1.8),
                        )
                    } else it
                },
                earthworkMarks = (prepared.earthworkMarks.orEmpty() + mark).takeLast(240),
                statusAlert = "Escavado +${String.format(java.util.Locale.US, "%.1f", min(cap - carried, scoopedM3))} m³ (Caçamba ${String.format(java.util.Locale.US, "%.1f", nextCarried)}/${cap.roundToInt()} m³)$waterMsg",
            )
        }

        VehicleBodyStyle.LOADER -> {
            // Wheel loader collects loose spoil mounds or topsoil into bucket
            for (dz in -1..1) {
                for (dx in -1..1) {
                    val gx = centerCoords.first + dx
                    val gz = centerCoords.second + dz
                    if (gx !in 0 until gridSide || gz !in 0 until gridSide) continue
                    val idx = gz * gridSide + gx
                    val c = nextCells[idx]
                    val cwx = gx * cellSize - halfSpan + cellSize * 0.5
                    val cwz = gz * cellSize - halfSpan + cellSize * 0.5
                    val deny = canModifyEarthAtCell(prepared, c, cwx, cwz)
                    if (deny != null) {
                        blockReason = deny
                        continue
                    }
                    val availSpoil = c.spoilHeightM ?: 0.0
                    val take = if (availSpoil > 0.05) min(availSpoil, 0.42) else 0.16
                    nextCells[idx] = c.copy(
                        height = c.height - take,
                        spoilHeightM = max(0.0, availSpoil - take),
                        excavationDepthM = if (availSpoil <= 0.05) (c.excavationDepthM ?: 0.0) + take else c.excavationDepthM,
                        soilState = if (availSpoil > take + 0.05) c.soilState else SoilState.ESCAVADO,
                        soilTone = "TERRA_CAVADA",
                    )
                    scoopedM3 += take * 1.8
                    touched++
                }
            }
            if (touched == 0) {
                return prepared.copy(statusAlert = blockReason ?: "Aproxime a pá-carregadeira de um monte ou solo livre.")
            }
            val nextCarried = min(cap, carried + max(0.8, scoopedM3))
            return prepared.copy(
                cells = nextCells,
                vehicles = prepared.vehicles.map {
                    if (it.uid == activeUid) {
                        it.copy(
                            carriedEarthM3 = nextCarried,
                            boomAngleDeg = 32.0,
                            bucketCurlDeg = -18.0,
                            hydraulicWorkTimerSec = 0.75,
                            fuelL = max(0.0, it.fuelL - 1.2),
                        )
                    } else it
                },
                statusAlert = "Pá carregada (${String.format(java.util.Locale.US, "%.1f", nextCarried)}/${cap.roundToInt()} m³). Aproxime-se de caminhão basculante ou descarregue.",
            )
        }

        VehicleBodyStyle.DOZER -> {
            // Crawler Dozer pushes dirt ahead to build dikes/berms & clear/grade
            val rad = (veh.headingDeg * PI) / 180.0
            val aheadX = tx + sin(rad) * 3.8
            val aheadZ = tz + cos(rad) * 3.8
            val aheadCoords = worldToGridCoordsForState(prepared, aheadX, aheadZ)

            var pushed = 0.0
            for (dz in -1..1) {
                for (dx in -1..1) {
                    val gx = centerCoords.first + dx
                    val gz = centerCoords.second + dz
                    if (gx !in 0 until gridSide || gz !in 0 until gridSide) continue
                    val idx = gz * gridSide + gx
                    val c = nextCells[idx]
                    val cwx = gx * cellSize - halfSpan + cellSize * 0.5
                    val cwz = gz * cellSize - halfSpan + cellSize * 0.5
                    if (canModifyEarthAtCell(prepared, c, cwx, cwz) != null) continue
                    val cut = 0.22
                    nextCells[idx] = c.copy(
                        height = c.height - cut,
                        excavationDepthM = (c.excavationDepthM ?: 0.0) + cut,
                        spoilHeightM = max(0.0, (c.spoilHeightM ?: 0.0) - cut),
                        soilState = SoilState.ESCAVADO,
                        soilTone = "TERRA_CAVADA",
                        cropId = null,
                    )
                    pushed += cut
                    touched++
                }
            }
            if (touched > 0) {
                val pushAvg = (pushed / touched.toDouble()).coerceIn(0.12, 0.45)
                val nearPond = prepared.ponds.orEmpty().any { hypot(it.centerX - aheadX, it.centerZ - aheadZ) <= it.radiusM + 9.0 }
                for (dz in -1..1) {
                    for (dx in -1..1) {
                        val gx = aheadCoords.first + dx
                        val gz = aheadCoords.second + dz
                        if (gx !in 0 until gridSide || gz !in 0 until gridSide) continue
                        val idx = gz * gridSide + gx
                        val c = nextCells[idx]
                        val cwx = gx * cellSize - halfSpan + cellSize * 0.5
                        val cwz = gz * cellSize - halfSpan + cellSize * 0.5
                        if (canModifyEarthAtCell(prepared, c, cwx, cwz) != null) continue
                        nextCells[idx] = c.copy(
                            height = c.height + pushAvg,
                            spoilHeightM = (c.spoilHeightM ?: 0.0) + pushAvg,
                            soilState = if (nearPond) SoilState.TALUDE_BARRAGEM else SoilState.ATERRO_SOLO,
                            soilTone = if (nearPond) "TALUDE_COMPACTADO" else "ATERRO_BARRO",
                        )
                    }
                }
            }
            if (touched == 0) {
                return prepared.copy(statusAlert = "Área bloqueada para lâmina do trator de esteira.")
            }
            val mark = EarthworkMark(
                uid = "ewm_dz_${prepared.day}_${(prepared.earthworkMarks?.size ?: 0) + 1}",
                x = tx,
                z = tz,
                headingDeg = veh.headingDeg,
                radiusM = 2.4,
                kind = "BLADE",
                soilTone = "TERRA_CAVADA",
                ageSec = 0.0,
            )
            return prepared.copy(
                cells = nextCells,
                vehicles = prepared.vehicles.map {
                    if (it.uid == activeUid) it.copy(bladeHeightM = -0.15, hydraulicWorkTimerSec = 0.85, fuelL = max(0.0, it.fuelL - 1.5)) else it
                },
                earthworkMarks = (prepared.earthworkMarks.orEmpty() + mark).takeLast(240),
                statusAlert = "Trator de esteira empurrou terra e reforçou leira/talude à frente.",
            )
        }

        VehicleBodyStyle.GRADER -> {
            // Motor Grader levels & restores uneven ground smoothly to local mean grade
            var sumH = 0.0
            var count = 0
            for (dz in -2..2) {
                for (dx in -2..2) {
                    val gx = centerCoords.first + dx
                    val gz = centerCoords.second + dz
                    if (gx !in 0 until gridSide || gz !in 0 until gridSide) continue
                    val idx = gz * gridSide + gx
                    val c = nextCells[idx]
                    sumH += c.height
                    count++
                }
            }
            if (count == 0) return prepared
            val targetH = sumH / count.toDouble()
            for (dz in -2..2) {
                for (dx in -2..2) {
                    val gx = centerCoords.first + dx
                    val gz = centerCoords.second + dz
                    if (gx !in 0 until gridSide || gz !in 0 until gridSide) continue
                    val idx = gz * gridSide + gx
                    val c = nextCells[idx]
                    val cwx = gx * cellSize - halfSpan + cellSize * 0.5
                    val cwz = gz * cellSize - halfSpan + cellSize * 0.5
                    if (canModifyEarthAtCell(prepared, c, cwx, cwz) != null) continue
                    val smoothedH = c.height + (targetH - c.height) * 0.65
                    val nextSpoil = max(0.0, (c.spoilHeightM ?: 0.0) * 0.35)
                    val nextExc = max(0.0, (c.excavationDepthM ?: 0.0) * 0.45)
                    nextCells[idx] = c.copy(
                        height = smoothedH,
                        spoilHeightM = nextSpoil,
                        excavationDepthM = nextExc,
                        soilState = if (nextExc < 0.25 && nextSpoil < 0.2) SoilState.ARADO else c.soilState,
                        soilTone = "NIVELADO",
                        compaction = max(0.1, c.compaction * 0.8),
                    )
                    touched++
                }
            }
            if (touched == 0) {
                return prepared.copy(statusAlert = "Sem solo livre ao redor para nivelamento.")
            }
            val mark = EarthworkMark(
                uid = "ewm_gr_${prepared.day}_${(prepared.earthworkMarks?.size ?: 0) + 1}",
                x = tx,
                z = tz,
                headingDeg = veh.headingDeg,
                radiusM = 2.8,
                kind = "BLADE",
                soilTone = "NIVELADO",
                ageSec = 0.0,
            )
            return prepared.copy(
                cells = nextCells,
                vehicles = prepared.vehicles.map {
                    if (it.uid == activeUid) it.copy(bladeHeightM = 0.0, hydraulicWorkTimerSec = 0.85, fuelL = max(0.0, it.fuelL - 1.1)) else it
                },
                earthworkMarks = (prepared.earthworkMarks.orEmpty() + mark).takeLast(240),
                statusAlert = "Motoniveladora nivelou $touched células de terreno em torno do eixo.",
            )
        }

        else -> return prepared
    }
}

fun stepEarthmovingContinuous(
    state: WorldState,
    dtSec: Double,
): WorldState {
    val activeUid = state.activeVehicleUid
    var anyChanged = false
    val nextVehicles = state.vehicles.map { v ->
        val timer = v.hydraulicWorkTimerSec ?: 0.0
        val bed = v.dumpBedAngleDeg ?: 0.0
        if (timer > 0.0 || (v.uid != activeUid && bed > 0.5) || (timer <= 0.0 && bed > 0.5)) {
            anyChanged = true
            val nextTimer = max(0.0, timer - dtSec)
            val nextBed = if (nextTimer <= 0.0 && bed > 0.0) max(0.0, bed - 28.0 * dtSec) else bed
            v.copy(hydraulicWorkTimerSec = nextTimer, dumpBedAngleDeg = nextBed)
        } else v
    }

    var nextMarks = state.earthworkMarks
    if (activeUid != null) {
        val activeVeh = nextVehicles.find { it.uid == activeUid }
        if (activeVeh != null && abs(activeVeh.speedMs) > 0.9) {
            val def = getVehicleDef(activeVeh.defId)
            val isTracked = def.wheelLayout == com.example.domain.WheelLayout.CRAWLER_TRACKS ||
                def.bodyStyle == VehicleBodyStyle.DOZER ||
                def.bodyStyle == VehicleBodyStyle.EXCAVATOR ||
                def.bodyStyle == VehicleBodyStyle.DRILLING_RIG
            val curMarks = state.earthworkMarks.orEmpty()
            val lastMark = curMarks.lastOrNull()
            val distSinceLast = if (lastMark != null) hypot(lastMark.x - activeVeh.x, lastMark.z - activeVeh.z) else 999.0
            if (distSinceLast >= 3.2 && (isEarthmovingVehicle(def) || abs(activeVeh.speedMs) > 1.2)) {
                val cellIdx = getCellIndexForState(state, activeVeh.x, activeVeh.z)
                val soil = cellIdx?.let { state.cells.getOrNull(it)?.soilState }
                if (soil != SoilState.ESTRADA_ASFALTO && soil != SoilState.AGUA_RIO) {
                    val trackKind = if (isTracked) "TRACK" else "TIRE"
                    val newMark = EarthworkMark(
                        uid = "ewm_trk_${state.day}_${curMarks.size + 1}_${(activeVeh.x * 10).roundToInt()}",
                        x = activeVeh.x,
                        z = activeVeh.z,
                        headingDeg = activeVeh.headingDeg,
                        radiusM = if (isTracked) 1.35 else 1.05,
                        kind = trackKind,
                        soilTone = if (soil == SoilState.ESCAVADO) "ARGILA_UMIDA" else "TERRA_MARCADA",
                        ageSec = 0.0,
                    )
                    nextMarks = (curMarks + newMark).takeLast(240)
                    anyChanged = true
                }
            }
        }
    }

    return if (!anyChanged) state else state.copy(vehicles = nextVehicles, earthworkMarks = nextMarks)
}
