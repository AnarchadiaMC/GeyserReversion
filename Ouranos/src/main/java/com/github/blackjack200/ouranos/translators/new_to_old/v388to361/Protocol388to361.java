package com.github.blackjack200.ouranos.translators.new_to_old.v388to361;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import com.github.blackjack200.ouranos.converter.BlockStateDictionary;
import com.github.blackjack200.ouranos.data.LegacyBlockIdToStringIdMap;
import com.github.blackjack200.ouranos.data.bedrock.GlobalBlockDataHandlers;
import com.github.blackjack200.ouranos.data.bedrock.block.BlockIdMetaUpgrader;
import com.github.blackjack200.ouranos.session.OuranosSession;
import com.github.blackjack200.ouranos.translators.new_to_old.v388to361.storage.ClientAuthMovementStorage;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.*;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;

import java.util.Map;
import java.util.Objects;

public class Protocol388to361 extends ProtocolToProtocol {
    @Override
    public void init(OuranosSession session) {
        session.put(new ClientAuthMovementStorage(session));
    }

    @Override
    protected void registerProtocol() {
        this.registerServerbound(PlayerActionPacket.class, wrapped -> {
            if (wrapped.session().getAuthoritativeMovementMode() == AuthoritativeMovementMode.CLIENT) {
                return;
            }

            final ClientAuthMovementStorage storage = wrapped.session().get(ClientAuthMovementStorage.class);

            final PlayerActionPacket packet = (PlayerActionPacket) wrapped.getPacket();
            final PlayerActionType actionType = packet.getAction();
            switch (actionType) {
                case START_SPRINT -> storage.getInputData().add(PlayerAuthInputData.START_SPRINTING);
                case STOP_SPRINT -> storage.getInputData().add(PlayerAuthInputData.STOP_SPRINTING);
                case START_SNEAK -> storage.getInputData().add(PlayerAuthInputData.START_SNEAKING);
                case STOP_SNEAK -> storage.getInputData().add(PlayerAuthInputData.STOP_SNEAKING);
                case START_SWIMMING -> storage.getInputData().add(PlayerAuthInputData.START_SWIMMING);
                case STOP_SWIMMING -> storage.getInputData().add(PlayerAuthInputData.STOP_SWIMMING);
                case START_GLIDE -> storage.getInputData().add(PlayerAuthInputData.START_GLIDING);
                case STOP_GLIDE -> storage.getInputData().add(PlayerAuthInputData.STOP_GLIDING);
                case START_CRAWLING -> storage.getInputData().add(PlayerAuthInputData.START_CRAWLING);
                case STOP_CRAWLING -> storage.getInputData().add(PlayerAuthInputData.STOP_CRAWLING);
                case START_FLYING -> storage.getInputData().add(PlayerAuthInputData.START_FLYING);
                case STOP_FLYING -> storage.getInputData().add(PlayerAuthInputData.STOP_FLYING);
                case JUMP -> storage.getInputData().add(PlayerAuthInputData.START_JUMPING);
            }
        });

        this.registerServerbound(MovePlayerPacket.class, wrapped -> {
            if (wrapped.session().getAuthoritativeMovementMode() == AuthoritativeMovementMode.CLIENT) {
                return;
            }

            final ClientAuthMovementStorage storage = wrapped.session().get(ClientAuthMovementStorage.class);

            final MovePlayerPacket packet = (MovePlayerPacket) wrapped.getPacket();
            storage.setPosition(packet.getPosition());
            storage.setRotation(packet.getRotation());

            wrapped.setPacket(storage.toAuthInput());
        });

        this.registerServerbound(LevelSoundEventPacket.class, wrapped -> {
            if (wrapped.session().getAuthoritativeMovementMode() == AuthoritativeMovementMode.CLIENT) {
                return;
            }

            final LevelSoundEventPacket packet = (LevelSoundEventPacket) wrapped.getPacket();

            if (packet.getSound() == SoundEvent.ATTACK_NODAMAGE) {
                final ClientAuthMovementStorage storage = wrapped.session().get(ClientAuthMovementStorage.class);
                storage.getInputData().add(PlayerAuthInputData.MISSED_SWING);
            }
        });

        this.registerServerbound(StartGamePacket.class, wrapped -> {
            final StartGamePacket packet = (StartGamePacket) wrapped.getPacket();

            packet.setBlockPalette(new NbtList<>(NbtType.COMPOUND, BlockStateDictionary.getInstance(wrapped.getOutput()).getKnownStates().stream().map((e) -> {
                BlockIdMetaUpgrader.Block blk = GlobalBlockDataHandlers.getUpgrader().fromLatestStateHash(e.latestStateHash());
                short legacyId = (short) (Objects.requireNonNullElse(LegacyBlockIdToStringIdMap.getInstance().fromString(wrapped.getOutput(), e.name()), 255) & 0xfffffff);
                return NbtMap.builder().putCompound("block", NbtMap.fromMap(
                        Map.of(
                                "name", blk.id(),
                                "meta", (short) blk.meta(),
                                "id", legacyId
                        ))).build();
            }).toList()));
        });
    }
}
