package com.github.blackjack200.ouranos.translators.new_to_old.v419to408;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import com.github.blackjack200.ouranos.converter.BlockStateDictionary;
import com.github.blackjack200.ouranos.data.LegacyBlockIdToStringIdMap;
import com.github.blackjack200.ouranos.session.OuranosSession;
import com.github.blackjack200.ouranos.translators.new_to_old.v419to408.tracker.EntityTracker_v419;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponse;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseStatus;
import org.cloudburstmc.protocol.bedrock.packet.*;

import java.util.List;
import java.util.Objects;

public class Protocol419to408 extends ProtocolToProtocol {
    @Override
    public void init(OuranosSession session) {
        session.put(new EntityTracker_v419(session));
    }

    @Override
    protected void registerProtocol() {
        this.registerClientbound(ItemStackResponsePacket.class, wrapped -> {
            final ItemStackResponsePacket packet = (ItemStackResponsePacket) wrapped.getPacket();

            packet.getEntries().replaceAll(entry -> new ItemStackResponse(entry.getResult().equals(ItemStackResponseStatus.OK), entry.getRequestId(), entry.getContainers()));
        });

        this.registerClientbound(StartGamePacket.class, wrapped -> {
            final StartGamePacket packet = (StartGamePacket) wrapped.getPacket();

            final List<NbtMap> states = BlockStateDictionary.getInstance(wrapped.getOutput()).getKnownStates().stream().map((e) -> {
                short legacyId = (short) (Objects.requireNonNullElse(LegacyBlockIdToStringIdMap.getInstance().fromString(wrapped.getOutput(), e.name()), 255) & 0xfffffff);
                return NbtMap.builder().putCompound("block", e.rawState()).putShort("id", legacyId).build();
            }).toList();
            packet.setBlockPalette(new NbtList<>(NbtType.COMPOUND, states));
        });

        this.registerServerbound(PlayerAuthInputPacket.class, wrapped -> {
            final PlayerAuthInputPacket packet = (PlayerAuthInputPacket) wrapped.getPacket();
            packet.setDelta(Vector3f.ZERO);
        });
        
        // Entity position translation part
        this.registerClientbound(RemoveEntityPacket.class, wrapped -> {
            final RemoveEntityPacket packet = (RemoveEntityPacket) wrapped.getPacket();
            wrapped.session().get(EntityTracker_v419.class).remove(packet.getUniqueEntityId());
        });
        this.registerClientbound(AddPaintingPacket.class, wrapped -> {
            final AddPaintingPacket packet = (AddPaintingPacket) wrapped.getPacket();
            wrapped.session().get(EntityTracker_v419.class).cache(packet.getRuntimeEntityId(), packet.getUniqueEntityId(), packet.getPosition(), Vector3f.ZERO);
        });
        this.registerClientbound(AddItemEntityPacket.class, wrapped -> {
            final AddItemEntityPacket packet = (AddItemEntityPacket) wrapped.getPacket();
            wrapped.session().get(EntityTracker_v419.class).cache(packet.getRuntimeEntityId(), packet.getUniqueEntityId(), packet.getPosition(), Vector3f.ZERO);
        });
        this.registerClientbound(AddPlayerPacket.class, wrapped -> {
            final AddPlayerPacket packet = (AddPlayerPacket) wrapped.getPacket();
            wrapped.session().get(EntityTracker_v419.class).cache(packet.getRuntimeEntityId(), packet.getUniqueEntityId(), packet.getPosition(), packet.getRotation());
        });
        this.registerClientbound(AddEntityPacket.class, wrapped -> {
            final AddEntityPacket packet = (AddEntityPacket) wrapped.getPacket();
            wrapped.session().get(EntityTracker_v419.class).cache(packet.getRuntimeEntityId(), packet.getUniqueEntityId(), packet.getPosition(), Vector3f.from(packet.getRotation().getX(), packet.getRotation().getY(), packet.getRotation().getY()));
        });
        this.registerClientbound(MovePlayerPacket.class, wrapped -> {
            final MovePlayerPacket packet = (MovePlayerPacket) wrapped.getPacket();
            wrapped.session().get(EntityTracker_v419.class).moveAbsolute(packet.getRuntimeEntityId(), packet.getPosition(), packet.getRotation());
        });
        this.registerClientbound(MoveEntityAbsolutePacket.class, wrapped -> {
            final MoveEntityAbsolutePacket packet = (MoveEntityAbsolutePacket) wrapped.getPacket();
            wrapped.session().get(EntityTracker_v419.class).moveAbsolute(packet.getRuntimeEntityId(), packet.getPosition(), packet.getRotation());
        });

        this.registerClientbound(MoveEntityDeltaPacket.class, wrapped -> {
            final EntityTracker_v419 entityTracker = wrapped.session().get(EntityTracker_v419.class);
            final MoveEntityDeltaPacket packet = (MoveEntityDeltaPacket) wrapped.getPacket();

            final EntityTracker_v419.EntityCache entity = entityTracker.getEntity(packet.getRuntimeEntityId());
            if (entity == null) {
                return;
            }

            packet.setX(Math.round(packet.getX() * 100.0) / 100.0f);
            packet.setY(Math.round(packet.getY() * 100.0) / 100.0f);
            packet.setZ(Math.round(packet.getZ() * 100.0) / 100.0f);

            packet.setDeltaX(toIntDelta(packet.getX(), entity.getPosition().getX()));
            packet.setDeltaY(toIntDelta(packet.getY(), entity.getPosition().getY()));
            packet.setDeltaZ(toIntDelta(packet.getZ(), entity.getPosition().getZ()));

            entityTracker.moveRelative(entity, packet);
        });
    }

    private int toIntDelta(float current, float prev) {
        // Cursed....
        return Float.floatToIntBits(Math.round(current * 100.0) / 100.0f) - Float.floatToIntBits(Math.round(prev * 100.0) / 100.0f);
    }
}
