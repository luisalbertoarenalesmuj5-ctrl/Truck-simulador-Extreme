package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberDiesel
import com.example.ui.theme.CabMetal
import com.example.ui.theme.CobaltBeacon
import com.example.ui.theme.CrimsonBrake
import com.example.ui.theme.EmeraldCargo
import com.example.ui.theme.GunmetalBorder
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.SteelSlate
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.model.EnvironmentTheme
import com.example.model.RouteSpec
import com.example.model.TruckSpec
import com.example.model.WeatherCondition
import com.example.sim.LandmarkKind
import com.example.sim.RoadSpline
import com.example.sim.Truck2DArtwork.drawTruckRig2DSide
import com.example.sim.WorldLandmark
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun PrecisionSteeringWheel(
    sensitivity: Float,
    onSteerChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var wheelAngleDeg by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(164.dp)
            .testTag("precision_steering_wheel")
            .pointerInput(sensitivity) {
                val center = Offset(size.width / 2f, size.height / 2f)
                awaitPointerEventScope {
                    while (true) {
                        val downEvent = awaitPointerEvent()
                        val firstDown = downEvent.changes.firstOrNull { it.pressed } ?: continue
                        isDragging = true
                        var prevAngle = atan2(
                            firstDown.position.y - center.y,
                            firstDown.position.x - center.x
                        ) * (180f / PI.toFloat())
                        firstDown.consume()

                        while (true) {
                            val moveEvent = awaitPointerEvent()
                            val active = moveEvent.changes.firstOrNull { it.id == firstDown.id }
                            if (active == null || !active.pressed) {
                                isDragging = false
                                wheelAngleDeg = 0f
                                onSteerChanged(0f)
                                break
                            }
                            val curAngle = atan2(
                                active.position.y - center.y,
                                active.position.x - center.x
                            ) * (180f / PI.toFloat())
                            var delta = curAngle - prevAngle
                            if (delta > 180f) delta -= 360f
                            if (delta < -180f) delta += 360f
                            prevAngle = curAngle
                            wheelAngleDeg = (wheelAngleDeg + delta).coerceIn(-270f, 270f)
                            val normalized = ((wheelAngleDeg / 270f) * sensitivity).coerceIn(-1f, 1f)
                            onSteerChanged(normalized)
                            active.consume()
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.43f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Subtle outer precision arc indicator
            drawArc(
                color = Color.White.copy(alpha = 0.14f),
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = Offset(center.x - r - 10f, center.y - r - 10f),
                size = Size((r + 10f) * 2f, (r + 10f) * 2f),
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
            val sweep = (wheelAngleDeg / 270f) * 130f
            if (abs(sweep) > 1f) {
                drawArc(
                    color = AmberDiesel,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(center.x - r - 10f, center.y - r - 10f),
                    size = Size((r + 10f) * 2f, (r + 10f) * 2f),
                    style = Stroke(width = 6f, cap = StrokeCap.Round)
                )
            }

            rotate(degrees = wheelAngleDeg, pivot = center) {
                // Wheel outer leather rim
                drawCircle(
                    color = Color(0xFF141A22),
                    radius = r,
                    center = center,
                    style = Stroke(width = 24f)
                )
                drawCircle(
                    color = Color(0xFF2D3748),
                    radius = r,
                    center = center,
                    style = Stroke(width = 16f)
                )
                // Top amber 12-o'clock center band
                drawArc(
                    color = AmberDiesel,
                    startAngle = -98f,
                    sweepAngle = 16f,
                    useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2f, r * 2f),
                    style = Stroke(width = 18f)
                )
                // Metallic spokes (left, right, bottom)
                val spokeColor = Color(0xFF94A3B8)
                drawLine(spokeColor, Offset(center.x - r + 10f, center.y), Offset(center.x + r - 10f, center.y), strokeWidth = 14f, cap = StrokeCap.Round)
                drawLine(spokeColor, center, Offset(center.x, center.y + r - 10f), strokeWidth = 14f, cap = StrokeCap.Round)
                // Center horn hub
                drawCircle(color = Color(0xFF1E293B), radius = r * 0.30f, center = center)
                drawCircle(color = AmberDiesel, radius = r * 0.14f, center = center, style = Stroke(width = 3f))
            }
        }
    }
}

@Composable
fun ProgressiveSteeringButtons(
    sensitivity: Float,
    onSteerChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SteerPad(
            isLeft = true,
            onPressed = { pressed ->
                onSteerChanged(if (pressed) (-0.85f * sensitivity).coerceIn(-1f, 1f) else 0f)
            },
            modifier = Modifier.testTag("steer_left_button")
        )
        SteerPad(
            isLeft = false,
            onPressed = { pressed ->
                onSteerChanged(if (pressed) (0.85f * sensitivity).coerceIn(-1f, 1f) else 0f)
            },
            modifier = Modifier.testTag("steer_right_button")
        )
    }
}

@Composable
private fun SteerPad(
    isLeft: Boolean,
    onPressed: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var active by remember { mutableStateOf(false) }
    val bg = if (active) AmberDiesel.copy(alpha = 0.85f) else SteelSlate.copy(alpha = 0.85f)
    val fg = if (active) ObsidianNight else Color.White

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = 76.dp, height = 74.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.5.dp, if (active) AmberDiesel else GunmetalBorder, RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        active = true
                        onPressed(true)
                        tryAwaitRelease()
                        active = false
                        onPressed(false)
                    }
                )
            }
    ) {
        Icon(
            imageVector = if (isLeft) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (isLeft) "Girar a la izquierda" else "Girar a la derecha",
            tint = fg,
            modifier = Modifier.size(42.dp)
        )
    }
}

