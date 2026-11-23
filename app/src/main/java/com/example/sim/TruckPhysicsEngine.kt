package com.example.sim

import com.example.model.ConvoyPlayer
import com.example.model.EnvironmentTheme
import com.example.model.RouteSpec
import com.example.model.TruckSpec
import com.example.model.WeatherCondition
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class RoadPoint(
    val s: Float,
    val x: Float,
    val y: Float,
    val z: Float,
    val yaw: Float,
    val pitch: Float,
    val curvature: Float
)

data class ControlInputs(
    val throttle: Float = 0f,
    val brake: Float = 0f,
    val steerInput: Float = 0f,
    val retarderOn: Boolean = false,
    val parkingBrake: Boolean = false,
    val headlightsOn: Boolean = true,
    val leftBlinker: Boolean = false,
    val rightBlinker: Boolean = false,
    val hornPressed: Boolean = false,
    val walkMoveX: Float = 0f,
    val walkMoveY: Float = 0f,
    val sprint: Boolean = false
)

enum class TrafficVehicleType {
    SEDAN,
    BUS,
    MOTORCYCLE,
    TRUCK
}

data class TrafficVehicle(
    var s: Float,
    var laneOffset: Float,
    var speedMs: Float,
    val baseSpeedMs: Float,
    val type: TrafficVehicleType,
    val isOncoming: Boolean,
    val colorRgb: FloatArray,
    var braking: Boolean = false
) {
    val isTruck: Boolean get() = type == TrafficVehicleType.TRUCK || type == TrafficVehicleType.BUS
}

enum class TrafficLightState {
    GREEN,
    AMBER,
    RED
}

data class TrafficLightNode(
    val s: Float,
    val phaseOffsetSec: Float,
    val label: String,
    var state: TrafficLightState = TrafficLightState.GREEN
)

enum class LandmarkKind {
    CITY_ORIGIN,
    CITY_MIDTOWN,
    CITY_DESTINATION,
    FUEL_STATION,
    WORKSHOP,
    TOLL_BOOTH,
    PARKING_REST,
    BRIDGE,
    TUNNEL,
    VOLCANO,
    FOREST,
    FARM_ZONE,
    INDUSTRIAL_ZONE,
    INTERSECTION,
    ROUNDABOUT,
    SECONDARY_ROAD_ZONE,
    VILLAGE_HOUSES,
    WIND_FARM,
    HIGHWAY_GANTRY
}

data class WorldLandmark(
    val s: Float,
    val kind: LandmarkKind,
    val label: String,
    val spanMeters: Float = 40f
)

data class TelemetrySnapshot(
    val speedKmh: Int = 0,
    val rpm: Int = 650,
    val gearLabel: String = "N",
    val currentGear: Int = 0,
    val manualTransmission: Boolean = false,
    val fuelPercent: Int = 100,
    val lowFuelWarning: Boolean = false,
    val nearFuelStation: Boolean = false,
    val nearWorkshop: Boolean = false,
    val nearParkingBay: Boolean = false,
    val activeZoneLabel: String = "Autopista Principal",
    val isSecondaryRoad: Boolean = false,
    val inTunnel: Boolean = false,
    val onBridge: Boolean = false,
    val damagePercent: Int = 0,
    val progressFraction: Float = 0f,
    val roadProgressMeters: Float = 0f,
    val totalDrivenKm: Double = 0.0,
    val remainingDisplayKm: Double = 0.0,
    val elapsedSeconds: Long = 0L,
    val upcomingCurvature: Float = 0f,
    val laneOffset: Float = 2.1f,
    val speedLimitKmh: Int = 85,
    val speeding: Boolean = false,
    val finished: Boolean = false,
    val collisionFlash: Float = 0f,
    val steerAngleNorm: Float = 0f,
    val retarderOn: Boolean = false,
    val parkingBrake: Boolean = false,
    val headlightsOn: Boolean = true,
    val leftBlinker: Boolean = false,
    val rightBlinker: Boolean = false,
    val wiperMode: Int = 0, // 0 = OFF, 1 = INT, 2 = FAST
    val wiperSweepNorm: Float = 0f,
    val driverOnFoot: Boolean = false,
    val nearCabDoor: Boolean = true,
    val canExitTruck: Boolean = true,
    val timeOfDay: Float = 0.36f,
    val dayPhaseLabel: String = "Día",
    val weatherCondition: WeatherCondition = WeatherCondition.CLEAR,
    val upcomingTrafficLight: TrafficLightState? = null,
    val upcomingTrafficLightDistMeters: Int = 0,
    val redLightFineCount: Int = 0,
    val tollPaidCount: Int = 0,
    val lastRoadAlert: String? = null,
    val lastRoadAlertSeq: Int = 0
)

class RoadSpline(route: RouteSpec) {
    val stepMeters = 4.0f
    val totalSimLength = route.simLengthMeters
    val points: List<RoadPoint>

    init {
        val count = ((totalSimLength + 240f) / stepMeters).toInt() + 1
        val list = ArrayList<RoadPoint>(count)
        var x = 0f
        var y = 0f
        var z = 0f
        var yaw = 0f

        val cInt = route.curveIntensity
        val hInt = route.hillIntensity

        for (i in 0 until count) {
            val s = i * stepMeters
            val ramp = ((s - 40f) / 120f).coerceIn(0f, 1f)
            val endRamp = ((totalSimLength - s) / 100f).coerceIn(0f, 1f)
            val envelope = ramp * (0.35f + 0.65f * endRamp)

            val curvature = envelope * cInt * (
                0.0085f * sin(s * 0.0068f) +
                    0.0062f * cos(s * 0.0115f + 0.7f) +
                    0.0038f * sin(s * 0.019f + 1.8f)
                )

            val slope = envelope * hInt * (
                0.055f * sin(s * 0.0052f + 0.3f) +
                    0.030f * cos(s * 0.0105f)
                )

            val pitch = atan2(slope, 1f)
            list.add(RoadPoint(s = s, x = x, y = y, z = z, yaw = yaw, pitch = pitch, curvature = curvature))

            val dx = sin(yaw) * stepMeters
            val dz = -cos(yaw) * stepMeters
            x += dx
            z += dz
            y += slope * stepMeters
            yaw += curvature * stepMeters
        }
        points = list
    }

    fun pointAtIndex(idx: Int): RoadPoint = points[idx.coerceIn(0, points.lastIndex)]

    fun sampleAtDistance(s: Float): RoadPoint {
        val exact = (s / stepMeters).coerceIn(0f, (points.lastIndex - 1).toFloat())
        val i0 = exact.toInt()
        val i1 = (i0 + 1).coerceAtMost(points.lastIndex)
        val t = exact - i0
        val p0 = points[i0]
        val p1 = points[i1]
        return RoadPoint(
            s = p0.s + (p1.s - p0.s) * t,
            x = p0.x + (p1.x - p0.x) * t,
            y = p0.y + (p1.y - p0.y) * t,
            z = p0.z + (p1.z - p0.z) * t,
            yaw = p0.yaw + (p1.yaw - p0.yaw) * t,
            pitch = p0.pitch + (p1.pitch - p0.pitch) * t,
            curvature = p0.curvature + (p1.curvature - p0.curvature) * t
        )
    }

