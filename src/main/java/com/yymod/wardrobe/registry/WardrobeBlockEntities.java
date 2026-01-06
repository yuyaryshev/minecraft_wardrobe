package com.yymod.wardrobe.registry;

import com.yymod.wardrobe.YYWardrobe;
import com.yymod.wardrobe.content.block.entity.WardrobeBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class WardrobeBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTER =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, YYWardrobe.MOD_ID);

    public static final RegistryObject<BlockEntityType<WardrobeBlockEntity>> WARDROBE =
            REGISTER.register("wardrobe",
                    () -> BlockEntityType.Builder.of(WardrobeBlockEntity::new, WardrobeBlocks.WARDROBE.get())
                            .build(null));

    private WardrobeBlockEntities() {
    }
}
