package dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;

/** Only confirmed terminal deaths transfer factors; old installed providers fail closed.
 * 仅确认终局死亡才传递因子；已安装的旧提供方缺少接口时保守拒绝。 */
public final class WitchFactorTraitsBridge {
    private static boolean lookedUp;
    private static Method intercepted;

    private WitchFactorTraitsBridge() { }

    public static boolean isDeathIntercepted(PlayerEntity player) {
        if (!FabricLoader.getInstance().isModLoaded("sparktraits")) return false;
        if (!lookedUp) {
            lookedUp = true;
            try {
                intercepted = Class.forName("dev.caecorthus.sparktraits.api.SparkTraitsApi")
                        .getMethod("isLastStandDeathIntercepted", PlayerEntity.class);
            } catch (ReflectiveOperationException | LinkageError ignored) { }
        }
        if (intercepted == null) return true;
        try {
            return Boolean.TRUE.equals(intercepted.invoke(null, player));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return true;
        }
    }
}
