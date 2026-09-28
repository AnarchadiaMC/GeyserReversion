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
import oxy.geyser.reversion.ouranos.converter.ItemTypeDictionary;
import oxy.geyser.reversion.ouranos.translators.new_to_old.v1001to944.Protocol1001to944;
import oxy.geyser.reversion.ouranos.translators.new_to_old.v340to332.Protocol340to332;
import oxy.geyser.reversion.ouranos.translators.new_to_old.v354to340.Protocol354to340;
import oxy.geyser.reversion.ouranos.translators.new_to_old.v361to354.Protocol361to354;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.GraphicsMode;
import org.cloudburstmc.protocol.bedrock.data.GraphicsOverrideParameterType;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.data.attributelayer.AttributeLayerSettings;
import org.cloudburstmc.protocol.bedrock.data.attributelayer.UpdateAttributeLayerSettingsData;
import org.cloudburstmc.protocol.bedrock.data.definitions.DimensionDefinition;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.EnchantData;
import org.cloudburstmc.protocol.bedrock.data.inventory.EnchantOptionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.BossEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientboundAttributeLayerSyncPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientboundUpdateSoundDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.DebugDrawerPacket;
import org.cloudburstmc.protocol.bedrock.packet.DimensionDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.GraphicsParameterOverridePacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.LocatorBarPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovementPredictionSyncPacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket;
import org.cloudburstmc.protocol.bedrock.packet.PartyChangedPacket;
import org.cloudburstmc.protocol.bedrock.packet.PartyDestinationCookieResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.PlaySoundPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerEnchantOptionsPacket;
import org.cloudburstmc.protocol.bedrock.packet.SendPartyDestinationCookiePacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerPresenceInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerStoreInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerboundDiagnosticsPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.SubChunkRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateClientOptionsPacket;
import org.cloudburstmc.protocol.common.util.TextConverter;

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

    // ============================================================================================
    // 1.26.30 (1001) -> 1.26.10 (944) downgrade.
    //
    // Direction policy follows the legacy tests above: the session is (protocolId = 944 legacy
    // client, targetVersion = 1001 modern server), so clientbound wire tests encode with the 1001
    // server codec and decode with the 944 client codec, and serverbound wire tests encode with the
    // 944 client codec and decode with the 1001 server codec.
    // ============================================================================================

    private static final StubSession SESSION_944 = new StubSession(944, 1001);

    private static Protocol1001to944 newV1001to944() {
        return new Protocol1001to944();
    }

    private static void assertCancelledClientbound1001(Protocol1001to944 translator, BedrockPacket packet) {
        final WrappedBedrockPacket wrapped = new WrappedBedrockPacket(SESSION_944, 1001, 944, packet, false);
        translator.passthroughClientbound(wrapped);
        assertTrue(wrapped.isCancelled(), packet.getClass().getSimpleName());
    }

    @SuppressWarnings("unchecked")
    private static <T extends BedrockPacket> T clientboundWire1001(WireSession session, BedrockPacket packet) {
        final ByteBuf input = Unpooled.buffer();
        final ByteBuf output = Unpooled.buffer();
        try {
            session.encodeServer(packet, input);
            final int id = session.getServerCodec().getPacketDefinition(packet.getClass()).getId();
            final Integer translatedId = session.translateClientbound(input, output, id);
            assertNotNull(translatedId, packet.getClass().getSimpleName());
            final T decoded = (T) session.getClientCodec().tryDecode(session.getClientCodecHelper(), output, translatedId);
            assertEquals(0, output.readableBytes());
            return decoded;
        } finally {
            input.release();
            output.release();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends BedrockPacket> T serverboundWire944(WireSession session, BedrockPacket packet) {
        final ByteBuf input = Unpooled.buffer();
        final ByteBuf output = Unpooled.buffer();
        try {
            session.encodeClient(packet, input);
            final int id = session.getClientCodec().getPacketDefinition(packet.getClass()).getId();
            final Integer translatedId = session.translateServerbound(input, output, id);
            assertNotNull(translatedId, packet.getClass().getSimpleName());
            final T decoded = (T) session.getServerCodec().tryDecode(session.getServerCodecHelper(), output, translatedId);
            assertEquals(0, output.readableBytes());
            return decoded;
        } finally {
            input.release();
            output.release();
        }
    }

    private static ItemData item1001(String identifier, int count) {
        final var entry = ItemTypeDictionary.getInstance(1001).getEntries().get(identifier);
        assertNotNull(entry, identifier + " in 1001");
        return ItemData.builder().definition(entry.toDefinition(identifier)).count(count).build();
    }

    private static void initItemDefinitions(WireSession session) {
        // StartGame initializes the BlockDictionaryStorage that block items are translated through.
        session.translateClientbound(new StartGamePacket());
        final ItemComponentPacket components = new ItemComponentPacket();
        components.getItems().addAll(ItemTypeDictionary.getInstance(1001).getEntries().entrySet()
                .stream().map(e -> e.getValue().toDefinition(e.getKey())).toList());
        final var registry = org.cloudburstmc.protocol.common.SimpleDefinitionRegistry
                .<org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition>builder();
        components.getItems().forEach(registry::add);
        final var definitions = registry.build();
        session.getServerCodecHelper().setItemDefinitions(definitions);
        session.getClientCodecHelper().setItemDefinitions(new oxy.geyser.reversion.ouranos.utils.ItemTypeDictionaryRegistry(definitions, 944));
    }

    @Test
    void v1001PacketRecipientsMatchTranslatorDirections() {
        assertEquals(PacketRecipient.CLIENT, recipient(1001, StartGamePacket.class));
        assertEquals(PacketRecipient.BOTH, recipient(1001, BossEventPacket.class));
        assertEquals(PacketRecipient.CLIENT, recipient(1001, PlaySoundPacket.class));
        assertEquals(PacketRecipient.SERVER, recipient(1001, MovementPredictionSyncPacket.class));
        assertEquals(PacketRecipient.CLIENT, recipient(1001, GraphicsParameterOverridePacket.class));
        assertEquals(PacketRecipient.CLIENT, recipient(1001, LocatorBarPacket.class));
        assertEquals(PacketRecipient.CLIENT, recipient(1001, DebugDrawerPacket.class));
        assertEquals(PacketRecipient.CLIENT, recipient(1001, DimensionDataPacket.class));
        assertEquals(PacketRecipient.CLIENT, recipient(1001, ClientboundAttributeLayerSyncPacket.class));
        assertEquals(PacketRecipient.SERVER, recipient(1001, PartyChangedPacket.class));
        assertEquals(PacketRecipient.SERVER, recipient(1001, SubChunkRequestPacket.class));
        assertEquals(PacketRecipient.SERVER, recipient(1001, ServerboundDiagnosticsPacket.class));
        assertEquals(PacketRecipient.SERVER, recipient(1001, UpdateClientOptionsPacket.class));
        assertEquals(PacketRecipient.BOTH, recipient(1001, MoveEntityAbsolutePacket.class));
        assertEquals(PacketRecipient.CLIENT, recipient(1001, PlayerEnchantOptionsPacket.class));
        assertEquals(PacketRecipient.CLIENT, recipient(1001, InventorySlotPacket.class));
        assertEquals(PacketRecipient.CLIENT, recipient(1001, InventoryContentPacket.class));
    }

    @Test
    void startGameStripsV1001EditorFields() {
        final StartGamePacket packet = new StartGamePacket();
        packet.setServerEditorConnectionPolicy(3);
        packet.setAllowAnonymousBlockDropsInEditorWorlds(true);
        packet.setLoggingChat(true);

        final WrappedBedrockPacket wrapped = new WrappedBedrockPacket(SESSION_944, 1001, 944, packet, false);
        newV1001to944().passthroughClientbound(wrapped);

        assertFalse(wrapped.isCancelled());
        assertEquals(0, packet.getServerEditorConnectionPolicy());
        assertFalse(packet.isAllowAnonymousBlockDropsInEditorWorlds());
        assertFalse(packet.isLoggingChat());
    }

    @Test
    void entityEventStripsFireAtPosition() {
        final EntityEventPacket packet = new EntityEventPacket();
        packet.setFireAtPosition(Vector3f.from(1, 2, 3));

        final WrappedBedrockPacket wrapped = new WrappedBedrockPacket(SESSION_944, 1001, 944, packet, false);
        newV1001to944().passthroughClientbound(wrapped);

        assertFalse(wrapped.isCancelled());
        assertNull(packet.getFireAtPosition());
    }

    @Test
    void playSoundStripsServerSoundHandle() {
        final PlaySoundPacket packet = new PlaySoundPacket();
        packet.setServerSoundHandle(42L);

        final WrappedBedrockPacket wrapped = new WrappedBedrockPacket(SESSION_944, 1001, 944, packet, false);
        newV1001to944().passthroughClientbound(wrapped);

        assertFalse(wrapped.isCancelled());
        assertNull(packet.getServerSoundHandle());
    }

    @Test
    void movementPredictionSyncDefaultsSurviveTheWire() {
        // MovementPredictionSync is serverbound; the 944 client wire (v776) has no v975 fields and the
        // 1001 server codec re-encodes the defaults, so no handler is required.
        final WireSession session = new WireSession(944, 1001);
        final MovementPredictionSyncPacket packet = new MovementPredictionSyncPacket();
        packet.setBoundingBox(Vector3f.ONE);
        packet.setSpeed(1.5f);
        packet.setUnderwaterSpeed(2.5f);
        packet.setLavaSpeed(3.5f);
        packet.setJumpStrength(4.5f);
        packet.setHealth(5.5f);
        packet.setHunger(6.5f);
        packet.setRuntimeEntityId(13L);

        final MovementPredictionSyncPacket decoded = serverboundWire944(session, packet);
        assertEquals(Vector3f.ONE, decoded.getBoundingBox());
        assertEquals(1.5f, decoded.getSpeed());
        assertEquals(2.5f, decoded.getUnderwaterSpeed());
        assertEquals(3.5f, decoded.getLavaSpeed());
        assertEquals(4.5f, decoded.getJumpStrength());
        assertEquals(5.5f, decoded.getHealth());
        assertEquals(6.5f, decoded.getHunger());
        assertEquals(13L, decoded.getRuntimeEntityId());
        assertEquals(0f, decoded.getUnknown1());
        assertEquals(0f, decoded.getUnknown2());
        assertEquals(0f, decoded.getUnknown3());
        assertFalse(decoded.isFlying());
    }

    @Test
    void graphicsParameterOverrideStripsPlayerIdentifier() {
        final GraphicsParameterOverridePacket packet = new GraphicsParameterOverridePacket();
        packet.setPlayerIdentifier("player1");

        final WrappedBedrockPacket wrapped = new WrappedBedrockPacket(SESSION_944, 1001, 944, packet, false);
        newV1001to944().passthroughClientbound(wrapped);

        assertFalse(wrapped.isCancelled());
        assertNull(packet.getPlayerIdentifier());
    }

    @Test
    void locatorBarAndDebugDrawerAreCancelled() {
        assertCancelledClientbound1001(newV1001to944(), new LocatorBarPacket());
        assertCancelledClientbound1001(newV1001to944(), new DebugDrawerPacket());
    }

    @Test
    void locatorBarAndDebugDrawerAreDroppedOnTheWire() {
        final WireSession session = new WireSession(944, 1001);
        for (BedrockPacket packet : List.of(new LocatorBarPacket(), new DebugDrawerPacket())) {
            final ByteBuf input = Unpooled.buffer();
            final ByteBuf output = Unpooled.buffer();
            try {
                session.encodeServer(packet, input);
                final int id = session.getServerCodec().getPacketDefinition(packet.getClass()).getId();
                assertNull(session.translateClientbound(input, output, id), packet.getClass().getSimpleName());
                assertEquals(0, output.readableBytes());
            } finally {
                input.release();
                output.release();
            }
        }
    }

    @Test
    void levelSoundEventSoundNameSurvivesAsIdOnTheWire() {
        final WireSession session = new WireSession(944, 1001);
        final LevelSoundEventPacket packet = new LevelSoundEventPacket();
        packet.setSound(SoundEvent.HIT);
        packet.setPosition(Vector3f.ZERO);
        packet.setExtraData(7);
        packet.setIdentifier("");
        packet.setBabySound(false);
        packet.setRelativeVolumeDisabled(false);
        packet.setEntityUniqueId(5L);
        packet.setFireAtPosition(Vector3f.ONE);

        final LevelSoundEventPacket decoded = clientboundWire1001(session, packet);
        assertEquals(SoundEvent.HIT, decoded.getSound());
        assertEquals(7, decoded.getExtraData());
        assertEquals(5L, decoded.getEntityUniqueId());
        assertNull(decoded.getFireAtPosition());
    }

    @Test
    void bossEventSurvivesTheWire() {
        final WireSession session = new WireSession(944, 1001);
        session.getServerCodecHelper().setTextConverter(TextConverter.DEFAULT);
        session.getClientCodecHelper().setTextConverter(TextConverter.DEFAULT);

        final BossEventPacket packet = new BossEventPacket();
        packet.setBossUniqueEntityId(11L);
        packet.setPlayerUniqueEntityId(22L);
        packet.setAction(BossEventPacket.Action.CREATE);
        packet.setTitle("Boss");
        packet.setFilteredTitle("Boss");
        packet.setHealthPercentage(0.5f);
        packet.setColor(3);
        packet.setOverlay(2);

        final BossEventPacket decoded = clientboundWire1001(session, packet);
        assertEquals(11L, decoded.getBossUniqueEntityId());
        // The v776 layout (used by v944) only carries playerUniqueEntityId for the QUERY,
        // REGISTER_PLAYER and UNREGISTER_PLAYER actions, so it is dropped for CREATE.
        assertEquals(0L, decoded.getPlayerUniqueEntityId());
        assertEquals(BossEventPacket.Action.CREATE, decoded.getAction());
        assertEquals("Boss", decoded.getTitle());
        assertEquals("Boss", decoded.getFilteredTitle());
        assertEquals(0.5f, decoded.getHealthPercentage());
        assertEquals(3, decoded.getColor());
        assertEquals(2, decoded.getOverlay());
    }

    @Test
    void entityEventSurvivesTheWire() {
        final WireSession session = new WireSession(944, 1001);
        final EntityEventPacket packet = new EntityEventPacket();
        packet.setRuntimeEntityId(9L);
        packet.setType(EntityEventType.HURT);
        packet.setData(3);
        packet.setFireAtPosition(Vector3f.ZERO);

        final EntityEventPacket decoded = clientboundWire1001(session, packet);
        assertEquals(9L, decoded.getRuntimeEntityId());
        assertEquals(EntityEventType.HURT, decoded.getType());
        assertEquals(3, decoded.getData());
        assertNull(decoded.getFireAtPosition());
    }

    @Test
    void playSoundSurvivesTheWire() {
        final WireSession session = new WireSession(944, 1001);
        final PlaySoundPacket packet = new PlaySoundPacket();
        packet.setSound("minecraft:random.levelup");
        packet.setPosition(Vector3f.from(1.5f, 2.5f, 3.5f));
        packet.setVolume(0.5f);
        packet.setPitch(0.7f);
        packet.setServerSoundHandle(42L);

        final PlaySoundPacket decoded = clientboundWire1001(session, packet);
        assertEquals("minecraft:random.levelup", decoded.getSound());
        assertEquals(0.5f, decoded.getVolume());
        assertEquals(0.7f, decoded.getPitch());
        assertNull(decoded.getServerSoundHandle());
    }

    @Test
    void graphicsParameterOverrideSurvivesTheWire() {
        final WireSession session = new WireSession(944, 1001);
        final GraphicsParameterOverridePacket packet = new GraphicsParameterOverridePacket();
        packet.setValues(new java.util.LinkedHashMap<>());
        packet.setFloatValue(0.25f);
        packet.setVec3Value(Vector3f.ONE);
        packet.setBiomeIdentifier("minecraft:plains");
        packet.setPlayerIdentifier("player1");
        packet.setParameterType(GraphicsOverrideParameterType.SKY_ZENITH_COLOR);
        packet.setReset(false);

        final GraphicsParameterOverridePacket decoded = clientboundWire1001(session, packet);
        assertEquals(0.25f, decoded.getFloatValue());
        assertEquals(Vector3f.ONE, decoded.getVec3Value());
        assertEquals("minecraft:plains", decoded.getBiomeIdentifier());
        assertEquals(GraphicsOverrideParameterType.SKY_ZENITH_COLOR, decoded.getParameterType());
        assertFalse(decoded.isReset());
        assertNull(decoded.getPlayerIdentifier());
    }

    @Test
    void partyChangedStripsPartyLeaderOnTheWire() {
        final WireSession session = new WireSession(944, 1001);
        final PartyChangedPacket packet = new PartyChangedPacket();
        packet.setParty(new PartyChangedPacket.PartyInfo("party1", true));

        final PartyChangedPacket decoded = clientboundWire1001(session, packet);
        assertEquals("party1", decoded.getParty().getPartyId());
        assertFalse(decoded.getParty().isPartyLeader());
    }

    @Test
    void dimensionDataStripsDimensionTypeOnTheWire() {
        final WireSession session = new WireSession(944, 1001);
        final DimensionDataPacket packet = new DimensionDataPacket();
        packet.getDefinitions().add(new DimensionDefinition("minecraft:overworld", 384, -64, 1, 7, null, null));

        final DimensionDataPacket decoded = clientboundWire1001(session, packet);
        assertEquals(1, decoded.getDefinitions().size());
        final DimensionDefinition definition = decoded.getDefinitions().getFirst();
        assertEquals("minecraft:overworld", definition.getId());
        assertEquals(384, definition.getMaximumHeight());
        assertEquals(-64, definition.getMinimumHeight());
        assertEquals(1, definition.getGeneratorType());
        assertEquals(0, definition.getDimensionType());
    }

    @Test
    void attributeLayerSyncSurvivesTheWire() {
        final WireSession session = new WireSession(944, 1001);
        final ClientboundAttributeLayerSyncPacket packet = new ClientboundAttributeLayerSyncPacket();
        packet.setData(new UpdateAttributeLayerSettingsData("layer1", 2,
                new AttributeLayerSettings(3, new AttributeLayerSettings.FloatWeight(0.5f), true, false)));

        final ClientboundAttributeLayerSyncPacket decoded = clientboundWire1001(session, packet);
        assertTrue(decoded.getData() instanceof UpdateAttributeLayerSettingsData);
        final UpdateAttributeLayerSettingsData data = (UpdateAttributeLayerSettingsData) decoded.getData();
        assertEquals("layer1", data.getLayerName());
        assertEquals(2, data.getDimension());
        assertEquals(3, data.getSettings().getPriority());
        assertEquals(0.5f, ((AttributeLayerSettings.FloatWeight) data.getSettings().getWeight()).getValue());
        assertTrue(data.getSettings().isEnabled());
        assertFalse(data.getSettings().isTransitionsPaused());
    }

    @Test
    void subChunkRequestSurvivesTheWire() {
        final WireSession session = new WireSession(944, 1001);
        final SubChunkRequestPacket packet = new SubChunkRequestPacket();
        packet.setDimension(0);
        packet.setSubChunkPosition(Vector3i.from(2, 3, 4));
        packet.getPositionOffsets().add(Vector3i.from(1, 0, -1));
        packet.getPositionOffsets().add(Vector3i.from(0, 1, 1));

        final SubChunkRequestPacket decoded = serverboundWire944(session, packet);
        assertEquals(0, decoded.getDimension());
        assertEquals(Vector3i.from(2, 3, 4), decoded.getSubChunkPosition());
        assertEquals(2, decoded.getPositionOffsets().size());
        assertEquals(Vector3i.from(1, 0, -1), decoded.getPositionOffsets().getFirst());
        assertEquals(Vector3i.from(0, 1, 1), decoded.getPositionOffsets().get(1));
    }

    @Test
    void serverboundDiagnosticsSurvivesTheWire() {
        final WireSession session = new WireSession(944, 1001);
        final ServerboundDiagnosticsPacket packet = new ServerboundDiagnosticsPacket();
        packet.setAvgFps(60f);
        packet.setAvgServerSimTickTimeMS(5f);
        packet.setAvgClientSimTickTimeMS(6f);
        packet.setAvgBeginFrameTimeMS(7f);
        packet.setAvgInputTimeMS(8f);
        packet.setAvgRenderTimeMS(9f);
        packet.setAvgEndFrameTimeMS(10f);
        packet.setAvgRemainderTimePercent(11f);
        packet.setAvgUnaccountedTimePercent(12f);

        final ServerboundDiagnosticsPacket decoded = serverboundWire944(session, packet);
        assertEquals(60f, decoded.getAvgFps());
        assertEquals(5f, decoded.getAvgServerSimTickTimeMS());
        assertEquals(6f, decoded.getAvgClientSimTickTimeMS());
        assertEquals(7f, decoded.getAvgBeginFrameTimeMS());
        assertEquals(8f, decoded.getAvgInputTimeMS());
        assertEquals(9f, decoded.getAvgRenderTimeMS());
        assertEquals(10f, decoded.getAvgEndFrameTimeMS());
        assertEquals(11f, decoded.getAvgRemainderTimePercent());
        assertEquals(12f, decoded.getAvgUnaccountedTimePercent());
    }

    @Test
    void updateClientOptionsSurvivesTheWire() {
        final WireSession session = new WireSession(944, 1001);
        final UpdateClientOptionsPacket packet = new UpdateClientOptionsPacket();
        packet.setGraphicsMode(GraphicsMode.FANCY);

        final UpdateClientOptionsPacket decoded = serverboundWire944(session, packet);
        assertEquals(GraphicsMode.FANCY, decoded.getGraphicsMode());
        assertNull(decoded.getFilterProfanityChange());
    }

    @Test
    void moveEntityAbsoluteKeepsForceCompletionClearedOnTheWire() {
        final WireSession session = new WireSession(944, 1001);
        final MoveEntityAbsolutePacket packet = new MoveEntityAbsolutePacket();
        packet.setRuntimeEntityId(7L);
        packet.setOnGround(true);
        packet.setTeleported(true);
        packet.setForceMove(false);
        packet.setPosition(Vector3f.from(1.5f, 64f, -2.5f));
        packet.setRotation(Vector3f.ZERO);

        final MoveEntityAbsolutePacket decoded = serverboundWire944(session, packet);
        assertTrue(decoded.isOnGround());
        assertTrue(decoded.isTeleported());
        assertFalse(decoded.isForceMove());
        assertFalse(decoded.isForceCompletion());
        assertEquals(Vector3f.from(1.5f, 64f, -2.5f), decoded.getPosition());
    }

    @Test
    void playerEnchantOptionsSurvivesTheWire() {
        final WireSession session = new WireSession(944, 1001);
        final PlayerEnchantOptionsPacket packet = new PlayerEnchantOptionsPacket();
        packet.getOptions().add(new EnchantOptionData(3, 1,
                List.of(new EnchantData(2, 1)), List.of(), List.of(), "ench", 7));

        final PlayerEnchantOptionsPacket decoded = clientboundWire1001(session, packet);
        assertEquals(1, decoded.getOptions().size());
        assertEquals(3, decoded.getOptions().getFirst().getCost());
        assertEquals(1, decoded.getOptions().getFirst().getPrimarySlot());
        assertEquals(2, decoded.getOptions().getFirst().getEnchants0().getFirst().getType());
        assertEquals(1, decoded.getOptions().getFirst().getEnchants0().getFirst().getLevel());
        assertEquals("ench", decoded.getOptions().getFirst().getEnchantName());
        assertEquals(7, decoded.getOptions().getFirst().getEnchantNetId());
    }

    @Test
    void playerEnchantOptionsWithUnencodableTypeIsCancelled() {
        final PlayerEnchantOptionsPacket packet = new PlayerEnchantOptionsPacket();
        packet.getOptions().add(new EnchantOptionData(3, 1,
                List.of(new EnchantData(300, 1)), List.of(), List.of(), "ench", 7));

        final WrappedBedrockPacket wrapped = new WrappedBedrockPacket(SESSION_944, 1001, 944, packet, false);
        newV1001to944().passthroughClientbound(wrapped);

        assertTrue(wrapped.isCancelled());
    }

    @Test
    void inventorySlotClientboundItemsAreDowngradedOnTheWire() {
        final WireSession session = new WireSession(944, 1001);
        initItemDefinitions(session);
        final InventorySlotPacket packet = new InventorySlotPacket();
        packet.setContainerId(0);
        packet.setSlot(3);
        packet.setItem(item1001("minecraft:stone", 2));

        final InventorySlotPacket decoded = clientboundWire1001(session, packet);
        assertEquals("minecraft:stone", decoded.getItem().getDefinition().getIdentifier());
        assertEquals(2, decoded.getItem().getCount());
    }

    @Test
    void inventoryContentClientboundItemsAreDowngradedOnTheWire() {
        final WireSession session = new WireSession(944, 1001);
        initItemDefinitions(session);
        final InventoryContentPacket packet = new InventoryContentPacket();
        packet.setContainerId(0);
        packet.setContainerNameData(new FullContainerName(ContainerSlotType.LEVEL_ENTITY, 0));
        packet.setStorageItem(ItemData.AIR);
        packet.getContents().add(item1001("minecraft:stone", 2));

        final InventoryContentPacket decoded = clientboundWire1001(session, packet);
        assertEquals(1, decoded.getContents().size());
        assertEquals("minecraft:stone", decoded.getContents().getFirst().getDefinition().getIdentifier());
        assertEquals(2, decoded.getContents().getFirst().getCount());
        assertTrue(decoded.getStorageItem().isNull());
    }

    @Test
    void v975ClientboundAdditionsAreDroppedOnTheWire() {
        final WireSession session = new WireSession(944, 1001);
        final SendPartyDestinationCookiePacket cookie = new SendPartyDestinationCookiePacket();
        cookie.setCookie("cookie");
        cookie.setIntent(SendPartyDestinationCookiePacket.Intent.NOTIFY);
        cookie.setDestinationName("destination");
        final ClientboundUpdateSoundDataPacket soundData = new ClientboundUpdateSoundDataPacket();
        soundData.setType("minecraft:sound");
        final PartyDestinationCookieResponsePacket cookieResponse = new PartyDestinationCookieResponsePacket();
        cookieResponse.setCookie("cookie");
        for (BedrockPacket packet : List.of(
                new ServerStoreInfoPacket(),
                new ServerPresenceInfoPacket(),
                soundData,
                cookie,
                cookieResponse)) {
            assertNull(session.getClientCodec().getPacketDefinition(packet.getClass()),
                    packet.getClass().getSimpleName() + " must be unknown to the client codec");
            final ByteBuf input = Unpooled.buffer();
            final ByteBuf output = Unpooled.buffer();
            try {
                session.encodeServer(packet, input);
                final int id = session.getServerCodec().getPacketDefinition(packet.getClass()).getId();
                assertNull(session.translateClientbound(input, output, id), packet.getClass().getSimpleName());
                assertEquals(0, output.readableBytes());
            } finally {
                input.release();
                output.release();
            }
        }
    }

    @Test
    void v1001TranslatorIsRegisteredForThe944Downgrade() {
        assertEquals(List.of(Protocol1001to944.class), classes(ProtocolInfo.getTranslators(1001, 944)));
        final List<ProtocolToProtocol> chain = ProtocolInfo.getTranslators(1001, 332);
        assertEquals(Protocol1001to944.class, chain.getFirst().getClass());
        assertEquals(Protocol340to332.class, chain.getLast().getClass());
        assertTrue(chain.stream().anyMatch(translator -> translator instanceof Protocol354to340));
        assertTrue(chain.stream().anyMatch(translator -> translator instanceof Protocol361to354));
    }
}
