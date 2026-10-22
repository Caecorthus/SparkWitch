package dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor;

import dev.caecorthus.sparkwitch.compat.OptionalStaticMethod;
import net.minecraft.entity.player.PlayerEntity;

/** Only confirmed terminal deaths transfer factors; old installed providers fail closed.
 * 仅确认终局死亡才传递因子；已安装的旧提供方缺少接口时保守拒绝。 */
public final class WitchFactorTraitsBridge {
    /**
     * Absent SparkTraits: not intercepted. Present but the facade method missing or failing: intercepted (fail closed),
     * unlike {@code SparkTraitsKillerBridge.isLastStandDeathIntercepted}, which reads every failure as not intercepted.
     * 未安装 SparkTraits：未拦截。已安装但门面方法缺失或出错：视为已拦截（保守失败），与把所有失败都视为未拦截的
     * {@code SparkTraitsKillerBridge.isLastStandDeathIntercepted} 不同。
     */
    private static final OptionalStaticMethod INTERCEPTED = OptionalStaticMethod.of("sparktraits",
            "dev.caecorthus.sparktraits.api.SparkTraitsApi", "isLastStandDeathIntercepted", PlayerEntity.class)
            .initializingClass();

    private WitchFactorTraitsBridge() { }

    public static boolean isDeathIntercepted(PlayerEntity player) {
        return INTERCEPTED.callBoolean(false, true, true, player);
    }
}
