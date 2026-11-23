package com.example.data

import android.content.Context
import com.example.R
import com.example.model.CameraMode
import com.example.model.CareerCertification
import com.example.model.CareerMilestone
import com.example.model.ContractGenerator
import com.example.model.ContractStatus
import com.example.model.DeliveryContract
import com.example.model.DeliveryRecord
import com.example.model.DriverProfile
import com.example.model.LiveryStyle
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

    fun observeContracts(userId: String): Flow<List<DeliveryContract>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Sesión cambiada; rechazando lectura cruzada." }
        val path = "users/$userId/contracts"
        emitAll(
            db.collection("users").document(userId).collection("contracts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DeliveryContract::class.java,
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

    suspend fun ensureContractsPopulated(driverLevel: Long): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/contracts"
        return try {
            val uid = requireUserId()
            val contractsRef = db.collection("users").document(uid).collection("contracts")
            path = contractsRef.path
            val existing = contractsRef.limit(20).get().await()
            val hasOpenContracts = existing.documents.any { doc ->
                val status = doc.getString("status")
                status == ContractStatus.AVAILABLE.name || status == ContractStatus.ACTIVE.name
            }
            if (!hasOpenContracts) {
                generateRandomContracts(driverLevel = driverLevel, clearAvailable = false).getOrThrow()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun generateRandomContracts(
        driverLevel: Long,
        clearAvailable: Boolean = true
    ): Result<Int> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/contracts"
        return try {
            val uid = requireUserId()
            val contractsRef = db.collection("users").document(uid).collection("contracts")
            path = contractsRef.path
            val snapshot = contractsRef.limit(30).get().await()
            val batch = db.batch()

            if (clearAvailable) {
                snapshot.documents
                    .filter { it.getString("status") == ContractStatus.AVAILABLE.name }
                    .forEach { batch.delete(it.reference) }
            }

            // Keep completed contract history bounded to most recent 8
            val completedDocs = snapshot.documents
                .filter { it.getString("status") == ContractStatus.COMPLETED.name }
            if (completedDocs.size > 8) {
                completedDocs.drop(8).forEach { batch.delete(it.reference) }
            }

            val generated = ContractGenerator.generateBatch(
                userId = uid,
                driverLevel = driverLevel.coerceIn(1L, DriverProfile.MAX_LEVEL),
                count = 5
            )
            generated.forEach { contract ->
                val docRef = contractsRef.document()
                val payload = mapOf(
                    "userId" to uid,
                    "title" to contract.title,
                    "companyName" to contract.companyName,
                    "cargoName" to contract.cargoName,
                    "cargoTons" to contract.cargoTons,
                    "trailerType" to contract.trailerType,
                    "originCity" to contract.originCity,
                    "destinationCity" to contract.destinationCity,
                    "routeId" to contract.routeId,
                    "distanceKm" to contract.distanceKm,
                    "difficulty" to contract.difficulty,
                    "rewardCredits" to contract.rewardCredits,
                    "rewardXp" to contract.rewardXp,
                    "bonusPerfectCredits" to contract.bonusPerfectCredits,
                    "requiredLevel" to contract.requiredLevel,
                    "status" to ContractStatus.AVAILABLE.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(docRef, payload)
            }
            batch.commit().await()
            Result.success(generated.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun acceptContract(
        currentProfile: DriverProfile,
        contract: DeliveryContract
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/contracts"
        return try {
            val uid = requireUserId()
            require(contract.id.isNotBlank()) { "Contrato sin identificador válido." }
            require(contract.status != ContractStatus.COMPLETED.name) { "Este contrato ya fue completado." }
            require(currentProfile.effectiveLevel() >= contract.requiredLevel) {
                "Requiere Nivel ${contract.requiredLevel} para aceptar este contrato."
            }
            val userRef = db.collection("users").document(uid)
            val contractsRef = userRef.collection("contracts")
            val targetRef = contractsRef.document(contract.id)
            path = targetRef.path

            val activeDocs = contractsRef
                .whereEqualTo("status", ContractStatus.ACTIVE.name)
                .get()
                .await()

            val batch = db.batch()
            activeDocs.documents
                .filter { it.id != contract.id }
                .forEach { doc ->
                    batch.update(
                        doc.reference,
                        mapOf(
                            "status" to ContractStatus.AVAILABLE.name,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            if (contract.status != ContractStatus.ACTIVE.name) {
                batch.update(
                    targetRef,
                    mapOf(
                        "status" to ContractStatus.ACTIVE.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.update(
                userRef,
                mapOf(
                    "activeContractId" to contract.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun releaseActiveContract(contract: DeliveryContract): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/contracts"
        return try {
            val uid = requireUserId()
            require(contract.id.isNotBlank()) { "Contrato inválido." }
            val userRef = db.collection("users").document(uid)
            val targetRef = userRef.collection("contracts").document(contract.id)
            path = targetRef.path

            val batch = db.batch()
            if (contract.status == ContractStatus.ACTIVE.name) {
                batch.update(
                    targetRef,
                    mapOf(
                        "status" to ContractStatus.AVAILABLE.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.update(
                userRef,
                mapOf(
                    "activeContractId" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
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
                    "level" to 1L,
                    "claimedMilestoneIds" to emptyList<String>(),
                    "activeContractId" to "",
                    "contractsCompleted" to 0L,
                    "selectedTruckId" to "nordic_fh540",
                    "unlockedTruckIds" to listOf("nordic_fh540"),
                    "paintColorHex" to "#1E5BB8",
                    "liveryStyle" to LiveryStyle.CLASSIC.name,
                    "bullbarInstalled" to false,
                    "roofLightbarInstalled" to false,
                    "aeroSpoilerInstalled" to false,
                    "chromeExhaustInstalled" to false,
                    "truckDamagePercent" to 0L,
                    "steeringMode" to SteeringMode.WHEEL.name,
                    "steeringSensitivity" to 1.0,
                    "cameraMode" to CameraMode.EXTERIOR_2D.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else if (!existing.contains("level")) {
                val existingXp = (existing.get("xp") as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
                val syncedLevel = DriverProfile.levelForXp(existingXp)
                docRef.update(
                    mapOf(
                        "level" to syncedLevel,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun savePlayerProfile(
        currentProfile: DriverProfile,
        driverName: String,
        steeringMode: SteeringMode,
        sensitivity: Double,
        cameraMode: CameraMode
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            val safeName = driverName.trim().take(40).let {
                require(it.length >= 2) { "El nombre del conductor debe tener al menos 2 caracteres." }
                it
            }
            val clampedSensitivity = sensitivity.coerceIn(0.4, 2.0)
            val syncedLevel = currentProfile.effectiveLevel()
            val safeXp = currentProfile.xp.coerceIn(0L, 100_000_000L)
            val safeCredits = currentProfile.credits.coerceIn(0L, 100_000_000L)
            docRef.update(
                mapOf(
                    "driverName" to safeName,
                    "level" to syncedLevel,
                    "xp" to safeXp,
                    "credits" to safeCredits,
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

    suspend fun claimCareerMilestone(
        currentProfile: DriverProfile,
        milestone: CareerMilestone
    ): Result<DriverProfile> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            require(milestone.id !in currentProfile.claimedMilestoneIds) {
                "Este hito ya fue reclamado en tu carrera."
            }
            require(milestone.isUnlocked(currentProfile)) {
                "Aún no cumples el requisito: ${milestone.requirementLabel}"
            }
            val nextXp = (currentProfile.xp + milestone.rewardXp).coerceAtMost(100_000_000L)
            val nextLevel = maxOf(currentProfile.effectiveLevel(), DriverProfile.levelForXp(nextXp))
            val nextCredits = (currentProfile.credits + milestone.rewardCredits).coerceAtMost(100_000_000L)
            val nextClaimed = (currentProfile.claimedMilestoneIds + milestone.id).distinct().take(20)
            docRef.update(
                mapOf(
                    "xp" to nextXp,
                    "level" to nextLevel,
                    "credits" to nextCredits,
                    "claimedMilestoneIds" to nextClaimed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(
                currentProfile.copy(
                    xp = nextXp,
                    level = nextLevel,
                    credits = nextCredits,
                    claimedMilestoneIds = nextClaimed
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun completeCareerCertification(
        currentProfile: DriverProfile,
        certification: CareerCertification
    ): Result<DriverProfile> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            require(certification.id !in currentProfile.claimedMilestoneIds) {
                "Ya cuentas con esta certificación profesional."
            }
            require(currentProfile.credits >= certification.costCredits) {
                "Créditos insuficientes para cursar ${certification.title}."
            }
            val nextCredits = (currentProfile.credits - certification.costCredits).coerceAtLeast(0L)
            val nextXp = (currentProfile.xp + certification.grantXp).coerceAtMost(100_000_000L)
            val nextLevel = maxOf(currentProfile.effectiveLevel(), DriverProfile.levelForXp(nextXp))
            val nextClaimed = (currentProfile.claimedMilestoneIds + certification.id).distinct().take(20)
            docRef.update(
                mapOf(
                    "credits" to nextCredits,
                    "xp" to nextXp,
                    "level" to nextLevel,
                    "claimedMilestoneIds" to nextClaimed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(
                currentProfile.copy(
                    credits = nextCredits,
                    xp = nextXp,
                    level = nextLevel,
                    claimedMilestoneIds = nextClaimed
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
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
                val updatedList = (currentProfile.unlockedTruckIds + truck.id).distinct().take(12)
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

    suspend fun saveWorkshopCustomization(
        currentProfile: DriverProfile,
        paintColorHex: String,
        liveryStyle: LiveryStyle,
        bullbarInstalled: Boolean,
        roofLightbarInstalled: Boolean,
        aeroSpoilerInstalled: Boolean,
        chromeExhaustInstalled: Boolean,
        totalCostCredits: Long
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            val safeCost = totalCostCredits.coerceAtLeast(0L)
            require(currentProfile.credits >= safeCost) {
                "Créditos insuficientes para esta mejora del taller"
            }
            val newCredits = (currentProfile.credits - safeCost).coerceAtLeast(0L)
            docRef.update(
                mapOf(
                    "credits" to newCredits,
                    "paintColorHex" to paintColorHex,
                    "liveryStyle" to liveryStyle.name,
                    "bullbarInstalled" to bullbarInstalled,
                    "roofLightbarInstalled" to roofLightbarInstalled,
                    "aeroSpoilerInstalled" to aeroSpoilerInstalled,
                    "chromeExhaustInstalled" to chromeExhaustInstalled,
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

    suspend fun repairTruckOrAdjustCredits(
        currentProfile: DriverProfile,
        creditsDelta: Long,
        newDamagePercent: Long
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            val newCredits = (currentProfile.credits + creditsDelta).coerceIn(0L, 100_000_000L)
            val safeDamage = newDamagePercent.coerceIn(0L, 100L)
            docRef.update(
                mapOf(
                    "credits" to newCredits,
                    "truckDamagePercent" to safeDamage,
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

    suspend fun recordCompletedDelivery(
        currentProfile: DriverProfile,
        route: RouteSpec,
        truckId: String,
        damagePercent: Int,
        durationSeconds: Long,
        activeContract: DeliveryContract? = null
    ): Result<Long> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val safeDamage = damagePercent.coerceIn(0, 100)
            val safeDuration = durationSeconds.coerceIn(1L, 86400L)
            val isPerfect = safeDamage == 0
            val damageFactor = (1.0 - (safeDamage * 0.006)).coerceAtLeast(0.4)

            val baseReward = activeContract?.rewardCredits ?: route.baseRewardCredits
            val perfectBonus = if (isPerfect) {
                activeContract?.bonusPerfectCredits ?: (baseReward * 0.15).roundToLong()
            } else {
                0L
            }
            val earnedCredits = ((baseReward * damageFactor).roundToLong() + perfectBonus)
                .coerceIn(500L, 1_000_000L)
            val earnedXp = if (activeContract != null) {
                (activeContract.rewardXp + if (isPerfect) 150L else 40L).coerceIn(50L, 50_000L)
            } else {
                (route.displayDistanceKm * 10 + if (isPerfect) 150 else 50).roundToLong()
            }

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
            val nextXp = (currentProfile.xp + earnedXp).coerceAtMost(100_000_000L)
            val nextLevel = maxOf(currentProfile.effectiveLevel(), DriverProfile.levelForXp(nextXp))
            val isCompletingContract = activeContract != null &&
                activeContract.id.isNotBlank() &&
                activeContract.status != ContractStatus.COMPLETED.name

            batch.set(deliveryRef, deliveryPayload)
            if (isCompletingContract && activeContract != null) {
                val contractRef = userRef.collection("contracts").document(activeContract.id)
                batch.update(
                    contractRef,
                    mapOf(
                        "status" to ContractStatus.COMPLETED.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            val profileUpdates = mutableMapOf<String, Any>(
                "credits" to (currentProfile.credits + earnedCredits).coerceAtMost(100_000_000L),
                "totalDistanceKm" to (currentProfile.totalDistanceKm + route.displayDistanceKm).coerceAtMost(10_000_000.0),
                "deliveriesCompleted" to (currentProfile.deliveriesCompleted + 1L),
                "xp" to nextXp,
                "level" to nextLevel,
                "truckDamagePercent" to safeDamage.toLong(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (isCompletingContract && activeContract != null) {
                profileUpdates["contractsCompleted"] = currentProfile.contractsCompleted + 1L
                if (currentProfile.activeContractId == activeContract.id) {
                    profileUpdates["activeContractId"] = ""
                }
            }
            batch.update(userRef, profileUpdates)
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
