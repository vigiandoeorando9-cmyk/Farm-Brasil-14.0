package com.example.simulation

import com.example.domain.FISH_SPECIES_CATALOG
import com.example.domain.LedgerEntry
import com.example.domain.PondFishStock
import com.example.domain.PondStructure
import com.example.domain.WATER_AND_FISH_ESTABLISHMENTS
import com.example.domain.WorldState
import com.example.domain.getFishSpeciesById
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

const val FISH_FEED_PRICE_PER_KG_BRL = 3.60

fun enablePiscicultureOnPond(state: WorldState, pondUid: String): WorldState {
    val prepared = ensureWaterAndFishWorldInitialized(state)
    val pond = prepared.ponds.orEmpty().find { it.uid == pondUid } ?: return prepared
    if (pond.piscicultureEnabled) {
        return prepared.copy(statusAlert = "'${pond.name}' já está habilitado para piscicultura.")
    }
    val prepCost = 1400.0
    if (prepared.moneyBrl < prepCost) {
        return prepared.copy(statusAlert = "Saldo insuficiente para preparar tanque-rede e telas (${formatBrl(prepCost)}).")
    }
    return prepared.copy(
        moneyBrl = prepared.moneyBrl - prepCost,
        ponds = prepared.ponds.orEmpty().map {
            if (it.uid == pondUid) it.copy(piscicultureEnabled = true) else it
        },
        ledger = prepared.ledger + LedgerEntry(prepared.day, "Adaptação Piscicultura: ${pond.name}", -prepCost),
        statusAlert = "Piscicultura ativada em '${pond.name}' (-${formatBrl(prepCost)})!",
    )
}

fun stockFishFingerlingsInPond(
    state: WorldState,
    pondUid: String,
    speciesId: String,
    count: Int = 250,
): WorldState {
    val prepared = ensureWaterAndFishWorldInitialized(state)
    val pond = prepared.ponds.orEmpty().find { it.uid == pondUid } ?: return prepared
    val sp = getFishSpeciesById(speciesId)
    val safeCount = count.coerceIn(10, 5000)

    if (!pond.piscicultureEnabled) {
        return prepared.copy(statusAlert = "Ative a Piscicultura no açude '${pond.name}' antes de povoar alevinos.")
    }
    if (pond.radiusM < sp.minPondRadiusM) {
        return prepared.copy(
            statusAlert = "${sp.name} exige açude de raio >= ${sp.minPondRadiusM.roundToInt()}m (atual ${pond.radiusM.roundToInt()}m)."
        )
    }
    if (pond.waterLevelM < sp.minWaterDepthM) {
        return prepared.copy(
            statusAlert = "Lâmina d'água insuficiente em '${pond.name}' (${String.format(java.util.Locale.US, "%.1f", pond.waterLevelM)}m < ${sp.minWaterDepthM}m exigido para ${sp.name})."
        )
    }

    val totalCost = (safeCount * sp.fingerlingCostBrl * 100.0).roundToInt() / 100.0
    if (prepared.moneyBrl < totalCost) {
        return prepared.copy(statusAlert = "Saldo insuficiente para $safeCount alevinos de ${sp.name} (${formatBrl(totalCost)}).")
    }

    val existing = pond.fishStocks.firstOrNull { it.speciesId == speciesId }
    val updatedStocks = if (existing != null) {
        val total = existing.count + safeCount
        val weightedProg = (existing.growthProgress * existing.count) / max(1, total)
        pond.fishStocks.map {
            if (it.speciesId == speciesId) {
                it.copy(
                    count = total,
                    growthProgress = weightedProg,
                    biomassKg = total * max(0.06, sp.harvestWeightKg * max(0.08, weightedProg)),
                )
            } else it
        }
    } else {
        pond.fishStocks + PondFishStock(
            speciesId = speciesId,
            count = safeCount,
            avgWeightKg = 0.04,
            biomassKg = safeCount * 0.04,
            growthProgress = 0.0,
            stockedDay = prepared.day,
            healthPct = 96.0,
        )
    }

    return prepared.copy(
        moneyBrl = prepared.moneyBrl - totalCost,
        ponds = prepared.ponds.orEmpty().map {
            if (it.uid == pondUid) it.copy(fishStocks = updatedStocks) else it
        },
        ledger = prepared.ledger + LedgerEntry(prepared.day, "Alevinos ${sp.name} (${safeCount} un.)", -totalCost),
        statusAlert = "+$safeCount alevinos de ${sp.name} soltos em '${pond.name}' (-${formatBrl(totalCost)})!",
    )
}

