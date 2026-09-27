package com.github.blackjack200.ouranos.utils;

import org.cloudburstmc.math.TrigMath;
import org.cloudburstmc.math.vector.Vector3f;

public class MathUtils {
    public static int ceil(float floatNumber) {
        int truncated = (int) floatNumber;
        return floatNumber > truncated ? truncated + 1 : truncated;
    }

    public static long chunkPositionToLong(int x, int z) {
        return ((x & 0xFFFFFFFFL) << 32L) | (z & 0xFFFFFFFFL);
    }

    public static int sign(final float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0;
        }

        return value == 0 ? 0 : value > 0 ? 1 : -1;
    }

    public static Vector3f getCameraOrientation(Vector3f rotation) {
        float f = rotation.getX() * 0.017453292F;
        float g = -rotation.getY() * 0.017453292F;
        float h = TrigMath.cos(g);
        float i = TrigMath.sin(g);
        float j = TrigMath.cos(f);
        float k = TrigMath.sin(f);
        return Vector3f.from(i * j, -k, h * j);
    }
}
