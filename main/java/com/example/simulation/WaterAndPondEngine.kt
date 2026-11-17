package com.example.simulation

import com.example.domain.BuildingType
import com.example.domain.LedgerEntry
import com.example.domain.PlacedBuilding
import com.example.domain.PlotZoneType
import com.example.domain.PondSizeTier
import com.example.domain.PondStructure
import com.example.domain.SoilState
import com.example.domain.TerrainCell
import com.example.domain.WaterBusinessState
import com.example.domain.WorldState
import com.example.domain.WEATHER_META
import com.example.domain.getVehicleDef
import com.example.domain.getWaterTankCapacityL
import com.example.domain.isWaterTankerVehicle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

const val PUMP_PIPING_COST_BRL = 8900.0
const val DIKE_REINFORCEMENT_COST_BRL = 3200.0
const val RESERVOIR_UPGRADE_COST_BRL = 16500.0
const val WATER_SALE_PRICE_PER_1000L_BRL = 195.0

fun buildOrConvertExcavationToPondInWorld(
    state: WorldState,
    pondName: String,
    tier: PondSizeTier,
    customX: Double? = null,
    customZ: Double? = null,
    fromExistingExcavation: Boolean = false,
): WorldState {
    val prepared = ensureWaterAndFishWorldInitialized(state)
    val activeVeh = prepared.activeVehicleUid?.let { uid -> prepared.vehicles.find { it.uid == uid } }
    val cx = customX ?: activeVeh?.x ?: prepared.player.x
    val cz = customZ ?: activeVeh?.z ?: prepared.player.z

    val gridSide = getGridSide(prepared)
    val cellSize = getWorldCellSizeM(prepared)
    val halfSpan = getWorldSpanM(prepared) * 0.5
    val centerIdx = getCellIndexForState(prepared, cx, cz)
        ?: return prepared.copy(statusAlert = "Posição fora dos limites do mapa para construir açude.")
    val centerCell = prepared.cells[centerIdx]

    if (centerCell.soilState == SoilState.AREA_PRESERVACAO ||
        centerCell.soilState == SoilState.ESTRADA_ASFALTO ||
        centerCell.soilState == SoilState.AGUA_RIO ||
        centerCell.soilState == SoilState.PONTE
    ) {
        return prepared.copy(statusAlert = "Não é possível construir açude sobre rio, rodovia ou área de preservação.")
    }
    if (centerCell.plotId > 0) {
        val plot = prepared.plots.find { it.id == centerCell.plotId }
        if (plot != null && !plot.owned && !plot.leased) {
            return prepared.copy(statusAlert = "Compre ou arrende '${plot.name}' antes de implantar um açude aqui.")
        }
    }
    if (prepared.ponds.orEmpty().any { hypot(it.centerX - cx, it.centerZ - cz) < it.radiusM + tier.defaultRadiusM + 4.0 }) {
        return prepared.copy(statusAlert = "Já existe um açude ou lago artificial próximo sobrepondo este local.")
    }

    val radiusM = tier.defaultRadiusM
    val nextCells = prepared.cells.toMutableList()
    var excavatedCellCount = 0
    var totalExistingExcDepth = 0.0
    var maxExistingExcDepth = 0.0
    var touchedCells = 0

    for (gz in 0 until gridSide) {
        for (gx in 0 until gridSide) {
            val idx = gz * gridSide + gx
            val c = nextCells[idx]
            val wx = gx * cellSize - halfSpan + cellSize * 0.5
            val wz = gz * cellSize - halfSpan + cellSize * 0.5
            val d = hypot(wx - cx, wz - cz)
            if (d <= radiusM) {
                if (c.soilState == SoilState.AREA_PRESERVACAO || c.soilState == SoilState.ESTRADA_ASFALTO || c.soilState == SoilState.PONTE) continue
                touchedCells++
                val curExc = c.excavationDepthM ?: 0.0
                if (curExc >= 0.45) {
                    excavatedCellCount++
                    totalExistingExcDepth += curExc
                    maxExistingExcDepth = max(maxExistingExcDepth, curExc)
                }
            }
        }
    }

    if (touchedCells == 0) {
        return prepared.copy(statusAlert = "Espaço insuficiente nesta posição para moldar a bacia do açude.")
    }

    if (fromExistingExcavation && excavatedCellCount < 2) {
        return prepared.copy(
            statusAlert = "Escave primeiro a bacia com uma Escavadeira ou Retroescavadeira antes de converter em açude."
        )
    }

    val costBrl = if (fromExistingExcavation) {
        (tier.buildCostBrl * 0.25).roundToInt().toDouble()
    } else {
        tier.buildCostBrl
    }
    if (prepared.moneyBrl < costBrl) {
        return prepared.copy(
            statusAlert = "Saldo insuficiente para obra do ${tier.label} (${formatBrl(costBrl)})."
        )
    }

    val nearestAq = findNearestAquiferZone(prepared, cx, cz)
    val aqDepth = nearestAq?.waterTableDepthM ?: 2.4
    val targetDepthM = if (fromExistingExcavation) {
        max(maxExistingExcDepth, totalExistingExcDepth / max(1, excavatedCellCount))
    } else {
        max(tier.defaultDepthM, aqDepth + 0.65)
    }

    // Carve physical geometry & build containment dike/talude around perimeter
    for (gz in 0 until gridSide) {
        for (gx in 0 until gridSide) {
            val idx = gz * gridSide + gx
            val c = nextCells[idx]
            if (c.soilState == SoilState.AREA_PRESERVACAO || c.soilState == SoilState.ESTRADA_ASFALTO || c.soilState == SoilState.PONTE || c.soilState == SoilState.AGUA_RIO) continue
            val wx = gx * cellSize - halfSpan + cellSize * 0.5
            val wz = gz * cellSize - halfSpan + cellSize * 0.5
            val d = hypot(wx - cx, wz - cz)
            if (d <= radiusM) {
                val bowlFactor = (1.0 - (d / radiusM) * 0.45).coerceIn(0.4, 1.0)
                val finalExc = max(c.excavationDepthM ?: 0.0, targetDepthM * bowlFactor)
                val dropDelta = max(0.0, finalExc - (c.excavationDepthM ?: 0.0))
                val initialWater = (c.waterDepthM ?: 0.0)
                nextCells[idx] = c.copy(
                    height = c.height - dropDelta,
                    excavationDepthM = finalExc,
                    waterDepthM = initialWater,
                    soilState = if (initialWater > 0.2) SoilState.ACUDE_AGUA else SoilState.ESCAVADO,
                    soilTone = "ARGILA_UMIDA",
                    cropId = null,
                    growthProgress = 0.0,
                    waterTableDepthM = aqDepth,
                    aquiferAvailability = nearestAq?.availability?.name,
                )
            } else if (d <= radiusM + cellSize * 1.45 && (c.spoilHeightM ?: 0.0) < 0.35) {
                val bermGain = 0.44
                nextCells[idx] = c.copy(
                    height = c.height + bermGain,
                    spoilHeightM = (c.spoilHeightM ?: 0.0) + bermGain,
                    soilState = SoilState.TALUDE_BARRAGEM,
                    soilTone = "TALUDE_COMPACTADO",
                    cropId = null,
                )
            }
        }
    }

    val reachedWaterTable = targetDepthM >= aqDepth
    val pond = PondStructure(
        uid = "pond_${prepared.day}_${prepared.ponds.orEmpty().size + 1}_${abs((cx * 13 + cz * 7).roundToInt())}",
        name = pondName.ifBlank { "${tier.label} #${prepared.ponds.orEmpty().size + 1}" },
        sizeTier = tier,
        centerX = cx,
        centerZ = cz,
        radiusM = radiusM,
        depthM = (targetDepthM * 100.0).roundToInt() / 100.0,
        waterLevelM = if (reachedWaterTable) 0.15 else 0.0,
        waterVolumeL = if (reachedWaterTable) tier.defaultCapacityL * 0.03 else 0.0,
        maxVolumeL = tier.defaultCapacityL,
        waterQualityPct = nearestAq?.qualityPct ?: 78.0,
        dikeIntegrityPct = 95.0,
        hasPump = false,
        hasPiping = false,
        pumpActive = false,
        piscicultureEnabled = false,
        linkedAquiferId = nearestAq?.id,
        createdDay = prepared.day,
    )

    val depthMsg = if (reachedWaterTable) {
        "Lençol freático (${String.format(Locale.US, "%.1f", aqDepth)}m) atingido — enchimento progressivo iniciado!"
    } else {
        "Profundidade (${String.format(Locale.US, "%.1f", targetDepthM)}m) ainda acima do lençol (${String.format(Locale.US, "%.1f", aqDepth)}m): escave mais fundo ou instale bomba de captação."
    }

    return prepared.copy(
        moneyBrl = prepared.moneyBrl - costBrl,
        cells = nextCells,
        ponds = prepared.ponds.orEmpty() + pond,
        ledger = prepared.ledger + LedgerEntry(prepared.day, "Construção ${tier.label}: ${pond.name}", -costBrl),
        statusAlert = "${pond.name} implantado (-${formatBrl(costBrl)})! $depthMsg",
    )
}

