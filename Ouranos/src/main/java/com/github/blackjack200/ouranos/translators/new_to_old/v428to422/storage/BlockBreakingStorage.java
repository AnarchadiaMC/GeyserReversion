package com.github.blackjack200.ouranos.translators.new_to_old.v428to422.storage;

import com.github.blackjack200.ouranos.session.OuranosSession;
import com.github.blackjack200.ouranos.session.storage.OuranosStorage;
import lombok.Getter;
import lombok.Setter;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData;

import java.util.ArrayDeque;
import java.util.Queue;

@Getter
@Setter
public class BlockBreakingStorage extends OuranosStorage {
    public BlockBreakingStorage(OuranosSession user) {
        super(user);
    }

    private Vector3i position;
    private int face;
    private final Queue<PlayerBlockActionData> blockInteractions = new ArrayDeque<>();
}
