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
    EXTERIOR_2D("Exterior 2D"),
    CAB_2D("Cabina 2D"),
    SIDE_2D("Lateral 2D"),
    FRONT_2D("Frontal 2D")
}

enum class LiveryStyle(val label: String, val priceCredits: Long) {
    CLASSIC("Clásica", 0L),
    RACING("Carreras", 1800L),
    BOLT("Rayo Pro", 2600L),
    EXECUTIVE("Ejecutiva", 3500L)
}

enum class CargoTrailerType(val label: String) {
    REEFER_BOX("Caja Refrigerada"),
    FUEL_TANKER("Cisterna ADR"),
    TIMBER_LOGS("Remolque Maderero"),
    FLATBED_EXCAVATOR("Plataforma Pesada"),
    CAR_CARRIER("Portavehículos"),
    WIND_TURBINE("Carga Especial"),
    AGRI_GRAIN("Tolva Agrícola")
}

data class PaintPreset(
    val id: String,
    val name: String,
    val hex: String,
    val priceCredits: Long
)

data class TruckVisualConfig(
    val paintHex: String = "#1E5BB8",
    val liveryStyle: LiveryStyle = LiveryStyle.CLASSIC,
    val bullbarInstalled: Boolean = false,
    val roofLightbarInstalled: Boolean = false,
    val aeroSpoilerInstalled: Boolean = false,
    val chromeExhaustInstalled: Boolean = false,
    val trailerType: CargoTrailerType = CargoTrailerType.REEFER_BOX
)

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
    ALPINE_DAY("Sierra y Volcán", "Día Despejado", 0.36f, WeatherCondition.CLEAR),
    COASTAL_SUNSET("Costa y Puentes", "Atardecer Dorado", 0.72f, WeatherCondition.CLEAR),
    DESERT_NIGHT("Cañón Nocturno", "Noche Estrellada", 0.94f, WeatherCondition.CLEAR),
    STORM_SUMMIT("Cumbre y Bosque", "Lluvia y Niebla", 0.58f, WeatherCondition.STORM),
    OPEN_VALLEY("Mundo Abierto", "Ciclo Completo", 0.42f, WeatherCondition.CLEAR)
}

data class TruckSpec(
    val id: String,
    val name: String,
    val subtitle: String,
    val roleTag: String,
    val cabStyle: TruckCabStyle,
    val defaultTrailerType: CargoTrailerType,
    val horsepower: Int,
    val torqueNm: Int,
    val maxSpeedKmh: Int,
    val brakingPower: Float,
    val fuelCapacityLiters: Float,
    val priceCredits: Long,
    val defaultPaintHex: String,
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
    val trailerType: CargoTrailerType,
    val simLengthMeters: Float,
    val displayDistanceKm: Double,
    val difficultyLabel: String,
    val curveIntensity: Float,
    val hillIntensity: Float,
    val trafficDensity: Float,
    val speedLimitKmh: Int,
    val baseRewardCredits: Long,
    val tollFeeCredits: Long,
    val isFreeRoam: Boolean = false,
    val biomeSummary: String,
    val environment: EnvironmentTheme
)

object WorkshopCatalog {
    val paints: List<PaintPreset> = listOf(
        PaintPreset("cobalt", "Azul Nórdico", "#1E5BB8", 0L),
        PaintPreset("crimson", "Rojo Valkiria", "#C22429", 0L),
        PaintPreset("amber", "Ámbar Diésel", "#E6730F", 1500L),
        PaintPreset("obsidian", "Negro Carbón", "#1D2430", 1800L),
        PaintPreset("emerald", "Verde Forestal", "#1F6E4A", 2200L),
        PaintPreset("pearl", "Blanco Perla", "#E2E8F0", 2500L),
        PaintPreset("purple", "Púrpura Convoy", "#6D28D9", 3200L),
        PaintPreset("gold", "Oro Dorado", "#D97706", 4500L)
    )

    const val BULLBAR_PRICE = 2400L
    const val LIGHTBAR_PRICE = 1900L
    const val SPOILER_PRICE = 2200L
    const val EXHAUST_PRICE = 2800L
    const val REPAIR_COST_PER_PERCENT = 55L
}

