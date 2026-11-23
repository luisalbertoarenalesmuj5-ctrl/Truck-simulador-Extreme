package com.example.sim

import android.content.Context
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
    private val context: Context,
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
    private var activeFovY = 62f

    private var meshProgram = 0
    private var roadProgram = 0
    private var skyProgram = 0

    private lateinit var textures: RealisticTextureSet

    // Textured 3D meshes & photographic billboards
    private lateinit var cabMesh: GlMesh
    private lateinit var americanHoodOnlyMesh: GlMesh
    private lateinit var trailerMesh: GlMesh
    private lateinit var wheelMesh: GlMesh
    private lateinit var headlightGlowMesh: GlMesh
    private lateinit var brakeLightMesh: GlMesh
    private lateinit var leftBlinkerMesh: GlMesh
    private lateinit var rightBlinkerMesh: GlMesh

    // Photographic sprite quads (On-Foot Driver, Ground Shadow, Pine/Canyon Trees)
    private lateinit var driverSpriteMesh: GlMesh
    private lateinit var groundShadowMesh: GlMesh
    private lateinit var treeSpriteMesh: GlMesh

    // Traffic & World props
    private lateinit var trafficCarMesh: GlMesh
    private lateinit var trafficTruckMesh: GlMesh
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
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        meshProgram = createProgram(MESH_VERT, MESH_FRAG)
        roadProgram = createProgram(ROAD_VERT, ROAD_FRAG)
        skyProgram = createProgram(SKY_VERT, SKY_FRAG)

        textures = RealisticTextureFactory.buildAll(context, env)
        buildMeshes()
        buildSkyDome()

        // Allocate dynamic buffers for visible road window
        roadBuffer = allocateFloatBuffer(150 * 6 * 8) // x,y,z, nx,ny,nz, u, s
        terrainBuffer = allocateFloatBuffer(150 * 24 * FLOATS_PER_VERTEX)
        railBuffer = allocateFloatBuffer(150 * 12 * FLOATS_PER_VERTEX)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        surfaceAspect = width.toFloat() / height.coerceAtLeast(1)
        activeFovY = if (cameraMode == CameraMode.CAB && !snapshot.driverOnFoot) 62f else 56f
        Matrix.perspectiveM(projMatrix, 0, activeFovY, surfaceAspect, 0.16f, 540f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        physics.readRenderState(snapshot)

        val desiredFov = if (cameraMode == CameraMode.CAB && !snapshot.driverOnFoot) 62f else 56f
        if (abs(desiredFov - activeFovY) > 0.5f) {
            activeFovY = desiredFov
            Matrix.perspectiveM(projMatrix, 0, activeFovY, surfaceAspect, 0.16f, 540f)
        }

        updateCamera()
        rebuildRoadWindow()

        // 1. Draw Panoramic Photographic Sky & Mountains (depth write off)
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        drawSkyDome()
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_CULL_FACE)

        // 2. Draw Textured Terrain, Mountains & Guardrails
        drawDynamicColorMesh(terrainBuffer, terrainVertexCount, textures.terrainTex)
        drawDynamicColorMesh(railBuffer, railVertexCount, textures.cabMetalTex)

        // 3. Draw Photorealistic Textured Asphalt Road with dual headlight cones
        drawRoadRibbon()

        // 4. Draw Roadside Scenery (Light Poles, Chevrons, Gantries, Posts, Textured Trees)
        drawSceneryProps()

        // 5. Draw AI Traffic
        drawTraffic()

        // 6. Draw Player Tractor + Articulated Trailer + Exhaust
        drawPlayerTruck()

        // 7. Draw Photorealistic Walking Driver Sprite ("Hombre") in On-Foot mode
        if (snapshot.driverOnFoot) {
            drawDriverOnFoot()
        }
    }

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

            val bob = abs(sin(snapshot.driverWalkCycle)) * 0.04f * snapshot.driverWalkSpeed
            val targetCamX = dx - fwdX * 3.85f + rightX * 0.45f
            val targetCamY = dy + 2.05f + bob * 0.4f
            val targetCamZ = dz - fwdZ * 3.85f + rightZ * 0.45f
            val targetLookX = dx + fwdX * 5.0f
            val targetLookY = dy + 1.25f
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
                // Clean unobstructed dashcam/windshield POV aligned with photographic cockpit overlay
                val roadVibe = sin(snapshot.simClock * 26f) * 0.005f *
                    (abs(snapshot.speedMs) / 22f).coerceIn(0f, 1f)
                val lookYaw = yaw + snapshot.steerWheelAngleRad * 0.20f
                val lookFwdX = sin(lookYaw)
                val lookFwdZ = -cos(lookYaw)

                targetCamX = tx - rightX * 0.42f + fwdX * 1.45f
                targetCamY = ty + 2.66f + snapshot.cabPitchOffset * 0.65f + roadVibe
                targetCamZ = tz - rightZ * 0.42f + fwdZ * 1.45f
                targetLookX = targetCamX + lookFwdX * 32f
                targetLookY = targetCamY - 2.35f + sin(snapshot.tractorPitch + snapshot.cabPitchOffset) * 24f
                targetLookZ = targetCamZ + lookFwdZ * 32f
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
        val uvHandle = GLES20.glGetAttribLocation(skyProgram, "aUV")
        val mvpHandle = GLES20.glGetUniformLocation(skyProgram, "uMVP")
        val texHandle = GLES20.glGetUniformLocation(skyProgram, "uSkyTex")
        val horizonHandle = GLES20.glGetUniformLocation(skyProgram, "uHorizon")
        val tintHandle = GLES20.glGetUniformLocation(skyProgram, "uEnvTint")

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, smoothCamX, smoothCamY - 14f, smoothCamZ)
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, tempMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures.skyPanoTex)
        GLES20.glUniform1i(texHandle, 0)

        val hCol = skyHorizonRgb()
        val envTint = skyPanoTintRgb()
        GLES20.glUniform3f(horizonHandle, hCol[0], hCol[1], hCol[2])
        GLES20.glUniform3f(tintHandle, envTint[0], envTint[1], envTint[2])

        skyDomeBuffer.position(0)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 20, skyDomeBuffer)
        skyDomeBuffer.position(3)
        GLES20.glEnableVertexAttribArray(uvHandle)
        GLES20.glVertexAttribPointer(uvHandle, 2, GLES20.GL_FLOAT, false, 20, skyDomeBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, skyVertexCount)
        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(uvHandle)
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

        val shoulderRgb = floatArrayOf(0.32f, 0.30f, 0.27f)
        val tintWhite = 1.0f

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

            // Left & right textured ground slopes
            val leftEdge0x = p0.x - r0x * (hw + 52f); val leftEdge0z = p0.z - r0z * (hw + 52f)
            val leftEdge1x = p1.x - r1x * (hw + 52f); val leftEdge1z = p1.z - r1z * (hw + 52f)
            val ridgeH0L = if (env == EnvironmentTheme.COASTAL_SUNSET) -1.2f else 4.5f + 3.5f * sin(p0.s * 0.03f)
            val ridgeH1L = if (env == EnvironmentTheme.COASTAL_SUNSET) -1.2f else 4.5f + 3.5f * sin(p1.s * 0.03f)
            val v0 = p0.s * 0.09f
            val v1 = p1.s * 0.09f

            if (env == EnvironmentTheme.COASTAL_SUNSET) {
                putTexColorQuad(
                    terrainBuffer,
                    leftEdge0x, p0.y + ridgeH0L, leftEdge0z,
                    leftEdge1x, p1.y + ridgeH1L, leftEdge1z,
                    lx1, p1.y - 0.06f, lz1,
                    lx0, p0.y - 0.06f, lz0,
                    0.18f, 0.44f, 0.65f, 0f,
                    0f, v0, 0f, v1, 3.5f, v1, 3.5f, v0
                )
            } else {
                putTexColorQuad(
                    terrainBuffer,
                    leftEdge0x, p0.y + ridgeH0L, leftEdge0z,
                    leftEdge1x, p1.y + ridgeH1L, leftEdge1z,
                    lx1, p1.y - 0.06f, lz1,
                    lx0, p0.y - 0.06f, lz0,
                    tintWhite, tintWhite, tintWhite, 0f,
                    0f, v0, 0f, v1, 3.5f, v1, 3.5f, v0
                )
            }
            terrainVertexCount += 6

            val rightEdge0x = p0.x + r0x * (hw + 52f); val rightEdge0z = p0.z + r0z * (hw + 52f)
            val rightEdge1x = p1.x + r1x * (hw + 52f); val rightEdge1z = p1.z + r1z * (hw + 52f)
            val ridgeH0R = 4.8f + 4.2f * cos(p0.s * 0.027f)
            val ridgeH1R = 4.8f + 4.2f * cos(p1.s * 0.027f)
            putTexColorQuad(
                terrainBuffer,
                rx0, p0.y - 0.06f, rz0,
                rx1, p1.y - 0.06f, rz1,
                rightEdge1x, p1.y + ridgeH1R, rightEdge1z,
                rightEdge0x, p0.y + ridgeH0R, rightEdge0z,
                0.95f, 0.95f, 0.95f, 0f,
                0f, v0, 0f, v1, 3.5f, v1, 3.5f, v0
            )
            terrainVertexCount += 6

            // Shoulder gravel lips on both sides
            val sx0 = p0.x + r0x * (hw + 1.5f); val sz0 = p0.z + r0z * (hw + 1.5f)
            val sx1 = p1.x + r1x * (hw + 1.5f); val sz1 = p1.z + r1z * (hw + 1.5f)
            putTexColorQuad(
                terrainBuffer,
                rx0, p0.y - 0.03f, rz0,
                rx1, p1.y - 0.03f, rz1,
                sx1, p1.y - 0.05f, sz1,
                sx0, p0.y - 0.05f, sz0,
                shoulderRgb[0], shoulderRgb[1], shoulderRgb[2], 0f,
                0f, v0, 0f, v1, 0.4f, v1, 0.4f, v0
            )
            terrainVertexCount += 6

            val slx0 = p0.x - r0x * (hw + 1.5f); val slz0 = p0.z - r0z * (hw + 1.5f)
            val slx1 = p1.x - r1x * (hw + 1.5f); val slz1 = p1.z - r1z * (hw + 1.5f)
            putTexColorQuad(
                terrainBuffer,
                slx0, p0.y - 0.05f, slz0,
                slx1, p1.y - 0.05f, slz1,
                lx1, p1.y - 0.03f, lz1,
                lx0, p0.y - 0.03f, lz0,
                shoulderRgb[0], shoulderRgb[1], shoulderRgb[2], 0f,
                0f, v0, 0f, v1, 0.4f, v1, 0.4f, v0
            )
            terrainVertexCount += 6

            // Guardrails (steel beams along edges)
            val railOff = hw + 0.35f
            val rlx0 = p0.x - r0x * railOff; val rlz0 = p0.z - r0z * railOff
            val rlx1 = p1.x - r1x * railOff; val rlz1 = p1.z - r1z * railOff
            putTexColorQuad(
                railBuffer,
                rlx0, p0.y + 0.45f, rlz0,
                rlx0, p0.y + 0.85f, rlz0,
                rlx1, p1.y + 0.85f, rlz1,
                rlx1, p1.y + 0.45f, rlz1,
                0.76f, 0.80f, 0.84f, 0f,
                0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f
            )
            railVertexCount += 6

            val rrx0 = p0.x + r0x * railOff; val rrz0 = p0.z + r0z * railOff
            val rrx1 = p1.x + r1x * railOff; val rrz1 = p1.z + r1z * railOff
            putTexColorQuad(
                railBuffer,
                rrx1, p1.y + 0.45f, rrz1,
                rrx1, p1.y + 0.85f, rrz1,
                rrx0, p0.y + 0.85f, rrz0,
                rrx0, p0.y + 0.45f, rrz0,
                0.76f, 0.80f, 0.84f, 0f,
                0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f
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
        GLES20.glUniform1f(GLES20.glGetUniformLocation(roadProgram, "uWetness"), if (env == EnvironmentTheme.STORM_SUMMIT || env == EnvironmentTheme.COASTAL_SUNSET) 0.72f else 0.25f)

        val fwdX = sin(snapshot.tractorYaw)
        val fwdZ = -cos(snapshot.tractorYaw)
        GLES20.glUniform3f(GLES20.glGetUniformLocation(roadProgram, "uTruckPos"), snapshot.tractorX, snapshot.tractorY, snapshot.tractorZ)
        GLES20.glUniform3f(GLES20.glGetUniformLocation(roadProgram, "uTruckFwd"), fwdX, 0f, fwdZ)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(roadProgram, "uHeadlights"), if (snapshot.headlightsOn) 1f else 0f)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures.asphaltTex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(roadProgram, "uRoadTex"), 0)

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

    private fun drawDynamicColorMesh(buffer: FloatBuffer, vertexCount: Int, texId: Int) {
        if (vertexCount <= 0) return
        setupMeshShaderCommon(texId, alphaCutoff = 0.0f)
        Matrix.setIdentityM(modelMatrix, 0)
        drawBufferWithMeshShader(buffer, vertexCount, modelMatrix)
    }

    private fun drawSceneryProps() {
        val road = physics.road
        val startIdx = (snapshot.roadIndexHint - 4).coerceAtLeast(0)
        val endIdx = (snapshot.roadIndexHint + 85).coerceAtMost(road.points.lastIndex)

        setupMeshShaderCommon(textures.whiteTex, alphaCutoff = 0.0f)
        for (i in startIdx..endIdx) {
            val pt = road.points[i]
            val rightX = cos(pt.yaw)
            val rightZ = sin(pt.yaw)

            if (i % 3 == 0) {
                val off = physics.roadHalfWidth + 0.38f
                setModelMatrix(pt.x - rightX * off, pt.y, pt.z - rightZ * off, pt.yaw, 0f, 0f)
                drawMesh(guardrailPostMesh, modelMatrix)
                setModelMatrix(pt.x + rightX * off, pt.y, pt.z + rightZ * off, pt.yaw, 0f, 0f)
                drawMesh(guardrailPostMesh, modelMatrix)
            }

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

            if (i % 4 == 0 && abs(pt.curvature) > 0.0065f) {
                val outsideSign = if (pt.curvature > 0f) -1f else 1f
                val off = physics.roadHalfWidth + 0.9f
                setModelMatrix(pt.x + rightX * off * outsideSign, pt.y, pt.z + rightZ * off * outsideSign, pt.yaw, 0f, 0f)
                drawMesh(chevronSignMesh, modelMatrix)
            }

            if (i % 35 == 15) {
                setModelMatrix(pt.x, pt.y, pt.z, pt.yaw, pt.pitch, 0f)
                drawMesh(gantryMesh, modelMatrix)
            }
        }

        // Crossed photorealistic foliage / desert canyon sprites with alpha cutout
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        setupMeshShaderCommon(textures.treeTex, alphaCutoff = 0.42f)
        for (i in startIdx..endIdx) {
            if (i % 4 != 0) continue
            val pt = road.points[i]
            val rightX = cos(pt.yaw)
            val rightZ = sin(pt.yaw)
            val scale = 0.85f + ((i * 13) % 5) * 0.14f

            val distR = physics.roadHalfWidth + 4.4f + ((i * 7) % 7)
            val rx = pt.x + rightX * distR
            val rz = pt.z + rightZ * distR
            val yawR = atan2(smoothCamX - rx, smoothCamZ - rz)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, rx, pt.y, rz)
            Matrix.rotateM(modelMatrix, 0, Math.toDegrees(yawR.toDouble()).toFloat(), 0f, 1f, 0f)
            Matrix.scaleM(modelMatrix, 0, scale, scale, 1f)
            drawMesh(treeSpriteMesh, modelMatrix)

            if (env != EnvironmentTheme.COASTAL_SUNSET) {
                val distL = physics.roadHalfWidth + 4.8f + ((i * 11) % 8)
                val lx = pt.x - rightX * distL
                val lz = pt.z - rightZ * distL
                val yawL = atan2(smoothCamX - lx, smoothCamZ - lz)
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, lx, pt.y, lz)
                Matrix.rotateM(modelMatrix, 0, Math.toDegrees(yawL.toDouble()).toFloat(), 0f, 1f, 0f)
                Matrix.scaleM(modelMatrix, 0, scale * 1.05f, scale * 1.05f, 1f)
                drawMesh(treeSpriteMesh, modelMatrix)
            }
        }
        GLES20.glEnable(GLES20.GL_CULL_FACE)
    }

    private fun drawTraffic() {
        setupMeshShaderCommon(textures.cabMetalTex, alphaCutoff = 0.0f)
        val road = physics.road
        for (v in snapshot.trafficCopy) {
            val dist = abs(v.s - road.points[snapshot.roadIndexHint].s)
            if (dist > 280f) continue
            val pt = road.sampleAtDistance(v.s)
            val rightX = cos(pt.yaw)
            val rightZ = sin(pt.yaw)
            val vx = pt.x + rightX * v.laneOffset
            val vz = pt.z + rightZ * v.laneOffset
            val vyaw = if (v.isOncoming) pt.yaw + PI.toFloat() else pt.yaw
            val vpitch = if (v.isOncoming) -pt.pitch else pt.pitch

            setModelMatrix(vx, pt.y, vz, vyaw, vpitch, 0f)
            drawMesh(if (v.isTruck) trafficTruckMesh else trafficCarMesh, modelMatrix)
        }
    }

    private fun drawPlayerTruck() {
        val isDashcamCabView = !snapshot.driverOnFoot && cameraMode == CameraMode.CAB

        if (isDashcamCabView) {
            // In Vista Real, Compose renders the genuine photographic cockpit frame;
            // only render the sculpted long-nose hood outside the windshield on American trucks.
            if (truck.cabStyle == TruckCabStyle.AMERICAN_CONVENTIONAL) {
                setupMeshShaderCommon(textures.cabMetalTex, alphaCutoff = 0.0f)
                setModelMatrix(
                    snapshot.tractorX,
                    snapshot.tractorY,
                    snapshot.tractorZ,
                    snapshot.tractorYaw,
                    snapshot.tractorPitch + snapshot.cabPitchOffset,
                    snapshot.cabRollOffset
                )
                drawMesh(americanHoodOnlyMesh, modelMatrix)
            }
            return
        }

        // Exterior Trailer with corrugated siding & DOT stripes
        setupMeshShaderCommon(textures.trailerTex, alphaCutoff = 0.0f)
        setModelMatrix(
            snapshot.trailerX,
            snapshot.tractorY,
            snapshot.trailerZ,
            snapshot.trailerYaw,
            snapshot.tractorPitch,
            snapshot.cabRollOffset * 0.6f
        )
        drawMesh(trailerMesh, modelMatrix)
        if (snapshot.braking) {
            setupMeshShaderCommon(textures.whiteTex, alphaCutoff = 0.0f)
            drawMesh(brakeLightMesh, modelMatrix)
        }

        // Trailer Wheels with radial chrome/rubber texture
        setupMeshShaderCommon(textures.wheelTex, alphaCutoff = 0.0f)
        drawTrailerWheels(snapshot.trailerX, snapshot.tractorY, snapshot.trailerZ, snapshot.trailerYaw)

        // Tractor Body with metallic automotive texture
        setupMeshShaderCommon(textures.cabMetalTex, alphaCutoff = 0.0f)
        setModelMatrix(
            snapshot.tractorX,
            snapshot.tractorY,
            snapshot.tractorZ,
            snapshot.tractorYaw,
            snapshot.tractorPitch + snapshot.cabPitchOffset,
            snapshot.cabRollOffset
        )
        drawMesh(cabMesh, modelMatrix)

        setupMeshShaderCommon(textures.whiteTex, alphaCutoff = 0.0f)
        if (snapshot.headlightsOn) drawMesh(headlightGlowMesh, modelMatrix)
        if (snapshot.leftBlinkerOn) drawMesh(leftBlinkerMesh, modelMatrix)
        if (snapshot.rightBlinkerOn) drawMesh(rightBlinkerMesh, modelMatrix)

        setupMeshShaderCommon(textures.wheelTex, alphaCutoff = 0.0f)
        drawTractorWheels()

        drawExhaustSmoke()
    }

    private fun drawDriverOnFoot() {
        val walkAmp = snapshot.driverWalkSpeed
        val phase = snapshot.driverWalkCycle
        val strideBob = abs(sin(phase)) * 0.055f * walkAmp
        val strideSwayDeg = sin(phase) * 3.8f * walkAmp
        val sprintLeanDeg = -walkAmp * 5.0f

        GLES20.glDisable(GLES20.GL_CULL_FACE)

        // 1. Soft photographic contact shadow under boots on the asphalt
        GLES20.glDepthMask(false)
        setupMeshShaderCommon(textures.shadowTex, alphaCutoff = 0.04f)
        val shadowScale = 1.0f - strideBob * 1.4f
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, snapshot.driverX, snapshot.driverY + 0.03f, snapshot.driverZ)
        Matrix.scaleM(modelMatrix, 0, shadowScale, 1f, shadowScale)
        drawMesh(groundShadowMesh, modelMatrix)
        GLES20.glDepthMask(true)

        // 2. Photorealistic walking driver cutout billboard facing camera
        setupMeshShaderCommon(textures.driverWalkTex, alphaCutoff = 0.18f)
        val billboardYaw = atan2(smoothCamX - snapshot.driverX, smoothCamZ - snapshot.driverZ)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, snapshot.driverX, snapshot.driverY + strideBob, snapshot.driverZ)
        Matrix.rotateM(modelMatrix, 0, Math.toDegrees(billboardYaw.toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.rotateM(modelMatrix, 0, strideSwayDeg, 0f, 0f, 1f)
        Matrix.rotateM(modelMatrix, 0, sprintLeanDeg, 1f, 0f, 0f)
        drawMesh(driverSpriteMesh, modelMatrix)

        GLES20.glEnable(GLES20.GL_CULL_FACE)
    }

    private fun drawExhaustSmoke() {
        setupMeshShaderCommon(textures.whiteTex, alphaCutoff = 0.0f)
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
            floatArrayOf(-1.12f, 0.52f, -1.75f, 1f),
            floatArrayOf(1.12f, 0.52f, -1.75f, 1f),
            floatArrayOf(-1.12f, 0.52f, 0.65f, 0f),
            floatArrayOf(1.12f, 0.52f, 0.65f, 0f),
            floatArrayOf(-1.12f, 0.52f, 1.95f, 0f),
            floatArrayOf(1.12f, 0.52f, 1.95f, 0f)
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

    private fun setupMeshShaderCommon(texId: Int, alphaCutoff: Float) {
        GLES20.glUseProgram(meshProgram)
        val hCol = skyHorizonRgb()
        val sDir = sunDirVec()
        GLES20.glUniform3f(GLES20.glGetUniformLocation(meshProgram, "uCamPos"), smoothCamX, smoothCamY, smoothCamZ)
        GLES20.glUniform3f(GLES20.glGetUniformLocation(meshProgram, "uFogColor"), hCol[0], hCol[1], hCol[2])
        GLES20.glUniform1f(GLES20.glGetUniformLocation(meshProgram, "uFogDensity"), fogDensity())
        GLES20.glUniform3f(GLES20.glGetUniformLocation(meshProgram, "uSunDir"), sDir[0], sDir[1], sDir[2])
        GLES20.glUniform1f(GLES20.glGetUniformLocation(meshProgram, "uAmbient"), ambientLight())
        GLES20.glUniform1f(GLES20.glGetUniformLocation(meshProgram, "uAlphaCutoff"), alphaCutoff)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(meshProgram, "uTex"), 0)
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
        val uvH = GLES20.glGetAttribLocation(meshProgram, "aTexCoord")

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
        buffer.position(10)
        GLES20.glEnableVertexAttribArray(uvH)
        GLES20.glVertexAttribPointer(uvH, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, buffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)

        GLES20.glDisableVertexAttribArray(posH)
        GLES20.glDisableVertexAttribArray(normH)
        GLES20.glDisableVertexAttribArray(colH)
        GLES20.glDisableVertexAttribArray(emH)
        GLES20.glDisableVertexAttribArray(uvH)
    }

    private fun buildMeshes() {
        val p = truck.primaryColorRgb
        val a = truck.accentColorRgb

        cabMesh = MeshBuilder().apply {
            addBox(0f, 0.72f, 0.1f, 1.05f, 0.28f, 5.1f, 0.16f, 0.18f, 0.22f)
            addBox(-0.88f, 0.72f, 0.2f, 0.52f, 0.52f, 1.6f, 0.80f, 0.83f, 0.87f)
            addBox(0.88f, 0.72f, 0.2f, 0.52f, 0.52f, 1.6f, 0.80f, 0.83f, 0.87f)
            addBox(-1.14f, 0.96f, -1.10f, 0.16f, 0.08f, 0.52f, 0.76f, 0.79f, 0.84f)
            addBox(1.14f, 0.96f, -1.10f, 0.16f, 0.08f, 0.52f, 0.76f, 0.79f, 0.84f)
            addBox(0f, 0.95f, 1.15f, 1.1f, 0.14f, 0.95f, 0.22f, 0.24f, 0.28f)
            addBox(0f, 0.58f, -2.52f, 2.34f, 0.34f, 0.22f, 0.86f, 0.89f, 0.92f)

            if (truck.cabStyle == TruckCabStyle.EURO_CABOVER) {
                addBox(0f, 2.15f, -1.45f, 2.34f, 2.25f, 2.05f, p[0], p[1], p[2])
                addBox(0f, 3.45f, -1.25f, 2.18f, 0.45f, 1.65f, p[0] * 0.95f, p[1] * 0.95f, p[2] * 0.95f)
                addBox(0f, 2.55f, -2.49f, 2.08f, 0.95f, 0.06f, 0.10f, 0.16f, 0.22f)
                addBox(-1.18f, 2.55f, -1.65f, 0.04f, 0.75f, 1.15f, 0.10f, 0.16f, 0.22f)
                addBox(1.18f, 2.55f, -1.65f, 0.04f, 0.75f, 1.15f, 0.10f, 0.16f, 0.22f)
                addBox(0f, 1.45f, -2.49f, 1.65f, 0.85f, 0.06f, 0.18f, 0.20f, 0.24f)
                addBox(0f, 2.02f, -2.49f, 2.1f, 0.12f, 0.07f, a[0], a[1], a[2])
                addCylinderY(-0.98f, 1.15f, -0.35f, 0.08f, 0.08f, 2.20f, 8, 0.82f, 0.84f, 0.88f)
                addBox(-1.34f, 2.45f, -2.25f, 0.14f, 0.48f, 0.18f, 0.16f, 0.18f, 0.20f)
                addBox(1.34f, 2.45f, -2.25f, 0.14f, 0.48f, 0.18f, 0.16f, 0.18f, 0.20f)
            } else {
                addBox(0f, 1.48f, -1.85f, 1.85f, 1.10f, 1.75f, p[0], p[1], p[2])
                addBox(0f, 1.45f, -2.74f, 1.45f, 1.02f, 0.08f, 0.88f, 0.90f, 0.94f)
                addBox(0f, 2.25f, -0.35f, 2.30f, 2.15f, 1.85f, p[0], p[1], p[2])
                addBox(0f, 2.55f, -1.29f, 2.02f, 0.72f, 0.06f, 0.10f, 0.16f, 0.22f)
                addBox(-1.16f, 1.95f, -0.35f, 0.04f, 0.18f, 1.70f, a[0], a[1], a[2])
                addBox(1.16f, 1.95f, -0.35f, 0.04f, 0.18f, 1.70f, a[0], a[1], a[2])
                addCylinderY(-1.12f, 1.1f, 0.35f, 0.09f, 0.09f, 2.65f, 8, 0.88f, 0.90f, 0.94f)
                addCylinderY(1.12f, 1.1f, 0.35f, 0.09f, 0.09f, 2.65f, 8, 0.88f, 0.90f, 0.94f)
                addBox(-1.32f, 2.40f, -1.15f, 0.14f, 0.50f, 0.16f, 0.86f, 0.88f, 0.90f)
                addBox(1.32f, 2.40f, -1.15f, 0.14f, 0.50f, 0.16f, 0.86f, 0.88f, 0.90f)
            }
        }.build()

        americanHoodOnlyMesh = MeshBuilder().apply {
            addBox(0f, 1.72f, -2.45f, 1.82f, 0.52f, 1.65f, p[0], p[1], p[2])
            addBox(0f, 2.00f, -2.45f, 0.06f, 0.03f, 1.50f, 0.88f, 0.90f, 0.94f)
        }.build()

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

        trailerMesh = MeshBuilder().apply {
            addBox(0f, 0.92f, 0.4f, 1.8f, 0.26f, 8.6f, 0.18f, 0.20f, 0.23f)
            addBox(0f, 0.85f, 4.65f, 2.40f, 0.32f, 0.24f, 0.24f, 0.26f, 0.28f)
            if (physics.route.id == "desert_night") {
                addCylinderZ(0f, 2.25f, 0.35f, 1.15f, 8.6f, 16, 0.90f, 0.92f, 0.95f)
                addBox(0f, 2.25f, 4.68f, 0.8f, 0.6f, 0.08f, 0.95f, 0.35f, 0.05f, 0.2f)
            } else {
                addBox(0f, 2.42f, 0.35f, 2.46f, 2.65f, 8.9f, 0.98f, 0.98f, 0.98f)
                addBox(-1.24f, 2.35f, 0.35f, 0.04f, 0.42f, 8.5f, p[0], p[1], p[2])
                addBox(1.24f, 2.35f, 0.35f, 0.04f, 0.42f, 8.5f, p[0], p[1], p[2])
                addBox(0f, 2.42f, 4.82f, 2.22f, 2.40f, 0.05f, 0.86f, 0.88f, 0.90f)
                addBox(-1.05f, 3.65f, 4.82f, 0.18f, 0.12f, 0.06f, 0.95f, 0.15f, 0.10f, 0.8f)
                addBox(1.05f, 3.65f, 4.82f, 0.18f, 0.12f, 0.06f, 0.95f, 0.15f, 0.10f, 0.8f)
            }
        }.build()

        brakeLightMesh = MeshBuilder().apply {
            addBox(-0.92f, 0.90f, 4.83f, 0.36f, 0.18f, 0.08f, 1.0f, 0.12f, 0.08f, 1.0f)
            addBox(0.92f, 0.90f, 4.83f, 0.36f, 0.18f, 0.08f, 1.0f, 0.12f, 0.08f, 1.0f)
        }.build()

        // White vertex color so wheelTex (rubber + chrome 10-lug hub) shows crisply
        wheelMesh = MeshBuilder().apply {
            addCylinderX(0f, 0f, 0f, 0.52f, 0.38f, 14, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f)
        }.build()

        // Photorealistic upright character cutout billboard (bottom y=0, top y=1.78)
        driverSpriteMesh = MeshBuilder().apply {
            addQuad(
                -0.66f, 0f, 0f,
                -0.66f, 1.78f, 0f,
                0.66f, 1.78f, 0f,
                0.66f, 0f, 0f,
                1f, 1f, 1f, 0.15f,
                0f, 0.98f, 0f, 0.02f, 1f, 0.02f, 1f, 0.98f
            )
        }.build()

        // Soft contact shadow disc on ground
        groundShadowMesh = MeshBuilder().apply {
            addQuad(
                -0.62f, 0f, 0.62f,
                -0.62f, 0f, -0.62f,
                0.62f, 0f, -0.62f,
                0.62f, 0f, 0.62f,
                1f, 1f, 1f, 0f,
                0f, 1f, 0f, 0f, 1f, 0f, 1f, 1f
            )
        }.build()

        // Photorealistic roadside pine/desert billboard sprite
        treeSpriteMesh = MeshBuilder().apply {
            addQuad(
                -2.1f, 0f, 0f,
                -2.1f, 6.2f, 0f,
                2.1f, 6.2f, 0f,
                2.1f, 0f, 0f,
                1f, 1f, 1f, 0f,
                0f, 1f, 0f, 0f, 1f, 0f, 1f, 1f
            )
        }.build()

        trafficCarMesh = MeshBuilder().apply {
            addBox(0f, 0.52f, 0f, 1.85f, 0.62f, 4.1f, 0.28f, 0.42f, 0.62f)
            addBox(0f, 1.05f, 0.15f, 1.60f, 0.52f, 2.0f, 0.22f, 0.34f, 0.52f)
            addBox(-0.65f, 0.54f, -2.07f, 0.28f, 0.16f, 0.06f, 1.0f, 0.95f, 0.8f, 0.9f)
            addBox(0.65f, 0.54f, -2.07f, 0.28f, 0.16f, 0.06f, 1.0f, 0.95f, 0.8f, 0.9f)
            addBox(-0.65f, 0.56f, 2.07f, 0.30f, 0.16f, 0.06f, 1.0f, 0.15f, 0.1f, 0.9f)
            addBox(0.65f, 0.56f, 2.07f, 0.30f, 0.16f, 0.06f, 1.0f, 0.15f, 0.1f, 0.9f)
        }.build()

        trafficTruckMesh = MeshBuilder().apply {
            addBox(0f, 1.85f, -3.2f, 2.25f, 2.20f, 2.1f, 0.65f, 0.22f, 0.20f)
            addBox(0f, 2.25f, 1.6f, 2.38f, 2.55f, 6.8f, 0.90f, 0.92f, 0.94f)
            addBox(-0.78f, 0.85f, -4.27f, 0.32f, 0.18f, 0.06f, 1.0f, 0.95f, 0.8f, 0.9f)
            addBox(0.78f, 0.85f, -4.27f, 0.32f, 0.18f, 0.06f, 1.0f, 0.95f, 0.8f, 0.9f)
            addBox(-0.85f, 0.95f, 5.02f, 0.32f, 0.18f, 0.06f, 1.0f, 0.15f, 0.1f, 0.9f)
            addBox(0.85f, 0.95f, 5.02f, 0.32f, 0.18f, 0.06f, 1.0f, 0.15f, 0.1f, 0.9f)
        }.build()

        guardrailPostMesh = MeshBuilder().apply {
            addBox(0f, 0.45f, 0f, 0.14f, 0.90f, 0.14f, 0.48f, 0.50f, 0.54f)
        }.build()

        lightPoleMesh = MeshBuilder().apply {
            addCylinderY(0f, 0f, 0f, 0.08f, 0.14f, 7.6f, 6, 0.48f, 0.50f, 0.54f)
            addBox(0.95f, 7.55f, 0f, 2.0f, 0.12f, 0.12f, 0.52f, 0.55f, 0.58f)
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
        val verts = ArrayList<Float>(3600)
        val rings = 10
        val segs = 24
        val r = 420f
        for (i in 0 until rings) {
            val frac0 = i.toFloat() / rings
            val frac1 = (i + 1).toFloat() / rings
            val phi0 = (-0.08f + frac0 * 0.58f) * PI.toFloat()
            val phi1 = (-0.08f + frac1 * 0.58f) * PI.toFloat()
            // Map horizon (frac ~0.12) to mountain line v=0.60, zenith (frac 1.0) to v=0.02
            val v0 = (0.66f - frac0 * 0.64f).coerceIn(0.01f, 0.72f)
            val v1 = (0.66f - frac1 * 0.64f).coerceIn(0.01f, 0.72f)
            for (j in 0 until segs) {
                val u0 = (j.toFloat() / segs) * 2.0f
                val u1 = ((j + 1).toFloat() / segs) * 2.0f
                val th0 = (j.toFloat() / segs) * 2f * PI.toFloat()
                val th1 = ((j + 1).toFloat() / segs) * 2f * PI.toFloat()

                val x00 = cos(phi0) * cos(th0) * r; val y00 = sin(phi0) * r; val z00 = cos(phi0) * sin(th0) * r
                val x10 = cos(phi1) * cos(th0) * r; val y10 = sin(phi1) * r; val z10 = cos(phi1) * sin(th0) * r
                val x11 = cos(phi1) * cos(th1) * r; val y11 = sin(phi1) * r; val z11 = cos(phi1) * sin(th1) * r
                val x01 = cos(phi0) * cos(th1) * r; val y01 = sin(phi0) * r; val z01 = cos(phi0) * sin(th1) * r

                verts.add(x00); verts.add(y00); verts.add(z00); verts.add(u0); verts.add(v0)
                verts.add(x10); verts.add(y10); verts.add(z10); verts.add(u0); verts.add(v1)
                verts.add(x11); verts.add(y11); verts.add(z11); verts.add(u1); verts.add(v1)

                verts.add(x00); verts.add(y00); verts.add(z00); verts.add(u0); verts.add(v0)
                verts.add(x11); verts.add(y11); verts.add(z11); verts.add(u1); verts.add(v1)
                verts.add(x01); verts.add(y01); verts.add(z01); verts.add(u1); verts.add(v0)
            }
        }
        skyVertexCount = verts.size / 5
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

    private fun putTexColorQuad(
        buf: FloatBuffer,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        r: Float, g: Float, b: Float,
        em: Float,
        u0: Float, v0: Float,
        u1: Float, v1: Float,
        u2: Float, v2: Float,
        u3: Float, v3: Float
    ) {
        val ux = x1 - x0; val uy = y1 - y0; val uz = z1 - z0
        val vx = x2 - x0; val vy = y2 - y0; val vz = z2 - z0
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.001f)
        nx /= len; ny /= len; nz /= len

        fun v(vx0: Float, vy0: Float, vz0: Float, uu: Float, vv: Float) {
            buf.put(vx0); buf.put(vy0); buf.put(vz0)
            buf.put(nx); buf.put(ny); buf.put(nz)
            buf.put(r); buf.put(g); buf.put(b); buf.put(em)
            buf.put(uu); buf.put(vv)
        }
        v(x0, y0, z0, u0, v0); v(x1, y1, z1, u1, v1); v(x2, y2, z2, u2, v2)
        v(x0, y0, z0, u0, v0); v(x2, y2, z2, u2, v2); v(x3, y3, z3, u3, v3)
    }

    private fun allocateFloatBuffer(floatCount: Int): FloatBuffer =
        ByteBuffer.allocateDirect(floatCount * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    private fun skyHorizonRgb(): FloatArray = when (env) {
        EnvironmentTheme.ALPINE_DAY -> floatArrayOf(0.68f, 0.80f, 0.90f)
        EnvironmentTheme.COASTAL_SUNSET -> floatArrayOf(0.88f, 0.50f, 0.28f)
        EnvironmentTheme.DESERT_NIGHT -> floatArrayOf(0.08f, 0.10f, 0.16f)
        EnvironmentTheme.STORM_SUMMIT -> floatArrayOf(0.46f, 0.52f, 0.57f)
    }

    private fun skyPanoTintRgb(): FloatArray = when (env) {
        EnvironmentTheme.ALPINE_DAY -> floatArrayOf(1.04f, 1.02f, 1.0f)
        EnvironmentTheme.COASTAL_SUNSET -> floatArrayOf(1.08f, 0.88f, 0.72f)
        EnvironmentTheme.DESERT_NIGHT -> floatArrayOf(0.95f, 0.95f, 1.05f)
        EnvironmentTheme.STORM_SUMMIT -> floatArrayOf(0.68f, 0.74f, 0.80f)
    }

    private fun sunDirVec(): FloatArray = when (env) {
        EnvironmentTheme.ALPINE_DAY -> floatArrayOf(0.45f, 0.75f, -0.48f)
        EnvironmentTheme.COASTAL_SUNSET -> floatArrayOf(-0.65f, 0.20f, -0.73f)
        EnvironmentTheme.DESERT_NIGHT -> floatArrayOf(0.30f, 0.65f, -0.70f)
        EnvironmentTheme.STORM_SUMMIT -> floatArrayOf(0.25f, 0.55f, -0.79f)
    }

    private fun fogDensity(): Float = when (env) {
        EnvironmentTheme.ALPINE_DAY -> 0.0036f
        EnvironmentTheme.COASTAL_SUNSET -> 0.0042f
        EnvironmentTheme.DESERT_NIGHT -> 0.0052f
        EnvironmentTheme.STORM_SUMMIT -> 0.0068f
    }

    private fun ambientLight(): Float = when (env) {
        EnvironmentTheme.ALPINE_DAY -> 0.52f
        EnvironmentTheme.COASTAL_SUNSET -> 0.44f
        EnvironmentTheme.DESERT_NIGHT -> 0.26f
        EnvironmentTheme.STORM_SUMMIT -> 0.42f
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
            attribute vec2 aUV;
            varying vec3 vDir;
            varying vec2 vUV;
            void main() {
                vDir = normalize(aPos);
                vUV = aUV;
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """

        private const val SKY_FRAG = """
            precision mediump float;
            varying vec3 vDir;
            varying vec2 vUV;
            uniform sampler2D uSkyTex;
            uniform vec3 uHorizon;
            uniform vec3 uEnvTint;
            void main() {
                vec3 texSky = texture2D(uSkyTex, vUV).rgb * uEnvTint;
                float horizonBlend = smoothstep(0.08, -0.04, normalize(vDir).y);
                vec3 finalSky = mix(texSky, uHorizon, horizonBlend);
                gl_FragColor = vec4(finalSky, 1.0);
            }
        """

        private const val MESH_VERT = """
            uniform mat4 uMVP;
            uniform mat4 uModel;
            attribute vec3 aPos;
            attribute vec3 aNorm;
            attribute vec3 aColor;
            attribute float aEmissive;
            attribute vec2 aTexCoord;
            varying vec3 vWorldPos;
            varying vec3 vWorldNorm;
            varying vec3 vColor;
            varying float vEmissive;
            varying vec2 vTexCoord;
            void main() {
                vec4 wp = uModel * vec4(aPos, 1.0);
                vWorldPos = wp.xyz;
                vWorldNorm = normalize((uModel * vec4(aNorm, 0.0)).xyz);
                vColor = aColor;
                vEmissive = aEmissive;
                vTexCoord = aTexCoord;
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """

        private const val MESH_FRAG = """
            precision mediump float;
            varying vec3 vWorldPos;
            varying vec3 vWorldNorm;
            varying vec3 vColor;
            varying float vEmissive;
            varying vec2 vTexCoord;
            uniform sampler2D uTex;
            uniform vec3 uCamPos;
            uniform vec3 uFogColor;
            uniform float uFogDensity;
            uniform vec3 uSunDir;
            uniform float uAmbient;
            uniform float uAlphaCutoff;
            void main() {
                vec4 texCol = texture2D(uTex, vTexCoord);
                if (texCol.a < uAlphaCutoff) discard;

                vec3 baseCol = texCol.rgb * vColor;
                vec3 N = normalize(vWorldNorm);
                vec3 L = normalize(uSunDir);
                vec3 V = normalize(uCamPos - vWorldPos);
                vec3 H = normalize(L + V);
                float diff = max(dot(N, L), 0.0);
                float spec = pow(max(dot(N, H), 0.0), 32.0) * 0.28;
                vec3 lit = baseCol * (uAmbient + diff * (1.0 - uAmbient * 0.55)) + vec3(spec);
                vec3 finalCol = mix(lit, baseCol * 1.25, vEmissive);
                float dist = length(uCamPos - vWorldPos);
                float fog = 1.0 - exp(-pow(dist * uFogDensity, 1.6));
                finalCol = mix(finalCol, uFogColor, clamp(fog, 0.0, 1.0));
                gl_FragColor = vec4(finalCol, texCol.a);
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
            uniform sampler2D uRoadTex;
            uniform vec3 uCamPos;
            uniform vec3 uFogColor;
            uniform float uFogDensity;
            uniform vec3 uSunDir;
            uniform float uAmbient;
            uniform float uWetness;
            uniform vec3 uTruckPos;
            uniform vec3 uTruckFwd;
            uniform float uHeadlights;

            void main() {
                float u = vRoadUV.x;
                float s = vRoadUV.y;
                vec2 texUV = vec2(clamp(u * 0.5 + 0.5, 0.01, 0.99), s * 0.085);
                vec3 roadCol = texture2D(uRoadTex, texUV).rgb;

                vec3 N = normalize(vNorm);
                vec3 L = normalize(uSunDir);
                vec3 V = normalize(uCamPos - vWorldPos);
                vec3 H = normalize(L + V);
                float diff = max(dot(N, L), 0.0);
                float spec = pow(max(dot(N, H), 0.0), 36.0) * (0.20 + uWetness * 0.52);
                vec3 lit = roadCol * (uAmbient + diff * 0.78) + vec3(spec);

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
