package oxy.geyser.reversion.ouranos.converter;

import oxy.geyser.reversion.ouranos.data.LegacyToStringBidirectionalIdMap;
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
