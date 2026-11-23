package com.example.sim

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.opengl.GLES20
import android.opengl.GLUtils
import androidx.annotation.DrawableRes
import com.example.R
import com.example.model.EnvironmentTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class RealisticTextureSet(
    val whiteTex: Int,
    val skyPanoTex: Int,
    val asphaltTex: Int,
    val terrainTex: Int,
    val cabMetalTex: Int,
    val trailerTex: Int,
    val wheelTex: Int,
    val treeTex: Int,
    val driverWalkTex: Int,
    val shadowTex: Int
)

object RealisticTextureFactory {

    fun buildAll(context: Context, env: EnvironmentTheme): RealisticTextureSet {
        val skyRes = when (env) {
            EnvironmentTheme.ALPINE_DAY -> R.drawable.img_real_highway_alpine
            EnvironmentTheme.DESERT_NIGHT -> R.drawable.img_real_highway_night
            EnvironmentTheme.COASTAL_SUNSET -> R.drawable.img_truck_hero
            EnvironmentTheme.STORM_SUMMIT -> R.drawable.img_real_highway_alpine
        }

        return RealisticTextureSet(
            whiteTex = createSolidWhiteTexture(),
            skyPanoTex = loadDrawableTexture(context, skyRes, repeatS = true, clampT = true, tintStorm = env == EnvironmentTheme.STORM_SUMMIT),
            asphaltTex = createAsphaltTexture(env),
            terrainTex = createTerrainTexture(env),
            cabMetalTex = createCabMetalTexture(),
            trailerTex = createTrailerTexture(),
            wheelTex = createWheelTexture(),
            treeTex = createTreeSpriteTexture(env),
            driverWalkTex = loadDrawableTexture(context, R.drawable.img_real_driver_walk, repeatS = false, clampT = true, tintStorm = false),
            shadowTex = createSoftShadowTexture()
        )
    }

    private fun createSolidWhiteTexture(): Int {
        val bmp = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        return uploadBitmap(bmp, repeatS = true, clampT = false)
    }

    private fun loadDrawableTexture(
        context: Context,
        @DrawableRes resId: Int,
        repeatS: Boolean,
        clampT: Boolean,
        tintStorm: Boolean
    ): Int {
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = 1
        }
        val raw = BitmapFactory.decodeResource(context.resources, resId, opts)
            ?: return createSolidWhiteTexture()

