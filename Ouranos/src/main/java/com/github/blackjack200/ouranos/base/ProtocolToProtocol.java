package com.github.blackjack200.ouranos.base;

import com.github.blackjack200.ouranos.session.OuranosSession;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ProtocolToProtocol {
    private final Map<Class<? extends BedrockPacket>, Consumer<WrappedBedrockPacket>> mappedClientBounds = new HashMap<>();
    private final Map<Class<? extends BedrockPacket>, Consumer<WrappedBedrockPacket>> mappedServerBounds = new HashMap<>();

    public ProtocolToProtocol() {
        this.registerProtocol();
    }

    public void init(OuranosSession session) {
    }

    protected void registerProtocol() {
    }

    public void registerClientbound(Class<? extends BedrockPacket> klass, Consumer<WrappedBedrockPacket> consumer) {
        if (this.mappedClientBounds.containsKey(klass)) {
            throw new RuntimeException("This (clientbound) packet is already mapped: " + klass + " - " + this.getClass());
        }

        this.mappedClientBounds.put(klass, consumer);
    }

    public void registerServerbound(Class<? extends BedrockPacket> klass, Consumer<WrappedBedrockPacket> consumer) {
        if (this.mappedServerBounds.containsKey(klass)) {
            throw new RuntimeException("This (serverbound) packet is already mapped: " + klass + " - " + this.getClass());
        }

        this.mappedServerBounds.put(klass, consumer);
    }

    public void passthroughClientbound(final WrappedBedrockPacket wrapped) {
        final Consumer<WrappedBedrockPacket> translator = this.mappedClientBounds.get(wrapped.getPacket().getClass());
        if (translator == null) {
            return;
        }

        translator.accept(wrapped);
    }

    public void passthroughServerbound(final WrappedBedrockPacket wrapped) {
        final Consumer<WrappedBedrockPacket> translator = this.mappedServerBounds.get(wrapped.getPacket().getClass());
        if (translator == null) {
            return;
        }

        translator.accept(wrapped);
    }
}