object TruckCatalog {
    val trucks: List<TruckSpec> = listOf(
        TruckSpec(
            id = "nordic_fh540",
            name = "Nordic FH-540",
            subtitle = "Tractora Europea 6x4 • Motor D13 Turbo",
            roleTag = "Carga General",
            cabStyle = TruckCabStyle.EURO_CABOVER,
            defaultTrailerType = CargoTrailerType.REEFER_BOX,
            horsepower = 540,
            torqueNm = 2600,
            maxSpeedKmh = 112,
            brakingPower = 1.0f,
            fuelCapacityLiters = 900f,
            priceCredits = 0L,
            defaultPaintHex = "#1E5BB8",
            primaryColorRgb = floatArrayOf(0.14f, 0.40f, 0.72f),
            accentColorRgb = floatArrayOf(0.88f, 0.91f, 0.95f)
        ),
        TruckSpec(
            id = "valkyrie_s730",
            name = "Valkyrie S-730 V8",
            subtitle = "Cabina Alta V8 • Retarder Hidráulico",
            roleTag = "Camión Pesado",
            cabStyle = TruckCabStyle.EURO_CABOVER,
            defaultTrailerType = CargoTrailerType.WIND_TURBINE,
            horsepower = 730,
            torqueNm = 3500,
            maxSpeedKmh = 126,
            brakingPower = 1.18f,
            fuelCapacityLiters = 1100f,
            priceCredits = 45000L,
            defaultPaintHex = "#C22429",
            primaryColorRgb = floatArrayOf(0.76f, 0.14f, 0.16f),
            accentColorRgb = floatArrayOf(0.98f, 0.75f, 0.20f)
        ),
        TruckSpec(
            id = "titan_579",
            name = "Titan 579 Aero",
            subtitle = "Tractoamericano Aerodinámico • Eje Tándem",
            roleTag = "Larga Distancia",
            cabStyle = TruckCabStyle.AMERICAN_CONVENTIONAL,
            defaultTrailerType = CargoTrailerType.REEFER_BOX,
            horsepower = 605,
            torqueNm = 2850,
            maxSpeedKmh = 120,
            brakingPower = 1.10f,
            fuelCapacityLiters = 1200f,
            priceCredits = 65000L,
            defaultPaintHex = "#E6730F",
            primaryColorRgb = floatArrayOf(0.90f, 0.45f, 0.06f),
            accentColorRgb = floatArrayOf(0.15f, 0.17f, 0.21f)
        ),
        TruckSpec(
            id = "colossus_w900",
            name = "Colossus W900L",
            subtitle = "Clásico Capó Largo • Escapes Cromados V8",
            roleTag = "Peso Extremo",
            cabStyle = TruckCabStyle.AMERICAN_CONVENTIONAL,
            defaultTrailerType = CargoTrailerType.FLATBED_EXCAVATOR,
            horsepower = 800,
            torqueNm = 3900,
            maxSpeedKmh = 135,
            brakingPower = 1.28f,
            fuelCapacityLiters = 1400f,
            priceCredits = 110000L,
            defaultPaintHex = "#1D2430",
            primaryColorRgb = floatArrayOf(0.12f, 0.14f, 0.18f),
            accentColorRgb = floatArrayOf(0.95f, 0.48f, 0.08f)
        ),
        TruckSpec(
            id = "bison_timber",
            name = "Bison Timber 6x6",
            subtitle = "Maderero Todo Terreno • Grúa Forestal",
            roleTag = "Madera / Bosque",
            cabStyle = TruckCabStyle.AMERICAN_CONVENTIONAL,
            defaultTrailerType = CargoTrailerType.TIMBER_LOGS,
            horsepower = 680,
            torqueNm = 3350,
            maxSpeedKmh = 115,
            brakingPower = 1.22f,
            fuelCapacityLiters = 1150f,
            priceCredits = 58000L,
            defaultPaintHex = "#1F6E4A",
            primaryColorRgb = floatArrayOf(0.12f, 0.43f, 0.29f),
            accentColorRgb = floatArrayOf(0.96f, 0.72f, 0.18f)
        ),
        TruckSpec(
            id = "atlas_tanker",
            name = "Atlas ADR-620",
            subtitle = "Tractora Cisterna • Frenado ABS de Alta Seguridad",
            roleTag = "Cisterna ADR",
            cabStyle = TruckCabStyle.EURO_CABOVER,
            defaultTrailerType = CargoTrailerType.FUEL_TANKER,
            horsepower = 620,
            torqueNm = 3000,
            maxSpeedKmh = 118,
            brakingPower = 1.26f,
            fuelCapacityLiters = 1250f,
            priceCredits = 52000L,
            defaultPaintHex = "#E2E8F0",
            primaryColorRgb = floatArrayOf(0.84f, 0.87f, 0.91f),
            accentColorRgb = floatArrayOf(0.92f, 0.34f, 0.08f)
        ),
        TruckSpec(
            id = "kodiak_flatbed",
            name = "Kodiak 8x4 Heavy",
            subtitle = "Chasis Plataforma Reforzado • Eje Cuádruple",
            roleTag = "Plataforma 8x4",
            cabStyle = TruckCabStyle.AMERICAN_CONVENTIONAL,
            defaultTrailerType = CargoTrailerType.FLATBED_EXCAVATOR,
            horsepower = 760,
            torqueNm = 3700,
            maxSpeedKmh = 124,
            brakingPower = 1.30f,
            fuelCapacityLiters = 1350f,
            priceCredits = 88000L,
            defaultPaintHex = "#D97706",
            primaryColorRgb = floatArrayOf(0.85f, 0.47f, 0.02f),
            accentColorRgb = floatArrayOf(0.11f, 0.14f, 0.20f)
        ),
        TruckSpec(
            id = "carrier_trans9",
            name = "Carrier Trans-9",
            subtitle = "Especializado Portavehículos • Doble Cubierta",
            roleTag = "Portavehículos",
            cabStyle = TruckCabStyle.EURO_CABOVER,
            defaultTrailerType = CargoTrailerType.CAR_CARRIER,
            horsepower = 640,
            torqueNm = 3100,
            maxSpeedKmh = 122,
            brakingPower = 1.20f,
            fuelCapacityLiters = 1100f,
            priceCredits = 72000L,
            defaultPaintHex = "#6D28D9",
            primaryColorRgb = floatArrayOf(0.43f, 0.16f, 0.85f),
            accentColorRgb = floatArrayOf(0.95f, 0.80f, 0.22f)
        )
    )