fun upgradePondDikesInWorld(state: WorldState, pondUid: String): WorldState {
    val prepared = ensureWaterAndFishWorldInitialized(state)
    val pond = prepared.ponds.orEmpty().find { it.uid == pondUid } ?: return prepared
    if (prepared.moneyBrl < DIKE_REINFORCEMENT_COST_BRL) {
        return prepared.copy(statusAlert = "Saldo insuficiente para reforçar taludes (${formatBrl(DIKE_REINFORCEMENT_COST_BRL)}).")
    }
    return prepared.copy(
        moneyBrl = prepared.moneyBrl - DIKE_REINFORCEMENT_COST_BRL,
        ponds = prepared.ponds.orEmpty().map {
            if (it.uid == pondUid) it.copy(dikeIntegrityPct = 100.0, maxVolumeL = it.maxVolumeL * 1.05) else it
        },
        ledger = prepared.ledger + LedgerEntry(prepared.day, "Reforço de taludes: ${pond.name}", -DIKE_REINFORCEMENT_COST_BRL),
        statusAlert = "Taludes e margens de '${pond.name}' compactados e reforçados (100%).",
    )
}

fun installPondPumpAndPipingInWorld(state: WorldState, pondUid: String): WorldState {
    val prepared = ensureWaterAndFishWorldInitialized(state)
    val pond = prepared.ponds.orEmpty().find { it.uid == pondUid } ?: return prepared
    if (pond.hasPump && pond.hasPiping) {
        val nextActive = !pond.pumpActive
        return prepared.copy(
            ponds = prepared.ponds.orEmpty().map { if (it.uid == pondUid) it.copy(pumpActive = nextActive) else it },
            statusAlert = if (nextActive) "Bomba hidráulica de '${pond.name}' LIGADA." else "Bomba de '${pond.name}' desligada.",
        )
    }
    if (prepared.moneyBrl < PUMP_PIPING_COST_BRL) {
        return prepared.copy(
            statusAlert = "Saldo insuficiente para instalar bomba e encanamento (${formatBrl(PUMP_PIPING_COST_BRL)})."
        )
    }
    val pumpBuilding = PlacedBuilding(
        uid = "bld_pump_${pond.uid}",
        type = BuildingType.ESTACAO_BOMBEAMENTO_ACUDE,
        x = pond.centerX + pond.radiusM + 3.2,
        z = pond.centerZ,
        rotationDeg = 270.0,
        customLabel = "Bomba ${pond.name}",
    )
    return prepared.copy(
        moneyBrl = prepared.moneyBrl - PUMP_PIPING_COST_BRL,
        ponds = prepared.ponds.orEmpty().map {
            if (it.uid == pondUid) it.copy(hasPump = true, hasPiping = true, pumpActive = true) else it
        },
        buildings = prepared.buildings + pumpBuilding,
        ledger = prepared.ledger + LedgerEntry(prepared.day, "Bomba & Tubulação: ${pond.name}", -PUMP_PIPING_COST_BRL),
        statusAlert = "Bomba hidráulica e tubulação instaladas em '${pond.name}' e acionadas (-${formatBrl(PUMP_PIPING_COST_BRL)})!",
    )
}

