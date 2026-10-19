package com.example.lbmenu

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.network.chat.Component
import kotlin.math.max

/**
 * `routes/menu/multiplayer/Multiplayer.svelte`
 * OptionBar(Online only | Auto Config | Version) + MenuList + BottomButtonWrapper.
 */
class LbMultiplayerScreen(private val parent: Screen?) :
    MenuScreen(Component.literal("Multiplayer")) {

    private var onlineOnly = false
    private var autoConfig = false

    private lateinit var list: MenuList
    private lateinit var bottomButtons: ButtonContainer
    private lateinit var backButtons: ButtonContainer
    private lateinit var onlineSwitch: SwitchSetting
    private lateinit var autoSwitch: SwitchSetting
    private lateinit var viaButton: ButtonSetting

    private var addModal: AddServerModal? = null
    private var directModal: DirectConnectModal? = null
    private var editModal: EditServerModal? = null

    private var rows: List<ServerRow> = emptyList()
    private var lastPoll = 0L

    override fun layoutBody() {
        ServerRepository.ensureLoaded()

        list = MenuList()

        onlineSwitch = SwitchSetting("Online only", onlineOnly) {
            onlineOnly = !onlineOnly
            refresh()
        }
        autoSwitch = SwitchSetting("Auto Config", autoConfig) { autoConfig = !autoConfig }
        viaButton = ButtonSetting("Install ViaFabricPlus", {
            NotifStore.push("ViaFabricPlus", "Version selection is unavailable in this build.")
        })

        bottomButtons = ButtonContainer().apply {
            add(IconTextButton("Add", Tex.PLUS_CIRCLE) { openAdd() })
            add(IconTextButton("Direct", Tex.PLANE) { openDirect() })
            add(IconTextButton("Refresh", Tex.REFRESH) { refresh() })
        }
        backButtons = ButtonContainer().apply {
            add(IconTextButton("Back", Tex.BACK) {
                mcInstance.gui.setScreen(parent ?: LbTitleScreen())
            })
        }

        addModal = AddServerModal(this) { name, address, pack ->
            ServerRepository.add(name, address, pack)
            refresh()
        }
        directModal = DirectConnectModal(this) { address ->
            ServerRepository.connect(address, this)
        }

        ui.add(list)
        ui.add(bottomButtons)
        ui.add(backButtons)
        ui.add(onlineSwitch)
        ui.add(autoSwitch)
        ui.add(viaButton)

        refresh()
    }

    private fun refresh() {
        rows = ServerRepository.rows(includeLan = true, onlineOnly = onlineOnly)
        list.items.clear()
        for (row in rows) {
            val item = MenuListItem(
                title = row.name,
                subtitle = if (row.lan) "LAN server" else if (row.ping > 0) "${row.ping}ms" else "Can't connect to server",
                imageText = if (row.online) "${row.ping}ms" else null,
                imageTextBg = pingColor(row.ping),
                iconTexture = if (row.online) DynamicIcons.fromBytes("srv_" + row.address, row.data.iconBytes) else null
            )
            item.tags = buildList {
                if (row.lan) add("LAN")
                if (row.online) {
                    row.data.players?.let { add("${it.online}/${it.max} Players") }
                    add(row.data.version.string)
                }
            }
            item.onDoubleClick = { ServerRepository.connect(row.address, this) }
            if (!row.lan) {
                item.active.add(MenuListItem.RowAction("Remove", Tex.TRASH) {
                    val idx = ServerRepository.servers().indexOfFirst { it.ip == row.address }
                    if (idx >= 0) ServerRepository.remove(idx)
                    refresh()
                })
                item.active.add(MenuListItem.RowAction("Edit", Tex.PEN_2) { openEdit(row) })
            }
            item.always.add(MenuListItem.RowAction("Join", Tex.PLAY) {
                ServerRepository.connect(row.address, this)
            })
            list.items.add(item)
        }
        for (row in rows) ServerRepository.ping(row) { }
    }

    private fun pingColor(ping: Long): Int = when {
        ping < 0 -> T.PING_BAD
        ping <= 50 -> T.PING_GOOD
        ping <= 100 -> T.PING_MID
        else -> T.PING_BAD
    }

    private fun openAdd() {
        addModal?.visible = true
        addModal?.layout(logicalW, logicalH)
    }

    private fun openDirect() {
        directModal?.visible = true
        directModal?.layout(logicalW, logicalH)
    }

    private fun openEdit(row: ServerRow) {
        val idx = ServerRepository.servers().indexOfFirst { it.ip == row.address }
        if (idx < 0) return
        val modal = EditServerModal(this, row.data) { name, address, pack ->
            ServerRepository.edit(idx, name, address, pack)
            refresh()
        }
        editModal = modal
        modal.visible = true
        modal.layout(logicalW, logicalH)
    }

    override fun tick() {
        super.tick()
        ServerRepository.tickPinger()
        if (System.currentTimeMillis() - lastPoll > 3000L) {
            lastPoll = System.currentTimeMillis()
            val before = rows.map { it.address + ":" + it.ping }.joinToString(",")
            val after = ServerRepository.rows(includeLan = true, onlineOnly = onlineOnly)
                .map { it.address + ":" + it.ping }.joinToString(",")
            if (before != after) refresh()
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
        onlineSwitch.measure()
        onlineSwitch.x = cx
        onlineSwitch.y = barY + (60f - onlineSwitch.h) / 2f
        onlineSwitch.hovered = onlineSwitch.contains(mouseLX, mouseLY)
        onlineSwitch.draw(g, mouseLX, mouseLY, 0.016f)
        cx += onlineSwitch.w + M.OPTION_BAR_GAP

        D.rect(g, cx, barY + M.OPTION_BAR_PAD_V, 4f, 60f - M.OPTION_BAR_PAD_V * 2, T.MENU_36)
        cx += 4f + M.OPTION_BAR_GAP

        autoSwitch.measure()
        autoSwitch.x = cx
        autoSwitch.y = barY + (60f - autoSwitch.h) / 2f
        autoSwitch.hovered = autoSwitch.contains(mouseLX, mouseLY)
        autoSwitch.draw(g, mouseLX, mouseLY, 0.016f)
        cx += autoSwitch.w + M.OPTION_BAR_GAP

        viaButton.measure()
        viaButton.x = cx
        viaButton.y = barY + (60f - viaButton.h) / 2f
        viaButton.hovered = viaButton.contains(mouseLX, mouseLY)
        viaButton.draw(g, mouseLX, mouseLY, 0.016f)

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

        editModal?.let { if (it.visible) it.draw(g, mouseLX, mouseLY, 0.016f) }
        addModal?.let { if (it.visible) it.draw(g, mouseLX, mouseLY, 0.016f) }
        directModal?.let { if (it.visible) it.draw(g, mouseLX, mouseLY, 0.016f) }

        g.pose().popMatrix()
        if (ready) drawNotifications(g)
    }

    private fun activeModal(): Modal? {
        editModal?.let { if (it.visible) return it }
        addModal?.let { if (it.visible) return it }
        directModal?.let { if (it.visible) return it }
        return null
    }

    override fun mouseClicked(event: net.minecraft.client.input.MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = (event.x / zoom).toFloat()
        val my = (event.y / zoom).toFloat()
        if (event.button() != 0) return super.mouseClicked(event, doubleClick)

        activeModal()?.let {
            it.mouseClicked(mx, my, 0)
            return true
        }
        if (onlineSwitch.mouseClicked(mx, my, 0)) return true
        if (autoSwitch.mouseClicked(mx, my, 0)) return true
        if (viaButton.mouseClicked(mx, my, 0)) return true
        if (list.mouseClicked(mx, my, 0)) return true
        if (bottomButtons.mouseClicked(mx, my, 0)) return true
        if (backButtons.mouseClicked(mx, my, 0)) return true
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseReleased(event: net.minecraft.client.input.MouseButtonEvent): Boolean {
        val mx = (event.x / zoom).toFloat()
        val my = (event.y / zoom).toFloat()
        if (event.button() != 0) return super.mouseReleased(event)
        activeModal()?.let {
            it.mouseReleased(mx, my, 0)
            return true
        }
        if (viaButton.mouseReleased(mx, my, 0)) return true
        if (list.mouseReleased(mx, my, 0)) return true
        if (bottomButtons.mouseReleased(mx, my, 0)) return true
        if (backButtons.mouseReleased(mx, my, 0)) return true
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(x: Double, y: Double, sx: Double, sy: Double): Boolean {
        val mx = (x / zoom).toFloat()
        val my = (y / zoom).toFloat()
        activeModal()?.let { return it.mouseScrolled(mx, my, sy.toFloat()) }
        if (list.mouseScrolled(mx, my, sy.toFloat())) return true
        return super.mouseScrolled(x, y, sx, sy)
    }

    override fun charTyped(event: net.minecraft.client.input.CharacterEvent): Boolean {
        val cp = event.codepointAsString().first()
        activeModal()?.let { return it.charTyped(cp) }
        return super.charTyped(event)
    }

    override fun keyPressed(event: net.minecraft.client.input.KeyEvent): Boolean {
        activeModal()?.let { return it.keyPressed(event) }
        return super.keyPressed(event)
    }
}