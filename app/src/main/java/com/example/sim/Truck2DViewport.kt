package com.example.sim

import android.graphics.BitmapFactory
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.R
import com.example.model.CameraMode
import com.example.model.EnvironmentTheme
import com.example.model.RouteSpec
import com.example.model.TruckCatalog
import com.example.model.TruckSpec
import com.example.model.TruckVisualConfig
import com.example.sim.Truck2DArtwork.drawConvoyTag2D
import com.example.sim.Truck2DArtwork.drawDriverOnFoot2DChase
import com.example.sim.Truck2DArtwork.drawDriverOnFoot2DSide
import com.example.sim.Truck2DArtwork.drawTrafficVehicle2DRearFront
import com.example.sim.Truck2DArtwork.drawTrafficVehicle2DSide
import com.example.sim.Truck2DArtwork.drawTruckRig2DRearChase
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
    val volcanoPath = Path()
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

    paint.color = android.graphics.Color.argb(34, 8, 10, 14)
    canvas.drawRect(0f, h * 0.16f, w.toFloat(), h * 0.25f, paint)
    canvas.drawRect(0f, h * 0.50f, w.toFloat(), h * 0.60f, paint)
    canvas.drawRect(0f, h * 0.74f, w.toFloat(), h * 0.85f, paint)

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
    visualConfig: TruckVisualConfig,
    cameraMode: CameraMode,
    paused: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snapshot = remember(physics) { RenderSnapshot() }
    var frameTick by remember { mutableLongStateOf(0L) }
    val drawCache = remember { ViewportDrawCache(createAsphaltTextureTile()) }
    val driverWalkBitmap = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.img_real_driver_walk)?.asImageBitmap()
    }

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
            CameraMode.EXTERIOR_2D -> {
                drawChase2DPerspectiveWorld(
                    snapshot = snapshot,
                    physics = physics,
                    truck = truck,
                    route = route,
                    visualConfig = visualConfig,
                    cache = drawCache,
                    nightFactor = nightFactor,
                    sunsetFactor = sunsetFactor,
                    stars = stars,
                    rainSeeds = rainSeeds,
                    driverSprite = driverWalkBitmap
                )
            }
            CameraMode.SIDE_2D -> {
                drawSide2DWorld(
                    snapshot = snapshot,
                    physics = physics,
                    truck = truck,
                    route = route,
                    visualConfig = visualConfig,
                    cache = drawCache,
                    nightFactor = nightFactor,
                    sunsetFactor = sunsetFactor,
                    stars = stars,
                    rainSeeds = rainSeeds,
                    closeUpSide = true,
                    driverSprite = driverWalkBitmap
                )
            }
            CameraMode.FRONT_2D -> {
                drawFrontTop2DWorld(
                    snapshot = snapshot,
                    physics = physics,
                    truck = truck,
                    route = route,
                    visualConfig = visualConfig,
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

private data class PerspectiveTrafficDraw(
    val ds: Float,
    val isConvoy: Boolean,
    val traffic: TrafficVehicle? = null,
    val convoy: com.example.model.ConvoyPlayer? = null
)

/**
 * Iconic BUSSID-Caliber 2D Chase Perspective View:
 * Looks down a curving receding multi-lane 2D highway with lush tropical palms / alpine pines,
 * tire-wear asphalt tracks, curve chevrons, toll plazas, fuel stations, zebra crosswalks,
 * depth-sorted 3/4 traffic, and a 3/4 elevated 2D truck+trailer rig in the foreground.
 */
private fun DrawScope.drawChase2DPerspectiveWorld(
    snapshot: RenderSnapshot,
    physics: TruckPhysicsEngine,
    truck: TruckSpec,
    route: RouteSpec,
    visualConfig: TruckVisualConfig,
    cache: ViewportDrawCache,
    nightFactor: Float,
    sunsetFactor: Float,
    stars: List<StarSeed>,
    rainSeeds: List<RainDropSeed>,
    driverSprite: ImageBitmap?
) {
    val w = size.width
    val h = size.height
    val env = route.environment
    val horizonY = h * 0.40f
    val cameraS = if (snapshot.driverOnFoot) snapshot.driverRoadS else snapshot.roadProgressMeters
    val activeLane = if (snapshot.driverOnFoot) snapshot.driverLaneOffset else snapshot.laneOffset

    // 1. Dynamic Atmospheric Sky + Sun/Moon + Stars + Volumetric Horizon Haze
    drawDynamicSky2D(
        w = w,
        horizonY = horizonY + h * 0.04f,
        env = env,
        timeOfDay = snapshot.timeOfDay,
        nightFactor = nightFactor,
        sunsetFactor = sunsetFactor,
        simClock = snapshot.simClock,
        cameraS = cameraS * 0.35f,
        stars = stars
    )

    // Multi-layered distant mountains & subtle volcano/ridge silhouettes
    drawParallaxMountains(w, horizonY, cameraS * 0.30f + activeLane * 4f, env, nightFactor, sunsetFactor, 0.22f, h * 0.14f, 0, cache)
    drawParallaxMountains(w, horizonY + 4f, cameraS * 0.45f + activeLane * 6f, env, nightFactor, sunsetFactor, 0.34f, h * 0.08f, 1, cache)

    // Horizon atmospheric depth glow band
    val hazeColor = lerpColor(
        lerpColor(Color(0xFFA7D3F2), Color(0xFFF5A97F), sunsetFactor),
        Color(0xFF1A2942),
        nightFactor
    ).copy(alpha = 0.38f)
    drawRect(
        brush = Brush.verticalGradient(listOf(Color.Transparent, hazeColor, Color.Transparent), startY = horizonY - h * 0.07f, endY = horizonY + h * 0.07f),
        topLeft = Offset(0f, horizonY - h * 0.07f),
        size = Size(w, h * 0.14f)
    )

    // 2. Terrain Ground Plane (Lush tropical/alpine or desert)
    val groundTop = lerpColor(
        if (env == EnvironmentTheme.DESERT_NIGHT) Color(0xFF6B4C35) else Color(0xFF39612E),
        Color(0xFF0D1411),
        nightFactor * 0.85f
    )
    val groundBot = lerpColor(
        if (env == EnvironmentTheme.DESERT_NIGHT) Color(0xFF422E20) else Color(0xFF213C1B),
        Color(0xFF070A09),
        nightFactor * 0.85f
    )
    drawRect(
        brush = Brush.verticalGradient(listOf(groundTop, groundBot), startY = horizonY, endY = h),
        topLeft = Offset(0f, horizonY),
        size = Size(w, h - horizonY)
    )

    // River water check if on or approaching Bridge
    val nearBridge = physics.landmarks.firstOrNull { it.kind == LandmarkKind.BRIDGE && (it.s - cameraS) in -20f..170f }
    if (nearBridge != null) {
        val ds = (nearBridge.s - cameraS).coerceAtLeast(0f)
        val depth = (1f - ds / 170f).coerceIn(0f, 1f)
        val p = depth * depth
        val ry = horizonY + (h - horizonY) * p
        drawRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF1E4E79), Color(0xFF0F2D4A))),
            topLeft = Offset(0f, ry - 18f - p * 38f),
            size = Size(w, 36f + p * 76f)
        )
    }

    // 3. Curved Perspective 2D Highway Ribbon (sampled smoothly across 18 slices)
    val lookCurvature = physics.road.sampleAtDistance(cameraS + 55f).curvature
    val nearCurvature = physics.road.sampleAtDistance(cameraS + 16f).curvature
    val blendedCurve = lookCurvature * 0.65f + nearCurvature * 0.35f
    val laneShiftNorm = (activeLane / 5.4f).coerceIn(-1.1f, 1.1f)

    val topCenterX = w * 0.5f + blendedCurve * w * 22f - laneShiftNorm * w * 0.08f
    val botCenterX = w * 0.5f - laneShiftNorm * w * 0.24f
    val topHalfW = w * 0.042f
    val botHalfW = w * 0.62f

    fun perspectivePos(p: Float): Pair<Float, Float> {
        val y = horizonY + (h - horizonY) * p
        val curveSwing = blendedCurve * w * 22f * (1f - p) * (1f - p)
        val cx = w * 0.5f + curveSwing - laneShiftNorm * w * (0.08f + p * 0.16f)
        val halfW = topHalfW + (botHalfW - topHalfW) * p
        return cx to halfW
    }

    val slices = 16
    val roadPoly = cache.roadRibbonPath
    roadPoly.reset()
    for (i in 0..slices) {
        val t = i / slices.toFloat()
        val p = t * t
        val y = horizonY + (h - horizonY) * p
        val (cx, hw) = perspectivePos(p)
        if (i == 0) roadPoly.moveTo(cx - hw, y) else roadPoly.lineTo(cx - hw, y)
    }
    for (i in slices downTo 0) {
        val t = i / slices.toFloat()
        val p = t * t
        val y = horizonY + (h - horizonY) * p
        val (cx, hw) = perspectivePos(p)
        roadPoly.lineTo(cx + hw, y)
    }
    roadPoly.close()

    val asphTop = lerpColor(Color(0xFF424A56), Color(0xFF191E26), nightFactor * 0.72f)
    val asphBot = lerpColor(Color(0xFF1F242C), Color(0xFF0C1015), nightFactor * 0.72f)
    drawPath(
        path = roadPoly,
        brush = Brush.verticalGradient(listOf(asphTop, asphBot), startY = horizonY, endY = h)
    )

    // Subtle rain-wet highway sheen reflection
    if (snapshot.rainIntensity > 0.08f) {
        drawPath(
            path = roadPoly,
            brush = Brush.verticalGradient(
                listOf(Color(0xFF93C5FD).copy(alpha = snapshot.rainIntensity * 0.16f), Color.Transparent),
                startY = horizonY,
                endY = h
            )
        )
    }

    // Highway Tire-Wear Wheel Tracks + Shoulder Rumble Curbs + Guardrails
    val dashOffset = ((cameraS * 0.085f) % 1f + 1f) % 1f
    val trackDark = Color(0x2805070A)
    for (i in 0 until slices) {
        val t0 = i / slices.toFloat()
        val t1 = (i + 1) / slices.toFloat()
        val p0 = t0 * t0
        val p1 = t1 * t1
        val y0 = horizonY + (h - horizonY) * p0
        val y1 = horizonY + (h - horizonY) * p1
        val (cx0, hw0) = perspectivePos(p0)
        val (cx1, hw1) = perspectivePos(p1)

        // Subtle asphalt tire wear tracks along left & right carriageways
        for (laneF in floatArrayOf(-0.62f, -0.30f, 0.30f, 0.62f)) {
            drawLine(trackDark, Offset(cx0 + hw0 * laneF, y0), Offset(cx1 + hw1 * laneF, y1), strokeWidth = 2f + p1 * 18f)
        }

        // Edge white markings & outer gravel shoulder rim
        drawLine(Color(0xFF334155), Offset(cx0 - hw0 * 0.99f, y0), Offset(cx1 - hw1 * 0.99f, y1), strokeWidth = 2f + p1 * 8f)
        drawLine(Color(0xFF334155), Offset(cx0 + hw0 * 0.99f, y0), Offset(cx1 + hw1 * 0.99f, y1), strokeWidth = 2f + p1 * 8f)
        drawLine(Color(0xFFE2E8F0), Offset(cx0 - hw0 * 0.90f, y0), Offset(cx1 - hw1 * 0.90f, y1), strokeWidth = 1.5f + p1 * 5.5f)
        drawLine(Color(0xFFE2E8F0), Offset(cx0 + hw0 * 0.90f, y0), Offset(cx1 + hw1 * 0.90f, y1), strokeWidth = 1.5f + p1 * 5.5f)

        // Metallic side W-beam guardrails
        val railCol = lerpColor(Color(0xFF94A3B8), Color(0xFF334155), nightFactor * 0.75f)
        val railH0 = 4f + p0 * 24f
        val railH1 = 4f + p1 * 24f
        drawLine(railCol, Offset(cx0 - hw0 * 1.03f, y0 - railH0), Offset(cx1 - hw1 * 1.03f, y1 - railH1), strokeWidth = 2f + p1 * 5f)
        drawLine(railCol, Offset(cx0 + hw0 * 1.03f, y0 - railH0), Offset(cx1 + hw1 * 1.03f, y1 - railH1), strokeWidth = 2f + p1 * 5f)
    }

    // Moving dashed lane lines + tropical palms / layered pines + curve chevrons + highway lamps (far -> near)
    val usePalms = env == EnvironmentTheme.COASTAL_SUNSET || env == EnvironmentTheme.OPEN_VALLEY || route.isFreeRoam
    val stepMeters = 14f
    val maxLookAhead = 196f
    val firstPost = ((cameraS / stepMeters).toInt() * stepMeters)
    val postCount = (maxLookAhead / stepMeters).toInt()
    for (k in postCount downTo 0) {
        val sObj = firstPost + k * stepMeters
        val ds = sObj - cameraS
        if (ds < 1f || ds > maxLookAhead) continue
        val depth = (1f - ds / maxLookAhead).coerceIn(0f, 1f)
        val p = depth * depth
        val y = horizonY + (h - horizonY) * p
        val (cx, hw) = perspectivePos(p)

        // Guardrail posts
        drawLine(Color(0xFF475569), Offset(cx - hw * 1.03f, y), Offset(cx - hw * 1.03f, y - (5f + p * 26f)), strokeWidth = 1.5f + p * 3f)
        drawLine(Color(0xFF475569), Offset(cx + hw * 1.03f, y), Offset(cx + hw * 1.03f, y - (5f + p * 26f)), strokeWidth = 1.5f + p * 3f)

        val idx = (sObj / stepMeters).toInt()

        // Curve Warning Chevron Boards on outside shoulder of sharper bends
        if (abs(blendedCurve) > 0.0045f && idx % 2 == 0 && p > 0.06f) {
            val outsideSide = if (blendedCurve > 0f) -1f else 1f
            val chevX = cx + outsideSide * (hw * 1.10f)
            val chevH = 12f + p * 38f
            val chevW = 8f + p * 22f
            drawLine(Color(0xFF475569), Offset(chevX, y), Offset(chevX, y - chevH), strokeWidth = 1.5f + p * 2.5f)
            drawRoundRect(
                color = Color(0xFFF59E0B),
                topLeft = Offset(chevX - chevW * 0.5f, y - chevH - chevW * 0.7f),
                size = Size(chevW, chevW * 0.72f),
                cornerRadius = CornerRadius(2f, 2f)
            )
            drawRoundRect(
                color = Color(0xFF111827),
                topLeft = Offset(chevX - chevW * 0.28f, y - chevH - chevW * 0.55f),
                size = Size(chevW * 0.56f, chevW * 0.42f),
                cornerRadius = CornerRadius(1.5f, 1.5f)
            )
        }

        // Authentic Roadside Foliage: Tropical Coconut Palms / Multi-Tiered Alpine Conifers / Desert Saguaro
        for (side in floatArrayOf(-1f, 1f)) {
            if ((idx + (if (side > 0) 1 else 0)) % 2 == 0) {
                val treeX = cx + side * (hw * (1.26f + ((idx * 17) % 5) * 0.14f))
                val treeH = (20f + p * 155f)
                if (env == EnvironmentTheme.DESERT_NIGHT) {
                    val cCol = lerpColor(Color(0xFF2E5C38), Color(0xFF0E1D13), nightFactor * 0.85f)
                    drawLine(cCol, Offset(treeX, y), Offset(treeX, y - treeH * 0.82f), strokeWidth = 2.5f + p * 8f, cap = StrokeCap.Round)
                    drawLine(cCol, Offset(treeX - treeH * 0.18f, y - treeH * 0.52f), Offset(treeX + treeH * 0.18f, y - treeH * 0.52f), strokeWidth = 2f + p * 5f, cap = StrokeCap.Round)
                } else if (usePalms) {
                    // BUSSID-iconic slender arching tropical palm tree
                    val trunkCol = lerpColor(Color(0xFF6D4C3D), Color(0xFF1E1713), nightFactor * 0.82f)
                    val crownX = treeX + side * (treeH * 0.12f)
                    val crownY = y - treeH * 0.92f
                    val trunkPath = Path().apply {
                        moveTo(treeX, y)
                        quadraticTo(treeX + side * (treeH * 0.04f), y - treeH * 0.5f, crownX, crownY)
                    }
                    drawPath(trunkPath, trunkCol, style = Stroke(width = (2.2f + p * 6.5f), cap = StrokeCap.Round))
                    val frondDark = lerpColor(Color(0xFF165B2B), Color(0xFF091B10), nightFactor * 0.85f)
                    val frondBright = lerpColor(Color(0xFF3E8E41), Color(0xFF143419), nightFactor * 0.85f)
                    for (fi in 0..5) {
                        val angleRad = (-160f + fi * 32f + (idx % 3) * 6f) * (PI.toFloat() / 180f)
                        val frondLen = treeH * (0.42f + (fi % 2) * 0.08f)
                        val tipX = crownX + cos(angleRad) * frondLen
                        val tipY = crownY + sin(angleRad) * frondLen * 0.65f + treeH * 0.14f
                        val midX = (crownX + tipX) * 0.5f
                        val midY = crownY + sin(angleRad) * frondLen * 0.35f - treeH * 0.08f
                        val frondPath = Path().apply {
                            moveTo(crownX, crownY)
                            quadraticTo(midX, midY, tipX, tipY)
                        }
                        drawPath(
                            frondPath,
                            if (fi % 2 == 0) frondBright else frondDark,
                            style = Stroke(width = (2.5f + p * 7.5f), cap = StrokeCap.Round)
                        )
                    }
                } else {
                    // Layered 3-tier alpine pine tree
                    val trunkCol = lerpColor(Color(0xFF4A3423), Color(0xFF18110C), nightFactor * 0.82f)
                    drawLine(trunkCol, Offset(treeX, y), Offset(treeX, y - treeH * 0.32f), strokeWidth = 2f + p * 6f)
                    val tierCols = listOf(
                        lerpColor(Color(0xFF144023), Color(0xFF08170D), nightFactor * 0.85f),
                        lerpColor(Color(0xFF1F5C31), Color(0xFF0D2114), nightFactor * 0.85f),
                        lerpColor(Color(0xFF2E7843), Color(0xFF142E1C), nightFactor * 0.85f)
                    )
                    for (tier in 0..2) {
                        val tierBot = y - treeH * (0.24f + tier * 0.21f)
                        val tierTop = y - treeH * (0.58f + tier * 0.21f)
                        val tierHalfW = treeH * (0.34f - tier * 0.07f)
                        val tp = cache.treePath
                        tp.reset()
                        tp.moveTo(treeX, tierTop)
                        tp.lineTo(treeX + tierHalfW, tierBot)
                        tp.lineTo(treeX - tierHalfW, tierBot)
                        tp.close()
                        drawPath(tp, tierCols[tier])
                    }
                }
            }
        }

        // Tall Highway Streetlamps every 3rd segment
        if (idx % 3 == 0) {
            val lampH = 24f + p * 150f
            for (side in floatArrayOf(-1f, 1f)) {
                val px = cx + side * hw * 1.07f
                drawLine(Color(0xFF64748B), Offset(px, y), Offset(px, y - lampH), strokeWidth = 1.6f + p * 3.8f)
                val armEndX = px - side * (5f + p * 18f)
                drawLine(Color(0xFF64748B), Offset(px, y - lampH), Offset(armEndX, y - lampH + 2f), strokeWidth = 1.5f + p * 3f)
                if (nightFactor > 0.22f) {
                    drawCircle(
                        brush = Brush.radialGradient(listOf(Color(0xFFFFE599).copy(alpha = 0.60f * nightFactor), Color.Transparent), center = Offset(armEndX, y - lampH + 3f), radius = 10f + p * 44f),
                        radius = 10f + p * 44f,
                        center = Offset(armEndX, y - lampH + 3f),
                        blendMode = BlendMode.Screen
                    )
                }
                drawCircle(if (nightFactor > 0.22f) Color(0xFFFFF8D6) else Color(0xFFCBD5E1), radius = 1.6f + p * 4.5f, center = Offset(armEndX, y - lampH + 3f))
            }
        }
    }

    // Center & Lane Dashed Strips (smoothly scrolling along highway)
    for (seg in 0..8) {
        val t0 = ((seg + (1f - dashOffset)) / 9f).coerceIn(0f, 1f)
        val t1 = ((seg + 0.52f + (1f - dashOffset)) / 9f).coerceIn(0f, 1f)
        if (t1 <= t0) continue
        val p0 = t0 * t0
        val p1 = t1 * t1
        val y0 = horizonY + (h - horizonY) * p0
        val y1 = horizonY + (h - horizonY) * p1
        val (cx0, hw0) = perspectivePos(p0)
        val (cx1, hw1) = perspectivePos(p1)
        // Center amber divider
        drawLine(Color(0xFFF5B82E), Offset(cx0, y0), Offset(cx1, y1), strokeWidth = 1.8f + p1 * 7.5f)
        // Subtle secondary lane dashes
        drawLine(Color(0x88E2E8F0), Offset(cx0 - hw0 * 0.45f, y0), Offset(cx1 - hw1 * 0.45f, y1), strokeWidth = 1f + p1 * 3.5f)
        drawLine(Color(0x88E2E8F0), Offset(cx0 + hw0 * 0.45f, y0), Offset(cx1 + hw1 * 0.45f, y1), strokeWidth = 1f + p1 * 3.5f)
    }

    // 4. Perspective Landmarks & Traffic Lights (Far -> Near)
    val sortedLandmarks = physics.landmarks
        .filter { (it.s - cameraS) in -8f..200f }
        .sortedByDescending { it.s }
    for (lm in sortedLandmarks) {
        val ds = (lm.s - cameraS).coerceAtLeast(0.5f)
        val depth = (1f - ds / 200f).coerceIn(0f, 1f)
        val p = depth * depth
        val ly = horizonY + (h - horizonY) * p
        val (cx, hw) = perspectivePos(p)

        when (lm.kind) {
            LandmarkKind.CITY_ORIGIN, LandmarkKind.CITY_MIDTOWN, LandmarkKind.CITY_DESTINATION -> {
                for (bi in -2..2) {
                    if (bi == 0) continue
                    val bx = cx + bi * hw * 0.85f
                    val bw = hw * (0.38f + (abs(bi) % 2) * 0.14f)
                    val bh = (24f + (3 - abs(bi)) * 18f) * (0.25f + p * 1.45f)
                    val bCol = lerpColor(Color(0xFF475569), Color(0xFF0F172A), nightFactor * 0.8f)
                    drawRect(bCol, Offset(bx - bw * 0.5f, ly - bh), Size(bw, bh))
                    val winCol = Color(0xFFFFE082).copy(alpha = 0.25f + nightFactor * 0.70f)
                    for (wf in 1..4) {
                        drawRect(winCol, Offset(bx - bw * 0.30f, ly - bh + wf * bh * 0.18f), Size(bw * 0.22f, bh * 0.08f))
                        drawRect(winCol, Offset(bx + bw * 0.08f, ly - bh + wf * bh * 0.18f), Size(bw * 0.22f, bh * 0.08f))
                    }
                }
            }
            LandmarkKind.HIGHWAY_GANTRY -> {
                val postH = 26f + p * 135f
                drawLine(Color(0xFF94A3B8), Offset(cx - hw * 1.02f, ly), Offset(cx - hw * 1.02f, ly - postH), strokeWidth = 2.5f + p * 5f)
                drawLine(Color(0xFF94A3B8), Offset(cx + hw * 1.02f, ly), Offset(cx + hw * 1.02f, ly - postH), strokeWidth = 2.5f + p * 5f)
                val signH = postH * 0.30f
                drawRoundRect(
                    color = Color(0xFF047857),
                    topLeft = Offset(cx - hw * 0.96f, ly - postH),
                    size = Size(hw * 1.92f, signH),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(cx - hw * 0.96f + 2f, ly - postH + 2f),
                    size = Size(hw * 1.92f - 4f, signH - 4f),
                    cornerRadius = CornerRadius(3f, 3f),
                    style = Stroke(width = 1.5f + p * 1.5f)
                )
                if (p > 0.14f) {
                    cache.gantryTitlePaint.textSize = (8f + p * 18f).coerceIn(8f, 24f)
                    drawContext.canvas.nativeCanvas.drawText(
                        "${route.destinationCity.uppercase()} • ${route.displayDistanceKm.toInt()} KM",
                        cx,
                        ly - postH + signH * 0.66f,
                        cache.gantryTitlePaint
                    )
                }
            }
            LandmarkKind.TOLL_BOOTH -> {
                val postH = 24f + p * 125f
                drawLine(Color(0xFF334155), Offset(cx - hw * 1.05f, ly), Offset(cx - hw * 1.05f, ly - postH), strokeWidth = 3f + p * 7f)
                drawLine(Color(0xFF334155), Offset(cx + hw * 1.05f, ly), Offset(cx + hw * 1.05f, ly - postH), strokeWidth = 3f + p * 7f)
                drawRoundRect(
                    color = Color(0xFF0284C7),
                    topLeft = Offset(cx - hw * 1.08f, ly - postH - postH * 0.18f),
                    size = Size(hw * 2.16f, postH * 0.28f),
                    cornerRadius = CornerRadius(5f, 5f)
                )
                // Toll booth islands + lifting barrier arm
                drawRoundRect(Color(0xFF1E293B), Offset(cx - hw * 0.12f, ly - postH * 0.45f), Size(hw * 0.24f, postH * 0.45f), CornerRadius(4f, 4f))
                val armLift = if (ds < 24f) -55f else 0f
                rotate(degrees = armLift, pivot = Offset(cx + hw * 0.12f, ly - postH * 0.22f)) {
                    drawLine(Color(0xFFEF4444), Offset(cx + hw * 0.12f, ly - postH * 0.22f), Offset(cx + hw * 0.90f, ly - postH * 0.22f), strokeWidth = 2.5f + p * 4f, cap = StrokeCap.Round)
                }
                if (p > 0.16f) {
                    cache.gantryTitlePaint.textSize = (8f + p * 16f).coerceIn(8f, 22f)
                    drawContext.canvas.nativeCanvas.drawText("PEAJE / TOLL", cx, ly - postH + postH * 0.03f, cache.gantryTitlePaint)
                }
            }
            LandmarkKind.FUEL_STATION -> {
                val bx = cx + hw * 1.24f
                val cw = hw * 0.92f
                val ch = 22f + p * 114f
                // Tall Fuel Price Pylon Totem
                val totemX = bx - cw * 0.56f
                drawRect(Color(0xFF334155), Offset(totemX - 2f, ly - ch * 1.25f), Size(4f + p * 4f, ch * 1.25f))
                drawRoundRect(Color(0xFF0F172A), Offset(totemX - cw * 0.14f, ly - ch * 1.28f), Size(cw * 0.28f, ch * 0.55f), CornerRadius(3f, 3f))
                drawRect(Color(0xFFF59E0B), Offset(totemX - cw * 0.11f, ly - ch * 1.24f), Size(cw * 0.22f, ch * 0.14f))
                // Illuminated station canopy + pump islands
                drawRect(Color(0xFFCBD5E1), Offset(bx - cw * 0.35f, ly - ch), Size(2f + p * 5f, ch))
                drawRect(Color(0xFFCBD5E1), Offset(bx + cw * 0.35f, ly - ch), Size(2f + p * 5f, ch))
                drawRoundRect(Color(0xFFDC2626), Offset(bx - cw * 0.12f, ly - ch * 0.36f), Size(cw * 0.14f, ch * 0.36f), CornerRadius(2f, 2f))
                drawRoundRect(Color(0xFFDC2626), Offset(bx + cw * 0.08f, ly - ch * 0.36f), Size(cw * 0.14f, ch * 0.36f), CornerRadius(2f, 2f))
                drawRoundRect(Color(0xFF1E293B), Offset(bx - cw * 0.5f, ly - ch - ch * 0.22f), Size(cw, ch * 0.26f), CornerRadius(4f, 4f))
                drawRect(Color(0xFFF59E0B), Offset(bx - cw * 0.5f, ly - ch - ch * 0.08f), Size(cw, ch * 0.10f))
                if (nightFactor > 0.2f || p > 0.25f) {
                    drawCircle(
                        brush = Brush.radialGradient(listOf(Color(0xFFFFF3C4).copy(alpha = 0.28f + 0.35f * nightFactor), Color.Transparent), center = Offset(bx, ly - ch * 0.4f), radius = cw * 0.9f),
                        radius = cw * 0.9f,
                        center = Offset(bx, ly - ch * 0.4f),
                        blendMode = BlendMode.Screen
                    )
                }
            }
            LandmarkKind.WORKSHOP -> {
                val bx = cx + hw * 1.24f
                val ww = hw * 0.92f
                val wh = 22f + p * 108f
                drawRoundRect(Color(0xFF1E293B), Offset(bx - ww * 0.5f, ly - wh), Size(ww, wh), CornerRadius(4f, 4f))
                drawRect(Color(0xFF38BDF8), Offset(bx - ww * 0.5f, ly - wh), Size(ww, wh * 0.22f))
                drawRect(Color(0xFF0A0E14), Offset(bx - ww * 0.32f, ly - wh * 0.65f), Size(ww * 0.64f, wh * 0.65f))
            }
            LandmarkKind.TUNNEL -> {
                val tw = hw * 2.25f
                val th = 32f + p * 165f
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF475569), Color(0xFF1E293B))),
                    topLeft = Offset(cx - tw * 0.58f, ly - th * 1.18f),
                    size = Size(tw * 1.16f, th * 1.18f),
                    cornerRadius = CornerRadius(tw * 0.30f, tw * 0.30f)
                )
                drawRoundRect(
                    color = Color(0xFFF59E0B),
                    topLeft = Offset(cx - tw * 0.48f, ly - th),
                    size = Size(tw * 0.96f, th),
                    cornerRadius = CornerRadius(tw * 0.26f, tw * 0.26f),
                    style = Stroke(width = 3f + p * 5f)
                )
            }
            LandmarkKind.WIND_FARM -> {
                val tx = cx - hw * 1.45f
                val hubY = ly - (30f + p * 145f)
                drawLine(Color(0xFFE2E8F0), Offset(tx, ly), Offset(tx, hubY), strokeWidth = 2f + p * 4f)
                val bLen = 12f + p * 58f
                rotate(degrees = snapshot.simClock * 55f, pivot = Offset(tx, hubY)) {
                    for (b in 0..2) {
                        rotate(degrees = b * 120f, pivot = Offset(tx, hubY)) {
                            drawLine(Color(0xFFF8FAFC), Offset(tx, hubY), Offset(tx, hubY - bLen), strokeWidth = 2f + p * 3f, cap = StrokeCap.Round)
                        }
                    }
                }
            }
            else -> Unit
        }
    }

    // Traffic Lights + Zebra Crosswalks ahead in 2.5D perspective
    for (tl in snapshot.trafficLightsCopy) {
        val ds = tl.s - cameraS
        if (ds in 2f..185f) {
            val depth = (1f - ds / 185f).coerceIn(0f, 1f)
            val p = depth * depth
            val vy = horizonY + (h - horizonY) * p
            val (cx, hw) = perspectivePos(p)

            // Crisp road stop line & zebra crosswalk stripes across highway
            if (p > 0.08f) {
                val stripeH = (2.5f + p * 14f)
                for (zi in -3..3) {
                    val zx = cx + zi * hw * 0.24f
                    drawRect(
                        color = Color(0xCCEEF2F6),
                        topLeft = Offset(zx - hw * 0.08f, vy - stripeH * 0.5f),
                        size = Size(hw * 0.15f, stripeH)
                    )
                }
            }

            val poleX = cx + hw * 1.03f
            val headH = 22f + p * 92f
            drawLine(Color(0xFF64748B), Offset(poleX, vy), Offset(poleX, vy - headH), strokeWidth = 2.5f + p * 4.5f)
            drawRoundRect(Color(0xFF111827), Offset(poleX - (5f + p * 10f), vy - headH - (12f + p * 26f)), Size(10f + p * 20f, 18f + p * 36f), CornerRadius(4f, 4f))
            val lCol = when (tl.state) {
                TrafficLightState.GREEN -> Color(0xFF22C55E)
                TrafficLightState.AMBER -> Color(0xFFF59E0B)
                TrafficLightState.RED -> Color(0xFFEF4444)
            }
            drawCircle(lCol, radius = (3.5f + p * 7.5f), center = Offset(poleX, vy - headH - (3f + p * 8f)))
            if (p > 0.15f) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(lCol.copy(alpha = 0.55f), Color.Transparent), center = Offset(poleX, vy - headH - (3f + p * 8f)), radius = 14f + p * 32f),
                    radius = 14f + p * 32f,
                    center = Offset(poleX, vy - headH - (3f + p * 8f)),
                    blendMode = BlendMode.Screen
                )
            }
        }
    }

    // 5. Depth-Sorted Traffic + Convoy Players ahead of camera (sorted far -> near)
    val items = ArrayList<PerspectiveTrafficDraw>(snapshot.trafficCopy.size + snapshot.remoteConvoyPlayers.size)
    for (v in snapshot.trafficCopy) {
        val ds = v.s - cameraS
        if (ds in 3f..185f) items.add(PerspectiveTrafficDraw(ds = ds, isConvoy = false, traffic = v))
    }
    for (cp in snapshot.remoteConvoyPlayers) {
        val ds = cp.roadS.toFloat() - cameraS
        if (ds in 3f..195f) items.add(PerspectiveTrafficDraw(ds = ds, isConvoy = true, convoy = cp))
    }
    items.sortByDescending { it.ds }

    for (item in items) {
        val maxD = if (item.isConvoy) 195f else 185f
        val depth = (1f - item.ds / maxD).coerceIn(0f, 1f)
        val p = depth * depth
        val vy = horizonY + (h - horizonY) * p
        val (cx, hw) = perspectivePos(p)

        if (item.isConvoy && item.convoy != null) {
            val cp = item.convoy
            val vx = cx + (cp.laneOffset.toFloat() / 5.4f) * hw
            val carW = (0.36f * hw).coerceAtLeast(10f)
            drawTrafficVehicle2DRearFront(
                vehicleType = TrafficVehicleType.TRUCK,
                isOncoming = false,
                bodyColor = Truck2DArtwork.parseHexColor(cp.paintColorHex, Color(0xFF38BDF8)),
                cx = vx,
                bottomY = vy,
                width = carW,
                nightFactor = nightFactor,
                braking = cp.speedKmh < 5L,
                vpX = topCenterX
            )
            if (p > 0.08f) {
                drawConvoyTag2D(cp.driverName, cp.speedKmh.toInt(), cp.hornActive, vx, vy - carW * 1.45f)
            }
        } else if (item.traffic != null) {
            val v = item.traffic
            val vx = cx + (v.laneOffset / 5.4f) * hw
            val carW = (if (v.isTruck) 0.35f else 0.25f) * hw
            drawTrafficVehicle2DRearFront(
                vehicleType = v.type,
                isOncoming = v.isOncoming,
                bodyColor = Color(v.colorRgb[0], v.colorRgb[1], v.colorRgb[2]),
                cx = vx,
                bottomY = vy,
                width = carW.coerceAtLeast(6f),
                nightFactor = nightFactor,
                braking = v.braking,
                vpX = topCenterX
            )
        }
    }

    // 6. Player Customized 2D Truck + Trailer Rig in Lower-Center Chase Foreground
    val rigWidth = (minOf(w, h) * 0.26f).coerceIn(96f, 178f)
    val bouncePx = if (abs(snapshot.speedMs) > 0.6f) {
        sin(snapshot.simClock * 22f) * (abs(snapshot.speedMs) / 28f).coerceIn(0f, 1f) * 3.0f
    } else 0f
    val truckScreenX = if (snapshot.driverOnFoot) {
        val dsTruck = snapshot.roadProgressMeters - cameraS
        val (cxTruck, hwTruck) = perspectivePos((1f - (dsTruck / 100f)).coerceIn(0.3f, 0.92f))
        cxTruck + ((snapshot.laneOffset - activeLane) / 5.4f) * hwTruck
    } else {
        w * 0.5f + snapshot.steerWheelAngleRad * w * 0.025f
    }
    val truckGroundY = h * 0.87f

    drawTruckRig2DRearChase(
        truck = truck,
        visualConfig = visualConfig,
        cx = truckScreenX,
        groundY = truckGroundY,
        rigWidth = rigWidth,
        steerNorm = (snapshot.steerWheelAngleRad / 0.55f).coerceIn(-1f, 1f),
        trailerRelYawRad = (snapshot.trailerYaw - snapshot.tractorYaw),
        bouncePx = bouncePx,
        headlightsOn = snapshot.headlightsOn,
        braking = snapshot.braking,
        reversing = snapshot.reversing,
        leftBlinker = snapshot.leftBlinkerOn,
        rightBlinker = snapshot.rightBlinkerOn,
        nightFactor = nightFactor,
        throttleLevel = snapshot.throttleLevel,
        simClock = snapshot.simClock,
        rainIntensity = snapshot.rainIntensity,
        speedMs = snapshot.speedMs,
        vpX = topCenterX
    )

    // Walking Driver ("Hombre 2D") beside truck when driverOnFoot is active
    if (snapshot.driverOnFoot) {
        drawDriverOnFoot2DChase(
            centerX = w * 0.5f,
            groundY = h * 0.91f,
            heightPx = rigWidth * 0.58f,
            walkCycle = snapshot.driverWalkCycle,
            walkSpeed = snapshot.driverWalkSpeed,
            driverSprite = driverSprite
        )
    }

    // 7. Tunnel Interior Atmospheric Overlay when driving inside tunnel
    for (lm in physics.landmarks) {
        if (lm.kind == LandmarkKind.TUNNEL && abs(lm.s - cameraS) < lm.spanMeters * 0.45f) {
            drawRect(color = Color(0x8804060B), size = size)
            for (li in 0..2) {
                val ly = horizonY + li * (h * 0.16f)
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color(0xFFFFB74D).copy(alpha = 0.42f), Color.Transparent), center = Offset(w * 0.5f, ly), radius = w * 0.24f),
                    radius = w * 0.24f,
                    center = Offset(w * 0.5f, ly),
                    blendMode = BlendMode.Screen
                )
            }
        }
    }

    drawAtmosphericEffects2D(
        w = w,
        h = h,
        roadBotY = h,
        speedMs = snapshot.speedMs,
        nightFactor = nightFactor * 0.72f,
        rainIntensity = snapshot.rainIntensity,
        fogDensity = snapshot.fogDensity,
        simClock = snapshot.simClock,
        rainSeeds = rainSeeds
    )
}

