package com.example.lbmenu

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.multiplayer.ServerData
import kotlin.math.max
import kotlin.math.min

/**
 * `common/menulist/MenuList.svelte` + `MenuListItem.svelte`
 *
 * .menu-list       → flex:1, 5px radius, 25px bottom margin
 * .menu-list-items → absolute inset 15px/0, 0 30px padding, 15px row-gap, overflow auto
 * .menu-list-item  → grid "a b c"/"a d c", 15px 25px padding, 15px column gap, 5px radius
 */
class MenuList : Node() {
    val items = ArrayList<MenuListItem>()
    private var scroll = 0f
    private var maxScroll = 0f

    override fun draw(g: GuiGraphicsExtractor, mx: Float, my: Float, dt: Float) {
        if (!visible) return
        D.rrect(g, x, y, w, h, M.LIST_RADIUS, T.MENU_68)

        val innerX = x + M.LIST_PAD_H
        val innerY = y + M.LIST_PAD_TOP
        val innerW = w - M.LIST_PAD_H * 2
        val innerH = h - M.LIST_PAD_TOP * 2

        var total = 0f
        for (it in items) {
            it.measure()
            total += it.h + M.LIST_ROW_GAP
        }
        if (items.isNotEmpty()) total -= M.LIST_ROW_GAP
        maxScroll = max(0f, total - innerH)
        scroll = scroll.coerceIn(0f, maxScroll)

        D.enableScissor(g, innerX, innerY, innerX + innerW, innerY + innerH)
        var cy = innerY - scroll
        for (it in items) {
            it.x = innerX
            it.y = cy
            it.w = innerW
            it.hovered = mx >= innerX && mx < innerX + innerW &&
                my >= max(cy, innerY) && my < min(cy + it.h, innerY + innerH)
            it.draw(g, mx, my, dt)
            cy += it.h + M.LIST_ROW_GAP
        }
        D.disableScissor(g)
    }

    override fun mouseScrolled(mx: Float, my: Float, dy: Float): Boolean {
        if (!contains(mx, my)) return false
        scroll = (scroll - dy * 30f).coerceIn(0f, maxScroll)
        return true
    }

    override fun mouseClicked(mx: Float, my: Float, button: Int): Boolean {
        if (!contains(mx, my)) return false
        for (i in items.indices.reversed()) if (items[i].mouseClicked(mx, my, button)) return true
        return false
    }

    override fun mouseReleased(mx: Float, my: Float, button: Int): Boolean {
        for (i in items.indices.reversed()) if (items[i].mouseReleased(mx, my, button)) return true
        return false
    }
}

/**
 * One list row: 68px circular icon, 20px semibold title + inline tag pills, 18px dimmed
 * subtitle, and right-side actions — the "active" group only fades in on hover.
 */
