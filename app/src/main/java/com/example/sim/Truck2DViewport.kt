package com.example.sim

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.model.CameraMode
import com.example.model.EnvironmentTheme
import com.example.model.RouteSpec
import com.example.model.TruckSpec
import com.example.sim.Truck2DArtwork.drawDriverOnFoot2DSide
import com.example.sim.Truck2DArtwork.drawTrafficVehicle2DRearFront
import com.example.sim.Truck2DArtwork.drawTrafficVehicle2DSide
import com.example.sim.Truck2DArtwork.drawTruckRig2DSide
import com.example.sim.Truck2DArtwork.drawTruckRig2DTopDown
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private data class StarSeed(val xFrac: Float, val yFrac: Float, val radius: Float, val phase: Float)
private data class RainDropSeed(val xFrac: Float, val yFrac: Float, val speedMul: Float, val len: Float)

private class ViewportDrawCache(val asphaltTile: ImageBitmap) {
    val mountainPath = Path()
    val snowCapPath = Path()
    val treePath = Path()
    val houseRoofPath = Path()
    val roadRibbonPath = Path()
    val asphaltTopDownPath = Path()

    val signTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(15, 23, 42)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val gantryTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val fuelSignPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(15, 23, 42)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
}

private fun createAsphaltTextureTile(): ImageBitmap {
    val w = 512
    val h = 128
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = Paint()
    val rng = Random(4201)

    canvas.drawColor(android.graphics.Color.TRANSPARENT)

    // Subtle dark tire wear grooves along traffic lanes
    paint.color = android.graphics.Color.argb(34, 8, 10, 14)
    canvas.drawRect(0f, h * 0.16f, w.toFloat(), h * 0.25f, paint)
    canvas.drawRect(0f, h * 0.50f, w.toFloat(), h * 0.60f, paint)
    canvas.drawRect(0f, h * 0.74f, w.toFloat(), h * 0.85f, paint)

    // Fine mineral granite aggregate speckles
    for (i in 0 until 2600) {
        val x = rng.nextFloat() * w
        val y = rng.nextFloat() * h
        val bright = rng.nextBoolean()
        paint.color = if (bright) {
            android.graphics.Color.argb(26, 210, 218, 228)
        } else {
            android.graphics.Color.argb(32, 12, 15, 20)
        }
        canvas.drawRect(x, y, x + 2f, y + 1.5f, paint)
    }

    // Subtle sealed asphalt micro-seams
    paint.color = android.graphics.Color.argb(28, 10, 12, 16)
    paint.strokeWidth = 1.4f
    canvas.drawLine(40f, 38f, 155f, 44f, paint)
    canvas.drawLine(290f, 88f, 430f, 84f, paint)

    return bmp.asImageBitmap()
}

