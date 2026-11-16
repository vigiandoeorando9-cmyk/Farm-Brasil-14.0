package com.example.simulation

import com.example.domain.ALL_CROPS
import com.example.domain.FieldPlot
import com.example.domain.ImplementCategory
import com.example.domain.LandUnit
import com.example.domain.OwnedImplement
import com.example.domain.OwnedVehicle
import com.example.domain.SEASON_META
import com.example.domain.SeasonType
import com.example.domain.SoilState
import com.example.domain.TerrainCell
import com.example.domain.WorldState
import com.example.domain.formatLandArea
import com.example.domain.getCropById
import com.example.domain.getImplementDef
import com.example.domain.getSeasonFromMonth
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class FieldOwnerKind(val label: String) {
    OWN_PROPERTY("SUA PROPRIEDADE"),
    OTHER_PLAYER("PROPRIEDADE DE OUTRO JOGADOR"),
    NPC_PROPERTY("PROPRIEDADE DE NPC"),
    AVAILABLE_FOR_PURCHASE("PROPRIEDADE DISPONÍVEL PARA COMPRA"),
}

enum class FieldGrowthStage(
    val stageNumber: Int,
    val label: String,
    val defaultProgressPct: Int,
) {
    TERRENO_PREPARADO(1, "Terreno preparado", 0),
    PLANTIO_REALIZADO(2, "Plantio realizado", 5),
    GERMINACAO(3, "Germinação", 15),
    CRESCIMENTO_INICIAL(4, "Crescimento inicial", 30),
    CRESCIMENTO_INTERMEDIARIO(5, "Crescimento intermediário", 50),
    CRESCIMENTO_AVANCADO(6, "Crescimento avançado", 70),
    MATURACAO(7, "Maturação", 85),
    PRONTO_PARA_COLHER(8, "Pronto para colher", 100),
    COLHIDO(9, "Colhido", 100),
}

enum class FieldConditionState(val label: String) {
    NECESSARIO("Necessário"),
    APLICADO("Aplicado"),
    NAO_NECESSARIO("Não necessário"),
    REALIZADA("Realizada"),
    NECESSARIA("Necessária"),
    NAO_NECESSARIA("Não necessária"),
    NAO_APLICADO("Não aplicado"),
    APLICACAO_PARCIAL("Aplicação parcial"),
    NIVEL_INSUFICIENTE("Nível insuficiente"),
    EM_ANDAMENTO("Em andamento"),
    ADEQUADA("Adequada"),
}

enum class CropCalendarStatus(val label: String) {
    EPOCA_ADEQUADA("Época adequada"),
    FORA_DA_EPOCA("Fora da época recomendada"),
    PERIODO_PLANTIO("Período de plantio"),
    PERIODO_CRESCIMENTO("Período de crescimento"),
    PERIODO_COLHEITA("Período de colheita"),
}

data class SoilConditionItem(
    val key: String,
    val title: String,
    val status: FieldConditionState,
    val detail: String,
    val needsAttention: Boolean,
)

data class FertilizationTelemetry(
    val totalPct: Int,
    val status: FieldConditionState,
    val statusDetail: String,
    val needsAdditionalPass: Boolean,
    val firstPassApplied: Boolean,
    val secondPassApplied: Boolean,
    val nitrogenPct: Int,
    val phosphorusPct: Int,
    val potassiumPct: Int,
)

data class CropGrowthTelemetry(
    val cropId: String,
    val cropName: String,
    val stage: FieldGrowthStage,
    val growthPct: Int,
    val isActiveCrop: Boolean,
)

data class FieldOperationalAdvisory(
    val implementCategoryLabel: String,
    val summary: String,
    val actionHint: String,
)

