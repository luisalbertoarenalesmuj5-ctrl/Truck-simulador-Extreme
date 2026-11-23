package com.example.model

import kotlin.math.roundToLong
import kotlin.random.Random

object ContractGenerator {

    private data class CargoTemplate(
        val cargoName: String,
        val trailerType: CargoTrailerType,
        val baseTons: IntRange,
        val preferredRoutes: List<String>
    )

    private val companies = listOf(
        "TransAndina Logistics",
        "NorteCarga Express",
        "EuroFlete S.A.",
        "Pacífico Heavy Haul",
        "BioBosque Maderera",
        "PetroRuta ADR",
        "AgroSur Silos",
        "Valkiria Especiales"
    )

    private val cargoTemplates = listOf(
        CargoTemplate(
            cargoName = "Vacunas y Farmacéuticos",
            trailerType = CargoTrailerType.REEFER_BOX,
            baseTons = 11..17,
            preferredRoutes = listOf("alpine_pass", "pacific_coast", "agro_highway")
        ),
        CargoTemplate(
            cargoName = "Salmón Fresco de Exportación",
            trailerType = CargoTrailerType.REEFER_BOX,
            baseTons = 12..18,
            preferredRoutes = listOf("pacific_coast", "agro_highway")
        ),
        CargoTemplate(
            cargoName = "Diésel Marino Ultra Bajo Azufre",
            trailerType = CargoTrailerType.FUEL_TANKER,
            baseTons = 20..28,
            preferredRoutes = listOf("desert_night", "storm_summit", "pacific_coast")
        ),
        CargoTemplate(
            cargoName = "Queroseno de Aviación Jet-A1",
            trailerType = CargoTrailerType.FUEL_TANKER,
            baseTons = 22..30,
            preferredRoutes = listOf("desert_night", "storm_summit")
        ),
        CargoTemplate(
            cargoName = "Troncos de Pino Certificado",
            trailerType = CargoTrailerType.TIMBER_LOGS,
            baseTons = 18..26,
            preferredRoutes = listOf("timber_valley", "alpine_pass")
        ),
        CargoTemplate(
            cargoName = "Vigas Laminadas Estructurales",
            trailerType = CargoTrailerType.TIMBER_LOGS,
            baseTons = 19..25,
            preferredRoutes = listOf("timber_valley", "storm_summit")
        ),
        CargoTemplate(
            cargoName = "Excavadora Hidráulica Industrial",
            trailerType = CargoTrailerType.FLATBED_EXCAVATOR,
            baseTons = 20..30,
            preferredRoutes = listOf("alpine_pass", "storm_summit", "timber_valley")
        ),
        CargoTemplate(
            cargoName = "Generador Eléctrico CAT 26t",
            trailerType = CargoTrailerType.FLATBED_EXCAVATOR,
            baseTons = 22..28,
            preferredRoutes = listOf("alpine_pass", "desert_night", "storm_summit")
        ),
        CargoTemplate(
            cargoName = "Flota de SUV Eléctricos",
            trailerType = CargoTrailerType.CAR_CARRIER,
            baseTons = 14..20,
            preferredRoutes = listOf("pacific_coast", "agro_highway")
        ),
        CargoTemplate(
            cargoName = "Lote Camionetas 4x4 Comerciales",
            trailerType = CargoTrailerType.CAR_CARRIER,
            baseTons = 15..21,
            preferredRoutes = listOf("pacific_coast", "desert_night")
        ),
        CargoTemplate(
            cargoName = "Rotor de Turbina Eólica",
            trailerType = CargoTrailerType.WIND_TURBINE,
            baseTons = 26..36,
            preferredRoutes = listOf("storm_summit", "alpine_pass")
        ),
        CargoTemplate(
            cargoName = "Transformador Subestación 34t",
            trailerType = CargoTrailerType.WIND_TURBINE,
            baseTons = 28..38,
            preferredRoutes = listOf("storm_summit", "desert_night")
        ),
        CargoTemplate(
            cargoName = "Trigo Candeal de Exportación",
            trailerType = CargoTrailerType.AGRI_GRAIN,
            baseTons = 16..24,
            preferredRoutes = listOf("agro_highway", "pacific_coast")
        ),
        CargoTemplate(
            cargoName = "Soya Granel de Silo Central",
            trailerType = CargoTrailerType.AGRI_GRAIN,
            baseTons = 18..25,
            preferredRoutes = listOf("agro_highway", "timber_valley")
        )
    )

    private val extraOriginCities = listOf(
        "Valparaíso", "Puerto Montt", "Mendoza", "Córdoba",
        "Santiago Norte", "Antofagasta", "San Carlos", "Bahía Blanca"
    )

    private val extraDestinationCities = listOf(
        "Puerto Sur", "Metrópoli Este", "Parque Industrial",
        "Terminal Marítima", "Centro Logístico", "Paso Fronterizo",
        "Refinería Central", "Zona Franca"
    )

