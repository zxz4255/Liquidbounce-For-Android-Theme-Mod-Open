package com.example.lbmenu

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.component.ResolvableProfile
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/**
 * Base for every LiquidBounce-style menu page.
 *
 * Mirrors `common/Menu.svelte`: 50px padding, 100vh content, a 60px-gapped header and a
 * zoom media-query ladder. The zoom is applied as a pose scale, with the logical viewport
 * expanded by 1/zoom so the visible region still covers the whole window.
 */
abstract class MenuScreen(title: Component) : Screen(title) {

    protected val ui = Ui()
    protected var zoom = 1f
    protected var logicalW = 0f
    protected var logicalH = 0f
    protected var mouseLX = 0f
    protected var mouseLY = 0f

    protected val mcInstance: Minecraft get() = Minecraft.getInstance()

    /** Svelte `<MenuContent>` waits 700ms before rendering its slot. */
    protected var ready = false
    protected var openedAtMs = System.currentTimeMillis()

    private var headerFly: Fly? = null

    /** Page body — called after the header, inside the zoom transform. */
    open fun layoutBody() {}

    /** Override to false for pages that paint their own backdrop. */
    open fun paintsOwnBackground(): Boolean = false

    override fun isPauseScreen(): Boolean = false

    override fun shouldCloseOnEsc(): Boolean = true

    override fun init() {
        zoom = M.zoomFor(width, height)
        logicalW = width / zoom
        logicalH = height / zoom
        ready = false
        openedAtMs = System.currentTimeMillis()
        headerFly = null
        ui.clear()
        layoutBody()
    }

    override fun tick() {
        if (!ready && System.currentTimeMillis() - openedAtMs >= 700L) {
            ready = true
            headerFly = Fly(700, dy = -100f, easing = Ease::quintOut)
        }
        ui.tick()
    }

    /** Skips vanilla panorama/blur; the LiquidBounce backdrop replaces it entirely. */
    override fun extractBackground(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        // intentionally empty — drawBackdrop() runs inside extractRenderState
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        mouseLX = mouseX / zoom
        mouseLY = mouseY / zoom

        g.pose().pushMatrix()
        g.pose().scale(zoom, zoom)

        if (!paintsOwnBackground()) {
            Backdrop.drawBackground(g, logicalW.toInt(), logicalH.toInt())
        }

        if (ready) {
            drawHeader(g)
        }

        ui.draw(g, mouseLX, mouseLY)

        g.pose().popMatrix()

        // Notifications live outside the zoomed tree so they can float above modals.
        if (ready) drawNotifications(g)
    }

    // ---- header ---------------------------------------------------------------

    protected fun drawHeader(g: GuiGraphicsExtractor) {
        val fly = headerFly
        val dy = fly?.offsetY ?: 0f
        val font = mcInstance.font

        // logo-wrapper: 261.263 x 98 (LiquidBounceLogo)
        val logoX = M.PAD
        val logoY = M.PAD + dy
        D.image(g, Tex.LOGO, logoX, logoY, M.LOGO_W, M.LOGO_H, 512, 192)

        drawAccount(g, M.PAD + logicalW - M.PAD * 2 - M.ACCOUNT_W, M.PAD + dy, font)
    }

    protected fun drawNotifications(g: GuiGraphicsExtractor, dy: Float = 0f) {
        val items = NotifStore.active()
        if (items.isEmpty()) return
        val font = mcInstance.font
        val centerX = logicalW / 2f
        var y = M.PAD + dy
        for ((n, age) in items) {
            val slide = min(1f, age / 500f)
            val offY = (1f - Ease.quintOut(slide)) * -100f
            val msgW = D.textWidth(font, n.message, 16f)
            val titleW = D.textWidth(font, n.title, 18f)
            val w = max(M.NOTIF_MIN_W, M.NOTIF_ICON + 10f + max(msgW, titleW) + 10f)
            val h = 65f
            val x = centerX - w / 2f
            D.rrect(g, x, y + offY, w, h, 5f, T.MENU_68)
            D.rect(g, x, y + offY, M.NOTIF_ICON, h, if (n.error) T.ERROR else T.ACCENT)
            val tex = if (n.error) Tex.NOTIF_ERROR else Tex.NOTIF_INFO
            val (sw, sh) = Tex.src(if (n.error) "error" else "info")
            D.iconFit(g, tex, 24f, x + (M.NOTIF_ICON - 24f) / 2f, y + offY + (h - 24f) / 2f, sw, sh)
            D.text(g, font, n.title, x + M.NOTIF_ICON + 10f, y + offY + 12f, T.TEXT, 18f)
            D.text(g, font, n.message, x + M.NOTIF_ICON + 10f, y + offY + 36f, T.DIMMED, 16f)
            y += h + 10f
        }
    }

