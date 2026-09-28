package oxy.geyser.reversion;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketDefinition;
import org.cloudburstmc.protocol.bedrock.data.CommandBlockMode;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.PacketRecipient;
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
import oxy.geyser.reversion.ouranos.session.SpecialOuranosSession;
import oxy.geyser.reversion.ouranos.translators.new_to_old.v340to332.Protocol340to332;
import oxy.geyser.reversion.ouranos.translators.new_to_old.v354to340.Protocol354to340;
import oxy.geyser.reversion.ouranos.translators.new_to_old.v361to354.Protocol361to354;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-memory protocol 361 -> 354 -> 340 -> 332 translator behavior.
 *
 * <p>Direction policy: clientbound handlers only ever see packets whose codec recipient is CLIENT and
 * serverbound handlers only ever see packets whose recipient is SERVER, so the recipient assertions
 * below are the ground truth for every registration. The wire tests drive the same packets through
 * {@link SpecialOuranosSession#translateClientbound(ByteBuf, ByteBuf, int)} and
 * {@link SpecialOuranosSession#translateServerbound(ByteBuf, ByteBuf, int)} exactly like the runtime.
 */
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

    private static final class WireSession extends SpecialOuranosSession {
        WireSession(int protocolId, int targetVersion) {
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

    private static PacketRecipient recipient(int protocol, Class<? extends BedrockPacket> packetClass) {
        final BedrockCodec codec = ProtocolInfo.getPacketCodec(protocol);
        assertNotNull(codec, "missing codec " + protocol);
        final BedrockPacketDefinition<?> definition = codec.getPacketDefinition(packetClass);
        assertNotNull(definition, packetClass.getSimpleName() + " is not defined in " + protocol);
        return definition.getRecipient();
    }

    private static boolean cancelledClientbound(ProtocolToProtocol translator, BedrockPacket packet) {
        final WrappedBedrockPacket wrapped = wrap(packet, 361, 332);
        translator.passthroughClientbound(wrapped);
        return wrapped.isCancelled();
    }

    private static void assertClientboundCancel(ProtocolToProtocol translator, int source, BedrockPacket packet) {
        assertEquals(PacketRecipient.CLIENT, recipient(source, packet.getClass()),
                packet.getClass().getSimpleName() + " must be clientbound in " + source);
        assertTrue(cancelledClientbound(translator, packet), packet.getClass().getSimpleName());
    }

    /** Drives a clientbound packet through the real session path and asserts the translator drops it. */
    private static void assertClientboundDropped(WireSession session, BedrockPacket packet,
                                                 boolean clientCodecMustLackPacket) {
        final ByteBuf input = Unpooled.buffer();
        final ByteBuf output = Unpooled.buffer();
        try {
            session.encodeServer(packet, input);
            final int id = session.getServerCodec().getPacketDefinition(packet.getClass()).getId();
            assertNull(session.translateClientbound(input, output, id), packet.getClass().getSimpleName());
            if (clientCodecMustLackPacket) {
                assertNull(session.getClientCodec().getPacketDefinition(packet.getClass()),
                        packet.getClass().getSimpleName() + " must be unknown to the client codec");
            }
        } finally {
            input.release();
            output.release();
        }
    }

    private static LevelEventGenericPacket levelEventGeneric() {
        final LevelEventGenericPacket packet = new LevelEventGenericPacket();
        packet.setType(LevelEvent.SOUND_CLICK);
        packet.setTag(NbtMap.EMPTY);
        return packet;
    }

    private static VideoStreamConnectPacket videoStreamConnect() {
        final VideoStreamConnectPacket packet = new VideoStreamConnectPacket();
        packet.setAddress("");
        packet.setAction(VideoStreamConnectPacket.Action.OPEN);
        return packet;
    }

    private static UpdateBlockPropertiesPacket updateBlockProperties() {
        final UpdateBlockPropertiesPacket packet = new UpdateBlockPropertiesPacket();
        packet.setProperties(NbtMap.EMPTY);
        return packet;
    }

    private static StructureTemplateDataResponsePacket structureTemplateDataResponse() {
        final StructureTemplateDataResponsePacket packet = new StructureTemplateDataResponsePacket();
        packet.setName("");
        packet.setSave(false);
        packet.setTag(NbtMap.EMPTY);
        return packet;
    }

    /**
     * Legacy (pre-1.12) chunk body: one subchunk in format 0, a 256-byte biome palette, no border
     * blocks and no block entities. GlobalWorldTranslator rewrites exactly this shape before the
     * version translator chain runs.
     */
    private static LevelChunkPacket legacyChunk(int chunkX, int chunkZ, boolean cachingEnabled) {
        final LevelChunkPacket packet = new LevelChunkPacket();
        packet.setChunkX(chunkX);
        packet.setChunkZ(chunkZ);
        packet.setSubChunksLength(1);
        packet.setCachingEnabled(cachingEnabled);
        final ByteBuf data = Unpooled.buffer(1 + 4096 + 2048 + 256 + 1);
        data.writeByte(0); // subchunk format 0: 4096 block ids + 2048 block data, copied verbatim
        data.writeZero(4096 + 2048);
        data.writeZero(256); // legacy biome palette
        data.writeByte(0); // border block count
        packet.setData(data);
        return packet;
    }

    @Test
    void removedServerboundRegistrationsHaveNoClientboundHandler() {
        final Protocol361to354 v361to354 = new Protocol361to354();
        for (BedrockPacket packet : List.of(
                new ClientCacheStatusPacket(),
                new StructureTemplateDataRequestPacket(),
                new ClientCacheBlobStatusPacket())) {
            final WrappedBedrockPacket wrapped = wrap(packet, 361, 354);
            v361to354.passthroughClientbound(wrapped);
            assertFalse(wrapped.isCancelled(), packet.getClass().getSimpleName());
            assertEquals(PacketRecipient.SERVER, recipient(361, packet.getClass()),
                    packet.getClass().getSimpleName() + " is serverbound in 361");
        }

        final MapCreateLockedCopyPacket mapCopy = new MapCreateLockedCopyPacket();
        final WrappedBedrockPacket mapWrapped = wrap(mapCopy, 354, 340);
        new Protocol354to340().passthroughClientbound(mapWrapped);
        assertFalse(mapWrapped.isCancelled());
        assertEquals(PacketRecipient.SERVER, recipient(354, MapCreateLockedCopyPacket.class));

        final LecternUpdatePacket lectern = new LecternUpdatePacket();
        lectern.setPage(5);
        lectern.setTotalPages(9);
        final WrappedBedrockPacket lecternWrapped = wrap(lectern, 340, 332);
        new Protocol340to332().passthroughClientbound(lecternWrapped);
        assertFalse(lecternWrapped.isCancelled());
        assertEquals(5, lectern.getPage(), "a clientbound pass must not run the serverbound normalization");
        assertEquals(9, lectern.getTotalPages(), "a clientbound pass must not run the serverbound normalization");
        assertEquals(PacketRecipient.SERVER, recipient(340, LecternUpdatePacket.class));
    }

    @Test
    void remainingClientboundCancelHandlersMatchClientRecipients() {
        final Protocol361to354 v361to354 = new Protocol361to354();
        assertClientboundCancel(v361to354, 361, levelEventGeneric());
        assertClientboundCancel(v361to354, 361, structureTemplateDataResponse());
        assertClientboundCancel(v361to354, 361, updateBlockProperties());
        assertClientboundCancel(v361to354, 361, new ClientCacheMissResponsePacket());

        assertClientboundCancel(new Protocol354to340(), 354, new OnScreenTextureAnimationPacket());
        assertClientboundCancel(new Protocol340to332(), 340, videoStreamConnect());
    }

    @Test
    void clientboundCancelHandlersDropPacketsOnTheWire() {
        final WireSession v354 = new WireSession(354, 361);
        assertClientboundDropped(v354, levelEventGeneric(), true);
        assertClientboundDropped(v354, structureTemplateDataResponse(), true);
        assertClientboundDropped(v354, updateBlockProperties(), true);
        assertClientboundDropped(v354, new ClientCacheMissResponsePacket(), true);

        assertClientboundDropped(new WireSession(340, 354), new OnScreenTextureAnimationPacket(), true);
        assertClientboundDropped(new WireSession(332, 340), videoStreamConnect(), true);
    }

    @Test
    void normalizationDirectionsMatchCodecRecipients() {
        assertEquals(PacketRecipient.CLIENT, recipient(361, LevelChunkPacket.class));
        assertEquals(PacketRecipient.CLIENT, recipient(354, LevelChunkPacket.class));
        assertEquals(PacketRecipient.SERVER, recipient(361, CommandBlockUpdatePacket.class));
        assertEquals(PacketRecipient.SERVER, recipient(354, CommandBlockUpdatePacket.class));
        assertEquals(PacketRecipient.SERVER, recipient(340, LecternUpdatePacket.class));
        assertEquals(PacketRecipient.SERVER, recipient(354, LecternUpdatePacket.class));
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
    void levelChunkCacheIsRewrittenOnTheWire() {
        final WireSession session = new WireSession(332, 361);

        // Object path gives the expected rewritten payload after GlobalWorldTranslator ran.
        final LevelChunkPacket expectedPacket = (LevelChunkPacket) session.translateClientbound(legacyChunk(3, -7, true));
        assertNotNull(expectedPacket);
        assertFalse(expectedPacket.isCachingEnabled(), "1.11.0/1.9.0 cannot negotiate the blob cache");
        final byte[] expectedPayload = new byte[expectedPacket.getData().readableBytes()];
        expectedPacket.getData().getBytes(expectedPacket.getData().readerIndex(), expectedPayload);
        assertTrue(expectedPayload.length > 0);
        expectedPacket.release();

        final LevelChunkPacket chunk = legacyChunk(3, -7, true);
        chunk.getBlobIds().add(7L);
        final ByteBuf input = Unpooled.buffer();
        final ByteBuf output = Unpooled.buffer();
        try {
            session.encodeServer(chunk, input);
            final int id = session.getServerCodec().getPacketDefinition(LevelChunkPacket.class).getId();
            final Integer translatedId = session.translateClientbound(input, output, id);
            assertNotNull(translatedId);

            final LevelChunkPacket decoded = (LevelChunkPacket) session.getClientCodec().tryDecode(
                    session.getClientCodecHelper(), output, translatedId);
            try {
                assertEquals(0, output.readableBytes());
                assertFalse(decoded.isCachingEnabled(), "the 361->354 rewrite must clear the cache flag");
                assertEquals(3, decoded.getChunkX());
                assertEquals(-7, decoded.getChunkZ());
                final byte[] payload = new byte[decoded.getData().readableBytes()];
                decoded.getData().readBytes(payload);
                assertArrayEquals(expectedPayload, payload, "the rewritten chunk payload must survive serialization");
            } finally {
                decoded.release();
            }
        } finally {
            chunk.release();
            input.release();
            output.release();
        }
    }

    @Test
    void payloadlessCachedChunksAreDroppedOnTheWire() {
        final WireSession session = new WireSession(332, 361);
        final LevelChunkPacket cachedOnly = new LevelChunkPacket();
        cachedOnly.setChunkX(1);
        cachedOnly.setChunkZ(1);
        cachedOnly.setCachingEnabled(true);
        cachedOnly.getBlobIds().add(42L);
        cachedOnly.setData(Unpooled.buffer(0));

        final ByteBuf input = Unpooled.buffer();
        try {
            session.encodeServer(cachedOnly, input);
            final int id = session.getServerCodec().getPacketDefinition(LevelChunkPacket.class).getId();
            final LevelChunkPacket decoded = (LevelChunkPacket) session.getServerCodec().tryDecode(
                    session.getServerCodecHelper(), input, id);
            try {
                assertNotNull(session.getClientCodec().getPacketDefinition(LevelChunkPacket.class),
                        "the client codec knows LevelChunk, so the drop must come from the 361->354 handler");
                // GlobalWorldTranslator rewrites LevelChunk before the version chain and cannot parse a
                // payloadless body, so the version chain is driven exactly as OuranosSession does after it.
                final WrappedBedrockPacket wrapped = new WrappedBedrockPacket(session, 361, 332, decoded, false);
                for (ProtocolToProtocol translator : ProtocolInfo.getTranslators(361, 332)) {
                    translator.passthroughClientbound(wrapped);
                }
                assertTrue(wrapped.isCancelled(), "a cached chunk without payload has nothing to deliver");
            } finally {
                decoded.release();
            }
        } finally {
            cachedOnly.release();
            input.release();
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
    void commandBlockUpdateDefaultsSurviveTheWire() {
        final WireSession session = new WireSession(354, 361);
        final CommandBlockUpdatePacket packet = new CommandBlockUpdatePacket();
        packet.setBlock(true);
        packet.setBlockPosition(Vector3i.from(1, 2, 3));
        packet.setMode(CommandBlockMode.NORMAL);
        packet.setRedstoneMode(false);
        packet.setConditional(false);
        packet.setCommand("say hi");
        packet.setLastOutput("");
        packet.setName("tester");
        packet.setOutputTracked(false);
        packet.setTickDelay(37);
        packet.setExecutingOnFirstTick(false);

        final ByteBuf input = Unpooled.buffer();
        final ByteBuf output = Unpooled.buffer();
        try {
            session.encodeClient(packet, input);
            final int id = session.getClientCodec().getPacketDefinition(CommandBlockUpdatePacket.class).getId();
            final Integer translatedId = session.translateServerbound(input, output, id);
            assertNotNull(translatedId);

            final CommandBlockUpdatePacket decoded = (CommandBlockUpdatePacket) session.getServerCodec().tryDecode(
                    session.getServerCodecHelper(), output, translatedId);
            assertEquals(0, output.readableBytes());
            assertEquals(0, decoded.getTickDelay(), "1.11.0 cannot send tickDelay");
            assertTrue(decoded.isExecutingOnFirstTick(), "1.11.0 always executes on the first tick");
            assertEquals("say hi", decoded.getCommand());
            assertEquals(Vector3i.from(1, 2, 3), decoded.getBlockPosition());
        } finally {
            input.release();
            output.release();
        }
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
    void lecternUpdateTotalPagesSurvivesTheWire() {
        final WireSession session = new WireSession(340, 354);
        final LecternUpdatePacket packet = new LecternUpdatePacket();
        packet.setPage(3);
        packet.setBlockPosition(Vector3i.from(4, 65, -6));

        final ByteBuf input = Unpooled.buffer();
        final ByteBuf output = Unpooled.buffer();
        try {
            session.encodeClient(packet, input);
            final int id = session.getClientCodec().getPacketDefinition(LecternUpdatePacket.class).getId();
            final Integer translatedId = session.translateServerbound(input, output, id);
            assertNotNull(translatedId);

            final LecternUpdatePacket decoded = (LecternUpdatePacket) session.getServerCodec().tryDecode(
                    session.getServerCodecHelper(), output, translatedId);
            assertEquals(0, output.readableBytes());
            assertEquals(3, decoded.getPage());
            assertEquals(4, decoded.getTotalPages(), "1.10.0 cannot send totalPages");
            assertEquals(Vector3i.from(4, 65, -6), decoded.getBlockPosition());
        } finally {
            input.release();
            output.release();
        }
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
