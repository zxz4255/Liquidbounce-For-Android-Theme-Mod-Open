package com.example.lbmenu

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.options.OptionsScreen
import net.minecraft.network.chat.Component
import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * `routes/menu/title/Title.svelte`
 *
 * .content grid:
 *   "a ."   → row 1fr, col 1fr        → main-buttons
 *   "b c"   → row max-content         → additional-buttons | social-buttons
 */
class LbTitleScreen : MenuScreen(Component.literal("LiquidBounce")) {

    private var showingClient = false
    private var toggledAt = 0L
    private val toggleDelay = 750L

    private lateinit var mainContainer: ButtonContainer
    private lateinit var bottomContainer: ButtonContainer
    private lateinit var socialContainer: ButtonContainer

    private val regularButtons = ArrayList<MainButton>()
    private val clientButtons = ArrayList<MainButton>()

    private val openedAt = System.currentTimeMillis()

    override fun layoutBody() {
        mainContainer = ButtonContainer(vertical = true)
        bottomContainer = ButtonContainer()
        socialContainer = ButtonContainer()

        regularButtons.clear()
        clientButtons.clear()

        regularButtons.add(MainButton("Singleplayer", Tex.SINGLEPLAYER, 0) {
            mcInstance.gui.setScreen(LbSelectWorldScreen(this))
        })
        val multiplayer = MainButton("Multiplayer", Tex.MULTIPLAYER, 1) {
            mcInstance.gui.setScreen(LbMultiplayerScreen(this))
        }
        multiplayer.child = ChildButton("Realms", Tex.REALMS) {
            mcInstance.gui.setScreen(LbMultiplayerScreen(this))
        }
        regularButtons.add(multiplayer)
        regularButtons.add(MainButton("LiquidBounce", Tex.LIQUIDBOUNCE, 2) { toggleButtons() })
        regularButtons.add(MainButton("Options", Tex.OPTIONS, 3) {
            mcInstance.gui.setScreen(OptionsScreen(this, mcInstance.options))
        })

        clientButtons.add(MainButton("Proxy Manager", Tex.PROXYMANAGER, 0) {
            NotifStore.push("Proxy Manager", "No proxies configured yet.")
        })
        clientButtons.add(MainButton("Click GUI", Tex.CLICKGUI, 1) {
            NotifStore.push("Click GUI", "Click GUI is not part of this menu build.")
        })
        clientButtons.add(MainButton("Back", Tex.BACK_LARGE, 2) { toggleButtons() })

        for (b in regularButtons) mainContainer.add(b)
        for (b in clientButtons) {
            b.visible = false
            b.removing = false
        }

        bottomContainer.add(IconTextButton("Exit", Tex.EXIT) { mcInstance.stop() })
        bottomContainer.add(IconTextButton("Basic Mode", Tex.EYE) {
            NotifStore.push("Basic Mode", "Basic mode keeps the vanilla screen beneath.")
        })
        bottomContainer.add(IconButton("Toggle Shader", Tex.CHANGE_BACKGROUND) {
            Backdrop.shaderEnabled = !Backdrop.shaderEnabled
            NotifStore.push(
                "Background",
                if (Backdrop.shaderEnabled) "Aurora shader enabled" else "Aurora shader disabled"
            )
        })

        socialContainer.add(IconButton("Forum", Tex.NODEBB) { openUrl("https://forum.ccbluex.net/") })
        socialContainer.add(IconButton("GitHub", Tex.GITHUB) { openUrl("https://github.com/CCBlueX/LiquidBounce") })
        socialContainer.add(IconButton("Discord", Tex.DISCORD) { openUrl("https://liquidbounce.net/discord") })
        socialContainer.add(IconButton("Twitter", Tex.TWITTER) { openUrl("https://twitter.com/CCBlueX") })
        socialContainer.add(IconButton("YouTube", Tex.YOUTUBE) { openUrl("https://youtube.com/CCBlueX") })
        socialContainer.add(IconTextButton("liquidbounce.net", Tex.WEBSITE) { openUrl("https://liquidbounce.net") })

        ui.add(mainContainer)
        ui.add(bottomContainer)
        ui.add(socialContainer)

        placeContainers()
    }

