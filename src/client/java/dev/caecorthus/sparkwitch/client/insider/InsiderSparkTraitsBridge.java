package dev.caecorthus.sparkwitch.client.insider;

import dev.caecorthus.sparkwitch.compat.OptionalStaticMethod;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * External seam (optional SparkTraits): the Insider's own client read of the public {@code SparkTraitsApi} facade,
 * {@code isInstinctHidden(viewer, target)}; never SparkTraits' implementation or component packages. Fail-closed means
 * "not hidden": an absent SparkTraits, an older build without the method, a wrong shape or a throwing call changes
 * nothing and never breaks the client.
 * 外部接缝（可选 SparkTraits）：内应自有的客户端读取，仅经由公共门面 {@code SparkTraitsApi} 的
 * {@code isInstinctHidden(viewer, target)}，从不触及 SparkTraits 的实现或组件包。失败关闭即“不隐藏”：SparkTraits 缺失、
 * 旧版缺少方法、形状不符或调用抛异常时都不改变任何结果，也绝不会让客户端崩溃。
 */
public final class InsiderSparkTraitsBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    // The mod check and the lookup each run once, on first use, instead of on every outline query.
    // 模组检查与方法查找各只在首次使用时执行一次，而不是每次描边查询都执行。
    private static final OptionalStaticMethod HIDDEN =
            resolve("isInstinctHidden", PlayerEntity.class, PlayerEntity.class);

    private InsiderSparkTraitsBridge() {
    }

    /** SparkTraits hides the target from this viewer's instinct. / SparkTraits 是否对该观察者的本能隐藏目标。 */
    public static boolean isInstinctHidden(@Nullable PlayerEntity viewer, @Nullable PlayerEntity target) {
        return viewer != null && target != null && HIDDEN.callBoolean(false, false, false, viewer, target);
    }

    private static OptionalStaticMethod resolve(String name, Class<?>... parameters) {
        return OptionalStaticMethod.of(MOD_ID, API_CLASS, name, parameters).returning(boolean.class);
    }
}
