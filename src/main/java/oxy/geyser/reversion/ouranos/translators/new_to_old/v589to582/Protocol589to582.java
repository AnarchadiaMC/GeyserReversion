package oxy.geyser.reversion.ouranos.translators.new_to_old.v589to582;

import oxy.geyser.reversion.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.packet.EmotePacket;

public class Protocol589to582 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerServerbound(EmotePacket.class, wrapped -> {
            final EmotePacket packet = (EmotePacket) wrapped.getPacket();
            packet.setXuid("");
            packet.setPlatformId("");
        });
    }
}
