package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.OptionalStaticMethod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * External seam for USEC task pay: reflects only the public facade
 * {@code SparkTraitsApi.hasActiveTrait(PlayerEntity, Identifier)} for {@code sparktraits:impostor}, never its
 * {@code impl} or {@code component} packages. Semantics match the Control Expert's bridge: FALSE when SparkTraits is
 * absent, null ("unknown") when it is present but the facade is missing, malformed or throws; the caller then denies
 * the pay (fail closed). Server use only (task completion).
 * USEC 任务收入的外部接缝：只反射公共门面 {@code SparkTraitsApi.hasActiveTrait} 查询 {@code sparktraits:impostor}，
 * 从不触及 {@code impl} 或 {@code component} 包。语义与控场专家桥接一致：未安装 SparkTraits 时为 FALSE；
 * 已安装但门面缺失、形状不符或抛出异常时为 null（“未知”），调用方随即拒绝发放（失败即关闭）。仅服务端（任务完成）使用。
 */
final class UsecEconomyTraitsProbe {
    static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static final Identifier IMPOSTOR = Identifier.of("sparktraits", "impostor");
    private static final OptionalStaticMethod HAS_ACTIVE_TRAIT = OptionalStaticMethod.of(MOD_ID, API_CLASS,
                    "hasActiveTrait", PlayerEntity.class, Identifier.class)
            .returning(boolean.class)
            .warnOnce(error -> SparkWitch.LOGGER.warn(
                    "USEC task income cannot verify the optional Impostor trait; skipping the reward", error));

    private UsecEconomyTraitsProbe() {
    }

    /** Asked once; a throwing loader reads as absent. / 只询问一次；加载器抛出异常时视为未安装。 */
    static boolean isSparkTraitsLoaded() {
        return HAS_ACTIVE_TRAIT.isModLoaded();
    }

    /**
     * FALSE when SparkTraits is absent; TRUE/FALSE from the facade; null when the answer cannot be trusted.
     * 未安装 SparkTraits 时为 FALSE；门面给出 TRUE/FALSE；答案不可信时为 null。
     */
    static @Nullable Boolean isImpostor(@Nullable PlayerEntity player) {
        if (player == null) {
            return null;
        }
        return HAS_ACTIVE_TRAIT.callNullableBoolean(Boolean.FALSE, null, null, player, IMPOSTOR);
    }
}
