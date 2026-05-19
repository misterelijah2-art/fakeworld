package com.example.fakeworld;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;

public class ModBlocks {

    public static final Block OBSERVATION_BLOCK = register(
            "observation_block",
            new ObservationBlock(
                    FabricBlockSettings.create()
                            .strength(-1.0F, Float.MAX_VALUE) // unbreakable like bedrock
                            .sound(SoundType.STONE)
                            .requiresCorrectToolForDrops()
            )
    );

    private static Block register(String name, Block block) {
        ResourceLocation id = new ResourceLocation("fakeworld", name);
        // Register block item so it appears in inventory
        Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, new Item.Properties()));
        return Registry.register(BuiltInRegistries.BLOCK, id, block);
    }

    // Call this from your main mod class to trigger static initialisation
    public static void init() {}
}
