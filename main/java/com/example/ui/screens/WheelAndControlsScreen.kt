package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SettingsInputComponent
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.GameSettings
import com.example.domain.UsbSteeringWheelConfig
import com.example.domain.WheelAxisTelemetry
import com.example.ui.components.ScreenHeader
import com.example.ui.components.SectionCard
import com.example.ui.theme.AgroSprout
import com.example.ui.theme.HarvestGold
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusInfo
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WheelAndControlsScreen(
    settings: GameSettings,
    telemetry: WheelAxisTelemetry,
    onUpdateWheelConfig: ((UsbSteeringWheelConfig) -> UsbSteeringWheelConfig) -> Unit,
    onApplyWheelPreset: (String) -> Unit,
    onUpdateSettings: ((GameSettings) -> GameSettings) -> Unit,
    onBack: () -> Unit,
) {
    val cfg = settings.usbWheelConfig
    val kb = settings.keyBindings
    var saveMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding(),
    ) {
        ScreenHeader(
            title = "CONTROLES E VOLANTE USB",
            subtitle = "Calibração e Configuração de Volante, Pedais e Teclado (Windows / Android)",
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Device Identification & Connection Status Card
            SectionCard(
                title = "DISPOSITIVO DE DIREÇÃO CONECTADO",
                subtitle = "Reconhecimento em tempo real de volantes DirectInput, XInput e Joysticks USB",
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, if (telemetry.isConnected) StatusGreen else MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("wheel_device_status_card"),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(if (telemetry.isConnected) StatusGreen else Color.Gray, CircleShape),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = telemetry.detectedDeviceName.ifBlank { "Volante USB / Gamepad Padrão" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (telemetry.isConnected) StatusGreen else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (telemetry.isConnected) "Dispositivo ativo e calibrado • Perfil: ${cfg.profileName}" else "Nenhum volante físico detectado. O teclado e gamepad continuam 100% operacionais.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.SettingsInputComponent,
                            contentDescription = "Volante USB",
                            tint = HarvestGold,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            // 2. Real-Time Axis Telemetry & Live Visualizer
            SectionCard(
                title = "VISUALIZADOR DE EIXOS EM TEMPO REAL",
                subtitle = "Gire o volante e pressione os pedais para validar a calibração instantânea",
            ) {
                // Volante / Steering Axis Indicator (-100% a +100%)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Eixo de Direção (Volante):", style = MaterialTheme.typography.labelMedium)
                        val angleVal = (telemetry.calibratedSteer * (cfg.maxSteerAngleDeg * 0.5)).roundToInt()
                        Text(
                            text = "${(telemetry.calibratedSteer * 100).roundToInt()}% ($angleVal°)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = HarvestGold,
                        )
                    }
                    val steerProg = ((telemetry.calibratedSteer + 1.0) * 0.5).toFloat().coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { steerProg },
                        modifier = Modifier.fillMaxWidth().height(10.dp),
                        color = HarvestGold,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                // Pedals Grid (Acelerador, Freio, Embreagem)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Acelerador
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Acelerador", style = MaterialTheme.typography.labelSmall)
                            Text("${(telemetry.calibratedThrottle * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = StatusGreen, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { telemetry.calibratedThrottle.toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = StatusGreen,
                        )
                    }

                    // Freio
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Freio", style = MaterialTheme.typography.labelSmall)
                            Text("${(telemetry.calibratedBrake * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = StatusDanger, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { telemetry.calibratedBrake.toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = StatusDanger,
                        )
                    }

                    // Embreagem
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Embreagem", style = MaterialTheme.typography.labelSmall)
                            Text("${(telemetry.calibratedClutch * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = StatusInfo, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { telemetry.calibratedClutch.toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = StatusInfo,
                        )
                    }
                }

                // Câmbio em H / Shifter Display
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Posição Atual do Câmbio:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val gears = listOf(-1 to "R", 0 to "N", 1 to "1", 2 to "2", 3 to "3", 4 to "4", 5 to "5", 6 to "6")
                        gears.forEach { (gCode, gLabel) ->
                            val active = telemetry.currentGear == gCode
                            Surface(
                                color = if (active) AgroSprout else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.size(34.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = gLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (active) Color(0xFF0E1310) else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Predefinições de Volantes (Presets)
            SectionCard(
                title = "PREDEFINIÇÕES RÁPIDAS DE VOLANTE",
                subtitle = "Selecione o modelo do seu volante para carregar os parâmetros ideais",
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val presets = listOf(
                        "Logitech G29 / G920 / G923 (900°)" to "Logitech G29",
                        "Thrustmaster T150 / T300 / TX (1080°)" to "Thrustmaster T300",
                        "Fanatec ClubSport / CSL (1080°)" to "Fanatec CSL",
                        "Volante Genérico / Arcade (270°)" to "Genérico 270",
                    )
                    presets.forEach { (label, presetId) ->
                        OutlinedButton(
                            onClick = {
                                onApplyWheelPreset(presetId)
                                saveMessage = "Predefinição '$label' aplicada com sucesso!"
                            },
                            modifier = Modifier.height(44.dp).testTag("wheel_preset_$presetId"),
                        ) {
                            Icon(Icons.Rounded.DirectionsCar, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // 4. Calibração do Volante & Pedais
            SectionCard(
                title = "CALIBRAÇÃO E AJUSTES DE PRECISÃO",
                subtitle = "Configure rotação, zonas mortas e sensibilidade para o seu estilo de condução",
            ) {
                // Rotação Máxima
                Text("Ângulo Máximo de Rotação:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(270.0 to "270° (Arcade)", 900.0 to "900° (Realista)", 1080.0 to "1080° (Simulador)").forEach { (deg, label) ->
                        FilterChip(
                            selected = cfg.maxSteerAngleDeg == deg,
                            onClick = { onUpdateWheelConfig { it.copy(maxSteerAngleDeg = deg) } },
                            label = { Text(label) },
                            modifier = Modifier.testTag("steer_angle_$deg"),
                        )
                    }
                }

                // Sliders de Calibração
                WheelSlider("Sensibilidade da Direção (${"%.2f".format(cfg.steerSensitivity)}x)", cfg.steerSensitivity.toFloat(), 0.5f..2.5f) {
                    onUpdateWheelConfig { c -> c.copy(steerSensitivity = it.toDouble()) }
                }

                WheelSlider("Zona Morta do Volante (${(cfg.deadzoneSteer * 100).roundToInt()}%)", cfg.deadzoneSteer.toFloat(), 0.0f..0.20f) {
                    onUpdateWheelConfig { c -> c.copy(deadzoneSteer = it.toDouble()) }
                }

                WheelSlider("Zona Morta do Acelerador (${(cfg.deadzoneThrottle * 100).roundToInt()}%)", cfg.deadzoneThrottle.toFloat(), 0.0f..0.20f) {
                    onUpdateWheelConfig { c -> c.copy(deadzoneThrottle = it.toDouble()) }
                }

                WheelSlider("Zona Morta do Freio (${(cfg.deadzoneBrake * 100).roundToInt()}%)", cfg.deadzoneBrake.toFloat(), 0.0f..0.20f) {
                    onUpdateWheelConfig { c -> c.copy(deadzoneBrake = it.toDouble()) }
                }

                WheelSlider("Force Feedback / Retorno ao Centro (${(cfg.forceFeedbackIntensity * 100).roundToInt()}%)", cfg.forceFeedbackIntensity.toFloat(), 0.0f..1.0f) {
                    onUpdateWheelConfig { c -> c.copy(forceFeedbackIntensity = it.toDouble()) }
                }

                // Inversão de Eixos
                Text("Inversão de Eixos (Correção de Pedais Invertidos):", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Inverter Acelerador", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = cfg.invertThrottle, onCheckedChange = { inv -> onUpdateWheelConfig { it.copy(invertThrottle = inv) } })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Inverter Freio", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = cfg.invertBrake, onCheckedChange = { inv -> onUpdateWheelConfig { it.copy(invertBrake = inv) } })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Inverter Volante", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = cfg.invertSteer, onCheckedChange = { inv -> onUpdateWheelConfig { it.copy(invertSteer = inv) } })
                }

                // Câmbio
                Text("Configuração de Transmissão:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Habilitar Câmbio em H (Shifter Manual)", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = cfg.hShifterEnabled, onCheckedChange = { en -> onUpdateWheelConfig { it.copy(hShifterEnabled = en) } })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Habilitar Borboletas (Paddle Shift Sequencial)", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = cfg.paddleShiftEnabled, onCheckedChange = { en -> onUpdateWheelConfig { it.copy(paddleShiftEnabled = en) } })
                }
            }

            // 5. Teclado & Mapeamento de Atalhos Agrícolas
            SectionCard(
                title = "ATALHOS DE TECLADO E MOUSE (PC / WINDOWS 11)",
                subtitle = "Controles completos configurados para o simulador no Windows",
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyBadge("W", "Acelerar")
                    KeyBadge("S", "Frear / Marcha à Ré")
                    KeyBadge("A", "Virar à Esquerda")
                    KeyBadge("D", "Virar à Direita")
                    KeyBadge("Espaço", "Freio de Mão")
                    KeyBadge("Shift", "Correr / Acelerar")
                    KeyBadge("E", "Entrar / Sair do Veículo")
                    KeyBadge("Q", "Engatar / Desengatar Implemento")
                    KeyBadge("V", "Levantar / Abaixar Levante Hidráulico")
                    KeyBadge("B", "Ligar / Desligar TDP (PTO)")
                    KeyBadge("O", "Abrir / Fechar Tubo Graneleiro")
                    KeyBadge("X", "Bloqueio de Diferencial")
                    KeyBadge("L", "Faróis Dianteiros / Trabalho")
                    KeyBadge("J", "Sinalizador Giroflex / Beacons")
                    KeyBadge("C", "Alternar Câmeras (1ª / 3ª Pessoa)")
                    KeyBadge("M", "Abrir / Fechar Mapa Completo")
                    KeyBadge("H", "Buzina")
                    KeyBadge("1, 2, 3", "Marchas e Piloto Automático")
                }
            }

            // Save confirmation banner
            saveMessage?.let {
                Surface(
                    color = AgroSprout.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, AgroSprout),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = AgroSprout, modifier = Modifier.size(20.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = AgroSprout)
                    }
                }
            }

            // Actions Buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        saveMessage = "Configurações de volante e controles salvas no perfil ativo!"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AgroSprout, contentColor = Color(0xFF0E1310)),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("wheel_save_btn"),
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("SALVAR CONFIGURAÇÃO", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.height(48.dp).testTag("wheel_back_btn"),
                ) {
                    Text("VOLTAR")
                }
            }
        }
    }
}

@Composable
private fun KeyBadge(key: String, description: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.padding(2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                color = HarvestGold.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    text = key,
                    style = MaterialTheme.typography.labelSmall,
                    color = HarvestGold,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WheelSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}
