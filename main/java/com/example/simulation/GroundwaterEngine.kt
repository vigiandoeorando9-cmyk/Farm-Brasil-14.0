package com.example.simulation

import com.example.domain.AquiferAvailabilityTier
import com.example.domain.BrazilianMapDef
import com.example.domain.LedgerEntry
import com.example.domain.ManualTool
import com.example.domain.PlotZoneType
import com.example.domain.PondSizeTier
import com.example.domain.PondStructure
import com.example.domain.ProspectingScanResult
import com.example.domain.RegionalAquiferZone
import com.example.domain.SoilState
import com.example.domain.TerrainCell
import com.example.domain.VehicleCategory
import com.example.domain.WaterBusinessState
import com.example.domain.WaterProspectPoint
import com.example.domain.WorldState
import com.example.domain.getMapById
import com.example.domain.getVehicleDef
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

const val WATER_PROBE_PRICE_BRL = 1850.0
const val DRILL_SOUNDING_COST_BRL = 320.0

fun generateRegionalAquiferGrid(
    mapDef: BrazilianMapDef,
    worldSpanM: Double,
    cells: List<TerrainCell>,
): List<RegionalAquiferZone> {
    val gridSide = kotlin.math.sqrt(cells.size.toDouble()).roundToInt().coerceAtLeast(16)
    val halfSpan = worldSpanM * 0.5
    val cellSize = worldSpanM / gridSide.toDouble()
    val sectorsPerAxis = 6
    val sectorSize = worldSpanM / sectorsPerAxis.toDouble()
    val seedPhase = (mapDef.seed % 199) * 0.17
    val biomeFactor = when {
        mapDef.id.contains("am") || mapDef.id.contains("pa") || mapDef.id.contains("pantanal") -> 1.28
        mapDef.id.contains("ba") || mapDef.id.contains("ce") || mapDef.id.contains("sert") -> 0.72
        mapDef.id.contains("pr") || mapDef.id.contains("rs") || mapDef.id.contains("sc") -> 1.15
        else -> 1.0
    }

    val zones = ArrayList<RegionalAquiferZone>(sectorsPerAxis * sectorsPerAxis)
    for (sz in 0 until sectorsPerAxis) {
        for (sx in 0 until sectorsPerAxis) {
            val cx = -halfSpan + (sx + 0.5) * sectorSize + sin(sx * 1.9 + sz * 2.3 + seedPhase) * (sectorSize * 0.12)
            val cz = -halfSpan + (sz + 0.5) * sectorSize + cos(sx * 2.1 - sz * 1.7 + seedPhase) * (sectorSize * 0.12)

            val gx = ((cx + halfSpan) / cellSize).toInt().coerceIn(0, gridSide - 1)
            val gz = ((cz + halfSpan) / cellSize).toInt().coerceIn(0, gridSide - 1)
            val sampleCell = cells.getOrNull(gz * gridSide + gx)
            val terrainH = sampleCell?.height ?: 0.0

            val riverZ = riverCenterZ(mapDef, cx)
            val riverDist = if (mapDef.hasRiver) abs(cz - riverZ) else 180.0
            val valleyBonus = ((1.2 - terrainH).coerceIn(-0.4, 0.8)) * 0.28
            val riverBonus = (1.0 - (riverDist / 160.0).coerceIn(0.0, 1.0)) * 0.44
            val waveNoise = (sin(cx * 0.017 + seedPhase) * cos(cz * 0.019 - seedPhase) + 1.0) * 0.25

            // Guarantee high/very high pockets on every map so exploration always pays off
            val specialSpring = ((sx + sz * 3 + mapDef.seed) % 7 == 0)
            val score = ((0.22 + riverBonus + valleyBonus + waveNoise + if (specialSpring) 0.26 else 0.0) * biomeFactor)
                .coerceIn(0.08, 1.0)

            val tier = when {
                score >= 0.78 -> AquiferAvailabilityTier.MUITO_ALTA
                score >= 0.56 -> AquiferAvailabilityTier.ALTA
                score >= 0.34 -> AquiferAvailabilityTier.MEDIA
                else -> AquiferAvailabilityTier.BAIXA
            }

            // Shallow near rivers/valleys (1.3m - 2.6m), deeper on dry ridges (3.5m - 5.2m)
            val baseDepthM = when (tier) {
                AquiferAvailabilityTier.MUITO_ALTA -> 1.25 + (1.0 - score) * 1.1
                AquiferAvailabilityTier.ALTA -> 1.75 + (1.0 - score) * 1.3
                AquiferAvailabilityTier.MEDIA -> 2.45 + (1.0 - score) * 1.5
                AquiferAvailabilityTier.BAIXA -> 3.60 + (1.0 - score) * 1.6
            }.coerceIn(1.15, 5.4)

            val totalCapL = when (tier) {
                AquiferAvailabilityTier.MUITO_ALTA -> 420_000.0 + (score * 220_000.0)
                AquiferAvailabilityTier.ALTA -> 240_000.0 + (score * 120_000.0)
                AquiferAvailabilityTier.MEDIA -> 125_000.0 + (score * 55_000.0)
                AquiferAvailabilityTier.BAIXA -> 52_000.0 + (score * 24_000.0)
            }

            val rechargeLPerDay = when (tier) {
                AquiferAvailabilityTier.MUITO_ALTA -> 34_000.0 + score * 12_000.0
                AquiferAvailabilityTier.ALTA -> 18_500.0 + score * 6_500.0
                AquiferAvailabilityTier.MEDIA -> 8_500.0 + score * 3_500.0
                AquiferAvailabilityTier.BAIXA -> 2_600.0 + score * 1_400.0
            }

            val qualityPct = when (tier) {
                AquiferAvailabilityTier.MUITO_ALTA -> (88.0 + score * 9.0).coerceIn(72.0, 99.0)
                AquiferAvailabilityTier.ALTA -> (80.0 + score * 12.0).coerceIn(68.0, 96.0)
                AquiferAvailabilityTier.MEDIA -> (70.0 + score * 14.0).coerceIn(58.0, 90.0)
                AquiferAvailabilityTier.BAIXA -> (56.0 + score * 18.0).coerceIn(48.0, 80.0)
            }

            zones.add(
                RegionalAquiferZone(
                    id = "aq_${sx}_${sz}",
                    centerX = cx,
                    centerZ = cz,
                    radiusM = sectorSize * 0.72,
                    waterTableDepthM = (baseDepthM * 100.0).roundToInt() / 100.0,
                    availability = tier,
                    totalCapacityL = totalCapL.roundToInt().toDouble(),
                    remainingCapacityL = totalCapL.roundToInt().toDouble(),
                    rechargePerDayL = rechargeLPerDay.roundToInt().toDouble(),
                    qualityPct = qualityPct.roundToInt().toDouble(),
                )
            )
        }
    }
    return zones
}