@Composable
fun TiltIndicatorWidget(
    steerNorm: Float,
    onRecalibrate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SteelSlate.copy(alpha = 0.88f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.border(1.dp, GunmetalBorder, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.width(120.dp)) {
                Text(
                    text = "GIROSCOPIO",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberDiesel
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(CabMetal)
                ) {
                    val fraction = ((steerNorm + 1f) * 0.5f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .background(AmberDiesel)
                    )
                }
            }
            Surface(
                onClick = onRecalibrate,
                color = CabMetal,
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Calibrar centro del giroscopio",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AnalogPedal(
    label: String,
    accentColor: Color,
    heightDp: Int,
    widthDp: Int,
    onLevelChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var level by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .size(width = widthDp.dp, height = heightDp.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF283240), Color(0xFF161C24), Color(0xFF0E1319))
                )
            )
            .border(
                width = if (level > 0.05f) 2.dp else 1.5.dp,
                color = if (level > 0.05f) accentColor else GunmetalBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val downEv = awaitPointerEvent()
                        val change = downEv.changes.firstOrNull { it.pressed } ?: continue
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        fun calcLevel(y: Float): Float = (1f - (y / h) * 0.85f).coerceIn(0.25f, 1f)
                        level = calcLevel(change.position.y)
                        onLevelChanged(level)
                        change.consume()

                        while (true) {
                            val ev = awaitPointerEvent()
                            val active = ev.changes.firstOrNull { it.id == change.id }
                            if (active == null || !active.pressed) {
                                level = 0f
                                onLevelChanged(0f)
                                break
                            }
                            level = calcLevel(active.position.y)
                            onLevelChanged(level)
                            active.consume()
                        }
                    }
                }
            }
    ) {
        // Fill bar showing exact analog pedal depression
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(level)
                .background(
                    Brush.verticalGradient(
                        listOf(accentColor.copy(alpha = 0.82f), accentColor.copy(alpha = 0.32f))
                    )
                )
        )
        // Machined rubber-grip pedal ribs + label
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            repeat(5) {
                Box(
                    modifier = Modifier
                        .width((widthDp * 0.62f).dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF090C10))
                        .border(0.5.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun AuxiliaryTruckBar(
    retarderOn: Boolean,
    parkingBrake: Boolean,
    headlightsOn: Boolean,
    leftBlinker: Boolean,
    rightBlinker: Boolean,
    onToggleRetarder: () -> Unit,
    onToggleParking: () -> Unit,
    onToggleLights: () -> Unit,
    onToggleLeftBlinker: () -> Unit,
    onToggleRightBlinker: () -> Unit,
    onHorn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AuxChip(
            label = "IZQ",
            active = leftBlinker,
            activeColor = AmberDiesel,
            onClick = onToggleLeftBlinker,
            testTag = "btn_blinker_left"
        )
        AuxChip(
            label = "DER",
            active = rightBlinker,
            activeColor = AmberDiesel,
            onClick = onToggleRightBlinker,
            testTag = "btn_blinker_right"
        )
        AuxChip(
            icon = Icons.Filled.Highlight,
            label = "LUZ",
            active = headlightsOn,
            activeColor = CobaltBeacon,
            onClick = onToggleLights,
            testTag = "btn_headlights"
        )
        AuxChip(
            icon = Icons.Filled.Speed,
            label = "RET",
            active = retarderOn,
            activeColor = AmberDiesel,
            onClick = onToggleRetarder,
            testTag = "btn_retarder"
        )
        AuxChip(
            icon = Icons.Filled.Lock,
            label = "P",
            active = parkingBrake,
            activeColor = CrimsonBrake,
            onClick = onToggleParking,
            testTag = "btn_parking_brake"
        )
        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onHorn()
            },
            color = CabMetal.copy(alpha = 0.9f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .size(48.dp)
                .border(1.dp, GunmetalBorder, RoundedCornerShape(12.dp))
                .testTag("btn_horn")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Campaign,
                    contentDescription = "Claxon",
                    tint = AmberDiesel,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun AuxChip(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    testTag: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Surface(
        onClick = onClick,
        color = if (active) activeColor.copy(alpha = 0.25f) else SteelSlate.copy(alpha = 0.85f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .height(48.dp)
            .border(1.dp, if (active) activeColor else GunmetalBorder, RoundedCornerShape(12.dp))
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (active) activeColor else Color(0xFF9FB0C3),
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) activeColor else Color(0xFFE6EDF3),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CurveRadarIndicator(
    curvature: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SteelSlate.copy(alpha = 0.86f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .size(56.dp)
            .border(1.dp, GunmetalBorder, RoundedCornerShape(14.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val w = size.width
            val h = size.height
            val curveShift = (curvature * 1400f).coerceIn(-w * 0.42f, w * 0.42f)
            val start = Offset(w * 0.5f, h * 0.9f)
            val mid = Offset(w * 0.5f, h * 0.52f)
            val end = Offset(w * 0.5f + curveShift, h * 0.14f)

            drawLine(Color.White.copy(alpha = 0.35f), start, mid, strokeWidth = 6f, cap = StrokeCap.Round)
            val curveColor = if (abs(curvature) > 0.010f) AmberDiesel else EmeraldCargo
            drawLine(curveColor, mid, end, strokeWidth = 6f, cap = StrokeCap.Round)
            drawCircle(color = CobaltBeacon, radius = 5f, center = start)
        }
    }
}

@Composable
fun DriverWalkJoystick(
    onMoveChanged: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var knobNormX by remember { mutableFloatStateOf(0f) }
    var knobNormY by remember { mutableFloatStateOf(0f) } // +1 up/forward, -1 down/back
    var active by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(156.dp)
            .testTag("driver_walk_joystick")
            .pointerInput(Unit) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = (minOf(size.width, size.height) * 0.36f).coerceAtLeast(1f)
                awaitPointerEventScope {
                    while (true) {
                        val downEv = awaitPointerEvent()
                        val firstDown = downEv.changes.firstOrNull { it.pressed } ?: continue
                        active = true

                        fun updateFrom(pos: Offset) {
                            val dx = pos.x - center.x
                            val dy = pos.y - center.y
                            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                            val clamped = dist.coerceAtMost(maxR)
                            val scale = if (dist > 0.001f) clamped / dist else 0f
                            knobNormX = ((dx * scale) / maxR).coerceIn(-1f, 1f)
                            knobNormY = ((-(dy * scale)) / maxR).coerceIn(-1f, 1f)
                            onMoveChanged(knobNormX, knobNormY)
                        }

                        updateFrom(firstDown.position)
                        firstDown.consume()

                        while (true) {
                            val ev = awaitPointerEvent()
                            val tracked = ev.changes.firstOrNull { it.id == firstDown.id }
                            if (tracked == null || !tracked.pressed) {
                                active = false
                                knobNormX = 0f
                                knobNormY = 0f
                                onMoveChanged(0f, 0f)
                                break
                            }
                            updateFrom(tracked.position)
                            tracked.consume()
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerR = size.minDimension * 0.45f
            val travelR = size.minDimension * 0.34f

            // Outer translucent steel ring
            drawCircle(
                color = ObsidianNight.copy(alpha = 0.72f),
                radius = outerR,
                center = center
            )
            drawCircle(
                color = if (active) AmberDiesel else GunmetalBorder,
                radius = outerR,
                center = center,
                style = Stroke(width = 4f)
            )
            // Crosshair guide lines
            drawLine(
                color = Color.White.copy(alpha = 0.14f),
                start = Offset(center.x - outerR * 0.72f, center.y),
                end = Offset(center.x + outerR * 0.72f, center.y),
                strokeWidth = 2f
            )
            drawLine(
                color = Color.White.copy(alpha = 0.14f),
                start = Offset(center.x, center.y - outerR * 0.72f),
                end = Offset(center.x, center.y + outerR * 0.72f),
                strokeWidth = 2f
            )

            val knobPos = Offset(
                center.x + knobNormX * travelR,
                center.y - knobNormY * travelR
            )
            drawCircle(
                color = if (active) AmberDiesel else CabMetal,
                radius = outerR * 0.38f,
                center = knobPos
            )
            drawCircle(
                color = Color.White.copy(alpha = if (active) 0.9f else 0.35f),
                radius = outerR * 0.38f,
                center = knobPos,
                style = Stroke(width = 3f)
            )
        }
        if (!active) {
            Icon(
                imageVector = Icons.Filled.DirectionsWalk,
                contentDescription = "Joystick para caminar",
                tint = AmberDiesel,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun OnFootActionCluster(
    sprint: Boolean,
    nearCabDoor: Boolean,
    onSprintChanged: (Boolean) -> Unit,
    onBoardTruck: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Hold-to-sprint pad
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 138.dp, height = 52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (sprint) AmberDiesel.copy(alpha = 0.88f) else SteelSlate.copy(alpha = 0.88f))
                .border(1.5.dp, if (sprint) AmberDiesel else GunmetalBorder, RoundedCornerShape(14.dp))
                .testTag("btn_driver_sprint")
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onSprintChanged(true)
                            tryAwaitRelease()
                            onSprintChanged(false)
                        }
                    )
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.DirectionsRun,
                    contentDescription = "Correr",
                    tint = if (sprint) ObsidianNight else AmberDiesel,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (sprint) "CORRIENDO" else "CORRER",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (sprint) ObsidianNight else Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Board truck button
        Button(
            onClick = onBoardTruck,
            enabled = nearCabDoor,
            colors = ButtonDefaults.buttonColors(
                containerColor = EmeraldCargo,
                contentColor = ObsidianNight,
                disabledContainerColor = SteelSlate.copy(alpha = 0.82f),
                disabledContentColor = Color(0xFF9FB0C3)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .height(56.dp)
                .border(
                    width = 1.5.dp,
                    color = if (nearCabDoor) EmeraldCargo else GunmetalBorder,
                    shape = RoundedCornerShape(16.dp)
                )
                .testTag("btn_board_truck")
        ) {
            Icon(
                imageVector = Icons.Filled.LocalShipping,
                contentDescription = "Subir al camión",
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (nearCabDoor) "SUBIR AL CAMIÓN" else "ACÉRCATE A LA PUERTA",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun RealCockpitOverlay(
    speedKmh: Int,
    rpm: Int,
    steerNorm: Float,
    upcomingCurvature: Float,
    weather: WeatherCondition,
    simTimeSec: Float,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        val isPortrait = viewW <= viewH * 1.05f

        val srcW = 1200f
        val srcH = 896f
        val scale = if (isPortrait) {
            viewW / srcW
        } else {
            maxOf(viewW / srcW, viewH / srcH)
        }
        val imgW = srcW * scale
        val imgH = srcH * scale
        val imgLeft = (viewW - imgW) * 0.5f
        val bottomLift = if (isPortrait) with(density) { 58.dp.toPx() } else 0f
        val imgTop = viewH - imgH - bottomLift

        // Micro suspension road bounce proportional to speed
        val bounceY = if (speedKmh > 2) {
            sin(simTimeSec * 18f) * (speedKmh / 100f).coerceIn(0f, 1f) * 3.2f
        } else 0f

        // Seamless dark cabin footwell extension under dashboard on tall portrait screens
        if (isPortrait) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xFF0E1217), Color(0xFF090B0E))
                        )
                    )
            )
            // Realistic upper windshield sun visor & A-pillar edge framing
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xD80A0D12), Color(0x7010161E), Color.Transparent)
                        )
                    )
            )
        }

        // Animated windshield wipers + sun-glare reflection + live dials + steering wheel
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Subtle realistic glass reflection tint across upper windshield
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x1888B8E8), Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.48f
                )
            )

            // Windshield wipers sweeping during Rain or Storm
            if (weather == WeatherCondition.RAIN || weather == WeatherCondition.STORM) {
                val sweepDeg = -18f + (sin(simTimeSec * 3.4f) * 0.5f + 0.5f) * 68f
                val wiperBaseY = (imgTop + imgH * 0.56f).coerceIn(size.height * 0.42f, size.height * 0.72f)
                val wiperLen = (imgW * 0.28f).coerceAtLeast(160f)
                for (bx in floatArrayOf(size.width * 0.28f, size.width * 0.64f)) {
                    val rad = Math.toRadians(sweepDeg.toDouble() - 90.0).toFloat()
                    val ex = bx + cos(rad) * wiperLen
                    val ey = wiperBaseY + sin(rad) * wiperLen
                    drawLine(
                        color = Color(0xCC12151A),
                        start = Offset(bx, wiperBaseY),
                        end = Offset(ex, ey),
                        strokeWidth = 7f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // Genuine photographic semi-truck cabin interior PNG (transparent windshield & mirrors)
        Image(
            painter = painterResource(id = R.drawable.img_real_cockpit),
            contentDescription = "Cabina real de camión",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = imgLeft.roundToInt(),
                        y = (imgTop + bounceY).roundToInt()
                    )
                }
                .size(
                    width = with(density) { imgW.toDp() },
                    height = with(density) { imgH.toDp() }
                )
        )

        // Live illuminated gauge needles, GPS route path & rotating steering wheel with driver hands
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseTop = imgTop + bounceY

            // 1. Live RPM Needle (Left Gauge Dial at 0.355, 0.695)
            val rpmCenter = Offset(imgLeft + imgW * 0.355f, baseTop + imgH * 0.695f)
            val rpmFrac = ((rpm - 500f) / 2000f).coerceIn(0f, 1f)
            val rpmAngleRad = Math.toRadians((135f + rpmFrac * 250f).toDouble()).toFloat()
            val rpmNeedleLen = imgW * 0.025f
            drawLine(
                color = Color(0xFFFF521B),
                start = rpmCenter,
                end = Offset(
                    rpmCenter.x + cos(rpmAngleRad) * rpmNeedleLen,
                    rpmCenter.y + sin(rpmAngleRad) * rpmNeedleLen
                ),
                strokeWidth = (imgW * 0.0032f).coerceAtLeast(2.5f),
                cap = StrokeCap.Round
            )

            // 2. Live Speedometer Needle (Right Gauge Dial at 0.480, 0.675)
            val spdCenter = Offset(imgLeft + imgW * 0.480f, baseTop + imgH * 0.675f)
            val spdFrac = (speedKmh / 125f).coerceIn(0f, 1f)
            val spdAngleRad = Math.toRadians((135f + spdFrac * 250f).toDouble()).toFloat()
            val spdNeedleLen = imgW * 0.025f
            drawLine(
                color = Color(0xFFFF521B),
                start = spdCenter,
                end = Offset(
                    spdCenter.x + cos(spdAngleRad) * spdNeedleLen,
                    spdCenter.y + sin(spdAngleRad) * spdNeedleLen
                ),
                strokeWidth = (imgW * 0.0032f).coerceAtLeast(2.5f),
                cap = StrokeCap.Round
            )

            // 3. Live GPS Curve Line overlay inside dashboard navigation display (0.612, 0.655)
            val gpsCenter = Offset(imgLeft + imgW * 0.612f, baseTop + imgH * 0.652f)
            val gpsH = imgH * 0.055f
            val curveShift = (upcomingCurvature * 2200f).coerceIn(-imgW * 0.025f, imgW * 0.025f)
            drawLine(
                color = EmeraldCargo,
                start = Offset(gpsCenter.x, gpsCenter.y + gpsH * 0.35f),
                end = Offset(gpsCenter.x + curveShift, gpsCenter.y - gpsH * 0.35f),
                strokeWidth = (imgW * 0.0045f).coerceAtLeast(3f),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = AmberDiesel,
                radius = (imgW * 0.0055f).coerceAtLeast(3.5f),
                center = Offset(gpsCenter.x, gpsCenter.y + gpsH * 0.35f)
            )

            // 4. Live Rotating Leather Steering Wheel + Driver Hands mounted on Steering Column (0.462, 0.782)
            val wheelPivot = Offset(imgLeft + imgW * 0.462f, baseTop + imgH * 0.782f)
            val wheelR = imgW * 0.115f
            val wheelRotDeg = steerNorm * 145f

            rotate(degrees = wheelRotDeg, pivot = wheelPivot) {
                // Soft drop shadow on dash behind rim
                drawCircle(
                    color = Color.Black.copy(alpha = 0.42f),
                    radius = wheelR,
                    center = Offset(wheelPivot.x + 3f, wheelPivot.y + 6f),
                    style = Stroke(width = wheelR * 0.17f)
                )
                // Metallic center spokes
                val spokeW = wheelR * 0.15f
                drawLine(
                    color = Color(0xFF475569),
                    start = Offset(wheelPivot.x - wheelR * 0.88f, wheelPivot.y),
                    end = Offset(wheelPivot.x + wheelR * 0.88f, wheelPivot.y),
                    strokeWidth = spokeW,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFF475569),
                    start = wheelPivot,
                    end = Offset(wheelPivot.x, wheelPivot.y + wheelR * 0.88f),
                    strokeWidth = spokeW,
                    cap = StrokeCap.Round
                )
                // Stitched dark leather rim
                drawCircle(
                    color = Color(0xFF15181D),
                    radius = wheelR,
                    center = wheelPivot,
                    style = Stroke(width = wheelR * 0.16f)
                )
                drawCircle(
                    color = Color(0xFF2B313B),
                    radius = wheelR,
                    center = wheelPivot,
                    style = Stroke(width = wheelR * 0.09f)
                )
                // Amber 12-o'clock center ring
                drawArc(
                    color = AmberDiesel,
                    startAngle = -97f,
                    sweepAngle = 14f,
                    useCenter = false,
                    topLeft = Offset(wheelPivot.x - wheelR, wheelPivot.y - wheelR),
                    size = Size(wheelR * 2f, wheelR * 2f),
                    style = Stroke(width = wheelR * 0.16f)
                )
                // Center leather horn pad
                drawCircle(color = Color(0xFF1A1E24), radius = wheelR * 0.28f, center = wheelPivot)
                drawCircle(
                    color = Color(0xFF64748B),
                    radius = wheelR * 0.28f,
                    center = wheelPivot,
                    style = Stroke(width = 2.5f)
                )
                drawCircle(color = AmberDiesel, radius = wheelR * 0.08f, center = wheelPivot)

                // Realistic gloved driver hands gripping left & right sides of the steering rim
                for (side in floatArrayOf(-1f, 1f)) {
                    val handPos = Offset(wheelPivot.x + side * wheelR, wheelPivot.y)
                    drawCircle(
                        color = Color(0xFF231D1A),
                        radius = wheelR * 0.145f,
                        center = handPos
                    )
                    drawCircle(
                        color = Color(0xFF433832),
                        radius = wheelR * 0.105f,
                        center = handPos
                    )
                }
            }
        }
    }
}

