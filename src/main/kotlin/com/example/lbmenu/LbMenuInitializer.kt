package com.example.lbmenu

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.gui.screens.TitleScreen

/**
 * Swaps the vanilla title screen for the native LiquidBounce recreation.
 *
 * `ClientTickEvents.END_CLIENT_TICK` is used instead of a ScreenEvents hook because the
 * vanilla TitleScreen must never become visible for a frame — replacing it on the next
 * tick keeps the panorama from flashing while the custom backdrop warms up.
 */
class LbMenuInitializer : ClientModInitializer {

    private var replaced = false

    override fun onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client ->
            val current = client.gui.screen()
            if (current is TitleScreen && current.javaClass == TitleScreen::class.java) {
                replaced = true
                client.gui.setScreen(LbTitleScreen())
            }
        })
    }
}