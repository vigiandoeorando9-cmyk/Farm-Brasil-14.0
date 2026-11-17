package com.example.state

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AmbientAudioEngine
import com.example.audio.VehicleAudioSnapshot
import com.example.auth.FarmAccountManager
import com.example.auth.FarmAccountResult
import com.example.auth.GoogleAuthManager
import com.example.domain.AccountSyncStatus
import com.example.domain.ACCESSORY_OPTIONS
import com.example.domain.ALL_CROPS
import com.example.domain.ANIMAL_META
import com.example.domain.AnimalSpecies
import com.example.domain.AppRoute
import com.example.domain.AppRoute.Gameplay
import com.example.domain.AppRoute.MainMenu
import com.example.domain.BuildableStructureDef
import com.example.domain.BuildingType
import com.example.domain.CAB_OPTIONS
import com.example.domain.CHARACTER_PRESETS
import com.example.domain.CameraMode
import com.example.domain.CloudSaveConflictState
import com.example.domain.CloudSaveSummary
import com.example.domain.CustomModParseResult
import com.example.domain.DEFAULT_PLAYER_APPEARANCE
import com.example.domain.ENGINE_MODELS
import com.example.domain.ENGINE_UPGRADES
import com.example.domain.EXHAUST_OPTIONS
import com.example.domain.EstablishmentDef
import com.example.domain.FarmAccountUser
import com.example.domain.GRAPHICS_PRESET_META
import com.example.domain.GameSettings
import com.example.domain.GraphicsConfig
import com.example.domain.GraphicsPreset
import com.example.domain.HEADLIGHT_OPTIONS
import com.example.domain.HudLayoutConfig
import com.example.domain.HubPackEntity
import com.example.domain.INDUSTRY_CHAINS
import com.example.domain.LandUnit
import com.example.domain.LedgerEntry
import com.example.domain.ManualTool
import com.example.domain.ModDownloadProgress
import com.example.domain.ModValidationStatus
import com.example.domain.PAINT_PALETTE
import com.example.domain.PlayerAppearanceConfig
import com.example.domain.RIM_COLORS
import com.example.domain.StorageQuotaConfig
import com.example.domain.TURBO_OPTIONS
import com.example.domain.WEATHER_META
import com.example.domain.WHEEL_OPTIONS
import com.example.domain.WorkshopComponent
import com.example.domain.WorldState
import com.example.domain.detectRecommendedGraphicsPreset
import com.example.domain.getVehicleDef
import com.example.domain.validateAndParseCustomModManifest
import com.example.firebase.CloudUploadOutcome
import com.example.firebase.FarmCloudRepository
import com.example.firebase.MultiplayerRepository
import com.example.firebase.MultiplayerRoomDoc
import com.example.firebase.RoomChatDoc
import com.example.firebase.RoomMemberDoc
import com.example.simulation.ControlInput
import com.example.simulation.EMPTY_CONTROL_INPUT
import com.example.simulation.HireOutsourcingParams
import com.example.simulation.PivotPlacementCheck
import com.example.simulation.WorkshopQuote
import com.example.simulation.acceptOrCompleteContractInWorld
import com.example.simulation.advanceDay
import com.example.simulation.applyContourProtectionInWorld
import com.example.simulation.applyLimeToPlotInWorld
import com.example.simulation.assignEmployeeInWorld
import com.example.simulation.buyImplementInWorld
import com.example.simulation.buyInputStockInWorld
import com.example.simulation.buyVehicleInWorld
import com.example.simulation.createNewWorld
import com.example.simulation.cycleWeather
import com.example.simulation.engageTowRescueInWorld
import com.example.simulation.fireEmployeeInWorld
import com.example.simulation.formatBrl
import com.example.simulation.hireEmployeeInWorld
import com.example.simulation.hireOutsourcingServiceInWorld
import com.example.simulation.inspectVehicle
import com.example.simulation.irrigatePlotInWorld
import com.example.simulation.leasePlotWithTermInWorld
import com.example.simulation.mergeAdjacentPlotsInWorld
import com.example.simulation.performManualAction
import com.example.simulation.performPreventiveMaintenance
import com.example.simulation.placeAuctionBidInWorld
import com.example.simulation.placeBuildingInWorld
import com.example.simulation.rateOutsourcingContractInWorld
import com.example.simulation.refillImplementReservoirInWorld
import com.example.simulation.refillSprayerInWorld
import com.example.simulation.refuelVehicleInWorld
import com.example.simulation.repairVehicleComponent
import com.example.simulation.repairVehicleFull
import com.example.simulation.repayBankLoan
import com.example.simulation.resolveOutsourcingIncidentInWorld
import com.example.simulation.sellAnimalHeadInWorld
import com.example.simulation.sellPlotInWorld
import com.example.simulation.sellProductAtWeighbridge
import com.example.simulation.sellVehicleInWorld
import com.example.simulation.setGpsPoint
import com.example.simulation.sprayPlotPestsInWorld
import com.example.simulation.stepSimulation
import com.example.simulation.takeBankLoan
import com.example.simulation.adjustCruiseSpeedInWorld
import com.example.simulation.toggleCruiseControlInWorld
import com.example.simulation.toggleEnterExitVehicle
import com.example.simulation.toggleFieldCreationMode
import com.example.simulation.toggleGpsAutoSteer
import com.example.simulation.toggleHarvesterHeader
import com.example.simulation.toggleHitchNearestImplement
import com.example.simulation.toggleImplementLowered
import com.example.simulation.toggleSprayerInWorld
import com.example.simulation.toggleVehicle4wd
import com.example.simulation.toggleVehicleEngineInWorld
import com.example.simulation.towAndRecoverVehicle
import com.example.simulation.unloadGrainInWorld
import com.example.simulation.washVehicle
import com.example.storage.CareerImportResult
import com.example.storage.CareerSlotMeta
import com.example.storage.FarmAccountSyncManager
import com.example.storage.ModDownloadManager
import com.example.storage.StorageBreakdownReport
import com.example.storage.SyncBundleRestoreResult
import com.example.storage.buildCloudSaveUploadPayload
import com.example.storage.clearBackupSlots
import com.example.storage.cloudSaveDocToSummary
import com.example.storage.deleteCareerSlot
import com.example.storage.detectMissingModsForCloudSave
import com.example.storage.exportCareerToJson
import com.example.storage.getOrCreateDeviceIdentity
import com.example.storage.getStorageBreakdown
import com.example.storage.installHubPackInList
import com.example.storage.listCareerSlots
import com.example.storage.loadHubFavorites
import com.example.storage.loadHubPacks
import com.example.storage.loadSettings
import com.example.storage.loadStorageQuotaConfig
import com.example.storage.loadWorldFromSlot
import com.example.storage.parseAndVerifyCareerImport
import com.example.storage.saveHubFavorites
import com.example.storage.saveHubPacks
import com.example.storage.saveSettings
import com.example.storage.saveStorageQuotaConfig
import com.example.storage.saveWorldToSlot
import com.example.storage.toggleHubPackEnabledInList
import com.example.storage.uninstallHubPackInList
import com.example.storage.updateHubPackInList
import com.example.storage.worldStateFromCloudSaveDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

import com.example.domain.BUILDING_META
import com.example.domain.BrazilianMapDef
import com.example.domain.CommunityMapConversionResult
import com.example.domain.ESTABLISHMENTS
import com.example.domain.ImplementCategory
import com.example.domain.MapAreaTier
import com.example.domain.SeasonType
import com.example.domain.WeatherType
import com.example.domain.convertAuthorizedCommunityMapManifest
import com.example.domain.findAllSellStationsInWorld
import com.example.domain.getAvailableMaps
import com.example.domain.getCustomConvertedMaps
import com.example.domain.getHubMeta
import com.example.domain.getImplementDef
import com.example.domain.getMapById
import com.example.domain.getVehiclePhysicsSpec
import com.example.domain.registerConvertedCommunityMap
import com.example.domain.FieldInfoPanelConfig
import com.example.domain.FieldPanelCorner
import com.example.domain.FieldPanelSize
import com.example.domain.FieldPanelViewMode
import com.example.domain.HitchMode
import com.example.firebase.MultiplayerCompatibilityReport
import com.example.firebase.evaluateRoomCompatibility
import com.example.simulation.ConstructionPlacementPreview
import com.example.simulation.FieldPanelTelemetry
import com.example.simulation.HitchCandidateEvaluation
import com.example.simulation.MultiplayerSyncCodec
import com.example.simulation.StreamingTelemetrySnapshot
import com.example.simulation.analyzeFieldPlot
import com.example.simulation.buyAndPlaceCenterPivot
import com.example.simulation.canPlaceCenterPivot
import com.example.simulation.confirmConstructionPurchase
import com.example.simulation.createInitialConstructionPreview
import com.example.simulation.customizeOwnedHouseBuilding
import com.example.simulation.depositToBank
import com.example.simulation.detectFieldPlotWithHysteresis
import com.example.simulation.evaluateOnFootHitch
import com.example.simulation.evaluatePropertyEvolution
import com.example.simulation.evaluateVehicleHitchPair
import com.example.simulation.evaluateVehicleUnhitch
import com.example.simulation.executeValidatedHitch
import com.example.simulation.executeValidatedUnhitch
import com.example.simulation.findBestVehicleHitchCandidate
import com.example.simulation.getGridSide
import com.example.simulation.refillCenterPivotWater
import com.example.simulation.resolveVehicleDrivetrainSpec
import com.example.simulation.sellCargoAtNearbyStation
import com.example.simulation.setCenterPivotSpeed
import com.example.simulation.setDistributorProductInWorld
import com.example.simulation.toggleCenterPivotActive
import com.example.simulation.toggleCenterPivotReverse
import com.example.simulation.togglePtoInWorld
import com.example.simulation.toggleVehicleHandbrakeInWorld
import com.example.simulation.toggleAugerPipeUnfold
import com.example.simulation.toggleDifferentialLock
import com.example.simulation.toggleBeaconLights
import com.example.simulation.adjustFrontLoaderBoom
import com.example.simulation.adjustFrontLoaderBucket
import com.example.simulation.unloadAttachedTrailerInWorld
import com.example.simulation.updateConstructionPreviewTransform
import com.example.domain.PondSizeTier
import com.example.domain.ProspectingScanResult
import com.example.domain.RuntimeDiagnosticSnapshot
import com.example.domain.UsbSteeringWheelConfig
import com.example.domain.WheelAxisTelemetry
import com.example.domain.VehicleCategory
import com.example.domain.WATER_AND_FISH_ESTABLISHMENTS
import com.example.domain.WATER_AND_FISH_LANDMARKS
import com.example.domain.isEarthmovingVehicle
import com.example.domain.isWaterTankerVehicle
import com.example.simulation.DiagnosticEngine
import com.example.simulation.adjustEarthworkHydraulicInWorld
import com.example.simulation.buildCommercialWaterReservoirInWorld
import com.example.simulation.buildOrConvertExcavationToPondInWorld
import com.example.simulation.buyAndFeedPondFish
import com.example.simulation.buyWaterProspectingToolInWorld
import com.example.simulation.dumpEarthFromMachineInWorld
import com.example.simulation.enablePiscicultureOnPond
import com.example.simulation.ensureWaterAndFishWorldInitialized
import com.example.simulation.evaluateProspectingAt
import com.example.simulation.executeEarthworkActionInWorld
import com.example.simulation.fillWaterTankerInWorld
import com.example.simulation.harvestPondFish
import com.example.simulation.installPondPumpAndPipingInWorld
import com.example.simulation.maintainPondWaterQuality
import com.example.simulation.markWaterProspectPointInWorld
import com.example.simulation.performDrillingRigSoundingInWorld
import com.example.simulation.pumpWaterFromPondToReservoir
import com.example.simulation.sellHarvestedFishAtMarket
import com.example.simulation.sellWaterCommercialDeliveryInWorld
import com.example.simulation.stockFishFingerlingsInPond
import com.example.simulation.upgradePondDikesInWorld
import com.example.simulation.withdrawFromBank
import kotlinx.coroutines.CancellationException
import kotlin.math.hypot

enum class InGameTab {
    MENU,
    LOJA,
    ECONOMIA,
    FROTA,
    OFICINA,
    CONSTRUCAO,
    CONTRATOS,
    CONFIGURACOES,
    TERCEIRIZACAO,
    INDUSTRIAS,
    PECUARIA,
    EQUIPE,
    AGUA_PESCA,
    CALENDARIO,
}

val IN_GAME_TAB_TITLES: Map<InGameTab, String> = mapOf(
    InGameTab.MENU to "Menu da Fazenda",
    InGameTab.LOJA to "Loja & Concessionária",
    InGameTab.ECONOMIA to "Banco & Balança",
    InGameTab.FROTA to "Garagem & Frota",
    InGameTab.OFICINA to "Oficina",
    InGameTab.CONSTRUCAO to "Propriedades & Obras",
    InGameTab.CONTRATOS to "Missões, Contratos & DRE",
    InGameTab.CONFIGURACOES to "Configurações da Partida",
    InGameTab.TERCEIRIZACAO to "Terceirização",
    InGameTab.INDUSTRIAS to "Indústrias",
    InGameTab.PECUARIA to "Pecuária",
    InGameTab.EQUIPE to "Equipe & Solo",
    InGameTab.AGUA_PESCA to "Água, Açudes & Peixes",
    InGameTab.CALENDARIO to "${ALL_CROPS.size} Culturas",
)

enum class ContextualActionKind {
    ENTER_VEHICLE,
    EXIT_VEHICLE,
    HITCH_IMPLEMENT,
    UNLOAD_GRAIN,
    RESCUE_VEHICLE,
    MANUAL_INTERACT,
    SELL_AT_STATION,
    REFUEL_AT_STATION,
    OPEN_BANK,
    OPEN_DEALERSHIP,
    OPEN_AGRO_STORE,
    OPEN_WORKSHOP,
    OPEN_LIVESTOCK,
    OPEN_CONSTRUCTION,
    OPEN_AGUA_PESCA,
    FILL_WATER_TANKER,
    SELL_WATER_COMMERCIAL,
    SELL_FISH_MARKET,
    EXECUTE_EARTHWORK,
}

data class ContextualInteraction(
    val label: String,
    val kind: ContextualActionKind,
)