@Composable
fun Truck2DViewport(
    physics: TruckPhysicsEngine,
    truck: TruckSpec,
    route: RouteSpec,
    cameraMode: CameraMode,
    paused: Boolean,
    modifier: Modifier = Modifier
) {
    val snapshot = remember(physics) { RenderSnapshot() }
    var frameTick by remember { mutableLongStateOf(0L) }
    val drawCache = remember { ViewportDrawCache(createAsphaltTextureTile()) }

    val stars = remember {
        val rng = Random(2025)
        List(78) {
            StarSeed(
                xFrac = rng.nextFloat(),
                yFrac = rng.nextFloat() * 0.46f,
                radius = 1.0f + rng.nextFloat() * 1.9f,
                phase = rng.nextFloat() * 6.28f
            )
        }
    }
    val rainSeeds = remember {
        val rng = Random(909)
        List(88) {
            RainDropSeed(
                xFrac = rng.nextFloat(),
                yFrac = rng.nextFloat(),
                speedMul = 0.85f + rng.nextFloat() * 0.55f,
                len = 16f + rng.nextFloat() * 22f
            )
        }
    }

    LaunchedEffect(physics, paused) {
        while (true) {
            withFrameNanos { nanos ->
                physics.readRenderState(snapshot)
                frameTick = nanos
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE")
        val sync = frameTick

        val tod = snapshot.timeOfDay
        val nightFactor = computeNightFactor(tod)
        val sunsetFactor = computeSunsetFactor(tod)

        when (cameraMode) {
            CameraMode.SIDE_2D -> {
                drawSide2DWorld(
                    snapshot = snapshot,
                    physics = physics,
                    truck = truck,
                    route = route,
                    cache = drawCache,
                    nightFactor = nightFactor,
                    sunsetFactor = sunsetFactor,
                    stars = stars,
                    rainSeeds = rainSeeds
                )
            }
            CameraMode.TOPDOWN_2D -> {
                drawTopDown2DWorld(
                    snapshot = snapshot,
                    physics = physics,
                    truck = truck,
                    route = route,
                    cache = drawCache,
                    nightFactor = nightFactor,
                    rainSeeds = rainSeeds
                )
            }
            CameraMode.CAB_2D -> {
                drawCab2DHorizonWorld(
                    snapshot = snapshot,
                    physics = physics,
                    route = route,
                    cache = drawCache,
                    nightFactor = nightFactor,
                    sunsetFactor = sunsetFactor,
                    stars = stars,
                    rainSeeds = rainSeeds
                )
            }
        }
    }
}

private fun DrawScope.drawSide2DWorld(
    snapshot: RenderSnapshot,
    physics: TruckPhysicsEngine,
    truck: TruckSpec,
    route: RouteSpec,
    cache: ViewportDrawCache,
    nightFactor: Float,
    sunsetFactor: Float,
    stars: List<StarSeed>,
    rainSeeds: List<RainDropSeed>
) {
    val w = size.width
    val h = size.height
    val env = route.environment

    val pxPerMeter = (minOf(w, h) / 14.5f).coerceIn(19f, 34f)
    val cameraS = if (snapshot.driverOnFoot) {
        snapshot.driverRoadS
    } else {
        snapshot.roadProgressMeters + (abs(snapshot.speedMs) * 0.14f)
    }

    val horizonY = h * 0.50f
    val roadTopY = h * 0.65f
    val roadBotY = h * 0.89f
    val roadHeight = roadBotY - roadTopY

    // 1. Dynamic 2D Day/Night Sky, Sun/Moon, Twinkling Stars & Clouds
    drawDynamicSky2D(
        w = w,
        horizonY = horizonY + h * 0.16f,
        env = env,
        timeOfDay = snapshot.timeOfDay,
        nightFactor = nightFactor,
        sunsetFactor = sunsetFactor,
        simClock = snapshot.simClock,
        cameraS = cameraS,
        stars = stars
    )

    // 2. Three-Layer Atmospheric 2D Mountains (Snow-capped Alpine / Mesa Strata)
    drawParallaxMountains(
        w = w,
        baseY = roadTopY,
        cameraS = cameraS,
        env = env,
        nightFactor = nightFactor,
        sunsetFactor = sunsetFactor,
        parallax = 0.05f,
        amplitude = h * 0.25f,
        layerIndex = 0,
        cache = cache
    )
    drawParallaxMountains(
        w = w,
        baseY = roadTopY,
        cameraS = cameraS,
        env = env,
        nightFactor = nightFactor,
        sunsetFactor = sunsetFactor,
        parallax = 0.12f,
        amplitude = h * 0.18f,
        layerIndex = 1,
        cache = cache
    )
    drawParallaxMountains(
        w = w,
        baseY = roadTopY,
        cameraS = cameraS,
        env = env,
        nightFactor = nightFactor,
        sunsetFactor = sunsetFactor,
        parallax = 0.24f,
        amplitude = h * 0.11f,
        layerIndex = 2,
        cache = cache
    )

    // 3. Detailed Scenery: Cities, Overpasses, Chimneyed Houses, Trees, Wind Farms & Fuel Stations
    drawSceneryStructures2D(
        w = w,
        groundBaseY = roadTopY,
        cameraS = cameraS,
        pxPerMeter = pxPerMeter,
        physics = physics,
        route = route,
        env = env,
        nightFactor = nightFactor,
        simClock = snapshot.simClock,
        cache = cache
    )

    // 4. HD Textured Multi-Lane 2D Asphalt Road, Guardrails, Cat's-Eye Reflectors & Signs
    drawSideHighwayRoad2D(
        w = w,
        roadTopY = roadTopY,
        roadBotY = roadBotY,
        cameraS = cameraS,
        pxPerMeter = pxPerMeter,
        route = route,
        nightFactor = nightFactor,
        rainIntensity = snapshot.rainIntensity,
        cache = cache
    )

    // 5. Foreground Grass / Shoulder Lip below road
    val grassTop = lerpColor(
        lerpColor(Color(0xFF3E6332), Color(0xFF6B5335), if (env == EnvironmentTheme.DESERT_NIGHT) 0.85f else 0f),
        Color(0xFF141D18),
        nightFactor * 0.82f
    )
    val grassBot = lerpColor(grassTop, Color(0xFF0A0D10), 0.55f)
    drawRect(
        brush = Brush.verticalGradient(listOf(grassTop, grassBot), startY = roadBotY, endY = h),
        topLeft = Offset(0f, roadBotY),
        size = Size(w, h - roadBotY)
    )

    // 6. Depth-Sorted 2D Vehicles & Walking Driver
    fun laneToGroundY(laneOff: Float): Float {
        val norm = ((laneOff + 4.35f) / 8.70f).coerceIn(0f, 1f)
        return roadTopY + roadHeight * (0.28f + norm * 0.58f)
    }

    val truckAnchorX = w * 0.42f - (cameraS - snapshot.roadProgressMeters) * pxPerMeter
    val playerGroundY = laneToGroundY(snapshot.laneOffset)
    val visibleWindowMeters = (w / pxPerMeter) * 0.85f

    for (v in snapshot.trafficCopy) {
        val ds = v.s - cameraS
        if (abs(ds) > visibleWindowMeters + 16f) continue
        val vy = laneToGroundY(v.laneOffset)
        if (vy <= playerGroundY) {
            val vx = w * 0.42f + ds * pxPerMeter
            val depthScale = pxPerMeter * (0.86f + ((vy - roadTopY) / roadHeight) * 0.22f)
            val wheelA = (v.s / 0.4f) % (2f * PI.toFloat())
            drawTrafficVehicle2DSide(
                isTruck = v.isTruck,
                isOncoming = v.isOncoming,
                bodyColor = Color(v.colorRgb[0], v.colorRgb[1], v.colorRgb[2]),
                centerX = vx,
                groundY = vy,
                scale = depthScale,
                wheelAngleRad = wheelA,
                nightFactor = nightFactor
            )
        }
    }

    val playerDepthScale = pxPerMeter * (0.88f + ((playerGroundY - roadTopY) / roadHeight) * 0.22f)
    val bouncePx = if (abs(snapshot.speedMs) > 0.6f) {
        sin(snapshot.simClock * 22f) * (abs(snapshot.speedMs) / 28f).coerceIn(0f, 1f) * 2.8f
    } else 0f

    val driverGroundY = laneToGroundY(snapshot.driverLaneOffset)
    val driverScreenX = w * 0.42f + (snapshot.driverRoadS - cameraS) * pxPerMeter

    if (snapshot.driverOnFoot && driverGroundY <= playerGroundY) {
        drawDriverOnFoot2DSide(
            centerX = driverScreenX,
            groundY = driverGroundY,
            scale = pxPerMeter * 1.15f,
            facingRight = sin(snapshot.driverYaw) >= 0f,
            walkCycle = snapshot.driverWalkCycle,
            walkSpeed = snapshot.driverWalkSpeed
        )
    }

    drawTruckRig2DSide(
        truck = truck,
        routeId = route.id,
        anchorX = truckAnchorX,
        groundY = playerGroundY,
        scale = playerDepthScale,
        wheelAngleRad = snapshot.wheelSpinAngle,
        cabPitchRad = snapshot.cabPitchOffset + snapshot.tractorPitch * 0.45f,
        bouncePx = bouncePx,
        headlightsOn = snapshot.headlightsOn,
        braking = snapshot.braking,
        reversing = snapshot.reversing,
        blinkerOn = snapshot.leftBlinkerOn || snapshot.rightBlinkerOn,
        nightFactor = nightFactor,
        throttleLevel = snapshot.throttleLevel,
        simClock = snapshot.simClock,
        rainIntensity = snapshot.rainIntensity,
        speedMs = snapshot.speedMs,
        driverInsideCab = !snapshot.driverOnFoot,
        showTrailer = true
    )

    if (snapshot.driverOnFoot && driverGroundY > playerGroundY) {
        drawDriverOnFoot2DSide(
            centerX = driverScreenX,
            groundY = driverGroundY,
            scale = pxPerMeter * 1.15f,
            facingRight = sin(snapshot.driverYaw) >= 0f,
            walkCycle = snapshot.driverWalkCycle,
            walkSpeed = snapshot.driverWalkSpeed
        )
    }

    for (v in snapshot.trafficCopy) {
        val ds = v.s - cameraS
        if (abs(ds) > visibleWindowMeters + 16f) continue
        val vy = laneToGroundY(v.laneOffset)
        if (vy > playerGroundY) {
            val vx = w * 0.42f + ds * pxPerMeter
            val depthScale = pxPerMeter * (0.86f + ((vy - roadTopY) / roadHeight) * 0.22f)
            val wheelA = (v.s / 0.4f) % (2f * PI.toFloat())
            drawTrafficVehicle2DSide(
                isTruck = v.isTruck,
                isOncoming = v.isOncoming,
                bodyColor = Color(v.colorRgb[0], v.colorRgb[1], v.colorRgb[2]),
                centerX = vx,
                groundY = vy,
                scale = depthScale,
                wheelAngleRad = wheelA,
                nightFactor = nightFactor
            )
        }
    }

    // 7. Ambient Night Vignette, Volumetric Fog, Rain & Speed Motion Lines
    drawAtmosphericEffects2D(
        w = w,
        h = h,
        roadBotY = roadBotY,
        speedMs = snapshot.speedMs,
        nightFactor = nightFactor,
        rainIntensity = snapshot.rainIntensity,
        fogDensity = snapshot.fogDensity,
        simClock = snapshot.simClock,
        rainSeeds = rainSeeds
    )
}

private fun DrawScope.drawTopDown2DWorld(
    snapshot: RenderSnapshot,
    physics: TruckPhysicsEngine,
    truck: TruckSpec,
    route: RouteSpec,
    cache: ViewportDrawCache,
    nightFactor: Float,
    rainSeeds: List<RainDropSeed>
) {
    val w = size.width
    val h = size.height
    val env = route.environment
    val pxPerMeter = (minOf(w, h) / 28f).coerceIn(14f, 24f)

    val terrainCol = when (env) {
        EnvironmentTheme.ALPINE_DAY -> lerpColor(Color(0xFF2D4A29), Color(0xFF0E1711), nightFactor * 0.8f)
        EnvironmentTheme.COASTAL_SUNSET -> lerpColor(Color(0xFF475736), Color(0xFF141812), nightFactor * 0.8f)
        EnvironmentTheme.DESERT_NIGHT -> lerpColor(Color(0xFF5A3E2B), Color(0xFF1A120D), nightFactor * 0.8f)
        EnvironmentTheme.STORM_SUMMIT -> lerpColor(Color(0xFF2A3B31), Color(0xFF0D1411), nightFactor * 0.8f)
    }
    drawRect(color = terrainCol, size = size)

    val centerS = if (snapshot.driverOnFoot) snapshot.driverRoadS else snapshot.roadProgressMeters
    val roadHalfPx = physics.roadHalfWidth * pxPerMeter
    val playerScreenY = h * 0.64f
    val roadCenterX = w * 0.5f

    val stepPx = 18f
    val ySamples = ((h + 120f) / stepPx).toInt() + 2
    val curveFactor = physics.road.sampleAtDistance(centerS).curvature

    fun roadXAtY(sy: Float): Float {
        val dyMeters = (playerScreenY - sy) / pxPerMeter
        return roadCenterX + curveFactor * dyMeters * dyMeters * 18f
    }

    val asphaltPath = cache.asphaltTopDownPath
    asphaltPath.reset()
    for (i in 0..ySamples) {
        val sy = -60f + i * stepPx
        val rx = roadXAtY(sy)
        if (i == 0) asphaltPath.moveTo(rx - roadHalfPx - 14f, sy) else asphaltPath.lineTo(rx - roadHalfPx - 14f, sy)
    }
    for (i in ySamples downTo 0) {
        val sy = -60f + i * stepPx
        val rx = roadXAtY(sy)
        asphaltPath.lineTo(rx + roadHalfPx + 14f, sy)
    }
    asphaltPath.close()
    drawPath(asphaltPath, color = Color(0xFF252930))

    // White edge lines, dashed yellow center line & roadside trees/houses
    for (i in 0 until ySamples) {
        val sy0 = -60f + i * stepPx
        val sy1 = sy0 + stepPx
        val rx0 = roadXAtY(sy0)
        val rx1 = roadXAtY(sy1)
        drawLine(Color(0xFFDDE3EB), Offset(rx0 - roadHalfPx + 6f, sy0), Offset(rx1 - roadHalfPx + 6f, sy1), strokeWidth = 3f)
        drawLine(Color(0xFFDDE3EB), Offset(rx0 + roadHalfPx - 6f, sy0), Offset(rx1 + roadHalfPx - 6f, sy1), strokeWidth = 3f)

        val worldS = centerS + (playerScreenY - sy0) / pxPerMeter
        if (((worldS % 12f) + 12f) % 12f < 6f) {
            drawLine(Color(0xFFF5B82E), Offset(rx0, sy0), Offset(rx1, sy1), strokeWidth = 4f)
        }

        val treeBucket = (worldS / 18f).toInt()
        if (abs(worldS % 18f) < 1.2f) {
            for (side in floatArrayOf(-1f, 1f)) {
                val offsetOut = roadHalfPx + 28f + ((abs(treeBucket) * 37) % 55)
                val tx = rx0 + side * offsetOut
                drawCircle(Color(0x55000000), radius = 16f, center = Offset(tx + 4f, sy0 + 4f))
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF4D8A43), Color(0xFF1F421D)),
                        center = Offset(tx - 3f, sy0 - 3f),
                        radius = 16f
                    ),
                    radius = 16f,
                    center = Offset(tx, sy0)
                )
            }
        }
    }

    for (fs in physics.fuelStationPositions) {
        val ds = fs - centerS
        val sy = playerScreenY - ds * pxPerMeter
        if (sy in -180f..(h + 180f)) {
            val rx = roadXAtY(sy)
            val bayX = rx + roadHalfPx + 8f
            drawRoundRect(
                color = Color(0xFF1E293B),
                topLeft = Offset(bayX, sy - 48f),
                size = Size(58f, 96f),
                cornerRadius = CornerRadius(8f, 8f)
            )
            drawRoundRect(
                color = Color(0xFFF59E0B),
                topLeft = Offset(bayX + 6f, sy - 42f),
                size = Size(46f, 84f),
                cornerRadius = CornerRadius(6f, 6f),
                style = Stroke(width = 3f)
            )
        }
    }

    for (v in snapshot.trafficCopy) {
        val ds = v.s - centerS
        val sy = playerScreenY - ds * pxPerMeter
        if (sy !in -120f..(h + 120f)) continue
        val rx = roadXAtY(sy)
        val vx = rx + v.laneOffset * pxPerMeter
        val vw = (if (v.isTruck) 2.3f else 1.85f) * pxPerMeter
        val vl = (if (v.isTruck) 7.0f else 4.2f) * pxPerMeter
        val vColor = Color(v.colorRgb[0], v.colorRgb[1], v.colorRgb[2])

        drawRoundRect(
            color = Color(0x66000000),
            topLeft = Offset(vx - vw * 0.5f + 4f, sy - vl * 0.5f + 4f),
            size = Size(vw, vl),
            cornerRadius = CornerRadius(6f, 6f)
        )
        drawRoundRect(
            color = vColor,
            topLeft = Offset(vx - vw * 0.5f, sy - vl * 0.5f),
            size = Size(vw, vl),
            cornerRadius = CornerRadius(6f, 6f)
        )
        val frontY = if (v.isOncoming) sy + vl * 0.5f else sy - vl * 0.5f
        val rearY = if (v.isOncoming) sy - vl * 0.5f else sy + vl * 0.5f
        drawCircle(Color(0xFFFFF4C2), radius = 3.5f, center = Offset(vx - vw * 0.32f, frontY))
        drawCircle(Color(0xFFFFF4C2), radius = 3.5f, center = Offset(vx + vw * 0.32f, frontY))
        drawCircle(Color(0xFFFF3030), radius = 3.2f, center = Offset(vx - vw * 0.32f, rearY))
        drawCircle(Color(0xFFFF3030), radius = 3.2f, center = Offset(vx + vw * 0.32f, rearY))
    }

    val truckY = if (snapshot.driverOnFoot) {
        playerScreenY - (snapshot.roadProgressMeters - centerS) * pxPerMeter
    } else {
        playerScreenY
    }
    val truckX = roadXAtY(truckY) + snapshot.laneOffset * pxPerMeter
    val relTrailerDeg = Math.toDegrees((snapshot.trailerYaw - snapshot.tractorYaw).toDouble()).toFloat()
    val tractorTurnDeg = (snapshot.steerWheelAngleRad * 26f).coerceIn(-18f, 18f)

    drawTruckRig2DTopDown(
        truck = truck,
        routeId = route.id,
        cx = truckX,
        cy = truckY,
        scale = pxPerMeter,
        headingDeg = tractorTurnDeg,
        trailerRelDeg = relTrailerDeg,
        headlightsOn = snapshot.headlightsOn,
        braking = snapshot.braking,
        nightFactor = nightFactor
    )

    if (snapshot.driverOnFoot) {
        val dx = roadXAtY(playerScreenY) + snapshot.driverLaneOffset * pxPerMeter
        drawCircle(Color(0x88000000), radius = 11f, center = Offset(dx + 3f, playerScreenY + 3f))
        drawCircle(Color(0xFFF97316), radius = 9f, center = Offset(dx, playerScreenY))
        drawCircle(Color(0xFF1E293B), radius = 5f, center = Offset(dx, playerScreenY - 2f))
    }

    drawAtmosphericEffects2D(
        w = w,
        h = h,
        roadBotY = h,
        speedMs = snapshot.speedMs,
        nightFactor = nightFactor * 0.85f,
        rainIntensity = snapshot.rainIntensity,
        fogDensity = snapshot.fogDensity,
        simClock = snapshot.simClock,
        rainSeeds = rainSeeds
    )
}

