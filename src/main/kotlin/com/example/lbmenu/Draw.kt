package com.example.lbmenu

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Native draw primitives. Everything is GPU batched through GuiGraphicsExtractor;
 * rounded corners are per-scanline spans with fractional edge coverage so they
 * anti-alias through normal alpha blending.
 */
object D {

    fun rect(g: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, color: Int) {
        if (w <= 0f || h <= 0f || ARGB.alpha(color) == 0) return
        g.fill(x.roundToInt(), y.roundToInt(), (x + w).roundToInt(), (y + h).roundToInt(), color)
    }

    /** Solid rounded rectangle, radius in px, uniform on all corners. */
    fun rrect(g: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int) {
        if (w <= 0f || h <= 0f) return
        if (ARGB.alpha(color) == 0) return
        val r = min(radius, min(w, h) / 2f)
        if (r <= 0.01f) {
            rect(g, x, y, w, h, color)
            return
        }
        val alpha = ARGB.alpha(color)
        val rgb = color and 0x00FFFFFF
        val rows = ceil(h).toInt()
        for (i in 0 until rows) {
            val rowTop = y + i
            val rowBottom = min(rowTop + 1f, y + h)
            val cy = rowTop + 0.5f
            val dyTop = cy - (y + r)
            val dyBottom = cy - (y + h - r)
            val dy = when {
                dyTop < 0f -> -dyTop
                dyBottom > 0f -> dyBottom
                else -> 0f
            }
            val inset = if (dy > 0f) r - sqrt(max(0f, r * r - dy * dy)) else 0f
            val left = x + inset
            val right = x + w - inset
            if (right - left <= 0.01f) continue
            val y0 = rowTop.roundToInt()
            val y1 = max(y0 + 1, rowBottom.roundToInt())
            // horizontal fractional edges -> alpha coverage
            val lx = floor(left).toInt()
            val rx = ceil(right).toInt()
            val lCov = (1f - (left - lx)).coerceIn(0f, 1f)
            val rCov = (right - (rx - 1)).coerceIn(0f, 1f)
            if (lx == rx - 1) {
                g.fill(lx, y0, rx, y1, withAlpha(rgb, alpha * ((lCov + rCov) / 2f)))
            } else {
                g.fill(lx, y0, lx + 1, y1, withAlpha(rgb, alpha * lCov))
                g.fill(rx - 1, y0, rx, y1, withAlpha(rgb, alpha * rCov))
                if (rx - 1 > lx + 1) g.fill(lx + 1, y0, rx - 1, y1, withAlpha(rgb, alpha.toFloat()))
            }
        }
    }

    fun circle(g: GuiGraphicsExtractor, cx: Float, cy: Float, r: Float, color: Int) {
        rrect(g, cx - r, cy - r, r * 2f, r * 2f, r, color)
    }

    fun outlineRect(g: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, thickness: Float, color: Int) {
        rect(g, x, y, w, thickness, color)
        rect(g, x, y + h - thickness, w, thickness, color)
        rect(g, x, y + thickness, thickness, h - thickness * 2, color)
        rect(g, x + w - thickness, y + thickness, thickness, h - thickness * 2, color)
    }

    fun withAlpha(rgb: Int, alpha: Float): Int =
        ARGB.color(alpha.roundToInt().coerceIn(0, 255), rgb and 0x00FFFFFF)

    /** linear-gradient(to left, a 50%, b 50%) with animated background-position (0 = right, 1 = left) */
    fun splitGradient(
        g: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Float, a: Int, b: Int, pos: Float
    ) {
        if (w <= 0f || h <= 0f) return
        // background-size: 200% 100%, position right -> left
        val fullW = w * 2f
        val offset = (1f - pos.coerceIn(0f, 1f)) * w
        val seam = x + w - offset
        val leftEdge = x - offset
        // visible window [x, x+w] maps to gradient-local [offset, offset+w]
        val splitLocal = w // 50% of fullW
        val splitScreen = leftEdge + splitLocal
        if (splitScreen <= x) {
            rect(g, x, y, w, h, a)
        } else if (splitScreen >= x + w) {
            rect(g, x, y, w, h, b)
        } else {
            rect(g, x, y, splitScreen - x, h, b)
            rect(g, splitScreen, y, x + w - splitScreen, h, a)
        }
        if (seam.isNaN()) return
    }

    fun icon(g: GuiGraphicsExtractor, tex: Identifier, x: Float, y: Float, w: Float, h: Float, tint: Int = -1) {
        if (w <= 0f || h <= 0f) return
        g.blit(
            RenderPipelines.GUI_TEXTURED, tex,
            x.roundToInt(), y.roundToInt(), 0f, 0f,
            max(1, w.roundToInt()), max(1, h.roundToInt()),
            128, 128, 128, 128, tint
        )
    }

