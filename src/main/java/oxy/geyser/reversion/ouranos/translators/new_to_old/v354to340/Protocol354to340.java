package oxy.geyser.reversion.ouranos.translators.new_to_old.v354to340;

import oxy.geyser.reversion.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.packet.LecternUpdatePacket;
import org.cloudburstmc.protocol.bedrock.packet.MapCreateLockedCopyPacket;
import org.cloudburstmc.protocol.bedrock.packet.OnScreenTextureAnimationPacket;

public class Protocol354to340 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        // OnScreenTextureAnimation was added in 1.11.0 and has no codec entry in 1.10.0.
        this.registerClientbound(OnScreenTextureAnimationPacket.class, wrapped -> wrapped.cancel());
        // MapCreateLockedCopy (serverbound in 1.11.0) is unknown to 1.10.0; drop defensively.
        this.registerClientbound(MapCreateLockedCopyPacket.class, wrapped -> wrapped.cancel());

        this.registerServerbound(LecternUpdatePacket.class, wrapped -> {
            final LecternUpdatePacket packet = (LecternUpdatePacket) wrapped.getPacket();
            // totalPages was added to the 1.11.0 serializer; 1.10.0 clients cannot send it, and
            // page + 1 is the smallest value that keeps the page the client did send valid.
            packet.setTotalPages(packet.getPage() + 1);
        });
    }
}