    fun project(wx: Float, wz: Float, hintIndex: Int): Triple<Int, Float, RoadPoint> {
        val start = (hintIndex - 18).coerceAtLeast(0)
        val end = (hintIndex + 26).coerceAtMost(points.lastIndex - 1)
        var bestIdx = hintIndex.coerceIn(0, points.lastIndex - 1)
        var bestDistSq = Float.MAX_VALUE
        for (i in start..end) {
            val p = points[i]
            val dx = wx - p.x
            val dz = wz - p.z
            val d2 = dx * dx + dz * dz
            if (d2 < bestDistSq) {
                bestDistSq = d2
                bestIdx = i
            }
        }
        val p0 = points[bestIdx]
        val p1 = points[(bestIdx + 1).coerceAtMost(points.lastIndex)]
        val segX = p1.x - p0.x
        val segZ = p1.z - p0.z
        val segLenSq = (segX * segX + segZ * segZ).coerceAtLeast(0.001f)
        val t = (((wx - p0.x) * segX + (wz - p0.z) * segZ) / segLenSq).coerceIn(0f, 1f)
        val projX = p0.x + segX * t
        val projZ = p0.z + segZ * t
        val yaw = p0.yaw + (p1.yaw - p0.yaw) * t
        val rightX = cos(yaw)
        val rightZ = sin(yaw)
        val laneOffset = (wx - projX) * rightX + (wz - projZ) * rightZ
        val interp = RoadPoint(
            s = p0.s + (p1.s - p0.s) * t,
            x = projX,
            y = p0.y + (p1.y - p0.y) * t,
            z = projZ,
            yaw = yaw,
            pitch = p0.pitch + (p1.pitch - p0.pitch) * t,
            curvature = p0.curvature + (p1.curvature - p0.curvature) * t
        )
        return Triple(bestIdx, laneOffset, interp)
    }
}

