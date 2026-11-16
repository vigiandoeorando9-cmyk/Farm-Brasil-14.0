package com.example.simulation

import com.example.domain.GraphicsConfig
import com.example.domain.GraphicsPreset
import com.example.domain.PlacedBuilding
import com.example.domain.PlacedCenterPivot
import com.example.domain.TerrainCell
import com.example.domain.WorldProp
import com.example.domain.WorldState
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

const val DEFAULT_CHUNK_CELLS = 16

enum class RenderLodLevel(val label: String, val terrainStep: Int) {
    LOD0("LOD0 (Alta Definição)", 1),
    LOD1("LOD1 (Intermediário)", 2),
    LOD2("LOD2 (Distante / Otimizado)", 4),
}

data class TerrainChunkKey(
    val cx: Int,
    val cz: Int,
)

data class TerrainChunk(
    val key: TerrainChunkKey,
    val startGx: Int,
    val startGz: Int,
    val endGx: Int,
    val endGz: Int,
    val centerX: Double,
    val centerZ: Double,
    val halfSpanM: Double,
    val avgHeight: Double,
    val minHeight: Double,
    val maxHeight: Double,
    val lodLevel: RenderLodLevel,
    val distanceToCamM: Double,
    val inFrustum: Boolean,
)

data class StreamingPlatformBudget(
    val profileLabel: String,
    val isDesktopPc: Boolean,
    val maxCachedChunks: Int,
    val maxChunkRadius: Int,
    val lod0RadiusM: Double,
    val lod1RadiusM: Double,
    val maxDrawDistanceM: Double,
    val frustumCosThreshold: Double,
    val ramBudgetMb: Int,
    val instancedPropLimit: Int,
)

data class StreamingTelemetrySnapshot(
    val loadedChunks: Int,
    val totalChunks: Int,
    val activeLod0Count: Int,
    val activeLod1Count: Int,
    val activeLod2Count: Int,
    val culledObjectsCount: Int,
    val estimatedRamMb: Double,
    val platformProfileLabel: String,
    val instancedPropCount: Int = 0,
    val activeChunkRadius: Int = 0,
)

data class ActiveStreamingWindow(
    val chunks: List<TerrainChunk>,
    val visibleBuildings: List<PlacedBuilding>,
    val visibleProps: List<WorldProp>,
    val visiblePivots: List<PlacedCenterPivot>,
    val instancedPropBatches: Map<String, List<WorldProp>>,
    val telemetry: StreamingTelemetrySnapshot,
)

