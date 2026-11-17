package com.example.simulation

import com.example.domain.AquiferAvailabilityTier
import com.example.domain.DiagnosticEventEntry
import com.example.domain.RuntimeDiagnosticSnapshot
import com.example.domain.WorldState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import java.util.Locale

object DiagnosticEngine {
    private val lock = Any()
    private val events = ArrayDeque<DiagnosticEventEntry>(84)
    private val frameSamplesMs = ArrayDeque<Double>(48)
    private var lastFrameStartNs: Long = 0L

    private val _snapshotFlow = MutableStateFlow(RuntimeDiagnosticSnapshot())
    val snapshotFlow: StateFlow<RuntimeDiagnosticSnapshot> = _snapshotFlow.asStateFlow()

    fun beginFrame(): Long = System.nanoTime()

    fun endFrame(startNs: Long, state: WorldState? = null) {
        val elapsedMs = ((System.nanoTime() - startNs).coerceAtLeast(100_000L)) / 1_000_000.0
        recordFrameTimeMs(elapsedMs, state)
    }

    fun recordFrameTimeMs(ms: Double, state: WorldState? = null) {
        val safeMs = if (ms.isFinite()) ms.coerceIn(0.5, 500.0) else 16.6
        val snap = synchronized(lock) {
            if (frameSamplesMs.size >= 40) frameSamplesMs.removeFirst()
            frameSamplesMs.addLast(safeMs)
            val avg = if (frameSamplesMs.isNotEmpty()) frameSamplesMs.average() else 16.6
            val fps = (1000.0 / avg).coerceIn(1.0, 240.0)
            val current = _snapshotFlow.value
            val updated = current.copy(
                fpsEstimate = fps,
                frameTimeMs = avg,
                usedRamMb = readUsedRamMb(),
                maxRamMb = readMaxRamMb(),
                activeCellsCount = state?.cells?.size ?: current.activeCellsCount,
                activePropsCount = state?.props?.size ?: current.activePropsCount,
                activePondsCount = state?.ponds?.size ?: current.activePondsCount,
                activeVehiclesCount = state?.vehicles?.size ?: current.activeVehiclesCount,
                capturedEvents = events.toList(),
            )
            _snapshotFlow.value = updated
            updated
        }
        lastFrameStartNs = System.nanoTime()
        if (snap.fpsEstimate < 15.0 && lastFrameStartNs == 0L) {
            // Keep compiler happy without noisy logs
        }
    }

    fun recordInfo(module: String, functionName: String, message: String) {
        appendEvent("INFO", module, functionName, message)
    }

    fun recordWarn(module: String, functionName: String, message: String) {
        appendEvent("WARN", module, functionName, message)
    }

    fun recordError(module: String, functionName: String, error: Throwable) {
        val msg = "${error::class.java.simpleName}: ${error.message ?: "sem detalhes"}"
        appendEvent("ERROR", module, functionName, msg)
    }

    fun recordError(module: String, functionName: String, message: String) {
        appendEvent("ERROR", module, functionName, message)
    }

    private fun appendEvent(level: String, module: String, functionName: String, message: String) {
        synchronized(lock) {
            if (events.size >= 80) events.removeFirst()
            events.addLast(
                DiagnosticEventEntry(
                    timestampMs = System.currentTimeMillis(),
                    level = level,
                    module = module,
                    functionName = functionName,
                    message = message,
                )
            )
            val cur = _snapshotFlow.value
            _snapshotFlow.value = cur.copy(
                errorCount = cur.errorCount + if (level == "ERROR") 1 else 0,
                warningCount = cur.warningCount + if (level == "WARN") 1 else 0,
                capturedEvents = events.toList(),
            )
        }
    }

