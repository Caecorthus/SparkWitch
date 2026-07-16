package dev.caecorthus.sparkwitch.client.blackraven;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;

import java.lang.reflect.Method;

/** Optional bridge to SparkTraits' stable visibility facade only. / 仅连接 SparkTraits 稳定可见性门面的可选桥。 */
public final class SparkTraitsInstinctVisibilityBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static Method hiddenMethod;
    private static boolean unavailable;

    private SparkTraitsInstinctVisibilityBridge() {
    }

    public static boolean isHidden(PlayerEntity viewer, PlayerEntity target) {
        if (viewer == null || target == null || unavailable
                || !FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return false;
        }
        try {
            Method method = hiddenMethod;
            if (method == null) {
                Class<?> api = Class.forName(API_CLASS, false, SparkTraitsInstinctVisibilityBridge.class.getClassLoader());
                method = api.getMethod("isInstinctHidden", PlayerEntity.class, PlayerEntity.class);
                hiddenMethod = method;
            }
            return Boolean.TRUE.equals(method.invoke(null, viewer, target));
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            unavailable = true;
            return false;
        } catch (LinkageError | ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }
}
