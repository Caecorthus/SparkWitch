package dev.caecorthus.sparkwitch.compat;

import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Shared optional SparkTraits Marksman query behind the Control Expert Taser and the USEC rifle. Reflects only the
 * public facade {@code SparkTraitsApi.getMarksmanRangeMultiplier(PlayerEntity)}; an absent, older or failing provider
 * means base range. Each role bridge forwards here and keeps its own, deliberately different, other trait policies.
 * Both sides may call it.
 * 控场专家电击枪与 USEC 狙击枪共用的可选 SparkTraits 精确枪手查询。仅反射公共门面 {@code getMarksmanRangeMultiplier}；
 * 提供方缺失、过旧或出错时使用基础射程。各职业桥接转发到这里，并各自保留其有意不同的其他词条策略。两端均可调用。
 */
final class SparkTraitsMarksmanBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    /** Mirrors ControlExpertRules' and UsecRules' Marksman bounds; kept local so this adapter has no role dependency.
     * 与 ControlExpertRules 和 UsecRules 的精确枪手倍率上下限一致；在此本地保存，使适配器不依赖职业模块。 */
    private static final double MIN_MULTIPLIER = 1.0;
    private static final double MAX_MULTIPLIER = 1.3;
    // Older SparkTraits builds lack this facade; that is an expected, silent base-range fallback.
    // 旧版 SparkTraits 没有此门面；这是预期中的静默回退到基础射程。
    private static final OptionalStaticMethod MARKSMAN = OptionalStaticMethod.of(MOD_ID, API_CLASS,
            "getMarksmanRangeMultiplier", PlayerEntity.class).returning(double.class).initializingClass();

    private SparkTraitsMarksmanBridge() {
    }

    /**
     * SparkTraits Marksman range multiplier, clamped to [1.0, 1.3]; any absence or failure is 1.0.
     * SparkTraits 精确枪手射程倍率，限制在 [1.0, 1.3]；任何缺失或失败均为 1.0。
     */
    static double marksmanRangeMultiplier(@Nullable PlayerEntity player) {
        return player == null ? MIN_MULTIPLIER : clampMultiplier(MARKSMAN.callOrNull(player));
    }

    /** Non-numeric or non-finite values fail closed to 1.0. / 非数值或非有限值回退为 1.0。 */
    static double clampMultiplier(@Nullable Object raw) {
        if (!(raw instanceof Number number)) {
            return MIN_MULTIPLIER;
        }
        double value = number.doubleValue();
        if (!Double.isFinite(value)) {
            return MIN_MULTIPLIER;
        }
        return Math.min(MAX_MULTIPLIER, Math.max(MIN_MULTIPLIER, value));
    }
}
