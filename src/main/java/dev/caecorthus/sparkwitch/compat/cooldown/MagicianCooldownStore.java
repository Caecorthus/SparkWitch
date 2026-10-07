package dev.caecorthus.sparkwitch.compat.cooldown;

import dev.caecorthus.sparkfactionapi.api.cooldown.RoleSkillCooldownStore;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianConstants;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlayerComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.OptionalInt;

/**
 * The Magician's ability cooldown, kept as a counting-down tick total in the owner-synced {@code sparkwitch:magician}
 * component. It gates starting a recording and starting a playback; a recording or playback already running is never
 * interrupted. The nominal is the 15 s playback cooldown after each use. Every write goes through
 * {@code raiseCooldownTicks}, which only lengthens and resyncs, and the playback cooldown never shortens a raised value.
 * 魔术师技能冷却，以倒计时刻数保存在只同步给本人的 {@code sparkwitch:magician} 组件中。它阻止开始录制与开始播放；
 * 进行中的录制或播放不会被打断。标准冷却为每次使用后的 15 秒播放冷却。每次写入都经 {@code raiseCooldownTicks}，
 * 只会延长并重新同步，播放冷却也绝不缩短已抬高的值。
 */
final class MagicianCooldownStore implements RoleSkillCooldownStore {
    static final Identifier ID = SparkWitch.id("magician");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public boolean appliesTo(ServerPlayerEntity player) {
        // An ACTIVE round and MagicianAbility's role gate (by id, like the ability handler).
        // ACTIVE 回合，且与 MagicianAbility 一致按 id 判断职业。
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        var role = game.getRole(player);
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && role != null && SparkWitchRoles.MAGICIAN_ID.equals(role.identifier());
    }

    @Override
    public int remainingTicks(ServerPlayerEntity player) {
        return MagicianPlayerComponent.KEY.get(player).cooldownTicks();
    }

    @Override
    public OptionalInt nominalTicks(ServerPlayerEntity player) {
        return OptionalInt.of(MagicianConstants.PLAYBACK_COOLDOWN_TICKS);
    }

    @Override
    public void setRemainingTicks(ServerPlayerEntity player, int ticks) {
        MagicianPlayerComponent.KEY.get(player).raiseCooldownTicks(Math.max(0, ticks));
    }
}
