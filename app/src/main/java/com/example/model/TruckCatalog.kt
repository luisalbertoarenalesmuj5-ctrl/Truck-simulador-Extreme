package com.example.model

enum class TruckCabStyle {
    EURO_CABOVER,
    AMERICAN_CONVENTIONAL
}

enum class SteeringMode(val label: String, val description: String) {
    WHEEL("Volante Táctil", "Volante progresivo de 540° con centrado automático"),
    BUTTONS("Botones Pro", "Botones de dirección con filtrado anti-brusquedad"),
    TILT("Giroscopio", "Inclinación del dispositivo con zona muerta calibrable")
}

enum class CameraMode(val label: String) {
    SIDE_2D("Lateral 2D"),
    TOPDOWN_2D("Aérea 2D"),
    CAB_2D("Cabina 2D")
}

enum class WeatherCondition(val label: String) {
    CLEAR("Despejado"),
    RAIN("Lluvia"),
    FOG("Niebla"),
    STORM("Tormenta")
}

enum class EnvironmentTheme(
    val label: String,
    val weatherLabel: String,
    val initialTimeOfDay: Float,
    val defaultWeather: WeatherCondition
) {
    ALPINE_DAY("Sierra Alpina", "Día Despejado", 0.36f, WeatherCondition.CLEAR),
    COASTAL_SUNSET("Costa del Pacífico", "Atardecer Dorado", 0.72f, WeatherCondition.CLEAR),
    DESERT_NIGHT("Cañón Nocturno", "Noche Estrellada", 0.94f, WeatherCondition.CLEAR),
    STORM_SUMMIT("Cumbre Extrema", "Lluvia y Niebla", 0.58f, WeatherCondition.STORM)
}

data class TruckSpec(
    val id: String,
    val name: String,
    val subtitle: String,
    val cabStyle: TruckCabStyle,
    val horsepower: Int,
    val torqueNm: Int,
    val maxSpeedKmh: Int,
    val brakingPower: Float,
    val fuelCapacityLiters: Float,
    val priceCredits: Long,
    val primaryColorRgb: FloatArray,
    val accentColorRgb: FloatArray
)

data class RouteSpec(
    val id: String,
    val title: String,
    val originCity: String,
    val destinationCity: String,
    val cargoName: String,
    val cargoTons: Int,
    val simLengthMeters: Float,
    val displayDistanceKm: Double,
    val difficultyLabel: String,
    val curveIntensity: Float,
    val hillIntensity: Float,
    val trafficDensity: Float,
    val speedLimitKmh: Int,
    val baseRewardCredits: Long,
    val environment: EnvironmentTheme
)

object TruckCatalog {
    val trucks: List<TruckSpec> = listOf(
        TruckSpec(
            id = "nordic_fh540",
            name = "Nordic FH-540",
            subtitle = "Tractora Europea 6x4 • Motor D13 Turbo",
            cabStyle = TruckCabStyle.EURO_CABOVER,
            horsepower = 540,
            torqueNm = 2600,
            maxSpeedKmh = 112,
            brakingPower = 1.0f,
            fuelCapacityLiters = 900f,
            priceCredits = 0L,
            primaryColorRgb = floatArrayOf(0.14f, 0.40f, 0.72f),
            accentColorRgb = floatArrayOf(0.88f, 0.91f, 0.95f)
        ),
        TruckSpec(
            id = "valkyrie_s730",
            name = "Valkyrie S-730 V8",
            subtitle = "Cabina Alta V8 • Freno Retarder Hidráulico",
            cabStyle = TruckCabStyle.EURO_CABOVER,
            horsepower = 730,
            torqueNm = 3500,
            maxSpeedKmh = 126,
            brakingPower = 1.18f,
            fuelCapacityLiters = 1100f,
            priceCredits = 45000L,
            primaryColorRgb = floatArrayOf(0.76f, 0.14f, 0.16f),
            accentColorRgb = floatArrayOf(0.98f, 0.75f, 0.20f)
        ),
        TruckSpec(
            id = "titan_579",
            name = "Titan 579 Aero",
            subtitle = "Tractoamericano Aerodinámico • Eje Tándem",
            cabStyle = TruckCabStyle.AMERICAN_CONVENTIONAL,
            horsepower = 605,
            torqueNm = 2850,
            maxSpeedKmh = 120,
            brakingPower = 1.10f,
            fuelCapacityLiters = 1200f,
            priceCredits = 75000L,
            primaryColorRgb = floatArrayOf(0.90f, 0.45f, 0.06f),
            accentColorRgb = floatArrayOf(0.15f, 0.17f, 0.21f)
        ),
        TruckSpec(
            id = "colossus_w900",
            name = "Colossus W900L",
            subtitle = "Clásico Capó Largo • Escapes Cromados V8",
            cabStyle = TruckCabStyle.AMERICAN_CONVENTIONAL,
            horsepower = 800,
            torqueNm = 3900,
            maxSpeedKmh = 135,
            brakingPower = 1.28f,
            fuelCapacityLiters = 1400f,
            priceCredits = 120000L,
            primaryColorRgb = floatArrayOf(0.12f, 0.14f, 0.18f),
            accentColorRgb = floatArrayOf(0.95f, 0.48f, 0.08f)
        )
    )

