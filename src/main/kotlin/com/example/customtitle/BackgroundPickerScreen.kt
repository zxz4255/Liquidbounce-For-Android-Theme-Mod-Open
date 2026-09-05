package com.example.customtitle

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import kotlin.math.roundToInt

private val PICKER_BG = ARGB.color(235, 12, 12, 15)
private val ROW_BG = ARGB.color(160, 26, 26, 31)
private val ROW_HOVER = ARGB.color(200, 40, 40, 47)
private val ROW_SELECTED = ARGB.color(220, 46, 120, 220)
private val TEXT_MAIN = -1
private val TEXT_DIM = ARGB.color(255, 165, 165, 172)
private val ACCENT = ARGB.color(255, 90, 170, 220)

private const val ROW_HEIGHT = 40
private const val THUMB_SIZE = 32
private const val LIST_MARGIN = 24

/**
 * Lets the player pick a custom background image from <gameDirectory>/background/.
 * Resolution shown per entry comes straight from the decoded file (see CustomBackgroundManager),
 * not a hardcoded constant, since these are arbitrary user-supplied images.
 */
class BackgroundPickerScreen(private val previous: CustomTitleScreen) : Screen(Component.literal("Select Background")) {

    private var scrollOffset = 0

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        context.fill(0, 0, width, height, PICKER_BG)

        context.text(font, "Select Background".asPlainText(), LIST_MARGIN, 16, TEXT_MAIN, false)

        val refreshLabel = "Refresh"
        val refreshW = CustomFont.width(font, refreshLabel) + 16
        val refreshX = width - LIST_MARGIN - refreshW
        val refreshHovered = mouseX in refreshX..(refreshX + refreshW) && mouseY in 12..(12 + 18)
        context.fill(refreshX, 12, refreshX + refreshW, 12 + 18, if (refreshHovered) ACCENT else ROW_BG)
        context.text(font, refreshLabel.asPlainText(), refreshX + 8, 12 + (18 - font.lineHeight) / 2, TEXT_MAIN, false)

        val entries = CustomBackgroundManager.entries()
        val listTop = 44

        if (entries.isEmpty()) {
            context.text(
                font,
                "No images found in:".asPlainText(),
                LIST_MARGIN, listTop, TEXT_DIM, false
            )
            context.text(
                font,
                CustomBackgroundManager.backgroundDir().absolutePath.asPlainText(),
                LIST_MARGIN, listTop + 14, TEXT_DIM, false
            )
            context.text(
                font,
                "Drop .png/.jpg files there, then click Refresh.".asPlainText(),
                LIST_MARGIN, listTop + 32, TEXT_DIM, false
            )
            return
        }

        var y = listTop - scrollOffset
        val listWidth = width - LIST_MARGIN * 2

        // "None" row - clears the custom selection, falls back to whatever mode was active before.
        run {
            if (y + ROW_HEIGHT >= listTop && y <= height) {
                val hovered = mouseX in LIST_MARGIN..(LIST_MARGIN + listWidth) && mouseY in y..(y + ROW_HEIGHT)
                val isSelected = CustomBackgroundManager.selected == null
                context.fill(
                    LIST_MARGIN, y, LIST_MARGIN + listWidth, y + ROW_HEIGHT,
                    if (isSelected) ROW_SELECTED else if (hovered) ROW_HOVER else ROW_BG
                )
                context.text(
                    font, "None (use previous mode)".asPlainText(),
                    LIST_MARGIN + 12, y + (ROW_HEIGHT - font.lineHeight) / 2, TEXT_MAIN, false
                )
            }
            y += ROW_HEIGHT
        }