private fun DrawScope.drawCab2DHorizonWorld(
    snapshot: RenderSnapshot,
    physics: TruckPhysicsEngine,
    route: RouteSpec,
    cache: ViewportDrawCache,
    nightFactor: Float,
    sunsetFactor: Float,
    stars: List<StarSeed>,
    rainSeeds: List<RainDropSeed>
) {
    val w = size.width
    val h = size.height
    val horizonY = h * 0.42f
    val env = route.environment

    drawDynamicSky2D(
        w = w,
        horizonY = horizonY + h * 0.05f,
        env = env,
        timeOfDay = snapshot.timeOfDay,
        nightFactor = nightFactor,
        sunsetFactor = sunsetFactor,
        simClock = snapshot.simClock,
        cameraS = snapshot.roadProgressMeters * 0.2f,
        stars = stars
    )

    drawParallaxMountains(
        w = w,
        baseY = horizonY,
        cameraS = snapshot.roadProgressMeters * 0.35f + snapshot.laneOffset * 6f,
        env = env,
        nightFactor = nightFactor,
        sunsetFactor = sunsetFactor,
        parallax = 0.25f,
        amplitude = h * 0.14f,
        layerIndex = 0,
        cache = cache
    )

    val groundCol = lerpColor(
        if (env == EnvironmentTheme.DESERT_NIGHT) Color(0xFF5A402D) else Color(0xFF33522B),
        Color(0xFF0E1311),
        nightFactor * 0.82f
    )
    drawRect(
        brush = Brush.verticalGradient(listOf(groundCol, Color(0xFF131915)), startY = horizonY, endY = h),
        topLeft = Offset(0f, horizonY),
        size = Size(w, h - horizonY)
    )

    val curve = physics.road.sampleAtDistance(snapshot.roadProgressMeters + 35f).curvature
    val laneShiftPx = (snapshot.laneOffset / 5.5f) * (w * 0.22f)
    val topCenterX = w * 0.5f + curve * w * 18f - laneShiftPx * 0.25f
    val botCenterX = w * 0.5f - laneShiftPx
    val topHalfW = w * 0.045f
    val botHalfW = w * 0.54f

    val roadPoly = cache.roadRibbonPath
    roadPoly.reset()
    roadPoly.moveTo(topCenterX - topHalfW, horizonY)
    roadPoly.lineTo(topCenterX + topHalfW, horizonY)
    roadPoly.lineTo(botCenterX + botHalfW, h)
    roadPoly.lineTo(botCenterX - botHalfW, h)
    roadPoly.close()
    drawPath(
        path = roadPoly,
        brush = Brush.verticalGradient(
            listOf(Color(0xFF3A3F47), Color(0xFF20242A)),
            startY = horizonY,
            endY = h
        )
    )

    // White edge lines + wet reflections
    drawLine(Color(0xFFDDE3EB), Offset(topCenterX - topHalfW * 0.9f, horizonY), Offset(botCenterX - botHalfW * 0.9f, h), strokeWidth = 4f)
    drawLine(Color(0xFFDDE3EB), Offset(topCenterX + topHalfW * 0.9f, horizonY), Offset(botCenterX + botHalfW * 0.9f, h), strokeWidth = 4f)

    // Scrolling dashed center divider & flanking trees/light poles in 2D horizon
    val dashPhase = (snapshot.roadProgressMeters * 0.08f) % 1f
    for (seg in 0..7) {
        val t0 = ((seg + (1f - dashPhase)) / 8f).coerceIn(0f, 1f)
        val t1 = ((seg + 0.55f + (1f - dashPhase)) / 8f).coerceIn(0f, 1f)
        if (t1 <= t0) continue
        val p0 = t0 * t0
        val p1 = t1 * t1
        val y0 = horizonY + (h - horizonY) * p0
        val y1 = horizonY + (h - horizonY) * p1
        val x0 = topCenterX + (botCenterX - topCenterX) * p0
        val x1 = topCenterX + (botCenterX - topCenterX) * p1
        drawLine(Color(0xFFF5B82E), Offset(x0, y0), Offset(x1, y1), strokeWidth = (2f + p1 * 7f))

        // Roadside light poles & pine silhouettes on every 2nd segment
        if (seg % 2 == 0) {
            val halfAtP = topHalfW + (botHalfW - topHalfW) * p0
            val poleH = (18f + p0 * 115f)
            for (side in floatArrayOf(-1f, 1f)) {
                val px = x0 + side * (halfAtP * 1.12f)
                drawLine(
                    color = Color(0xFF475569),
                    start = Offset(px, y0),
                    end = Offset(px, y0 - poleH),
                    strokeWidth = (1.5f + p0 * 3.5f)
                )
                drawCircle(
                    color = if (nightFactor > 0.25f) Color(0xFFFFE599) else Color(0xFFCBD5E1),
                    radius = (1.5f + p0 * 3.5f),
                    center = Offset(px - side * (2f + p0 * 6f), y0 - poleH)
                )
            }
        }
    }

    if (snapshot.headlightsOn) {
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFF4C2).copy(alpha = 0.26f + nightFactor * 0.36f), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.68f),
                radius = w * 0.38f
            ),
            topLeft = Offset(w * 0.12f, h * 0.48f),
            size = Size(w * 0.76f, h * 0.40f),
            blendMode = BlendMode.Screen
        )
    }

    // Detailed 2D Rear/Front Vehicles scaled smoothly along the 2D horizon ribbon
    for (v in snapshot.trafficCopy) {
        val ds = v.s - snapshot.roadProgressMeters
        if (ds in 6f..140f) {
            val depth = (1f - (ds / 140f)).coerceIn(0f, 1f)
            val p = depth * depth
            val vy = horizonY + (h * 0.72f - horizonY) * p
            val roadHalfAtDepth = topHalfW + (botHalfW - topHalfW) * p
            val cxAtDepth = topCenterX + (botCenterX - topCenterX) * p
            val vx = cxAtDepth + (v.laneOffset / 5.5f) * roadHalfAtDepth
            val carW = (if (v.isTruck) 0.40f else 0.30f) * roadHalfAtDepth
            drawTrafficVehicle2DRearFront(
                isTruck = v.isTruck,
                isOncoming = v.isOncoming,
                bodyColor = Color(v.colorRgb[0], v.colorRgb[1], v.colorRgb[2]),
                cx = vx,
                bottomY = vy,
                width = carW,
                nightFactor = nightFactor
            )
        }
    }

    drawAtmosphericEffects2D(
        w = w,
        h = h,
        roadBotY = h,
        speedMs = snapshot.speedMs,
        nightFactor = nightFactor * 0.65f,
        rainIntensity = snapshot.rainIntensity,
        fogDensity = snapshot.fogDensity,
        simClock = snapshot.simClock,
        rainSeeds = rainSeeds
    )
}

