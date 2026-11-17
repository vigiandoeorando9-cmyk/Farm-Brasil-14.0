package com.example.domain

import kotlin.math.roundToInt

private val SOIL_ORANGE = rgb(0.91, 0.56, 0.08)
private val DARK_METAL = rgb(0.16, 0.18, 0.21)
private val WATER_BLUE = rgb(0.16, 0.52, 0.82)
private val WHITE_TANK = rgb(0.93, 0.95, 0.97)
private val CAT_YELLOW = rgb(0.92, 0.68, 0.08)
private val SANDVIK_ORANGE = rgb(0.88, 0.34, 0.08)

val EARTHWORK_AND_WATER_VEHICLES: List<VehicleDef> = listOf(
    VehicleDef(
        id = "sonda_geo_pd120",
        brand = "GeoSonda",
        model = "PD-120 Perfuratriz Rural",
        category = VehicleCategory.PERFURATRIZ,
        era = "Moderno",
        modelYear = 2024,
        horsepower = 145.0,
        maxTorqueNm = 620.0,
        maxSpeedKmh = 36.0,
        fuelCapacityL = 220.0,
        weightKg = 11200.0,
        priceBrl = 465000.0,
        supportedHitches = listOf(HitchType.DRAWBAR),
        primaryColorRgb = SANDVIK_ORANGE,
        accentColorRgb = DARK_METAL,
        bodyStyle = VehicleBodyStyle.DRILLING_RIG,
        wheelLayout = WheelLayout.CRAWLER_TRACKS,
        hasCab = true,
        dimensionsM = dim(6.2, 2.35, 3.4),
        turnRateDegPerSec = 56.0,
        soundProfile = "Perfuratriz Hidráulica Rotativa",
        hasGrainTank = false,
        tankCapacityL = 0.0,
        gpsReady = true,
        description = "Perfuratriz sobre esteiras com torre vertical para sondagem geológica profunda e perfuração de poços e reservas subterrâneas.",
        subTypeLabel = "Perfuratriz & Sondagem",
    ),
    VehicleDef(
        id = "epiroc_t35_rural",
        brand = "Epiroc",
        model = "SmartRoc T35 Hidro-Sonda",
        category = VehicleCategory.PERFURATRIZ,
        era = "Moderno",
        modelYear = 2025,
        horsepower = 225.0,
        maxTorqueNm = 940.0,
        maxSpeedKmh = 32.0,
        fuelCapacityL = 340.0,
        weightKg = 15800.0,
        priceBrl = 740000.0,
        supportedHitches = listOf(HitchType.DRAWBAR),
        primaryColorRgb = CAT_YELLOW,
        accentColorRgb = DARK_METAL,
        bodyStyle = VehicleBodyStyle.DRILLING_RIG,
        wheelLayout = WheelLayout.CRAWLER_TRACKS,
        hasCab = true,
        dimensionsM = dim(7.1, 2.55, 3.65),
        turnRateDegPerSec = 52.0,
        soundProfile = "Perfuratriz Pesada DTH",
        hasGrainTank = false,
        tankCapacityL = 0.0,
        gpsReady = true,
        description = "Equipamento de sondagem de alta precisão que mapeia lençol freático, vazão e qualidade hídrica até 60m de profundidade.",
        subTypeLabel = "Perfuratriz & Sondagem",
    ),
    VehicleDef(
        id = "vw_constellation_pipa_12k",
        brand = "Volkswagen",
        model = "Constellation 17.280 Pipa 12.000L",
        category = VehicleCategory.CAMINHAO_PIPA,
        era = "Moderno",
        modelYear = 2024,
        horsepower = 275.0,
        maxTorqueNm = 1050.0,
        maxSpeedKmh = 90.0,
        fuelCapacityL = 380.0,
        weightKg = 8400.0,
        priceBrl = 445000.0,
        supportedHitches = listOf(HitchType.DRAWBAR),
        primaryColorRgb = WHITE_TANK,
        accentColorRgb = WATER_BLUE,
        bodyStyle = VehicleBodyStyle.WATER_TANKER,
        wheelLayout = WheelLayout.STANDARD,
        hasCab = true,
        dimensionsM = dim(7.5, 2.48, 3.15),
        turnRateDegPerSec = 42.0,
        soundProfile = "Caminhão-Pipa Motobomba",
        hasGrainTank = false,
        tankCapacityL = 12000.0,
        gpsReady = true,
        description = "Caminhão-pipa com tanque pressurizado de 12.000L, motobomba acoplada e engate rápido para comercialização de água e abastecimento de açudes.",
        subTypeLabel = "Caminhão-Pipa",
    ),
    VehicleDef(
        id = "volvo_vm270_pipa_18k",
        brand = "Volvo",
        model = "VM 270 6x4 Pipa 18.000L",
        category = VehicleCategory.CAMINHAO_PIPA,
        era = "Moderno",
        modelYear = 2025,
        horsepower = 330.0,
        maxTorqueNm = 1280.0,
        maxSpeedKmh = 88.0,
        fuelCapacityL = 440.0,
        weightKg = 10200.0,
        priceBrl = 595000.0,
        supportedHitches = listOf(HitchType.DRAWBAR),
        primaryColorRgb = WATER_BLUE,
        accentColorRgb = WHITE_TANK,
        bodyStyle = VehicleBodyStyle.WATER_TANKER,
        wheelLayout = WheelLayout.STANDARD,
        hasCab = true,
        dimensionsM = dim(8.4, 2.52, 3.25),
        turnRateDegPerSec = 39.0,
        soundProfile = "Volvo 6x4 Pipa Pesado",
        hasGrainTank = false,
        tankCapacityL = 18000.0,
        gpsReady = true,
        description = "Caminhão-pipa 6x4 fora-de-estrada de 18.000 litros para transporte comercial de água entre fontes, estações e centros distribuidores.",
        subTypeLabel = "Caminhão-Pipa",
    ),
    VehicleDef(
        id = "mb_arocs_basculante_20m3",
        brand = "Mercedes-Benz",
        model = "Arocs 4151 8x4 Basculante 20m³",
        category = VehicleCategory.CAMINHAO,
        era = "Moderno",
        modelYear = 2025,
        horsepower = 510.0,
        maxTorqueNm = 2400.0,
        maxSpeedKmh = 84.0,
        fuelCapacityL = 500.0,
        weightKg = 14200.0,
        priceBrl = 940000.0,
        supportedHitches = listOf(HitchType.DRAWBAR),
        primaryColorRgb = SOIL_ORANGE,
        accentColorRgb = DARK_METAL,
        bodyStyle = VehicleBodyStyle.TRUCK,
        wheelLayout = WheelLayout.STANDARD,
        hasCab = true,
        dimensionsM = dim(8.8, 2.6, 3.45),
        turnRateDegPerSec = 36.0,
        soundProfile = "MB Arocs Pesado Basculante",
        hasGrainTank = true,
        tankCapacityL = 24000.0,
        gpsReady = true,
        description = "Caminhão basculante fora-de-estrada 8x4 com caçamba reforçada de 20m³ para transporte e despejo de terra escavada em barragens e diques.",
        subTypeLabel = "Basculante Terraplanagem",
    ),
)

