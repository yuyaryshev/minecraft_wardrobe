package com.yymod.wardrobe.registry;

import com.yymod.wardrobe.YYWardrobe;
import com.yymod.wardrobe.content.menu.WardrobeMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class WardrobeMenus {
    public static final DeferredRegister<MenuType<?>> REGISTER =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, YYWardrobe.MOD_ID);

    public static final RegistryObject<MenuType<WardrobeMenu>> WARDROBE =
            REGISTER.register("wardrobe", () -> IForgeMenuType.create(WardrobeMenu::new));

    private WardrobeMenus() {
    }
}