data class FieldPanelTelemetry(
    val plot: FieldPlot,
    val areaFormatted: String,
    val ownerKind: FieldOwnerKind,
    val ownerSubtitle: String?,
    val plantationStatusLabel: String,
    val dominantSoilState: SoilState,
    val soilConditions: List<SoilConditionItem>,
    val fertilization: FertilizationTelemetry,
    val growth: CropGrowthTelemetry?,
    val season: SeasonType,
    val seasonLabel: String,
    val monthNumber: Int,
    val monthName: String,
    val calendarStatus: CropCalendarStatus,
    val calendarDetail: String,
    val avgMoisturePct: Int,
    val avgPh: Double,
    val avgWeedPct: Int,
    val avgCompactionPct: Int,
    val activePivotIrrigating: Boolean,
    val operationalAdvisory: FieldOperationalAdvisory? = null,
)

private val MONTH_NAMES_PT = listOf(
    "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
)

fun detectFieldPlotWithHysteresis(
    state: WorldState,
    worldX: Double,
    worldZ: Double,
    previousPlotId: Int?,
): Int? {
    val gridSide = getGridSide(state)
    val cellSize = getWorldCellSizeM(state)
    val worldHalf = getWorldSpanM(state) / 2.0

    // Direct cell check first
    val centerIdx = getCellIndexForState(state, worldX, worldZ)
    val directCell = centerIdx?.let { state.cells.getOrNull(it) }
    if (directCell != null && directCell.plotId > 0) {
        val validPlot = state.plots.any { it.id == directCell.plotId }
        if (validPlot) return directCell.plotId
    }

    val gxFloat = (worldX + worldHalf) / cellSize
    val gzFloat = (worldZ + worldHalf) / cellSize
    val baseGx = gxFloat.toInt().coerceIn(0, gridSide - 1)
    val baseGz = gzFloat.toInt().coerceIn(0, gridSide - 1)
    val scanCells = 3 // ~18m radius

    val enterDistM = cellSize * 1.45
    val exitDistM = cellSize * 2.35

    var bestPlotId: Int? = null
    var bestDist = Double.MAX_VALUE
    var prevMinDist = Double.MAX_VALUE

    for (dz in -scanCells..scanCells) {
        for (dx in -scanCells..scanCells) {
            val gx = baseGx + dx
            val gz = baseGz + dz
            if (gx !in 0 until gridSide || gz !in 0 until gridSide) continue
            val cell = state.cells.getOrNull(gz * gridSide + gx) ?: continue
            if (cell.plotId <= 0) continue
            val cWx = gx * cellSize - worldHalf + cellSize * 0.5
            val cWz = gz * cellSize - worldHalf + cellSize * 0.5
            val d = hypot(cWx - worldX, cWz - worldZ)
            if (cell.plotId == previousPlotId && d < prevMinDist) {
                prevMinDist = d
            }
            if (d < bestDist) {
                bestDist = d
                bestPlotId = cell.plotId
            }
        }
    }

    if (bestPlotId != null && bestDist <= enterDistM) {
        return bestPlotId
    }
    if (previousPlotId != null && previousPlotId > 0 && prevMinDist <= exitDistM) {
        if (state.plots.any { it.id == previousPlotId }) return previousPlotId
    }
    return null
}

