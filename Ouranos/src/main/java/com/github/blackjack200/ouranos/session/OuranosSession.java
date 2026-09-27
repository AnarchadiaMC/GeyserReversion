package com.github.blackjack200.ouranos.session;

import com.github.blackjack200.ouranos.ProtocolInfo;
import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import com.github.blackjack200.ouranos.base.WrappedBedrockPacket;
import com.github.blackjack200.ouranos.session.storage.BlockDictionaryStorage;
import com.github.blackjack200.ouranos.translators.GlobalItemTranslator;
import com.github.blackjack200.ouranos.translators.GlobalProtocolTranslator;
import com.github.blackjack200.ouranos.translators.GlobalWorldTranslator;
import com.github.blackjack200.ouranos.session.storage.OuranosStorage;
import lombok.Getter;
import lombok.Setter;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.packet.*;

import java.util.*;

public abstract class OuranosSession {
    private static final List<ProtocolToProtocol> GLOBAL_TRANSLATORS = new ArrayList<>();
    static {
        GLOBAL_TRANSLATORS.add(new GlobalProtocolTranslator());
        GLOBAL_TRANSLATORS.add(new GlobalWorldTranslator());
        GLOBAL_TRANSLATORS.add(new GlobalItemTranslator());
    }

    @Getter @Setter
    private long uniqueId, runtimeId;
    @Getter
    private final int protocolId, targetVersion;

    @Getter
    private boolean serverAuthoritativeInventories, serverAuthoritativeBlockBreaking;

    @Getter @Setter
    private boolean hashedBlockIds;

    @Getter
    private AuthoritativeMovementMode authoritativeMovementMode;

    private final Map<Class<?>, OuranosStorage> storages = new HashMap<>();

    public void put(OuranosStorage storage) {
        this.storages.put(storage.getClass(), storage);
    }
    @SuppressWarnings("unchecked")
    public final <T extends OuranosStorage> T get(Class<T> klass) {
        return (T) this.storages.get(klass);
    }

    private final List<ProtocolToProtocol> translators = new ArrayList<>(), reversedTranslators;

    public OuranosSession(int protocolId, int targetVersion) {
        this.protocolId = protocolId;
        this.targetVersion = targetVersion;

        this.translators.addAll(ProtocolInfo.getTranslators(targetVersion, protocolId));
        this.translators.forEach(translator -> translator.init(this));

        GLOBAL_TRANSLATORS.forEach(translator -> translator.init(this));

        this.reversedTranslators = new ArrayList<>();
        this.reversedTranslators.addAll(this.translators);
        Collections.reverse(this.reversedTranslators);
    }

    public abstract void sendUpstreamPacket(BedrockPacket packet);
    public abstract void sendDownstreamPacket(BedrockPacket packet);

    public final BedrockPacket translateClientbound(BedrockPacket packet) {
        if (packet instanceof StartGamePacket startGamePacket) {
            this.uniqueId = startGamePacket.getUniqueEntityId();
            this.runtimeId = startGamePacket.getRuntimeEntityId();
            this.serverAuthoritativeInventories = startGamePacket.isInventoriesServerAuthoritative();
            this.authoritativeMovementMode = startGamePacket.getAuthoritativeMovementMode();
            this.serverAuthoritativeBlockBreaking = startGamePacket.isServerAuthoritativeBlockBreaking();
            setHashedBlockIds(startGamePacket.isBlockNetworkIdsHashed());

            put(new BlockDictionaryStorage(this, startGamePacket.getBlockProperties()));
        }

        final WrappedBedrockPacket wrapped = new WrappedBedrockPacket(this, this.getTargetVersion(), this.getProtocolId(), packet, false);
        for (ProtocolToProtocol translator : GLOBAL_TRANSLATORS) {
            translator.passthroughClientbound(wrapped);
        }

        for (ProtocolToProtocol translator : this.translators) {
            translator.passthroughClientbound(wrapped);
        }
        return wrapped.isCancelled() ? null : wrapped.getPacket();
    }

    public final BedrockPacket translateServerbound(BedrockPacket packet) {
        final WrappedBedrockPacket wrapped = new WrappedBedrockPacket(this, this.getProtocolId(), this.getTargetVersion(), packet, false);
        for (ProtocolToProtocol translator : GLOBAL_TRANSLATORS) {
            translator.passthroughServerbound(wrapped);
        }

        for (ProtocolToProtocol translator : this.reversedTranslators) {
            translator.passthroughServerbound(wrapped);
        }
        return wrapped.isCancelled() ? null : wrapped.getPacket();
    }
}
