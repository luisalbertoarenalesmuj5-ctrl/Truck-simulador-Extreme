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
    val level: Long = 1L,
    val claimedMilestoneIds: List<String> = emptyList(),
    val activeContractId: String = "",
    val contractsCompleted: Long = 0L,
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
    companion object {
        const val XP_PER_LEVEL = 500L
        const val MAX_LEVEL = 500L

        fun levelForXp(xp: Long): Long =
            (1L + (xp.coerceAtLeast(0L) / XP_PER_LEVEL)).coerceIn(1L, MAX_LEVEL)
    }

    fun effectiveLevel(): Long = maxOf(level.coerceIn(1L, MAX_LEVEL), levelForXp(xp))

    fun xpIntoCurrentLevel(): Long = xp.coerceAtLeast(0L) % XP_PER_LEVEL

    fun xpForNextLevel(): Long = XP_PER_LEVEL

    fun xpRemainingToNextLevel(): Long = XP_PER_LEVEL - xpIntoCurrentLevel()

    fun levelProgressFraction(): Float =
        (xpIntoCurrentLevel().toFloat() / XP_PER_LEVEL.toFloat()).coerceIn(0f, 1f)

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

    fun driverRankTitle(): String {
        val lvl = effectiveLevel()
        return when {
            lvl >= 11L || xp >= 5000L -> "Leyenda del Asfalto"
            lvl >= 6L || xp >= 2500L -> "Capitán de Ruta"
            lvl >= 3L || xp >= 1000L -> "Transportista Experto"
            lvl >= 2L || xp >= 300L -> "Conductor Profesional"
            else -> "Novato de Carretera"
        }
    }
}

data class CareerMilestone(
    val id: String,
    val title: String,
    val description: String,
    val rewardXp: Long,
    val rewardCredits: Long,
    val requirementLabel: String,
    val isUnlocked: (DriverProfile) -> Boolean
)

data class CareerCertification(
    val id: String,
    val title: String,
    val description: String,
    val costCredits: Long,
    val grantXp: Long
)

object CareerCatalog {
    val milestones: List<CareerMilestone> = listOf(
        CareerMilestone(
            id = "starter_license",
            title = "Licencia Comercial Activa",
            description = "Activa tu expediente oficial en la nube y recibe tu bono inicial de carrera.",
            rewardXp = 250L,
            rewardCredits = 1500L,
            requirementLabel = "Perfil verificado",
            isUnlocked = { true }
        ),
        CareerMilestone(
            id = "first_haul",
            title = "Primera Carga Entregada",
            description = "Completa tu primera entrega de mercancía en cualquier ruta.",
            rewardXp = 350L,
            rewardCredits = 3000L,
            requirementLabel = "1 entrega",
            isUnlocked = { it.deliveriesCompleted >= 1L }
        ),
        CareerMilestone(
            id = "road_50km",
            title = "Explorador de Ruta",
            description = "Acumula al menos 50 km recorridos por autopistas y montañas.",
            rewardXp = 400L,
            rewardCredits = 4000L,
            requirementLabel = "50 km",
            isUnlocked = { it.totalDistanceKm >= 50.0 }
        ),
        CareerMilestone(
            id = "fleet_owner",
            title = "Dueño de Flota",
            description = "Desbloquea 2 o más tractocamiones en el Garaje.",
            rewardXp = 500L,
            rewardCredits = 5500L,
            requirementLabel = "2 camiones",
            isUnlocked = { it.unlockedTruckIds.size >= 2 }
        ),
        CareerMilestone(
            id = "veteran_5_hauls",
            title = "Veterano de Autopista",
            description = "Completa 5 entregas comerciales sin perder tu licencia.",
            rewardXp = 750L,
            rewardCredits = 8000L,
            requirementLabel = "5 entregas",
            isUnlocked = { it.deliveriesCompleted >= 5L }
        ),
        CareerMilestone(
            id = "master_250km",
            title = "Leyenda Transcontinental",
            description = "Supera los 250 km recorridos en tu carrera profesional.",
            rewardXp = 1200L,
            rewardCredits = 15000L,
            requirementLabel = "250 km",
            isUnlocked = { it.totalDistanceKm >= 250.0 }
        )
    )

    val certifications: List<CareerCertification> = listOf(
        CareerCertification(
            id = "cert_eco_drive",
            title = "Curso Eco-Conducción",
            description = "Optimiza consumo diésel y frenado motor. Sube tu experiencia al instante.",
            costCredits = 2000L,
            grantXp = 300L
        ),
        CareerCertification(
            id = "cert_night_mountain",
            title = "Especialidad Montaña y Niebla",
            description = "Acreditación avanzada para rutas de tormenta, túneles y alta pendiente.",
            costCredits = 4500L,
            grantXp = 650L
        ),
        CareerCertification(
            id = "cert_heavy_convoy",
            title = "Maestría Convoy Pesado",
            description = "Certificación superior para cargas especiales y liderazgo de convoy.",
            costCredits = 8000L,
            grantXp = 1200L
        )
    )
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

enum class ContractDifficulty(
    val label: String,
    val rewardMultiplier: Double,
    val xpMultiplier: Double,
    val minLevel: Long,
    val lengthScale: Float
) {
    FACIL("Fácil", 1.0, 1.0, 1L, 0.85f),
    MEDIA("Media", 1.40, 1.35, 1L, 1.0f),
    ALTA("Alta", 1.90, 1.80, 2L, 1.15f),
    EXTREMA("Extrema", 2.55, 2.40, 3L, 1.30f)
}

enum class ContractStatus(val label: String) {
    AVAILABLE("Disponible"),
    ACTIVE("En Curso"),
    COMPLETED("Completado")
}

@Keep
@IgnoreExtraProperties
data class DeliveryContract(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val companyName: String = "",
    val cargoName: String = "",
    val cargoTons: Long = 16L,
    val trailerType: String = CargoTrailerType.REEFER_BOX.name,
    val originCity: String = "",
    val destinationCity: String = "",
    val routeId: String = "alpine_pass",
    val distanceKm: Double = 50.0,
    val difficulty: String = ContractDifficulty.FACIL.name,
    val rewardCredits: Long = 8000L,
    val rewardXp: Long = 250L,
    val bonusPerfectCredits: Long = 1200L,
    val requiredLevel: Long = 1L,
    val status: String = ContractStatus.AVAILABLE.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedDifficulty(): ContractDifficulty =
        enumValues<ContractDifficulty>().firstOrNull { it.name == difficulty } ?: ContractDifficulty.FACIL

    fun resolvedStatus(): ContractStatus =
        enumValues<ContractStatus>().firstOrNull { it.name == status } ?: ContractStatus.AVAILABLE

    fun resolvedTrailerType(): CargoTrailerType =
        enumValues<CargoTrailerType>().firstOrNull { it.name == trailerType } ?: CargoTrailerType.REEFER_BOX

    fun toRouteSpec(): RouteSpec {
        val base = RouteCatalog.byId(routeId)
        val diff = resolvedDifficulty()
        val adjustedLength = (base.simLengthMeters * diff.lengthScale).coerceIn(1600f, 4400f)
        return base.copy(
            title = title,
            originCity = originCity,
            destinationCity = destinationCity,
            cargoName = "$cargoName ($companyName)",
            cargoTons = cargoTons.toInt(),
            trailerType = resolvedTrailerType(),
            simLengthMeters = adjustedLength,
            displayDistanceKm = distanceKm,
            difficultyLabel = diff.label,
            baseRewardCredits = rewardCredits,
            isFreeRoam = false
        )
    }
}