class TruckPhysicsEngine(
    val truck: TruckSpec,
    val route: RouteSpec,
    initialDamagePercent: Int = 0
) {
    val road = RoadSpline(route)
    val roadHalfWidth = 5.6f

    @Volatile
    var inputs = ControlInputs()

    private val stateLock = Any()

    var tractorX = 0f
        private set
    var tractorY = 0f
        private set
    var tractorZ = -12f
        private set
    var tractorYaw = 0f
        private set
    var tractorPitch = 0f
        private set
    var cabPitchOffset = 0f
        private set
    var cabRollOffset = 0f
        private set

    var trailerYaw = 0f
        private set
    var trailerX = 0f
        private set
    var trailerZ = -12f + 5.8f
        private set

    var speedMs = 0f
        private set
    var wheelSpinAngle = 0f
        private set
    var steerWheelAngleRad = 0f
        private set
    var rpm = 680f
        private set
    var currentGear = 0
        private set
    var manualTransmission = false
        private set

    var fuelLiters = truck.fuelCapacityLiters
        private set
    var nearFuelStation = false
        private set
    var nearWorkshop = false
        private set
    var nearParkingBay = false
        private set
    var damageAccum = initialDamagePercent.toFloat().coerceIn(0f, 100f)
        private set
    var collisionFlash = 0f
        private set
    var roadIndexHint = 3
        private set
    var roadProgressMeters = 12f
        private set
    var totalDrivenMeters = 0f
        private set
    var laneOffset = 2.15f
        private set
    var elapsedSimTime = 0f
        private set
    var blinkTimer = 0f
        private set
    var finished = false
        private set

    var timeOfDay = route.environment.initialTimeOfDay
        private set
    var weatherCondition = route.environment.defaultWeather
        private set
    var rainIntensity = 0f
        private set
    var fogDensity = 0f
        private set

    var wiperMode = if (route.environment.defaultWeather == WeatherCondition.RAIN ||
        route.environment.defaultWeather == WeatherCondition.STORM
    ) 1 else 0
        private set
    var wiperSweepNorm = 0f
        private set
    private var wiperTimer = 0f

    var driverOnFoot = false
        private set
    var driverX = 0f
        private set
    var driverY = 0f
        private set
    var driverZ = 0f
        private set
    var driverYaw = 0f
        private set
    var driverWalkCycle = 0f
        private set
    var driverWalkSpeed = 0f
        private set
    var nearCabDoor = true
        private set
    private var simClock = 0f
    private var smoothThrottle = 0f

    var redLightFineCount = 0
        private set
    var tollPaidCount = 0
        private set
    private var lastRoadAlert: String? = null
    private var lastRoadAlertSeq = 0

    private val triggeredTolls = HashSet<Int>()
    private val triggeredRedLights = HashSet<Int>()

    val traffic: MutableList<TrafficVehicle> = mutableListOf()
    private val remoteConvoyList: MutableList<ConvoyPlayer> = mutableListOf()

    val fuelStationPositions: List<Float> = listOf(
        route.simLengthMeters * 0.28f,
        route.simLengthMeters * 0.68f
    )
    val workshopPositions: List<Float> = listOf(
        route.simLengthMeters * 0.44f,
        route.simLengthMeters * 0.80f
    )
    val parkingBayPositions: List<Float> = listOf(
        route.simLengthMeters * 0.19f,
        route.simLengthMeters * 0.59f
    )
    val tollPositions: List<Float> = listOf(
        route.simLengthMeters * 0.38f,
        route.simLengthMeters * 0.85f
    )
    val trafficLights: List<TrafficLightNode> = listOf(
        TrafficLightNode(route.simLengthMeters * 0.24f, phaseOffsetSec = 0f, label = "Cruce Valle Norte"),
        TrafficLightNode(route.simLengthMeters * 0.54f, phaseOffsetSec = 5.5f, label = "Rotonda Central"),
        TrafficLightNode(route.simLengthMeters * 0.75f, phaseOffsetSec = 10.2f, label = "Avenida Puerto")
    )

    val landmarks: List<WorldLandmark> = buildList {
        val len = route.simLengthMeters
        add(WorldLandmark(15f, LandmarkKind.CITY_ORIGIN, route.originCity, 50f))
        add(WorldLandmark(len * 0.09f, LandmarkKind.FARM_ZONE, "Zona Agrícola", 75f))
        add(WorldLandmark(len * 0.14f, LandmarkKind.FOREST, "Bosque Pinos", 80f))
        add(WorldLandmark(parkingBayPositions[0], LandmarkKind.PARKING_REST, "Área de Descanso", 36f))
        add(WorldLandmark(len * 0.22f, LandmarkKind.BRIDGE, "Puente del Río", 68f))
        add(WorldLandmark(len * 0.24f, LandmarkKind.INTERSECTION, "Cruce Norte", 28f))
        add(WorldLandmark(fuelStationPositions[0], LandmarkKind.FUEL_STATION, "Diésel Express", 38f))
        add(WorldLandmark(len * 0.33f, LandmarkKind.VOLCANO, "Volcán Nevado", 110f))
        add(WorldLandmark(tollPositions[0], LandmarkKind.TOLL_BOOTH, "Peaje Sur", 32f))
        add(WorldLandmark(workshopPositions[0], LandmarkKind.WORKSHOP, "Taller Mecánico 24H", 38f))
        add(WorldLandmark(len * 0.48f, LandmarkKind.INDUSTRIAL_ZONE, "Parque Industrial", 85f))
        add(WorldLandmark(len * 0.54f, LandmarkKind.ROUNDABOUT, "Rotonda Central", 42f))
        add(WorldLandmark(parkingBayPositions[1], LandmarkKind.PARKING_REST, "Estacionamiento", 36f))
        add(WorldLandmark(len * 0.62f, LandmarkKind.SECONDARY_ROAD_ZONE, "Camino Rural", 95f))
        add(WorldLandmark(len * 0.64f, LandmarkKind.TUNNEL, "Túnel Sierra", 74f))
        add(WorldLandmark(fuelStationPositions[1], LandmarkKind.FUEL_STATION, "Ruta Diésel 24H", 38f))
        add(WorldLandmark(len * 0.72f, LandmarkKind.WIND_FARM, "Parque Eólico", 60f))
        add(WorldLandmark(len * 0.75f, LandmarkKind.INTERSECTION, "Cruce Puerto", 28f))
        add(WorldLandmark(workshopPositions[1], LandmarkKind.WORKSHOP, "Servicio Pesado", 38f))
        add(WorldLandmark(tollPositions[1], LandmarkKind.TOLL_BOOTH, "Peaje Metrópoli", 32f))
        add(WorldLandmark(len * 0.90f, LandmarkKind.HIGHWAY_GANTRY, "Pórtico", 24f))
        add(WorldLandmark(len - 18f, LandmarkKind.CITY_DESTINATION, route.destinationCity, 50f))
    }.sortedBy { it.s }

    private val gearRatios = floatArrayOf(
        0f, 14.5f, 11.2f, 8.8f, 6.9f, 5.4f, 4.2f, 3.3f, 2.6f, 2.05f, 1.60f, 1.28f, 1.02f
    )
    private val totalMassKg = 8800f + route.cargoTons * 1000f
    private val maxSpeedMs = truck.maxSpeedKmh / 3.6f

    init {
        reset(preserveDamage = true)
    }

    fun reset(preserveDamage: Boolean = false) {
        synchronized(stateLock) {
            val startPt = road.sampleAtDistance(18f)
            val rightX = cos(startPt.yaw)
            val rightZ = sin(startPt.yaw)
            laneOffset = 2.15f
            tractorX = startPt.x + rightX * laneOffset
            tractorZ = startPt.z + rightZ * laneOffset
            tractorY = startPt.y
            tractorYaw = startPt.yaw
            tractorPitch = startPt.pitch
            trailerYaw = startPt.yaw
            updateTrailerPosition()
            speedMs = 0f
            wheelSpinAngle = 0f
            steerWheelAngleRad = 0f
            rpm = 680f
            currentGear = 0
            fuelLiters = truck.fuelCapacityLiters
            nearFuelStation = false
            nearWorkshop = false
            nearParkingBay = false
            if (!preserveDamage) damageAccum = 0f
            collisionFlash = 0f
            roadIndexHint = 4
            roadProgressMeters = 18f
            totalDrivenMeters = 0f
            elapsedSimTime = 0f
            finished = false
            timeOfDay = route.environment.initialTimeOfDay
            weatherCondition = route.environment.defaultWeather
            rainIntensity = targetRainFor(weatherCondition)
            fogDensity = targetFogFor(weatherCondition)
            cabPitchOffset = 0f
            cabRollOffset = 0f
            driverOnFoot = false
            driverX = tractorX - rightX * 1.95f + sin(tractorYaw) * 1.1f
            driverY = tractorY
            driverZ = tractorZ - rightZ * 1.95f - cos(tractorYaw) * 1.1f
            driverYaw = tractorYaw
            driverWalkCycle = 0f
            driverWalkSpeed = 0f
            nearCabDoor = true
            simClock = 0f
            smoothThrottle = 0f
            wiperTimer = 0f
            wiperSweepNorm = 0f
            redLightFineCount = 0
            tollPaidCount = 0
            lastRoadAlert = null
            triggeredTolls.clear()
            triggeredRedLights.clear()
            spawnTraffic()
        }
    }

    fun setRemoteConvoyPlayers(players: List<ConvoyPlayer>) {
        synchronized(stateLock) {
            remoteConvoyList.clear()
            remoteConvoyList.addAll(players)
        }
    }

    fun toggleTransmissionMode(): Boolean {
        synchronized(stateLock) {
            manualTransmission = !manualTransmission
            if (manualTransmission && currentGear == 0 && speedMs > 1.0f) {
                currentGear = selectForwardGear(abs(speedMs))
            }
            return manualTransmission
        }
    }

    fun shiftGearUp() {
        synchronized(stateLock) {
            if (!manualTransmission) manualTransmission = true
            if (currentGear < 12) {
                if (currentGear == -1 && speedMs < -1.0f) return
                currentGear += 1
            }
        }
    }

    fun shiftGearDown() {
        synchronized(stateLock) {
            if (!manualTransmission) manualTransmission = true
            if (currentGear > -1) {
                if (currentGear == 0 && speedMs > 1.5f) return
                currentGear -= 1
            }
        }
    }

    fun cycleWiperMode(): Int {
        synchronized(stateLock) {
            wiperMode = (wiperMode + 1) % 3
            return wiperMode
        }
    }

    fun cycleTimeOfDayPreset() {
        synchronized(stateLock) {
            timeOfDay = when {
                timeOfDay < 0.55f -> 0.72f
                timeOfDay < 0.82f -> 0.94f
                else -> 0.36f
            }
        }
    }

    fun cycleWeatherCondition() {
        synchronized(stateLock) {
            val all = WeatherCondition.entries
            weatherCondition = all[(all.indexOf(weatherCondition) + 1) % all.size]
            if ((weatherCondition == WeatherCondition.RAIN || weatherCondition == WeatherCondition.STORM) && wiperMode == 0) {
                wiperMode = 1
            }
        }
    }

    fun refuelAtStation(): Boolean {
        synchronized(stateLock) {
            if (!isAtFuelStation()) return false
            fuelLiters = truck.fuelCapacityLiters
            pushAlert("Tanque Diésel Lleno al 100%")
            return true
        }
    }

    fun repairVehicleAtWorkshop(): Int {
        synchronized(stateLock) {
            if (!isAtWorkshop()) return -1
            val repaired = damageAccum.toInt()
            damageAccum = 0f
            pushAlert("Taller 24H: Camión reparado (0% daño)")
            return repaired
        }
    }

    fun restAtParkingBay(): Boolean {
        synchronized(stateLock) {
            if (!isAtParkingBay()) return false
            timeOfDay = (timeOfDay + 0.25f) % 1f
            speedMs = 0f
            currentGear = 0
            pushAlert("Descanso completado • Ciclo horario avanzado")
            return true
        }
    }

    fun finishFreeRoamNow() {
        synchronized(stateLock) {
            finished = true
            speedMs = 0f
        }
    }

    private fun pushAlert(msg: String) {
        lastRoadAlert = msg
        lastRoadAlertSeq += 1
    }

    private fun isAtFuelStation(): Boolean {
        if (abs(speedMs) > 7.5f) return false
        return fuelStationPositions.any { abs(roadProgressMeters - it) <= 32f }
    }

    private fun isAtWorkshop(): Boolean {
        if (abs(speedMs) > 7.5f) return false
        return workshopPositions.any { abs(roadProgressMeters - it) <= 34f }
    }

    private fun isAtParkingBay(): Boolean {
        if (abs(speedMs) > 6.5f) return false
        return parkingBayPositions.any { abs(roadProgressMeters - it) <= 30f }
    }

    private fun targetRainFor(w: WeatherCondition): Float = when (w) {
        WeatherCondition.CLEAR -> 0f
        WeatherCondition.RAIN -> 0.72f
        WeatherCondition.FOG -> 0.12f
        WeatherCondition.STORM -> 1.0f
    }

    private fun targetFogFor(w: WeatherCondition): Float = when (w) {
        WeatherCondition.CLEAR -> 0f
        WeatherCondition.RAIN -> 0.28f
        WeatherCondition.FOG -> 0.85f
        WeatherCondition.STORM -> 0.62f
    }

    fun exitTruckToFoot(): Boolean {
        synchronized(stateLock) {
            if (driverOnFoot || finished) return false
            if (abs(speedMs) >= 1.8f) return false

            speedMs = 0f
            currentGear = 0
            val fwdX = sin(tractorYaw)
            val fwdZ = -cos(tractorYaw)
            val rightX = cos(tractorYaw)
            val rightZ = sin(tractorYaw)
            driverX = tractorX - rightX * 1.95f + fwdX * 1.10f
            driverZ = tractorZ - rightZ * 1.95f + fwdZ * 1.10f
            val (_, _, roadPt) = road.project(driverX, driverZ, roadIndexHint)
            driverY = roadPt.y
            driverYaw = tractorYaw
            driverWalkCycle = 0f
            driverWalkSpeed = 0f
            nearCabDoor = true
            driverOnFoot = true
            return true
        }
    }

    fun boardTruckFromFoot(): Boolean {
        synchronized(stateLock) {
            if (!driverOnFoot) return false
            if (!isNearLeftCabDoor()) return false
            driverOnFoot = false
            driverWalkSpeed = 0f
            return true
        }
    }

    private fun spawnTraffic() {
        traffic.clear()
        val colors = listOf(
            floatArrayOf(0.84f, 0.86f, 0.90f),
            floatArrayOf(0.18f, 0.34f, 0.56f),
            floatArrayOf(0.76f, 0.18f, 0.18f),
            floatArrayOf(0.16f, 0.52f, 0.35f),
            floatArrayOf(0.92f, 0.68f, 0.14f),
            floatArrayOf(0.24f, 0.26f, 0.31f)
        )
        val count = (18 * route.trafficDensity).toInt().coerceIn(10, 28)
        val spacing = (route.simLengthMeters - 180f) / count
        for (i in 0 until count) {
            val s = 120f + i * spacing
            val isOncoming = i % 2 == 1
            val lane = when {
                isOncoming && i % 4 == 3 -> -3.65f
                isOncoming -> -1.95f
                i % 4 == 2 -> 3.65f
                else -> 1.95f
            }
            val type = when (i % 5) {
                0 -> TrafficVehicleType.TRUCK
                1 -> TrafficVehicleType.SEDAN
                2 -> TrafficVehicleType.BUS
                3 -> TrafficVehicleType.MOTORCYCLE
                else -> TrafficVehicleType.SEDAN
            }
            val baseSpeed = when (type) {
                TrafficVehicleType.MOTORCYCLE -> if (isOncoming) 23f else 20f
                TrafficVehicleType.SEDAN -> if (isOncoming) 21f else 18f
                TrafficVehicleType.BUS -> if (isOncoming) 17f else 15f
                TrafficVehicleType.TRUCK -> if (isOncoming) 16f else 13.5f
            }
            traffic.add(
                TrafficVehicle(
                    s = s,
                    laneOffset = lane,
                    speedMs = baseSpeed,
                    baseSpeedMs = baseSpeed,
                    type = type,
                    isOncoming = isOncoming,
                    colorRgb = colors[i % colors.size]
                )
            )
        }
    }

    fun step(rawDt: Float, paused: Boolean) {
        if (paused) return
        val dt = rawDt.coerceIn(0.001f, 0.05f)
        val subSteps = 2
        val subDt = dt / subSteps

        synchronized(stateLock) {
            if (finished) return
            elapsedSimTime += dt
            blinkTimer += dt
            simClock += dt
            collisionFlash = max(0f, collisionFlash - dt * 2.4f)

            updateTrafficLightCycles()
            updateWiperAnimation(dt)

            val ctrl = inputs
            timeOfDay = (timeOfDay + dt / 135f) % 1f
            rainIntensity += (targetRainFor(weatherCondition) - rainIntensity) * min(1f, dt * 1.8f)
            fogDensity += (targetFogFor(weatherCondition) - fogDensity) * min(1f, dt * 1.8f)

            for (stepIdx in 0 until subSteps) {
                stepPhysics(subDt, ctrl)
            }
            nearFuelStation = isAtFuelStation()
            nearWorkshop = isAtWorkshop()
            nearParkingBay = isAtParkingBay()
        }
    }

    private fun updateTrafficLightCycles() {
        for (node in trafficLights) {
            val cycle = (simClock + node.phaseOffsetSec) % 16f
            node.state = when {
                cycle < 8.5f -> TrafficLightState.GREEN
                cycle < 11.0f -> TrafficLightState.AMBER
                else -> TrafficLightState.RED
            }
        }
    }

    private fun updateWiperAnimation(dt: Float) {
        val effectiveRate = when (wiperMode) {
            2 -> 5.6f
            1 -> 3.0f
            else -> 0f
        }
        if (effectiveRate > 0f) {
            wiperTimer += dt * effectiveRate
            wiperSweepNorm = (sin(wiperTimer) * 0.5f + 0.5f)
        } else {
            wiperSweepNorm = max(0f, wiperSweepNorm - dt * 3.5f)
        }
    }

    private fun stepPhysics(dt: Float, ctrl: ControlInputs) {
        smoothThrottle += ((if (driverOnFoot) 0f else ctrl.throttle) - smoothThrottle) * min(1f, dt * 8f)

        if (driverOnFoot) {
            stepDriverOnFoot(dt, ctrl)
            updateTraffic(dt, checkCollisions = false, roadYawForPush = tractorYaw)
            return
        }

        val absSpeed = abs(speedMs)
        val speedDamp = 1f / (1f + absSpeed * 0.055f)
        val maxSteerAngle = 0.56f * (0.35f + 0.65f * speedDamp)
        val targetSteer = ctrl.steerInput.coerceIn(-1f, 1f) * maxSteerAngle
        val steerRate = if (abs(ctrl.steerInput) > 0.03f) 3.8f else 5.2f
        steerWheelAngleRad += (targetSteer - steerWheelAngleRad) * min(1f, dt * steerRate)

        var driveForce = 0f
        var brakeForce = 0f

        if (ctrl.parkingBrake) {
            brakeForce += 68000f * truck.brakingPower
            if (absSpeed < 0.25f) {
                speedMs = 0f
                if (!manualTransmission) currentGear = 0
            }
        } else if (manualTransmission) {
            if (ctrl.throttle > 0.02f && fuelLiters > 0f) {
                when {
                    currentGear > 0 -> {
                        val gearMaxMs = (maxSpeedMs / 12f) * currentGear * 1.08f
                        val ratio = gearRatios[currentGear]
                        val limiter = if (speedMs > gearMaxMs) 0.08f else 1.0f
                        val engineForce = (truck.torqueNm * ratio * 1.98f) * ctrl.throttle * limiter
                        driveForce += engineForce.coerceAtMost(115000f)
                        fuelLiters = max(0f, fuelLiters - dt * (0.04f + 0.16f * ctrl.throttle))
                    }
                    currentGear < 0 -> {
                        val reverseForce = (truck.torqueNm * 7.8f) * ctrl.throttle
                        driveForce -= reverseForce.coerceAtMost(34000f)
                        fuelLiters = max(0f, fuelLiters - dt * (0.04f + 0.14f * ctrl.throttle))
                    }
                    else -> {
                        fuelLiters = max(0f, fuelLiters - dt * 0.02f)
                    }
                }
            }
            if (ctrl.brake > 0.02f) {
                brakeForce += 56000f * truck.brakingPower * ctrl.brake
            }
        } else {
            if (ctrl.throttle > 0.02f && fuelLiters > 0f) {
                if (currentGear <= 0) currentGear = 1
                val targetGear = selectForwardGear(absSpeed)
                currentGear = targetGear
                val ratio = gearRatios[currentGear]
                val engineForce = (truck.torqueNm * ratio * 1.95f) * ctrl.throttle
                driveForce += engineForce.coerceAtMost(110000f)
                fuelLiters = max(0f, fuelLiters - dt * (0.04f + 0.16f * ctrl.throttle))
            } else if (ctrl.brake > 0.02f) {
                if (speedMs > 0.8f) {
                    brakeForce += 56000f * truck.brakingPower * ctrl.brake
                } else {
                    currentGear = -1
                    val reverseForce = (truck.torqueNm * 7.5f) * ctrl.brake
                    driveForce -= reverseForce.coerceAtMost(32000f)
                }
            }
        }

        if (ctrl.retarderOn && speedMs > 3.5f) {
            brakeForce += 22000f * truck.brakingPower
        }

        val (_, latOff, roadPt) = road.project(tractorX, tractorZ, roadIndexHint)
        laneOffset = latOff
        tractorY = roadPt.y
        tractorPitch = roadPt.pitch
        val prevProgress = roadProgressMeters
        roadProgressMeters = roadPt.s

        val gravityForce = -totalMassKg * 9.81f * sin(roadPt.pitch)
        val aeroDrag = 0.5f * 1.225f * 6.8f * 0.68f * speedMs * absSpeed
        val rollingDrag = totalMassKg * 9.81f * 0.0085f * (if (speedMs >= 0f) 1f else -1f)

        val brakingSigned = if (speedMs > 0.05f) -brakeForce else if (speedMs < -0.05f) brakeForce else 0f
        var netForce = driveForce + gravityForce - aeroDrag - rollingDrag + brakingSigned
        if (absSpeed < 0.15f && abs(driveForce) < 500f && (ctrl.brake > 0.02f || ctrl.parkingBrake)) {
            speedMs = 0f
            netForce = 0f
        }

        val accel = netForce / totalMassKg
        val prevSpeed = speedMs
        speedMs = (speedMs + accel * dt).coerceIn(-8.5f, maxSpeedMs)
        if (prevSpeed > 0.2f && speedMs < 0f && ctrl.throttle == 0f && ctrl.brake > 0f && prevSpeed > 1.0f) {
            speedMs = 0f
        }

        val targetPitch = (-accel * 0.014f).coerceIn(-0.045f, 0.045f)
        val lateralAccel = speedMs * speedMs * (steerWheelAngleRad / 3.8f)
        val targetRoll = (-lateralAccel * 0.009f).coerceIn(-0.065f, 0.065f)
        cabPitchOffset += (targetPitch - cabPitchOffset) * min(1f, dt * 8f)
        cabRollOffset += (targetRoll - cabRollOffset) * min(1f, dt * 7f)

        val wheelbase = 3.8f
        val yawRate = if (abs(speedMs) > 0.05f) (speedMs / wheelbase) * tanApprox(steerWheelAngleRad) else 0f
        tractorYaw += yawRate * dt
        val stepDx = sin(tractorYaw) * speedMs * dt
        val stepDz = -cos(tractorYaw) * speedMs * dt
        tractorX += stepDx
        tractorZ += stepDz
        totalDrivenMeters += abs(speedMs) * dt
        wheelSpinAngle = (wheelSpinAngle + (speedMs / 0.52f) * dt) % (2f * PI.toFloat())

        val trailerLen = 8.4f
        val hitchAngleDiff = normalizeAngle(tractorYaw - trailerYaw)
        val maxJackknife = 1.15f
        if (abs(hitchAngleDiff) > maxJackknife) {
            trailerYaw = tractorYaw - (if (hitchAngleDiff > 0f) maxJackknife else -maxJackknife)
        } else if (abs(speedMs) > 0.05f) {
            val trailerYawRate = (speedMs / trailerLen) * sin(hitchAngleDiff)
            trailerYaw += trailerYawRate * dt
        }
        updateTrailerPosition()

        val (newIdx, newLat, newRoadPt) = road.project(tractorX, tractorZ, roadIndexHint)
        roadIndexHint = newIdx
        laneOffset = newLat
        val maxLateral = roadHalfWidth - 1.15f
        if (abs(newLat) > maxLateral) {
            val sign = if (newLat > 0f) 1f else -1f
            val penetration = abs(newLat) - maxLateral
            val rightX = cos(newRoadPt.yaw)
            val rightZ = sin(newRoadPt.yaw)
            tractorX -= rightX * sign * penetration
            tractorZ -= rightZ * sign * penetration
            val yawDiff = normalizeAngle(newRoadPt.yaw - tractorYaw)
            tractorYaw += yawDiff * min(1f, dt * 6.5f)
            if (absSpeed > 4.5f && penetration > 0.04f) {
                damageAccum = min(100f, damageAccum + absSpeed * 1.15f * dt)
                collisionFlash = 1f
                speedMs *= (1f - 1.4f * dt).coerceAtLeast(0.6f)
            }
            updateTrailerPosition()
        }

        // Check Toll Booth crossings
        tollPositions.forEachIndexed { idx, tollS ->
            if (prevProgress < tollS && newRoadPt.s >= tollS && idx !in triggeredTolls) {
                triggeredTolls.add(idx)
                tollPaidCount += 1
                pushAlert("Peaje abonado: -$${route.tollFeeCredits} créditos")
            }
        }

        // Check Red Light violations
        trafficLights.forEachIndexed { idx, light ->
            if (prevProgress < light.s && newRoadPt.s >= light.s && idx !in triggeredRedLights) {
                if (light.state == TrafficLightState.RED && speedMs > 4.2f) {
                    triggeredRedLights.add(idx)
                    redLightFineCount += 1
                    collisionFlash = 0.65f
                    pushAlert("¡Multa semáforo en rojo! -$250 créditos")
                }
            }
        }

        updateTraffic(dt, checkCollisions = true, roadYawForPush = newRoadPt.yaw)

        val targetRpm = when {
            currentGear == 0 -> 680f + ctrl.throttle * 950f
            currentGear < 0 -> 720f + (absSpeed / 8.5f) * 1100f
            else -> {
                val gearTopSpeed = (maxSpeedMs / 12f) * currentGear
                val ratioNorm = (absSpeed / max(1.5f, gearTopSpeed)).coerceIn(0.32f, 1.05f)
                680f + ratioNorm * 1450f + ctrl.throttle * 180f
            }
        }
        rpm += (targetRpm - rpm) * min(1f, dt * 8f)

        if (roadProgressMeters >= route.simLengthMeters) {
            if (route.isFreeRoam) {
                // Seamless open-world loop back to corridor start while preserving odometer & damage
                val startPt = road.sampleAtDistance(24f)
                val rightX = cos(startPt.yaw)
                val rightZ = sin(startPt.yaw)
                tractorX = startPt.x + rightX * laneOffset.coerceIn(-4f, 4f)
                tractorZ = startPt.z + rightZ * laneOffset.coerceIn(-4f, 4f)
                tractorY = startPt.y
                tractorYaw = startPt.yaw
                trailerYaw = startPt.yaw
                roadIndexHint = 6
                roadProgressMeters = 24f
                triggeredTolls.clear()
                triggeredRedLights.clear()
                updateTrailerPosition()
                pushAlert("Nueva vuelta en Mundo Abierto")
            } else {
                finished = true
                speedMs = 0f
            }
        }
    }

    private fun stepDriverOnFoot(dt: Float, ctrl: ControlInputs) {
        speedMs = 0f
        currentGear = 0
        rpm += (680f - rpm) * min(1f, dt * 6f)
        cabPitchOffset *= (1f - min(1f, dt * 8f))
        cabRollOffset *= (1f - min(1f, dt * 8f))

        val turnInput = ctrl.walkMoveX.coerceIn(-1f, 1f)
        val moveInput = ctrl.walkMoveY.coerceIn(-1f, 1f)

        driverYaw = normalizeAngle(driverYaw + turnInput * 2.55f * dt)
        val fwdX = sin(driverYaw)
        val fwdZ = -cos(driverYaw)
        val maxWalkSpeed = if (ctrl.sprint) 6.4f else 3.3f
        val moveVel = moveInput * maxWalkSpeed

        var nextX = driverX + fwdX * moveVel * dt
        var nextZ = driverZ + fwdZ * moveVel * dt

        val resolvedTractor = pushOutsideOrientedBox(
            px = nextX, pz = nextZ, bx = tractorX, bz = tractorZ, byaw = tractorYaw,
            halfWidth = 1.55f, minLocalZ = -3.0f, maxLocalZ = 2.6f
        )
        nextX = resolvedTractor.first
        nextZ = resolvedTractor.second

        val resolvedTrailer = pushOutsideOrientedBox(
            px = nextX, pz = nextZ, bx = trailerX, bz = trailerZ, byaw = trailerYaw,
            halfWidth = 1.60f, minLocalZ = -4.6f, maxLocalZ = 5.15f
        )
        nextX = resolvedTrailer.first
        nextZ = resolvedTrailer.second

        val (_, latOff, roadPt) = road.project(nextX, nextZ, roadIndexHint)
        val maxDriverLat = roadHalfWidth + 3.2f
        if (abs(latOff) > maxDriverLat) {
            val sign = if (latOff > 0f) 1f else -1f
            val excess = abs(latOff) - maxDriverLat
            nextX -= cos(roadPt.yaw) * sign * excess
            nextZ -= sin(roadPt.yaw) * sign * excess
        }
        val longitudinalDelta = roadPt.s - roadProgressMeters
        if (abs(longitudinalDelta) > 42f) {
            val pull = longitudinalDelta - (if (longitudinalDelta > 0f) 42f else -42f)
            nextX -= sin(roadPt.yaw) * pull
            nextZ += cos(roadPt.yaw) * pull
        }

        val (_, _, finalRoadPt) = road.project(nextX, nextZ, roadIndexHint)
        driverX = nextX
        driverZ = nextZ
        driverY = finalRoadPt.y

        val effectiveActivity = max(abs(moveVel) / 6.4f, abs(turnInput) * 0.25f).coerceIn(0f, 1f)
        driverWalkSpeed += (effectiveActivity - driverWalkSpeed) * min(1f, dt * 10f)
        if (effectiveActivity > 0.04f) {
            val cycleRate = if (ctrl.sprint && abs(moveInput) > 0.2f) 11.5f else 7.2f
            driverWalkCycle = (driverWalkCycle + cycleRate * dt) % (2f * PI.toFloat())
        }
        nearCabDoor = isNearLeftCabDoor()
    }

    private fun pushOutsideOrientedBox(
        px: Float, pz: Float, bx: Float, bz: Float, byaw: Float,
        halfWidth: Float, minLocalZ: Float, maxLocalZ: Float
    ): Pair<Float, Float> {
        val fwdX = sin(byaw)
        val fwdZ = -cos(byaw)
        val rightX = cos(byaw)
        val rightZ = sin(byaw)

        val dx = px - bx
        val dz = pz - bz
        var lx = dx * rightX + dz * rightZ
        var lz = -(dx * fwdX + dz * fwdZ)

        if (lx in -halfWidth..halfWidth && lz in minLocalZ..maxLocalZ) {
            val dLeft = lx + halfWidth
            val dRight = halfWidth - lx
            val dFront = lz - minLocalZ
            val dBack = maxLocalZ - lz
            val minPen = min(min(dLeft, dRight), min(dFront, dBack))
            when (minPen) {
                dLeft -> lx = -halfWidth - 0.02f
                dRight -> lx = halfWidth + 0.02f
                dFront -> lz = minLocalZ - 0.02f
                else -> lz = maxLocalZ + 0.02f
            }
            return Pair(
                bx + rightX * lx - fwdX * lz,
                bz + rightZ * lx - fwdZ * lz
            )
        }
        return Pair(px, pz)
    }

    private fun leftCabDoorWorldPos(): Pair<Float, Float> {
        val fwdX = sin(tractorYaw)
        val fwdZ = -cos(tractorYaw)
        val rightX = cos(tractorYaw)
        val rightZ = sin(tractorYaw)
        return Pair(
            tractorX - rightX * 1.75f + fwdX * 1.10f,
            tractorZ - rightZ * 1.75f + fwdZ * 1.10f
        )
    }

    private fun isNearLeftCabDoor(): Boolean {
        val (dxPos, dzPos) = leftCabDoorWorldPos()
        val dx = driverX - dxPos
        val dz = driverZ - dzPos
        return (dx * dx + dz * dz) <= (4.5f * 4.5f)
    }

    private fun updateTraffic(dt: Float, checkCollisions: Boolean, roadYawForPush: Float) {
        for (i in traffic.indices) {
            val v = traffic[i]
            val dir = if (v.isOncoming) -1f else 1f

            // Intelligent traffic rule compliance: stop for red/amber lights & slower vehicles ahead
            var targetSpeed = v.baseSpeedMs
            for (light in trafficLights) {
                val distToStop = (light.s - v.s) * dir
                if (distToStop in 5f..38f && light.state != TrafficLightState.GREEN) {
                    targetSpeed = if (distToStop < 12f) 0f else min(targetSpeed, (distToStop - 5f) * 0.45f)
                }
            }

            // Yield to vehicles ahead in the same lane
            for (j in traffic.indices) {
                if (i == j) continue
                val other = traffic[j]
                if (other.isOncoming != v.isOncoming) continue
                if (abs(other.laneOffset - v.laneOffset) > 1.3f) continue
                val gap = (other.s - v.s) * dir
                if (gap in 2f..24f) {
                    val safeMatch = if (gap < 11f) 0f else min(targetSpeed, other.speedMs * 0.92f)
                    targetSpeed = min(targetSpeed, safeMatch)
                }
            }
            // Yield to player truck if ahead in same lane
            if (!v.isOncoming && abs(laneOffset - v.laneOffset) < 1.8f) {
                val gapToPlayer = roadProgressMeters - v.s
                if (gapToPlayer in 8f..26f) {
                    targetSpeed = min(targetSpeed, max(0f, speedMs * 0.9f))
                }
            }

            val prevSpeed = v.speedMs
            val rate = if (targetSpeed < v.speedMs) 7.5f else 3.2f
            v.speedMs += (targetSpeed - v.speedMs) * min(1f, dt * rate)
            v.braking = v.speedMs < prevSpeed - 0.05f || v.speedMs < 0.8f

            v.s += dir * v.speedMs * dt
            if (v.s > route.simLengthMeters + 120f) v.s = 40f
            if (v.s < 20f) v.s = route.simLengthMeters + 80f

            if (checkCollisions) {
                val distAlong = abs(v.s - roadProgressMeters)
                val hitHalfLen = when (v.type) {
                    TrafficVehicleType.TRUCK, TrafficVehicleType.BUS -> 7.0f
                    TrafficVehicleType.MOTORCYCLE -> 4.2f
                    TrafficVehicleType.SEDAN -> 5.6f
                }
                val hitHalfWidth = if (v.type == TrafficVehicleType.MOTORCYCLE) 1.65f else 2.20f
                if (distAlong < hitHalfLen && abs(v.laneOffset - laneOffset) < hitHalfWidth) {
                    val relSpeed = abs(speedMs - (dir * v.speedMs))
                    if (relSpeed > 2.5f) {
                        damageAccum = min(100f, damageAccum + relSpeed * 0.45f)
                        collisionFlash = 1f
                        speedMs *= 0.48f
                        val push = if (laneOffset >= v.laneOffset) 0.8f else -0.8f
                        val rightX = cos(roadYawForPush)
                        val rightZ = sin(roadYawForPush)
                        tractorX += rightX * push
                        tractorZ += rightZ * push
                        v.s += if (v.isOncoming) -32f else 32f
                    }
                }
            }
        }
    }

    private fun selectForwardGear(absSpeed: Float): Int {
        val norm = (absSpeed / maxSpeedMs).coerceIn(0f, 1f)
        return (1 + (norm * 11.4f).toInt()).coerceIn(1, 12)
    }

    private fun updateTrailerPosition() {
        val hitchOffset = -1.1f
        val hitchX = tractorX + sin(tractorYaw) * hitchOffset
        val hitchZ = tractorZ - cos(tractorYaw) * hitchOffset
        val trailerCenterBack = 4.6f
        trailerX = hitchX - sin(trailerYaw) * trailerCenterBack
        trailerZ = hitchZ + cos(trailerYaw) * trailerCenterBack
    }

    fun readRenderState(out: RenderSnapshot) {
        synchronized(stateLock) {
            out.tractorX = tractorX
            out.tractorY = tractorY
            out.tractorZ = tractorZ
            out.tractorYaw = tractorYaw
            out.tractorPitch = tractorPitch
            out.cabPitchOffset = cabPitchOffset
            out.cabRollOffset = cabRollOffset
            out.trailerX = trailerX
            out.trailerZ = trailerZ
            out.trailerYaw = trailerYaw
            out.wheelSpinAngle = wheelSpinAngle
            out.steerWheelAngleRad = steerWheelAngleRad
            out.roadIndexHint = roadIndexHint
            out.roadProgressMeters = roadProgressMeters
            out.laneOffset = laneOffset
            out.speedMs = speedMs
            out.headlightsOn = inputs.headlightsOn
            out.braking = inputs.brake > 0.05f || inputs.parkingBrake || (inputs.retarderOn && speedMs > 3.5f)
            out.reversing = currentGear < 0 && speedMs < -0.1f
            out.leftBlinkerOn = inputs.leftBlinker && ((blinkTimer * 2.2f).toInt() % 2 == 0)
            out.rightBlinkerOn = inputs.rightBlinker && ((blinkTimer * 2.2f).toInt() % 2 == 0)
            out.driverOnFoot = driverOnFoot
            out.driverX = driverX
            out.driverY = driverY
            out.driverZ = driverZ
            out.driverYaw = driverYaw
            out.driverWalkCycle = driverWalkCycle
            out.driverWalkSpeed = driverWalkSpeed
            if (driverOnFoot) {
                val (_, dLat, dPt) = road.project(driverX, driverZ, roadIndexHint)
                out.driverRoadS = dPt.s
                out.driverLaneOffset = dLat
            } else {
                out.driverRoadS = roadProgressMeters
                out.driverLaneOffset = laneOffset
            }
            out.throttleLevel = smoothThrottle
            out.currentGear = currentGear
            out.rpm = rpm
            out.simClock = simClock
            out.timeOfDay = timeOfDay
            out.rainIntensity = rainIntensity
            out.fogDensity = fogDensity
            out.wiperSweepNorm = wiperSweepNorm
            out.nearFuelStation = nearFuelStation
            out.trafficCopy.clear()
            for (v in traffic) {
                out.trafficCopy.add(v.copy())
            }
            out.trafficLightsCopy.clear()
            for (tl in trafficLights) {
                out.trafficLightsCopy.add(tl.copy())
            }
            out.remoteConvoyPlayers.clear()
            out.remoteConvoyPlayers.addAll(remoteConvoyList)
        }
    }

    fun readTelemetry(): TelemetrySnapshot {
        synchronized(stateLock) {
            val kmh = (abs(speedMs) * 3.6f).toInt()
            val gearStr = when {
                currentGear < 0 -> "R"
                currentGear == 0 -> "N"
                manualTransmission -> "M$currentGear"
                else -> "D$currentGear"
            }
            val progress = (roadProgressMeters / route.simLengthMeters).coerceIn(0f, 1f)
            val remKm = route.displayDistanceKm * (1.0 - progress)
            val drivenKm = (totalDrivenMeters / route.simLengthMeters) * route.displayDistanceKm
            val previewPt = road.sampleAtDistance(roadProgressMeters + 72f)
            val ctrl = inputs
            val fuelPct = ((fuelLiters / truck.fuelCapacityLiters) * 100f).toInt().coerceIn(0, 100)
            val phaseLabel = when {
                timeOfDay < 0.22f || timeOfDay >= 0.84f -> "Noche"
                timeOfDay < 0.34f -> "Amanecer"
                timeOfDay < 0.66f -> "Día"
                else -> "Atardecer"
            }

            val activeLm = landmarks.firstOrNull { abs(it.s - roadProgressMeters) <= it.spanMeters }
            val inTunnel = activeLm?.kind == LandmarkKind.TUNNEL
            val onBridge = activeLm?.kind == LandmarkKind.BRIDGE
            val isSecRoad = activeLm?.kind == LandmarkKind.SECONDARY_ROAD_ZONE
            val zoneLabel = activeLm?.label ?: if (route.isFreeRoam) "Autopista Mundo Abierto" else "Autopista Principal"

            val upcomingLight = trafficLights
                .filter { it.s > roadProgressMeters - 2f && it.s - roadProgressMeters < 110f }
                .minByOrNull { it.s - roadProgressMeters }

            return TelemetrySnapshot(
                speedKmh = kmh,
                rpm = rpm.toInt(),
                gearLabel = gearStr,
                currentGear = currentGear,
                manualTransmission = manualTransmission,
                fuelPercent = fuelPct,
                lowFuelWarning = fuelPct <= 20,
                nearFuelStation = nearFuelStation,
                nearWorkshop = nearWorkshop,
                nearParkingBay = nearParkingBay,
                activeZoneLabel = zoneLabel,
                isSecondaryRoad = isSecRoad,
                inTunnel = inTunnel,
                onBridge = onBridge,
                damagePercent = damageAccum.toInt().coerceIn(0, 100),
                progressFraction = progress,
                roadProgressMeters = roadProgressMeters,
                totalDrivenKm = drivenKm.coerceAtLeast(0.5),
                remainingDisplayKm = remKm,
                elapsedSeconds = elapsedSimTime.toLong().coerceAtLeast(1L),
                upcomingCurvature = previewPt.curvature,
                laneOffset = laneOffset,
                speedLimitKmh = if (isSecRoad) (route.speedLimitKmh - 20).coerceAtLeast(50) else route.speedLimitKmh,
                speeding = kmh > (if (isSecRoad) route.speedLimitKmh - 15 else route.speedLimitKmh + 5),
                finished = finished,
                collisionFlash = collisionFlash,
                steerAngleNorm = (steerWheelAngleRad / 0.52f).coerceIn(-1f, 1f),
                retarderOn = ctrl.retarderOn,
                parkingBrake = ctrl.parkingBrake,
                headlightsOn = ctrl.headlightsOn,
                leftBlinker = ctrl.leftBlinker,
                rightBlinker = ctrl.rightBlinker,
                wiperMode = wiperMode,
                wiperSweepNorm = wiperSweepNorm,
                driverOnFoot = driverOnFoot,
                nearCabDoor = nearCabDoor,
                canExitTruck = !driverOnFoot && abs(speedMs) < 1.8f,
                timeOfDay = timeOfDay,
                dayPhaseLabel = phaseLabel,
                weatherCondition = weatherCondition,
                upcomingTrafficLight = upcomingLight?.state,
                upcomingTrafficLightDistMeters = upcomingLight?.let { max(0, (it.s - roadProgressMeters).toInt()) } ?: 0,
                redLightFineCount = redLightFineCount,
                tollPaidCount = tollPaidCount,
                lastRoadAlert = lastRoadAlert,
                lastRoadAlertSeq = lastRoadAlertSeq
            )
        }
    }

    private fun tanApprox(rad: Float): Float {
        val c = cos(rad)
        return if (abs(c) < 0.05f) 0f else sin(rad) / c
    }

    private fun normalizeAngle(angle: Float): Float {
        var a = angle
        while (a > PI.toFloat()) a -= 2f * PI.toFloat()
        while (a < -PI.toFloat()) a += 2f * PI.toFloat()
        return a
    }
}

