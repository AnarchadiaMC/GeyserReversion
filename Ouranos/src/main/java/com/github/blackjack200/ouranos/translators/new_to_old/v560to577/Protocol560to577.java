package com.github.blackjack200.ouranos.translators.new_to_old.v560to577;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;

import static org.cloudburstmc.protocol.bedrock.data.SoundEvent.*;

public class Protocol560to577 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerClientbound(LevelSoundEventPacket.class, wrapped -> {
            final LevelSoundEventPacket packet = (LevelSoundEventPacket) wrapped.getPacket();
            final SoundEvent sound = packet.getSound();
            if (sound == DOOR_OPEN || sound == DOOR_CLOSE || sound == TRAPDOOR_OPEN || sound == TRAPDOOR_CLOSE || sound == FENCE_GATE_OPEN || sound == FENCE_GATE_CLOSE) {
                final LevelEventPacket levelEvent = new LevelEventPacket();
                levelEvent.setType(LevelEvent.SOUND_DOOR_OPEN);
                levelEvent.setPosition(packet.getPosition());
                wrapped.setPacket(levelEvent);
            }
        });
    }
}
