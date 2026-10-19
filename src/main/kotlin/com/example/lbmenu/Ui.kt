package com.example.lbmenu

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A hit-testable, drawable node. All coordinates are CSS px inside the zoomed viewport. */
abstract class Node {
    var x = 0f
    var y = 0f
    var w = 0f
    var h = 0f

    var visible = true
    var enabled = true

    var hovered = false
    /** 0..1 hover animation, mirrors CSS `transition: ... .2s` */
    protected var hoverT = 0f

    open fun measure(): Unit = Unit
    open fun draw(g: GuiGraphicsExtractor, mx: Float, my: Float, dt: Float) {}
    open fun mouseClicked(mx: Float, my: Float, button: Int): Boolean = false
    open fun mouseReleased(mx: Float, my: Float, button: Int): Boolean = false
    open fun mouseDragged(mx: Float, my: Float, dx: Float, dy: Float, button: Int): Boolean = false
    open fun mouseScrolled(mx: Float, my: Float, dy: Float): Boolean = false
    open fun charTyped(cp: Char): Boolean = false
    open fun keyPressed(ev: KeyEvent): Boolean = false
    open fun tick(dt: Float) {}

    fun contains(mx: Float, my: Float) = visible && mx >= x && mx < x + w && my >= y && my < y + h

    /** advances hover interpolation, returns current hover alpha */
    protected fun stepHover(dt: Float): Float {
        val target = if (hovered) 1f else 0f
        val speed = dt / 0.2f
        hoverT = if (hoverT < target) min(target, hoverT + speed) else max(target, hoverT - speed)
        return hoverT
    }
}

/** Ordered node tree; the last drawn node receives input first (Svelte's DOM stacking). */
class Ui {
    val nodes = ArrayList<Node>()
    private var last = System.currentTimeMillis()

    fun clear() {
        nodes.clear()
    }

    fun <T : Node> add(n: T): T {
        nodes.add(n)
        n.measure()
        return n
    }

    fun remove(n: Node) {
        nodes.remove(n)
    }

    fun tick() {
        val now = System.currentTimeMillis()
        val dt = ((now - last).coerceIn(0L, 100L)) / 1000f
        last = now
        for (n in nodes) n.tick(dt)
    }

    fun draw(g: GuiGraphicsExtractor, mx: Float, my: Float) {
        val now = System.currentTimeMillis()
        val dt = ((now - last).coerceIn(0L, 100L)) / 1000f
        for (n in nodes) {
            n.hovered = n.contains(mx, my)
            n.draw(g, mx, my, dt)
        }
    }

    fun mouseClicked(mx: Float, my: Float, button: Int): Boolean {
        for (i in nodes.indices.reversed()) {
            val n = nodes[i]
            if (n.visible && n.mouseClicked(mx, my, button)) return true
        }
        return false
    }

    fun mouseReleased(mx: Float, my: Float, button: Int): Boolean {
        for (i in nodes.indices.reversed()) {
            if (nodes[i].mouseReleased(mx, my, button)) return true
        }
        return false
    }

    fun mouseDragged(mx: Float, my: Float, dx: Float, dy: Float, button: Int): Boolean {
        for (i in nodes.indices.reversed()) {
            if (nodes[i].mouseDragged(mx, my, dx, dy, button)) return true
        }
        return false
    }

    fun mouseScrolled(mx: Float, my: Float, dy: Float): Boolean {
        for (i in nodes.indices.reversed()) {
            if (nodes[i].visible && nodes[i].mouseScrolled(mx, my, dy)) return true
        }
        return false
    }

    fun charTyped(cp: Char): Boolean {
        for (i in nodes.indices.reversed()) {
            if (nodes[i].charTyped(cp)) return true
        }
        return false
    }

    fun keyPressed(ev: KeyEvent): Boolean {
        for (i in nodes.indices.reversed()) {
            if (nodes[i].keyPressed(ev)) return true
        }
        return false
    }

