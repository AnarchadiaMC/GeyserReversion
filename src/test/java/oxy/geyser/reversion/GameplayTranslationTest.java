package oxy.geyser.reversion;

import oxy.geyser.reversion.ouranos.ProtocolInfo;
import oxy.geyser.reversion.ouranos.converter.ItemTypeDictionary;
import oxy.geyser.reversion.ouranos.session.SpecialOuranosSession;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.bedrock.data.*;
import org.cloudburstmc.protocol.bedrock.data.inventory.*;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.*;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.*;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.*;
import org.cloudburstmc.math.vector.*;
import org.junit.jupiter.api.*;
import io.netty.buffer.*;
import oxy.geyser.reversion.util.BridgeCodecSelector;
import oxy.geyser.reversion.util.GeyserApiCompat;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** In-memory wire/semantic tests, NOT real-client gameplay certification. */
class GameplayTranslationTest {
    /**
     * The bridge the runtime actually selects: the newest Bedrock protocol the pinned Geyser and
     * the checked-in Ouranos mapping data share (1001 on both Geyser 2.11.2 and 2.11.3). Derived
     * from both sides' own data rather than from {@link BridgeCodecSelector#select}. Every
     * gameplay, recipe and inventory case below therefore runs against the production target
     * version instead of a hardcoded protocol.
     */
    static final int BRIDGE_PROTOCOL = derivedBridgeProtocol();

    private static int derivedBridgeProtocol() {
        int[] geyserProtocols = GeyserApiCompat.supportedBedrockProtocols();
        return ProtocolInfo.getPacketCodecs().stream()
                .map(BedrockCodec::getProtocolVersion)
                .filter(protocol -> Arrays.stream(geyserProtocols).anyMatch(supported -> supported == protocol))
                .filter(BridgeCodecSelector::hasMappingData)
                .max(Integer::compare)
                .orElseThrow(() -> new AssertionError("no Bedrock protocol is shared between the pinned Geyser ("
                        + Arrays.toString(geyserProtocols) + ") and the checked-in Ouranos mapping data"));
    }

    static class Harness extends SpecialOuranosSession {
        Harness(int protocol) { super(protocol, BRIDGE_PROTOCOL); }
        @Override public void sendUpstreamPacket(BedrockPacket packet) { }
        @Override public void sendDownstreamPacket(BedrockPacket packet) { }
    }

    static Stream<Integer> protocols() {
        return ProtocolInfo.getPacketCodecs().stream().map(c -> c.getProtocolVersion()).sorted();
    }

    static ItemData item(int protocol, String identifier, int count) {
        var definition = ItemTypeDictionary.getInstance(protocol).getEntries().get(identifier);
        assertNotNull(definition, identifier + " in " + protocol);
        return ItemData.builder().definition(definition.toDefinition(identifier)).count(count).build();
    }

    static BedrockPacket clientbound(Harness session, BedrockPacket packet) {
        var translated = session.translateClientbound(packet);
        assertNotNull(translated);
        var buffer = Unpooled.buffer();
        try {
            session.encodeClient(translated, buffer);
            var decoded = session.getClientCodec().tryDecode(session.getClientCodecHelper(), buffer,
                    session.getClientCodec().getPacketDefinition(translated.getClass()).getId());
            assertEquals(0, buffer.readableBytes());
            return decoded;
        } finally { buffer.release(); }
    }

    static Harness initialized(int protocol) {
        var session = new Harness(protocol);
        var start = new StartGamePacket();
        start.setUniqueEntityId(1); start.setRuntimeEntityId(1);
        start.setAuthoritativeMovementMode(AuthoritativeMovementMode.SERVER);
        start.setInventoriesServerAuthoritative(true);
        start.setBlockNetworkIdsHashed(false);
        session.translateClientbound(start);
        var components = new ItemComponentPacket();
        components.getItems().addAll(ItemTypeDictionary.getInstance(BRIDGE_PROTOCOL).getEntries().entrySet()
                .stream().map(e -> e.getValue().toDefinition(e.getKey())).toList());
        var registry = org.cloudburstmc.protocol.common.SimpleDefinitionRegistry
                .<org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition>builder();
        components.getItems().forEach(registry::add);
        var definitions = registry.build();
        session.getServerCodecHelper().setItemDefinitions(definitions);
        session.getClientCodecHelper().setItemDefinitions(
                new oxy.geyser.reversion.ouranos.utils.ItemTypeDictionaryRegistry(definitions, protocol));
        return session;
    }

