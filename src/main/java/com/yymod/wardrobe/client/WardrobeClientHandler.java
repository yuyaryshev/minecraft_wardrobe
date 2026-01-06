package com.yymod.wardrobe.client;

import com.yymod.wardrobe.content.menu.WardrobeMenu;
import net.minecraft.client.Minecraft;

public class WardrobeClientHandler {
    public static void handleError(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.containerMenu instanceof WardrobeMenu menu) {
            menu.setLastError(message);
        }
    }
}
