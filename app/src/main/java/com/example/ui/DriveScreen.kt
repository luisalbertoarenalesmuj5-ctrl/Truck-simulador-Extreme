package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.R
import com.example.model.CameraMode
import com.example.model.EnvironmentTheme
import com.example.model.RouteSpec
import com.example.model.SteeringMode
import com.example.model.TruckSpec
import com.example.sim.ControlInputs
import com.example.sim.TelemetrySnapshot
import com.example.sim.TiltSteeringController
import com.example.sim.TruckGlViewport
import com.example.sim.TruckPhysicsEngine
import com.example.sim.TruckSimRenderer
import com.example.ui.theme.AmberDiesel
import com.example.ui.theme.CabMetal
import com.example.ui.theme.CobaltBeacon
import com.example.ui.theme.CrimsonBrake
import com.example.ui.theme.EmeraldCargo
import com.example.ui.theme.GoldTrophy
import com.example.ui.theme.GunmetalBorder
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.SteelSlate
import kotlinx.coroutines.launch

@Composable
fun DriveScreen(
    truck: TruckSpec,
    route: RouteSpec,
    initialSteeringMode: SteeringMode,
    initialSensitivity: Float,
    initialCameraMode: CameraMode,
    onSaveDelivery: suspend (damagePercent: Int, durationSeconds: Long) -> Result<Long>,
    onExitToHub: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val physics = remember(truck.id, route.id) { TruckPhysicsEngine(truck, route) }
    val renderer = remember(context, physics) { TruckSimRenderer(context, physics, truck) }
    var visualClockSec by remember { mutableFloatStateOf(0f) }

    var steeringMode by remember { mutableStateOf(initialSteeringMode) }
    var sensitivity by remember { mutableFloatStateOf(initialSensitivity) }
    var cameraMode by remember { mutableStateOf(initialCameraMode) }
    var paused by remember { mutableStateOf(false) }

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
    var walkMoveX by remember { mutableFloatStateOf(0f) }
    var walkMoveY by remember { mutableFloatStateOf(0f) }
    var sprint by remember { mutableStateOf(false) }
    var exitHintVisible by remember { mutableStateOf(false) }

    var telemetry by remember { mutableStateOf(physics.readTelemetry()) }
    var deliverySaving by remember { mutableStateOf(false) }
    var deliverySavedCredits by remember { mutableStateOf<Long?>(null) }
    var deliverySaveError by remember { mutableStateOf<String?>(null) }

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
            kotlinx.coroutines.delay(2200L)
            exitHintVisible = false
        }
    }

    // Push live inputs into physics engine cleanly
    LaunchedEffect(
        throttle, brake, steerInput, retarderOn, parkingBrake,
        headlightsOn, leftBlinker, rightBlinker,
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

        // 1. Full-Bleed Real-Time Viewport
        TruckGlViewport(
            renderer = renderer,
            cameraMode = cameraMode,
            modifier = Modifier.fillMaxSize()
        )

        // 1b. Photorealistic Cabin Interior Overlay in Vista Real (CAB) mode
        if (!telemetry.driverOnFoot && cameraMode == CameraMode.CAB) {
            RealCockpitOverlay(
                speedKmh = telemetry.speedKmh,
                rpm = telemetry.rpm,
                steerNorm = telemetry.steerAngleNorm,
                upcomingCurvature = telemetry.upcomingCurvature,
                environment = route.environment,
                simTimeSec = visualClockSec
            )
        }

        // Impact vignette flash on guardrail/traffic hit
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

        // 2. Top Telemetry & Route Progress HUD
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speedometer + Gear + RPM Cluster
                Surface(
                    color = ObsidianNight.copy(alpha = 0.80f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.border(1.dp, GunmetalBorder, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${telemetry.speedKmh}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = if (telemetry.speeding) CrimsonBrake else Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "km/h",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF9FB0C3),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            // RPM progress bar
                            LinearProgressIndicator(
                                progress = { (telemetry.rpm / 2200f).coerceIn(0f, 1f) },
                                color = if (telemetry.rpm > 1850) CrimsonBrake else AmberDiesel,
                                trackColor = CabMetal,
                                modifier = Modifier
                                    .width(78.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                            )
                        }

                        // Gear indicator badge
                        Surface(
                            color = AmberDiesel.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.border(1.dp, AmberDiesel, RoundedCornerShape(10.dp))
                        ) {
                            Text(
                                text = telemetry.gearLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = AmberDiesel,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        // Speed limit circle
                        Surface(
                            color = Color.White,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(34.dp)
                                .border(3.dp, CrimsonBrake, CircleShape)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${telemetry.speedLimitKmh}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ObsidianNight,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Right Top Quick Buttons (Curve Radar, Camera Switch, Pause)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CurveRadarIndicator(curvature = telemetry.upcomingCurvature)

                    Surface(
                        onClick = {
                            if (!telemetry.driverOnFoot) {
                                val all = CameraMode.entries
                                cameraMode = all[(all.indexOf(cameraMode) + 1) % all.size]
                            }
                        },
                        color = ObsidianNight.copy(alpha = 0.80f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .border(
                                1.dp,
                                if (telemetry.driverOnFoot) GunmetalBorder.copy(alpha = 0.5f) else GunmetalBorder,
                                RoundedCornerShape(14.dp)
                            )
                            .testTag("btn_switch_camera")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Cameraswitch,
                                contentDescription = "Cambiar cámara",
                                tint = if (telemetry.driverOnFoot) Color(0xFF6B7C90) else CobaltBeacon,
                                modifier = Modifier.size(20.dp)
                            )
                            if (!isCompactWidth) {
                                Text(
                                    text = if (telemetry.driverOnFoot) "3ª Persona" else cameraMode.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = { paused = true },
                        color = ObsidianNight.copy(alpha = 0.80f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .size(50.dp)
                            .border(1.dp, GunmetalBorder, RoundedCornerShape(14.dp))
                            .testTag("btn_pause_sim")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = "Pausar simulación",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Route progress + Fuel + Cargo integrity strip
            Surface(
                color = ObsidianNight.copy(alpha = 0.72f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GunmetalBorder.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "%.1f km".format(telemetry.remainingDisplayKm),
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberDiesel,
                        fontWeight = FontWeight.Bold
                    )
                    LinearProgressIndicator(
                        progress = { telemetry.progressFraction },
                        color = EmeraldCargo,
                        trackColor = CabMetal,
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.LocalGasStation,
                            contentDescription = "Combustible",
                            tint = CobaltBeacon,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "${telemetry.fuelPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Daño de carga",
                            tint = if (telemetry.damagePercent > 15) CrimsonBrake else EmeraldCargo,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "${telemetry.damagePercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (telemetry.damagePercent > 15) CrimsonBrake else Color.White
                        )
                    }
                }
            }
            // Quick mode bar: Active Camera badge + Exit Truck ("Bajar / Hombre") trigger
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = {
                        if (!telemetry.driverOnFoot) {
                            val all = CameraMode.entries
                            cameraMode = all[(all.indexOf(cameraMode) + 1) % all.size]
                        }
                    },
                    color = ObsidianNight.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .border(1.dp, CobaltBeacon.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .testTag("badge_camera_mode")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Visibility,
                            contentDescription = "Cámara activa",
                            tint = CobaltBeacon,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (telemetry.driverOnFoot) "Hombre • A Pie" else cameraMode.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

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
                        color = if (telemetry.canExitTruck) AmberDiesel.copy(alpha = 0.22f) else ObsidianNight.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .border(
                                1.dp,
                                if (telemetry.canExitTruck) AmberDiesel else GunmetalBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .testTag("btn_exit_cab_foot")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DirectionsWalk,
                                contentDescription = "Bajar del camión",
                                tint = if (telemetry.canExitTruck) AmberDiesel else Color(0xFF9FB0C3),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Bajar • Hombre",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (telemetry.canExitTruck) AmberDiesel else Color(0xFF9FB0C3),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = exitHintVisible) {
                Surface(
                    color = CrimsonBrake.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.align(Alignment.End)
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AuxiliaryTruckBar(
                        retarderOn = retarderOn,
                        parkingBrake = parkingBrake,
                        headlightsOn = headlightsOn,
                        leftBlinker = leftBlinker,
                        rightBlinker = rightBlinker,
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
                        onHorn = { /* Haptic feedback triggered in AuxiliaryTruckBar */ },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Left: Selected Precision Steering Control
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

                        // Right: Analog Brake / Reverse & Analog Throttle Pedals
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
                    colors = CardDefaults.cardColors(containerColor = SteelSlate),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GunmetalBorder, MaterialTheme.shapes.large)
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

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    physics.reset()
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
                    colors = CardDefaults.cardColors(containerColor = SteelSlate),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, EmeraldCargo, MaterialTheme.shapes.large)
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
                            SummaryStat("Tiempo", "${telemetry.elapsedSeconds}s", CobaltBeacon)
                            SummaryStat(
                                "Daño Carga",
                                "${telemetry.damagePercent}%",
                                if (telemetry.damagePercent == 0) EmeraldCargo else AmberDiesel
                            )
                            SummaryStat("Distancia", "${route.displayDistanceKm.toInt()} km", Color.White)
                        }

                        if (deliverySaving) {
                            CircularProgressIndicator(color = AmberDiesel)
                            Text("Registrando bitácora en la nube…", style = MaterialTheme.typography.bodyMedium)
                        } else if (deliverySaveError != null) {
                            Text(
                                text = deliverySaveError ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CrimsonBrake
                            )
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        deliverySaveError = null
                                        deliverySaving = true
                                        onSaveDelivery(telemetry.damagePercent, telemetry.elapsedSeconds)
                                            .onSuccess { deliverySavedCredits = it }
                                            .onFailure { deliverySaveError = it.localizedMessage }
                                        deliverySaving = false
                                    }
                                }
                            ) {
                                Text("Reintentar guardado")
                            }
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