private fun DrawScope.drawDynamicSky2D(
    w: Float,
    horizonY: Float,
    env: EnvironmentTheme,
    timeOfDay: Float,
    nightFactor: Float,
    sunsetFactor: Float,
    simClock: Float,
    cameraS: Float,
    stars: List<StarSeed>
) {
    val dayTop = if (env == EnvironmentTheme.STORM_SUMMIT) Color(0xFF3E4C5E) else Color(0xFF1D62AD)
    val dayBot = if (env == EnvironmentTheme.STORM_SUMMIT) Color(0xFF7B8C9E) else Color(0xFF8CC5EE)
    val sunsetTop = Color(0xFF28204B)
    val sunsetBot = Color(0xFFEC7A38)
    val nightTop = Color(0xFF040814)
    val nightBot = Color(0xFF13223D)

    val topCol = lerpColor(lerpColor(dayTop, sunsetTop, sunsetFactor), nightTop, nightFactor)
    val botCol = lerpColor(lerpColor(dayBot, sunsetBot, sunsetFactor), nightBot, nightFactor)

    drawRect(
        brush = Brush.verticalGradient(listOf(topCol, botCol), startY = 0f, endY = horizonY),
        size = Size(w, horizonY)
    )

    if (nightFactor > 0.22f) {
        val starVis = ((nightFactor - 0.22f) / 0.78f).coerceIn(0f, 1f)
        for (st in stars) {
            val twinkle = 0.55f + 0.45f * sin(simClock * 3.0f + st.phase)
            drawCircle(
                color = Color(0xFFF1F5F9).copy(alpha = starVis * twinkle),
                radius = st.radius,
                center = Offset(st.xFrac * w, st.yFrac * horizonY)
            )
        }
    }

    val isDayOrSunset = timeOfDay in 0.22f..0.80f
    if (isDayOrSunset) {
        val sunArc = ((timeOfDay - 0.22f) / 0.58f).coerceIn(0f, 1f)
        val sunX = w * (0.15f + sunArc * 0.70f)
        val sunY = horizonY * (0.78f - sin(sunArc * PI.toFloat()) * 0.62f)
        val sunCol = lerpColor(Color(0xFFFFF8D1), Color(0xFFFF8C38), sunsetFactor)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(sunCol.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(sunX, sunY),
                radius = w * 0.16f
            ),
            radius = w * 0.16f,
            center = Offset(sunX, sunY)
        )
        drawCircle(color = sunCol, radius = 22f, center = Offset(sunX, sunY))
    } else {
        val moonArc = if (timeOfDay >= 0.80f) (timeOfDay - 0.80f) / 0.42f else (timeOfDay + 0.20f) / 0.42f
        val moonX = w * (0.18f + moonArc.coerceIn(0f, 1f) * 0.64f)
        val moonY = horizonY * (0.68f - sin(moonArc.coerceIn(0f, 1f) * PI.toFloat()) * 0.48f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0x38D8E6FF), Color.Transparent),
                center = Offset(moonX, moonY),
                radius = 74f
            ),
            radius = 74f,
            center = Offset(moonX, moonY)
        )
        drawCircle(color = Color(0xFFE6EEFF), radius = 17f, center = Offset(moonX, moonY))
    }

    val cloudAlpha = (0.36f * (1f - nightFactor * 0.65f)).coerceIn(0.08f, 0.42f)
    val cloudCol = lerpColor(Color(0xFFFFFFFF), Color(0xFFFFC18E), sunsetFactor).copy(alpha = cloudAlpha)
    val drift = (cameraS * 0.04f + simClock * 4.5f)
    for (i in 0..4) {
        val cx = ((i * w * 0.28f - drift) % (w + 260f) + (w + 260f)) % (w + 260f) - 130f
        val cy = horizonY * (0.16f + (i % 3) * 0.12f)
        drawOval(cloudCol, topLeft = Offset(cx, cy), size = Size(165f, 36f))
        drawOval(cloudCol, topLeft = Offset(cx + 38f, cy - 14f), size = Size(92f, 34f))
    }
}

