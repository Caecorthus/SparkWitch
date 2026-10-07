package dev.caecorthus.sparkwitch.client.fisher;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;

import java.lang.reflect.Method;

/**
 * External seam (optional SparkTraits): the Angler's own copy of the public {@code SparkTraitsApi
 * .isInstinctHidden(viewer, target)} facade read, deliberately not another role's bridge. Fail-closed means "not
 * hidden": an absent SparkTraits, an older build without the method, a wrong return type or a throwing call all hide
 * nothing and never break the client. Reflection never targets SparkTraits internals.
 * 外部接缝（可选 SparkTraits）：钓鱼佬自有的一份公共 {@code SparkTraitsApi.isInstinctHidden(viewer, target)}
 * 门面读取，刻意不复用其他职业的桥。失败关闭即“不隐藏”：SparkTraits 缺失、旧版本缺少该方法、返回类型不符或调用抛异常时
 * 都不隐藏任何人，也绝不会让客户端崩溃。反射从不触及 SparkTraits 内部实现。
 */
public final class FisherInstinctVisibilityBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static volatile Method hiddenMethod;
    private static volatile boolean unavailable;

    private FisherInstinctVisibilityBridge() {
    }

    public static boolean isHidden(PlayerEntity viewer, PlayerEntity target) {
        if (viewer == null || target == null || unavailable || !FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return false;
        }
        try {
            Method method = hiddenMethod;
            if (method == null) {
                Class<?> api = Class.forName(API_CLASS, false, FisherInstinctVisibilityBridge.class.getClassLoader());
                method = api.getMethod("isInstinctHidden", PlayerEntity.class, PlayerEntity.class);
                if (method.getReturnType() != boolean.class) {
                    unavailable = true;
                    return false;
                }
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
