package oxy.geyser.reversion.ouranos.translators.new_to_old.v712to686;

import oxy.geyser.reversion.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

public class Protocol712to686 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerServerbound(InventoryTransactionPacket.class, wrapped -> {
            final InventoryTransactionPacket packet = (InventoryTransactionPacket) wrapped.getPacket();

            packet.setTriggerType(ItemUseTransaction.TriggerType.PLAYER_INPUT);
            packet.setClientInteractPrediction(ItemUseTransaction.PredictedResult.SUCCESS);
        });

        // PlayerAuthInputPacket embeds the same ItemUseTransaction, but the pre-712 wire format
        // carries neither triggerType nor clientInteractPrediction. GlobalItemTranslator rewrites
        // the embedded transaction in place and Protocol712to686 never saw it, so the 2168 bridge
        // serializer dereferenced the null triggerType and the whole movement packet failed.
        this.registerServerbound(PlayerAuthInputPacket.class, wrapped -> {
            final PlayerAuthInputPacket packet = (PlayerAuthInputPacket) wrapped.getPacket();
            final ItemUseTransaction transaction = packet.getItemUseTransaction();
            if (transaction != null) {
                transaction.setTriggerType(ItemUseTransaction.TriggerType.PLAYER_INPUT);
                transaction.setClientInteractPrediction(ItemUseTransaction.PredictedResult.SUCCESS);
            }
        });
    }
}
