package dev.caecorthus.sparkwitch.client.blackraven;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Reads parameters only through the optional common public facade; never calls a renderer. */
final class LastEscapeTraitsVisionBridge {
    private static boolean resolved;
    private static Method query;
    private static Method protocol;

    private LastEscapeTraitsVisionBridge() {
    }

    static float[] composition(PlayerEntity player) {
        if (!resolved) {
            resolved = true;
            if (FabricLoader.getInstance().isModLoaded("sparktraits")) {
                try {
                    Class<?> facade = Class.forName("dev.caecorthus.sparktraits.api.SparkTraitsApi", false,
                            LastEscapeTraitsVisionBridge.class.getClassLoader());
                    Method method = facade.getMethod("getLastEscapeComposition", PlayerEntity.class);
                    Method version = facade.getMethod("getLastEscapeVisionProtocolVersion");
                    if (Modifier.isStatic(method.getModifiers()) && method.getReturnType() == float[].class
                            && Modifier.isStatic(version.getModifiers()) && version.getReturnType() == int.class) {
                        query = method;
                        protocol = version;
                    }
                } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
                    // Old/absent Traits leaves the original Witch render path untouched.
                }
            }
        }
        if (query != null && player != null) {
            try {
                // Only a matching client promises to invoke the delegated pass; method presence is insufficient.
                // 仅匹配协议的客户端保证调用委托后处理，旧版仅有参数查询时不能跳过普通渲染。
                if (!Integer.valueOf(1).equals(protocol.invoke(null))) {
                    return new float[] {0.0f, 0.0f, 1.0f};
                }
                float[] values = (float[]) query.invoke(null, player);
                if (values != null && values.length == 3 && Float.isFinite(values[0])
                        && Float.isFinite(values[1]) && Float.isFinite(values[2])
                        && values[0] >= 0.5f && values[0] <= 1.0f && values[1] >= 0.0f && values[2] >= 0.0f) {
                    return values.clone();
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                query = null;
            }
        }
        return new float[] {0.0f, 0.0f, 1.0f};
    }
}
