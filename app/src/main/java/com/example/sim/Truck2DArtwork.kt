package com.example.sim

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.model.TruckCabStyle
import com.example.model.TruckSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object Truck2DArtwork {

    /**
     * Draws a full articulated 2D HD Semi-Truck + Cargo Trailer in Side-Profile (facing +X / right).
     */
    fun DrawScope.drawTruckRig2DSide(
        truck: TruckSpec,
        routeId: String,
        anchorX: Float,
        groundY: Float,
        scale: Float,
        wheelAngleRad: Float,
        cabPitchRad: Float,
        bouncePx: Float,
        headlightsOn: Boolean,
        braking: Boolean,
        reversing: Boolean,
        blinkerOn: Boolean,
        nightFactor: Float,
        throttleLevel: Float,
        simClock: Float,
        rainIntensity: Float = 0f,
        speedMs: Float = 0f,
        driverInsideCab: Boolean = true,
        showTrailer: Boolean = true
    ) {
        val primary = Color(truck.primaryColorRgb[0], truck.primaryColorRgb[1], truck.primaryColorRgb[2])
        val primaryDark = darken(primary, 0.50f)
        val primaryLight = lighten(primary, 0.34f)
        val accent = Color(truck.accentColorRgb[0], truck.accentColorRgb[1], truck.accentColorRgb[2])

        // 1. Multi-layered soft 2D contact shadow + ambient occlusion under chassis
        val rigLeft = if (showTrailer) anchorX - 11.45f * scale else anchorX - 2.65f * scale
        val rigRight = anchorX + 3.60f * scale
        drawOval(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xA8040609), Color(0x52040609), Color.Transparent),
                startY = groundY - 0.16f * scale,
                endY = groundY + 0.36f * scale
            ),
            topLeft = Offset(rigLeft, groundY - 0.15f * scale),
            size = Size(rigRight - rigLeft, 0.48f * scale)
        )

        // 2. Mission-matched 2D HD Trailer (to the left of fifth-wheel anchorX)
        if (showTrailer) {
            withTransform({
                rotate(
                    degrees = Math.toDegrees(cabPitchRad.toDouble() * 0.32).toFloat(),
                    pivot = Offset(anchorX, groundY)
                )
                translate(top = bouncePx * 0.65f)
            }) {
                drawTrailer2DSide(
                    routeId = routeId,
                    fifthWheelX = anchorX,
                    groundY = groundY,
                    scale = scale,
                    wheelAngleRad = wheelAngleRad,
                    braking = braking,
                    reversing = reversing,
                    blinkerOn = blinkerOn,
                    nightFactor = nightFactor,
                    rainIntensity = rainIntensity,
                    speedMs = speedMs,
                    simClock = simClock
                )
            }
        }

        // 3. Tractor Chassis, Air Tanks, Suspension & Cab
        val chassisTopY = groundY - 1.06f * scale
        withTransform({
            rotate(
                degrees = Math.toDegrees(cabPitchRad.toDouble()).toFloat(),
                pivot = Offset(anchorX + 1.0f * scale, groundY - 0.5f * scale)
            )
            translate(top = bouncePx)
        }) {
            // Heavy steel C-channel chassis frame
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF475569), Color(0xFF1E293B), Color(0xFF0F172A))),
                topLeft = Offset(anchorX - 2.15f * scale, chassisTopY),
                size = Size(5.45f * scale, 0.40f * scale),
                cornerRadius = CornerRadius(0.08f * scale, 0.08f * scale)
            )
            // Air suspension bellows + brake chambers
            for (ax in floatArrayOf(-1.56f, -0.48f)) {
                drawRoundRect(
                    color = Color(0xFF111827),
                    topLeft = Offset(anchorX + (ax - 0.14f) * scale, chassisTopY + 0.12f * scale),
                    size = Size(0.28f * scale, 0.36f * scale),
                    cornerRadius = CornerRadius(0.06f * scale, 0.06f * scale)
                )
            }
            // Fifth wheel heavy coupling plate + coiled air brake lines
            drawRoundRect(
                color = Color(0xFF64748B),
                topLeft = Offset(anchorX - 0.55f * scale, chassisTopY - 0.15f * scale),
                size = Size(0.95f * scale, 0.17f * scale),
                cornerRadius = CornerRadius(0.05f * scale, 0.05f * scale)
            )
            if (showTrailer) {
                // Red & Blue coiled pneumatic Suzie hoses behind cab
                for (ci in 0..2) {
                    val hx = anchorX + (0.04f + ci * 0.08f) * scale
                    drawLine(
                        color = if (ci % 2 == 0) Color(0xFFEF4444) else Color(0xFF3B82F6),
                        start = Offset(hx, groundY - 2.05f * scale),
                        end = Offset(hx - 0.25f * scale, chassisTopY - 0.05f * scale),
                        strokeWidth = 0.045f * scale,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Brushed aluminum/chrome side diesel tank + aluminum entry steps + battery box
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF94A3B8), Color(0xFFF8FAFC), Color(0xFFCBD5E1), Color(0xFF475569), Color(0xFF1E293B))
                ),
                topLeft = Offset(anchorX - 0.12f * scale, groundY - 0.94f * scale),
                size = Size(1.38f * scale, 0.46f * scale),
                cornerRadius = CornerRadius(0.18f * scale, 0.18f * scale)
            )
            drawRoundRect(
                color = Color(0xFF1E293B),
                topLeft = Offset(anchorX + 1.30f * scale, groundY - 0.88f * scale),
                size = Size(0.48f * scale, 0.40f * scale),
                cornerRadius = CornerRadius(0.05f * scale, 0.05f * scale)
            )
            // Chrome fuel tank straps + filler cap
            for (sx in floatArrayOf(0.14f, 0.55f, 0.96f)) {
                drawRect(
                    color = Color(0xFF0F172A),
                    topLeft = Offset(anchorX + sx * scale, groundY - 0.94f * scale),
                    size = Size(0.06f * scale, 0.46f * scale)
                )
            }
            drawCircle(color = Color(0xFFE2E8F0), radius = 0.07f * scale, center = Offset(anchorX + 0.34f * scale, groundY - 0.94f * scale))

            // Rear tandem fenders + mudflaps
            for (fx in floatArrayOf(-1.62f, -0.52f)) {
                drawArc(
                    color = Color(0xFF171C23),
                    startAngle = 195f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(anchorX + (fx - 0.55f) * scale, groundY - 1.07f * scale),
                    size = Size(1.10f * scale, 1.10f * scale),
                    style = Stroke(width = 0.11f * scale, cap = StrokeCap.Round)
                )
            }
            drawRect(
                color = Color(0xFF0F1318),
                topLeft = Offset(anchorX - 2.20f * scale, groundY - 0.74f * scale),
                size = Size(0.10f * scale, 0.54f * scale)
            )

            // Chrome Vertical Exhaust Stacks + Heat Shields + Diesel Exhaust Smoke
            val stackCount = if (truck.cabStyle == TruckCabStyle.AMERICAN_CONVENTIONAL) 2 else 1
            for (si in 0 until stackCount) {
                val stackX = anchorX + (0.28f - si * 0.16f) * scale
                val stackTopY = groundY - if (truck.id == "colossus_w900") 3.82f * scale else 3.48f * scale
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF64748B), Color(0xFFF8FAFC), Color(0xFF94A3B8), Color(0xFF334155)),
                        startX = stackX,
                        endX = stackX + 0.14f * scale
                    ),
                    topLeft = Offset(stackX, stackTopY),
                    size = Size(0.14f * scale, (groundY - 0.85f * scale) - stackTopY),
                    cornerRadius = CornerRadius(0.05f * scale, 0.05f * scale)
                )
                drawRect(
                    color = Color(0xFF1E293B),
                    topLeft = Offset(stackX + 0.025f * scale, stackTopY + 0.55f * scale),
                    size = Size(0.09f * scale, 0.85f * scale)
                )
                // Animated diesel exhaust puffs
                if (throttleLevel > 0.14f || ((simClock * 2f).toInt() % 3 == 0)) {
                    val puffCount = if (throttleLevel > 0.25f) 5 else 2
                    for (p in 0 until puffCount) {
                        val phase = ((simClock * (1.8f + throttleLevel * 2.4f) + p * 0.21f) % 1f)
                        val px = stackX - phase * (0.95f + throttleLevel * 1.6f) * scale
                        val py = stackTopY - phase * 0.82f * scale
                        val pr = (0.09f + phase * (0.24f + throttleLevel * 0.26f)) * scale
                        val alpha = ((1f - phase) * (0.14f + throttleLevel * 0.28f)).coerceIn(0f, 0.46f)
                        drawCircle(
                            color = Color(0xFFB8C1CC).copy(alpha = alpha),
                            radius = pr,
                            center = Offset(px, py)
                        )
                    }
                }
            }

            // 4. Cab Bodywork (Euro Cabover vs American Conventional)
            if (truck.cabStyle == TruckCabStyle.EURO_CABOVER) {
                drawEuroCabSide(
                    truck = truck,
                    anchorX = anchorX,
                    groundY = groundY,
                    scale = scale,
                    primary = primary,
                    primaryLight = primaryLight,
                    primaryDark = primaryDark,
                    accent = accent,
                    driverInsideCab = driverInsideCab
                )
            } else {
                drawAmericanCabSide(
                    truck = truck,
                    anchorX = anchorX,
                    groundY = groundY,
                    scale = scale,
                    primary = primary,
                    primaryLight = primaryLight,
                    primaryDark = primaryDark,
                    accent = accent,
                    driverInsideCab = driverInsideCab
                )
            }

            // 5. Headlights, Fog Lamps, Taillights, Blinkers & Volumetric 2D Light Cones
            val frontBumperX = anchorX + 3.28f * scale
            val headY = groundY - 0.86f * scale

            drawRoundRect(
                color = if (headlightsOn) Color(0xFFFFFCE0) else Color(0xFF9EAAB8),
                topLeft = Offset(frontBumperX - 0.14f * scale, headY - 0.12f * scale),
                size = Size(0.16f * scale, 0.20f * scale),
                cornerRadius = CornerRadius(0.04f * scale, 0.04f * scale)
            )
            // Lower fog lamp
            drawCircle(
                color = if (headlightsOn) Color(0xFFFFE082) else Color(0xFF64748B),
                radius = 0.055f * scale,
                center = Offset(frontBumperX - 0.06f * scale, groundY - 0.64f * scale)
            )

            if (headlightsOn) {
                val coneAlpha = (0.20f + nightFactor * 0.45f).coerceIn(0.18f, 0.65f)
                val beamLen = (9.5f + nightFactor * 5.2f) * scale
                val beamPath = Path().apply {
                    moveTo(frontBumperX, headY - 0.06f * scale)
                    lineTo(frontBumperX + beamLen, headY - 1.45f * scale)
                    lineTo(frontBumperX + beamLen, groundY + 0.22f * scale)
                    lineTo(frontBumperX, headY + 0.12f * scale)
                    close()
                }
                drawPath(
                    path = beamPath,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFFFF6CC).copy(alpha = coneAlpha),
                            Color(0xFFFFE599).copy(alpha = coneAlpha * 0.32f),
                            Color.Transparent
                        ),
                        startX = frontBumperX,
                        endX = frontBumperX + beamLen
                    ),
                    blendMode = BlendMode.Screen
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.90f), Color.Transparent),
                        center = Offset(frontBumperX, headY),
                        radius = 0.62f * scale
                    ),
                    radius = 0.62f * scale,
                    center = Offset(frontBumperX, headY),
                    blendMode = BlendMode.Screen
                )
            }

            // Rear frame brake light + blinker
            val rearFrameX = anchorX - 2.15f * scale
            drawRoundRect(
                color = if (braking) Color(0xFFFF2424) else Color(0xFF7D1111),
                topLeft = Offset(rearFrameX, groundY - 0.86f * scale),
                size = Size(0.12f * scale, 0.16f * scale),
                cornerRadius = CornerRadius(0.03f * scale, 0.03f * scale)
            )
            if (blinkerOn) {
                drawCircle(
                    color = Color(0xFFFFA500),
                    radius = 0.12f * scale,
                    center = Offset(frontBumperX - 0.12f * scale, headY + 0.14f * scale)
                )
            }
        }

        // 6. Spinning 10-Lug Alloy Semi Wheels + Brake Discs + Wet Tire Spray
        val wheelR = 0.50f * scale
        val driveAxle1X = anchorX - 1.56f * scale
        val driveAxle2X = anchorX - 0.48f * scale
        val steerAxleX = anchorX + 2.42f * scale

        drawSemiWheel2D(driveAxle1X, groundY - wheelR, wheelR, wheelAngleRad)
        drawSemiWheel2D(driveAxle2X, groundY - wheelR, wheelR, wheelAngleRad)
        drawSemiWheel2D(steerAxleX, groundY - wheelR, wheelR, wheelAngleRad)

        if (rainIntensity > 0.15f && abs(speedMs) > 4f) {
            for (wx in floatArrayOf(driveAxle1X, driveAxle2X, steerAxleX)) {
                drawTireSpray2D(wx - wheelR * 0.8f, groundY, scale, rainIntensity, speedMs, simClock)
            }
        }
    }

    private fun DrawScope.drawEuroCabSide(
        truck: TruckSpec,
        anchorX: Float,
        groundY: Float,
        scale: Float,
        primary: Color,
        primaryLight: Color,
        primaryDark: Color,
        accent: Color,
        driverInsideCab: Boolean
    ) {
        val isHighRoof = truck.id == "valkyrie_s730"
        val roofY = groundY - (if (isHighRoof) 3.70f else 3.44f) * scale
        val cabLeft = anchorX + 0.36f * scale
        val cabRight = anchorX + 3.24f * scale
        val beltY = groundY - 1.88f * scale

        val cabPath = Path().apply {
            moveTo(cabLeft, groundY - 0.85f * scale)
            lineTo(cabLeft, roofY + 0.25f * scale)
            quadraticTo(cabLeft + 0.08f * scale, roofY, cabLeft + 0.40f * scale, roofY)
            lineTo(cabRight - 0.65f * scale, roofY)
            quadraticTo(cabRight - 0.28f * scale, roofY + 0.05f * scale, cabRight - 0.14f * scale, beltY - 0.12f * scale)
            lineTo(cabRight, groundY - 1.10f * scale)
            lineTo(cabRight + 0.06f * scale, groundY - 0.72f * scale)
            lineTo(cabLeft, groundY - 0.72f * scale)
            close()
        }
        drawPath(
            path = cabPath,
            brush = Brush.verticalGradient(
                colors = listOf(primaryLight, primary, primaryDark),
                startY = roofY,
                endY = groundY - 0.70f * scale
            )
        )
        // Specular metallic clearcoat reflection band across cab waist
        drawLine(
            color = Color.White.copy(alpha = 0.20f),
            start = Offset(cabLeft + 0.08f * scale, beltY + 0.06f * scale),
            end = Offset(cabRight - 0.10f * scale, beltY + 0.10f * scale),
            strokeWidth = 0.06f * scale
        )

        // Metallic roof aero deflector spoiler + roof lightbar
        val spoilerPath = Path().apply {
            moveTo(cabLeft + 0.05f * scale, roofY + 0.08f * scale)
            lineTo(cabLeft - 0.28f * scale, roofY - 0.14f * scale)
            lineTo(cabLeft + 0.98f * scale, roofY)
            close()
        }
        drawPath(spoilerPath, primaryDark)

        // Fleet accent stripe
        val stripePath = Path().apply {
            moveTo(cabLeft + 0.04f * scale, beltY + 0.18f * scale)
            lineTo(cabRight - 0.06f * scale, beltY + 0.26f * scale)
            lineTo(cabRight - 0.04f * scale, beltY + 0.42f * scale)
            lineTo(cabLeft + 0.04f * scale, beltY + 0.34f * scale)
            close()
        }
        drawPath(stripePath, accent.copy(alpha = 0.92f))

        // Tinted Windshield + Side Window + Driver Inside Silhouette
        val windPath = Path().apply {
            moveTo(cabRight - 0.64f * scale, roofY + 0.20f * scale)
            lineTo(cabRight - 0.26f * scale, roofY + 0.24f * scale)
            lineTo(cabRight - 0.10f * scale, beltY - 0.06f * scale)
            lineTo(cabRight - 0.64f * scale, beltY - 0.06f * scale)
            close()
        }
        drawPath(
            path = windPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF172738), Color(0xFF3E6182), Color(0xFF0E1822)),
                start = Offset(cabRight - 0.64f * scale, roofY + 0.20f * scale),
                end = Offset(cabRight - 0.10f * scale, beltY)
            )
        )
        drawLine(
            color = Color.White.copy(alpha = 0.32f),
            start = Offset(cabRight - 0.50f * scale, roofY + 0.26f * scale),
            end = Offset(cabRight - 0.28f * scale, beltY - 0.12f * scale),
            strokeWidth = 0.08f * scale
        )

        val winLeft = cabLeft + 1.05f * scale
        val winTop = roofY + 0.38f * scale
        val winW = 1.06f * scale
        val winH = (beltY - 0.12f * scale) - winTop
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF283E56), Color(0xFF101924))),
            topLeft = Offset(winLeft, winTop),
            size = Size(winW, winH),
            cornerRadius = CornerRadius(0.08f * scale, 0.08f * scale)
        )
        if (driverInsideCab) {
            // Driver silhouette in seat wearing cap & orange vest
            val drvX = winLeft + winW * 0.52f
            val drvY = winTop + winH * 0.56f
            drawCircle(Color(0xFF0B1118), radius = 0.15f * scale, center = Offset(drvX, drvY - 0.14f * scale))
            drawRoundRect(
                color = Color(0xFFEA580C).copy(alpha = 0.72f),
                topLeft = Offset(drvX - 0.16f * scale, drvY),
                size = Size(0.32f * scale, winH * 0.42f),
                cornerRadius = CornerRadius(0.06f * scale, 0.06f * scale)
            )
        }

        // Door seam, grab bar & chrome handle
        drawRoundRect(
            color = Color(0x55000000),
            topLeft = Offset(cabLeft + 0.96f * scale, roofY + 0.30f * scale),
            size = Size(1.26f * scale, (groundY - 0.95f * scale) - (roofY + 0.30f * scale)),
            cornerRadius = CornerRadius(0.08f * scale, 0.08f * scale),
            style = Stroke(width = 0.025f * scale)
        )
        drawRect(
            color = Color(0xFFE2E8F0),
            topLeft = Offset(cabLeft + 1.10f * scale, beltY + 0.16f * scale),
            size = Size(0.18f * scale, 0.06f * scale)
        )
        // Entry steps recessed cutouts
        for (st in 0..1) {
            drawRect(
                color = Color(0xFF0F172A),
                topLeft = Offset(cabLeft + 1.55f * scale, groundY - (1.10f - st * 0.22f) * scale),
                size = Size(0.42f * scale, 0.09f * scale)
            )
        }

        // Front Grille + Bumper + Mirror
        drawRoundRect(
            color = Color(0xFF1A2028),
            topLeft = Offset(cabRight - 0.35f * scale, beltY + 0.08f * scale),
            size = Size(0.36f * scale, 0.56f * scale),
            cornerRadius = CornerRadius(0.06f * scale, 0.06f * scale)
        )
        for (g in 0..3) {
            drawLine(
                color = Color(0xFFCBD5E1),
                start = Offset(cabRight - 0.30f * scale, beltY + (0.16f + g * 0.11f) * scale),
                end = Offset(cabRight - 0.03f * scale, beltY + (0.16f + g * 0.11f) * scale),
                strokeWidth = 0.03f * scale
            )
        }
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF475569), Color(0xFF1E293B))),
            topLeft = Offset(cabRight - 0.45f * scale, groundY - 0.88f * scale),
            size = Size(0.55f * scale, 0.36f * scale),
            cornerRadius = CornerRadius(0.06f * scale, 0.06f * scale)
        )
        drawLine(
            color = Color(0xFF1E242B),
            start = Offset(cabRight - 0.52f * scale, roofY + 0.42f * scale),
            end = Offset(cabRight - 0.30f * scale, roofY + 0.52f * scale),
            strokeWidth = 0.05f * scale
        )
        drawRoundRect(
            color = Color(0xFF12161C),
            topLeft = Offset(cabRight - 0.35f * scale, roofY + 0.45f * scale),
            size = Size(0.14f * scale, 0.44f * scale),
            cornerRadius = CornerRadius(0.04f * scale, 0.04f * scale)
        )
        drawRoundRect(
            color = Color(0xFFFFB020),
            topLeft = Offset(cabRight - 0.72f * scale, roofY + 0.03f * scale),
            size = Size(0.24f * scale, 0.08f * scale),
            cornerRadius = CornerRadius(0.03f * scale, 0.03f * scale)
        )
    }

    private fun DrawScope.drawAmericanCabSide(
        truck: TruckSpec,
        anchorX: Float,
        groundY: Float,
        scale: Float,
        primary: Color,
        primaryLight: Color,
        primaryDark: Color,
        accent: Color,
        driverInsideCab: Boolean
    ) {
        val isClassic = truck.id == "colossus_w900"
        val sleeperTopY = groundY - (if (isClassic) 3.45f else 3.55f) * scale
        val cabLeft = anchorX + 0.22f * scale
        val cowlX = anchorX + 2.02f * scale
        val noseX = anchorX + 3.28f * scale
        val hoodTopY = groundY - (if (isClassic) 2.24f else 2.15f) * scale
        val beltY = groundY - 1.92f * scale

        val cabBody = Path().apply {
            moveTo(cabLeft, groundY - 0.82f * scale)
            lineTo(cabLeft, sleeperTopY + 0.22f * scale)
            quadraticTo(cabLeft + 0.08f * scale, sleeperTopY, cabLeft + 0.36f * scale, sleeperTopY)
            lineTo(cowlX - 0.20f * scale, sleeperTopY)
            quadraticTo(cowlX - 0.02f * scale, sleeperTopY + 0.08f * scale, cowlX, hoodTopY)
            lineTo(noseX - 0.06f * scale, if (isClassic) hoodTopY + 0.08f * scale else hoodTopY + 0.34f * scale)
            lineTo(noseX, groundY - 0.90f * scale)
            lineTo(cabLeft, groundY - 0.74f * scale)
            close()
        }
        drawPath(
            path = cabBody,
            brush = Brush.verticalGradient(
                colors = listOf(primaryLight, primary, primaryDark),
                startY = sleeperTopY,
                endY = groundY - 0.72f * scale
            )
        )

        drawLine(
            color = accent.copy(alpha = 0.92f),
            start = Offset(cabLeft + 0.05f * scale, beltY + 0.14f * scale),
            end = Offset(noseX - 0.08f * scale, beltY + (if (isClassic) 0.14f else 0.26f) * scale),
            strokeWidth = 0.11f * scale
        )

        // Chrome Front Radiator Grille + Classic Bumper
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFFF1F5F9), Color(0xFF94A3B8), Color(0xFF475569))),
            topLeft = Offset(noseX - 0.16f * scale, hoodTopY + (if (isClassic) 0.10f else 0.34f) * scale),
            size = Size(0.18f * scale, 1.05f * scale),
            cornerRadius = CornerRadius(0.04f * scale, 0.04f * scale)
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFFF8FAFC), Color(0xFF64748B))),
            topLeft = Offset(noseX - 0.28f * scale, groundY - 0.88f * scale),
            size = Size(0.38f * scale, 0.36f * scale),
            cornerRadius = CornerRadius(0.06f * scale, 0.06f * scale)
        )

        val windTopY = sleeperTopY + 0.26f * scale
        val windPath = Path().apply {
            moveTo(cowlX - 0.22f * scale, windTopY)
            lineTo(cowlX - 0.03f * scale, windTopY + 0.08f * scale)
            lineTo(cowlX + 0.04f * scale, hoodTopY - 0.02f * scale)
            lineTo(cowlX - 0.24f * scale, hoodTopY - 0.02f * scale)
            close()
        }
        drawPath(
            path = windPath,
            brush = Brush.verticalGradient(listOf(Color(0xFF1D3044), Color(0xFF42668A)))
        )

        val dWinLeft = cowlX - 0.85f * scale
        val dWinTop = windTopY + 0.06f * scale
        val dWinW = 0.56f * scale
        val dWinH = (hoodTopY - 0.06f * scale) - dWinTop
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF283E56), Color(0xFF0E1621))),
            topLeft = Offset(dWinLeft, dWinTop),
            size = Size(dWinW, dWinH),
            cornerRadius = CornerRadius(0.06f * scale, 0.06f * scale)
        )
        if (driverInsideCab) {
            val drvX = dWinLeft + dWinW * 0.52f
            val drvY = dWinTop + dWinH * 0.58f
            drawCircle(Color(0xFF0B1118), radius = 0.14f * scale, center = Offset(drvX, drvY - 0.14f * scale))
            drawRoundRect(
                color = Color(0xFFEA580C).copy(alpha = 0.72f),
                topLeft = Offset(drvX - 0.14f * scale, drvY),
                size = Size(0.28f * scale, dWinH * 0.40f),
                cornerRadius = CornerRadius(0.05f * scale, 0.05f * scale)
            )
        }

        // Sleeper bunk window + chrome air canister
        drawRoundRect(
            color = Color(0xFF162231),
            topLeft = Offset(cabLeft + 0.24f * scale, sleeperTopY + 0.42f * scale),
            size = Size(0.46f * scale, 0.48f * scale),
            cornerRadius = CornerRadius(0.07f * scale, 0.07f * scale)
        )
        drawRoundRect(
            color = Color(0xFFE2E8F0),
            topLeft = Offset(cowlX - 0.30f * scale, sleeperTopY + 0.04f * scale),
            size = Size(0.34f * scale, 0.14f * scale),
            cornerRadius = CornerRadius(0.03f * scale, 0.03f * scale)
        )
        drawRect(
            color = Color(0xFFCBD5E1),
            topLeft = Offset(cowlX - 0.72f * scale, sleeperTopY - 0.10f * scale),
            size = Size(0.42f * scale, 0.08f * scale)
        )
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color(0xFF94A3B8), Color(0xFFF8FAFC), Color(0xFF475569))),
            topLeft = Offset(cowlX - 0.06f * scale, hoodTopY + 0.06f * scale),
            size = Size(0.24f * scale, 0.62f * scale),
            cornerRadius = CornerRadius(0.05f * scale, 0.05f * scale)
        )
    }

    private fun DrawScope.drawTrailer2DSide(
        routeId: String,
        fifthWheelX: Float,
        groundY: Float,
        scale: Float,
        wheelAngleRad: Float,
        braking: Boolean,
        reversing: Boolean,
        blinkerOn: Boolean,
        nightFactor: Float,
        rainIntensity: Float,
        speedMs: Float,
        simClock: Float
    ) {
        val rearX = fifthWheelX - 11.15f * scale
        val frontX = fifthWheelX + 0.35f * scale
        val trailerW = frontX - rearX
        val deckY = groundY - 1.15f * scale

        val wheelR = 0.48f * scale
        drawSemiWheel2D(rearX + 1.25f * scale, groundY - wheelR, wheelR, wheelAngleRad)
        drawSemiWheel2D(rearX + 2.38f * scale, groundY - wheelR, wheelR, wheelAngleRad)
        if (rainIntensity > 0.15f && abs(speedMs) > 4f) {
            drawTireSpray2D(rearX + 0.85f * scale, groundY, scale, rainIntensity, speedMs, simClock)
        }

        // Steel I-beam frame + side underrun bars
        drawRect(
            color = Color(0xFF1F2630),
            topLeft = Offset(rearX + 0.15f * scale, deckY - 0.02f * scale),
            size = Size(trailerW - 0.35f * scale, 0.24f * scale)
        )
        drawRect(
            color = Color(0xFF64748B),
            topLeft = Offset(rearX + 3.4f * scale, deckY + 0.34f * scale),
            size = Size(trailerW * 0.42f, 0.08f * scale)
        )
        drawRect(
            color = Color(0xFF3A434F),
            topLeft = Offset(frontX - 2.3f * scale, deckY + 0.18f * scale),
            size = Size(0.22f * scale, 0.48f * scale)
        )

        when (routeId) {
            "desert_night" -> {
                // Polished Chrome ADR Fuel Tanker + top catwalk rail + hazard diamond
                val tankTopY = groundY - 3.35f * scale
                val tankH = deckY - tankTopY
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFCBD5E1), Color(0xFFFFFFFF), Color(0xFF94A3B8), Color(0xFF475569)),
                        startY = tankTopY,
                        endY = deckY
                    ),
                    topLeft = Offset(rearX + 0.15f * scale, tankTopY),
                    size = Size(trailerW - 0.35f * scale, tankH),
                    cornerRadius = CornerRadius(0.68f * scale, 0.68f * scale)
                )
                // Top safety rail
                drawLine(
                    color = Color(0xFF94A3B8),
                    start = Offset(rearX + 1.2f * scale, tankTopY - 0.12f * scale),
                    end = Offset(frontX - 1.2f * scale, tankTopY - 0.12f * scale),
                    strokeWidth = 0.05f * scale
                )
                for (rx in floatArrayOf(0.22f, 0.50f, 0.78f)) {
                    drawRect(
                        color = Color(0xFF334155),
                        topLeft = Offset(rearX + trailerW * rx, tankTopY + 0.04f * scale),
                        size = Size(0.09f * scale, tankH - 0.08f * scale)
                    )
                }
                drawRect(
                    color = Color(0xFFFF6D00),
                    topLeft = Offset(rearX + trailerW * 0.44f, tankTopY + tankH * 0.40f),
                    size = Size(0.65f * scale, 0.44f * scale)
                )
            }
            "alpine_pass", "storm_summit" -> {
                drawRoundRect(
                    color = Color(0xFF28303B),
                    topLeft = Offset(rearX, deckY - 0.24f * scale),
                    size = Size(trailerW, 0.26f * scale),
                    cornerRadius = CornerRadius(0.04f * scale, 0.04f * scale)
                )
                if (routeId == "alpine_pass") {
                    val exX = rearX + trailerW * 0.42f
                    val exBaseY = deckY - 0.24f * scale
                    // Crawler tracks + rollers
                    drawRoundRect(
                        color = Color(0xFF15191F),
                        topLeft = Offset(exX - 1.55f * scale, exBaseY - 0.52f * scale),
                        size = Size(3.10f * scale, 0.52f * scale),
                        cornerRadius = CornerRadius(0.24f * scale, 0.24f * scale)
                    )
                    for (tr in -3..3) {
                        drawCircle(Color(0xFF475569), radius = 0.12f * scale, center = Offset(exX + tr * 0.36f * scale, exBaseY - 0.26f * scale))
                    }
                    // Heavy yellow CAT upper structure
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(Color(0xFFFDE047), Color(0xFFEAB308), Color(0xFFA16207))),
                        topLeft = Offset(exX - 1.35f * scale, exBaseY - 1.42f * scale),
                        size = Size(2.45f * scale, 0.90f * scale),
                        cornerRadius = CornerRadius(0.14f * scale, 0.14f * scale)
                    )
                    drawRoundRect(
                        color = Color(0xFF1E293B),
                        topLeft = Offset(exX + 0.05f * scale, exBaseY - 2.15f * scale),
                        size = Size(0.95f * scale, 0.85f * scale),
                        cornerRadius = CornerRadius(0.10f * scale, 0.10f * scale)
                    )
                    drawRoundRect(
                        color = Color(0xFF38BDF8).copy(alpha = 0.38f),
                        topLeft = Offset(exX + 0.16f * scale, exBaseY - 2.02f * scale),
                        size = Size(0.72f * scale, 0.55f * scale),
                        cornerRadius = CornerRadius(0.06f * scale, 0.06f * scale)
                    )
                    // Hydraulic boom + steel hydraulic piston cylinder
                    drawLine(
                        color = Color(0xFFEAB308),
                        start = Offset(exX + 0.95f * scale, exBaseY - 1.05f * scale),
                        end = Offset(exX + 2.45f * scale, exBaseY - 2.05f * scale),
                        strokeWidth = 0.34f * scale,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFFCBD5E1),
                        start = Offset(exX + 1.10f * scale, exBaseY - 1.35f * scale),
                        end = Offset(exX + 1.95f * scale, exBaseY - 1.85f * scale),
                        strokeWidth = 0.08f * scale
                    )
                    drawLine(
                        color = Color(0xFFEAB308),
                        start = Offset(exX + 2.45f * scale, exBaseY - 2.05f * scale),
                        end = Offset(exX + 3.45f * scale, exBaseY - 0.72f * scale),
                        strokeWidth = 0.28f * scale,
                        cap = StrokeCap.Round
                    )
                    drawArc(
                        color = Color(0xFF334155),
                        startAngle = 10f,
                        sweepAngle = 155f,
                        useCenter = true,
                        topLeft = Offset(exX + 3.05f * scale, exBaseY - 0.95f * scale),
                        size = Size(0.78f * scale, 0.72f * scale)
                    )
                    // Tie-down orange cargo chains
                    drawLine(Color(0xFFF97316), Offset(exX - 1.8f * scale, exBaseY), Offset(exX - 1.2f * scale, exBaseY - 0.45f * scale), strokeWidth = 0.05f * scale)
                    drawLine(Color(0xFFF97316), Offset(exX + 1.8f * scale, exBaseY), Offset(exX + 1.2f * scale, exBaseY - 0.45f * scale), strokeWidth = 0.05f * scale)
                } else {
                    val bladePath = Path().apply {
                        moveTo(rearX + 0.45f * scale, deckY - 0.30f * scale)
                        lineTo(rearX + 0.95f * scale, deckY - 1.55f * scale)
                        lineTo(frontX - 0.55f * scale, deckY - 1.25f * scale)
                        lineTo(frontX - 0.35f * scale, deckY - 0.35f * scale)
                        close()
                    }
                    drawPath(
                        path = bladePath,
                        brush = Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFCBD5E1)))
                    )
                    for (cx in floatArrayOf(0.22f, 0.72f)) {
                        drawRect(
                            color = Color(0xFFF59E0B),
                            topLeft = Offset(rearX + trailerW * cx, deckY - 0.95f * scale),
                            size = Size(0.32f * scale, 0.75f * scale)
                        )
                    }
                }
            }
            else -> {
                val topY = groundY - 3.68f * scale
                val boxH = deckY - topY
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFFFFFF), Color(0xFFE2E8F0), Color(0xFFCBD5E1)),
                        startY = topY,
                        endY = deckY
                    ),
                    topLeft = Offset(rearX, topY),
                    size = Size(trailerW, boxH),
                    cornerRadius = CornerRadius(0.08f * scale, 0.08f * scale)
                )
                val ribSteps = 22
                for (i in 1 until ribSteps) {
                    val rx = rearX + (trailerW / ribSteps) * i
                    drawLine(
                        color = Color(0x2A475569),
                        start = Offset(rx, topY + 0.14f * scale),
                        end = Offset(rx, deckY - 0.24f * scale),
                        strokeWidth = 0.03f * scale
                    )
                }
                drawRoundRect(
                    color = Color(0xFF1E293B),
                    topLeft = Offset(frontX - 0.55f * scale, topY + 0.22f * scale),
                    size = Size(0.55f * scale, 1.15f * scale),
                    cornerRadius = CornerRadius(0.06f * scale, 0.06f * scale)
                )
                drawRect(
                    color = Color(0xFF0284C7),
                    topLeft = Offset(rearX + 0.45f * scale, topY + boxH * 0.34f),
                    size = Size(trailerW - 1.2f * scale, 0.34f * scale)
                )
                val segW = 0.42f * scale
                val count = (trailerW / segW).toInt()
                for (i in 0 until count) {
                    drawRect(
                        color = if (i % 2 == 0) Color(0xFFDC2626) else Color(0xFFF8FAFC),
                        topLeft = Offset(rearX + i * segW, deckY - 0.14f * scale),
                        size = Size(segW * 0.92f, 0.09f * scale)
                    )
                }
            }
        }

        for (mx in floatArrayOf(0.18f, 0.48f, 0.78f)) {
            drawCircle(
                color = if (blinkerOn || nightFactor > 0.35f) Color(0xFFFFB020) else Color(0xFF9A6814),
                radius = 0.065f * scale,
                center = Offset(rearX + trailerW * mx, deckY - 0.05f * scale)
            )
        }

        val tailColor = when {
            reversing -> Color(0xFFF8FAFC)
            braking -> Color(0xFFFF2424)
            else -> Color(0xFF991B1B)
        }
        drawRoundRect(
            color = tailColor,
            topLeft = Offset(rearX - 0.04f * scale, deckY - 0.45f * scale),
            size = Size(0.12f * scale, 0.24f * scale),
            cornerRadius = CornerRadius(0.03f * scale, 0.03f * scale)
        )
        if (braking || nightFactor > 0.35f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF3838).copy(alpha = if (braking) 0.68f else 0.30f), Color.Transparent),
                    center = Offset(rearX, deckY - 0.34f * scale),
                    radius = 0.90f * scale
                ),
                radius = 0.90f * scale,
                center = Offset(rearX, deckY - 0.34f * scale),
                blendMode = BlendMode.Screen
            )
        }
    }

    private fun DrawScope.drawTireSpray2D(
        x: Float,
        groundY: Float,
        scale: Float,
        rainIntensity: Float,
        speedMs: Float,
        simClock: Float
    ) {
        val intensity = ((abs(speedMs) / 25f).coerceIn(0f, 1f) * rainIntensity)
        for (i in 0..3) {
            val phase = ((simClock * 6f + i * 0.25f) % 1f)
            val sx = x - phase * (0.6f + intensity * 1.1f) * scale
            val sy = groundY - phase * 0.45f * scale
            drawCircle(
                color = Color(0xFFD6E8FF).copy(alpha = (1f - phase) * 0.35f * intensity),
                radius = (0.06f + phase * 0.14f) * scale,
                center = Offset(sx, sy)
            )
        }
    }

    fun DrawScope.drawSemiWheel2D(cx: Float, cy: Float, radius: Float, angleRad: Float) {
        // Outer deep rubber tire + tread rim
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF232830), Color(0xFF14181E), Color(0xFF0B0E12)),
                center = Offset(cx, cy),
                radius = radius
            ),
            radius = radius,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Color(0xFF374151),
            radius = radius * 0.78f,
            center = Offset(cx, cy),
            style = Stroke(width = radius * 0.09f)
        )
        // Ventilated steel brake rotor + red brake caliper visible behind alloy rim
        drawCircle(color = Color(0xFF4B5563), radius = radius * 0.48f, center = Offset(cx, cy))
        drawArc(
            color = Color(0xFFDC2626),
            startAngle = -35f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = Offset(cx - radius * 0.42f, cy - radius * 0.42f),
            size = Size(radius * 0.84f, radius * 0.84f),
            style = Stroke(width = radius * 0.14f, cap = StrokeCap.Round)
        )
        // Polished 10-lug semi alloy rim
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFF8FAFC), Color(0xFFCBD5E1), Color(0xFF64748B)),
                center = Offset(cx - radius * 0.1f, cy - radius * 0.1f),
                radius = radius * 0.56f
            ),
            radius = radius * 0.56f,
            center = Offset(cx, cy)
        )
        val deg = Math.toDegrees(angleRad.toDouble()).toFloat()
        rotate(degrees = deg, pivot = Offset(cx, cy)) {
            for (i in 0 until 8) {
                val a = (i / 8f) * 2f * PI.toFloat()
                val hx = cx + cos(a) * radius * 0.40f
                val hy = cy + sin(a) * radius * 0.40f
                drawCircle(color = Color(0xFF1E293B), radius = radius * 0.08f, center = Offset(hx, hy))
            }
            for (i in 0 until 6) {
                val a = (i / 6f) * 2f * PI.toFloat()
                val bx = cx + cos(a) * radius * 0.24f
                val by = cy + sin(a) * radius * 0.24f
                drawCircle(color = Color(0xFFF8FAFC), radius = radius * 0.045f, center = Offset(bx, by))
            }
        }
        drawCircle(color = Color(0xFF334155), radius = radius * 0.15f, center = Offset(cx, cy))
    }

    /**
     * Draws a Top-Down 2D HD Truck Rig + Trailer for CameraMode.TOPDOWN_2D.
     */
    fun DrawScope.drawTruckRig2DTopDown(
        truck: TruckSpec,
        routeId: String,
        cx: Float,
        cy: Float,
        scale: Float,
        headingDeg: Float,
        trailerRelDeg: Float,
        headlightsOn: Boolean,
        braking: Boolean,
        nightFactor: Float
    ) {
        val primary = Color(truck.primaryColorRgb[0], truck.primaryColorRgb[1], truck.primaryColorRgb[2])
        val primaryDark = darken(primary, 0.58f)
        val accent = Color(truck.accentColorRgb[0], truck.accentColorRgb[1], truck.accentColorRgb[2])

        rotate(degrees = headingDeg + trailerRelDeg, pivot = Offset(cx, cy)) {
            val tw = 2.45f * scale
            val tl = 10.6f * scale
            val tTop = cy + 0.35f * scale
            drawRoundRect(
                color = Color(0x75040608),
                topLeft = Offset(cx - tw * 0.5f + 0.35f * scale, tTop + 0.30f * scale),
                size = Size(tw, tl),
                cornerRadius = CornerRadius(0.14f * scale, 0.14f * scale)
            )
            val roofBrush = when (routeId) {
                "desert_night" -> Brush.horizontalGradient(listOf(Color(0xFF64748B), Color(0xFFF8FAFC), Color(0xFF475569)), startX = cx - tw * 0.5f, endX = cx + tw * 0.5f)
                "alpine_pass", "storm_summit" -> Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF475569), Color(0xFF1E293B)), startX = cx - tw * 0.5f, endX = cx + tw * 0.5f)
                else -> Brush.horizontalGradient(listOf(Color(0xFFCBD5E1), Color(0xFFF8FAFC), Color(0xFF94A3B8)), startX = cx - tw * 0.5f, endX = cx + tw * 0.5f)
            }
            drawRoundRect(
                brush = roofBrush,
                topLeft = Offset(cx - tw * 0.5f, tTop),
                size = Size(tw, tl),
                cornerRadius = CornerRadius(0.12f * scale, 0.12f * scale)
            )
            if (routeId == "alpine_pass") {
                drawRoundRect(
                    color = Color(0xFFFACC15),
                    topLeft = Offset(cx - tw * 0.35f, tTop + 2.8f * scale),
                    size = Size(tw * 0.70f, 3.6f * scale),
                    cornerRadius = CornerRadius(0.15f * scale, 0.15f * scale)
                )
            } else if (routeId == "storm_summit") {
                drawRoundRect(
                    color = Color(0xFFF8FAFC),
                    topLeft = Offset(cx - tw * 0.22f, tTop + 0.8f * scale),
                    size = Size(tw * 0.44f, 8.8f * scale),
                    cornerRadius = CornerRadius(0.3f * scale, 0.3f * scale)
                )
            }
            if (braking || nightFactor > 0.35f) {
                for (sx in floatArrayOf(-0.42f, 0.42f)) {
                    drawCircle(
                        color = if (braking) Color(0xFFFF2828) else Color(0xFFB91C1C),
                        radius = 0.22f * scale,
                        center = Offset(cx + tw * sx, tTop + tl)
                    )
                }
            }
        }

        rotate(degrees = headingDeg, pivot = Offset(cx, cy)) {
            val cw = 2.48f * scale
            val cl = 4.6f * scale
            val cabTop = cy - 3.3f * scale

            drawRoundRect(
                color = Color(0x78040608),
                topLeft = Offset(cx - cw * 0.5f + 0.35f * scale, cabTop + 0.35f * scale),
                size = Size(cw, cl),
                cornerRadius = CornerRadius(0.24f * scale, 0.24f * scale)
            )
            if (headlightsOn) {
                val coneAlpha = (0.20f + nightFactor * 0.44f).coerceIn(0.18f, 0.62f)
                val bLen = (11.5f + nightFactor * 4.5f) * scale
                for (lx in floatArrayOf(-0.36f, 0.36f)) {
                    val bx = cx + cw * lx
                    val path = Path().apply {
                        moveTo(bx, cabTop)
                        lineTo(bx - 1.85f * scale, cabTop - bLen)
                        lineTo(bx + 1.85f * scale, cabTop - bLen)
                        close()
                    }
                    drawPath(
                        path = path,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xFFFFF4C2).copy(alpha = coneAlpha)),
                            startY = cabTop - bLen,
                            endY = cabTop
                        ),
                        blendMode = BlendMode.Screen
                    )
                }
            }

            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(primaryDark, primary, primaryDark), startX = cx - cw * 0.5f, endX = cx + cw * 0.5f),
                topLeft = Offset(cx - cw * 0.5f, cabTop),
                size = Size(cw, cl * 0.78f),
                cornerRadius = CornerRadius(0.25f * scale, 0.25f * scale)
            )
            drawRect(
                color = accent,
                topLeft = Offset(cx - cw * 0.14f, cabTop + 0.1f * scale),
                size = Size(cw * 0.28f, cl * 0.66f)
            )
            val glassY = if (truck.cabStyle == TruckCabStyle.EURO_CABOVER) cabTop + 0.26f * scale else cabTop + 1.25f * scale
            drawRoundRect(
                color = Color(0xFF172738),
                topLeft = Offset(cx - cw * 0.42f, glassY),
                size = Size(cw * 0.84f, 0.62f * scale),
                cornerRadius = CornerRadius(0.10f * scale, 0.10f * scale)
            )
            for (sx in floatArrayOf(-0.58f, 0.50f)) {
                drawRect(
                    color = Color(0xFF111827),
                    topLeft = Offset(cx + cw * sx, glassY),
                    size = Size(0.20f * scale, 0.34f * scale)
                )
            }
        }
    }

    /**
     * Draws detailed 2D Traffic Cars, Vans & Rival Semi-Trucks in Side Profile.
     */
    fun DrawScope.drawTrafficVehicle2DSide(
        isTruck: Boolean,
        isOncoming: Boolean,
        bodyColor: Color,
        centerX: Float,
        groundY: Float,
        scale: Float,
        wheelAngleRad: Float,
        nightFactor: Float
    ) {
        val dir = if (isOncoming) -1f else 1f
        val bodyDark = darken(bodyColor, 0.52f)
        val bodyLight = lighten(bodyColor, 0.28f)

        val len = if (isTruck) 7.4f * scale else 4.35f * scale
        val h = if (isTruck) 3.15f * scale else 1.48f * scale
        val leftX = centerX - len * 0.5f
        val rightX = centerX + len * 0.5f

        drawOval(
            color = Color(0x8805070A),
            topLeft = Offset(leftX - 0.15f * scale, groundY - 0.10f * scale),
            size = Size(len + 0.30f * scale, 0.32f * scale)
        )

        if (isTruck) {
            val boxStartX = if (isOncoming) centerX - 1.2f * scale else leftX
            val boxW = len * 0.68f
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFF8FAFC), Color(0xFFCBD5E1))),
                topLeft = Offset(boxStartX, groundY - h),
                size = Size(boxW, h - 0.55f * scale),
                cornerRadius = CornerRadius(0.08f * scale, 0.08f * scale)
            )
            val cabStartX = if (isOncoming) leftX else leftX + boxW + 0.12f * scale
            val cabW = len * 0.29f
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(bodyLight, bodyColor, bodyDark)),
                topLeft = Offset(cabStartX, groundY - h * 0.85f),
                size = Size(cabW, h * 0.85f - 0.45f * scale),
                cornerRadius = CornerRadius(0.14f * scale, 0.14f * scale)
            )
            val winX = if (isOncoming) cabStartX + 0.12f * scale else cabStartX + cabW - 0.72f * scale
            drawRoundRect(
                color = Color(0xFF1E293B),
                topLeft = Offset(winX, groundY - h * 0.76f),
                size = Size(0.60f * scale, 0.68f * scale),
                cornerRadius = CornerRadius(0.06f * scale, 0.06f * scale)
            )
        } else {
            val bodyPath = Path().apply {
                moveTo(leftX, groundY - 0.36f * scale)
                lineTo(leftX + 0.10f * scale, groundY - 0.92f * scale)
                lineTo(centerX - 0.95f * scale * dir, groundY - 0.98f * scale)
                lineTo(centerX - 0.45f * scale * dir, groundY - h)
                lineTo(centerX + 0.55f * scale * dir, groundY - h)
                lineTo(centerX + 1.15f * scale * dir, groundY - 0.96f * scale)
                lineTo(rightX - 0.08f * scale, groundY - 0.85f * scale)
                lineTo(rightX, groundY - 0.36f * scale)
                close()
            }
            drawPath(
                path = bodyPath,
                brush = Brush.verticalGradient(
                    colors = listOf(bodyLight, bodyColor, bodyDark),
                    startY = groundY - h,
                    endY = groundY - 0.35f * scale
                )
            )
            drawRoundRect(
                color = Color(0xFF1A2838),
                topLeft = Offset(centerX - 0.85f * scale, groundY - h + 0.12f * scale),
                size = Size(1.75f * scale, 0.44f * scale),
                cornerRadius = CornerRadius(0.10f * scale, 0.10f * scale)
            )
        }

        val wr = if (isTruck) 0.44f * scale else 0.34f * scale
        drawSemiWheel2D(leftX + len * 0.22f, groundY - wr, wr, wheelAngleRad * dir)
        drawSemiWheel2D(leftX + len * 0.78f, groundY - wr, wr, wheelAngleRad * dir)

        val frontX = if (isOncoming) leftX else rightX
        val rearX = if (isOncoming) rightX else leftX
        drawCircle(
            color = Color(0xFFFFF8D6),
            radius = 0.11f * scale,
            center = Offset(frontX, groundY - 0.66f * scale)
        )
        drawCircle(
            color = Color(0xFFFF3030),
            radius = 0.10f * scale,
            center = Offset(rearX, groundY - 0.68f * scale)
        )
        if (nightFactor > 0.22f || isOncoming) {
            val coneAlpha = (0.14f + nightFactor * 0.36f).coerceIn(0.12f, 0.50f)
            val bLen = 4.8f * scale * dir
            val cone = Path().apply {
                moveTo(frontX, groundY - 0.66f * scale)
                lineTo(frontX + bLen, groundY - 1.25f * scale)
                lineTo(frontX + bLen, groundY + 0.05f * scale)
                close()
            }
            drawPath(
                path = cone,
                brush = Brush.horizontalGradient(
                    colors = if (isOncoming) {
                        listOf(Color.Transparent, Color(0xFFFFF4C2).copy(alpha = coneAlpha))
                    } else {
                        listOf(Color(0xFFFFF4C2).copy(alpha = coneAlpha), Color.Transparent)
                    },
                    startX = minOf(frontX, frontX + bLen),
                    endX = maxOf(frontX, frontX + bLen)
                ),
                blendMode = BlendMode.Screen
            )
        }
    }

    /**
     * Detailed Rear/Front 2D vehicle view for Cabina 2D horizon perspective.
     */
    fun DrawScope.drawTrafficVehicle2DRearFront(
        isTruck: Boolean,
        isOncoming: Boolean,
        bodyColor: Color,
        cx: Float,
        bottomY: Float,
        width: Float,
        nightFactor: Float
    ) {
        val h = width * (if (isTruck) 1.18f else 0.72f)
        val left = cx - width * 0.5f
        val bodyDark = darken(bodyColor, 0.55f)

        // Ground shadow
        drawOval(
            color = Color(0x88040608),
            topLeft = Offset(left - width * 0.08f, bottomY - h * 0.06f),
            size = Size(width * 1.16f, h * 0.16f)
        )
        // Rear/Front tires
        val tireW = width * 0.16f
        val tireH = h * 0.20f
        drawRoundRect(Color(0xFF0F1318), Offset(left + width * 0.06f, bottomY - tireH), Size(tireW, tireH), CornerRadius(3f, 3f))
        drawRoundRect(Color(0xFF0F1318), Offset(left + width * 0.78f, bottomY - tireH), Size(tireW, tireH), CornerRadius(3f, 3f))

        // Main body/box
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(if (isTruck && !isOncoming) Color(0xFFE2E8F0) else bodyColor, bodyDark)),
            topLeft = Offset(left, bottomY - h),
            size = Size(width, h - tireH * 0.45f),
            cornerRadius = CornerRadius(width * 0.08f, width * 0.08f)
        )
        // Rear window / windshield
        drawRoundRect(
            color = Color(0xFF142230),
            topLeft = Offset(left + width * 0.14f, bottomY - h * 0.88f),
            size = Size(width * 0.72f, h * 0.28f),
            cornerRadius = CornerRadius(width * 0.05f, width * 0.05f)
        )
        // Twin lights + glow
        val lightCol = if (isOncoming) Color(0xFFFFF8D6) else Color(0xFFFF2C2C)
        val ly = bottomY - h * 0.34f
        drawCircle(lightCol, radius = (width * 0.075f).coerceAtLeast(2.2f), center = Offset(left + width * 0.16f, ly))
        drawCircle(lightCol, radius = (width * 0.075f).coerceAtLeast(2.2f), center = Offset(left + width * 0.84f, ly))
        if (nightFactor > 0.25f || isOncoming) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(lightCol.copy(alpha = 0.42f), Color.Transparent),
                    center = Offset(cx, ly),
                    radius = width * 0.75f
                ),
                radius = width * 0.75f,
                center = Offset(cx, ly),
                blendMode = BlendMode.Screen
            )
        }
    }

    /**
     * Articulated 2D Trucker Driver ("Hombre") in High-Vis Vest, Cap & Boots.
     */
    fun DrawScope.drawDriverOnFoot2DSide(
        centerX: Float,
        groundY: Float,
        scale: Float,
        facingRight: Boolean,
        walkCycle: Float,
        walkSpeed: Float
    ) {
        val dir = if (facingRight) 1f else -1f
        val swing = sin(walkCycle) * (0.35f + walkSpeed * 0.65f) * 28f
        val bob = abs(sin(walkCycle)) * 0.07f * scale * walkSpeed

        drawOval(
            color = Color(0x8805070A),
            topLeft = Offset(centerX - 0.42f * scale, groundY - 0.10f * scale),
            size = Size(0.84f * scale, 0.22f * scale)
        )

        val hipY = groundY - 0.88f * scale - bob
        val shoulderY = groundY - 1.46f * scale - bob

        rotate(degrees = -swing, pivot = Offset(centerX, hipY)) {
            drawLine(
                color = Color(0xFF1E3A5F),
                start = Offset(centerX, hipY),
                end = Offset(centerX, groundY - 0.14f * scale - bob),
                strokeWidth = 0.20f * scale,
                cap = StrokeCap.Round
            )
            drawRoundRect(
                color = Color(0xFF3E2723),
                topLeft = Offset(centerX - 0.12f * scale, groundY - 0.18f * scale - bob),
                size = Size(0.34f * scale, 0.16f * scale),
                cornerRadius = CornerRadius(0.05f * scale, 0.05f * scale)
            )
        }

        rotate(degrees = swing, pivot = Offset(centerX, hipY)) {
            drawLine(
                color = Color(0xFF254B7A),
                start = Offset(centerX, hipY),
                end = Offset(centerX, groundY - 0.14f * scale - bob),
                strokeWidth = 0.22f * scale,
                cap = StrokeCap.Round
            )
            drawRoundRect(
                color = Color(0xFF4E342E),
                topLeft = Offset(centerX - 0.12f * scale, groundY - 0.18f * scale - bob),
                size = Size(0.36f * scale, 0.16f * scale),
                cornerRadius = CornerRadius(0.05f * scale, 0.05f * scale)
            )
        }

        drawRoundRect(
            color = Color(0xFF1F2937),
            topLeft = Offset(centerX - 0.22f * scale, shoulderY),
            size = Size(0.44f * scale, 0.62f * scale),
            cornerRadius = CornerRadius(0.10f * scale, 0.10f * scale)
        )
        drawRoundRect(
            color = Color(0xFFF97316),
            topLeft = Offset(centerX - 0.20f * scale, shoulderY + 0.06f * scale),
            size = Size(0.40f * scale, 0.48f * scale),
            cornerRadius = CornerRadius(0.08f * scale, 0.08f * scale)
        )
        drawRect(
            color = Color(0xFFE2E8F0),
            topLeft = Offset(centerX - 0.20f * scale, shoulderY + 0.34f * scale),
            size = Size(0.40f * scale, 0.07f * scale)
        )

        val headCy = shoulderY - 0.20f * scale
        drawCircle(color = Color(0xFFE0AC84), radius = 0.18f * scale, center = Offset(centerX, headCy))
        drawArc(
            color = Color(0xFF1E293B),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(centerX - 0.19f * scale, headCy - 0.21f * scale),
            size = Size(0.38f * scale, 0.26f * scale)
        )
        drawLine(
            color = Color(0xFFF59E0B),
            start = Offset(centerX, headCy - 0.08f * scale),
            end = Offset(centerX + 0.28f * scale * dir, headCy - 0.06f * scale),
            strokeWidth = 0.06f * scale,
            cap = StrokeCap.Round
        )

        rotate(degrees = -swing * 0.85f, pivot = Offset(centerX, shoulderY + 0.08f * scale)) {
            drawLine(
                color = Color(0xFF1F2937),
                start = Offset(centerX, shoulderY + 0.08f * scale),
                end = Offset(centerX, hipY - 0.04f * scale),
                strokeWidth = 0.15f * scale,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = Color(0xFFE0AC84),
                radius = 0.07f * scale,
                center = Offset(centerX, hipY - 0.02f * scale)
            )
        }
    }

    fun darken(color: Color, factor: Float): Color = Color(
        red = (color.red * factor).coerceIn(0f, 1f),
        green = (color.green * factor).coerceIn(0f, 1f),
        blue = (color.blue * factor).coerceIn(0f, 1f),
        alpha = color.alpha
    )

    fun lighten(color: Color, amount: Float): Color = Color(
        red = (color.red + (1f - color.red) * amount).coerceIn(0f, 1f),
        green = (color.green + (1f - color.green) * amount).coerceIn(0f, 1f),
        blue = (color.blue + (1f - color.blue) * amount).coerceIn(0f, 1f),
        alpha = color.alpha
    )
}
