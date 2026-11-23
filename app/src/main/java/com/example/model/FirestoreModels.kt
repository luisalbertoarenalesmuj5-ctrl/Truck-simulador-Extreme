package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class DriverProfile(
    val userId: String = "",
    val driverName: String = "Conductor Pro",
    val credits: Long = 25000L,
    val totalDistanceKm: Double = 0.0,
    val deliveriesCompleted: Long = 0L,
    val xp: Long = 0L,
    val selectedTruckId: String = "nordic_fh540",
    val unlockedTruckIds: List<String> = listOf("nordic_fh540"),
    val steeringMode: String = SteeringMode.WHEEL.name,
    val steeringSensitivity: Double = 1.0,
    val cameraMode: String = CameraMode.CHASE.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedSteeringMode(): SteeringMode =
        enumValues<SteeringMode>().firstOrNull { it.name == steeringMode } ?: SteeringMode.WHEEL

    fun resolvedCameraMode(): CameraMode =
        enumValues<CameraMode>().firstOrNull { it.name == cameraMode } ?: CameraMode.CHASE

    fun driverRankTitle(): String = when {
        xp >= 5000 -> "Leyenda del Asfalto"
        xp >= 2500 -> "Capitán de Ruta"
        xp >= 1000 -> "Transportista Experto"
        xp >= 300 -> "Conductor Profesional"
        else -> "Novato de Carretera"
    }
}

@Keep
@IgnoreExtraProperties
data class DeliveryRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val routeId: String = "",
    val routeName: String = "",
    val cargoName: String = "",
    val truckId: String = "nordic_fh540",
    val distanceKm: Double = 0.0,
    val earnedCredits: Long = 0L,
    val damagePercent: Long = 0L,
    val durationSeconds: Long = 0L,
    val perfectDelivery: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)
