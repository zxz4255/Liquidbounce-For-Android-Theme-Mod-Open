package com.example.customtitle

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.options.OptionsScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

/**
 * Native recreation of LiquidBounce Title.svelte layout.
 * - Icons: real PNG textures (exported from SVG assets)
 * - Logo / background.png: real textures
 * - AURORA mode: smooth seamless aurora (continuous flow, no mountains)
 */
object BrandConfig {
    const val CLIENT_NAME = "CustomTitle"
}

private const val MOD_ID = "customtitle"
/**
 * Native pixel sizes of each icon_*.png (must match the files in assets).
 * Dest on button stays ICON_SIZE; full source is scaled via blit srcW/srcH.
 */
private val ICON_SRC_SIZE = mapOf(
    "singleplayer" to (520 to 680),
    "multiplayer" to (680 to 680),
    "options" to (680 to 680),
    "brand" to (510 to 510),
    "back" to (440 to 440),
    "placeholder_a" to (64 to 64),
    "placeholder_b" to (64 to 64)
)

private fun tex(path: String): Identifier =
    Identifier.fromNamespaceAndPath(MOD_ID, path)

private fun iconTexture(name: String): Identifier =
    tex("textures/gui/icon_$name.png")

private val BG_TEXTURE = tex("textures/gui/background.png")
private val LOGO_TEXTURE = tex("textures/gui/logo.png")

/**
 * Persistent GPU texture for the aurora composite buffer. A singleton (not per-Screen-instance)
 * so re-opening the title screen (e.g. backing out of Options) doesn't register a new texture
 * every time and leak the old one - same lifetime pattern as CustomBackgroundManager's textures.
 */
private object AuroraTexture {
    val ID: Identifier = tex("dynamic/aurora")
    private var texture: DynamicTexture? = null

    fun ensure(width: Int, height: Int): DynamicTexture {
        texture?.let { return it }
        val created = DynamicTexture({ "aurora" }, width, height, false)
        Minecraft.getInstance().textureManager.register(ID, created)
        texture = created
        return created
    }
}

// Source image size of background.png (2560x1440)
private const val BG_SRC_W = 2560
private const val BG_SRC_H = 1440

private data class MainButtonSpec(
    val title: String,
    val iconName: String,
    val onClick: (CustomTitleScreen) -> Unit
) {
    val icon: Identifier get() = iconTexture(iconName)
}

private val MAIN_BUTTONS = listOf(
    MainButtonSpec("Singleplayer", "singleplayer") { screen ->
        val mc = Minecraft.getInstance()
        mc.gui.setScreen(SelectWorldScreen(screen))
    },
    MainButtonSpec("Multiplayer", "multiplayer") { screen ->
        val mc = Minecraft.getInstance()
        mc.gui.setScreen(JoinMultiplayerScreen(screen))
    },
    MainButtonSpec("Exit", "back") { _ ->
        Minecraft.getInstance().stop()
    },
    MainButtonSpec("Options", "options") { screen ->
        val mc = Minecraft.getInstance()
        mc.gui.setScreen(OptionsScreen(screen, mc.options, mc.level != null))
    }
)

private val SECONDARY_BUTTONS = listOf(
    MainButtonSpec("Custom Background", "placeholder_a") { screen ->
        screen.openBackgroundPicker()
    },
    MainButtonSpec("Toggle Background", "placeholder_b") { screen ->
        screen.cycleBackground()
    },
    MainButtonSpec("Back", "back") { screen -> screen.toggleButtonSet() }
)

private class SmallButton(val title: String, val onClick: () -> Unit)

private val ADDITIONAL_BUTTONS = listOf(
    SmallButton("Toggle Background") { },
    SmallButton("Custom Background") { }
)

// ---- Colors ----
private val BG_TOP = ARGB.color(255, 12, 12, 16)
private val BG_BOTTOM = ARGB.color(255, 20, 20, 26)
private val BUTTON_BG = ARGB.color(230, 24, 24, 30)
private val BUTTON_ACCENT = ARGB.color(255, 70, 70, 78) // gray hover
private val ICON_BG = ARGB.color(230, 24, 24, 30) // match BUTTON_BG — no dark patch
private val ICON_BG_HOVER = ARGB.color(255, 70, 70, 78) // gray hover
private val TEXT_MAIN = -1
private val WATERMARK_TEXT = ARGB.color(255, 220, 220, 230)
private val WATERMARK_SHADOW = ARGB.color(180, 0, 0, 0)