        val bmp = if (tintStorm) {
            val copy = raw.copy(Bitmap.Config.ARGB_8888, true)
            raw.recycle()
            val c = Canvas(copy)
            c.drawColor(Color.argb(95, 58, 72, 86))
            copy
        } else {
            raw
        }
        return uploadBitmap(bmp, repeatS = repeatS, clampT = clampT, mirrorS = repeatS)
    }

    private fun createAsphaltTexture(env: EnvironmentTheme): Int {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val rng = Random(42)
        val pixels = IntArray(size * size)
        val baseDark = if (env == EnvironmentTheme.STORM_SUMMIT) 36 else 44

        for (y in 0 until size) {
            val u = y.toFloat() / size // Note: mapped carefully in road UVs
            for (x in 0 until size) {
                val uNorm = x.toFloat() / size // across road -1..1 mapped to 0..1
                // Tire wear tracks around lane centers (0.28 and 0.72)
                val distL = kotlin.math.abs(uNorm - 0.30f)
                val distR = kotlin.math.abs(uNorm - 0.70f)
                val tireTrack = ((1f - (distL / 0.14f).coerceIn(0f, 1f)) +
                    (1f - (distR / 0.14f).coerceIn(0f, 1f))) * 9f
                val pebble = rng.nextInt(-10, 12)
                val lum = (baseDark - tireTrack.toInt() + pebble).coerceIn(20, 82)
                pixels[y * size + x] = Color.rgb(lum, lum + 1, lum + 3)
            }
        }
        bmp.setPixels(pixels, 0, size, 0, 0, size, size)

        val canvas = Canvas(bmp)
        // Subtle asphalt tar seam lines
        val crackPaint = Paint().apply {
            color = Color.argb(55, 18, 20, 22)
            strokeWidth = 2.5f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(40f, 120f, 470f, 134f, crackPaint)
        canvas.drawLine(90f, 360f, 440f, 348f, crackPaint)

        // Crisp weathered outer shoulder white edge lines
        val whiteLine = Paint().apply { color = Color.rgb(232, 234, 236) }
        canvas.drawRect(36f, 0f, 50f, size.toFloat(), whiteLine)
        canvas.drawRect(size - 50f, 0f, size - 36f, size.toFloat(), whiteLine)

        // Dashed yellow highway center line
        val yellowLine = Paint().apply { color = Color.rgb(238, 178, 32) }
        canvas.drawRect(size * 0.5f - 6f, 32f, size * 0.5f + 6f, 210f, yellowLine)
        canvas.drawRect(size * 0.5f - 6f, 288f, size * 0.5f + 6f, 466f, yellowLine)

        return uploadBitmap(bmp, repeatS = true, clampT = false)
    }

    private fun createTerrainTexture(env: EnvironmentTheme): Int {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val rng = Random(107)
        val pixels = IntArray(size * size)
        val (br, bg, bb) = when (env) {
            EnvironmentTheme.ALPINE_DAY -> Triple(64, 96, 52)
            EnvironmentTheme.COASTAL_SUNSET -> Triple(92, 74, 54)
            EnvironmentTheme.DESERT_NIGHT -> Triple(54, 40, 32)
            EnvironmentTheme.STORM_SUMMIT -> Triple(58, 74, 62)
        }

        for (y in 0 until size) {
            for (x in 0 until size) {
                val n1 = sin(x * 0.08f) * cos(y * 0.08f) * 10f
                val n2 = rng.nextInt(-12, 13)
                val r = (br + n1.toInt() + n2).coerceIn(18, 200)
                val g = (bg + n1.toInt() + n2).coerceIn(18, 200)
                val b = (bb + (n1 * 0.6f).toInt() + n2 / 2).coerceIn(14, 200)
                pixels[y * size + x] = Color.rgb(r, g, b)
            }
        }
        bmp.setPixels(pixels, 0, size, 0, 0, size, size)
        return uploadBitmap(bmp, repeatS = true, clampT = false)
    }

    private fun createCabMetalTexture(): Int {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val grad = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, size.toFloat(),
                intArrayOf(Color.rgb(252, 252, 255), Color.rgb(228, 232, 238), Color.rgb(198, 204, 212)),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), grad)

        // Subtle automotive panel door seam & vents
        val seamPaint = Paint().apply {
            color = Color.argb(65, 30, 35, 42)
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        canvas.drawRect(6f, 6f, size - 6f, size - 6f, seamPaint)
        return uploadBitmap(bmp, repeatS = true, clampT = false)
    }

    private fun createTrailerTexture(): Int {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(236, 240, 244))

        // Vertical corrugated aluminum trailer ribs & rivets
        val ribLight = Paint().apply { color = Color.rgb(246, 248, 252); strokeWidth = 3f }
        val ribDark = Paint().apply { color = Color.rgb(206, 212, 220); strokeWidth = 2f }
        for (x in 0 until size step 24) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), size.toFloat(), ribLight)
            canvas.drawLine(x + 3f, 0f, x + 3f, size.toFloat(), ribDark)
        }

        // Bottom DOT reflective red/white conspicuity strip
        for (x in 0 until size step 48) {
            val dotPaint = Paint().apply {
                color = if ((x / 48) % 2 == 0) Color.rgb(218, 36, 36) else Color.rgb(245, 248, 252)
            }
            canvas.drawRect(x.toFloat(), size - 32f, x + 48f, size - 16f, dotPaint)
        }

        // Subtle framing border
        val borderPaint = Paint().apply {
            color = Color.rgb(165, 174, 184)
            strokeWidth = 8f
            style = Paint.Style.STROKE
        }
        canvas.drawRect(4f, 4f, size - 4f, size - 4f, borderPaint)
        return uploadBitmap(bmp, repeatS = true, clampT = false)
    }

    private fun createWheelTexture(): Int {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = size * 0.5f
        val cy = size * 0.5f

        // Dark rubber tire base
        canvas.drawColor(Color.rgb(34, 37, 42))

        // Sidewall ring
        val sidewallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(46, 50, 56)
            style = Paint.Style.STROKE
            strokeWidth = 24f
        }
        canvas.drawCircle(cx, cy, size * 0.37f, sidewallPaint)

        // Polished steel 10-lug semi wheel rim
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, size * 0.28f,
                intArrayOf(Color.rgb(230, 236, 242), Color.rgb(168, 176, 186), Color.rgb(112, 120, 130)),
                floatArrayOf(0f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, size * 0.28f, rimPaint)

        // Lug nuts & cooling slots
        val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(28, 32, 38) }
        val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(245, 248, 252) }
        for (i in 0 until 10) {
            val a = (i / 10f) * 2f * PI.toFloat()
            val hx = cx + cos(a) * size * 0.20f
            val hy = cy + sin(a) * size * 0.20f
            canvas.drawCircle(hx, hy, 7f, holePaint)

            val bx = cx + cos(a + 0.31f) * size * 0.12f
            val by = cy + sin(a + 0.31f) * size * 0.12f
            canvas.drawCircle(bx, by, 4f, boltPaint)
        }
        canvas.drawCircle(cx, cy, size * 0.07f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(72, 78, 86) })

        return uploadBitmap(bmp, repeatS = false, clampT = true)
    }

    private fun createTreeSpriteTexture(env: EnvironmentTheme): Int {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bmp)
        val cx = size * 0.5f

        if (env == EnvironmentTheme.DESERT_NIGHT) {
            // Photorealistic-shaded Desert Saguaro / Canyon Rock Pillar
            val rockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, size.toFloat(), size.toFloat(),
                    intArrayOf(Color.rgb(112, 78, 56), Color.rgb(74, 48, 34)),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            val path = Path().apply {
                moveTo(cx - 56f, size - 4f)
                lineTo(cx - 38f, 92f)
                lineTo(cx - 8f, 34f)
                lineTo(cx + 26f, 64f)
                lineTo(cx + 58f, size - 4f)
                close()
            }
            canvas.drawPath(path, rockPaint)
        } else {
            // Layered evergreen alpine pine silhouette with needle texture
            val trunkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(72, 48, 32) }
            canvas.drawRect(cx - 10f, size * 0.72f, cx + 10f, size.toFloat(), trunkPaint)

            val rng = Random(77)
            val layers = 5
            for (l in 0 until layers) {
                val topY = 18f + l * 34f
                val botY = topY + 68f
                val halfW = 24f + l * 18f
                val dark = 28 + l * 6
                val green = 74 + (layers - l) * 11
                val branchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        cx - halfW, topY, cx + halfW, botY,
                        Color.rgb(dark + 12, green + 18, dark + 8),
                        Color.rgb(dark - 6, green - 14, dark - 4),
                        Shader.TileMode.CLAMP
                    )
                }
                val p = Path().apply {
                    moveTo(cx, topY)
                    lineTo(cx + halfW, botY)
                    lineTo(cx + halfW * 0.55f, botY - 8f)
                    lineTo(cx, botY + 4f)
                    lineTo(cx - halfW * 0.55f, botY - 8f)
                    lineTo(cx - halfW, botY)
                    close()
                }
                canvas.drawPath(p, branchPaint)

                // Pine needle highlights
                val highlight = Paint().apply { color = Color.argb(95, 110, 168, 92); strokeWidth = 2f }
                repeat(12) {
                    val rx = cx + rng.nextFloat() * halfW * 1.3f - halfW * 0.65f
                    val ry = topY + 14f + rng.nextFloat() * 44f
                    canvas.drawLine(rx, ry, rx + rng.nextInt(-8, 9), ry + 7f, highlight)
                }
            }
        }
        return uploadBitmap(bmp, repeatS = false, clampT = true)
    }

    private fun createSoftShadowTexture(): Int {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                size * 0.5f, size * 0.5f, size * 0.48f,
                intArrayOf(Color.argb(175, 8, 10, 14), Color.argb(85, 8, 10, 14), Color.TRANSPARENT),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(size * 0.5f, size * 0.5f, size * 0.48f, p)
        return uploadBitmap(bmp, repeatS = false, clampT = true)
    }

    private fun uploadBitmap(
        bmp: Bitmap,
        repeatS: Boolean,
        clampT: Boolean,
        mirrorS: Boolean = false
    ): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val texId = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            when {
                mirrorS -> GLES20.GL_MIRRORED_REPEAT
                repeatS -> GLES20.GL_REPEAT
                else -> GLES20.GL_CLAMP_TO_EDGE
            }
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            if (clampT) GLES20.GL_CLAMP_TO_EDGE else GLES20.GL_REPEAT
        )
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        bmp.recycle()
        return texId
    }
}
