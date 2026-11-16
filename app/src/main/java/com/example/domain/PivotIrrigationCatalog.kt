package com.example.domain

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.roundToInt

@Serializable
data class PivotModelDef(
    val id: String,
    val brand: String,
    val name: String,
    val sizeTier: PivotSizeTier,
    val spanCount: Int,
    val radiusM: Double,
    val towerHeightM: Double,
    val clearanceHeightM: Double,
    val flowRateLPerMin: Double,
    val maxWaterReserveL: Double,
    val angularSpeedDegPerSec: Double,
    val workingPressureBar: Double,
    val purchasePriceBrl: Double,
    val refillCostPer1000LBrl: Double,
    val metallicHex: String,
    val accentHex: String,
    val description: String,
    val requiredModPackId: String? = null,
) {
    val coveredAreaHa: Double
        get() = ((PI * radiusM * radiusM) / 10000.0 * 100.0).roundToInt() / 100.0
}

val PIVOT_MODEL_CATALOG: List<PivotModelDef> = listOf(
    PivotModelDef(
        id = "pivo_fockink_pequeno_48",
        brand = "Fockink",
        name = "Pivô Central Fockink Compact 1L (48m)",
        sizeTier = PivotSizeTier.PEQUENO,
        spanCount = 1,
        radiusM = 48.0,
        towerHeightM = 3.6,
        clearanceHeightM = 2.9,
        flowRateLPerMin = 1250.0,
        maxWaterReserveL = 32000.0,
        angularSpeedDegPerSec = 8.5,
        workingPressureBar = 2.8,
        purchasePriceBrl = 88000.0,
        refillCostPer1000LBrl = 3.20,
        metallicHex = "#B8C4CC",
        accentHex = "#0E7490",
        description = "Pivô compacto de 1 lance em aço galvanizado a fogo com bico aspersor i-Wob e canhão final de baixa pressão para hortaliças, café e pequenos talhões.",
    ),
    PivotModelDef(
        id = "pivo_valley_medio_82",
        brand = "Valley",
        name = "Pivô Central Valley 8000 2 Lances (82m)",
        sizeTier = PivotSizeTier.MEDIO,
        spanCount = 2,
        radiusM = 82.0,
        towerHeightM = 4.1,
        clearanceHeightM = 3.3,
        flowRateLPerMin = 2150.0,
        maxWaterReserveL = 55000.0,
        angularSpeedDegPerSec = 6.2,
        workingPressureBar = 3.2,
        purchasePriceBrl = 168000.0,
        refillCostPer1000LBrl = 2.95,
        metallicHex = "#CBD5E1",
        accentHex = "#15803D",
        description = "Estrutura treliçada galvanizada de 2 torres motrizes com motoredutores selados, tirantes de aço e painel eletrônico de reversão.",
    ),
    PivotModelDef(
        id = "pivo_lindsay_grande_120",
        brand = "Zimmatic / Lindsay",
        name = "Pivô Central Zimmatic 9500P 4 Lances (120m)",
        sizeTier = PivotSizeTier.GRANDE,
        spanCount = 4,
        radiusM = 120.0,
        towerHeightM = 4.6,
        clearanceHeightM = 3.7,
        flowRateLPerMin = 3400.0,
        maxWaterReserveL = 95000.0,
        angularSpeedDegPerSec = 4.6,
        workingPressureBar = 3.6,
        purchasePriceBrl = 295000.0,
        refillCostPer1000LBrl = 2.65,
        metallicHex = "#D6DCE3",
        accentHex = "#0369A1",
        description = "Pivô de alta vazão com 4 lances treliçados, pneus agrícolas de flutuação 14.9-24, telemetria FieldNET e canhão final para grandes lavouras de soja, milho e algodão.",
    ),
    PivotModelDef(
        id = "pivo_irrigabrasil_mega_120",
        brand = "Irrigabrasil",
        name = "Pivô Nacional Irrigabrasil Pro 4L (120m)",
        sizeTier = PivotSizeTier.GRANDE,
        spanCount = 4,
        radiusM = 120.0,
        towerHeightM = 4.5,
        clearanceHeightM = 3.6,
        flowRateLPerMin = 3250.0,
        maxWaterReserveL = 90000.0,
        angularSpeedDegPerSec = 4.8,
        workingPressureBar = 3.4,
        purchasePriceBrl = 274000.0,
        refillCostPer1000LBrl = 2.55,
        metallicHex = "#C4CDD6",
        accentHex = "#D97706",
        description = "Projeto brasileiro robusto com juntas flexíveis de alta durabilidade, tubulação 8\" e controle automático de setor molhado.",
        requiredModPackId = "pack_irrigacao_pivos_brasil",
    ),
)

fun getPivotModelDef(id: String): PivotModelDef =
    PIVOT_MODEL_CATALOG.find { it.id == id } ?: PIVOT_MODEL_CATALOG[1]
