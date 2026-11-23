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
    val paintColorHex: String = "#1E5BB8",
    val liveryStyle: String = LiveryStyle.CLASSIC.name,
    val bullbarInstalled: Boolean = false,
    val roofLightbarInstalled: Boolean = false,
    val aeroSpoilerInstalled: Boolean = false,
    val chromeExhaustInstalled: Boolean = false,
    val truckDamagePercent: Long = 0L,
    val steeringMode: String = SteeringMode.WHEEL.name,
    val steeringSensitivity: Double = 1.0,
    val cameraMode: String = CameraMode.EXTERIOR_2D.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedSteeringMode(): SteeringMode =
        enumValues<SteeringMode>().firstOrNull { it.name == steeringMode } ?: SteeringMode.WHEEL

    fun resolvedCameraMode(): CameraMode = when (cameraMode) {
        CameraMode.EXTERIOR_2D.name, "CHASE" -> CameraMode.EXTERIOR_2D
        CameraMode.CAB_2D.name, "CAB" -> CameraMode.CAB_2D
        CameraMode.SIDE_2D.name -> CameraMode.SIDE_2D
        CameraMode.FRONT_2D.name, "HOOD", "TOPDOWN_2D" -> CameraMode.FRONT_2D
        else -> CameraMode.EXTERIOR_2D
    }

    fun resolvedLiveryStyle(): LiveryStyle =
        enumValues<LiveryStyle>().firstOrNull { it.name == liveryStyle } ?: LiveryStyle.CLASSIC

    fun toVisualConfig(truck: TruckSpec, trailerType: CargoTrailerType = truck.defaultTrailerType): TruckVisualConfig {
        val hex = if (paintColorHex.startsWith("#") && (paintColorHex.length == 7 || paintColorHex.length == 9)) {
            paintColorHex
        } else {
            truck.defaultPaintHex
        }
        return TruckVisualConfig(
            paintHex = hex,
            liveryStyle = resolvedLiveryStyle(),
            bullbarInstalled = bullbarInstalled,
            roofLightbarInstalled = roofLightbarInstalled,
            aeroSpoilerInstalled = aeroSpoilerInstalled,
            chromeExhaustInstalled = chromeExhaustInstalled,
            trailerType = trailerType
        )
    }

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

@Keep
@IgnoreExtraProperties
data class ConvoyRoom(
    @DocumentId val id: String = "",
    val roomCode: String = "",
    val roomName: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val routeId: String = "free_roam_world",
    val privateRoom: Boolean = false,
    val status: String = "OPEN",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ConvoyPlayer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val driverName: String = "",
    val truckId: String = "nordic_fh540",
    val paintColorHex: String = "#1E5BB8",
    val roadS: Double = 18.0,
    val laneOffset: Double = 2.15,
    val speedKmh: Long = 0L,
    val headlightsOn: Boolean = true,
    val hornActive: Boolean = false,
    val online: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ConvoyMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val senderName: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
