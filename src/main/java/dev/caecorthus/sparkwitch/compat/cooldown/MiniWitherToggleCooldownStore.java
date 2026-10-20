package dev.caecorthus.sparkwitch.compat.cooldown;

import dev.caecorthus.sparkfactionapi.api.cooldown.RoleSkillCooldownStore;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.WitchMaidenRules;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import java.util.OptionalInt;

/**
 * The Witch Maiden's Mini Wither toggle cooldown (owner 2026-10-20), kept in the owner-synced
 * {@code sparkwitch:mini_wither} component and separate from Focused Footsteps. The nominal is the 10 s cooldown after
 * each summon or dismiss. The crimson skull is an item cooldown and goes through the item nominal table instead.
 * 巫女小凋灵的切换冷却（所有者 2026-10-20），保存在只同步给本人的 {@code sparkwitch:mini_wither} 组件中，与聚焦步伐分开。
 * 标准冷却为每次召唤或收回后的 10 秒。赤首属于物品冷却，改由物品标准冷却表处理。
 */
final class MiniWitherToggleCooldownStore implements RoleSkillCooldownStore {
    static final Identifier ID = SparkWitch.id("mini_wither_toggle");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public boolean appliesTo(ServerPlayerEntity player) {
        // An ACTIVE round, the exact role, and the unlock: the toggle exists only after two tasks.
        // ACTIVE 回合、精确职业并要求已解锁：切换只在完成两个任务后存在。
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && WitchMaidenRules.isWitchMaiden(game.getRole(player))
                && MiniWitherPlayerComponent.KEY.get(player).isUnlocked();
    }

    @Override
    public int remainingTicks(ServerPlayerEntity player) {
        return MiniWitherPlayerComponent.KEY.get(player).getToggleCooldownTicks();
    }

    @Override
    public OptionalInt nominalTicks(ServerPlayerEntity player) {
        return OptionalInt.of(MiniWitherRules.TOGGLE_COOLDOWN_TICKS);
    }

    @Override
    public void setRemainingTicks(ServerPlayerEntity player, int ticks) {
        MiniWitherPlayerComponent.KEY.get(player).setToggleCooldown(ticks);
    }
}