fun isEarthmovingVehicle(def: VehicleDef): Boolean = when (def.bodyStyle) {
    VehicleBodyStyle.EXCAVATOR,
    VehicleBodyStyle.BACKHOE,
    VehicleBodyStyle.GRADER,
    VehicleBodyStyle.DOZER,
    VehicleBodyStyle.LOADER,
    VehicleBodyStyle.DRILLING_RIG,
    -> true
    VehicleBodyStyle.TRUCK -> def.subTypeLabel.contains("Basculante", ignoreCase = true) ||
        def.subTypeLabel.contains("Off-Road", ignoreCase = true) ||
        def.id.contains("basculante", ignoreCase = true) ||
        def.id.contains("caçamba", ignoreCase = true) ||
        def.id.contains("cacamba", ignoreCase = true) ||
        def.model.contains("Caçamba", ignoreCase = true) ||
        def.model.contains("Basculante", ignoreCase = true)
    else -> false
}

fun isWaterTankerVehicle(def: VehicleDef): Boolean =
    def.bodyStyle == VehicleBodyStyle.WATER_TANKER ||
        def.category == VehicleCategory.CAMINHAO_PIPA ||
        def.id.contains("pipa", ignoreCase = true) ||
        def.model.contains("Pipa", ignoreCase = true)

