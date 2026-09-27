package com.github.blackjack200.ouranos.base;

import com.github.blackjack200.ouranos.session.OuranosSession;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

@AllArgsConstructor
@Getter
@Setter
public class WrappedBedrockPacket {
    private final OuranosSession session;
    private final int input, output;
    private BedrockPacket packet;
    private boolean cancelled;

    public void cancel() {
        this.cancelled = true;
    }
    public OuranosSession session() {
        return this.session;
    }
}
