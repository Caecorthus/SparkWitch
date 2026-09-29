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
     * Returns true when the kill must be cancelled; the hit reaction runs only then (C3), and a failing reaction never
     * weakens the immunity.
     * 返回 true 表示必须取消此次击杀；仅在此时触发受击反应（C3），且反应出错绝不削弱免疫。
     */
    public static boolean interceptKill(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer,
                                        @Nullable Identifier deathReason, boolean force) {
        if (!FiendParticipation.isDormantFiend(victim)) {
            return false;
        }
        if (!blocksDeath(true, deathReason, force)) {
            return false;
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
