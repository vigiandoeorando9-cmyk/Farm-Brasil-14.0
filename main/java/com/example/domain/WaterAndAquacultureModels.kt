package com.example.domain

import kotlinx.serialization.Serializable

@Serializable
enum class AquiferAvailabilityTier(
    val label: String,
    val signalRangeMin: Double,
    val signalRangeMax: Double,
    val dailyRechargeBaseL: Double,
    val capacityBaseL: Double,
) {
    BAIXA("Baixa", 0.12, 0.35, 1800.0, 45000.0),
    MEDIA("Média", 0.35, 0.62, 5400.0, 145000.0),
    ALTA("Alta", 0.62, 0.84, 12500.0, 360000.0),
    MUITO_ALTA("Muito alta", 0.84, 1.0, 26000.0, 820000.0),
}

@Serializable
data class RegionalAquiferZone(
    val id: String,
    val name: String = "Aquífero Regional",
    val centerX: Double,
    val centerZ: Double,
    val radiusM: Double,
    val waterTableDepthM: Double,
    val availability: AquiferAvailabilityTier,
    val totalCapacityL: Double,
    val remainingCapacityL: Double,
    val rechargePerDayL: Double,
    val qualityPct: Double = 80.0,
) {
    val waterQualityPct: Double get() = qualityPct
}

@Serializable
data class WaterProspectPoint(
    val uid: String,
    val label: String = "Ponto Hídrico",
    val x: Double,
    val z: Double,
    val estimatedDepthM: Double,
    val availability: AquiferAvailabilityTier,
    val estimatedCapacityL: Double = 120000.0,
    val waterQualityPct: Double,
    val rechargeRateLPerDay: Double,
    val discoveredDay: Int,
    val method: String = "SONDA_MANUAL", // SONDA_MANUAL | PERFURATRIZ
) {
    val availabilityTier: AquiferAvailabilityTier get() = availability
    val supplyCapacityL: Double get() = estimatedCapacityL
}

data class ProspectingScanResult(
    val x: Double,
    val z: Double,
    val signalStrength: Double,
    val estimatedDepthM: Double,
    val availability: AquiferAvailabilityTier,
    val qualityPct: Double,
    val estimatedRechargeLPerDay: Double,
    val audioBeepIntervalMs: Int,
    val summaryLabel: String,
)

@Serializable
enum class PondSizeTier(
    val label: String,
    val defaultRadiusM: Double,
    val defaultDepthM: Double,
    val defaultCapacityL: Double,
    val buildCostBrl: Double,
    val maxFishCapacityKg: Double,
) {
    PEQUENO("Açude Pequeno (12m • 140 mil L)", 12.0, 2.2, 140000.0, 14500.0, 1200.0),
    MEDIO("Açude Médio (18m • 420 mil L)", 18.0, 3.0, 420000.0, 32000.0, 3800.0),
    GRANDE("Açude Grande (26m • 1,05M L)", 26.0, 3.8, 1050000.0, 68000.0, 9500.0),
    LAGO_ARTIFICIAL("Lago Artificial / Represa (36m • 2,4M L)", 36.0, 4.5, 2400000.0, 138000.0, 22000.0),
}

@Serializable
data class FishSpeciesDef(
    val id: String,
    val name: String,
    val scientificName: String,
    val minPondRadiusM: Double,
    val minWaterDepthM: Double,
    val idealO2MgL: Double,
    val idealPhMin: Double,
    val idealPhMax: Double,
    val growthDays: Int,
    val harvestWeightKg: Double,
    val fingerlingCostBrl: Double,
    val marketPricePerKgBrl: Double,
    val feedConversionRatio: Double,
    val description: String,
)

@Serializable
data class PondFishStock(
    val speciesId: String,
    val count: Int,
    val avgWeightKg: Double,
    val biomassKg: Double,
    val growthProgress: Double,
    val stockedDay: Int = 1,
    val healthPct: Double = 95.0,
    val readyToHarvest: Boolean = false,
)

@Serializable
data class PondStructure(
    val uid: String,
    val name: String,
    val sizeTier: PondSizeTier,
    val centerX: Double,
    val centerZ: Double,
    val radiusM: Double,
    val depthM: Double,
    val waterLevelM: Double = 0.0,
    val waterVolumeL: Double = 0.0,
    val maxVolumeL: Double = 140000.0,
    val waterQualityPct: Double = 85.0,
    val dikeIntegrityPct: Double = 96.0,
    val hasPump: Boolean = false,
    val hasPiping: Boolean = false,
    val pumpActive: Boolean = false,
    val piscicultureEnabled: Boolean = false,
    val linkedAquiferId: String? = null,
    val dissolvedO2MgL: Double = 6.5,
    val waterPh: Double = 7.1,
    val turbidityPct: Double = 16.0,
    val temperatureC: Double = 25.5,
    val fishFeedStockKg: Double = 0.0,
    val fishStocks: List<PondFishStock> = emptyList(),
    val createdDay: Int = 1,
) {
    val x: Double get() = centerX
    val z: Double get() = centerZ
    val maxCapacityL: Double get() = maxVolumeL
}

@Serializable
data class WaterBusinessState(
    val hasReservoirStructure: Boolean = false,
    val reservoirVolumeL: Double = 0.0,
    val reservoirMaxL: Double = 120000.0,
    val totalExtractedL: Double = 0.0,
    val totalWaterSoldL: Double = 0.0,
    val totalWaterRevenueBrl: Double = 0.0,
    val totalExpensesBrl: Double = 0.0,
    val totalFishSoldKg: Double = 0.0,
    val totalFishRevenueBrl: Double = 0.0,
)

@Serializable
data class EarthworkMark(
    val uid: String,
    val x: Double,
    val z: Double,
    val headingDeg: Double,
    val radiusM: Double = 1.6,
    val kind: String, // BUCKET | BLADE | TRACK | TIRE | SPOIL | BERM | DRILL_BORE
    val soilTone: String = "SUBSOLO",
    val ageSec: Double = 0.0,
) {
    val widthM: Double get() = radiusM * 1.4
    val lengthM: Double get() = radiusM * 1.6
}

@Serializable
data class DiagnosticEventEntry(
    val timestampMs: Long,
    val level: String, // INFO | WARN | ERROR
    val module: String,
    val functionName: String,
    val message: String,
)

@Serializable
data class RuntimeDiagnosticSnapshot(
    val fpsEstimate: Double = 60.0,
    val frameTimeMs: Double = 16.6,
    val usedRamMb: Double = 0.0,
    val maxRamMb: Double = 256.0,
    val activeCellsCount: Int = 0,
    val activePropsCount: Int = 0,
    val activePondsCount: Int = 0,
    val activeVehiclesCount: Int = 0,
    val errorCount: Int = 0,
    val warningCount: Int = 0,
    val integrityStatus: String = "Sistema operacional sem falhas.",
    val lastPersistenceCheck: String = "Verificado",
    val capturedEvents: List<DiagnosticEventEntry> = emptyList(),
)
