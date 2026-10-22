package dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;

/** Only confirmed terminal deaths transfer factors; old installed providers fail closed.
 * 仅确认终局死亡才传递因子；已安装的旧提供方缺少接口时保守拒绝。 */
public final class WitchFactorTraitsBridge {
    /**
     * Set once, after {@code loaded} and {@code intercepted}: the loaded mod set never changes at runtime, so the
     * {@code isModLoaded} answer is cached with the method lookup instead of being asked on every death.
     * 在 {@code loaded} 与 {@code intercepted} 之后只写一次：运行期已加载的模组集合不会改变，因此 {@code isModLoaded}
     * 的结果与方法查找一起缓存，而不是每次死亡都询问。
     */
    private static volatile boolean lookedUp;
    private static boolean loaded;
    private static Method intercepted;

    private WitchFactorTraitsBridge() { }

    public static boolean isDeathIntercepted(PlayerEntity player) {
        if (!lookedUp) lookUp();
        if (!loaded) return false;
        if (intercepted == null) return true;
        try {
            return Boolean.TRUE.equals(intercepted.invoke(null, player));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return true;
        }
    }

    private static synchronized void lookUp() {
        if (lookedUp) return;
        loaded = FabricLoader.getInstance().isModLoaded("sparktraits");
        if (loaded) {
            try {
                intercepted = Class.forName("dev.caecorthus.sparktraits.api.SparkTraitsApi")
                        .getMethod("isLastStandDeathIntercepted", PlayerEntity.class);
            } catch (ReflectiveOperationException | LinkageError ignored) { }
        }
        lookedUp = true;
    }
}