fun buildCommercialWaterReservoirInWorld(state: WorldState): WorldState {
    val prepared = ensureWaterAndFishWorldInitialized(state)
    val biz = prepared.waterBusiness ?: WaterBusinessState()
    if (biz.hasReservoirStructure && biz.reservoirMaxL >= 260_000.0) {
        return prepared.copy(statusAlert = "Complexo de reservatórios comerciais já está em capacidade máxima (260.000 L).")
    }
    if (prepared.moneyBrl < RESERVOIR_UPGRADE_COST_BRL) {
        return prepared.copy(
            statusAlert = "Saldo insuficiente para ampliar reservatório hídrico (${formatBrl(RESERVOIR_UPGRADE_COST_BRL)})."
        )
    }
    val nextMax = if (!biz.hasReservoirStructure) 140_000.0 else min(280_000.0, biz.reservoirMaxL + 70_000.0)
    return prepared.copy(
        moneyBrl = prepared.moneyBrl - RESERVOIR_UPGRADE_COST_BRL,
        waterBusiness = biz.copy(
            hasReservoirStructure = true,
            reservoirMaxL = nextMax,
            totalExpensesBrl = biz.totalExpensesBrl + RESERVOIR_UPGRADE_COST_BRL,
        ),
        ledger = prepared.ledger + LedgerEntry(prepared.day, "Ampliação Reservatório Comercial de Água", -RESERVOIR_UPGRADE_COST_BRL),
        statusAlert = "Reservatório comercial ampliado para ${(nextMax / 1000.0).roundToInt()} mil litros!",
    )
}

