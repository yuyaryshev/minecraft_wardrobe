package com.yymod.wardrobe;

import com.mojang.logging.LogUtils;
import com.yymod.wardrobe.client.WardrobeClient;
import com.yymod.wardrobe.network.WardrobeNetwork;
import com.yymod.wardrobe.registry.WardrobeBlockEntities;
import com.yymod.wardrobe.registry.WardrobeBlocks;
import com.yymod.wardrobe.registry.WardrobeCreativeTabs;
import com.yymod.wardrobe.registry.WardrobeItems;
import com.yymod.wardrobe.registry.WardrobeMenus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(YYWardrobe.MOD_ID)
public class YYWardrobe {
    public static final String MOD_ID = "yy_wardrobe";
    public static final Logger LOGGER = LogUtils.getLogger();

    public YYWardrobe() {
        IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();

        WardrobeBlocks.REGISTER.register(eventBus);
        WardrobeItems.REGISTER.register(eventBus);
        WardrobeBlockEntities.REGISTER.register(eventBus);
        WardrobeMenus.REGISTER.register(eventBus);
        WardrobeCreativeTabs.REGISTER.register(eventBus);

        eventBus.addListener(this::commonSetup);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> WardrobeClient.init(eventBus));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(WardrobeNetwork::init);
    }
}
