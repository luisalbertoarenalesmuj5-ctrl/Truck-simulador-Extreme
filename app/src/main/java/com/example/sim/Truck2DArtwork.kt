package com.example.sim

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.model.CargoTrailerType
import com.example.model.LiveryStyle
import com.example.model.TruckCabStyle
import com.example.model.TruckSpec
import com.example.model.TruckVisualConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object Truck2DArtwork {

    private val convoyTagBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(210, 14, 22, 34)
    }
    private val convoyTagBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(56, 189, 248)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val convoyTagTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun parseHexColor(hex: String, fallback: Color): Color {
        return try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (_: Throwable) {
            fallback
        }
    }

    fun DrawScope.drawTruckRig2DSide(
        truck: TruckSpec,
        routeId: String,
        visualConfig: TruckVisualConfig = TruckVisualConfig(
            paintHex = truck.defaultPaintHex,
            trailerType = truck.defaultTrailerType
        ),
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
        val fallbackPrimary = Color(truck.primaryColorRgb[0], truck.primaryColorRgb[1], truck.primaryColorRgb[2])
        val primary = parseHexColor(visualConfig.paintHex, fallbackPrimary)
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

        // 2. Mission-matched 2D HD Trailer
        if (showTrailer) {
            withTransform({
                rotate(
                    degrees = Math.toDegrees(cabPitchRad.toDouble() * 0.32).toFloat(),
                    pivot = Offset(anchorX, groundY)
                )
                translate(top = bouncePx * 0.65f)
            }) {
                drawTrailer2DSide(
                    trailerType = visualConfig.trailerType,
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
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF475569), Color(0xFF1E293B), Color(0xFF0F172A))),
                topLeft = Offset(anchorX - 2.15f * scale, chassisTopY),
                size = Size(5.45f * scale, 0.40f * scale),
                cornerRadius = CornerRadius(0.08f * scale, 0.08f * scale)
            )
            for (ax in floatArrayOf(-1.56f, -0.48f)) {
                drawRoundRect(
                    color = Color(0xFF111827),
                    topLeft = Offset(anchorX + (ax - 0.14f) * scale, chassisTopY + 0.12f * scale),
                    size = Size(0.28f * scale, 0.36f * scale),
                    cornerRadius = CornerRadius(0.06f * scale, 0.06f * scale)
                )
            }
            drawRoundRect(
                color = Color(0xFF64748B),
                topLeft = Offset(anchorX - 0.55f * scale, chassisTopY - 0.15f * scale),
                size = Size(0.95f * scale, 0.17f * scale),
                cornerRadius = CornerRadius(0.05f * scale, 0.05f * scale)
            )
            if (showTrailer) {
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

            // Brushed aluminum/chrome diesel tank & steps
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
            for (sx in floatArrayOf(0.14f, 0.55f, 0.96f)) {
                drawRect(
                    color = Color(0xFF0F172A),
                    topLeft = Offset(anchorX + sx * scale, groundY - 0.94f * scale),
                    size = Size(0.06f * scale, 0.46f * scale)
                )
            }
            drawCircle(color = Color(0xFFE2E8F0), radius = 0.07f * scale, center = Offset(anchorX + 0.34f * scale, groundY - 0.94f * scale))

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

            // Chrome Vertical Exhaust Stacks (doubled / taller if chromeExhaustInstalled)
            val stackCount = when {
                visualConfig.chromeExhaustInstalled -> 2
                truck.cabStyle == TruckCabStyle.AMERICAN_CONVENTIONAL -> 2
                else -> 1
            }
            val extraTall = if (visualConfig.chromeExhaustInstalled) 0.38f else 0f
            for (si in 0 until stackCount) {
                val stackX = anchorX + (0.28f - si * 0.16f) * scale
                val baseH = if (truck.id == "colossus_w900") 3.82f else 3.48f
                val stackTopY = groundY - (baseH + extraTall) * scale
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

            // 4. Cab Bodywork + Livery + Accessories
            if (truck.cabStyle == TruckCabStyle.EURO_CABOVER) {
                drawEuroCabSide(
                    truck = truck,
                    visualConfig = visualConfig,
                    anchorX = anchorX,
                    groundY = groundY,
                    scale = scale,
                    primary = primary,
                    primaryLight = primaryLight,
                    primaryDark = primaryDark,
                    accent = accent,
                    driverInsideCab = driverInsideCab,
                    headlightsOn = headlightsOn,
                    nightFactor = nightFactor
                )
            } else {
                drawAmericanCabSide(
                    truck = truck,
                    visualConfig = visualConfig,
                    anchorX = anchorX,
                    groundY = groundY,
                    scale = scale,
                    primary = primary,
                    primaryLight = primaryLight,
                    primaryDark = primaryDark,
                    accent = accent,
                    driverInsideCab = driverInsideCab,
                    headlightsOn = headlightsOn,
                    nightFactor = nightFactor
                )
            }

            // Optional Chrome Bullbar Bumper Guard
            val frontBumperX = anchorX + 3.28f * scale
            if (visualConfig.bullbarInstalled) {
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFFF8FAFC), Color(0xFF94A3B8), Color(0xFF475569))),
                    topLeft = Offset(frontBumperX - 0.04f * scale, groundY - 1.38f * scale),
                    size = Size(0.26f * scale, 0.82f * scale),
                    cornerRadius = CornerRadius(0.06f * scale, 0.06f * scale),
                    style = Stroke(width = 0.08f * scale)
                )
                drawLine(
                    color = Color(0xFFE2E8F0),
                    start = Offset(frontBumperX - 0.02f * scale, groundY - 1.04f * scale),
                    end = Offset(frontBumperX + 0.22f * scale, groundY - 1.04f * scale),
                    strokeWidth = 0.07f * scale
                )
            }

            // 5. Headlights, Fog Lamps, Taillights, Blinkers & Volumetric 2D Light Cones
            val headY = groundY - 0.86f * scale
            drawRoundRect(
                color = if (headlightsOn) Color(0xFFFFFCE0) else Color(0xFF9EAAB8),
                topLeft = Offset(frontBumperX - 0.14f * scale, headY - 0.12f * scale),
                size = Size(0.16f * scale, 0.20f * scale),
                cornerRadius = CornerRadius(0.04f * scale, 0.04f * scale)
            )
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
        visualConfig: TruckVisualConfig,
        anchorX: Float,
        groundY: Float,
        scale: Float,
        primary: Color,
        primaryLight: Color,
        primaryDark: Color,
        accent: Color,
        driverInsideCab: Boolean,
        headlightsOn: Boolean,
        nightFactor: Float
    ) {
        val isHighRoof = truck.id == "valkyrie_s730" || truck.id == "carrier_trans9"
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
        drawLine(
            color = Color.White.copy(alpha = 0.20f),
            start = Offset(cabLeft + 0.08f * scale, beltY + 0.06f * scale),
            end = Offset(cabRight - 0.10f * scale, beltY + 0.10f * scale),
            strokeWidth = 0.06f * scale
        )

        // Aero Roof Deflector Spoiler (extra prominent if aeroSpoilerInstalled)
        val spoilerLift = if (visualConfig.aeroSpoilerInstalled) 0.36f else 0.14f
        val spoilerPath = Path().apply {
            moveTo(cabLeft + 0.05f * scale, roofY + 0.08f * scale)
            lineTo(cabLeft - 0.32f * scale, roofY - spoilerLift * scale)
            lineTo(cabLeft + 1.12f * scale, roofY)
            close()
        }
        drawPath(spoilerPath, if (visualConfig.aeroSpoilerInstalled) accent else primaryDark)

        // Livery Stripe Patterns
        drawCabLiverySide(
            livery = visualConfig.liveryStyle,
            cabLeft = cabLeft,
            cabRight = cabRight,
            roofY = roofY,
            beltY = beltY,
            scale = scale,
            accent = accent
        )

        // Roof Lightbar Accessory
        if (visualConfig.roofLightbarInstalled) {
            val barX = cabRight - 1.15f * scale
            val barY = roofY - 0.18f * scale
            drawRoundRect(
                color = Color(0xFFE2E8F0),
                topLeft = Offset(barX, barY),
                size = Size(0.68f * scale, 0.14f * scale),
                cornerRadius = CornerRadius(0.05f * scale, 0.05f * scale)
            )
            for (li in 0..2) {
                val lx = barX + (0.12f + li * 0.22f) * scale
                drawCircle(
                    color = if (headlightsOn) Color(0xFFFFF59D) else Color(0xFF94A3B8),
                    radius = 0.08f * scale,
                    center = Offset(lx, barY + 0.06f * scale)
                )
            }
            if (headlightsOn && nightFactor > 0.15f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFFFF59D).copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(barX + 0.50f * scale, barY),
                        radius = 0.95f * scale
                    ),
                    radius = 0.95f * scale,
                    center = Offset(barX + 0.50f * scale, barY),
                    blendMode = BlendMode.Screen
                )
            }
        }

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
        for (st in 0..1) {
            drawRect(
                color = Color(0xFF0F172A),
                topLeft = Offset(cabLeft + 1.55f * scale, groundY - (1.10f - st * 0.22f) * scale),
                size = Size(0.42f * scale, 0.09f * scale)
            )
        }

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
        visualConfig: TruckVisualConfig,
        anchorX: Float,
        groundY: Float,
        scale: Float,
        primary: Color,
        primaryLight: Color,
        primaryDark: Color,
        accent: Color,
        driverInsideCab: Boolean,
        headlightsOn: Boolean,
        nightFactor: Float
    ) {
        val isClassic = truck.id == "colossus_w900" || truck.id == "kodiak_flatbed"
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

        if (visualConfig.aeroSpoilerInstalled) {
            val wing = Path().apply {
                moveTo(cabLeft + 0.08f * scale, sleeperTopY + 0.04f * scale)
                lineTo(cabLeft - 0.24f * scale, sleeperTopY - 0.32f * scale)
                lineTo(cabLeft + 0.92f * scale, sleeperTopY)
                close()
            }
            drawPath(wing, accent)
        }

        drawCabLiverySide(
            livery = visualConfig.liveryStyle,
            cabLeft = cabLeft,
            cabRight = noseX - 0.08f * scale,
            roofY = sleeperTopY,
            beltY = beltY,
            scale = scale,
            accent = accent
        )

        if (visualConfig.roofLightbarInstalled) {
            val barX = cowlX - 0.75f * scale
            val barY = sleeperTopY - 0.16f * scale
            drawRoundRect(
                color = Color(0xFFE2E8F0),
                topLeft = Offset(barX, barY),
                size = Size(0.58f * scale, 0.12f * scale),
                cornerRadius = CornerRadius(0.04f * scale, 0.04f * scale)
            )
            for (li in 0..2) {
                drawCircle(
                    color = if (headlightsOn) Color(0xFFFFF59D) else Color(0xFF94A3B8),
                    radius = 0.07f * scale,
                    center = Offset(barX + (0.10f + li * 0.19f) * scale, barY + 0.05f * scale)
                )
            }
            if (headlightsOn && nightFactor > 0.15f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFFFF59D).copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(barX + 0.45f * scale, barY),
                        radius = 0.90f * scale
                    ),
                    radius = 0.90f * scale,
                    center = Offset(barX + 0.45f * scale, barY),
                    blendMode = BlendMode.Screen
                )
            }
        }

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

    private fun DrawScope.drawCabLiverySide(
        livery: LiveryStyle,
        cabLeft: Float,
        cabRight: Float,
        roofY: Float,
        beltY: Float,
        scale: Float,
        accent: Color
    ) {
        when (livery) {
            LiveryStyle.CLASSIC -> {
                val stripePath = Path().apply {
                    moveTo(cabLeft + 0.04f * scale, beltY + 0.18f * scale)
                    lineTo(cabRight - 0.06f * scale, beltY + 0.24f * scale)
                    lineTo(cabRight - 0.04f * scale, beltY + 0.40f * scale)
                    lineTo(cabLeft + 0.04f * scale, beltY + 0.34f * scale)
                    close()
                }
                drawPath(stripePath, accent.copy(alpha = 0.92f))
            }
            LiveryStyle.RACING -> {
                val racingCol = Color(0xFFFACC15)
                drawLine(
                    color = racingCol,
                    start = Offset(cabLeft + 0.08f * scale, beltY + 0.14f * scale),
                    end = Offset(cabRight - 0.08f * scale, beltY + 0.22f * scale),
                    strokeWidth = 0.12f * scale
                )
                drawLine(
                    color = Color.White,
                    start = Offset(cabLeft + 0.08f * scale, beltY + 0.32f * scale),
                    end = Offset(cabRight - 0.08f * scale, beltY + 0.40f * scale),
                    strokeWidth = 0.08f * scale
                )
                for (ci in 0..4) {
                    drawRect(
                        color = if (ci % 2 == 0) racingCol else Color(0xFF0F172A),
                        topLeft = Offset(cabLeft + (0.16f + ci * 0.18f) * scale, beltY + 0.48f * scale),
                        size = Size(0.16f * scale, 0.14f * scale)
                    )
                }
            }
            LiveryStyle.BOLT -> {
                val boltCol = Color(0xFF38BDF8)
                val bolt = Path().apply {
                    moveTo(cabLeft + 0.12f * scale, beltY + 0.56f * scale)
                    lineTo(cabLeft + 0.95f * scale, beltY + 0.06f * scale)
                    lineTo(cabLeft + 1.05f * scale, beltY + 0.24f * scale)
                    lineTo(cabRight - 0.20f * scale, roofY + 0.58f * scale)
                    lineTo(cabLeft + 1.18f * scale, beltY + 0.42f * scale)
                    lineTo(cabLeft + 1.05f * scale, beltY + 0.24f * scale)
                    close()
                }
                drawPath(bolt, boltCol.copy(alpha = 0.92f))
            }
            LiveryStyle.EXECUTIVE -> {
                val gold = Color(0xFFF59E0B)
                drawLine(
                    color = gold,
                    start = Offset(cabLeft + 0.08f * scale, roofY + 0.22f * scale),
                    end = Offset(cabRight - 0.68f * scale, roofY + 0.22f * scale),
                    strokeWidth = 0.06f * scale
                )
                drawLine(
                    color = gold,
                    start = Offset(cabLeft + 0.06f * scale, beltY + 0.26f * scale),
                    end = Offset(cabRight - 0.06f * scale, beltY + 0.28f * scale),
                    strokeWidth = 0.09f * scale
                )
            }
        }
    }

    private fun DrawScope.drawTrailer2DSide(
        trailerType: CargoTrailerType,
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

        when (trailerType) {
            CargoTrailerType.FUEL_TANKER -> {
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
            CargoTrailerType.TIMBER_LOGS -> {
                // Heavy steel bunk platform + vertical stakes + stacked pine logs
                drawRoundRect(
                    color = Color(0xFF28303B),
                    topLeft = Offset(rearX, deckY - 0.24f * scale),
                    size = Size(trailerW, 0.26f * scale),
                    cornerRadius = CornerRadius(0.04f * scale, 0.04f * scale)
                )
                val logRows = 3
                val logCols = 2
                for (row in 0 until logRows) {
                    val ly = deckY - (0.68f + row * 0.48f) * scale
                    val lCol = if (row % 2 == 0) Color(0xFF784B2A) else Color(0xFF5D3A20)
                    for (col in 0 until logCols) {
                        val lx = rearX + (0.35f + col * 5.2f) * scale
                        val lw = 5.0f * scale
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(lighten(lCol, 0.25f), lCol, darken(lCol, 0.6f))),
                            topLeft = Offset(lx, ly),
                            size = Size(lw, 0.44f * scale),
                            cornerRadius = CornerRadius(0.14f * scale, 0.14f * scale)
                        )
                        drawCircle(Color(0xFFD6B083), radius = 0.18f * scale, center = Offset(lx + 0.15f * scale, ly + 0.22f * scale))
                    }
                }
                // Steel vertical log bunks & orange tie chains
                for (bx in floatArrayOf(0.12f, 0.38f, 0.64f, 0.88f)) {
                    val sx = rearX + trailerW * bx
                    drawRect(
                        color = Color(0xFFEA580C),
                        topLeft = Offset(sx, deckY - 2.35f * scale),
                        size = Size(0.16f * scale, 2.15f * scale)
                    )
                }
            }
            CargoTrailerType.CAR_CARRIER -> {
                // Double-deck open steel car transporter carrying 3 colorful 2D cars
                drawRoundRect(
                    color = Color(0xFF1E293B),
                    topLeft = Offset(rearX, deckY - 0.22f * scale),
                    size = Size(trailerW, 0.24f * scale),
                    cornerRadius = CornerRadius(0.04f * scale, 0.04f * scale)
                )
                // Upper steel deck + trusses
                val upperDeckY = deckY - 1.55f * scale
                drawRect(
                    color = Color(0xFF334155),
                    topLeft = Offset(rearX + 0.20f * scale, upperDeckY),
                    size = Size(trailerW - 0.40f * scale, 0.14f * scale)
                )
                for (px in floatArrayOf(0.08f, 0.46f, 0.88f)) {
                    drawRect(
                        color = Color(0xFF475569),
                        topLeft = Offset(rearX + trailerW * px, upperDeckY),
                        size = Size(0.16f * scale, 1.35f * scale)
                    )
                }
                // Draw 2 cars on upper deck + 1 car on lower deck
                val carSpecs = listOf(
                    Triple(rearX + 1.8f * scale, upperDeckY - 0.02f * scale, Color(0xFFEF4444)),
                    Triple(rearX + 6.8f * scale, upperDeckY - 0.02f * scale, Color(0xFF38BDF8)),
                    Triple(rearX + 4.4f * scale, deckY - 0.22f * scale, Color(0xFFFACC15))
                )
                for ((cx, cy, col) in carSpecs) {
                    val cw = 3.5f * scale
                    val ch = 1.05f * scale
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(lighten(col, 0.25f), col, darken(col, 0.55f))),
                        topLeft = Offset(cx - cw * 0.5f, cy - ch * 0.72f),
                        size = Size(cw, ch * 0.52f),
                        cornerRadius = CornerRadius(0.12f * scale, 0.12f * scale)
                    )
                    drawRoundRect(
                        color = Color(0xFF1E293B),
                        topLeft = Offset(cx - cw * 0.28f, cy - ch),
                        size = Size(cw * 0.54f, ch * 0.34f),
                        cornerRadius = CornerRadius(0.10f * scale, 0.10f * scale)
                    )
                    drawCircle(Color(0xFF0F172A), radius = 0.26f * scale, center = Offset(cx - cw * 0.30f, cy - 0.24f * scale))
                    drawCircle(Color(0xFF0F172A), radius = 0.26f * scale, center = Offset(cx + cw * 0.30f, cy - 0.24f * scale))
                }
            }
            CargoTrailerType.FLATBED_EXCAVATOR -> {
                drawRoundRect(
                    color = Color(0xFF28303B),
                    topLeft = Offset(rearX, deckY - 0.24f * scale),
                    size = Size(trailerW, 0.26f * scale),
                    cornerRadius = CornerRadius(0.04f * scale, 0.04f * scale)
                )
                val exX = rearX + trailerW * 0.42f
                val exBaseY = deckY - 0.24f * scale
                drawRoundRect(
                    color = Color(0xFF15191F),
                    topLeft = Offset(exX - 1.55f * scale, exBaseY - 0.52f * scale),
                    size = Size(3.10f * scale, 0.52f * scale),
                    cornerRadius = CornerRadius(0.24f * scale, 0.24f * scale)
                )
                for (tr in -3..3) {
                    drawCircle(Color(0xFF475569), radius = 0.12f * scale, center = Offset(exX + tr * 0.36f * scale, exBaseY - 0.26f * scale))
                }
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
                drawLine(Color(0xFFEAB308), Offset(exX + 0.95f * scale, exBaseY - 1.05f * scale), Offset(exX + 2.45f * scale, exBaseY - 2.05f * scale), strokeWidth = 0.34f * scale, cap = StrokeCap.Round)
                drawLine(Color(0xFFCBD5E1), Offset(exX + 1.10f * scale, exBaseY - 1.35f * scale), Offset(exX + 1.95f * scale, exBaseY - 1.85f * scale), strokeWidth = 0.08f * scale)
                drawLine(Color(0xFFEAB308), Offset(exX + 2.45f * scale, exBaseY - 2.05f * scale), Offset(exX + 3.45f * scale, exBaseY - 0.72f * scale), strokeWidth = 0.28f * scale, cap = StrokeCap.Round)
                drawArc(Color(0xFF334155), startAngle = 10f, sweepAngle = 155f, useCenter = true, topLeft = Offset(exX + 3.05f * scale, exBaseY - 0.95f * scale), size = Size(0.78f * scale, 0.72f * scale))
            }
            CargoTrailerType.WIND_TURBINE -> {
                drawRoundRect(
                    color = Color(0xFF28303B),
                    topLeft = Offset(rearX, deckY - 0.24f * scale),
                    size = Size(trailerW, 0.26f * scale),
                    cornerRadius = CornerRadius(0.04f * scale, 0.04f * scale)
                )
                val bladePath = Path().apply {
                    moveTo(rearX + 0.45f * scale, deckY - 0.30f * scale)
                    lineTo(rearX + 0.95f * scale, deckY - 1.55f * scale)
                    lineTo(frontX - 0.55f * scale, deckY - 1.25f * scale)
                    lineTo(frontX - 0.35f * scale, deckY - 0.35f * scale)
                    close()
                }
                drawPath(bladePath, Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFCBD5E1))))
                for (cx in floatArrayOf(0.22f, 0.72f)) {
                    drawRect(
                        color = Color(0xFFF59E0B),
                        topLeft = Offset(rearX + trailerW * cx, deckY - 0.95f * scale),
                        size = Size(0.32f * scale, 0.75f * scale)
                    )
                }
            }
            CargoTrailerType.AGRI_GRAIN -> {
                val topY = groundY - 3.22f * scale
                val boxH = deckY - topY
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFFE5D299), Color(0xFFD4A74A), Color(0xFF9C7125))),
                    topLeft = Offset(rearX + 0.1f * scale, topY),
                    size = Size(trailerW - 0.2f * scale, boxH),
                    cornerRadius = CornerRadius(0.12f * scale, 0.12f * scale)
                )
                for (i in 1..8) {
                    val rx = rearX + (trailerW / 9f) * i
                    drawRect(Color(0xFF475569), Offset(rx, topY), Size(0.08f * scale, boxH))
                }
                drawRect(Color(0xFF15803D), Offset(rearX + 0.4f * scale, topY + boxH * 0.36f), Size(trailerW - 0.8f * scale, 0.28f * scale))
            }
            CargoTrailerType.REEFER_BOX -> {
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
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF282E38), Color(0xFF151920), Color(0xFF0A0D12)),
                center = Offset(cx - radius * 0.14f, cy - radius * 0.14f),
                radius = radius
            ),
            radius = radius,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Color(0xFF3E4756),
            radius = radius * 0.80f,
            center = Offset(cx, cy),
            style = Stroke(width = radius * 0.09f)
        )
        drawArc(
            color = Color(0x38FFFFFF),
            startAngle = 205f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = Offset(cx - radius * 0.86f, cy - radius * 0.86f),
            size = Size(radius * 1.72f, radius * 1.72f),
            style = Stroke(width = radius * 0.06f, cap = StrokeCap.Round)
        )
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
     * Top-Down / Elevated Forward 2D Truck Rig + Trailer for CameraMode.FRONT_2D.
     */
    fun DrawScope.drawTruckRig2DTopDown(
        truck: TruckSpec,
        visualConfig: TruckVisualConfig = TruckVisualConfig(
            paintHex = truck.defaultPaintHex,
            trailerType = truck.defaultTrailerType
        ),
        cx: Float,
        cy: Float,
        scale: Float,
        headingDeg: Float,
        trailerRelDeg: Float,
        headlightsOn: Boolean,
        braking: Boolean,
        nightFactor: Float
    ) {
        val fallbackPrimary = Color(truck.primaryColorRgb[0], truck.primaryColorRgb[1], truck.primaryColorRgb[2])
        val primary = parseHexColor(visualConfig.paintHex, fallbackPrimary)
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
            val roofBrush = when (visualConfig.trailerType) {
                CargoTrailerType.FUEL_TANKER -> Brush.horizontalGradient(listOf(Color(0xFF64748B), Color(0xFFF8FAFC), Color(0xFF475569)), startX = cx - tw * 0.5f, endX = cx + tw * 0.5f)
                CargoTrailerType.FLATBED_EXCAVATOR, CargoTrailerType.WIND_TURBINE -> Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF475569), Color(0xFF1E293B)), startX = cx - tw * 0.5f, endX = cx + tw * 0.5f)
                CargoTrailerType.TIMBER_LOGS -> Brush.horizontalGradient(listOf(Color(0xFF5D3A20), Color(0xFF8B5A2B), Color(0xFF4B2D17)), startX = cx - tw * 0.5f, endX = cx + tw * 0.5f)
                CargoTrailerType.CAR_CARRIER -> Brush.horizontalGradient(listOf(Color(0xFF1E293B), Color(0xFF334155), Color(0xFF1E293B)), startX = cx - tw * 0.5f, endX = cx + tw * 0.5f)
                CargoTrailerType.AGRI_GRAIN -> Brush.horizontalGradient(listOf(Color(0xFF9C7125), Color(0xFFE5D299), Color(0xFF9C7125)), startX = cx - tw * 0.5f, endX = cx + tw * 0.5f)
                CargoTrailerType.REEFER_BOX -> Brush.horizontalGradient(listOf(Color(0xFFCBD5E1), Color(0xFFF8FAFC), Color(0xFF94A3B8)), startX = cx - tw * 0.5f, endX = cx + tw * 0.5f)
            }
            drawRoundRect(
                brush = roofBrush,
                topLeft = Offset(cx - tw * 0.5f, tTop),
                size = Size(tw, tl),
                cornerRadius = CornerRadius(0.12f * scale, 0.12f * scale)
            )
            if (visualConfig.trailerType == CargoTrailerType.CAR_CARRIER) {
                val cols = listOf(Color(0xFFEF4444), Color(0xFF38BDF8), Color(0xFFFACC15))
                cols.forEachIndexed { idx, col ->
                    drawRoundRect(
                        color = col,
                        topLeft = Offset(cx - tw * 0.36f, tTop + (0.6f + idx * 3.2f) * scale),
                        size = Size(tw * 0.72f, 2.6f * scale),
                        cornerRadius = CornerRadius(0.14f * scale, 0.14f * scale)
                    )
                }
            } else if (visualConfig.trailerType == CargoTrailerType.FLATBED_EXCAVATOR) {
                drawRoundRect(
                    color = Color(0xFFFACC15),
                    topLeft = Offset(cx - tw * 0.35f, tTop + 2.8f * scale),
                    size = Size(tw * 0.70f, 3.6f * scale),
                    cornerRadius = CornerRadius(0.15f * scale, 0.15f * scale)
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
     * Draws 2D Side-Profile Traffic: Cars, Coaches/Buses, Motorcycles & Trucks.
     */
    fun DrawScope.drawTrafficVehicle2DSide(
        vehicleType: TrafficVehicleType,
        isOncoming: Boolean,
        bodyColor: Color,
        centerX: Float,
        groundY: Float,
        scale: Float,
        wheelAngleRad: Float,
        nightFactor: Float,
        braking: Boolean = false
    ) {
        val dir = if (isOncoming) -1f else 1f
        val bodyDark = darken(bodyColor, 0.52f)
        val bodyLight = lighten(bodyColor, 0.28f)

        val len = when (vehicleType) {
            TrafficVehicleType.TRUCK -> 7.4f * scale
            TrafficVehicleType.BUS -> 8.6f * scale
            TrafficVehicleType.SEDAN -> 4.35f * scale
            TrafficVehicleType.MOTORCYCLE -> 2.15f * scale
        }
        val h = when (vehicleType) {
            TrafficVehicleType.TRUCK -> 3.15f * scale
            TrafficVehicleType.BUS -> 3.05f * scale
            TrafficVehicleType.SEDAN -> 1.48f * scale
            TrafficVehicleType.MOTORCYCLE -> 1.28f * scale
        }
        val leftX = centerX - len * 0.5f
        val rightX = centerX + len * 0.5f

        drawOval(
            color = Color(0x8805070A),
            topLeft = Offset(leftX - 0.15f * scale, groundY - 0.10f * scale),
            size = Size(len + 0.30f * scale, 0.30f * scale)
        )

        when (vehicleType) {
            TrafficVehicleType.TRUCK -> {
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
            }
            TrafficVehicleType.BUS -> {
                // Sleek BUSSID-inspired tourist coach bus with panoramic windows & accent stripe
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(bodyLight, bodyColor, bodyDark)),
                    topLeft = Offset(leftX, groundY - h),
                    size = Size(len, h - 0.42f * scale),
                    cornerRadius = CornerRadius(0.24f * scale, 0.24f * scale)
                )
                drawRect(
                    color = Color(0xFFFACC15),
                    topLeft = Offset(leftX + 0.10f * scale, groundY - h * 0.44f),
                    size = Size(len - 0.20f * scale, 0.14f * scale)
                )
                // Panoramic passenger windows
                val winCount = 5
                val winW = (len * 0.78f) / winCount - 0.14f * scale
                for (wi in 0 until winCount) {
                    val wx = leftX + 0.42f * scale + wi * (winW + 0.14f * scale)
                    drawRoundRect(
                        color = Color(0xFF162536),
                        topLeft = Offset(wx, groundY - h + 0.26f * scale),
                        size = Size(winW, h * 0.36f),
                        cornerRadius = CornerRadius(0.06f * scale, 0.06f * scale)
                    )
                }
            }
            TrafficVehicleType.MOTORCYCLE -> {
                // Motorcycle frame + fuel tank + helmeted rider
                drawLine(
                    color = bodyColor,
                    start = Offset(leftX + 0.4f * scale, groundY - 0.45f * scale),
                    end = Offset(rightX - 0.35f * scale, groundY - 0.72f * scale),
                    strokeWidth = 0.20f * scale,
                    cap = StrokeCap.Round
                )
                // Rider torso + helmet
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(centerX - 0.12f * scale * dir, groundY - 0.65f * scale),
                    end = Offset(centerX + 0.14f * scale * dir, groundY - 1.12f * scale),
                    strokeWidth = 0.24f * scale,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = Color(0xFFF8FAFC),
                    radius = 0.20f * scale,
                    center = Offset(centerX + 0.16f * scale * dir, groundY - 1.24f * scale)
                )
            }
            TrafficVehicleType.SEDAN -> {
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
        }

        val wr = when (vehicleType) {
            TrafficVehicleType.TRUCK, TrafficVehicleType.BUS -> 0.44f * scale
            TrafficVehicleType.MOTORCYCLE -> 0.28f * scale
            TrafficVehicleType.SEDAN -> 0.34f * scale
        }
        drawSemiWheel2D(leftX + len * 0.22f, groundY - wr, wr, wheelAngleRad * dir)
        drawSemiWheel2D(leftX + len * 0.78f, groundY - wr, wr, wheelAngleRad * dir)

        val frontX = if (isOncoming) leftX else rightX
        val rearX = if (isOncoming) rightX else leftX
        drawCircle(color = Color(0xFFFFF8D6), radius = 0.11f * scale, center = Offset(frontX, groundY - 0.66f * scale))
        drawCircle(
            color = if (braking) Color(0xFFFF1818) else Color(0xFFAA1E1E),
            radius = if (braking) 0.14f * scale else 0.10f * scale,
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

    fun DrawScope.drawTrafficVehicle2DRearFront(
        vehicleType: TrafficVehicleType,
        isOncoming: Boolean,
        bodyColor: Color,
        cx: Float,
        bottomY: Float,
        width: Float,
        nightFactor: Float,
        braking: Boolean = false,
        vpX: Float = cx
    ) {
        val h = width * when (vehicleType) {
            TrafficVehicleType.TRUCK -> 1.26f
            TrafficVehicleType.BUS -> 1.34f
            TrafficVehicleType.MOTORCYCLE -> 1.45f
            TrafficVehicleType.SEDAN -> 0.72f
        }
        val actualW = if (vehicleType == TrafficVehicleType.MOTORCYCLE) width * 0.42f else width
        val left = cx - actualW * 0.5f
        val right = cx + actualW * 0.5f
        val bodyDark = darken(bodyColor, 0.52f)
        val bodyLight = lighten(bodyColor, 0.24f)

        // Contact ground shadow
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xB0030508), Color.Transparent),
                center = Offset(cx, bottomY),
                radius = actualW * 0.72f
            ),
            topLeft = Offset(left - actualW * 0.14f, bottomY - h * 0.07f),
            size = Size(actualW * 1.28f, h * 0.18f)
        )

        if (vehicleType == TrafficVehicleType.MOTORCYCLE) {
            drawRoundRect(Color(0xFF0F1318), Offset(cx - actualW * 0.24f, bottomY - h * 0.36f), Size(actualW * 0.48f, h * 0.36f), CornerRadius(3f, 3f))
            drawRoundRect(bodyColor, Offset(left, bottomY - h * 0.76f), Size(actualW, h * 0.48f), CornerRadius(4f, 4f))
            drawCircle(Color(0xFFF8FAFC), radius = actualW * 0.35f, center = Offset(cx, bottomY - h * 0.88f))
            val lightCol = if (isOncoming) Color(0xFFFFF8D6) else Color(0xFFFF2C2C)
            drawCircle(lightCol, radius = (actualW * 0.18f).coerceAtLeast(2f), center = Offset(cx, bottomY - h * 0.42f))
            return
        }

        // True 1-point 2D elevated perspective depth extrusion toward highway vanishing point vpX
        val depthRatio = when (vehicleType) {
            TrafficVehicleType.BUS -> 0.26f
            TrafficVehicleType.TRUCK -> 0.25f
            TrafficVehicleType.SEDAN -> 0.20f
            TrafficVehicleType.MOTORCYCLE -> 0.10f
        }
        val rawSkew = ((vpX - cx) * depthRatio).coerceIn(-actualW * 0.45f, actualW * 0.45f)
        val sideVisible = abs(rawSkew) > actualW * 0.04f
        val roofDropY = h * when (vehicleType) {
            TrafficVehicleType.BUS -> 0.18f
            TrafficVehicleType.TRUCK -> 0.18f
            else -> 0.16f
        }
        val tireW = actualW * 0.17f
        val tireH = h * 0.22f
        val mainTop = bottomY - h
        val mainH = h - tireH * 0.44f
        val farLeft = left + rawSkew
        val farRight = right + rawSkew
        val farTop = mainTop - roofDropY
        val farBottom = bottomY - tireH * 0.44f - roofDropY * 0.72f

        // Side wheels visible in 3/4 depth flank when offset from road vanishing center
        if (sideVisible && actualW > 14f) {
            val sideX = if (rawSkew > 0f) right else left
            val farWheelX = if (rawSkew > 0f) sideX + rawSkew * 0.68f else sideX + rawSkew * 0.68f
            val farWheelY = bottomY - roofDropY * 0.48f
            drawCircle(Color(0xFF0A0D12), radius = (tireH * 0.42f).coerceAtLeast(2.5f), center = Offset(farWheelX, farWheelY - tireH * 0.25f))
        }

        // Rear / front tires on primary ground line
        drawRoundRect(Color(0xFF0B0E13), Offset(left + actualW * 0.05f, bottomY - tireH), Size(tireW, tireH), CornerRadius(3f, 3f))
        drawRoundRect(Color(0xFF0B0E13), Offset(left + actualW * 0.78f, bottomY - tireH), Size(tireW, tireH), CornerRadius(3f, 3f))

        val isBoxTruckRear = vehicleType == TrafficVehicleType.TRUCK && !isOncoming
        val faceTopTint = if (isBoxTruckRear) Color(0xFFF1F5F9) else bodyLight
        val faceBotTint = if (isBoxTruckRear) Color(0xFF94A3B8) else bodyDark
        val faceMidTint = if (isBoxTruckRear) Color(0xFFE2E8F0) else bodyColor
        val flankColor = if (isBoxTruckRear) Color(0xFFCBD5E1) else bodyDark
        val roofColor = if (isBoxTruckRear) Color(0xFFF8FAFC) else lighten(bodyLight, 0.18f)

        // 1. Foreshortened Roof Plane (elevated 2D perspective top face)
        val roofScaleShrink = actualW * 0.04f
        val roofPath = Path().apply {
            moveTo(left + roofScaleShrink, mainTop)
            lineTo(right - roofScaleShrink, mainTop)
            lineTo(farRight - roofScaleShrink * 1.4f, farTop)
            lineTo(farLeft + roofScaleShrink * 1.4f, farTop)
            close()
        }
        drawPath(
            path = roofPath,
            brush = Brush.verticalGradient(listOf(darken(roofColor, 0.84f), roofColor), startY = farTop, endY = mainTop)
        )
        // BUSSID Coach dual roof AC pods
        if (vehicleType == TrafficVehicleType.BUS && actualW > 18f) {
            val acCx = cx + rawSkew * 0.48f
            val acCy = (mainTop + farTop) * 0.5f
            drawRoundRect(
                color = Color(0xFFE2E8F0),
                topLeft = Offset(acCx - actualW * 0.24f, acCy - roofDropY * 0.25f),
                size = Size(actualW * 0.48f, roofDropY * 0.46f),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }

        // 2. Exposed Side Flank Plane (left or right depending on position relative to vpX)
        if (sideVisible) {
            val nearEdgeX = if (rawSkew > 0f) right else left
            val farEdgeX = if (rawSkew > 0f) farRight - roofScaleShrink * 1.4f else farLeft + roofScaleShrink * 1.4f
            val flankPath = Path().apply {
                moveTo(nearEdgeX, mainTop)
                lineTo(farEdgeX, farTop)
                lineTo(farEdgeX, farBottom)
                lineTo(nearEdgeX, mainTop + mainH)
                close()
            }
            drawPath(
                path = flankPath,
                brush = Brush.horizontalGradient(
                    colors = if (rawSkew > 0f) listOf(flankColor, darken(flankColor, 0.65f)) else listOf(darken(flankColor, 0.65f), flankColor),
                    startX = minOf(nearEdgeX, farEdgeX),
                    endX = maxOf(nearEdgeX, farEdgeX)
                )
            )
            // Side panoramic windows on coach/sedan flank
            if ((vehicleType == TrafficVehicleType.BUS || vehicleType == TrafficVehicleType.SEDAN) && abs(rawSkew) > 4f) {
                val sideWinPath = Path().apply {
                    val wT1 = mainTop + mainH * 0.14f
                    val wB1 = mainTop + mainH * 0.44f
                    val wT2 = farTop + (farBottom - farTop) * 0.14f
                    val wB2 = farTop + (farBottom - farTop) * 0.44f
                    val nx = nearEdgeX + (if (rawSkew > 0f) 0.10f else -0.10f) * rawSkew
                    val fx = farEdgeX - (if (rawSkew > 0f) 0.12f else -0.12f) * rawSkew
                    moveTo(nx, wT1)
                    lineTo(fx, wT2)
                    lineTo(fx, wB2)
                    lineTo(nx, wB1)
                    close()
                }
                drawPath(sideWinPath, Color(0xFF0F1E2E))
            }
        }

        // 3. Primary Front / Rear Facade
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(faceTopTint, faceMidTint, faceBotTint)),
            topLeft = Offset(left, mainTop),
            size = Size(actualW, mainH),
            cornerRadius = CornerRadius(actualW * 0.08f, actualW * 0.08f)
        )

        if (vehicleType == TrafficVehicleType.BUS) {
            // BUSSID coach rear/front details: panoramic tinted glass + yellow accent band + engine vents
            drawRoundRect(
                color = Color(0xFF0F1E2E),
                topLeft = Offset(left + actualW * 0.10f, bottomY - h * 0.90f),
                size = Size(actualW * 0.80f, h * 0.32f),
                cornerRadius = CornerRadius(actualW * 0.06f, actualW * 0.06f)
            )
            drawRect(
                color = Color(0xFFFACC15),
                topLeft = Offset(left + actualW * 0.06f, bottomY - h * 0.52f),
                size = Size(actualW * 0.88f, h * 0.08f)
            )
            drawRoundRect(
                color = Color(0xFF1E293B),
                topLeft = Offset(left + actualW * 0.22f, bottomY - h * 0.40f),
                size = Size(actualW * 0.56f, h * 0.18f),
                cornerRadius = CornerRadius(actualW * 0.04f, actualW * 0.04f)
            )
        } else if (isBoxTruckRear) {
            drawLine(
                color = Color(0xFF475569),
                start = Offset(cx, mainTop + mainH * 0.06f),
                end = Offset(cx, mainTop + mainH * 0.84f),
                strokeWidth = (actualW * 0.025f).coerceAtLeast(1.2f)
            )
            for (barFrac in floatArrayOf(0.32f, 0.68f)) {
                drawLine(
                    color = Color(0xFF64748B),
                    start = Offset(left + actualW * barFrac, mainTop + mainH * 0.08f),
                    end = Offset(left + actualW * barFrac, mainTop + mainH * 0.82f),
                    strokeWidth = (actualW * 0.02f).coerceAtLeast(1f)
                )
            }
            drawRect(
                color = Color(0xFF1E293B),
                topLeft = Offset(left + actualW * 0.04f, bottomY - tireH * 0.95f),
                size = Size(actualW * 0.92f, tireH * 0.35f)
            )
        } else {
            drawRoundRect(
                color = Color(0xFF142230),
                topLeft = Offset(left + actualW * 0.14f, bottomY - h * 0.88f),
                size = Size(actualW * 0.72f, h * 0.28f),
                cornerRadius = CornerRadius(actualW * 0.05f, actualW * 0.05f)
            )
            drawRoundRect(
                color = Color(0xFF1E293B),
                topLeft = Offset(left + actualW * 0.34f, bottomY - h * 0.38f),
                size = Size(actualW * 0.32f, h * 0.12f),
                cornerRadius = CornerRadius(actualW * 0.03f, actualW * 0.03f)
            )
        }

        val lightCol = when {
            isOncoming -> Color(0xFFFFF8D6)
            braking -> Color(0xFFFF1414)
            else -> Color(0xFFD92626)
        }
        val ly = bottomY - h * 0.32f
        val lightW = (actualW * 0.15f).coerceAtLeast(3f)
        val lightH = (h * 0.08f).coerceAtLeast(2.2f)
        drawRoundRect(lightCol, Offset(left + actualW * 0.09f, ly - lightH * 0.5f), Size(lightW, lightH), CornerRadius(2f, 2f))
        drawRoundRect(lightCol, Offset(left + actualW * 0.76f, ly - lightH * 0.5f), Size(lightW, lightH), CornerRadius(2f, 2f))

        if (nightFactor > 0.20f || isOncoming || braking) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(lightCol.copy(alpha = if (braking) 0.58f else 0.38f), Color.Transparent),
                    center = Offset(cx, ly),
                    radius = actualW * 0.85f
                ),
                radius = actualW * 0.85f,
                center = Offset(cx, ly),
                blendMode = BlendMode.Screen
            )
        }
    }

    /**
     * BUSSID-Caliber 2D Elevated 3/4 Rear-Quarter Chase Perspective of the player's customized tractor + trailer.
     * Renders 100% in 2D Canvas with foreshortened roof depth, exposed side flank, side wheels, custom paints,
     * liveries, accessories, and all 7 mission trailers.
     */
    fun DrawScope.drawTruckRig2DRearChase(
        truck: TruckSpec,
        visualConfig: TruckVisualConfig,
        cx: Float,
        groundY: Float,
        rigWidth: Float,
        steerNorm: Float,
        trailerRelYawRad: Float,
        bouncePx: Float,
        headlightsOn: Boolean,
        braking: Boolean,
        reversing: Boolean,
        leftBlinker: Boolean,
        rightBlinker: Boolean,
        nightFactor: Float,
        throttleLevel: Float,
        simClock: Float,
        rainIntensity: Float,
        speedMs: Float,
        vpX: Float = cx
    ) {
        val fallbackPrimary = Color(truck.primaryColorRgb[0], truck.primaryColorRgb[1], truck.primaryColorRgb[2])
        val primary = parseHexColor(visualConfig.paintHex, fallbackPrimary)
        val primaryDark = darken(primary, 0.48f)
        val primaryLight = lighten(primary, 0.28f)
        val accent = Color(truck.accentColorRgb[0], truck.accentColorRgb[1], truck.accentColorRgb[2])

        val w = rigWidth
        val h = w * 1.32f
        val blinkPulse = ((simClock * 2.4f).toInt() % 2 == 0)
        // Perspective depth offset driven by vanishing point + steering + trailer articulation
        val rawPerspSkew = ((vpX - cx) * 0.22f + steerNorm * w * 0.14f + trailerRelYawRad * w * 0.48f)
        // Guarantee a slight crisp 3/4 depth reveal even when driving dead-center on a straightaway
        val minBias = if (abs(rawPerspSkew) < w * 0.09f) {
            val sign = if (rawPerspSkew >= 0f) 1f else -1f
            sign * w * 0.09f
        } else rawPerspSkew
        val perspSkew = minBias.coerceIn(-w * 0.44f, w * 0.44f)
        val cabShiftX = perspSkew * 1.24f + steerNorm * w * 0.14f
        val leanDeg = (steerNorm * 2.4f).coerceIn(-3.6f, 3.6f)
        val roofDropY = h * 0.22f

        // 1. Forward volumetric headlight glow fanning out past tractor fenders
        if (headlightsOn) {
            val beamAlpha = (0.16f + nightFactor * 0.42f).coerceIn(0.14f, 0.58f)
            val beamLen = w * (2.8f + nightFactor * 1.5f)
            for (side in floatArrayOf(-1f, 1f)) {
                val startX = cx + cabShiftX + side * w * 0.36f
                val beamPath = Path().apply {
                    moveTo(startX, groundY - h * 0.22f)
                    lineTo(startX + side * w * 0.85f, groundY - beamLen)
                    lineTo(startX - side * w * 0.15f, groundY - beamLen)
                    close()
                }
                drawPath(
                    path = beamPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFFFFF4C2).copy(alpha = beamAlpha)),
                        startY = groundY - beamLen,
                        endY = groundY - h * 0.18f
                    ),
                    blendMode = BlendMode.Screen
                )
            }
        }

        // 2. Multi-layered soft road contact shadow extending forward along perspective flank
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xD8020407), Color(0x75020407), Color.Transparent),
                center = Offset(cx + perspSkew * 0.45f, groundY + h * 0.01f),
                radius = w * 0.88f
            ),
            topLeft = Offset(cx + perspSkew * 0.2f - w * 0.80f, groundY - h * 0.10f),
            size = Size(w * 1.60f, h * 0.20f)
        )

        // 3. Forward Articulated Tractor Cab (roof, side window, chrome exhausts, wide mirrors, bullbar, lightbar)
        val cabCx = cx + cabShiftX
        val cabTopY = groundY - h * (if (truck.cabStyle == TruckCabStyle.EURO_CABOVER) 1.14f else 1.10f) + bouncePx * 0.8f
        val cabHalfW = w * 0.52f

        // Tractor visible side tires & diesel tank ahead of trailer axles
        val tractorTireX = if (perspSkew > 0f) cabCx + cabHalfW * 0.88f else cabCx - cabHalfW * 0.88f
        drawOval(
            color = Color(0xFF0A0D12),
            topLeft = Offset(tractorTireX - w * 0.09f, groundY - h * 0.26f),
            size = Size(w * 0.18f, h * 0.19f)
        )
        drawOval(
            color = Color(0xFFCBD5E1),
            topLeft = Offset(tractorTireX - w * 0.045f, groundY - h * 0.21f),
            size = Size(w * 0.09f, h * 0.095f)
        )

        // Wide chrome side mirrors extending out on brackets (signature BUSSID rear-chase detail)
        for (side in floatArrayOf(-1f, 1f)) {
            val mirrorArmX = cabCx + side * (cabHalfW + w * 0.08f)
            val mirrorY = cabTopY + h * 0.16f
            drawLine(
                color = Color(0xFF94A3B8),
                start = Offset(cabCx + side * cabHalfW * 0.94f, mirrorY + h * 0.02f),
                end = Offset(mirrorArmX, mirrorY + h * 0.01f),
                strokeWidth = (w * 0.024f).coerceAtLeast(2.5f)
            )
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF475569), primaryDark)),
                topLeft = Offset(mirrorArmX - w * 0.034f, mirrorY - h * 0.06f),
                size = Size(w * 0.068f, h * 0.16f),
                cornerRadius = CornerRadius(w * 0.018f, w * 0.018f)
            )
            val blinkerActive = (side < 0f && leftBlinker && blinkPulse) || (side > 0f && rightBlinker && blinkPulse)
            if (blinkerActive) {
                drawCircle(
                    color = Color(0xFFFFA500),
                    radius = w * 0.034f,
                    center = Offset(mirrorArmX, mirrorY)
                )
            }
        }

        // Tractor painted cab rear/flanks behind trailer with sleeper side glass & chrome diesel step
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(primaryLight, primary, primaryDark)),
            topLeft = Offset(cabCx - cabHalfW, cabTopY),
            size = Size(cabHalfW * 2f, h * 0.84f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
        )
        // Exposed tractor cab side window when angled/cornering
        val cabWinX = if (perspSkew > 0f) cabCx + cabHalfW * 0.42f else cabCx - cabHalfW * 0.82f
        drawRoundRect(
            color = Color(0xFF0F1E2E),
            topLeft = Offset(cabWinX, cabTopY + h * 0.09f),
            size = Size(cabHalfW * 0.40f, h * 0.22f),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
        )
        // Exposed side brushed aluminum diesel tank on tractor flank
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFFF8FAFC), Color(0xFF94A3B8), Color(0xFF334155))),
            topLeft = Offset(if (perspSkew > 0f) cabCx + cabHalfW * 0.48f else cabCx - cabHalfW * 0.86f, groundY - h * 0.34f),
            size = Size(cabHalfW * 0.38f, h * 0.12f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )

        // Optional Chrome Bullbar lower ears visible around tractor fenders
        if (visualConfig.bullbarInstalled) {
            for (side in floatArrayOf(-1f, 1f)) {
                val bx = cabCx + side * (cabHalfW + w * 0.03f)
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFFF8FAFC), Color(0xFF64748B))),
                    topLeft = Offset(bx - w * 0.025f, groundY - h * 0.26f),
                    size = Size(w * 0.05f, h * 0.12f),
                    cornerRadius = CornerRadius(w * 0.015f, w * 0.015f)
                )
            }
        }

        // Chrome Vertical Exhaust Stacks (single/double + dynamic diesel smoke)
        val stackCount = if (visualConfig.chromeExhaustInstalled || truck.cabStyle == TruckCabStyle.AMERICAN_CONVENTIONAL) 2 else 1
        val stackTopLift = if (visualConfig.chromeExhaustInstalled) 0.12f else 0.05f
        for (si in 0 until stackCount) {
            val side = if (stackCount == 1) -1f else if (si == 0) -1f else 1f
            val sx = cabCx + side * (cabHalfW * 0.90f)
            val sTopY = cabTopY - h * stackTopLift
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFF475569), Color(0xFFF8FAFC), Color(0xFF64748B)),
                    startX = sx - w * 0.025f,
                    endX = sx + w * 0.025f
                ),
                topLeft = Offset(sx - w * 0.025f, sTopY),
                size = Size(w * 0.05f, h * 0.56f),
                cornerRadius = CornerRadius(w * 0.015f, w * 0.015f)
            )
            if (throttleLevel > 0.12f || ((simClock * 2f).toInt() % 3 == 0)) {
                val puffs = if (throttleLevel > 0.25f) 4 else 2
                for (p in 0 until puffs) {
                    val phase = ((simClock * (2.0f + throttleLevel * 2.2f) + p * 0.24f) % 1f)
                    val px = sx + side * phase * w * 0.08f
                    val py = sTopY - phase * h * 0.16f
                    val pr = w * (0.025f + phase * (0.055f + throttleLevel * 0.045f))
                    val alpha = ((1f - phase) * (0.14f + throttleLevel * 0.24f)).coerceIn(0f, 0.40f)
                    drawCircle(Color(0xFFB8C1CC).copy(alpha = alpha), radius = pr, center = Offset(px, py))
                }
            }
        }

        // Aero Roof Spoiler & Roof LED Lightbar
        if (visualConfig.aeroSpoilerInstalled) {
            val wingPath = Path().apply {
                moveTo(cabCx - cabHalfW * 0.90f, cabTopY + h * 0.02f)
                lineTo(cabCx - cabHalfW * 0.72f, cabTopY - h * 0.07f)
                lineTo(cabCx + cabHalfW * 0.72f, cabTopY - h * 0.07f)
                lineTo(cabCx + cabHalfW * 0.90f, cabTopY + h * 0.02f)
                close()
            }
            drawPath(wingPath, accent)
        }
        if (visualConfig.roofLightbarInstalled) {
            val barY = cabTopY - h * 0.045f
            drawRoundRect(
                color = Color(0xFFE2E8F0),
                topLeft = Offset(cabCx - cabHalfW * 0.62f, barY),
                size = Size(cabHalfW * 1.24f, h * 0.032f),
                cornerRadius = CornerRadius(w * 0.015f, w * 0.015f)
            )
            for (li in -2..2) {
                val lx = cabCx + li * cabHalfW * 0.24f
                drawCircle(
                    color = if (headlightsOn) Color(0xFFFFF59D) else Color(0xFF94A3B8),
                    radius = w * 0.024f,
                    center = Offset(lx, barY + h * 0.016f)
                )
            }
            if (headlightsOn && nightFactor > 0.15f) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color(0xFFFFF59D).copy(alpha = 0.42f), Color.Transparent), center = Offset(cabCx, barY), radius = w * 0.45f),
                    radius = w * 0.45f,
                    center = Offset(cabCx, barY),
                    blendMode = BlendMode.Screen
                )
            }
        }

        // 4. Articulated 3/4 Elevated Trailer Assembly (leans slightly in turns + bounces with road speed)
        withTransform({
            rotate(degrees = leanDeg, pivot = Offset(cx, groundY))
            translate(top = bouncePx)
        }) {
            val tHalfW = w * 0.49f
            val tLeft = cx - tHalfW
            val tRight = cx + tHalfW
            val tTop = groundY - h * 0.94f
            val deckBottom = groundY - h * 0.24f
            val bodyH = deckBottom - tTop

            // Perspective top roof plane & side wall convergence toward tractor kingpin
            val farLeft = tLeft + perspSkew * 0.90f + w * 0.05f
            val farRight = tRight + perspSkew * 0.90f - w * 0.05f
            val farTopY = tTop - roofDropY
            val farDeckBottom = deckBottom - roofDropY * 0.85f

            // Visible side tandem trailer tires along exposed flank
            val sideFlankLeftSide = perspSkew < 0f
            val flankNearX = if (sideFlankLeftSide) tLeft else tRight
            val flankFarX = if (sideFlankLeftSide) farLeft else farRight
            for (axFrac in floatArrayOf(0.28f, 0.56f)) {
                val wx = flankNearX + (flankFarX - flankNearX) * axFrac
                val wy = groundY - roofDropY * 0.65f * axFrac
                val wr = w * (0.088f - axFrac * 0.015f)
                drawCircle(Color(0xFF0B0E13), radius = wr, center = Offset(wx, wy - wr * 0.85f))
                drawCircle(Color(0xFFCBD5E1), radius = wr * 0.50f, center = Offset(wx, wy - wr * 0.85f))
            }

            // Twin Rear Dual Tires + Swinging Rubber Mudflaps
            val tireW = w * 0.185f
            val tireH = h * 0.22f
            val mudSway = (steerNorm * 5f + sin(simClock * 11f) * (abs(speedMs) / 30f).coerceIn(0f, 1f) * 3.5f)
            for (side in floatArrayOf(-1f, 1f)) {
                val tx = if (side < 0f) tLeft + w * 0.02f else tRight - w * 0.02f - tireW
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF1F252E), Color(0xFF0A0D12))),
                    topLeft = Offset(tx, groundY - tireH),
                    size = Size(tireW, tireH),
                    cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
                )
                for (tr in 1..4) {
                    val ry = groundY - tireH + (tireH / 5f) * tr
                    drawLine(Color(0xFF334155), Offset(tx + 2f, ry), Offset(tx + tireW - 2f, ry), strokeWidth = 1.6f)
                }
                rotate(degrees = mudSway, pivot = Offset(tx + tireW * 0.5f, deckBottom)) {
                    val flapTop = deckBottom - h * 0.02f
                    val flapH = tireH * 0.72f
                    drawRoundRect(
                        color = Color(0xFF11151B),
                        topLeft = Offset(tx + w * 0.01f, flapTop),
                        size = Size(tireW - w * 0.02f, flapH),
                        cornerRadius = CornerRadius(w * 0.015f, w * 0.015f)
                    )
                    drawRect(
                        color = Color(0xFFEF4444),
                        topLeft = Offset(tx + w * 0.02f, flapTop + flapH * 0.72f),
                        size = Size((tireW - w * 0.04f) * 0.5f, flapH * 0.12f)
                    )
                    drawRect(
                        color = Color(0xFFF8FAFC),
                        topLeft = Offset(tx + tireW * 0.5f, flapTop + flapH * 0.72f),
                        size = Size((tireW - w * 0.04f) * 0.5f, flapH * 0.12f)
                    )
                }
            }

            if (rainIntensity > 0.15f && abs(speedMs) > 4f) {
                drawTireSpray2D(tLeft + w * 0.06f, groundY, w * 0.16f, rainIntensity, speedMs, simClock)
                drawTireSpray2D(tRight - w * 0.06f, groundY, w * 0.16f, rainIntensity, speedMs, simClock)
            }

            // Axle differential & dark steel undercarriage
            drawRect(
                color = Color(0xFF111827),
                topLeft = Offset(tLeft + tireW, groundY - tireH * 0.65f),
                size = Size(w * 0.98f - tireW * 2f, tireH * 0.25f)
            )
            drawCircle(Color(0xFF1E293B), radius = w * 0.07f, center = Offset(cx, groundY - tireH * 0.52f))

            // 4A. Foreshortened 2D Trailer Roof & Exposed Side Wall (varies by CargoTrailerType)
            when (visualConfig.trailerType) {
                CargoTrailerType.REEFER_BOX -> {
                    // Roof surface + side flank wall
                    val roofPath = Path().apply {
                        moveTo(tLeft + w * 0.02f, tTop)
                        lineTo(tRight - w * 0.02f, tTop)
                        lineTo(farRight, farTopY)
                        lineTo(farLeft, farTopY)
                        close()
                    }
                    drawPath(roofPath, Brush.verticalGradient(listOf(Color(0xFFCBD5E1), Color(0xFFF8FAFC)), startY = farTopY, endY = tTop))
                    // Reefer ThermoKing cooling box silhouette at front roof edge
                    val unitCx = (farLeft + farRight) * 0.5f
                    drawRoundRect(Color(0xFFE2E8F0), Offset(unitCx - w * 0.20f, farTopY - h * 0.03f), Size(w * 0.40f, h * 0.07f), CornerRadius(4f, 4f))

                    val flankPath = Path().apply {
                        moveTo(flankNearX, tTop)
                        lineTo(flankFarX, farTopY)
                        lineTo(flankFarX, farDeckBottom)
                        lineTo(flankNearX, deckBottom)
                        close()
                    }
                    drawPath(flankPath, Brush.horizontalGradient(listOf(Color(0xFF94A3B8), Color(0xFFCBD5E1)), startX = minOf(flankNearX, flankFarX), endX = maxOf(flankNearX, flankFarX)))
                    // Side livery stripe along exposed flank
                    val stripePath = Path().apply {
                        moveTo(flankNearX, tTop + h * 0.08f)
                        lineTo(flankFarX, farTopY + h * 0.07f)
                        lineTo(flankFarX, farTopY + h * 0.18f)
                        lineTo(flankNearX, tTop + h * 0.22f)
                        close()
                    }
                    drawPath(stripePath, primary.copy(alpha = 0.88f))
                }
                CargoTrailerType.FUEL_TANKER -> {
                    // Polished stainless cylinder receding toward tractor
                    val tankBody = Path().apply {
                        moveTo(tLeft + w * 0.08f, tTop + h * 0.14f)
                        lineTo(farLeft + w * 0.06f, farTopY + h * 0.10f)
                        lineTo(farRight - w * 0.06f, farTopY + h * 0.10f)
                        lineTo(tRight - w * 0.08f, tTop + h * 0.14f)
                        lineTo(tRight - w * 0.06f, deckBottom - h * 0.06f)
                        lineTo(farRight - w * 0.06f, farDeckBottom - h * 0.06f)
                        lineTo(farLeft + w * 0.06f, farDeckBottom - h * 0.06f)
                        lineTo(tLeft + w * 0.06f, deckBottom - h * 0.06f)
                        close()
                    }
                    drawPath(tankBody, Brush.horizontalGradient(listOf(Color(0xFF64748B), Color(0xFFF8FAFC), Color(0xFF94A3B8))))
                    // Steel catwalk + dome hatches along top
                    for (hi in 1..3) {
                        val f = hi * 0.25f
                        val hx = cx + perspSkew * f
                        val hy = (tTop + h * 0.06f) * (1f - f) + (farTopY + h * 0.04f) * f
                        drawOval(Color(0xFF475569), Offset(hx - w * 0.08f, hy), Size(w * 0.16f, h * 0.03f))
                    }
                }
                CargoTrailerType.AGRI_GRAIN -> {
                    val roofPath = Path().apply {
                        moveTo(tLeft + w * 0.02f, tTop + h * 0.08f)
                        lineTo(tRight - w * 0.02f, tTop + h * 0.08f)
                        lineTo(farRight, farTopY + h * 0.07f)
                        lineTo(farLeft, farTopY + h * 0.07f)
                        close()
                    }
                    drawPath(roofPath, Color(0xFF334155))
                    val flankPath = Path().apply {
                        moveTo(flankNearX, tTop + h * 0.08f)
                        lineTo(flankFarX, farTopY + h * 0.07f)
                        lineTo(flankFarX, farDeckBottom)
                        lineTo(flankNearX, deckBottom)
                        close()
                    }
                    drawPath(flankPath, Color(0xFF9C7125))
                }
                CargoTrailerType.TIMBER_LOGS -> {
                    // Long bark-textured timber logs receding along perspective deck
                    val barkTop = Path().apply {
                        moveTo(tLeft + w * 0.08f, tTop + h * 0.14f)
                        lineTo(farLeft + w * 0.04f, farTopY + h * 0.12f)
                        lineTo(farRight - w * 0.04f, farTopY + h * 0.12f)
                        lineTo(tRight - w * 0.08f, tTop + h * 0.14f)
                        close()
                    }
                    drawPath(barkTop, Brush.verticalGradient(listOf(Color(0xFF3E2716), Color(0xFF6D4427))))
                    val flankLog = Path().apply {
                        moveTo(flankNearX, tTop + h * 0.14f)
                        lineTo(flankFarX, farTopY + h * 0.12f)
                        lineTo(flankFarX, farDeckBottom - h * 0.06f)
                        lineTo(flankNearX, deckBottom - h * 0.06f)
                        close()
                    }
                    drawPath(flankLog, Color(0xFF54331C))
                }
                else -> {
                    // Open flatbed / carrier deck extending toward tractor fifth-wheel
                    val deckPath = Path().apply {
                        moveTo(tLeft, deckBottom - h * 0.08f)
                        lineTo(farLeft, farDeckBottom - h * 0.06f)
                        lineTo(farRight, farDeckBottom - h * 0.06f)
                        lineTo(tRight, deckBottom - h * 0.08f)
                        close()
                    }
                    drawPath(deckPath, Color(0xFF334155))
                }
            }

            // 4B. Mission-Specific Trailer Rear Facade
            when (visualConfig.trailerType) {
                CargoTrailerType.FUEL_TANKER -> {
                    val tankPad = w * 0.05f
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFFFFFF), Color(0xFFCBD5E1), Color(0xFF475569)),
                            center = Offset(cx - w * 0.10f, tTop + bodyH * 0.35f),
                            radius = w * 0.65f
                        ),
                        topLeft = Offset(tLeft + tankPad, tTop + h * 0.04f),
                        size = Size(tHalfW * 2f - tankPad * 2f, bodyH - h * 0.06f),
                        cornerRadius = CornerRadius(w * 0.38f, w * 0.38f)
                    )
                    val ladX = tRight - w * 0.24f
                    drawLine(Color(0xFF334155), Offset(ladX, tTop + h * 0.05f), Offset(ladX, deckBottom), strokeWidth = 3f)
                    drawLine(Color(0xFF334155), Offset(ladX + w * 0.10f, tTop + h * 0.05f), Offset(ladX + w * 0.10f, deckBottom), strokeWidth = 3f)
                    for (rung in 0..5) {
                        val ry = tTop + h * (0.10f + rung * 0.10f)
                        drawLine(Color(0xFF475569), Offset(ladX, ry), Offset(ladX + w * 0.10f, ry), strokeWidth = 2.5f)
                    }
                    drawRect(Color(0xFFFF6D00), Offset(cx - w * 0.08f, tTop + bodyH * 0.52f), Size(w * 0.16f, w * 0.14f))
                    drawRect(Color(0xFF0F172A), Offset(cx - w * 0.08f, tTop + bodyH * 0.52f), Size(w * 0.16f, w * 0.14f), style = Stroke(width = 2f))
                }
                CargoTrailerType.TIMBER_LOGS -> {
                    drawRoundRect(Color(0xFF1F2937), Offset(tLeft, deckBottom - h * 0.08f), Size(tHalfW * 2f, h * 0.10f), CornerRadius(4f, 4f))
                    val logR = w * 0.105f
                    for (r in 0 until 3) {
                        for (c in 0 until 4) {
                            val lx = tLeft + w * 0.16f + c * (w * 0.22f) + (if (r % 2 == 1) w * 0.04f else 0f)
                            val ly = deckBottom - h * 0.14f - r * (logR * 1.75f)
                            drawCircle(Color(0xFF5D3A20), radius = logR, center = Offset(lx, ly))
                            drawCircle(Color(0xFFD6B083), radius = logR * 0.78f, center = Offset(lx, ly))
                            drawCircle(Color(0xFFA47148), radius = logR * 0.42f, center = Offset(lx, ly), style = Stroke(width = 1.8f))
                        }
                    }
                    for (side in floatArrayOf(-1f, 1f)) {
                        val sx = if (side < 0f) tLeft + w * 0.03f else tRight - w * 0.09f
                        drawRoundRect(Color(0xFFEA580C), Offset(sx, tTop + h * 0.12f), Size(w * 0.06f, bodyH - h * 0.12f), CornerRadius(3f, 3f))
                    }
                }
                CargoTrailerType.CAR_CARRIER -> {
                    drawRoundRect(Color(0xFF1E293B), Offset(tLeft, deckBottom - h * 0.08f), Size(tHalfW * 2f, h * 0.10f), CornerRadius(4f, 4f))
                    val upperY = tTop + bodyH * 0.42f
                    drawRect(Color(0xFF334155), Offset(tLeft + w * 0.02f, upperY), Size(tHalfW * 2f - w * 0.04f, h * 0.05f))
                    for (side in floatArrayOf(-1f, 1f)) {
                        val sx = if (side < 0f) tLeft + w * 0.02f else tRight - w * 0.08f
                        drawRect(Color(0xFF475569), Offset(sx, tTop + h * 0.08f), Size(w * 0.06f, bodyH - h * 0.08f))
                    }
                    drawTrafficVehicle2DRearFront(TrafficVehicleType.SEDAN, false, Color(0xFFEF4444), cx + perspSkew * 0.45f, upperY - roofDropY * 0.25f, w * 0.64f, nightFactor, false, vpX = cx + perspSkew)
                    drawTrafficVehicle2DRearFront(TrafficVehicleType.SEDAN, false, Color(0xFFFACC15), cx, deckBottom - h * 0.08f, w * 0.74f, nightFactor, false, vpX = cx + perspSkew)
                }
                CargoTrailerType.FLATBED_EXCAVATOR -> {
                    drawRoundRect(Color(0xFF28303B), Offset(tLeft, deckBottom - h * 0.09f), Size(tHalfW * 2f, h * 0.11f), CornerRadius(4f, 4f))
                    for (side in floatArrayOf(-1f, 1f)) {
                        val rx = if (side < 0f) tLeft + w * 0.10f else tRight - w * 0.30f
                        drawRoundRect(Color(0xFF475569), Offset(rx, deckBottom - h * 0.32f), Size(w * 0.20f, h * 0.24f), CornerRadius(3f, 3f))
                    }
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(Color(0xFFFDE047), Color(0xFFEAB308), Color(0xFFA16207))),
                        topLeft = Offset(cx - w * 0.34f, deckBottom - h * 0.56f),
                        size = Size(w * 0.68f, h * 0.44f),
                        cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
                    )
                    drawRoundRect(
                        color = Color(0xFF1E293B),
                        topLeft = Offset(cx - w * 0.28f, tTop + h * 0.14f),
                        size = Size(w * 0.34f, h * 0.28f),
                        cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
                    )
                    drawRoundRect(Color(0xFFEAB308), Offset(cx + w * 0.08f, tTop + h * 0.06f), Size(w * 0.14f, h * 0.48f), CornerRadius(4f, 4f))
                }
                CargoTrailerType.WIND_TURBINE -> {
                    drawRoundRect(Color(0xFF28303B), Offset(tLeft, deckBottom - h * 0.09f), Size(tHalfW * 2f, h * 0.11f), CornerRadius(4f, 4f))
                    drawCircle(
                        brush = Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFCBD5E1))),
                        radius = w * 0.28f,
                        center = Offset(cx, deckBottom - h * 0.38f)
                    )
                    drawRect(Color(0xFFF59E0B), Offset(tLeft + w * 0.04f, deckBottom - h * 0.18f), Size(w * 0.90f, h * 0.06f))
                }
                CargoTrailerType.AGRI_GRAIN -> {
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(Color(0xFFE5D299), Color(0xFFD4A74A), Color(0xFF9C7125))),
                        topLeft = Offset(tLeft + w * 0.02f, tTop + h * 0.08f),
                        size = Size(tHalfW * 2f - w * 0.04f, bodyH - h * 0.10f),
                        cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
                    )
                    drawRect(Color(0xFF15803D), Offset(tLeft + w * 0.06f, tTop + bodyH * 0.36f), Size(tHalfW * 2f - w * 0.12f, h * 0.09f))
                }
                CargoTrailerType.REEFER_BOX -> {
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE2E8F0), Color(0xFFCBD5E1))),
                        topLeft = Offset(tLeft, tTop),
                        size = Size(tHalfW * 2f, bodyH),
                        cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
                    )
                    drawRect(
                        color = primary.copy(alpha = 0.88f),
                        topLeft = Offset(tLeft + w * 0.03f, tTop + h * 0.05f),
                        size = Size(tHalfW * 2f - w * 0.06f, h * 0.14f)
                    )
                    drawRect(
                        color = accent,
                        topLeft = Offset(tLeft + w * 0.03f, tTop + h * 0.19f),
                        size = Size(tHalfW * 2f - w * 0.06f, h * 0.03f)
                    )
                    drawRoundRect(
                        color = Color(0xFF94A3B8),
                        topLeft = Offset(tLeft + w * 0.03f, tTop + h * 0.03f),
                        size = Size(tHalfW * 2f - w * 0.06f, bodyH - h * 0.06f),
                        cornerRadius = CornerRadius(w * 0.02f, w * 0.02f),
                        style = Stroke(width = 2.5f)
                    )
                    drawLine(Color(0xFF475569), Offset(cx, tTop + h * 0.03f), Offset(cx, deckBottom - h * 0.03f), strokeWidth = 2.5f)
                    for (rxFrac in floatArrayOf(0.22f, 0.38f, 0.62f, 0.78f)) {
                        val rx = tLeft + tHalfW * 2f * rxFrac
                        drawLine(Color(0xFF64748B), Offset(rx, tTop + h * 0.06f), Offset(rx, deckBottom - h * 0.06f), strokeWidth = 2.4f)
                        drawRect(Color(0xFF1E293B), Offset(rx - w * 0.018f, deckBottom - h * 0.18f), Size(w * 0.036f, h * 0.07f))
                    }
                    drawCircle(Color(0xFFFF3838), radius = w * 0.022f, center = Offset(tLeft + w * 0.04f, tTop + h * 0.03f))
                    drawCircle(Color(0xFFFF3838), radius = w * 0.022f, center = Offset(tRight - w * 0.04f, tTop + h * 0.03f))
                }
            }

            // 5. Rear Underride Bumper Beam + Reflective Chevron Tape + License Plate + Tail/Brake/Blinker LEDs
            val bumperY = deckBottom - h * 0.01f
            val bumperH = h * 0.09f
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF334155), Color(0xFF111827))),
                topLeft = Offset(tLeft, bumperY),
                size = Size(tHalfW * 2f, bumperH),
                cornerRadius = CornerRadius(w * 0.025f, w * 0.025f)
            )
            val segCount = 10
            val segW = (tHalfW * 1.90f) / segCount
            for (si in 0 until segCount) {
                drawRect(
                    color = if (si % 2 == 0) Color(0xFFDC2626) else Color(0xFFF8FAFC),
                    topLeft = Offset(tLeft + w * 0.025f + si * segW, bumperY + bumperH * 0.72f),
                    size = Size(segW * 0.90f, bumperH * 0.22f)
                )
            }
            drawRoundRect(
                color = Color(0xFFF8FAFC),
                topLeft = Offset(cx - w * 0.10f, bumperY + bumperH * 0.16f),
                size = Size(w * 0.20f, bumperH * 0.44f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            drawRect(
                color = Color(0xFF1D4ED8),
                topLeft = Offset(cx - w * 0.10f, bumperY + bumperH * 0.16f),
                size = Size(w * 0.035f, bumperH * 0.44f)
            )

            val redCol = if (braking) Color(0xFFFF1A1A) else if (headlightsOn || nightFactor > 0.25f) Color(0xFFEF4444) else Color(0xFF7F1D1D)
            for (side in floatArrayOf(-1f, 1f)) {
                val clusterX = if (side < 0f) tLeft + w * 0.03f else tRight - w * 0.23f
                val clusterY = bumperY + bumperH * 0.14f
                val clusterW = w * 0.20f
                val clusterH = bumperH * 0.50f
                drawRoundRect(Color(0xFF090D12), Offset(clusterX, clusterY), Size(clusterW, clusterH), CornerRadius(3f, 3f))
                drawRoundRect(redCol, Offset(clusterX + clusterW * 0.08f, clusterY + clusterH * 0.15f), Size(clusterW * 0.42f, clusterH * 0.70f), CornerRadius(2f, 2f))
                drawRoundRect(
                    color = if (reversing) Color(0xFFFFFFFF) else Color(0xFF94A3B8),
                    topLeft = Offset(clusterX + clusterW * 0.54f, clusterY + clusterH * 0.15f),
                    size = Size(clusterW * 0.18f, clusterH * 0.70f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
                val blinkerLit = (side < 0f && leftBlinker && blinkPulse) || (side > 0f && rightBlinker && blinkPulse)
                drawRoundRect(
                    color = if (blinkerLit) Color(0xFFFFA500) else Color(0xFF78350F),
                    topLeft = Offset(clusterX + clusterW * 0.75f, clusterY + clusterH * 0.15f),
                    size = Size(clusterW * 0.20f, clusterH * 0.70f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
                if (braking || nightFactor > 0.30f || headlightsOn) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(redCol.copy(alpha = if (braking) 0.72f else 0.34f), Color.Transparent),
                            center = Offset(clusterX + clusterW * 0.30f, clusterY + clusterH * 0.5f),
                            radius = w * 0.30f
                        ),
                        radius = w * 0.30f,
                        center = Offset(clusterX + clusterW * 0.30f, clusterY + clusterH * 0.5f),
                        blendMode = BlendMode.Screen
                    )
                }
            }
        }
    }

    fun DrawScope.drawDriverOnFoot2DChase(
        centerX: Float,
        groundY: Float,
        heightPx: Float,
        walkCycle: Float,
        walkSpeed: Float,
        driverSprite: ImageBitmap? = null
    ) {
        val bob = abs(sin(walkCycle)) * heightPx * 0.045f * walkSpeed
        val scale = heightPx / 1.75f

        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xC0030508), Color.Transparent),
                center = Offset(centerX, groundY),
                radius = 0.44f * scale
            ),
            topLeft = Offset(centerX - 0.38f * scale, groundY - 0.08f * scale),
            size = Size(0.76f * scale, 0.18f * scale)
        )

        if (driverSprite != null) {
            val spriteAspect = driverSprite.width.toFloat() / driverSprite.height.coerceAtLeast(1).toFloat()
            val drawH = heightPx * 1.18f
            val drawW = drawH * spriteAspect
            val leanDeg = sin(walkCycle) * 4.2f * walkSpeed
            rotate(degrees = leanDeg, pivot = Offset(centerX, groundY)) {
                drawImage(
                    image = driverSprite,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(driverSprite.width, driverSprite.height),
                    dstOffset = IntOffset((centerX - drawW * 0.5f).toInt(), (groundY - drawH - bob).toInt()),
                    dstSize = IntSize(drawW.toInt().coerceAtLeast(1), drawH.toInt().coerceAtLeast(1))
                )
            }
            return
        }

        val swing = sin(walkCycle) * (0.25f + walkSpeed * 0.75f) * 20f
        val hipY = groundY - 0.80f * scale - bob
        val shoulderY = groundY - 1.38f * scale - bob

        rotate(degrees = -swing, pivot = Offset(centerX - 0.10f * scale, hipY)) {
            drawLine(Color(0xFF1E3A5F), Offset(centerX - 0.10f * scale, hipY), Offset(centerX - 0.12f * scale, groundY - 0.10f * scale - bob), strokeWidth = 0.16f * scale, cap = StrokeCap.Round)
        }
        rotate(degrees = swing, pivot = Offset(centerX + 0.10f * scale, hipY)) {
            drawLine(Color(0xFF254B7A), Offset(centerX + 0.10f * scale, hipY), Offset(centerX + 0.12f * scale, groundY - 0.10f * scale - bob), strokeWidth = 0.16f * scale, cap = StrokeCap.Round)
        }

        drawRoundRect(Color(0xFFF97316), Offset(centerX - 0.24f * scale, shoulderY), Size(0.48f * scale, 0.60f * scale), CornerRadius(0.10f * scale, 0.10f * scale))
        drawRect(Color(0xFFE2E8F0), Offset(centerX - 0.24f * scale, shoulderY + 0.34f * scale), Size(0.48f * scale, 0.08f * scale))
        drawCircle(Color(0xFF1E293B), radius = 0.18f * scale, center = Offset(centerX, shoulderY - 0.20f * scale))
    }

    fun DrawScope.drawConvoyTag2D(
        driverName: String,
        speedKmh: Int,
        hornActive: Boolean,
        cx: Float,
        topY: Float
    ) {
        val label = if (hornActive) "📣 $driverName ($speedKmh km/h)" else "$driverName • $speedKmh km/h"
        convoyTagTextPaint.textSize = 24f
        val textW = convoyTagTextPaint.measureText(label)
        val boxW = textW + 28f
        val boxH = 38f
        val left = cx - boxW * 0.5f
        val top = topY - boxH
        val rect = android.graphics.RectF(left, top, left + boxW, top + boxH)
        drawContext.canvas.nativeCanvas.drawRoundRect(rect, 12f, 12f, convoyTagBgPaint)
        convoyTagBorderPaint.color = if (hornActive) {
            android.graphics.Color.rgb(245, 158, 11)
        } else {
            android.graphics.Color.rgb(56, 189, 248)
        }
        drawContext.canvas.nativeCanvas.drawRoundRect(rect, 12f, 12f, convoyTagBorderPaint)
        drawContext.canvas.nativeCanvas.drawText(label, cx, top + 27f, convoyTagTextPaint)
    }

    fun DrawScope.drawDriverOnFoot2DSide(
        centerX: Float,
        groundY: Float,
        scale: Float,
        facingRight: Boolean,
        walkCycle: Float,
        walkSpeed: Float,
        driverSprite: ImageBitmap? = null
    ) {
        val dir = if (facingRight) 1f else -1f
        val bob = abs(sin(walkCycle)) * 0.08f * scale * walkSpeed

        drawOval(
            color = Color(0x9605070A),
            topLeft = Offset(centerX - 0.44f * scale, groundY - 0.10f * scale),
            size = Size(0.88f * scale, 0.22f * scale)
        )

        if (driverSprite != null) {
            val spriteAspect = driverSprite.width.toFloat() / driverSprite.height.coerceAtLeast(1).toFloat()
            val drawH = 1.92f * scale
            val drawW = drawH * spriteAspect
            val leanDeg = sin(walkCycle) * 4.5f * walkSpeed
            withTransform({
                if (!facingRight) {
                    scale(scaleX = -1f, scaleY = 1f, pivot = Offset(centerX, groundY))
                }
                rotate(degrees = leanDeg, pivot = Offset(centerX, groundY))
            }) {
                drawImage(
                    image = driverSprite,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(driverSprite.width, driverSprite.height),
                    dstOffset = IntOffset((centerX - drawW * 0.5f).toInt(), (groundY - drawH - bob).toInt()),
                    dstSize = IntSize(drawW.toInt().coerceAtLeast(1), drawH.toInt().coerceAtLeast(1))
                )
            }
            return
        }

        val swing = sin(walkCycle) * (0.35f + walkSpeed * 0.65f) * 28f
        val hipY = groundY - 0.88f * scale - bob
        val shoulderY = groundY - 1.46f * scale - bob

        rotate(degrees = -swing, pivot = Offset(centerX, hipY)) {
            drawLine(Color(0xFF1E3A5F), Offset(centerX, hipY), Offset(centerX, groundY - 0.14f * scale - bob), strokeWidth = 0.20f * scale, cap = StrokeCap.Round)
            drawRoundRect(Color(0xFF3E2723), Offset(centerX - 0.12f * scale, groundY - 0.18f * scale - bob), Size(0.34f * scale, 0.16f * scale), CornerRadius(0.05f * scale, 0.05f * scale))
        }

        rotate(degrees = swing, pivot = Offset(centerX, hipY)) {
            drawLine(Color(0xFF254B7A), Offset(centerX, hipY), Offset(centerX, groundY - 0.14f * scale - bob), strokeWidth = 0.22f * scale, cap = StrokeCap.Round)
            drawRoundRect(Color(0xFF4E342E), Offset(centerX - 0.12f * scale, groundY - 0.18f * scale - bob), Size(0.36f * scale, 0.16f * scale), CornerRadius(0.05f * scale, 0.05f * scale))
        }

        drawRoundRect(Color(0xFF1F2937), Offset(centerX - 0.22f * scale, shoulderY), Size(0.44f * scale, 0.62f * scale), CornerRadius(0.10f * scale, 0.10f * scale))
        drawRoundRect(Color(0xFFF97316), Offset(centerX - 0.20f * scale, shoulderY + 0.06f * scale), Size(0.40f * scale, 0.48f * scale), CornerRadius(0.08f * scale, 0.08f * scale))
        drawRect(Color(0xFFE2E8F0), Offset(centerX - 0.20f * scale, shoulderY + 0.34f * scale), Size(0.40f * scale, 0.07f * scale))

        val headCy = shoulderY - 0.20f * scale
        drawCircle(Color(0xFFE0AC84), radius = 0.18f * scale, center = Offset(centerX, headCy))
        drawArc(Color(0xFF1E293B), startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = Offset(centerX - 0.19f * scale, headCy - 0.21f * scale), size = Size(0.38f * scale, 0.26f * scale))
        drawLine(Color(0xFFF59E0B), Offset(centerX, headCy - 0.08f * scale), Offset(centerX + 0.28f * scale * dir, headCy - 0.06f * scale), strokeWidth = 0.06f * scale, cap = StrokeCap.Round)

        rotate(degrees = -swing * 0.85f, pivot = Offset(centerX, shoulderY + 0.08f * scale)) {
            drawLine(Color(0xFF1F2937), Offset(centerX, shoulderY + 0.08f * scale), Offset(centerX, hipY - 0.04f * scale), strokeWidth = 0.15f * scale, cap = StrokeCap.Round)
            drawCircle(Color(0xFFE0AC84), radius = 0.07f * scale, center = Offset(centerX, hipY - 0.02f * scale))
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