fun analyzeFieldPlot(
    state: WorldState,
    plotId: Int,
    localPlayerUid: String? = null,
    activeVehicle: OwnedVehicle? = null,
    attachedImplement: OwnedImplement? = null,
): FieldPanelTelemetry? {
    val plot = state.plots.find { it.id == plotId } ?: return null
    val plotCells = state.cells.filter { it.plotId == plotId }
    if (plotCells.isEmpty()) return null

    val unit = state.landUnit ?: LandUnit.HECTARE
    val areaFormatted = formatLandArea(plot.areaHa, unit)

    // 2.2 Owner ID
    val (ownerKind, ownerSub) = when {
        plot.ownerPlayerUid != null && localPlayerUid != null && plot.ownerPlayerUid != localPlayerUid ->
            FieldOwnerKind.OTHER_PLAYER to (plot.ownerPlayerName ?: "Fazendeiro Online")
        plot.ownerPlayerUid != null && localPlayerUid != null && plot.ownerPlayerUid == localPlayerUid ->
            FieldOwnerKind.OWN_PROPERTY to if (plot.leased && !plot.owned) "Arrendamento ativo" else null
        plot.owned || plot.leased ->
            FieldOwnerKind.OWN_PROPERTY to if (plot.leased && !plot.owned) "Arrendamento ativo" else null
        !plot.ownerNpcName.isNullOrBlank() ->
            FieldOwnerKind.NPC_PROPERTY to plot.ownerNpcName
        else ->
            FieldOwnerKind.AVAILABLE_FOR_PURCHASE to formatBrl(plot.buyPriceBrl)
    }

    // Aggregate soil metrics
    val n = plotCells.size.toDouble()
    val avgPh = plotCells.sumOf { it.phLevel ?: 6.0 } / n
    val avgMoisture = plotCells.sumOf { it.moisture } / n
    val avgCompaction = plotCells.sumOf { it.compaction } / n
    val avgWeed = plotCells.sumOf { it.weedLevel } / n
    val avgPest = plotCells.sumOf { it.pestLevel ?: 0.0 } / n
    val avgFertStages = plotCells.sumOf { (it.fertilizedStages ?: 0).toDouble() } / n
    val avgN = plotCells.sumOf { it.nitrogen ?: 0.58 } / n
    val avgP = plotCells.sumOf { it.phosphorus ?: 0.56 } / n
    val avgK = plotCells.sumOf { it.potassium ?: 0.58 } / n

    val dominantSoilState = plotCells.groupingBy { it.soilState }.eachCount().maxByOrNull { it.value }?.key
        ?: SoilState.PASTAGEM

    val cropCells = plotCells.filter { it.cropId != null }
    val activeCropId = cropCells.groupingBy { it.cropId!! }.eachCount().maxByOrNull { it.value }?.key
    val lastCropCells = plotCells.filter { it.lastCropId != null && it.soilState == SoilState.PALHADA }
    val lastCropId = lastCropCells.groupingBy { it.lastCropId!! }.eachCount().maxByOrNull { it.value }?.key

    val activeCropDef = activeCropId?.let { getCropById(it) }
    val lastCropDef = lastCropId?.let { getCropById(it) }

    // 2.5 Growth 9-stage calculation
    val growthTelemetry: CropGrowthTelemetry? = when {
        activeCropId != null && activeCropDef != null -> {
            val avgProg = (cropCells.sumOf { it.growthProgress } / cropCells.size.toDouble()).coerceIn(0.0, 1.0)
            val readyRatio = cropCells.count { it.soilState == SoilState.PRONTO_COLHEITA || it.growthProgress >= 0.99 } / cropCells.size.toDouble()
            val pct = (avgProg * 100.0).roundToInt().coerceIn(0, 100)
            val stage = when {
                readyRatio >= 0.5 || pct >= 99 -> FieldGrowthStage.PRONTO_PARA_COLHER
                avgProg >= 0.78 -> FieldGrowthStage.MATURACAO
                avgProg >= 0.58 -> FieldGrowthStage.CRESCIMENTO_AVANCADO
                avgProg >= 0.38 -> FieldGrowthStage.CRESCIMENTO_INTERMEDIARIO
                avgProg >= 0.20 -> FieldGrowthStage.CRESCIMENTO_INICIAL
                avgProg >= 0.09 -> FieldGrowthStage.GERMINACAO
                else -> FieldGrowthStage.PLANTIO_REALIZADO
            }
            CropGrowthTelemetry(
                cropId = activeCropId,
                cropName = activeCropDef.name,
                stage = stage,
                growthPct = pct,
                isActiveCrop = true,
            )
        }
        dominantSoilState == SoilState.PALHADA && lastCropDef != null -> {
            CropGrowthTelemetry(
                cropId = lastCropDef.id,
                cropName = lastCropDef.name,
                stage = FieldGrowthStage.COLHIDO,
                growthPct = 100,
                isActiveCrop = false,
            )
        }
        dominantSoilState == SoilState.ARADO || dominantSoilState == SoilState.SUBSOLADO || dominantSoilState == SoilState.PALHADA -> {
            CropGrowthTelemetry(
                cropId = "",
                cropName = "Preparo de Solo",
                stage = FieldGrowthStage.TERRENO_PREPARADO,
                growthPct = 0,
                isActiveCrop = false,
            )
        }
        else -> null
    }

    val plantationStatusLabel = when {
        growthTelemetry != null && growthTelemetry.isActiveCrop ->
            "${growthTelemetry.cropName} • ${growthTelemetry.stage.label}"
        growthTelemetry?.stage == FieldGrowthStage.COLHIDO ->
            "Colhido (${growthTelemetry.cropName})"
        growthTelemetry?.stage == FieldGrowthStage.TERRENO_PREPARADO ->
            "Terreno preparado (sem plantio)"
        dominantSoilState == SoilState.PASTAGEM ->
            "Pastagem / Área livre"
        plot.isPreservation ->
            "Reserva Legal (APP)"
        else ->
            "Não plantada"
    }

    // Check active pivot irrigating this plot
    val (plotCx, plotCz) = if (plot.centerX != null && plot.centerZ != null) {
        plot.centerX to plot.centerZ
    } else {
        0.0 to 0.0
    }
    val activePivot = state.centerPivots.orEmpty().find { pivot ->
        pivot.isActive && pivot.waterReserveL > 5.0 &&
            (pivot.plotId == plotId || hypot(pivot.centerX - plotCx, pivot.centerZ - plotCz) <= pivot.radiusM + 12.0)
    }
    val hasActivePivot = activePivot != null || plotCells.any { it.isIrrigated == true && (it.irrigatedUntilDay ?: 0) >= state.day }

    // 2.3 Soil Conditions
    val hasCropPlanted = activeCropId != null
    val limeAppliedCount = plotCells.count { (it.limedDay ?: 0) > 0 }
    val limeStatus = when {
        avgPh < 5.75 && limeAppliedCount < (plotCells.size * 0.45) -> FieldConditionState.NECESSARIO
        limeAppliedCount >= (plotCells.size * 0.45) || avgPh >= 6.05 -> FieldConditionState.APLICADO
        else -> FieldConditionState.NAO_NECESSARIO
    }

    val plowedCount = plotCells.count { it.soilState == SoilState.ARADO || it.soilState == SoilState.SUBSOLADO || (it.plowedDay ?: 0) > 0 }
    val noTillCount = plotCells.count { it.soilState == SoilState.PALHADA }
    val plowStatus = when {
        hasCropPlanted -> FieldConditionState.NAO_NECESSARIA
        plowedCount >= (plotCells.size * 0.5) -> FieldConditionState.REALIZADA
        noTillCount >= (plotCells.size * 0.5) && avgCompaction < 0.35 -> FieldConditionState.NAO_NECESSARIA
        else -> FieldConditionState.NECESSARIA
    }

    val harrowedCount = plotCells.count { it.isHarrowed == true }
    val unHarrowedPlowedCount = plotCells.count { it.soilState == SoilState.ARADO && it.isHarrowed == false }
    val harrowStatus = when {
        hasCropPlanted -> FieldConditionState.NAO_NECESSARIA
        unHarrowedPlowedCount >= (plotCells.size * 0.35) -> FieldConditionState.NECESSARIA
        harrowedCount >= (plotCells.size * 0.4) -> FieldConditionState.REALIZADA
        else -> FieldConditionState.NAO_NECESSARIA
    }

    // 2.4 Fertilization Level
    val nutrientAvg = ((avgN + avgP + avgK) / 3.0).coerceIn(0.0, 1.0)
    val fertPct = if (avgFertStages > 0.05) {
        ((avgFertStages / 2.0) * 65.0 + nutrientAvg * 35.0).roundToInt().coerceIn(0, 100)
    } else {
        (nutrientAvg * 42.0).roundToInt().coerceIn(0, 100)
    }
    val firstPass = avgFertStages >= 0.55
    val secondPass = avgFertStages >= 1.55
    val highNutrientDemand = (activeCropDef?.nutrientNeed ?: 0.70) >= 0.68
    val fertStatus = when {
        avgFertStages < 0.15 -> FieldConditionState.NAO_APLICADO
        avgFertStages < 0.85 -> FieldConditionState.APLICACAO_PARCIAL
        avgFertStages < 1.60 && highNutrientDemand -> FieldConditionState.NIVEL_INSUFICIENTE
        else -> FieldConditionState.APLICADO
    }
    val needsExtraFert = !secondPass && (fertStatus != FieldConditionState.APLICADO)
    val fertTelemetry = FertilizationTelemetry(
        totalPct = fertPct,
        status = fertStatus,
        statusDetail = when (fertStatus) {
            FieldConditionState.NAO_APLICADO -> "Sem aplicação recente ($fertPct%)"
            FieldConditionState.APLICACAO_PARCIAL -> "1ª aplicação parcial ($fertPct%)"
            FieldConditionState.NIVEL_INSUFICIENTE -> "1ª OK • 2ª cobertura recomendada ($fertPct%)"
            else -> "Adubação completa ($fertPct%)"
        },
        needsAdditionalPass = needsExtraFert,
        firstPassApplied = firstPass,
        secondPassApplied = secondPass,
        nitrogenPct = (avgN * 100.0).roundToInt().coerceIn(0, 100),
        phosphorusPct = (avgP * 100.0).roundToInt().coerceIn(0, 100),
        potassiumPct = (avgK * 100.0).roundToInt().coerceIn(0, 100),
    )

    val herbicideApplied = plotCells.count { (it.herbicideAppliedDay ?: 0) > 0 }
    val herbicideStatus = when {
        avgWeed >= 0.10 || avgPest >= 0.09 -> FieldConditionState.NECESSARIO
        herbicideApplied >= (plotCells.size * 0.4) -> FieldConditionState.APLICADO
        else -> FieldConditionState.NAO_NECESSARIO
    }

    val waterTarget = activeCropDef?.waterNeed ?: 0.58
    val irrigStatus = when {
        hasActivePivot -> FieldConditionState.EM_ANDAMENTO
        avgMoisture < waterTarget * 0.74 || avgMoisture < 0.42 -> FieldConditionState.NECESSARIA
        avgMoisture >= 0.60 -> FieldConditionState.ADEQUADA
        else -> FieldConditionState.NAO_NECESSARIA
    }

    val compPct = (avgCompaction * 100.0).roundToInt()
    val compactionStatus = if (avgCompaction >= 0.42) FieldConditionState.NECESSARIA else FieldConditionState.ADEQUADA

    val soilConditions = listOf(
        SoilConditionItem(
            key = "calcario",
            title = "Calcário (pH)",
            status = limeStatus,
            detail = "pH ${"%.1f".format(avgPh)}",
            needsAttention = limeStatus == FieldConditionState.NECESSARIO,
        ),
        SoilConditionItem(
            key = "aradura",
            title = "Aradura",
            status = plowStatus,
            detail = if (plowStatus == FieldConditionState.REALIZADA) "Solo revolvido" else if (plowStatus == FieldConditionState.NECESSARIA) "Compactação $compPct%" else "Dispensada",
            needsAttention = plowStatus == FieldConditionState.NECESSARIA,
        ),
        SoilConditionItem(
            key = "gradagem",
            title = "Gradagem",
            status = harrowStatus,
            detail = if (harrowStatus == FieldConditionState.REALIZADA) "Solo nivelado" else if (harrowStatus == FieldConditionState.NECESSARIA) "Torrões pós-arado" else "Pronto",
            needsAttention = harrowStatus == FieldConditionState.NECESSARIA,
        ),
        SoilConditionItem(
            key = "fertilizante",
            title = "Fertilizante",
            status = fertStatus,
            detail = fertTelemetry.statusDetail,
            needsAttention = fertStatus == FieldConditionState.NAO_APLICADO || fertStatus == FieldConditionState.NIVEL_INSUFICIENTE || fertStatus == FieldConditionState.APLICACAO_PARCIAL,
        ),
        SoilConditionItem(
            key = "herbicida",
            title = "Herbicida / Defensivo",
            status = herbicideStatus,
            detail = if (herbicideStatus == FieldConditionState.NECESSARIO) "Ervas ${(avgWeed * 100).roundToInt()}%" else "Limpo (${(avgWeed * 100).roundToInt()}%)",
            needsAttention = herbicideStatus == FieldConditionState.NECESSARIO,
        ),
        SoilConditionItem(
            key = "irrigacao",
            title = "Irrigação",
            status = irrigStatus,
            detail = "Umidade ${(avgMoisture * 100).roundToInt()}%",
            needsAttention = irrigStatus == FieldConditionState.NECESSARIA,
        ),
        SoilConditionItem(
            key = "compactacao",
            title = "Condição do Terreno",
            status = compactionStatus,
            detail = if (compPct >= 42) "Subsolagem útil ($compPct%)" else "Boa estrutura ($compPct%)",
            needsAttention = compactionStatus == FieldConditionState.NECESSARIA,
        ),
    )

    // 2.6 Season & Calendar Check
    val season = getSeasonFromMonth(state.month)
    val seasonMeta = SEASON_META[season] ?: SEASON_META.getValue(SeasonType.PRIMAVERA)
    val monthIdx = ((state.month - 1).mod(12))
    val monthName = MONTH_NAMES_PT[monthIdx]

    val candidateCropId = activeCropId ?: activeVehicle?.selectedCropId ?: "soja"
    val evalCrop = getCropById(candidateCropId) ?: ALL_CROPS.first()
    val inRecommendedWindow = evalCrop.plantingMonths.contains(((state.month - 1).mod(12)) + 1)

    val (calendarStatus, calendarDetail) = when {
        growthTelemetry?.stage == FieldGrowthStage.PRONTO_PARA_COLHER ->
            CropCalendarStatus.PERIODO_COLHEITA to "${evalCrop.name} madura — pronta para colher agora."
        growthTelemetry != null && growthTelemetry.isActiveCrop ->
            CropCalendarStatus.PERIODO_CRESCIMENTO to (
                if (inRecommendedWindow) "${evalCrop.name} em desenvolvimento dentro da janela ideal."
                else "${evalCrop.name} em desenvolvimento (plantio fora da janela típica)."
            )
        inRecommendedWindow ->
            CropCalendarStatus.EPOCA_ADEQUADA to "${evalCrop.name}: janela recomendada em $monthName."
        else -> {
            val idealMonths = evalCrop.plantingMonths.joinToString(", ") { MONTH_NAMES_PT[(it - 1).mod(12)].take(3) }
            CropCalendarStatus.FORA_DA_EPOCA to "${evalCrop.name}: fora da época ideal ($idealMonths)."
        }
    }

    // Part 3 Active Implement Operational Context
    val impAdvisory = buildOperationalAdvisory(
        attachedImplement = attachedImplement,
        activeVehicle = activeVehicle,
        growth = growthTelemetry,
        limeStatus = limeStatus,
        fert = fertTelemetry,
        herbicideStatus = herbicideStatus,
        plowStatus = plowStatus,
        harrowStatus = harrowStatus,
        irrigStatus = irrigStatus,
        avgPh = avgPh,
        avgMoisture = avgMoisture,
        calendarStatus = calendarStatus,
    )

    return FieldPanelTelemetry(
        plot = plot,
        areaFormatted = areaFormatted,
        ownerKind = ownerKind,
        ownerSubtitle = ownerSub,
        plantationStatusLabel = plantationStatusLabel,
        dominantSoilState = dominantSoilState,
        soilConditions = soilConditions,
        fertilization = fertTelemetry,
        growth = growthTelemetry,
        season = season,
        seasonLabel = seasonMeta.label,
        monthNumber = ((state.month - 1).mod(12)) + 1,
        monthName = monthName,
        calendarStatus = calendarStatus,
        calendarDetail = calendarDetail,
        avgMoisturePct = (avgMoisture * 100.0).roundToInt(),
        avgPh = (avgPh * 10.0).roundToInt() / 10.0,
        avgWeedPct = (avgWeed * 100.0).roundToInt(),
        avgCompactionPct = compPct,
        activePivotIrrigating = activePivot != null,
        operationalAdvisory = impAdvisory,
    )
}

