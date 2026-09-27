package oxy.geyser.reversion.ouranos.converter;

public class ChunkRewriteException extends Exception {
    public ChunkRewriteException(String message) {
        super(message);
    }

    public ChunkRewriteException(Throwable e) {
        super(e);
    }
}