    fun readUsedRamMb(): Double {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()).coerceAtLeast(0L)
        return (used / (1024.0 * 1024.0))
    }

    fun readMaxRamMb(): Double {
        val rt = Runtime.getRuntime()
        return (rt.maxMemory() / (1024.0 * 1024.0)).coerceAtLeast(128.0)
    }

    fun runWorldIntegrityAudit(state: WorldState): RuntimeDiagnosticSnapshot {
        val issues = mutableListOf<String>()
        if (!state.moneyBrl.isFinite() || !state.bankBalanceBrl.isFinite() || !state.bankLoanBrl.isFinite()) {
            issues.add("Valores financeiros inválidos (NaN/Infinito)")
        }
        val expectedCells = getGridSide(state) * getGridSide(state)
        if (state.cells.size != expectedCells) {
            issues.add("Grade de terreno inconsistente (${state.cells.size}/$expectedCells células)")
        }
        var nanCells = 0
        var negativeWaterCells = 0
        for (c in state.cells) {
            if (!c.height.isFinite() || !c.moisture.isFinite()) nanCells++
            if ((c.waterDepthM ?: 0.0) < -0.01) negativeWaterCells++
        }
        if (nanCells > 0) issues.add("$nanCells células com altura/umidade inválida")
        if (negativeWaterCells > 0) issues.add("$negativeWaterCells células com lâmina d'água negativa")

        for (v in state.vehicles) {
            if (!v.x.isFinite() || !v.z.isFinite() || !v.fuelL.isFinite()) {
                issues.add("Veículo ${v.uid} com telemetria inválida")
            }
            if ((v.carriedWaterL ?: 0.0) < -0.01 || (v.carriedEarthM3 ?: 0.0) < -0.01) {
                issues.add("Carga negativa detectada em ${v.defId}")
            }
        }

        val aquifers = state.aquiferGrid.orEmpty().associateBy { it.id }
        for (aq in aquifers.values) {
            if (aq.remainingCapacityL < -1.0 || aq.remainingCapacityL > aq.totalCapacityL + 1.0) {
                issues.add("Aquífero ${aq.id} fora dos limites (${aq.remainingCapacityL}/${aq.totalCapacityL} L)")
            }
        }

        for (pond in state.ponds.orEmpty()) {
            if (pond.waterVolumeL < -1.0 || pond.waterVolumeL > pond.maxVolumeL + 10.0) {
                issues.add("Açude ${pond.name} com volume inconsistente")
            }
            if (pond.linkedAquiferId != null && !aquifers.containsKey(pond.linkedAquiferId)) {
                issues.add("Açude ${pond.name} aponta para aquífero ausente")
            }
        }

        for ((k, v) in state.inventoryKg) {
            if (!v.isFinite() || v < -0.01) issues.add("Estoque negativo/inválido em '$k'")
        }

        val statusSummary = if (issues.isEmpty()) {
            "OK: Integridade verificada (${state.cells.size} células, ${state.ponds.orEmpty().size} açudes, ${aquifers.size} aquíferos)"
        } else {
            "ALERTA (${issues.size}): ${issues.take(3).joinToString(" • ")}"
        }

        val updated = synchronized(lock) {
            if (issues.isNotEmpty()) {
                for (iss in issues) {
                    if (events.size >= 80) events.removeFirst()
                    events.addLast(
                        DiagnosticEventEntry(
                            timestampMs = System.currentTimeMillis(),
                            level = "WARN",
                            module = "WorldAudit",
                            functionName = "runWorldIntegrityAudit",
                            message = iss,
                        )
                    )
                }
            }
            val cur = _snapshotFlow.value
            val avg = if (frameSamplesMs.isNotEmpty()) frameSamplesMs.average() else cur.frameTimeMs
            cur.copy(
                fpsEstimate = if (avg > 0.1) (1000.0 / avg).coerceIn(1.0, 240.0) else cur.fpsEstimate,
                frameTimeMs = avg,
                usedRamMb = readUsedRamMb(),
                maxRamMb = readMaxRamMb(),
                activeCellsCount = state.cells.size,
                activePropsCount = state.props.size,
                activePondsCount = state.ponds.orEmpty().size,
                activeVehiclesCount = state.vehicles.size,
                integrityStatus = statusSummary,
                lastPersistenceCheck = "Schema v${state.schemaVersion} • Dia ${state.day}",
                warningCount = cur.warningCount + issues.size,
                capturedEvents = events.toList(),
            ).also { _snapshotFlow.value = it }
        }
        return updated
    }

    fun formatCompactReport(): String {
        val s = _snapshotFlow.value
        return String.format(
            Locale.US,
            "FPS %.1f (%.1f ms) | RAM %.0f/%.0f MB | Erros %d | Avisos %d | %s",
            s.fpsEstimate,
            s.frameTimeMs,
            s.usedRamMb,
            s.maxRamMb,
            s.errorCount,
            s.warningCount,
            s.integrityStatus,
        )
    }

    fun clearEvents() {
        synchronized(lock) {
            events.clear()
            _snapshotFlow.value = _snapshotFlow.value.copy(
                errorCount = 0,
                warningCount = 0,
                capturedEvents = emptyList(),
            )
        }
    }
}
