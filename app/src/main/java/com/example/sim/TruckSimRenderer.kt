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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class TruckSimRenderer(
    private val physics: TruckPhysicsEngine,
    private val truck: TruckSpec
) : GLSurfaceView.Renderer {

    @Volatile
    var cameraMode: CameraMode = CameraMode.CHASE

    private val snapshot = RenderSnapshot()
    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

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
    private lateinit var trafficCarMesh: GlMesh
    private lateinit var trafficTruckMesh: GlMesh
    private lateinit var treeMesh: GlMesh
    private lateinit var gantryMesh: GlMesh
    private lateinit var guardrailPostMesh: GlMesh
    private lateinit var skyDomeBuffer: FloatBuffer
    private var skyVertexCount = 0

    // Dynamic road ribbon buffer (reused without heap allocations)
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

        // Allocate dynamic buffers for visible road window (~140 segments = ~840 vertices)
        roadBuffer = allocateFloatBuffer(150 * 6 * 8) // x,y,z, nx,ny,nz, u, s
        terrainBuffer = allocateFloatBuffer(150 * 12 * 10)
        railBuffer = allocateFloatBuffer(150 * 12 * 10)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.coerceAtLeast(1)
        Matrix.perspectiveM(projMatrix, 0, 56f, aspect, 0.2f, 520f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        physics.readRenderState(snapshot)
        updateCamera()
        rebuildRoadWindow()

        // 1. Draw Sky Dome (depth write off)
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        drawSkyDome()
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_CULL_FACE)

        // 2. Draw Terrain & Guardrails
        drawDynamicColorMesh(terrainBuffer, terrainVertexCount)
        drawDynamicColorMesh(railBuffer, railVertexCount)

        // 3. Draw Procedural Asphalt Road with real-time dual headlight cones
        drawRoadRibbon()

        // 4. Draw Roadside Scenery (Trees / Gantries / Guardrail Posts)
        drawSceneryProps()

        // 5. Draw AI Traffic
        drawTraffic()

        // 6. Draw Player Tractor + Articulated Trailer
        drawPlayerTruck()
    }

    private fun updateCamera() {
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
                // Driver seat position (left side of cab)
                targetCamX = tx - rightX * 0.52f + fwdX * 1.15f
                targetCamY = ty + 2.65f + snapshot.cabPitchOffset * 0.8f
                targetCamZ = tz - rightZ * 0.52f + fwdZ * 1.15f
                targetLookX = targetCamX + fwdX * 35f
                targetLookY = targetCamY - 0.8f + sin(snapshot.tractorPitch) * 10f
                targetLookZ = targetCamZ + fwdZ * 35f
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

            // Left & right terrain banks + distant ridges
            val leftEdge0x = p0.x - r0x * (hw + 65f); val leftEdge0z = p0.z - r0z * (hw + 65f)
            val leftEdge1x = p1.x - r1x * (hw + 65f); val leftEdge1z = p1.z - r1z * (hw + 65f)
            val ridgeH0L = if (env == EnvironmentTheme.COASTAL_SUNSET) -1.2f else 5.5f + 4.5f * sin(p0.s * 0.03f)
            val ridgeH1L = if (env == EnvironmentTheme.COASTAL_SUNSET) -1.2f else 5.5f + 4.5f * sin(p1.s * 0.03f)
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

            val rightEdge0x = p0.x + r0x * (hw + 65f); val rightEdge0z = p0.z + r0z * (hw + 65f)
            val rightEdge1x = p1.x + r1x * (hw + 65f); val rightEdge1z = p1.z + r1z * (hw + 65f)
            val ridgeH0R = 6.0f + 5.2f * cos(p0.s * 0.027f)
            val ridgeH1R = 6.0f + 5.2f * cos(p1.s * 0.027f)
            putColorQuad(
                terrainBuffer,
                rx0, p0.y - 0.06f, rz0,
                rx1, p1.y - 0.06f, rz1,
                rightEdge1x, p1.y + ridgeH1R, rightEdge1z,
                rightEdge0x, p0.y + ridgeH0R, rightEdge0z,
                terrainRgb[0] * 0.92f, terrainRgb[1] * 0.92f, terrainRgb[2] * 0.92f, 0f
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

            // Shoulder gravel lip
            val sx0 = p0.x + r0x * (hw + 1.4f); val sz0 = p0.z + r0z * (hw + 1.4f)
            val sx1 = p1.x + r1x * (hw + 1.4f); val sz1 = p1.z + r1z * (hw + 1.4f)
            putColorQuad(
                terrainBuffer,
                rx0, p0.y - 0.03f, rz0,
                rx1, p1.y - 0.03f, rz1,
                sx1, p1.y - 0.05f, sz1,
                sx0, p0.y - 0.05f, sz0,
                shoulderRgb[0], shoulderRgb[1], shoulderRgb[2], 0f
            )
            terrainVertexCount += 6
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

        // Pass tractor world position & forward vector for live dual headlight projection on the asphalt
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

        // 1. Trailer (always visible in Chase & mirrors; skip cab interior clipping in Cab mode)
        if (cameraMode != CameraMode.CAB) {
            setModelMatrix(snapshot.trailerX, snapshot.tractorY, snapshot.trailerZ, snapshot.trailerYaw, snapshot.tractorPitch, snapshot.cabRollOffset * 0.6f)
            drawMesh(trailerMesh, modelMatrix)
            drawTrailerWheels(snapshot.trailerX, snapshot.tractorY, snapshot.trailerZ, snapshot.trailerYaw)
            if (snapshot.braking) {
                drawMesh(brakeLightMesh, modelMatrix)
            }
        }

        // 2. Tractor Body
        if (cameraMode == CameraMode.CAB) {
            // Render realistic Cockpit Interior + rotating 3D steering wheel
            setModelMatrix(
                snapshot.tractorX,
                snapshot.tractorY,
                snapshot.tractorZ,
                snapshot.tractorYaw,
                snapshot.tractorPitch + snapshot.cabPitchOffset,
                snapshot.cabRollOffset
            )
            drawMesh(cockpitFrameMesh, modelMatrix)

            // Rotating steering wheel mounted in front of driver (-0.52f X, 2.08f Y, -1.62f Z)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, snapshot.tractorX, snapshot.tractorY, snapshot.tractorZ)
            Matrix.rotateM(modelMatrix, 0, -Math.toDegrees(snapshot.tractorYaw.toDouble()).toFloat(), 0f, 1f, 0f)
            Matrix.rotateM(modelMatrix, 0, Math.toDegrees((snapshot.tractorPitch + snapshot.cabPitchOffset).toDouble()).toFloat(), 1f, 0f, 0f)
            Matrix.translateM(modelMatrix, 0, -0.52f, 2.08f, -1.62f)
            Matrix.rotateM(modelMatrix, 0, -28f, 1f, 0f, 0f)
            Matrix.rotateM(modelMatrix, 0, -Math.toDegrees((snapshot.steerWheelAngleRad * 4.5f).toDouble()).toFloat(), 0f, 0f, 1f)
            drawMesh(steeringWheelMesh, modelMatrix)
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

        // 1. Player Tractor Cab (Euro Cabover vs American Long-Nose)
        cabMesh = MeshBuilder().apply {
            // Heavy steel chassis frame rails
            addBox(0f, 0.72f, 0.1f, 1.05f, 0.28f, 5.1f, 0.13f, 0.15f, 0.18f)
            // Chrome fuel tanks on left & right
            addBox(-0.88f, 0.72f, 0.2f, 0.52f, 0.52f, 1.6f, 0.75f, 0.78f, 0.82f)
            addBox(0.88f, 0.72f, 0.2f, 0.52f, 0.52f, 1.6f, 0.75f, 0.78f, 0.82f)
            // Fifth wheel hitch plate
            addBox(0f, 0.95f, 1.15f, 1.1f, 0.14f, 0.95f, 0.2f, 0.22f, 0.25f)
            // Front chrome bumper
            addBox(0f, 0.58f, -2.52f, 2.34f, 0.34f, 0.22f, 0.80f, 0.83f, 0.87f)

            if (truck.cabStyle == TruckCabStyle.EURO_CABOVER) {
                // Tall modern Euro Cabover body
                addBox(0f, 2.15f, -1.45f, 2.34f, 2.25f, 2.05f, p[0], p[1], p[2])
                // Aero roof fairing
                addBox(0f, 3.45f, -1.25f, 2.18f, 0.45f, 1.65f, p[0] * 0.95f, p[1] * 0.95f, p[2] * 0.95f)
                // Tinted wrap-around windshield
                addBox(0f, 2.55f, -2.49f, 2.08f, 0.95f, 0.06f, 0.08f, 0.14f, 0.20f)
                // Side windows
                addBox(-1.18f, 2.55f, -1.65f, 0.04f, 0.75f, 1.15f, 0.08f, 0.14f, 0.20f)
                addBox(1.18f, 2.55f, -1.65f, 0.04f, 0.75f, 1.15f, 0.08f, 0.14f, 0.20f)
                // Accent racing stripe & front grille
                addBox(0f, 1.45f, -2.49f, 1.65f, 0.85f, 0.06f, 0.16f, 0.18f, 0.22f)
                addBox(0f, 2.02f, -2.49f, 2.1f, 0.12f, 0.07f, a[0], a[1], a[2])
                // Side mirrors
                addBox(-1.34f, 2.45f, -2.25f, 0.14f, 0.48f, 0.18f, 0.15f, 0.16f, 0.18f)
                addBox(1.34f, 2.45f, -2.25f, 0.14f, 0.48f, 0.18f, 0.15f, 0.16f, 0.18f)
            } else {
                // American Conventional Long-Nose Hood + Sleeper Cab
                // Long nose hood
                addBox(0f, 1.48f, -1.85f, 1.85f, 1.10f, 1.75f, p[0], p[1], p[2])
                // Iconic chrome radiator grille
                addBox(0f, 1.45f, -2.74f, 1.45f, 1.02f, 0.08f, 0.86f, 0.89f, 0.92f)
                // Cab + Sleeper behind hood
                addBox(0f, 2.25f, -0.35f, 2.30f, 2.15f, 1.85f, p[0], p[1], p[2])
                addBox(0f, 2.55f, -1.29f, 2.02f, 0.72f, 0.06f, 0.08f, 0.14f, 0.20f)
                // Accent stripe along hood & sleeper
                addBox(-1.16f, 1.95f, -0.35f, 0.04f, 0.18f, 1.70f, a[0], a[1], a[2])
                addBox(1.16f, 1.95f, -0.35f, 0.04f, 0.18f, 1.70f, a[0], a[1], a[2])
                // Twin vertical chrome exhaust stacks!
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

        // 2. Freight Trailer (Box Van / Cistern / Heavy Equipment)
        trailerMesh = MeshBuilder().apply {
            // Under-chassis & tandem bogie
            addBox(0f, 0.92f, 0.4f, 1.8f, 0.26f, 8.6f, 0.16f, 0.18f, 0.21f)
            addBox(0f, 0.85f, 4.65f, 2.40f, 0.32f, 0.24f, 0.22f, 0.24f, 0.26f)
            if (physics.route.id == "desert_night") {
                // ADR Fuel Tanker Cylinder
                addCylinderZ(0f, 2.25f, 0.35f, 1.15f, 8.6f, 14, 0.82f, 0.84f, 0.88f)
                addBox(0f, 2.25f, 4.68f, 0.8f, 0.6f, 0.08f, 0.95f, 0.35f, 0.05f, 0.2f)
            } else {
                // Full 53ft Refrigerated / Freight Box Trailer
                addBox(0f, 2.42f, 0.35f, 2.46f, 2.65f, 8.9f, 0.88f, 0.90f, 0.93f)
                // Accent stripe on trailer flanks
                addBox(-1.24f, 2.35f, 0.35f, 0.04f, 0.42f, 8.5f, p[0], p[1], p[2])
                addBox(1.24f, 2.35f, 0.35f, 0.04f, 0.42f, 8.5f, p[0], p[1], p[2])
                // Rear roll-up doors & red marker lights
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

        // 4. Cockpit Interior Frame (for CAB view) + 3D Steering Wheel
        cockpitFrameMesh = MeshBuilder().apply {
            // Left & Right A-Pillars
            addBox(-1.12f, 2.65f, -2.15f, 0.12f, 1.25f, 0.14f, 0.12f, 0.14f, 0.17f)
            addBox(1.12f, 2.65f, -2.15f, 0.12f, 1.25f, 0.14f, 0.12f, 0.14f, 0.17f)
            // Roof header & sun visor
            addBox(0f, 3.25f, -2.15f, 2.34f, 0.18f, 0.22f, 0.10f, 0.12f, 0.15f)
            // Dashboard cowl
            addBox(0f, 1.98f, -1.92f, 2.28f, 0.42f, 0.52f, 0.14f, 0.16f, 0.19f)
            // Illuminated gauge cluster panel
            addBox(-0.52f, 2.12f, -1.78f, 0.56f, 0.18f, 0.10f, 0.08f, 0.12f, 0.16f, 0.3f)
            // Hood visible out front if American Conventional
            if (truck.cabStyle == TruckCabStyle.AMERICAN_CONVENTIONAL) {
                addBox(0f, 1.72f, -2.75f, 1.72f, 0.35f, 1.35f, p[0], p[1], p[2])
            }
            // Side mirrors visible from cab
            addBox(-1.32f, 2.48f, -2.20f, 0.16f, 0.48f, 0.10f, 0.22f, 0.24f, 0.28f)
            addBox(1.32f, 2.48f, -2.20f, 0.16f, 0.48f, 0.10f, 0.22f, 0.24f, 0.28f)
        }.build()

        steeringWheelMesh = MeshBuilder().apply {
            val rimR = 0.25f
            val segs = 16
            for (i in 0 until segs) {
                val a = (i.toFloat() / segs) * 2f * Math.PI.toFloat()
                val px = cos(a) * rimR
                val py = sin(a) * rimR
                addBox(px, py, 0f, 0.045f, 0.045f, 0.045f, 0.12f, 0.13f, 0.15f)
            }
            // Center hub & spokes
            addBox(0f, 0f, 0.01f, 0.14f, 0.12f, 0.05f, 0.22f, 0.24f, 0.28f)
            addBox(0f, -0.11f, 0.01f, 0.05f, 0.22f, 0.03f, 0.65f, 0.68f, 0.72f)
            addBox(-0.11f, 0f, 0.01f, 0.22f, 0.04f, 0.03f, 0.65f, 0.68f, 0.72f)
            addBox(0.11f, 0f, 0.01f, 0.22f, 0.04f, 0.03f, 0.65f, 0.68f, 0.72f)
        }.build()

        // 5. AI Traffic Meshes
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

        // 6. Scenery Props (Trees/Rocks, Gantries, Guardrail Posts)
        treeMesh = MeshBuilder().apply {
            if (env == EnvironmentTheme.DESERT_NIGHT) {
                // Desert Canyon Pillar / Saguaro
                addCylinderY(0f, 0f, 0f, 0.45f, 0.95f, 4.2f, 6, 0.35f, 0.22f, 0.16f)
            } else {
                // Alpine Pine Tree
                addCylinderY(0f, 0f, 0f, 0.22f, 0.28f, 2.4f, 6, 0.32f, 0.22f, 0.14f)
                addCylinderY(0f, 2.0f, 0f, 0.15f, 1.75f, 3.5f, 7, 0.10f, 0.30f, 0.14f)
                addCylinderY(0f, 4.2f, 0f, 0.05f, 1.25f, 3.0f, 7, 0.12f, 0.36f, 0.16f)
            }
        }.build()

        guardrailPostMesh = MeshBuilder().apply {
            addBox(0f, 0.45f, 0f, 0.14f, 0.90f, 0.14f, 0.45f, 0.48f, 0.52f)
        }.build()

        gantryMesh = MeshBuilder().apply {
            val w = physics.roadHalfWidth + 0.8f
            addBox(-w, 3.2f, 0f, 0.32f, 6.4f, 0.32f, 0.55f, 0.58f, 0.62f)
            addBox(w, 3.2f, 0f, 0.32f, 6.4f, 0.32f, 0.55f, 0.58f, 0.62f)
            addBox(0f, 6.2f, 0f, w * 2f + 0.4f, 0.36f, 0.36f, 0.55f, 0.58f, 0.62f)
            // Green/Blue highway sign board
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
        val len = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.001f)
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
