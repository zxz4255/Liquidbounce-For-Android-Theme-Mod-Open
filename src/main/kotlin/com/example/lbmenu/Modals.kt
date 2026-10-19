package com.example.lbmenu

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.network.chat.Component

/** `multiplayer/AddServerModal.svelte` */
class AddServerModal(
    private val parent: Screen,
    private val onAdd: (String, String, ServerData.ServerPackStatus) -> Unit
) : Modal("Add Server") {

    private val nameInput = IconTextInput("Name", Tex.INFO, "Minecraft Server")
    private val addressInput = IconTextInput("Address", Tex.SERVER, "")
    private val packSelect = Select(
        "Server Resource Packs",
        listOf("Prompt", "Enabled", "Disabled"),
        "Prompt"
    )
    private val addButton = ButtonSetting("Add Server", { submit() }, inset = true, listenForEnter = true)

    init {
        ui.add(nameInput)
        ui.add(addressInput)
        ui.add(packSelect)
        ui.add(addButton)
        addButton.disabledState = true
        nameInput.onChange = ::validate
        addressInput.onChange = ::validate
    }

    private fun validate() {
        addButton.disabledState = nameInput.value.isEmpty() || addressInput.value.isEmpty()
    }

    private fun submit() {
        if (addButton.disabledState) return
        val pack = when (packSelect.value) {
            "Enabled" -> ServerData.ServerPackStatus.ENABLED
            "Disabled" -> ServerData.ServerPackStatus.DISABLED
            else -> ServerData.ServerPackStatus.PROMPT
        }
        onAdd(nameInput.value.trim(), addressInput.value.trim(), pack)
        cleanUp()
        close()
    }

    private fun cleanUp() {
        nameInput.value = "Minecraft Server"
        addressInput.value = ""
        packSelect.value = "Prompt"
        validate()
    }

    override fun charTyped(cp: Char): Boolean {
        if (!visible) return false
        if (nameInput.charTyped(cp)) return true
        if (addressInput.charTyped(cp)) return true
        return false
    }

    override fun keyPressed(event: net.minecraft.client.input.KeyEvent): Boolean {
        if (!visible) return false
        if (nameInput.keyPressed(event)) return true
        if (addressInput.keyPressed(event)) return true
        if (event.isEscape) {
            close()
            return true
        }
        if (event.shortcutKey() == 257 || event.shortcutKey() == 335) {
            submit()
            return true
        }
        return false
    }
}

/** `multiplayer/DirectConnectModal.svelte` — address is persisted in options.txt-style storage. */
class DirectConnectModal(
    private val parent: Screen,
    private val onConnect: (String) -> Unit
) : Modal("Direct Connection") {

    private val addressInput = IconTextInput("Address", Tex.SERVER, Persist.directAddress())
    private val joinButton = ButtonSetting("Join Server", { submit() }, inset = true, listenForEnter = true)

    init {
        ui.add(addressInput)
        ui.add(joinButton)
        joinButton.disabledState = addressInput.value.trim().isEmpty()
        addressInput.onChange = {
            joinButton.disabledState = addressInput.value.trim().isEmpty()
        }
    }

    private fun submit() {
        val address = addressInput.value.trim()
        if (address.isEmpty()) return
        Persist.setDirectAddress(address)
        close()
        onConnect(address)
    }

    override fun charTyped(cp: Char): Boolean = visible && addressInput.charTyped(cp)

    override fun keyPressed(event: net.minecraft.client.input.KeyEvent): Boolean {
        if (!visible) return false
        if (addressInput.keyPressed(event)) return true
        if (event.isEscape) {
            close()
            return true
        }
        if (event.shortcutKey() == 257 || event.shortcutKey() == 335) {
            submit()
            return true
        }
        return false
    }
}

/** `multiplayer/EditServerModal.svelte` */
class EditServerModal(
    private val parent: Screen,
    private val data: ServerData,
    private val onEdit: (String, String, ServerData.ServerPackStatus) -> Unit
) : Modal("Edit Server") {

    private val nameInput = IconTextInput("Name", Tex.INFO, data.name)
    private val addressInput = IconTextInput("Address", Tex.SERVER, data.ip)
    private val packSelect = Select(
        "Server Resource Packs",
        listOf("Prompt", "Enabled", "Disabled"),
        when (data.resourcePackStatus) {
            ServerData.ServerPackStatus.ENABLED -> "Enabled"
            ServerData.ServerPackStatus.DISABLED -> "Disabled"
            else -> "Prompt"
        }
    )
    private val saveButton = ButtonSetting("Edit Server", { submit() }, inset = true, listenForEnter = true)

    init {
        ui.add(nameInput)
        ui.add(addressInput)
        ui.add(packSelect)
        ui.add(saveButton)
        nameInput.onChange = ::validate
        addressInput.onChange = ::validate
        validate()
    }

    private fun validate() {
        saveButton.disabledState = nameInput.value.isEmpty() || addressInput.value.isEmpty()
    }

    private fun submit() {
        if (saveButton.disabledState) return
        val pack = when (packSelect.value) {
            "Enabled" -> ServerData.ServerPackStatus.ENABLED
            "Disabled" -> ServerData.ServerPackStatus.DISABLED
            else -> ServerData.ServerPackStatus.PROMPT
        }
        onEdit(nameInput.value.trim(), addressInput.value.trim(), pack)
        close()
    }

    override fun charTyped(cp: Char): Boolean {
        if (!visible) return false
        if (nameInput.charTyped(cp)) return true
        return addressInput.charTyped(cp)
    }

    override fun keyPressed(event: net.minecraft.client.input.KeyEvent): Boolean {
        if (!visible) return false
        if (nameInput.keyPressed(event)) return true
        if (addressInput.keyPressed(event)) return true
        if (event.isEscape) {
            close()
            return true
        }
        if (event.shortcutKey() == 257 || event.shortcutKey() == 335) {
            submit()
            return true
        }
        return false
    }
}

/** Small persisted strings for the menu (direct-connect address, etc.). */
object Persist {
    private val mc: Minecraft get() = Minecraft.getInstance()

    private fun file() = mc.gameDirectory.toPath().resolve("lbmenu.properties")

    fun directAddress(): String = read()["direct_address"] ?: ""

    fun setDirectAddress(address: String) {
        val map = read()
        map["direct_address"] = address
        write(map)
    }

    private fun read(): MutableMap<String, String> {
        val out = LinkedHashMap<String, String>()
        try {
            val p = file()
            if (java.nio.file.Files.isRegularFile(p)) {
                for (line in java.nio.file.Files.readAllLines(p)) {
                    val i = line.indexOf('=')
                    if (i > 0) out[line.substring(0, i)] = line.substring(i + 1)
                }
            }
        } catch (t: Throwable) {
            // ignore
        }
        return out
    }

    private fun write(map: Map<String, String>) {
        try {
            val sb = StringBuilder()
            for ((k, v) in map) sb.append(k).append('=').append(v).append('\n')
            java.nio.file.Files.write(file(), sb.toString().toByteArray())
        } catch (t: Throwable) {
            // ignore
        }
    }
}