private fun DrawScope.drawSide2DWorld(
    snapshot: RenderSnapshot,
    physics: TruckPhysicsEngine,
    truck: TruckSpec,
    route: RouteSpec,
    visualConfig: TruckVisualConfig,
    cache: ViewportDrawCache,
    nightFactor: Float,
    sunsetFactor: Float,
    stars: List<StarSeed>,
    rainSeeds: List<RainDropSeed>,
    closeUpSide: Boolean,
    driverSprite: ImageBitmap?
) {
    val w = size.width
    val h = size.height
    val env = route.environment

    val baseDiv = if (closeUpSide) 12.2f else 15.8f
    val pxPerMeter = (minOf(w, h) / baseDiv).coerceIn(17f, 38f)
    val cameraS = if (snapshot.driverOnFoot) {
        snapshot.driverRoadS
    } else {
        snapshot.roadProgressMeters + (abs(snapshot.speedMs) * (if (closeUpSide) 0.08f else 0.18f))
    }

    val horizonY = h * 0.50f
    val roadTopY = h * 0.65f
    val roadBotY = h * 0.89f
    val roadHeight = roadBotY - roadTopY

    // 1. Dynamic 2D Day/Night Sky
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

    // 2. Three-Layer Atmospheric Mountains
    drawParallaxMountains(w, roadTopY, cameraS, env, nightFactor, sunsetFactor, 0.05f, h * 0.25f, 0, cache)
    drawParallaxMountains(w, roadTopY, cameraS, env, nightFactor, sunsetFactor, 0.12f, h * 0.18f, 1, cache)
    drawParallaxMountains(w, roadTopY, cameraS, env, nightFactor, sunsetFactor, 0.24f, h * 0.11f, 2, cache)

    // 3. Detailed Biomes & Structures (Volcano, Bridge, Tunnel, Forest, Farms, Industrial, Toll, Workshops, Traffic Lights)
    drawSceneryStructures2D(
        w = w,
        groundBaseY = roadTopY,
        roadBotY = roadBotY,
        cameraS = cameraS,
        pxPerMeter = pxPerMeter,
        physics = physics,
        snapshot = snapshot,
        route = route,
        env = env,
        nightFactor = nightFactor,
        simClock = snapshot.simClock,
        cache = cache
    )

    // 4. HD Textured Multi-Lane 2D Asphalt Road + Bridges + Secondary Road Shoulders + Traffic Lights
    drawSideHighwayRoad2D(
        w = w,
        roadTopY = roadTopY,
        roadBotY = roadBotY,
        cameraS = cameraS,
        pxPerMeter = pxPerMeter,
        physics = physics,
        snapshot = snapshot,
        route = route,
        nightFactor = nightFactor,
        rainIntensity = snapshot.rainIntensity,
        cache = cache
    )

    // 5. Foreground Shoulder below road
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

    // 6. Depth-Sorted 2D Vehicles, Multiplayer Convoy Friends & Walking Driver
    fun laneToGroundY(laneOff: Float): Float {
        val norm = ((laneOff + 4.35f) / 8.70f).coerceIn(0f, 1f)
        return roadTopY + roadHeight * (0.28f + norm * 0.58f)
    }

    val anchorRatio = if (closeUpSide) 0.48f else 0.40f
    val truckAnchorX = w * anchorRatio - (cameraS - snapshot.roadProgressMeters) * pxPerMeter
    val playerGroundY = laneToGroundY(snapshot.laneOffset)
    val visibleWindowMeters = (w / pxPerMeter) * 0.85f

    // Background Traffic
    for (v in snapshot.trafficCopy) {
        val ds = v.s - cameraS
        if (abs(ds) > visibleWindowMeters + 16f) continue
        val vy = laneToGroundY(v.laneOffset)
        if (vy <= playerGroundY) {
            val vx = w * anchorRatio + ds * pxPerMeter
            val depthScale = pxPerMeter * (0.86f + ((vy - roadTopY) / roadHeight) * 0.22f)
            val wheelA = (v.s / 0.4f) % (2f * PI.toFloat())
            drawTrafficVehicle2DSide(
                vehicleType = v.type,
                isOncoming = v.isOncoming,
                bodyColor = Color(v.colorRgb[0], v.colorRgb[1], v.colorRgb[2]),
                centerX = vx,
                groundY = vy,
                scale = depthScale,
                wheelAngleRad = wheelA,
                nightFactor = nightFactor,
                braking = v.braking
            )
        }
    }

    // Background Remote Convoy Players
    for (cp in snapshot.remoteConvoyPlayers) {
        val ds = cp.roadS.toFloat() - cameraS
        if (abs(ds) > visibleWindowMeters + 22f) continue
        val cy = laneToGroundY(cp.laneOffset.toFloat())
        if (cy <= playerGroundY) {
            val cx = w * anchorRatio + ds * pxPerMeter
            val depthScale = pxPerMeter * (0.86f + ((cy - roadTopY) / roadHeight) * 0.22f)
            val rTruck = TruckCatalog.byId(cp.truckId)
            val rCfg = TruckVisualConfig(paintHex = cp.paintColorHex, trailerType = rTruck.defaultTrailerType)
            drawTruckRig2DSide(
                truck = rTruck,
                routeId = route.id,
                visualConfig = rCfg,
                anchorX = cx,
                groundY = cy,
                scale = depthScale,
                wheelAngleRad = ((cp.roadS.toFloat() / 0.52f) % (2f * PI.toFloat())),
                cabPitchRad = 0f,
                bouncePx = 0f,
                headlightsOn = cp.headlightsOn,
                braking = cp.speedKmh < 5L,
                reversing = false,
                blinkerOn = false,
                nightFactor = nightFactor,
                throttleLevel = if (cp.speedKmh > 10L) 0.6f else 0f,
                simClock = snapshot.simClock,
                rainIntensity = snapshot.rainIntensity,
                speedMs = cp.speedKmh / 3.6f
            )
            drawConvoyTag2D(
                driverName = cp.driverName,
                speedKmh = cp.speedKmh.toInt(),
                hornActive = cp.hornActive,
                cx = cx,
                topY = cy - 4.1f * depthScale
            )
        }
    }

    val playerDepthScale = pxPerMeter * (0.88f + ((playerGroundY - roadTopY) / roadHeight) * 0.22f)
    val bouncePx = if (abs(snapshot.speedMs) > 0.6f) {
        sin(snapshot.simClock * 22f) * (abs(snapshot.speedMs) / 28f).coerceIn(0f, 1f) * 2.8f
    } else 0f

    val driverGroundY = laneToGroundY(snapshot.driverLaneOffset)
    val driverScreenX = w * anchorRatio + (snapshot.driverRoadS - cameraS) * pxPerMeter

    if (snapshot.driverOnFoot && driverGroundY <= playerGroundY) {
        drawDriverOnFoot2DSide(
            centerX = driverScreenX,
            groundY = driverGroundY,
            scale = pxPerMeter * 1.15f,
            facingRight = sin(snapshot.driverYaw) >= 0f,
            walkCycle = snapshot.driverWalkCycle,
            walkSpeed = snapshot.driverWalkSpeed,
            driverSprite = driverSprite
        )
    }

    drawTruckRig2DSide(
        truck = truck,
        routeId = route.id,
        visualConfig = visualConfig,
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
            walkSpeed = snapshot.driverWalkSpeed,
            driverSprite = driverSprite
        )
    }

    // Foreground Remote Convoy Players
    for (cp in snapshot.remoteConvoyPlayers) {
        val ds = cp.roadS.toFloat() - cameraS
        if (abs(ds) > visibleWindowMeters + 22f) continue
        val cy = laneToGroundY(cp.laneOffset.toFloat())
        if (cy > playerGroundY) {
            val cx = w * anchorRatio + ds * pxPerMeter
            val depthScale = pxPerMeter * (0.86f + ((cy - roadTopY) / roadHeight) * 0.22f)
            val rTruck = TruckCatalog.byId(cp.truckId)
            val rCfg = TruckVisualConfig(paintHex = cp.paintColorHex, trailerType = rTruck.defaultTrailerType)
            drawTruckRig2DSide(
                truck = rTruck,
                routeId = route.id,
                visualConfig = rCfg,
                anchorX = cx,
                groundY = cy,
                scale = depthScale,
                wheelAngleRad = ((cp.roadS.toFloat() / 0.52f) % (2f * PI.toFloat())),
                cabPitchRad = 0f,
                bouncePx = 0f,
                headlightsOn = cp.headlightsOn,
                braking = cp.speedKmh < 5L,
                reversing = false,
                blinkerOn = false,
                nightFactor = nightFactor,
                throttleLevel = if (cp.speedKmh > 10L) 0.6f else 0f,
                simClock = snapshot.simClock,
                rainIntensity = snapshot.rainIntensity,
                speedMs = cp.speedKmh / 3.6f
            )
            drawConvoyTag2D(
                driverName = cp.driverName,
                speedKmh = cp.speedKmh.toInt(),
                hornActive = cp.hornActive,
                cx = cx,
                topY = cy - 4.1f * depthScale
            )
        }
    }

    // Foreground Traffic
    for (v in snapshot.trafficCopy) {
        val ds = v.s - cameraS
        if (abs(ds) > visibleWindowMeters + 16f) continue
        val vy = laneToGroundY(v.laneOffset)
        if (vy > playerGroundY) {
            val vx = w * anchorRatio + ds * pxPerMeter
            val depthScale = pxPerMeter * (0.86f + ((vy - roadTopY) / roadHeight) * 0.22f)
            val wheelA = (v.s / 0.4f) % (2f * PI.toFloat())
            drawTrafficVehicle2DSide(
                vehicleType = v.type,
                isOncoming = v.isOncoming,
                bodyColor = Color(v.colorRgb[0], v.colorRgb[1], v.colorRgb[2]),
                centerX = vx,
                groundY = vy,
                scale = depthScale,
                wheelAngleRad = wheelA,
                nightFactor = nightFactor,
                braking = v.braking
            )
        }
    }

    // Tunnel interior darkening overlay if player is inside a Tunnel landmark
    for (lm in physics.landmarks) {
        if (lm.kind == LandmarkKind.TUNNEL) {
            val ds = lm.s - cameraS
            if (abs(ds) < lm.spanMeters * 0.7f) {
                val tx = w * anchorRatio + ds * pxPerMeter
                val tw = lm.spanMeters * 1.15f * pxPerMeter
                drawRect(
                    color = Color(0xCC060910),
                    topLeft = Offset(tx - tw * 0.5f, 0f),
                    size = Size(tw, roadTopY)
                )
                for (li in -2..2) {
                    val lampX = tx + li * tw * 0.18f
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFFFB74D).copy(alpha = 0.65f), Color.Transparent),
                            center = Offset(lampX, roadTopY - pxPerMeter * 2.8f),
                            radius = pxPerMeter * 2.2f
                        ),
                        radius = pxPerMeter * 2.2f,
                        center = Offset(lampX, roadTopY - pxPerMeter * 2.8f),
                        blendMode = BlendMode.Screen
                    )
                }
            }
        }
    }

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

