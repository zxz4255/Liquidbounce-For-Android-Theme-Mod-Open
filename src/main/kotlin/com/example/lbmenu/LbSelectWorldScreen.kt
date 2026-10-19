package com.example.lbmenu

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows
import net.minecraft.network.chat.Component
import net.minecraft.world.Difficulty
import net.minecraft.world.level.GameType
import net.minecraft.world.level.storage.LevelStorageSource
import net.minecraft.world.level.storage.LevelSummary
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * `routes/menu/singleplayer/Singleplayer.svelte`
 * OptionBar(Game Mode | Difficulty) + MenuList + BottomButtonWrapper(Add | Back)
 */
class LbSelectWorldScreen(private val parent: Screen?) :
    MenuScreen(Component.literal("Singleplayer")) {

    private var gameModes: MutableSet<String> =
        linkedSetOf("Survival", "Creative", "Adventure", "Spectator")
    private var difficulties: MutableSet<String> =
        linkedSetOf("Peaceful", "Easy", "Normal", "Hard")

    private lateinit var list: MenuList
    private lateinit var bottomButtons: ButtonContainer
    private lateinit var backButtons: ButtonContainer
    private lateinit var gameModeSelect: Select
    private lateinit var difficultySelect: Select

    private var worlds: List<LevelSummary> = emptyList()
    private var rows: List<LevelSummary> = emptyList()

    override fun layoutBody() {
        list = MenuList()

        gameModeSelect = Select(
            "Game Mode",
            listOf("Survival", "Creative", "Adventure", "Spectator"),
            "",
            multi = true,
            values = gameModes
        ) { refresh() }
        difficultySelect = Select(
            "Difficulty",
            listOf("Peaceful", "Easy", "Normal", "Hard"),
            "",
            multi = true,
            values = difficulties
        ) { refresh() }

        bottomButtons = ButtonContainer().apply {
            add(IconTextButton("Add", Tex.PLUS_CIRCLE) {
                val screen = this@LbSelectWorldScreen
                CreateWorldScreen.openFresh(mcInstance) {
                    mcInstance.gui.setScreen(screen)
                    refresh()
                }
            })
        }
        backButtons = ButtonContainer().apply {
            add(IconTextButton("Back", Tex.BACK) {
                mcInstance.gui.setScreen(parent ?: LbTitleScreen())
            })
        }

        ui.add(list)
        ui.add(bottomButtons)
        ui.add(backButtons)

        refresh()
    }

    private fun refresh() {
        worlds = try {
            val source: LevelStorageSource = mcInstance.levelSource
            val candidates = source.findLevelCandidates()
            source.loadLevelSummaries(candidates).join()
                .sortedWith(compareByDescending<LevelSummary> { it.lastPlayed })
        } catch (t: Throwable) {
            emptyList()
        }
        rows = worlds.filter { w ->
            val mode = capitalize(w.gameMode.serializedName)
            val diff = capitalize(w.settings.difficultySettings().difficulty().serializedName)
            gameModes.contains(mode) && difficulties.contains(diff)
        }

        list.items.clear()
        for (w in rows) {
            val item = MenuListItem(
                title = w.levelName,
                subtitle = "${w.levelId}  (${formatDate(w.lastPlayed)})",
                tags = listOf(
                    capitalize(w.gameMode.serializedName),
                    capitalize(w.settings.difficultySettings().difficulty().serializedName),
                    "Minecraft ${w.worldVersionName.string}"
                ),
                iconTexture = DynamicIcons.fromFile("world_" + w.levelId, w.icon)
            )
            item.onDoubleClick = { openWorld(w.levelId) }
            item.active.add(MenuListItem.RowAction("Delete", Tex.TRASH) { confirmDelete(w) })
            item.active.add(MenuListItem.RowAction("Edit", Tex.PEN_2) { editWorld(w.levelId) })
            item.always.add(MenuListItem.RowAction("Open", Tex.PLAY) { openWorld(w.levelId) })
            list.items.add(item)
        }
    }

    private fun capitalize(s: String): String =
        if (s.isEmpty()) s else s[0].uppercaseChar() + s.substring(1)

    private fun formatDate(ms: Long): String =
        SimpleDateFormat("yyyy/MM/dd h:mm:ss a", Locale.US).format(Date(ms))

    private fun openWorld(levelId: String) {
        try {
            mcInstance.createWorldOpenFlows().openWorld(levelId) {
                mcInstance.gui.setScreen(this)
            }
        } catch (t: Throwable) {
            NotifStore.push("World", "Could not open $levelId", true)
        }
    }

    private fun editWorld(levelId: String) {
        try {
            val access = mcInstance.levelSource.createAccess(levelId)
            val screen = EditWorldScreen.create(mcInstance, access) { _ ->
                mcInstance.gui.setScreen(this)
                refresh()
            }
            mcInstance.gui.setScreen(screen)
        } catch (t: Throwable) {
            NotifStore.push("World", "Could not edit $levelId", true)
        }
    }

    private fun confirmDelete(w: LevelSummary) {
        mcInstance.gui.setScreen(
            ConfirmScreen(
                { confirmed ->
                    if (confirmed) deleteWorld(w.levelId)
                    mcInstance.gui.setScreen(this)
                    refresh()
                },
                Component.literal("Delete world?"),
                Component.literal("'${w.levelName}' will be lost forever!"),
                Component.literal("Delete"),
                Component.literal("Cancel")
            )
        )
    }

    private fun deleteWorld(levelId: String) {
        try {
            val access = mcInstance.levelSource.createAccess(levelId)
            try {
                access.deleteLevel()
            } finally {
                access.safeClose()
            }
        } catch (t: Throwable) {
            NotifStore.push("World", "Could not delete $levelId", true)
        }
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        mouseLX = mouseX / zoom
        mouseLY = mouseY / zoom

        g.pose().pushMatrix()
        g.pose().scale(zoom, zoom)

        Backdrop.drawBackground(g, logicalW.toInt(), logicalH.toInt())
        if (ready) drawHeader(g)

        val barY = M.PAD + M.LOGO_H + M.HEADER_MARGIN_BOTTOM
        val barX = M.PAD
        D.rrect(g, barX, barY, logicalW - M.PAD * 2, 60f, 5f, T.MENU_68)

        var cx = barX + M.OPTION_BAR_PAD_H
        gameModeSelect.measure()
        gameModeSelect.x = cx
        gameModeSelect.y = barY + (60f - gameModeSelect.h) / 2f
        gameModeSelect.hovered = gameModeSelect.contains(mouseLX, mouseLY)
        gameModeSelect.draw(g, mouseLX, mouseLY, 0.016f)
        cx += gameModeSelect.w + M.OPTION_BAR_GAP

        difficultySelect.measure()
        difficultySelect.x = cx
        difficultySelect.y = barY + (60f - difficultySelect.h) / 2f
        difficultySelect.hovered = difficultySelect.contains(mouseLX, mouseLY)
        difficultySelect.draw(g, mouseLX, mouseLY, 0.016f)

        val listY = barY + 60f + M.OPTION_BAR_MARGIN_BOTTOM
        val bottomY = logicalH - M.PAD
        val rowH = max(bottomButtons.h, backButtons.h)
        list.x = M.PAD
        list.y = listY
        list.w = logicalW - M.PAD * 2
        list.h = bottomY - rowH - M.LIST_MARGIN_BOTTOM - listY
        list.draw(g, mouseLX, mouseLY, 0.016f)

        bottomButtons.measure()
        backButtons.measure()
        bottomButtons.x = M.PAD
        bottomButtons.y = bottomY - rowH + (rowH - bottomButtons.h) / 2f
        bottomButtons.draw(g, mouseLX, mouseLY, 0.016f)
        backButtons.x = M.PAD + logicalW - M.PAD * 2 - backButtons.w
        backButtons.y = bottomY - rowH + (rowH - backButtons.h) / 2f
        backButtons.draw(g, mouseLX, mouseLY, 0.016f)

        g.pose().popMatrix()
        if (ready) drawNotifications(g)
    }

    override fun mouseClicked(event: net.minecraft.client.input.MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = (event.x / zoom).toFloat()
        val my = (event.y / zoom).toFloat()
        if (event.button() != 0) return super.mouseClicked(event, doubleClick)
        if (gameModeSelect.mouseClicked(mx, my, 0)) return true
        if (difficultySelect.mouseClicked(mx, my, 0)) return true
        if (list.mouseClicked(mx, my, 0)) return true
        if (bottomButtons.mouseClicked(mx, my, 0)) return true
        if (backButtons.mouseClicked(mx, my, 0)) return true
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseReleased(event: net.minecraft.client.input.MouseButtonEvent): Boolean {
        val mx = (event.x / zoom).toFloat()
        val my = (event.y / zoom).toFloat()
        if (event.button() != 0) return super.mouseReleased(event)
        gameModeSelect.mouseReleased(mx, my, 0)
        difficultySelect.mouseReleased(mx, my, 0)
        if (list.mouseReleased(mx, my, 0)) return true
        if (bottomButtons.mouseReleased(mx, my, 0)) return true
        if (backButtons.mouseReleased(mx, my, 0)) return true
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(x: Double, y: Double, sx: Double, sy: Double): Boolean {
        val mx = (x / zoom).toFloat()
        val my = (y / zoom).toFloat()
        if (list.mouseScrolled(mx, my, sy.toFloat())) return true
        return super.mouseScrolled(x, y, sx, sy)
    }
}