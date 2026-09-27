package com.github.blackjack200.ouranos.translators.new_to_old.v712to686;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;

public class Protocol712to686 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerServerbound(InventoryTransactionPacket.class, wrapped -> {
            final InventoryTransactionPacket packet = (InventoryTransactionPacket) wrapped.getPacket();

            packet.setTriggerType(ItemUseTransaction.TriggerType.PLAYER_INPUT);
            packet.setClientInteractPrediction(ItemUseTransaction.PredictedResult.SUCCESS);
        });
    }
}