private const val BUTTON_WIDTH = 300
private const val BUTTON_HEIGHT = 46
private const val ICON_SIZE = 34
private const val BUTTON_GAP = 12
private const val ANIM_DURATION_MS = 400L
private const val ANIM_STAGGER_MS = 100L
// logo.png is 1920x721 — keep aspect, modest height above buttons
private const val LOGO_SRC_W = 1920
private const val LOGO_SRC_H = 721
private const val WATERMARK_LOGO_H = 56
private const val WATERMARK_LOGO_W = 149
private const val WATERMARK_MARGIN = 12

/**
 * Background modes:
 *  - IMAGE  → static background.png (default)
 *  - AURORA → smooth seamless aurora
 *  - CUSTOM → user image from background/ (picker only, not in Toggle cycle)
 */
enum class BackgroundMode { IMAGE, AURORA, CUSTOM }
private var BACKGROUND_MODE = BackgroundMode.IMAGE

class CustomTitleScreen : Screen(Component.literal(BrandConfig.CLIENT_NAME)) {

    private var showingSecondary = false
    private var toggledAtMs = System.currentTimeMillis()
    private var hoveredButtonIndex = -1

    // click press animation
    private var pressedButtonIndex = -1
    private var pressedAtMs = 0L
    private var pendingClick: (() -> Unit)? = null
    private val PRESS_ANIM_MS = 120L

    private fun currentButtons() = if (showingSecondary) SECONDARY_BUTTONS else MAIN_BUTTONS

    fun toggleButtonSet() {
        showingSecondary = !showingSecondary
        toggledAtMs = System.currentTimeMillis()
        hoveredButtonIndex = -1
    }

    fun cycleBackground() {
        // only IMAGE <-> AURORA
        BACKGROUND_MODE = when (BACKGROUND_MODE) {
            BackgroundMode.IMAGE -> BackgroundMode.AURORA
            BackgroundMode.AURORA -> BackgroundMode.IMAGE
            BackgroundMode.CUSTOM -> BackgroundMode.IMAGE
        }
    }

    /** Called by BackgroundPickerScreen after the player picks (or clears) a custom image. */
    fun setBackgroundMode(mode: BackgroundMode) {
        BACKGROUND_MODE = mode
    }

    fun openBackgroundPicker() {
        minecraft.gui.setScreen(BackgroundPickerScreen(this))
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        drawBackground(context)

        val buttons = currentButtons()
        val startX = 40
        val startY = height / 2 - (buttons.size * (BUTTON_HEIGHT + BUTTON_GAP)) / 2

        // Logo sits above the top main button (not screen corner)
        drawWatermark(context, startX, startY - WATERMARK_LOGO_H - 16)

        hoveredButtonIndex = -1

        // finish deferred click after press animation
        if (pendingClick != null && pressedButtonIndex >= 0) {
            val elapsed = System.currentTimeMillis() - pressedAtMs
            if (elapsed >= PRESS_ANIM_MS) {
                val action = pendingClick
                pendingClick = null
                pressedButtonIndex = -1
                action?.invoke()
            }
        }

        for ((index, spec) in buttons.withIndex()) {
            val slideProgress = animProgress(index)
            val offsetX = ((1f - slideProgress) * -260f).roundToInt()

            val x = startX + offsetX
            val y = startY + index * (BUTTON_HEIGHT + BUTTON_GAP)

            if (slideProgress <= 0.01f) continue

            val hovered = mouseX in x..(x + BUTTON_WIDTH) && mouseY in y..(y + BUTTON_HEIGHT) && offsetX == 0
            if (hovered) hoveredButtonIndex = index

            val press = pressScale(index)
            drawMainButton(context, spec, x, y, hovered, press)
        }

        drawBottomBar(context, mouseX, mouseY)
        drawRepoWatermark(context)
    }

    private fun animProgress(index: Int): Float {
        val elapsed = System.currentTimeMillis() - toggledAtMs
        val delay = index * ANIM_STAGGER_MS
        val local = (elapsed - delay).coerceIn(0, ANIM_DURATION_MS)
        return local.toFloat() / ANIM_DURATION_MS
    }