    val isEmpty: Boolean get() = nodes.isEmpty()
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

/** `IconTextButton` — 58px icon plate + 0 30px padded label, 200%-wide split gradient. */
class IconTextButton(
    val title: String,
    val icon: net.minecraft.resources.Identifier,
    val onClick: () -> Unit
) : Node() {
    var pressed = false
    var disabledState = false

    override fun measure() {
        val font = Minecraft.getInstance().font
        w = M.ICON_BOX + D.textWidth(font, title, 20f) + 60f
        h = M.ICON_BOX
    }

    override fun draw(g: GuiGraphicsExtractor, mx: Float, my: Float, dt: Float) {
        if (!visible) return
        val hv = stepHover(dt)
        val font = Minecraft.getInstance().font
        val alpha = if (disabledState) 0.6f else 1f
        D.rrect(g, x, y, w, h, 5f, blend(T.MENU_36, T.ACCENT, hv, alpha))
        D.rect(g, x, y, M.ICON_BOX, h, blend(T.ACCENT, T.ACCENT, 0f, alpha))
        val (sw, sh) = Tex.src(iconName())
        D.iconFit(g, icon, M.ICON_BOX, x, y, sw, sh)
        val ty = D.textCenterY(y, h, 20f)
        D.text(g, font, title, x + M.ICON_BOX + 30f, ty, blend(T.TEXT, T.TEXT, 0f, alpha), 20f)
    }

    private fun iconName(): String = icon.path.substringAfterLast("icon-").removeSuffix(".png")

    override fun mouseClicked(mx: Float, my: Float, button: Int): Boolean {
        if (button != 0 || disabledState || !contains(mx, my)) return false
        pressed = true
        return true
    }

    override fun mouseReleased(mx: Float, my: Float, button: Int): Boolean {
        if (button != 0 || !pressed) return false
        pressed = false
        if (contains(mx, my)) onClick()
        return true
    }
}

/** `IconButton` — bare 58x58 glyph. */
class IconButton(
    val title: String,
    val icon: net.minecraft.resources.Identifier,
    val onClick: () -> Unit
) : Node() {
    var disabledState = false
    private var pressed = false

    override fun measure() {
        w = M.ICON_BOX
        h = M.ICON_BOX
    }

    override fun draw(g: GuiGraphicsExtractor, mx: Float, my: Float, dt: Float) {
        if (!visible) return
        stepHover(dt)
        val (sw, sh) = Tex.src(icon.path.substringAfterLast("icon-").removeSuffix(".png"))
        D.iconFit(g, icon, M.ICON_BOX, x, y, sw, sh)
    }

    override fun mouseClicked(mx: Float, my: Float, button: Int): Boolean {
        if (button != 0 || disabledState || !contains(mx, my)) return false
        pressed = true
        return true
    }

    override fun mouseReleased(mx: Float, my: Float, button: Int): Boolean {
        if (button != 0 || !pressed) return false
        pressed = false
        if (contains(mx, my)) onClick()
        return true
    }
}

/** `ButtonContainer` — translucent pill holding a row (or column) of buttons. */
class ButtonContainer(private val vertical: Boolean = false) : Node() {
    val children = ArrayList<Node>()

    fun add(n: Node): Node {
        children.add(n)
        return n
    }

    fun clear() {
        children.clear()
    }

    override fun measure() {
        if (vertical) {
            w = (children.maxOfOrNull { it.w } ?: 0f) + M.BTN_CONTAINER_PAD_H * 2
            h = children.sumOf { it.h.toDouble() }.toFloat() +
                M.BTN_CONTAINER_GAP * max(0, children.size - 1) + M.BTN_CONTAINER_PAD_V * 2
        } else {
            w = children.sumOf { it.w.toDouble() }.toFloat() +
                M.BTN_CONTAINER_GAP * max(0, children.size - 1) + M.BTN_CONTAINER_PAD_H * 2
            h = (children.maxOfOrNull { it.h } ?: 0f) + M.BTN_CONTAINER_PAD_V * 2
        }
    }

    override fun draw(g: GuiGraphicsExtractor, mx: Float, my: Float, dt: Float) {
        if (!visible) return
        D.rrect(g, x, y, w, h, 5f, T.MENU_68)
        if (vertical) {
            var cy = y + M.BTN_CONTAINER_PAD_V
            val cx = x + (w - (children.maxOfOrNull { it.w } ?: 0f)) / 2f
            for (c in children) {
                c.x = cx + ((children.maxOfOrNull { it.w } ?: 0f) - c.w) / 2f
                c.y = cy
                c.hovered = c.contains(mx, my)
                c.draw(g, mx, my, dt)
                cy += c.h + M.BTN_CONTAINER_GAP
            }
        } else {
            var cx = x + M.BTN_CONTAINER_PAD_H
            for (c in children) {
                c.x = cx
                c.y = y + (h - c.h) / 2f
                c.hovered = c.contains(mx, my)
                c.draw(g, mx, my, dt)
                cx += c.w + M.BTN_CONTAINER_GAP
            }
        }
    }

