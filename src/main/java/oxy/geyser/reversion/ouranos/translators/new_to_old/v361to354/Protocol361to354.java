package oxy.geyser.reversion.ouranos.translators.new_to_old.v361to354;

import oxy.geyser.reversion.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheMissResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.CommandBlockUpdatePacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventGenericPacket;
import org.cloudburstmc.protocol.bedrock.packet.StructureTemplateDataResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPropertiesPacket;

public class Protocol361to354 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        // LevelEventGeneric is CLIENT recipient in 1.12.0 and unknown to 1.11.0; cancel it while it travels down.
        this.registerClientbound(LevelEventGenericPacket.class, wrapped -> wrapped.cancel());
        // StructureTemplateDataResponse is CLIENT recipient in 1.12.0 and cannot be decoded by 1.11.0.
        this.registerClientbound(StructureTemplateDataResponsePacket.class, wrapped -> wrapped.cancel());
        // UpdateBlockProperties is CLIENT recipient in 1.12.0 and only exists alongside the new block palette.
        this.registerClientbound(UpdateBlockPropertiesPacket.class, wrapped -> wrapped.cancel());
        // ClientCacheMissResponse is CLIENT recipient in 1.12.0 and only makes sense with the blob cache.
        this.registerClientbound(ClientCacheMissResponsePacket.class, wrapped -> wrapped.cancel());
        // LevelChunk is CLIENT recipient in 1.12.0/1.11.0; rewrite the cache fields while it travels down.
        this.registerClientbound(LevelChunkPacket.class, wrapped -> {
            final LevelChunkPacket packet = (LevelChunkPacket) wrapped.getPacket();
            // 1.12.0 introduced the client blob cache; 1.11.0 cannot request blobs, so a cached
            // chunk without a real subchunk payload has nothing to deliver and must be dropped.
            if (packet.isCachingEnabled() && (packet.getData() == null || !packet.getData().isReadable())) {
                wrapped.cancel();
                return;
            }
            packet.setCachingEnabled(false); // 1.11.0 chunk serializers ignore cache fields.
        });

        // CommandBlockUpdate is SERVER recipient in 1.11.0/1.12.0; normalize defaults while it travels up.
        this.registerServerbound(CommandBlockUpdatePacket.class, wrapped -> {
            final CommandBlockUpdatePacket packet = (CommandBlockUpdatePacket) wrapped.getPacket();
            // tickDelay/executingOnFirstTick were added to the 1.12.0 serializer; 1.11.0 clients
            // cannot send them and always execute immediately, so normalize the absent fields.
            packet.setTickDelay(0);
            packet.setExecutingOnFirstTick(true);
        });
    }
}