    private fun placeContainers() {
        mainContainer.measure()
        bottomContainer.measure()
        socialContainer.measure()

        val contentTop = M.PAD + M.LOGO_H + M.HEADER_MARGIN_BOTTOM
        val contentBottom = logicalH - M.PAD
        val bottomRowH = max(bottomContainer.h, socialContainer.h)

        mainContainer.x = M.PAD
        mainContainer.y = contentTop

        bottomContainer.x = M.PAD
        bottomContainer.y = contentBottom - bottomRowH + (bottomRowH - bottomContainer.h) / 2f

        socialContainer.x = M.PAD + logicalW - M.PAD * 2 - socialContainer.w
        socialContainer.y = contentBottom - bottomRowH + (bottomRowH - socialContainer.h) / 2f
    }

    /** `toggleButtons()` — 750ms cross-fade, exactly like Title.svelte's two setTimeout calls. */
    private fun toggleButtons() {
        toggledAt = System.currentTimeMillis()
        if (showingClient) {
            showingClient = false
            for (b in clientButtons) {
                b.visible = true
                b.removing = true
            }
            for (b in regularButtons) {
                b.removing = false
                b.visible = true
            }
        } else {
            showingClient = true
            for (b in regularButtons) {
                b.visible = true
                b.removing = true
            }
            for (b in clientButtons) {
                b.removing = false
                b.visible = true
            }
        }
        mainContainer.clear()
        val set = if (showingClient) clientButtons else regularButtons
        for (b in set) mainContainer.add(b)
        placeContainers()
    }

    private fun settleToggle() {
        if (toggledAt == 0L) return
        if (System.currentTimeMillis() - toggledAt < toggleDelay) return
        if (showingClient) {
            for (b in regularButtons) b.visible = false
        } else {
            for (b in clientButtons) b.visible = false
        }
    }

    override fun tick() {
        super.tick()
        settleToggle()
    }

    private fun headerOffset(): Float {
        val t = ((System.currentTimeMillis() - openedAt) / 700f).coerceIn(0f, 1f)
        return (1f - Ease.quintOut(t)) * -100f
    }

    private fun bottomOffset(): Float {
        val t = ((System.currentTimeMillis() - openedAt) / 700f).coerceIn(0f, 1f)
        return (1f - Ease.quintOut(t)) * 100f
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        mouseLX = mouseX / zoom
        mouseLY = mouseY / zoom

        g.pose().pushMatrix()
        g.pose().scale(zoom, zoom)

        Backdrop.drawBackground(g, logicalW.toInt(), logicalH.toInt())
        drawConfetti(g)

        if (ready) drawTitleHeader(g)

        val off = bottomOffset()
        bottomContainer.y += off
        socialContainer.y += off
        ui.draw(g, mouseLX, mouseLY)
        bottomContainer.y -= off
        socialContainer.y -= off

        g.pose().popMatrix()
        if (ready) drawNotifications(g)
    }

    private fun drawTitleHeader(g: GuiGraphicsExtractor) {
        val fly = headerOffset()
        val logoY = M.PAD + fly
        if (isAnniversary()) drawAnimatedLogo(g, M.PAD, logoY) else
            D.image(g, Tex.LOGO, M.PAD, logoY, M.LOGO_W, M.LOGO_H, 512, 192)

        drawAccount(g, M.PAD + logicalW - M.PAD * 2 - M.ACCOUNT_W, M.PAD + fly, mcInstance.font)
    }

    /** `AnimatedLogo.svelte` — logo + 2px divider + "Thank you for N Years / of LiquidBounce!" */
    private fun drawAnimatedLogo(g: GuiGraphicsExtractor, x: Float, y: Float) {
        val font = mcInstance.font
        val pulse = (sin((System.currentTimeMillis() % 9100L) / 9100.0 * Math.PI * 2)).toFloat()
        D.circle(g, x + 48f, y + 49f, 54f, D.withAlpha(0x4677FF, 40 + 20 * (pulse + 1f) / 2f))
        D.circle(g, x + 48f, y + 49f, 34f, D.withAlpha(0xFC4130, 16 + 10 * (1f - pulse) / 2f))
        D.image(g, Tex.LOGO, x, y, M.LOGO_W, M.LOGO_H, 512, 192)

        val divX = x + M.LOGO_W + 30f
        D.rect(g, divX, y, 2f, M.LOGO_H, T.TEXT)

        val years = LocalDate.now().year - 2016
        val msgX = divX + 30f
        D.text(g, font, "Thank you for $years Years", msgX, y + 8f, T.TEXT, 25f)
        D.text(g, font, "of LiquidBounce!", msgX, y + 48f, T.TEXT, 25f)
    }

