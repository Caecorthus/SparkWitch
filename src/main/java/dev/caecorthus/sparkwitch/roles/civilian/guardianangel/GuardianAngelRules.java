package dev.caecorthus.sparkwitch.roles.civilian.guardianangel;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/** Pure Guardian Angel tuning and server-validation decisions. / 守护天使的纯数值与服务端校验决策。 */
public final class GuardianAngelRules {
    public static final Identifier ROLE_ID = Identifier.of("sparkwitch", "guardian_angel");
    public static final Identifier SKILL_ID = Identifier.of("sparkwitch", "guardian");
    public static final int COLOR = 0xF0D77A;
    public static final int INITIAL_COOLDOWN_TICKS = 20 * 60;
    public static final int POST_USE_COOLDOWN_TICKS = 20 * 90;
    public static final int SHIELD_DURATION_TICKS = 20 * 10;
    public static final double TARGET_RANGE = 3.0D;
    public static final double TARGET_RANGE_SQUARED = TARGET_RANGE * TARGET_RANGE;

    private static final Identifier ASSASSINATED = Identifier.of("noellesroles", "assassinated");
    private static final Identifier VOODOO = Identifier.of("noellesroles", "voodoo");

    private GuardianAngelRules() {
    }

    public static boolean isGuardianAngel(Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    public static boolean isGuardianAngel(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        return isGuardianAngel(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    public static boolean canUse(
            boolean gameRunning,
            boolean activeWraith,
            boolean promotedWraith,
            Role role,
            int cooldownTicks
    ) {
        return gameRunning
                && activeWraith
                && promotedWraith
                && isGuardianAngel(role)
                && cooldownTicks <= 0;
    }

    public static boolean blocksBlackout(
            boolean gameRunning,
            boolean activeWraith,
            boolean promotedWraith,
            boolean guardianAngel
    ) {
        return gameRunning && activeWraith && promotedWraith && guardianAngel;
    }

    /**
     * Shared by the server aim, the server validator and the client HUD preview. A spectator is never a target: a Rift
     * Gate occupant is an ALIVE spectator (Riftwalker D3), and a shield there would block the poison death D3 allows.
     * 服务端准星、服务端校验与客户端 HUD 预览共用。旁观者永远不是目标：裂隙门内的玩家是存活旁观者（隙行者 D3），
     * 给它护盾还会挡下 D3 允许的毒杀。
     */
    public static boolean canTarget(
            boolean self,
            boolean targetAlive,
            boolean targetSpectator,
            boolean targetAlreadyShielded,
            boolean hasLineOfSight,
            double squaredDistance
    ) {
        return !self
                && targetAlive
                && !targetSpectator
                && !targetAlreadyShielded
                && hasLineOfSight
                && squaredDistance <= TARGET_RANGE_SQUARED;
    }

    /**
     * Mirrors NoellesRoles Iron Man exclusions without loading its implementation classes, plus the three Knight duel
     * deaths (owner 2026-11). Those are forced kills: Wathe asks {@code KillPlayer.BEFORE} for them but ignores the
     * cancel, so the shield could never stop one and was only spent on it, with its armour sound and its replay line,
     * for a player who died anyway. Excluded, the shield is left alone. Wathe's own forced deaths (mental breakdown,
     * the ESCAPED disconnect) keep today's behaviour by the same owner decision.
     * 不加载其实现类并复刻铁人药剂的排除项，另加三种骑士决斗死亡（所有者 2026-11）。它们是强制击杀：Wathe 会为其询问
     * {@code KillPlayer.BEFORE} 但无视取消，因此护盾永远拦不住，只会被白白消耗，并为一名终究会死的玩家播放护甲音效、写入回放
     * 记录。排除之后护盾保持不动。Wathe 自身的强制死亡（精神崩溃、ESCAPED 断线）按同一所有者决定保持现状。
     */
    public static boolean shouldBlockDeath(Identifier deathReason) {
        return !GameConstants.DeathReasons.SHOT_INNOCENT.equals(deathReason)
                && !GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(deathReason)
                && !ASSASSINATED.equals(deathReason)
                && !VOODOO.equals(deathReason)
                && !SparkWitchDeathReasons.DUEL_SUICIDE.equals(deathReason)
                && !SparkWitchDeathReasons.DUEL_FAILED.equals(deathReason)
                && !SparkWitchDeathReasons.DUEL_INTERFERENCE.equals(deathReason);
    }
}