    override fun mouseClicked(mx: Float, my: Float, button: Int): Boolean {
        for (i in children.indices.reversed()) if (children[i].mouseClicked(mx, my, button)) return true
        return false
    }

    override fun mouseReleased(mx: Float, my: Float, button: Int): Boolean {
        for (i in children.indices.reversed()) if (children[i].mouseReleased(mx, my, button)) return true
        return false
    }
}

/** `MainButton` — 590px wide, 25/35 padding, 90px circular icon plate, 26px label, split gradient. */
class MainButton(
    val title: String,
    val icon: net.minecraft.resources.Identifier,
    val index: Int,
    val onClick: () -> Unit
) : Node() {
    private val flyIn = Fly(400, dx = -500f, delayMs = index * 100L, easing = Ease::backOut)
    private val flyOut = Fly(400, dx = -500f, delayMs = index * 100L, easing = Ease::backIn)
    var removing = false
    private var pressT = 0f
    private var pressed = false

    var child: ChildButton? = null

    override fun measure() {
        w = M.MAIN_BTN_W
        h = M.MAIN_BTN_PAD_V * 2 + M.MAIN_ICON
        child?.let { c ->
            c.measure()
            h = max(h, M.MAIN_BTN_PAD_V * 2 + c.h)
        }
    }

    private val anim: Float get() = if (removing) 1f - flyOut.progress else flyIn.progress

    override fun draw(g: GuiGraphicsExtractor, mx: Float, my: Float, dt: Float) {
        if (!visible) return
        val a = anim
        if (a <= 0.001f) return
        val hv = stepHover(dt)
        pressT = if (pressed) min(1f, pressT + dt / 0.12f) else max(0f, pressT - dt / 0.12f)

        val ox = (if (removing) flyOut.offsetX else flyIn.offsetX)
        val bx = x + ox
        val by = y
        val font = Minecraft.getInstance().font

        D.rrect(g, bx, by, w, h, M.MAIN_BTN_RADIUS, blend(T.MENU_68, T.ACCENT, hv, 1f))

        val iconCx = bx + M.MAIN_BTN_PAD_H + M.MAIN_ICON / 2f
        val iconCy = by + h / 2f
        D.circle(g, iconCx, iconCy, M.MAIN_ICON / 2f, blend(T.ACCENT, T.TEXT, hv, 1f))
        val (sw, sh) = Tex.src(icon.path.substringAfterLast("icon-").removeSuffix(".png"))
        D.iconFit(
            g, icon, M.MAIN_ICON - 34f, iconCx - (M.MAIN_ICON - 34f) / 2f, iconCy - (M.MAIN_ICON - 34f) / 2f,
            sw, sh, blend(T.TEXT, T.ACCENT, hv, 1f)
        )

        val textX = bx + M.MAIN_BTN_PAD_H + M.MAIN_ICON + M.MAIN_COL_GAP
        D.text(g, font, title, textX, D.textCenterY(by, h, 26f), T.TEXT, 26f)

        child?.let { c ->
            c.x = bx + w - M.MAIN_BTN_PAD_H - c.w
            c.y = by + (h - c.h) / 2f
            c.parentHovered = hovered
            c.hovered = c.contains(mx, my)
            c.draw(g, mx, my, dt)
        }
    }

    fun hitTestChild(mx: Float, my: Float): Boolean = child?.contains(mx, my) == true

    override fun mouseClicked(mx: Float, my: Float, button: Int): Boolean {
        if (button != 0 || !visible) return false
        child?.let { c ->
            if (c.contains(mx, my) && c.mouseClicked(mx, my, button)) return true
        }
        if (!contains(mx, my)) return false
        pressed = true
        return true
    }

    override fun mouseReleased(mx: Float, my: Float, button: Int): Boolean {
        if (button != 0) return false
        var handled = false
        child?.let { c ->
            if (c.mouseReleased(mx, my, button)) handled = true
        }
        if (pressed) {
            pressed = false
            if (contains(mx, my) && !hitTestChild(mx, my)) {
                onClick()
                handled = true
            }
        }
        return handled
    }
}

/** `ChildButton` — accent pill nested inside MainButton, 15px padding, 28px glyph. */
class ChildButton(
    val title: String,
    val icon: net.minecraft.resources.Identifier,
    val onClick: () -> Unit
) : Node() {
    var parentHovered = false
    private var pressed = false

    override fun measure() {
        val font = Minecraft.getInstance().font
        w = M.CHILD_PAD * 2 + M.CHILD_ICON + 10f + D.textWidth(font, title, 16f)
        h = M.CHILD_PAD * 2 + M.CHILD_ICON
    }

    override fun draw(g: GuiGraphicsExtractor, mx: Float, my: Float, dt: Float) {
        if (!visible) return
        val hv = if (parentHovered) 1f else 0f
        stepHover(dt)
        val font = Minecraft.getInstance().font
        D.rrect(g, x, y, w, h, 5f, blend(T.ACCENT, T.TEXT, hv, 1f))
        val (sw, sh) = Tex.src(icon.path.substringAfterLast("icon-").removeSuffix(".png"))
        D.iconFit(
            g, icon, M.CHILD_ICON, x + M.CHILD_PAD, y + M.CHILD_PAD, sw, sh,
            blend(T.TEXT, T.ACCENT, hv, 1f)
        )
        D.text(
            g, font, title, x + M.CHILD_PAD + M.CHILD_ICON + 10f,
            D.textCenterY(y, h, 16f), blend(T.TEXT, T.ACCENT, hv, 1f), 16f
        )
    }

    override fun mouseClicked(mx: Float, my: Float, button: Int): Boolean {
        if (button != 0 || !contains(mx, my)) return false
        pressed = true
        return true
    }

    override fun mouseReleased(mx: Float, my: Float, button: Int): Boolean {
        if (button != 0 || !pressed) return false
        pressed = false
        if (contains(mx, my)) onClick()
        return true
    }
}

// ---------------------------------------------------------------------------
// Settings widgets
// ---------------------------------------------------------------------------

/** `SwitchSetting` — 28x18 track with a 21px thumb sliding 27px. */
class SwitchSetting(var title: String, var value: Boolean, val onChange: () -> Unit = {}) : Node() {
    private var anim = if (value) 1f else 0f