    protected fun drawAccount(g: GuiGraphicsExtractor, x: Float, y: Float, font: net.minecraft.client.gui.Font) {
        val user = mcInstance.user
        val name = user.name
        val online = user.profileId != null
        D.rrect(g, x, y, M.ACCOUNT_W, M.AVATAR + M.ACCOUNT_PAD_V * 2, 5f, T.MENU_68)

        val avX = x + M.ACCOUNT_PAD_H
        val avY = y + M.ACCOUNT_PAD_V
        // circular avatar clip: draw the 8x8 head blit inside a rounded mask approximation
        D.circle(g, avX + M.AVATAR / 2f, avY + M.AVATAR / 2f, M.AVATAR / 2f, D.withAlpha(0x2A2A32, 255f))
        try {
            val profile = ResolvableProfile.createUnresolved(user.profileId)
            val cache = mcInstance.playerSkinRenderCache()
            val info = cache.getOrDefault(profile)
            PlayerFaceExtractor.extractRenderState(g, info.playerSkin(), avX.toInt(), avY.toInt(), M.AVATAR.toInt())
        } catch (t: Throwable) {
            D.image(g, Tex.STEVE, avX, avY, M.AVATAR, M.AVATAR, 8, 8)
        }

        val textX = avX + M.AVATAR + 15f
        D.text(g, font, name, textX, y + M.ACCOUNT_PAD_V + 6f, T.TEXT, 20f)
        D.text(
            g, font, if (online) "Microsoft" else "Offline",
            textX, y + M.ACCOUNT_PAD_V + 34f,
            if (online) T.PREMIUM else T.DIMMED, 20f
        )

        val btnY = y + (M.AVATAR + M.ACCOUNT_PAD_V * 2) / 2f - 14f
        val penX = x + M.ACCOUNT_W - M.ACCOUNT_PAD_H - 28f
        val rndX = penX - 20f - 28f
        val (pw, ph) = Tex.src("pen")
        D.iconFit(g, Tex.PEN, 28f, penX, btnY, pw, ph)
        val (rw, rh) = Tex.src("random-account")
        D.iconFit(g, Tex.RANDOM_ACCOUNT, 28f, rndX, btnY, rw, rh)
    }

    // ---- input ----------------------------------------------------------------

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = (event.x / zoom).toFloat()
        val my = (event.y / zoom).toFloat()
        if (ui.mouseClicked(mx, my, event.button())) return true
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val mx = (event.x / zoom).toFloat()
        val my = (event.y / zoom).toFloat()
        if (ui.mouseReleased(mx, my, event.button())) return true
        return super.mouseReleased(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val mx = (event.x / zoom).toFloat()
        val my = (event.y / zoom).toFloat()
        if (ui.mouseDragged(mx, my, (dx / zoom).toFloat(), (dy / zoom).toFloat(), event.button())) return true
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseScrolled(x: Double, y: Double, sx: Double, sy: Double): Boolean {
        if (ui.mouseScrolled((x / zoom).toFloat(), (y / zoom).toFloat(), sy.toFloat())) return true
        return super.mouseScrolled(x, y, sx, sy)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (ui.charTyped(event.codepointAsString().first())) return true
        return super.charTyped(event)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (ui.keyPressed(event)) return true
        return super.keyPressed(event)
    }

    protected fun backToTitle() {
        mcInstance.gui.setScreen(LbTitleScreen())
    }

    protected fun isAnniversary(): Boolean {
        val now = LocalDate.now()
        val year = now.year
        val start = LocalDate.of(year, 3, 31)
        val end = LocalDate.of(year, 4, 7)
        return !now.isBefore(start) && !now.isAfter(end)
    }
}