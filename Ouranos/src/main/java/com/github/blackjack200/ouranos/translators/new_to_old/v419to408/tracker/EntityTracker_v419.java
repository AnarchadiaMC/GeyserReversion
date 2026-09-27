package com.github.blackjack200.ouranos.translators.new_to_old.v419to408.tracker;

import com.github.blackjack200.ouranos.session.OuranosSession;
import com.github.blackjack200.ouranos.session.storage.OuranosStorage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityTracker_v419 extends OuranosStorage {
    private final Map<Long, Long> uniqueIdToRuntimeId = new HashMap<>();
    private final Map<Long, EntityCache> entities = new ConcurrentHashMap<>();

    public EntityTracker_v419(OuranosSession user) {
        super(user);
    }

    public void cache(long runtimeId, long uniqueId, Vector3f position, Vector3f rotation) {
        this.entities.put(runtimeId, new EntityCache(position, rotation));
        this.uniqueIdToRuntimeId.put(uniqueId, runtimeId);
    }

    public void remove(long entityId) {
        this.entities.remove(this.uniqueIdToRuntimeId.remove(entityId));
    }

    public void moveRelative(EntityCache entity, MoveEntityDeltaPacket delta) {
        if (entity == null) {
            return;
        }

        float posX = delta.getFlags().contains(MoveEntityDeltaPacket.Flag.HAS_X) ? delta.getX() : entity.position.getX();
        float posY = delta.getFlags().contains(MoveEntityDeltaPacket.Flag.HAS_Y) ? delta.getY() : entity.position.getY();
        float posZ = delta.getFlags().contains(MoveEntityDeltaPacket.Flag.HAS_Z) ? delta.getZ() : entity.position.getZ();

        float pitch = delta.getFlags().contains(MoveEntityDeltaPacket.Flag.HAS_PITCH) ? delta.getPitch() : entity.rotation.getX();
        float yaw = delta.getFlags().contains(MoveEntityDeltaPacket.Flag.HAS_YAW) ? delta.getYaw() : entity.rotation.getY();
        float headYaw = delta.getFlags().contains(MoveEntityDeltaPacket.Flag.HAS_HEAD_YAW) ? delta.getHeadYaw() : entity.rotation.getZ();

        entity.setPosition(Vector3f.from(posX, posY, posZ));
        entity.setRotation(Vector3f.from(pitch, yaw, headYaw));
    }

    public void moveAbsolute(long entityId, Vector3f position, Vector3f rotation) {
        final EntityCache entity = this.entities.get(entityId);
        if (this.entities.get(entityId) == null) {
            return;
        }

        entity.setPosition(position);
        entity.setRotation(rotation);
    }

    public EntityCache getEntity(long entityId) {
        return this.entities.get(entityId);
    }

    @AllArgsConstructor
    @Getter
    @Setter
    public static class EntityCache {
        private Vector3f position, rotation;
    }
}