    override fun measure() {
        val font = Minecraft.getInstance().font
        w = 28f + 20f + D.textWidth(font, title, 20f) + 10f
        h = 30f
    }

    override fun draw(g: GuiGraphicsExtractor, mx: Float, my: Float, dt: Float) {
        if (!visible) return
        stepHover(dt)
        val target = if (value) 1f else 0f
        anim = if (anim < target) min(target, anim + dt / 0.4f) else max(target, anim - dt / 0.4f)
        val trackY = y + (h - 18f) / 2f
        val trackX = x + 10f
        D.rrect(g, trackX, trackY + 2f, 28f, 14f, 10f, blend(T.SWITCH_TRACK, T.SWITCH_TRACK_ON, anim, 1f))
        val thumbX = trackX - 10f + 27f * anim
        D.circle(g, thumbX + 10.5f, trackY + 2f + 7f, 10.5f, blend(T.TEXT, T.ACCENT, anim, 1f))
        D.text(
            g, Minecraft.getInstance().font, title, x + 38f,
            D.textCenterY(y, h, 20f), T.TEXT, 20f
        )
    }

    override fun mouseClicked(mx: Float, my: Float, button: Int): Boolean {
        if (button != 0 || !contains(mx, my)) return false
        value = !value
        onChange()
        return true
    }
}

/** `ButtonSetting` — accent filled, 20px padding, 5px radius, 20px label. */
class ButtonSetting(
    var title: String,
    val onClick: () -> Unit,
    var inset: Boolean = false,
    var secondary: Boolean = false,
    var listenForEnter: Boolean = false
) : Node() {
    var disabledState = false
    var loading = false
    private var pressed = false

    override fun measure() {
        val font = Minecraft.getInstance().font
        w = D.textWidth(font, title, 20f) + 40f + if (inset) 60f else 0f
        h = 20f * 2 + 20f
    }

    override fun draw(g: GuiGraphicsExtractor, mx: Float, my: Float, dt: Float) {
        if (!visible) return
        val hv = stepHover(dt)
        val base = if (secondary) T.MENU_36 else T.ACCENT
        val hot = if (secondary) T.SURFACE else T.ACCENT_HOVER
        val a = if (disabledState) 0.6f else 1f
        D.rrect(g, x, y, w, h, 5f, blend(base, hot, hv, a))
        D.textCenter(
            g, Minecraft.getInstance().font, title, x + w / 2f,
            D.textCenterY(y, h, 20f), blend(T.TEXT, T.TEXT, 0f, a), 20f
        )
    }

    override fun mouseClicked(mx: Float, my: Float, button: Int): Boolean {
        if (button != 0 || disabledState || !contains(mx, my)) return false
        pressed = true
        return true
    }

    override fun mouseReleased(mx: Float, my: Float, button: Int): Boolean {
        if (button != 0 || !pressed) return false
        pressed = false
        if (contains(mx, my)) onClick()
        return true
    }

    override fun keyPressed(ev: KeyEvent): Boolean {
        if (!listenForEnter || disabledState) return false
        if (ev.shortcutKey() == 257 || ev.shortcutKey() == 335) {
            onClick()
            return true
        }
        return false
    }
}

/** `IconTextInput` — 64px accent icon plate + underlined text field. */
class IconTextInput(
    var title: String,
    val icon: net.minecraft.resources.Identifier,
    var value: String = "",
    var maxLength: Int = 128
) : Node() {
    var onChange: () -> Unit = {}
    var focused = false
    private var caret = 0
    private var blink = 0f
    private var scroll = 0f

    override fun measure() {
        w = 420f
        h = 64f
        caret = caret.coerceIn(0, value.length)
    }

    override fun draw(g: GuiGraphicsExtractor, mx: Float, my: Float, dt: Float) {
        if (!visible) return
        val font = Minecraft.getInstance().font
        D.rrect(g, x, y, 64f, h, 5f, T.ACCENT)
        val (sw, sh) = Tex.src(icon.path.substringAfterLast("icon-").removeSuffix(".png"))
        D.iconFit(g, icon, 30f, x + 17f, y + 17f, sw, sh)
        D.rect(g, x + 64f, y, w - 64f, h, T.MENU_36)
        D.rect(g, x + 64f, y, 2f, h, T.SURFACE)

        val textX = x + 64f + 18f
        val maxW = w - 64f - 18f - 20f
        val shown = if (value.isEmpty() && !focused) title else value
        val color = if (value.isEmpty() && !focused) T.DIMMED else T.TEXT
        D.enableScissor(g, textX, y, textX + maxW, y + h)
        D.text(g, font, shown, textX - scroll, D.textCenterY(y, h, 20f), color, 20f)
        if (focused) {
            blink += dt
            if (blink % 1f < 0.5f) {
                val cx = textX - scroll + D.textWidth(font, value.take(caret), 20f)
                D.rect(g, cx, y + 14f, 2f, h - 28f, T.TEXT)
            }
        }
        D.disableScissor(g)
    }

    private fun recomputeScroll() {
        val font = Minecraft.getInstance().font
        val maxW = w - 64f - 18f - 20f
        val caretX = D.textWidth(font, value.take(caret), 20f)
        if (caretX - scroll > maxW) scroll = caretX - maxW
        if (caretX - scroll < 0f) scroll = caretX
        if (scroll < 0f) scroll = 0f
    }

    override fun mouseClicked(mx: Float, my: Float, button: Int): Boolean {
        if (button != 0 || !contains(mx, my)) return false
        focused = true
        blink = 0f
        val font = Minecraft.getInstance().font
        val rel = mx - (x + 64f + 18f) + scroll
        var best = value.length
        for (i in 0..value.length) {
            if (D.textWidth(font, value.take(i), 20f) >= rel) {
                best = i
                break
            }
        }
        caret = best
        return true
    }

    fun blur() {
        focused = false
    }

    override fun charTyped(cp: Char): Boolean {
        if (!focused) return false
        if (value.length >= maxLength) return true
        value = value.substring(0, caret) + cp + value.substring(caret)
        caret++
        recomputeScroll()
        onChange()
        return true
    }

    override fun keyPressed(ev: KeyEvent): Boolean {
        if (!focused) return false
        when (ev.shortcutKey()) {
            259 -> if (caret > 0) {
                value = value.substring(0, caret - 1) + value.substring(caret)
                caret--
                recomputeScroll()
                onChange()
            }

            261 -> if (caret < value.length) {
                value = value.substring(0, caret) + value.substring(caret + 1)
                recomputeScroll()
                onChange()
            }

            263 -> caret = (caret - 1).coerceAtLeast(0)
            262 -> caret = (caret + 1).coerceAtMost(value.length)
            268 -> caret = 0
            269 -> caret = value.length
            257, 335 -> {
                focused = false
                return true
            }

            256 -> {
                focused = false
                return true
            }
        }
        recomputeScroll()
        return true
    }
}

/** `GenericSelect` — accent header + dropdown options list. */
class Select(
    var title: String,
    var options: List<String>,
    var value: String,
    var multi: Boolean = false,
    var values: MutableSet<String> = LinkedHashSet(),
    val onChange: () -> Unit = {}
) : Node() {
    var expanded = false
    private var openT = 0f

    override fun measure() {
        val font = Minecraft.getInstance().font
        val label = if (multi) title else "$title $value"
        w = max(250f, D.textWidth(font, label, 20f) + 40f + 20f + 20f)
        h = 20f * 2 + 20f
    }

    override fun draw(g: GuiGraphicsExtractor, mx: Float, my: Float, dt: Float) {
        if (!visible) return
        stepHover(dt)
        val font = Minecraft.getInstance().font
        val open = if (expanded) 1f else 0f
        openT = if (openT < open) min(open, openT + dt / 0.2f) else max(open, openT - dt / 0.2f)

        val headerH = 60f
        val topR = if (expanded) 5f else 5f
        D.rrect(g, x, y, w, headerH, topR, T.ACCENT)
        if (expanded) D.rect(g, x, y + headerH - 6f, w, 6f, T.ACCENT)

        val label = if (multi) title else "$title $value"
        D.text(g, font, label, x + 20f, D.textCenterY(y, headerH, 20f), T.TEXT, 20f)
        val (sw, sh) = Tex.src("select-arrow")
        D.iconFit(g, Tex.SELECT_ARROW, 16f, x + w - 20f - 16f, y + (headerH - 16f) / 2f, sw, sh)

        if (openT > 0.01f) {
            var oy = y + headerH
            for (o in options) {
                val oh = 50f
                val active = if (multi) values.contains(o) else o == value
                val hover = mx >= x && mx < x + w && my >= oy && my < oy + oh
                D.rect(g, x, oy, w, oh, T.MENU_90)
                D.text(
                    g, font, o, x + 20f, D.textCenterY(oy, oh, 20f),
                    if (active) T.ACCENT else if (hover) T.TEXT else T.DIMMED, 20f
                )
                oy += oh
            }
        }
    }

    private fun optionsHeight() = options.size * 50f

    override fun mouseClicked(mx: Float, my: Float, button: Int): Boolean {
        if (button != 0 || !visible) return false
        if (expanded) {
            var oy = y + 60f
            for (o in options) {
                if (mx >= x && mx < x + w && my >= oy && my < oy + 50f) {
                    if (multi) {
                        if (!values.add(o)) values.remove(o)
                    } else {
                        value = o
                        expanded = false
                    }
                    onChange()
                    measure()
                    return true
                }
                oy += 50f
            }
            if (contains(mx, my)) {
                if (!multi) expanded = false
                return true
            }
            expanded = false
            return false
        }
        if (!contains(mx, my)) return false
        expanded = true
        return true
    }

    override fun mouseScrolled(mx: Float, my: Float, dy: Float): Boolean {
        if (!expanded) return false
        if (mx < x || mx > x + w || my < y || my > y + 60f + optionsHeight()) return false
        return true
    }

    override fun mouseReleased(mx: Float, my: Float, button: Int) = false
}

/** `Search` — translucent field with an inset magnifier and 4px accent underline. */
class Search(var placeholder: String = "Search...") : Node() {
    var value = ""
    var focused = false
    private var caret = 0
    private var blink = 0f
    private var scroll = 0f
    val onChange: MutableList<() -> Unit> = ArrayList()

