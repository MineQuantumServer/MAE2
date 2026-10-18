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
package stone.mae2.core.datagen;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import stone.mae2.MAE2;
import stone.mae2.api.features.MultiP2PTunnelAttunement;
import stone.mae2.bootstrap.MAE2Items;

import java.util.function.Consumer;

public class MAE2RecipeProvider extends RecipeProvider {

  public MAE2RecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) { super(output, registries); }

  @Override
  protected void buildRecipes(RecipeOutput consumer) {
    buildAcceleratorRecipe(consumer, MAE2Items.ACCELERATOR_4x.get(),
      AEItems.CELL_COMPONENT_4K, "network/crafting/4x_crafting_accelerator");
    buildAcceleratorRecipe(consumer, MAE2Items.ACCELERATOR_16x.get(),
      AEItems.CELL_COMPONENT_16K, "network/crafting/16x_crafting_accelerator");
    buildAcceleratorRecipe(consumer, MAE2Items.ACCELERATOR_64x.get(),
      AEItems.CELL_COMPONENT_64K, "network/crafting/64x_crafting_accelerator");
    buildAcceleratorRecipe(consumer, MAE2Items.ACCELERATOR_256x.get(),
      AEItems.CELL_COMPONENT_256K,
      "network/crafting/256x_crafting_accelerator");

    buildMultiP2PRecipe(consumer, MAE2Items.ITEM_MULTI_P2P_TUNNEL.get(), AEParts.ITEM_P2P_TUNNEL, MAE2.toKey("network/parts/multi_p2p_tunnel_item"));
    buildMultiP2PRecipe(consumer, MAE2Items.FLUID_MULTI_P2P_TUNNEL.get(), AEParts.FLUID_P2P_TUNNEL, MAE2.toKey("network/parts/multi_p2p_tunnel_fluid"));
    buildMultiP2PRecipe(consumer, MAE2Items.FE_MULTI_P2P_TUNNEL.get(), AEParts.FE_P2P_TUNNEL, MAE2.toKey("network/parts/multi_p2p_tunnel_fe"));
    buildMultiP2PRecipe(consumer, MAE2Items.REDSTONE_MULTI_P2P_TUNNEL.get(), AEParts.REDSTONE_P2P_TUNNEL, MAE2.toKey("network/parts/multi_p2p_tunnel_redstone"));
    buildMultiP2PRecipe(consumer, MAE2Items.PATTERN_MULTI_P2P_TUNNEL.get(), MAE2Items.PATTERN_P2P_TUNNEL.get(), MAE2.toKey("network/parts/multi_p2p_tunnel_pattern"));

    // stopgap for the fact that ME Multi P2Ps don't exist (so there's no direct
    // crafting recipe to Multi P2Ps, you'd need to attune a ME P2P to something
    // else before crafting the Multi P2P)
    buildMultiP2PRecipe(consumer, MAE2Items.ITEM_MULTI_P2P_TUNNEL.get(),
      AEParts.ME_P2P_TUNNEL,
      MAE2.toKey("network/parts/multi_p2p_tunnel_workaround"));

    ShapedRecipeBuilder
      .shaped(RecipeCategory.TOOLS, MAE2Items.FAULTY_MEMORY_CARD.get())
      .pattern("lcc")
      .pattern("grg")
      .define('l', AEItems.LOGIC_PROCESSOR)
      .define('c', Items.COPPER_INGOT)
      .define('g', Items.GOLD_INGOT)
      .define('r', Items.REDSTONE)
      .unlockedBy("has_memory_card", has(AEItems.MEMORY_CARD))
      .save(consumer);

    ShapedRecipeBuilder
      .shaped(RecipeCategory.MISC, MAE2Items.CLOUD_CHAMBER.get(), 8)
      .pattern("ggg")
      .pattern("gag")
      .pattern("ggg")
      .define('g', AEBlocks.QUARTZ_VIBRANT_GLASS.asItem())
      .define('a', Items.AMETHYST_SHARD)
      .unlockedBy("has_vibrant_glass",
        has(AEBlocks.QUARTZ_VIBRANT_GLASS.asItem()))
      .save(consumer);
  }

  public static void buildAcceleratorRecipe(RecipeOutput consumer,
    ItemLike output, ItemLike component, String id) {
    ShapelessRecipeBuilder
      .shapeless(RecipeCategory.MISC, output)
      .requires(component)
      .requires(AEBlocks.CRAFTING_ACCELERATOR.asItem())
      .unlockedBy("has_acceleration_unit", has(AEBlocks.CRAFTING_ACCELERATOR))
      .save(consumer, MAE2.toKey(id));
  }

  public static void buildMultiP2PRecipe(RecipeOutput consumer,
    ItemLike multi, ItemLike single, ResourceLocation id) {
    ShapelessRecipeBuilder
      .shapeless(RecipeCategory.MISC, multi)
      .requires(single)
      .requires(AEItems.ENGINEERING_PROCESSOR)
      .unlockedBy("has_me_p2p", has(AEParts.ME_P2P_TUNNEL))
      .save(consumer, id);
  }

}
