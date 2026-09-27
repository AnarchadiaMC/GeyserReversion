package oxy.geyser.reversion;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.data.BlockPropertyData;
import org.cloudburstmc.protocol.bedrock.data.ChatRestrictionLevel;
import org.cloudburstmc.protocol.bedrock.data.EduSharedUriResource;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;
import org.cloudburstmc.protocol.bedrock.data.GamePublishSetting;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.NetworkPermissions;
import org.cloudburstmc.protocol.bedrock.data.PlayerPermission;
import org.cloudburstmc.protocol.bedrock.data.SpawnBiomeType;
import org.cloudburstmc.protocol.bedrock.data.WorldType;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemVersion;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.CraftingDataType;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapedRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptorWithCount;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import oxy.geyser.reversion.ouranos.session.SpecialOuranosSession;
import oxy.geyser.reversion.session.GeyserTranslatedUser;
import oxy.geyser.reversion.util.BridgeCodecSelector;
import oxy.geyser.reversion.util.CodecUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Boundary wire tests for the Cloudburst codecs and helpers exactly as
 * {@link GeyserTranslatedUser} and {@link SpecialOuranosSession} construct them.
 *
 * <p>Direction audit of the runtime classes:
 * <ul>
 *   <li>{@code GeyserTranslatedUser.cloudburstClientCodecHelper} =
 *       {@link CodecUtil#applyClientboundLimits} (CLIENT settings) and is used by
 *       {@code decodeClient} for clientbound packets produced by the trusted local Geyser.</li>
 *   <li>{@code GeyserTranslatedUser.cloudburstServerCodecHelper} =
 *       {@link CodecUtil#applyServerboundLimits} (SERVER settings) and is used by
 *       {@code decodeServer} for serverbound packets from the untrusted client.</li>
 *   <li>{@code SpecialOuranosSession} names the same roles by packet target:
 *       {@code serverCodecHelper} (CLIENT settings) decodes clientbound packets and
 *       {@code clientCodecHelper} (SERVER settings) decodes serverbound packets.</li>
 * </ul>
 */
@Tag("hermetic")
class CodecWireCompatibilityTest {
    static final int BRIDGE_PROTOCOL = 944;
    static final UUID RECIPE_UUID = new UUID(0, 42);
    static final List<String> ITEM_IDENTIFIERS = List.of(
            "minecraft:stone", "minecraft:crafting_table", "minecraft:chest");

    static Stream<BedrockCodec> mappedCodecs() {
        return DuplicatedProtocolInfo.getPacketCodecs().stream()
                .filter(codec -> BridgeCodecSelector.hasMappingData(codec.getProtocolVersion()))
                .sorted(Comparator.comparingInt(BedrockCodec::getProtocolVersion));
    }

    static BedrockCodec bridgeCodec() {
        return Objects.requireNonNull(DuplicatedProtocolInfo.getPacketCodec(BRIDGE_PROTOCOL),
                "missing bridge codec " + BRIDGE_PROTOCOL);
    }

    static BedrockCodecHelper clientboundHelper(BedrockCodec codec) {
        return CodecUtil.applyClientboundLimits(codec.createHelper());
    }

    static BedrockCodecHelper serverboundHelper(BedrockCodec codec) {
        return CodecUtil.applyServerboundLimits(codec.createHelper());
    }

    static void installBlockDefinitions(BedrockCodecHelper helper) {
        // Same registry shape as GeyserTranslatedUser: every runtime id is accepted.
        helper.setBlockDefinitions(new DefinitionRegistry<>() {
            @Override
            public BlockDefinition getDefinition(int runtimeId) {
                return () -> runtimeId;
            }

            @Override
            public boolean isRegistered(BlockDefinition definition) {
                return true;
            }
        });
    }

    static void installItemDefinitions(BedrockCodecHelper helper) {
        SimpleDefinitionRegistry.Builder<ItemDefinition> builder = SimpleDefinitionRegistry.<ItemDefinition>builder()
                .add(itemDefinition(0))
                .add(itemDefinition(1))
                .add(itemDefinition(2));
        helper.setItemDefinitions(builder.build());
    }

    static ItemDefinition itemDefinition(int index) {
        return new SimpleItemDefinition(ITEM_IDENTIFIERS.get(index), index + 1, ItemVersion.NONE, false, NbtMap.EMPTY);
    }

    static ItemDefinition itemDefinition(String identifier, int runtimeId) {
        return new SimpleItemDefinition(identifier, runtimeId, ItemVersion.NONE, false, NbtMap.EMPTY);
    }

    static NbtList<NbtMap> blockPalette() {
        return new NbtList<>(NbtType.COMPOUND,
                NbtMap.builder().putString("name", "minecraft:chest").putCompound("states", NbtMap.EMPTY).build(),
                NbtMap.builder().putString("name", "minecraft:stone").putCompound("states", NbtMap.EMPTY).build());
    }

    static StartGamePacket startGamePacket() {
        StartGamePacket packet = new StartGamePacket();
        packet.setUniqueEntityId(1);
        packet.setRuntimeEntityId(2);
        packet.setPlayerGameType(GameType.CREATIVE);
        packet.setPlayerPosition(Vector3f.from(1.5f, 65f, -2.5f));
        packet.setRotation(Vector2f.ZERO);
        packet.setSeed(42L);
        packet.setSpawnBiomeType(SpawnBiomeType.DEFAULT);
        packet.setCustomBiomeName("");
        packet.setDimensionId(0);
        packet.setGeneratorId(1);
        packet.setLevelGameType(GameType.CREATIVE);
        packet.setDifficulty(1);
        packet.setDefaultSpawn(Vector3i.ZERO);
        packet.setAchievementsDisabled(true);
        packet.setDayCycleStopTime(0);
        packet.setEduEditionOffers(0);
        packet.setEduFeaturesEnabled(false);
        packet.setEducationProductionId("");
        packet.setRainLevel(0f);
        packet.setLightningLevel(0f);
        packet.setPlatformLockedContentConfirmed(false);
        packet.setMultiplayerGame(true);
        packet.setBroadcastingToLan(true);
        packet.setXblBroadcastMode(GamePublishSetting.PUBLIC);
        packet.setPlatformBroadcastMode(GamePublishSetting.PUBLIC);
        packet.setCommandsEnabled(true);
        packet.setTexturePacksRequired(false);
        packet.setExperimentsPreviouslyToggled(false);
        packet.setBonusChestEnabled(false);
        packet.setStartingWithMap(false);
        packet.setTrustingPlayers(false);
        packet.setDefaultPlayerPermission(PlayerPermission.MEMBER);
        packet.setServerChunkTickRange(4);
        packet.setBehaviorPackLocked(false);
        packet.setResourcePackLocked(false);
        packet.setFromLockedWorldTemplate(false);
        packet.setUsingMsaGamertagsOnly(false);
        packet.setFromWorldTemplate(false);
        packet.setWorldTemplateOptionLocked(false);
        packet.setOnlySpawningV1Villagers(false);
        packet.setVanillaVersion("1.21.0");
        packet.setLimitedWorldWidth(0);
        packet.setLimitedWorldHeight(0);
        packet.setNetherType(false);
        packet.setEduSharedUriResource(EduSharedUriResource.EMPTY);
        packet.setForceExperimentalGameplay(OptionalBoolean.empty());
        packet.setChatRestrictionLevel(ChatRestrictionLevel.NONE);
        packet.setLevelId("world");
        packet.setLevelName("world");
        packet.setPremiumWorldTemplateId("");
        packet.setTrial(false);
        packet.setAuthoritativeMovementMode(AuthoritativeMovementMode.SERVER);
        packet.setRewindHistorySize(0);
        packet.setServerAuthoritativeBlockBreaking(true);
        packet.setCurrentTick(1L);
        packet.setEnchantmentSeed(0);
        packet.setBlockPalette(blockPalette());
        packet.getBlockProperties().add(new BlockPropertyData("test:alpha", NbtMap.EMPTY));
        packet.getBlockProperties().add(new BlockPropertyData("test:beta", NbtMap.EMPTY));
        packet.setItemDefinitions(new ArrayList<>(List.of(itemDefinition("minecraft:stone", 1))));
        packet.setMultiplayerCorrelationId("");
        packet.setInventoriesServerAuthoritative(true);
        packet.setServerEngine("");
        packet.setPlayerPropertyData(NbtMap.EMPTY);
        packet.setBlockRegistryChecksum(0L);
        packet.setWorldTemplateId(new UUID(0, 0));
        packet.setEditorWorldType(WorldType.NON_EDITOR);
        packet.setClientSideGenerationEnabled(false);
        packet.setEmoteChatMuted(false);
        packet.setBlockNetworkIdsHashed(false);
        packet.setCreatedInEditor(false);
        packet.setExportedFromEditor(false);
        packet.setNetworkPermissions(NetworkPermissions.DEFAULT);
        packet.setHardcore(false);
        packet.setServerId("");
        packet.setWorldId("");
        packet.setScenarioId("");
        packet.setOwnerId("");
        packet.setTickDeathSystemsEnabled(false);
        packet.setLoggingChat(false);
        return packet;
    }

    static ItemComponentPacket itemComponentPacket() {
        ItemComponentPacket packet = new ItemComponentPacket();
        for (int i = 0; i < ITEM_IDENTIFIERS.size(); i++) {
            packet.getItems().add(itemDefinition(i));
        }
        return packet;
    }

    static CraftingDataPacket craftingDataPacket() {
        CraftingDataPacket packet = new CraftingDataPacket();
        packet.setCleanRecipes(true);
        var ingredients = List.of(ItemDescriptorWithCount.fromItem(ItemData.builder()
                .definition(itemDefinition("minecraft:stone", 1)).count(1).build()));
        var results = List.of(ItemData.builder()
                .definition(itemDefinition("minecraft:crafting_table", 2)).count(1).build());
        packet.getCraftingData().add(ShapedRecipeData.of(CraftingDataType.SHAPED, "test:shaped", 1, 1,
                ingredients, results, RECIPE_UUID, "crafting_table", 0, 123));
        return packet;
    }

    static LevelChunkPacket levelChunkPacket() {
        LevelChunkPacket packet = new LevelChunkPacket();
        packet.setChunkX(3);
        packet.setChunkZ(-7);
        packet.setSubChunksLength(0);
        packet.setCachingEnabled(false);
        packet.setRequestSubChunks(false);
        packet.setData(Unpooled.wrappedBuffer(new byte[]{1, 2, 3, 4}));
        return packet;
    }

    static BedrockPacket roundTrip(BedrockCodec codec, BedrockCodecHelper helper, BedrockPacket packet) {
        var definition = codec.getPacketDefinition(packet.getClass());
        assertNotNull(definition, "codec " + codec.getProtocolVersion() + " cannot express " + packet.getClass().getSimpleName());
        ByteBuf buffer = Unpooled.buffer();
        try {
            codec.tryEncode(helper, buffer, packet);
            BedrockPacket decoded = codec.tryDecode(helper, buffer, definition.getId());
            assertEquals(0, buffer.readableBytes(),
                    "protocol " + codec.getProtocolVersion() + " decoder left bytes for " + packet.getClass().getSimpleName());
            return decoded;
        } finally {
            buffer.release();
        }
    }

    static ByteBuf encodeWithUnlimited(BedrockCodec codec, BedrockPacket packet) {
        BedrockCodecHelper helper = codec.createHelper();
        helper.setEncodingSettings(EncodingSettings.UNLIMITED);
        installBlockDefinitions(helper);
        installItemDefinitions(helper);
        ByteBuf buffer = Unpooled.buffer();
        try {
            codec.tryEncode(helper, buffer, packet);
            return buffer;
        } catch (RuntimeException exception) {
            buffer.release();
            throw exception;
        }
    }

    /**
     * Asserts the boundary codec either round-trips the packet or documents it as a drop. The runtime
     * translation path ({@code SpecialOuranosSession.translateClientbound}) drops any packet whose class
     * has no definition in the boundary codec.
     */
    static void assertBoundaryPacket(int protocol, BedrockCodec codec, BedrockCodecHelper helper, BedrockPacket packet) {
        if (codec.getPacketDefinition(packet.getClass()) == null) {
            // Explicit drop assertion: the codec must genuinely not know the packet class.
            assertNull(codec.getPacketDefinition(packet.getClass()),
                    "protocol " + protocol + " must document " + packet.getClass().getSimpleName() + " as dropped");
            if (packet instanceof ItemComponentPacket) {
                assertTrue(protocol < 419, "ItemComponentPacket may only be droppable before protocol 419");
            } else {
                fail(packet.getClass().getSimpleName() + " is unexpectedly droppable at protocol " + protocol);
            }
            return;
        }
        BedrockPacket decoded;
        try {
            decoded = roundTrip(codec, helper, packet);
        } finally {
            if (packet instanceof LevelChunkPacket chunk) {
                chunk.release();
            }
        }
        assertPreservedFields(protocol, decoded);
    }

    static void assertPreservedFields(int protocol, BedrockPacket decoded) {
        if (decoded instanceof StartGamePacket startGame) {
            assertStartGame(protocol, startGame);
        } else if (decoded instanceof ItemComponentPacket components) {
            assertItemComponents(protocol, components);
        } else if (decoded instanceof CraftingDataPacket crafting) {
            assertCraftingData(protocol, crafting);
        } else if (decoded instanceof LevelChunkPacket chunk) {
            try {
                assertLevelChunk(chunk);
            } finally {
                chunk.release();
            }
        } else {
            fail("unexpected decoded packet " + decoded.getClass().getName());
        }
    }

    static void assertStartGame(int protocol, StartGamePacket decoded) {
        assertEquals(1L, decoded.getUniqueEntityId());
        assertEquals(2L, decoded.getRuntimeEntityId());
        assertEquals(Vector3f.from(1.5f, 65f, -2.5f), decoded.getPlayerPosition());
        assertEquals("world", decoded.getLevelId());
        assertFalse(decoded.isBlockNetworkIdsHashed());
        if (protocol >= 419) {
            assertEquals(List.of("test:alpha", "test:beta"),
                    decoded.getBlockProperties().stream().map(BlockPropertyData::getName).toList(),
                    "block property data must survive the boundary codec");
        } else {
            assertNotNull(decoded.getBlockPalette(), "legacy StartGame carries a block palette");
            assertEquals(2, decoded.getBlockPalette().size());
            assertTrue(decoded.getBlockProperties().isEmpty(),
                    "protocols before 419 do not carry BlockPropertyData");
        }
    }

    static void assertItemComponents(int protocol, ItemComponentPacket decoded) {
        assertEquals(ITEM_IDENTIFIERS.size(), decoded.getItems().size());
        assertEquals(ITEM_IDENTIFIERS,
                decoded.getItems().stream().map(ItemDefinition::getIdentifier).toList());
        if (protocol >= 859) {
            assertEquals(List.of(1, 2, 3),
                    decoded.getItems().stream().map(ItemDefinition::getRuntimeId).toList(),
                    "component item runtime ids survive from protocol 859");
        }
    }

    static void assertCraftingData(int protocol, CraftingDataPacket decoded) {
        assertEquals(1, decoded.getCraftingData().size());
        assertInstanceOf(ShapedRecipeData.class, decoded.getCraftingData().getFirst());
        ShapedRecipeData recipe = (ShapedRecipeData) decoded.getCraftingData().getFirst();
        assertEquals(protocol >= 361 ? "test:shaped" : "", recipe.getId(),
                "the CraftingData recipe string id only exists on the wire from protocol 361");
        assertEquals(RECIPE_UUID, recipe.getUuid());
        assertEquals(1, recipe.getWidth());
        assertEquals(1, recipe.getHeight());
        assertEquals(1, recipe.getIngredients().size());
        assertEquals(1, recipe.getResults().size());
        ItemData result = recipe.getResults().getFirst();
        assertEquals("minecraft:crafting_table", result.getDefinition().getIdentifier());
        assertEquals(2, result.getDefinition().getRuntimeId());
        assertEquals(1, result.getCount());
        if (protocol >= 407) {
            assertEquals(123, recipe.getNetId(), "network recipe id must survive from protocol 407");
        }
    }

    static void assertLevelChunk(LevelChunkPacket decoded) {
        assertEquals(3, decoded.getChunkX());
        assertEquals(-7, decoded.getChunkZ());
        assertEquals(0, decoded.getSubChunksLength());
        assertNotNull(decoded.getData());
        byte[] payload = new byte[decoded.getData().readableBytes()];
        decoded.getData().readBytes(payload);
        assertArrayEquals(new byte[]{1, 2, 3, 4}, payload, "chunk payload must survive the boundary codec");
    }

    @TestFactory
    Stream<DynamicTest> clientboundPacketsRoundTripAtEveryMappedProtocolBoundary() {
        return mappedCodecs().map(codec -> DynamicTest.dynamicTest(
                "clientbound boundary protocol " + codec.getProtocolVersion(), () -> {
                    int protocol = codec.getProtocolVersion();
                    BedrockCodec client = Objects.requireNonNull(DuplicatedProtocolInfo.getPacketCodec(protocol));
                    BedrockCodec bridge = bridgeCodec();
                    BedrockCodecHelper clientHelper = clientboundHelper(client);
                    BedrockCodecHelper bridgeHelper = serverboundHelper(bridge);
                    installBlockDefinitions(clientHelper);
                    installBlockDefinitions(bridgeHelper);
                    installItemDefinitions(clientHelper);
                    installItemDefinitions(bridgeHelper);
                    assertSame(EncodingSettings.SERVER, bridgeHelper.getEncodingSettings(),
                            "bridge helper must mirror the runtime serverbound helper");

                    assertBoundaryPacket(protocol, client, clientHelper, startGamePacket());
                    assertBoundaryPacket(protocol, client, clientHelper, itemComponentPacket());
                    assertBoundaryPacket(protocol, client, clientHelper, craftingDataPacket());
                    assertBoundaryPacket(protocol, client, clientHelper, levelChunkPacket());
                }));
    }

    @Test
    void serverboundBridgePacketsRoundTripUnderServerLimits() {
        BedrockCodec bridge = bridgeCodec();
        BedrockCodecHelper helper = serverboundHelper(bridge);
        installBlockDefinitions(helper);
        installItemDefinitions(helper);

        assertBoundaryPacket(BRIDGE_PROTOCOL, bridge, helper, startGamePacket());
        assertBoundaryPacket(BRIDGE_PROTOCOL, bridge, helper, itemComponentPacket());
        assertBoundaryPacket(BRIDGE_PROTOCOL, bridge, helper, craftingDataPacket());
        assertBoundaryPacket(BRIDGE_PROTOCOL, bridge, helper, levelChunkPacket());
    }

    @Test
    void clientboundProfileAcceptsWhatServerboundProfileRejects() {
        BedrockCodec bridge = bridgeCodec();
        int startGameId = bridge.getPacketDefinition(StartGamePacket.class).getId();

        StartGamePacket manyBlocks = startGamePacket();
        for (int i = 0; i < 1_100; i++) {
            manyBlocks.getBlockProperties().add(new BlockPropertyData("test:block_" + i, NbtMap.EMPTY));
        }
        assertAcceptedByClientboundRejectedByServerbound(bridge, startGameId, manyBlocks, 1_102);

        StartGamePacket bigNbt = startGamePacket();
        bigNbt.getBlockProperties().add(new BlockPropertyData("test:big",
                NbtMap.builder().putByteArray("payload", new byte[600_000]).build()));
        assertAcceptedByClientboundRejectedByServerbound(bridge, startGameId, bigNbt, 3);
    }

    static void assertAcceptedByClientboundRejectedByServerbound(BedrockCodec codec, int packetId,
                                                                 StartGamePacket packet, int expectedBlockProperties) {
        ByteBuf encoded = encodeWithUnlimited(codec, packet);
        try {
            BedrockCodecHelper clientHelper = clientboundHelper(codec);
            installBlockDefinitions(clientHelper);
            ByteBuf clientInput = encoded.duplicate();
            StartGamePacket accepted = (StartGamePacket) codec.tryDecode(clientHelper, clientInput, packetId);
            assertEquals(expectedBlockProperties, accepted.getBlockProperties().size());
            assertEquals(0, clientInput.readableBytes(),
                    "CLIENT-profile helper must consume the oversized clientbound payload");

            BedrockCodecHelper serverHelper = serverboundHelper(codec);
            installBlockDefinitions(serverHelper);
            ByteBuf serverInput = encoded.duplicate();
            assertThrows(RuntimeException.class, () -> codec.tryDecode(serverHelper, serverInput, packetId),
                    "SERVER-profile helper must reject a payload that exceeds the serverbound limits");
        } finally {
            encoded.release();
        }
    }

    @Test
    void helpersUseRoleSpecificLimits() {
        for (BedrockCodec codec : mappedCodecs().toList()) {
            int protocol = codec.getProtocolVersion();
            BedrockCodec duplicated = DuplicatedProtocolInfo.getPacketCodec(protocol);
            assertNotNull(duplicated);
            // DuplicatedProtocolInfo rebuilds every codec so its default helper carries SERVER limits.
            assertSame(EncodingSettings.SERVER, duplicated.createHelper().getEncodingSettings(),
                    "default helper for protocol " + protocol);
            assertSame(EncodingSettings.CLIENT, CodecUtil.applyClientboundLimits(duplicated.createHelper()).getEncodingSettings(),
                    "clientbound helper for protocol " + protocol);
            assertSame(EncodingSettings.SERVER, CodecUtil.applyServerboundLimits(duplicated.createHelper()).getEncodingSettings(),
                    "serverbound helper for protocol " + protocol);
        }

        for (int protocol : List.of(361, 419, 575, 844, 944)) {
            GeyserTranslatedUser user = new GeyserTranslatedUser(protocol, BRIDGE_PROTOCOL, null);
            assertSame(EncodingSettings.CLIENT, user.getCloudburstClientCodecHelper().getEncodingSettings(),
                    "GeyserTranslatedUser.decodeClient helper must be clientbound (CLIENT)");
            assertSame(EncodingSettings.SERVER, user.getCloudburstServerCodecHelper().getEncodingSettings(),
                    "GeyserTranslatedUser.decodeServer helper must be serverbound (SERVER)");

            Harness harness = new Harness(protocol);
            assertSame(EncodingSettings.CLIENT, harness.getServerCodecHelper().getEncodingSettings(),
                    "SpecialOuranosSession serverCodecHelper decodes clientbound packets");
            assertSame(EncodingSettings.SERVER, harness.getClientCodecHelper().getEncodingSettings(),
                    "SpecialOuranosSession clientCodecHelper decodes serverbound packets");
        }

        // End-to-end direction proof through the real runtime entry points.
        GeyserTranslatedUser bridgeUser = new GeyserTranslatedUser(BRIDGE_PROTOCOL, BRIDGE_PROTOCOL, null);
        StartGamePacket manyBlocks = startGamePacket();
        for (int i = 0; i < 1_100; i++) {
            manyBlocks.getBlockProperties().add(new BlockPropertyData("test:block_" + i, NbtMap.EMPTY));
        }
        ByteBuf encoded = encodeWithUnlimited(bridgeCodec(), manyBlocks);
        try {
            int packetId = bridgeCodec().getPacketDefinition(StartGamePacket.class).getId();
            assertNotNull(bridgeUser.decodeClient(encoded.duplicate(), packetId),
                    "clientbound decode must accept a StartGame sized payload");
            assertThrows(RuntimeException.class, () -> bridgeUser.decodeServer(encoded.duplicate(), packetId),
                    "serverbound decode must reject the same payload");
        } finally {
            encoded.release();
        }
    }

    @Test
    void encodingSettingsOrdering() {
        assertTrue(EncodingSettings.CLIENT.maxListSize() >= EncodingSettings.SERVER.maxListSize());
        assertTrue(EncodingSettings.CLIENT.maxByteArraySize() >= EncodingSettings.SERVER.maxByteArraySize());
        assertTrue(EncodingSettings.CLIENT.maxNetworkNBTSize() >= EncodingSettings.SERVER.maxNetworkNBTSize());
        assertTrue(EncodingSettings.CLIENT.maxStringLength() >= EncodingSettings.SERVER.maxStringLength());
        assertTrue(EncodingSettings.SERVER.maxListSize() > 0);
        assertTrue(EncodingSettings.SERVER.maxByteArraySize() > 0);
        assertTrue(EncodingSettings.SERVER.maxNetworkNBTSize() > 0);
        assertTrue(EncodingSettings.SERVER.maxStringLength() > 0);
    }

    static final class Harness extends SpecialOuranosSession {
        Harness(int protocol) {
            super(protocol, BRIDGE_PROTOCOL);
        }

        @Override
        public void sendUpstreamPacket(BedrockPacket packet) {
        }

        @Override
        public void sendDownstreamPacket(BedrockPacket packet) {
        }
    }
}