    fun generateBatch(
        userId: String,
        driverLevel: Long,
        count: Int = 5,
        random: Random = Random.Default
    ): List<DeliveryContract> {
        val safeCount = count.coerceIn(1, 8)
        // Always include varied difficulty tiers; ensure at least 2 contracts match current driverLevel
        val tiers = mutableListOf(
            ContractDifficulty.FACIL,
            ContractDifficulty.MEDIA,
            ContractDifficulty.ALTA,
            ContractDifficulty.EXTREMA
        )
        while (tiers.size < safeCount) {
            val extra = when (random.nextInt(100)) {
                in 0..29 -> ContractDifficulty.FACIL
                in 30..64 -> ContractDifficulty.MEDIA
                in 65..84 -> ContractDifficulty.ALTA
                else -> ContractDifficulty.EXTREMA
            }
            tiers.add(extra)
        }
        tiers.shuffle(random)

        return tiers.take(safeCount).map { difficulty ->
            generateSingle(
                userId = userId,
                driverLevel = driverLevel,
                difficulty = difficulty,
                random = random
            )
        }
    }

    fun generateSingle(
        userId: String,
        driverLevel: Long,
        difficulty: ContractDifficulty,
        random: Random = Random.Default
    ): DeliveryContract {
        val template = selectTemplateForDifficulty(difficulty, random)
        val baseRouteId = template.preferredRoutes.random(random)
        val baseRoute = RouteCatalog.byId(baseRouteId)
        val company = companies.random(random)

        val origin = if (random.nextBoolean()) baseRoute.originCity else extraOriginCities.random(random)
        val destination = if (random.nextBoolean()) baseRoute.destinationCity else extraDestinationCities.random(random)
        val finalDest = if (destination == origin) "Terminal Logística Central" else destination

        val distanceJitter = 0.88 + random.nextDouble() * 0.34
        val distanceKm = ((baseRoute.displayDistanceKm * difficulty.lengthScale * distanceJitter) * 10.0)
            .roundToLong()
            .coerceIn(280L, 2800L) / 10.0

        val tons = (template.baseTons.random(random) + when (difficulty) {
            ContractDifficulty.FACIL -> -2
            ContractDifficulty.MEDIA -> 0
            ContractDifficulty.ALTA -> 2
            ContractDifficulty.EXTREMA -> 5
        }).coerceIn(8, 52).toLong()

        val variance = 0.92 + random.nextDouble() * 0.20
        val levelBonus = 1.0 + ((driverLevel - 1L).coerceIn(0L, 20L) * 0.04)
        val rawCredits = (baseRoute.baseRewardCredits * difficulty.rewardMultiplier * variance * levelBonus)
            .roundToLong()
            .coerceIn(5000L, 250_000L)
        val rewardCredits = ((rawCredits + 50L) / 100L) * 100L

        val rewardXp = ((distanceKm * 5.2 + tons * 7.0) * difficulty.xpMultiplier * variance)
            .roundToLong()
            .coerceIn(140L, 12_000L)

        val bonusPerfectCredits = (rewardCredits * when (difficulty) {
            ContractDifficulty.FACIL -> 0.14
            ContractDifficulty.MEDIA -> 0.18
            ContractDifficulty.ALTA -> 0.22
            ContractDifficulty.EXTREMA -> 0.28
        }).roundToLong().let { ((it + 50L) / 100L) * 100L }.coerceIn(500L, 80_000L)

        val shortCargo = template.cargoName.take(36)
        val title = "${difficulty.label.uppercase()}: $shortCargo".take(80)

        return DeliveryContract(
            userId = userId,
            title = title,
            companyName = company.take(50),
            cargoName = template.cargoName.take(80),
            cargoTons = tons,
            trailerType = template.trailerType.name,
            originCity = origin.take(40),
            destinationCity = finalDest.take(40),
            routeId = baseRoute.id,
            distanceKm = distanceKm,
            difficulty = difficulty.name,
            rewardCredits = rewardCredits,
            rewardXp = rewardXp,
            bonusPerfectCredits = bonusPerfectCredits,
            requiredLevel = difficulty.minLevel,
            status = ContractStatus.AVAILABLE.name
        )
    }

    private fun selectTemplateForDifficulty(
        difficulty: ContractDifficulty,
        random: Random
    ): CargoTemplate {
        val preferred = when (difficulty) {
            ContractDifficulty.FACIL -> cargoTemplates.filter {
                it.trailerType == CargoTrailerType.REEFER_BOX ||
                    it.trailerType == CargoTrailerType.AGRI_GRAIN ||
                    it.trailerType == CargoTrailerType.CAR_CARRIER
            }
            ContractDifficulty.MEDIA -> cargoTemplates.filter {
                it.trailerType == CargoTrailerType.REEFER_BOX ||
                    it.trailerType == CargoTrailerType.CAR_CARRIER ||
                    it.trailerType == CargoTrailerType.TIMBER_LOGS ||
                    it.trailerType == CargoTrailerType.AGRI_GRAIN
            }
            ContractDifficulty.ALTA -> cargoTemplates.filter {
                it.trailerType == CargoTrailerType.FUEL_TANKER ||
                    it.trailerType == CargoTrailerType.TIMBER_LOGS ||
                    it.trailerType == CargoTrailerType.FLATBED_EXCAVATOR
            }
            ContractDifficulty.EXTREMA -> cargoTemplates.filter {
                it.trailerType == CargoTrailerType.WIND_TURBINE ||
                    it.trailerType == CargoTrailerType.FUEL_TANKER ||
                    it.trailerType == CargoTrailerType.FLATBED_EXCAVATOR
            }
        }
        return (if (preferred.isNotEmpty()) preferred else cargoTemplates).random(random)
    }
}