    /** 1.0 = idle, dips to ~0.92 while pressed, eases back. */
    private fun pressScale(index: Int): Float {
        if (index != pressedButtonIndex || pressedButtonIndex < 0) return 1f
        val t = ((System.currentTimeMillis() - pressedAtMs).toFloat() / PRESS_ANIM_MS).coerceIn(0f, 1f)
        // ease: quick shrink then slight recover before action fires
        return if (t < 0.5f) {
            1f - 0.08f * (t / 0.5f)
        } else {
            0.92f + 0.04f * ((t - 0.5f) / 0.5f)
        }
    }


    private fun drawBackground(context: GuiGraphicsExtractor) {
        when (BACKGROUND_MODE) {
            BackgroundMode.IMAGE -> drawImageBackground(context)
            BackgroundMode.AURORA -> drawAuroraBackground(context)
            BackgroundMode.CUSTOM -> drawCustomBackground(context)
        }
    }

    /** User-picked image from <gameDirectory>/background/. Falls back to gradient if none selected. */
    private fun drawCustomBackground(context: GuiGraphicsExtractor) {
        val entry = CustomBackgroundManager.selected
        if (entry == null) {
            drawImageBackground(context)
            return
        }
        blitCoverFit(context, entry.identifier, entry.width, entry.height, 0, 0, width, height)
        context.fill(0, 0, width, height, ARGB.color(50, 0, 0, 0))
    }

    /**
     * Cover-fit blit of background.png (2560x1440) — crop excess, never stretch unevenly.
     */
    private fun drawImageBackground(context: GuiGraphicsExtractor) {
        val screenAspect = width.toFloat() / height.coerceAtLeast(1)
        val texAspect = BG_SRC_W.toFloat() / BG_SRC_H

        val srcW: Int
        val srcH: Int
        val srcU: Float
        val srcV: Float
        if (screenAspect > texAspect) {
            // screen wider → crop top/bottom of texture
            srcW = BG_SRC_W
            srcH = (BG_SRC_W / screenAspect).roundToInt().coerceAtMost(BG_SRC_H)
            srcU = 0f
            srcV = ((BG_SRC_H - srcH) / 2f)
        } else {
            // screen taller → crop left/right
            srcH = BG_SRC_H
            srcW = (BG_SRC_H * screenAspect).roundToInt().coerceAtMost(BG_SRC_W)
            srcU = ((BG_SRC_W - srcW) / 2f)
            srcV = 0f
        }

        context.blit(
            RenderPipelines.GUI_TEXTURED,
            BG_TEXTURE,
            0, 0,
            srcU, srcV,
            width, height,
            srcW, srcH,
            BG_SRC_W, BG_SRC_H
        )
        context.fill(0, 0, width, height, ARGB.color(70, 0, 0, 0))
    }

    /**
     * Faithful CPU port of LiquidBounce src-theme/public/backgrounds/background.frag
     *
     * frag uses gl_FragCoord with origin bottom-left; here y=0 is top, so uv.y is flipped.
     * Mountain + aurora math matches the GLSL line-for-line.
     */
    /**
     * Smooth aurora port of LiquidBounce src-theme/public/backgrounds/background.frag
     * (no mountains). Instead of stepping between a few pre-rendered frames (choppy ~2 FPS),
     * every band is baked once into a small seamlessly tileable noise tile and then sampled
     * with wrapped bilinear interpolation at continuously advancing scroll offsets —
     * fluid motion at any framerate with near-zero per-frame cost.
     */
    // ---- Smooth aurora: seamless tileable noise bake + continuous scroll ----
    // Same band palette/intensity as the GLSL port, but the field flows every frame.
    private val auroraSpeed = doubleArrayOf(0.05, 0.10, 0.15)
    private val auroraIntensity = doubleArrayOf(0.35, 0.40, 0.30)
    private val auroraLayerCol = doubleArrayOf(
        0.0, 1.0, 0.3, // band 1 (r, g, b)
        0.1, 0.5, 0.9, // band 2
        0.4, 0.1, 0.8  // band 3
    )
    // GLSL reuses the band colors (cr, cg) as inner-noise offsets — keep that
    private val auroraInnerOff = doubleArrayOf(0.0, 1.0, 0.1, 0.5, 0.4, 0.1)

    private val auroraTileP = 4.0        // noise period in lattice units (tile wraps here)
    private val auroraTileS = 4          // tile samples per lattice unit
    private val auroraTileSize = 16      // auroraTileP * auroraTileS
    // Composite resolution: was 112x96, which (combined with the old 6-bit color quantization
    // below) produced visible banding/ripple contours once upscaled ~17x to a real screen.
    // Raised 4x here and switched the draw path to a single GPU texture blit (see AuroraTexture)
    // instead of many CPU fill() rectangles, so the extra resolution costs one upload, not more
    // draw calls.
    private val auroraCacheW = 480
    private val auroraCacheH = 270