fun pumpWaterFromPondToReservoir(state: WorldState, pondUid: String, litersRequested: Double = 20_000.0): WorldState {
    val prepared = ensureWaterAndFishWorldInitialized(state)
    val pond = prepared.ponds.orEmpty().find { it.uid == pondUid } ?: return prepared
    val biz = prepared.waterBusiness ?: WaterBusinessState()
    if (!pond.hasPump || !pond.hasPiping) {
        return prepared.copy(statusAlert = "Instale bomba hidráulica e tubulação em '${pond.name}' primeiro.")
    }
    val roomL = max(0.0, biz.reservoirMaxL - biz.reservoirVolumeL)
    if (roomL <= 50.0) {
        return prepared.copy(statusAlert = "Reservatório comercial já está cheio (${biz.reservoirVolumeL.roundToInt()} L).")
    }
    // Keep safe minimum ecological reserve if pond is raising fish
    val minEcoReserve = if (pond.piscicultureEnabled && pond.fishStocks.isNotEmpty()) pond.maxVolumeL * 0.22 else 0.0
    val availableFromPond = max(0.0, pond.waterVolumeL - minEcoReserve)
    val transferL = min(litersRequested, min(roomL, availableFromPond))
    if (transferL <= 50.0) {
        return prepared.copy(
            statusAlert = "Volume disponível em '${pond.name}' insuficiente no momento (aguarde recarga natural do aquífero)."
        )
    }

    val energyCost = ((transferL / 1000.0) * 14.0 * 100.0).roundToInt() / 100.0
    val newPondVol = pond.waterVolumeL - transferL
    val newPondLvl = ((newPondVol / max(1.0, pond.maxVolumeL)) * pond.depthM).coerceIn(0.0, pond.depthM)

    return prepared.copy(
        moneyBrl = prepared.moneyBrl - energyCost,
        ponds = prepared.ponds.orEmpty().map {
            if (it.uid == pondUid) it.copy(waterVolumeL = newPondVol, waterLevelM = newPondLvl) else it
        },
        waterBusiness = biz.copy(
            reservoirVolumeL = biz.reservoirVolumeL + transferL,
            totalExtractedL = biz.totalExtractedL + transferL,
            totalExpensesBrl = biz.totalExpensesBrl + energyCost,
        ),
        ledger = prepared.ledger + LedgerEntry(prepared.day, "Bombeamento de água (${transferL.roundToInt()} L)", -energyCost),
        statusAlert = "Bombeados +${transferL.roundToInt()} L para o reservatório comercial (energia -${formatBrl(energyCost)}).",
    )
}