fun getEarthCapacityM3(def: VehicleDef): Double = when (def.bodyStyle) {
    VehicleBodyStyle.EXCAVATOR -> 3.5
    VehicleBodyStyle.BACKHOE -> 1.6
    VehicleBodyStyle.LOADER -> 4.2
    VehicleBodyStyle.DOZER -> 2.8
    VehicleBodyStyle.GRADER -> 1.8
    VehicleBodyStyle.TRUCK -> when {
        def.id.contains("arocs") -> 20.0
        def.tankCapacityL >= 20000.0 -> 16.0
        else -> 12.0
    }
    else -> 0.0
}

fun getWaterTankCapacityL(def: VehicleDef): Double = when {
    def.category == VehicleCategory.CAMINHAO_PIPA -> def.tankCapacityL.coerceAtLeast(12000.0)
    isWaterTankerVehicle(def) -> def.tankCapacityL.coerceAtLeast(10000.0)
    else -> 0.0
}

val FISH_SPECIES_CATALOG: List<FishSpeciesDef> = listOf(
    FishSpeciesDef(
        id = "tilapia_nilo",
        name = "Tilápia-do-Nilo",
        scientificName = "Oreochromis niloticus",
        minPondRadiusM = 8.0,
        minWaterDepthM = 1.1,
        idealO2MgL = 5.5,
        idealPhMin = 6.5,
        idealPhMax = 8.2,
        growthDays = 6,
        harvestWeightKg = 0.95,
        fingerlingCostBrl = 1.60,
        marketPricePerKgBrl = 14.50,
        feedConversionRatio = 1.45,
        description = "Espécie mais cultivada no Brasil, rústica e de rápido giro econômico em açudes pequenos, médios ou grandes.",
    ),
    FishSpeciesDef(
        id = "tambaqui",
        name = "Tambaqui",
        scientificName = "Colossoma macropomum",
        minPondRadiusM = 11.0,
        minWaterDepthM = 1.5,
        idealO2MgL = 5.2,
        idealPhMin = 6.2,
        idealPhMax = 7.8,
        growthDays = 9,
        harvestWeightKg = 2.40,
        fingerlingCostBrl = 3.20,
        marketPricePerKgBrl = 21.80,
        feedConversionRatio = 1.60,
        description = "Peixe amazônico nobre de alto valor no mercado nacional; ótimo rendimento de filé e costela.",
    ),
    FishSpeciesDef(
        id = "pacu",
        name = "Pacu Caranha",
        scientificName = "Piaractus mesopotamicus",
        minPondRadiusM = 10.0,
        minWaterDepthM = 1.3,
        idealO2MgL = 5.4,
        idealPhMin = 6.4,
        idealPhMax = 8.0,
        growthDays = 8,
        harvestWeightKg = 1.85,
        fingerlingCostBrl = 2.75,
        marketPricePerKgBrl = 19.40,
        feedConversionRatio = 1.55,
        description = "Adaptado às bacias do Paraná e Centro-Oeste, excelente conversão alimentar com ração balanceada.",
    ),
    FishSpeciesDef(
        id = "pintado",
        name = "Pintado / Surubim",
        scientificName = "Pseudoplatystoma corruscans",
        minPondRadiusM = 14.0,
        minWaterDepthM = 1.8,
        idealO2MgL = 6.2,
        idealPhMin = 6.6,
        idealPhMax = 7.8,
        growthDays = 11,
        harvestWeightKg = 3.60,
        fingerlingCostBrl = 7.40,
        marketPricePerKgBrl = 36.50,
        feedConversionRatio = 1.75,
        description = "Pescado nobre sem espinhos em Y; exige boa oxigenação da água e rende alta margem no Mercado de Peixes.",
    ),
    FishSpeciesDef(
        id = "pirarucu",
        name = "Pirarucu Gigante",
        scientificName = "Arapaima gigas",
        minPondRadiusM = 20.0,
        minWaterDepthM = 2.4,
        idealO2MgL = 4.8,
        idealPhMin = 6.2,
        idealPhMax = 7.6,
        growthDays = 14,
        harvestWeightKg = 11.50,
        fingerlingCostBrl = 28.00,
        marketPricePerKgBrl = 42.00,
        feedConversionRatio = 1.80,
        description = "O gigante brasileiro da piscicultura; requer açudes grandes ou lagos artificiais com alta lâmina d'água.",
    ),
    FishSpeciesDef(
        id = "carpa_capim",
        name = "Carpa-Capim & Húngara",
        scientificName = "Ctenopharyngodon idella",
        minPondRadiusM = 9.0,
        minWaterDepthM = 1.2,
        idealO2MgL = 5.0,
        idealPhMin = 6.4,
        idealPhMax = 8.4,
        growthDays = 7,
        harvestWeightKg = 2.10,
        fingerlingCostBrl = 2.10,
        marketPricePerKgBrl = 15.80,
        feedConversionRatio = 1.38,
        description = "Ajuda no equilíbrio natural de vegetação aquática no açude e se reproduz com facilidade.",
    ),
)

