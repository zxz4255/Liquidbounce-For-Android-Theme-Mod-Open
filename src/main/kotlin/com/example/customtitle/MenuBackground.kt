package com.example.customtitle

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import kotlin.math.roundToInt

/**
 * Shared cover-fit background.png for title + nested menu screens
 * (SelectWorld, Multiplayer, Options, …).
 */
object MenuBackground {
    private val MOD_ID = "customtitle"
    val TEXTURE: Identifier = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/background.png")
    private const val SRC_W = 2560
    private const val SRC_H = 1440

    fun draw(context: GuiGraphicsExtractor, screenWidth: Int, screenHeight: Int) {
        val w = screenWidth.coerceAtLeast(1)
        val h = screenHeight.coerceAtLeast(1)
        val screenAspect = w.toFloat() / h
        val texAspect = SRC_W.toFloat() / SRC_H

        val srcW: Int
        val srcH: Int
        val srcU: Float
        val srcV: Float
        if (screenAspect > texAspect) {
            srcW = SRC_W
            srcH = (SRC_W / screenAspect).roundToInt().coerceAtMost(SRC_H)
            srcU = 0f
            srcV = ((SRC_H - srcH) / 2f)
        } else {
            srcH = SRC_H
            srcW = (SRC_H * screenAspect).roundToInt().coerceAtMost(SRC_W)
            srcU = ((SRC_W - srcW) / 2f)
            srcV = 0f
        }

        context.blit(
            RenderPipelines.GUI_TEXTURED,
            TEXTURE,
            0, 0,
            srcU, srcV,
            w, h,
            srcW, srcH,
            SRC_W, SRC_H
        )
        // slight darken so vanilla widgets stay readable
        context.fill(0, 0, w, h, ARGB.color(50, 0, 0, 0))
    }

    /** Screens that should keep vanilla look (ingame, overlays, etc.). */
    fun shouldApply(screen: Screen): Boolean {
        if (screen is CustomTitleScreen) return false // draws its own modes
        val name = screen.javaClass.name
        // Apply to common menu hierarchy opened from title
        return name.contains("SelectWorld") ||
            name.contains("JoinMultiplayer") ||
            name.contains("OptionsScreen") ||
            name.contains("SafetyScreen") ||
            name.contains("CreateWorld") ||
            name.contains("EditWorld") ||
            name.contains("LanguageSelect") ||
            name.contains("VideoSettings") ||
            name.contains("ControlsScreen") ||
            name.contains("SoundOptions") ||
            name.contains("ChatOptions") ||
            name.contains("Accessibility") ||
            name.contains("SkinCustomization") ||
            name.contains("PackSelection") ||
            name.contains("Telemetry") ||
            name.contains("CreditsAndAttribution") ||
            name.contains("OnlineOptions") ||
            name.contains("WorldOptions") ||
            name.contains("ServerSelection") ||
            name.contains("DirectJoin") ||
            name.contains("AddServer") ||
            name.contains("EditServer") ||
            name.contains("ConfirmScreen") ||
            name.contains("GenericConfirm") ||
            name.contains("ProgressScreen") ||
            name.contains("LevelLoading") ||
            name.contains("ReceivingLevel") ||
            // package-level: all net.minecraft.client.gui.screens.* menus while not in-game
            (name.startsWith("net.minecraft.client.gui.screens") &&
                !name.contains("inventory") &&
                !name.contains("Inventory") &&
                !name.contains("ChatScreen") &&
                !name.contains("DeathScreen") &&
                !name.contains("PauseScreen") &&
                !name.contains("TitleScreen"))
    }
}
