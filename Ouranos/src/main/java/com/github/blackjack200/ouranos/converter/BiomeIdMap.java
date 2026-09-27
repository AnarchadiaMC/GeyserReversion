package com.github.blackjack200.ouranos.converter;

import com.github.blackjack200.ouranos.data.LegacyToStringBidirectionalIdMap;
import lombok.Getter;

public class BiomeIdMap extends LegacyToStringBidirectionalIdMap {
    @Getter
    private static final BiomeIdMap instance;

    static {
        instance = new BiomeIdMap();
    }

    public BiomeIdMap() {
        super("biome_id_map.json");
    }
}