fun fillWaterTankerInWorld(state: WorldState): WorldState {
    val prepared = ensureWaterAndFishWorldInitialized(state)
    val activeUid = prepared.activeVehicleUid
        ?: return prepared.copy(statusAlert = "Entre em um Caminhão-Pipa para carregar água.")
    val veh = prepared.vehicles.find { it.uid == activeUid } ?: return prepared
    val def = getVehicleDef(veh.defId)
    if (!isWaterTankerVehicle(def)) {
        return prepared.copy(statusAlert = "${def.model} não é um Caminhão-Pipa.")
    }

    val capL = getWaterTankCapacityL(def)
    val curL = veh.carriedWaterL ?: 0.0
    val roomL = max(0.0, capL - curL)
    if (roomL <= 50.0) {
        return prepared.copy(statusAlert = "Tanque do ${def.model} já está completo (${capL.roundToInt()} L).")
    }

    val nearStation = prepared.buildings.firstOrNull {
        (it.type == BuildingType.ESTACAO_ABASTECIMENTO_AGUA || it.type == BuildingType.ESTACAO_BOMBEAMENTO_ACUDE) &&
            hypot(it.x - veh.x, it.z - veh.z) <= 24.0
    }
    val nearPond = prepared.ponds.orEmpty().firstOrNull {
        hypot(it.centerX - veh.x, it.centerZ - veh.z) <= it.radiusM + 18.0 && it.hasPump
    }

    val biz = prepared.waterBusiness ?: WaterBusinessState()
    return when {
        nearPond != null && nearPond.waterVolumeL > 200.0 -> {
            val minEco = if (nearPond.piscicultureEnabled && nearPond.fishStocks.isNotEmpty()) nearPond.maxVolumeL * 0.2 else 0.0
            val take = min(roomL, max(0.0, nearPond.waterVolumeL - minEco))
            if (take <= 50.0) {
                return prepared.copy(statusAlert = "Nível de '${nearPond.name}' muito baixo para captação segura.")
            }
            val nextVol = nearPond.waterVolumeL - take
            val nextLvl = (nextVol / max(1.0, nearPond.maxVolumeL)) * nearPond.depthM
            prepared.copy(
                vehicles = prepared.vehicles.map { if (it.uid == activeUid) it.copy(carriedWaterL = curL + take) else it },
                ponds = prepared.ponds.orEmpty().map {
                    if (it.uid == nearPond.uid) it.copy(waterVolumeL = nextVol, waterLevelM = nextLvl) else it
                },
                waterBusiness = biz.copy(totalExtractedL = biz.totalExtractedL + take),
                statusAlert = "Caminhão-pipa carregado com +${take.roundToInt()} L do açude '${nearPond.name}'!",
            )
        }
        biz.reservoirVolumeL > 200.0 -> {
            val take = min(roomL, biz.reservoirVolumeL)
            prepared.copy(
                vehicles = prepared.vehicles.map { if (it.uid == activeUid) it.copy(carriedWaterL = curL + take) else it },
                waterBusiness = biz.copy(reservoirVolumeL = biz.reservoirVolumeL - take),
                statusAlert = "Caminhão-pipa abastecido com +${take.roundToInt()} L do reservatório da fazenda!",
            )
        }
        nearStation != null -> {
            val aq = findNearestAquiferZone(prepared, veh.x, veh.z)
            val availAq = aq?.remainingCapacityL ?: 20_000.0
            val take = min(roomL, availAq)
            if (take <= 50.0) {
                return prepared.copy(statusAlert = "Aquífero regional momentaneamente esgotado. Aguarde recarga diária.")
            }
            val tariffCost = ((take / 1000.0) * 52.0 * 100.0).roundToInt() / 100.0
            if (prepared.moneyBrl < tariffCost) {
                return prepared.copy(statusAlert = "Saldo insuficiente para tarifa da Estação (${formatBrl(tariffCost)}).")
            }
            prepared.copy(
                moneyBrl = prepared.moneyBrl - tariffCost,
                vehicles = prepared.vehicles.map { if (it.uid == activeUid) it.copy(carriedWaterL = curL + take) else it },
                aquiferGrid = prepared.aquiferGrid.orEmpty().map {
                    if (it.id == aq?.id) it.copy(remainingCapacityL = max(0.0, it.remainingCapacityL - take)) else it
                },
                waterBusiness = biz.copy(
                    totalExtractedL = biz.totalExtractedL + take,
                    totalExpensesBrl = biz.totalExpensesBrl + tariffCost,
                ),
                ledger = prepared.ledger + LedgerEntry(prepared.day, "Captação Estação de Água (${take.roundToInt()} L)", -tariffCost),
                statusAlert = "Caminhão-pipa abastecido (+${take.roundToInt()} L) na Estação (-${formatBrl(tariffCost)}).",
            )
        }
        else -> prepared.copy(
            statusAlert = "Aproxime o Caminhão-Pipa de um Açude com bomba, use o Reservatório próprio ou vá à Estação de Abastecimento."
        )
    }
}

