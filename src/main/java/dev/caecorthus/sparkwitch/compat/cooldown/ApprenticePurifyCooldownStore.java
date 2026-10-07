package dev.caecorthus.sparkwitch.compat.cooldown;

import dev.caecorthus.sparkfactionapi.api.cooldown.RoleSkillCooldownStore;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.ApprenticePlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Purify.PurifyAbility;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.OptionalInt;

/**
 * The graduated Apprentice's Purify cooldown (owner decision 2026-10-06 D3), kept in the owner-synced
 * {@code sparkwitch:apprentice_player} component and separate from her shared witch skill cooldown. The nominal is the
 * 20 s cooldown after each use. Every write goes through {@code raisePurifyCooldown}, which only lengthens and resyncs.
 * 出师后预备魔女的净化冷却（所有者 2026-10-06 决定 D3），保存在只同步给本人的 {@code sparkwitch:apprentice_player} 组件中，
 * 与她共享的魔女技能冷却分开。标准冷却为每次使用后的 20 秒。每次写入都经 {@code raisePurifyCooldown}，只会延长并重新同步。
 */
final class ApprenticePurifyCooldownStore implements RoleSkillCooldownStore {
    static final Identifier ID = SparkWitch.id("apprentice_purify");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public boolean appliesTo(ServerPlayerEntity player) {
        // An ACTIVE round, PurifyAbility's role gate, and graduation: Purify exists only once she has graduated.
        // ACTIVE 回合、与 PurifyAbility 一致的职业门控，并要求已出师：净化只在出师后存在。
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && game.getRole(player) == SparkWitchRoles.apprenticeWitch()
                && ApprenticePlayerComponent.KEY.get(player).isGraduated();
    }

    @Override
    public int remainingTicks(ServerPlayerEntity player) {
        return ApprenticePlayerComponent.KEY.get(player).getPurifyCooldownTicks();
    }

    @Override
    public OptionalInt nominalTicks(ServerPlayerEntity player) {
        return OptionalInt.of(PurifyAbility.COOLDOWN_TICKS);
    }

    @Override
    public void setRemainingTicks(ServerPlayerEntity player, int ticks) {
        ApprenticePlayerComponent.KEY.get(player).raisePurifyCooldown(Math.max(0, ticks));
    }
}
