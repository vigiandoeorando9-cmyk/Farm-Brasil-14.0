package com.example

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.domain.ALL_BUILTIN_MAPS
import com.example.domain.ALL_IMPLEMENTS
import com.example.domain.ALL_VEHICLES
import com.example.domain.BASE_MAP_1X_AREA_HA
import com.example.domain.BASE_MAP_1X_SIDE_M
import com.example.domain.COMMUNITY_INSPIRED_MAPS
import com.example.domain.CommunityMapConversionResult
import com.example.domain.ConstructionCategory
import com.example.domain.DEFAULT_HUB_PACKS
import com.example.domain.GRAPHICS_PRESET_META
import com.example.domain.GraphicsPreset
import com.example.domain.HUB_BRAND_FILTERS
import com.example.domain.HUB_CATEGORIES
import com.example.domain.HUB_LIFECYCLE_FILTERS
import com.example.domain.HUB_MAP_SIZE_FILTERS
import com.example.domain.HUB_REGION_FILTERS
import com.example.domain.MAP_SIZE_META
import com.example.domain.MapAreaTier
import com.example.domain.MapSizeOption
import com.example.domain.ModAssetDeliveryMode
import com.example.domain.ModValidationStatus
import com.example.domain.PIVOT_MODEL_CATALOG
import com.example.domain.PlotZoneType
import com.example.domain.SoilState
import com.example.domain.StorageQuotaConfig
import com.example.domain.TerrainCell
import com.example.domain.clearCustomConvertedMapsForTest
import com.example.domain.computePackSignature
import com.example.domain.convertAuthorizedCommunityMapManifest
import com.example.domain.filterHubPacks
import com.example.domain.findAllSellStationsInWorld
import com.example.domain.getAvailableMaps
import com.example.domain.getMapById
import com.example.domain.getModAssetDeliveryMode
import com.example.domain.getPivotModelDef
import com.example.domain.registerConvertedCommunityMap
import com.example.domain.validateAndParseCustomModManifest
import com.example.domain.validateHubModQualityGate
import com.example.domain.validateModPack
import com.example.firebase.MultiplayerRoomDoc
import com.example.firebase.evaluateRoomCompatibility
import com.example.simulation.WorldStreamingEngine
import com.example.simulation.buyAndPlaceCenterPivot
import com.example.simulation.canPlaceCenterPivot
import com.example.simulation.createNewWorld
import com.example.simulation.getCellIndexForState
import com.example.simulation.getCellSize
import com.example.simulation.getWorldCellSizeM
import com.example.simulation.getWorldSpanM
import com.example.simulation.isPointBlockedBySolidWorld
import com.example.simulation.refillCenterPivotWater
import com.example.simulation.setCenterPivotSpeed
import com.example.simulation.stepCenterPivotsSimulation
import com.example.simulation.stepSimulation
import com.example.simulation.toggleCenterPivotActive
import com.example.simulation.toggleCenterPivotReverse
import com.example.storage.ModDownloadManager
import com.example.storage.loadWorldFromSlot
import com.example.storage.saveWorldToSlot
import com.example.ui.components.soilCellColor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HubExpansionAndPivotTest {

    @After
    fun tearDown() {
        clearCustomConvertedMapsForTest()
        WorldStreamingEngine.clearCache()
    }

    @Test
    fun part1_mapAreaTiersHaveTrueProportionalAreasAndUnstretchedCore() {
        assertThat(BASE_MAP_1X_SIDE_M).isWithin(0.01).of(500.0)
        assertThat(BASE_MAP_1X_AREA_HA).isWithin(0.01).of(25.0)

        val tiers = listOf(
            MapAreaTier.TIER_4X to Pair(4.0, 100.0),
            MapAreaTier.TIER_6X to Pair(6.0, 150.0),
            MapAreaTier.TIER_8X to Pair(8.0, 200.0),
            MapAreaTier.TIER_12X to Pair(12.0, 300.0),
            MapAreaTier.TIER_16X to Pair(16.0, 400.0),
        )
        for ((tier, expected) in tiers) {
            val (mult, ha) = expected
            assertThat(tier.areaMultiplier).isWithin(0.01).of(mult)
            assertThat(tier.areaHa).isWithin(0.01).of(ha)
            // Side scales by sqrt(areaMultiplier), never confused with linear multiplication
            assertThat(tier.sideM).isWithin(0.5).of(BASE_MAP_1X_SIDE_M * sqrt(mult))
        }

        val world4X = createNewWorld(0, "Sítio 4X", "map_pr", 250_000.0, "Padrão", 128, MapAreaTier.TIER_4X)
        val world16X = createNewWorld(1, "Fazenda Colossal 16X", "map_pr", 500_000.0, "Padrão", 192, MapAreaTier.TIER_16X)

        assertThat(getWorldSpanM(world4X)).isWithin(0.5).of(1000.0)
        assertThat(getWorldSpanM(world16X)).isWithin(0.5).of(2000.0)
        assertThat(world4X.mapAreaTierId).isEqualTo("4X")
        assertThat(world16X.mapAreaTierId).isEqualTo("16X")

        // Core buildings/plots preserve real unstretched world-meter coordinates between 4X and 16X
        val coreHouse4X = world4X.buildings.first { !it.uid.startsWith("bld_vila_") }
        val coreHouse16X = world16X.buildings.first { !it.uid.startsWith("bld_vila_") }
        assertThat(abs(coreHouse16X.x - coreHouse4X.x)).isLessThan(1.0)
        assertThat(abs(coreHouse16X.z - coreHouse4X.z)).isLessThan(1.0)

        // 16X expands outward with extra fields, roads, and secondary hubs/towns instead of stretching 4X
        assertThat(world16X.plots.size).isGreaterThan(world4X.plots.size)
        assertThat(world16X.secondaryPois.orEmpty().size).isAtLeast(3)
        assertThat(findAllSellStationsInWorld(world16X).size).isGreaterThan(findAllSellStationsInWorld(world4X).size)
        assertThat(world4X.secondaryPois.orEmpty().size).isLessThan(world16X.secondaryPois.orEmpty().size)
    }

    @Test
    fun part2and3_communityInspiredMapsAndRegionalIdentities() {
        val expectedCommunityTitles = listOf(
            "Recanto da Alvorada",
            "Mato Piba",
            "Paranazão",
            "Catuai",
            "Santa Helena",
            "Agronorte",
            "Cedro Alto, do Lorenzo",
        )
        val actualTitles = COMMUNITY_INSPIRED_MAPS.map { it.stateName }
        assertThat(actualTitles).containsAtLeastElementsIn(expectedCommunityTitles)

        // Ensure ethical attribution: original regional inspirations, never mislabeled as official external packages
        COMMUNITY_INSPIRED_MAPS.forEach { m ->
            assertThat(m.isCommunityInspired).isTrue()
            assertThat(m.isOfficialCreatorPackage).isFalse()
            assertThat(m.licenseNotice).isNotEmpty()
            assertThat(m.recommendedAreaTierId).isIn(listOf("8X", "12X", "16X"))
        }

        // Regional diversity across Paraná, SC, RS, MT, MS, GO, MG, SP
        val regionalIds = listOf("map_pr", "map_sc", "map_rs", "map_mt", "map_ms", "map_go", "map_mg", "map_sp")
        val regionalMaps = regionalIds.map { getMapById(it) }
        assertThat(regionalMaps.map { it.seed }.toSet().size).isEqualTo(regionalIds.size)
        assertThat(regionalMaps.map { it.reliefProfile }.toSet().size).isAtLeast(3)

        // Community converter rejects unlicensed / misleading manifests
        val rejectedUnlicensed = convertAuthorizedCommunityMapManifest(
            """
            {
              "mapId": "comm_pirata_test",
              "title": "Mapa Pirata",
              "originalCreator": "Autor X",
              "licenseType": "PROPRIETARY_UNLICENSED",
              "creatorAuthorized": false,
              "authorizationReference": "none"
            }
            """.trimIndent(),
        )
        assertThat(rejectedUnlicensed).isInstanceOf(CommunityMapConversionResult.Rejected::class.java)

        val rejectedOfficialSpoof = convertAuthorizedCommunityMapManifest(
            """
            {
              "mapId": "comm_spoof_test",
              "title": "Falso Oficial",
              "originalCreator": "Criador Y",
              "licenseType": "CC-BY-4.0",
              "creatorAuthorized": true,
              "authorizationReference": "https://creativecommons.org/licenses/by/4.0/",
              "isOfficialCreatorPackage": true
            }
            """.trimIndent(),
        )
        assertThat(rejectedOfficialSpoof).isInstanceOf(CommunityMapConversionResult.Rejected::class.java)

        // Accepts legitimately licensed + authorized manifest
        val validResult = convertAuthorizedCommunityMapManifest(
            """
            {
              "mapId": "comm_vale_dourado_cc",
              "title": "Vale Dourado Comunitário",
              "regionName": "Oeste do Paraná",
              "originalCreator": "Coletivo AgroMap BR",
              "licenseType": "CC-BY-4.0",
              "creatorAuthorized": true,
              "authorizationReference": "https://agromap.example/licenca-cc-by-4",
              "areaTierId": "16X",
              "reliefProfile": "COLINAS_SUAVES"
            }
            """.trimIndent(),
        )
        assertThat(validResult).isInstanceOf(CommunityMapConversionResult.AuthorizedConverted::class.java)
        val ok = validResult as CommunityMapConversionResult.AuthorizedConverted
        registerConvertedCommunityMap(ok.mapDef)
        assertThat(getAvailableMaps(emptySet()).any { it.id == "comm_vale_dourado_cc" }).isTrue()
        assertThat(ok.recommendedTier).isEqualTo(MapAreaTier.TIER_16X)
    }

    @Test
    fun part4_giant16XWorldStreamingChunkingLodCullingAndPlatformBudgets() {
        val world16X = createNewWorld(0, "Mega 16X", "map_comm_agronorte", 500_000.0, "Padrão", 192, MapAreaTier.TIER_16X)
        val mobileWindow = WorldStreamingEngine.buildActiveStreamingWindow(
            state = world16X,
            camX = 0.0,
            camZ = 0.0,
            fwdX = 0.0,
            fwdZ = -1.0,
            gfx = GRAPHICS_PRESET_META[GraphicsPreset.CELULAR]!!.config,
            preset = GraphicsPreset.CELULAR,
        )
        val desktopWindow = WorldStreamingEngine.buildActiveStreamingWindow(
            state = world16X,
            camX = 0.0,
            camZ = 0.0,
            fwdX = 0.0,
            fwdZ = -1.0,
            gfx = GRAPHICS_PRESET_META[GraphicsPreset.DESKTOP_PC]!!.config,
            preset = GraphicsPreset.DESKTOP_PC,
        )

        // 192 cells / 16 cells per chunk = 12x12 = 144 total chunks; never loads all 144 simultaneously on mobile
        assertThat(mobileWindow.telemetry.totalChunks).isEqualTo(144)
        assertThat(mobileWindow.telemetry.loadedChunks).isGreaterThan(0)
        assertThat(mobileWindow.telemetry.loadedChunks).isLessThan(mobileWindow.telemetry.totalChunks)
        assertThat(mobileWindow.telemetry.culledObjectsCount).isGreaterThan(0)
        assertThat(mobileWindow.chunks.any { it.lodLevel.name == "LOD0" }).isTrue()
        assertThat(mobileWindow.instancedPropBatches.values.sumOf { it.size }).isEqualTo(mobileWindow.visibleProps.size)

        // Desktop PC budget supports wider chunk streaming radius & RAM ceiling than tight mobile budget
        assertThat(desktopWindow.telemetry.activeChunkRadius).isGreaterThan(mobileWindow.telemetry.activeChunkRadius)
        assertThat(desktopWindow.telemetry.loadedChunks).isAtLeast(mobileWindow.telemetry.loadedChunks)
    }

    @Test
    fun part5_6_9_10_12_hubExpansion18CategoriesQualityGateAndSaveSafeUpdates() {
        val requiredCategories = listOf(
            "Tratores", "Colheitadeiras", "Plantadeiras", "Pulverizadores",
            "Implementos", "Caminhões", "Carretas", "Reboques",
            "Equipamentos agrícolas", "Construções", "Decorações", "Animais",
            "Vegetação", "Máquinas antigas", "Máquinas modernas",
            "Equipamentos de irrigação", "Sistemas de armazenamento", "Equipamentos de fazenda",
        )
        requiredCategories.forEach { cat ->
            assertThat(HUB_CATEGORIES).contains(cat)
            val matches = filterHubPacks(DEFAULT_HUB_PACKS, category = cat)
            assertThat(matches).isNotEmpty()
        }

        // Priority Brazilian brands in filters
        val priorityBrands = listOf("Valmet", "Massey Ferguson", "John Deere", "New Holland", "Agrale", "Case IH", "Ford", "Mercedes-Benz", "Volvo")
        assertThat(HUB_BRAND_FILTERS).containsAtLeastElementsIn(priorityBrands)
        assertThat(HUB_MAP_SIZE_FILTERS).containsExactly("Todos", "4X", "6X", "8X", "12X", "16X").inOrder()
        assertThat(HUB_REGION_FILTERS).containsAtLeast("PR", "SC", "RS", "MT", "MS", "GO", "MG", "SP")
        assertThat(HUB_LIFECYCLE_FILTERS).containsAtLeast("Disponíveis", "Instalados", "Em atualização", "Incompatíveis", "Em desenvolvimento")

        // Expanded vehicle & implement counts (classic + modern machines, planters, sprayers, trucks, trailers)
        assertThat(ALL_VEHICLES.size).isAtLeast(45)
        assertThat(ALL_IMPLEMENTS.size).isAtLeast(35)

        // Quality gate + SHA signature
        val pivotPack = DEFAULT_HUB_PACKS.first { it.packId == "pack_irrigacao_pivos_brasil" }
        val gate = validateHubModQualityGate(pivotPack, allPacks = DEFAULT_HUB_PACKS)
        assertThat(gate.passed).isTrue()
        assertThat(computePackSignature(pivotPack)).startsWith("fb2-")

        // Honest WIP & missing external file states never fake-install
        val wipPack = DEFAULT_HUB_PACKS.first { it.lifecycleState == ModValidationStatus.EM_DESENVOLVIMENTO }
        assertThat(getModAssetDeliveryMode(wipPack)).isEqualTo(ModAssetDeliveryMode.WIP_BLOCKED)
        assertThat(validateModPack(wipPack, DEFAULT_HUB_PACKS).status).isEqualTo(ModValidationStatus.EM_DESENVOLVIMENTO)

        val extPack = DEFAULT_HUB_PACKS.first { it.lifecycleState == ModValidationStatus.AGUARDANDO_ARQUIVOS }
        assertThat(getModAssetDeliveryMode(extPack)).isEqualTo(ModAssetDeliveryMode.EXTERNAL_FILE_REQUIRED)
        assertThat(validateModPack(extPack, DEFAULT_HUB_PACKS, platform = "android").ok).isFalse()

        // Duplicate packId detection
        val dupAttempt = validateAndParseCustomModManifest(
            """{"packId":"pack_cbt_classico","name":"Duplicado","version":"1.0.0"}""",
            DEFAULT_HUB_PACKS,
        )
        assertThat(dupAttempt.toString()).contains("Duplicidade")

        // Save-safe in-place update check using Robolectric Context
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val originalCareer = createNewWorld(4, "Fazenda Preservada", "map_pr", 345_678.0, "Padrão", 128, MapAreaTier.TIER_8X)
        val savedOk = saveWorldToSlot(ctx, originalCareer).first
        assertThat(savedOk).isTrue()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val dm = ModDownloadManager(ctx, scope)
            var wipErr: String? = null
            dm.startDownload(wipPack, DEFAULT_HUB_PACKS, StorageQuotaConfig(), onComplete = {}, onError = { wipErr = it })
            assertThat(wipErr).isNotNull()

            var extErr: String? = null
            dm.startDownload(extPack, DEFAULT_HUB_PACKS, StorageQuotaConfig(), onComplete = {}, onError = { extErr = it })
            assertThat(extErr).isNotNull()

            var updatedPack: com.example.domain.HubPackEntity? = null
            var updateErr: String? = null
            dm.updatePackInPlace(
                pivotPack,
                "2.1.0",
                DEFAULT_HUB_PACKS,
                StorageQuotaConfig(),
                onComplete = { updatedPack = it },
                onError = { updateErr = it },
            )
            val deadline = System.currentTimeMillis() + 5000L
            while (updatedPack == null && updateErr == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(40L)
            }
            assertThat(updateErr).isNull()
            assertThat(updatedPack).isNotNull()
            assertThat(updatedPack!!.version).isEqualTo("2.1.0")
            assertThat(updatedPack!!.installed).isTrue()

            // Career save file remains untouched and intact after mod update
            val reloadedCareer = loadWorldFromSlot(ctx, 4).first
            assertThat(reloadedCareer).isNotNull()
            assertThat(reloadedCareer!!.farmName).isEqualTo("Fazenda Preservada")
            assertThat(reloadedCareer.moneyBrl).isWithin(0.01).of(345_678.0)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun part7and8_centerPivotPlacementControlsIrrigationVisualDarkeningCollisionAndGrowth() {
        assertThat(PIVOT_MODEL_CATALOG.size).isAtLeast(4)
        var world = createNewWorld(0, "Fazenda Irrigada", "map_go", 1_000_000.0, "Padrão", 128, MapAreaTier.TIER_8X)

        // APP (Preservation plot) must reject Center Pivot placement
        val appPlot = world.plots.firstOrNull { it.isPreservation || it.zoneType == PlotZoneType.PRESERVACAO }
        if (appPlot != null) {
            val appCheck = canPlaceCenterPivot(world, getPivotModelDef("pivo_valley_medio_82"), plotId = appPlot.id)
            assertThat(appCheck.ok).isFalse()
        }

        // Owned farmable plot accepts placement
        val ownedPlot = world.plots.first { it.owned && !it.isPreservation }
        val checkOk = canPlaceCenterPivot(world, getPivotModelDef("pivo_valley_medio_82"), plotId = ownedPlot.id)
        assertThat(checkOk.ok).isTrue()

        val initialMoney = world.moneyBrl
        world = buyAndPlaceCenterPivot(world, modelId = "pivo_valley_medio_82", plotId = ownedPlot.id)
        val placed = world.centerPivots.orEmpty().single()
        assertThat(world.moneyBrl).isLessThan(initialMoney)
        assertThat(placed.isActive).isFalse()
        assertThat(placed.waterReserveL).isGreaterThan(10_000.0)

        // Center pad solid collision
        assertThat(isPointBlockedBySolidWorld(world, placed.centerX, placed.centerZ, radius = 0.6)).isTrue()

        // Turn ON, adjust speed & reverse, and step simulation
        world = toggleCenterPivotActive(world, placed.uid)
        assertThat(world.centerPivots!!.single().isActive).isTrue()

        world = setCenterPivotSpeed(world, placed.uid, 12.0)
        assertThat(world.centerPivots!!.single().angularSpeedDegPerSec).isWithin(0.01).of(12.0)

        val startAngle = world.centerPivots!!.single().angleDeg
        val startWater = world.centerPivots!!.single().waterReserveL
        // Seed a couple of dry cells under the initial spray sector so we verify soil wetting + irrigated crop growth
        val cellSize = getWorldCellSizeM(world)
        val half = getWorldSpanM(world) / 2.0
        val dryCells = world.cells.toMutableList()
        var seededIdx: Int? = null
        for (i in dryCells.indices) {
            val c = dryCells[i]
            if (c.plotId == ownedPlot.id && c.soilState !in setOf(SoilState.AGUA_RIO, SoilState.ESTRADA_ASFALTO, SoilState.ESTRADA_TERRA, SoilState.PONTE)) {
                val wx = c.gx * cellSize - half + cellSize * 0.5
                val wz = c.gz * cellSize - half + cellSize * 0.5
                val dx = wx - placed.centerX
                val dz = wz - placed.centerZ
                if (dx in -4.0..4.0 && dz in 10.0..30.0) {
                    dryCells[i] = c.copy(
                        soilState = SoilState.CRESCENDO,
                        cropId = "soja",
                        moisture = 0.25,
                        growthProgress = 0.20,
                        isIrrigated = false,
                    )
                    if (seededIdx == null) seededIdx = i
                }
            }
        }
        world = world.copy(cells = dryCells)

        repeat(4) {
            world = stepCenterPivotsSimulation(world, 0.5)
        }
        val afterPivot = world.centerPivots!!.single()
        assertThat(abs(afterPivot.angleDeg - startAngle)).isGreaterThan(1.0)
        assertThat(afterPivot.waterReserveL).isLessThan(startWater)

        seededIdx?.let { idx ->
            val wettedCell = world.cells[idx]
            assertThat(wettedCell.isIrrigated).isTrue()
            assertThat(wettedCell.moisture).isGreaterThan(0.75)
            // Soil darkens visually when irrigated vs dry cell
            val mapDef = getMapById(world.mapId)
            val drySample = TerrainCell(0, 0, 0.0, ownedPlot.id, SoilState.ARADO, moisture = 0.25, isIrrigated = false)
            val wetSample = TerrainCell(0, 0, 0.0, ownedPlot.id, SoilState.ARADO, moisture = wettedCell.moisture, isIrrigated = true)
            val dryCol = soilCellColor(drySample, Color(0.28f, 0.5f, 0.2f), Color(0.5f, 0.25f, 0.15f), mapDef)
            val wetCol = soilCellColor(wetSample, Color(0.28f, 0.5f, 0.2f), Color(0.5f, 0.25f, 0.15f), mapDef)
            assertThat(wetCol.red).isLessThan(dryCol.red)
        }

        // Reverse direction moves angle in opposite direction
        val beforeRev = afterPivot.angleDeg
        world = toggleCenterPivotReverse(world, placed.uid)
        assertThat(world.centerPivots!!.single().reverseDirection).isTrue()
        world = stepCenterPivotsSimulation(world, 0.5)
        assertThat(world.centerPivots!!.single().angleDeg).isNotEqualTo(beforeRev)

        // Refill water increases reserve up to max
        world = world.copy(centerPivots = world.centerPivots!!.map { it.copy(waterReserveL = 5000.0) })
        world = refillCenterPivotWater(world, placed.uid)
        assertThat(world.centerPivots!!.single().waterReserveL).isWithin(1.0).of(placed.maxWaterReserveL)

        // Crop growth progression compares irrigated vs dry cell
        val baseWorld = world.copy(
            cells = listOf(
                TerrainCell(0, 0, 0.0, ownedPlot.id, SoilState.CRESCENDO, cropId = "soja", growthProgress = 0.20, moisture = 0.30, isIrrigated = false, fertilizedStages = 2, weedLevel = 0.0),
                TerrainCell(1, 0, 0.0, ownedPlot.id, SoilState.CRESCENDO, cropId = "soja", growthProgress = 0.20, moisture = 0.90, isIrrigated = true, irrigatedUntilDay = world.day + 5, fertilizedStages = 2, weedLevel = 0.0),
            ),
            mapGridSize = 2,
        )
        val stepped = stepSimulation(baseWorld, com.example.simulation.ControlInput(), 0.5, 60.0)
        assertThat(stepped.cells.size).isEqualTo(2)
        assertThat(stepped.cells[1].growthProgress).isAtLeast(stepped.cells[0].growthProgress)
        val centerIdx = getCellIndexForState(world, checkOk.centerX, checkOk.centerZ)
        assertThat(centerIdx).isNotNull()
    }

    @Test
    fun part11_multiplayerCompatibilityBlocksMismatchedModsAndAllowsMatchingRoom() {
        val room = MultiplayerRoomDoc(
            roomCode = "AGRO-16X",
            hostUid = "host_1",
            hostName = "Produtor Host",
            mapId = "map_pr",
            mapVersion = "2.0.0",
            mapAreaTierId = "16X",
            gameVersion = "3.0",
            requiredModIds = listOf("pack_cbt_classico", "pack_irrigacao_pivos_brasil"),
            requiredModVersions = mapOf(
                "pack_cbt_classico" to "1.0.2",
                "pack_irrigacao_pivos_brasil" to "2.0.0",
            ),
        )

        // Missing pack_cbt_classico => blocked
        val blockedMissing = evaluateRoomCompatibility(room, DEFAULT_HUB_PACKS)
        assertThat(blockedMissing.isCompatible).isFalse()
        assertThat(blockedMissing.missingModIds).contains("pack_cbt_classico")

        // Outdated version => blocked
        val installedWithOldCbt = DEFAULT_HUB_PACKS.map {
            if (it.packId == "pack_cbt_classico") it.copy(installed = true, enabled = true, version = "0.9.0") else it
        }
        val blockedVersion = evaluateRoomCompatibility(room, installedWithOldCbt)
        assertThat(blockedVersion.isCompatible).isFalse()
        assertThat(blockedVersion.outdatedModIds).contains("pack_cbt_classico")

        // Exact matching installed mods & versions => compatible
        val matchingPacks = DEFAULT_HUB_PACKS.map {
            if (it.packId == "pack_cbt_classico") it.copy(installed = true, enabled = true, version = "1.0.2") else it
        }
        val allowed = evaluateRoomCompatibility(room, matchingPacks)
        assertThat(allowed.isCompatible).isTrue()
        assertThat(allowed.missingModIds).isEmpty()
        assertThat(allowed.outdatedModIds).isEmpty()
    }

    @Test
    fun preservedCoreContracts_nineConstructionCategoriesAndGridAndStorageQuota() {
        assertThat(ConstructionCategory.entries.size).isEqualTo(9)
        assertThat(MAP_SIZE_META[MapSizeOption.GIGANTE_CONTINENTAL]?.gridSide).isEqualTo(192)
        assertThat(getCellSize(192)).isEqualTo(8)
        assertThat(StorageQuotaConfig().mobileLimitMb).isWithin(0.01).of(8192.0)
    }
}
