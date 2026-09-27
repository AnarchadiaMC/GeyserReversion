package com.github.blackjack200.ouranos.translators.old_to_new.v408to419;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponse;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseStatus;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackResponsePacket;

public class Protocol408to419 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerClientbound(ItemStackResponsePacket.class, wrapped -> {
            final ItemStackResponsePacket packet = (ItemStackResponsePacket) wrapped.getPacket();
            packet.getEntries().replaceAll(entry -> new ItemStackResponse(entry.isSuccess() ? ItemStackResponseStatus.OK : ItemStackResponseStatus.ERROR, entry.getRequestId(), entry.getContainers()));
        });
    }
}
