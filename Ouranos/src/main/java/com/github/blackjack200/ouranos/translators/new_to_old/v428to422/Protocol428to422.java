package com.github.blackjack200.ouranos.translators.new_to_old.v428to422;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import com.github.blackjack200.ouranos.session.OuranosSession;
import com.github.blackjack200.ouranos.translators.new_to_old.v428to422.storage.BlockBreakingStorage;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

import java.util.Objects;

public class Protocol428to422 extends ProtocolToProtocol {
    @Override
    public void init(OuranosSession session) {
        session.put(new BlockBreakingStorage(session));
    }

    @Override
    protected void registerProtocol() {
        this.registerServerbound(PlayerActionPacket.class, wrapped -> {
            if (wrapped.session().getAuthoritativeMovementMode() == AuthoritativeMovementMode.CLIENT) {
                return;
            }

            boolean serverAuth = wrapped.session().isServerAuthoritativeBlockBreaking();

            final BlockBreakingStorage storage = wrapped.session().get(BlockBreakingStorage.class);

            final PlayerActionPacket packet = (PlayerActionPacket) wrapped.getPacket();
            final PlayerActionType actionType = packet.getAction();
            switch (actionType) {
                case START_BREAK, STOP_BREAK, ABORT_BREAK, CONTINUE_BREAK -> {
                    final PlayerBlockActionData data = new PlayerBlockActionData();
                    data.setAction(switch (actionType) {
                        case START_BREAK -> PlayerActionType.START_BREAK;
                        case STOP_BREAK -> serverAuth ? PlayerActionType.BLOCK_PREDICT_DESTROY : PlayerActionType.STOP_BREAK;
                        case ABORT_BREAK -> PlayerActionType.ABORT_BREAK;
                        case CONTINUE_BREAK -> serverAuth ? PlayerActionType.BLOCK_CONTINUE_DESTROY : PlayerActionType.CONTINUE_BREAK;
                        default -> null;
                    });

//                    System.out.println(packet);

                    data.setFace(packet.getFace());
                    boolean reset = false;
                    if (actionType == PlayerActionType.CONTINUE_BREAK) {
                        if (storage.getPosition() == null) { // Seems to be happening in some weird case?
                            data.setAction(PlayerActionType.START_BREAK);
                        }

                        storage.setPosition(packet.getBlockPosition());
                        storage.setFace(packet.getFace());
                    } else if (actionType == PlayerActionType.START_BREAK) {
                        storage.setPosition(packet.getBlockPosition());
                        storage.setFace(packet.getFace());
                    } else {
                        data.setFace(storage.getFace());
                        reset = true;
                    }

                    data.setBlockPosition(Objects.requireNonNullElse(storage.getPosition(), packet.getBlockPosition())); // not a typo.
                    storage.getBlockInteractions().add(data);
//                    System.out.println(data);

                    if (reset) {
                        storage.setPosition(null); // Finish breaking, reset!
                    }

                    wrapped.cancel();
                }
            }
        });

        this.registerServerbound(PlayerAuthInputPacket.class, wrapped -> {
            final PlayerAuthInputPacket packet = (PlayerAuthInputPacket) wrapped.getPacket();
            final BlockBreakingStorage storage = wrapped.session().get(BlockBreakingStorage.class);

            if (!storage.getBlockInteractions().isEmpty()) {
                packet.getInputData().add(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS);
                packet.getPlayerActions().addAll(storage.getBlockInteractions());
                storage.getBlockInteractions().clear();
            }
        });
    }
}
