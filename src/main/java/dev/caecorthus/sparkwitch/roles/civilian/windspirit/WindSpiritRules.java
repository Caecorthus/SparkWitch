package dev.caecorthus.sparkwitch.roles.civilian.windspirit;

import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Pure Wind Spirit decisions plus the exact promoted-Wraith identity boundary.
 * 风精灵纯规则，以及精确的冤魂晋升身份边界。
 */
public final class WindSpiritRules {
    public static final int SPEED_AMPLIFIER = 1;
    public static final int SPEED_REFRESH_THRESHOLD_TICKS = 20;

    private WindSpiritRules() {
    }

    public static boolean shouldRefreshSpeed(int currentAmplifier, int remainingTicks) {
        return currentAmplifier < SPEED_AMPLIFIER
                || currentAmplifier == SPEED_AMPLIFIER
                && remainingTicks <= SPEED_REFRESH_THRESHOLD_TICKS;
    }

    public static boolean canPassShopAliveGate(
            boolean ordinarilyAllowed,
            boolean windSpirit,
            boolean activeWraith,
            boolean restrictedWraith
    ) {
        return ordinarilyAllowed || windSpirit && activeWraith && !restrictedWraith;
    }

    public static boolean shouldMaintainBlackoutVision(boolean windSpirit, boolean blackoutActive) {
        return windSpirit && blackoutActive;
    }

    public static boolean shouldRewardTask(boolean promotedWindSpirit) {
        return promotedWindSpirit;
    }

    /**
     * Preserves vanilla eligibility except for Wind Spirit charges: active Wraiths are denied, while
     * another living, participating ordinary player may regain a hit filtered by Wraith collision.
     * 保留原版命中资格；风精灵风弹排除 active Wraith，并可恢复对其他存活参赛玩家的命中。
     */
    public static boolean resolveWindChargeHit(
            boolean vanillaCanHit,
            boolean playerWindCharge,
            boolean activePromotedWindSpiritOwner,
            boolean targetIsOwner,
            boolean playerTarget,
            boolean targetAlive,
            boolean targetParticipating,
            boolean targetSpectator,
            boolean targetActiveWraith
    ) {
        if (!playerWindCharge || !activePromotedWindSpiritOwner || !playerTarget) {
            return vanillaCanHit;
        }
        return !targetIsOwner
                && targetAlive
                && targetParticipating
                && !targetSpectator
                && !targetActiveWraith;
    }

    public static boolean isWindSpirit(Role role) {
        return role != null && WindSpiritRole.ID.equals(role.identifier());
    }

    public static boolean isWindSpirit(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        return isWindSpirit(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    /**
     * Allows only the promoted Wind Spirit to cross Wathe gates that reject recorded-dead players.
     * 只允许晋升后的风精灵穿过 Wathe 对死亡记录玩家的统一门禁。
     */
    public static boolean isActivePromotedWindSpirit(PlayerEntity player) {
        if (!isWindSpirit(player)) {
            return false;
        }
        WraithPlayerComponent wraith = WraithPlayerComponent.KEY.get(player);
        return wraith.isActive() && wraith.isPromoted();
    }
}