class RenderSnapshot {
    var tractorX = 0f
    var tractorY = 0f
    var tractorZ = 0f
    var tractorYaw = 0f
    var tractorPitch = 0f
    var cabPitchOffset = 0f
    var cabRollOffset = 0f
    var trailerX = 0f
    var trailerZ = 0f
    var trailerYaw = 0f
    var wheelSpinAngle = 0f
    var steerWheelAngleRad = 0f
    var roadIndexHint = 0
    var roadProgressMeters = 18f
    var laneOffset = 2.15f
    var speedMs = 0f
    var headlightsOn = true
    var braking = false
    var reversing = false
    var leftBlinkerOn = false
    var rightBlinkerOn = false
    var driverOnFoot = false
    var driverX = 0f
    var driverY = 0f
    var driverZ = 0f
    var driverYaw = 0f
    var driverWalkCycle = 0f
    var driverWalkSpeed = 0f
    var driverRoadS = 18f
    var driverLaneOffset = 2.15f
    var throttleLevel = 0f
    var currentGear = 0
    var rpm = 680f
    var simClock = 0f
    var timeOfDay = 0.36f
    var rainIntensity = 0f
    var fogDensity = 0f
    var wiperSweepNorm = 0f
    var nearFuelStation = false
    val trafficCopy = ArrayList<TrafficVehicle>(32)
    val trafficLightsCopy = ArrayList<TrafficLightNode>(8)
    val remoteConvoyPlayers = ArrayList<ConvoyPlayer>(8)
}
