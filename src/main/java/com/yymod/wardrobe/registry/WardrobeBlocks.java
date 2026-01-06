package com.yymod.wardrobe.registry;

import com.yymod.wardrobe.YYWardrobe;
import com.yymod.wardrobe.content.block.WardrobeBlock;
import com.yymod.wardrobe.content.block.WardrobeConnectionBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class WardrobeBlocks {
    public static final DeferredRegister<Block> REGISTER =
            DeferredRegister.create(ForgeRegistries.BLOCKS, YYWardrobe.MOD_ID);

    public static final RegistryObject<Block> WARDROBE = REGISTER.register("wardrobe",
            () -> new WardrobeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .noOcclusion()));

    public static final RegistryObject<Block> WARDROBE_CONNECTION = REGISTER.register("wardrobe_connection",
            () -> new WardrobeConnectionBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.5F)
                    .noOcclusion()));

    private WardrobeBlocks() {
    }
}
