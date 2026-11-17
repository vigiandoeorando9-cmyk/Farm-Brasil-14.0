package com.example.firebase

import android.content.Context
import com.example.R
import com.example.domain.ALL_BUILTIN_MAPS
import com.example.domain.GAME_SEMVER
import com.example.domain.HubPackEntity
import com.example.domain.compareVersions
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class MultiplayerCompatibilityReport(
    val isCompatible: Boolean,
    val missingModIds: List<String>,
    val outdatedModIds: List<String>,
    val mapMismatchReason: String? = null,
    val versionMismatchReason: String? = null,
    val summaryMessage: String,
    val actionPackIds: List<String> = (missingModIds + outdatedModIds).distinct(),
)

fun evaluateRoomCompatibility(
    room: MultiplayerRoomDoc,
    installedPacks: List<HubPackEntity>,
    localGameVersion: String = GAME_SEMVER,
): MultiplayerCompatibilityReport {
    val missing = mutableListOf<String>()
    val outdated = mutableListOf<String>()
    var mapMismatch: String? = null
    var versionMismatch: String? = null

    if (room.gameVersion.isNotBlank() && compareVersions(localGameVersion, room.gameVersion) < 0) {
        versionMismatch = "Versão do jogo incompatível (Sala v${room.gameVersion} vs Local v$localGameVersion)."
    }

    val mapDef = ALL_BUILTIN_MAPS.find { it.id == room.mapId }
    if (mapDef == null) {
        mapMismatch = "Mapa '${room.mapId}' (${room.mapAreaTierId}) não encontrado localmente."
    } else if (mapDef.requiredPackId != null) {
        val mapPack = installedPacks.find { it.packId == mapDef.requiredPackId }
        if (mapPack == null || !mapPack.installed || !mapPack.enabled) {
            missing += mapDef.requiredPackId
            mapMismatch = "Pacote do mapa '${mapDef.stateName}' não está instalado ou ativado."
        } else if (room.mapVersion.isNotBlank() && compareVersions(mapPack.version, room.mapVersion) < 0) {
            outdated += mapDef.requiredPackId
            mapMismatch = "Mapa '${mapDef.stateName}' desatualizado (v${mapPack.version} < v${room.mapVersion})."
        }
    }

    for (reqModId in room.requiredModIds) {
        val pack = installedPacks.find { it.packId == reqModId }
        if (pack == null || !pack.installed || !pack.enabled) {
            if (!missing.contains(reqModId)) missing += reqModId
        } else {
            val reqVer = room.requiredModVersions[reqModId]
            if (!reqVer.isNullOrBlank() && compareVersions(pack.version, reqVer) != 0) {
                if (!outdated.contains(reqModId)) outdated += reqModId
            }
        }
    }

    val ok = versionMismatch == null && mapMismatch == null && missing.isEmpty() && outdated.isEmpty()
    val summary = if (ok) {
        "Compatível com a sala ${room.roomCode} (${room.mapAreaTierId})."
    } else {
        val parts = mutableListOf<String>()
        versionMismatch?.let { parts += it }
        mapMismatch?.let { parts += it }
        if (missing.isNotEmpty()) parts += "Faltando: ${missing.joinToString(", ")}"
        if (outdated.isNotEmpty()) parts += "Desatualizado/Divergente: ${outdated.joinToString(", ")}"
        parts.joinToString(" • ")
    }

    return MultiplayerCompatibilityReport(
        isCompatible = ok,
        missingModIds = missing,
        outdatedModIds = outdated,
        mapMismatchReason = mapMismatch,
        versionMismatchReason = versionMismatch,
        summaryMessage = summary,
    )
}

