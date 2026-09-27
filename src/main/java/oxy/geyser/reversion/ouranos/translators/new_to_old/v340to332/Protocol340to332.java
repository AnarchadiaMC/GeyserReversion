package oxy.geyser.reversion.ouranos.translators.new_to_old.v340to332;

import oxy.geyser.reversion.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.packet.LecternUpdatePacket;
import org.cloudburstmc.protocol.bedrock.packet.VideoStreamConnectPacket;

public class Protocol340to332 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        // LecternUpdate was added in 1.10.0 (serverbound); 1.9.0 has no codec entry, drop defensively.
        this.registerClientbound(LecternUpdatePacket.class, wrapped -> wrapped.cancel());
        // VideoStreamConnect was added in 1.10.0 and cannot be decoded by 1.9.0.
        this.registerClientbound(VideoStreamConnectPacket.class, wrapped -> wrapped.cancel());
    }
}
