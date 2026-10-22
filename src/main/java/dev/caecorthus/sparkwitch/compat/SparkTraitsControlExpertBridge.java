package dev.caecorthus.sparkwitch.compat;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional SparkTraits seam for the Control Expert. Reflects only the public facade
 * {@code SparkTraitsApi.getMarksmanRangeMultiplier(PlayerEntity)} and {@code hasActiveTrait(PlayerEntity, Identifier)};
 * an absent, older or failing provider means base Taser range and Judge-style task-pay semantics.
 * 控场专家的可选 SparkTraits 接缝。仅反射公共门面 {@code getMarksmanRangeMultiplier} 与 {@code hasActiveTrait}；
 * 提供方缺失、过旧或出错时，电击枪使用基础射程，任务收入沿用法官语义。
 */
public final class SparkTraitsControlExpertBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static final Identifier IMPOSTOR = Identifier.of("sparktraits", "impostor");
    /** Mirrors ControlExpertRules' multiplier bounds; kept local so this adapter has no role dependency.
     * 与 ControlExpertRules 的倍率上下限一致；在此本地保存，使适配器不依赖职业模块。 */
    private static final double MIN_MULTIPLIER = 1.0;
    private static final double MAX_MULTIPLIER = 1.3;
    private static final Logger LOGGER = LoggerFactory.getLogger("sparkwitch");
    // Older SparkTraits builds lack this facade; that is an expected, silent base-range fallback.
    // 旧版 SparkTraits 没有此门面；这是预期中的静默回退到基础射程。
    private static final OptionalStaticMethod MARKSMAN = resolve("getMarksmanRangeMultiplier", double.class,
            PlayerEntity.class);
    private static final OptionalStaticMethod TRAIT = resolve("hasActiveTrait", boolean.class, PlayerEntity.class,
            Identifier.class).warnOnce(error -> LOGGER.warn(
                    "Control Expert task income cannot verify the optional Impostor trait; skipping the reward",
                    error));

    private SparkTraitsControlExpertBridge() {
    }

    /**
     * SparkTraits Marksman range multiplier for the Taser, clamped to [1.0, 1.3]; any absence or failure is 1.0.
     * 电击枪使用的 SparkTraits 精确枪手射程倍率，限制在 [1.0, 1.3]；任何缺失或失败均为 1.0。
     */
    public static double marksmanRangeMultiplier(@Nullable PlayerEntity player) {
        return player == null ? MIN_MULTIPLIER : clampMultiplier(MARKSMAN.callOrNull(player));
    }

    /**
     * Whether the SparkTraits Impostor trait is active: FALSE when SparkTraits is absent, null when the facade is
     * missing or fails (callers treat null as "do not pay", like the Judge).
     * SparkTraits 内鬼词条是否生效：未安装 SparkTraits 时为 FALSE；门面缺失或出错时为 null（调用方与法官一致，视为不发放）。
     */
    public static @Nullable Boolean isImpostor(@Nullable PlayerEntity player) {
        return player == null ? null : TRAIT.callNullableBoolean(Boolean.FALSE, null, null, player, IMPOSTOR);
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

    private static OptionalStaticMethod resolve(String name, Class<?> returnType, Class<?>... parameters) {
        return OptionalStaticMethod.of(MOD_ID, API_CLASS, name, parameters).returning(returnType).initializingClass();
    }
}
