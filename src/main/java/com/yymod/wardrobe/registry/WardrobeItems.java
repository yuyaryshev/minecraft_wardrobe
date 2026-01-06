package com.yymod.wardrobe.registry;

import com.yymod.wardrobe.YYWardrobe;
import com.yymod.wardrobe.content.block.item.WardrobeBlockItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class WardrobeItems {
    public static final DeferredRegister<Item> REGISTER =
            DeferredRegister.create(ForgeRegistries.ITEMS, YYWardrobe.MOD_ID);

    public static final RegistryObject<Item> WARDROBE = REGISTER.register("wardrobe",
            () -> new WardrobeBlockItem(WardrobeBlocks.WARDROBE.get(), new Item.Properties()));

    public static final RegistryObject<Item> WARDROBE_CONNECTION = REGISTER.register("wardrobe_connection",
            () -> new BlockItem(WardrobeBlocks.WARDROBE_CONNECTION.get(), new Item.Properties()));

    private WardrobeItems() {
    }
}