    @TestFactory
    Stream<DynamicTest> chestAndInventorySurviveTranslation() {
        return protocols().map(protocol -> DynamicTest.dynamicTest("translated chest/inventory " + protocol, () -> {
            var session = initialized(protocol);
            var open = new ContainerOpenPacket(); open.setId((byte) 7);
            open.setType(ContainerType.CONTAINER); open.setBlockPosition(Vector3i.from(4, 65, -6));
            open.setUniqueEntityId(-1);
            assertEquals(open, clientbound(session, open));
            var content = new InventoryContentPacket(); content.setContainerId(7);
            content.setContainerNameData(new FullContainerName(ContainerSlotType.LEVEL_ENTITY, 0));
            content.setStorageItem(ItemData.AIR);
            content.getContents().add(item(BRIDGE_PROTOCOL, "minecraft:chest", 3));
            var decoded = (InventoryContentPacket) clientbound(session, content);
            assertEquals(7, decoded.getContainerId()); assertEquals(3, decoded.getContents().getFirst().getCount());
            assertEquals(ItemTypeDictionary.getInstance(protocol).fromStringId("minecraft:chest").intValue(),
                    decoded.getContents().getFirst().getDefinition().getRuntimeId());
        }));
    }

    @TestFactory
    Stream<DynamicTest> ordinaryCraftingRecipesRetainIdentityAndUseClientIds() {
        return protocols().map(protocol -> DynamicTest.dynamicTest("translated shaped/shapeless recipes " + protocol, () -> {
            var session = initialized(protocol);
            var packet = new CraftingDataPacket(); packet.setCleanRecipes(true);
            var uuid = new UUID(0, 42);
            var ingredients = List.of(ItemDescriptorWithCount.fromItem(item(BRIDGE_PROTOCOL, "minecraft:chest", 1)));
            var results = List.of(item(BRIDGE_PROTOCOL, "minecraft:crafting_table", 1));
            packet.getCraftingData().add(ShapedRecipeData.of(CraftingDataType.SHAPED, "test:shaped", 1, 1,
                    ingredients, results, uuid, "crafting_table", 0, 123));
            packet.getCraftingData().add(ShapelessRecipeData.of(CraftingDataType.SHAPELESS, "test:shapeless",
                    ingredients, results, uuid, "crafting_table", 0, 124));
            var decoded = (CraftingDataPacket) clientbound(session, packet);
            assertEquals(2, decoded.getCraftingData().size(), "Never clear all ordinary recipes");
            var shaped = (ShapedRecipeData) decoded.getCraftingData().getFirst();
            assertEquals(uuid, shaped.getUuid());
            assertEquals(ItemTypeDictionary.getInstance(protocol).fromStringId("minecraft:crafting_table").intValue(),
                    shaped.getResults().getFirst().getDefinition().getRuntimeId());
            assertEquals(ItemTypeDictionary.getInstance(protocol).fromStringId("minecraft:chest").intValue(),
                    ((DefaultDescriptor) shaped.getIngredients().getFirst().getDescriptor()).getItemId().getRuntimeId());
            if (protocol >= 407) { assertEquals(123, shaped.getNetId()); }
        }));
    }

    @Test
    void unsupportedLegacyRecipeIsOmittedWithoutClearingConcreteRecipes() {
        var session = initialized(419);
        var packet = new CraftingDataPacket(); packet.setCleanRecipes(true);
        var uuid = new UUID(0, 43);
        var result = List.of(item(BRIDGE_PROTOCOL, "minecraft:crafting_table", 1));
        packet.getCraftingData().add(ShapelessRecipeData.of(CraftingDataType.SHAPELESS, "test:valid",
                List.of(ItemDescriptorWithCount.fromItem(item(BRIDGE_PROTOCOL, "minecraft:chest", 1))),
                result, uuid, "crafting_table", 0, 125));
        packet.getCraftingData().add(ShapelessRecipeData.of(CraftingDataType.SHAPELESS, "test:tag",
                List.of(new ItemDescriptorWithCount(new ItemTagDescriptor("minecraft:planks"), 1)),
                result, uuid, "crafting_table", 0, 126));

        var decoded = (CraftingDataPacket) clientbound(session, packet);
        assertEquals(1, decoded.getCraftingData().size());
        assertEquals("test:valid", ((ShapelessRecipeData) decoded.getCraftingData().getFirst()).getId());
    }