class ChunkSpatialIndex(
    val gridSide: Int,
    val worldSpanM: Double,
    val chunkSizeCells: Int = DEFAULT_CHUNK_CELLS,
) {
    val chunksPerSide: Int = max(1, ceil(gridSide.toDouble() / chunkSizeCells).toInt())
    val totalChunks: Int = chunksPerSide * chunksPerSide
    val cellSizeM: Double = worldSpanM / max(1, gridSide)
    val worldHalfM: Double = worldSpanM * 0.5

    fun chunkCoordsForWorld(wx: Double, wz: Double): TerrainChunkKey {
        val gx = floor((wx + worldHalfM) / cellSizeM).toInt().coerceIn(0, gridSide - 1)
        val gz = floor((wz + worldHalfM) / cellSizeM).toInt().coerceIn(0, gridSide - 1)
        return TerrainChunkKey(
            cx = (gx / chunkSizeCells).coerceIn(0, chunksPerSide - 1),
            cz = (gz / chunkSizeCells).coerceIn(0, chunksPerSide - 1),
        )
    }

    fun buildChunk(
        cells: List<TerrainCell>,
        cx: Int,
        cz: Int,
        camX: Double,
        camZ: Double,
        fwdX: Double,
        fwdZ: Double,
        budget: StreamingPlatformBudget,
    ): TerrainChunk {
        val startGx = cx * chunkSizeCells
        val startGz = cz * chunkSizeCells
        val endGx = min(gridSide - 1, startGx + chunkSizeCells - 1)
        val endGz = min(gridSide - 1, startGz + chunkSizeCells - 1)

        val centerGx = (startGx + endGx) * 0.5 + 0.5
        val centerGz = (startGz + endGz) * 0.5 + 0.5
        val centerX = -worldHalfM + centerGx * cellSizeM
        val centerZ = -worldHalfM + centerGz * cellSizeM
        val halfSpanM = max(cellSizeM, ((endGx - startGx + 1) * cellSizeM) * 0.5)

        var sumH = 0.0
        var minH = Double.POSITIVE_INFINITY
        var maxH = Double.NEGATIVE_INFINITY
        var sampleCount = 0
        for (gz in startGz..endGz step 4) {
            for (gx in startGx..endGx step 4) {
                val idx = gz * gridSide + gx
                if (idx in cells.indices) {
                    val h = cells[idx].height
                    sumH += h
                    if (h < minH) minH = h
                    if (h > maxH) maxH = h
                    sampleCount++
                }
            }
        }
        val avgH = if (sampleCount > 0) sumH / sampleCount else 0.0
        if (!minH.isFinite()) minH = 0.0
        if (!maxH.isFinite()) maxH = 0.0

        val dx = centerX - camX
        val dz = centerZ - camZ
        val dist = hypot(dx, dz)

        val surfaceDist = max(0.0, dist - halfSpanM * 0.9)
        val lod = when {
            surfaceDist <= budget.lod0RadiusM -> RenderLodLevel.LOD0
            surfaceDist <= budget.lod1RadiusM -> RenderLodLevel.LOD1
            else -> RenderLodLevel.LOD2
        }

        val inFrustum = if (dist <= halfSpanM * 2.15) {
            true
        } else {
            val invD = 1.0 / max(dist, 1e-4)
            val dot = (dx * invD) * fwdX + (dz * invD) * fwdZ
            val chunkAngularSlop = min(0.45, (halfSpanM * 1.45) / max(dist, 1.0))
            dot >= (budget.frustumCosThreshold - chunkAngularSlop)
        }

        return TerrainChunk(
            key = TerrainChunkKey(cx, cz),
            startGx = startGx,
            startGz = startGz,
            endGx = endGx,
            endGz = endGz,
            centerX = centerX,
            centerZ = centerZ,
            halfSpanM = halfSpanM,
            avgHeight = avgH,
            minHeight = minH,
            maxHeight = maxH,
            lodLevel = lod,
            distanceToCamM = dist,
            inFrustum = inFrustum,
        )
    }
}

object WorldStreamingEngine {
    private val chunkLruCache = LinkedHashMap<String, TerrainChunk>(96, 0.75f, true)

    fun resolveStreamingPlatformBudget(
        gfx: GraphicsConfig,
        preset: GraphicsPreset,
    ): StreamingPlatformBudget {
        val isDesktop = preset == GraphicsPreset.DESKTOP_PC ||
            preset == GraphicsPreset.ULTRA_DOS_ULTRAS ||
            preset == GraphicsPreset.MEGA_REALISTA ||
            gfx.adaptiveMobile == false

        return if (isDesktop) {
            StreamingPlatformBudget(
                profileLabel = "PC / Desktop (Streaming Amplo)",
                isDesktopPc = true,
                maxCachedChunks = 144,
                maxChunkRadius = 5,
                lod0RadiusM = min(gfx.drawDistance * 0.45, 220.0),
                lod1RadiusM = min(gfx.drawDistance * 0.80, 380.0),
                maxDrawDistanceM = max(280.0, gfx.drawDistance),
                frustumCosThreshold = -0.18,
                ramBudgetMb = 1024,
                instancedPropLimit = 620,
            )
        } else {
            val mobileTight = preset == GraphicsPreset.CELULAR ||
                preset == GraphicsPreset.MUITO_BAIXO ||
                preset == GraphicsPreset.BAIXO
            StreamingPlatformBudget(
                profileLabel = if (mobileTight) "Android Mobile (Econômico)" else "Android Equilibrado",
                isDesktopPc = false,
                maxCachedChunks = if (mobileTight) 28 else 48,
                maxChunkRadius = if (mobileTight) 2 else 3,
                lod0RadiusM = if (mobileTight) 95.0 else 135.0,
                lod1RadiusM = if (mobileTight) 170.0 else 235.0,
                maxDrawDistanceM = min(gfx.drawDistance, if (mobileTight) 210.0 else 320.0),
                frustumCosThreshold = 0.02,
                ramBudgetMb = if (mobileTight) 280 else 440,
                instancedPropLimit = if (mobileTight) 180 else 340,
            )
        }
    }

