package com.yymod.wardrobe.registry;

import com.yymod.wardrobe.YYWardrobe;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class WardrobeCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, YYWardrobe.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = REGISTER.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.yy_wardrobe"))
                    .icon(() -> new ItemStack(WardrobeItems.WARDROBE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(WardrobeItems.WARDROBE.get());
                        output.accept(WardrobeItems.WARDROBE_CONNECTION.get());
                    })
                    .build());

    private WardrobeCreativeTabs() {
    }
}
