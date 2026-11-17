package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Agriculture
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.AquiferAvailabilityTier
import com.example.domain.FISH_SPECIES_CATALOG
import com.example.domain.ManualTool
import com.example.domain.PondSizeTier
import com.example.domain.PondStructure
import com.example.domain.VehicleBodyStyle
import com.example.domain.WaterBusinessState
import com.example.domain.WorldState
import com.example.domain.getEarthCapacityM3
import com.example.domain.getFishSpeciesById
import com.example.domain.getVehicleDef
import com.example.domain.getWaterTankCapacityL
import com.example.domain.isEarthmovingVehicle
import com.example.domain.isWaterTankerVehicle
import com.example.simulation.DIKE_REINFORCEMENT_COST_BRL
import com.example.simulation.FISH_FEED_PRICE_PER_KG_BRL
import com.example.simulation.PUMP_PIPING_COST_BRL
import com.example.simulation.RESERVOIR_UPGRADE_COST_BRL
import com.example.simulation.WATER_PROBE_PRICE_BRL
import com.example.simulation.WATER_SALE_PRICE_PER_1000L_BRL
import com.example.simulation.evaluateProspectingAt
import com.example.simulation.formatBrl
import com.example.state.FarmViewModel
import com.example.ui.components.SectionCard
import com.example.ui.components.StatBadge
import com.example.ui.theme.AgroSprout
import com.example.ui.theme.CabinCard
import com.example.ui.theme.HarvestGold
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusWarning
import java.util.Locale
import kotlin.math.roundToInt

