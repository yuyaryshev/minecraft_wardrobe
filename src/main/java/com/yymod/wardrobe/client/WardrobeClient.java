package com.yymod.wardrobe.client;

import com.yymod.wardrobe.client.screen.WardrobeScreen;
import com.yymod.wardrobe.registry.WardrobeMenus;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.gui.screens.MenuScreens;

public class WardrobeClient {
    public static void init(IEventBus eventBus) {
        eventBus.addListener(WardrobeClient::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(WardrobeMenus.WARDROBE.get(), WardrobeScreen::new));
    }
}
