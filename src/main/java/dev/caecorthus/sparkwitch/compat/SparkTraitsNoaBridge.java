package dev.caecorthus.sparkwitch.compat;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Optional SparkTraits seam for 城崎诺亚. Reflects only the public facade
 * {@code SparkTraitsApi.hasActiveTrait(PlayerEntity, Identifier)}; never {@code sparktraits.impl} or
 * {@code sparktraits.component}. SparkTraits itself never gives Noa the Impostor trait (owner 2026-11-04); this guards
 * older SparkTraits builds. Callers pick their own fail-closed direction: task pay needs a confirmed non-Impostor, the
 * knife innocent-kill death is skipped only for a confirmed Impostor.
 * 城崎诺亚的可选 SparkTraits 接缝。仅反射公共门面 {@code SparkTraitsApi.hasActiveTrait}，从不触及 {@code impl} 或
 * {@code component} 包。SparkTraits 自身不会给诺亚内鬼词条（所有者 2026-11-04）；此处防护较旧的 SparkTraits 版本。
 * 调用方各自决定失败关闭的方向：任务收入要求确认不是内鬼，用刀误杀处死只在确认是内鬼时跳过。
 */
public final class SparkTraitsNoaBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static final Identifier IMPOSTOR = Identifier.of(MOD_ID, "impostor");
    private static final OptionalStaticMethod HAS_ACTIVE_TRAIT = OptionalStaticMethod.of(MOD_ID, API_CLASS,
                    "hasActiveTrait", PlayerEntity.class, Identifier.class)
            .returning(boolean.class)
            .warnOnce(error -> SparkWitch.LOGGER.warn(
                    "Noa cannot verify the optional Impostor trait; task pay is skipped", error));

    private SparkTraitsNoaBridge() {
    }

    /**
     * FALSE when SparkTraits is absent; TRUE/FALSE from the facade; null when the answer cannot be trusted.
     * 未安装 SparkTraits 时为 FALSE；门面给出 TRUE/FALSE；答案不可信时为 null。
     */
    public static @Nullable Boolean isImpostor(@Nullable PlayerEntity player) {
        if (player == null) {
            return null;
        }
        return HAS_ACTIVE_TRAIT.callNullableBoolean(Boolean.FALSE, null, null, player, IMPOSTOR);
    }
}