fun buyAndFeedPondFish(
    state: WorldState,
    pondUid: String,
    feedKg: Double = 80.0,
): WorldState {
    val prepared = ensureWaterAndFishWorldInitialized(state)
    val pond = prepared.ponds.orEmpty().find { it.uid == pondUid } ?: return prepared
    val safeKg = feedKg.coerceIn(10.0, 2000.0)
    val cost = (safeKg * FISH_FEED_PRICE_PER_KG_BRL * 100.0).roundToInt() / 100.0
    if (prepared.moneyBrl < cost) {
        return prepared.copy(statusAlert = "Saldo insuficiente para comprar ${safeKg.roundToInt()} kg de ração (${formatBrl(cost)}).")
    }
    return prepared.copy(
        moneyBrl = prepared.moneyBrl - cost,
        ponds = prepared.ponds.orEmpty().map {
            if (it.uid == pondUid) it.copy(fishFeedStockKg = it.fishFeedStockKg + safeKg) else it
        },
        ledger = prepared.ledger + LedgerEntry(prepared.day, "Ração Piscicultura: ${pond.name} (${safeKg.roundToInt()} kg)", -cost),
        statusAlert = "+${safeKg.roundToInt()} kg de ração extrusada estocados para '${pond.name}' (-${formatBrl(cost)}).",
    )
}

fun maintainPondWaterQuality(
    state: WorldState,
    pondUid: String,
    maintenanceType: String,
): WorldState {
    val prepared = ensureWaterAndFishWorldInitialized(state)
    val pond = prepared.ponds.orEmpty().find { it.uid == pondUid } ?: return prepared
    val (cost, label) = when (maintenanceType) {
        "AERACAO" -> 420.0 to "Aeração e Oxigenação"
        "CALAGEM_PH" -> 580.0 to "Correção de pH (Calcário)"
        else -> 650.0 to "Limpeza e Controle de Turbidez"
    }
    if (prepared.moneyBrl < cost) {
        return prepared.copy(statusAlert = "Saldo insuficiente para $label (${formatBrl(cost)}).")
    }

    val updatedPond = when (maintenanceType) {
        "AERACAO" -> pond.copy(
            dissolvedO2MgL = min(8.8, pond.dissolvedO2MgL + 2.4),
            waterQualityPct = min(99.0, pond.waterQualityPct + 6.0),
        )
        "CALAGEM_PH" -> pond.copy(
            waterPh = 7.1,
            waterQualityPct = min(99.0, pond.waterQualityPct + 8.0),
        )
        else -> pond.copy(
            turbidityPct = max(5.0, pond.turbidityPct - 18.0),
            waterQualityPct = min(99.0, pond.waterQualityPct + 10.0),
            dikeIntegrityPct = min(100.0, pond.dikeIntegrityPct + 4.0),
        )
    }

    return prepared.copy(
        moneyBrl = prepared.moneyBrl - cost,
        ponds = prepared.ponds.orEmpty().map { if (it.uid == pondUid) updatedPond else it },
        ledger = prepared.ledger + LedgerEntry(prepared.day, "$label: ${pond.name}", -cost),
        statusAlert = "$label realizada em '${pond.name}'! O₂=${String.format(java.util.Locale.US, "%.1f", updatedPond.dissolvedO2MgL)} mg/L • pH=${String.format(java.util.Locale.US, "%.1f", updatedPond.waterPh)}.",
    )
}

