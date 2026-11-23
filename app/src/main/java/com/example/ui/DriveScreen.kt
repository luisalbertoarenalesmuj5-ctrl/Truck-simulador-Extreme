package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.CameraMode
import com.example.model.ConvoyPlayer
import com.example.model.DriverProfile
import com.example.model.EnvironmentTheme
import com.example.model.RouteSpec
import com.example.model.SteeringMode
import com.example.model.TruckSpec
import com.example.model.TruckVisualConfig
import com.example.model.WorkshopCatalog
import com.example.sim.ControlInputs
import com.example.sim.RenderSnapshot
import com.example.sim.TiltSteeringController
import com.example.sim.TrafficLightState
import com.example.sim.TrafficVehicle
import com.example.sim.Truck2DViewport
import com.example.sim.TruckAudioEngine
import com.example.sim.TruckPhysicsEngine
import com.example.ui.theme.AmberDiesel
import com.example.ui.theme.CabMetal
import com.example.ui.theme.CobaltBeacon
import com.example.ui.theme.CrimsonBrake
import com.example.ui.theme.EmeraldCargo
import com.example.ui.theme.GoldTrophy
import com.example.ui.theme.GunmetalBorder
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.SteelSlate
import com.example.viewmodel.ConvoyViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DriveScreen(
    truck: TruckSpec,
    route: RouteSpec,
    visualConfig: TruckVisualConfig,
    initialDamagePercent: Int,
    initialSteeringMode: SteeringMode,
    initialSensitivity: Float,
    initialCameraMode: CameraMode,
    profile: DriverProfile?,
    convoyViewModel: ConvoyViewModel?,
    activeConvoyRoomId: String?,
    onRoadCreditAdjustment: (deltaCredits: Long, damagePercent: Int?) -> Unit,
    onSaveDelivery: suspend (damagePercent: Int, durationSeconds: Long) -> Result<Long>,
    onExitToHub: () -> Unit
) {
    val context = LocalContext.current

    val physics = remember(truck.id, route.id) {
        TruckPhysicsEngine(truck, route, initialDamagePercent = initialDamagePercent)
    }
    val audioEngine = remember { TruckAudioEngine() }
    val mirrorSnapshot = remember(physics) { RenderSnapshot() }
    var mirrorTraffic by remember { mutableStateOf<List<TrafficVehicle>>(emptyList()) }
    var visualClockSec by remember { mutableFloatStateOf(0f) }

    var steeringMode by remember { mutableStateOf(initialSteeringMode) }
    var sensitivity by remember { mutableFloatStateOf(initialSensitivity) }
    var cameraMode by remember { mutableStateOf(initialCameraMode) }
    var paused by remember { mutableStateOf(false) }
    var showFullMap by remember { mutableStateOf(false) }
    var showMirror by remember { mutableStateOf(true) }
    var showConvoyRadio by remember { mutableStateOf(false) }
    var hudToolsExpanded by remember { mutableStateOf(false) }

    var throttle by remember { mutableFloatStateOf(0f) }
    var brake by remember { mutableFloatStateOf(0f) }
    var steerInput by remember { mutableFloatStateOf(0f) }
    var retarderOn by remember { mutableStateOf(false) }
    var parkingBrake by remember { mutableStateOf(false) }
    var headlightsOn by remember {
        mutableStateOf(
            route.environment == EnvironmentTheme.DESERT_NIGHT ||
                route.environment == EnvironmentTheme.STORM_SUMMIT ||
                route.environment == EnvironmentTheme.COASTAL_SUNSET
        )
    }
    var leftBlinker by remember { mutableStateOf(false) }
    var rightBlinker by remember { mutableStateOf(false) }
    var hornActive by remember { mutableStateOf(false) }
    var walkMoveX by remember { mutableFloatStateOf(0f) }
    var walkMoveY by remember { mutableFloatStateOf(0f) }
    var sprint by remember { mutableStateOf(false) }
    var exitHintVisible by remember { mutableStateOf(false) }

    var telemetry by remember { mutableStateOf(physics.readTelemetry()) }
    var handledTolls by remember { mutableIntStateOf(0) }
    var handledFines by remember { mutableIntStateOf(0) }
    var lastAlertSeq by remember { mutableIntStateOf(0) }
    var activeAlertBanner by remember { mutableStateOf<String?>(null) }

    var deliverySaving by remember { mutableStateOf(false) }
    var deliverySavedCredits by remember { mutableStateOf<Long?>(null) }
    var deliverySaveError by remember { mutableStateOf<String?>(null) }

    val convoyPlayersState = convoyViewModel?.players?.collectAsStateWithLifecycle()
    val convoyMessagesState = convoyViewModel?.messages?.collectAsStateWithLifecycle()
    val allConvoyPlayers = convoyPlayersState?.value ?: emptyList()
    val convoyMessages = convoyMessagesState?.value ?: emptyList()
    val remoteConvoyPlayers: List<ConvoyPlayer> = remember(allConvoyPlayers, convoyViewModel?.currentUserId) {
        val myUid = convoyViewModel?.currentUserId
        if (myUid == null) emptyList() else allConvoyPlayers.filter { it.userId != myUid }
    }

    LaunchedEffect(remoteConvoyPlayers) {
        physics.setRemoteConvoyPlayers(remoteConvoyPlayers)
    }

    DisposableEffect(audioEngine) {
        onDispose { audioEngine.release() }
    }

    val tiltController = remember(context) {
        TiltSteeringController(context) { tilt ->
            if (steeringMode == SteeringMode.TILT && !paused && !telemetry.driverOnFoot) {
                steerInput = (tilt * sensitivity).coerceIn(-1f, 1f)
            }
        }
    }

    DisposableEffect(steeringMode) {
        if (steeringMode == SteeringMode.TILT) {
            tiltController.start()
        } else {
            tiltController.stop()
        }
        onDispose { tiltController.stop() }
    }

    LaunchedEffect(exitHintVisible) {
        if (exitHintVisible) {
            delay(2200L)
            exitHintVisible = false
        }
    }

    LaunchedEffect(hornActive) {
        if (hornActive) {
            delay(650L)
            hornActive = false
        }
    }

    LaunchedEffect(telemetry.lastRoadAlertSeq) {
        if (telemetry.lastRoadAlertSeq > lastAlertSeq && telemetry.lastRoadAlert != null) {
            lastAlertSeq = telemetry.lastRoadAlertSeq
            activeAlertBanner = telemetry.lastRoadAlert
            delay(2800L)
            if (lastAlertSeq == telemetry.lastRoadAlertSeq) {
                activeAlertBanner = null
            }
        }
    }

    LaunchedEffect(telemetry.tollPaidCount) {
        if (telemetry.tollPaidCount > handledTolls) {
            val count = telemetry.tollPaidCount - handledTolls
            handledTolls = telemetry.tollPaidCount
            audioEngine.playTollBeep()
            onRoadCreditAdjustment(-route.tollFeeCredits * count, telemetry.damagePercent)
        }
    }

    LaunchedEffect(telemetry.redLightFineCount) {
        if (telemetry.redLightFineCount > handledFines) {
            val count = telemetry.redLightFineCount - handledFines
            handledFines = telemetry.redLightFineCount
            onRoadCreditAdjustment(-250L * count, telemetry.damagePercent)
        }
    }

    // Periodic live multiplayer Convoy position sync
    LaunchedEffect(activeConvoyRoomId, convoyViewModel, profile?.userId) {
        if (activeConvoyRoomId != null && convoyViewModel != null && profile != null) {
            while (true) {
                convoyViewModel.syncDriveTelemetry(
                    profile = profile,
                    roadS = telemetry.roadProgressMeters.toDouble(),
                    laneOffset = telemetry.laneOffset.toDouble(),
                    speedKmh = telemetry.speedKmh,
                    headlightsOn = telemetry.headlightsOn,
                    hornActive = hornActive,
                    online = true
                )
                delay(700L)
            }
        }
    }

    // Push live inputs into physics engine cleanly
    LaunchedEffect(
        throttle, brake, steerInput, retarderOn, parkingBrake,
        headlightsOn, leftBlinker, rightBlinker, hornActive,
        walkMoveX, walkMoveY, sprint
    ) {
        physics.inputs = ControlInputs(
            throttle = throttle,
            brake = brake,
            steerInput = steerInput,
            retarderOn = retarderOn,
            parkingBrake = parkingBrake,
            headlightsOn = headlightsOn,
            leftBlinker = leftBlinker,
            rightBlinker = rightBlinker,
            hornPressed = hornActive,
            walkMoveX = walkMoveX,
            walkMoveY = walkMoveY,
            sprint = sprint
        )
    }

    // 60Hz Fixed-Frame Physics Loop + ~20Hz HUD Telemetry sync
    LaunchedEffect(physics, paused) {
        var lastNanos = 0L
        var hudAccum = 0f
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = (frameNanos - lastNanos) / 1_000_000_000f
                    if (!paused) {
                        visualClockSec += dt
                    }
                    physics.step(dt, paused)
                    hudAccum += dt
                    if (hudAccum >= 0.05f) {
                        hudAccum = 0f
                        telemetry = physics.readTelemetry()
                        if (showMirror) {
                            physics.readRenderState(mirrorSnapshot)
                            mirrorTraffic = mirrorSnapshot.trafficCopy.toList()
                        }
                    }
                }
                lastNanos = frameNanos
            }
        }
    }

    // Auto-record delivery once route finishes
    LaunchedEffect(telemetry.finished) {
        if (telemetry.finished && deliverySavedCredits == null && !deliverySaving) {
            deliverySaving = true
            val res = onSaveDelivery(telemetry.damagePercent, telemetry.elapsedSeconds)
            deliverySaving = false
            res.onSuccess { earned ->
                deliverySavedCredits = earned
            }.onFailure { err ->
                deliverySaveError = err.localizedMessage ?: "Error al guardar entrega"
            }
        }
    }

    BackHandler {
        if (!telemetry.finished) {
            paused = !paused
        } else {
            onExitToHub()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(ObsidianNight)) {
        val isCompactWidth = maxWidth < 600.dp

        // 1. Full-Bleed Pure 2D Ultra-Realistic Engine Viewport (Zero 3D)
        Truck2DViewport(
            physics = physics,
            truck = truck,
            route = route,
            visualConfig = visualConfig,
            cameraMode = cameraMode,
            paused = paused,
            modifier = Modifier.fillMaxSize()
        )

        // 1b. 2D Cabin Interior Overlay in Cabina 2D perspective
        if (!telemetry.driverOnFoot && cameraMode == CameraMode.CAB_2D) {
            RealCockpitOverlay(
                speedKmh = telemetry.speedKmh,
                rpm = telemetry.rpm,
                steerNorm = telemetry.steerAngleNorm,
                upcomingCurvature = telemetry.upcomingCurvature,
                weather = telemetry.weatherCondition,
                wiperMode = telemetry.wiperMode,
                wiperSweepNorm = telemetry.wiperSweepNorm,
                simTimeSec = visualClockSec
            )
        }

        // Impact vignette flash on guardrail/traffic/red-light hit
        if (telemetry.collisionFlash > 0.05f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = (8 * telemetry.collisionFlash).dp,
                        color = CrimsonBrake.copy(alpha = 0.75f * telemetry.collisionFlash)
                    )
            )
        }

        // 2. BUSSID-Style Sleek Translucent Perimeter HUD (>82% Open Viewport)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpeedFuelCluster(
                    speedKmh = telemetry.speedKmh,
                    rpm = telemetry.rpm,
                    gearLabel = telemetry.gearLabel,
                    speedLimitKmh = telemetry.speedLimitKmh,
                    speeding = telemetry.speeding,
                    fuelPercent = telemetry.fuelPercent,
                    lowFuelWarning = telemetry.lowFuelWarning,
                    compact = isCompactWidth
                )

                if (!isCompactWidth) {
                    // Wide / Landscape inline translucent route + zone pill
                    Surface(
                        color = Color(0xAA090E16),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = telemetry.activeZoneLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CobaltBeacon,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (route.isFreeRoam) "${"%.1f".format(telemetry.totalDrivenKm)} km"
                                    else "${"%.1f".format(telemetry.remainingDisplayKm)} km",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberDiesel,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Daño ${telemetry.damagePercent}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (telemetry.damagePercent > 15) CrimsonBrake else EmeraldCargo,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            LinearProgressIndicator(
                                progress = { telemetry.progressFraction },
                                color = EmeraldCargo,
                                trackColor = Color(0xFF1E293B),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoadMapMiniPanel(
                        route = route,
                        road = physics.road,
                        progressFraction = telemetry.progressFraction,
                        landmarks = physics.landmarks,
                        remoteConvoyPlayers = remoteConvoyPlayers,
                        onOpenFullMap = { showFullMap = true }
                    )

                    if (!isCompactWidth) {
                        CurveRadarIndicator(curvature = telemetry.upcomingCurvature)
                    }

                    // Camera Cycle Button
                    Surface(
                        onClick = {
                            if (!telemetry.driverOnFoot) {
                                val all = CameraMode.entries
                                cameraMode = all[(all.indexOf(cameraMode) + 1) % all.size]
                            }
                        },
                        color = Color(0xB8090E16),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .border(
                                1.2.dp,
                                if (telemetry.driverOnFoot) Color.White.copy(alpha = 0.08f) else CobaltBeacon.copy(alpha = 0.55f),
                                RoundedCornerShape(14.dp)
                            )
                            .testTag("btn_switch_camera")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Cameraswitch,
                                contentDescription = "Cambiar cámara 2D",
                                tint = if (telemetry.driverOnFoot) Color(0xFF6B7C90) else CobaltBeacon,
                                modifier = Modifier.size(20.dp)
                            )
                            if (!isCompactWidth) {
                                Text(
                                    text = if (telemetry.driverOnFoot) "A Pie 2D" else cameraMode.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Quick Environment & Tools Toggle
                    Surface(
                        onClick = { hudToolsExpanded = !hudToolsExpanded },
                        color = if (hudToolsExpanded) AmberDiesel.copy(alpha = 0.24f) else Color(0xB8090E16),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .size(48.dp)
                            .border(
                                1.2.dp,
                                if (hudToolsExpanded) AmberDiesel else Color.White.copy(alpha = 0.16f),
                                RoundedCornerShape(14.dp)
                            )
                            .testTag("btn_toggle_hud_tools")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = "Herramientas de clima y entorno",
                                tint = if (hudToolsExpanded) AmberDiesel else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Pause Button
                    Surface(
                        onClick = { paused = true },
                        color = Color(0xB8090E16),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .size(48.dp)
                            .border(1.2.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                            .testTag("btn_pause_sim")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = "Pausar simulación",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // Slim compact progress bar for narrow portrait devices
            if (isCompactWidth) {
                Surface(
                    color = Color(0xA4090E16),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (route.isFreeRoam) "${"%.1f".format(telemetry.totalDrivenKm)} km"
                            else "${"%.1f".format(telemetry.remainingDisplayKm)} km",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberDiesel,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = telemetry.activeZoneLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = CobaltBeacon,
                            maxLines = 1
                        )
                        LinearProgressIndicator(
                            progress = { telemetry.progressFraction },
                            color = EmeraldCargo,
                            trackColor = Color(0xFF1E293B),
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(CircleShape)
                        )
                        Text(
                            text = "${telemetry.damagePercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (telemetry.damagePercent > 15) CrimsonBrake else EmeraldCargo,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Collapsible Environment & Camera Tools Strip
            AnimatedVisibility(visible = hudToolsExpanded) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        onClick = {
                            if (!telemetry.driverOnFoot) {
                                val all = CameraMode.entries
                                cameraMode = all[(all.indexOf(cameraMode) + 1) % all.size]
                            }
                        },
                        color = Color(0xCC090E16),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .border(1.dp, CobaltBeacon.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .testTag("badge_camera_mode")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Filled.Visibility, contentDescription = "Cámara 2D activa", tint = CobaltBeacon, modifier = Modifier.size(16.dp))
                            Text(
                                text = if (telemetry.driverOnFoot) "Hombre 2D" else cameraMode.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            physics.cycleTimeOfDayPreset()
                            telemetry = physics.readTelemetry()
                        },
                        color = Color(0xCC090E16),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                            .testTag("btn_cycle_daynight")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Filled.WbSunny, contentDescription = "Cambiar ciclo día y noche", tint = AmberDiesel, modifier = Modifier.size(15.dp))
                            Text(telemetry.dayPhaseLabel, style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }

                    Surface(
                        onClick = {
                            physics.cycleWeatherCondition()
                            telemetry = physics.readTelemetry()
                        },
                        color = Color(0xCC090E16),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                            .testTag("btn_cycle_weather")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                            Text(telemetry.weatherCondition.label, style = MaterialTheme.typography.labelSmall, color = CobaltBeacon, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Row below Top Bar: Windshield Mirror + Direct Hombre 2D button + Active Contextual Prompts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (showMirror && !telemetry.driverOnFoot && !paused) {
                        val nightFrac = if (telemetry.timeOfDay > 0.78f || telemetry.timeOfDay < 0.24f) 0.85f else 0.1f
                        RearviewMirror2D(
                            roadProgressMeters = telemetry.roadProgressMeters,
                            playerLaneOffset = telemetry.laneOffset,
                            traffic = mirrorTraffic,
                            remoteConvoyPlayers = remoteConvoyPlayers,
                            nightFactor = nightFrac
                        )
                    }

                    // Always-accessible Hombre 2D On-Foot Exit Pill
                    if (!telemetry.driverOnFoot) {
                        Surface(
                            onClick = {
                                if (telemetry.canExitTruck) {
                                    throttle = 0f
                                    brake = 0f
                                    steerInput = 0f
                                    if (physics.exitTruckToFoot()) {
                                        telemetry = physics.readTelemetry()
                                    }
                                } else {
                                    exitHintVisible = true
                                }
                            },
                            color = if (telemetry.canExitTruck) AmberDiesel.copy(alpha = 0.28f) else Color(0x9A090E16),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .border(
                                    1.dp,
                                    if (telemetry.canExitTruck) AmberDiesel else Color.White.copy(alpha = 0.16f),
                                    RoundedCornerShape(12.dp)
                                )
                                .testTag("btn_exit_cab_foot")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DirectionsWalk,
                                    contentDescription = "Bajar del camión",
                                    tint = if (telemetry.canExitTruck) AmberDiesel else Color(0xFFB5C4D4),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Hombre",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (telemetry.canExitTruck) AmberDiesel else Color(0xFFB5C4D4),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Contextual Roadside Services, Traffic Light, CB & Alert Pills (Right-aligned)
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val lightState = telemetry.upcomingTrafficLight
                        if (lightState != null) {
                            val lightColor = when (lightState) {
                                TrafficLightState.GREEN -> EmeraldCargo
                                TrafficLightState.AMBER -> AmberDiesel
                                TrafficLightState.RED -> CrimsonBrake
                            }
                            val lightText = when (lightState) {
                                TrafficLightState.GREEN -> "VERDE"
                                TrafficLightState.AMBER -> "ÁMBAR"
                                TrafficLightState.RED -> "ROJO"
                            }
                            Surface(
                                color = lightColor.copy(alpha = 0.24f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .border(1.2.dp, lightColor, RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Filled.Traffic, contentDescription = "Semáforo", tint = lightColor, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "$lightText ${telemetry.upcomingTrafficLightDistMeters}m",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = lightColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (telemetry.nearFuelStation && !telemetry.driverOnFoot) {
                            Surface(
                                onClick = {
                                    if (physics.refuelAtStation()) {
                                        telemetry = physics.readTelemetry()
                                    }
                                },
                                color = EmeraldCargo.copy(alpha = 0.28f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .border(1.5.dp, EmeraldCargo, RoundedCornerShape(12.dp))
                                    .testTag("btn_refuel_diesel")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Filled.LocalGasStation, contentDescription = "Repostar Diésel", tint = EmeraldCargo, modifier = Modifier.size(17.dp))
                                    Text("REPOSTAR", style = MaterialTheme.typography.labelSmall, color = EmeraldCargo, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (telemetry.nearWorkshop && telemetry.damagePercent > 0 && !telemetry.driverOnFoot) {
                            val repairCost = telemetry.damagePercent * WorkshopCatalog.REPAIR_COST_PER_PERCENT
                            val canPay = (profile?.credits ?: Long.MAX_VALUE) >= repairCost
                            Surface(
                                onClick = {
                                    if (canPay) {
                                        val repaired = physics.repairVehicleAtWorkshop()
                                        if (repaired >= 0) {
                                            onRoadCreditAdjustment(-repairCost, 0)
                                            telemetry = physics.readTelemetry()
                                        }
                                    }
                                },
                                color = Color(0xFF06B6D4).copy(alpha = 0.26f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .border(1.5.dp, Color(0xFF06B6D4), RoundedCornerShape(12.dp))
                                    .testTag("btn_roadside_repair")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Filled.Build, contentDescription = "Reparar en taller", tint = Color(0xFF06B6D4), modifier = Modifier.size(17.dp))
                                    Text(
                                        text = if (canPay) "REPARAR (-$$repairCost)" else "SIN CRÉDITOS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF06B6D4),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (telemetry.nearParkingBay && !telemetry.driverOnFoot) {
                            Surface(
                                onClick = {
                                    if (physics.restAtParkingBay()) {
                                        telemetry = physics.readTelemetry()
                                    }
                                },
                                color = CobaltBeacon.copy(alpha = 0.26f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .border(1.5.dp, CobaltBeacon, RoundedCornerShape(12.dp))
                                    .testTag("btn_parking_rest")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Filled.Hotel, contentDescription = "Descansar en estacionamiento", tint = CobaltBeacon, modifier = Modifier.size(17.dp))
                                    Text("DESCANSAR", style = MaterialTheme.typography.labelSmall, color = CobaltBeacon, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (activeConvoyRoomId != null) {
                            Surface(
                                onClick = { showConvoyRadio = true },
                                color = AmberDiesel.copy(alpha = 0.26f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .border(1.2.dp, AmberDiesel, RoundedCornerShape(12.dp))
                                    .testTag("btn_convoy_radio")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(Icons.Filled.Groups, contentDescription = "Radio Convoy", tint = AmberDiesel, modifier = Modifier.size(17.dp))
                                    Text("CB (${allConvoyPlayers.size})", style = MaterialTheme.typography.labelSmall, color = AmberDiesel, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = activeAlertBanner != null) {
                        Surface(
                            color = Color(0xE0090E16),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.border(1.dp, AmberDiesel, RoundedCornerShape(10.dp))
                        ) {
                            Text(
                                text = activeAlertBanner ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberDiesel,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                    AnimatedVisibility(visible = exitHintVisible) {
                        Surface(
                            color = CrimsonBrake.copy(alpha = 0.90f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Detén el camión para bajar a pie",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Bottom Precision Controls Overlay (Truck Driving vs On-Foot Hombre)
        if (!telemetry.finished && !paused) {
            if (telemetry.driverOnFoot) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    DriverWalkJoystick(
                        onMoveChanged = { x, y ->
                            walkMoveX = x
                            walkMoveY = y
                        }
                    )
                    OnFootActionCluster(
                        sprint = sprint,
                        nearCabDoor = telemetry.nearCabDoor,
                        onSprintChanged = { sprint = it },
                        onBoardTruck = {
                            if (physics.boardTruckFromFoot()) {
                                walkMoveX = 0f
                                walkMoveY = 0f
                                sprint = false
                                telemetry = physics.readTelemetry()
                            }
                        }
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AuxiliaryTruckBar(
                        retarderOn = retarderOn,
                        parkingBrake = parkingBrake,
                        headlightsOn = headlightsOn,
                        leftBlinker = leftBlinker,
                        rightBlinker = rightBlinker,
                        wiperMode = telemetry.wiperMode,
                        mirrorsVisible = showMirror,
                        onToggleRetarder = { retarderOn = !retarderOn },
                        onToggleParking = { parkingBrake = !parkingBrake },
                        onToggleLights = { headlightsOn = !headlightsOn },
                        onToggleLeftBlinker = {
                            leftBlinker = !leftBlinker
                            if (leftBlinker) rightBlinker = false
                        },
                        onToggleRightBlinker = {
                            rightBlinker = !rightBlinker
                            if (rightBlinker) leftBlinker = false
                        },
                        onCycleWiper = {
                            physics.cycleWiperMode()
                            telemetry = physics.readTelemetry()
                        },
                        onToggleMirrors = { showMirror = !showMirror },
                        onHorn = {
                            audioEngine.playAirHorn()
                            hornActive = true
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    if (isCompactWidth) {
                        GearShifterModule(
                            gearLabel = telemetry.gearLabel,
                            manualMode = telemetry.manualTransmission,
                            onShiftDown = {
                                physics.shiftGearDown()
                                telemetry = physics.readTelemetry()
                            },
                            onShiftUp = {
                                physics.shiftGearUp()
                                telemetry = physics.readTelemetry()
                            },
                            onToggleMode = {
                                physics.toggleTransmissionMode()
                                telemetry = physics.readTelemetry()
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        when (steeringMode) {
                            SteeringMode.WHEEL -> PrecisionSteeringWheel(
                                sensitivity = sensitivity,
                                onSteerChanged = { steerInput = it }
                            )
                            SteeringMode.BUTTONS -> ProgressiveSteeringButtons(
                                sensitivity = sensitivity,
                                onSteerChanged = { steerInput = it }
                            )
                            SteeringMode.TILT -> TiltIndicatorWidget(
                                steerNorm = telemetry.steerAngleNorm,
                                onRecalibrate = { tiltController.recalibrateZero() }
                            )
                        }

                        if (!isCompactWidth) {
                            GearShifterModule(
                                gearLabel = telemetry.gearLabel,
                                manualMode = telemetry.manualTransmission,
                                onShiftDown = {
                                    physics.shiftGearDown()
                                    telemetry = physics.readTelemetry()
                                },
                                onShiftUp = {
                                    physics.shiftGearUp()
                                    telemetry = physics.readTelemetry()
                                },
                                onToggleMode = {
                                    physics.toggleTransmissionMode()
                                    telemetry = physics.readTelemetry()
                                },
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            AnalogPedal(
                                label = "FRENO / R",
                                accentColor = CrimsonBrake,
                                heightDp = if (isCompactWidth) 114 else 132,
                                widthDp = if (isCompactWidth) 66 else 78,
                                onLevelChanged = { brake = it },
                                modifier = Modifier.testTag("brake_pedal")
                            )
                            AnalogPedal(
                                label = "ACEL",
                                accentColor = EmeraldCargo,
                                heightDp = if (isCompactWidth) 136 else 156,
                                widthDp = if (isCompactWidth) 70 else 82,
                                onLevelChanged = { throttle = it },
                                modifier = Modifier.testTag("throttle_pedal")
                            )
                        }
                    }
                }
            }
        }

        // 4. Pause & Live Control Tuning Overlay
        AnimatedVisibility(
            visible = paused && !telemetry.finished,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianNight.copy(alpha = 0.78f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xEE111924)),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 460.dp)
                        .border(1.2.dp, AmberDiesel.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.pause_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )

                        Text(
                            text = "Modo de Dirección en Vivo",
                            style = MaterialTheme.typography.labelLarge,
                            color = AmberDiesel
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SteeringMode.entries.forEach { mode ->
                                val selected = mode == steeringMode
                                Surface(
                                    onClick = {
                                        steeringMode = mode
                                        steerInput = 0f
                                    },
                                    color = if (selected) AmberDiesel else CabMetal,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                                        Text(
                                            text = mode.label.split(" ").first(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) ObsidianNight else Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Sensibilidad: ${"%.2f".format(sensitivity)}x",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Slider(
                            value = sensitivity,
                            onValueChange = { sensitivity = it },
                            valueRange = 0.4f..2.0f,
                            colors = SliderDefaults.colors(thumbColor = AmberDiesel, activeTrackColor = AmberDiesel)
                        )

                        Button(
                            onClick = { paused = false },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberDiesel, contentColor = ObsidianNight),
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_resume_drive")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(id = R.string.resume_drive), style = MaterialTheme.typography.labelLarge)
                        }

                        if (route.isFreeRoam) {
                            Button(
                                onClick = {
                                    paused = false
                                    physics.finishFreeRoamNow()
                                    telemetry = physics.readTelemetry()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldCargo, contentColor = ObsidianNight),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_finish_freeroam")
                            ) {
                                Text("Cobrar y Terminar Mundo Abierto", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    physics.reset()
                                    handledTolls = 0
                                    handledFines = 0
                                    telemetry = physics.readTelemetry()
                                    paused = false
                                },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Reiniciar")
                            }
                            OutlinedButton(
                                onClick = onExitToHub,
                                modifier = Modifier.weight(1f).height(48.dp).testTag("btn_exit_hub")
                            ) {
                                Text("Despacho")
                            }
                        }
                    }
                }
            }
        }

        // 5. Delivery Completed Summary Overlay
        AnimatedVisibility(
            visible = telemetry.finished,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianNight.copy(alpha = 0.85f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xEE111924)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .border(1.5.dp, EmeraldCargo, RoundedCornerShape(24.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Entrega completada",
                            tint = EmeraldCargo,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = stringResource(id = R.string.delivery_complete_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                        Text(
                            text = "${route.originCity} → ${route.destinationCity} • ${route.cargoName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SummaryStat("Distancia", "${route.displayDistanceKm.toInt()} km", CobaltBeacon)
                            SummaryStat("Tiempo", "${telemetry.elapsedSeconds}s", Color.White)
                            SummaryStat("Daño", "${telemetry.damagePercent}%", if (telemetry.damagePercent == 0) EmeraldCargo else AmberDiesel)
                        }

                        if (deliverySaving) {
                            CircularProgressIndicator(color = AmberDiesel)
                        } else if (deliverySaveError != null) {
                            Text(deliverySaveError ?: "", color = CrimsonBrake, style = MaterialTheme.typography.bodyMedium)
                        } else if (deliverySavedCredits != null) {
                            Surface(
                                color = GoldTrophy.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.border(1.dp, GoldTrophy, RoundedCornerShape(14.dp))
                            ) {
                                Text(
                                    text = "+$${deliverySavedCredits} CRÉDITOS",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = GoldTrophy,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                                )
                            }
                        }

                        Button(
                            onClick = onExitToHub,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberDiesel, contentColor = ObsidianNight),
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("btn_finish_exit")
                        ) {
                            Text(stringResource(id = R.string.exit_to_hub), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // 6. Full Interactive 2D Road Map Modal
        if (showFullMap) {
            RoadMapDialog(
                route = route,
                road = physics.road,
                progressFraction = telemetry.progressFraction,
                landmarks = physics.landmarks,
                fuelPercent = telemetry.fuelPercent,
                remoteConvoyPlayers = remoteConvoyPlayers,
                onDismiss = { showFullMap = false }
            )
        }

        // 7. Live Convoy CB Radio Chat Modal while driving
        if (showConvoyRadio && convoyViewModel != null && profile != null) {
            var cbText by remember { mutableStateOf("") }
            Dialog(onDismissRequest = { showConvoyRadio = false }) {
                Surface(
                    color = SteelSlate,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, AmberDiesel, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("RADIO CB EN RUTA (${allConvoyPlayers.size} camioneros)", style = MaterialTheme.typography.labelLarge, color = AmberDiesel)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (convoyMessages.isEmpty()) {
                                Text("Canal CB abierto y listo.", style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                            } else {
                                convoyMessages.takeLast(6).forEach { msg ->
                                    Text("${msg.senderName}: ${msg.text}", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = cbText,
                                onValueChange = { if (it.length <= 160) cbText = it },
                                label = { Text("Mensaje CB…") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (cbText.isNotBlank()) {
                                        convoyViewModel.sendChatMessage(profile.driverName, cbText)
                                        cbText = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AmberDiesel, contentColor = ObsidianNight),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar CB")
                            }
                        }
                        OutlinedButton(onClick = { showConvoyRadio = false }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Text("Cerrar Radio")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9FB0C3))
        Spacer(Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
    }
}