    override fun measure() {
        h = 60f
        if (w <= 0f) w = 400f
    }

    override fun draw(g: GuiGraphicsExtractor, mx: Float, my: Float, dt: Float) {
        if (!visible) return
        val font = Minecraft.getInstance().font
        D.rrect(g, x, y, w, h, 5f, T.MENU_36)
        D.rect(g, x, y + h - 4f, w, 4f, T.ACCENT)
        val (sw, sh) = Tex.src("search")
        D.iconFit(g, Tex.SEARCH, 18f, x + 20f, y + (h - 18f) / 2f, sw, sh)

        val textX = x + 60f
        val maxW = w - 60f - 20f
        val shown = if (value.isEmpty()) placeholder else value
        D.enableScissor(g, textX, y, textX + maxW, y + h)
        D.text(
            g, font, shown, textX - scroll, D.textCenterY(y, h, 20f),
            if (value.isEmpty()) T.DIMMED else T.TEXT, 20f
        )
        if (focused) {
            blink += dt
            if (blink % 1f < 0.5f) {
                val cx = textX - scroll + D.textWidth(font, value.take(caret), 20f)
                D.rect(g, cx, y + 14f, 2f, h - 28f, T.TEXT)
            }
        }
        D.disableScissor(g)
    }

    private fun fire() {
        for (cb in onChange) cb()
    }

