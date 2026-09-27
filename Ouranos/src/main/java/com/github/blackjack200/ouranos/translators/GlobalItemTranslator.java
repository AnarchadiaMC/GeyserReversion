package com.github.blackjack200.ouranos.translators;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import com.github.blackjack200.ouranos.base.WrappedBedrockPacket;
import com.github.blackjack200.ouranos.converter.ItemTypeDictionary;
import com.github.blackjack200.ouranos.converter.TypeConverter;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.*;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData;
import org.cloudburstmc.protocol.bedrock.packet.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// These will be translated directly instead of passing through multiple translators.
public class GlobalItemTranslator extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerServerbound(ItemStackRequestPacket.class, wrapped -> {
            final ItemStackRequestPacket packet = (ItemStackRequestPacket) wrapped.getPacket();

            final List<ItemStackRequest> newRequests = new ArrayList<>();
            for (final ItemStackRequest request : packet.getRequests()) {
                final List<ItemStackRequestAction> newActions = new ArrayList<>();
                final ItemStackRequestAction[] actions = request.getActions();
                for (final ItemStackRequestAction action : actions) {
                    switch (action) {
                        case TakeAction a ->
                                newActions.add(new TakeAction(a.getCount(), translateItemStackRequestSlotData(a.getSource()), translateItemStackRequestSlotData(a.getDestination())));
                        case ConsumeAction a ->
                                newActions.add(new ConsumeAction(a.getCount(), translateItemStackRequestSlotData(a.getSource())));
                        case DestroyAction a ->
                                newActions.add(new DestroyAction(a.getCount(), translateItemStackRequestSlotData(a.getSource())));
                        case DropAction a ->
                                newActions.add(new DropAction(a.getCount(), translateItemStackRequestSlotData(a.getSource()), a.isRandomly()));
                        case PlaceAction a -> {
                            final ItemStackRequestAction newAct = new PlaceAction(a.getCount(), translateItemStackRequestSlotData(a.getSource()), translateItemStackRequestSlotData(a.getDestination()));
                            newActions.add(newAct);
                        }
                        case SwapAction a ->
                                newActions.add(new SwapAction(translateItemStackRequestSlotData(a.getSource()), translateItemStackRequestSlotData(a.getDestination())));
                        case null, default -> newActions.add(action);
                    }
                }
                newRequests.add(new ItemStackRequest(request.getRequestId(), newActions.toArray(new ItemStackRequestAction[0]), request.getFilterStrings()));
            }
            packet.getRequests().clear();
            packet.getRequests().addAll(newRequests);
        });

        this.registerClientbound(InventoryContentPacket.class, wrapped -> {
            final InventoryContentPacket packet = (InventoryContentPacket) wrapped.getPacket();
            packet.getContents().replaceAll(itemData -> TypeConverter.translateItemData(wrapped.session(), wrapped.getInput(), wrapped.getOutput(), itemData));
        });

        this.registerClientbound(ItemComponentPacket.class, wrapped -> {
            final ItemComponentPacket packet = (ItemComponentPacket) wrapped.getPacket();
            final ItemTypeDictionary.InnerEntry itemDictionary = ItemTypeDictionary.getInstance(wrapped.getOutput());

            // Clear all vanilla items, both from the old protocol and new one... while allowing non-vanilla items to stay.
            packet.getItems().removeIf(definition -> ItemTypeDictionary.getInstance(wrapped.getInput()).fromStringId(definition.getIdentifier()) != null);
            packet.getItems().removeIf(definition -> itemDictionary.fromStringId(definition.getIdentifier()) != null);

            // Now add back all the vanilla items that is actually correct.
            packet.getItems().addAll(itemDictionary.getEntries().entrySet().stream().map((e) -> e.getValue().toDefinition(e.getKey())).toList());
        });

        this.registerClientbound(CraftingDataPacket.class, wrapped -> {
            final CraftingDataPacket packet = (CraftingDataPacket) wrapped.getPacket();

            // TODO: Properly translate these... Not too hard however I'm not used to this codebase (ouranos) yet!
            packet.getPotionMixData().clear();
            packet.getMaterialReducers().clear();
            packet.getCraftingData().clear();
            packet.getContainerMixData().clear();
            packet.setCleanRecipes(true);
        });

        this.registerClientbound(CreativeContentPacket.class, wrapped -> {
            final CreativeContentPacket packet = (CreativeContentPacket) wrapped.getPacket();
            final int input = wrapped.getInput(), output = wrapped.getOutput();

            // Temp fix for now.
            packet.getContents().clear();
            packet.getGroups().clear();
//            packet.getContents().replaceAll(itemData -> TypeConverter.translateCreativeItemData(input, output, itemData));
//            packet.getGroups().replaceAll(group -> group.toBuilder().icon(TypeConverter.translateItemData(input, output, group.getIcon())).build());
        });

        this.registerClientbound(AddItemEntityPacket.class, wrapped -> {
            final AddItemEntityPacket packet = (AddItemEntityPacket) wrapped.getPacket();
            packet.setItemInHand(TypeConverter.translateItemData(wrapped.session(), wrapped.getInput(), wrapped.getOutput(), packet.getItemInHand()));
        });

        this.registerClientbound(InventorySlotPacket.class, wrapped -> {
            final InventorySlotPacket packet = (InventorySlotPacket) wrapped.getPacket();
            final int input = wrapped.getInput(), output = wrapped.getOutput();

            packet.setItem(TypeConverter.translateItemData(wrapped.session(), input, output, packet.getItem()));
            packet.setStorageItem(TypeConverter.translateItemData(wrapped.session(), input, output, packet.getStorageItem()));
        });

        this.registerClientbound(AddPlayerPacket.class, wrapped -> {
            final AddPlayerPacket packet = (AddPlayerPacket) wrapped.getPacket();
            packet.setHand(TypeConverter.translateItemData(wrapped.session(), wrapped.getInput(), wrapped.getOutput(), packet.getHand()));
        });

        this.registerClientbound(InventoryTransactionPacket.class, this::translateBothWay);
        this.registerClientbound(MobEquipmentPacket.class, this::translateBothWay);
        this.registerClientbound(MobArmorEquipmentPacket.class, this::translateBothWay);

        this.registerServerbound(InventoryTransactionPacket.class, this::translateBothWay);
        this.registerServerbound(MobEquipmentPacket.class, this::translateBothWay);
        this.registerServerbound(MobArmorEquipmentPacket.class, this::translateBothWay);
    }

    private void translateBothWay(WrappedBedrockPacket wrapped) {
        final int input = wrapped.getInput(), output = wrapped.getOutput();

        final BedrockPacket bedrockPacket = wrapped.getPacket();
        if (bedrockPacket instanceof InventoryTransactionPacket packet) {
            for (int i = 0; i < packet.getActions().size(); i++) {
                final InventoryActionData action = packet.getActions().get(i);
                packet.getActions().set(i, new InventoryActionData(action.getSource(), action.getSlot(), TypeConverter.translateItemData(wrapped.session(), input, output, action.getFromItem()), TypeConverter.translateItemData(wrapped.session(), input, output, action.getToItem()), action.getStackNetworkId()));
            }

            if (packet.getBlockDefinition() != null) {
                packet.setBlockDefinition(TypeConverter.translateBlockDefinition(wrapped.session(), input, output, packet.getBlockDefinition()));
            }
            if (packet.getItemInHand() != null) {
                packet.setItemInHand(TypeConverter.translateItemData(wrapped.session(), input, output, packet.getItemInHand()));
            }
        } else if (bedrockPacket instanceof MobEquipmentPacket packet) {
            packet.setItem(TypeConverter.translateItemData(wrapped.session(), input, output, packet.getItem()));
        } else if (bedrockPacket instanceof MobArmorEquipmentPacket packet) {
            if (packet.getBody() != null) {
                packet.setBody(TypeConverter.translateItemData(wrapped.session(), input, output, packet.getBody()));
            }
            packet.setChestplate(TypeConverter.translateItemData(wrapped.session(), input, output, packet.getChestplate()));
            packet.setHelmet(TypeConverter.translateItemData(wrapped.session(), input, output, packet.getHelmet()));
            packet.setBoots(TypeConverter.translateItemData(wrapped.session(), input, output, packet.getBoots()));
            packet.setLeggings(TypeConverter.translateItemData(wrapped.session(), input, output, packet.getLeggings()));
        }
    }

    private ItemStackRequestSlotData translateItemStackRequestSlotData(ItemStackRequestSlotData dest) {
        return new ItemStackRequestSlotData(
                dest.getContainer(),
                dest.getSlot(),
                dest.getStackNetworkId(),
                Optional.ofNullable(dest.getContainerName())
                        .orElse(new FullContainerName(dest.getContainer(), 0))
        );
    }
}