fun resolveContextualInteraction(state: WorldState): ContextualInteraction? {
    val activeVeh = state.activeVehicleUid?.let { uid -> state.vehicles.find { it.uid == uid } }
    if (activeVeh != null) {
        if (activeVeh.isBogged == true || activeVeh.conservationPct < 5.0) {
            return ContextualInteraction("Resgatar", ContextualActionKind.RESCUE_VEHICLE)
        }
        val vDef = getVehicleDef(activeVeh.defId)
        val allStations = findAllSellStationsInWorld(state)
        val nearEst = allStations.any {
            hypot(it.worldX - activeVeh.x, it.worldZ - activeVeh.z) <= 24.0
        } || state.buildings.any {
            (it.type == BuildingType.ESTACAO_GRAOS || it.type == BuildingType.COOPERATIVA || it.type == BuildingType.MERCADO_RURAL) &&
                hypot(it.x - activeVeh.x, it.z - activeVeh.z) <= 18.0
        }
        val hasTrailerCargo = activeVeh.attachedImplementUid?.let { iUid ->
            state.implements.find { it.uid == iUid }?.takeIf { imp ->
                getImplementDef(imp.defId).category == ImplementCategory.CARRETA && imp.fillL > 50.0
            }
        }
        val hasHarvesterCargo = activeVeh.grainTankFillL > 50.0
        if (nearEst && (hasTrailerCargo != null || hasHarvesterCargo)) {
            return ContextualInteraction("Vender Carga", ContextualActionKind.SELL_AT_STATION)
        }
        val nearUnload = hasHarvesterCargo && (
            nearEst ||
                state.buildings.any { it.type == BuildingType.SILO && hypot(it.x - activeVeh.x, it.z - activeVeh.z) <= 18.0 } ||
                state.implements.any { getImplementDef(it.defId).category == ImplementCategory.CARRETA && hypot(it.x - activeVeh.x, it.z - activeVeh.z) <= 14.0 }
            )
        if (nearUnload) {
            return ContextualInteraction("Descarregar", ContextualActionKind.UNLOAD_GRAIN)
        }
        val attachedTrailerUnload = hasTrailerCargo?.let { imp ->
            nearEst || state.buildings.any {
                (it.type == BuildingType.SILO || it.type == BuildingType.SECADOR || it.type == BuildingType.DEPOSITO || it.type == BuildingType.ARMAZEM) &&
                    hypot(it.x - imp.x, it.z - imp.z) <= 18.0
            }
        } == true
        if (attachedTrailerUnload) {
            return ContextualInteraction("Descarregar", ContextualActionKind.UNLOAD_GRAIN)
        }
        if (isWaterTankerVehicle(vDef)) {
            val waterCenter = WATER_AND_FISH_ESTABLISHMENTS.firstOrNull { it.id == "centro_comercializacao_agua" }
            val nearWaterCenter = (waterCenter != null && hypot(waterCenter.worldX - activeVeh.x, waterCenter.worldZ - activeVeh.z) <= 28.0) ||
                state.buildings.any { it.type == BuildingType.CENTRO_COMERCIALIZACAO_AGUA && hypot(it.x - activeVeh.x, it.z - activeVeh.z) <= 24.0 }
            if (nearWaterCenter && (activeVeh.carriedWaterL ?: 0.0) > 50.0) {
                return ContextualInteraction("Vender Água", ContextualActionKind.SELL_WATER_COMMERCIAL)
            }
            val nearWaterSource = state.buildings.any {
                (it.type == BuildingType.ESTACAO_ABASTECIMENTO_AGUA || it.type == BuildingType.ESTACAO_BOMBEAMENTO_ACUDE) &&
                    hypot(it.x - activeVeh.x, it.z - activeVeh.z) <= 24.0
            } || state.ponds.orEmpty().any { it.hasPump && hypot(it.centerX - activeVeh.x, it.centerZ - activeVeh.z) <= it.radiusM + 18.0 }
            if (nearWaterSource && (activeVeh.carriedWaterL ?: 0.0) < vDef.tankCapacityL * 0.95) {
                return ContextualInteraction("Encher Pipa", ContextualActionKind.FILL_WATER_TANKER)
            }
        }
        if (abs(activeVeh.speedMs) < 1.2) {
            val nearFuel = state.buildings.any {
                (it.type == BuildingType.POSTO_COMBUSTIVEL || it.type == BuildingType.TANQUE_COMBUSTIVEL) &&
                    hypot(it.x - activeVeh.x, it.z - activeVeh.z) <= 16.0
            }
            if (nearFuel && activeVeh.fuelL < vDef.fuelCapacityL * 0.96) {
                return ContextualInteraction("Abastecer", ContextualActionKind.REFUEL_AT_STATION)
            }
            val nearWorkshop = state.buildings.any {
                it.type == BuildingType.OFICINA && hypot(it.x - activeVeh.x, it.z - activeVeh.z) <= 14.0
            }
            if (nearWorkshop) {
                return ContextualInteraction("Oficina", ContextualActionKind.OPEN_WORKSHOP)
            }
        }
        return ContextualInteraction("Sair", ContextualActionKind.EXIT_VEHICLE)
    }

    val p = state.player
    if (p.carryingPropUid != null) {
        return ContextualInteraction("Entregar", ContextualActionKind.MANUAL_INTERACT)
    }
    val nearGate = state.buildings.any { it.type == BuildingType.PORTEIRA && hypot(it.x - p.x, it.z - p.z) < 5.2 }
    if (nearGate) {
        return ContextualInteraction("Porteira", ContextualActionKind.MANUAL_INTERACT)
    }
    val nearNpc = (state.npcAgents ?: emptyList()).any { hypot(it.x - p.x, it.z - p.z) <= 6.5 }
    if (nearNpc) {
        return ContextualInteraction("Conversar", ContextualActionKind.MANUAL_INTERACT)
    }
    val nearVeh = state.vehicles.any { hypot(it.x - p.x, it.z - p.z) <= 7.5 }
    if (nearVeh) {
        return ContextualInteraction("Entrar", ContextualActionKind.ENTER_VEHICLE)
    }

    val nearBuilding = state.buildings.minByOrNull { hypot(it.x - p.x, it.z - p.z) }
        ?.takeIf { hypot(it.x - p.x, it.z - p.z) <= 14.0 }
    when (nearBuilding?.type) {
        BuildingType.BANCO -> return ContextualInteraction("Banco Rural", ContextualActionKind.OPEN_BANK)
        BuildingType.CONCESSIONARIA -> return ContextualInteraction("Concessionária", ContextualActionKind.OPEN_DEALERSHIP)
        BuildingType.LOJA_AGROPECUARIA -> return ContextualInteraction("Loja Agro", ContextualActionKind.OPEN_AGRO_STORE)
        BuildingType.OFICINA -> return ContextualInteraction("Oficina", ContextualActionKind.OPEN_WORKSHOP)
        BuildingType.PONTO_ANIMAIS -> return ContextualInteraction("Feira Animais", ContextualActionKind.OPEN_LIVESTOCK)
        BuildingType.MADEIREIRA, BuildingType.LOJA_MATERIAIS ->
            return ContextualInteraction("Materiais & Obras", ContextualActionKind.OPEN_CONSTRUCTION)
        BuildingType.CENTRO_COMERCIALIZACAO_AGUA, BuildingType.ESTACAO_ABASTECIMENTO_AGUA, BuildingType.ESTACAO_BOMBEAMENTO_ACUDE ->
            return ContextualInteraction("Água & Açudes", ContextualActionKind.OPEN_AGUA_PESCA)
        BuildingType.ESTACAO_GRAOS, BuildingType.COOPERATIVA, BuildingType.MERCADO_RURAL, BuildingType.ARMAZEM ->
            return ContextualInteraction("Vender Grãos", ContextualActionKind.SELL_AT_STATION)
        else -> Unit
    }
    if (WATER_AND_FISH_LANDMARKS.any { hypot(it.worldX - p.x, it.worldZ - p.z) <= 22.0 } ||
        state.ponds.orEmpty().any { hypot(it.centerX - p.x, it.centerZ - p.z) <= it.radiusM + 8.0 }
    ) {
        return ContextualInteraction("Água & Peixes", ContextualActionKind.OPEN_AGUA_PESCA)
    }
    if (findAllSellStationsInWorld(state).any { hypot(it.worldX - p.x, it.worldZ - p.z) <= 22.0 }) {
        return ContextualInteraction("Vender Grãos", ContextualActionKind.SELL_AT_STATION)
    }
    if (p.tool == ManualTool.SONDA_AGUA) {
        return ContextualInteraction("Marcar Água", ContextualActionKind.MANUAL_INTERACT)
    }

    val nearProp = state.props.any { hypot(it.x - p.x, it.z - p.z) < 4.5 }
    if (nearProp || p.tool != ManualTool.MAOS) {
        return ContextualInteraction("Interagir", ContextualActionKind.MANUAL_INTERACT)
    }
    return null
}

data class UniversalWorkshopPatch(
    val wheelOptionId: String? = null,
    val engineModelId: String? = null,
    val engineUpgradeId: String? = null,
    val turboId: String? = null,
    val rimColorId: String? = null,
    val cabStyleId: String? = null,
    val customPaintRgb: List<Double>? = null,
    val clearPaint: Boolean = false,
    val headlightStyleId: String? = null,
    val exhaustStyleId: String? = null,
    val accessoryId: String? = null,
)

data class PerfSample(
    val fps: Int = 60,
    val frameTimeMs: Double = 16.6,
    val activeCells: Int = 0,
    val vehiclesCount: Int = 0,
    val implementsCount: Int = 0,
)

enum class MpConnectionStatus(val label: String) {
    DISCONNECTED("Desconectado"),
    CONNECTING("Conectando..."),
    CONNECTED("Conectado (Tempo Real)"),
    PAUSED_BY_DISCONNECT("Pausado por Desconexão"),
}

const val MP_SYNC_PREFIX = "__SYNC__:"

fun computeMpReconnectBackoffMs(attempt: Int): Long {
    val clamped = attempt.coerceIn(0, 4)
    return (1500L shl clamped).coerceAtMost(24_000L)
}

fun parseSharedSyncEvent(text: String): Pair<String, String>? {
    if (!text.startsWith(MP_SYNC_PREFIX)) return null
    val payload = text.removePrefix(MP_SYNC_PREFIX)
    val sepIdx = payload.indexOf(':')
    if (sepIdx <= 0 || sepIdx >= payload.lastIndex) return null
    val kind = payload.substring(0, sepIdx).trim()
    val value = payload.substring(sepIdx + 1).trim()
    if (kind.isEmpty() || value.isEmpty()) return null
    return kind to value
}

class FarmViewModel(application: Application) : AndroidViewModel(application) {
    private val appCtx = application.applicationContext
    val farmAccountManager = FarmAccountManager(appCtx)
    val authManager = GoogleAuthManager(appCtx)
    val isFirebaseConfigured: Boolean = authManager.isFirebaseConfigured
    private val cloudRepo = FarmCloudRepository(appCtx)
    private val mpRepo = MultiplayerRepository(appCtx)
    private val downloadManager = ModDownloadManager(appCtx, viewModelScope)
    val audioEngine = AmbientAudioEngine(viewModelScope)

    private val _accountUser = MutableStateFlow(farmAccountManager.currentUser())
    val accountUser: StateFlow<FarmAccountUser?> = _accountUser.asStateFlow()