    override fun mouseClicked(mx: Float, my: Float, button: Int): Boolean {
        if (button != 0 || !contains(mx, my)) return false
        focused = true
        blink = 0f
        return true
    }

    fun blur() {
        focused = false
    }

    override fun charTyped(cp: Char): Boolean {
        if (!focused) return false
        value = value.substring(0, caret) + cp + value.substring(caret)
        caret++
        fire()
        return true
    }

    override fun keyPressed(ev: KeyEvent): Boolean {
        if (!focused) return false
        when (ev.shortcutKey()) {
            259 -> if (caret > 0) {
                value = value.substring(0, caret - 1) + value.substring(caret)
                caret--
                fire()
            }

            261 -> if (caret < value.length) {
                value = value.substring(0, caret) + value.substring(caret + 1)
                fire()
            }

            263 -> caret = (caret - 1).coerceAtLeast(0)
            262 -> caret = (caret + 1).coerceAtMost(value.length)
            257, 335, 256 -> {
                focused = false
                return true
            }
        }
        return true
    }
}

/** `Modal` — 60% black backdrop, 500px min card, 34px title with 8px accent underline. */
open class Modal(var title: String) : Node() {
    val ui = Ui()
    init { visible = false }
    private var openT = 0f
    private var openFly = Fly(300, dy = -100f, easing = Ease::quintOut)
    private var dyOffset = 0f