    private var auroraTiles: Array<FloatArray>? = null // 3 baked tiles, one per band
    private var auroraFrame: IntArray? = null          // reused composite buffer (no GC)
    private var auroraSkyR: FloatArray? = null         // per-row sky base (static)
    private var auroraSkyG: FloatArray? = null
    private var auroraSkyB: FloatArray? = null
    private var auroraUvY2: FloatArray? = null         // per-row uvY * 2 (noise space)
    private var auroraUvYc: FloatArray? = null         // per-row uvY * 0.55 (band cutoff)
    private var auroraUvX2: FloatArray? = null         // per-column uvX * 2 (screen aspect)
    private var auroraAspect = -1.0
    private val auroraOffX = DoubleArray(3)            // per-frame scroll offsets
    private val auroraOffY = DoubleArray(3)

    private fun drawAuroraBackground(context: GuiGraphicsExtractor) {
        ensureAuroraBaked()

        val tiles = auroraTiles!!
        val frame = auroraFrame!!
        val skyR = auroraSkyR!!
        val skyG = auroraSkyG!!
        val skyB = auroraSkyB!!
        val uvY2 = auroraUvY2!!
        val uvYc = auroraUvYc!!
        val uvX2 = auroraUvX2!!
        val col = auroraLayerCol
        val inten = auroraIntensity

        // Continuous time in GLSL units — same drift pace as the old 4 s / 10-unit loop
        val t = System.currentTimeMillis() * 0.0025
        for (k in 0 until 3) {
            val o = (t * auroraSpeed[k] * 2.0) % auroraTileP
            auroraOffX[k] = o
            auroraOffY[k] = (auroraTileP - o) % auroraTileP // -o wrapped into [0, p)
        }

        // Composite one frame: 3 wrapped bilinear tile samples per pixel.
        // No sin()/noise evaluation here — that is bake-time only.
        var i = 0
        for (row in 0 until auroraCacheH) {
            val sy = uvY2[row].toDouble()
            val yc = uvYc[row].toDouble()
            val r0 = skyR[row].toDouble()
            val g0 = skyG[row].toDouble()
            val b0 = skyB[row].toDouble()
            for (cx in 0 until auroraCacheW) {
                val sx = uvX2[cx].toDouble()
                var r = r0
                var g = g0
                var b = b0
                for (k in 0 until 3) {
                    val n = sampleAuroraTile(tiles[k], sx + auroraOffX[k], sy + auroraOffY[k])
                    val a = (n - yc) * inten[k] * 0.55
                    if (a > 0.0) {
                        val k3 = k * 3
                        r += a * col[k3]
                        g += a * col[k3 + 1]
                        b += a * col[k3 + 2]
                    }
                }
                // Full 8-bit precision - the old "and 0xFC" quantization here was what caused
                // the visible banding/ripple contours (see comment above auroraCacheW).
                frame[i++] = ARGB.color(
                    255,
                    (r.coerceIn(0.0, 1.0) * 255.0).toInt(),
                    (g.coerceIn(0.0, 1.0) * 255.0).toInt(),
                    (b.coerceIn(0.0, 1.0) * 255.0).toInt()
                )
            }
        }

        // Single GPU upload + stretch blit instead of many CPU fill() rectangles. The uv arrays
        // above already account for screen aspect, so the buffer maps 1:1 onto the viewport -
        // a plain stretch to (width, height) is correct here (not cover-fit crop).
        val texture = AuroraTexture.ensure(auroraCacheW, auroraCacheH)
        val image = texture.pixels
        var p = 0
        for (row in 0 until auroraCacheH) {
            for (cx in 0 until auroraCacheW) {
                image.setPixel(cx, row, frame[p++])
            }
        }
        texture.upload()

        context.blit(
            RenderPipelines.GUI_TEXTURED, AuroraTexture.ID,
            0, 0, 0f, 0f,
            width, height,
            auroraCacheW, auroraCacheH,
            auroraCacheW, auroraCacheH
        )
    }

