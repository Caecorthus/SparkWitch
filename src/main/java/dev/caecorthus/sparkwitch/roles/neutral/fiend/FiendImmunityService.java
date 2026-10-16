package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Dormant Fiend kill immunity (D3), decided for {@code GameFunctionsFiendImmunityMixin}. Server only. Every victim that
 * is not a dormant Fiend returns at once, so no other role, weapon or death reason reaches the reaction path.
 * 休眠魔人击杀免疫（D3），供 {@code GameFunctionsFiendImmunityMixin} 判定。仅服务端。凡不是休眠魔人的受害者立即返回，
 * 因此其他职业、武器与死因都不会进入反应路径。
 */
public final class FiendImmunityService {
    private FiendImmunityService() {
    }

    /**
     * Returns true when the kill must be cancelled; the feign death and the hit reaction run only then (C3), and a
     * failing one never weakens the immunity. Every cancelled kill is lethal force (owner answer 2026-10-16): it starts
     * the 20 s feign death first, so the reaction's Speed III can wait for the revival. While the Fiend feigns, a fall
     * is cancelled too ({@link FiendFeignRules#shieldsWhileFeigning}).
     * 返回 true 表示必须取消此次击杀；仅在此时触发假死与受击反应（C3），且二者出错绝不削弱免疫。每次被取消的击杀都算致命武力
     * （所有者 2026-10-16 回答）：先开始 20 秒假死，受击反应的速度 III 才能等到复活时给予。魔人假死期间，坠落同样被取消
     * （{@link FiendFeignRules#shieldsWhileFeigning}）。
     */
    public static boolean interceptKill(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer,
                                        @Nullable Identifier deathReason, boolean force) {
        if (!FiendParticipation.isDormantFiend(victim)) {
            return false;
        }
        if (!blocksDeath(true, deathReason, force)
                && !FiendFeignRules.shieldsWhileFeigning(FiendFeignService.isFeigning(victim), deathReason)) {
            return false;
        }
        try {
            FiendFeignService.onBlockedKill(victim, deathReason);
        } catch (RuntimeException exception) {
            SparkWitch.LOGGER.error("Fiend feign death failed; the kill stays cancelled.", exception);
        }
        try {
            FiendReactionService.onBlockedHit(victim, killer, deathReason);
        } catch (RuntimeException exception) {
            SparkWitch.LOGGER.error("Fiend hit reaction failed; the kill stays cancelled.", exception);
        }
        return true;
    }

    /**
     * Pure D3 decision. {@code force} is deliberately ignored: forced and piercing kills (bell toll, time curse,
     * gun punishment) are blocked too; only the {@link FiendRules#mayDie} reasons pass.
     * 纯 D3 判定。刻意忽略 {@code force}：强制与穿透击杀（丧钟、时间诅咒、误击惩罚）同样被拦截；只有
     * {@link FiendRules#mayDie} 的死因放行。
     */
    public static boolean blocksDeath(boolean dormantFiend, @Nullable Identifier deathReason, boolean force) {
        return dormantFiend && !FiendRules.mayDie(deathReason);
    }
}