fun advanceDailyAquaculture(state: WorldState): WorldState {
    val ponds = state.ponds
    if (ponds.isNullOrEmpty()) return state

    val nextPonds = ponds.map { pond ->
        if (!pond.piscicultureEnabled || pond.fishStocks.isEmpty()) return@map pond

        val totalCount = pond.fishStocks.sumOf { it.count }
        val maxDensitySafe = (pond.waterVolumeL / 180.0).coerceAtLeast(80.0)
        val crowdingFactor = (totalCount.toDouble() / maxDensitySafe).coerceIn(0.0, 2.5)
        val totalBiomassKg = pond.fishStocks.sumOf { it.biomassKg }
        val dailyFeedDemand = (totalBiomassKg * 0.032).coerceAtLeast(1.5)
        val hasFeed = pond.fishFeedStockKg >= dailyFeedDemand * 0.5
        val remainingFeed = max(0.0, pond.fishFeedStockKg - min(pond.fishFeedStockKg, dailyFeedDemand))

        val o2Drop = 0.35 + crowdingFactor * 0.45 + (if (hasFeed) 0.15 else 0.0)
        val pumpRecovery = if (pond.pumpActive) 0.55 else 0.0
        val nextO2 = (pond.dissolvedO2MgL - o2Drop + pumpRecovery).coerceIn(1.8, 9.0)
        val nextTurbidity = (pond.turbidityPct + (if (hasFeed) 2.2 else 0.8) + crowdingFactor * 1.8).coerceIn(4.0, 75.0)
        val nextPh = (pond.waterPh - (if (crowdingFactor > 1.0) 0.08 else 0.02)).coerceIn(5.4, 8.6)
        val qualityScore = ((nextO2 / 6.5) * 45.0 + (1.0 - nextTurbidity / 85.0) * 35.0 + (1.0 - kotlin.math.abs(nextPh - 7.1) / 2.2) * 20.0)
            .coerceIn(22.0, 99.0)

        val nextStocks = pond.fishStocks.mapNotNull { st ->
            val sp = getFishSpeciesById(st.speciesId)
            val waterOk = pond.waterLevelM >= sp.minWaterDepthM * 0.75
            val feedMult = if (hasFeed) 1.0 else 0.25
            val qualityMult = when {
                qualityScore >= 75.0 && waterOk -> 1.12
                qualityScore >= 52.0 && waterOk -> 0.85
                else -> 0.35
            }
            val dailyStep = (1.0 / max(3, sp.growthDays)) * feedMult * qualityMult
            val nextProg = (st.growthProgress + dailyStep).coerceIn(0.0, 1.0)
            val mortalityRate = when {
                !waterOk -> 0.12
                nextO2 < 3.2 -> 0.08
                crowdingFactor > 1.4 -> 0.04
                else -> 0.002
            }
            val surviving = (st.count * (1.0 - mortalityRate)).roundToInt()
            // Gentle natural reproduction when adults are mature & space/quality allow
            val reproBonus = if (nextProg >= 0.96 && qualityScore >= 78.0 && crowdingFactor < 0.75 && state.day % 3 == 0) {
                (surviving * 0.04).roundToInt()
            } else 0
            val finalCount = (surviving + reproBonus).coerceAtLeast(0)
            if (finalCount <= 0) return@mapNotNull null

            val avgWeight = (0.04 + (sp.harvestWeightKg - 0.04) * nextProg).coerceAtLeast(0.04)
            st.copy(
                count = finalCount,
                avgWeightKg = (avgWeight * 100.0).roundToInt() / 100.0,
                biomassKg = ((finalCount * avgWeight) * 10.0).roundToInt() / 10.0,
                growthProgress = nextProg,
                readyToHarvest = nextProg >= 0.95,
                healthPct = (st.healthPct + if (qualityScore >= 65.0) 1.5 else -6.0).coerceIn(15.0, 100.0),
            )
        }

        pond.copy(
            fishFeedStockKg = (remainingFeed * 10.0).roundToInt() / 10.0,
            dissolvedO2MgL = (nextO2 * 10.0).roundToInt() / 10.0,
            turbidityPct = (nextTurbidity * 10.0).roundToInt() / 10.0,
            waterPh = (nextPh * 100.0).roundToInt() / 100.0,
            waterQualityPct = qualityScore.roundToInt().toDouble(),
            fishStocks = nextStocks,
        )
    }

    return state.copy(ponds = nextPonds)
}

