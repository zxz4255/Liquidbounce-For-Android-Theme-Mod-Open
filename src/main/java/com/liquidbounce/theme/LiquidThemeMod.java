package com.liquidbounce.theme;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class LiquidThemeMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("[LiquidTheme] 水影主题模组已加载!");
    }
}
