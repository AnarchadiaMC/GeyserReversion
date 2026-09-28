package oxy.geyser.reversion.ouranos.translators.new_to_old.v354to340;

import oxy.geyser.reversion.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.packet.LecternUpdatePacket;
import org.cloudburstmc.protocol.bedrock.packet.OnScreenTextureAnimationPacket;

public class Protocol354to340 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        // OnScreenTextureAnimation is CLIENT recipient in 1.11.0 and unknown to 1.10.0; cancel it while it travels down.
        this.registerClientbound(OnScreenTextureAnimationPacket.class, wrapped -> wrapped.cancel());

        // LecternUpdate is SERVER recipient in 1.10.0/1.11.0; default totalPages while it travels up.
        this.registerServerbound(LecternUpdatePacket.class, wrapped -> {
            final LecternUpdatePacket packet = (LecternUpdatePacket) wrapped.getPacket();
            // totalPages was added to the 1.11.0 serializer; 1.10.0 clients cannot send it, and
            // page + 1 is the smallest value that keeps the page the client did send valid.
            packet.setTotalPages(packet.getPage() + 1);
        });
    }
}