fun findNearestAquiferZone(
    state: WorldState,
    wx: Double,
    wz: Double,
): RegionalAquiferZone? {
    val grid = state.aquiferGrid
    if (grid.isNullOrEmpty()) return null
    return grid.minByOrNull { hypot(it.centerX - wx, it.centerZ - wz) }
}

fun buildStarterPondForReadyProperty(
    state: WorldState,
    aquiferZones: List<RegionalAquiferZone>,
): Pair<List<PondStructure>, List<TerrainCell>>? {
    val readyPlot = state.plots.firstOrNull { it.zoneType == PlotZoneType.PROPRIEDADE_ACUDE_PRONTO }
        ?: return null
    val cx = readyPlot.centerX ?: return null
    val cz = readyPlot.centerZ ?: return null
    val nearestAq = aquiferZones.minByOrNull { hypot(it.centerX - cx, it.centerZ - cz) }

    val gridSide = getGridSide(state)
    val cellSize = getWorldCellSizeM(state)
    val halfSpan = getWorldSpanM(state) * 0.5
    val pondRadius = PondSizeTier.MEDIO.defaultRadiusM
    val pondDepth = 2.65
    val maxVol = PondSizeTier.MEDIO.defaultCapacityL

    val nextCells = state.cells.toMutableList()
    for (gz in 0 until gridSide) {
        for (gx in 0 until gridSide) {
            val idx = gz * gridSide + gx
            val c = nextCells[idx]
            val wx = gx * cellSize - halfSpan + cellSize * 0.5
            val wz = gz * cellSize - halfSpan + cellSize * 0.5
            val d = hypot(wx - cx, wz - cz)
            if (d <= pondRadius) {
                val depthFrac = (1.0 - (d / pondRadius) * 0.55).coerceIn(0.35, 1.0)
                val targetDepth = pondDepth * depthFrac
                nextCells[idx] = c.copy(
                    height = c.height - targetDepth,
                    excavationDepthM = targetDepth,
                    waterDepthM = (targetDepth - 0.35).coerceAtLeast(0.45),
                    soilState = SoilState.ACUDE_AGUA,
                    cropId = null,
                    soilTone = "ARGILA_UMIDA",
                    waterTableDepthM = nearestAq?.waterTableDepthM ?: 1.6,
                    aquiferAvailability = nearestAq?.availability?.name,
                )
            } else if (d <= pondRadius + cellSize * 1.4) {
                nextCells[idx] = c.copy(
                    height = c.height + 0.48,
                    spoilHeightM = 0.48,
                    soilState = SoilState.TALUDE_BARRAGEM,
                    cropId = null,
                    soilTone = "TALUDE_COMPACTADO",
                )
            }
        }
    }

    val pond = PondStructure(
        uid = "pond_ready_${readyPlot.id}",
        name = "Açude Santa Helena (Pronto)",
        sizeTier = PondSizeTier.MEDIO,
        centerX = cx,
        centerZ = cz,
        radiusM = pondRadius,
        depthM = pondDepth,
        waterLevelM = 2.05,
        waterVolumeL = maxVol * 0.78,
        maxVolumeL = maxVol,
        waterQualityPct = nearestAq?.qualityPct ?: 88.0,
        dikeIntegrityPct = 96.0,
        hasPump = true,
        hasPiping = true,
        pumpActive = false,
        piscicultureEnabled = true,
        linkedAquiferId = nearestAq?.id,
        dissolvedO2MgL = 6.4,
        waterPh = 7.1,
        turbidityPct = 14.0,
        temperatureC = 25.5,
        fishFeedStockKg = 180.0,
        createdDay = state.day,
    )
    return listOf(pond) to nextCells
}

