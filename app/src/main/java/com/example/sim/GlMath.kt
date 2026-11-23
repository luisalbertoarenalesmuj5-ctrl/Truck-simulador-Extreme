package com.example.sim

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Interleaved vertex format: x, y, z, nx, ny, nz, r, g, b, emissive (10 floats = 40 bytes)
const val FLOATS_PER_VERTEX = 10
const val STRIDE_BYTES = FLOATS_PER_VERTEX * 4

class MeshBuilder {
    private val data = ArrayList<Float>(1024)

    fun addVertex(
        x: Float, y: Float, z: Float,
        nx: Float, ny: Float, nz: Float,
        r: Float, g: Float, b: Float,
        emissive: Float = 0f
    ) {
        data.add(x); data.add(y); data.add(z)
        data.add(nx); data.add(ny); data.add(nz)
        data.add(r); data.add(g); data.add(b)
        data.add(emissive)
    }

    fun addQuad(
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        r: Float, g: Float, b: Float,
        emissive: Float = 0f
    ) {
        val ux = x1 - x0; val uy = y1 - y0; val uz = z1 - z0
        val vx = x2 - x0; val vy = y2 - y0; val vz = z2 - z0
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
        nx /= len; ny /= len; nz /= len

        addVertex(x0, y0, z0, nx, ny, nz, r, g, b, emissive)
        addVertex(x1, y1, z1, nx, ny, nz, r, g, b, emissive)
        addVertex(x2, y2, z2, nx, ny, nz, r, g, b, emissive)

        addVertex(x0, y0, z0, nx, ny, nz, r, g, b, emissive)
        addVertex(x2, y2, z2, nx, ny, nz, r, g, b, emissive)
        addVertex(x3, y3, z3, nx, ny, nz, r, g, b, emissive)
    }

    fun addBox(
        cx: Float, cy: Float, cz: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float,
        emissive: Float = 0f
    ) {
        val hx = sx * 0.5f
        val hy = sy * 0.5f
        val hz = sz * 0.5f
        val x0 = cx - hx; val x1 = cx + hx
        val y0 = cy - hy; val y1 = cy + hy
        val z0 = cz - hz; val z1 = cz + hz

        // Front (-Z)
        addQuad(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, emissive)
        // Back (+Z)
        addQuad(x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, r * 0.92f, g * 0.92f, b * 0.92f, emissive)
        // Left (-X)
        addQuad(x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, r * 0.96f, g * 0.96f, b * 0.96f, emissive)
        // Right (+X)
        addQuad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r * 0.96f, g * 0.96f, b * 0.96f, emissive)
        // Top (+Y)
        addQuad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, (r * 1.05f).coerceAtMost(1f), (g * 1.05f).coerceAtMost(1f), (b * 1.05f).coerceAtMost(1f), emissive)
        // Bottom (-Y)
        addQuad(x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, r * 0.7f, g * 0.7f, b * 0.7f, emissive)
    }

    fun addCylinderX(
        cx: Float, cy: Float, cz: Float,
        radius: Float, width: Float, segments: Int,
        r: Float, g: Float, b: Float,
        rimR: Float = r, rimG: Float = g, rimB: Float = b
    ) {
        val hx = width * 0.5f
        for (i in 0 until segments) {
            val a0 = (i.toFloat() / segments) * 2f * PI.toFloat()
            val a1 = ((i + 1).toFloat() / segments) * 2f * PI.toFloat()
            val y0 = cy + cos(a0) * radius
            val z0 = cz + sin(a0) * radius
            val y1 = cy + cos(a1) * radius
            val z1 = cz + sin(a1) * radius
            // Tread
            addQuad(cx - hx, y0, z0, cx + hx, y0, z0, cx + hx, y1, z1, cx - hx, y1, z1, r, g, b, 0f)
            // Side caps
            val hubScale = 0.56f
            val hy0 = cy + cos(a0) * radius * hubScale
            val hz0 = cz + sin(a0) * radius * hubScale
            val hy1 = cy + cos(a1) * radius * hubScale
            val hz1 = cz + sin(a1) * radius * hubScale
            addQuad(cx - hx, y0, z0, cx - hx, y1, z1, cx - hx, hy1, hz1, cx - hx, hy0, hz0, r, g, b, 0f)
            addQuad(cx - hx, hy0, hz0, cx - hx, hy1, hz1, cx - hx, cy, cz, cx - hx, cy, cz, rimR, rimG, rimB, 0f)

            addQuad(cx + hx, y1, z1, cx + hx, y0, z0, cx + hx, hy0, hz0, cx + hx, hy1, hz1, r, g, b, 0f)
            addQuad(cx + hx, hy1, hz1, cx + hx, hy0, hz0, cx + hx, cy, cz, cx + hx, cy, cz, rimR, rimG, rimB, 0f)
        }
    }

    fun addCylinderY(
        cx: Float, cy: Float, cz: Float,
        radiusTop: Float, radiusBottom: Float, height: Float, segments: Int,
        r: Float, g: Float, b: Float
    ) {
        val y0 = cy
        val y1 = cy + height
        for (i in 0 until segments) {
            val a0 = (i.toFloat() / segments) * 2f * PI.toFloat()
            val a1 = ((i + 1).toFloat() / segments) * 2f * PI.toFloat()
            val bx0 = cx + cos(a0) * radiusBottom
            val bz0 = cz + sin(a0) * radiusBottom
            val bx1 = cx + cos(a1) * radiusBottom
            val bz1 = cz + sin(a1) * radiusBottom

            val tx0 = cx + cos(a0) * radiusTop
            val tz0 = cz + sin(a0) * radiusTop
            val tx1 = cx + cos(a1) * radiusTop
            val tz1 = cz + sin(a1) * radiusTop

            addQuad(bx0, y0, bz0, tx0, y1, tz0, tx1, y1, tz1, bx1, y0, bz1, r, g, b, 0f)
        }
    }

    fun addCylinderZ(
        cx: Float, cy: Float, cz: Float,
        radius: Float, length: Float, segments: Int,
        r: Float, g: Float, b: Float
    ) {
        val hz = length * 0.5f
        for (i in 0 until segments) {
            val a0 = (i.toFloat() / segments) * 2f * PI.toFloat()
            val a1 = ((i + 1).toFloat() / segments) * 2f * PI.toFloat()
            val x0 = cx + cos(a0) * radius
            val y0 = cy + sin(a0) * radius
            val x1 = cx + cos(a1) * radius
            val y1 = cy + sin(a1) * radius
            addQuad(x0, y0, cz - hz, x1, y1, cz - hz, x1, y1, cz + hz, x0, y0, cz + hz, r, g, b, 0f)
        }
    }

    fun build(): GlMesh {
        val arr = FloatArray(data.size)
        for (i in data.indices) arr[i] = data[i]
        val bb = ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        fb.put(arr)
        fb.position(0)
        return GlMesh(fb, data.size / FLOATS_PER_VERTEX)
    }
}

class GlMesh(
    val buffer: FloatBuffer,
    val vertexCount: Int
)
