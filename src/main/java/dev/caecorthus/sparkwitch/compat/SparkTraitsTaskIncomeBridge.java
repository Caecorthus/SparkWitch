package dev.caecorthus.sparkwitch.compat;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * External seam for role-owned task income that must not stack with SparkTraits' own Impostor task pay. Reflects only
 * the public facade {@code SparkTraitsApi.hasActiveTrait(PlayerEntity, Identifier)} for {@code sparktraits:impostor},
 * never its {@code impl} or {@code component} packages. Same semantics as the USEC, Knight and Control Expert probes:
 * FALSE when SparkTraits is absent, null ("unknown") when it is present but the facade is missing, malformed or throws;
 * callers then pay nothing (fail closed). Server use only (task completion). Read by the Discoverer and the Accountant.
 * 角色自有任务收入的外部接缝，该收入不得与 SparkTraits 自行支付的内鬼任务收入叠加。只反射公共门面
 * {@code SparkTraitsApi.hasActiveTrait} 查询 {@code sparktraits:impostor}，从不触及 {@code impl} 或 {@code component} 包。
 * 语义与 USEC、骑士、控场专家的探测一致：未安装 SparkTraits 时为 FALSE；已安装但门面缺失、形状不符或抛出异常时为 null
 * （“未知”），调用方随即不发放（失败即关闭）。仅服务端（任务完成）使用。探索者与会计读取。
 */
public final class SparkTraitsTaskIncomeBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static final Identifier IMPOSTOR = Identifier.of(MOD_ID, "impostor");
    private static final OptionalStaticMethod HAS_ACTIVE_TRAIT = OptionalStaticMethod.of(MOD_ID, API_CLASS,
                    "hasActiveTrait", PlayerEntity.class, Identifier.class)
            .returning(boolean.class)
            .warnOnce(error -> SparkWitch.LOGGER.warn(
                    "Role task income cannot verify the optional Impostor trait; skipping the reward", error));

    private SparkTraitsTaskIncomeBridge() {
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
