package com.example.lbmenu

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Shared animated backdrop: LiquidBounce's background.png painted cover-fit, plus an
 * optional CPU port of backgrounds/background.frag (aurora + mountain silhouettes)
 * baked into a DynamicTexture and stretched 1:1 onto the viewport.
 */
object Backdrop {
    private val AURORA_ID: Identifier = Identifier.fromNamespaceAndPath("lbmenu", "dynamic/aurora")

    private const val CACHE_W = 320
    private const val CACHE_H = 180

    private var texture: DynamicTexture? = null
    private var frame: IntArray? = null
    private var uvX2: FloatArray? = null
    private var uvY2: FloatArray? = null
    private var aspect = -1.0

    /** Whether the shader-style aurora is painted instead of the flat PNG. */
    @Volatile
    var shaderEnabled: Boolean = true

    fun drawBackground(g: GuiGraphicsExtractor, w: Int, h: Int) {
        if (shaderEnabled) drawAurora(g, w, h) else drawImage(g, w, h)
    }

    fun drawImage(g: GuiGraphicsExtractor, w: Int, h: Int) {
        D.imageCover(g, Tex.BACKGROUND, w.toFloat(), h.toFloat(), 2560, 1440)
    }

    private fun drawAurora(g: GuiGraphicsExtractor, w: Int, h: Int) {
        val tex = ensureTexture()
        val img = tex.pixels
        val buf = frame ?: IntArray(CACHE_W * CACHE_H).also { frame = it }
        val ux = ensureColumns(w, h)
        val uy = ensureRows()

        val t = (System.currentTimeMillis() % 3_600_000L) / 1000.0
        val layerCount = 4
        val speeds = doubleArrayOf(0.05, 0.1, 0.15, 0.07)
        val intensities = doubleArrayOf(0.3, 0.4, 0.3, 0.2)
        val colors = doubleArrayOf(
            0.0, 1.0, 0.3,
            0.1, 0.5, 0.9,
            0.4, 0.1, 0.8,
            0.8, 0.1, 0.6
        )

        var idx = 0
        for (row in 0 until CACHE_H) {
            val v = uy[row].toDouble()
            val skyGradient = 1.0 - smoothstep(0.0, 0.5, v)
            val baseR = 0.15 + 0.20 * skyGradient
            val baseG = 0.20 + 0.0 * skyGradient
            val baseB = 0.35 + 0.40 * skyGradient
            for (col in 0 until CACHE_W) {
                val u = ux[col].toDouble()
                var r = baseR
                var gg = baseG
                var b = baseB
                for (k in 0 until layerCount) {
                    val tt = t * speeds[k]
                    val px = u * 2.0 + tt * 2.0
                    val py = v * 2.0 - tt * 2.0
                    val c = k * 3
                    val n = noise(px + noise(colors[c] + px + tt, colors[c + 1] + py + tt), py)
                    val aurora = n - v * 0.6
                    if (aurora > 0.0) {
                        val a = aurora * intensities[k] * 0.5
                        r += a * colors[c]
                        gg += a * colors[c + 1]
                        b += a * colors[c + 2]
                    }
                }
                // 5 mountain silhouettes, matching the GLSL loop
                for (i in 0 until 5) {
                    val layer = 5 - i
                    val hgt = layer * 0.1 * (1.0 - smoothstep(0.0, 1.0,
                        mountainRange(
                            t * 0.03 * (i + 1.0) + i * 4.0 + u * (1.0 + layer * 0.05),
                            v * 0.23
                        )
                    ))
                    if (hgt - v >= 0.0) {
                        val m = layer / 4.0
                        r = 0.15 * m
                        gg = 0.20 * m
                        b = 0.35 * m
                    }
                }
                buf[idx++] = ARGB.color(
                    255,
                    (r.coerceIn(0.0, 1.0) * 255.0).toInt(),
                    (gg.coerceIn(0.0, 1.0) * 255.0).toInt(),
                    (b.coerceIn(0.0, 1.0) * 255.0).toInt()
                )
            }
        }

        for (row in 0 until CACHE_H) {
            for (col in 0 until CACHE_W) {
                img.setPixel(col, row, buf[row * CACHE_W + col])
            }
        }
        tex.upload()

        g.blit(
            RenderPipelines.GUI_TEXTURED, AURORA_ID,
            0, 0, 0f, 0f,
            max(1, w), max(1, h),
            CACHE_W, CACHE_H,
            CACHE_W, CACHE_H
        )
    }