private fun DrawScope.drawFrontTop2DWorld(
    snapshot: RenderSnapshot,
    physics: TruckPhysicsEngine,
    truck: TruckSpec,
    route: RouteSpec,
    visualConfig: TruckVisualConfig,
    cache: ViewportDrawCache,
    nightFactor: Float,
    rainSeeds: List<RainDropSeed>
) {
    val w = size.width
    val h = size.height
    val env = route.environment
    val pxPerMeter = (minOf(w, h) / 28f).coerceIn(14f, 24f)

    val terrainCol = when (env) {
        EnvironmentTheme.ALPINE_DAY, EnvironmentTheme.OPEN_VALLEY -> lerpColor(Color(0xFF2D4A29), Color(0xFF0E1711), nightFactor * 0.8f)
        EnvironmentTheme.COASTAL_SUNSET -> lerpColor(Color(0xFF475736), Color(0xFF141812), nightFactor * 0.8f)
        EnvironmentTheme.DESERT_NIGHT -> lerpColor(Color(0xFF5A3E2B), Color(0xFF1A120D), nightFactor * 0.8f)
        EnvironmentTheme.STORM_SUMMIT -> lerpColor(Color(0xFF2A3B31), Color(0xFF0D1411), nightFactor * 0.8f)
    }
    drawRect(color = terrainCol, size = size)

    val centerS = if (snapshot.driverOnFoot) snapshot.driverRoadS else snapshot.roadProgressMeters
    val roadHalfPx = physics.roadHalfWidth * pxPerMeter
    val playerScreenY = h * 0.66f
    val roadCenterX = w * 0.5f

    val stepPx = 18f
    val ySamples = ((h + 120f) / stepPx).toInt() + 2
    val curveFactor = physics.road.sampleAtDistance(centerS).curvature

    fun roadXAtY(sy: Float): Float {
        val dyMeters = (playerScreenY - sy) / pxPerMeter
        return roadCenterX + curveFactor * dyMeters * dyMeters * 18f
    }

    // River water under bridges
    for (lm in physics.landmarks) {
        if (lm.kind == LandmarkKind.BRIDGE) {
            val ds = lm.s - centerS
            val sy = playerScreenY - ds * pxPerMeter
            if (sy in -260f..(h + 260f)) {
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF1E4E79), Color(0xFF0F2D4A))),
                    topLeft = Offset(0f, sy - lm.spanMeters * 0.45f * pxPerMeter),
                    size = Size(w, lm.spanMeters * 0.90f * pxPerMeter)
                )
            }
        }
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

    // Roundabout island rings
    for (lm in physics.landmarks) {
        if (lm.kind == LandmarkKind.ROUNDABOUT) {
            val sy = playerScreenY - (lm.s - centerS) * pxPerMeter
            if (sy in -220f..(h + 220f)) {
                val rx = roadXAtY(sy)
                drawCircle(Color(0xFF252930), radius = roadHalfPx * 1.35f, center = Offset(rx, sy))
                drawCircle(Color(0xFF335E31), radius = roadHalfPx * 0.56f, center = Offset(rx, sy))
                drawCircle(Color(0xFFF5B82E), radius = roadHalfPx * 0.62f, center = Offset(rx, sy), style = Stroke(width = 3f))
            }
        }
    }

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
                    brush = Brush.radialGradient(listOf(Color(0xFF4D8A43), Color(0xFF1F421D)), center = Offset(tx - 3f, sy0 - 3f), radius = 16f),
                    radius = 16f,
                    center = Offset(tx, sy0)
                )
            }
        }
    }

    // Fuel Stations, Workshops, Tolls & Traffic Lights in Front/Top 2D
    for (fs in physics.fuelStationPositions) {
        val sy = playerScreenY - (fs - centerS) * pxPerMeter
        if (sy in -180f..(h + 180f)) {
            val bayX = roadXAtY(sy) + roadHalfPx + 8f
            drawRoundRect(Color(0xFF1E293B), Offset(bayX, sy - 48f), Size(58f, 96f), CornerRadius(8f, 8f))
            drawRoundRect(Color(0xFFF59E0B), Offset(bayX + 6f, sy - 42f), Size(46f, 84f), CornerRadius(6f, 6f), style = Stroke(width = 3f))
        }
    }
    for (ws in physics.workshopPositions) {
        val sy = playerScreenY - (ws - centerS) * pxPerMeter
        if (sy in -180f..(h + 180f)) {
            val bayX = roadXAtY(sy) + roadHalfPx + 8f
            drawRoundRect(Color(0xFF0F172A), Offset(bayX, sy - 44f), Size(58f, 88f), CornerRadius(8f, 8f))
            drawRoundRect(Color(0xFF38BDF8), Offset(bayX + 6f, sy - 38f), Size(46f, 76f), CornerRadius(6f, 6f), style = Stroke(width = 3f))
        }
    }
    for (tl in snapshot.trafficLightsCopy) {
        val sy = playerScreenY - (tl.s - centerS) * pxPerMeter
        if (sy in -100f..(h + 100f)) {
            val rx = roadXAtY(sy)
            val lCol = when (tl.state) {
                TrafficLightState.GREEN -> Color(0xFF22C55E)
                TrafficLightState.AMBER -> Color(0xFFF59E0B)
                TrafficLightState.RED -> Color(0xFFEF4444)
            }
            drawLine(lCol.copy(alpha = 0.65f), Offset(rx, sy), Offset(rx + roadHalfPx, sy), strokeWidth = 5f)
            drawCircle(lCol, radius = 9f, center = Offset(rx + roadHalfPx + 14f, sy))
        }
    }

    for (v in snapshot.trafficCopy) {
        val sy = playerScreenY - (v.s - centerS) * pxPerMeter
        if (sy !in -120f..(h + 120f)) continue
        val rx = roadXAtY(sy)
        val vx = rx + v.laneOffset * pxPerMeter
        val vw = when (v.type) {
            TrafficVehicleType.TRUCK, TrafficVehicleType.BUS -> 2.3f * pxPerMeter
            TrafficVehicleType.MOTORCYCLE -> 0.9f * pxPerMeter
            TrafficVehicleType.SEDAN -> 1.85f * pxPerMeter
        }
        val vl = when (v.type) {
            TrafficVehicleType.BUS -> 8.2f * pxPerMeter
            TrafficVehicleType.TRUCK -> 7.0f * pxPerMeter
            TrafficVehicleType.MOTORCYCLE -> 2.0f * pxPerMeter
            TrafficVehicleType.SEDAN -> 4.2f * pxPerMeter
        }
        val vColor = Color(v.colorRgb[0], v.colorRgb[1], v.colorRgb[2])

        drawRoundRect(Color(0x66000000), Offset(vx - vw * 0.5f + 4f, sy - vl * 0.5f + 4f), Size(vw, vl), CornerRadius(6f, 6f))
        drawRoundRect(vColor, Offset(vx - vw * 0.5f, sy - vl * 0.5f), Size(vw, vl), CornerRadius(6f, 6f))
        val frontY = if (v.isOncoming) sy + vl * 0.5f else sy - vl * 0.5f
        val rearY = if (v.isOncoming) sy - vl * 0.5f else sy + vl * 0.5f
        drawCircle(Color(0xFFFFF4C2), radius = 3.5f, center = Offset(vx - vw * 0.32f, frontY))
        drawCircle(Color(0xFFFFF4C2), radius = 3.5f, center = Offset(vx + vw * 0.32f, frontY))
        drawCircle(if (v.braking) Color(0xFFFF1818) else Color(0xFFB91C1C), radius = 3.2f, center = Offset(vx - vw * 0.32f, rearY))
        drawCircle(if (v.braking) Color(0xFFFF1818) else Color(0xFFB91C1C), radius = 3.2f, center = Offset(vx + vw * 0.32f, rearY))
    }

    // Remote Multiplayer Convoy Players in Top/Front 2D
    for (cp in snapshot.remoteConvoyPlayers) {
        val sy = playerScreenY - (cp.roadS.toFloat() - centerS) * pxPerMeter
        if (sy !in -240f..(h + 240f)) continue
        val rTruck = TruckCatalog.byId(cp.truckId)
        val cx = roadXAtY(sy) + cp.laneOffset.toFloat() * pxPerMeter
        drawTruckRig2DTopDown(
            truck = rTruck,
            visualConfig = TruckVisualConfig(paintHex = cp.paintColorHex, trailerType = rTruck.defaultTrailerType),
            cx = cx,
            cy = sy,
            scale = pxPerMeter,
            headingDeg = 0f,
            trailerRelDeg = 0f,
            headlightsOn = cp.headlightsOn,
            braking = cp.speedKmh < 5L,
            nightFactor = nightFactor
        )
        drawConvoyTag2D(cp.driverName, cp.speedKmh.toInt(), cp.hornActive, cx, sy - 4.0f * pxPerMeter)
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
        visualConfig = visualConfig,
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
        brush = Brush.verticalGradient(listOf(Color(0xFF3A3F47), Color(0xFF20242A)), startY = horizonY, endY = h)
    )

    drawLine(Color(0xFFDDE3EB), Offset(topCenterX - topHalfW * 0.9f, horizonY), Offset(botCenterX - botHalfW * 0.9f, h), strokeWidth = 4f)
    drawLine(Color(0xFFDDE3EB), Offset(topCenterX + topHalfW * 0.9f, horizonY), Offset(botCenterX + botHalfW * 0.9f, h), strokeWidth = 4f)

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

        if (seg % 2 == 0) {
            val halfAtP = topHalfW + (botHalfW - topHalfW) * p0
            val poleH = (18f + p0 * 115f)
            for (side in floatArrayOf(-1f, 1f)) {
                val px = x0 + side * (halfAtP * 1.12f)
                drawLine(Color(0xFF475569), Offset(px, y0), Offset(px, y0 - poleH), strokeWidth = (1.5f + p0 * 3.5f))
                drawCircle(
                    color = if (nightFactor > 0.25f) Color(0xFFFFE599) else Color(0xFFCBD5E1),
                    radius = (1.5f + p0 * 3.5f),
                    center = Offset(px - side * (2f + p0 * 6f), y0 - poleH)
                )
            }
        }
    }

    // Traffic Lights ahead in Cab 2D view
    for (tl in snapshot.trafficLightsCopy) {
        val ds = tl.s - snapshot.roadProgressMeters
        if (ds in 6f..140f) {
            val depth = (1f - (ds / 140f)).coerceIn(0f, 1f)
            val p = depth * depth
            val vy = horizonY + (h * 0.72f - horizonY) * p
            val halfW = topHalfW + (botHalfW - topHalfW) * p
            val cx = topCenterX + (botCenterX - topCenterX) * p
            val poleX = cx + halfW * 1.05f
            val headH = 26f + p * 68f
            drawLine(Color(0xFF64748B), Offset(poleX, vy), Offset(poleX, vy - headH), strokeWidth = 3f + p * 4f)
            val lCol = when (tl.state) {
                TrafficLightState.GREEN -> Color(0xFF22C55E)
                TrafficLightState.AMBER -> Color(0xFFF59E0B)
                TrafficLightState.RED -> Color(0xFFEF4444)
            }
            drawCircle(lCol, radius = (4f + p * 8f), center = Offset(poleX, vy - headH))
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

    // Remote convoy players ahead in Cab 2D horizon
    for (cp in snapshot.remoteConvoyPlayers) {
        val ds = cp.roadS.toFloat() - snapshot.roadProgressMeters
        if (ds in 8f..160f) {
            val depth = (1f - (ds / 160f)).coerceIn(0f, 1f)
            val p = depth * depth
            val vy = horizonY + (h * 0.72f - horizonY) * p
            val roadHalfAtDepth = topHalfW + (botHalfW - topHalfW) * p
            val cxAtDepth = topCenterX + (botCenterX - topCenterX) * p
            val vx = cxAtDepth + (cp.laneOffset.toFloat() / 5.5f) * roadHalfAtDepth
            val carW = 0.42f * roadHalfAtDepth
            drawTrafficVehicle2DRearFront(
                vehicleType = TrafficVehicleType.TRUCK,
                isOncoming = false,
                bodyColor = Truck2DArtwork.parseHexColor(cp.paintColorHex, Color(0xFF38BDF8)),
                cx = vx,
                bottomY = vy,
                width = carW,
                nightFactor = nightFactor,
                braking = cp.speedKmh < 5L,
                vpX = topCenterX
            )
            drawConvoyTag2D(cp.driverName, cp.speedKmh.toInt(), cp.hornActive, vx, vy - carW * 1.25f)
        }
    }

    // Traffic ahead in Cab 2D horizon
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
                vehicleType = v.type,
                isOncoming = v.isOncoming,
                bodyColor = Color(v.colorRgb[0], v.colorRgb[1], v.colorRgb[2]),
                cx = vx,
                bottomY = vy,
                width = carW,
                nightFactor = nightFactor,
                braking = v.braking,
                vpX = topCenterX
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
            brush = Brush.radialGradient(listOf(sunCol.copy(alpha = 0.55f), Color.Transparent), center = Offset(sunX, sunY), radius = w * 0.16f),
            radius = w * 0.16f,
            center = Offset(sunX, sunY)
        )
        drawCircle(color = sunCol, radius = 22f, center = Offset(sunX, sunY))
    } else {
        val moonArc = if (timeOfDay >= 0.80f) (timeOfDay - 0.80f) / 0.42f else (timeOfDay + 0.20f) / 0.42f
        val moonX = w * (0.18f + moonArc.coerceIn(0f, 1f) * 0.64f)
        val moonY = horizonY * (0.68f - sin(moonArc.coerceIn(0f, 1f) * PI.toFloat()) * 0.48f)
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0x38D8E6FF), Color.Transparent), center = Offset(moonX, moonY), radius = 74f),
            radius = 74f,
            center = Offset(moonX, moonY)
        )
        drawCircle(color = Color(0xFFE6EEFF), radius = 17f, center = Offset(moonX, moonY))
    }

    val cloudAlpha = (0.42f * (1f - nightFactor * 0.68f)).coerceIn(0.08f, 0.48f)
    val cloudLit = lerpColor(Color(0xFFFFFFFF), Color(0xFFFFCBA4), sunsetFactor).copy(alpha = cloudAlpha)
    val cloudShade = lerpColor(Color(0xFFC5D5E8), Color(0xFFD9876E), sunsetFactor).copy(alpha = cloudAlpha * 0.78f)
    val drift = (cameraS * 0.04f + simClock * 4.5f)
    for (i in 0..4) {
        val cx = ((i * w * 0.28f - drift) % (w + 280f) + (w + 280f)) % (w + 280f) - 140f
        val cy = horizonY * (0.14f + (i % 3) * 0.13f)
        val sc = 0.85f + (i % 3) * 0.20f
        drawOval(cloudShade, topLeft = Offset(cx + 8f, cy + 10f * sc), size = Size(168f * sc, 28f * sc))
        drawOval(cloudLit, topLeft = Offset(cx, cy), size = Size(120f * sc, 34f * sc))
        drawOval(cloudLit, topLeft = Offset(cx + 34f * sc, cy - 16f * sc), size = Size(92f * sc, 40f * sc))
        drawOval(cloudLit, topLeft = Offset(cx + 84f * sc, cy - 6f * sc), size = Size(78f * sc, 32f * sc))
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
    val drawSnow = layerIndex == 0 && (env == EnvironmentTheme.ALPINE_DAY || env == EnvironmentTheme.STORM_SUMMIT || env == EnvironmentTheme.OPEN_VALLEY)
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
        layerIndex == 0 && (env == EnvironmentTheme.ALPINE_DAY || env == EnvironmentTheme.OPEN_VALLEY) -> Color(0xFF567391)
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
    roadBotY: Float,
    cameraS: Float,
    pxPerMeter: Float,
    physics: TruckPhysicsEngine,
    snapshot: RenderSnapshot,
    route: RouteSpec,
    env: EnvironmentTheme,
    nightFactor: Float,
    simClock: Float,
    cache: ViewportDrawCache
) {
    val spanMeters = (w / pxPerMeter) * 0.85f + 75f
    val minS = ((cameraS - spanMeters) / 14f).toInt() * 14f
    val maxS = cameraS + spanMeters

    val treePath = cache.treePath
    val houseRoofPath = cache.houseRoofPath

    // 1. Recurring Natural Trees & Houses
    var sPos = minS.coerceAtLeast(0f)
    while (sPos <= maxS) {
        val sx = w * 0.40f + (sPos - cameraS) * pxPerMeter
        val idx = (sPos / 14f).toInt()

        val treeH = pxPerMeter * (2.8f + (idx % 3) * 0.68f)
        if (env == EnvironmentTheme.DESERT_NIGHT) {
            val cCol = lerpColor(Color(0xFF2E5C38), Color(0xFF0E1D13), nightFactor * 0.85f)
            drawLine(cCol, Offset(sx, groundBaseY), Offset(sx, groundBaseY - treeH * 0.72f), strokeWidth = 8f, cap = StrokeCap.Round)
            drawLine(cCol, Offset(sx - 12f, groundBaseY - treeH * 0.45f), Offset(sx + 12f, groundBaseY - treeH * 0.45f), strokeWidth = 6f, cap = StrokeCap.Round)
            drawLine(cCol, Offset(sx - 12f, groundBaseY - treeH * 0.45f), Offset(sx - 12f, groundBaseY - treeH * 0.62f), strokeWidth = 5f, cap = StrokeCap.Round)
            drawLine(cCol, Offset(sx + 12f, groundBaseY - treeH * 0.45f), Offset(sx + 12f, groundBaseY - treeH * 0.66f), strokeWidth = 5f, cap = StrokeCap.Round)
        } else {
            val trunkCol = lerpColor(Color(0xFF4B3626), Color(0xFF18120E), nightFactor * 0.8f)
            drawRect(trunkCol, topLeft = Offset(sx - 4f, groundBaseY - treeH * 0.36f), size = Size(8f, treeH * 0.36f))
            val leafDark = lerpColor(Color(0xFF1B4D22), Color(0xFF0A180E), nightFactor * 0.85f)
            val leafMid = lerpColor(Color(0xFF2C6D33), Color(0xFF102414), nightFactor * 0.85f)
            val leafLight = lerpColor(Color(0xFF428D49), Color(0xFF19351E), nightFactor * 0.85f)
            val r = treeH * 0.25f
            drawCircle(leafDark, radius = r * 1.02f, center = Offset(sx - r * 0.44f, groundBaseY - treeH * 0.50f))
            drawCircle(leafDark, radius = r * 1.02f, center = Offset(sx + r * 0.44f, groundBaseY - treeH * 0.50f))
            drawCircle(leafMid, radius = r * 1.15f, center = Offset(sx, groundBaseY - treeH * 0.68f))
            drawCircle(leafLight, radius = r * 0.72f, center = Offset(sx - r * 0.14f, groundBaseY - treeH * 0.78f))
        }

        if (idx % 5 == 1) {
            val hx = sx + pxPerMeter * 5.2f
            val hw = pxPerMeter * 3.2f
            val hh = pxPerMeter * 1.95f
            val wallCol = lerpColor(Color(0xFFE5DFD3), Color(0xFF393E47), nightFactor * 0.82f)
            val roofCol = lerpColor(Color(0xFF8E3B2E), Color(0xFF2B1615), nightFactor * 0.82f)
            val chimX = hx + hw * 0.72f
            drawRect(roofCol, topLeft = Offset(chimX, groundBaseY - hh * 1.55f), size = Size(pxPerMeter * 0.36f, hh * 0.55f))
            val smokeP = (simClock * 0.45f + idx * 0.3f) % 1f
            drawCircle(
                color = Color(0xFFD1D5DB).copy(alpha = (1f - smokeP) * 0.36f),
                radius = (4f + smokeP * 9f),
                center = Offset(chimX + pxPerMeter * 0.18f - smokeP * 14f, groundBaseY - hh * 1.62f - smokeP * 22f)
            )
            drawRect(wallCol, topLeft = Offset(hx, groundBaseY - hh), size = Size(hw, hh))
            houseRoofPath.reset()
            houseRoofPath.moveTo(hx - 7f, groundBaseY - hh)
            houseRoofPath.lineTo(hx + hw * 0.5f, groundBaseY - hh - hh * 0.65f)
            houseRoofPath.lineTo(hx + hw + 7f, groundBaseY - hh)
            houseRoofPath.close()
            drawPath(houseRoofPath, roofCol)
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

    // 2. Specialized BUSSID-Style Landmarks
    for (lm in physics.landmarks) {
        val ds = lm.s - cameraS
        if (abs(ds) > spanMeters + 55f) continue
        val lx = w * 0.40f + ds * pxPerMeter

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
            LandmarkKind.VOLCANO -> {
                val vw = pxPerMeter * 22f
                val vh = pxPerMeter * 11.5f
                val vp = cache.volcanoPath
                vp.reset()
                vp.moveTo(lx - vw * 0.5f, groundBaseY)
                vp.lineTo(lx - vw * 0.12f, groundBaseY - vh)
                vp.lineTo(lx + vw * 0.12f, groundBaseY - vh)
                vp.lineTo(lx + vw * 0.5f, groundBaseY)
                vp.close()
                drawPath(vp, Brush.verticalGradient(listOf(Color(0xFF473735), Color(0xFF1E1A22)), startY = groundBaseY - vh, endY = groundBaseY))
                // Glowing magma crater rim + drifting volcanic plume
                drawOval(
                    color = Color(0xFFFF6D00).copy(alpha = 0.75f + 0.25f * sin(simClock * 3f)),
                    topLeft = Offset(lx - vw * 0.10f, groundBaseY - vh - 6f),
                    size = Size(vw * 0.20f, 12f)
                )
                for (pi in 0..4) {
                    val phase = (simClock * 0.22f + pi * 0.20f) % 1f
                    drawCircle(
                        color = Color(0xFF94A3B8).copy(alpha = (1f - phase) * 0.32f),
                        radius = (12f + phase * 36f),
                        center = Offset(lx - phase * 42f, groundBaseY - vh - phase * 95f)
                    )
                }
            }
            LandmarkKind.BRIDGE -> {
                val bw = lm.spanMeters * 1.1f * pxPerMeter
                val bh = pxPerMeter * 5.2f
                val left = lx - bw * 0.5f
                // Blue shimmering river below bridge
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF1D4ED8), Color(0xFF0F294A))),
                    topLeft = Offset(left - 24f, roadBotY - 4f),
                    size = Size(bw + 48f, size.height - roadBotY + 8f)
                )
                // Steel Arch Bridge truss over the road
                drawArc(
                    color = Color(0xFF94A3B8),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(left, groundBaseY - bh),
                    size = Size(bw, bh * 2f),
                    style = Stroke(width = 7f)
                )
                for (si in 1..7) {
                    val cx = left + (bw / 8f) * si
                    val frac = abs(si - 4) / 4f
                    val topArcY = groundBaseY - bh * (1f - frac * frac * 0.75f)
                    drawLine(Color(0xFFCBD5E1), Offset(cx, groundBaseY), Offset(cx, topArcY), strokeWidth = 3f)
                }
            }
            LandmarkKind.TUNNEL -> {
                val tw = lm.spanMeters * 1.15f * pxPerMeter
                val th = pxPerMeter * 6.5f
                val left = lx - tw * 0.5f
                // Rocky mountain tunnel mass & illuminated portal
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF475569), Color(0xFF1E293B))),
                    topLeft = Offset(left, groundBaseY - th),
                    size = Size(tw, th),
                    cornerRadius = CornerRadius(24f, 24f)
                )
                drawRoundRect(
                    color = Color(0xFF0A0D12),
                    topLeft = Offset(left + 12f, groundBaseY - pxPerMeter * 3.8f),
                    size = Size(tw - 24f, pxPerMeter * 3.8f),
                    cornerRadius = CornerRadius(14f, 14f)
                )
                drawRoundRect(
                    color = Color(0xFFF59E0B),
                    topLeft = Offset(left + 6f, groundBaseY - pxPerMeter * 4.0f),
                    size = Size(10f, pxPerMeter * 4.0f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawRoundRect(
                    color = Color(0xFFF59E0B),
                    topLeft = Offset(left + tw - 16f, groundBaseY - pxPerMeter * 4.0f),
                    size = Size(10f, pxPerMeter * 4.0f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }
            LandmarkKind.FOREST -> {
                for (fi in -4..4) {
                    val fx = lx + fi * pxPerMeter * 2.4f
                    val fh = pxPerMeter * (3.2f + (abs(fi) % 2) * 1.1f)
                    drawRect(Color(0xFF3F2B1D), Offset(fx - 4f, groundBaseY - fh * 0.28f), Size(8f, fh * 0.28f))
                    treePath.reset()
                    treePath.moveTo(fx, groundBaseY - fh)
                    treePath.lineTo(fx + fh * 0.34f, groundBaseY - fh * 0.22f)
                    treePath.lineTo(fx - fh * 0.34f, groundBaseY - fh * 0.22f)
                    treePath.close()
                    drawPath(treePath, lerpColor(Color(0xFF164A27), Color(0xFF0A1D11), nightFactor * 0.8f))
                }
            }
            LandmarkKind.FARM_ZONE -> {
                // Red Barn + Silver Grain Silo + Wooden Fence
                val bx = lx - pxPerMeter * 3f
                val bw = pxPerMeter * 4.5f
                val bh = pxPerMeter * 2.6f
                drawRect(lerpColor(Color(0xFFB91C1C), Color(0xFF450A0A), nightFactor * 0.7f), Offset(bx, groundBaseY - bh), Size(bw, bh))
                drawLine(Color.White, Offset(bx + 4f, groundBaseY - bh + 4f), Offset(bx + bw - 4f, groundBaseY - 4f), strokeWidth = 3f)
                drawLine(Color.White, Offset(bx + bw - 4f, groundBaseY - bh + 4f), Offset(bx + 4f, groundBaseY - 4f), strokeWidth = 3f)
                // Cylindrical Silo
                val sx = bx + bw + pxPerMeter * 0.6f
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(Color(0xFF64748B), Color(0xFFE2E8F0), Color(0xFF475569)), startX = sx, endX = sx + pxPerMeter * 1.8f),
                    topLeft = Offset(sx, groundBaseY - pxPerMeter * 4.2f),
                    size = Size(pxPerMeter * 1.8f, pxPerMeter * 4.2f),
                    cornerRadius = CornerRadius(pxPerMeter * 0.8f, pxPerMeter * 0.8f)
                )
            }
            LandmarkKind.INDUSTRIAL_ZONE -> {
                // Shipping Containers + Tall Steel Gantry Crane
                val craneX = lx
                val craneH = pxPerMeter * 7.2f
                drawRect(Color(0xFFD97706), Offset(craneX - 5f, groundBaseY - craneH), Size(10f, craneH))
                drawRect(Color(0xFFD97706), Offset(craneX - pxPerMeter * 4f, groundBaseY - craneH), Size(pxPerMeter * 8.5f, 10f))
                val cCols = listOf(Color(0xFFDC2626), Color(0xFF0284C7), Color(0xFF15803D))
                for (ci in 0..2) {
                    drawRect(
                        cCols[ci],
                        Offset(lx - pxPerMeter * 4.5f + ci * pxPerMeter * 3.1f, groundBaseY - pxPerMeter * 1.4f),
                        Size(pxPerMeter * 2.9f, pxPerMeter * 1.4f)
                    )
                }
            }
            LandmarkKind.WORKSHOP -> {
                val ww = pxPerMeter * 7.6f
                val wh = pxPerMeter * 3.8f
                val left = lx - ww * 0.5f
                drawRoundRect(Color(0xFF1E293B), Offset(left, groundBaseY - wh), Size(ww, wh), CornerRadius(8f, 8f))
                drawRect(Color(0xFF38BDF8), Offset(left, groundBaseY - wh), Size(ww, pxPerMeter * 0.72f))
                drawRoundRect(Color(0xFF0F172A), Offset(left + pxPerMeter * 1.1f, groundBaseY - pxPerMeter * 2.5f), Size(ww - pxPerMeter * 2.2f, pxPerMeter * 2.5f), CornerRadius(4f, 4f))
                cache.fuelSignPaint.textSize = (pxPerMeter * 0.34f).coerceIn(10f, 18f)
                drawContext.canvas.nativeCanvas.drawText("TALLER 24H 🔧", lx, groundBaseY - wh + pxPerMeter * 0.52f, cache.fuelSignPaint)
            }
            LandmarkKind.TOLL_BOOTH -> {
                val tw = pxPerMeter * 6.2f
                val th = pxPerMeter * 4.2f
                val left = lx - tw * 0.5f
                drawRect(Color(0xFF334155), Offset(left + 8f, groundBaseY - th), Size(12f, th))
                drawRect(Color(0xFF334155), Offset(left + tw - 20f, groundBaseY - th), Size(12f, th))
                drawRoundRect(Color(0xFF0284C7), Offset(left, groundBaseY - th - pxPerMeter * 0.55f), Size(tw, pxPerMeter * 0.85f), CornerRadius(6f, 6f))
                cache.gantryTitlePaint.textSize = (pxPerMeter * 0.34f).coerceIn(10f, 18f)
                drawContext.canvas.nativeCanvas.drawText("PEAJE / TOLL", lx, groundBaseY - th + pxPerMeter * 0.05f, cache.gantryTitlePaint)
                // Lifting barrier arm when player is within 24m
                val barrierLiftDeg = if (abs(ds) < 26f) -68f else 0f
                rotate(degrees = barrierLiftDeg, pivot = Offset(lx - 10f, groundBaseY - pxPerMeter * 1.1f)) {
                    drawLine(
                        color = Color(0xFFEF4444),
                        start = Offset(lx - 10f, groundBaseY - pxPerMeter * 1.1f),
                        end = Offset(lx + pxPerMeter * 2.4f, groundBaseY - pxPerMeter * 1.1f),
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )
                }
            }
            LandmarkKind.PARKING_REST -> {
                val pw = pxPerMeter * 5.2f
                drawRoundRect(Color(0xFF1D4ED8), Offset(lx - pw * 0.5f, groundBaseY - pxPerMeter * 2.8f), Size(pw, pxPerMeter * 1.1f), CornerRadius(6f, 6f))
                drawRect(Color(0xFF94A3B8), Offset(lx - 4f, groundBaseY - pxPerMeter * 1.7f), Size(8f, pxPerMeter * 1.7f))
                cache.gantryTitlePaint.textSize = (pxPerMeter * 0.34f).coerceIn(10f, 18f)
                drawContext.canvas.nativeCanvas.drawText("🅿 DESCANSO", lx, groundBaseY - pxPerMeter * 2.1f, cache.gantryTitlePaint)
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
                drawRect(Color(0xFFCBD5E1), Offset(left + pxPerMeter * 0.7f, groundBaseY - canH), Size(pxPerMeter * 0.36f, canH))
                drawRect(Color(0xFFCBD5E1), Offset(left + canW - pxPerMeter * 1.1f, groundBaseY - canH), Size(pxPerMeter * 0.36f, canH))
                drawRoundRect(Color(0xFF1E293B), Offset(left, groundBaseY - canH - pxPerMeter * 0.62f), Size(canW, pxPerMeter * 0.72f), CornerRadius(6f, 6f))
                drawRect(Color(0xFFF59E0B), Offset(left, groundBaseY - canH - pxPerMeter * 0.24f), Size(canW, pxPerMeter * 0.30f))
                cache.fuelSignPaint.textSize = (pxPerMeter * 0.30f).coerceIn(10f, 18f)
                drawContext.canvas.nativeCanvas.drawText("DIÉSEL 24H", lx, groundBaseY - canH - pxPerMeter * 0.02f, cache.fuelSignPaint)
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
                    drawRoundRect(Color(0xFFDC2626), Offset(px - pxPerMeter * 0.32f, groundBaseY - pxPerMeter * 1.45f), Size(pxPerMeter * 0.64f, pxPerMeter * 1.45f), CornerRadius(4f, 4f))
                }
            }
            LandmarkKind.HIGHWAY_GANTRY -> {
                val gw = pxPerMeter * 5.8f
                val gh = pxPerMeter * 4.3f
                drawRect(Color(0xFF94A3B8), Offset(lx, groundBaseY - gh), Size(8f, gh))
                drawRoundRect(Color(0xFF047857), Offset(lx - gw * 0.5f, groundBaseY - gh), Size(gw, pxPerMeter * 1.40f), CornerRadius(4f, 4f))
                drawRoundRect(Color.White, Offset(lx - gw * 0.5f + 3f, groundBaseY - gh + 3f), Size(gw - 6f, pxPerMeter * 1.40f - 6f), CornerRadius(3f, 3f), style = Stroke(width = 2f))
                cache.gantryTitlePaint.textSize = (pxPerMeter * 0.36f).coerceIn(10f, 18f)
                drawContext.canvas.nativeCanvas.drawText(route.destinationCity.uppercase(), lx, groundBaseY - gh + pxPerMeter * 0.62f, cache.gantryTitlePaint)
                drawContext.canvas.nativeCanvas.drawText("RUTA ${route.displayDistanceKm.toInt()} KM →", lx, groundBaseY - gh + pxPerMeter * 1.10f, cache.gantryTitlePaint)
            }
            LandmarkKind.INTERSECTION, LandmarkKind.ROUNDABOUT, LandmarkKind.SECONDARY_ROAD_ZONE, LandmarkKind.VILLAGE_HOUSES -> Unit
        }
    }

    // 3. Working Roadside Traffic Lights (Green / Amber / Red)
    for (tl in snapshot.trafficLightsCopy) {
        val ds = tl.s - cameraS
        if (abs(ds) > spanMeters + 25f) continue
        val tx = w * 0.40f + ds * pxPerMeter
        val postH = pxPerMeter * 3.8f
        drawRect(Color(0xFF475569), Offset(tx - 4f, groundBaseY - postH), Size(8f, postH))
        drawRoundRect(Color(0xFF111827), Offset(tx - 14f, groundBaseY - postH - 52f), Size(28f, 64f), CornerRadius(6f, 6f))
        val redLit = tl.state == TrafficLightState.RED
        val amberLit = tl.state == TrafficLightState.AMBER
        val greenLit = tl.state == TrafficLightState.GREEN
        drawCircle(if (redLit) Color(0xFFFF2828) else Color(0xFF451010), radius = 7f, center = Offset(tx, groundBaseY - postH - 38f))
        drawCircle(if (amberLit) Color(0xFFFFB020) else Color(0xFF45300A), radius = 7f, center = Offset(tx, groundBaseY - postH - 20f))
        drawCircle(if (greenLit) Color(0xFF22C55E) else Color(0xFF0A381C), radius = 7f, center = Offset(tx, groundBaseY - postH - 2f))
    }
}

