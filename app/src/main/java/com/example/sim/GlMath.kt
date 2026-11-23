package com.example.sim

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Interleaved vertex format: x, y, z, nx, ny, nz, r, g, b, emissive, u, v (12 floats = 48 bytes)
const val FLOATS_PER_VERTEX = 12
const val STRIDE_BYTES = FLOATS_PER_VERTEX * 4

class MeshBuilder {
    private val data = ArrayList<Float>(1024)

    fun addVertex(
        x: Float, y: Float, z: Float,
        nx: Float, ny: Float, nz: Float,
        r: Float, g: Float, b: Float,
        emissive: Float = 0f,
        u: Float = 0.5f,
        v: Float = 0.5f
    ) {
        data.add(x); data.add(y); data.add(z)
        data.add(nx); data.add(ny); data.add(nz)
        data.add(r); data.add(g); data.add(b)
        data.add(emissive)
        data.add(u); data.add(v)
    }

    fun addQuad(
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        r: Float, g: Float, b: Float,
        emissive: Float = 0f,
        u0: Float = 0f, v0: Float = 1f,
        u1: Float = 0f, v1: Float = 0f,
        u2: Float = 1f, v2: Float = 0f,
        u3: Float = 1f, v3: Float = 1f
    ) {
        val ux = x1 - x0; val uy = y1 - y0; val uz = z1 - z0
        val vx = x2 - x0; val vy = y2 - y0; val vz = z2 - z0
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
        nx /= len; ny /= len; nz /= len

        addVertex(x0, y0, z0, nx, ny, nz, r, g, b, emissive, u0, v0)
        addVertex(x1, y1, z1, nx, ny, nz, r, g, b, emissive, u1, v1)
        addVertex(x2, y2, z2, nx, ny, nz, r, g, b, emissive, u2, v2)

        addVertex(x0, y0, z0, nx, ny, nz, r, g, b, emissive, u0, v0)
        addVertex(x2, y2, z2, nx, ny, nz, r, g, b, emissive, u2, v2)
        addVertex(x3, y3, z3, nx, ny, nz, r, g, b, emissive, u3, v3)
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
            val c0 = cos(a0); val s0 = sin(a0)
            val c1 = cos(a1); val s1 = sin(a1)
            val y0 = cy + c0 * radius
            val z0 = cz + s0 * radius
            val y1 = cy + c1 * radius
            val z1 = cz + s1 * radius

            // Tire tread (dark outer band UV near edge 0.05..0.12)
            addQuad(
                cx - hx, y0, z0, cx + hx, y0, z0, cx + hx, y1, z1, cx - hx, y1, z1,
                r, g, b, 0f,
                0.04f, 0.04f, 0.08f, 0.04f, 0.08f, 0.08f, 0.04f, 0.08f
            )

            // Sidewall & rim radial UVs mapped directly onto wheelTex circle (0..1)
            val uOut0 = 0.5f + c0 * 0.48f; val vOut0 = 0.5f + s0 * 0.48f
            val uOut1 = 0.5f + c1 * 0.48f; val vOut1 = 0.5f + s1 * 0.48f
            val hubScale = 0.56f
            val hy0 = cy + c0 * radius * hubScale
            val hz0 = cz + s0 * radius * hubScale
            val hy1 = cy + c1 * radius * hubScale
            val hz1 = cz + s1 * radius * hubScale
            val uHub0 = 0.5f + c0 * 0.48f * hubScale; val vHub0 = 0.5f + s0 * 0.48f * hubScale
            val uHub1 = 0.5f + c1 * 0.48f * hubScale; val vHub1 = 0.5f + s1 * 0.48f * hubScale

            addQuad(
                cx - hx, y0, z0, cx - hx, y1, z1, cx - hx, hy1, hz1, cx - hx, hy0, hz0,
                r, g, b, 0f,
                uOut0, vOut0, uOut1, vOut1, uHub1, vHub1, uHub0, vHub0
            )
            addQuad(
                cx - hx, hy0, hz0, cx - hx, hy1, hz1, cx - hx, cy, cz, cx - hx, cy, cz,
                rimR, rimG, rimB, 0f,
                uHub0, vHub0, uHub1, vHub1, 0.5f, 0.5f, 0.5f, 0.5f
            )

            addQuad(
                cx + hx, y1, z1, cx + hx, y0, z0, cx + hx, hy0, hz0, cx + hx, hy1, hz1,
                r, g, b, 0f,
                uOut1, vOut1, uOut0, vOut0, uHub0, vHub0, uHub1, vHub1
            )
            addQuad(
                cx + hx, hy1, hz1, cx + hx, hy0, hz0, cx + hx, cy, cz, cx + hx, cy, cz,
                rimR, rimG, rimB, 0f,
                uHub1, vHub1, uHub0, vHub0, 0.5f, 0.5f, 0.5f, 0.5f
            )
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

            val u0 = i.toFloat() / segments
            val u1 = (i + 1).toFloat() / segments
            addQuad(bx0, y0, bz0, tx0, y1, tz0, tx1, y1, tz1, bx1, y0, bz1, r, g, b, 0f, u0, 1f, u0, 0f, u1, 0f, u1, 1f)
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
            val u0 = i.toFloat() / segments
            val u1 = (i + 1).toFloat() / segments
            addQuad(x0, y0, cz - hz, x1, y1, cz - hz, x1, y1, cz + hz, x0, y0, cz + hz, r, g, b, 0f, u0, 0f, u1, 0f, u1, 1f, u0, 1f)
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