@Composable
fun GearShifterModule(
    gearLabel: String,
    manualMode: Boolean,
    onShiftDown: () -> Unit,
    onShiftUp: () -> Unit,
    onToggleMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SteelSlate.copy(alpha = 0.88f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(1.dp, GunmetalBorder, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                onClick = onShiftDown,
                color = CabMetal,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .size(48.dp)
                    .border(1.dp, GunmetalBorder, RoundedCornerShape(10.dp))
                    .testTag("btn_gear_down")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "-",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Surface(
                onClick = onToggleMode,
                color = if (manualMode) AmberDiesel.copy(alpha = 0.22f) else CobaltBeacon.copy(alpha = 0.18f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(48.dp)
                    .border(
                        1.dp,
                        if (manualMode) AmberDiesel else CobaltBeacon,
                        RoundedCornerShape(10.dp)
                    )
                    .testTag("btn_gear_mode")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = gearLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (manualMode) AmberDiesel else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (manualMode) "MANUAL" else "AUTO",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (manualMode) AmberDiesel else CobaltBeacon
                    )
                }
            }

            Surface(
                onClick = onShiftUp,
                color = CabMetal,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .size(48.dp)
                    .border(1.dp, GunmetalBorder, RoundedCornerShape(10.dp))
                    .testTag("btn_gear_up")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.titleLarge,
                        color = EmeraldCargo,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun RoadMapMiniPanel(
    route: RouteSpec,
    road: RoadSpline,
    progressFraction: Float,
    landmarks: List<WorldLandmark>,
    onOpenFullMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onOpenFullMap,
        color = ObsidianNight.copy(alpha = 0.84f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .size(width = 106.dp, height = 56.dp)
            .border(1.dp, CobaltBeacon.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .testTag("btn_road_map")
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val padX = 8f
                val path = Path()
                val samples = 24
                for (i in 0..samples) {
                    val t = i / samples.toFloat()
                    val pt = road.sampleAtDistance(t * route.simLengthMeters)
                    val x = padX + t * (w - padX * 2f)
                    val y = h * 0.55f + (pt.curvature * 1400f).coerceIn(-h * 0.28f, h * 0.28f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = Color(0xFF475569), style = Stroke(width = 5f, cap = StrokeCap.Round))

                // Progress highlight
                val progX = padX + progressFraction.coerceIn(0f, 1f) * (w - padX * 2f)
                val ptNow = road.sampleAtDistance(progressFraction * route.simLengthMeters)
                val progY = h * 0.55f + (ptNow.curvature * 1400f).coerceIn(-h * 0.28f, h * 0.28f)

                // Fuel markers on mini map
                for (lm in landmarks) {
                    if (lm.kind == LandmarkKind.FUEL_STATION) {
                        val lt = (lm.s / route.simLengthMeters).coerceIn(0f, 1f)
                        val fx = padX + lt * (w - padX * 2f)
                        drawCircle(color = AmberDiesel, radius = 3.5f, center = Offset(fx, h * 0.55f))
                    }
                }
                drawCircle(color = EmeraldCargo, radius = 5.5f, center = Offset(progX, progY))
                drawCircle(color = Color.White, radius = 2.5f, center = Offset(progX, progY))
            }
            Text(
                text = "MAPA",
                style = MaterialTheme.typography.labelSmall,
                color = CobaltBeacon,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}

@Composable
fun RoadMapDialog(
    route: RouteSpec,
    road: RoadSpline,
    progressFraction: Float,
    landmarks: List<WorldLandmark>,
    fuelPercent: Int,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = SteelSlate,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, CobaltBeacon, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "MAPA DE CARRETERAS 2D",
                            style = MaterialTheme.typography.labelSmall,
                            color = CobaltBeacon
                        )
                        Text(
                            text = "${route.originCity} → ${route.destinationCity}",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                    }
                    Surface(
                        color = AmberDiesel.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.border(1.dp, AmberDiesel, RoundedCornerShape(10.dp))
                    ) {
                        Text(
                            text = "Diésel $fuelPercent%",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberDiesel,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Detailed curved route radar map
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianNight)
                        .border(1.dp, GunmetalBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val pad = 16f
                        val routePath = Path()
                        val samples = 48
                        for (i in 0..samples) {
                            val t = i / samples.toFloat()
                            val pt = road.sampleAtDistance(t * route.simLengthMeters)
                            val x = pad + t * (w - pad * 2f)
                            val y = h * 0.52f + (pt.curvature * 1900f).coerceIn(-h * 0.32f, h * 0.32f)
                            if (i == 0) routePath.moveTo(x, y) else routePath.lineTo(x, y)
                        }
                        drawPath(routePath, color = Color(0xFF334155), style = Stroke(width = 14f, cap = StrokeCap.Round))
                        drawPath(routePath, color = Color(0xFF64748B), style = Stroke(width = 4f, cap = StrokeCap.Round))

                        // Landmark pins
                        for (lm in landmarks) {
                            val lt = (lm.s / route.simLengthMeters).coerceIn(0f, 1f)
                            val pt = road.sampleAtDistance(lm.s)
                            val lx = pad + lt * (w - pad * 2f)
                            val ly = h * 0.52f + (pt.curvature * 1900f).coerceIn(-h * 0.32f, h * 0.32f)
                            val pinCol = when (lm.kind) {
                                LandmarkKind.FUEL_STATION -> AmberDiesel
                                LandmarkKind.CITY_ORIGIN, LandmarkKind.CITY_DESTINATION -> EmeraldCargo
                                LandmarkKind.CITY_MIDTOWN -> CobaltBeacon
                                else -> Color(0xFF94A3B8)
                            }
                            drawCircle(color = pinCol, radius = 7f, center = Offset(lx, ly))
                        }

                        // Current Truck Position
                        val ptNow = road.sampleAtDistance(progressFraction * route.simLengthMeters)
                        val tx = pad + progressFraction.coerceIn(0f, 1f) * (w - pad * 2f)
                        val ty = h * 0.52f + (ptNow.curvature * 1900f).coerceIn(-h * 0.32f, h * 0.32f)
                        drawCircle(color = Color.White, radius = 11f, center = Offset(tx, ty))
                        drawCircle(color = CobaltBeacon, radius = 7f, center = Offset(tx, ty))
                    }
                }

                Text(
                    text = "• Punto Blanco/Azul: Tu Camión  • Ámbar: Gasolineras Diésel  • Verde: Origen/Destino",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9FB0C3)
                )

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AmberDiesel, contentColor = ObsidianNight),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("CERRAR MAPA", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun Truck2DShowroomPreview(
    truck: TruckSpec,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Subtle 2D showroom spotlight backdrop
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF141D29), Color(0xFF0B1018))
            )
        )
        drawRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(0f, h * 0.80f),
            size = Size(w, h * 0.20f)
        )
        val scale = (w / 6.8f).coerceAtMost(h / 4.4f)
        val anchorX = w * 0.34f
        val groundY = h * 0.82f
        drawTruckRig2DSide(
            truck = truck,
            routeId = "pacific_coast",
            anchorX = anchorX,
            groundY = groundY,
            scale = scale,
            wheelAngleRad = 0f,
            cabPitchRad = 0f,
            bouncePx = 0f,
            headlightsOn = true,
            braking = false,
            reversing = false,
            blinkerOn = false,
            nightFactor = 0.25f,
            throttleLevel = 0f,
            simClock = 0f,
            showTrailer = false
        )
    }
}

