package com.example.lbmenu

import net.minecraft.util.ARGB
import kotlin.math.roundToInt

/** Palette transcribed 1:1 from LiquidBounce src-theme/src/colors.scss */
object T {
    const val SURFACE = 0xFF000000.toInt()
    const val TEXT = 0xFFFFFFFF.toInt()
    const val DIMMED = 0xFFD3D3D3.toInt()
    const val ACCENT = 0xFF4677FF.toInt()
    const val ACCENT_HOVER = 0xFF3961D1.toInt()
    const val ERROR = 0xFFFC4130.toInt()
    const val SUCCESS = 0xFF4DAC68.toInt()
    const val PREMIUM = 0xFF67D24A.toInt()
    const val WARNING = 0xFFEFBF04.toInt()

    /** color-mix(in srgb, color X%, transparent) */
    fun alpha(color: Int, pct: Float): Int = ARGB.color((255 * pct).roundToInt().coerceIn(0, 255), color)

    /** color-mix(in srgb, color X%, black) */
    fun overBlack(color: Int, pct: Float): Int = ARGB.color(
        255,
        (ARGB.red(color) * pct).roundToInt().coerceIn(0, 255),
        (ARGB.green(color) * pct).roundToInt().coerceIn(0, 255),
        (ARGB.blue(color) * pct).roundToInt().coerceIn(0, 255)
    )

    val MENU_36: Int get() = alpha(SURFACE, 0.36f)
    val MENU_50: Int get() = alpha(SURFACE, 0.50f)
    val MENU_68: Int get() = alpha(SURFACE, 0.68f)
    val MENU_70: Int get() = alpha(SURFACE, 0.70f)
    val MENU_80: Int get() = alpha(SURFACE, 0.80f)
    val MENU_90: Int get() = alpha(SURFACE, 0.90f)
    val MODAL_BACKDROP: Int get() = alpha(0xFF000000.toInt(), 0.60f)

    val SWITCH_TRACK: Int get() = overBlack(TEXT, 0.45f)
    val SWITCH_TRACK_ON: Int get() = overBlack(ACCENT, 0.40f)

    const val PING_GOOD = 0xFF2DCC70.toInt()
    const val PING_MID = 0xFFF1C40F.toInt()
    const val PING_BAD = 0xFFE84C3D.toInt()

    const val TOOLTIP_BG = ACCENT

    /** Confetti palette (anniversary background) */
    val CONFETTI = intArrayOf(
        0xFF4677FF.toInt(), 0xFFFC4130.toInt(), 0xFF67D24A.toInt(), 0xFFFFD166.toInt(),
        0xFFFFFFFF.toInt(), 0xFF8AD9FF.toInt(), 0xFFFF8FAB.toInt()
    )
}

/** Svelte / CSS easing curves used by the theme */
object Ease {
    private const val C1 = 1.70158f
    private const val C3 = C1 + 1f

    fun linear(t: Float) = t.coerceIn(0f, 1f)
    fun quadOut(t: Float) = 1f - (1f - t) * (1f - t)
    fun cubicOut(t: Float) = 1f - Math.pow((1f - t).toDouble(), 3.0).toFloat()
    fun quintOut(t: Float): Float {
        val x = 1f - t
        return 1f - x * x * x * x * x
    }

    fun backOut(t: Float): Float {
        val p = t - 1f
        return 1f + C3 * p * p * p + C1 * p * p
    }

    fun backIn(t: Float): Float = C3 * t * t * t - C1 * t * t

    /** CSS `ease-out` cubic-bezier(0, 0, 0.58, 1) approximated */
    fun easeOut(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return 1f - (1f - x) * (1f - x) * (1f - 0.4f * x)
    }

    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}

/** Global menu metrics (CSS px == MC gui px) */
object M {
    const val PAD = 50f
    const val HEADER_MARGIN_BOTTOM = 60f
    const val LOGO_W = 261.263f
    const val LOGO_H = 98f

    const val MAIN_BTN_W = 590f
    const val MAIN_BTN_PAD_V = 25f
    const val MAIN_BTN_PAD_H = 35f
    const val MAIN_BTN_GAP = 25f
    const val MAIN_BTN_RADIUS = 5f
    const val MAIN_ICON = 90f
    const val MAIN_COL_GAP = 25f

    const val CHILD_PAD = 15f
    const val CHILD_ICON = 28f

    const val BTN_CONTAINER_PAD_V = 15f
    const val BTN_CONTAINER_PAD_H = 30f
    const val BTN_CONTAINER_GAP = 20f
    const val ICON_BOX = 58f

    const val ACCOUNT_W = 488f
    const val ACCOUNT_PAD_V = 15f
    const val ACCOUNT_PAD_H = 18f
    const val AVATAR = 68f

    const val NOTIF_MIN_W = 350f
    const val NOTIF_ICON = 65f

    const val OPTION_BAR_PAD_V = 15f
    const val OPTION_BAR_PAD_H = 30f
    const val OPTION_BAR_GAP = 30f
    const val OPTION_BAR_MARGIN_BOTTOM = 25f

    const val LIST_RADIUS = 5f
    const val LIST_MARGIN_BOTTOM = 25f
    const val LIST_PAD_TOP = 15f
    const val LIST_PAD_H = 30f
    const val LIST_ROW_GAP = 15f

    const val ITEM_PAD_V = 15f
    const val ITEM_PAD_H = 25f
    const val ITEM_GAP = 15f
    const val ITEM_IMAGE = 68f

    const val MODAL_MIN_W = 500f
    const val MODAL_PAD = 40f

    /** Menu.svelte / Modal.svelte media queries, in source order (last match wins) */
    fun zoomFor(w: Int, h: Int): Float {
        var z = 1f
        if (w <= 1366) z = 0.8f
        if (w <= 1200) z = 0.5f
        if (h <= 1100) z = 0.8f
        if (h <= 700) z = 0.5f
        if (h <= 540) z = 0.4f
        return z
    }

    fun vhScale(z: Float): Float = when {
        z >= 0.8f -> 1.25f
        z >= 0.5f -> 2.0f
        else -> 2.5f
    }
}
