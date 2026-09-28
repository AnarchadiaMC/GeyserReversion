package oxy.geyser.reversion.ouranos.translators.new_to_old.v340to332;

import oxy.geyser.reversion.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.packet.VideoStreamConnectPacket;

public class Protocol340to332 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        // VideoStreamConnect is CLIENT recipient in 1.10.0 and unknown to 1.9.0; cancel it while it travels down.
        this.registerClientbound(VideoStreamConnectPacket.class, wrapped -> wrapped.cancel());
    }
}
