package com.example.data

import com.example.model.ConvoyMessage
import com.example.model.ConvoyPlayer
import com.example.model.ConvoyRoom
import com.example.model.DriverProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class ConvoyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Debes iniciar sesión con Google para usar el Modo Convoy.")
    }

    fun observePublicRooms(): Flow<List<ConvoyRoom>> = flow {
        requireUserId()
        val path = "convoyRooms"
        emitAll(
            db.collection(path)
                .whereEqualTo("privateRoom", false)
                .limit(20)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ConvoyRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val ex = fsErr ?: (error as? Exception)
                    if (ex != null && auth.currentUser != null) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeRoom(roomId: String): Flow<ConvoyRoom?> = flow {
        requireUserId()
        val docRef = db.collection("convoyRooms").document(roomId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            ConvoyRoom::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val ex = fsErr ?: (error as? Exception)
                    if (ex != null && auth.currentUser != null) {
                        handleFirestoreError(ex, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observePlayers(roomId: String): Flow<List<ConvoyPlayer>> = flow {
        requireUserId()
        val path = "convoyRooms/$roomId/players"
        emitAll(
            db.collection("convoyRooms").document(roomId).collection("players")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ConvoyPlayer::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val ex = fsErr ?: (error as? Exception)
                    if (ex != null && auth.currentUser != null) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMessages(roomId: String): Flow<List<ConvoyMessage>> = flow {
        requireUserId()
        val path = "convoyRooms/$roomId/messages"
        emitAll(
            db.collection("convoyRooms").document(roomId).collection("messages")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(40)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ConvoyMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).reversed()
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val ex = fsErr ?: (error as? Exception)
                    if (ex != null && auth.currentUser != null) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createConvoyRoom(
        roomNameRaw: String,
        routeId: String,
        privateRoom: Boolean,
        profile: DriverProfile
    ): Result<String> {
        var path = "convoyRooms"
        return try {
            val uid = requireUserId()
            val code = generateRoomCode()
            val roomId = "CONVOY_$code"
            val roomRef = db.collection("convoyRooms").document(roomId)
            val playerRef = roomRef.collection("players").document(uid)
            path = roomRef.path

            val safeRoomName = roomNameRaw.trim().take(50).let {
                if (it.length >= 2) it else "Convoy ${profile.driverName.take(18)}"
            }
            val safeHostName = profile.driverName.trim().take(40).let {
                if (it.length >= 2) it else "Conductor Pro"
            }

            val roomPayload = mapOf(
                "roomCode" to code,
                "roomName" to safeRoomName,
                "hostId" to uid,
                "hostName" to safeHostName,
                "routeId" to routeId,
                "privateRoom" to privateRoom,
                "status" to "OPEN",
                "memberUids" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            roomRef.set(roomPayload).await()

            val playerPayload = buildPlayerPayload(uid, profile, roadS = 18.0, laneOffset = 2.15, speedKmh = 0L, headlightsOn = true, hornActive = false, online = true)
            playerRef.set(playerPayload).await()

            Result.success(roomId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun joinConvoyRoom(
        roomCodeOrIdRaw: String,
        profile: DriverProfile
    ): Result<String> {
        var path = "convoyRooms"
        return try {
            val uid = requireUserId()
            val cleaned = roomCodeOrIdRaw.trim().uppercase().removePrefix("CONVOY_")
                .filter { it.isLetterOrDigit() }
            require(cleaned.length in 4..10) { "Código de sala inválido (ej. TRK901)." }

            val roomId = "CONVOY_$cleaned"
            val roomRef = db.collection("convoyRooms").document(roomId)
            path = roomRef.path

            val snapshot = roomRef.get().await()
            require(snapshot.exists()) { "No existe ninguna sala de convoy con código $cleaned." }
            val room = snapshot.toObject(ConvoyRoom::class.java)
                ?: throw IllegalStateException("Datos de sala inválidos.")

            if (uid !in room.memberUids) {
                require(room.memberUids.size < 8) { "La sala de convoy está llena (máximo 8 camioneros)." }
                val updatedMembers = (room.memberUids + uid).distinct().take(8)
                roomRef.update(
                    mapOf(
                        "memberUids" to updatedMembers,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val playerRef = roomRef.collection("players").document(uid)
            val playerPayload = buildPlayerPayload(
                uid = uid,
                profile = profile,
                roadS = 18.0,
                laneOffset = 2.15,
                speedKmh = 0L,
                headlightsOn = true,
                hornActive = false,
                online = true
            )
            playerRef.set(playerPayload).await()
            Result.success(roomId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun leaveConvoyRoom(roomId: String, currentRoom: ConvoyRoom?): Result<Unit> {
        var path = "convoyRooms/$roomId"
        return try {
            val uid = requireUserId()
            val roomRef = db.collection("convoyRooms").document(roomId)
            val playerRef = roomRef.collection("players").document(uid)
            playerRef.delete().await()

            val snap = roomRef.get().await()
            if (snap.exists()) {
                val room = snap.toObject(ConvoyRoom::class.java) ?: currentRoom
                if (room != null) {
                    val remaining = room.memberUids.filter { it != uid }
                    if (remaining.isEmpty() || room.hostId == uid) {
                        if (room.hostId == uid && remaining.isEmpty()) {
                            roomRef.delete().await()
                        } else if (remaining.isNotEmpty()) {
                            roomRef.update(
                                mapOf(
                                    "memberUids" to remaining,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                    } else {
                        roomRef.update(
                            mapOf(
                                "memberUids" to remaining,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateRoomHostConfig(roomId: String, routeId: String, status: String): Result<Unit> {
        val path = "convoyRooms/$roomId"
        return try {
            requireUserId()
            val safeStatus = if (status == "DRIVING") "DRIVING" else "OPEN"
            db.collection("convoyRooms").document(roomId).update(
                mapOf(
                    "routeId" to routeId,
                    "status" to safeStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun syncPlayerTelemetry(
        roomId: String,
        profile: DriverProfile,
        roadS: Double,
        laneOffset: Double,
        speedKmh: Int,
        headlightsOn: Boolean,
        hornActive: Boolean,
        online: Boolean = true
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val path = "convoyRooms/$roomId/players/$uid"
        return try {
            val payload = buildPlayerPayload(
                uid = uid,
                profile = profile,
                roadS = roadS.coerceIn(0.0, 20000.0),
                laneOffset = laneOffset.coerceIn(-12.0, 12.0),
                speedKmh = speedKmh.coerceIn(0, 220).toLong(),
                headlightsOn = headlightsOn,
                hornActive = hornActive,
                online = online
            )
            db.collection("convoyRooms").document(roomId)
                .collection("players").document(uid)
                .set(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(roomId: String, senderNameRaw: String, textRaw: String): Result<Unit> {
        var path = "convoyRooms/$roomId/messages"
        return try {
            val uid = requireUserId()
            val cleanText = textRaw.trim().take(200)
            require(cleanText.isNotEmpty()) { "El mensaje está vacío." }
            val safeSender = senderNameRaw.trim().take(40).let {
                if (it.length >= 2) it else "Conductor"
            }
            val msgRef = db.collection("convoyRooms").document(roomId)
                .collection("messages").document()
            path = msgRef.path
            msgRef.set(
                mapOf(
                    "userId" to uid,
                    "senderName" to safeSender,
                    "text" to cleanText,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private fun buildPlayerPayload(
        uid: String,
        profile: DriverProfile,
        roadS: Double,
        laneOffset: Double,
        speedKmh: Long,
        headlightsOn: Boolean,
        hornActive: Boolean,
        online: Boolean
    ): Map<String, Any> {
        val safeName = profile.driverName.trim().take(40).let { if (it.length >= 2) it else "Conductor Pro" }
        val safeHex = if (profile.paintColorHex.startsWith("#") && profile.paintColorHex.length in 4..9) {
            profile.paintColorHex
        } else "#1E5BB8"
        return mapOf(
            "userId" to uid,
            "driverName" to safeName,
            "truckId" to profile.selectedTruckId,
            "paintColorHex" to safeHex,
            "roadS" to roadS,
            "laneOffset" to laneOffset,
            "speedKmh" to speedKmh,
            "headlightsOn" to headlightsOn,
            "hornActive" to hornActive,
            "online" to online,
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    private fun generateRoomCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(6) {
            repeat(6) {
                append(alphabet[Random.nextInt(alphabet.length)])
            }
        }
    }
}