    fun byId(id: String): TruckSpec = trucks.firstOrNull { it.id == id } ?: trucks.first()
}

object RouteCatalog {
    val routes: List<RouteSpec> = listOf(
        RouteSpec(
            id = "alpine_pass",
            title = "Paso Alpino — Maquinaria Pesada",
            originCity = "Innsbruck",
            destinationCity = "Bolzano",
            cargoName = "Excavadora CAT 18t",
            cargoTons = 18,
            simLengthMeters = 1800f,
            displayDistanceKm = 45.0,
            difficultyLabel = "Media",
            curveIntensity = 0.85f,
            hillIntensity = 1.10f,
            trafficDensity = 0.7f,
            speedLimitKmh = 85,
            baseRewardCredits = 8500L,
            environment = EnvironmentTheme.ALPINE_DAY
        ),
        RouteSpec(
            id = "pacific_coast",
            title = "Ruta 101 — Carga Refrigerada",
            originCity = "Monterey",
            destinationCity = "San Luis",
            cargoName = "Contenedor Térmico 14t",
            cargoTons = 14,
            simLengthMeters = 2200f,
            displayDistanceKm = 60.0,
            difficultyLabel = "Media-Alta",
            curveIntensity = 1.10f,
            hillIntensity = 0.70f,
            trafficDensity = 1.0f,
            speedLimitKmh = 90,
            baseRewardCredits = 12000L,
            environment = EnvironmentTheme.COASTAL_SUNSET
        ),
        RouteSpec(
            id = "desert_night",
            title = "Cañón Nocturno — Cisterna ADR",
            originCity = "Flagstaff",
            destinationCity = "Gallup",
            cargoName = "Cisterna Combustible 24t",
            cargoTons = 24,
            simLengthMeters = 2600f,
            displayDistanceKm = 75.0,
            difficultyLabel = "Difícil",
            curveIntensity = 1.00f,
            hillIntensity = 0.90f,
            trafficDensity = 0.85f,
            speedLimitKmh = 90,
            baseRewardCredits = 18500L,
            environment = EnvironmentTheme.DESERT_NIGHT
        ),
        RouteSpec(
            id = "storm_summit",
            title = "Cumbre Extrema — Turbina Eólica",
            originCity = "Oviedo",
            destinationCity = "León",
            cargoName = "Rotor Eólico Especial 28t",
            cargoTons = 28,
            simLengthMeters = 3000f,
            displayDistanceKm = 95.0,
            difficultyLabel = "Extrema",
            curveIntensity = 1.35f,
            hillIntensity = 1.35f,
            trafficDensity = 1.15f,
            speedLimitKmh = 75,
            baseRewardCredits = 26000L,
            environment = EnvironmentTheme.STORM_SUMMIT
        )
    )

    fun byId(id: String): RouteSpec = routes.firstOrNull { it.id == id } ?: routes.first()
}
