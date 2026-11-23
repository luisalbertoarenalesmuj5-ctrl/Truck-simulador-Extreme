package com.example.data

import android.content.Context
import com.example.R
import com.example.model.CameraMode
import com.example.model.DeliveryRecord
import com.example.model.DriverProfile
import com.example.model.RouteSpec
import com.example.model.SteeringMode
import com.example.model.TruckSpec
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
import kotlin.math.roundToLong

class CareerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("El conductor debe iniciar sesión con Google antes de acceder a Firestore.")
    }

    fun observeDriverProfile(userId: String): Flow<DriverProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Sesión cambiada; rechazando lectura cruzada." }
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            DriverProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeDeliveries(userId: String): Flow<List<DeliveryRecord>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Sesión cambiada; rechazando lectura cruzada." }
        val path = "users/$userId/deliveries"
        emitAll(
            db.collection("users").document(userId).collection("deliveries")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DeliveryRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureDriverProfileInitialized(displayNameRaw: String?): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val cleanName = (displayNameRaw?.trim()?.take(40) ?: "").let {
                    if (it.length >= 2) it else "Conductor Pro"
                }
                val payload = mapOf(
                    "userId" to uid,
                    "driverName" to cleanName,
                    "credits" to 25000L,
                    "totalDistanceKm" to 0.0,
                    "deliveriesCompleted" to 0L,
                    "xp" to 0L,
                    "selectedTruckId" to "nordic_fh540",
                    "unlockedTruckIds" to listOf("nordic_fh540"),
                    "steeringMode" to SteeringMode.WHEEL.name,
                    "steeringSensitivity" to 1.0,
                    "cameraMode" to CameraMode.SIDE_2D.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateControlSettings(
        steeringMode: SteeringMode,
        sensitivity: Double,
        cameraMode: CameraMode,
        driverName: String
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            val safeName = driverName.trim().take(40).let { if (it.length >= 2) it else "Conductor Pro" }
            val clampedSensitivity = sensitivity.coerceIn(0.4, 2.0)
            docRef.update(
                mapOf(
                    "driverName" to safeName,
                    "steeringMode" to steeringMode.name,
                    "steeringSensitivity" to clampedSensitivity,
                    "cameraMode" to cameraMode.name,
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

    suspend fun selectOrPurchaseTruck(
        currentProfile: DriverProfile,
        truck: TruckSpec
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path

            if (truck.id in currentProfile.unlockedTruckIds) {
                docRef.update(
                    mapOf(
                        "selectedTruckId" to truck.id,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                require(currentProfile.credits >= truck.priceCredits) {
                    "Créditos insuficientes para adquirir ${truck.name}"
                }
                val newCredits = (currentProfile.credits - truck.priceCredits).coerceAtLeast(0L)
                val updatedList = (currentProfile.unlockedTruckIds + truck.id).distinct().take(10)
                docRef.update(
                    mapOf(
                        "credits" to newCredits,
                        "selectedTruckId" to truck.id,
                        "unlockedTruckIds" to updatedList,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordCompletedDelivery(
        currentProfile: DriverProfile,
        route: RouteSpec,
        truckId: String,
        damagePercent: Int,
        durationSeconds: Long
    ): Result<Long> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val safeDamage = damagePercent.coerceIn(0, 100)
            val safeDuration = durationSeconds.coerceIn(1L, 86400L)
            val isPerfect = safeDamage == 0
            val damageFactor = (1.0 - (safeDamage * 0.006)).coerceAtLeast(0.4)
            val perfectBonus = if (isPerfect) (route.baseRewardCredits * 0.15).roundToLong() else 0L
            val earnedCredits = ((route.baseRewardCredits * damageFactor).roundToLong() + perfectBonus)
                .coerceIn(500L, 1_000_000L)
            val earnedXp = (route.displayDistanceKm * 10 + if (isPerfect) 150 else 50).roundToLong()

            val userRef = db.collection("users").document(uid)
            val deliveryRef = userRef.collection("deliveries").document()
            path = deliveryRef.path

            val batch = db.batch()
            val deliveryPayload = mapOf(
                "userId" to uid,
                "routeId" to route.id,
                "routeName" to route.title.take(80),
                "cargoName" to route.cargoName.take(80),
                "truckId" to truckId,
                "distanceKm" to route.displayDistanceKm,
                "earnedCredits" to earnedCredits,
                "damagePercent" to safeDamage.toLong(),
                "durationSeconds" to safeDuration,
                "perfectDelivery" to isPerfect,
                "createdAt" to FieldValue.serverTimestamp()
            )
            batch.set(deliveryRef, deliveryPayload)
            batch.update(
                userRef,
                mapOf(
                    "credits" to (currentProfile.credits + earnedCredits).coerceAtMost(100_000_000L),
                    "totalDistanceKm" to (currentProfile.totalDistanceKm + route.displayDistanceKm).coerceAtMost(10_000_000.0),
                    "deliveriesCompleted" to (currentProfile.deliveriesCompleted + 1L),
                    "xp" to (currentProfile.xp + earnedXp).coerceAtMost(100_000_000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(earnedCredits)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}
