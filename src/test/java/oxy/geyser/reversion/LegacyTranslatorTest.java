package oxy.geyser.reversion;

import io.netty.buffer.Unpooled;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheBlobStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheMissResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.CommandBlockUpdatePacket;
import org.cloudburstmc.protocol.bedrock.packet.LecternUpdatePacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventGenericPacket;
import org.cloudburstmc.protocol.bedrock.packet.MapCreateLockedCopyPacket;
import org.cloudburstmc.protocol.bedrock.packet.OnScreenTextureAnimationPacket;
import org.cloudburstmc.protocol.bedrock.packet.StructureTemplateDataRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.StructureTemplateDataResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPropertiesPacket;
import org.cloudburstmc.protocol.bedrock.packet.VideoStreamConnectPacket;
import org.junit.jupiter.api.Test;
import oxy.geyser.reversion.ouranos.ProtocolInfo;
import oxy.geyser.reversion.ouranos.base.ProtocolToProtocol;
import oxy.geyser.reversion.ouranos.base.WrappedBedrockPacket;
import oxy.geyser.reversion.ouranos.data.bedrock.GlobalItemDataHandlers;
import oxy.geyser.reversion.ouranos.session.OuranosSession;
import oxy.geyser.reversion.ouranos.translators.new_to_old.v340to332.Protocol340to332;
import oxy.geyser.reversion.ouranos.translators.new_to_old.v354to340.Protocol354to340;
import oxy.geyser.reversion.ouranos.translators.new_to_old.v361to354.Protocol361to354;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** In-memory protocol 361 -> 354 -> 340 -> 332 translator behavior. */
class LegacyTranslatorTest {
    private static final class StubSession extends OuranosSession {
        StubSession(int protocolId, int targetVersion) {
            super(protocolId, targetVersion);
        }

        @Override
        public void sendUpstreamPacket(BedrockPacket packet) {
        }

        @Override
        public void sendDownstreamPacket(BedrockPacket packet) {
        }
    }

    private static final StubSession SESSION = new StubSession(332, 361);

    private static WrappedBedrockPacket wrap(BedrockPacket packet, int input, int output) {
        return new WrappedBedrockPacket(SESSION, input, output, packet, false);
    }

    private static boolean cancelledClientbound(ProtocolToProtocol translator, BedrockPacket packet) {
        final WrappedBedrockPacket wrapped = wrap(packet, 361, 332);
        translator.passthroughClientbound(wrapped);
        return wrapped.isCancelled();
    }

    @Test
    void v361OnlyClientboundPacketsAreCancelledForV354() {
        final Protocol361to354 translator = new Protocol361to354();
        for (BedrockPacket packet : List.of(
                new LevelEventGenericPacket(),
                new ClientCacheStatusPacket(),
                new StructureTemplateDataRequestPacket(),
                new StructureTemplateDataResponsePacket(),
                new UpdateBlockPropertiesPacket(),
                new ClientCacheBlobStatusPacket(),
                new ClientCacheMissResponsePacket())) {
            assertTrue(cancelledClientbound(translator, packet), packet.getClass().getSimpleName());
        }
    }

    @Test
    void v354OnlyClientboundPacketsAreCancelledForV340() {
        final Protocol354to340 translator = new Protocol354to340();
        for (BedrockPacket packet : List.of(
                new OnScreenTextureAnimationPacket(),
                new MapCreateLockedCopyPacket())) {
            assertTrue(cancelledClientbound(translator, packet), packet.getClass().getSimpleName());
        }
    }

    @Test
    void v340OnlyClientboundPacketsAreCancelledForV332() {
        final Protocol340to332 translator = new Protocol340to332();
        for (BedrockPacket packet : List.of(
                new LecternUpdatePacket(),
                new VideoStreamConnectPacket())) {
            assertTrue(cancelledClientbound(translator, packet), packet.getClass().getSimpleName());
        }
    }

    @Test
    void supportedClientboundPacketsPassThroughUntouched() {
        for (ProtocolToProtocol translator : List.of(new Protocol361to354(), new Protocol354to340(), new Protocol340to332())) {
            final UpdateBlockPacket packet = new UpdateBlockPacket();
            final WrappedBedrockPacket wrapped = wrap(packet, 361, 332);
            translator.passthroughClientbound(wrapped);
            assertFalse(wrapped.isCancelled(), translator.getClass().getSimpleName());
            assertSame(packet, wrapped.getPacket());
        }
    }

