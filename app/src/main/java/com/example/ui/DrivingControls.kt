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
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
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
            .background(SteelSlate.copy(alpha = 0.86f))
            .border(
                width = 1.5.dp,
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
                        listOf(accentColor.copy(alpha = 0.78f), accentColor.copy(alpha = 0.35f))
                    )
                )
        )
        // Pedal ridges + label
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .width((widthDp * 0.56f).dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
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