fun ensureWaterAndFishWorldInitialized(state: WorldState): WorldState {
    var updated = state
    if (updated.aquiferGrid.isNullOrEmpty()) {
        val mapDef = getMapById(updated.mapId)
        val generated = generateRegionalAquiferGrid(mapDef, getWorldSpanM(updated), updated.cells)
        updated = updated.copy(aquiferGrid = generated)
    }
    if (updated.waterBusiness == null) {
        updated = updated.copy(waterBusiness = WaterBusinessState(reservoirMaxL = 120_000.0))
    }
    if (updated.ponds == null) {
        val ready = buildStarterPondForReadyProperty(updated, updated.aquiferGrid.orEmpty())
        updated = if (ready != null) {
            updated.copy(ponds = ready.first, cells = ready.second)
        } else {
            updated.copy(ponds = emptyList())
        }
    }
    return updated
}

fun evaluateProspectingAt(
    state: WorldState,
    wx: Double,
    wz: Double,
): ProspectingScanResult {
    val aquifers = state.aquiferGrid
    if (aquifers.isNullOrEmpty()) {
        return ProspectingScanResult(
            x = wx,
            z = wz,
            signalStrength = 0.25,
            estimatedDepthM = 3.2,
            availability = AquiferAvailabilityTier.MEDIA,
            qualityPct = 74.0,
            estimatedRechargeLPerDay = 8500.0,
            audioBeepIntervalMs = 680,
            summaryLabel = "Lençol moderado (~3.2 m)",
        )
    }

    var weightSum = 0.0
    var depthAcc = 0.0
    var qualAcc = 0.0
    var rechAcc = 0.0
    var capRatioAcc = 0.0
    var tierScoreAcc = 0.0

    for (aq in aquifers) {
        val d = hypot(aq.centerX - wx, aq.centerZ - wz)
        val w = 1.0 / (1.0 + (d / 75.0) * (d / 75.0))
        weightSum += w
        depthAcc += aq.waterTableDepthM * w
        qualAcc += aq.qualityPct * w
        rechAcc += aq.rechargePerDayL * w
        val capRatio = if (aq.totalCapacityL > 1.0) (aq.remainingCapacityL / aq.totalCapacityL).coerceIn(0.0, 1.0) else 0.0
        capRatioAcc += capRatio * w
        val tierVal = when (aq.availability) {
            AquiferAvailabilityTier.BAIXA -> 0.22
            AquiferAvailabilityTier.MEDIA -> 0.50
            AquiferAvailabilityTier.ALTA -> 0.76
            AquiferAvailabilityTier.MUITO_ALTA -> 0.96
        }
        tierScoreAcc += tierVal * w
    }

    val norm = max(0.0001, weightSum)
    val cIdx = getCellIndexForState(state, wx, wz)
    val cell = cIdx?.let { state.cells.getOrNull(it) }
    val digBonus = min(0.35, (cell?.excavationDepthM ?: 0.0) * 0.14)
    val rawDepth = (depthAcc / norm).coerceIn(1.1, 5.5)
    val effectiveDepth = (rawDepth - (cell?.excavationDepthM ?: 0.0)).coerceIn(0.0, 5.5)
    val baseScore = ((tierScoreAcc / norm) * (0.45 + 0.55 * (capRatioAcc / norm)) + digBonus * 0.18)
        .coerceIn(0.05, 1.0)

    val resolvedTier = when {
        baseScore >= 0.76 -> AquiferAvailabilityTier.MUITO_ALTA
        baseScore >= 0.54 -> AquiferAvailabilityTier.ALTA
        baseScore >= 0.33 -> AquiferAvailabilityTier.MEDIA
        else -> AquiferAvailabilityTier.BAIXA
    }

    val quality = (qualAcc / norm).coerceIn(45.0, 99.0)
    val recharge = (rechAcc / norm).coerceIn(1800.0, 48000.0)
    val beepInterval = (920.0 - baseScore * 680.0).roundToInt().coerceIn(220, 950)

    return ProspectingScanResult(
        x = wx,
        z = wz,
        signalStrength = baseScore,
        estimatedDepthM = (effectiveDepth * 10.0).roundToInt() / 10.0,
        availability = resolvedTier,
        qualityPct = quality.roundToInt().toDouble(),
        estimatedRechargeLPerDay = recharge.roundToInt().toDouble(),
        audioBeepIntervalMs = beepInterval,
        summaryLabel = "${resolvedTier.label} • Lençol a ~${String.format(Locale.US, "%.1f", effectiveDepth)}m (${quality.roundToInt()}% pureza)",
    )
}

