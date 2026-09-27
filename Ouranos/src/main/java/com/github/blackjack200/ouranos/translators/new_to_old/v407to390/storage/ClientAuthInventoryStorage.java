package com.github.blackjack200.ouranos.translators.new_to_old.v407to390.storage;

import com.github.blackjack200.ouranos.session.OuranosSession;
import com.github.blackjack200.ouranos.session.storage.OuranosStorage;
import lombok.Getter;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseContainer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Getter
public class ClientAuthInventoryStorage extends OuranosStorage {
    private final Map<Integer, List<ItemData>> inventories = new HashMap<>();
    private final Map<Integer, Consumer<List<ItemStackResponseContainer>>> stackResponses = new HashMap<>();

    public ClientAuthInventoryStorage(OuranosSession user) {
        super(user);
    }
}