    var onClose: (() -> Unit)? = null

    /** logical viewport, set by the owning screen before drawing */
    var viewportW = 1920f
    var viewportH = 1080f

    override fun measure() {
        for (n in ui.nodes) n.measure()
    }

    fun layout(screenW: Float, screenH: Float) {
        viewportW = screenW
        viewportH = screenH
        val font = Minecraft.getInstance().font
        val contentW = (ui.nodes.maxOfOrNull { it.w } ?: 0f)
        val contentH = ui.nodes.sumOf { it.h.toDouble() }.toFloat() +
            40f * max(0, ui.nodes.size - 1)
        w = max(M.MODAL_MIN_W, max(contentW, D.textWidth(font, title, 34f) + 80f) + M.MODAL_PAD * 2)
        h = M.MODAL_PAD * 2 + 34f + 80f + contentH
        x = (screenW - w) / 2f
        y = (screenH - h) / 2f

        var cy = y + M.MODAL_PAD + 34f + 80f
        for (n in ui.nodes) {
            n.x = x + (w - n.w) / 2f
            n.y = cy
            cy += n.h + 40f
        }
    }

    override fun draw(g: GuiGraphicsExtractor, mx: Float, my: Float, dt: Float) {
        if (!visible) return
        val a = openFly.alpha
        openT = if (openT < a) min(a, openT + dt / 0.3f) else max(a, openT - dt / 0.3f)
        D.rect(g, 0f, 0f, viewportW, viewportH, D.withAlpha(0x000000, 153 * openT))

        val dy = openFly.offsetY
        dyOffset = dy
        D.rrect(g, x, y + dy, w, h, 5f, T.MENU_80)
        val font = Minecraft.getInstance().font
        val titleY = y + dy + M.MODAL_PAD
        D.textCenter(g, font, title, x + w / 2f, titleY, T.TEXT, 34f)
        val underlineW = w * 0.9f
        D.rrect(g, x + (w - underlineW) / 2f, titleY + 34f + 17f, underlineW, 8f, 10f, T.ACCENT)

        // close button: 40px circle, 2px border
        val cbx = x + w - 20f - 40f
        val cby = y + dy + 20f
        D.circle(g, cbx + 20f, cby + 20f, 20f, D.withAlpha(0x000000, 0f))
        val ch = mx >= cbx && mx < cbx + 40f && my >= cby && my < cby + 40f
        if (ch) D.circle(g, cbx + 20f, cby + 20f, 20f, D.withAlpha(0xFFFFFF, 26f))
        D.outlineRect(g, cbx, cby, 40f, 40f, 2f, T.TEXT)
        val (sw, sh) = Tex.src("close")
        D.iconFit(g, Tex.CLOSE, 18f, cbx + 11f, cby + 11f, sw, sh)

        for (n in ui.nodes) {
            n.y += dy
            n.hovered = n.contains(mx, my)
            n.draw(g, mx, my, dt)
            n.y -= dy
        }
    }