private enum class WaterFishSubtab(val label: String, val tag: String) {
    PROSPECCAO("Prospecção & Poços", "subtab_prospeccao"),
    ACUDES("Açudes & Obras", "subtab_acudes"),
    AGUA_COMERCIAL("Água Comercial", "subtab_agua_comercial"),
    PISCICULTURA("Piscicultura", "subtab_piscicultura"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaterAndPiscicultureTabContent(
    viewModel: FarmViewModel,
    worldState: WorldState,
) {
    var selectedSubtab by remember { mutableStateOf(WaterFishSubtab.PROSPECCAO) }
    val liveScan by viewModel.prospectingScan.collectAsStateWithLifecycle()

    val activeVeh = worldState.activeVehicleUid?.let { uid -> worldState.vehicles.find { it.uid == uid } }
    val activeDef = activeVeh?.let { getVehicleDef(it.defId) }
    val currentX = activeVeh?.x ?: worldState.player.x
    val currentZ = activeVeh?.z ?: worldState.player.z
    val scan = liveScan ?: remember(currentX, currentZ, worldState.aquiferGrid) {
        evaluateProspectingAt(worldState, currentX, currentZ)
    }
    val ponds = worldState.ponds.orEmpty()
    val waterBiz = worldState.waterBusiness ?: WaterBusinessState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            WaterFishSubtab.entries.forEach { tab ->
                FilterChip(
                    selected = selectedSubtab == tab,
                    onClick = { selectedSubtab = tab },
                    label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.height(48.dp).testTag(tab.tag),
                )
            }
        }

        when (selectedSubtab) {
            WaterFishSubtab.PROSPECCAO -> ProspeccaoSubtabContent(
                viewModel = viewModel,
                worldState = worldState,
                scan = scan,
                activeVeh = activeVeh,
                activeDef = activeDef,
            )
            WaterFishSubtab.ACUDES -> AcudesObrasSubtabContent(
                viewModel = viewModel,
                worldState = worldState,
                ponds = ponds,
                activeVeh = activeVeh,
                activeDef = activeDef,
            )
            WaterFishSubtab.AGUA_COMERCIAL -> AguaComercialSubtabContent(
                viewModel = viewModel,
                worldState = worldState,
                ponds = ponds,
                waterBiz = waterBiz,
                activeVeh = activeVeh,
                activeDef = activeDef,
            )
            WaterFishSubtab.PISCICULTURA -> PisciculturaSubtabContent(
                viewModel = viewModel,
                worldState = worldState,
                ponds = ponds,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProspeccaoSubtabContent(
    viewModel: FarmViewModel,
    worldState: WorldState,
    scan: com.example.domain.ProspectingScanResult,
    activeVeh: com.example.domain.OwnedVehicle?,
    activeDef: com.example.domain.VehicleDef?,
) {
    val hasProbe = worldState.hasWaterProbeTool == true
    val isProbeEquipped = worldState.player.tool == ManualTool.SONDA_AGUA && activeVeh == null
    val isDrillingRig = activeDef?.bodyStyle == VehicleBodyStyle.DRILLING_RIG
    val points = worldState.waterProspectPoints.orEmpty()
    val aquifers = worldState.aquiferGrid.orEmpty()

    val tierColor = when (scan.availability) {
        AquiferAvailabilityTier.MUITO_ALTA -> StatusGreen
        AquiferAvailabilityTier.ALTA -> StatusInfo
        AquiferAvailabilityTier.MEDIA -> HarvestGold
        AquiferAvailabilityTier.BAIXA -> StatusWarning
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(
                title = "Sonda Portátil de Prospecção Hídrica",
                subtitle = if (hasProbe) {
                    "Equipamento adquirido! Segure a Sonda a pé para ler o lençol freático com sonar."
                } else {
                    "Adquira o geofone portátil (${formatBrl(WATER_PROBE_PRICE_BRL)}) para detectar reservas subterrâneas."
                },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBadge(
                        if (hasProbe) if (isProbeEquipped) "SONDA NA MÃO" else "NO INVENTÁRIO" else "NÃO ADQUIRIDA",
                        if (isProbeEquipped) StatusGreen else if (hasProbe) HarvestGold else StatusWarning,
                    )
                    StatBadge("${points.size} pontos marcados", StatusInfo)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!hasProbe) {
                        Button(
                            onClick = { viewModel.buyWaterProspectTool() },
                            colors = ButtonDefaults.buttonColors(containerColor = HarvestGold, contentColor = Color(0xFF0E1310)),
                            modifier = Modifier.height(48.dp).testTag("buy_water_probe_btn"),
                        ) {
                            Icon(Icons.Rounded.WaterDrop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Comprar Sonda (${formatBrl(WATER_PROBE_PRICE_BRL)})", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        FilledTonalButton(
                            onClick = {
                                viewModel.selectManualTool(
                                    if (worldState.player.tool == ManualTool.SONDA_AGUA) ManualTool.MAOS else ManualTool.SONDA_AGUA
                                )
                            },
                            modifier = Modifier.height(48.dp).testTag("buy_water_probe_btn"),
                        ) {
                            Icon(Icons.Rounded.WaterDrop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (worldState.player.tool == ManualTool.SONDA_AGUA) "Guardar Sonda" else "Equipar Sonda na Mão")
                        }
                    }

                    Button(
                        onClick = { viewModel.markWaterProspectPoint() },
                        enabled = hasProbe,
                        modifier = Modifier.height(48.dp).testTag("mark_prospect_point_btn"),
                    ) {
                        Text("Marcar Ponto no Mapa")
                    }

                    if (isDrillingRig) {
                        FilledTonalButton(
                            onClick = { viewModel.performDrillingSounding() },
                            modifier = Modifier.height(48.dp).testTag("drill_sounding_btn"),
                        ) {
                            Icon(Icons.Rounded.Construction, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sondagem c/ Perfuratriz")
                        }
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Leitura Subterrânea da Posição Atual",
                subtitle = scan.summaryLabel,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBadge("Disp.: ${scan.availability.label}", tierColor)
                    StatBadge("Prof.: ${String.format(Locale.US, "%.1f", scan.estimatedDepthM)}m", StatusInfo)
                    StatBadge("Qualidade: ${scan.qualityPct.roundToInt()}%", AgroSprout)
                }
                LinearProgressIndicator(
                    progress = { scan.signalStrength.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = tierColor,
                )
                Text(
                    "Recarga natural estimada: ${scan.estimatedRechargeLPerDay.roundToInt()} L/dia • Intervalo sonar ${scan.audioBeepIntervalMs} ms.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Text("Pontos de Água Marcados (${points.size})", style = MaterialTheme.typography.titleMedium)
        }
        if (points.isEmpty()) {
            item {
                SectionCard(title = "Nenhum ponto marcado ainda") {
                    Text(
                        "Equipe a Sonda de Água, caminhe pelo mapa e toque em 'Marcar Ponto no Mapa' ao encontrar sinal Alta ou Muito alta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(points, key = { it.uid }) { pt ->
                SectionCard(
                    title = pt.label,
                    subtitle = "Coord. (${pt.x.roundToInt()}m, ${pt.z.roundToInt()}m) • Método: ${pt.method} • Dia ${pt.discoveredDay}",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatBadge(pt.availability.label, StatusInfo)
                        StatBadge("${String.format(Locale.US, "%.1f", pt.estimatedDepthM)}m", HarvestGold)
                        StatBadge("${pt.rechargeRateLPerDay.roundToInt()} L/dia", AgroSprout)
                    }
                    OutlinedButton(
                        onClick = { viewModel.teleportToLocation(pt.x, pt.z, pt.label) },
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text("Ir até este Ponto")
                    }
                }
            }
        }

        item {
            Text("Bacias Aquíferas Regionais (${aquifers.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(aquifers, key = { it.id }) { aq ->
            val pct = ((aq.remainingCapacityL / aq.totalCapacityL.coerceAtLeast(1.0)) * 100.0).coerceIn(0.0, 100.0)
            SectionCard(
                title = aq.name,
                subtitle = "${aq.availability.label} • Lençol a ${String.format(Locale.US, "%.1f", aq.waterTableDepthM)}m • Qualidade ${aq.qualityPct.roundToInt()}%",
            ) {
                LinearProgressIndicator(
                    progress = { (pct / 100.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = StatusInfo,
                )
                Text(
                    "Reserva: ${(aq.remainingCapacityL / 1000.0).roundToInt()} / ${(aq.totalCapacityL / 1000.0).roundToInt()} mil L • Recarga ${aq.rechargePerDayL.roundToInt()} L/dia",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AcudesObrasSubtabContent(
    viewModel: FarmViewModel,
    worldState: WorldState,
    ponds: List<PondStructure>,
    activeVeh: com.example.domain.OwnedVehicle?,
    activeDef: com.example.domain.VehicleDef?,
) {
    var pondNameInput by remember { mutableStateOf("Açude São José") }
    var selectedTier by remember { mutableStateOf(PondSizeTier.PEQUENO) }
    val isEarthmover = activeDef != null && isEarthmovingVehicle(activeDef)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(
                title = "Terraplanagem & Escavação de Terreno",
                subtitle = if (isEarthmover && activeVeh != null && activeDef != null) {
                    "${activeDef.brand} ${activeDef.model} • Caçamba: ${String.format(Locale.US, "%.1f", activeVeh.carriedEarthM3 ?: 0.0)} / ${String.format(Locale.US, "%.1f", getEarthCapacityM3(activeDef))} m³"
                } else {
                    "Assuma uma Escavadeira, Retroescavadeira, Patrola, Trator de Esteira, Pá-Carregadeira ou Basculante na loja/frota."
                },
            ) {
                if (isEarthmover && activeVeh != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatBadge("Lança: ${(activeVeh.hydraulicBoomDeg ?: 20.0).roundToInt()}°", HarvestGold)
                        StatBadge("Caçamba: ${(activeVeh.bucketCurlDeg ?: 12.0).roundToInt()}°", StatusInfo)
                        StatBadge("Marcas 3D: ${worldState.earthworkMarks.orEmpty().size}", AgroSprout)
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { viewModel.executeEarthworkAction() },
                            modifier = Modifier.height(48.dp).testTag("exec_earthwork_btn"),
                        ) {
                            Icon(Icons.Rounded.Construction, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Escavar / Nivelar Agora")
                        }
                        FilledTonalButton(
                            onClick = { viewModel.dumpEarthFromMachine() },
                            modifier = Modifier.height(48.dp).testTag("dump_earth_btn"),
                        ) {
                            Text("Despejar Terra / Formar Talude")
                        }
                        OutlinedButton(
                            onClick = { viewModel.adjustEarthworkHydraulic(boomDeltaDeg = -8.0, bucketDeltaDeg = -10.0) },
                            modifier = Modifier.height(48.dp),
                        ) {
                            Text("Baixar Braço/Caçamba")
                        }
                        OutlinedButton(
                            onClick = { viewModel.adjustEarthworkHydraulic(boomDeltaDeg = 8.0, bucketDeltaDeg = 10.0) },
                            modifier = Modifier.height(48.dp),
                        ) {
                            Text("Erguer Braço/Caçamba")
                        }
                    }
                } else {
                    FilledTonalButton(
                        onClick = { viewModel.openInGameTab(com.example.state.InGameTab.LOJA) },
                        modifier = Modifier.height(48.dp),
                    ) {
                        Icon(Icons.Rounded.Agriculture, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ir para Loja de Máquinas Pesadas")
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Construir Açude ou Converter Escavação",
                subtitle = "Escolha o porte e construa na posição atual ou aproveite a cava escavada (-75% no custo).",
            ) {
                OutlinedTextField(
                    value = pondNameInput,
                    onValueChange = { pondNameInput = it },
                    label = { Text("Nome do Açude / Lago") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pond_name_input"),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PondSizeTier.entries.forEach { tier ->
                        FilterChip(
                            selected = selectedTier == tier,
                            onClick = { selectedTier = tier },
                            label = { Text(tier.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(48.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val clean = pondNameInput.trim().ifBlank { "Açude Rural" }
                            viewModel.buildOrConvertPond(clean, selectedTier, fromExistingExcavation = false)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HarvestGold, contentColor = Color(0xFF0E1310)),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("build_pond_btn"),
                    ) {
                        Text("Construir (${formatBrl(selectedTier.buildCostBrl)})", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledTonalButton(
                        onClick = {
                            val clean = pondNameInput.trim().ifBlank { "Açude Escavado" }
                            viewModel.buildOrConvertPond(clean, selectedTier, fromExistingExcavation = true)
                        },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("convert_pond_btn"),
                    ) {
                        Text("Converter Cava (${formatBrl(selectedTier.buildCostBrl * 0.25)})", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        item {
            Text("Açudes & Lagos na Propriedade (${ponds.size})", style = MaterialTheme.typography.titleMedium)
        }
        if (ponds.isEmpty()) {
            item {
                SectionCard(title = "Nenhum açude construído") {
                    Text(
                        "Construa um açude acima ou escave uma bacia com sua Escavadeira para captar água subterrânea.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(ponds, key = { it.uid }) { pond ->
                PondManagementCard(viewModel = viewModel, pond = pond)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PondManagementCard(
    viewModel: FarmViewModel,
    pond: PondStructure,
) {
    val fillPct = ((pond.waterVolumeL / pond.maxVolumeL.coerceAtLeast(1.0)) * 100.0).coerceIn(0.0, 100.0)
    SectionCard(
        title = pond.name,
        subtitle = "${pond.sizeTier.label} • Raio ${pond.radiusM.roundToInt()}m • Prof. ${String.format(Locale.US, "%.1f", pond.depthM)}m",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBadge("Nível ${String.format(Locale.US, "%.2f", pond.waterLevelM)}m (${fillPct.roundToInt()}%)", StatusInfo)
            StatBadge("Talude ${pond.dikeIntegrityPct.roundToInt()}%", if (pond.dikeIntegrityPct >= 80) AgroSprout else StatusWarning)
            StatBadge("Qual. ${pond.waterQualityPct.roundToInt()}%", StatusGreen)
        }
        LinearProgressIndicator(
            progress = { (fillPct / 100.0).toFloat() },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = StatusInfo,
        )
        Text(
            "Volume: ${(pond.waterVolumeL / 1000.0).roundToInt()} / ${(pond.maxVolumeL / 1000.0).roundToInt()} mil L • Bomba: ${if (pond.hasPump) if (pond.pumpActive) "Ligada" else "Instalada" else "Sem bomba"}",
            style = MaterialTheme.typography.bodySmall,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(
                onClick = { viewModel.upgradePondDikes(pond.uid) },
                modifier = Modifier.height(48.dp).testTag("upgrade_dike_btn_${pond.uid}"),
            ) {
                Text("Reforçar Taludes (${formatBrl(DIKE_REINFORCEMENT_COST_BRL)})")
            }
            FilledTonalButton(
                onClick = { viewModel.installOrTogglePondPump(pond.uid) },
                modifier = Modifier.height(48.dp).testTag("pump_pond_btn_${pond.uid}"),
            ) {
                Text(
                    when {
                        !pond.hasPump -> "Instalar Bomba (${formatBrl(PUMP_PIPING_COST_BRL)})"
                        pond.pumpActive -> "Desligar Bomba"
                        else -> "Ligar Bomba"
                    }
                )
            }
            OutlinedButton(
                onClick = { viewModel.teleportToLocation(pond.centerX, pond.centerZ, pond.name) },
                modifier = Modifier.height(48.dp),
            ) {
                Text("Ir ao Açude")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AguaComercialSubtabContent(
    viewModel: FarmViewModel,
    worldState: WorldState,
    ponds: List<PondStructure>,
    waterBiz: WaterBusinessState,
    activeVeh: com.example.domain.OwnedVehicle?,
    activeDef: com.example.domain.VehicleDef?,
) {
    val isTanker = activeDef != null && isWaterTankerVehicle(activeDef)
    val tankerCap = activeDef?.let { getWaterTankCapacityL(it) } ?: 0.0
    val tankerFill = activeVeh?.carriedWaterL ?: 0.0
    val resRatio = (waterBiz.reservoirVolumeL / waterBiz.reservoirMaxL.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(
                title = "Reservatório Comercial & Rede de Abastecimento",
                subtitle = if (waterBiz.hasReservoirStructure) {
                    "Reservatório ativo: ${(waterBiz.reservoirVolumeL / 1000.0).roundToInt()} / ${(waterBiz.reservoirMaxL / 1000.0).roundToInt()} mil L"
                } else {
                    "Construa o Reservatório Comercial para armazenar água bombeada dos açudes e negociar em grande escala."
                },
            ) {
                if (waterBiz.hasReservoirStructure) {
                    LinearProgressIndicator(
                        progress = { resRatio },
                        modifier = Modifier.fillMaxWidth().height(10.dp),
                        color = StatusInfo,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBadge("Extraído: ${(waterBiz.totalExtractedL / 1000.0).roundToInt()} m³", StatusInfo)
                    StatBadge("Receita: ${formatBrl(waterBiz.totalWaterRevenueBrl)}", StatusGreen)
                    StatBadge("Despesas: ${formatBrl(waterBiz.totalExpensesBrl)}", StatusWarning)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { viewModel.buildWaterReservoir() },
                        colors = ButtonDefaults.buttonColors(containerColor = HarvestGold, contentColor = Color(0xFF0E1310)),
                        modifier = Modifier.height(48.dp).testTag("build_reservoir_btn"),
                    ) {
                        Text(
                            if (waterBiz.hasReservoirStructure) "Ampliar Reservatório (${formatBrl(RESERVOIR_UPGRADE_COST_BRL)})"
                            else "Construir Reservatório (${formatBrl(RESERVOIR_UPGRADE_COST_BRL)})",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    FilledTonalButton(
                        onClick = { viewModel.sellWaterCommercial(directFromReservoirLiters = 10_000.0) },
                        enabled = waterBiz.hasReservoirStructure && waterBiz.reservoirVolumeL >= 1000.0 && !isTanker,
                        modifier = Modifier.height(48.dp).testTag("sell_water_btn"),
                    ) {
                        Text("Vender 10 mil L via Rede (${formatBrl(10.0 * WATER_SALE_PRICE_PER_1000L_BRL)})")
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Caminhão-Pipa & Entrega Comercial",
                subtitle = if (isTanker) {
                    "${activeDef?.brand} ${activeDef?.model} • Tanque ${tankerFill.roundToInt()} / ${tankerCap.roundToInt()} L"
                } else {
                    "Assuma um Caminhão-Pipa para captar água em açudes/estações e entregar no Centro de Comercialização de Água."
                },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { viewModel.fillWaterTanker() },
                        enabled = isTanker,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("fill_tanker_btn"),
                    ) {
                        Icon(Icons.Rounded.Waves, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Abastecer Pipa")
                    }
                    Button(
                        onClick = { viewModel.sellWaterCommercial(directFromReservoirLiters = null) },
                        enabled = isTanker && tankerFill > 50.0,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Rounded.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Vender Carga Pipa")
                    }
                }
            }
        }

        if (waterBiz.hasReservoirStructure && ponds.isNotEmpty()) {
            item {
                Text("Bombeamento dos Açudes para o Reservatório", style = MaterialTheme.typography.titleMedium)
            }
            items(ponds, key = { "pump_${it.uid}" }) { pond ->
                SectionCard(
                    title = pond.name,
                    subtitle = "Disponível: ${(pond.waterVolumeL / 1000.0).roundToInt()} mil L • Bomba ${if (pond.hasPump) "OK" else "Necessária"}",
                ) {
                    Button(
                        onClick = { viewModel.pumpPondWaterToReservoir(pond.uid, 20_000.0) },
                        enabled = pond.hasPump && pond.waterVolumeL >= 1000.0,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text("Bombear até 20 mil L para Reservatório")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PisciculturaSubtabContent(
    viewModel: FarmViewModel,
    worldState: WorldState,
    ponds: List<PondStructure>,
) {
    var selectedPondUid by remember(ponds) { mutableStateOf(ponds.firstOrNull()?.uid) }
    val selectedPond = ponds.find { it.uid == selectedPondUid } ?: ponds.firstOrNull()
    var selectedSpeciesId by remember { mutableStateOf(FISH_SPECIES_CATALOG.first().id) }
    var fingerlingCount by remember { mutableIntStateOf(250) }

    val fishInv = worldState.inventoryKg.filter { (k, v) -> k.startsWith("peixe_") && v > 0.1 }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (ponds.isEmpty()) {
            item {
                SectionCard(
                    title = "Construa um Açude Primeiro",
                    subtitle = "A piscicultura depende de um açude com lâmina d'água suficiente.",
                ) {
                    Text(
                        "Acesse a aba 'Açudes & Obras' para escavar ou construir seu primeiro açude antes de soltar alevinos.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        } else {
            item {
                SectionCard(title = "Selecionar Açude para Cultivo") {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ponds.forEach { p ->
                            FilterChip(
                                selected = selectedPond?.uid == p.uid,
                                onClick = { selectedPondUid = p.uid },
                                label = { Text(p.name, style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.height(48.dp),
                            )
                        }
                    }
                    selectedPond?.let { pond ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBadge(if (pond.piscicultureEnabled) "Piscicultura Ativa" else "Inativa", if (pond.piscicultureEnabled) StatusGreen else StatusWarning)
                            StatBadge("O₂ ${String.format(Locale.US, "%.1f", pond.dissolvedO2MgL)} mg/L", StatusInfo)
                            StatBadge("pH ${String.format(Locale.US, "%.1f", pond.waterPh)}", AgroSprout)
                        }
                        Text(
                            "Ração estocada: ${pond.fishFeedStockKg.roundToInt()} kg • Temp: ${String.format(Locale.US, "%.1f", pond.temperatureC)}°C • Turbidez: ${pond.turbidityPct.roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (!pond.piscicultureEnabled) {
                            Button(
                                onClick = { viewModel.enablePondPisciculture(pond.uid) },
                                colors = ButtonDefaults.buttonColors(containerColor = HarvestGold, contentColor = Color(0xFF0E1310)),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("enable_pisciculture_btn"),
                            ) {
                                Text("Ativar Piscicultura neste Açude (R$ 1.400)", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                FilledTonalButton(
                                    onClick = { viewModel.buyPondFishFeed(pond.uid, 80.0) },
                                    modifier = Modifier.height(48.dp).testTag("buy_fish_feed_btn"),
                                ) {
                                    Text("+80kg Ração (${formatBrl(80.0 * FISH_FEED_PRICE_PER_KG_BRL)})")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.maintainPondWater(pond.uid, "AERACAO") },
                                    modifier = Modifier.height(48.dp),
                                ) { Text("Oxigenar (R$ 420)") }
                                OutlinedButton(
                                    onClick = { viewModel.maintainPondWater(pond.uid, "CALAGEM_PH") },
                                    modifier = Modifier.height(48.dp),
                                ) { Text("Corrigir pH (R$ 580)") }
                                OutlinedButton(
                                    onClick = { viewModel.maintainPondWater(pond.uid, "LIMPEZA") },
                                    modifier = Modifier.height(48.dp),
                                ) { Text("Limpar Água (R$ 650)") }
                            }
                        }
                    }
                }
            }

            if (selectedPond != null && selectedPond.piscicultureEnabled) {
                item {
                    val sp = getFishSpeciesById(selectedSpeciesId)
                    SectionCard(
                        title = "Povoar Alevinos — ${sp.name}",
                        subtitle = "${sp.scientificName} • Raio mín ${sp.minPondRadiusM.roundToInt()}m • Lâmina mín ${sp.minWaterDepthM}m • Mercado ${formatBrl(sp.marketPricePerKgBrl)}/kg",
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FISH_SPECIES_CATALOG.forEach { catSp ->
                                FilterChip(
                                    selected = selectedSpeciesId == catSp.id,
                                    onClick = { selectedSpeciesId = catSp.id },
                                    label = { Text(catSp.name, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.height(48.dp),
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(100, 250, 500).forEach { qty ->
                                FilterChip(
                                    selected = fingerlingCount == qty,
                                    onClick = { fingerlingCount = qty },
                                    label = { Text("$qty alevinos") },
                                    modifier = Modifier.height(48.dp),
                                )
                            }
                        }
                        Button(
                            onClick = { viewModel.stockFishFingerlings(selectedPond.uid, selectedSpeciesId, fingerlingCount) },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("stock_fingerlings_btn"),
                        ) {
                            Icon(Icons.Rounded.SetMeal, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Comprar e Soltar $fingerlingCount Alevinos (${formatBrl(fingerlingCount * sp.fingerlingCostBrl)})")
                        }
                    }
                }

                if (selectedPond.fishStocks.isNotEmpty()) {
                    item {
                        Text("Lotes em Cultivo (${selectedPond.fishStocks.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    items(selectedPond.fishStocks, key = { "${selectedPond.uid}_${it.speciesId}" }) { stock ->
                        val sp = getFishSpeciesById(stock.speciesId)
                        SectionCard(
                            title = "${sp.name} (${stock.count} peixes)",
                            subtitle = "Biomassa ${String.format(Locale.US, "%.1f", stock.biomassKg)} kg • Saúde ${stock.healthPct.roundToInt()}%",
                        ) {
                            LinearProgressIndicator(
                                progress = { stock.growthProgress.toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = if (stock.readyToHarvest) StatusGreen else AgroSprout,
                            )
                            Text(
                                "Crescimento: ${(stock.growthProgress * 100.0).roundToInt()}% • Peso médio ${String.format(Locale.US, "%.2f", stock.avgWeightKg)} kg",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(
                                onClick = { viewModel.harvestPond(selectedPond.uid, stock.speciesId) },
                                enabled = stock.biomassKg >= 1.0,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (stock.readyToHarvest) HarvestGold else MaterialTheme.colorScheme.secondary,
                                    contentColor = if (stock.readyToHarvest) Color(0xFF0E1310) else Color.White,
                                ),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("harvest_pond_btn_${stock.speciesId}"),
                            ) {
                                Text(if (stock.readyToHarvest) "Despescar Lote Pronto" else "Despescar Antecipado")
                            }
                        }
                    }
                }
            }
        }

        if (fishInv.isNotEmpty()) {
            item {
                Text("Pescado em Estoque p/ Venda (${fishInv.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(fishInv.entries.toList(), key = { it.key }) { (invKey, kg) ->
                val spId = invKey.removePrefix("peixe_")
                val sp = getFishSpeciesById(spId)
                Surface(
                    color = CabinCard,
                    border = BorderStroke(1.dp, AgroSprout.copy(alpha = 0.45f)),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(sp.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "${String.format(Locale.US, "%.1f", kg)} kg • ${formatBrl(sp.marketPricePerKgBrl)}/kg",
                                style = MaterialTheme.typography.bodySmall,
                                color = HarvestGold,
                            )
                        }
                        Button(
                            onClick = { viewModel.sellHarvestedFish(spId) },
                            colors = ButtonDefaults.buttonColors(containerColor = HarvestGold, contentColor = Color(0xFF0E1310)),
                            modifier = Modifier.height(48.dp).testTag("sell_fish_btn_$spId"),
                        ) {
                            Text("Vender", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
