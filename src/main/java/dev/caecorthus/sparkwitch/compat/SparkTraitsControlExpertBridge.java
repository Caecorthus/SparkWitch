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
    private static final Logger LOGGER = LoggerFactory.getLogger("sparkwitch");
    private static final OptionalStaticMethod TRAIT = resolve("hasActiveTrait", boolean.class, PlayerEntity.class,
            Identifier.class).warnOnce(error -> LOGGER.warn(
                    "Control Expert task income cannot verify the optional Impostor trait; skipping the reward",
                    error));

    private SparkTraitsControlExpertBridge() {
    }

    /**
     * SparkTraits Marksman range multiplier for the Taser, clamped to [1.0, 1.3]; any absence or failure is 1.0.
     * Shared with USEC through {@link SparkTraitsMarksmanBridge}.
     * 电击枪使用的 SparkTraits 精确枪手射程倍率，限制在 [1.0, 1.3]；任何缺失或失败均为 1.0。经 {@link SparkTraitsMarksmanBridge}
     * 与 USEC 共用。
     */
    public static double marksmanRangeMultiplier(@Nullable PlayerEntity player) {
        return SparkTraitsMarksmanBridge.marksmanRangeMultiplier(player);
    }

    /**
     * Whether the SparkTraits Impostor trait is active: FALSE when SparkTraits is absent, null when the facade is
     * missing or fails (callers treat null as "do not pay", like the Judge).
     * SparkTraits 内鬼词条是否生效：未安装 SparkTraits 时为 FALSE；门面缺失或出错时为 null（调用方与法官一致，视为不发放）。
     */
    public static @Nullable Boolean isImpostor(@Nullable PlayerEntity player) {
        return player == null ? null : TRAIT.callNullableBoolean(Boolean.FALSE, null, null, player, IMPOSTOR);
    }

    private static OptionalStaticMethod resolve(String name, Class<?> returnType, Class<?>... parameters) {
        return OptionalStaticMethod.of(MOD_ID, API_CLASS, name, parameters).returning(returnType).initializingClass();
    }
}
