package dev.caecorthus.sparkwitch.client.blind.render;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * Reflective Iris check (no compile dependency): {@code IrisApi.getInstance().isShaderPackInUse()}. A shader pack
 * renders into its own G-buffers, so the captured depth cannot be trusted; any reflection failure while Iris is
 * loaded counts as "in use" so the Blind fails closed (C13).
 * 反射方式的 Iris 检测（无编译依赖）：{@code IrisApi.getInstance().isShaderPackInUse()}。光影包渲染到自己的 G 缓冲，
 * 捕获的深度不可信；Iris 已加载时任何反射失败都视为“正在使用”，使盲人失败即全黑（C13）。
 */
public final class BlindShaderPackCheck {
    static final String IRIS_MOD_ID = "iris";
    static final String IRIS_API = "net.irisshaders.iris.api.v0.IrisApi";
    private static Method getInstance;
    private static Method isShaderPackInUse;

    private BlindShaderPackCheck() {
    }

    public static boolean isShaderPackInUse() {
        return resolve(FabricLoader.getInstance().isModLoaded(IRIS_MOD_ID), BlindShaderPackCheck::queryIris);
    }

    /** Pure rule: no Iris means no pack; with Iris, an error fails closed. / 纯规则：无 Iris 即无光影包；有 Iris 时出错按正在使用处理。 */
    static boolean resolve(boolean irisLoaded, Callable<Boolean> query) {
        if (!irisLoaded) {
            return false;
        }
        try {
            return !Boolean.FALSE.equals(query.call());
        } catch (Exception | LinkageError exception) {
            return true;
        }
    }

    private static Boolean queryIris() throws ReflectiveOperationException {
        if (getInstance == null || isShaderPackInUse == null) {
            Class<?> api = Class.forName(IRIS_API);
            getInstance = api.getMethod("getInstance");
            isShaderPackInUse = api.getMethod("isShaderPackInUse");
        }
        return (Boolean) isShaderPackInUse.invoke(getInstance.invoke(null));
    }
}
