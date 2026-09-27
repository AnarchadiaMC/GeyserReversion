package com.github.blackjack200.ouranos.converter;

import com.github.blackjack200.ouranos.converter.palette.Palette;
import io.netty.buffer.ByteBuf;
import lombok.experimental.UtilityClass;
import org.cloudburstmc.protocol.common.util.TriFunction;

//@Log4j2
@UtilityClass
public class PaletteStorage {
    public static void translatePaletteStorage(int input, int output, ByteBuf from, ByteBuf to, TriFunction<Integer, Integer, Integer, Integer> rewriter) throws ChunkRewriteException {
        var storage = Palette.readNetwork(from, (id) -> id);
        storage.writeNetwork(to, output, (id) -> rewriter.apply(input, output, id));
    }
}