    @TestFactory
    Stream<DynamicTest> serverboundMovementRetainsCoordinatesAndTick() {
        return protocols().filter(p -> p >= 419).map(protocol -> DynamicTest.dynamicTest("movement input " + protocol, () -> {
            var session = initialized(protocol);
            var packet = new PlayerAuthInputPacket();
            packet.setPosition(Vector3f.from(3.25f, 65.62f, -7.5f)); packet.setRotation(Vector3f.ZERO);
            packet.setMotion(Vector2f.from(1, 0)); packet.setTick(123);
            packet.setDelta(Vector3f.from(0.25f, 0, 0)); packet.setVrGazeDirection(Vector3f.ZERO);
            packet.setInputMode(InputMode.MOUSE); packet.setPlayMode(ClientPlayMode.NORMAL);
            packet.setInputInteractionModel(InputInteractionModel.CROSSHAIR);
            packet.setInteractRotation(Vector2f.ZERO); packet.setAnalogMoveVector(Vector2f.ZERO);
            packet.setCameraOrientation(Vector3f.ZERO); packet.setRawMoveVector(Vector2f.ZERO);
            var buffer = Unpooled.buffer(); var translatedBuffer = Unpooled.buffer();
            try {
                session.encodeClient(packet, buffer);
                var id = session.translateServerbound(buffer, translatedBuffer,
                        session.getClientCodec().getPacketDefinition(packet.getClass()).getId());
                assertNotNull(id);
                var decoded = (PlayerAuthInputPacket) session.getServerCodec().tryDecode(
                        session.getServerCodecHelper(), translatedBuffer, id);
                assertEquals(packet.getPosition(), decoded.getPosition()); assertEquals(123, decoded.getTick());
                assertEquals(0, translatedBuffer.readableBytes());
            } finally { buffer.release(); translatedBuffer.release(); }
        }));
    }

    @TestFactory
    Stream<DynamicTest> heldItemsTranslateBackToBridgeIds() {
        return protocols().map(protocol -> DynamicTest.dynamicTest("serverbound held item " + protocol, () -> {
            var session = initialized(protocol); var packet = new MobEquipmentPacket();
            packet.setRuntimeEntityId(1); packet.setItem(item(protocol, "minecraft:chest", 2));
            var translated = (MobEquipmentPacket) session.translateServerbound(packet);
            assertEquals(ItemTypeDictionary.getInstance(BRIDGE_PROTOCOL).fromStringId("minecraft:chest").intValue(),
                    translated.getItem().getDefinition().getRuntimeId());
            assertEquals(2, translated.getItem().getCount());
        }));
    }

    @TestFactory
    Stream<DynamicTest> embeddedChestInteractionsUseBridgeItemIds() {
        return protocols().filter(p -> p >= 419).map(protocol -> DynamicTest.dynamicTest("embedded item-use transaction " + protocol, () -> {
            var session = initialized(protocol);
            var input = new PlayerAuthInputPacket();
            input.setRotation(Vector3f.ZERO); input.setPosition(Vector3f.ZERO); input.setMotion(Vector2f.ZERO);
            input.setAnalogMoveVector(Vector2f.ZERO); input.setInteractRotation(Vector2f.ZERO);
            input.setDelta(Vector3f.ZERO); input.setRawMoveVector(Vector2f.ZERO);
            var use = new org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction();
            use.setItemInHand(item(protocol, "minecraft:chest", 1));
            input.setItemUseTransaction(use);
            var translated = (PlayerAuthInputPacket) session.translateServerbound(input);
            assertEquals(ItemTypeDictionary.getInstance(BRIDGE_PROTOCOL).fromStringId("minecraft:chest").intValue(),
                    translated.getItemUseTransaction().getItemInHand().getDefinition().getRuntimeId());
        }));
    }
}