fun buyWaterProspectingToolInWorld(state: WorldState): WorldState {
    if (state.hasWaterProbeTool == true) {
        return state.copy(
            player = state.player.copy(tool = ManualTool.SONDA_AGUA),
            statusAlert = "Sonda Geofísica Portátil já adquirida e equipada na mão!",
        )
    }
    if (state.moneyBrl < WATER_PROBE_PRICE_BRL) {
        return state.copy(
            statusAlert = "Saldo insuficiente para comprar a Sonda Geofísica (${formatBrl(WATER_PROBE_PRICE_BRL)})."
        )
    }
    return ensureWaterAndFishWorldInitialized(state).copy(
        moneyBrl = state.moneyBrl - WATER_PROBE_PRICE_BRL,
        hasWaterProbeTool = true,
        player = state.player.copy(tool = ManualTool.SONDA_AGUA),
        ledger = state.ledger + LedgerEntry(state.day, "Compra: Sonda Portátil de Prospecção de Água", -WATER_PROBE_PRICE_BRL),
        statusAlert = "Sonda Portátil comprada e equipada! Caminhe pelo terreno e toque em SONDAR/MARCAR.",
    )
}

fun markWaterProspectPointInWorld(
    state: WorldState,
    customX: Double? = null,
    customZ: Double? = null,
    method: String = "SONDA_MANUAL",
): WorldState {
    val prepared = ensureWaterAndFishWorldInitialized(state)
    if (method == "SONDA_MANUAL" && prepared.hasWaterProbeTool != true && prepared.player.tool != ManualTool.SONDA_AGUA) {
        return prepared.copy(
            statusAlert = "Adquira e selecione a Sonda de Prospecção de Água antes de mapear pontos."
        )
    }
    val activeVeh = prepared.activeVehicleUid?.let { uid -> prepared.vehicles.find { it.uid == uid } }
    val px = customX ?: activeVeh?.x ?: prepared.player.x
    val pz = customZ ?: activeVeh?.z ?: prepared.player.z
    val scan = evaluateProspectingAt(prepared, px, pz)
    val existing = prepared.waterProspectPoints.orEmpty()
    val duplicate = existing.firstOrNull { hypot(it.x - px, it.z - pz) < 10.0 }
    val aq = findNearestAquiferZone(prepared, px, pz)

    val point = WaterProspectPoint(
        uid = duplicate?.uid ?: "wp_${prepared.day}_${existing.size + 1}_${abs((px * 31 + pz * 17).roundToInt())}",
        label = if (method == "PERFURATRIZ") {
            "Sondagem Profunda #${existing.size + 1} (${scan.availability.label})"
        } else {
            "Ponto Hídrico #${existing.size + 1} (${scan.availability.label})"
        },
        x = px,
        z = pz,
        estimatedDepthM = scan.estimatedDepthM,
        availability = scan.availability,
        estimatedCapacityL = aq?.remainingCapacityL ?: 120_000.0,
        waterQualityPct = scan.qualityPct,
        rechargeRateLPerDay = scan.estimatedRechargeLPerDay,
        discoveredDay = prepared.day,
        method = method,
    )

    val nextPoints = if (duplicate != null) {
        existing.map { if (it.uid == duplicate.uid) point else it }
    } else {
        (existing + point).takeLast(64)
    }

    return prepared.copy(
        waterProspectPoints = nextPoints,
        player = if (prepared.activeVehicleUid == null) prepared.player.copy(interactionAnimTimerSec = 0.65) else prepared.player,
        statusAlert = "Ponto marcado no mapa: ${scan.availability.label} • Lençol ~${scan.estimatedDepthM}m • Recarga ${(scan.estimatedRechargeLPerDay / 1000.0).roundToInt()} mil L/dia.",
    )
}

