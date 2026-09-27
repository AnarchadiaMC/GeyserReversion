package oxy.geyser.reversion.session;

import oxy.geyser.reversion.ouranos.session.SpecialOuranosSession;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Getter;
import lombok.Setter;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import oxy.geyser.reversion.DuplicatedProtocolInfo;
import oxy.geyser.reversion.GeyserReversion;
import oxy.geyser.reversion.util.CodecUtil;
import oxy.geyser.reversion.util.TranslationFailures;

import java.util.Objects;

@Getter @Setter
public class GeyserTranslatedUser extends SpecialOuranosSession {
    private final GeyserSession session;

    private final BedrockCodec cloudburstClientCodec;
    private final BedrockCodecHelper cloudburstClientCodecHelper;

    private final BedrockCodec cloudburstServerCodec;
    private final BedrockCodecHelper cloudburstServerCodecHelper;

    private final TranslationFailures failures = new TranslationFailures();

    private boolean authenticated;

    public void setAuthenticated(boolean authenticated) {
        if (this.authenticated == authenticated) {
            return;
        }
        this.authenticated = authenticated;
        if (authenticated) {
            GeyserReversion.LOGGER.info("Player with username " + session.getAuthData().name() + " joined using Minecraft version " + this.cloudburstClientCodec.getMinecraftVersion() + "!");
        }
    }

    public GeyserTranslatedUser(int protocolVersion, int serverVersion, GeyserSession session) {
        super(protocolVersion, serverVersion);
        this.session = session;

        this.cloudburstClientCodec = Objects.requireNonNull(DuplicatedProtocolInfo.getPacketCodec(protocolVersion),
                "Unsupported client Bedrock protocol: " + protocolVersion);
        this.cloudburstServerCodec = Objects.requireNonNull(DuplicatedProtocolInfo.getPacketCodec(serverVersion),
                "Unsupported bridge Bedrock protocol: " + serverVersion);

        // cloudburstClientCodecHelper decodes clientbound (trusted local Geyser) packets;
        // cloudburstServerCodecHelper decodes serverbound (untrusted client) bridge packets.
        this.cloudburstClientCodecHelper = CodecUtil.applyClientboundLimits(this.cloudburstClientCodec.createHelper());
        this.cloudburstServerCodecHelper = CodecUtil.applyServerboundLimits(this.cloudburstServerCodec.createHelper());

        this.cloudburstClientCodecHelper.setBlockDefinitions(new DefinitionRegistry<>() {
            @Override
            public BlockDefinition getDefinition(int runtimeId) {
                return () -> runtimeId;
            }

            @Override
            public boolean isRegistered(BlockDefinition definition) {
                return true;
            }
        });
        this.cloudburstServerCodecHelper.setBlockDefinitions(this.cloudburstClientCodecHelper.getBlockDefinitions());
    }

    @Override
    public void sendUpstreamPacket(org.cloudburstmc.protocol.bedrock.packet.BedrockPacket bedrockPacket) {
        final ByteBuf input = Unpooled.buffer();

        BedrockPacket packet = null;
        try {

            this.encodeClient(bedrockPacket, input);
            packet = this.decodeClient(input, this.getClientCodec().getPacketDefinition(bedrockPacket.getClass()).getId());
        } catch (Exception e) {
            failures.report(session, bedrockPacket, "clientbound", e);
        } finally {
            input.release();
        }

        if (packet == null) {
            return;
        }

        session.getUpstream().getSession().sendPacket(packet);
    }

    @Override
    public void sendDownstreamPacket(org.cloudburstmc.protocol.bedrock.packet.BedrockPacket bedrockPacket) {
        final ByteBuf input = Unpooled.buffer();

        BedrockPacket packet = null;
        try {

            this.encodeServer(bedrockPacket, input);
            packet = this.decodeServer(input, this.getServerCodec().getPacketDefinition(bedrockPacket.getClass()).getId());
        } catch (Exception e) {
            failures.report(session, bedrockPacket, "serverbound", e);
        } finally {
            input.release();
        }

        if (packet == null) {
            return;
        }

        Registries.BEDROCK_PACKET_TRANSLATORS.translate(packet.getClass(), packet, session, false);
    }

    public final void encodeClient(BedrockPacket packet, ByteBuf output) {
        this.cloudburstClientCodec.tryEncode(this.cloudburstClientCodecHelper, output, packet);
    }

    public final void encodeServer(BedrockPacket packet, ByteBuf output) {
        this.cloudburstServerCodec.tryEncode(this.cloudburstServerCodecHelper, output, packet);
    }

    public final BedrockPacket decodeClient(ByteBuf input, int id) {
        return this.cloudburstClientCodec.tryDecode(this.cloudburstClientCodecHelper, input, id);
    }

    public final BedrockPacket decodeServer(ByteBuf input, int id) {
        return this.cloudburstServerCodec.tryDecode(this.cloudburstServerCodecHelper, input, id);
    }
}
