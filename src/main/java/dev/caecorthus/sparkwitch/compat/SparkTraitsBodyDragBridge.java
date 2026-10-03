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

    /**
     * True only when SparkTraits positively reports a Depression fake-death body; absent or broken API reads as real,
     * so Death Sense keeps working without SparkTraits.
     * 仅当 SparkTraits 明确报告为抑郁假死尸体时返回 true；API 缺失或异常视为真尸体，保证无 SparkTraits 时死亡感知照常工作。
     */
    public static boolean isConfirmedFakeDeathBody(Entity body) {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return false;
        }
        Method method = isFakeDeathBodyMethod();
        if (method == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(method.invoke(null, body));
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