    /** One-time bake of the static parts (tiles, sky rows, uv rows). Runs in a single frame. */
    private fun ensureAuroraBaked() {
        if (auroraTiles == null) {
            auroraTiles = Array(3) { k ->
                bakeAuroraTile(auroraInnerOff[k * 2], auroraInnerOff[k * 2 + 1])
            }
            auroraFrame = IntArray(auroraCacheW * auroraCacheH)

            val skyR = FloatArray(auroraCacheH)
            val skyG = FloatArray(auroraCacheH)
            val skyB = FloatArray(auroraCacheH)
            val uvY2 = FloatArray(auroraCacheH)
            val uvYc = FloatArray(auroraCacheH)
            for (row in 0 until auroraCacheH) {
                // uvY is bottom-up like in the shader; row 0 is the top of the screen
                val uvY = ((auroraCacheH - 1 - row).toDouble() + 0.5) / auroraCacheH
                val sky1 = 1.0 - smoothstep(0.0, 0.55, uvY)
                skyR[row] = (0.12 + 0.18 * sky1).toFloat()
                skyG[row] = 0.16f
                skyB[row] = (0.32 + 0.35 * sky1).toFloat()
                uvY2[row] = (uvY * 2.0).toFloat()
                uvYc[row] = (uvY * 0.55).toFloat()
            }
            auroraSkyR = skyR
            auroraSkyG = skyG
            auroraSkyB = skyB
            auroraUvY2 = uvY2
            auroraUvYc = uvYc
        }

        // Column mapping follows the real screen aspect (rebuilt only on resize)
        val aspect = if (height > 0) width.toDouble() / height
            else auroraCacheW.toDouble() / auroraCacheH
        if (aspect != auroraAspect || auroraUvX2 == null) {
            auroraAspect = aspect
            val uvX2 = FloatArray(auroraCacheW)
            for (cx in 0 until auroraCacheW) {
                val uvX = ((cx + 0.5) / auroraCacheW) * aspect
                uvX2[cx] = (uvX * 2.0).toFloat()
            }
            auroraUvX2 = uvX2
        }
    }

    /** Bake one seamlessly tileable composed-noise tile for a band (16x16, one-time). */
    private fun bakeAuroraTile(innerX: Double, innerY: Double): FloatArray {
        val tile = FloatArray(auroraTileSize * auroraTileSize)
        for (sy in 0 until auroraTileSize) {
            val y = sy.toDouble() / auroraTileS
            for (sx in 0 until auroraTileSize) {
                val x = sx.toDouble() / auroraTileS
                val inner = auroraNoisePeriodic(innerX + x, innerY + y)
                val n = auroraNoisePeriodic(x + inner, y)
                tile[sy * auroraTileSize + sx] = n.toFloat()
            }
        }
        return tile
    }

    /** Value noise whose lattice wraps every [auroraTileP] units — makes tiles seamless. */
    private fun auroraNoisePeriodic(x: Double, y: Double): Double {
        val ix = floor(x)
        val iy = floor(y)
        val fx = x - ix
        val fy = y - iy
        val u = fx * fx * (3.0 - 2.0 * fx)
        val v = fy * fy * (3.0 - 2.0 * fy)
        val x0 = wrapAuroraTile(ix)
        val x1 = wrapAuroraTile(ix + 1.0)
        val y0 = wrapAuroraTile(iy)
        val y1 = wrapAuroraTile(iy + 1.0)
        val a = auroraHash(x0, y0)
        val b = auroraHash(x1, y0)
        val c = auroraHash(x0, y1)
        val d = auroraHash(x1, y1)
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v
    }

    private fun wrapAuroraTile(i: Double): Int {
        val n = i.toInt()
        return ((n % auroraTileSize) + auroraTileSize) % auroraTileSize
    }

    private fun auroraHash(ix: Int, iy: Int): Double = hash(ix * 157.31 + iy * 113.97 + 7.7)

    /** Wrapped bilinear sample of a baked tile; x/y may be any real numbers. */
    private fun sampleAuroraTile(tile: FloatArray, x: Double, y: Double): Double {
        val p = auroraTileP
        val ux = (((x % p) + p) % p) * auroraTileS
        val uy = (((y % p) + p) % p) * auroraTileS
        val i0 = ux.toInt()
        val j0 = uy.toInt()
        val fx = (ux - i0).toFloat()
        val fy = (uy - j0).toFloat()
        val i1 = if (i0 + 1 >= auroraTileSize) 0 else i0 + 1
        val j1 = if (j0 + 1 >= auroraTileSize) 0 else j0 + 1
        val size = auroraTileSize
        val a = tile[j0 * size + i0]
        val b = tile[j0 * size + i1]
        val c = tile[j1 * size + i0]
        val d = tile[j1 * size + i1]
        val top = a + (b - a) * fx
        val bot = c + (d - c) * fx
        return (top + (bot - top) * fy).toDouble()
    }

