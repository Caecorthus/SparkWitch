package dev.caecorthus.sparkwitch.compat.cooldown;

import dev.caecorthus.sparkfactionapi.api.cooldown.RoleSkillCooldownStore;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.OptionalInt;

/**
 * The Black Raven's Raven Form cooldown (plan 2026-10-21 §3.6), kept in the owner-synced
 * {@code sparkwitch:black_raven_crow} component. The nominal is the 45 s cooldown that starts when a session ends. A
 * forced raise during a session keeps running and the session end only ever lengthens it. Raven Form has no item, so
 * SparkFactionAPI's admin {@code clearCooldown} (held-item only) does not reach it, like the Mini Wither toggle.
 * 黑羽鸦化鸦冷却（2026-10-21 计划 §3.6），保存在只同步给本人的 {@code sparkwitch:black_raven_crow} 组件中。标准冷却为会话结束后
 * 开始的 45 秒。会话期间被强制提高的冷却照常倒数，会话结束只会延长它。化鸦没有物品，因此 SparkFactionAPI 的管理员
 * {@code clearCooldown}（只清手持物品）触及不到它，与小凋灵切换相同。
 */
final class BlackRavenCrowCooldownStore implements RoleSkillCooldownStore {
    static final Identifier ID = BlackRavenCrowRules.SKILL_ID;

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public boolean appliesTo(ServerPlayerEntity player) {
        // An ACTIVE round, the RAW role (never the disguise overlay) and the two-task unlock.
        // ACTIVE 回合、原始职业（绝不使用伪装覆盖层）并要求已完成两个任务解锁。
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && BlackRavenRules.isBlackRaven(game.getRole(player))
                && BlackRavenCrowPlayerComponent.KEY.get(player).isUnlocked();
    }

    @Override
    public int remainingTicks(ServerPlayerEntity player) {
        return BlackRavenCrowPlayerComponent.KEY.get(player).getCooldownTicks();
    }

    @Override
    public OptionalInt nominalTicks(ServerPlayerEntity player) {
        return OptionalInt.of(BlackRavenCrowRules.COOLDOWN_TICKS);
    }

    @Override
    public void setRemainingTicks(ServerPlayerEntity player, int ticks) {
        BlackRavenCrowPlayerComponent.KEY.get(player).setCooldown(ticks);
    }
}
