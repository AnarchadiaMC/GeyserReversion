package oxy.geyser.reversion.ouranos.translators.new_to_old.v2168to1001;

import org.cloudburstmc.protocol.bedrock.data.LevelEventType;
import org.cloudburstmc.protocol.bedrock.data.ParticleType;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventGenericPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import oxy.geyser.reversion.ouranos.base.ProtocolToProtocol;

import java.util.EnumMap;

/**
 * Downgrade step from the 2168 bridge (Bedrock 1.26.40-1.26.44) to 1001 (1.26.30), the newest
 * protocol Geyser 2.12.1 no longer speaks natively.
 *
 * <p>Only values that 2168 added to shared enums or metadata tables need a step here: every codec
 * writes packets through its own {@code TypeMap}/{@code EntityDataTypeMap}, so a constant added at
 * 2168 has no encoding further down the chain, while plain id renumbering between two protocols is
 * handled by each codec's own table and needs no translation.</p>
 */
public class Protocol2168to1001 extends ProtocolToProtocol {
    /**
     * Particles introduced at 2168: ids 102/103/104 in {@code PARTICLE_TYPES}, exposed to level
     * events as {@code LEVEL_EVENTS} ids 16486/16487/16488 (0x4000 + particle id).
     */
    private static final ParticleType[] NEW_PARTICLES = {
            ParticleType.ORANGE_POPLAR_LEAVES,
            ParticleType.RED_POPLAR_LEAVES,
            ParticleType.YELLOW_POPLAR_LEAVES
    };

    /**
     * Sounds introduced at 2168: id 611 was replaced with {@code MOUNT}, ids 612 and 613 were added
     * as {@code DISMOUNT} and {@code STRAW_BED_BREAK_LEAVE}. Codecs from v975 upward (1001 and the
     * 2168 bridge itself) write {@code SoundEvent.getSerializeName()} as a string, so the id change
     * is invisible there, but every older codec in the downgrade chain (944, 575, ...) still writes
     * the numeric {@code SOUND_EVENTS} id and throws on these three.
     */
    private static final SoundEvent[] NEW_SOUNDS = {
            SoundEvent.MOUNT,
            SoundEvent.DISMOUNT,
            SoundEvent.STRAW_BED_BREAK_LEAVE
    };

    @Override
    protected void registerProtocol() {
        // A level sound event carries no state: dropping the packet loses one sound, while a
        // pre-v975 target codec (944, 575, ...) would throw in TypeMap.getId. A 1001 target names the
        // sound on the wire and would ignore the unknown name anyway, so dropping is equivalent there.
        this.registerClientbound(LevelSoundEventPacket.class, wrapped -> {
            final SoundEvent sound = ((LevelSoundEventPacket) wrapped.getPacket()).getSound();
            for (final SoundEvent newSound : NEW_SOUNDS) {
                if (sound == newSound) {
                    wrapped.cancel();
                    return;
                }
            }
        });

        // Both level event packets encode their type through LEVEL_EVENTS, which embeds the particle
        // ids above; the 1001 table ends at 16485 (SULFUR_CUBE).
        this.registerClientbound(LevelEventPacket.class, wrapped -> {
            if (isNewParticle(((LevelEventPacket) wrapped.getPacket()).getType())) {
                wrapped.cancel();
            }
        });
        this.registerClientbound(LevelEventGenericPacket.class, wrapped -> {
            if (isNewParticle(((LevelEventGenericPacket) wrapped.getPacket()).getType())) {
                wrapped.cancel();
            }
        });

        // SetEntityData is stateful, so unrepresentable entries are stripped instead of dropping the
        // whole packet: an entity that keeps its position/health metadata visibly beats one that
        // freezes because one 2168-only entry could not be encoded.
        this.registerClientbound(SetEntityDataPacket.class, wrapped -> {
            final EntityDataMap metadata = ((SetEntityDataPacket) wrapped.getPacket()).getMetadata();

            // Entity flag 130 NOT_PICKABLE_FROM_INSIDE is new at 2168. The 1001 ENTITY_FLAGS map ends
            // at 129, and FlagTransformer.serialize calls TypeMap.getId for every present flag before
            // it decides which 64-bit chunk the flag belongs to, so encoding it throws. The flag has
            // no 64-bit chunk on either protocol (the chunks cover ids 0-127), so nothing is lost.
            removeNewFlag(metadata, EntityDataTypes.FLAGS);
            removeNewFlag(metadata, EntityDataTypes.FLAGS_2);

            // 2168 rebuilt entity data id 16 (DISPLAY_BLOCK_STATE/DISPLAY_FIREWORK/HORSE_FLAGS/
            // WITHER_SKULL_DANGEROUS) but kept every shared type at the same id, so only the formats
            // differ and each codec writes its own; that rebuild needs no translation.
            //
            // Id 25 UNKNOWN_HORSE_INT_25 is the one genuinely new EntityDataType at 2168. The 1001
            // EntityDataTypeMap has no definition for it, and BedrockCodecHelper.writeEntityData
            // dereferences the null returned by EntityDataTypeMap.fromType.
            metadata.remove(EntityDataTypes.UNKNOWN_HORSE_INT_25);

            // AREA_EFFECT_CLOUD_PARTICLE keeps its key at 1001, but its transformer resolves the
            // value through the codec's own PARTICLE_TYPES map, which lacks the particles above.
            final ParticleType particle = metadata.get(EntityDataTypes.AREA_EFFECT_CLOUD_PARTICLE);
            if (isNewParticle(particle)) {
                metadata.remove(EntityDataTypes.AREA_EFFECT_CLOUD_PARTICLE);
            }
        });
    }

    private static boolean isNewParticle(LevelEventType type) {
        for (final ParticleType newParticle : NEW_PARTICLES) {
            if (type == newParticle) {
                return true;
            }
        }
        return false;
    }

    private static void removeNewFlag(EntityDataMap metadata,
                                      EntityDataType<EnumMap<EntityFlag, Boolean>> type) {
        final EnumMap<EntityFlag, Boolean> flags = metadata.get(type);
        if (flags != null) {
            flags.remove(EntityFlag.NOT_PICKABLE_FROM_INSIDE);
        }
    }
}