private fun DrawScope.drawParallaxMountains(
    w: Float,
    baseY: Float,
    cameraS: Float,
    env: EnvironmentTheme,
    nightFactor: Float,
    sunsetFactor: Float,
    parallax: Float,
    amplitude: Float,
    layerIndex: Int,
    cache: ViewportDrawCache
) {
    val step = 28f
    val count = (w / step).toInt() + 2
    val scroll = cameraS * parallax
    val phaseOff = layerIndex * 1.65f

    val path = cache.mountainPath
    path.reset()
    path.moveTo(0f, baseY + 12f)

    val snowPath = cache.snowCapPath
    val drawSnow = layerIndex == 0 && (env == EnvironmentTheme.ALPINE_DAY || env == EnvironmentTheme.STORM_SUMMIT)
    if (drawSnow) {
        snowPath.reset()
        snowPath.moveTo(0f, baseY + 12f)
    }

    for (i in 0..count) {
        val sx = i * step
        val worldX = sx * 0.015f + scroll * 0.12f + phaseOff
        val hWave = if (env == EnvironmentTheme.DESERT_NIGHT) {
            ((sin(worldX * 0.65f) + 0.5f * cos(worldX * 1.4f)).coerceIn(-0.4f, 0.9f) + 0.4f) * 0.72f
        } else {
            0.40f + 0.36f * sin(worldX * 0.9f + phaseOff) + 0.22f * cos(worldX * 2.1f)
        }
        val normH = hWave.coerceIn(0.08f, 1.05f)
        val py = baseY - (normH * amplitude)
        path.lineTo(sx, py)
        if (drawSnow) {
            val snowY = if (normH > 0.64f) py else py + amplitude * 0.22f
            snowPath.lineTo(sx, snowY.coerceAtMost(baseY))
        }
    }
    path.lineTo(w + step, baseY + 12f)
    path.close()

    val dayCol = when {
        layerIndex == 0 && env == EnvironmentTheme.ALPINE_DAY -> Color(0xFF567391)
        layerIndex == 0 && env == EnvironmentTheme.DESERT_NIGHT -> Color(0xFF8A543E)
        layerIndex == 0 -> Color(0xFF5C7085)
        layerIndex == 1 && env == EnvironmentTheme.DESERT_NIGHT -> Color(0xFF704531)
        layerIndex == 1 -> Color(0xFF3E5E4E)
        env == EnvironmentTheme.DESERT_NIGHT -> Color(0xFF5A3726)
        else -> Color(0xFF284733)
    }
    val tinted = lerpColor(lerpColor(dayCol, Color(0xFF6B3F4C), sunsetFactor * 0.50f), Color(0xFF0E1622), nightFactor * 0.85f)
    drawPath(
        path = path,
        brush = Brush.verticalGradient(listOf(tinted, Truck2DArtwork.darken(tinted, 0.72f)), startY = baseY - amplitude, endY = baseY)
    )

    if (drawSnow) {
        snowPath.lineTo(w + step, baseY - amplitude * 0.56f)
        snowPath.lineTo(0f, baseY - amplitude * 0.56f)
        snowPath.close()
        val snowCol = lerpColor(Color(0xFFF1F5F9), Color(0xFF475569), nightFactor * 0.78f).copy(alpha = 0.78f)
        clipRect(top = baseY - amplitude * 1.1f, bottom = baseY - amplitude * 0.60f) {
            drawPath(path = path, color = snowCol)
        }
    }
}

