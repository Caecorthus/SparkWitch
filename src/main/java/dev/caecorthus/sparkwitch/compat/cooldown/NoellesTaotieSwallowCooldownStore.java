package dev.caecorthus.sparkwitch.compat.cooldown;

import dev.caecorthus.sparkfactionapi.api.cooldown.RoleSkillCooldownStore;
import dev.caecorthus.sparkwitch.compat.NoellesRoleIds;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.taotie.TaotiePlayerComponent;

import java.util.OptionalInt;
import java.util.Set;

/**
 * NoellesRoles Taotie swallow cooldown (pinned 1.7.6: {@code getSwallowCooldown}, and {@code setSwallowCooldown},
 * which writes exactly and syncs). The nominal is unknown: the real post-swallow value is the private, per-round
 * {@code calculatedSwallowCooldown}; the public {@code SWALLOW_COOLDOWN} is only its pre-round default.
 * NoellesRoles 饕餮吞噬冷却（锁定 1.7.6：getSwallowCooldown 与精确写入并同步的 setSwallowCooldown）。标准冷却未知：
 * 实际吞噬后的值是私有且按回合计算的 calculatedSwallowCooldown，公开的 SWALLOW_COOLDOWN 只是开局前的默认值。
 */
final class NoellesTaotieSwallowCooldownStore implements RoleSkillCooldownStore {
    static final Identifier ID = Identifier.of(NoellesRoleIds.NAMESPACE, "taotie_swallow");
    static final Identifier ROLE_ID = Identifier.of(NoellesRoleIds.NAMESPACE, "taotie");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public boolean appliesTo(ServerPlayerEntity player) {
        return ForcedCooldownRoles.selfRoleIn(player, Set.of(ROLE_ID)) != null;
    }

    @Override
    public int remainingTicks(ServerPlayerEntity player) {
        return TaotiePlayerComponent.KEY.get(player).getSwallowCooldown();
    }

    @Override
    public OptionalInt nominalTicks(ServerPlayerEntity player) {
        return OptionalInt.empty();
    }

    @Override
    public void setRemainingTicks(ServerPlayerEntity player, int ticks) {
        TaotiePlayerComponent.KEY.get(player).setSwallowCooldown(Math.max(0, ticks));
    }
}