@Composable
fun SpeedFuelCluster(
    speedKmh: Int,
    rpm: Int,
    gearLabel: String,
    speedLimitKmh: Int,
    speeding: Boolean,
    fuelPercent: Int,
    lowFuelWarning: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ObsidianNight.copy(alpha = 0.84f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .border(1.dp, GunmetalBorder, RoundedCornerShape(16.dp))
            .testTag("speed_fuel_cluster")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
        ) {
            // Precision 2D Analog Arc Speed & RPM Dial
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(if (compact) 54.dp else 62.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pad = 4f
                    val arcSize = Size(size.width - pad * 2f, size.height - pad * 2f)
                    val topLeft = Offset(pad, pad)
                    drawArc(
                        color = Color(0xFF283240),
                        startAngle = 140f,
                        sweepAngle = 260f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = 6f, cap = StrokeCap.Round)
                    )
                    val speedSweep = ((speedKmh / 125f).coerceIn(0f, 1f)) * 260f
                    drawArc(
                        color = if (speeding) CrimsonBrake else CobaltBeacon,
                        startAngle = 140f,
                        sweepAngle = speedSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = 6f, cap = StrokeCap.Round)
                    )
                    // Inner thin RPM ring
                    val rpmPad = 11f
                    val rpmArc = Size(size.width - rpmPad * 2f, size.height - rpmPad * 2f)
                    val rpmSweep = ((rpm / 2200f).coerceIn(0f, 1f)) * 260f
                    drawArc(
                        color = if (rpm > 1850) CrimsonBrake else AmberDiesel,
                        startAngle = 140f,
                        sweepAngle = rpmSweep,
                        useCenter = false,
                        topLeft = Offset(rpmPad, rpmPad),
                        size = rpmArc,
                        style = Stroke(width = 3f, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$speedKmh",
                        style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        color = if (speeding) CrimsonBrake else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "km/h",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF9FB0C3)
                    )
                }
            }

            // Gear + Diesel Fuel Micro-Gauge
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = AmberDiesel.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.border(1.dp, AmberDiesel, RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = gearLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = AmberDiesel,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        color = Color.White,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(28.dp)
                            .border(2.5.dp, CrimsonBrake, CircleShape)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$speedLimitKmh",
                                style = MaterialTheme.typography.labelSmall,
                                color = ObsidianNight,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                // Compact Diesel Fuel Level Gauge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(54.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(CabMetal)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((fuelPercent / 100f).coerceIn(0f, 1f))
                                .background(if (lowFuelWarning) CrimsonBrake else AmberDiesel)
                        )
                    }
                    Text(
                        text = "$fuelPercent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (lowFuelWarning) CrimsonBrake else Color(0xFFD1DAE4),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}


