/*
 * GeyserReversion Ouranos compatibility changes by siberanka, 2026-09-15.
 * SPDX-License-Identifier: AGPL-3.0-only
 * Original Ouranos authors: Blackjack200, oryxel1 and contributors.
 */
package com.github.blackjack200.ouranos.converter;

import com.github.blackjack200.ouranos.session.OuranosSession;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.*;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.*;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.*;
import org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket;
import java.util.List;

/** Preserve recipe identities while converting every concrete ingredient/result ID. */
public final class RecipeTranslator {
    private RecipeTranslator() { }

    public static void translate(OuranosSession session, CraftingDataPacket packet) {
        var translated = new java.util.ArrayList<RecipeData>(packet.getCraftingData().size());
        for (var recipe : packet.getCraftingData()) {
            // An unrepresentable modern recipe must not make the entire legacy recipe book unusable.
            try {
                var result = recipe(session, recipe);
                if (result != null) translated.add(result);
            } catch (UnsupportedRecipeException ignored) { }
        }
        packet.getCraftingData().clear();
        packet.getCraftingData().addAll(translated);
        packet.getPotionMixData().replaceAll(mix -> {
            int[] in = id(session, mix.getInputId(), mix.getInputMeta());
            int[] reagent = id(session, mix.getReagentId(), mix.getReagentMeta());
            int[] out = id(session, mix.getOutputId(), mix.getOutputMeta());
            return new PotionMixData(in[0], in[1], reagent[0], reagent[1], out[0], out[1]);
        });
        packet.getContainerMixData().replaceAll(mix -> new ContainerMixData(
                id(session, mix.getInputId(), 0)[0], id(session, mix.getReagentId(), 0)[0],
                id(session, mix.getOutputId(), 0)[0]));
        // Education-only material reducers need a separate legacy chemistry implementation.
        // Do not advertise them with mismatched IDs; ordinary crafting/brewing stays intact.
        packet.getMaterialReducers().clear();
    }

    private static int[] id(OuranosSession s, int id, int meta) {
        return TypeConverter.translateItemRuntimeId(s, s.getTargetVersion(), s.getProtocolId(), id, meta);
    }

    private static ItemData item(OuranosSession s, ItemData item) {
        return TypeConverter.translateItemData(s, s.getTargetVersion(), s.getProtocolId(), item);
    }

    private static List<ItemData> items(OuranosSession s, List<ItemData> items) {
        return items.stream().map(i -> item(s, i)).toList();
    }

    private static ItemDescriptorWithCount ingredient(OuranosSession s, ItemDescriptorWithCount value) {
        if (value == null || value.getDescriptor() instanceof InvalidDescriptor) {
            return ItemDescriptorWithCount.EMPTY;
        }
        if (value.getDescriptor() instanceof DefaultDescriptor) {
            return ItemDescriptorWithCount.fromItem(item(s, value.toItem()));
        }
        if (value.getDescriptor() instanceof DeferredDescriptor deferred) {
            var definition = ItemTypeDictionary.getInstance(s.getTargetVersion()).getEntries().get(deferred.getFullName());
            if (definition == null) {
                throw new UnsupportedRecipeException();
            }
            return ItemDescriptorWithCount.fromItem(item(s, ItemData.builder()
                    .definition(definition.toDefinition(deferred.getFullName()))
                    .damage(deferred.getAuxValue()).count(value.getCount()).build()));
        }
        // Tags and Molang cannot be resolved into one arbitrary item without changing the recipe.
        if (s.getProtocolId() < 554) {
            throw new UnsupportedRecipeException();
        }
        return value;
    }

    private static List<ItemDescriptorWithCount> ingredients(OuranosSession s, List<ItemDescriptorWithCount> items) {
        return items.stream().map(i -> ingredient(s, i)).toList();
    }

    private static RecipeData recipe(OuranosSession s, RecipeData recipe) {
        // Smithing templates/trims do not exist on pre-1.20 clients.
        if (s.getProtocolId() < 589
                && (recipe instanceof SmithingTrimRecipeData || recipe instanceof SmithingTransformRecipeData)) {
            return null;
        }
        if (recipe instanceof ShapedRecipeData r) {
            return ShapedRecipeData.of(r.getType(), r.getId(), r.getWidth(), r.getHeight(),
                    ingredients(s, r.getIngredients()), items(s, r.getResults()), r.getUuid(), r.getTag(),
                    r.getPriority(), r.getNetId(), r.isAssumeSymetry(), r.getRequirement());
        }
        if (recipe instanceof ShapelessRecipeData r) {
            return ShapelessRecipeData.of(r.getType(), r.getId(), ingredients(s, r.getIngredients()),
                    items(s, r.getResults()), r.getUuid(), r.getTag(), r.getPriority(), r.getNetId(), r.getRequirement());
        }
        if (recipe instanceof FurnaceRecipeData r) {
            int[] input = id(s, r.getInputId(), r.getInputData());
            return FurnaceRecipeData.of(r.getType(), input[0], input[1], item(s, r.getResult()), r.getTag());
        }
        if (recipe instanceof SmithingTransformRecipeData r && s.getProtocolId() >= 589) {
            return SmithingTransformRecipeData.of(r.getId(), ingredient(s, r.getTemplate()),
                    ingredient(s, r.getBase()), ingredient(s, r.getAddition()), item(s, r.getResult()), r.getTag(), r.getNetId());
        }
        if (recipe instanceof SmithingTrimRecipeData r && s.getProtocolId() >= 589) {
            return SmithingTrimRecipeData.of(r.getId(), ingredient(s, r.getBase()),
                    ingredient(s, r.getAddition()), ingredient(s, r.getTemplate()), r.getTag(), r.getNetId());
        }
        return recipe; // MultiRecipeData contains no versioned item IDs.
    }

    private static final class UnsupportedRecipeException extends RuntimeException { }
}