    @Test
    void levelChunkCacheIsDisabledAndPayloadlessCachedChunksAreDropped() {
        final Protocol361to354 translator = new Protocol361to354();

        final LevelChunkPacket withPayload = new LevelChunkPacket();
        withPayload.setCachingEnabled(true);
        withPayload.setData(Unpooled.wrappedBuffer(new byte[]{1, 2, 3}));
        try {
            final WrappedBedrockPacket wrapped = wrap(withPayload, 361, 354);
            translator.passthroughClientbound(wrapped);
            assertFalse(wrapped.isCancelled());
            assertFalse(withPayload.isCachingEnabled());
        } finally {
            withPayload.release();
        }

        final LevelChunkPacket cachedOnly = new LevelChunkPacket();
        cachedOnly.setCachingEnabled(true);
        cachedOnly.getBlobIds().add(42L);
        cachedOnly.setData(Unpooled.buffer(0));
        try {
            final WrappedBedrockPacket wrapped = wrap(cachedOnly, 361, 354);
            translator.passthroughClientbound(wrapped);
            assertTrue(wrapped.isCancelled());
        } finally {
            cachedOnly.release();
        }

        final LevelChunkPacket plain = new LevelChunkPacket();
        plain.setData(Unpooled.wrappedBuffer(new byte[]{4}));
        try {
            final WrappedBedrockPacket wrapped = wrap(plain, 361, 354);
            translator.passthroughClientbound(wrapped);
            assertFalse(wrapped.isCancelled());
            assertFalse(plain.isCachingEnabled());
        } finally {
            plain.release();
        }
    }

    @Test
    void v354CommandBlockUpdateGetsV361Defaults() {
        final Protocol361to354 translator = new Protocol361to354();
        final CommandBlockUpdatePacket packet = new CommandBlockUpdatePacket();
        packet.setBlock(true);
        packet.setCommand("say hi");
        packet.setTickDelay(37);
        packet.setExecutingOnFirstTick(false);

        final WrappedBedrockPacket wrapped = wrap(packet, 354, 361);
        translator.passthroughServerbound(wrapped);

        assertFalse(wrapped.isCancelled());
        assertEquals(0, packet.getTickDelay());
        assertTrue(packet.isExecutingOnFirstTick());
    }

    @Test
    void v340LecternUpdateGetsV354TotalPagesDefault() {
        final Protocol354to340 translator = new Protocol354to340();
        final LecternUpdatePacket packet = new LecternUpdatePacket();
        packet.setPage(3);
        packet.setBlockPosition(Vector3i.ZERO);

        final WrappedBedrockPacket wrapped = wrap(packet, 340, 354);
        translator.passthroughServerbound(wrapped);

        assertFalse(wrapped.isCancelled());
        assertEquals(4, packet.getTotalPages());
    }

    @Test
    void legacyChainIsDiscoverableInDescendingOrder() {
        assertEquals(List.of(Protocol361to354.class), classes(ProtocolInfo.getTranslators(361, 354)));
        assertEquals(List.of(Protocol361to354.class, Protocol354to340.class, Protocol340to332.class),
                classes(ProtocolInfo.getTranslators(361, 332)));
        assertEquals(List.of(Protocol354to340.class, Protocol340to332.class),
                classes(ProtocolInfo.getTranslators(354, 332)));
    }

    @Test
    void legacyCodecsAndSchemaIdsAreRegistered() {
        assertNotNull(ProtocolInfo.getPacketCodec(354));
        assertNotNull(ProtocolInfo.getPacketCodec(340));
        assertNotNull(ProtocolInfo.getPacketCodec(332));
        assertNotNull(DuplicatedProtocolInfo.getPacketCodec(354));
        assertNotNull(DuplicatedProtocolInfo.getPacketCodec(340));
        assertNotNull(DuplicatedProtocolInfo.getPacketCodec(332));
        // Only schema 0001 exists at or below 1.11.0, so all three legacy protocols downgrade from it.
        assertEquals(1, GlobalItemDataHandlers.SCHEMA_ID.get(354));
        assertEquals(1, GlobalItemDataHandlers.SCHEMA_ID.get(340));
        assertEquals(1, GlobalItemDataHandlers.SCHEMA_ID.get(332));
    }

    private static List<Class<?>> classes(List<ProtocolToProtocol> translators) {
        return translators.stream().<Class<?>>map(ProtocolToProtocol::getClass).toList();
    }
}
