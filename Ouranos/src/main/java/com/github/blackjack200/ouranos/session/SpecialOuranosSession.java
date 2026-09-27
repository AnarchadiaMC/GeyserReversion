package com.github.blackjack200.ouranos.session;

import com.github.blackjack200.ouranos.ProtocolInfo;
import com.github.blackjack200.ouranos.utils.BlockDictionaryRegistry;
import io.netty.buffer.ByteBuf;
import lombok.Getter;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

// Meant for GeyserReversion, since the protocol is relocated..
@SuppressWarnings("ALL")
@Getter
public abstract class SpecialOuranosSession extends OuranosSession {
    private final BedrockCodec clientCodec;
    private final BedrockCodecHelper clientCodecHelper;

    private final BedrockCodec serverCodec;
    private final BedrockCodecHelper serverCodecHelper;

    public SpecialOuranosSession(int protocolId, int targetVersion) {
        super(protocolId, targetVersion);

        this.clientCodec = ProtocolInfo.getPacketCodec(protocolId);
        this.serverCodec = ProtocolInfo.getPacketCodec(targetVersion);

        this.clientCodecHelper = this.clientCodec.createHelper();
        this.serverCodecHelper = this.serverCodec.createHelper();

        this.clientCodecHelper.setBlockDefinitions(new BlockDictionaryRegistry(protocolId, false));
        this.serverCodecHelper.setBlockDefinitions(new BlockDictionaryRegistry(targetVersion, false));
    }

    @Override
    public void setHashedBlockIds(boolean hashedBlockIds) {
        this.clientCodecHelper.setBlockDefinitions(new BlockDictionaryRegistry(this.getProtocolId(), hashedBlockIds));
        this.serverCodecHelper.setBlockDefinitions(new BlockDictionaryRegistry(this.getTargetVersion(), hashedBlockIds));
        super.setHashedBlockIds(hashedBlockIds);
    }

    public final Integer translateClientbound(ByteBuf input, ByteBuf output, int id) {
        BedrockPacket packet = this.serverCodec.tryDecode(this.serverCodecHelper, input, id);
        packet = this.translateClientbound(packet);
        if (packet == null || this.clientCodec.getPacketDefinition(packet.getClass()) == null) {
            return null;
        }

        this.clientCodec.tryEncode(this.clientCodecHelper, output, packet);
        return this.clientCodec.getPacketDefinition(packet.getClass()).getId();
    }

    public final Integer translateServerbound(ByteBuf input, ByteBuf output, int id) {
        BedrockPacket packet = this.clientCodec.tryDecode(this.clientCodecHelper, input, id);
        packet = this.translateServerbound(packet);
        if (packet == null || this.serverCodec.getPacketDefinition(packet.getClass()) == null) {
            return null;
        }

        this.serverCodec.tryEncode(this.serverCodecHelper, output, packet);
        return this.serverCodec.getPacketDefinition(packet.getClass()).getId();
    }

    public final void encodeClient(final BedrockPacket packet, final ByteBuf output) {
        this.clientCodec.tryEncode(this.clientCodecHelper, output, packet);
    }

    public final void encodeServer(final BedrockPacket packet, final ByteBuf output) {
        this.serverCodec.tryEncode(this.serverCodecHelper, output, packet);
    }
}