        for (entry in entries) {
            if (y + ROW_HEIGHT < listTop || y > height) {
                y += ROW_HEIGHT
                continue
            }

            val hovered = mouseX in LIST_MARGIN..(LIST_MARGIN + listWidth) && mouseY in y..(y + ROW_HEIGHT)
            val isSelected = CustomBackgroundManager.selected?.file == entry.file
            context.fill(
                LIST_MARGIN, y, LIST_MARGIN + listWidth, y + ROW_HEIGHT,
                if (isSelected) ROW_SELECTED else if (hovered) ROW_HOVER else ROW_BG
            )

            val thumbX = LIST_MARGIN + 4
            val thumbY = y + (ROW_HEIGHT - THUMB_SIZE) / 2
            blitCoverFit(context, entry.identifier, entry.width, entry.height, thumbX, thumbY, THUMB_SIZE, THUMB_SIZE)

            val textX = thumbX + THUMB_SIZE + 12
            context.text(
                font, entry.displayName.asPlainText(),
                textX, y + 6, TEXT_MAIN, false
            )
            context.text(
                font, "${entry.width} x ${entry.height}".asPlainText(),
                textX, y + 6 + font.lineHeight + 2, TEXT_DIM, false
            )

            y += ROW_HEIGHT
        }
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mouseX = click.x.toInt()
        val mouseY = click.y.toInt()

        val refreshLabel = "Refresh"
        val refreshW = CustomFont.width(font, refreshLabel) + 16
        val refreshX = width - LIST_MARGIN - refreshW
        if (mouseX in refreshX..(refreshX + refreshW) && mouseY in 12..(12 + 18)) {
            CustomBackgroundManager.rescan()
            return true
        }

        val entries = CustomBackgroundManager.entries()
        val listTop = 44
        val listWidth = width - LIST_MARGIN * 2
        var y = listTop - scrollOffset

        // "None" row
        if (mouseX in LIST_MARGIN..(LIST_MARGIN + listWidth) && mouseY in y..(y + ROW_HEIGHT)) {
            CustomBackgroundManager.clearSelection()
            previous.setBackgroundMode(BackgroundMode.IMAGE)
            minecraft.gui.setScreen(previous)
            return true
        }
        y += ROW_HEIGHT

        for (entry in entries) {
            if (mouseX in LIST_MARGIN..(LIST_MARGIN + listWidth) && mouseY in y..(y + ROW_HEIGHT)) {
                CustomBackgroundManager.select(entry)
                previous.setBackgroundMode(BackgroundMode.CUSTOM)
                minecraft.gui.setScreen(previous)
                return true
            }
            y += ROW_HEIGHT
        }

        return super.mouseClicked(click, doubled)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double
    ): Boolean {
        scrollOffset = (scrollOffset - (verticalAmount * ROW_HEIGHT).toInt()).coerceAtLeast(0)
        return true
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (input.key == InputConstants.KEY_ESCAPE) {
            minecraft.gui.setScreen(previous)
            return true
        }
        return super.keyPressed(input)
    }

    override fun isPauseScreen() = false
}

/** All screen text flows through here → styled with the custom TTF font (see CustomFont) */
private fun String.asPlainText(): Component = CustomFont.styled(this)

/**
 * Cover-fit blit for an arbitrary texW x texH image into a dstW x dstH box - same math as
 * MenuBackground.draw / CustomTitleScreen.drawImageBackground, generalized so it works for
 * dynamically-loaded images whose resolution isn't known at compile time.
 */
fun blitCoverFit(
    context: GuiGraphicsExtractor,
    texture: net.minecraft.resources.Identifier,
    texW: Int,
    texH: Int,
    dstX: Int,
    dstY: Int,
    dstW: Int,
    dstH: Int
) {
    val w = dstW.coerceAtLeast(1)
    val h = dstH.coerceAtLeast(1)
    val dstAspect = w.toFloat() / h
    val texAspect = texW.toFloat() / texH.coerceAtLeast(1)

    val srcW: Int
    val srcH: Int
    val srcU: Float
    val srcV: Float
    if (dstAspect > texAspect) {
        srcW = texW
        srcH = (texW / dstAspect).roundToInt().coerceAtMost(texH).coerceAtLeast(1)
        srcU = 0f
        srcV = (texH - srcH) / 2f
    } else {
        srcH = texH
        srcW = (texH * dstAspect).roundToInt().coerceAtMost(texW).coerceAtLeast(1)
        srcU = (texW - srcW) / 2f
        srcV = 0f
    }

    context.blit(RenderPipelines.GUI_TEXTURED, texture, dstX, dstY, srcU, srcV, w, h, srcW, srcH, texW, texH)
}
