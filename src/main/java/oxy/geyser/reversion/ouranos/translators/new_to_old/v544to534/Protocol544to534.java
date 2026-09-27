package oxy.geyser.reversion.ouranos.translators.new_to_old.v544to534;

import oxy.geyser.reversion.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormResponsePacket;

import java.util.Optional;

public class Protocol544to534 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerServerbound(ModalFormResponsePacket.class, wrapped -> {
            final ModalFormResponsePacket packet = (ModalFormResponsePacket) wrapped.getPacket();
            packet.setCancelReason(Optional.empty());
        });
    }
}