    private fun ensureTexture(): DynamicTexture {
        texture?.let { return it }
        val created = DynamicTexture({ "lbmenu_aurora" }, CACHE_W, CACHE_H, false)
        Minecraft.getInstance().textureManager.register(AURORA_ID, created)
        texture = created
        return created
    }

    private fun ensureColumns(w: Int, h: Int): FloatArray {
        val a = if (h > 0) w.toDouble() / h else CACHE_W.toDouble() / CACHE_H
        if (aspect == a) uvX2?.let { return it }
        aspect = a
        val arr = FloatArray(CACHE_W)
        for (c in 0 until CACHE_W) {
            arr[c] = (((c + 0.5) / CACHE_W) * a * 2.0).toFloat()
        }
        uvX2 = arr
        return arr
    }

    private fun ensureRows(): FloatArray {
        uvY2?.let { return it }
        val arr = FloatArray(CACHE_H)
        for (row in 0 until CACHE_H) {
            val v = ((CACHE_H - 1 - row) + 0.5) / CACHE_H
            arr[row] = (v * 2.0).toFloat()
        }
        uvY2 = arr
        return arr
    }

    private fun hash(n: Double): Double {
        val s = sin(n) * 43758.5453
        return s - floor(s)
    }

    private fun noise(x: Double, y: Double): Double {
        val ix = floor(x)
        val iy = floor(y)
        val fx = x - ix
        val fy = y - iy
        val u = fx * fx * (3.0 - 2.0 * fx)
        val v = fy * fy * (3.0 - 2.0 * fy)
        val a = hash(ix + hash(iy))
        val b = hash(ix + 1.0 + hash(iy))
        val c = hash(ix + hash(iy + 1.0))
        val d = hash(ix + 1.0 + hash(iy + 1.0))
        val top = a + (b - a) * u
        val bottom = c + (d - c) * u
        return top + (bottom - top) * v
    }

    private fun mountainRange(x: Double, y: Double): Double {
        var height = 0.0
        var frequency = 2.0
        var amplitude = 0.5
        for (i in 0 until 5) {
            height += noise(x * frequency, y * frequency) * amplitude
            frequency *= 2.0
            amplitude *= 0.5
        }
        return height
    }

    private fun smoothstep(e0: Double, e1: Double, x: Double): Double {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }
}

/** Svelte `fly` transitions, reimplemented as a time-based offset/alpha pair. */
class Fly(
    private val durationMs: Long,
    private val dx: Float = 0f,
    private val dy: Float = 0f,
    private val delayMs: Long = 0L,
    private val easing: (Float) -> Float = Ease::cubicOut
) {
    private val start = System.currentTimeMillis()

    val progress: Float
        get() {
            val elapsed = System.currentTimeMillis() - start - delayMs
            if (elapsed <= 0L) return 0f
            if (elapsed >= durationMs) return 1f
            return easing(elapsed.toFloat() / durationMs)
        }

    val offsetX: Float get() = dx * (1f - progress)
    val offsetY: Float get() = dy * (1f - progress)
    val alpha: Float get() = progress
    val done: Boolean get() = progress >= 1f
}

/** Notification feed shared between menus (notification_store.ts equivalent). */
data class Notif(val title: String, val message: String, val error: Boolean, val delayMs: Long)

object NotifStore {
    private class Entry(val n: Notif, val id: Long, val shownAt: Long = System.currentTimeMillis())

    private val entries = CopyOnWriteArrayList<Entry>()
    private var seq = 0L

    fun push(title: String, message: String, error: Boolean = false, delayMs: Long = 3000L) {
        seq++
        entries.add(Entry(Notif(title, message, error, delayMs), seq))
        if (entries.size > 4) entries.removeAt(0)
    }

    /** live entries, dropping the expired ones */
    fun active(): List<Pair<Notif, Long>> {
        val now = System.currentTimeMillis()
        entries.removeAll { now - it.shownAt > it.n.delayMs }
        return entries.map { it.n to (now - it.shownAt) }
    }
}