    fun byId(id: String): TruckSpec = trucks.firstOrNull { it.id == id } ?: trucks.first()
}

object RouteCatalog {
    val routes: List<RouteSpec> = listOf(
        RouteSpec(
            id = "free_roam_world",
            title = "Mundo Abierto — Corredor Libre",
            originCity = "Puerto Bahía",
            destinationCity = "Metrópoli Central",
            cargoName = "Ruta Libre Sin Límite • Todos los Biomas",
            cargoTons = 16,
            trailerType = CargoTrailerType.REEFER_BOX,
            simLengthMeters = 4800f,
            displayDistanceKm = 160.0,
            difficultyLabel = "Libre",
            curveIntensity = 0.95f,
            hillIntensity = 1.05f,
            trafficDensity = 1.1f,
            speedLimitKmh = 95,
            baseRewardCredits = 24000L,
            tollFeeCredits = 45L,
            isFreeRoam = true,
            biomeSummary = "Autopista • Puentes • Túnel • Volcán • Granjas • Taller • Rotondas",
            environment = EnvironmentTheme.OPEN_VALLEY
        ),
        RouteSpec(
            id = "alpine_pass",
            title = "Paso del Volcán — Maquinaria Pesada",
            originCity = "Innsbruck",
            destinationCity = "Bolzano",
            cargoName = "Excavadora CAT 18t (Plataforma)",
            cargoTons = 18,
            trailerType = CargoTrailerType.FLATBED_EXCAVATOR,
            simLengthMeters = 2000f,
            displayDistanceKm = 52.0,
            difficultyLabel = "Media",
            curveIntensity = 0.90f,
            hillIntensity = 1.15f,
            trafficDensity = 0.85f,
            speedLimitKmh = 85,
            baseRewardCredits = 10500L,
            tollFeeCredits = 35L,
            biomeSummary = "Montañas • Volcán Nevado • Puente • Túnel",
            environment = EnvironmentTheme.ALPINE_DAY
        ),
        RouteSpec(
            id = "pacific_coast",
            title = "Ruta Costera — Portavehículos",
            originCity = "Monterey",
            destinationCity = "San Luis",
            cargoName = "Flota de 3 Autos Sedán (Portavehículos)",
            cargoTons = 15,
            trailerType = CargoTrailerType.CAR_CARRIER,
            simLengthMeters = 2300f,
            displayDistanceKm = 64.0,
            difficultyLabel = "Media-Alta",
            curveIntensity = 1.10f,
            hillIntensity = 0.72f,
            trafficDensity = 1.05f,
            speedLimitKmh = 90,
            baseRewardCredits = 14500L,
            tollFeeCredits = 40L,
            biomeSummary = "Puente Costero • Peaje • Rotonda • Granjas",
            environment = EnvironmentTheme.COASTAL_SUNSET
        ),
        RouteSpec(
            id = "timber_valley",
            title = "Bosque Profundo — Carga de Madera",
            originCity = "Valdivia",
            destinationCity = "Aserradero Sur",
            cargoName = "Troncos de Pino 22t (Maderero)",
            cargoTons = 22,
            trailerType = CargoTrailerType.TIMBER_LOGS,
            simLengthMeters = 2500f,
            displayDistanceKm = 70.0,
            difficultyLabel = "Alta",
            curveIntensity = 1.18f,
            hillIntensity = 1.10f,
            trafficDensity = 0.95f,
            speedLimitKmh = 80,
            baseRewardCredits = 17200L,
            tollFeeCredits = 30L,
            biomeSummary = "Camino Rural • Bosque Denso • Puente de Acero • Taller",
            environment = EnvironmentTheme.ALPINE_DAY
        ),
        RouteSpec(
            id = "desert_night",
            title = "Cañón Nocturno — Cisterna ADR",
            originCity = "Flagstaff",
            destinationCity = "Gallup",
            cargoName = "Cisterna Diésel 24t (ADR)",
            cargoTons = 24,
            trailerType = CargoTrailerType.FUEL_TANKER,
            simLengthMeters = 2600f,
            displayDistanceKm = 78.0,
            difficultyLabel = "Difícil",
            curveIntensity = 1.00f,
            hillIntensity = 0.90f,
            trafficDensity = 0.90f,
            speedLimitKmh = 90,
            baseRewardCredits = 19800L,
            tollFeeCredits = 50L,
            biomeSummary = "Autopista Nocturna • Túnel • Zona Industrial • Semáforos",
            environment = EnvironmentTheme.DESERT_NIGHT
        ),
        RouteSpec(
            id = "agro_highway",
            title = "Valle Agrícola — Granos y Silos",
            originCity = "Santa Fe",
            destinationCity = "Rosario Puerto",
            cargoName = "Tolva de Trigo 20t (Agrícola)",
            cargoTons = 20,
            trailerType = CargoTrailerType.AGRI_GRAIN,
            simLengthMeters = 2200f,
            displayDistanceKm = 58.0,
            difficultyLabel = "Media",
            curveIntensity = 0.80f,
            hillIntensity = 0.65f,
            trafficDensity = 1.0f,
            speedLimitKmh = 90,
            baseRewardCredits = 13200L,
            tollFeeCredits = 35L,
            biomeSummary = "Zona Agrícola • Cruces con Semáforo • Estacionamiento",
            environment = EnvironmentTheme.COASTAL_SUNSET
        ),
        RouteSpec(
            id = "storm_summit",
            title = "Cumbre Extrema — Turbina Eólica",
            originCity = "Oviedo",
            destinationCity = "León",
            cargoName = "Rotor Eólico Especial 28t",
            cargoTons = 28,
            trailerType = CargoTrailerType.WIND_TURBINE,
            simLengthMeters = 3000f,
            displayDistanceKm = 95.0,
            difficultyLabel = "Extrema",
            curveIntensity = 1.35f,
            hillIntensity = 1.35f,
            trafficDensity = 1.15f,
            speedLimitKmh = 75,
            baseRewardCredits = 27500L,
            tollFeeCredits = 60L,
            biomeSummary = "Tormenta • Volcán • Túnel Iluminado • Puerto Industrial",
            environment = EnvironmentTheme.STORM_SUMMIT
        )
    )

    fun byId(id: String): RouteSpec = routes.firstOrNull { it.id == id } ?: routes.first()
}