    /** Draws an icon scaled so its own aspect ratio is preserved inside a square box. */
    fun iconFit(
        g: GuiGraphicsExtractor, tex: Identifier, box: Float, x: Float, y: Float,
        srcW: Float, srcH: Float, tint: Int = -1
    ) {
        val s = min(box / srcW, box / srcH)
        val w = srcW * s
        val h = srcH * s
        icon(g, tex, x + (box - w) / 2f, y + (box - h) / 2f, w, h, tint)
    }

    fun image(g: GuiGraphicsExtractor, tex: Identifier, x: Float, y: Float, w: Float, h: Float, texW: Int, texH: Int) {
        g.blit(
            RenderPipelines.GUI_TEXTURED, tex,
            x.roundToInt(), y.roundToInt(), 0f, 0f,
            max(1, w.roundToInt()), max(1, h.roundToInt()),
            texW, texH, texW, texH
        )
    }

    /** cover-fit image blit (crop excess, never stretch unevenly) */
    fun imageCover(
        g: GuiGraphicsExtractor, tex: Identifier, screenW: Float, screenH: Float, texW: Int, texH: Int
    ) {
        val screenAspect = screenW / screenH.coerceAtLeast(1f)
        val texAspect = texW.toFloat() / texH
        var srcW = texW
        var srcH = texH
        var srcU = 0f
        var srcV = 0f
        if (screenAspect > texAspect) {
            srcH = (texW / screenAspect).roundToInt().coerceAtMost(texH)
            srcV = (texH - srcH) / 2f
        } else {
            srcW = (texH * screenAspect).roundToInt().coerceAtMost(texW)
            srcU = (texW - srcW) / 2f
        }
        g.blit(
            RenderPipelines.GUI_TEXTURED, tex,
            0, 0, srcU, srcV,
            max(1, screenW.roundToInt()), max(1, screenH.roundToInt()),
            srcW, srcH, texW, texH
        )
    }

    fun enableScissor(g: GuiGraphicsExtractor, x0: Float, y0: Float, x1: Float, y1: Float) {
        g.enableScissor(x0.roundToInt(), y0.roundToInt(), x1.roundToInt(), y1.roundToInt())
    }

    fun disableScissor(g: GuiGraphicsExtractor) {
        g.disableScissor()
    }

    // ---- text -----------------------------------------------------------------

    /** MC's font has a 9px line box; CSS px sizes are emulated by scaling the pose. */
    fun scaleFor(sizePx: Float): Float = sizePx / 9f

    fun text(
        g: GuiGraphicsExtractor, font: Font, s: String, x: Float, y: Float,
        color: Int, sizePx: Float = 20f, shadow: Boolean = false
    ) {
        if (s.isEmpty()) return
        val sc = scaleFor(sizePx)
        g.pose().pushMatrix()
        g.pose().translate(x, y)
        g.pose().scale(sc, sc)
        g.text(font, Component.literal(s), 0, 0, color, shadow)
        g.pose().popMatrix()
    }

    fun textWidth(font: Font, s: String, sizePx: Float): Float =
        font.width(s.toComponent()) * scaleFor(sizePx)

    fun textRight(
        g: GuiGraphicsExtractor, font: Font, s: String, right: Float, y: Float,
        color: Int, sizePx: Float = 20f, shadow: Boolean = false
    ) = text(g, font, s, right - textWidth(font, s, sizePx), y, color, sizePx, shadow)

    fun textCenter(
        g: GuiGraphicsExtractor, font: Font, s: String, cx: Float, y: Float,
        color: Int, sizePx: Float = 20f, shadow: Boolean = false
    ) = text(g, font, s, cx - textWidth(font, s, sizePx) / 2f, y, color, sizePx, shadow)

    /** Vertically centres a text run of the given CSS size inside a box. */
    fun textCenterY(y: Float, h: Float, sizePx: Float): Float = y + (h - sizePx) / 2f - sizePx * 0.16f

    fun String.toComponent(): Component = Component.literal(this)

    fun wrap(g: GuiGraphicsExtractor, font: Font, s: String, x: Float, y: Float, maxW: Float, color: Int, sizePx: Float): Float {
        val sc = scaleFor(sizePx)
        val limit = (maxW / sc).toInt().coerceAtLeast(1)
        var cursorY = y
        for (line in font.split(Component.literal(s), limit)) {
            g.pose().pushMatrix()
            g.pose().translate(x, cursorY)
            g.pose().scale(sc, sc)
            g.text(font, line, 0, 0, color, false)
            g.pose().popMatrix()
            cursorY += sizePx
        }
        return cursorY
    }
}