fun harvestPondFish(
    state: WorldState,
    pondUid: String,
    speciesId: String? = null,
): WorldState {
    val prepared = ensureWaterAndFishWorldInitialized(state)
    val pond = prepared.ponds.orEmpty().find { it.uid == pondUid } ?: return prepared
    val candidates = pond.fishStocks.filter { st ->
        (speciesId == null || st.speciesId == speciesId) && (st.growthProgress >= 0.90 || st.readyToHarvest)
    }
    if (candidates.isEmpty()) {
        return prepared.copy(
            statusAlert = "Nenhum lote em '${pond.name}' atingiu ponto de despesca (aguarde crescimento >= 90%)."
        )
    }

    val nextInv = prepared.inventoryKg.toMutableMap()
    var totalKgHarvested = 0.0
    val harvestedSpeciesIds = candidates.map { it.speciesId }.toSet()
    for (st in candidates) {
        val invKey = "peixe_${st.speciesId}"
        nextInv[invKey] = (nextInv[invKey] ?: 0.0) + st.biomassKg
        totalKgHarvested += st.biomassKg
    }

    return prepared.copy(
        inventoryKg = nextInv,
        ponds = prepared.ponds.orEmpty().map {
            if (it.uid == pondUid) {
                it.copy(fishStocks = it.fishStocks.filterNot { st -> harvestedSpeciesIds.contains(st.speciesId) })
            } else it
        },
        statusAlert = "Despesca concluída em '${pond.name}': +${totalKgHarvested.roundToInt()} kg estocados para venda no Mercado de Peixes!",
    )
}

fun sellHarvestedFishAtMarket(
    state: WorldState,
    speciesId: String,
    kgToSell: Double? = null,
): WorldState {
    val prepared = ensureWaterAndFishWorldInitialized(state)
    val invKey = "peixe_$speciesId"
    val avail = prepared.inventoryKg[invKey] ?: 0.0
    val sp = getFishSpeciesById(speciesId)
    if (avail <= 0.5) {
        return prepared.copy(statusAlert = "Sem estoque de ${sp.name} despescado para vender.")
    }

    val activeVeh = prepared.activeVehicleUid?.let { uid -> prepared.vehicles.find { it.uid == uid } }
    val px = activeVeh?.x ?: prepared.player.x
    val pz = activeVeh?.z ?: prepared.player.z
    val nearFishEst = WATER_AND_FISH_ESTABLISHMENTS.firstOrNull {
        (it.id == "mercado_peixes" || it.id == "cooperativa_piscicultura") && hypot(it.worldX - px, it.worldZ - pz) <= 28.0
    }
    val mult = nearFishEst?.priceMultiplier ?: 1.0
    val sellKg = min(avail, max(1.0, kgToSell ?: avail))
    val revenue = ((sellKg * sp.marketPricePerKgBrl * mult) * 100.0).roundToInt() / 100.0

    val nextInv = prepared.inventoryKg.toMutableMap()
    val rem = avail - sellKg
    if (rem <= 0.5) nextInv.remove(invKey) else nextInv[invKey] = rem

    val venue = nearFishEst?.name ?: "Entrega Mercado de Peixes"
    val biz = prepared.waterBusiness ?: com.example.domain.WaterBusinessState()
    return prepared.copy(
        moneyBrl = prepared.moneyBrl + revenue,
        inventoryKg = nextInv,
        waterBusiness = biz.copy(
            totalFishSoldKg = biz.totalFishSoldKg + sellKg,
            totalFishRevenueBrl = biz.totalFishRevenueBrl + revenue,
        ),
        ledger = prepared.ledger + LedgerEntry(prepared.day, "$venue: ${sp.name} (${sellKg.roundToInt()} kg)", revenue),
        statusAlert = "$venue: +${formatBrl(revenue)} por ${sellKg.roundToInt()} kg de ${sp.name}!",
    )
}
