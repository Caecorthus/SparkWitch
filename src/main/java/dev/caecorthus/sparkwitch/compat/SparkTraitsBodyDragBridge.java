package dev.caecorthus.sparkwitch.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Weak, fail-closed body query through SparkTraits' public API only.
 * 仅通过 SparkTraits 公共 API 弱反射查询尸体，并在兼容失败时拒绝拖动。
 */
public final class SparkTraitsBodyDragBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";

    private static volatile Method isFakeDeathBodyMethod;
    private static volatile boolean lookupFailed;

    private SparkTraitsBodyDragBridge() {
    }

    public static boolean canDragBody(Entity body) {
        boolean loaded = FabricLoader.getInstance().isModLoaded(MOD_ID);
        if (!loaded) {
            return true;
        }

        Method method = isFakeDeathBodyMethod();
        if (method == null) {
            return false;
        }
        try {
            Object result = method.invoke(null, body);
            return canDragFromQuery(true, result instanceof Boolean value ? value : null);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
            return false;
        }
    }

    static boolean canDragFromQuery(boolean loaded, @Nullable Boolean fakeDeathBody) {
        return !loaded || Boolean.FALSE.equals(fakeDeathBody);
    }

    @Nullable
    private static Method isFakeDeathBodyMethod() {
        if (lookupFailed) {
            return null;
        }
        Method cached = isFakeDeathBodyMethod;
        if (cached != null) {
            return cached;
        }
        synchronized (SparkTraitsBodyDragBridge.class) {
            if (isFakeDeathBodyMethod != null) {
                return isFakeDeathBodyMethod;
            }
            if (lookupFailed) {
                return null;
            }
            try {
                Class<?> apiClass = Class.forName(API_CLASS);
                isFakeDeathBodyMethod = apiClass.getMethod("isFakeDeathBody", Entity.class);
                return isFakeDeathBodyMethod;
            } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
                lookupFailed = true;
                return null;
            }
        }
    }
}
