package com.github.blackjack200.ouranos.translators.new_to_old.v407to390;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import com.github.blackjack200.ouranos.session.OuranosSession;
import com.github.blackjack200.ouranos.translators.new_to_old.v407to390.storage.ClientAuthInventoryStorage;
import org.cloudburstmc.protocol.bedrock.data.inventory.*;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.PlaceAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.SwapAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.TakeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponse;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseStatus;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource;
import org.cloudburstmc.protocol.bedrock.packet.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Protocol407to390 extends ProtocolToProtocol {
    @Override
    public void init(OuranosSession session) {
        session.put(new ClientAuthInventoryStorage(session));
    }

    @Override
    protected void registerProtocol() {
        this.registerClientbound(CreativeContentPacket.class, wrapped -> {
            final CreativeContentPacket packet = (CreativeContentPacket) wrapped.getPacket();

            final InventoryContentPacket newPacket = new InventoryContentPacket();
            newPacket.setContainerId(ContainerId.CREATIVE);
            newPacket.setContents(packet.getContents().stream().map(CreativeItemData::getItem).filter(i -> i.getDefinition().getVersion().equals(ItemVersion.LEGACY)).collect(Collectors.toList()));
            wrapped.setPacket(newPacket);
        });

        this.registerClientbound(InventoryContentPacket.class, wrapped -> {
            final InventoryContentPacket packet = (InventoryContentPacket) wrapped.getPacket();
            if (wrapped.session().isServerAuthoritativeInventories()) {
                wrapped.session().get(ClientAuthInventoryStorage.class).getInventories().put(packet.getContainerId(), new ArrayList<>(packet.getContents()));
            }
        });

        this.registerClientbound(InventorySlotPacket.class, wrapped -> {
            final InventorySlotPacket packet = (InventorySlotPacket) wrapped.getPacket();
            final OuranosSession session = wrapped.session();
            final ClientAuthInventoryStorage storage = session.get(ClientAuthInventoryStorage.class);
            if (session.isServerAuthoritativeInventories()) {
                storage.getInventories().putIfAbsent(packet.getContainerId(), new ArrayList<>());
                final List<ItemData> inv = storage.getInventories().get(packet.getContainerId());
                while (inv.size() <= packet.getSlot()) {
                    inv.add(ItemData.AIR);
                }
                inv.set(packet.getSlot(), packet.getItem());
            }
        });

        this.registerClientbound(MobEquipmentPacket.class, wrapped -> {
            final MobEquipmentPacket packet = (MobEquipmentPacket) wrapped.getPacket();
            final OuranosSession session = wrapped.session();
            final ClientAuthInventoryStorage storage = session.get(ClientAuthInventoryStorage.class);
            if (session.isServerAuthoritativeInventories()) {
                storage.getInventories().putIfAbsent(packet.getContainerId(), new ArrayList<>());
                final List<ItemData> inv = storage.getInventories().get(packet.getContainerId());
                while (inv.size() < packet.getInventorySlot()) {
                    inv.add(ItemData.AIR);
                }
                inv.set(packet.getInventorySlot(), packet.getItem());
            }
        });

        this.registerClientbound(ItemStackResponsePacket.class, wrapped -> {
            final ItemStackResponsePacket packet = (ItemStackResponsePacket) wrapped.getPacket();
            final OuranosSession session = wrapped.session();
            final ClientAuthInventoryStorage storage = session.get(ClientAuthInventoryStorage.class);
            if (!session.isServerAuthoritativeInventories()) { // Not possible but check for it anyway.
                return;
            }

            for (final ItemStackResponse entry : packet.getEntries()) {
                var xa = storage.getStackResponses().get(entry.getRequestId());
                storage.getStackResponses().remove(entry.getRequestId());
                if (entry.getResult() == ItemStackResponseStatus.OK) {
                    if (xa != null) {
                        xa.accept(entry.getContainers());
                    }
                    for (var slot : entry.getContainers()) {
                        var id = parseContainerId(slot.getContainerName().getContainer());
                        var container = storage.getInventories().get(id);
                        if (container != null) {
                            for (var item : slot.getItems()) {
                                container.set(item.getSlot(), container.get(item.getSlot()).toBuilder().count(item.getCount()).damage(item.getDurabilityCorrection()).usingNetId(true).netId(item.getStackNetworkId()).build());
                            }
                        }
                    }
                } else {
                    storage.getInventories().forEach((containerId, contents) -> {
                        var pp = new InventoryContentPacket();
                        pp.setContainerId(containerId);
                        pp.setContents(contents);
                        session.sendUpstreamPacket(pp);
                    });
                    wrapped.cancel();
                }
            }
        });

        this.registerServerbound(InventoryTransactionPacket.class, wrapped -> {
            final ClientAuthInventoryStorage storage = wrapped.session().get(ClientAuthInventoryStorage.class);

            final InventoryTransactionPacket packet = (InventoryTransactionPacket) wrapped.getPacket();
            // TODO: Translate other cases? Are these cases even possible....?
            if (packet.getActions().size() != 2) {
                return;
            }

            var a = packet.getActions().get(0);
            var b = packet.getActions().get(1);

            final ItemStackRequestPacket stackPacket = new ItemStackRequestPacket();
            final ContainerSlotType slotType = parseContainerId(a.getSource().getContainerId());
            final ContainerSlotType otherSlotType = parseContainerId(b.getSource().getContainerId());

            if (a.getSource().getType() == InventorySource.Type.CONTAINER && b.getSource().getType() == InventorySource.Type.CONTAINER) {
                return;
            }

            // TODO: Refactor me, this is literally unreadable.... well not really but it could be better.

            var source = storage.getInventories().get(a.getSource().getContainerId()).get(a.getSlot());
            var destination = storage.getInventories().get(b.getSource().getContainerId()).get(b.getSlot());
            if (!source.isNull()) {
                var count = Math.abs(source.getCount() - a.getToItem().getCount());
                if (destination.isNull()) {
                    stackPacket.getRequests().add(new ItemStackRequest(0, new ItemStackRequestAction[]{
                            new TakeAction(
                                    count,
                                    new ItemStackRequestSlotData(slotType, a.getSlot(), source.getNetId(), new FullContainerName(slotType, 0)),
                                    new ItemStackRequestSlotData(otherSlotType, b.getSlot(), destination.getNetId(), new FullContainerName(otherSlotType, 0))
                            )
                    }, new String[]{}));
                    storage.getStackResponses().put(0, (slots) -> {
                        storage.getInventories().get(a.getSource().getContainerId()).set(a.getSlot(), source.toBuilder().count(source.getCount() - count).build());
                        storage.getInventories().get(b.getSource().getContainerId()).set(b.getSlot(), source.toBuilder().count(count).build());
                    });
                } else {
                    stackPacket.getRequests().add(new ItemStackRequest(1, new ItemStackRequestAction[]{
                            new PlaceAction(
                                    count,
                                    new ItemStackRequestSlotData(slotType, a.getSlot(), source.getNetId(), new FullContainerName(slotType, 0)),
                                    new ItemStackRequestSlotData(otherSlotType, b.getSlot(), destination.getNetId(), new FullContainerName(otherSlotType, 0))
                            )
                    }, new String[]{}));
                    storage.getStackResponses().put(1, (slots) -> {
                        storage.getInventories().get(a.getSource().getContainerId()).set(a.getSlot(), source.toBuilder().count(source.getCount() - count).build());
                        storage.getInventories().get(b.getSource().getContainerId()).set(b.getSlot(), destination.toBuilder().count(destination.getCount() + count).build());
                    });
                }
            } else {
                stackPacket.getRequests().add(new ItemStackRequest(2, new ItemStackRequestAction[]{
                        new SwapAction(
                                new ItemStackRequestSlotData(slotType, a.getSlot(), source.getNetId(), new FullContainerName(slotType, 0)),
                                new ItemStackRequestSlotData(otherSlotType, b.getSlot(), destination.getNetId(), new FullContainerName(otherSlotType, 0))
                        )
                }, new String[]{}));
                storage.getStackResponses().put(2, (slots) -> {
                    storage.getInventories().get(a.getSource().getContainerId()).set(a.getSlot(), destination);
                    storage.getInventories().get(b.getSource().getContainerId()).set(b.getSlot(), source);
                });
            }

            wrapped.setPacket(stackPacket);
        });
    }

    private static ContainerSlotType parseContainerId(int containerId) {
        return switch (containerId) {
            case ContainerId.INVENTORY -> ContainerSlotType.INVENTORY;
            case ContainerId.HOTBAR -> ContainerSlotType.HOTBAR;
            case ContainerId.ARMOR -> ContainerSlotType.ARMOR;
            case ContainerId.OFFHAND -> ContainerSlotType.OFFHAND;
            case ContainerId.UI -> ContainerSlotType.CURSOR;
            case ContainerId.BEACON -> ContainerSlotType.BEACON_PAYMENT;
            case ContainerId.ENCHANT_INPUT -> ContainerSlotType.ENCHANTING_INPUT;
            case ContainerId.ENCHANT_OUTPUT -> ContainerSlotType.ENCHANTING_MATERIAL;
            default -> ContainerSlotType.UNKNOWN;
        };
    }

    private static int parseContainerId(ContainerSlotType containerId) {
        return switch (containerId) {
            case UNKNOWN -> ContainerId.NONE;
            case INVENTORY, HOTBAR, HOTBAR_AND_INVENTORY -> ContainerId.INVENTORY;
            case ARMOR -> ContainerId.ARMOR;
            case OFFHAND -> ContainerId.OFFHAND;
            case CURSOR -> ContainerId.UI;
            case BEACON_PAYMENT -> ContainerId.BEACON;
            case ENCHANTING_INPUT -> ContainerId.ENCHANT_INPUT;
            case ENCHANTING_MATERIAL -> ContainerId.ENCHANT_OUTPUT;
            default -> ContainerSlotType.UNKNOWN.ordinal();
        };
    }
}