private fun DrawScope.drawSceneryStructures2D(
    w: Float,
    groundBaseY: Float,
    cameraS: Float,
    pxPerMeter: Float,
    physics: TruckPhysicsEngine,
    route: RouteSpec,
    env: EnvironmentTheme,
    nightFactor: Float,
    simClock: Float,
    cache: ViewportDrawCache
) {
    val spanMeters = (w / pxPerMeter) * 0.75f + 44f
    val minS = ((cameraS - spanMeters) / 14f).toInt() * 14f
    val maxS = cameraS + spanMeters

    val treePath = cache.treePath
    val houseRoofPath = cache.houseRoofPath

    // 1. Recurring Natural Pine/Cactus Trees & Chimneyed Rural Houses
    var sPos = minS.coerceAtLeast(0f)
    while (sPos <= maxS) {
        val sx = w * 0.42f + (sPos - cameraS) * pxPerMeter
        val idx = (sPos / 14f).toInt()

        val treeH = pxPerMeter * (2.7f + (idx % 3) * 0.65f)
        if (env == EnvironmentTheme.DESERT_NIGHT) {
            val cCol = lerpColor(Color(0xFF2E5C38), Color(0xFF0E1D13), nightFactor * 0.85f)
            drawLine(cCol, Offset(sx, groundBaseY), Offset(sx, groundBaseY - treeH * 0.72f), strokeWidth = 8f, cap = StrokeCap.Round)
            drawLine(cCol, Offset(sx - 12f, groundBaseY - treeH * 0.45f), Offset(sx + 12f, groundBaseY - treeH * 0.45f), strokeWidth = 6f, cap = StrokeCap.Round)
            drawLine(cCol, Offset(sx - 12f, groundBaseY - treeH * 0.45f), Offset(sx - 12f, groundBaseY - treeH * 0.62f), strokeWidth = 5f, cap = StrokeCap.Round)
            drawLine(cCol, Offset(sx + 12f, groundBaseY - treeH * 0.45f), Offset(sx + 12f, groundBaseY - treeH * 0.66f), strokeWidth = 5f, cap = StrokeCap.Round)
        } else {
            val trunkCol = lerpColor(Color(0xFF4B3626), Color(0xFF18120E), nightFactor * 0.8f)
            drawRect(trunkCol, topLeft = Offset(sx - 3.5f, groundBaseY - treeH * 0.26f), size = Size(7f, treeH * 0.26f))
            val leafDark = lerpColor(Color(0xFF1F4724), Color(0xFF0A180E), nightFactor * 0.85f)
            val leafLight = lerpColor(Color(0xFF346B38), Color(0xFF132617), nightFactor * 0.85f)
            for (tier in 0..2) {
                val ty = groundBaseY - treeH * (0.28f + tier * 0.22f)
                val tw = treeH * (0.36f - tier * 0.08f)
                treePath.reset()
                treePath.moveTo(sx, ty - treeH * 0.35f)
                treePath.lineTo(sx + tw, ty)
                treePath.lineTo(sx - tw, ty)
                treePath.close()
                drawPath(treePath, if (tier == 2) leafLight else leafDark)
            }
        }

        if (idx % 4 == 1) {
            val hx = sx + pxPerMeter * 5.2f
            val hw = pxPerMeter * 3.2f
            val hh = pxPerMeter * 1.95f
            val wallCol = lerpColor(Color(0xFFE5DFD3), Color(0xFF393E47), nightFactor * 0.82f)
            val roofCol = lerpColor(Color(0xFF8E3B2E), Color(0xFF2B1615), nightFactor * 0.82f)
            // Brick chimney + animated drifting smoke puff
            val chimX = hx + hw * 0.72f
            drawRect(roofCol, topLeft = Offset(chimX, groundBaseY - hh * 1.55f), size = Size(pxPerMeter * 0.36f, hh * 0.55f))
            val smokeP = (simClock * 0.45f + idx * 0.3f) % 1f
            drawCircle(
                color = Color(0xFFD1D5DB).copy(alpha = (1f - smokeP) * 0.36f),
                radius = (4f + smokeP * 9f),
                center = Offset(chimX + pxPerMeter * 0.18f - smokeP * 14f, groundBaseY - hh * 1.62f - smokeP * 22f)
            )
            // House walls & pitched roof
            drawRect(wallCol, topLeft = Offset(hx, groundBaseY - hh), size = Size(hw, hh))
            houseRoofPath.reset()
            houseRoofPath.moveTo(hx - 7f, groundBaseY - hh)
            houseRoofPath.lineTo(hx + hw * 0.5f, groundBaseY - hh - hh * 0.65f)
            houseRoofPath.lineTo(hx + hw + 7f, groundBaseY - hh)
            houseRoofPath.close()
            drawPath(houseRoofPath, roofCol)
            // Door + mullioned warm glowing windows
            drawRect(Color(0xFF4B3222), topLeft = Offset(hx + hw * 0.42f, groundBaseY - hh * 0.55f), size = Size(hw * 0.16f, hh * 0.55f))
            val winCol = if (nightFactor > 0.25f) Color(0xFFFFD166) else Color(0xFF2A3E52)
            for (wx in floatArrayOf(0.14f, 0.66f)) {
                val leftW = hx + hw * wx
                val topW = groundBaseY - hh * 0.68f
                val ww = hw * 0.20f
                val wh = hh * 0.28f
                drawRect(winCol, topLeft = Offset(leftW, topW), size = Size(ww, wh))
                drawLine(Color(0xFF1E293B), Offset(leftW + ww * 0.5f, topW), Offset(leftW + ww * 0.5f, topW + wh), strokeWidth = 1.5f)
            }
        }

        sPos += 14f
    }

    // 2. Named Landmarks: Cities, Overpasses, Wind Turbines, Fuel Stations & Gantries
    for (lm in physics.landmarks) {
        val ds = lm.s - cameraS
        if (abs(ds) > spanMeters + 35f) continue
        val lx = w * 0.42f + ds * pxPerMeter

        when (lm.kind) {
            LandmarkKind.CITY_ORIGIN, LandmarkKind.CITY_MIDTOWN, LandmarkKind.CITY_DESTINATION -> {
                val buildings = intArrayOf(5, 8, 6, 11, 7, 9, 5)
                buildings.forEachIndexed { bi, floors ->
                    val bw = pxPerMeter * 2.25f
                    val bh = pxPerMeter * (floors * 0.95f)
                    val bx = lx + (bi - 3) * (bw + 4f)
                    val bCol = lerpColor(Color(0xFF475569), Color(0xFF0F172A), nightFactor * 0.78f)
                    drawRect(
                        brush = Brush.verticalGradient(listOf(Truck2DArtwork.lighten(bCol, 0.18f), bCol)),
                        topLeft = Offset(bx, groundBaseY - bh),
                        size = Size(bw, bh)
                    )
                    // Rooftop spire / water tank on tall towers
                    if (floors >= 9) {
                        drawLine(Color(0xFF94A3B8), Offset(bx + bw * 0.5f, groundBaseY - bh), Offset(bx + bw * 0.5f, groundBaseY - bh - pxPerMeter * 1.1f), strokeWidth = 3f)
                        drawCircle(Color(0xFFFF3838), radius = 3.5f, center = Offset(bx + bw * 0.5f, groundBaseY - bh - pxPerMeter * 1.1f))
                    }
                    val litAlpha = (0.20f + nightFactor * 0.72f).coerceIn(0.18f, 0.92f)
                    for (fl in 1 until floors) {
                        for (col in 0..2) {
                            if ((bi + fl + col) % 2 == 0) {
                                drawRect(
                                    color = Color(0xFFFFE082).copy(alpha = litAlpha),
                                    topLeft = Offset(bx + 6f + col * (bw * 0.30f), groundBaseY - bh + fl * pxPerMeter * 0.85f),
                                    size = Size(bw * 0.16f, pxPerMeter * 0.38f)
                                )
                            }
                        }
                    }
                }
            }
            LandmarkKind.WIND_FARM -> {
                for (ti in -1..1) {
                    val tx = lx + ti * pxPerMeter * 7.5f
                    val hubY = groundBaseY - pxPerMeter * 6.4f
                    drawLine(Color(0xFFE2E8F0), Offset(tx, groundBaseY), Offset(tx, hubY), strokeWidth = 5f)
                    rotate(degrees = simClock * 52f + ti * 40f, pivot = Offset(tx, hubY)) {
                        for (b in 0..2) {
                            rotate(degrees = b * 120f, pivot = Offset(tx, hubY)) {
                                drawLine(Color(0xFFF8FAFC), Offset(tx, hubY), Offset(tx, hubY - pxPerMeter * 2.4f), strokeWidth = 4f, cap = StrokeCap.Round)
                            }
                        }
                    }
                }
            }
            LandmarkKind.FUEL_STATION -> {
                val canW = pxPerMeter * 8.4f
                val canH = pxPerMeter * 3.5f
                val left = lx - canW * 0.5f
                drawRect(Color(0xFFCBD5E1), topLeft = Offset(left + pxPerMeter * 0.7f, groundBaseY - canH), size = Size(pxPerMeter * 0.36f, canH))
                drawRect(Color(0xFFCBD5E1), topLeft = Offset(left + canW - pxPerMeter * 1.1f, groundBaseY - canH), size = Size(pxPerMeter * 0.36f, canH))
                drawRoundRect(
                    color = Color(0xFF1E293B),
                    topLeft = Offset(left, groundBaseY - canH - pxPerMeter * 0.62f),
                    size = Size(canW, pxPerMeter * 0.72f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRect(
                    color = Color(0xFFF59E0B),
                    topLeft = Offset(left, groundBaseY - canH - pxPerMeter * 0.24f),
                    size = Size(canW, pxPerMeter * 0.30f)
                )
                cache.fuelSignPaint.textSize = (pxPerMeter * 0.30f).coerceIn(10f, 18f)
                drawContext.canvas.nativeCanvas.drawText(
                    "DIÉSEL 24H",
                    lx,
                    groundBaseY - canH - pxPerMeter * 0.02f,
                    cache.fuelSignPaint
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFFFF3C4).copy(alpha = 0.26f + nightFactor * 0.36f), Color.Transparent),
                        center = Offset(lx, groundBaseY - canH * 0.35f),
                        radius = canW * 0.65f
                    ),
                    radius = canW * 0.65f,
                    center = Offset(lx, groundBaseY - canH * 0.35f),
                    blendMode = BlendMode.Screen
                )
                for (p in -1..1 step 2) {
                    val px = lx + p * pxPerMeter * 1.4f
                    drawRoundRect(
                        color = Color(0xFFDC2626),
                        topLeft = Offset(px - pxPerMeter * 0.32f, groundBaseY - pxPerMeter * 1.45f),
                        size = Size(pxPerMeter * 0.64f, pxPerMeter * 1.45f),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }
            LandmarkKind.HIGHWAY_GANTRY -> {
                val gw = pxPerMeter * 5.8f
                val gh = pxPerMeter * 4.3f
                drawRect(Color(0xFF94A3B8), topLeft = Offset(lx, groundBaseY - gh), size = Size(8f, gh))
                drawRoundRect(
                    color = Color(0xFF047857),
                    topLeft = Offset(lx - gw * 0.5f, groundBaseY - gh),
                    size = Size(gw, pxPerMeter * 1.40f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(lx - gw * 0.5f + 3f, groundBaseY - gh + 3f),
                    size = Size(gw - 6f, pxPerMeter * 1.40f - 6f),
                    cornerRadius = CornerRadius(3f, 3f),
                    style = Stroke(width = 2f)
                )
                cache.gantryTitlePaint.textSize = (pxPerMeter * 0.36f).coerceIn(10f, 18f)
                drawContext.canvas.nativeCanvas.drawText(
                    route.destinationCity.uppercase(),
                    lx,
                    groundBaseY - gh + pxPerMeter * 0.62f,
                    cache.gantryTitlePaint
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "RUTA ${route.displayDistanceKm.toInt()} KM →",
                    lx,
                    groundBaseY - gh + pxPerMeter * 1.10f,
                    cache.gantryTitlePaint
                )
            }
            LandmarkKind.VILLAGE_HOUSES -> Unit
        }
    }
}

private fun DrawScope.drawSideHighwayRoad2D(
    w: Float,
    roadTopY: Float,
    roadBotY: Float,
    cameraS: Float,
    pxPerMeter: Float,
    route: RouteSpec,
    nightFactor: Float,
    rainIntensity: Float,
    cache: ViewportDrawCache
) {
    val roadH = roadBotY - roadTopY
    val asphTop = lerpColor(Color(0xFF3E444D), Color(0xFF1A1E24), nightFactor * 0.65f)
    val asphBot = lerpColor(Color(0xFF282D34), Color(0xFF101318), nightFactor * 0.65f)

    // Gravel shoulder top strip
    drawRect(
        color = lerpColor(Color(0xFF5A544A), Color(0xFF1C1B18), nightFactor * 0.75f),
        topLeft = Offset(0f, roadTopY - 8f),
        size = Size(w, 8f)
    )
    drawRect(
        brush = Brush.verticalGradient(listOf(asphTop, asphBot), startY = roadTopY, endY = roadBotY),
        topLeft = Offset(0f, roadTopY),
        size = Size(w, roadH)
    )

    // Tiled Pre-Baked HD Asphalt Aggregate Texture scrolling cleanly with cameraS!
    val tilePxW = 512f
    val scrollTileOffset = (((cameraS * pxPerMeter) % tilePxW) + tilePxW) % tilePxW
    var tx = -scrollTileOffset
    while (tx < w) {
        drawImage(
            image = cache.asphaltTile,
            dstOffset = IntOffset(tx.roundToInt(), roadTopY.roundToInt()),
            dstSize = IntSize(tilePxW.toInt(), roadH.roundToInt().coerceAtLeast(1)),
            alpha = 0.65f
        )
        tx += tilePxW
    }

    // Wet road mirror sheen during rain
    if (rainIntensity > 0.08f) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF94B4D4).copy(alpha = rainIntensity * 0.16f), Color.Transparent),
                startY = roadTopY,
                endY = roadBotY
            ),
            topLeft = Offset(0f, roadTopY),
            size = Size(w, roadH)
        )
    }

    // Crisp outer highway white edge lines
    drawLine(Color(0xFFE2E8F0), Offset(0f, roadTopY + roadH * 0.08f), Offset(w, roadTopY + roadH * 0.08f), strokeWidth = 4f)
    drawLine(Color(0xFFE2E8F0), Offset(0f, roadBotY - roadH * 0.08f), Offset(w, roadBotY - roadH * 0.08f), strokeWidth = 5f)

    val step = 12f
    val startS = (((cameraS - w / pxPerMeter) / step).toInt() - 1) * step
    val endS = cameraS + w / pxPerMeter + 24f

    val railY = roadTopY - 11f
    drawRect(
        color = lerpColor(Color(0xFF9AA5B1), Color(0xFF3F4752), nightFactor * 0.7f),
        topLeft = Offset(0f, railY),
        size = Size(w, 6f)
    )

    var sPos = startS.coerceAtLeast(0f)
    while (sPos <= endS) {
        val sx = w * 0.42f + (sPos - cameraS) * pxPerMeter

        drawRect(Color(0xFF475569), topLeft = Offset(sx - 2f, railY), size = Size(4f, 14f))

        // Dashed Yellow Center Divider + glowing Amber Cat's-Eye Road Reflector
        drawRect(
            color = Color(0xFFF5B82E),
            topLeft = Offset(sx, roadTopY + roadH * 0.43f),
            size = Size(pxPerMeter * 4.5f, 4f)
        )
        drawCircle(
            color = Color(0xFFFFD54F),
            radius = 2.6f,
            center = Offset(sx + pxPerMeter * 5.2f, roadTopY + roadH * 0.435f)
        )
        // Dashed White Same-Direction Lane Divider
        drawRect(
            color = Color(0xAAFFFFFF),
            topLeft = Offset(sx + pxPerMeter * 2f, roadTopY + roadH * 0.68f),
            size = Size(pxPerMeter * 3.2f, 3f)
        )

        val idx = (sPos / step).toInt()
        if (idx % 3 == 0) {
            val lampH = pxPerMeter * 4.4f
            drawLine(Color(0xFF64748B), Offset(sx, roadTopY - 4f), Offset(sx, roadTopY - lampH), strokeWidth = 4f)
            drawLine(Color(0xFF64748B), Offset(sx, roadTopY - lampH), Offset(sx + 16f, roadTopY - lampH + 4f), strokeWidth = 4f)
            val bulbX = sx + 16f
            val bulbY = roadTopY - lampH + 5f
            if (nightFactor > 0.25f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFFFE599).copy(alpha = 0.55f * nightFactor), Color.Transparent),
                        center = Offset(bulbX, bulbY),
                        radius = pxPerMeter * 2.2f
                    ),
                    radius = pxPerMeter * 2.2f,
                    center = Offset(bulbX, bulbY),
                    blendMode = BlendMode.Screen
                )
                drawOval(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFFFE599).copy(alpha = 0.22f * nightFactor), Color.Transparent),
                        center = Offset(bulbX, roadTopY + roadH * 0.28f),
                        radius = pxPerMeter * 3.4f
                    ),
                    topLeft = Offset(bulbX - pxPerMeter * 3.2f, roadTopY),
                    size = Size(pxPerMeter * 6.4f, roadH * 0.56f),
                    blendMode = BlendMode.Screen
                )
            }
            drawCircle(if (nightFactor > 0.25f) Color(0xFFFFF8D6) else Color(0xFFCBD5E1), radius = 4f, center = Offset(bulbX, bulbY))
        }

        // Crisp Speed Limit Sign with actual speed numeral every 72m
        if (idx % 6 == 2) {
            val signY = roadTopY - pxPerMeter * 2.2f
            drawLine(Color(0xFF94A3B8), Offset(sx, roadTopY - 4f), Offset(sx, signY), strokeWidth = 3f)
            drawCircle(Color.White, radius = 14f, center = Offset(sx, signY))
            drawCircle(Color(0xFFDC2626), radius = 13f, center = Offset(sx, signY), style = Stroke(width = 3.5f))
            cache.signTextPaint.textSize = 11.5f
            drawContext.canvas.nativeCanvas.drawText(
                route.speedLimitKmh.toString(),
                sx,
                signY + 4f,
                cache.signTextPaint
            )
        }

        sPos += step
    }
}