fun sellWaterCommercialDeliveryInWorld(
    state: WorldState,
    directFromReservoirLiters: Double? = null,
): WorldState {
    val prepared = ensureWaterAndFishWorldInitialized(state)
    val biz = prepared.waterBusiness ?: WaterBusinessState()
    val activeVeh = prepared.activeVehicleUid?.let { uid -> prepared.vehicles.find { it.uid == uid } }

    if (directFromReservoirLiters != null && directFromReservoirLiters > 10.0) {
        val soldL = min(directFromReservoirLiters, biz.reservoirVolumeL)
        if (soldL <= 50.0) {
            return prepared.copy(statusAlert = "Reservatório comercial vazio! Bombeie água de um açude primeiro.")
        }
        val revenue = ((soldL / 1000.0) * WATER_SALE_PRICE_PER_1000L_BRL * 0.85 * 100.0).roundToInt() / 100.0
        return prepared.copy(
            moneyBrl = prepared.moneyBrl + revenue,
            waterBusiness = biz.copy(
                reservoirVolumeL = biz.reservoirVolumeL - soldL,
                totalWaterSoldL = biz.totalWaterSoldL + soldL,
                totalWaterRevenueBrl = biz.totalWaterRevenueBrl + revenue,
            ),
            ledger = prepared.ledger + LedgerEntry(prepared.day, "Venda de Água Tubulada (${soldL.roundToInt()} L)", revenue),
            statusAlert = "Venda de água faturada: +${formatBrl(revenue)} (${soldL.roundToInt()} L)!",
        )
    }

    if (activeVeh == null || !isWaterTankerVehicle(getVehicleDef(activeVeh.defId))) {
        return prepared.copy(statusAlert = "Dirija um Caminhão-Pipa carregado ou venda direto do Reservatório Comercial.")
    }
    val cargoL = activeVeh.carriedWaterL ?: 0.0
    if (cargoL <= 50.0) {
        return prepared.copy(statusAlert = "Caminhão-Pipa vazio! Encha o tanque em um açude ou reservatório.")
    }

    val centerEst = com.example.domain.WATER_AND_FISH_ESTABLISHMENTS.firstOrNull { it.id == "centro_comercializacao_agua" }
    val nearCenter = centerEst != null && hypot(centerEst.worldX - activeVeh.x, centerEst.worldZ - activeVeh.z) <= 28.0
    val mult = if (nearCenter) 1.15 else 0.92
    val revenue = ((cargoL / 1000.0) * WATER_SALE_PRICE_PER_1000L_BRL * mult * 100.0).roundToInt() / 100.0
    val venue = if (nearCenter) "Centro de Comercialização de Água" else "Entrega Regional de Água"

    return prepared.copy(
        moneyBrl = prepared.moneyBrl + revenue,
        vehicles = prepared.vehicles.map { if (it.uid == activeVeh.uid) it.copy(carriedWaterL = 0.0) else it },
        waterBusiness = biz.copy(
            totalWaterSoldL = biz.totalWaterSoldL + cargoL,
            totalWaterRevenueBrl = biz.totalWaterRevenueBrl + revenue,
        ),
        ledger = prepared.ledger + LedgerEntry(prepared.day, "$venue (${cargoL.roundToInt()} L)", revenue),
        statusAlert = "$venue: +${formatBrl(revenue)} por ${cargoL.roundToInt()} L entregues!",
    )
}