fun getFishSpeciesById(id: String): FishSpeciesDef =
    FISH_SPECIES_CATALOG.firstOrNull { it.id == id } ?: FISH_SPECIES_CATALOG.first()

val WATER_AND_FISH_LANDMARKS: List<MapLandmark> = listOf(
    MapLandmark(
        id = "lm_centro_agua",
        name = "Centro de Comercialização de Água",
        category = "Água & Logística",
        worldX = -112.0,
        worldZ = 142.0,
        description = "Recepção e venda comercial de água bruta e potável via caminhão-pipa.",
    ),
    MapLandmark(
        id = "lm_estacao_agua",
        name = "Estação de Abastecimento de Água",
        category = "Água & Captação",
        worldX = 118.0,
        worldZ = 128.0,
        description = "Torre de captação, bombas e carregamento rápido de caminhões-pipa.",
    ),
    MapLandmark(
        id = "lm_mercado_peixes",
        name = "Mercado de Peixes Brasil",
        category = "Piscicultura",
        worldX = -146.0,
        worldZ = 78.0,
        description = "Compra e venda direta de tilápia, tambaqui, pacu, pintado e pirarucu.",
    ),
    MapLandmark(
        id = "lm_coop_piscicultura",
        name = "Cooperativa de Piscicultura",
        category = "Piscicultura",
        worldX = 148.0,
        worldZ = 84.0,
        description = "Alevinos selecionados, ração para peixes, aeradores e escoamento de despesca.",
    ),
)

val WATER_AND_FISH_ESTABLISHMENTS: List<EstablishmentDef> = listOf(
    EstablishmentDef(
        id = "centro_comercializacao_agua",
        name = "Centro de Comercialização de Água",
        type = "Comércio Hídrico",
        acceptedProductIds = listOf("agua_pipa", "agua_reservatorio"),
        priceMultiplier = 1.08,
        capacityTons = 8000.0,
        openHour = 5,
        closeHour = 22,
        hasWeighbridge = true,
        worldX = -112.0,
        worldZ = 142.0,
    ),
    EstablishmentDef(
        id = "mercado_peixes",
        name = "Mercado de Peixes Brasil",
        type = "Piscicultura",
        acceptedProductIds = listOf("peixe_tilapia_nilo", "peixe_tambaqui", "peixe_pacu", "peixe_pintado", "peixe_pirarucu", "peixe_carpa_capim"),
        priceMultiplier = 1.12,
        capacityTons = 2500.0,
        openHour = 5,
        closeHour = 20,
        hasWeighbridge = true,
        worldX = -146.0,
        worldZ = 78.0,
    ),
    EstablishmentDef(
        id = "cooperativa_piscicultura",
        name = "Cooperativa de Piscicultura",
        type = "Piscicultura",
        acceptedProductIds = listOf("peixe_tilapia_nilo", "peixe_tambaqui", "peixe_pacu", "peixe_pintado", "peixe_pirarucu", "peixe_carpa_capim"),
        priceMultiplier = 1.06,
        capacityTons = 4000.0,
        openHour = 6,
        closeHour = 21,
        hasWeighbridge = true,
        worldX = 148.0,
        worldZ = 84.0,
    ),
)