private fun DrawScope.drawAtmosphericEffects2D(
    w: Float,
    h: Float,
    roadBotY: Float,
    speedMs: Float,
    nightFactor: Float,
    rainIntensity: Float,
    fogDensity: Float,
    simClock: Float,
    rainSeeds: List<RainDropSeed>
) {
    val speedKmh = abs(speedMs) * 3.6f
    if (speedKmh > 65f) {
        val streakAlpha = (((speedKmh - 65f) / 60f).coerceIn(0f, 1f) * 0.26f)
        for (i in 0..9) {
            val sy = h * (0.12f + (i / 10f) * 0.78f)
            val sx = ((w * 1.4f - (simClock * speedMs * 42f + i * 197f)) % w + w) % w
            val len = 52f + (speedKmh - 65f) * 1.7f
            drawLine(
                color = Color.White.copy(alpha = streakAlpha),
                start = Offset(sx, sy),
                end = Offset(sx + len, sy),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        }
    }

    if (fogDensity > 0.04f) {
        val fogCol = lerpColor(Color(0xFFCBD5E1), Color(0xFF334155), nightFactor)
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    fogCol.copy(alpha = fogDensity * 0.34f),
                    fogCol.copy(alpha = fogDensity * 0.48f),
                    fogCol.copy(alpha = fogDensity * 0.14f)
                )
            ),
            size = size
        )
    }

    if (rainIntensity > 0.05f) {
        val activeCount = (rainSeeds.size * rainIntensity).toInt().coerceIn(8, rainSeeds.size)
        val rainColor = Color(0xFFD6E8FF).copy(alpha = 0.25f + rainIntensity * 0.30f)
        val windTilt = -(10f + abs(speedMs) * 0.65f)
        for (i in 0 until activeCount) {
            val drop = rainSeeds[i]
            val fallProgress = (drop.yFrac + simClock * 1.45f * drop.speedMul) % 1f
            val xDrift = (drop.xFrac * w - simClock * (85f + abs(speedMs) * 14f) * drop.speedMul)
            val rx = ((xDrift % w) + w) % w
            val ry = fallProgress * h
            drawLine(
                color = rainColor,
                start = Offset(rx, ry),
                end = Offset(rx + windTilt, ry + drop.len),
                strokeWidth = 1.6f,
                cap = StrokeCap.Round
            )
            if (ry > roadBotY - 34f && ry < roadBotY + 8f && i % 4 == 0) {
                drawOval(
                    color = Color(0x45E2F1FF),
                    topLeft = Offset(rx - 5f, ry - 1.5f),
                    size = Size(10f, 3f),
                    style = Stroke(width = 1.2f)
                )
            }
        }
    }

    if (nightFactor > 0.2f) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color(0xFF020408).copy(alpha = nightFactor * 0.42f)),
                center = Offset(w * 0.5f, h * 0.55f),
                radius = maxOf(w, h) * 0.72f
            ),
            size = size
        )
    }
}

private fun computeNightFactor(tod: Float): Float {
    val distFromNoon = abs(tod - 0.50f)
    return ((distFromNoon - 0.24f) / 0.16f).coerceIn(0f, 1f)
}

private fun computeSunsetFactor(tod: Float): Float {
    val dDawn = abs(tod - 0.26f)
    val dDusk = abs(tod - 0.74f)
    val nearest = minOf(dDawn, dDusk)
    return (1f - (nearest / 0.11f)).coerceIn(0f, 1f)
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * k,
        green = a.green + (b.green - a.green) * k,
        blue = a.blue + (b.blue - a.blue) * k,
        alpha = a.alpha + (b.alpha - a.alpha) * k
    )
}