fun stepPondsAndWaterBusinessSimulation(
    state: WorldState,
    dtSec: Double,
): WorldState {
    val ponds = state.ponds
    val aquifers = state.aquiferGrid
    if (ponds.isNullOrEmpty() || aquifers.isNullOrEmpty()) return state

    val rainInt = WEATHER_META[state.weather]?.rainIntensity ?: 0.0
    val nextAq = aquifers.toMutableList()
    val aqIndexMap = aquifers.withIndex().associate { it.value.id to it.index }
    var pondChanged = false
    val cellWaterUpdates = mutableListOf<Triple<Double, Double, Double>>() // cx, cz, levelNorm

    val nextPonds = ponds.map { pond ->
        if (pond.waterVolumeL >= pond.maxVolumeL * 0.998 && !pond.pumpActive) {
            return@map pond
        }
        val aqIdx = pond.linkedAquiferId?.let { aqIndexMap[it] }
        val aq = aqIdx?.let { nextAq[it] }
        val aqDepth = aq?.waterTableDepthM ?: 2.4
        val reachedTable = pond.depthM >= aqDepth

        val baseSeepLPerSec = when {
            reachedTable && (aq?.remainingCapacityL ?: 0.0) > 50.0 -> (aq?.rechargePerDayL ?: 6000.0) / 1440.0
            else -> 0.0
        }
        val pumpGainLPerSec = if (pond.hasPump && pond.pumpActive && (aq?.remainingCapacityL ?: 0.0) > 50.0) 115.0 else 0.0
        val rainGainLPerSec = rainInt * pond.radiusM * 1.6
        val roomL = max(0.0, pond.maxVolumeL - pond.waterVolumeL)
        if (roomL <= 1.0) return@map pond

        val aqDemand = (baseSeepLPerSec + pumpGainLPerSec) * dtSec
        val actualFromAq = if (aqIdx != null && aq != null && aqDemand > 0.0) {
            min(aqDemand, aq.remainingCapacityL)
        } else 0.0
        if (aqIdx != null && aq != null && actualFromAq > 0.0) {
            nextAq[aqIdx] = aq.copy(remainingCapacityL = max(0.0, aq.remainingCapacityL - actualFromAq))
        }

        val totalGain = min(roomL, actualFromAq + rainGainLPerSec * dtSec)
        if (totalGain <= 0.01) return@map pond

        pondChanged = true
        val newVol = min(pond.maxVolumeL, pond.waterVolumeL + totalGain)
        val newLvl = ((newVol / max(1.0, pond.maxVolumeL)) * pond.depthM).coerceIn(0.0, pond.depthM)
        if (abs(newLvl - pond.waterLevelM) > 0.03) {
            cellWaterUpdates.add(Triple(pond.centerX, pond.centerZ, pond.radiusM))
        }
        pond.copy(waterVolumeL = newVol, waterLevelM = newLvl)
    }

    if (!pondChanged) return state

    val nextCells = if (cellWaterUpdates.isNotEmpty()) {
        val gridSide = getGridSide(state)
        val cellSize = getWorldCellSizeM(state)
        val halfSpan = getWorldSpanM(state) * 0.5
        val list = state.cells.toMutableList()
        for (p in nextPonds) {
            val minGx = max(0, (((p.centerX - p.radiusM) + halfSpan) / cellSize).toInt())
            val maxGx = min(gridSide - 1, (((p.centerX + p.radiusM) + halfSpan) / cellSize).toInt())
            val minGz = max(0, (((p.centerZ - p.radiusM) + halfSpan) / cellSize).toInt())
            val maxGz = min(gridSide - 1, (((p.centerZ + p.radiusM) + halfSpan) / cellSize).toInt())
            for (gz in minGz..maxGz) {
                for (gx in minGx..maxGx) {
                    val idx = gz * gridSide + gx
                    val c = list[idx]
                    val wx = gx * cellSize - halfSpan + cellSize * 0.5
                    val wz = gz * cellSize - halfSpan + cellSize * 0.5
                    if (hypot(wx - p.centerX, wz - p.centerZ) <= p.radiusM && c.soilState != SoilState.AGUA_RIO) {
                        list[idx] = c.copy(
                            waterDepthM = p.waterLevelM,
                            soilState = if (p.waterLevelM > 0.12) SoilState.ACUDE_AGUA else c.soilState,
                        )
                    }
                }
            }
        }
        list
    } else state.cells

    return state.copy(
        cells = nextCells,
        ponds = nextPonds,
        aquiferGrid = nextAq,
    )
}