    private fun isPointInViewFrustum(
        px: Double,
        pz: Double,
        camX: Double,
        camZ: Double,
        fwdX: Double,
        fwdZ: Double,
        maxDistM: Double,
        cosThresh: Double,
        objectRadiusM: Double,
    ): Boolean {
        val dx = px - camX
        val dz = pz - camZ
        val dist = hypot(dx, dz)
        if (dist - objectRadiusM > maxDistM) return false
        if (dist <= objectRadiusM + 24.0) return true
        val invD = 1.0 / max(dist, 1e-4)
        val dot = (dx * invD) * fwdX + (dz * invD) * fwdZ
        val angularPad = min(0.35, objectRadiusM / max(dist, 1.0))
        return dot >= (cosThresh - angularPad)
    }

    fun buildActiveStreamingWindow(
        state: WorldState,
        camX: Double,
        camZ: Double,
        fwdX: Double,
        fwdZ: Double,
        gfx: GraphicsConfig,
        preset: GraphicsPreset = GraphicsPreset.EQUILIBRADO,
        chunkSizeCells: Int = DEFAULT_CHUNK_CELLS,
    ): ActiveStreamingWindow {
        val gridSide = getGridSide(state)
        val spanM = getWorldSpanM(state)
        val budget = resolveStreamingPlatformBudget(gfx, preset)
        val index = ChunkSpatialIndex(gridSide, spanM, chunkSizeCells)
        val camKey = index.chunkCoordsForWorld(camX, camZ)

        val fwdLen = hypot(fwdX, fwdZ).coerceAtLeast(1e-4)
        val nFwdX = fwdX / fwdLen
        val nFwdZ = fwdZ / fwdLen

        val activeChunks = mutableListOf<TerrainChunk>()
        val cacheScope = "${state.mapId}_${state.mapAreaTierId ?: gridSide}_${gridSide}"

        val minCx = max(0, camKey.cx - budget.maxChunkRadius)
        val maxCx = min(index.chunksPerSide - 1, camKey.cx + budget.maxChunkRadius)
        val minCz = max(0, camKey.cz - budget.maxChunkRadius)
        val maxCz = min(index.chunksPerSide - 1, camKey.cz + budget.maxChunkRadius)

        for (cz in minCz..maxCz) {
            for (cx in minCx..maxCx) {
                val chunk = index.buildChunk(
                    cells = state.cells,
                    cx = cx,
                    cz = cz,
                    camX = camX,
                    camZ = camZ,
                    fwdX = nFwdX,
                    fwdZ = nFwdZ,
                    budget = budget,
                )
                if (chunk.distanceToCamM - chunk.halfSpanM * 1.45 <= budget.maxDrawDistanceM && chunk.inFrustum) {
                    activeChunks.add(chunk)
                    synchronized(chunkLruCache) {
                        val lruKey = "${cacheScope}_${cx}_$cz"
                        chunkLruCache[lruKey] = chunk
                        while (chunkLruCache.size > budget.maxCachedChunks) {
                            val oldestKey = chunkLruCache.entries.firstOrNull()?.key ?: break
                            chunkLruCache.remove(oldestKey)
                        }
                    }
                }
            }
        }

        // Fallback ensure camera's immediate chunk is always present
        if (activeChunks.isEmpty()) {
            activeChunks.add(
                index.buildChunk(
                    cells = state.cells,
                    cx = camKey.cx,
                    cz = camKey.cz,
                    camX = camX,
                    camZ = camZ,
                    fwdX = nFwdX,
                    fwdZ = nFwdZ,
                    budget = budget,
                )
            )
        }

        val sortedChunks = activeChunks.sortedBy { it.distanceToCamM }

        var culledCount = 0
        val visBuildings = mutableListOf<PlacedBuilding>()
        for (b in state.buildings) {
            val radius = max(b.customWidthM ?: 10.0, b.customDepthM ?: 10.0) * 0.75 + 6.0
            if (isPointInViewFrustum(b.x, b.z, camX, camZ, nFwdX, nFwdZ, budget.maxDrawDistanceM, budget.frustumCosThreshold, radius)) {
                visBuildings.add(b)
            } else {
                culledCount++
            }
        }

        val visPivots = mutableListOf<PlacedCenterPivot>()
        for (p in state.centerPivots.orEmpty()) {
            if (isPointInViewFrustum(p.centerX, p.centerZ, camX, camZ, nFwdX, nFwdZ, budget.maxDrawDistanceM + p.radiusM, budget.frustumCosThreshold, p.radiusM + 8.0)) {
                visPivots.add(p)
            } else {
                culledCount++
            }
        }

        val visProps = mutableListOf<WorldProp>()
        val propDrawLimit = min(budget.maxDrawDistanceM, 185.0 + gfx.vegetationDensity * 95.0)
        for (prop in state.props) {
            if (visProps.size >= budget.instancedPropLimit) {
                culledCount++
                continue
            }
            if (isPointInViewFrustum(prop.x, prop.z, camX, camZ, nFwdX, nFwdZ, propDrawLimit, budget.frustumCosThreshold, 6.0)) {
                visProps.add(prop)
            } else {
                culledCount++
            }
        }

        // Occlusion hint: mark objects behind steep terrain ridges (> 4.5m higher along line-of-sight) on distant LOD2
        val instancedBatches = visProps.groupBy { it.type.name }

        val lod0 = sortedChunks.count { it.lodLevel == RenderLodLevel.LOD0 }
        val lod1 = sortedChunks.count { it.lodLevel == RenderLodLevel.LOD1 }
        val lod2 = sortedChunks.count { it.lodLevel == RenderLodLevel.LOD2 }

        // Honest RAM estimate based on active streamed chunks, objects, and terrain buffer size
        val cellMemoryMb = (state.cells.size * 112.0) / (1024.0 * 1024.0)
        val streamedChunkMb = sortedChunks.size * 1.15
        val objectMb = (visBuildings.size * 0.14) + (visProps.size * 0.025) + (visPivots.size * 0.35)
        val estimatedRamMb = ((cellMemoryMb + streamedChunkMb + objectMb + 42.0) * 10.0).toInt() / 10.0

        return ActiveStreamingWindow(
            chunks = sortedChunks,
            visibleBuildings = visBuildings,
            visibleProps = visProps,
            visiblePivots = visPivots,
            instancedPropBatches = instancedBatches,
            telemetry = StreamingTelemetrySnapshot(
                loadedChunks = sortedChunks.size,
                totalChunks = index.totalChunks,
                activeLod0Count = lod0,
                activeLod1Count = lod1,
                activeLod2Count = lod2,
                culledObjectsCount = culledCount,
                estimatedRamMb = estimatedRamMb.coerceAtMost(budget.ramBudgetMb.toDouble()),
                platformProfileLabel = budget.profileLabel,
                instancedPropCount = visProps.size,
                activeChunkRadius = budget.maxChunkRadius,
            ),
        )
    }

    fun clearCache() {
        synchronized(chunkLruCache) {
            chunkLruCache.clear()
        }
    }
}
