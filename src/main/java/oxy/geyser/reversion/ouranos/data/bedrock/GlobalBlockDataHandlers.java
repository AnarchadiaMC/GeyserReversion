package oxy.geyser.reversion.ouranos.data.bedrock;

import oxy.geyser.reversion.ouranos.ProtocolInfo;
import oxy.geyser.reversion.ouranos.data.bedrock.block.BlockIdMetaUpgrader;
import oxy.geyser.reversion.ouranos.utils.binary.BinaryStream;
import lombok.SneakyThrows;

public class GlobalBlockDataHandlers {
    private static BlockIdMetaUpgrader blockIdMetaUpgrader = null;


    @SneakyThrows
    public static BlockIdMetaUpgrader getUpgrader() {
        if (blockIdMetaUpgrader == null) {
            try (var bin = ProtocolInfo.class.getClassLoader().getResourceAsStream("block_schema/id_meta_to_nbt/1.12.0.bin")) {
                assert bin != null;
                blockIdMetaUpgrader = BlockIdMetaUpgrader.loadFromString(new BinaryStream(bin.readAllBytes()));
            }
        }
        return blockIdMetaUpgrader;
    }
}