fun advanceDailyPondsAndAquifers(state: WorldState): WorldState {
    val prepared = ensureWaterAndFishWorldInitialized(state)
    val rainInt = WEATHER_META[prepared.weather]?.rainIntensity ?: 0.0
    val rainMult = 1.0 + rainInt * 0.85

    // 1. Recharge groundwater aquifers naturally up to totalCapacityL (never infinite!)
    val nextAq = prepared.aquiferGrid.orEmpty().map { aq ->
        val nextRem = min(aq.totalCapacityL, aq.remainingCapacityL + aq.rechargePerDayL * rainMult)
        aq.copy(remainingCapacityL = nextRem.roundToInt().toDouble())
    }
    val aqMutable = nextAq.toMutableList()
    val aqIdxMap = nextAq.withIndex().associate { it.value.id to it.index }

    // 2. Update ponds daily water balance (evaporation vs seepage/pump + rainfall)
    val nextPonds = prepared.ponds.orEmpty().map { pond ->
        val aqIdx = pond.linkedAquiferId?.let { aqIdxMap[it] }
        val aq = aqIdx?.let { aqMutable[it] }
        val reachedTable = pond.depthM >= (aq?.waterTableDepthM ?: 2.4)
        val evapLoss = pond.radiusM * pond.radiusM * 12.0 * (1.0 - rainInt * 0.7)
        val dailySeep = if (reachedTable && aq != null) {
            min(aq.rechargePerDayL * 0.65, aq.remainingCapacityL)
        } else 0.0
        val dailyPump = if (pond.hasPump && pond.pumpActive && aq != null) {
            min(28_000.0, max(0.0, aq.remainingCapacityL - dailySeep))
        } else 0.0
        val rainGain = rainInt * pond.radiusM * pond.radiusM * 38.0

        val drawnAq = dailySeep + dailyPump
        if (aqIdx != null && aq != null && drawnAq > 0.0) {
            aqMutable[aqIdx] = aq.copy(remainingCapacityL = max(0.0, aq.remainingCapacityL - drawnAq))
        }

        val dikeDecay = if (rainInt > 0.5) 2.5 else 0.4
        val nextVol = (pond.waterVolumeL + drawnAq + rainGain - evapLoss).coerceIn(0.0, pond.maxVolumeL)
        val nextLvl = ((nextVol / max(1.0, pond.maxVolumeL)) * pond.depthM).coerceIn(0.0, pond.depthM)
        pond.copy(
            waterVolumeL = nextVol,
            waterLevelM = nextLvl,
            dikeIntegrityPct = max(25.0, pond.dikeIntegrityPct - dikeDecay),
        )
    }

    val pondMap = nextPonds.associateBy { it.uid }
    val gridSide = getGridSide(prepared)
    val cellSize = getWorldCellSizeM(prepared)
    val halfSpan = getWorldSpanM(prepared) * 0.5
    val nextCells = prepared.cells.map { c ->
        val wx = c.gx * cellSize - halfSpan + cellSize * 0.5
        val wz = c.gz * cellSize - halfSpan + cellSize * 0.5
        val matchingPond = nextPonds.firstOrNull { hypot(wx - it.centerX, wz - it.centerZ) <= it.radiusM }
        if (matchingPond != null && c.soilState != SoilState.AGUA_RIO) {
            c.copy(
                waterDepthM = matchingPond.waterLevelM,
                soilState = if (matchingPond.waterLevelM > 0.12) SoilState.ACUDE_AGUA else SoilState.ESCAVADO,
            )
        } else c
    }

    if (pondMap.isEmpty()) {
        return prepared.copy(aquiferGrid = aqMutable, ponds = nextPonds, cells = nextCells)
    }
    return prepared.copy(
        aquiferGrid = aqMutable,
        ponds = nextPonds,
        cells = nextCells,
    )
}
