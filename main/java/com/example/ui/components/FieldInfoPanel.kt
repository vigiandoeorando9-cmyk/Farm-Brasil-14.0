package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Agriculture
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.FieldInfoPanelConfig
import com.example.domain.FieldPanelSize
import com.example.domain.HitchMode
import com.example.simulation.CropCalendarStatus
import com.example.simulation.FieldConditionState
import com.example.simulation.FieldOwnerKind
import com.example.simulation.FieldPanelTelemetry
import com.example.simulation.HitchCandidateEvaluation
import com.example.simulation.OnFootHitchEvaluation
import com.example.simulation.UnhitchCandidateEvaluation
import com.example.ui.theme.AgroSprout
import com.example.ui.theme.HarvestGold
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusWarning

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FieldInfoHudPanel(
    telemetry: FieldPanelTelemetry,
    config: FieldInfoPanelConfig,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxWidthDp = when (config.size) {
        FieldPanelSize.PEQUENO -> 268.dp
        FieldPanelSize.MEDIO -> 312.dp
        FieldPanelSize.GRANDE -> 356.dp
    }
    val ownerColor = when (telemetry.ownerKind) {
        FieldOwnerKind.OWN_PROPERTY -> StatusGreen
        FieldOwnerKind.OTHER_PLAYER -> Color(0xFF4FC3F7)
        FieldOwnerKind.NPC_PROPERTY -> StatusWarning
        FieldOwnerKind.AVAILABLE_FOR_PURCHASE -> HarvestGold
    }
    val calendarColor = when (telemetry.calendarStatus) {
        CropCalendarStatus.EPOCA_ADEQUADA, CropCalendarStatus.PERIODO_PLANTIO, CropCalendarStatus.PERIODO_COLHEITA -> StatusGreen
        CropCalendarStatus.PERIODO_CRESCIMENTO -> AgroSprout
        CropCalendarStatus.FORA_DA_EPOCA -> StatusDanger
    }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, ownerColor.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 6.dp,
        modifier = modifier
            .widthIn(min = 232.dp, max = maxWidthDp)
            .testTag("field_info_hud_panel"),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 2.1 Field ID + Compact/Expanded Toggle (>= 48dp touch height)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Grass,
                            contentDescription = "Lavoura",
                            tint = AgroSprout,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "${telemetry.plot.name} (#${telemetry.plot.id})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("field_panel_title"),
                        )
                    }
                    Text(
                        text = "Área: ${telemetry.areaFormatted} • ${telemetry.plantationStatusLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Surface(
                    onClick = onToggleExpanded,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .size(width = 48.dp, height = 48.dp)
                        .testTag("field_panel_toggle_btn"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (isExpanded) "Recolher painel da lavoura" else "Expandir painel da lavoura",
                            tint = HarvestGold,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            // 2.2 Ownership & 2.6 Season / Calendar badges
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    color = ownerColor.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, ownerColor.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = buildString {
                            append(telemetry.ownerKind.label)
                            if (!telemetry.ownerSubtitle.isNullOrBlank()) append(" • ${telemetry.ownerSubtitle}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ownerColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .testTag("field_panel_owner_badge"),
                    )
                }

                Surface(
                    color = calendarColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, calendarColor.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = calendarColor, modifier = Modifier.size(12.dp))
                        Text(
                            text = "${telemetry.season.label} (${telemetry.monthName.take(3)}) • ${telemetry.calendarStatus.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = calendarColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("field_panel_calendar_badge"),
                        )
                    }
                }
            }

            // 2.5 Crop Growth Visual Progress Bar
            telemetry.growth?.let { growth ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val cropUpper = growth.cropName.uppercase()
                    Text(
                        text = "CRESCIMENTO DA $cropUpper ${growth.growthPct}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = HarvestGold,
                        modifier = Modifier.testTag("field_panel_growth_label"),
                    )
                    LinearProgressIndicator(
                        progress = { (growth.growthPct / 100f).coerceIn(0f, 1f) },
                        color = if (growth.growthPct >= 99) StatusGreen else AgroSprout,
                        trackColor = Color.White.copy(alpha = 0.14f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    Text(
                        text = "Estágio ${growth.stage.stageNumber}/9: ${growth.stage.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("field_panel_stage_label"),
                    )
                }
            }

            // Expanded agronomic details
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    // 2.4 Fertilization Progress & NPK Breakdown
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Fertilização Total: ${telemetry.fertilization.totalPct}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (telemetry.fertilization.needsAdditionalPass) StatusWarning else StatusGreen,
                            )
                            Text(
                                text = "1ª: ${if (telemetry.fertilization.firstPassApplied) "OK" else "Pend."} • 2ª: ${if (telemetry.fertilization.secondPassApplied) "OK" else "Pend."}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (telemetry.fertilization.totalPct / 100f).coerceIn(0f, 1f) },
                            color = if (telemetry.fertilization.needsAdditionalPass) HarvestGold else StatusGreen,
                            trackColor = Color.White.copy(alpha = 0.14f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                        )
                        Text(
                            text = "${telemetry.fertilization.status.label} • N ${telemetry.fertilization.nitrogenPct}% | P ${telemetry.fertilization.phosphorusPct}% | K ${telemetry.fertilization.potassiumPct}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("field_panel_fert_detail"),
                        )
                    }

                    // 2.3 CONDIÇÕES DA LAVOURA
                    Text(
                        text = "CONDIÇÕES DA LAVOURA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = HarvestGold,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        telemetry.soilConditions.forEach { cond ->
                            val badgeCol = when (cond.status) {
                                FieldConditionState.APLICADO, FieldConditionState.REALIZADA, FieldConditionState.ADEQUADA -> StatusGreen
                                FieldConditionState.EM_ANDAMENTO -> Color(0xFF4FC3F7)
                                FieldConditionState.NAO_NECESSARIO, FieldConditionState.NAO_NECESSARIA -> Color(0xFF9EABA2)
                                FieldConditionState.APLICACAO_PARCIAL, FieldConditionState.NIVEL_INSUFICIENTE -> StatusWarning
                                FieldConditionState.NECESSARIO, FieldConditionState.NECESSARIA, FieldConditionState.NAO_APLICADO -> StatusDanger
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${cond.title} (${cond.detail})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = cond.status.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeCol,
                                )
                            }
                        }
                    }

                    Text(
                        text = telemetry.calendarDetail,
                        style = MaterialTheme.typography.labelSmall,
                        color = calendarColor,
                    )

                    // Part 3 Active Implement Operational Context
                    telemetry.operationalAdvisory?.let { tip ->
                        Surface(
                            color = HarvestGold.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, HarvestGold.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                                Text(
                                    text = "${tip.implementCategoryLabel}: ${tip.summary}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = HarvestGold,
                                )
                                Text(
                                    text = tip.actionHint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (telemetry.activePivotIrrigating) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Rounded.WaterDrop, contentDescription = null, tint = Color(0xFF4FC3F7), modifier = Modifier.size(14.dp))
                            Text(
                                text = "Pivô Central irrigando este talhão agora",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4FC3F7),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HitchHudActionControl(
    inVehicleHitch: HitchCandidateEvaluation?,
    inVehicleUnhitch: UnhitchCandidateEvaluation?,
    onFootEval: OnFootHitchEvaluation?,
    isInVehicle: Boolean,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label: String?
    val subHint: String?
    val isReady: Boolean
    val isUnhitch: Boolean

    when {
        isInVehicle && inVehicleUnhitch != null -> {
            label = "DESENGATAR"
            subHint = if (inVehicleUnhitch.canUnhitchNow) inVehicleUnhitch.implementDef.model else inVehicleUnhitch.statusMessage
            isReady = inVehicleUnhitch.canUnhitchNow
            isUnhitch = true
        }
        isInVehicle && inVehicleHitch != null -> {
            label = "ENGATAR IMPLEMENTO"
            subHint = if (inVehicleHitch.canHitchNow) {
                "${inVehicleHitch.implementDef.model} (${"%.1f".format(inVehicleHitch.distanceM)}m)"
            } else {
                inVehicleHitch.statusMessage
            }
            isReady = inVehicleHitch.canHitchNow
            isUnhitch = false
        }
        !isInVehicle && onFootEval?.unhitchCandidate != null -> {
            val u = onFootEval.unhitchCandidate
            label = "DESENGATAR"
            subHint = if (u.canUnhitchNow) "Manual a pé • ${u.implementDef.model}" else u.statusMessage
            isReady = u.canUnhitchNow
            isUnhitch = true
        }
        !isInVehicle && onFootEval?.hitchCandidate != null -> {
            val h = onFootEval.hitchCandidate
            label = "ENGATAR"
            subHint = if (h.canHitchNow) "Manual a pé • ${h.implementDef.model}" else h.statusMessage
            isReady = h.canHitchNow
            isUnhitch = false
        }
        else -> {
            label = null
            subHint = null
            isReady = false
            isUnhitch = false
        }
    }

    if (label == null) return

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        if (!subHint.isNullOrBlank()) {
            Surface(
                color = Color(0xFF101712).copy(alpha = 0.86f),
                border = BorderStroke(1.dp, (if (isReady) StatusGreen else StatusWarning).copy(alpha = 0.55f)),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = subHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isReady) AgroSprout else HarvestGold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Button(
            onClick = onActionClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = when {
                    isUnhitch && isReady -> Color(0xFFD97706)
                    isReady -> StatusGreen
                    else -> Color(0xFF334139)
                },
                contentColor = Color.White,
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            modifier = Modifier
                .height(48.dp)
                .testTag("hud_hitch_action_button"),
        ) {
            Icon(
                imageVector = if (isUnhitch) Icons.Rounded.LinkOff else Icons.Rounded.Link,
                contentDescription = label,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun HitchModeSelectionDialog(
    implementModel: String,
    onChooseMode: (HitchMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modo de Engate Manual") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Escolha como deseja conectar $implementModel:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { onChooseMode(HitchMode.IN_VEHICLE_BUTTON) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("hitch_choice_in_cab_btn"),
                ) {
                    Icon(Icons.Rounded.Agriculture, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Engatar da Cabine")
                }
                FilledTonalButton(
                    onClick = { onChooseMode(HitchMode.ON_FOOT_MANUAL) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("hitch_choice_on_foot_btn"),
                ) {
                    Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Descer do Trator e Engatar a Pé")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("hitch_choice_cancel_btn"),
            ) {
                Text("Cancelar")
            }
        },
    )
}
