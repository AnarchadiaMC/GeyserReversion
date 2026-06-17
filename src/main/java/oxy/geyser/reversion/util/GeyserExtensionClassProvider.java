package oxy.geyser.reversion.util;

import net.lenni0451.classtransform.utils.tree.IClassProvider;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.extension.ExtensionLoader;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static net.lenni0451.classtransform.utils.ASMUtils.slash;

public class GeyserExtensionClassProvider implements IClassProvider {
    @Override
    @Nonnull
    public byte[] getClass(@NotNull String name) throws ClassNotFoundException {
        ExtensionLoader extensionLoader = GeyserImpl.getInstance().getExtensionManager().extensionLoader();
        Class<?> klass;
        try {
            Method classByName = extensionLoader.getClass().getDeclaredMethod("classByName", String.class);
            classByName.setAccessible(true);
            klass = (Class<?>) classByName.invoke(extensionLoader, name);
        } catch (Exception e) {
            throw new ClassNotFoundException(name, e);
        }

        try (InputStream is = klass.getClassLoader().getResourceAsStream(slash(name) + ".class")) {
            Objects.requireNonNull(is, "Class input stream is null");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = is.read(buf)) > 0) baos.write(buf, 0, len);
            return baos.toByteArray();
        } catch (Throwable t) {
            throw new ClassNotFoundException(name, t);
        }
    }

    @Override
    @Nonnull
    public Map<String, Supplier<byte[]>> getAllClasses() {
        return Map.of();
    }
}