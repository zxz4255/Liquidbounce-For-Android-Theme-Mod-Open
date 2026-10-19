package com.example.lbmenu

import net.minecraft.resources.Identifier

/** Every texture reference used by the mod. Paths live under assets/lbmenu/. */
object Tex {
    private const val NS = "lbmenu"

    private fun id(path: String) = Identifier.fromNamespaceAndPath(NS, path)

    val BACKGROUND = id("textures/gui/background.png")
    val LOGO = id("textures/gui/logo.png")
    val STEVE = id("textures/gui/steve.png")
    val PARTY_HAT = id("textures/gui/party-hat.png")

    fun icon(name: String): Identifier = id("textures/gui/icons/icon-$name.png")

    val SINGLEPLAYER get() = icon("singleplayer")
    val MULTIPLAYER get() = icon("multiplayer")
    val REALMS get() = icon("realms")
    val LIQUIDBOUNCE get() = icon("liquidbounce")
    val OPTIONS get() = icon("options")
    val PROXYMANAGER get() = icon("proxymanager")
    val CLICKGUI get() = icon("clickgui")
    val BACK_LARGE get() = icon("back-large")
    val BACK get() = icon("back")
    val EXIT get() = icon("exit")
    val EYE get() = icon("eye")
    val CHANGE_BACKGROUND get() = icon("change-background")
    val NODEBB get() = icon("nodebb")
    val GITHUB get() = icon("github")
    val DISCORD get() = icon("discord")
    val TWITTER get() = icon("twitter")
    val YOUTUBE get() = icon("youtube")
    val WEBSITE get() = icon("liquidbounce.net")
    val PLUS_CIRCLE get() = icon("plus-circle")
    val PLANE get() = icon("plane")
    val REFRESH get() = icon("refresh")
    val PLAY get() = icon("play")
    val TRASH get() = icon("trash")
    val PEN_2 get() = icon("pen-2")
    val PEN get() = icon("pen")
    val RANDOM_ACCOUNT get() = icon("random-account")
    val SELECT_ARROW get() = icon("select-arrow")
    val SEARCH get() = icon("search")
    val INFO get() = icon("info")
    val SERVER get() = icon("server")
    val CLOSE get() = icon("close")
    val USER get() = icon("user")
    val NOTIF_INFO get() = icon("info")
    val NOTIF_ERROR get() = icon("error")
    val NOTIF_SUCCESS get() = icon("success")

    /** Fallback 64x64 for unknown server / world icons. */
    val PLACEHOLDER get() = icon("server")

    /** Native SVG viewBox sizes — needed to letterbox each glyph correctly. */
    val SRC_SIZE: Map<String, Pair<Float, Float>> = mapOf(
        "singleplayer" to (26.557f to 34f),
        "multiplayer" to (34f to 34f),
        "realms" to (28f to 28f),
        "liquidbounce" to (32f to 32f),
        "options" to (34f to 34f),
        "proxymanager" to (28f to 28f),
        "clickgui" to (34f to 34f),
        "back-large" to (34f to 34f),
        "back" to (22.499f to 22f),
        "exit" to (22f to 22f),
        "eye" to (25f to 25f),
        "change-background" to (22f to 22f),
        "nodebb" to (40f to 40f),
        "github" to (38f to 39.013f),
        "discord" to (127.14f to 96.36f),
        "twitter" to (40f to 40f),
        "youtube" to (40f to 40f),
        "liquidbounce.net" to (22f to 22f),
        "plus-circle" to (22f to 22.001f),
        "plane" to (22f to 20.56f),
        "refresh" to (22.015f to 21.999f),
        "play" to (27f to 27f),
        "trash" to (23.523f to 27f),
        "pen-2" to (27.003f to 27f),
        "pen" to (27.003f to 27f),
        "random-account" to (27f to 27f),
        "select-arrow" to (20.722f to 11.361f),
        "search" to (21.994f to 22f),
        "info" to (25f to 25f),
        "server" to (22f to 22f),
        "close" to (13.5f to 13.501f),
        "user" to (19.336f to 22f),
        "error" to (14f to 12f),
        "success" to (17f to 12f)
    )

    fun src(name: String): Pair<Float, Float> = SRC_SIZE[name] ?: (24f to 24f)
}