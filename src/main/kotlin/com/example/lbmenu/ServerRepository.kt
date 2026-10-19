package com.example.lbmenu

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.ServerList
import net.minecraft.client.multiplayer.ServerStatusPinger
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.server.LanServer
import net.minecraft.client.server.LanServerDetection
import net.minecraft.resources.Identifier
import net.minecraft.server.network.EventLoopGroupHolder
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** One row in the multiplayer list — a real ServerData (WAN) or a LAN broadcast entry. */
data class ServerRow(
    val data: ServerData,
    val lan: Boolean,
    val lanAddress: String = ""
) {
    val address: String get() = if (lan) lanAddress else data.ip
    val name: String get() = data.name
    val ping: Long get() = if (lan) 0L else data.ping
    val online: Boolean get() = !lan && data.ping > 0L
}

/** Wraps ServerList + LAN detection + pinging, matching Multiplayer.svelte's data flow. */
object ServerRepository {
    private val mc: Minecraft get() = Minecraft.getInstance()

    private var serverList: ServerList? = null
    private var lanList: LanServerDetection.LanServerList? = null
    private var lanDetector: LanServerDetection.LanServerDetector? = null
    private val pinger = ServerStatusPinger()

    private val pinged = ConcurrentHashMap<String, Long>()

    fun servers(): List<ServerData> {
        val list = serverList ?: ServerList(mc).also {
            it.load()
            serverList = it
        }
        return (0 until list.size()).mapNotNull { list.get(it) }
    }

    fun ensureLoaded() {
        if (serverList == null) {
            val list = ServerList(mc)
            list.load()
            serverList = list
        }
        if (lanDetector == null) {
            val ll = LanServerDetection.LanServerList()
            lanList = ll
            lanDetector = try {
                LanServerDetection.LanServerDetector(ll).also { it.start() }
            } catch (t: Throwable) {
                null
            }
        }
    }

    fun lanServers(): List<LanServer> = lanList?.takeDirtyServers() ?: emptyList()

    fun rows(includeLan: Boolean = true, onlineOnly: Boolean = false, query: String = ""): List<ServerRow> {
        ensureLoaded()
        val out = ArrayList<ServerRow>()
        for (s in servers()) {
            val row = ServerRow(s, lan = false)
            if (onlineOnly && !row.online) continue
            if (query.isNotEmpty() && !s.name.lowercase().contains(query.lowercase())) continue
            out.add(row)
        }
        if (includeLan) {
            for (l in lanServers()) {
                val data = ServerData(l.motd, l.address, ServerData.Type.LAN)
                data.ping = 0L
                val row = ServerRow(data, lan = true, lanAddress = l.address)
                if (query.isNotEmpty() && !l.motd.lowercase().contains(query.lowercase())) continue
                out.add(row)
            }
        }
        return out
    }

    fun ping(row: ServerRow, onDone: () -> Unit) {
        if (row.lan) {
            onDone()
            return
        }
        try {
            pinger.pingServer(
                row.data,
                { },
                { onDone() },
                EventLoopGroupHolder.remote(mc.options.useNativeTransport())
            )
        } catch (t: Throwable) {
            row.data.ping = -1L
            onDone()
        }
    }

    fun tickPinger() {
        try {
            pinger.tick()
        } catch (t: Throwable) {
            // ignore
        }
    }

    fun add(name: String, address: String, pack: ServerData.ServerPackStatus) {
        val list = serverList ?: return
        val data = ServerData(name, address, ServerData.Type.OTHER)
        data.setResourcePackStatus(pack)
        list.add(data, false)
        list.save()
    }

    fun edit(index: Int, name: String, address: String, pack: ServerData.ServerPackStatus) {
        val list = serverList ?: return
        val data = ServerData(name, address, ServerData.Type.OTHER)
        data.setResourcePackStatus(pack)
        list.replace(index, data)
        list.save()
    }

    fun remove(index: Int) {
        val list = serverList ?: return
        val data = list.get(index) ?: return
        list.remove(data)
        list.save()
    }

    fun swap(a: Int, b: Int) {
        serverList?.swap(a, b)
    }

    fun connect(address: String, parent: net.minecraft.client.gui.screens.Screen) {
        val data = ServerData("", address, ServerData.Type.OTHER)
        net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
            parent, mc, ServerAddress.parseString(address), data, false, null
        )
    }

    fun shutdown() {
        try {
            lanDetector?.interrupt()
        } catch (t: Throwable) {
            // ignore
        }
        lanDetector = null
        lanList = null
    }
}

/** Dynamic 64x64 textures created from raw PNG bytes (server icons, world icons). */
object DynamicIcons {
    private val ids = HashMap<String, Identifier>()
    private var counter = AtomicInteger(0)

    fun fromBytes(key: String, bytes: ByteArray?): Identifier? {
        if (bytes == null || bytes.isEmpty()) return null
        ids[key]?.let { return it }
        return try {
            val image = NativeImage.read(bytes)
            val id = Identifier.fromNamespaceAndPath("lbmenu", "dynamic/icon_${counter.incrementAndGet()}")
            val tex = DynamicTexture({ "lbmenu_icon" }, image)
            Minecraft.getInstance().textureManager.register(id, tex)
            ids[key] = id
            id
        } catch (t: Throwable) {
            null
        }
    }

    fun fromFile(key: String, path: java.nio.file.Path?): Identifier? {
        if (path == null || !Files.isRegularFile(path)) return null
        ids[key]?.let { return it }
        return try {
            fromBytes(key, Files.readAllBytes(path))
        } catch (t: Throwable) {
            null
        }
    }
}