    /** `ConfettiBackground.svelte` — 84 pieces, 42 columns, golden-ratio delays. */
    private fun drawConfetti(g: GuiGraphicsExtractor) {
        if (!isAnniversary()) return
        val now = System.currentTimeMillis() / 1000.0
        val golden = 0.61803398875
        for (i in 0 until 84) {
            val shape = rnd(i + 11)
            val baseSize = 6f + rnd(i + 23) * 12f
            val isRound = shape > 0.7
            val isStreamer = shape < 0.2
            val columnWidth = 116f / 42f
            val column = i % 42
            val layer = i / 42
            val bw = if (isRound) baseSize else if (isStreamer) baseSize * 0.45f else baseSize * 0.72f
            val bh = if (isRound) baseSize else if (isStreamer) baseSize * 1.8f else baseSize * 1.24f
            val duration = 7.0 + rnd(i + 41) * 9.0
            val delayProgress = frac(i * golden + rnd(i + 53) * 0.2)
            val delay = -(delayProgress * duration)
            val leftJitter = (rnd(i + 67) - 0.5) * columnWidth * 0.8
            val leftPct = -8f + (column + 0.5f) * columnWidth + leftJitter
            val driftDir = if ((column + layer) % 2 == 0) -1 else 1
            val drift = driftDir * (34f + rnd(i + 79) * 74f) + (rnd(i + 149) - 0.5f) * 12f
            val opacity = 0.45f + rnd(i + 83) * 0.35f
            val flip = 240f + rnd(i + 97) * 540f
            val spinDuration = 1.8 + rnd(i + 101) * 2.8
            val color = T.CONFETTI[(rnd(i + 113) * T.CONFETTI.size).toInt().coerceIn(0, T.CONFETTI.size - 1)]
            val scale = 0.75f + rnd(i + 127) * 0.85f

            val phase = frac((now - delay) / duration)
            val px = (leftPct / 100f) * logicalW + drift * phase
            val py = (-0.2f + 1.4f * phase) * logicalH

            val spinPhase = frac((now - delay) / spinDuration)
            val rot = flip * spinPhase
            val sx = when {
                spinPhase < 0.25f -> 1f + (0.68f - 1f) * (spinPhase / 0.25f).toFloat()
                spinPhase < 0.5f -> 0.68f + (0.48f - 0.68f) * ((spinPhase - 0.25f) / 0.25f).toFloat()
                spinPhase < 0.75f -> 0.48f + (0.72f - 0.48f) * ((spinPhase - 0.5f) / 0.25f).toFloat()
                else -> 0.72f + (1f - 0.72f) * ((spinPhase - 0.75f) / 0.25f).toFloat()
            }

            g.pose().pushMatrix()
            g.pose().translate(px.toFloat(), py.toFloat())
            g.pose().rotate(Math.toRadians(rot.toDouble()).toFloat())
            g.pose().scale(sx * scale, scale)
            val c = D.withAlpha(color, 255 * opacity)
            if (isRound) {
                D.circle(g, 0f, 0f, bw / 2f, c)
            } else {
                D.rrect(g, -bw / 2f, -bh / 2f, bw, bh, if (isStreamer) 999f else 2f, c)
            }
            g.pose().popMatrix()
        }
    }

    private fun rnd(seed: Int): Float {
        val v = sin(seed * 12.9898) * 43758.5453
        return (v - floor(v)).toFloat()
    }

    private fun frac(v: Double): Double = v - floor(v)

    private fun openUrl(url: String) {
        try {
            com.mojang.blaze3d.Blaze3D.openUri(java.net.URI(url))
        } catch (t: Throwable) {
            NotifStore.push("Browser", "Could not open $url", true)
        }
    }
}