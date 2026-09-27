package com.github.blackjack200.ouranos.translators.new_to_old.v582to575;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.data.LevelEventType;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;

import java.util.List;

import static org.cloudburstmc.protocol.bedrock.data.LevelEvent.*;

public class Protocol582to575 extends ProtocolToProtocol {
    private static final List<LevelEventType> NEW_CRACK_EVENTS = List.of(
            PARTICLE_BREAK_BLOCK_DOWN, PARTICLE_BREAK_BLOCK_UP, PARTICLE_BREAK_BLOCK_NORTH,
            PARTICLE_BREAK_BLOCK_SOUTH, PARTICLE_BREAK_BLOCK_WEST, PARTICLE_BREAK_BLOCK_EAST
    );

    @Override
    protected void registerProtocol() {
        this.registerClientbound(LevelEventPacket.class, wrapped -> {
            final LevelEventPacket packet = (LevelEventPacket) wrapped.getPacket();
            if (NEW_CRACK_EVENTS.contains(packet.getType())) {
                packet.setType(PARTICLE_CRACK_BLOCK);
            }
        });
    }
}