class MultiplayerRepository(
    private val db: FirebaseFirestore?,
    private val auth: FirebaseAuth?,
) {
    constructor(context: Context) : this(
        db = tryCreateFirestore(context),
        auth = tryGetFirebaseAuth(context),
    )

    companion object {
        private fun tryCreateFirestore(context: Context): FirebaseFirestore? = runCatching {
            val appCtx = context.applicationContext
            if (FirebaseApp.getApps(appCtx).isNotEmpty()) {
                FirebaseFirestore.getInstance(appCtx.getString(R.string.firestore_database_id))
            } else null
        }.getOrNull()

        private fun tryGetFirebaseAuth(context: Context): FirebaseAuth? = runCatching {
            if (FirebaseApp.getApps(context.applicationContext).isNotEmpty()) Firebase.auth else null
        }.getOrNull()
    }

    private fun requireDb(): FirebaseFirestore =
        db ?: throw IllegalStateException("Multiplayer online indisponível no modo offline.")

    private fun requireAuth(): FirebaseAuth =
        auth ?: throw IllegalStateException("Autenticação Firebase indisponível no modo offline.")

    private fun requireUserId(): String {
        return requireAuth().currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun createOrHostRoom(
        roomCode: String,
        hostName: String,
        mapId: String,
        mapVersion: String = "2.0.0",
        mapAreaTierId: String = "8X",
        gameVersion: String = GAME_SEMVER,
        sharedFarmMode: Boolean = true,
        pauseOnDisconnect: Boolean = true,
        requiredModIds: List<String> = emptyList(),
        requiredModVersions: Map<String, String> = emptyMap(),
        maxPlayers: Long = 8L,
    ) {
        val uid = requireUserId()
        val cleanRoom = roomCode.trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "-").take(32)
        require(cleanRoom.isNotBlank()) { "Room code must not be blank" }
        val safeHost = hostName.trim().take(48).let { if (it.length >= 2) it else "Fazendeiro BR" }

        val roomRef = requireDb().collection("mpRooms").document(cleanRoom)
        try {
            val snap = roomRef.get().await()
            if (!snap.exists()) {
                roomRef.set(
                    mapOf(
                        "roomCode" to cleanRoom,
                        "hostUid" to uid,
                        "hostName" to safeHost,
                        "mapId" to mapId.take(64).ifBlank { "map_pr" },
                        "mapVersion" to mapVersion.take(24).ifBlank { "2.0.0" },
                        "mapAreaTierId" to mapAreaTierId.take(16).ifBlank { "8X" },
                        "gameVersion" to gameVersion.take(24).ifBlank { GAME_SEMVER },
                        "sharedFarmMode" to sharedFarmMode,
                        "pauseOnDisconnect" to pauseOnDisconnect,
                        "status" to "WAITING",
                        "requiredModIds" to requiredModIds.take(250),
                        "requiredModVersions" to requiredModVersions,
                        "memberCount" to 1L,
                        "maxPlayers" to maxPlayers.coerceIn(2L, 16L),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else if (snap.getString("hostUid") == uid && snap.getString("status") != "CLOSED") {
                roomRef.update(
                    mapOf(
                        "hostName" to safeHost,
                        "mapVersion" to mapVersion.take(24).ifBlank { "2.0.0" },
                        "mapAreaTierId" to mapAreaTierId.take(16).ifBlank { "8X" },
                        "sharedFarmMode" to sharedFarmMode,
                        "pauseOnDisconnect" to pauseOnDisconnect,
                        "requiredModIds" to requiredModIds.take(250),
                        "requiredModVersions" to requiredModVersions,
                        "status" to "ACTIVE",
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }

            val memberRef = roomRef.collection("members").document(uid)
            val memSnap = memberRef.get().await()
            if (!memSnap.exists()) {
                memberRef.set(
                    mapOf(
                        "memberUid" to uid,
                        "roomCode" to cleanRoom,
                        "playerName" to safeHost,
                        "x" to 0.0,
                        "z" to 0.0,
                        "headingDeg" to 0.0,
                        "isApproved" to true,
                        "role" to "HOST",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, roomRef.path, auth)
            throw e
        }
    }

    suspend fun joinRoomAsGuest(
        roomCode: String,
        playerName: String,
        x: Double = 0.0,
        z: Double = 0.0,
        installedPacks: List<HubPackEntity>? = null,
    ): MultiplayerRoomDoc {
        val uid = requireUserId()
        val cleanRoom = roomCode.trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "-").take(32)
        val roomRef = requireDb().collection("mpRooms").document(cleanRoom)
        try {
            val snap = roomRef.get().await()
            val room = snap.toObject(
                MultiplayerRoomDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: throw IllegalStateException("Sala online '$cleanRoom' não encontrada.")
            check(room.status != "CLOSED") { "Sala '$cleanRoom' já foi encerrada pelo Host." }
            if (installedPacks != null) {
                val compat = evaluateRoomCompatibility(room, installedPacks)
                check(compat.isCompatible) { compat.summaryMessage }
            }

            val safeName = playerName.trim().take(48).let { if (it.length >= 2) it else "Operador BR" }
            val memberRef = roomRef.collection("members").document(uid)
            if (!memberRef.get().await().exists()) {
                memberRef.set(
                    mapOf(
                        "memberUid" to uid,
                        "roomCode" to cleanRoom,
                        "playerName" to safeName,
                        "x" to x,
                        "z" to z,
                        "headingDeg" to 0.0,
                        "isApproved" to false,
                        "role" to "GUEST",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            return room
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, roomRef.path, auth)
            throw e
        }
    }

    fun observeOpenRooms(): Flow<List<MultiplayerRoomDoc>> {
        val firestore = db ?: return flowOf(emptyList())
        val firebaseAuth = auth ?: return flowOf(emptyList())
        return flow {
            requireUserId()
            val query = firestore.collection("mpRooms")
                .whereIn("status", listOf("WAITING", "ACTIVE"))
            emitAll(
                query.snapshots()
                    .map { snapshot ->
                        snapshot.toObjects(
                            MultiplayerRoomDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val toLog = firestoreError ?: (error as? Exception)
                        if (toLog != null && firebaseAuth.currentUser != null) {
                            handleFirestoreError(toLog, OperationType.LIST, "mpRooms", firebaseAuth)
                        }
                        throw error
                    }
            )
        }
    }

    fun observeRoom(roomCode: String): Flow<MultiplayerRoomDoc?> {
        val firestore = db ?: return flowOf(null)
        val firebaseAuth = auth ?: return flowOf(null)
        return flow {
            requireUserId()
            val cleanRoom = roomCode.trim().uppercase()
            val roomRef = firestore.collection("mpRooms").document(cleanRoom)
            emitAll(
                roomRef.snapshots()
                    .map { snapshot ->
                        if (!snapshot.exists()) {
                            null
                        } else {
                            snapshot.toObject(
                                MultiplayerRoomDoc::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )
                        }
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val toLog = firestoreError ?: (error as? Exception)
                        if (toLog != null && firebaseAuth.currentUser != null) {
                            handleFirestoreError(toLog, OperationType.GET, "mpRooms/$cleanRoom", firebaseAuth)
                        }
                        throw error
                    }
            )
        }
    }

    fun observeRoomMembers(roomCode: String): Flow<List<RoomMemberDoc>> {
        val firestore = db ?: return flowOf(emptyList())
        val firebaseAuth = auth ?: return flowOf(emptyList())
        return flow {
            requireUserId()
            val cleanRoom = roomCode.trim().uppercase()
            val query = firestore.collection("mpRooms").document(cleanRoom)
                .collection("members")
                .whereEqualTo("roomCode", cleanRoom)
            emitAll(
                query.snapshots()
                    .map { snapshot ->
                        snapshot.toObjects(
                            RoomMemberDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val toLog = firestoreError ?: (error as? Exception)
                        if (toLog != null && firebaseAuth.currentUser != null) {
                            handleFirestoreError(toLog, OperationType.LIST, "mpRooms/$cleanRoom/members", firebaseAuth)
                        }
                        throw error
                    }
            )
        }
    }

    fun observeRoomMessages(roomCode: String): Flow<List<RoomChatDoc>> {
        val firestore = db ?: return flowOf(emptyList())
        val firebaseAuth = auth ?: return flowOf(emptyList())
        return flow {
            requireUserId()
            val cleanRoom = roomCode.trim().uppercase()
            val query = firestore.collection("mpRooms").document(cleanRoom)
                .collection("messages")
                .whereEqualTo("roomCode", cleanRoom)
            emitAll(
                query.snapshots()
                    .map { snapshot ->
                        snapshot.toObjects(
                            RoomChatDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ).sortedBy { it.createdAt?.toDate()?.time ?: 0L }.takeLast(40)
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val toLog = firestoreError ?: (error as? Exception)
                        if (toLog != null && firebaseAuth.currentUser != null) {
                            handleFirestoreError(toLog, OperationType.LIST, "mpRooms/$cleanRoom/messages", firebaseAuth)
                        }
                        throw error
                    }
            )
        }
    }

    suspend fun approveMember(roomCode: String, memberUid: String, approve: Boolean) {
        requireUserId()
        val cleanRoom = roomCode.trim().uppercase()
        val firestore = requireDb()
        val memRef = firestore.collection("mpRooms").document(cleanRoom).collection("members").document(memberUid)
        try {
            memRef.update(
                mapOf(
                    "isApproved" to approve,
                    "role" to if (approve) "OPERATOR" else "GUEST",
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            if (approve) {
                firestore.collection("mpRooms").document(cleanRoom).update(
                    mapOf(
                        "status" to "ACTIVE",
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, memRef.path, auth)
            throw e
        }
    }

    suspend fun updateLocalTelemetry(
        roomCode: String,
        playerName: String,
        x: Double,
        z: Double,
        vehicleDefId: String?,
        headingDeg: Double,
        pitchDeg: Double = 0.0,
        rollDeg: Double = 0.0,
        steerAngleDeg: Double = 0.0,
        wheelSpinDeg: Double = 0.0,
        suspensionOffsetY: Double = 0.0,
    ) {
        val uid = requireUserId()
        val cleanRoom = roomCode.trim().uppercase()
        val memRef = requireDb().collection("mpRooms").document(cleanRoom).collection("members").document(uid)
        val payload = mutableMapOf<String, Any?>(
            "playerName" to playerName.trim().take(48).let { if (it.length >= 2) it else "Produtor BR" },
            "x" to x,
            "z" to z,
            "vehicleDefId" to vehicleDefId?.take(64),
            "headingDeg" to headingDeg,
            "pitchDeg" to pitchDeg,
            "rollDeg" to rollDeg,
            "steerAngleDeg" to steerAngleDeg,
            "wheelSpinDeg" to wheelSpinDeg,
            "suspensionOffsetY" to suspensionOffsetY,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            memRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, memRef.path, auth)
            throw e
        }
    }

    suspend fun sendChatMessage(roomCode: String, senderName: String, text: String) {
        val uid = requireUserId()
        val cleanRoom = roomCode.trim().uppercase()
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val msgRef = requireDb().collection("mpRooms").document(cleanRoom).collection("messages").document(msgId)
        val cleanText = text.trim().take(240)
        require(cleanText.isNotEmpty()) { "Chat message must not be empty" }
        try {
            msgRef.set(
                mapOf(
                    "senderUid" to uid,
                    "senderName" to senderName.trim().take(48).let { if (it.length >= 2) it else "Produtor BR" },
                    "roomCode" to cleanRoom,
                    "text" to cleanText,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path, auth)
            throw e
        }
    }

    suspend fun toggleSharedFarm(roomCode: String, enabled: Boolean) {
        requireUserId()
        val cleanRoom = roomCode.trim().uppercase()
        val roomRef = requireDb().collection("mpRooms").document(cleanRoom)
        try {
            roomRef.update(
                mapOf(
                    "sharedFarmMode" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, roomRef.path, auth)
            throw e
        }
    }

    suspend fun closeRoom(roomCode: String) {
        requireUserId()
        val cleanRoom = roomCode.trim().uppercase()
        val roomRef = requireDb().collection("mpRooms").document(cleanRoom)
        try {
            roomRef.update(
                mapOf(
                    "status" to "CLOSED",
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, roomRef.path, auth)
            throw e
        }
    }
}