class MenuListItem(
    var title: String,
    var subtitle: String = "",
    var tags: List<String> = emptyList(),
    var imageText: String? = null,
    var imageTextBg: Int = T.PING_BAD,
    var iconTexture: net.minecraft.resources.Identifier? = null,
    var onDoubleClick: (() -> Unit)? = null
) : Node() {

    class RowAction(
        val title: String,
        val icon: net.minecraft.resources.Identifier,
        val onClick: () -> Unit
    ) {
        var x = 0f
        var y = 0f
        var hovered = false
        var pressed = false
        val size = 27f
    }

    val always = ArrayList<RowAction>()
    val active = ArrayList<RowAction>()

    var pressed = false

    override fun measure() {
        h = M.ITEM_PAD_V * 2 + M.ITEM_IMAGE
    }

    override fun draw(g: GuiGraphicsExtractor, mx: Float, my: Float, dt: Float) {
        if (!visible) return
        val hv = stepHover(dt)
        val font = Minecraft.getInstance().font

        D.rrect(g, x, y, w, h, 5f, blend(T.MENU_36, T.ACCENT, hv, 1f))

        val imgX = x + M.ITEM_PAD_H
        val imgY = y + M.ITEM_PAD_V
        D.circle(g, imgX + M.ITEM_IMAGE / 2f, imgY + M.ITEM_IMAGE / 2f, M.ITEM_IMAGE / 2f, D.withAlpha(0x202028, 255f))
        val tex = iconTexture
        if (tex != null) {
            D.icon(g, tex, imgX, imgY, M.ITEM_IMAGE, M.ITEM_IMAGE)
        } else {
            D.iconFit(g, Tex.PLACEHOLDER, M.ITEM_IMAGE, imgX, imgY, 64f, 64f)
        }

        imageText?.let { txt ->
            val pillW = D.textWidth(font, txt, 12f) + 20f
            val pillH = 24f
            val pillX = imgX + M.ITEM_IMAGE - pillW
            val pillY = imgY + M.ITEM_IMAGE - pillH
            D.rrect(g, pillX, pillY, pillW, pillH, 20f, imageTextBg)
            D.textCenter(g, font, txt, pillX + pillW / 2f, D.textCenterY(pillY, pillH, 12f), T.TEXT, 12f)
        }

        val textX = imgX + M.ITEM_IMAGE + M.ITEM_GAP
        val titleY = y + M.ITEM_PAD_V + 2f
        D.text(g, font, title, textX, titleY, T.TEXT, 20f)

        var tagX = textX + D.textWidth(font, title, 20f)
        for (tag in tags) {
            val pillW = D.textWidth(font, tag, 12f) + 20f
            val pillH = 18f
            D.rrect(g, tagX + 10f, titleY - 3f, pillW, pillH, 20f, T.MENU_36)
            D.text(g, font, tag, tagX + 20f, titleY, T.TEXT, 12f)
            tagX += pillW + 20f
        }

        if (subtitle.isNotEmpty()) {
            D.text(
                g, font, subtitle, textX, y + M.ITEM_PAD_V + M.ITEM_IMAGE - 22f,
                blend(T.DIMMED, T.TEXT, hv, 1f), 18f
            )
        }

        val rightEdge = x + w - M.ITEM_PAD_H
        var bx = rightEdge - always.size * (27f + 15f)
        for (a in always) {
            a.x = bx
            a.y = y + (h - a.size) / 2f
            a.hovered = mx >= a.x && mx < a.x + a.size && my >= a.y && my < a.y + a.size
            drawAction(g, a, 1f)
            bx += a.size + 15f
        }
        if (hv > 0.01f) {
            var ax = rightEdge - always.size * (27f + 15f) - 20f - active.size * (27f + 15f)
            for (a in active) {
                a.x = ax
                a.y = y + (h - a.size) / 2f
                a.hovered = mx >= a.x && mx < a.x + a.size && my >= a.y && my < a.y + a.size
                drawAction(g, a, hv)
                ax += a.size + 15f
            }
        }
    }

    private fun drawAction(g: GuiGraphicsExtractor, a: RowAction, alpha: Float) {
        val (sw, sh) = Tex.src(a.icon.path.substringAfterLast("icon-").removeSuffix(".png"))
        D.iconFit(g, a.icon, a.size, a.x, a.y, sw, sh, D.withAlpha(0xFFFFFF, 255 * alpha))
    }

    private fun hitAction(mx: Float, my: Float): RowAction? {
        for (a in always) if (mx >= a.x && mx < a.x + a.size && my >= a.y && my < a.y + a.size) return a
        if (hoverT > 0.5f) {
            for (a in active) if (mx >= a.x && mx < a.x + a.size && my >= a.y && my < a.y + a.size) return a
        }
        return null
    }

    override fun mouseClicked(mx: Float, my: Float, button: Int): Boolean {
        if (button != 0 || !contains(mx, my)) return false
        val action = hitAction(mx, my)
        if (action != null) {
            action.pressed = true
            return true
        }
        pressed = true
        return true
    }

    override fun mouseReleased(mx: Float, my: Float, button: Int): Boolean {
        if (button != 0) return false
        for (a in always) {
            if (a.pressed) {
                a.pressed = false
                if (mx >= a.x && mx < a.x + a.size && my >= a.y && my < a.y + a.size) {
                    a.onClick()
                    return true
                }
            }
        }
        for (a in active) {
            if (a.pressed) {
                a.pressed = false
                if (mx >= a.x && mx < a.x + a.size && my >= a.y && my < a.y + a.size) {
                    a.onClick()
                    return true
                }
            }
        }
        if (pressed) {
            pressed = false
            if (contains(mx, my)) {
                onDoubleClick?.invoke()
                return true
            }
        }
        return false
    }
}