fun performDrillingRigSoundingInWorld(state: WorldState): WorldState {
    val prepared = ensureWaterAndFishWorldInitialized(state)
    val activeUid = prepared.activeVehicleUid
        ?: return prepared.copy(statusAlert = "Embarque em uma Perfuratriz ou Sonda Geofísica para executar sondagem.")
    val veh = prepared.vehicles.find { it.uid == activeUid } ?: return prepared
    val def = getVehicleDef(veh.defId)
    if (def.category != VehicleCategory.PERFURATRIZ) {
        return prepared.copy(statusAlert = "${def.model} não é uma perfuratriz de sondagem.")
    }
    if (veh.engineRunning == false) {
        return prepared.copy(statusAlert = "Ligue o motor diesel da ${def.model} antes de iniciar a perfuração.")
    }
    if (prepared.moneyBrl < DRILL_SOUNDING_COST_BRL) {
        return prepared.copy(statusAlert = "Saldo insuficiente para operação de sondagem (${formatBrl(DRILL_SOUNDING_COST_BRL)}).")
    }

    val scan = evaluateProspectingAt(prepared, veh.x, veh.z)
    val rad = (veh.headingDeg * PI) / 180.0
    val drillX = veh.x + sin(rad) * 2.6
    val drillZ = veh.z + cos(rad) * 2.6
    val mark = com.example.domain.EarthworkMark(
        uid = "ewm_drill_${prepared.day}_${(prepared.earthworkMarks?.size ?: 0) + 1}",
        x = drillX,
        z = drillZ,
        headingDeg = veh.headingDeg,
        radiusM = 1.6,
        kind = "DRILL_BORE",
        soilTone = "ARGILA_UMIDA",
        ageSec = 0.0,
    )

    val updated = markWaterProspectPointInWorld(
        state = prepared.copy(
            moneyBrl = prepared.moneyBrl - DRILL_SOUNDING_COST_BRL,
            vehicles = prepared.vehicles.map {
                if (it.uid == veh.uid) {
                    it.copy(
                        drillDepthM = min(55.0, (it.drillDepthM ?: 0.0) + 12.5),
                        hydraulicBoomDeg = 0.0,
                        hydraulicWorkTimerSec = 1.8,
                        fuelL = max(0.0, it.fuelL - 4.5),
                    )
                } else it
            },
            earthworkMarks = (prepared.earthworkMarks.orEmpty() + mark).takeLast(240),
            ledger = prepared.ledger + LedgerEntry(prepared.day, "Sondagem Geofísica (${def.model})", -DRILL_SOUNDING_COST_BRL),
        ),
        customX = drillX,
        customZ = drillZ,
        method = "PERFURATRIZ",
    )

    // Bonus recharge boost on mapped borehole from localized fracture stimulation
    val nextAq = updated.aquiferGrid.orEmpty().map { aq ->
        if (hypot(aq.centerX - drillX, aq.centerZ - drillZ) <= aq.radiusM) {
            aq.copy(rechargePerDayL = (aq.rechargePerDayL * 1.06).roundToInt().toDouble())
        } else aq
    }
    return updated.copy(
        aquiferGrid = nextAq,
        statusAlert = "Furo técnico concluído (${def.model}): ${scan.availability.label} a ${scan.estimatedDepthM}m (${scan.qualityPct.roundToInt()}% qualidade).",
    )
}
