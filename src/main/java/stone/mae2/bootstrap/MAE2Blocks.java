/*
 * Copyright (C) 2024 AE2 Enthusiast
 *
 * This file is part of MAE2.
 *
 * MAE2 is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * MAE2 is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see https://www.gnu.org/licenses/.
 */
package stone.mae2.bootstrap;

import appeng.block.crafting.CraftingUnitBlock;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

import stone.mae2.MAE2;
import stone.mae2.block.CloudChamberBlock;
import stone.mae2.block.crafting.DynamicCraftingBlockItem;
import stone.mae2.block.crafting.DynamicCraftingUnitType;

public abstract class MAE2Blocks {
  static final DeferredRegister<Block> BLOCKS = DeferredRegister
    .create(Registries.BLOCK, MAE2.MODID);
  static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
    .create(Registries.BLOCK_ENTITY_TYPE, MAE2.MODID);

  public static DeferredHolder<Block, CraftingUnitBlock> ACCELERATOR_4x;
  public static DeferredHolder<Block, CraftingUnitBlock> ACCELERATOR_16x;
  public static DeferredHolder<Block, CraftingUnitBlock> ACCELERATOR_64x;
  public static DeferredHolder<Block, CraftingUnitBlock> ACCELERATOR_256x;

  public static DeferredHolder<Block, CloudChamberBlock> CLOUD_CHAMBER;

  public static void init(IEventBus bus) {
    BLOCKS.register(bus);
    register(bus);

    MAE2BlockEntities.init(bus);
  }

  @SuppressWarnings("unchecked")
  public static void register(IEventBus bus) {
    ACCELERATOR_4x = BLOCKS
      .register("4x_crafting_accelerator",
        () -> new CraftingUnitBlock(DynamicCraftingUnitType.ACCELERATOR_4x));

    MAE2Items.ACCELERATOR_4x = MAE2Items.ITEMS
      .register("4x_crafting_accelerator",
        () -> new DynamicCraftingBlockItem(ACCELERATOR_4x.get(),
          new Item.Properties(), () -> AEBlocks.CRAFTING_ACCELERATOR.asItem(),
          () -> AEItems.CELL_COMPONENT_4K.asItem()));

    ACCELERATOR_16x = BLOCKS
      .register("16x_crafting_accelerator",
        () -> new CraftingUnitBlock(DynamicCraftingUnitType.ACCELERATOR_16x));

    MAE2Items.ACCELERATOR_16x = MAE2Items.ITEMS
      .register("16x_crafting_accelerator",
        () -> new DynamicCraftingBlockItem(ACCELERATOR_16x.get(),
          new Item.Properties(), () -> AEBlocks.CRAFTING_ACCELERATOR.asItem(),
          () -> AEItems.CELL_COMPONENT_16K.asItem()));

    ACCELERATOR_64x = BLOCKS
      .register("64x_crafting_accelerator",
        () -> new CraftingUnitBlock(DynamicCraftingUnitType.ACCELERATOR_64x));

    MAE2Items.ACCELERATOR_64x = MAE2Items.ITEMS
      .register("64x_crafting_accelerator",
        () -> new DynamicCraftingBlockItem(ACCELERATOR_64x.get(),
          new Item.Properties(), () -> AEBlocks.CRAFTING_ACCELERATOR.asItem(),
          () -> AEItems.CELL_COMPONENT_64K.asItem()));

    ACCELERATOR_256x = BLOCKS
      .register("256x_crafting_accelerator",
        () -> new CraftingUnitBlock(DynamicCraftingUnitType.ACCELERATOR_256x));

    MAE2Items.ACCELERATOR_256x = MAE2Items.ITEMS
      .register("256x_crafting_accelerator",
        () -> new DynamicCraftingBlockItem(ACCELERATOR_256x.get(),
          new Item.Properties(), () -> AEBlocks.CRAFTING_ACCELERATOR.asItem(),
          () -> AEItems.CELL_COMPONENT_256K.asItem()));

    CLOUD_CHAMBER = BLOCKS.register("cloud_chamber", () -> new CloudChamberBlock());
    MAE2Items.CLOUD_CHAMBER = MAE2Items.ITEMS
      .register("cloud_chamber",
        () -> new BlockItem(CLOUD_CHAMBER.get(), new Item.Properties()));
  }
}
