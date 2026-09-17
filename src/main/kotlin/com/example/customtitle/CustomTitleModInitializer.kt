package com.example.customtitle

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import java.util.Collections
import java.util.WeakHashMap

/**
 * - Swaps vanilla TitleScreen → CustomTitleScreen
 * - Paints background.png behind Options / Singleplayer / Multiplayer and related menus
 *   (afterBackground: drawn after the vanilla background, before the widgets)
 */
class CustomTitleModInitializer : ClientModInitializer {

    /** BEFORE_INIT re-fires for the same Screen instance on window resize — hook only once */
    private val hooked = Collections.newSetFromMap(WeakHashMap<Screen, Boolean>())

    override fun onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client ->
            if (client.gui.screen() is TitleScreen) {
                client.gui.setScreen(CustomTitleScreen())
            }
        })

        // Draw shared menu background under nested screens (after title clicks)
        ScreenEvents.BEFORE_INIT.register(ScreenEvents.BeforeInit { client, screen, scaledWidth, scaledHeight ->
            if (MenuBackground.shouldApply(screen) && hooked.add(screen)) {
                ScreenEvents.afterBackground(screen).register(
                    ScreenEvents.AfterBackground { scr, context, mouseX, mouseY, tickDelta ->
                        MenuBackground.draw(context, scr.width, scr.height)
                    }
                )
            }
        })
    }
}