private fun buildOperationalAdvisory(
    attachedImplement: OwnedImplement?,
    activeVehicle: OwnedVehicle?,
    growth: CropGrowthTelemetry?,
    limeStatus: FieldConditionState,
    fert: FertilizationTelemetry,
    herbicideStatus: FieldConditionState,
    plowStatus: FieldConditionState,
    harrowStatus: FieldConditionState,
    irrigStatus: FieldConditionState,
    avgPh: Double,
    avgMoisture: Double,
    calendarStatus: CropCalendarStatus,
): FieldOperationalAdvisory? {
    val imp = attachedImplement ?: return null
    val def = getImplementDef(imp.defId)
    val selectedCrop = activeVehicle?.selectedCropId?.let { getCropById(it) }
    val fillPct = if (def.capacityL > 0) ((imp.fillL / def.capacityL) * 100.0).roundToInt() else 100

    return when (def.category) {
        ImplementCategory.PLANTADEIRA, ImplementCategory.SEMEADORA -> {
            val readiness = when {
                plowStatus == FieldConditionState.NECESSARIA -> "Solo ainda não arado."
                harrowStatus == FieldConditionState.NECESSARIA -> "Recomenda-se gradagem antes do plantio."
                growth?.isActiveCrop == true -> "Talhão já cultivado com ${growth.cropName}."
                calendarStatus == CropCalendarStatus.FORA_DA_EPOCA -> "Atenção: época fora do calendário recomendado."
                else -> "Solo apto para semeadura."
            }
            FieldOperationalAdvisory(
                implementCategoryLabel = "Plantadeira (${selectedCrop?.name ?: "Soja"})",
                summary = readiness,
                actionHint = "Sementes: $fillPct% • Abaixe e avance no talhão.",
            )
        }
        ImplementCategory.DISTRIBUIDOR -> {
            val mode = if (imp.fillProductId == "calcario") "Calcário" else "Adubo NPK"
            val rec = when {
                limeStatus == FieldConditionState.NECESSARIO -> "Solo ácido (pH $avgPh): recomenda-se Calcário."
                fert.needsAdditionalPass -> "NPK em ${fert.totalPct}%: adubação recomendada."
                else -> "pH e fertilidade equilibrados."
            }
            FieldOperationalAdvisory(
                implementCategoryLabel = "Distribuidor ($mode)",
                summary = rec,
                actionHint = "Insumo: $fillPct% • Alterne Adubo/Calcário no Menu se preciso.",
            )
        }
        ImplementCategory.PULVERIZADOR -> {
            val need = if (herbicideStatus == FieldConditionState.NECESSARIO) {
                "Ervas/pragas detectadas — aplicação necessária!"
            } else {
                "Lavoura controlada — sem infestação crítica."
            }
            FieldOperationalAdvisory(
                implementCategoryLabel = "Pulverizador",
                summary = need,
                actionHint = "Calda: $fillPct% • Abra barras e abaixe para aplicar.",
            )
        }
        ImplementCategory.ARADO, ImplementCategory.GRADE, ImplementCategory.SUBSOLADOR, ImplementCategory.ESCARIFICADOR -> {
            val prep = when {
                plowStatus == FieldConditionState.NECESSARIA -> "Aração necessária nesta área."
                harrowStatus == FieldConditionState.NECESSARIA -> "Gradagem niveladora recomendada pós-arao."
                else -> "Preparo de solo em dia neste talhão."
            }
            FieldOperationalAdvisory(
                implementCategoryLabel = def.model,
                summary = prep,
                actionHint = "Abaixe o hidráulico para preparar a terra.",
            )
        }
        else -> {
            if (irrigStatus == FieldConditionState.NECESSARIA) {
                FieldOperationalAdvisory(
                    implementCategoryLabel = "Monitoramento Hídrico",
                    summary = "Umidade baixa (${(avgMoisture * 100).roundToInt()}%) — ligue um Pivô Central se instalado.",
                    actionHint = "A irrigação eleva a produtividade diária.",
                )
            } else null
        }
    }
}