    override fun mouseClicked(mx: Float, my: Float, button: Int): Boolean {
        if (!visible) return false
        if (ui.mouseClicked(mx, my - dyOffset, button)) return true
        val dy = dyOffset
        val cbx = x + w - 60f
        val cby = y + dy + 20f
        if (mx >= cbx && mx < cbx + 40f && my >= cby && my < cby + 40f) {
            close()
            return true
        }
        return contains(mx - 0f, my - dy)
    }

    override fun mouseReleased(mx: Float, my: Float, button: Int): Boolean {
        if (!visible) return false
        return ui.mouseReleased(mx, my - dyOffset, button)
    }

    override fun mouseScrolled(mx: Float, my: Float, dy: Float): Boolean {
        if (!visible) return false
        return ui.mouseScrolled(mx, my - dyOffset, dy)
    }

    override fun charTyped(cp: Char): Boolean = visible && ui.charTyped(cp)

    override fun keyPressed(ev: KeyEvent): Boolean {
        if (!visible) return false
        if (ev.isEscape) {
            close()
            return true
        }
        return ui.keyPressed(ev)
    }

    open fun close() {
        visible = false
        onClose?.invoke()
    }
}

/** `ToolTip` — accent pill floating 50px above the hovered widget, with a rotated tail. */
object Tooltip {
    fun draw(g: GuiGraphicsExtractor, text: String, cx: Float, top: Float) {
        if (text.isEmpty()) return
        val font = Minecraft.getInstance().font
        val tw = D.textWidth(font, text, 16f)
        val w = tw + 30f
        val h = 16f + 20f
        val x = cx - w / 2f
        val y = top - 50f - h / 2f
        D.rrect(g, x, y, w, h, 20f, T.TOOLTIP_BG)
        D.textCenter(g, font, text, cx, D.textCenterY(y, h, 16f), T.TEXT, 16f)
        // rotated 12x12 tail
        g.pose().pushMatrix()
        g.pose().translate(cx, y + h - 6f)
        g.pose().rotate(Math.toRadians(45.0).toFloat())
        D.rect(g, -6f, -6f, 12f, 12f, T.TOOLTIP_BG)
        g.pose().popMatrix()
    }
}

/** Linear blend of two ARGB colors, with a global alpha multiplier. */
fun blend(a: Int, b: Int, t: Float, alphaMul: Float = 1f): Int {
    val tt = t.coerceIn(0f, 1f)
    val aa = ((ar(a) + (ar(b) - ar(a)) * tt) * alphaMul).roundToInt().coerceIn(0, 255)
    val rr = (red(a) + (red(b) - red(a)) * tt).roundToInt().coerceIn(0, 255)
    val gg = (green(a) + (green(b) - green(a)) * tt).roundToInt().coerceIn(0, 255)
    val bb = (blue(a) + (blue(b) - blue(a)) * tt).roundToInt().coerceIn(0, 255)
    return net.minecraft.util.ARGB.color(aa, rr, gg, bb)
}

fun ar(c: Int) = net.minecraft.util.ARGB.alpha(c)
fun red(c: Int) = net.minecraft.util.ARGB.red(c)
fun green(c: Int) = net.minecraft.util.ARGB.green(c)
fun blue(c: Int) = net.minecraft.util.ARGB.blue(c)