    private val _hasRegisteredAccounts = MutableStateFlow(farmAccountManager.hasAnyRegisteredAccount())
    val hasRegisteredAccounts: StateFlow<Boolean> = _hasRegisteredAccounts.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _backStack = MutableStateFlow<List<AppRoute>>(listOf(MainMenu))
    val route: StateFlow<AppRoute> = _backStack
        .map { it.lastOrNull() ?: MainMenu }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainMenu)

    private val _worldState = MutableStateFlow(
        createNewWorld(0, "Fazenda Pioneira", "map_pr", 250000.0, "Padrão", 128)
    )
    val worldState: StateFlow<WorldState> = _worldState.asStateFlow()

    private val _hasActiveCareer = MutableStateFlow(false)
    val hasActiveCareer: StateFlow<Boolean> = _hasActiveCareer.asStateFlow()

    private val _cameraMode = MutableStateFlow(CameraMode.TERCEIRA_PESSOA)
    val cameraMode: StateFlow<CameraMode> = _cameraMode.asStateFlow()

    private val _timeScale = MutableStateFlow(1.0)
    val timeScale: StateFlow<Double> = _timeScale.asStateFlow()

    private val _activeTab = MutableStateFlow<InGameTab?>(null)
    val activeTab: StateFlow<InGameTab?> = _activeTab.asStateFlow()

    private val _showFullMap = MutableStateFlow(false)
    val showFullMap: StateFlow<Boolean> = _showFullMap.asStateFlow()

    private val _hudHidden = MutableStateFlow(false)
    val hudHidden: StateFlow<Boolean> = _hudHidden.asStateFlow()

    private val _selectedMapForNewCareer = MutableStateFlow("map_pr")
    val selectedMapForNewCareer: StateFlow<String> = _selectedMapForNewCareer.asStateFlow()

    private val _workshopFocusUid = MutableStateFlow<String?>(null)
    val workshopFocusUid: StateFlow<String?> = _workshopFocusUid.asStateFlow()

    private val _constructionPreview = MutableStateFlow<ConstructionPlacementPreview?>(null)
    val constructionPreview: StateFlow<ConstructionPlacementPreview?> = _constructionPreview.asStateFlow()

    private val _careerSlots = MutableStateFlow<List<CareerSlotMeta?>>(emptyList())
    val careerSlots: StateFlow<List<CareerSlotMeta?>> = _careerSlots.asStateFlow()

    private val _hubPacks = MutableStateFlow(loadHubPacks(appCtx))
    val hubPacks: StateFlow<List<HubPackEntity>> = _hubPacks.asStateFlow()

    val installedPackIds: StateFlow<Set<String>> = _hubPacks
        .map { list -> list.filter { it.installed && it.enabled }.map { it.packId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _hubFavorites = MutableStateFlow(loadHubFavorites(appCtx))
    val hubFavorites: StateFlow<List<String>> = _hubFavorites.asStateFlow()

    val downloadStates: StateFlow<Map<String, ModDownloadProgress>> = downloadManager.downloads

    private val _quotaConfig = MutableStateFlow(loadStorageQuotaConfig(appCtx))
    val quotaConfig: StateFlow<StorageQuotaConfig> = _quotaConfig.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings(appCtx))
    val settings: StateFlow<GameSettings> = _settings.asStateFlow()

    private val _perfStats = MutableStateFlow(PerfSample())
    val perfStats: StateFlow<PerfSample> = _perfStats.asStateFlow()

    // Cloud Saves
    private val _cloudSaves = MutableStateFlow<List<CloudSaveSummary>>(emptyList())
    val cloudSaves: StateFlow<List<CloudSaveSummary>> = _cloudSaves.asStateFlow()

    private val _cloudConflict = MutableStateFlow<CloudSaveConflictState?>(null)
    val cloudConflict: StateFlow<CloudSaveConflictState?> = _cloudConflict.asStateFlow()

    private val _isSyncingCloud = MutableStateFlow(false)
    val isSyncingCloud: StateFlow<Boolean> = _isSyncingCloud.asStateFlow()

    // Multiplayer
    private val _openRooms = MutableStateFlow<List<MultiplayerRoomDoc>>(emptyList())
    val openRooms: StateFlow<List<MultiplayerRoomDoc>> = _openRooms.asStateFlow()

    private val _activeRoomCode = MutableStateFlow<String?>(null)
    val activeRoomCode: StateFlow<String?> = _activeRoomCode.asStateFlow()

    private val _roomMembers = MutableStateFlow<List<RoomMemberDoc>>(emptyList())
    val roomMembers: StateFlow<List<RoomMemberDoc>> = _roomMembers.asStateFlow()

    private val _roomMessages = MutableStateFlow<List<RoomChatDoc>>(emptyList())
    val roomMessages: StateFlow<List<RoomChatDoc>> = _roomMessages.asStateFlow()

    private val _mpStatus = MutableStateFlow(MpConnectionStatus.DISCONNECTED)
    val mpStatus: StateFlow<MpConnectionStatus> = _mpStatus.asStateFlow()

    private val _mpIsHost = MutableStateFlow(false)
    val mpIsHost: StateFlow<Boolean> = _mpIsHost.asStateFlow()

    private val _selectedAreaTierForNewCareer = MutableStateFlow(MapAreaTier.TIER_8X)
    val selectedAreaTierForNewCareer: StateFlow<MapAreaTier> = _selectedAreaTierForNewCareer.asStateFlow()

    private val _streamingTelemetry = MutableStateFlow<StreamingTelemetrySnapshot?>(null)
    val streamingTelemetry: StateFlow<StreamingTelemetrySnapshot?> = _streamingTelemetry.asStateFlow()

    private val _convertedCommunityMaps = MutableStateFlow<List<BrazilianMapDef>>(getCustomConvertedMaps())
    val convertedCommunityMaps: StateFlow<List<BrazilianMapDef>> = _convertedCommunityMaps.asStateFlow()

    private val _mpCompatibilityReport = MutableStateFlow<MultiplayerCompatibilityReport?>(null)
    val mpCompatibilityReport: StateFlow<MultiplayerCompatibilityReport?> = _mpCompatibilityReport.asStateFlow()

    private val _pendingHitchModeChoice = MutableStateFlow<HitchCandidateEvaluation?>(null)
    val pendingHitchModeChoice: StateFlow<HitchCandidateEvaluation?> = _pendingHitchModeChoice.asStateFlow()

    private val _fieldPanelExpanded = MutableStateFlow(
        _settings.value.fieldPanelConfig.defaultViewMode == FieldPanelViewMode.EXPANDIDO
    )
    val fieldPanelExpanded: StateFlow<Boolean> = _fieldPanelExpanded.asStateFlow()

    private val _activeFieldTelemetry = MutableStateFlow<FieldPanelTelemetry?>(null)
    val activeFieldTelemetry: StateFlow<FieldPanelTelemetry?> = _activeFieldTelemetry.asStateFlow()

    private val _prospectingScan = MutableStateFlow<ProspectingScanResult?>(null)
    val prospectingScan: StateFlow<ProspectingScanResult?> = _prospectingScan.asStateFlow()

    val diagnosticSnapshot: StateFlow<RuntimeDiagnosticSnapshot> = DiagnosticEngine.snapshotFlow

    @Volatile
    private var lastDetectedPlotId: Int? = null

    @Volatile
    private var lastProspectBeepMs: Long = 0L

    private val worldLock = Any()

    private inline fun mutateWorld(crossinline block: (WorldState) -> WorldState): WorldState {
        synchronized(worldLock) {
            val next = block(_worldState.value)
            _worldState.value = next
            return next
        }
    }

    @Volatile
    private var controlInput: ControlInput = EMPTY_CONTROL_INPUT

    @Volatile
    private var touchInput: ControlInput = EMPTY_CONTROL_INPUT

    @Volatile
    private var keyboardInput: ControlInput = EMPTY_CONTROL_INPUT

    @Volatile
    private var gamepadInput: ControlInput = EMPTY_CONTROL_INPUT

    @Volatile
    private var wheelInput: ControlInput = EMPTY_CONTROL_INPUT

    private val _wheelTelemetry = MutableStateFlow(WheelAxisTelemetry())
    val wheelTelemetry: StateFlow<WheelAxisTelemetry> = _wheelTelemetry.asStateFlow()

    private var keyLeftPressed = false
    private var keyRightPressed = false
    private var keyUpPressed = false
    private var keyDownPressed = false
    private var keyBrakePressed = false
    private var keyRunPressed = false

    private fun recomputeControlInput() {
        val mx = (touchInput.moveX + keyboardInput.moveX + gamepadInput.moveX + wheelInput.moveX).coerceIn(-1.0, 1.0)
        val my = (touchInput.moveY + keyboardInput.moveY + gamepadInput.moveY + wheelInput.moveY).coerceIn(-1.0, 1.0)
        val thr = (touchInput.throttle + keyboardInput.throttle + gamepadInput.throttle + wheelInput.throttle).coerceIn(0.0, 1.0)
        val brk = (touchInput.brake + keyboardInput.brake + gamepadInput.brake + wheelInput.brake).coerceIn(0.0, 1.0)
        val hb = touchInput.handbrake || keyboardInput.handbrake || gamepadInput.handbrake || wheelInput.handbrake
        val run = touchInput.running || keyboardInput.running || gamepadInput.running || wheelInput.running
        controlInput = ControlInput(mx, my, thr, brk, hb, run)
    }

    private var simJob: Job? = null
    private var cloudObserveJob: Job? = null
    private var roomsObserveJob: Job? = null
    private var roomSessionJob: Job? = null
    private var telemetryPushJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private val appliedSyncIds = mutableSetOf<String>()
    private var currentRoomSharedFarm = true
    private var currentRoomPauseOnDisconnect = true

    init {
        refreshCareerSlots()
        audioEngine.start()
        audioEngine.setEnabled(_settings.value.soundEnabled)

        viewModelScope.launch {
            farmAccountManager.currentUserFlow.collect { user ->
                _accountUser.value = user
                _hasRegisteredAccounts.value = farmAccountManager.hasAnyRegisteredAccount()
                if (user != null) {
                    onUserSignedIn(user)
                } else {
                    onUserSignedOut()
                }
            }
        }

        startSimulationLoop()
    }

    private fun onUserSignedIn(user: FarmAccountUser) {
        val hasFirebaseUser = runCatching {
            com.google.firebase.FirebaseApp.getApps(appCtx).isNotEmpty() &&
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null
        }.getOrDefault(false)
        if (!isFirebaseConfigured || !hasFirebaseUser) return
        val (devId, devName) = getOrCreateDeviceIdentity(appCtx)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                cloudRepo.ensureProfilePrivateAndDevice(
                    displayName = user.displayName,
                    avatarStyle = user.avatarStyle,
                    activePlatform = "MOBILE",
                    email = user.email,
                    mobileStorageLimitMb = _quotaConfig.value.mobileLimitMb.roundToInt().toLong(),
                    deviceId = devId,
                    deviceName = devName,
                    graphicsPreset = _settings.value.graphicsPreset.name,
                )
            }
        }
        cloudObserveJob?.cancel()
        cloudObserveJob = viewModelScope.launch {
            cloudRepo.observeCloudSaves(user.uid)
                .catch { /* logged by FirestoreErrorHandler */ }
                .collect { docs ->
                    _cloudSaves.value = docs.map { cloudSaveDocToSummary(it) }
                }
        }
        roomsObserveJob?.cancel()
        roomsObserveJob = viewModelScope.launch {
            mpRepo.observeOpenRooms()
                .catch { /* logged */ }
                .collect { rooms ->
                    _openRooms.value = rooms
                }
        }
    }

    private fun onUserSignedOut() {
        cloudObserveJob?.cancel()
        roomsObserveJob?.cancel()
        roomSessionJob?.cancel()
        telemetryPushJob?.cancel()
        reconnectJob?.cancel()
        reconnectAttempts = 0
        appliedSyncIds.clear()
        _cloudSaves.value = emptyList()
        _openRooms.value = emptyList()
        _roomMembers.value = emptyList()
        _roomMessages.value = emptyList()
        _activeRoomCode.value = null
        _mpStatus.value = MpConnectionStatus.DISCONNECTED
    }

    fun generatePersonalCode(): String = farmAccountManager.generateUniquePersonalCode()

    fun createFarmAccount(username: String, personalCode: String) {
        _authLoading.value = true
        _authError.value = null
        when (val res = farmAccountManager.createAccount(username, personalCode)) {
            is FarmAccountResult.Success -> {
                _accountUser.value = res.user
                _hasRegisteredAccounts.value = true
            }
            is FarmAccountResult.Error -> _authError.value = res.message
        }
        _authLoading.value = false
    }

    fun signInOrRecoverFarmAccount(username: String, personalCode: String) {
        _authLoading.value = true
        _authError.value = null
        when (val res = farmAccountManager.signInOrRecover(username, personalCode)) {
            is FarmAccountResult.Success -> {
                _accountUser.value = res.user
                _hasRegisteredAccounts.value = true
            }
            is FarmAccountResult.Error -> _authError.value = res.message
        }
        _authLoading.value = false
    }

    fun restoreAccountSyncBundle(username: String, personalCode: String, rawJson: String): Pair<Boolean, String> {
        _authLoading.value = true
        _authError.value = null
        return when (val res = FarmAccountSyncManager.verifyAndRestoreSyncBundle(appCtx, rawJson, username, personalCode)) {
            is SyncBundleRestoreResult.Ok -> {
                val accRes = farmAccountManager.registerRecoveredExternalAccount(
                    username = res.payload.username,
                    personalCode = personalCode,
                    uidOverride = res.payload.accountUid,
                    createdAtIso = res.payload.exportedAtIso,
                )
                when (accRes) {
                    is FarmAccountResult.Success -> {
                        _settings.value = loadSettings(appCtx)
                        _hubPacks.value = loadHubPacks(appCtx)
                        _hubFavorites.value = loadHubFavorites(appCtx)
                        audioEngine.setEnabled(_settings.value.soundEnabled)
                        refreshCareerSlots()
                        _accountUser.value = accRes.user
                        _hasRegisteredAccounts.value = true
                        val msg = "Conta '${accRes.user.displayName}' restaurada com ${res.restoredSlotsCount} fazenda(s)!"
                        _worldState.value = _worldState.value.copy(statusAlert = msg)
                        _authLoading.value = false
                        true to msg
                    }
                    is FarmAccountResult.Error -> {
                        _authError.value = accRes.message
                        _authLoading.value = false
                        false to accRes.message
                    }
                }
            }
            is SyncBundleRestoreResult.Err -> {
                _authError.value = res.message
                _authLoading.value = false
                false to res.message
            }
        }
    }

    fun acknowledgeAccountWarning() {
        farmAccountManager.acknowledgeCodeWarning()
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun exportAccountSyncBundleJson(): String {
        val user = _accountUser.value ?: return ""
        return FarmAccountSyncManager.exportSyncBundleJson(
            context = appCtx,
            user = user,
            settings = _settings.value,
            hubPacks = _hubPacks.value,
            hubFavorites = _hubFavorites.value,
        )
    }

    fun getAccountSyncStatus(): AccountSyncStatus =
        FarmAccountSyncManager.buildHonestSyncStatus(appCtx)

    fun signOut() {
        viewModelScope.launch {
            leaveMultiplayerRoom()
            farmAccountManager.signOut()
        }
    }

    private fun startSimulationLoop() {
        simJob?.cancel()
        simJob = viewModelScope.launch(Dispatchers.Default) {
            var lastNs = System.nanoTime()
            var perfCounter = 0
            var prevActiveVehUid: String? = null
            var prevGear = 0
            var prevBraking = false
            while (isActive) {
                try {
                    val curRoute = route.value
                    val menuPaused = _activeTab.value != null || _showFullMap.value
                    if (curRoute == Gameplay && !menuPaused) {
                        val nowNs = System.nanoTime()
                        val dt = ((nowNs - lastNs) / 1_000_000_000.0).coerceIn(0.01, 0.05)
                        lastNs = nowNs

                        val stepped = mutateWorld { cur ->
                            stepSimulation(
                                state = cur,
                                input = controlInput,
                                dtSec = dt,
                                timeScale = _timeScale.value,
                                steerSpeedMultiplier = _settings.value.steerSpeedMultiplier,
                            )
                        }

                        // Audio sync
                        val activeVeh = stepped.activeVehicleUid?.let { uid ->
                            stepped.vehicles.find { it.uid == uid }
                        }
                        val vDef = activeVeh?.let { getVehicleDef(it.defId) }
                        val drivetrain = if (vDef != null) {
                            resolveVehicleDrivetrainSpec(vDef, getVehiclePhysicsSpec(vDef))
                        } else null

                        if (activeVeh != null && activeVeh.uid == prevActiveVehUid && activeVeh.engineRunning == true) {
                            if (activeVeh.gear != prevGear) {
                                audioEngine.playGearShift()
                            }
                            if (drivetrain?.hasAirBrakes == true && prevBraking && activeVeh.isBraking != true) {
                                audioEngine.playAirBrakeHiss()
                            }
                        }
                        prevActiveVehUid = activeVeh?.uid
                        prevGear = activeVeh?.gear ?: 0
                        prevBraking = activeVeh?.isBraking == true

                        val rain = (WEATHER_META[stepped.weather] ?: WEATHER_META[WeatherType.ENSOLARADO])?.rainIntensity ?: 0.0
                        audioEngine.updateParameters(rain, if (rain > 0.4) 0.5 else 0.15, stepped.hourOfDay, _settings.value.audioChannels)
                        audioEngine.updateVehicleAudio(
                            VehicleAudioSnapshot(
                                inVehicle = activeVeh != null,
                                speedMs = activeVeh?.speedMs ?: 0.0,
                                rpm = activeVeh?.rpm ?: 0.0,
                                gear = activeVeh?.gear ?: 0,
                                engineRunning = activeVeh?.engineRunning == true,
                                surfaceType = activeVeh?.surfaceType ?: "terra",
                                soundProfile = vDef?.soundProfile ?: "",
                                horsepower = vDef?.horsepower ?: 90.0,
                                implementActive = activeVeh?.attachedImplementUid?.let { iUid ->
                                    stepped.implements.find { it.uid == iUid }?.isActive
                                } == true || (activeVeh?.isPtoEngaged == true),
                                isSprayerActive = activeVeh?.isSprayerActive == true,
                                isBraking = activeVeh?.isBraking == true,
                                engineLoadPct = activeVeh?.engineLoadPct ?: 0.0,
                                hasAirBrake = drivetrain?.hasAirBrakes == true,
                            )
                        )

                        perfCounter++
                        if (stepped.activeVehicleUid == null && stepped.player.tool == ManualTool.SONDA_AGUA) {
                            val scan = evaluateProspectingAt(stepped, stepped.player.x, stepped.player.z)
                            _prospectingScan.value = scan
                            val nowPingMs = System.currentTimeMillis()
                            if (nowPingMs - lastProspectBeepMs >= scan.audioBeepIntervalMs.toLong()) {
                                lastProspectBeepMs = nowPingMs
                                audioEngine.playProspectingPing()
                            }
                        } else if (_prospectingScan.value != null) {
                            _prospectingScan.value = null
                        }
                        if (perfCounter % 6 == 0) {
                            refreshFieldTelemetry(stepped)
                        }
                        if (perfCounter % 15 == 0) {
                            val ms = max(8.0, dt * 1000.0)
                            _perfStats.value = PerfSample(
                                fps = (1000.0 / ms).roundToInt().coerceIn(15, 60),
                                frameTimeMs = (ms * 10.0).roundToInt() / 10.0,
                                activeCells = stepped.cells.size,
                                vehiclesCount = stepped.vehicles.size,
                                implementsCount = stepped.implements.size,
                            )
                        }
                        if (perfCounter % 90 == 0 && activeVeh?.attachedImplementUid != null) {
                            _activeFieldTelemetry.value?.let { tele ->
                                if (_activeRoomCode.value != null && currentRoomSharedFarm) {
                                    broadcastSyncMessage(MultiplayerSyncCodec.buildPlotSummaryFromTelemetry(tele))
                                }
                            }
                        }
                    } else {
                        lastNs = System.nanoTime()
                        val cur = _worldState.value
                        val rain = (WEATHER_META[cur.weather] ?: WEATHER_META[WeatherType.ENSOLARADO])?.rainIntensity ?: 0.0
                        audioEngine.updateParameters(rain, 0.08, cur.hourOfDay, _settings.value.audioChannels)
                        audioEngine.updateVehicleAudio(
                            VehicleAudioSnapshot(
                                inVehicle = false,
                                speedMs = 0.0,
                                rpm = 0.0,
                                gear = 0,
                                engineRunning = false,
                                surfaceType = "terra",
                                soundProfile = "",
                                horsepower = 90.0,
                                implementActive = false,
                                isSprayerActive = false,
                                isBraking = false,
                            )
                        )
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    DiagnosticEngine.recordError("FarmViewModel", "SimulationLoop", t)
                    mutateWorld { cur ->
                        cur.copy(statusAlert = "Recuperação automática da simulação: ${t.message ?: "estado normalizado"}")
                    }
                }
                delay(33L)
            }
        }
    }

    // Navigation
    fun navigateTo(target: AppRoute) {
        audioEngine.playUiClick()
        val stack = _backStack.value
        if (stack.lastOrNull() == target) return
        _backStack.value = stack + target
    }

    fun goBack(): Boolean {
        audioEngine.playUiClick()
        val stack = _backStack.value
        if (stack.size <= 1) return false
        _backStack.value = stack.dropLast(1)
        return true
    }

    fun setSelectedMapForNewCareer(mapId: String) {
        _selectedMapForNewCareer.value = mapId
    }

    fun setSelectedAreaTierForNewCareer(areaTier: MapAreaTier) {
        _selectedAreaTierForNewCareer.value = areaTier
    }

    fun updateStreamingTelemetry(snapshot: StreamingTelemetrySnapshot) {
        _streamingTelemetry.value = snapshot
    }

    fun convertAndRegisterCommunityMap(rawJson: String): Pair<Boolean, String> {
        val currentMaps = getAvailableMaps(installedPackIds.value)
        return when (val res = convertAuthorizedCommunityMapManifest(rawJson, currentMaps)) {
            is CommunityMapConversionResult.AuthorizedConverted -> {
                registerConvertedCommunityMap(res.mapDef)
                _convertedCommunityMaps.value = getCustomConvertedMaps()
                _selectedMapForNewCareer.value = res.mapDef.id
                _selectedAreaTierForNewCareer.value = res.recommendedTier
                _worldState.value = _worldState.value.copy(
                    statusAlert = "Mapa '${res.mapDef.stateName}' convertido (${res.licenseBadge}) • ${res.recommendedTier.label}!"
                )
                true to "Conversão autorizada (${res.licenseBadge})! SHA: ${res.integrityHash}"
            }
            is CommunityMapConversionResult.Rejected -> {
                false to res.reason
            }
        }
    }

    // Career Lifecycle
    fun refreshCareerSlots() {
        _careerSlots.value = listCareerSlots(appCtx)
    }

    fun startNewCareer(
        slotIndex: Int,
        farmName: String,
        mapId: String,
        startingMoneyBrl: Double,
        startingMode: String,
        gridSide: Int = 128,
        initialAppearance: PlayerAppearanceConfig = DEFAULT_PLAYER_APPEARANCE,
        areaTier: MapAreaTier? = _selectedAreaTierForNewCareer.value,
    ) {
        val world = createNewWorld(
            slotIndex = slotIndex,
            farmName = farmName.ifBlank { "Fazenda Brasil" },
            mapId = mapId,
            startingMoneyBrl = startingMoneyBrl,
            startingMode = startingMode,
            requestedGridSide = gridSide,
            areaTier = areaTier,
        ).let { w ->
            w.copy(player = w.player.copy(appearance = initialAppearance))
        }
        _worldState.value = world
        _hasActiveCareer.value = true
        _constructionPreview.value = null
        lastDetectedPlotId = null
        refreshFieldTelemetry(world)
        saveWorldToSlot(appCtx, world)
        refreshCareerSlots()
        navigateTo(Gameplay)
    }

    fun loadCareer(slotIndex: Int) {
        val (loaded, recovered) = loadWorldFromSlot(appCtx, slotIndex)
        if (loaded != null) {
            val initialized = ensureWaterAndFishWorldInitialized(loaded)
            val loadedWorld = initialized.copy(
                statusAlert = if (recovered) "Carreira recuperada via backup SHA-256!" else "Carreira carregada com sucesso."
            )
            _worldState.value = loadedWorld
            _hasActiveCareer.value = true
            _constructionPreview.value = null
            lastDetectedPlotId = null
            refreshFieldTelemetry(loadedWorld)
            DiagnosticEngine.runWorldIntegrityAudit(loadedWorld)
            navigateTo(Gameplay)
        } else {
            _worldState.value = _worldState.value.copy(statusAlert = "Espaço ${slotIndex + 1} vazio ou corrompido.")
        }
    }

    fun continueMostRecentCareer() {
        val slots = listCareerSlots(appCtx).filterNotNull()
        val latest = slots.maxByOrNull { it.savedAtEpochMs }
        if (latest != null) {
            loadCareer(latest.slotIndex)
        } else {
            navigateTo(AppRoute.NewCareerSetup)
        }
    }

    fun saveCurrentCareer() {
        val cur = _worldState.value
        val (ok, _) = saveWorldToSlot(appCtx, cur)
        refreshCareerSlots()
        _worldState.value = cur.copy(
            statusAlert = if (ok) "Jogo salvo no Espaço ${cur.slotIndex + 1} (SHA-256 verificado)." else "Erro ao salvar localmente."
        )
    }

    fun deleteSlot(slotIndex: Int) {
        deleteCareerSlot(appCtx, slotIndex)
        refreshCareerSlots()
    }

    fun exportCurrentCareerJson(): String = exportCareerToJson(_worldState.value)

    fun importCareerJson(rawJson: String, targetSlot: Int): Pair<Boolean, String> {
        return when (val res = parseAndVerifyCareerImport(rawJson, targetSlot)) {
            is CareerImportResult.Ok -> {
                saveWorldToSlot(appCtx, res.state)
                refreshCareerSlots()
                true to "Backup '${res.state.farmName}' importado para o Espaço ${targetSlot + 1}!"
            }
            is CareerImportResult.Err -> false to res.error
        }
    }

    // Cloud Save Actions
    fun uploadCurrentToCloud(forceOverwrite: Boolean = false) {
        val user = _accountUser.value ?: return
        val cur = _worldState.value
        val (devId, devName) = getOrCreateDeviceIdentity(appCtx)
        viewModelScope.launch {
            _isSyncingCloud.value = true
            runCatching {
                val payload = buildCloudSaveUploadPayload(cur, devId, devName)
                when (val outcome = cloudRepo.uploadCloudSave(payload, forceOverwrite)) {
                    is CloudUploadOutcome.Success -> {
                        val updated = cur.copy(
                            cloudRevision = outcome.newRevision.toInt(),
                            statusAlert = "Nuvem sincronizada! Revisão #${outcome.newRevision} salva para ${user.displayName}.",
                        )
                        _worldState.value = updated
                        saveWorldToSlot(appCtx, updated)
                        refreshCareerSlots()
                        _cloudConflict.value = null
                    }
                    is CloudUploadOutcome.Conflict -> {
                        _cloudConflict.value = CloudSaveConflictState(
                            slotIndex = cur.slotIndex,
                            localFarmName = cur.farmName,
                            localDay = cur.day,
                            localRevision = cur.cloudRevision ?: 0,
                            localMoneyBrl = cur.moneyBrl,
                            cloud = cloudSaveDocToSummary(outcome.cloudSave),
                        )
                    }
                }
            }.onFailure { err ->
                _worldState.value = cur.copy(statusAlert = "Erro ao enviar para nuvem: ${err.localizedMessage}")
            }
            _isSyncingCloud.value = false
        }
    }

    fun resolveCloudConflict(choice: String) {
        val conflict = _cloudConflict.value ?: return
        when (choice) {
            "KEEP_LOCAL" -> uploadCurrentToCloud(forceOverwrite = true)
            "USE_CLOUD" -> downloadCloudSlot(conflict.slotIndex)
            else -> _cloudConflict.value = null
        }
    }

    fun downloadCloudSlot(slotIndex: Int) {
        viewModelScope.launch {
            _isSyncingCloud.value = true
            runCatching {
                val doc = cloudRepo.getCloudSave(slotIndex)
                if (doc == null) {
                    _worldState.value = _worldState.value.copy(statusAlert = "Save na nuvem não encontrado neste espaço.")
                } else {
                    val missing = detectMissingModsForCloudSave(doc.requiredModIds, _hubPacks.value)
                    val restored = ensureWaterAndFishWorldInitialized(worldStateFromCloudSaveDoc(doc))
                    _worldState.value = restored.copy(
                        statusAlert = if (missing.isNotEmpty()) {
                            "Fazenda baixada! Instale ${missing.size} mod(s) no ModHub para todos os veículos."
                        } else {
                            "Fazenda '${restored.farmName}' restaurada da nuvem (Rev #${doc.revision})!"
                        }
                    )
                    _hasActiveCareer.value = true
                    _cloudConflict.value = null
                    DiagnosticEngine.runWorldIntegrityAudit(_worldState.value)
                    saveWorldToSlot(appCtx, _worldState.value)
                    refreshCareerSlots()
                }
            }.onFailure { err ->
                _worldState.value = _worldState.value.copy(statusAlert = "Erro ao baixar da nuvem: ${err.localizedMessage}")
            }
            _isSyncingCloud.value = false
        }
    }

    fun deleteCloudSlot(slotIndex: Int) {
        viewModelScope.launch {
            runCatching { cloudRepo.deleteCloudSave(slotIndex) }
        }
    }

    // Gameplay Controls & Actions
    fun updateTouchInput(patch: ControlInput) {
        touchInput = patch
        recomputeControlInput()
    }

    private fun matchesKeyBinding(keyCode: Int, binding: String): Boolean {
        return when (binding) {
            "KeyW" -> keyCode == android.view.KeyEvent.KEYCODE_W
            "KeyS" -> keyCode == android.view.KeyEvent.KEYCODE_S
            "KeyA" -> keyCode == android.view.KeyEvent.KEYCODE_A
            "KeyD" -> keyCode == android.view.KeyEvent.KEYCODE_D
            "KeyQ" -> keyCode == android.view.KeyEvent.KEYCODE_Q
            "KeyE" -> keyCode == android.view.KeyEvent.KEYCODE_E
            "KeyV" -> keyCode == android.view.KeyEvent.KEYCODE_V
            "KeyF" -> keyCode == android.view.KeyEvent.KEYCODE_F
            "KeyG" -> keyCode == android.view.KeyEvent.KEYCODE_G
            "KeyR" -> keyCode == android.view.KeyEvent.KEYCODE_R
            "KeyT" -> keyCode == android.view.KeyEvent.KEYCODE_T
            "KeyX" -> keyCode == android.view.KeyEvent.KEYCODE_X
            "KeyZ" -> keyCode == android.view.KeyEvent.KEYCODE_Z
            "KeyC" -> keyCode == android.view.KeyEvent.KEYCODE_C
            "KeyL" -> keyCode == android.view.KeyEvent.KEYCODE_L
            "KeyM" -> keyCode == android.view.KeyEvent.KEYCODE_M
            "KeyH" -> keyCode == android.view.KeyEvent.KEYCODE_H
            "KeyI" -> keyCode == android.view.KeyEvent.KEYCODE_I
            "KeyK" -> keyCode == android.view.KeyEvent.KEYCODE_K
            "KeyJ" -> keyCode == android.view.KeyEvent.KEYCODE_J
            "KeyU" -> keyCode == android.view.KeyEvent.KEYCODE_U
            "KeyB" -> keyCode == android.view.KeyEvent.KEYCODE_B
            "ShiftLeft" -> keyCode == android.view.KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == android.view.KeyEvent.KEYCODE_SHIFT_RIGHT
            "Space" -> keyCode == android.view.KeyEvent.KEYCODE_SPACE
            else -> false
        }
    }

    fun onHardwareKeyEvent(keyCode: Int, isDown: Boolean): Boolean {
        val keys = _settings.value.keyBindings
        if (isDown && route.value == Gameplay && _showFullMap.value && matchesKeyBinding(keyCode, keys.map)) {
            toggleFullMap(false)
            return true
        }
        val activeGameplay = route.value == Gameplay && _activeTab.value == null && !_showFullMap.value && _constructionPreview.value == null
        if (!activeGameplay) {
            if (keyLeftPressed || keyRightPressed || keyUpPressed || keyDownPressed || keyBrakePressed) {
                keyLeftPressed = false
                keyRightPressed = false
                keyUpPressed = false
                keyDownPressed = false
                keyBrakePressed = false
                keyRunPressed = false
                keyboardInput = EMPTY_CONTROL_INPUT
                recomputeControlInput()
            }
            return false
        }
        val curState = _worldState.value
        val activeVeh = curState.activeVehicleUid?.let { uid -> curState.vehicles.find { it.uid == uid } }
        val activeDef = activeVeh?.let { getVehicleDef(it.defId) }
        val handled = when {
            matchesKeyBinding(keyCode, keys.left) || keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                keyLeftPressed = isDown
                true
            }
            matchesKeyBinding(keyCode, keys.right) || keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                keyRightPressed = isDown
                true
            }
            matchesKeyBinding(keyCode, keys.forward) || keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                keyUpPressed = isDown
                true
            }
            matchesKeyBinding(keyCode, keys.backward) || keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                keyDownPressed = isDown
                true
            }
            keyCode == android.view.KeyEvent.KEYCODE_SPACE -> {
                keyBrakePressed = isDown
                true
            }
            matchesKeyBinding(keyCode, keys.sprint) || keyCode == android.view.KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == android.view.KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                keyRunPressed = isDown
                true
            }
            matchesKeyBinding(keyCode, keys.hitch) -> {
                if (isDown) requestHitchOrUnhitch()
                true
            }
            matchesKeyBinding(keyCode, keys.lowerActivate) -> {
                if (isDown) toggleLowerImp()
                true
            }
            matchesKeyBinding(keyCode, keys.enterExit) || keyCode == android.view.KeyEvent.KEYCODE_ENTER -> {
                if (isDown) performContextualInteraction()
                true
            }
            matchesKeyBinding(keyCode, keys.map) -> {
                if (isDown) toggleFullMap(true)
                true
            }
            matchesKeyBinding(keyCode, keys.camera) -> {
                if (isDown) cycleCameraMode()
                true
            }
            matchesKeyBinding(keyCode, keys.lights) -> {
                if (isDown) toggleHeadlights()
                true
            }
            matchesKeyBinding(keyCode, keys.horn) -> {
                if (isDown) playHorn()
                true
            }
            matchesKeyBinding(keyCode, keys.unfoldPipe) || keyCode == android.view.KeyEvent.KEYCODE_O -> {
                if (isDown) toggleAugerPipeUnfold()
                true
            }
            matchesKeyBinding(keyCode, keys.diffLock) || keyCode == android.view.KeyEvent.KEYCODE_X -> {
                if (isDown) toggleDifferentialLock()
                true
            }
            matchesKeyBinding(keyCode, keys.beacon) || (keyCode == android.view.KeyEvent.KEYCODE_J && activeDef?.category != VehicleCategory.ESCAVADEIRA) -> {
                if (isDown) toggleBeaconLights()
                true
            }
            matchesKeyBinding(keyCode, keys.cruiseControl) || keyCode == android.view.KeyEvent.KEYCODE_3 -> {
                if (isDown) toggleCruiseControl()
                true
            }
            matchesKeyBinding(keyCode, keys.ptoToggle) || (keyCode == android.view.KeyEvent.KEYCODE_B && activeDef?.category != VehicleCategory.CAMINHAO_PIPA && activeDef?.category != VehicleCategory.ESCAVADEIRA) -> {
                if (isDown) togglePto()
                true
            }
            keyCode == android.view.KeyEvent.KEYCODE_I -> {
                if (isDown) {
                    when {
                        activeDef?.category == VehicleCategory.PERFURATRIZ -> performDrillingSounding()
                        activeDef != null && isEarthmovingVehicle(activeDef) -> executeEarthworkAction()
                        activeVeh == null && curState.player.tool == ManualTool.SONDA_AGUA -> markWaterProspectPoint()
                        else -> adjustEarthworkHydraulic(boomDeltaDeg = 6.0, bladeDeltaM = 0.08)
                    }
                }
                true
            }
            keyCode == android.view.KeyEvent.KEYCODE_K -> {
                if (isDown && activeDef != null && isEarthmovingVehicle(activeDef)) {
                    adjustEarthworkHydraulic(boomDeltaDeg = -6.0, bladeDeltaM = -0.08)
                }
                true
            }
            keyCode == android.view.KeyEvent.KEYCODE_J -> {
                if (isDown && activeDef != null && isEarthmovingVehicle(activeDef)) {
                    adjustEarthworkHydraulic(swingDeltaDeg = -14.0, bucketDeltaDeg = -10.0)
                }
                true
            }
            keyCode == android.view.KeyEvent.KEYCODE_U -> {
                if (isDown && activeDef != null && isEarthmovingVehicle(activeDef)) {
                    adjustEarthworkHydraulic(swingDeltaDeg = 14.0, bucketDeltaDeg = 10.0)
                }
                true
            }
            keyCode == android.view.KeyEvent.KEYCODE_B -> {
                if (isDown) {
                    when {
                        activeDef != null && isWaterTankerVehicle(activeDef) -> {
                            val loadedL = activeVeh.carriedWaterL ?: 0.0
                            if (loadedL > 50.0) sellWaterCommercial(null) else fillWaterTanker()
                        }
                        activeDef != null && isEarthmovingVehicle(activeDef) -> dumpEarthFromMachine()
                    }
                }
                true
            }
            else -> false
        }
        if (handled) {
            val kMoveX = (if (keyRightPressed) 1.0 else 0.0) - (if (keyLeftPressed) 1.0 else 0.0)
            val kMoveY = (if (keyUpPressed) 1.0 else 0.0) - (if (keyDownPressed) 1.0 else 0.0)
            val kThrottle = if (keyUpPressed) 1.0 else 0.0
            val kBrake = if (keyBrakePressed || keyDownPressed) 1.0 else 0.0
            keyboardInput = ControlInput(
                moveX = kMoveX,
                moveY = kMoveY,
                throttle = kThrottle,
                brake = kBrake,
                handbrake = keyBrakePressed,
                running = keyRunPressed,
            )
            recomputeControlInput()
        }
        return handled
    }

    fun onGamepadMotion(axisX: Double, axisY: Double, throttle: Double, brake: Double): Boolean {
        val activeGameplay = route.value == Gameplay && _activeTab.value == null && !_showFullMap.value
        if (!activeGameplay) {
            if (gamepadInput != EMPTY_CONTROL_INPUT) {
                gamepadInput = EMPTY_CONTROL_INPUT
                recomputeControlInput()
            }
            return false
        }
        val dzX = if (abs(axisX) < 0.12) 0.0 else axisX.coerceIn(-1.0, 1.0)
        val dzY = if (abs(axisY) < 0.12) 0.0 else axisY.coerceIn(-1.0, 1.0)
        val thr = if (throttle < 0.08) 0.0 else throttle.coerceIn(0.0, 1.0)
        val brk = if (brake < 0.08) 0.0 else brake.coerceIn(0.0, 1.0)
        gamepadInput = ControlInput(
            moveX = dzX,
            moveY = if (abs(dzY) > 0.12) dzY else (thr - brk).coerceIn(-1.0, 1.0),
            throttle = thr,
            brake = brk,
        )
        recomputeControlInput()
        return true
    }

    fun onWheelInput(
        rawSteer: Double,
        rawThrottle: Double,
        rawBrake: Double,
        rawClutch: Double = 0.0,
        gear: Int = 0,
        handbrake: Boolean = false,
        deviceName: String? = null,
    ): Boolean {
        val cfg = _settings.value.usbWheelConfig
        val steerSign = if (cfg.invertSteer) -1.0 else 1.0
        val thrSign = if (cfg.invertThrottle) -1.0 else 1.0
        val brkSign = if (cfg.invertBrake) -1.0 else 1.0
        val cluSign = if (cfg.invertClutch) -1.0 else 1.0

        val dzSteer = if (abs(rawSteer) < cfg.deadzoneSteer) 0.0 else (rawSteer * steerSign * cfg.steerSensitivity).coerceIn(-1.0, 1.0)
        val dzThr = if (abs(rawThrottle) < cfg.deadzoneThrottle) 0.0 else (rawThrottle * thrSign).coerceIn(0.0, 1.0)
        val dzBrk = if (abs(rawBrake) < cfg.deadzoneBrake) 0.0 else (rawBrake * brkSign).coerceIn(0.0, 1.0)
        val dzClu = if (abs(rawClutch) < cfg.deadzoneClutch) 0.0 else (rawClutch * cluSign).coerceIn(0.0, 1.0)

        _wheelTelemetry.value = WheelAxisTelemetry(
            rawSteer = rawSteer,
            rawThrottle = rawThrottle,
            rawBrake = rawBrake,
            rawClutch = rawClutch,
            calibratedSteer = dzSteer,
            calibratedThrottle = dzThr,
            calibratedBrake = dzBrk,
            calibratedClutch = dzClu,
            currentGear = gear,
            detectedDeviceName = deviceName ?: _wheelTelemetry.value.detectedDeviceName,
            isConnected = true,
        )

        wheelInput = ControlInput(
            moveX = dzSteer,
            moveY = (dzThr - dzBrk).coerceIn(-1.0, 1.0),
            throttle = dzThr,
            brake = dzBrk,
            handbrake = handbrake,
        )
        recomputeControlInput()
        return true
    }

    fun updateWheelConfig(updater: (UsbSteeringWheelConfig) -> UsbSteeringWheelConfig) {
        val nextCfg = updater(_settings.value.usbWheelConfig)
        updateSettings { it.copy(usbWheelConfig = nextCfg) }
    }

    fun applyWheelPreset(presetId: String) {
        val nextCfg = when (presetId) {
            "Logitech G29" -> UsbSteeringWheelConfig(
                profileName = "Logitech G29 / G920 / G923",
                maxSteerAngleDeg = 900.0,
                deadzoneSteer = 0.03,
                deadzoneThrottle = 0.02,
                deadzoneBrake = 0.02,
                deadzoneClutch = 0.04,
                steerSensitivity = 1.0,
                hShifterEnabled = true,
                paddleShiftEnabled = true,
                forceFeedbackIntensity = 0.85,
            )
            "Thrustmaster T300" -> UsbSteeringWheelConfig(
                profileName = "Thrustmaster T150 / T300",
                maxSteerAngleDeg = 1080.0,
                deadzoneSteer = 0.02,
                deadzoneThrottle = 0.02,
                deadzoneBrake = 0.02,
                deadzoneClutch = 0.03,
                steerSensitivity = 1.05,
                hShifterEnabled = false,
                paddleShiftEnabled = true,
                forceFeedbackIntensity = 0.90,
            )
            "Fanatec CSL" -> UsbSteeringWheelConfig(
                profileName = "Fanatec ClubSport / CSL",
                maxSteerAngleDeg = 1080.0,
                deadzoneSteer = 0.01,
                deadzoneThrottle = 0.01,
                deadzoneBrake = 0.01,
                deadzoneClutch = 0.02,
                steerSensitivity = 1.0,
                hShifterEnabled = true,
                paddleShiftEnabled = true,
                forceFeedbackIntensity = 1.0,
            )
            else -> UsbSteeringWheelConfig(
                profileName = "Volante Genérico 270°",
                maxSteerAngleDeg = 270.0,
                deadzoneSteer = 0.06,
                deadzoneThrottle = 0.04,
                deadzoneBrake = 0.04,
                deadzoneClutch = 0.06,
                steerSensitivity = 0.95,
                hShifterEnabled = false,
                paddleShiftEnabled = true,
                forceFeedbackIntensity = 0.70,
            )
        }
        updateSettings { it.copy(usbWheelConfig = nextCfg) }
    }

    fun currentEffectiveControlInput(): ControlInput = controlInput

    fun cycleCameraMode() {
        val all = CameraMode.entries
        _cameraMode.value = all[(all.indexOf(_cameraMode.value) + 1) % all.size]
    }

    fun cycleTimeScale() {
        _timeScale.value = when {
            _timeScale.value < 4.0 -> 5.0
            _timeScale.value < 14.0 -> 15.0
            _timeScale.value < 50.0 -> 60.0
            else -> 1.0
        }
    }

    fun toggleHudHidden() {
        _hudHidden.value = !_hudHidden.value
    }

    fun openInGameTab(tab: InGameTab?) {
        audioEngine.playUiClick()
        _activeTab.value = tab
    }

    fun toggleFullMap(open: Boolean) {
        audioEngine.playUiClick()
        _showFullMap.value = open
    }

    fun teleportToLocation(wx: Double, wz: Double, label: stringCompat) {
        mutateWorld { cur ->
            val activeUid = cur.activeVehicleUid
            if (activeUid != null) {
                cur.copy(
                    vehicles = cur.vehicles.map { if (it.uid == activeUid) it.copy(x = wx, z = wz, speedMs = 0.0) else it },
                    player = cur.player.copy(x = wx, z = wz),
                    statusAlert = "Deslocado para $label.",
                )
            } else {
                cur.copy(
                    player = cur.player.copy(x = wx, z = wz),
                    statusAlert = "Movido para $label.",
                )
            }
        }
        _showFullMap.value = false
    }

    fun clearStatusAlert() {
        mutateWorld { it.copy(statusAlert = null) }
    }

    fun cycleWorldWeather() {
        val next = mutateWorld { cycleWeather(it) }
        val code = _activeRoomCode.value
        val user = _accountUser.value
        if (code != null && _mpIsHost.value && currentRoomSharedFarm && user != null) {
            viewModelScope.launch {
                runCatching {
                    mpRepo.sendChatMessage(
                        roomCode = code,
                        senderName = user.displayName,
                        text = "${MP_SYNC_PREFIX}WEATHER:${next.weather.name}",
                    )
                }
            }
        }
    }
    fun toggleEnterExit() { mutateWorld { toggleEnterExitVehicle(it) } }
    fun toggleEngine() {
        val next = mutateWorld { toggleVehicleEngineInWorld(it) }
        val running = next.activeVehicleUid?.let { uid -> next.vehicles.find { it.uid == uid }?.engineRunning } == true
        if (running) audioEngine.playEngineStart() else audioEngine.playEngineStop()
    }
    fun toggleHandbrake() {
        audioEngine.playUiClick()
        mutateWorld { toggleVehicleHandbrakeInWorld(it) }
    }
    fun toggleCruiseControl() {
        audioEngine.playUiClick()
        mutateWorld { toggleCruiseControlInWorld(it) }
    }
    fun adjustCruiseSpeed(deltaKmh: Double) {
        audioEngine.playUiClick()
        mutateWorld { adjustCruiseSpeedInWorld(it, deltaKmh) }
    }
    fun setPlayerAppearancePreset(presetId: String) {
        val preset = CHARACTER_PRESETS.firstOrNull { it.id == presetId } ?: return
        audioEngine.playUiClick()
        mutateWorld { cur ->
            cur.copy(
                player = cur.player.copy(appearance = preset.config),
                statusAlert = "Personagem alterado para ${preset.title}.",
            )
        }
    }
    fun performContextualInteraction() {
        val action = resolveContextualInteraction(_worldState.value) ?: return
        when (action.kind) {
            ContextualActionKind.ENTER_VEHICLE,
            ContextualActionKind.EXIT_VEHICLE -> toggleEnterExit()
            ContextualActionKind.HITCH_IMPLEMENT -> toggleHitch()
            ContextualActionKind.UNLOAD_GRAIN -> unloadGrain()
            ContextualActionKind.RESCUE_VEHICLE -> engageTowRescue()
            ContextualActionKind.MANUAL_INTERACT -> actionManual()
            ContextualActionKind.SELL_AT_STATION -> sellCargoAtStation()
            ContextualActionKind.REFUEL_AT_STATION -> refuelActiveVehicle()
            ContextualActionKind.OPEN_BANK -> openInGameTab(InGameTab.ECONOMIA)
            ContextualActionKind.OPEN_DEALERSHIP -> navigateTo(AppRoute.Dealership)
            ContextualActionKind.OPEN_AGRO_STORE -> openInGameTab(InGameTab.EQUIPE)
            ContextualActionKind.OPEN_WORKSHOP -> openWorkshopFromWorld()
            ContextualActionKind.OPEN_LIVESTOCK -> openInGameTab(InGameTab.PECUARIA)
            ContextualActionKind.OPEN_CONSTRUCTION -> openInGameTab(InGameTab.CONSTRUCAO)
            ContextualActionKind.OPEN_AGUA_PESCA -> openInGameTab(InGameTab.AGUA_PESCA)
            ContextualActionKind.FILL_WATER_TANKER -> fillWaterTanker()
            ContextualActionKind.SELL_WATER_COMMERCIAL -> sellWaterCommercial(null)
            ContextualActionKind.SELL_FISH_MARKET -> openInGameTab(InGameTab.AGUA_PESCA)
            ContextualActionKind.EXECUTE_EARTHWORK -> executeEarthworkAction()
        }
    }
    fun toggleHitch() {
        requestHitchOrUnhitch()
    }

    fun requestHitchOrUnhitch() {
        val cur = _worldState.value
        val activeUid = cur.activeVehicleUid
        if (activeUid != null) {
            val veh = cur.vehicles.find { it.uid == activeUid } ?: return
            if (veh.attachedImplementUid != null) {
                val unhitchEval = evaluateVehicleUnhitch(cur, veh) ?: return
                val after = mutateWorld { state -> executeValidatedUnhitch(state, unhitchEval, onFoot = false) }
                refreshFieldTelemetry(after)
                if (unhitchEval.canUnhitchNow) {
                    audioEngine.playUiClick()
                    after.implements.find { it.uid == unhitchEval.implement.uid }?.let { imp ->
                        broadcastSyncMessage(
                            MultiplayerSyncCodec.encodeUnhitch(veh.uid, imp.uid, imp.x, imp.z, imp.headingDeg)
                        )
                    }
                }
                return
            }
            val eval = findBestVehicleHitchCandidate(cur, veh, searchRadiusM = 12.0)
            if (eval == null) {
                mutateWorld { it.copy(statusAlert = "Nenhum implemento próximo para engatar. Aproxime a barra de tração.") }
                return
            }
            if (!eval.canHitchNow) {
                mutateWorld { it.copy(statusAlert = eval.statusMessage) }
                return
            }
            when (_settings.value.hitchMode) {
                HitchMode.IN_VEHICLE_BUTTON -> {
                    val after = mutateWorld { state -> executeValidatedHitch(state, eval, onFoot = false) }
                    audioEngine.playUiClick()
                    refreshFieldTelemetry(after)
                    after.implements.find { it.uid == eval.implement.uid }?.let { imp ->
                        broadcastSyncMessage(
                            MultiplayerSyncCodec.encodeHitch(veh.uid, imp.uid, imp.x, imp.z, imp.headingDeg)
                        )
                    }
                }
                HitchMode.ON_FOOT_MANUAL -> {
                    mutateWorld {
                        it.copy(
                            statusAlert = "Modo 'Descendo do trator' ativo: estacione, saia do veículo e caminhe até o engate para conectar manualmente."
                        )
                    }
                }
                HitchMode.ASK_EACH_TIME -> {
                    _pendingHitchModeChoice.value = eval
                }
            }
            return
        }

        // On-foot manual hitch / unhitch near coupling point
        val onFoot = evaluateOnFootHitch(cur)
        if (onFoot == null) {
            mutateWorld {
                it.copy(statusAlert = "Aproxime-se do ponto de engate entre o veículo estacionado e o implemento.")
            }
            return
        }
        onFoot.unhitchCandidate?.let { uEval ->
            val after = mutateWorld { state -> executeValidatedUnhitch(state, uEval, onFoot = true) }
            refreshFieldTelemetry(after)
            if (uEval.canUnhitchNow) {
                audioEngine.playUiClick()
                after.implements.find { it.uid == uEval.implement.uid }?.let { imp ->
                    broadcastSyncMessage(
                        MultiplayerSyncCodec.encodeUnhitch(uEval.vehicle.uid, imp.uid, imp.x, imp.z, imp.headingDeg)
                    )
                }
            }
            return
        }
        onFoot.hitchCandidate?.let { hEval ->
            val after = mutateWorld { state -> executeValidatedHitch(state, hEval, onFoot = true) }
            refreshFieldTelemetry(after)
            if (hEval.canHitchNow) {
                audioEngine.playUiClick()
                after.implements.find { it.uid == hEval.implement.uid }?.let { imp ->
                    broadcastSyncMessage(
                        MultiplayerSyncCodec.encodeHitch(hEval.vehicle.uid, imp.uid, imp.x, imp.z, imp.headingDeg)
                    )
                }
            }
        }
    }

    fun confirmHitchModeChoice(choice: HitchMode) {
        val eval = _pendingHitchModeChoice.value ?: return
        _pendingHitchModeChoice.value = null
        if (choice == HitchMode.IN_VEHICLE_BUTTON) {
            val after = mutateWorld { cur ->
                val veh = cur.vehicles.find { it.uid == eval.vehicle.uid } ?: return@mutateWorld cur
                val imp = cur.implements.find { it.uid == eval.implement.uid } ?: return@mutateWorld cur
                val freshEval = evaluateVehicleHitchPair(cur, veh, imp)
                executeValidatedHitch(cur, freshEval, onFoot = false)
            }
            audioEngine.playUiClick()
            refreshFieldTelemetry(after)
            after.implements.find { it.uid == eval.implement.uid }?.let { imp ->
                if (imp.attachedToVehicleUid == eval.vehicle.uid) {
                    broadcastSyncMessage(
                        MultiplayerSyncCodec.encodeHitch(eval.vehicle.uid, imp.uid, imp.x, imp.z, imp.headingDeg)
                    )
                }
            }
        } else if (choice == HitchMode.ON_FOOT_MANUAL) {
            mutateWorld { cur ->
                val exited = if (cur.activeVehicleUid != null) toggleEnterExitVehicle(cur) else cur
                exited.copy(
                    statusAlert = "Desça até a barra de tração de ${eval.implementDef.model} e pressione [ENGATAR]."
                )
            }
        }
    }

    fun dismissHitchModeChoice() {
        _pendingHitchModeChoice.value = null
    }

    fun setDistributorProduct(productId: String) {
        audioEngine.playUiClick()
        val after = mutateWorld { setDistributorProductInWorld(it, productId) }
        refreshFieldTelemetry(after)
    }

    fun toggleFieldPanelExpanded() {
        audioEngine.playUiClick()
        _fieldPanelExpanded.value = !_fieldPanelExpanded.value
    }

    fun refreshFieldTelemetry(state: WorldState = _worldState.value) {
        val activeVeh = state.activeVehicleUid?.let { uid -> state.vehicles.find { it.uid == uid } }
        val attachedImp = activeVeh?.attachedImplementUid?.let { uid -> state.implements.find { it.uid == uid } }
        val (fx, fz) = if (activeVeh != null) activeVeh.x to activeVeh.z else state.player.x to state.player.z
        var detectedId = detectFieldPlotWithHysteresis(state, fx, fz, lastDetectedPlotId)
        if (detectedId == null && attachedImp != null) {
            detectedId = detectFieldPlotWithHysteresis(state, attachedImp.x, attachedImp.z, lastDetectedPlotId)
        }
        lastDetectedPlotId = detectedId
        _activeFieldTelemetry.value = if (detectedId != null) {
            analyzeFieldPlot(
                state = state,
                plotId = detectedId,
                localPlayerUid = _accountUser.value?.uid,
                activeVehicle = activeVeh,
                attachedImplement = attachedImp,
            )
        } else null
    }

    fun toggleLowerImp() { mutateWorld { toggleImplementLowered(it) } }
    fun togglePto() { mutateWorld { togglePtoInWorld(it) } }
    fun toggleFieldOpenMode() { mutateWorld { toggleFieldCreationMode(it) } }
    fun toggle4wd() { mutateWorld { toggleVehicle4wd(it) } }
    fun toggleDifferentialLock() {
        audioEngine.playUiClick()
        mutateWorld { toggleDifferentialLock(it) }
    }
    fun toggleHarvesterHeader() { mutateWorld { toggleHarvesterHeader(it) } }
    fun toggleAugerPipeUnfold() {
        audioEngine.playUiClick()
        mutateWorld { toggleAugerPipeUnfold(it) }
    }
    fun toggleBeaconLights() {
        audioEngine.playUiClick()
        mutateWorld { toggleBeaconLights(it) }
    }
    fun adjustLoaderBoom(deltaDeg: Double) {
        audioEngine.playUiClick()
        mutateWorld { adjustFrontLoaderBoom(it, deltaDeg) }
    }
    fun adjustLoaderBucket(deltaDeg: Double) {
        audioEngine.playUiClick()
        mutateWorld { adjustFrontLoaderBucket(it, deltaDeg) }
    }
    fun toggleSprayer() { mutateWorld { toggleSprayerInWorld(it) } }
    fun refillSprayer() { mutateWorld { refillSprayerInWorld(it) } }
    fun engageTowRescue() { mutateWorld { engageTowRescueInWorld(it) } }
    fun refuelActiveVehicle() { mutateWorld { refuelVehicleInWorld(it, true) } }
    fun refillAttachedImplement() { mutateWorld { refillImplementReservoirInWorld(it) } }
    fun unloadGrain() {
        val cur = _worldState.value
        val activeVeh = cur.activeVehicleUid?.let { uid -> cur.vehicles.find { it.uid == uid } }
        audioEngine.playGrainUnload()
        if (activeVeh != null && activeVeh.grainTankFillL <= 5.0 && activeVeh.attachedImplementUid != null) {
            mutateWorld { unloadAttachedTrailerInWorld(it) }
        } else {
            mutateWorld { unloadGrainInWorld(it) }
        }
    }
    fun unloadAttachedTrailer() {
        audioEngine.playGrainUnload()
        mutateWorld { unloadAttachedTrailerInWorld(it) }
    }
    fun actionManual() { mutateWorld { performManualAction(it) } }
    fun selectManualTool(tool: ManualTool) {
        mutateWorld { it.copy(player = it.player.copy(tool = tool)) }
    }
    fun toggleHeadlights() {
        mutateWorld { cur ->
            val activeUid = cur.activeVehicleUid ?: return@mutateWorld cur
            cur.copy(
                vehicles = cur.vehicles.map { if (it.uid == activeUid) it.copy(headlightsOn = !it.headlightsOn) else it }
            )
        }
    }
    fun markGpsA() { mutateWorld { setGpsPoint(it, "A") } }
    fun markGpsB() { mutateWorld { setGpsPoint(it, "B") } }
    fun toggleGpsAuto() { mutateWorld { toggleGpsAutoSteer(it) } }
    fun playHorn() { audioEngine.playHorn() }

    fun nextDay() {
        val next = mutateWorld { advanceDay(it) }
        refreshFieldTelemetry(next)
        broadcastSyncMessage(MultiplayerSyncCodec.encodeTimeMonth(next.day, next.month))
        saveWorldToSlot(appCtx, next)
        refreshCareerSlots()
    }

    // Finance & Fleet & Workshop
    fun depositBank(amountBrl: Double) {
        audioEngine.playPurchaseSuccess()
        mutateWorld { depositToBank(it, amountBrl) }
    }
    fun withdrawBank(amountBrl: Double) {
        audioEngine.playPurchaseSuccess()
        mutateWorld { withdrawFromBank(it, amountBrl) }
    }
    fun sellCargoAtStation() {
        audioEngine.playGrainUnload()
        mutateWorld { sellCargoAtNearbyStation(it) }
    }
    fun openWorkshopFromWorld() {
        val cur = _worldState.value
        val targetUid = cur.activeVehicleUid ?: cur.vehicles.firstOrNull()?.uid
        if (targetUid != null) {
            openWorkshopForVehicle(targetUid)
        } else {
            openInGameTab(InGameTab.OFICINA)
        }
    }
    fun takeLoan(amountBrl: Double) { mutateWorld { takeBankLoan(it, amountBrl) } }
    fun repayLoan(amountBrl: Double) { mutateWorld { repayBankLoan(it, amountBrl) } }

    fun switchToVehicle(vehicleUid: stringCompat) {
        mutateWorld { cur ->
            val v = cur.vehicles.find { it.uid == vehicleUid } ?: return@mutateWorld cur
            cur.copy(
                activeVehicleUid = vehicleUid,
                player = cur.player.copy(x = v.x, z = v.z, headingDeg = v.headingDeg),
                vehicles = cur.vehicles.map { if (it.uid == vehicleUid) it.copy(engineRunning = true) else it },
            )
        }
        _activeTab.value = null
    }

    fun selectVehicleSeedCrop(vehicleUid: String, cropId: String) {
        mutateWorld { cur ->
            cur.copy(
                vehicles = cur.vehicles.map { if (it.uid == vehicleUid) it.copy(selectedCropId = cropId) else it }
            )
        }
    }

    fun openWorkshopForVehicle(vehicleUid: String) {
        _workshopFocusUid.value = vehicleUid
        navigateTo(AppRoute.Workshop)
    }

    fun customizeVehicleUniversal(vehicleUid: String, patch: UniversalWorkshopPatch) {
        mutateWorld { cur ->
            val veh = cur.vehicles.find { it.uid == vehicleUid } ?: return@mutateWorld cur
            var cost = 0.0
            patch.wheelOptionId?.let { id -> WHEEL_OPTIONS.find { it.id == id }?.let { if (veh.wheelOptionId != id) cost += it.priceBrl } }
            patch.engineModelId?.let { id -> ENGINE_MODELS.find { it.id == id }?.let { if (veh.engineModelId != id) cost += it.priceBrl } }
            patch.engineUpgradeId?.let { id -> ENGINE_UPGRADES.find { it.id == id }?.let { if (veh.engineUpgradeId != id) cost += it.priceBrl } }
            patch.turboId?.let { id -> TURBO_OPTIONS.find { it.id == id }?.let { if (veh.turboId != id) cost += it.priceBrl } }
            patch.rimColorId?.let { id -> RIM_COLORS.find { it.id == id }?.let { if (veh.rimColorId != id) cost += it.priceBrl } }
            patch.cabStyleId?.let { id -> CAB_OPTIONS.find { it.id == id }?.let { if (veh.cabStyleId != id) cost += it.priceBrl } }
            patch.headlightStyleId?.let { id -> HEADLIGHT_OPTIONS.find { it.id == id }?.let { if (veh.headlightStyleId != id) cost += it.priceBrl } }
            patch.exhaustStyleId?.let { id -> EXHAUST_OPTIONS.find { it.id == id }?.let { if (veh.exhaustStyleId != id) cost += it.priceBrl } }
            patch.accessoryId?.let { id -> ACCESSORY_OPTIONS.find { it.id == id }?.let { if (veh.accessoryId != id) cost += it.priceBrl } }
            if (patch.customPaintRgb != null || patch.clearPaint) {
                cost += 4800.0
            }
            if (cur.moneyBrl < cost) {
                return@mutateWorld cur.copy(statusAlert = "Saldo insuficiente na oficina (${formatBrl(cost)}).")
            }
            val nextVeh = veh.copy(
                wheelOptionId = patch.wheelOptionId ?: veh.wheelOptionId,
                engineModelId = patch.engineModelId ?: veh.engineModelId,
                engineUpgradeId = patch.engineUpgradeId ?: veh.engineUpgradeId,
                turboId = patch.turboId ?: veh.turboId,
                rimColorId = patch.rimColorId ?: veh.rimColorId,
                cabStyleId = patch.cabStyleId ?: veh.cabStyleId,
                customPaintRgb = if (patch.clearPaint) null else (patch.customPaintRgb ?: veh.customPaintRgb),
                headlightStyleId = patch.headlightStyleId ?: veh.headlightStyleId,
                exhaustStyleId = patch.exhaustStyleId ?: veh.exhaustStyleId,
                accessoryId = patch.accessoryId ?: veh.accessoryId,
            )
            audioEngine.playPurchaseSuccess()
            cur.copy(
                moneyBrl = cur.moneyBrl - cost,
                vehicles = cur.vehicles.map { if (it.uid == vehicleUid) nextVeh else it },
                ledger = if (cost > 0) cur.ledger + LedgerEntry(cur.day, "Customização Oficina", -cost) else cur.ledger,
                statusAlert = "Customização aplicada na oficina (${formatBrl(cost)})!",
            )
        }
    }

    fun inspectVehicleQuote(vehicleUid: String): WorkshopQuote? =
        _worldState.value.vehicles.find { it.uid == vehicleUid }?.let { inspectVehicle(it) }

    fun repairVehicleFullAction(vehicleUid: String, includeRefuel: Boolean = false) {
        mutateWorld { repairVehicleFull(it, vehicleUid, includeRefuel) }
    }

    fun repairVehicleCompAction(vehicleUid: String, comp: WorkshopComponent) {
        mutateWorld { repairVehicleComponent(it, vehicleUid, comp) }
    }

    fun preventiveMaintenanceAction(vehicleUid: String) {
        mutateWorld { performPreventiveMaintenance(it, vehicleUid) }
    }

    fun washVehicleAction(vehicleUid: String) {
        mutateWorld { washVehicle(it, vehicleUid) }
    }

    fun towVehicleAction(vehicleUid: String) {
        mutateWorld { towAndRecoverVehicle(it, vehicleUid) }
    }

    // Store & Market & Lands & Livestock & Industries & Outsourcing
    fun buyVehicle(defId: String) {
        audioEngine.playPurchaseSuccess()
        mutateWorld { buyVehicleInWorld(it, defId) }
    }

    fun sellVehicle(vehicleUid: String) {
        audioEngine.playPurchaseSuccess()
        mutateWorld { sellVehicleInWorld(it, vehicleUid) }
    }

    fun buyImplement(defId: String) {
        audioEngine.playPurchaseSuccess()
        mutateWorld { buyImplementInWorld(it, defId) }
    }

    fun sellAtWeighbridge(est: EstablishmentDef, prodId: String, amountKg: Double) {
        audioEngine.playPurchaseSuccess()
        mutateWorld { sellProductAtWeighbridge(it, est.id, prodId, amountKg) }
    }

    fun buyPlot(plotId: Int) {
        val user = _accountUser.value
        var bought = false
        val after = mutateWorld { cur ->
            val p = cur.plots.find { it.id == plotId } ?: return@mutateWorld cur
            if (p.owned || p.isPreservation) return@mutateWorld cur
            if (cur.moneyBrl < p.buyPriceBrl) {
                return@mutateWorld cur.copy(statusAlert = "Saldo insuficiente para comprar ${p.name} (${formatBrl(p.buyPriceBrl)}).")
            }
            audioEngine.playPurchaseSuccess()
            bought = true
            cur.copy(
                moneyBrl = cur.moneyBrl - p.buyPriceBrl,
                plots = cur.plots.map {
                    if (it.id == plotId) {
                        it.copy(
                            owned = true,
                            leased = false,
                            ownerPlayerUid = user?.uid ?: it.ownerPlayerUid,
                            ownerPlayerName = user?.displayName ?: it.ownerPlayerName,
                        )
                    } else it
                },
                ledger = cur.ledger + LedgerEntry(cur.day, "Compra de Terra: ${p.name}", -p.buyPriceBrl),
                statusAlert = "${p.name} agora é seu!",
            )
        }
        if (bought) {
            refreshFieldTelemetry(after)
            if (user != null) {
                broadcastSyncMessage(
                    MultiplayerSyncCodec.encodePlotOwnership(
                        plotId = plotId,
                        ownerUid = user.uid,
                        ownerName = user.displayName,
                        owned = true,
                        leased = false,
                    )
                )
            }
        }
    }

    fun sellPlot(plotId: Int) { mutateWorld { sellPlotInWorld(it, plotId) } }
    fun leasePlotWithTerm(plotId: Int, term: String) { mutateWorld { leasePlotWithTermInWorld(it, plotId, term) } }
    fun mergeAdjacentPlots(primaryId: Int, secondaryId: Int) { mutateWorld { mergeAdjacentPlotsInWorld(it, primaryId, secondaryId) } }

    fun placeBuildingNearPlayer(type: BuildingType) {
        mutateWorld { cur ->
            val rad = Math.toRadians(cur.player.headingDeg)
            val bx = cur.player.x + sin(rad) * 14.0
            val bz = cur.player.z + cos(rad) * 14.0
            audioEngine.playPurchaseSuccess()
            placeBuildingInWorld(cur, type, bx, bz)
        }
    }

    // ===== Property Construction Mode, Safe House Replacement & Customization =====
    fun startConstructionPlacement(def: BuildableStructureDef, replaceHouseUid: String? = null) {
        audioEngine.playUiClick()
        val cur = _worldState.value
        _constructionPreview.value = createInitialConstructionPreview(cur, def, replaceHouseUid)
        _activeTab.value = null
    }

    fun moveConstructionPreview(dx: Double, dz: Double) {
        val curPreview = _constructionPreview.value ?: return
        _constructionPreview.value = updateConstructionPreviewTransform(
            state = _worldState.value,
            preview = curPreview,
            newX = curPreview.x + dx,
            newZ = curPreview.z + dz,
        )
    }

    fun setConstructionPreviewPosition(wx: Double, wz: Double) {
        val curPreview = _constructionPreview.value ?: return
        _constructionPreview.value = updateConstructionPreviewTransform(
            state = _worldState.value,
            preview = curPreview,
            newX = wx,
            newZ = wz,
        )
    }

    fun rotateConstructionPreview(deltaDeg: Double) {
        audioEngine.playUiClick()
        val curPreview = _constructionPreview.value ?: return
        _constructionPreview.value = updateConstructionPreviewTransform(
            state = _worldState.value,
            preview = curPreview,
            newRotDeg = curPreview.rotationDeg + deltaDeg,
        )
    }

    fun confirmConstructionPlacement(): Boolean {
        val curPreview = _constructionPreview.value ?: return false
        var succeeded = false
        val after = mutateWorld { cur ->
            val (next, msg) = confirmConstructionPurchase(cur, curPreview)
            if (next !== cur) {
                succeeded = true
                next
            } else {
                cur.copy(statusAlert = msg)
            }
        }
        if (succeeded) {
            audioEngine.playPurchaseSuccess()
            _constructionPreview.value = null
            saveWorldToSlot(appCtx, after)
            refreshCareerSlots()
        }
        return succeeded
    }

    fun cancelConstructionPlacement() {
        audioEngine.playUiClick()
        _constructionPreview.value = null
    }

    fun customizeOwnedHouse(
        buildingUid: String,
        presetId: String? = null,
        wallColorHex: String? = null,
        roofColorHex: String? = null,
        trimColorHex: String? = null,
        materialStyle: String? = null,
        addVeranda: Boolean? = null,
        addGarage: Boolean? = null,
    ): Boolean {
        var changed = false
        val after = mutateWorld { cur ->
            val (next, msg) = customizeOwnedHouseBuilding(
                state = cur,
                buildingUid = buildingUid,
                presetId = presetId,
                wallColorHex = wallColorHex,
                roofColorHex = roofColorHex,
                trimColorHex = trimColorHex,
                materialStyle = materialStyle,
                addVeranda = addVeranda,
                addGarage = addGarage,
            )
            if (next !== cur) {
                changed = true
                next
            } else {
                cur.copy(statusAlert = msg)
            }
        }
        if (changed) {
            audioEngine.playPurchaseSuccess()
            saveWorldToSlot(appCtx, after)
            refreshCareerSlots()
        }
        return changed
    }

    fun checkPropertyEvolutionStatus() = evaluatePropertyEvolution(_worldState.value)

    fun buyAnimals(species: AnimalSpecies, qty: Int) {
        mutateWorld { cur ->
            val meta = ANIMAL_META[species] ?: return@mutateWorld cur
            val total = meta.unitPriceBrl * qty
            if (cur.moneyBrl < total) {
                return@mutateWorld cur.copy(statusAlert = "Saldo insuficiente para comprar $qty ${meta.displayName} (${formatBrl(total)}).")
            }
            audioEngine.playPurchaseSuccess()
            cur.copy(
                moneyBrl = cur.moneyBrl - total,
                herds = cur.herds.map {
                    if (it.species == species) it.copy(count = it.count + qty, feedReserveKg = it.feedReserveKg + qty * 20.0) else it
                },
                ledger = cur.ledger + LedgerEntry(cur.day, "Compra Pecuária: ${qty}x ${meta.displayName}", -total),
                statusAlert = "+$qty ${meta.displayName} adicionados ao rebanho!",
            )
        }
    }

    fun sellAnimalHeads(species: AnimalSpecies, qty: Int) { mutateWorld { sellAnimalHeadInWorld(it, species, qty) } }

    fun feedAndWaterHerd(species: AnimalSpecies) {
        mutateWorld { cur ->
            val meta = ANIMAL_META[species] ?: return@mutateWorld cur
            val herd = cur.herds.find { it.species == species } ?: return@mutateWorld cur
            val cost = max(120.0, herd.count * 24.0)
            if (cur.moneyBrl < cost) {
                return@mutateWorld cur.copy(statusAlert = "Saldo insuficiente para ração e água (${formatBrl(cost)}).")
            }
            cur.copy(
                moneyBrl = cur.moneyBrl - cost,
                herds = cur.herds.map {
                    if (it.species == species) {
                        it.copy(
                            feedReserveKg = it.feedReserveKg + max(250.0, herd.count * 35.0),
                            waterReserveL = it.waterReserveL + max(450.0, herd.count * 80.0),
                            healthPct = min(100.0, it.healthPct + 5.0),
                        )
                    } else it
                },
                ledger = cur.ledger + LedgerEntry(cur.day, "Trato Pecuária: ${meta.displayName}", -cost),
                statusAlert = "Ração e aguada abastecidas para ${meta.displayName}!",
            )
        }
    }

    fun buyIndustry(recipeId: String) {
        mutateWorld { cur ->
            val recipe = INDUSTRY_CHAINS.find { it.id == recipeId } ?: return@mutateWorld cur
            if (cur.moneyBrl < recipe.plantCostBrl) {
                return@mutateWorld cur.copy(statusAlert = "Saldo insuficiente para adquirir ${recipe.industryName} (${formatBrl(recipe.plantCostBrl)}).")
            }
            audioEngine.playPurchaseSuccess()
            cur.copy(
                moneyBrl = cur.moneyBrl - recipe.plantCostBrl,
                industries = cur.industries.map { if (it.recipeId == recipeId) it.copy(purchased = true, stage = 1) else it },
                ledger = cur.ledger + LedgerEntry(cur.day, "Compra Agroindústria: ${recipe.industryName}", -recipe.plantCostBrl),
                statusAlert = "${recipe.industryName} instalada e pronta para operar!",
            )
        }
    }

    fun supplyIndustry(recipeId: String) {
        mutateWorld { cur ->
            val recipe = INDUSTRY_CHAINS.find { it.id == recipeId } ?: return@mutateWorld cur
            val stock = cur.inventoryKg[recipe.inputProductId] ?: 0.0
            if (stock < recipe.inputAmountKg) {
                return@mutateWorld cur.copy(statusAlert = "Precisa de ${recipe.inputAmountKg.roundToInt()} kg de ${recipe.inputProductId} no estoque.")
            }
            val nextInv = cur.inventoryKg.toMutableMap()
            nextInv[recipe.inputProductId] = stock - recipe.inputAmountKg
            cur.copy(
                inventoryKg = nextInv,
                industries = cur.industries.map {
                    if (it.recipeId == recipeId) {
                        it.copy(rawStockKg = it.rawStockKg + recipe.inputAmountKg, isProcessing = true)
                    } else it
                },
                statusAlert = "Lote enviado para processamento em ${recipe.industryName}!",
            )
        }
    }

    fun acceptOrCompleteContract(contractUid: String) { mutateWorld { acceptOrCompleteContractInWorld(it, contractUid) } }
    fun irrigatePlot(plotId: Int) { mutateWorld { irrigatePlotInWorld(it, plotId) } }
    fun applyLimeToPlot(plotId: Int) { mutateWorld { applyLimeToPlotInWorld(it, plotId) } }
    fun sprayPlotPests(plotId: Int) { mutateWorld { sprayPlotPestsInWorld(it, plotId) } }
    fun applyContourProtection(plotId: Int) { mutateWorld { applyContourProtectionInWorld(it, plotId) } }
    fun buyInputStock(inputId: String, kg: Double, totalCostBrl: Double) { mutateWorld { buyInputStockInWorld(it, inputId, kg, totalCostBrl) } }
    fun hireEmployee(name: String, role: String, dailyWageBrl: Double) { mutateWorld { hireEmployeeInWorld(it, name, role, dailyWageBrl) } }
    fun assignEmployee(empUid: String, vehUid: String?, plotId: Int?, taskType: String) { mutateWorld { assignEmployeeInWorld(it, empUid, vehUid, plotId, taskType) } }
    fun fireEmployee(empUid: String) { mutateWorld { fireEmployeeInWorld(it, empUid) } }
    fun placeAuctionBid(lotUid: String, bidBrl: Double) { mutateWorld { placeAuctionBidInWorld(it, lotUid, bidBrl) } }
    fun hireOutsourcingService(params: HireOutsourcingParams) { mutateWorld { hireOutsourcingServiceInWorld(it, params) } }
    fun resolveOutsourcingIncident(contractUid: String, action: String) { mutateWorld { resolveOutsourcingIncidentInWorld(it, contractUid, action) } }
    fun rateOutsourcingContract(contractUid: String, stars: Int) { mutateWorld { rateOutsourcingContractInWorld(it, contractUid, stars) } }
    fun setLandUnit(unit: LandUnit) { mutateWorld { it.copy(landUnit = unit) } }

    // Pivôs Centrais de Irrigação (Compra, Posicionamento, Operação e Reabastecimento)
    fun checkCenterPivotPlacement(modelId: String, plotId: Int?): PivotPlacementCheck {
        val def = com.example.domain.getPivotModelDef(modelId)
        return canPlaceCenterPivot(_worldState.value, def, plotId = plotId)
    }

    fun buyAndPlaceCenterPivotAction(modelId: String, plotId: Int?) {
        val after = mutateWorld { buyAndPlaceCenterPivot(it, modelId = modelId, plotId = plotId) }
        if ((after.centerPivots?.size ?: 0) > (_worldState.value.centerPivots?.size ?: 0)) {
            audioEngine.playPurchaseSuccess()
        }
        saveWorldToSlot(appCtx, after)
        refreshCareerSlots()
    }

    fun toggleCenterPivotActiveAction(pivotUid: String) {
        audioEngine.playUiClick()
        mutateWorld { toggleCenterPivotActive(it, pivotUid) }
    }

    fun toggleCenterPivotReverseAction(pivotUid: String) {
        audioEngine.playUiClick()
        mutateWorld { toggleCenterPivotReverse(it, pivotUid) }
    }

    fun setCenterPivotSpeedAction(pivotUid: String, speedDegPerSec: Double) {
        mutateWorld { setCenterPivotSpeed(it, pivotUid, speedDegPerSec) }
    }

    fun refillCenterPivotWaterAction(pivotUid: String) {
        audioEngine.playPurchaseSuccess()
        mutateWorld { refillCenterPivotWater(it, pivotUid) }
    }

    // ===== Escavação, Prospecção de Água, Açudes, Água Comercial & Piscicultura =====
    fun buyWaterProspectTool() {
        val after = mutateWorld { buyWaterProspectingToolInWorld(it) }
        if (after.hasWaterProbeTool == true) audioEngine.playPurchaseSuccess()
        DiagnosticEngine.recordInfo("Water", "buyWaterProspectTool", after.statusAlert ?: "OK")
    }

    fun markWaterProspectPoint() {
        audioEngine.playProspectingPing()
        val after = mutateWorld { markWaterProspectPointInWorld(it, method = "SONDA_MANUAL") }
        DiagnosticEngine.recordInfo("Water", "markWaterProspectPoint", after.statusAlert ?: "Ponto marcado")
    }

    fun performDrillingSounding() {
        audioEngine.playDrillSounding()
        val after = mutateWorld { performDrillingRigSoundingInWorld(it) }
        DiagnosticEngine.recordInfo("Drilling", "performDrillingSounding", after.statusAlert ?: "Sondagem")
    }

    fun executeEarthworkAction() {
        audioEngine.playHydraulicPulse()
        val after = mutateWorld { executeEarthworkActionInWorld(it) }
        saveWorldToSlot(appCtx, after)
    }

    fun dumpEarthFromMachine() {
        audioEngine.playHydraulicPulse()
        val after = mutateWorld { dumpEarthFromMachineInWorld(it) }
        saveWorldToSlot(appCtx, after)
    }

    fun adjustEarthworkHydraulic(
        boomDeltaDeg: Double = 0.0,
        armDeltaDeg: Double = 0.0,
        bucketDeltaDeg: Double = 0.0,
        swingDeltaDeg: Double = 0.0,
        bladeDeltaM: Double = 0.0,
        dumpBedDeltaDeg: Double = 0.0,
    ) {
        mutateWorld {
            adjustEarthworkHydraulicInWorld(
                state = it,
                boomDeltaDeg = boomDeltaDeg,
                armDeltaDeg = armDeltaDeg,
                bucketDeltaDeg = bucketDeltaDeg,
                swingDeltaDeg = swingDeltaDeg,
                bladeDeltaM = bladeDeltaM,
                dumpBedDeltaDeg = dumpBedDeltaDeg,
            )
        }
    }

    fun buildOrConvertPond(
        pondName: String,
        tier: PondSizeTier,
        fromExistingExcavation: Boolean = false,
    ) {
        audioEngine.playPurchaseSuccess()
        val after = mutateWorld {
            buildOrConvertExcavationToPondInWorld(
                state = it,
                pondName = pondName,
                tier = tier,
                fromExistingExcavation = fromExistingExcavation,
            )
        }
        saveWorldToSlot(appCtx, after)
        refreshCareerSlots()
    }

    fun upgradePondDikes(pondUid: String) {
        audioEngine.playHydraulicPulse()
        val after = mutateWorld { upgradePondDikesInWorld(it, pondUid) }
        saveWorldToSlot(appCtx, after)
    }

    fun installOrTogglePondPump(pondUid: String) {
        audioEngine.playWaterFlow()
        val after = mutateWorld { installPondPumpAndPipingInWorld(it, pondUid) }
        saveWorldToSlot(appCtx, after)
    }

    fun buildWaterReservoir() {
        audioEngine.playPurchaseSuccess()
        val after = mutateWorld { buildCommercialWaterReservoirInWorld(it) }
        saveWorldToSlot(appCtx, after)
    }

    fun pumpPondWaterToReservoir(pondUid: String, litersRequested: Double = 20_000.0) {
        audioEngine.playWaterFlow()
        val after = mutateWorld { pumpWaterFromPondToReservoir(it, pondUid, litersRequested) }
        saveWorldToSlot(appCtx, after)
    }

    fun fillWaterTanker() {
        audioEngine.playWaterFlow()
        val after = mutateWorld { fillWaterTankerInWorld(it) }
        saveWorldToSlot(appCtx, after)
    }

    fun sellWaterCommercial(directFromReservoirLiters: Double? = null) {
        audioEngine.playPurchaseSuccess()
        val after = mutateWorld { sellWaterCommercialDeliveryInWorld(it, directFromReservoirLiters) }
        saveWorldToSlot(appCtx, after)
    }

    fun enablePondPisciculture(pondUid: String) {
        audioEngine.playPurchaseSuccess()
        val after = mutateWorld { enablePiscicultureOnPond(it, pondUid) }
        saveWorldToSlot(appCtx, after)
    }

    fun stockFishFingerlings(pondUid: String, speciesId: String, count: Int = 250) {
        audioEngine.playWaterFlow()
        val after = mutateWorld { stockFishFingerlingsInPond(it, pondUid, speciesId, count) }
        saveWorldToSlot(appCtx, after)
    }

    fun buyPondFishFeed(pondUid: String, feedKg: Double = 80.0) {
        audioEngine.playPurchaseSuccess()
        val after = mutateWorld { buyAndFeedPondFish(it, pondUid, feedKg) }
        saveWorldToSlot(appCtx, after)
    }

    fun maintainPondWater(pondUid: String, maintenanceType: String) {
        audioEngine.playWaterFlow()
        val after = mutateWorld { maintainPondWaterQuality(it, pondUid, maintenanceType) }
        saveWorldToSlot(appCtx, after)
    }

    fun harvestPond(pondUid: String, speciesId: String? = null) {
        audioEngine.playWaterFlow()
        val after = mutateWorld { harvestPondFish(it, pondUid, speciesId) }
        saveWorldToSlot(appCtx, after)
    }

    fun sellHarvestedFish(speciesId: String, kgToSell: Double? = null) {
        audioEngine.playPurchaseSuccess()
        val after = mutateWorld { sellHarvestedFishAtMarket(it, speciesId, kgToSell) }
        saveWorldToSlot(appCtx, after)
    }

    fun runSelfDiagnostics(): RuntimeDiagnosticSnapshot {
        audioEngine.playUiClick()
        val snap = DiagnosticEngine.runWorldIntegrityAudit(_worldState.value)
        mutateWorld { it.copy(diagnosticSnapshot = snap, statusAlert = snap.integrityStatus) }
        return snap
    }

    // Hub & Downloads & Storage Quota
    fun startHubPackDownload(packId: String) {
        val pack = _hubPacks.value.find { it.packId == packId } ?: return
        downloadManager.startDownload(
            pack = pack,
            allPacks = _hubPacks.value,
            quotaConfig = _quotaConfig.value,
            onComplete = { completed ->
                val next = installHubPackInList(_hubPacks.value, completed.packId)
                _hubPacks.value = next
                saveHubPacks(appCtx, next)
                _worldState.value = _worldState.value.copy(statusAlert = "ModHub: '${completed.name}' instalado e ativado!")
            },
            onError = { err ->
                _worldState.value = _worldState.value.copy(statusAlert = err)
            }
        )
    }

    fun pauseHubDownload(packId: String) = downloadManager.pauseDownload(packId)

    fun resumeHubDownload(packId: String) {
        val pack = _hubPacks.value.find { it.packId == packId } ?: return
        downloadManager.resumeDownload(pack) { completed ->
            val next = installHubPackInList(_hubPacks.value, completed.packId)
            _hubPacks.value = next
            saveHubPacks(appCtx, next)
        }
    }

    fun cancelHubDownload(packId: String) = downloadManager.cancelDownload(packId)

    fun updateHubPack(packId: String) {
        val pack = _hubPacks.value.find { it.packId == packId } ?: return
        val latestVersion = getHubMeta(packId).latestVersion
        downloadManager.updatePackInPlace(
            pack = pack,
            latestVersion = latestVersion,
            allPacks = _hubPacks.value,
            quotaConfig = _quotaConfig.value,
            onComplete = {
                val next = updateHubPackInList(_hubPacks.value, packId)
                _hubPacks.value = next
                saveHubPacks(appCtx, next)
                _worldState.value = _worldState.value.copy(
                    statusAlert = "ModHub: '${pack.name}' atualizado para v$latestVersion (saves preservados)!"
                )
            },
            onError = { err ->
                _worldState.value = _worldState.value.copy(statusAlert = err)
            }
        )
    }

    fun installMissingHubDependencies(packId: String) {
        val pack = _hubPacks.value.find { it.packId == packId } ?: return
        val deps = pack.dependencies.orEmpty()
        for (depId in deps) {
            val depPack = _hubPacks.value.find { it.packId == depId } ?: continue
            if (!depPack.installed) {
                startHubPackDownload(depId)
            } else if (!depPack.enabled) {
                toggleHubPackEnabled(depId)
            }
        }
    }

    fun uninstallHubPack(packId: String) {
        val next = uninstallHubPackInList(_hubPacks.value, packId)
        _hubPacks.value = next
        saveHubPacks(appCtx, next)
        downloadManager.markUninstalled(packId)
    }

    fun toggleHubPackEnabled(packId: String) {
        val next = toggleHubPackEnabledInList(_hubPacks.value, packId)
        _hubPacks.value = next
        saveHubPacks(appCtx, next)
        val toggled = next.find { it.packId == packId }
        if (toggled != null) {
            _worldState.value = _worldState.value.copy(
                statusAlert = if (toggled.enabled) "Mod '${toggled.name}' ativado!" else "Mod '${toggled.name}' desativado."
            )
        }
    }

    fun getAvailableDeviceFreeSpaceMb(): Double = downloadManager.getAvailableDeviceSpaceMb()

    fun toggleHubFavorite(packId: String) {
        val cur = _hubFavorites.value
        val next = if (cur.contains(packId)) cur - packId else cur + packId
        _hubFavorites.value = next
        saveHubFavorites(appCtx, next)
    }

    fun importCustomHubMod(rawJson: String): Pair<Boolean, String> {
        return when (val res = validateAndParseCustomModManifest(rawJson, _hubPacks.value)) {
            is CustomModParseResult.Ok -> {
                val next = _hubPacks.value + res.pack
                _hubPacks.value = next
                saveHubPacks(appCtx, next)
                true to "Mod '${res.pack.name}' importado com sucesso!"
            }
            is CustomModParseResult.Err -> false to res.error
        }
    }

    fun updateStorageQuotaConfig(config: StorageQuotaConfig) {
        _quotaConfig.value = config
        saveStorageQuotaConfig(appCtx, config)
    }

    fun storageBreakdown(): StorageBreakdownReport =
        getStorageBreakdown(appCtx, _hubPacks.value, downloadStates.value)

    fun clearLocalBackups(): Int = clearBackupSlots(appCtx)

    // Settings
    fun updateSettings(updater: (GameSettings) -> GameSettings) {
        val next = updater(_settings.value)
        _settings.value = next
        saveSettings(appCtx, next)
        audioEngine.setEnabled(next.soundEnabled)
    }

    fun applyGraphicsPreset(preset: GraphicsPreset) {
        val cfg = (GRAPHICS_PRESET_META[preset] ?: GRAPHICS_PRESET_META[GraphicsPreset.EQUILIBRADO])?.config ?: return
        updateSettings { it.copy(graphicsPreset = preset, customGraphics = cfg) }
    }

    fun detectRecommendedGraphics() {
        val rec = detectRecommendedGraphicsPreset()
        applyGraphicsPreset(rec)
        _worldState.value = _worldState.value.copy(
            statusAlert = "Perfil recomendado detectado: ${GRAPHICS_PRESET_META[rec]?.displayName ?: rec.name}"
        )
    }

    fun updateCustomGraphics(updater: (GraphicsConfig) -> GraphicsConfig) {
        updateSettings { it.copy(graphicsPreset = GraphicsPreset.PERSONALIZADO, customGraphics = updater(it.customGraphics)) }
    }

    fun updateHudLayout(patch: HudLayoutConfig) {
        updateSettings { it.copy(hudLayout = patch) }
    }

    fun setHitchMode(mode: HitchMode) {
        audioEngine.playUiClick()
        updateSettings { it.copy(hitchMode = mode) }
    }

    fun updateFieldPanelConfig(updater: (FieldInfoPanelConfig) -> FieldInfoPanelConfig) {
        val before = _settings.value.fieldPanelConfig
        val updated = updater(before)
        updateSettings { it.copy(fieldPanelConfig = updated) }
        if (updated.defaultViewMode != before.defaultViewMode) {
            _fieldPanelExpanded.value = updated.defaultViewMode == FieldPanelViewMode.EXPANDIDO
        }
    }

    fun updateKeyBinding(actionKey: String, binding: String) {
        updateSettings { cur ->
            val kb = cur.keyBindings
            val nextKb = when (actionKey) {
                "hitch" -> kb.copy(hitch = binding)
                "lowerActivate" -> kb.copy(lowerActivate = binding)
                "enterExit" -> kb.copy(enterExit = binding)
                "map" -> kb.copy(map = binding)
                "camera" -> kb.copy(camera = binding)
                "lights" -> kb.copy(lights = binding)
                "horn" -> kb.copy(horn = binding)
                else -> kb
            }
            cur.copy(keyBindings = nextKb)
        }
    }

    private fun broadcastSyncMessage(text: String) {
        val code = _activeRoomCode.value ?: return
        val user = _accountUser.value ?: return
        if (!currentRoomSharedFarm) return
        viewModelScope.launch {
            runCatching {
                mpRepo.sendChatMessage(
                    roomCode = code,
                    senderName = user.displayName,
                    text = text,
                )
            }
        }
    }

    fun clearMpCompatibilityReport() {
        _mpCompatibilityReport.value = null
    }

    // Multiplayer Online Cooperativo
    fun hostMultiplayerRoom(roomCode: String, sharedFarm: Boolean = true, pauseOnDisconnect: Boolean = true) {
        val user = _accountUser.value ?: return
        val cur = _worldState.value
        val activePacks = _hubPacks.value.filter { it.installed && it.enabled }
        val mapDef = getMapById(cur.mapId)
        val mapVer = mapDef.requiredPackId?.let { pid -> activePacks.find { it.packId == pid }?.version } ?: "2.0.0"
        _mpCompatibilityReport.value = null
        viewModelScope.launch {
            _mpStatus.value = MpConnectionStatus.CONNECTING
            runCatching {
                mpRepo.createOrHostRoom(
                    roomCode = roomCode,
                    hostName = user.displayName,
                    mapId = cur.mapId,
                    mapVersion = mapVer,
                    mapAreaTierId = cur.mapAreaTierId ?: "8X",
                    sharedFarmMode = sharedFarm,
                    pauseOnDisconnect = pauseOnDisconnect,
                    requiredModIds = activePacks.map { it.packId },
                    requiredModVersions = activePacks.associate { it.packId to it.version },
                )
                val cleanRoom = roomCode.trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "-").take(32)
                _mpIsHost.value = true
                attachToRoom(cleanRoom)
            }.onFailure { err ->
                _mpStatus.value = MpConnectionStatus.DISCONNECTED
                _worldState.value = cur.copy(statusAlert = "Erro ao criar sala: ${err.localizedMessage}")
            }
        }
    }

    fun joinMultiplayerRoom(roomCode: String) {
        val user = _accountUser.value ?: return
        val cur = _worldState.value
        val cleanRoom = roomCode.trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "-").take(32)
        val cachedRoom = _openRooms.value.find { it.roomCode.equals(cleanRoom, ignoreCase = true) }
        if (cachedRoom != null) {
            val report = evaluateRoomCompatibility(cachedRoom, _hubPacks.value)
            _mpCompatibilityReport.value = report
            if (!report.isCompatible) {
                _mpStatus.value = MpConnectionStatus.DISCONNECTED
                _worldState.value = cur.copy(statusAlert = "Entrada bloqueada: ${report.summaryMessage}")
                return
            }
        }
        viewModelScope.launch {
            _mpStatus.value = MpConnectionStatus.CONNECTING
            runCatching {
                val room = mpRepo.joinRoomAsGuest(
                    roomCode = cleanRoom,
                    playerName = user.displayName,
                    x = cur.player.x,
                    z = cur.player.z,
                    installedPacks = _hubPacks.value,
                )
                _mpCompatibilityReport.value = evaluateRoomCompatibility(room, _hubPacks.value)
                _mpIsHost.value = room.hostUid == user.uid
                attachToRoom(room.roomCode)
            }.onFailure { err ->
                _mpStatus.value = MpConnectionStatus.DISCONNECTED
                _worldState.value = cur.copy(statusAlert = "Erro ao entrar na sala: ${err.localizedMessage}")
            }
        }
    }

    private fun scheduleMultiplayerReconnect(roomCode: String) {
        if (reconnectJob?.isActive == true) return
        if (_activeRoomCode.value != roomCode) return
        reconnectJob = viewModelScope.launch {
            val waitMs = computeMpReconnectBackoffMs(reconnectAttempts)
            reconnectAttempts = (reconnectAttempts + 1).coerceAtMost(8)
            delay(waitMs)
            if (_activeRoomCode.value == roomCode) {
                _mpStatus.value = MpConnectionStatus.CONNECTING
                attachToRoom(roomCode)
            }
        }
    }

    fun reconnectMultiplayerRoom() {
        val code = _activeRoomCode.value ?: return
        reconnectJob?.cancel()
        reconnectAttempts = 0
        _mpStatus.value = MpConnectionStatus.CONNECTING
        attachToRoom(code)
    }

    private fun attachToRoom(roomCode: String) {
        _activeRoomCode.value = roomCode
        _mpStatus.value = MpConnectionStatus.CONNECTED

        roomSessionJob?.cancel()
        roomSessionJob = viewModelScope.launch {
            launch {
                mpRepo.observeRoom(roomCode)
                    .catch {
                        _mpStatus.value = MpConnectionStatus.PAUSED_BY_DISCONNECT
                        scheduleMultiplayerReconnect(roomCode)
                    }
                    .collect { room ->
                        if (room != null) {
                            currentRoomSharedFarm = room.sharedFarmMode
                            currentRoomPauseOnDisconnect = room.pauseOnDisconnect
                            if (room.status == "CLOSED") {
                                _worldState.value = _worldState.value.copy(
                                    statusAlert = "Sala '$roomCode' encerrada pelo Host."
                                )
                                leaveMultiplayerRoom()
                            }
                        }
                    }
            }
            launch {
                mpRepo.observeRoomMembers(roomCode)
                    .catch {
                        _mpStatus.value = MpConnectionStatus.PAUSED_BY_DISCONNECT
                        scheduleMultiplayerReconnect(roomCode)
                    }
                    .collect { members -> _roomMembers.value = members }
            }
            launch {
                mpRepo.observeRoomMessages(roomCode)
                    .catch {
                        _mpStatus.value = MpConnectionStatus.PAUSED_BY_DISCONNECT
                        scheduleMultiplayerReconnect(roomCode)
                    }
                    .collect { msgs ->
                        val localUid = _accountUser.value?.uid
                        val visibleChat = mutableListOf<RoomChatDoc>()
                        for (msg in msgs) {
                            if (msg.text.startsWith(MP_SYNC_PREFIX)) {
                                if (currentRoomSharedFarm && msg.senderUid != localUid) {
                                    val dedupKey = msg.uid.ifBlank { "${msg.senderUid}:${msg.text}" }
                                    if (appliedSyncIds.add(dedupKey)) {
                                        parseSharedSyncEvent(msg.text)?.let { (kind, value) ->
                                            if (kind == "WEATHER") {
                                                runCatching { WeatherType.valueOf(value) }.getOrNull()?.let { targetWeather ->
                                                    _worldState.value = _worldState.value.copy(
                                                        weather = targetWeather,
                                                        statusAlert = "Clima sincronizado na sala: $value",
                                                    )
                                                }
                                            } else {
                                                val synced = mutateWorld { cur ->
                                                    MultiplayerSyncCodec.applySyncMessage(cur, kind, value)
                                                }
                                                refreshFieldTelemetry(synced)
                                            }
                                        }
                                    }
                                }
                            } else {
                                visibleChat.add(msg)
                            }
                        }
                        _roomMessages.value = visibleChat
                    }
            }
        }

        telemetryPushJob?.cancel()
        telemetryPushJob = viewModelScope.launch {
            var consecutiveFailures = 0
            while (isActive && _activeRoomCode.value == roomCode) {
                val user = _accountUser.value
                val cur = _worldState.value
                val v = cur.activeVehicleUid?.let { uid -> cur.vehicles.find { it.uid == uid } }
                if (user != null) {
                    val pushResult = runCatching {
                        mpRepo.updateLocalTelemetry(
                            roomCode = roomCode,
                            playerName = user.displayName,
                            x = v?.x ?: cur.player.x,
                            z = v?.z ?: cur.player.z,
                            vehicleDefId = v?.defId,
                            headingDeg = v?.headingDeg ?: cur.player.headingDeg,
                            pitchDeg = v?.pitchDeg ?: 0.0,
                            rollDeg = v?.rollDeg ?: 0.0,
                            steerAngleDeg = v?.steerAngleDeg ?: 0.0,
                            wheelSpinDeg = v?.wheelSpinDeg ?: 0.0,
                            suspensionOffsetY = v?.suspensionOffsetY ?: 0.0,
                        )
                    }
                    if (pushResult.isSuccess) {
                        consecutiveFailures = 0
                        reconnectAttempts = 0
                        if (_mpStatus.value != MpConnectionStatus.CONNECTED) {
                            _mpStatus.value = MpConnectionStatus.CONNECTED
                        }
                    } else {
                        consecutiveFailures++
                        if (consecutiveFailures >= 3) {
                            _mpStatus.value = MpConnectionStatus.PAUSED_BY_DISCONNECT
                            scheduleMultiplayerReconnect(roomCode)
                        }
                    }
                }
                delay(180L)
            }
        }
    }

    fun approveRoomMember(memberUid: String, approve: Boolean) {
        val code = _activeRoomCode.value ?: return
        viewModelScope.launch {
            runCatching { mpRepo.approveMember(code, memberUid, approve) }
        }
    }

    fun sendRoomChat(text: String) {
        val code = _activeRoomCode.value ?: return
        val user = _accountUser.value ?: return
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            runCatching { mpRepo.sendChatMessage(code, user.displayName, clean) }
        }
    }

    fun leaveMultiplayerRoom() {
        val code = _activeRoomCode.value
        val wasHost = _mpIsHost.value
        roomSessionJob?.cancel()
        telemetryPushJob?.cancel()
        reconnectJob?.cancel()
        reconnectAttempts = 0
        appliedSyncIds.clear()
        _activeRoomCode.value = null
        _roomMembers.value = emptyList()
        _roomMessages.value = emptyList()
        _mpIsHost.value = false
        _mpStatus.value = MpConnectionStatus.DISCONNECTED
        if (wasHost && code != null) {
            viewModelScope.launch {
                runCatching { mpRepo.closeRoom(code) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        simJob?.cancel()
        audioEngine.release()
    }
}

private typealias stringCompat = String