private fun DrawScope.drawSideHighwayRoad2D(
    w: Float,
    roadTopY: Float,
    roadBotY: Float,
    cameraS: Float,
    pxPerMeter: Float,
    physics: TruckPhysicsEngine,
    snapshot: RenderSnapshot,
    route: RouteSpec,
    nightFactor: Float,
    rainIntensity: Float,
    cache: ViewportDrawCache
) {
    val roadH = roadBotY - roadTopY
    val asphTop = lerpColor(Color(0xFF3E444D), Color(0xFF1A1E24), nightFactor * 0.65f)
    val asphBot = lerpColor(Color(0xFF282D34), Color(0xFF101318), nightFactor * 0.65f)

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

    drawLine(Color(0xFFE2E8F0), Offset(0f, roadTopY + roadH * 0.08f), Offset(w, roadTopY + roadH * 0.08f), strokeWidth = 4f)
    drawLine(Color(0xFFE2E8F0), Offset(0f, roadBotY - roadH * 0.08f), Offset(w, roadBotY - roadH * 0.08f), strokeWidth = 5f)

    // Crosswalks at intersections
    for (tl in snapshot.trafficLightsCopy) {
        val ds = tl.s - cameraS
        val cx = w * 0.40f + ds * pxPerMeter
        if (cx in -100f..(w + 100f)) {
            for (stripe in 0..5) {
                val sy = roadTopY + roadH * (0.12f + stripe * 0.14f)
                drawRect(Color(0xDDE2E8F0), Offset(cx - 12f, sy), Size(24f, roadH * 0.08f))
            }
        }
    }

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
        val sx = w * 0.40f + (sPos - cameraS) * pxPerMeter

        drawRect(Color(0xFF475569), Offset(sx - 2f, railY), Size(4f, 14f))
        drawRect(Color(0xFFF5B82E), Offset(sx, roadTopY + roadH * 0.43f), Size(pxPerMeter * 4.5f, 4f))
        drawCircle(Color(0xFFFFD54F), radius = 2.6f, center = Offset(sx + pxPerMeter * 5.2f, roadTopY + roadH * 0.435f))
        drawRect(Color(0xAAFFFFFF), Offset(sx + pxPerMeter * 2f, roadTopY + roadH * 0.68f), Size(pxPerMeter * 3.2f, 3f))

        val idx = (sPos / step).toInt()
        if (idx % 3 == 0) {
            val lampH = pxPerMeter * 4.4f
            drawLine(Color(0xFF64748B), Offset(sx, roadTopY - 4f), Offset(sx, roadTopY - lampH), strokeWidth = 4f)
            drawLine(Color(0xFF64748B), Offset(sx, roadTopY - lampH), Offset(sx + 16f, roadTopY - lampH + 4f), strokeWidth = 4f)
            val bulbX = sx + 16f
            val bulbY = roadTopY - lampH + 5f
            if (nightFactor > 0.25f) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color(0xFFFFE599).copy(alpha = 0.55f * nightFactor), Color.Transparent), center = Offset(bulbX, bulbY), radius = pxPerMeter * 2.2f),
                    radius = pxPerMeter * 2.2f,
                    center = Offset(bulbX, bulbY),
                    blendMode = BlendMode.Screen
                )
                drawOval(
                    brush = Brush.radialGradient(listOf(Color(0xFFFFE599).copy(alpha = 0.22f * nightFactor), Color.Transparent), center = Offset(bulbX, roadTopY + roadH * 0.28f), radius = pxPerMeter * 3.4f),
                    topLeft = Offset(bulbX - pxPerMeter * 3.2f, roadTopY),
                    size = Size(pxPerMeter * 6.4f, roadH * 0.56f),
                    blendMode = BlendMode.Screen
                )
            }
            drawCircle(if (nightFactor > 0.25f) Color(0xFFFFF8D6) else Color(0xFFCBD5E1), radius = 4f, center = Offset(bulbX, bulbY))
        }

        if (idx % 6 == 2) {
            val signY = roadTopY - pxPerMeter * 2.2f
            drawLine(Color(0xFF94A3B8), Offset(sx, roadTopY - 4f), Offset(sx, signY), strokeWidth = 3f)
            drawCircle(Color.White, radius = 14f, center = Offset(sx, signY))
            drawCircle(Color(0xFFDC2626), radius = 13f, center = Offset(sx, signY), style = Stroke(width = 3.5f))
            cache.signTextPaint.textSize = 11.5f
            drawContext.canvas.nativeCanvas.drawText(route.speedLimitKmh.toString(), sx, signY + 4f, cache.signTextPaint)
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
            drawLine(Color.White.copy(alpha = streakAlpha), Offset(sx, sy), Offset(sx + len, sy), strokeWidth = 2f, cap = StrokeCap.Round)
        }
    }

    if (fogDensity > 0.04f) {
        val fogCol = lerpColor(Color(0xFFCBD5E1), Color(0xFF334155), nightFactor)
        drawRect(
            brush = Brush.verticalGradient(
                listOf(fogCol.copy(alpha = fogDensity * 0.34f), fogCol.copy(alpha = fogDensity * 0.48f), fogCol.copy(alpha = fogDensity * 0.14f))
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
            drawLine(rainColor, Offset(rx, ry), Offset(rx + windTilt, ry + drop.len), strokeWidth = 1.6f, cap = StrokeCap.Round)
            if (ry > roadBotY - 34f && ry < roadBotY + 8f && i % 4 == 0) {
                drawOval(Color(0x45E2F1FF), Offset(rx - 5f, ry - 1.5f), Size(10f, 3f), style = Stroke(width = 1.2f))
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
