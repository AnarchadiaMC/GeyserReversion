package com.github.blackjack200.ouranos.translators.new_to_old.v388to361.storage;

import com.github.blackjack200.ouranos.session.OuranosSession;
import com.github.blackjack200.ouranos.session.storage.OuranosStorage;
import lombok.Getter;
import lombok.Setter;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.*;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

import java.util.ArrayDeque;
import java.util.Queue;

@Getter
@Setter
public class ClientAuthMovementStorage extends OuranosStorage {
    public ClientAuthMovementStorage(OuranosSession user) {
        super(user);
    }

    private Vector3f position, rotation;

    private final Queue<PlayerAuthInputData> inputData = new ArrayDeque<>();
    private InputMode inputMode = InputMode.UNDEFINED;

    private boolean breaking;

    public PlayerAuthInputPacket toAuthInput() {
        final PlayerAuthInputPacket packet = new PlayerAuthInputPacket();
        packet.setInputMode(this.inputMode);
        packet.setPosition(this.position);
        packet.setRotation(this.rotation);
        packet.getInputData().addAll(this.inputData);
        packet.setMotion(Vector2f.ZERO);
        packet.setDelta(Vector3f.ZERO);
        packet.setPlayMode(ClientPlayMode.NORMAL);
        this.inputData.clear();
        return packet;
    }
}