    private fun hash(n: Double): Double {
        val s = sin(n) * 43758.5453
        return s - floor(s)
    }

    private fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }


    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        fun ch(c: Int, shift: Int) = (c ushr shift) and 0xFF
        val r = (ch(a, 16) + (ch(b, 16) - ch(a, 16)) * t).roundToInt()
        val g = (ch(a, 8) + (ch(b, 8) - ch(a, 8)) * t).roundToInt()
        val bl = (ch(a, 0) + (ch(b, 0) - ch(a, 0)) * t).roundToInt()
        return ARGB.color(255, r, g, bl)
    }

    /** Top-left watermark: real logo.png + text */
    private fun drawWatermark(context: GuiGraphicsExtractor, x: Int, y: Int) {
        // logo.png 1920x721 → full source scaled to aspect-correct dest
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            LOGO_TEXTURE,
            x, y,
            0f, 0f,
            WATERMARK_LOGO_W, WATERMARK_LOGO_H, // dest (aspect correct)
            LOGO_SRC_W, LOGO_SRC_H,             // full src region
            LOGO_SRC_W, LOGO_SRC_H              // texture size
        )
    }

    private fun drawMainButton(
        context: GuiGraphicsExtractor,
        spec: MainButtonSpec,
        x: Int,
        y: Int,
        hovered: Boolean,
        press: Float = 1f
    ) {
        // scale around center for press feedback
        val bw = (BUTTON_WIDTH * press).roundToInt().coerceAtLeast(1)
        val bh = (BUTTON_HEIGHT * press).roundToInt().coerceAtLeast(1)
        val bx = x + (BUTTON_WIDTH - bw) / 2
        val by = y + (BUTTON_HEIGHT - bh) / 2

        val radius = 6 // smooth mild radius
        fillRoundedRect(context, bx, by, bx + bw, by + bh, radius, BUTTON_BG)
        if (hovered || press < 0.99f) {
            fillRoundedRect(context, bx, by, bx + bw, by + bh, radius, ARGB.color(255, 70, 70, 78))
        }

        val iconX = bx + 8
        val iconY = by + (bh - ICON_SIZE) / 2
        // icon plate matches button color — no dark mismatch block
        fillRoundedRect(
            context, iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 3,
            if (hovered) ICON_BG_HOVER else ICON_BG
        )

        val drawSize = ICON_SIZE - 8
        val ix = iconX + (ICON_SIZE - drawSize) / 2
        val iy = iconY + (ICON_SIZE - drawSize) / 2
        val (srcW, srcH) = ICON_SRC_SIZE[spec.iconName] ?: (64 to 64)
        // letterbox inside square so non-square icons (e.g. 520x680) keep aspect
        val scale = minOf(drawSize.toFloat() / srcW, drawSize.toFloat() / srcH)
        val dw = (srcW * scale).toInt().coerceAtLeast(1)
        val dh = (srcH * scale).toInt().coerceAtLeast(1)
        val dx = ix + (drawSize - dw) / 2
        val dy = iy + (drawSize - dh) / 2
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            spec.icon,
            dx, dy,
            0f, 0f,
            dw, dh,       // dest on screen
            srcW, srcH,   // full source region
            srcW, srcH    // texture size
        )

        context.text(
            font,
            spec.title.asPlainText(),
            iconX + ICON_SIZE + 14,
            by + (bh - font.lineHeight) / 2,
            TEXT_MAIN,
            false
        )
    }


    /**
     * Right-anchored bottom bar for Toggle/Custom Background. Shares its start-X calculation
     * with mouseClicked's hit-testing (bottomBarStartX) so the two can't drift apart.
     */
    private fun drawBottomBar(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        var x = bottomBarStartX()
        val y = height - 34
        val h = 20
        val radius = 6
        for (btn in ADDITIONAL_BUTTONS) {
            val w = CustomFont.width(font, btn.title) + 20
            val hovered = mouseX in x..(x + w) && mouseY in y..(y + h)
            fillRoundedRect(context, x, y, x + w, y + h, radius, if (hovered) BUTTON_ACCENT else BUTTON_BG)
            // Rounded (not truncated) vertical centering - integer-division truncation here was
            // why the gap below the text was consistently 1px bigger than the gap above it.
            val textY = y + Math.round((h - font.lineHeight) / 2.0).toInt()
            context.text(
                font, btn.title.asPlainText(),
                x + (w - CustomFont.width(font, btn.title)) / 2,
                textY,
                TEXT_MAIN, false
            )
            x += w + 8
        }
    }

    /** Total width of all ADDITIONAL_BUTTONS (with their gaps), right-anchored with a 40px margin. */
    private fun bottomBarStartX(): Int {
        var total = 0
        for (btn in ADDITIONAL_BUTTONS) {
            total += CustomFont.width(font, btn.title) + 20 + 8
        }
        if (total > 0) total -= 8 // no trailing gap after the last button
        return width - 40 - total
    }

    /** Bottom-left credit text, deliberately smaller than the buttons (pose-scaled, not a separate font asset). */
    private fun drawRepoWatermark(context: GuiGraphicsExtractor) {
        val text = "https://github.com/zxz4255/Liquidbounce-For-Android-Theme-Mod-Open"
        val scale = 0.6f
        val y = height - 34 + (20 - Math.round(font.lineHeight * scale)) / 2

        context.pose().pushMatrix()
        context.pose().translate(40f, y.toFloat())
        context.pose().scale(scale, scale)
        context.text(font, text.asPlainText(), 0, 0, WATERMARK_TEXT, false)
        context.pose().popMatrix()
    }

    /**
     * Smooth rounded rect — full rows per scanline so corners have no visible pixel steps.
     * Radius is inclusive; uses continuous circle equation per row.
     */
    private fun fillRoundedRect(
        context: GuiGraphicsExtractor,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        r: Int,
        color: Int
    ) {
        val w = x1 - x0
        val h = y1 - y0
        if (w <= 0 || h <= 0) return
        val radius = r.coerceAtMost(w / 2).coerceAtMost(h / 2).coerceAtLeast(0)
        if (radius <= 0) {
            context.fill(x0, y0, x1, y1, color)
            return
        }
        val r2 = radius.toDouble() * radius
        for (row in 0 until h) {
            val yy = y0 + row
            val inset: Int = when {
                row < radius -> {
                    // top corners: distance from top edge
                    val dy = (radius - 1 - row).toDouble()
                    val dx = kotlin.math.sqrt((r2 - dy * dy).coerceAtLeast(0.0))
                    (radius - dx).toInt().coerceIn(0, radius)
                }
                row >= h - radius -> {
                    // bottom corners
                    val dy = (row - (h - radius)).toDouble()
                    val dx = kotlin.math.sqrt((r2 - dy * dy).coerceAtLeast(0.0))
                    (radius - dx).toInt().coerceIn(0, radius)
                }
                else -> 0
            }
            context.fill(x0 + inset, yy, x1 - inset, yy + 1, color)
        }
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mouseX = click.x.toInt()
        val mouseY = click.y.toInt()

        val buttons = currentButtons()
        val startX = 40
        val startY = height / 2 - (buttons.size * (BUTTON_HEIGHT + BUTTON_GAP)) / 2

        for ((index, spec) in buttons.withIndex()) {
            val y = startY + index * (BUTTON_HEIGHT + BUTTON_GAP)
            if (mouseX in startX..(startX + BUTTON_WIDTH) && mouseY in y..(y + BUTTON_HEIGHT)) {
                // play press animation, then run action
                pressedButtonIndex = index
                pressedAtMs = System.currentTimeMillis()
                pendingClick = { spec.onClick(this) }
                return true
            }
        }

        var bx = bottomBarStartX()
        val by = height - 34
        for ((i, btn) in ADDITIONAL_BUTTONS.withIndex()) {
            val w = CustomFont.width(font, btn.title) + 20
            if (mouseX in bx..(bx + w) && mouseY in by..(by + 20)) {
                when (i) {
                    0 -> cycleBackground()
                    1 -> openBackgroundPicker()
                    else -> btn.onClick()
                }
                return true
            }
            bx += w + 8
        }

        return super.mouseClicked(click, doubled)
    }

    override fun isPauseScreen() = false
}

/** All screen text flows through here → styled with the custom TTF font (see CustomFont) */
private fun String.asPlainText(): Component = CustomFont.styled(this)
