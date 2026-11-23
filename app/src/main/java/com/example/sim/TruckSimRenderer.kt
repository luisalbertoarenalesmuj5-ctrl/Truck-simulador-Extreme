package com.example.sim

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.model.CameraMode
import com.example.model.EnvironmentTheme
import com.example.model.TruckCabStyle
import com.example.model.TruckSpec
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class TruckSimRenderer(
    private val physics: TruckPhysicsEngine,
    private val truck: TruckSpec
) : GLSurfaceView.Renderer {

    @Volatile
    var cameraMode: CameraMode = CameraMode.CAB

    private val snapshot = RenderSnapshot()
    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val baseMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

    private var surfaceAspect = 1.6f
    private var activeFovY = 66f

    private var meshProgram = 0
    private var roadProgram = 0
    private var skyProgram = 0

    // Pre-built static 3D meshes
    private lateinit var cabMesh: GlMesh
    private lateinit var trailerMesh: GlMesh
    private lateinit var wheelMesh: GlMesh
    private lateinit var headlightGlowMesh: GlMesh
    private lateinit var brakeLightMesh: GlMesh
    private lateinit var leftBlinkerMesh: GlMesh
    private lateinit var rightBlinkerMesh: GlMesh
    private lateinit var steeringWheelMesh: GlMesh
    private lateinit var cockpitFrameMesh: GlMesh
    private lateinit var gearStickMesh: GlMesh
    private lateinit var wiperArmMesh: GlMesh
    private lateinit var forearmMesh: GlMesh
    private lateinit var gloveHandMesh: GlMesh

    // Articulated 3D driver ("Hombre") meshes
    private lateinit var driverTorsoMesh: GlMesh
    private lateinit var driverHeadMesh: GlMesh
    private lateinit var driverArmMesh: GlMesh
    private lateinit var driverLegMesh: GlMesh

    // Traffic & World props
    private lateinit var trafficCarMesh: GlMesh
    private lateinit var trafficTruckMesh: GlMesh
    private lateinit var treeMesh: GlMesh
    private lateinit var gantryMesh: GlMesh
    private lateinit var guardrailPostMesh: GlMesh
    private lateinit var lightPoleMesh: GlMesh
    private lateinit var chevronSignMesh: GlMesh
    private lateinit var exhaustPuffMesh: GlMesh

    private lateinit var skyDomeBuffer: FloatBuffer
    private var skyVertexCount = 0

    // Dynamic road ribbon & terrain buffers (reused without heap allocations)
    private lateinit var roadBuffer: FloatBuffer
    private var roadVertexCount = 0
    private lateinit var terrainBuffer: FloatBuffer
    private var terrainVertexCount = 0
    private lateinit var railBuffer: FloatBuffer
    private var railVertexCount = 0

    private val env = physics.route.environment
    private val isNight = env == EnvironmentTheme.DESERT_NIGHT

    private var smoothCamX = 0f
    private var smoothCamY = 4.5f
    private var smoothCamZ = 0f
    private var smoothLookX = 0f
    private var smoothLookY = 2.0f
    private var smoothLookZ = -20f
    private var camInitialized = false
    private var lastOnFootCam = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val skyHorizon = skyHorizonRgb()
        GLES20.glClearColor(skyHorizon[0], skyHorizon[1], skyHorizon[2], 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        meshProgram = createProgram(MESH_VERT, MESH_FRAG)
        roadProgram = createProgram(ROAD_VERT, ROAD_FRAG)
        skyProgram = createProgram(SKY_VERT, SKY_FRAG)

        buildMeshes()
        buildSkyDome()

        // Allocate dynamic buffers for visible road window
        roadBuffer = allocateFloatBuffer(150 * 6 * 8) // x,y,z, nx,ny,nz, u, s
        terrainBuffer = allocateFloatBuffer(150 * 24 * 10)
        railBuffer = allocateFloatBuffer(150 * 12 * 10)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        surfaceAspect = width.toFloat() / height.coerceAtLeast(1)
        activeFovY = if (cameraMode == CameraMode.CAB && !snapshot.driverOnFoot) 66f else 56f
        Matrix.perspectiveM(projMatrix, 0, activeFovY, surfaceAspect, 0.16f, 540f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        physics.readRenderState(snapshot)

        val desiredFov = if (cameraMode == CameraMode.CAB && !snapshot.driverOnFoot) 66f else 56f
        if (abs(desiredFov - activeFovY) > 0.5f) {
            activeFovY = desiredFov
            Matrix.perspectiveM(projMatrix, 0, activeFovY, surfaceAspect, 0.16f, 540f)
        }

        updateCamera()
        rebuildRoadWindow()

        // 1. Draw Sky Dome (depth write off)
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        drawSkyDome()
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_CULL_FACE)

        // 2. Draw Terrain, Mountains & Guardrails
        drawDynamicColorMesh(terrainBuffer, terrainVertexCount)
        drawDynamicColorMesh(railBuffer, railVertexCount)

        // 3. Draw Procedural Asphalt Road with real-time dual headlight cones
        drawRoadRibbon()

        // 4. Draw Roadside Scenery (Trees, Light Poles, Chevrons, Gantries, Posts)
        drawSceneryProps()

        // 5. Draw AI Traffic
        drawTraffic()

        // 6. Draw Player Tractor + Articulated Trailer + Exhaust + Driver
        drawPlayerTruck()

        // 7. Draw Walking Driver Character in 3rd-person On-Foot mode
        if (snapshot.driverOnFoot) {
            drawDriverOnFoot()
        }
    }

    private fun cockpitEyeLocalZ(): Float =
        if (truck.cabStyle == TruckCabStyle.EURO_CABOVER) -1.06f else -0.68f

    private fun updateCamera() {
        if (snapshot.driverOnFoot) {
            val dx = snapshot.driverX
            val dy = snapshot.driverY
            val dz = snapshot.driverZ
            val yaw = snapshot.driverYaw
            val fwdX = sin(yaw)
            val fwdZ = -cos(yaw)
            val rightX = cos(yaw)
            val rightZ = sin(yaw)

            val bob = abs(sin(snapshot.driverWalkCycle)) * 0.05f * snapshot.driverWalkSpeed
            val targetCamX = dx - fwdX * 4.2f + rightX * 0.55f
            val targetCamY = dy + 2.25f + bob * 0.5f
            val targetCamZ = dz - fwdZ * 4.2f + rightZ * 0.55f
            val targetLookX = dx + fwdX * 5.0f
            val targetLookY = dy + 1.38f
            val targetLookZ = dz + fwdZ * 5.0f

            if (!camInitialized || !lastOnFootCam) {
                smoothCamX = targetCamX
                smoothCamY = targetCamY
                smoothCamZ = targetCamZ
                smoothLookX = targetLookX
                smoothLookY = targetLookY
                smoothLookZ = targetLookZ
                camInitialized = true
                lastOnFootCam = true
            } else {
                val alpha = 0.22f
                smoothCamX += (targetCamX - smoothCamX) * alpha
                smoothCamY += (targetCamY - smoothCamY) * alpha
                smoothCamZ += (targetCamZ - smoothCamZ) * alpha
                smoothLookX += (targetLookX - smoothLookX) * 0.28f
                smoothLookY += (targetLookY - smoothLookY) * 0.28f
                smoothLookZ += (targetLookZ - smoothLookZ) * 0.28f
            }

            Matrix.setLookAtM(
                viewMatrix, 0,
                smoothCamX, smoothCamY, smoothCamZ,
                smoothLookX, smoothLookY, smoothLookZ,
                0f, 1f, 0f
            )
            return
        }

        lastOnFootCam = false
        val tx = snapshot.tractorX
        val ty = snapshot.tractorY
        val tz = snapshot.tractorZ
        val yaw = snapshot.tractorYaw
        val fwdX = sin(yaw)
        val fwdZ = -cos(yaw)
        val rightX = cos(yaw)
        val rightZ = sin(yaw)

        val targetCamX: Float
        val targetCamY: Float
        val targetCamZ: Float
        val targetLookX: Float
        val targetLookY: Float
        val targetLookZ: Float

        when (cameraMode) {
            CameraMode.CHASE -> {
                val dist = 14.5f
                targetCamX = tx - fwdX * dist
                targetCamY = ty + 4.8f - sin(snapshot.tractorPitch) * 2.5f
                targetCamZ = tz - fwdZ * dist
                targetLookX = tx + fwdX * 16f
                targetLookY = ty + 1.8f + sin(snapshot.tractorPitch) * 4f
                targetLookZ = tz + fwdZ * 16f
            }
            CameraMode.CAB -> {
                // Realistic driver eye position looking over steering wheel, gauges, GPS & hood
                val eyeLocalZ = cockpitEyeLocalZ()
                val roadVibe = sin(snapshot.simClock * 26f) * 0.005f *
                    (abs(snapshot.speedMs) / 22f).coerceIn(0f, 1f)
                val lookYaw = yaw + snapshot.steerWheelAngleRad * 0.22f
                val lookFwdX = sin(lookYaw)
                val lookFwdZ = -cos(lookYaw)

                targetCamX = tx - rightX * 0.52f - fwdX * eyeLocalZ
                targetCamY = ty + 2.63f + snapshot.cabPitchOffset * 0.75f + roadVibe
                targetCamZ = tz - rightZ * 0.52f - fwdZ * eyeLocalZ
                targetLookX = targetCamX + lookFwdX * 28f
                targetLookY = targetCamY - 3.35f + sin(snapshot.tractorPitch + snapshot.cabPitchOffset) * 24f
                targetLookZ = targetCamZ + lookFwdZ * 28f
            }
            CameraMode.HOOD -> {
                targetCamX = tx + fwdX * 2.4f
                targetCamY = ty + 2.15f
                targetCamZ = tz + fwdZ * 2.4f
                targetLookX = targetCamX + fwdX * 35f
                targetLookY = targetCamY - 0.6f + sin(snapshot.tractorPitch) * 10f
                targetLookZ = targetCamZ + fwdZ * 35f
            }
        }

        if (!camInitialized || cameraMode != CameraMode.CHASE) {
            smoothCamX = targetCamX
            smoothCamY = targetCamY
            smoothCamZ = targetCamZ
            smoothLookX = targetLookX
            smoothLookY = targetLookY
            smoothLookZ = targetLookZ
            camInitialized = true
        } else {
            val alpha = 0.18f
            smoothCamX += (targetCamX - smoothCamX) * alpha
            smoothCamY += (targetCamY - smoothCamY) * alpha
            smoothCamZ += (targetCamZ - smoothCamZ) * alpha
            smoothLookX += (targetLookX - smoothLookX) * 0.25f
            smoothLookY += (targetLookY - smoothLookY) * 0.25f
            smoothLookZ += (targetLookZ - smoothLookZ) * 0.25f
        }

        Matrix.setLookAtM(
            viewMatrix, 0,
            smoothCamX, smoothCamY, smoothCamZ,
            smoothLookX, smoothLookY, smoothLookZ,
            0f, 1f, 0f
        )
    }

    private fun drawSkyDome() {
        GLES20.glUseProgram(skyProgram)
        val posHandle = GLES20.glGetAttribLocation(skyProgram, "aPos")
        val mvpHandle = GLES20.glGetUniformLocation(skyProgram, "uMVP")
        val zenithHandle = GLES20.glGetUniformLocation(skyProgram, "uZenith")
        val horizonHandle = GLES20.glGetUniformLocation(skyProgram, "uHorizon")
        val sunDirHandle = GLES20.glGetUniformLocation(skyProgram, "uSunDir")
        val sunColorHandle = GLES20.glGetUniformLocation(skyProgram, "uSunColor")

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, smoothCamX, smoothCamY - 20f, smoothCamZ)
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, tempMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)

        val zCol = skyZenithRgb()
        val hCol = skyHorizonRgb()
        val sDir = sunDirVec()
        val sCol = sunColorRgb()
        GLES20.glUniform3f(zenithHandle, zCol[0], zCol[1], zCol[2])
        GLES20.glUniform3f(horizonHandle, hCol[0], hCol[1], hCol[2])
        GLES20.glUniform3f(sunDirHandle, sDir[0], sDir[1], sDir[2])
        GLES20.glUniform3f(sunColorHandle, sCol[0], sCol[1], sCol[2])

        skyDomeBuffer.position(0)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 12, skyDomeBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, skyVertexCount)
        GLES20.glDisableVertexAttribArray(posHandle)
    }

    private fun rebuildRoadWindow() {
        val road = physics.road
        val startIdx = (snapshot.roadIndexHint - 8).coerceAtLeast(0)
        val endIdx = (snapshot.roadIndexHint + 95).coerceAtMost(road.points.lastIndex - 1)
        val hw = physics.roadHalfWidth

        roadBuffer.clear()
        terrainBuffer.clear()
        railBuffer.clear()
        roadVertexCount = 0
        terrainVertexCount = 0
        railVertexCount = 0

        val terrainRgb = terrainRgb()
        val mountainRgb = mountainRgb()
        val shoulderRgb = floatArrayOf(0.28f, 0.27f, 0.24f)

        for (i in startIdx until endIdx) {
            val p0 = road.points[i]
            val p1 = road.points[i + 1]
            val r0x = cos(p0.yaw); val r0z = sin(p0.yaw)
            val r1x = cos(p1.yaw); val r1z = sin(p1.yaw)

            // Road quad (two triangles)
            val lx0 = p0.x - r0x * hw; val lz0 = p0.z - r0z * hw
            val rx0 = p0.x + r0x * hw; val rz0 = p0.z + r0z * hw
            val lx1 = p1.x - r1x * hw; val lz1 = p1.z - r1z * hw
            val rx1 = p1.x + r1x * hw; val rz1 = p1.z + r1z * hw

            putRoadVert(lx0, p0.y, lz0, 0f, 1f, 0f, -1f, p0.s)
            putRoadVert(lx1, p1.y, lz1, 0f, 1f, 0f, -1f, p1.s)
            putRoadVert(rx1, p1.y, rz1, 0f, 1f, 0f, 1f, p1.s)

            putRoadVert(lx0, p0.y, lz0, 0f, 1f, 0f, -1f, p0.s)
            putRoadVert(rx1, p1.y, rz1, 0f, 1f, 0f, 1f, p1.s)
            putRoadVert(rx0, p0.y, rz0, 0f, 1f, 0f, 1f, p0.s)

            // Left & right immediate terrain banks
            val leftEdge0x = p0.x - r0x * (hw + 52f); val leftEdge0z = p0.z - r0z * (hw + 52f)
            val leftEdge1x = p1.x - r1x * (hw + 52f); val leftEdge1z = p1.z - r1z * (hw + 52f)
            val ridgeH0L = if (env == EnvironmentTheme.COASTAL_SUNSET) -1.2f else 4.5f + 3.5f * sin(p0.s * 0.03f)
            val ridgeH1L = if (env == EnvironmentTheme.COASTAL_SUNSET) -1.2f else 4.5f + 3.5f * sin(p1.s * 0.03f)
            val leftColor = if (env == EnvironmentTheme.COASTAL_SUNSET) floatArrayOf(0.06f, 0.22f, 0.36f) else terrainRgb

            putColorQuad(
                terrainBuffer,
                leftEdge0x, p0.y + ridgeH0L, leftEdge0z,
                leftEdge1x, p1.y + ridgeH1L, leftEdge1z,
                lx1, p1.y - 0.06f, lz1,
                lx0, p0.y - 0.06f, lz0,
                leftColor[0], leftColor[1], leftColor[2], 0f
            )
            terrainVertexCount += 6

            val rightEdge0x = p0.x + r0x * (hw + 52f); val rightEdge0z = p0.z + r0z * (hw + 52f)
            val rightEdge1x = p1.x + r1x * (hw + 52f); val rightEdge1z = p1.z + r1z * (hw + 52f)
            val ridgeH0R = 4.8f + 4.2f * cos(p0.s * 0.027f)
            val ridgeH1R = 4.8f + 4.2f * cos(p1.s * 0.027f)
            putColorQuad(
                terrainBuffer,
                rx0, p0.y - 0.06f, rz0,
                rx1, p1.y - 0.06f, rz1,
                rightEdge1x, p1.y + ridgeH1R, rightEdge1z,
                rightEdge0x, p0.y + ridgeH0R, rightEdge0z,
                terrainRgb[0] * 0.92f, terrainRgb[1] * 0.92f, terrainRgb[2] * 0.92f, 0f
            )
            terrainVertexCount += 6

            // Distant monumental mountain silhouettes along the flanks
            if (env != EnvironmentTheme.COASTAL_SUNSET) {
                val farL0x = p0.x - r0x * (hw + 145f); val farL0z = p0.z - r0z * (hw + 145f)
                val farL1x = p1.x - r1x * (hw + 145f); val farL1z = p1.z - r1z * (hw + 145f)
                val peakL0 = 18f + 13f * sin(p0.s * 0.017f) + 6f * cos(p0.s * 0.041f)
                val peakL1 = 18f + 13f * sin(p1.s * 0.017f) + 6f * cos(p1.s * 0.041f)
                putColorQuad(
                    terrainBuffer,
                    farL0x, p0.y + peakL0, farL0z,
                    farL1x, p1.y + peakL1, farL1z,
                    leftEdge1x, p1.y + ridgeH1L, leftEdge1z,
                    leftEdge0x, p0.y + ridgeH0L, leftEdge0z,
                    mountainRgb[0], mountainRgb[1], mountainRgb[2], 0f
                )
                terrainVertexCount += 6
            }

            val farR0x = p0.x + r0x * (hw + 145f); val farR0z = p0.z + r0z * (hw + 145f)
            val farR1x = p1.x + r1x * (hw + 145f); val farR1z = p1.z + r1z * (hw + 145f)
            val peakR0 = 20f + 14f * cos(p0.s * 0.015f) + 7f * sin(p0.s * 0.036f)
            val peakR1 = 20f + 14f * cos(p1.s * 0.015f) + 7f * sin(p1.s * 0.036f)
            putColorQuad(
                terrainBuffer,
                rightEdge0x, p0.y + ridgeH0R, rightEdge0z,
                rightEdge1x, p1.y + ridgeH1R, rightEdge1z,
                farR1x, p1.y + peakR1, farR1z,
                farR0x, p0.y + peakR0, farR0z,
                mountainRgb[0] * 0.94f, mountainRgb[1] * 0.94f, mountainRgb[2] * 0.94f, 0f
            )
            terrainVertexCount += 6

            // Shoulder gravel lips on both sides
            val sx0 = p0.x + r0x * (hw + 1.5f); val sz0 = p0.z + r0z * (hw + 1.5f)
            val sx1 = p1.x + r1x * (hw + 1.5f); val sz1 = p1.z + r1z * (hw + 1.5f)
            putColorQuad(
                terrainBuffer,
                rx0, p0.y - 0.03f, rz0,
                rx1, p1.y - 0.03f, rz1,
                sx1, p1.y - 0.05f, sz1,
                sx0, p0.y - 0.05f, sz0,
                shoulderRgb[0], shoulderRgb[1], shoulderRgb[2], 0f
            )
            terrainVertexCount += 6

            val slx0 = p0.x - r0x * (hw + 1.5f); val slz0 = p0.z - r0z * (hw + 1.5f)
            val slx1 = p1.x - r1x * (hw + 1.5f); val slz1 = p1.z - r1z * (hw + 1.5f)
            putColorQuad(
                terrainBuffer,
                slx0, p0.y - 0.05f, slz0,
                slx1, p1.y - 0.05f, slz1,
                lx1, p1.y - 0.03f, lz1,
                lx0, p0.y - 0.03f, lz0,
                shoulderRgb[0], shoulderRgb[1], shoulderRgb[2], 0f
            )
            terrainVertexCount += 6

            // Guardrails (steel beams along edges)
            val railOff = hw + 0.35f
            val rlx0 = p0.x - r0x * railOff; val rlz0 = p0.z - r0z * railOff
            val rlx1 = p1.x - r1x * railOff; val rlz1 = p1.z - r1z * railOff
            putColorQuad(
                railBuffer,
                rlx0, p0.y + 0.45f, rlz0,
                rlx0, p0.y + 0.85f, rlz0,
                rlx1, p1.y + 0.85f, rlz1,
                rlx1, p1.y + 0.45f, rlz1,
                0.70f, 0.74f, 0.78f, 0f
            )
            railVertexCount += 6

            val rrx0 = p0.x + r0x * railOff; val rrz0 = p0.z + r0z * railOff
            val rrx1 = p1.x + r1x * railOff; val rrz1 = p1.z + r1z * railOff
            putColorQuad(
                railBuffer,
                rrx1, p1.y + 0.45f, rrz1,
                rrx1, p1.y + 0.85f, rrz1,
                rrx0, p0.y + 0.85f, rrz0,
                rrx0, p0.y + 0.45f, rrz0,
                0.70f, 0.74f, 0.78f, 0f
            )
            railVertexCount += 6
        }

        roadBuffer.position(0)
        terrainBuffer.position(0)
        railBuffer.position(0)
    }

    private fun drawRoadRibbon() {
        GLES20.glUseProgram(roadProgram)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, viewMatrix, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(roadProgram, "uMVP"), 1, false, mvpMatrix, 0)
        GLES20.glUniform3f(GLES20.glGetUniformLocation(roadProgram, "uCamPos"), smoothCamX, smoothCamY, smoothCamZ)

        val hCol = skyHorizonRgb()
        val sDir = sunDirVec()
        GLES20.glUniform3f(GLES20.glGetUniformLocation(roadProgram, "uFogColor"), hCol[0], hCol[1], hCol[2])
        GLES20.glUniform1f(GLES20.glGetUniformLocation(roadProgram, "uFogDensity"), fogDensity())
        GLES20.glUniform3f(GLES20.glGetUniformLocation(roadProgram, "uSunDir"), sDir[0], sDir[1], sDir[2])
        GLES20.glUniform1f(GLES20.glGetUniformLocation(roadProgram, "uAmbient"), ambientLight())
        GLES20.glUniform1f(GLES20.glGetUniformLocation(roadProgram, "uWetness"), if (env == EnvironmentTheme.STORM_SUMMIT || env == EnvironmentTheme.COASTAL_SUNSET) 0.7f else 0.2f)

        val fwdX = sin(snapshot.tractorYaw)
        val fwdZ = -cos(snapshot.tractorYaw)
        GLES20.glUniform3f(GLES20.glGetUniformLocation(roadProgram, "uTruckPos"), snapshot.tractorX, snapshot.tractorY, snapshot.tractorZ)
        GLES20.glUniform3f(GLES20.glGetUniformLocation(roadProgram, "uTruckFwd"), fwdX, 0f, fwdZ)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(roadProgram, "uHeadlights"), if (snapshot.headlightsOn) 1f else 0f)

        val posH = GLES20.glGetAttribLocation(roadProgram, "aPos")
        val normH = GLES20.glGetAttribLocation(roadProgram, "aNorm")
        val uvH = GLES20.glGetAttribLocation(roadProgram, "aRoadUV")

        roadBuffer.position(0)
        GLES20.glEnableVertexAttribArray(posH)
        GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, 32, roadBuffer)
        roadBuffer.position(3)
        GLES20.glEnableVertexAttribArray(normH)
        GLES20.glVertexAttribPointer(normH, 3, GLES20.GL_FLOAT, false, 32, roadBuffer)
        roadBuffer.position(6)
        GLES20.glEnableVertexAttribArray(uvH)
        GLES20.glVertexAttribPointer(uvH, 2, GLES20.GL_FLOAT, false, 32, roadBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, roadVertexCount)
        GLES20.glDisableVertexAttribArray(posH)
        GLES20.glDisableVertexAttribArray(normH)
        GLES20.glDisableVertexAttribArray(uvH)
    }

    private fun drawDynamicColorMesh(buffer: FloatBuffer, vertexCount: Int) {
        if (vertexCount <= 0) return
        setupMeshShaderCommon()
        Matrix.setIdentityM(modelMatrix, 0)
        drawBufferWithMeshShader(buffer, vertexCount, modelMatrix)
    }

    private fun drawSceneryProps() {
        setupMeshShaderCommon()
        val road = physics.road
        val startIdx = (snapshot.roadIndexHint - 4).coerceAtLeast(0)
        val endIdx = (snapshot.roadIndexHint + 85).coerceAtMost(road.points.lastIndex)

        for (i in startIdx..endIdx) {
            val pt = road.points[i]
            val rightX = cos(pt.yaw)
            val rightZ = sin(pt.yaw)

            // Guardrail posts every 3 segments
            if (i % 3 == 0) {
                val off = physics.roadHalfWidth + 0.38f
                setModelMatrix(pt.x - rightX * off, pt.y, pt.z - rightZ * off, pt.yaw, 0f, 0f)
                drawMesh(guardrailPostMesh, modelMatrix)
                setModelMatrix(pt.x + rightX * off, pt.y, pt.z + rightZ * off, pt.yaw, 0f, 0f)
                drawMesh(guardrailPostMesh, modelMatrix)
            }

            // Highway street lights every 11 segments
            if (i % 11 == 4) {
                val off = physics.roadHalfWidth + 1.15f
                val leftSide = (i / 11) % 2 == 0
                if (leftSide) {
                    setModelMatrix(pt.x - rightX * off, pt.y, pt.z - rightZ * off, pt.yaw, 0f, 0f)
                } else {
                    setModelMatrix(pt.x + rightX * off, pt.y, pt.z + rightZ * off, pt.yaw + PI.toFloat(), 0f, 0f)
                }
                drawMesh(lightPoleMesh, modelMatrix)
            }

            // Curve warning chevron posts on sharper curves
            if (i % 4 == 0 && abs(pt.curvature) > 0.0065f) {
                val outsideSign = if (pt.curvature > 0f) -1f else 1f
                val off = physics.roadHalfWidth + 0.9f
                setModelMatrix(
                    pt.x + rightX * off * outsideSign,
                    pt.y,
                    pt.z + rightZ * off * outsideSign,
                    pt.yaw,
                    0f,
                    0f
                )
                drawMesh(chevronSignMesh, modelMatrix)
            }

            // Trees / Roadside rocks every 5 segments
            if (i % 5 == 0) {
                val distR = physics.roadHalfWidth + 4.8f + ((i * 7) % 6)
                setModelMatrix(pt.x + rightX * distR, pt.y, pt.z + rightZ * distR, pt.yaw, 0f, 0f)
                drawMesh(treeMesh, modelMatrix)

                if (env != EnvironmentTheme.COASTAL_SUNSET) {
                    val distL = physics.roadHalfWidth + 5.2f + ((i * 11) % 7)
                    setModelMatrix(pt.x - rightX * distL, pt.y, pt.z - rightZ * distL, pt.yaw, 0f, 0f)
                    drawMesh(treeMesh, modelMatrix)
                }
            }

            // Overhead Highway Gantries every 35 segments
            if (i % 35 == 15) {
                setModelMatrix(pt.x, pt.y, pt.z, pt.yaw, pt.pitch, 0f)
                drawMesh(gantryMesh, modelMatrix)
            }
        }
    }

    private fun drawTraffic() {
        setupMeshShaderCommon()
        val road = physics.road
        for (v in snapshot.trafficCopy) {
            val dist = abs(v.s - road.points[snapshot.roadIndexHint].s)
            if (dist > 280f) continue
            val pt = road.sampleAtDistance(v.s)
            val rightX = cos(pt.yaw)
            val rightZ = sin(pt.yaw)
            val vx = pt.x + rightX * v.laneOffset
            val vz = pt.z + rightZ * v.laneOffset
            val vyaw = if (v.isOncoming) pt.yaw + Math.PI.toFloat() else pt.yaw
            val vpitch = if (v.isOncoming) -pt.pitch else pt.pitch

            setModelMatrix(vx, pt.y, vz, vyaw, vpitch, 0f)
            drawMesh(if (v.isTruck) trafficTruckMesh else trafficCarMesh, modelMatrix)
        }
    }

    private fun drawPlayerTruck() {
        setupMeshShaderCommon()
        val isInteriorCabView = !snapshot.driverOnFoot && cameraMode == CameraMode.CAB

        // 1. Trailer (always visible outside or in CHASE/HOOD/On-Foot)
        if (!isInteriorCabView) {
            setModelMatrix(
                snapshot.trailerX,
                snapshot.tractorY,
                snapshot.trailerZ,
                snapshot.trailerYaw,
                snapshot.tractorPitch,
                snapshot.cabRollOffset * 0.6f
            )
            drawMesh(trailerMesh, modelMatrix)
            drawTrailerWheels(snapshot.trailerX, snapshot.tractorY, snapshot.trailerZ, snapshot.trailerYaw)
            if (snapshot.braking) {
                drawMesh(brakeLightMesh, modelMatrix)
            }
        }

        // 2. Tractor Body / Realistic Cockpit
        if (isInteriorCabView) {
            drawVistaRealCockpit()
        } else {
            setModelMatrix(
                snapshot.tractorX,
                snapshot.tractorY,
                snapshot.tractorZ,
                snapshot.tractorYaw,
                snapshot.tractorPitch + snapshot.cabPitchOffset,
                snapshot.cabRollOffset
            )
            drawMesh(cabMesh, modelMatrix)
            if (snapshot.headlightsOn) {
                drawMesh(headlightGlowMesh, modelMatrix)
            }
            if (snapshot.leftBlinkerOn) {
                drawMesh(leftBlinkerMesh, modelMatrix)
            }
            if (snapshot.rightBlinkerOn) {
                drawMesh(rightBlinkerMesh, modelMatrix)
            }
            drawTractorWheels()
            drawExhaustSmoke()

            // When seated inside the rig, draw the 3D driver behind the framed windshield
            if (!snapshot.driverOnFoot) {
                drawSeatedDriverInCab()
            }
        }
    }

    private fun drawVistaRealCockpit() {
        setModelMatrix(
            snapshot.tractorX,
            snapshot.tractorY,
            snapshot.tractorZ,
            snapshot.tractorYaw,
            snapshot.tractorPitch + snapshot.cabPitchOffset,
            snapshot.cabRollOffset
        )
        System.arraycopy(modelMatrix, 0, baseMatrix, 0, 16)
        drawMesh(cockpitFrameMesh, baseMatrix)

        // Animated gear shifter lever on center console
        val gearTiltDeg = when {
            snapshot.currentGear < 0 -> -14f
            snapshot.currentGear == 0 -> 0f
            snapshot.currentGear % 2 == 1 -> 12f
            else -> -10f
        }
        System.arraycopy(baseMatrix, 0, modelMatrix, 0, 16)
        val shifterZ = if (truck.cabStyle == TruckCabStyle.EURO_CABOVER) -1.68f else -1.32f
        Matrix.translateM(modelMatrix, 0, -0.06f, 1.98f, shifterZ)
        Matrix.rotateM(modelMatrix, 0, gearTiltDeg, 1f, 0f, 0f)
        drawMesh(gearStickMesh, modelMatrix)

        // Animated windshield wipers (sweeping during Storm Summit, parked otherwise)
        val wiperAngle = if (env == EnvironmentTheme.STORM_SUMMIT) {
            24f + sin(snapshot.simClock * 3.8f) * 22f
        } else {
            5f
        }
        val wiperZ = if (truck.cabStyle == TruckCabStyle.EURO_CABOVER) -2.26f else -1.80f
        for (wx in floatArrayOf(-0.62f, 0.25f)) {
            System.arraycopy(baseMatrix, 0, modelMatrix, 0, 16)
            Matrix.translateM(modelMatrix, 0, wx, 2.34f, wiperZ)
            Matrix.rotateM(modelMatrix, 0, wiperAngle, 0f, 0f, 1f)
            drawMesh(wiperArmMesh, modelMatrix)
        }

        // Rotating 3D Steering Wheel positioned cleanly in driver's lower field of view
        val wheelX = -0.52f
        val wheelY = 2.28f
        val wheelZ = cockpitEyeLocalZ() - 0.76f
        val wheelTiltDeg = -25f
        val wheelSpinRad = -snapshot.steerWheelAngleRad * 4.2f
        val wheelSpinDeg = Math.toDegrees(wheelSpinRad.toDouble()).toFloat()

        System.arraycopy(baseMatrix, 0, modelMatrix, 0, 16)
        Matrix.translateM(modelMatrix, 0, wheelX, wheelY, wheelZ)
        Matrix.rotateM(modelMatrix, 0, wheelTiltDeg, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, wheelSpinDeg, 0f, 0f, 1f)
        drawMesh(steeringWheelMesh, modelMatrix)

        // First-person 3D driver forearms & gloved hands gripping the rotating rim!
        val rimR = 0.20f
        val tiltRad = Math.toRadians(wheelTiltDeg.toDouble()).toFloat()
        val handRotRad = wheelSpinRad.coerceIn(-1.45f, 1.45f)

        val lLocalX = -cos(handRotRad) * rimR
        val lLocalY = -sin(handRotRad) * rimR
        val rLocalX = cos(handRotRad) * rimR
        val rLocalY = sin(handRotRad) * rimR

        // Transform local rim grip (lx, ly, 0.02) by X-tilt into cab space
        fun rimToCab(lx: Float, ly: Float, lz: Float): Triple<Float, Float, Float> {
            val ty = ly * cos(tiltRad) - lz * sin(tiltRad)
            val tz = ly * sin(tiltRad) + lz * cos(tiltRad)
            return Triple(wheelX + lx, wheelY + ty, wheelZ + tz)
        }

        val lGrip = rimToCab(lLocalX, lLocalY, 0.025f)
        val rGrip = rimToCab(rLocalX, rLocalY, 0.025f)
        val lElbow = Triple(-0.78f, 2.16f, cockpitEyeLocalZ() - 0.16f)
        val rElbow = Triple(-0.24f, 2.16f, cockpitEyeLocalZ() - 0.16f)

        drawCabLimb(lElbow.first, lElbow.second, lElbow.third, lGrip.first, lGrip.second, lGrip.third)
        drawCabLimb(rElbow.first, rElbow.second, rElbow.third, rGrip.first, rGrip.second, rGrip.third)

        // Draw gloved hands right at the grip points
        for (grip in arrayOf(lGrip, rGrip)) {
            System.arraycopy(baseMatrix, 0, modelMatrix, 0, 16)
            Matrix.translateM(modelMatrix, 0, grip.first, grip.second, grip.third)
            Matrix.rotateM(modelMatrix, 0, wheelTiltDeg, 1f, 0f, 0f)
            Matrix.rotateM(modelMatrix, 0, wheelSpinDeg * 0.45f, 0f, 0f, 1f)
            drawMesh(gloveHandMesh, modelMatrix)
        }
    }

    private fun drawCabLimb(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float) {
        val dx = x1 - x0
        val dy = y1 - y0
        val dz = z1 - z0
        val len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.05f)
        val yawDeg = Math.toDegrees(atan2(dx.toDouble(), -dz.toDouble())).toFloat()
        val horiz = sqrt(dx * dx + dz * dz)
        val pitchDeg = Math.toDegrees(atan2(dy.toDouble(), horiz.toDouble())).toFloat()

        System.arraycopy(baseMatrix, 0, modelMatrix, 0, 16)
        Matrix.translateM(modelMatrix, 0, x0, y0, z0)
        Matrix.rotateM(modelMatrix, 0, -yawDeg, 0f, 1f, 0f)
        Matrix.rotateM(modelMatrix, 0, pitchDeg, 1f, 0f, 0f)
        Matrix.scaleM(modelMatrix, 0, 1f, 1f, len)
        drawMesh(forearmMesh, modelMatrix)
    }

    private fun drawSeatedDriverInCab() {
        setModelMatrix(
            snapshot.tractorX,
            snapshot.tractorY,
            snapshot.tractorZ,
            snapshot.tractorYaw,
            snapshot.tractorPitch + snapshot.cabPitchOffset,
            snapshot.cabRollOffset
        )
        System.arraycopy(modelMatrix, 0, baseMatrix, 0, 16)

        val seatZ = if (truck.cabStyle == TruckCabStyle.EURO_CABOVER) -1.25f else -0.58f
        val seatY = 1.98f
        val steerTurnDeg = -Math.toDegrees(snapshot.steerWheelAngleRad.toDouble()).toFloat() * 0.5f

        // Torso
        System.arraycopy(baseMatrix, 0, modelMatrix, 0, 16)
        Matrix.translateM(modelMatrix, 0, -0.52f, seatY, seatZ)
        drawMesh(driverTorsoMesh, modelMatrix)

        // Head looking slightly into steering turn
        System.arraycopy(baseMatrix, 0, modelMatrix, 0, 16)
        Matrix.translateM(modelMatrix, 0, -0.52f, seatY + 0.56f, seatZ)
        Matrix.rotateM(modelMatrix, 0, steerTurnDeg, 0f, 1f, 0f)
        drawMesh(driverHeadMesh, modelMatrix)

        // Arms reaching forward toward wheel
        for (sideX in floatArrayOf(-0.27f, 0.27f)) {
            System.arraycopy(baseMatrix, 0, modelMatrix, 0, 16)
            Matrix.translateM(modelMatrix, 0, -0.52f + sideX, seatY + 0.48f, seatZ)
            Matrix.rotateM(modelMatrix, 0, 62f, 1f, 0f, 0f)
            drawMesh(driverArmMesh, modelMatrix)
        }
    }

    private fun drawDriverOnFoot() {
        setupMeshShaderCommon()
        val walkAmp = snapshot.driverWalkSpeed
        val phase = snapshot.driverWalkCycle
        val legSwingDeg = sin(phase) * 34f * walkAmp
        val armSwingDeg = -sin(phase) * 28f * walkAmp
        val strideBob = abs(sin(phase)) * 0.055f * walkAmp
        val idleBreath = sin(snapshot.simClock * 2.4f) * 0.012f * (1f - walkAmp)
        val torsoTwistDeg = sin(phase) * 5f * walkAmp

        setModelMatrix(
            snapshot.driverX,
            snapshot.driverY + strideBob + idleBreath,
            snapshot.driverZ,
            snapshot.driverYaw,
            0f,
            0f
        )
        System.arraycopy(modelMatrix, 0, baseMatrix, 0, 16)

        // 1. Torso (hips at y = 0.78f, shoulders at y = 1.32f)
        System.arraycopy(baseMatrix, 0, modelMatrix, 0, 16)
        Matrix.translateM(modelMatrix, 0, 0f, 0.78f, 0f)
        Matrix.rotateM(modelMatrix, 0, torsoTwistDeg, 0f, 1f, 0f)
        drawMesh(driverTorsoMesh, modelMatrix)

        // 2. Head + Trucker Cap + Aviators
        System.arraycopy(baseMatrix, 0, modelMatrix, 0, 16)
        Matrix.translateM(modelMatrix, 0, 0f, 1.36f, 0f)
        Matrix.rotateM(modelMatrix, 0, -torsoTwistDeg * 0.5f, 0f, 1f, 0f)
        drawMesh(driverHeadMesh, modelMatrix)

        // 3. Articulated Left & Right Legs (swings opposite)
        System.arraycopy(baseMatrix, 0, modelMatrix, 0, 16)
        Matrix.translateM(modelMatrix, 0, -0.12f, 0.78f, 0f)
        Matrix.rotateM(modelMatrix, 0, legSwingDeg, 1f, 0f, 0f)
        drawMesh(driverLegMesh, modelMatrix)

        System.arraycopy(baseMatrix, 0, modelMatrix, 0, 16)
        Matrix.translateM(modelMatrix, 0, 0.12f, 0.78f, 0f)
        Matrix.rotateM(modelMatrix, 0, -legSwingDeg, 1f, 0f, 0f)
        drawMesh(driverLegMesh, modelMatrix)

        // 4. Articulated Left & Right Arms (natural counter-swing)
        System.arraycopy(baseMatrix, 0, modelMatrix, 0, 16)
        Matrix.translateM(modelMatrix, 0, -0.29f, 1.30f, 0f)
        Matrix.rotateM(modelMatrix, 0, -armSwingDeg, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, -5f, 0f, 0f, 1f)
        drawMesh(driverArmMesh, modelMatrix)

        System.arraycopy(baseMatrix, 0, modelMatrix, 0, 16)
        Matrix.translateM(modelMatrix, 0, 0.29f, 1.30f, 0f)
        Matrix.rotateM(modelMatrix, 0, armSwingDeg, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, 5f, 0f, 0f, 1f)
        drawMesh(driverArmMesh, modelMatrix)
    }

    private fun drawExhaustSmoke() {
        val smokeActivity = (0.22f + snapshot.throttleLevel * 0.78f).coerceIn(0.2f, 1f)
        setModelMatrix(
            snapshot.tractorX,
            snapshot.tractorY,
            snapshot.tractorZ,
            snapshot.tractorYaw,
            snapshot.tractorPitch,
            0f
        )
        System.arraycopy(modelMatrix, 0, baseMatrix, 0, 16)

        val stacks = if (truck.cabStyle == TruckCabStyle.AMERICAN_CONVENTIONAL) {
            arrayOf(floatArrayOf(-1.12f, 3.76f, 0.35f), floatArrayOf(1.12f, 3.76f, 0.35f))
        } else {
            arrayOf(floatArrayOf(-0.98f, 3.35f, -0.35f))
        }

        val puffSpeed = 1.6f + snapshot.throttleLevel * 2.2f
        for (st in stacks) {
            for (k in 0 until 3) {
                val life = ((snapshot.simClock * puffSpeed + k * 0.33f) % 1f)
                val rise = life * (0.9f + smokeActivity * 0.9f)
                val driftBack = life * (0.35f + abs(snapshot.speedMs) * 0.08f)
                val scale = (0.35f + life * 0.95f) * (0.55f + smokeActivity * 0.6f)

                System.arraycopy(baseMatrix, 0, modelMatrix, 0, 16)
                Matrix.translateM(modelMatrix, 0, st[0], st[1] + rise, st[2] + driftBack)
                Matrix.scaleM(modelMatrix, 0, scale, scale * 0.85f, scale)
                drawMesh(exhaustPuffMesh, modelMatrix)
            }
        }
    }

    private fun drawTractorWheels() {
        val steerDeg = -Math.toDegrees(snapshot.steerWheelAngleRad.toDouble()).toFloat()
        val spinDeg = -Math.toDegrees(snapshot.wheelSpinAngle.toDouble()).toFloat()
        val wheelPositions = arrayOf(
            floatArrayOf(-1.12f, 0.52f, -1.75f, 1f), // Front Left (steerable)
            floatArrayOf(1.12f, 0.52f, -1.75f, 1f),  // Front Right (steerable)
            floatArrayOf(-1.12f, 0.52f, 0.65f, 0f),  // Drive Axle 1 L
            floatArrayOf(1.12f, 0.52f, 0.65f, 0f),   // Drive Axle 1 R
            floatArrayOf(-1.12f, 0.52f, 1.95f, 0f),  // Drive Axle 2 L
            floatArrayOf(1.12f, 0.52f, 1.95f, 0f)    // Drive Axle 2 R
        )
        for (wp in wheelPositions) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, snapshot.tractorX, snapshot.tractorY, snapshot.tractorZ)
            Matrix.rotateM(modelMatrix, 0, -Math.toDegrees(snapshot.tractorYaw.toDouble()).toFloat(), 0f, 1f, 0f)
            Matrix.rotateM(modelMatrix, 0, Math.toDegrees(snapshot.tractorPitch.toDouble()).toFloat(), 1f, 0f, 0f)
            Matrix.translateM(modelMatrix, 0, wp[0], wp[1], wp[2])
            if (wp[3] > 0.5f) {
                Matrix.rotateM(modelMatrix, 0, steerDeg, 0f, 1f, 0f)
            }
            Matrix.rotateM(modelMatrix, 0, spinDeg, 1f, 0f, 0f)
            drawMesh(wheelMesh, modelMatrix)
        }
    }

    private fun drawTrailerWheels(tx: Float, ty: Float, tz: Float, yaw: Float) {
        val spinDeg = -Math.toDegrees(snapshot.wheelSpinAngle.toDouble()).toFloat()
        val offsets = arrayOf(
            floatArrayOf(-1.12f, 0.52f, 2.2f),
            floatArrayOf(1.12f, 0.52f, 2.2f),
            floatArrayOf(-1.12f, 0.52f, 3.4f),
            floatArrayOf(1.12f, 0.52f, 3.4f)
        )
        for (op in offsets) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, tx, ty, tz)
            Matrix.rotateM(modelMatrix, 0, -Math.toDegrees(yaw.toDouble()).toFloat(), 0f, 1f, 0f)
            Matrix.rotateM(modelMatrix, 0, Math.toDegrees(snapshot.tractorPitch.toDouble()).toFloat(), 1f, 0f, 0f)
            Matrix.translateM(modelMatrix, 0, op[0], op[1], op[2])
            Matrix.rotateM(modelMatrix, 0, spinDeg, 1f, 0f, 0f)
            drawMesh(wheelMesh, modelMatrix)
        }
    }

    private fun setModelMatrix(x: Float, y: Float, z: Float, yaw: Float, pitch: Float, roll: Float) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.rotateM(modelMatrix, 0, -Math.toDegrees(yaw.toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.rotateM(modelMatrix, 0, Math.toDegrees(pitch.toDouble()).toFloat(), 1f, 0f, 0f)
        if (roll != 0f) {
            Matrix.rotateM(modelMatrix, 0, Math.toDegrees(roll.toDouble()).toFloat(), 0f, 0f, 1f)
        }
    }

    private fun setupMeshShaderCommon() {
        GLES20.glUseProgram(meshProgram)
        val hCol = skyHorizonRgb()
        val sDir = sunDirVec()
        GLES20.glUniform3f(GLES20.glGetUniformLocation(meshProgram, "uCamPos"), smoothCamX, smoothCamY, smoothCamZ)
        GLES20.glUniform3f(GLES20.glGetUniformLocation(meshProgram, "uFogColor"), hCol[0], hCol[1], hCol[2])
        GLES20.glUniform1f(GLES20.glGetUniformLocation(meshProgram, "uFogDensity"), fogDensity())
        GLES20.glUniform3f(GLES20.glGetUniformLocation(meshProgram, "uSunDir"), sDir[0], sDir[1], sDir[2])
        GLES20.glUniform1f(GLES20.glGetUniformLocation(meshProgram, "uAmbient"), ambientLight())
    }

    private fun drawMesh(mesh: GlMesh, model: FloatArray) {
        drawBufferWithMeshShader(mesh.buffer, mesh.vertexCount, model)
    }

    private fun drawBufferWithMeshShader(buffer: FloatBuffer, vertexCount: Int, model: FloatArray) {
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, model, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, tempMatrix, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(meshProgram, "uMVP"), 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(meshProgram, "uModel"), 1, false, model, 0)

        val posH = GLES20.glGetAttribLocation(meshProgram, "aPos")
        val normH = GLES20.glGetAttribLocation(meshProgram, "aNorm")
        val colH = GLES20.glGetAttribLocation(meshProgram, "aColor")
        val emH = GLES20.glGetAttribLocation(meshProgram, "aEmissive")

        buffer.position(0)
        GLES20.glEnableVertexAttribArray(posH)
        GLES20.glVertexAttribPointer(posH, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, buffer)
        buffer.position(3)
        GLES20.glEnableVertexAttribArray(normH)
        GLES20.glVertexAttribPointer(normH, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, buffer)
        buffer.position(6)
        GLES20.glEnableVertexAttribArray(colH)
        GLES20.glVertexAttribPointer(colH, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, buffer)
        buffer.position(9)
        GLES20.glEnableVertexAttribArray(emH)
        GLES20.glVertexAttribPointer(emH, 1, GLES20.GL_FLOAT, false, STRIDE_BYTES, buffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)

        GLES20.glDisableVertexAttribArray(posH)
        GLES20.glDisableVertexAttribArray(normH)
        GLES20.glDisableVertexAttribArray(colH)
        GLES20.glDisableVertexAttribArray(emH)
    }

    private fun buildMeshes() {
        val p = truck.primaryColorRgb
        val a = truck.accentColorRgb

        // 1. Player Tractor Cab with open framed windshield so seated 3D driver is visible from outside!
        cabMesh = MeshBuilder().apply {
            // Heavy steel chassis frame rails
            addBox(0f, 0.72f, 0.1f, 1.05f, 0.28f, 5.1f, 0.13f, 0.15f, 0.18f)
            // Chrome fuel tanks & entry steps on left & right
            addBox(-0.88f, 0.72f, 0.2f, 0.52f, 0.52f, 1.6f, 0.75f, 0.78f, 0.82f)
            addBox(0.88f, 0.72f, 0.2f, 0.52f, 0.52f, 1.6f, 0.75f, 0.78f, 0.82f)
            addBox(-1.14f, 0.96f, -1.10f, 0.16f, 0.08f, 0.52f, 0.72f, 0.75f, 0.80f)
            addBox(1.14f, 0.96f, -1.10f, 0.16f, 0.08f, 0.52f, 0.72f, 0.75f, 0.80f)
            // Fifth wheel hitch plate
            addBox(0f, 0.95f, 1.15f, 1.1f, 0.14f, 0.95f, 0.2f, 0.22f, 0.25f)
            // Front chrome bumper
            addBox(0f, 0.58f, -2.52f, 2.34f, 0.34f, 0.22f, 0.80f, 0.83f, 0.87f)

            if (truck.cabStyle == TruckCabStyle.EURO_CABOVER) {
                // Lower front cab body below windshield
                addBox(0f, 1.56f, -1.45f, 2.34f, 1.10f, 2.05f, p[0], p[1], p[2])
                // Rear sleeper wall & side columns
                addBox(0f, 2.58f, -0.58f, 2.34f, 1.02f, 0.32f, p[0], p[1], p[2])
                addBox(-1.11f, 2.58f, -1.48f, 0.12f, 1.02f, 1.70f, p[0], p[1], p[2])
                addBox(1.11f, 2.58f, -1.48f, 0.12f, 1.02f, 1.70f, p[0], p[1], p[2])
                // Cab roof + aero roof fairing + amber clearance lights
                addBox(0f, 3.16f, -1.45f, 2.34f, 0.22f, 2.05f, p[0], p[1], p[2])
                addBox(0f, 3.50f, -1.25f, 2.18f, 0.45f, 1.65f, p[0] * 0.95f, p[1] * 0.95f, p[2] * 0.95f)
                addBox(-0.65f, 3.22f, -2.49f, 0.14f, 0.08f, 0.06f, 1.0f, 0.58f, 0.05f, 0.9f)
                addBox(0.65f, 3.22f, -2.49f, 0.14f, 0.08f, 0.06f, 1.0f, 0.58f, 0.05f, 0.9f)
                // Interior seats & dash visible through windshield
                addBox(0f, 2.04f, -2.18f, 2.06f, 0.24f, 0.45f, 0.14f, 0.16f, 0.19f)
                addBox(-0.52f, 2.18f, -1.05f, 0.46f, 0.65f, 0.16f, 0.18f, 0.20f, 0.24f)
                addBox(0.52f, 2.18f, -1.05f, 0.46f, 0.65f, 0.16f, 0.18f, 0.20f, 0.24f)
                // Front grille & accent stripe
                addBox(0f, 1.45f, -2.49f, 1.65f, 0.85f, 0.06f, 0.16f, 0.18f, 0.22f)
                addBox(0f, 2.02f, -2.49f, 2.1f, 0.12f, 0.07f, a[0], a[1], a[2])
                // Exhaust stack behind cab
                addCylinderY(-0.98f, 1.15f, -0.35f, 0.08f, 0.08f, 2.20f, 8, 0.78f, 0.80f, 0.84f)
                // Side mirrors
                addBox(-1.34f, 2.45f, -2.25f, 0.14f, 0.48f, 0.18f, 0.15f, 0.16f, 0.18f)
                addBox(1.34f, 2.45f, -2.25f, 0.14f, 0.48f, 0.18f, 0.15f, 0.16f, 0.18f)
            } else {
                // American Conventional Long-Nose Hood + Sleeper Cab with open windshield frame
                addBox(0f, 1.48f, -1.85f, 1.85f, 1.10f, 1.75f, p[0], p[1], p[2])
                addBox(0f, 1.45f, -2.74f, 1.45f, 1.02f, 0.08f, 0.86f, 0.89f, 0.92f)
                // Lower cab base + rear sleeper + A-pillars + roof
                addBox(0f, 1.62f, -0.35f, 2.30f, 0.96f, 1.85f, p[0], p[1], p[2])
                addBox(0f, 2.55f, 0.22f, 2.30f, 0.96f, 0.70f, p[0], p[1], p[2])
                addBox(-1.09f, 2.55f, -0.68f, 0.12f, 0.96f, 1.12f, p[0], p[1], p[2])
                addBox(1.09f, 2.55f, -0.68f, 0.12f, 0.96f, 1.12f, p[0], p[1], p[2])
                addBox(0f, 3.12f, -0.35f, 2.30f, 0.24f, 1.85f, p[0], p[1], p[2])
                addBox(-0.52f, 2.15f, -0.38f, 0.46f, 0.65f, 0.16f, 0.18f, 0.20f, 0.24f)
                // Accent stripe along hood & sleeper
                addBox(-1.16f, 1.95f, -0.35f, 0.04f, 0.18f, 1.70f, a[0], a[1], a[2])
                addBox(1.16f, 1.95f, -0.35f, 0.04f, 0.18f, 1.70f, a[0], a[1], a[2])
                // Twin vertical chrome exhaust stacks
                addCylinderY(-1.12f, 1.1f, 0.35f, 0.09f, 0.09f, 2.65f, 8, 0.86f, 0.88f, 0.92f)
                addCylinderY(1.12f, 1.1f, 0.35f, 0.09f, 0.09f, 2.65f, 8, 0.86f, 0.88f, 0.92f)
                // Side mirrors
                addBox(-1.32f, 2.40f, -1.15f, 0.14f, 0.50f, 0.16f, 0.82f, 0.85f, 0.88f)
                addBox(1.32f, 2.40f, -1.15f, 0.14f, 0.50f, 0.16f, 0.82f, 0.85f, 0.88f)
            }
        }.build()

        // Headlights, Blinkers, Brake Lights
        val zFront = if (truck.cabStyle == TruckCabStyle.EURO_CABOVER) -2.54f else -2.75f
        headlightGlowMesh = MeshBuilder().apply {
            addBox(-0.82f, 0.88f, zFront, 0.36f, 0.20f, 0.08f, 1.0f, 0.96f, 0.82f, 1.0f)
            addBox(0.82f, 0.88f, zFront, 0.36f, 0.20f, 0.08f, 1.0f, 0.96f, 0.82f, 1.0f)
        }.build()

        leftBlinkerMesh = MeshBuilder().apply {
            addBox(-1.08f, 0.88f, zFront, 0.16f, 0.18f, 0.10f, 1.0f, 0.55f, 0.0f, 1.0f)
        }.build()
        rightBlinkerMesh = MeshBuilder().apply {
            addBox(1.08f, 0.88f, zFront, 0.16f, 0.18f, 0.10f, 1.0f, 0.55f, 0.0f, 1.0f)
        }.build()

        // 2. Freight Trailer
        trailerMesh = MeshBuilder().apply {
            addBox(0f, 0.92f, 0.4f, 1.8f, 0.26f, 8.6f, 0.16f, 0.18f, 0.21f)
            addBox(0f, 0.85f, 4.65f, 2.40f, 0.32f, 0.24f, 0.22f, 0.24f, 0.26f)
            if (physics.route.id == "desert_night") {
                addCylinderZ(0f, 2.25f, 0.35f, 1.15f, 8.6f, 14, 0.82f, 0.84f, 0.88f)
                addBox(0f, 2.25f, 4.68f, 0.8f, 0.6f, 0.08f, 0.95f, 0.35f, 0.05f, 0.2f)
            } else {
                addBox(0f, 2.42f, 0.35f, 2.46f, 2.65f, 8.9f, 0.88f, 0.90f, 0.93f)
                addBox(-1.24f, 2.35f, 0.35f, 0.04f, 0.42f, 8.5f, p[0], p[1], p[2])
                addBox(1.24f, 2.35f, 0.35f, 0.04f, 0.42f, 8.5f, p[0], p[1], p[2])
                addBox(0f, 2.42f, 4.82f, 2.22f, 2.40f, 0.05f, 0.72f, 0.75f, 0.78f)
                addBox(-1.05f, 3.65f, 4.82f, 0.18f, 0.12f, 0.06f, 0.95f, 0.15f, 0.10f, 0.8f)
                addBox(1.05f, 3.65f, 4.82f, 0.18f, 0.12f, 0.06f, 0.95f, 0.15f, 0.10f, 0.8f)
            }
        }.build()

        brakeLightMesh = MeshBuilder().apply {
            addBox(-0.92f, 0.90f, 4.83f, 0.36f, 0.18f, 0.08f, 1.0f, 0.12f, 0.08f, 1.0f)
            addBox(0.92f, 0.90f, 4.83f, 0.36f, 0.18f, 0.08f, 1.0f, 0.12f, 0.08f, 1.0f)
        }.build()

        // 3. Detailed Wheel Mesh
        wheelMesh = MeshBuilder().apply {
            addCylinderX(0f, 0f, 0f, 0.52f, 0.38f, 12, 0.11f, 0.12f, 0.14f, 0.72f, 0.75f, 0.80f)
        }.build()

        // 4. Vista Real Cockpit Interior (Dashboard, Gauges, GPS, Visor, Mirrors, Hood)
        val eyeZ = cockpitEyeLocalZ()
        val dashZ = eyeZ - 0.94f
        val glassZ = if (truck.cabStyle == TruckCabStyle.EURO_CABOVER) -2.32f else -1.84f
        cockpitFrameMesh = MeshBuilder().apply {
            // Left & Right A-Pillars + Grab Handles
            addBox(-1.14f, 2.86f, glassZ, 0.12f, 1.25f, 0.14f, 0.12f, 0.14f, 0.17f)
            addBox(1.14f, 2.86f, glassZ, 0.12f, 1.25f, 0.14f, 0.12f, 0.14f, 0.17f)
            addBox(-1.05f, 2.74f, glassZ + 0.08f, 0.03f, 0.36f, 0.03f, 0.58f, 0.62f, 0.68f)

            // Roof header, sun visor & cabin dome light strip
            addBox(0f, 3.44f, glassZ + 0.08f, 2.40f, 0.24f, 0.34f, 0.09f, 0.11f, 0.14f)
            addBox(-0.48f, 3.30f, glassZ + 0.16f, 0.62f, 0.11f, 0.05f, 0.16f, 0.18f, 0.22f)
            addBox(0f, 3.36f, glassZ + 0.22f, 0.22f, 0.03f, 0.06f, 1.0f, 0.78f, 0.38f, 0.7f)

            // Main sculpted 2-tier dashboard cowl
            addBox(0f, 2.12f, dashZ - 0.14f, 2.30f, 0.36f, 0.62f, 0.13f, 0.15f, 0.18f)
            addBox(0f, 2.31f, dashZ - 0.18f, 2.26f, 0.05f, 0.44f, 0.17f, 0.19f, 0.23f)
            // Brushed aluminum dash trim strip
            addBox(0f, 2.26f, dashZ + 0.15f, 2.10f, 0.025f, 0.03f, 0.65f, 0.69f, 0.74f)

            // Driver Instrument Cluster Binnacle behind steering wheel (-0.52 X)
            addBox(-0.52f, 2.37f, dashZ + 0.02f, 0.56f, 0.16f, 0.12f, 0.07f, 0.09f, 0.12f)
            // Glowing Speedometer & Tachometer dials + needles
            addCylinderZ(-0.65f, 2.37f, dashZ + 0.08f, 0.056f, 0.02f, 12, 0.06f, 0.16f, 0.24f)
            addBox(-0.65f, 2.37f, dashZ + 0.095f, 0.07f, 0.07f, 0.01f, 0.12f, 0.72f, 0.96f, 0.75f)
            addBox(-0.65f, 2.38f, dashZ + 0.105f, 0.012f, 0.045f, 0.01f, 1.0f, 0.35f, 0.08f, 0.9f)

            addCylinderZ(-0.39f, 2.37f, dashZ + 0.08f, 0.056f, 0.02f, 12, 0.06f, 0.16f, 0.24f)
            addBox(-0.39f, 2.37f, dashZ + 0.095f, 0.07f, 0.07f, 0.01f, 0.98f, 0.52f, 0.08f, 0.75f)
            addBox(-0.39f, 2.38f, dashZ + 0.105f, 0.012f, 0.045f, 0.01f, 1.0f, 0.22f, 0.12f, 0.9f)

            // Digital center multi-function telemetry strip between dials
            addBox(-0.52f, 2.37f, dashZ + 0.09f, 0.10f, 0.08f, 0.01f, 0.14f, 0.85f, 0.45f, 0.8f)

            // Center Stack Illuminated GPS Navigation Tablet
            addBox(-0.04f, 2.34f, dashZ + 0.04f, 0.30f, 0.21f, 0.06f, 0.10f, 0.12f, 0.15f)
            addBox(-0.04f, 2.34f, dashZ + 0.074f, 0.25f, 0.16f, 0.01f, 0.07f, 0.16f, 0.24f, 0.65f)
            // Glowing GPS green route path & truck marker
            addBox(-0.04f, 2.34f, dashZ + 0.082f, 0.024f, 0.12f, 0.008f, 0.18f, 0.92f, 0.46f, 0.95f)
            addBox(-0.04f, 2.30f, dashZ + 0.084f, 0.035f, 0.035f, 0.008f, 1.0f, 0.48f, 0.05f, 1.0f)

            // Center console base for gear stick
            addBox(-0.06f, 1.94f, eyeZ - 0.64f, 0.26f, 0.14f, 0.46f, 0.12f, 0.14f, 0.17f)

            // Hood visible out front on American Long-Nose trucks (and subtle front nose edge on Euro)
            if (truck.cabStyle == TruckCabStyle.AMERICAN_CONVENTIONAL) {
                addBox(0f, 1.94f, -2.55f, 1.82f, 0.36f, 1.65f, p[0], p[1], p[2])
                // Chrome hood center ornament strip & twin stacks visible in side peripheral
                addBox(0f, 2.13f, -2.55f, 0.06f, 0.03f, 1.50f, 0.86f, 0.89f, 0.92f)
                addCylinderY(-1.12f, 1.95f, -1.65f, 0.07f, 0.07f, 1.50f, 8, 0.85f, 0.88f, 0.92f)
                addCylinderY(1.12f, 1.95f, -1.65f, 0.07f, 0.07f, 1.50f, 8, 0.85f, 0.88f, 0.92f)
            } else {
                // Subtle lower cab nose edge & chrome badge strip
                addBox(0f, 2.18f, -2.42f, 2.15f, 0.10f, 0.24f, p[0], p[1], p[2])
            }

            // Realistic exterior Side Mirrors with arm brackets + reflective glass + amber marker lights
            addBox(-1.26f, 2.70f, glassZ - 0.06f, 0.24f, 0.04f, 0.04f, 0.20f, 0.22f, 0.26f)
            addBox(-1.34f, 2.58f, glassZ - 0.08f, 0.16f, 0.46f, 0.10f, 0.14f, 0.16f, 0.19f)
            addBox(-1.34f, 2.58f, glassZ - 0.025f, 0.12f, 0.38f, 0.02f, 0.42f, 0.56f, 0.70f, 0.28f)
            addBox(-1.34f, 2.82f, glassZ - 0.08f, 0.06f, 0.04f, 0.08f, 1.0f, 0.55f, 0.05f, 0.9f)

            addBox(1.26f, 2.70f, glassZ - 0.06f, 0.24f, 0.04f, 0.04f, 0.20f, 0.22f, 0.26f)
            addBox(1.34f, 2.58f, glassZ - 0.08f, 0.16f, 0.46f, 0.10f, 0.14f, 0.16f, 0.19f)
            addBox(1.34f, 2.58f, glassZ - 0.025f, 0.12f, 0.38f, 0.02f, 0.42f, 0.56f, 0.70f, 0.28f)
        }.build()

        gearStickMesh = MeshBuilder().apply {
            // Chrome shift stalk + leather/amber knob
            addCylinderY(0f, 0f, 0f, 0.016f, 0.020f, 0.28f, 8, 0.78f, 0.80f, 0.84f)
            addBox(0f, 0.29f, 0f, 0.055f, 0.065f, 0.055f, 0.14f, 0.15f, 0.18f)
            addBox(0f, 0.325f, 0f, 0.03f, 0.01f, 0.03f, 0.98f, 0.45f, 0.05f, 0.35f)
        }.build()

        wiperArmMesh = MeshBuilder().apply {
            // Pivoted at base (0,0,0), extends left-upward
            addBox(0.24f, 0.02f, 0f, 0.48f, 0.022f, 0.02f, 0.10f, 0.11f, 0.13f)
            addBox(0.42f, 0.03f, 0f, 0.36f, 0.018f, 0.025f, 0.07f, 0.08f, 0.09f)
        }.build()

        steeringWheelMesh = MeshBuilder().apply {
            val rimR = 0.20f
            val segs = 20
            for (i in 0 until segs) {
                val a0 = (i.toFloat() / segs) * 2f * PI.toFloat()
                val px = cos(a0) * rimR
                val py = sin(a0) * rimR
                val isTopMarker = i == 5
                addBox(
                    px, py, 0f, 0.038f, 0.038f, 0.038f,
                    if (isTopMarker) 1.0f else 0.12f,
                    if (isTopMarker) 0.44f else 0.13f,
                    if (isTopMarker) 0.04f else 0.15f
                )
            }
            // Center horn pad, metallic spokes & amber emblem
            addBox(0f, 0f, 0.01f, 0.11f, 0.095f, 0.04f, 0.20f, 0.22f, 0.26f)
            addBox(0f, 0f, 0.032f, 0.04f, 0.025f, 0.01f, 0.98f, 0.45f, 0.05f, 0.5f)
            addBox(0f, -0.09f, 0.008f, 0.04f, 0.18f, 0.025f, 0.65f, 0.68f, 0.72f)
            addBox(-0.09f, 0f, 0.008f, 0.18f, 0.035f, 0.025f, 0.65f, 0.68f, 0.72f)
            addBox(0.09f, 0f, 0.008f, 0.18f, 0.035f, 0.025f, 0.65f, 0.68f, 0.72f)
            // Steering column shaft into dash
            addCylinderZ(0f, 0f, -0.14f, 0.045f, 0.24f, 10, 0.15f, 0.17f, 0.20f)
        }.build()

        // First-person forearm unit (length = 1.0 along -Z so scaleZ = len)
        forearmMesh = MeshBuilder().apply {
            addBox(0f, 0f, -0.26f, 0.09f, 0.09f, 0.52f, 0.16f, 0.21f, 0.29f)
            addBox(0f, 0f, -0.76f, 0.075f, 0.075f, 0.48f, 0.82f, 0.64f, 0.52f)
        }.build()

        gloveHandMesh = MeshBuilder().apply {
            addBox(0f, 0f, 0f, 0.085f, 0.095f, 0.085f, 0.14f, 0.12f, 0.11f)
        }.build()

        // 5. Articulated 3D Driver Character ("Hombre") Meshes
        driverTorsoMesh = MeshBuilder().apply {
            // Trucker jacket torso (0..0.56m above waist)
            addBox(0f, 0.29f, 0f, 0.44f, 0.54f, 0.25f, 0.16f, 0.21f, 0.29f)
            // High-vis amber safety vest panels & reflective silver trim
            addBox(-0.12f, 0.30f, -0.13f, 0.12f, 0.46f, 0.02f, 0.98f, 0.45f, 0.05f, 0.15f)
            addBox(0.12f, 0.30f, -0.13f, 0.12f, 0.46f, 0.02f, 0.98f, 0.45f, 0.05f, 0.15f)
            addBox(0f, 0.30f, 0.13f, 0.38f, 0.46f, 0.02f, 0.98f, 0.45f, 0.05f, 0.15f)
            addBox(0f, 0.19f, 0f, 0.46f, 0.06f, 0.28f, 0.88f, 0.92f, 0.95f, 0.28f)
            // Leather belt & steel buckle
            addBox(0f, 0.03f, 0f, 0.45f, 0.07f, 0.26f, 0.12f, 0.10f, 0.09f)
            addBox(0f, 0.03f, -0.135f, 0.08f, 0.06f, 0.02f, 0.78f, 0.80f, 0.84f)
        }.build()

        driverHeadMesh = MeshBuilder().apply {
            // Neck & head
            addBox(0f, 0.05f, 0f, 0.11f, 0.10f, 0.11f, 0.82f, 0.64f, 0.52f)
            addBox(0f, 0.20f, 0f, 0.21f, 0.23f, 0.22f, 0.85f, 0.67f, 0.54f)
            // Hair back & beard trim
            addBox(0f, 0.21f, 0.08f, 0.22f, 0.20f, 0.08f, 0.16f, 0.12f, 0.10f)
            addBox(0f, 0.12f, -0.07f, 0.19f, 0.08f, 0.10f, 0.18f, 0.14f, 0.12f)
            // Dark aviator sunglasses
            addBox(0f, 0.23f, -0.115f, 0.19f, 0.055f, 0.025f, 0.06f, 0.08f, 0.11f)
            // Amber & navy trucker cap + front brim
            addBox(0f, 0.33f, 0.01f, 0.23f, 0.10f, 0.24f, 0.96f, 0.44f, 0.05f)
            addBox(0f, 0.29f, -0.16f, 0.22f, 0.025f, 0.13f, 0.14f, 0.18f, 0.24f)
        }.build()

        driverArmMesh = MeshBuilder().apply {
            // Shoulder-pivoted arm extending downward (-Y)
            addBox(0f, -0.14f, 0f, 0.125f, 0.28f, 0.125f, 0.17f, 0.22f, 0.30f)
            addBox(0f, -0.37f, 0f, 0.10f, 0.20f, 0.10f, 0.83f, 0.65f, 0.52f)
            addBox(0f, -0.51f, 0f, 0.095f, 0.10f, 0.10f, 0.14f, 0.12f, 0.11f)
        }.build()

        driverLegMesh = MeshBuilder().apply {
            // Hip-pivoted leg extending downward (-Y) to boot sole at -0.78m
            addBox(0f, -0.31f, 0f, 0.16f, 0.62f, 0.17f, 0.15f, 0.23f, 0.38f)
            addBox(0f, -0.68f, -0.03f, 0.17f, 0.15f, 0.25f, 0.28f, 0.16f, 0.09f)
            addBox(0f, -0.765f, -0.03f, 0.18f, 0.035f, 0.26f, 0.08f, 0.08f, 0.09f)
        }.build()

        // 6. AI Traffic Meshes
        trafficCarMesh = MeshBuilder().apply {
            addBox(0f, 0.52f, 0f, 1.85f, 0.62f, 4.1f, 0.22f, 0.36f, 0.56f)
            addBox(0f, 1.05f, 0.15f, 1.60f, 0.52f, 2.0f, 0.18f, 0.30f, 0.48f)
            addBox(-0.65f, 0.54f, -2.07f, 0.28f, 0.16f, 0.06f, 1.0f, 0.95f, 0.8f, 0.9f)
            addBox(0.65f, 0.54f, -2.07f, 0.28f, 0.16f, 0.06f, 1.0f, 0.95f, 0.8f, 0.9f)
            addBox(-0.65f, 0.56f, 2.07f, 0.30f, 0.16f, 0.06f, 1.0f, 0.15f, 0.1f, 0.9f)
            addBox(0.65f, 0.56f, 2.07f, 0.30f, 0.16f, 0.06f, 1.0f, 0.15f, 0.1f, 0.9f)
        }.build()

        trafficTruckMesh = MeshBuilder().apply {
            addBox(0f, 1.85f, -3.2f, 2.25f, 2.20f, 2.1f, 0.65f, 0.20f, 0.18f)
            addBox(0f, 2.25f, 1.6f, 2.38f, 2.55f, 6.8f, 0.82f, 0.84f, 0.86f)
            addBox(-0.78f, 0.85f, -4.27f, 0.32f, 0.18f, 0.06f, 1.0f, 0.95f, 0.8f, 0.9f)
            addBox(0.78f, 0.85f, -4.27f, 0.32f, 0.18f, 0.06f, 1.0f, 0.95f, 0.8f, 0.9f)
            addBox(-0.85f, 0.95f, 5.02f, 0.32f, 0.18f, 0.06f, 1.0f, 0.15f, 0.1f, 0.9f)
            addBox(0.85f, 0.95f, 5.02f, 0.32f, 0.18f, 0.06f, 1.0f, 0.15f, 0.1f, 0.9f)
        }.build()

        // 7. Scenery Props (Trees, Light Poles, Chevrons, Gantries, Exhaust)
        treeMesh = MeshBuilder().apply {
            if (env == EnvironmentTheme.DESERT_NIGHT) {
                addCylinderY(0f, 0f, 0f, 0.45f, 0.95f, 4.2f, 6, 0.35f, 0.22f, 0.16f)
            } else {
                addCylinderY(0f, 0f, 0f, 0.22f, 0.28f, 2.4f, 6, 0.32f, 0.22f, 0.14f)
                addCylinderY(0f, 2.0f, 0f, 0.15f, 1.75f, 3.5f, 7, 0.10f, 0.30f, 0.14f)
                addCylinderY(0f, 4.2f, 0f, 0.05f, 1.25f, 3.0f, 7, 0.12f, 0.36f, 0.16f)
            }
        }.build()

        guardrailPostMesh = MeshBuilder().apply {
            addBox(0f, 0.45f, 0f, 0.14f, 0.90f, 0.14f, 0.45f, 0.48f, 0.52f)
        }.build()

        lightPoleMesh = MeshBuilder().apply {
            addCylinderY(0f, 0f, 0f, 0.08f, 0.14f, 7.6f, 6, 0.45f, 0.48f, 0.52f)
            addBox(0.95f, 7.55f, 0f, 2.0f, 0.12f, 0.12f, 0.48f, 0.52f, 0.56f)
            val lampEm = if (isNight || env == EnvironmentTheme.STORM_SUMMIT || env == EnvironmentTheme.COASTAL_SUNSET) 1.0f else 0.25f
            addBox(1.85f, 7.45f, 0f, 0.48f, 0.10f, 0.26f, 1.0f, 0.88f, 0.58f, lampEm)
        }.build()

        chevronSignMesh = MeshBuilder().apply {
            addBox(0f, 1.05f, 0f, 0.10f, 2.10f, 0.10f, 0.48f, 0.50f, 0.54f)
            addBox(0f, 2.10f, 0f, 0.95f, 0.95f, 0.08f, 0.12f, 0.13f, 0.15f)
            addBox(0f, 2.10f, 0.045f, 0.72f, 0.72f, 0.02f, 0.98f, 0.65f, 0.05f, if (isNight) 0.85f else 0.20f)
            addBox(0f, 2.10f, -0.045f, 0.72f, 0.72f, 0.02f, 0.98f, 0.65f, 0.05f, if (isNight) 0.85f else 0.20f)
        }.build()

        exhaustPuffMesh = MeshBuilder().apply {
            val smokeCol = if (isNight) 0.22f else 0.48f
            addBox(0f, 0f, 0f, 0.26f, 0.22f, 0.28f, smokeCol, smokeCol, smokeCol + 0.02f)
            addBox(0.06f, 0.06f, 0.05f, 0.18f, 0.18f, 0.20f, smokeCol * 1.12f, smokeCol * 1.12f, smokeCol * 1.14f)
        }.build()

        gantryMesh = MeshBuilder().apply {
            val w = physics.roadHalfWidth + 0.8f
            addBox(-w, 3.2f, 0f, 0.32f, 6.4f, 0.32f, 0.55f, 0.58f, 0.62f)
            addBox(w, 3.2f, 0f, 0.32f, 6.4f, 0.32f, 0.55f, 0.58f, 0.62f)
            addBox(0f, 6.2f, 0f, w * 2f + 0.4f, 0.36f, 0.36f, 0.55f, 0.58f, 0.62f)
            addBox(2.2f, 5.9f, 0.22f, 4.2f, 1.55f, 0.12f, 0.08f, 0.42f, 0.24f, if (isNight) 0.25f else 0.05f)
            if (isNight) {
                addBox(-2.2f, 6.0f, 0f, 0.6f, 0.12f, 0.3f, 1.0f, 0.85f, 0.6f, 1.0f)
                addBox(2.2f, 6.0f, 0f, 0.6f, 0.12f, 0.3f, 1.0f, 0.85f, 0.6f, 1.0f)
            }
        }.build()
    }

    private fun buildSkyDome() {
        val verts = ArrayList<Float>(1800)
        val rings = 8
        val segs = 16
        val r = 420f
        for (i in 0 until rings) {
            val phi0 = (-0.1f + (i.toFloat() / rings) * 0.6f) * Math.PI.toFloat()
            val phi1 = (-0.1f + ((i + 1).toFloat() / rings) * 0.6f) * Math.PI.toFloat()
            for (j in 0 until segs) {
                val th0 = (j.toFloat() / segs) * 2f * Math.PI.toFloat()
                val th1 = ((j + 1).toFloat() / segs) * 2f * Math.PI.toFloat()

                val x00 = cos(phi0) * cos(th0) * r; val y00 = sin(phi0) * r; val z00 = cos(phi0) * sin(th0) * r
                val x10 = cos(phi1) * cos(th0) * r; val y10 = sin(phi1) * r; val z10 = cos(phi1) * sin(th0) * r
                val x11 = cos(phi1) * cos(th1) * r; val y11 = sin(phi1) * r; val z11 = cos(phi1) * sin(th1) * r
                val x01 = cos(phi0) * cos(th1) * r; val y01 = sin(phi0) * r; val z01 = cos(phi0) * sin(th1) * r

                verts.add(x00); verts.add(y00); verts.add(z00)
                verts.add(x10); verts.add(y10); verts.add(z10)
                verts.add(x11); verts.add(y11); verts.add(z11)

                verts.add(x00); verts.add(y00); verts.add(z00)
                verts.add(x11); verts.add(y11); verts.add(z11)
                verts.add(x01); verts.add(y01); verts.add(z01)
            }
        }
        skyVertexCount = verts.size / 3
        skyDomeBuffer = allocateFloatBuffer(verts.size)
        for (f in verts) skyDomeBuffer.put(f)
        skyDomeBuffer.position(0)
    }

    private fun putRoadVert(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float, s: Float) {
        roadBuffer.put(x); roadBuffer.put(y); roadBuffer.put(z)
        roadBuffer.put(nx); roadBuffer.put(ny); roadBuffer.put(nz)
        roadBuffer.put(u); roadBuffer.put(s)
        roadVertexCount++
    }

    private fun putColorQuad(
        buf: FloatBuffer,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        r: Float, g: Float, b: Float,
        em: Float
    ) {
        val ux = x1 - x0; val uy = y1 - y0; val uz = z1 - z0
        val vx = x2 - x0; val vy = y2 - y0; val vz = z2 - z0
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.001f)
        nx /= len; ny /= len; nz /= len

        fun v(vx0: Float, vy0: Float, vz0: Float) {
            buf.put(vx0); buf.put(vy0); buf.put(vz0)
            buf.put(nx); buf.put(ny); buf.put(nz)
            buf.put(r); buf.put(g); buf.put(b); buf.put(em)
        }
        v(x0, y0, z0); v(x1, y1, z1); v(x2, y2, z2)
        v(x0, y0, z0); v(x2, y2, z2); v(x3, y3, z3)
    }

    private fun allocateFloatBuffer(floatCount: Int): FloatBuffer =
        ByteBuffer.allocateDirect(floatCount * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    private fun skyZenithRgb(): FloatArray = when (env) {
        EnvironmentTheme.ALPINE_DAY -> floatArrayOf(0.16f, 0.44f, 0.78f)
        EnvironmentTheme.COASTAL_SUNSET -> floatArrayOf(0.14f, 0.18f, 0.38f)
        EnvironmentTheme.DESERT_NIGHT -> floatArrayOf(0.02f, 0.04f, 0.10f)
        EnvironmentTheme.STORM_SUMMIT -> floatArrayOf(0.24f, 0.30f, 0.36f)
    }

    private fun skyHorizonRgb(): FloatArray = when (env) {
        EnvironmentTheme.ALPINE_DAY -> floatArrayOf(0.66f, 0.80f, 0.92f)
        EnvironmentTheme.COASTAL_SUNSET -> floatArrayOf(0.92f, 0.48f, 0.24f)
        EnvironmentTheme.DESERT_NIGHT -> floatArrayOf(0.06f, 0.08f, 0.15f)
        EnvironmentTheme.STORM_SUMMIT -> floatArrayOf(0.48f, 0.54f, 0.58f)
    }

    private fun sunDirVec(): FloatArray = when (env) {
        EnvironmentTheme.ALPINE_DAY -> floatArrayOf(0.45f, 0.75f, -0.48f)
        EnvironmentTheme.COASTAL_SUNSET -> floatArrayOf(-0.65f, 0.20f, -0.73f)
        EnvironmentTheme.DESERT_NIGHT -> floatArrayOf(0.30f, 0.65f, -0.70f)
        EnvironmentTheme.STORM_SUMMIT -> floatArrayOf(0.25f, 0.55f, -0.79f)
    }

    private fun sunColorRgb(): FloatArray = when (env) {
        EnvironmentTheme.ALPINE_DAY -> floatArrayOf(1.0f, 0.96f, 0.88f)
        EnvironmentTheme.COASTAL_SUNSET -> floatArrayOf(1.0f, 0.58f, 0.22f)
        EnvironmentTheme.DESERT_NIGHT -> floatArrayOf(0.75f, 0.82f, 0.98f)
        EnvironmentTheme.STORM_SUMMIT -> floatArrayOf(0.72f, 0.76f, 0.80f)
    }

    private fun terrainRgb(): FloatArray = when (env) {
        EnvironmentTheme.ALPINE_DAY -> floatArrayOf(0.22f, 0.34f, 0.20f)
        EnvironmentTheme.COASTAL_SUNSET -> floatArrayOf(0.32f, 0.26f, 0.20f)
        EnvironmentTheme.DESERT_NIGHT -> floatArrayOf(0.14f, 0.11f, 0.10f)
        EnvironmentTheme.STORM_SUMMIT -> floatArrayOf(0.20f, 0.24f, 0.22f)
    }

    private fun mountainRgb(): FloatArray = when (env) {
        EnvironmentTheme.ALPINE_DAY -> floatArrayOf(0.28f, 0.36f, 0.42f)
        EnvironmentTheme.COASTAL_SUNSET -> floatArrayOf(0.42f, 0.28f, 0.24f)
        EnvironmentTheme.DESERT_NIGHT -> floatArrayOf(0.10f, 0.08f, 0.11f)
        EnvironmentTheme.STORM_SUMMIT -> floatArrayOf(0.26f, 0.30f, 0.34f)
    }

    private fun fogDensity(): Float = when (env) {
        EnvironmentTheme.ALPINE_DAY -> 0.0038f
        EnvironmentTheme.COASTAL_SUNSET -> 0.0044f
        EnvironmentTheme.DESERT_NIGHT -> 0.0055f
        EnvironmentTheme.STORM_SUMMIT -> 0.0072f
    }

    private fun ambientLight(): Float = when (env) {
        EnvironmentTheme.ALPINE_DAY -> 0.44f
        EnvironmentTheme.COASTAL_SUNSET -> 0.36f
        EnvironmentTheme.DESERT_NIGHT -> 0.20f
        EnvironmentTheme.STORM_SUMMIT -> 0.38f
    }

    private fun createProgram(vertSrc: String, fragSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }

    companion object {
        private const val SKY_VERT = """
            uniform mat4 uMVP;
            attribute vec3 aPos;
            varying vec3 vDir;
            void main() {
                vDir = normalize(aPos);
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """

        private const val SKY_FRAG = """
            precision mediump float;
            varying vec3 vDir;
            uniform vec3 uZenith;
            uniform vec3 uHorizon;
            uniform vec3 uSunDir;
            uniform vec3 uSunColor;
            void main() {
                vec3 d = normalize(vDir);
                float h = clamp(d.y * 1.9, 0.0, 1.0);
                vec3 sky = mix(uHorizon, uZenith, pow(h, 0.65));
                float sunDot = max(dot(d, normalize(uSunDir)), 0.0);
                sky += uSunColor * (pow(sunDot, 22.0) * 0.45 + pow(sunDot, 260.0) * 0.95);
                gl_FragColor = vec4(sky, 1.0);
            }
        """

        private const val MESH_VERT = """
            uniform mat4 uMVP;
            uniform mat4 uModel;
            attribute vec3 aPos;
            attribute vec3 aNorm;
            attribute vec3 aColor;
            attribute float aEmissive;
            varying vec3 vWorldPos;
            varying vec3 vWorldNorm;
            varying vec3 vColor;
            varying float vEmissive;
            void main() {
                vec4 wp = uModel * vec4(aPos, 1.0);
                vWorldPos = wp.xyz;
                vWorldNorm = normalize((uModel * vec4(aNorm, 0.0)).xyz);
                vColor = aColor;
                vEmissive = aEmissive;
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """

        private const val MESH_FRAG = """
            precision mediump float;
            varying vec3 vWorldPos;
            varying vec3 vWorldNorm;
            varying vec3 vColor;
            varying float vEmissive;
            uniform vec3 uCamPos;
            uniform vec3 uFogColor;
            uniform float uFogDensity;
            uniform vec3 uSunDir;
            uniform float uAmbient;
            void main() {
                vec3 N = normalize(vWorldNorm);
                vec3 L = normalize(uSunDir);
                vec3 V = normalize(uCamPos - vWorldPos);
                vec3 H = normalize(L + V);
                float diff = max(dot(N, L), 0.0);
                float spec = pow(max(dot(N, H), 0.0), 28.0) * 0.35;
                vec3 lit = vColor * (uAmbient + diff * (1.0 - uAmbient * 0.6)) + vec3(spec);
                vec3 finalCol = mix(lit, vColor * 1.25, vEmissive);
                float dist = length(uCamPos - vWorldPos);
                float fog = 1.0 - exp(-pow(dist * uFogDensity, 1.6));
                finalCol = mix(finalCol, uFogColor, clamp(fog, 0.0, 1.0));
                gl_FragColor = vec4(finalCol, 1.0);
            }
        """

        private const val ROAD_VERT = """
            uniform mat4 uMVP;
            attribute vec3 aPos;
            attribute vec3 aNorm;
            attribute vec2 aRoadUV;
            varying vec3 vWorldPos;
            varying vec3 vNorm;
            varying vec2 vRoadUV;
            void main() {
                vWorldPos = aPos;
                vNorm = aNorm;
                vRoadUV = aRoadUV;
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """

        private const val ROAD_FRAG = """
            precision mediump float;
            varying vec3 vWorldPos;
            varying vec3 vNorm;
            varying vec2 vRoadUV;
            uniform vec3 uCamPos;
            uniform vec3 uFogColor;
            uniform float uFogDensity;
            uniform vec3 uSunDir;
            uniform float uAmbient;
            uniform float uWetness;
            uniform vec3 uTruckPos;
            uniform vec3 uTruckFwd;
            uniform float uHeadlights;

            float hashNoise(vec2 p) {
                return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
            }

            void main() {
                float u = vRoadUV.x;
                float s = vRoadUV.y;
                float grain = hashNoise(floor(vec2(u * 30.0, s * 6.0))) * 0.04;
                vec3 asphalt = vec3(0.14, 0.15, 0.17) + grain;

                // Solid white outer lane markings (at +/- 0.78)
                float edgeMask = smoothstep(0.76, 0.775, abs(u)) * (1.0 - smoothstep(0.80, 0.815, abs(u)));
                // Dashed yellow center highway lines (at +/- 0.04)
                float dashCycle = step(0.48, fract(s * 0.08));
                float centerMask = (1.0 - smoothstep(0.028, 0.045, abs(u))) * dashCycle;

                vec3 roadCol = mix(asphalt, vec3(0.92, 0.93, 0.95), edgeMask);
                roadCol = mix(roadCol, vec3(0.95, 0.75, 0.12), centerMask);

                // Lighting & wet asphalt specular
                vec3 N = normalize(vNorm);
                vec3 L = normalize(uSunDir);
                vec3 V = normalize(uCamPos - vWorldPos);
                vec3 H = normalize(L + V);
                float diff = max(dot(N, L), 0.0);
                float spec = pow(max(dot(N, H), 0.0), 36.0) * (0.20 + uWetness * 0.55);
                vec3 lit = roadCol * (uAmbient + diff * 0.72) + vec3(spec);

                // Dual truck headlight cone projection on asphalt ahead
                if (uHeadlights > 0.5) {
                    vec3 toFrag = vWorldPos - uTruckPos;
                    float fwdDist = dot(toFrag, normalize(uTruckFwd));
                    if (fwdDist > 0.5 && fwdDist < 72.0) {
                        vec3 rightVec = vec3(-uTruckFwd.z, 0.0, uTruckFwd.x);
                        float lat = dot(toFrag, normalize(rightVec));
                        float coneWidth = 1.6 + fwdDist * 0.20;
                        float latMask = 1.0 - smoothstep(coneWidth * 0.4, coneWidth, abs(lat));
                        float distAtten = 1.0 - smoothstep(12.0, 72.0, fwdDist);
                        lit += vec3(0.95, 0.90, 0.72) * latMask * distAtten * 0.85;
                    }
                }

                float dist = length(uCamPos - vWorldPos);
                float fog = 1.0 - exp(-pow(dist * uFogDensity, 1.6));
                lit = mix(lit, uFogColor, clamp(fog, 0.0, 1.0));
                gl_FragColor = vec4(lit, 1.0);
            }
        """
    }
}
