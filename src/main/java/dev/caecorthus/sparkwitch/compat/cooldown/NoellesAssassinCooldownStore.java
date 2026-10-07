package dev.caecorthus.sparkwitch.compat.cooldown;

import dev.caecorthus.sparkfactionapi.api.cooldown.RoleSkillCooldownStore;
import dev.caecorthus.sparkwitch.compat.NoellesRoleIds;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.assassin.AssassinPlayerComponent;

import java.util.OptionalInt;
import java.util.Set;

/**
 * NoellesRoles Assassin cooldown (pinned 1.7.6: {@code AssassinPlayerComponent.getCooldownTicks}, and
 * {@code setCooldown}, which writes exactly and syncs).
 * NoellesRoles 刺客冷却（锁定 1.7.6：getCooldownTicks 与精确写入并同步的 setCooldown）。
 */
final class NoellesAssassinCooldownStore implements RoleSkillCooldownStore {
    static final Identifier ID = Identifier.of(NoellesRoleIds.NAMESPACE, "assassin");
    static final Identifier ROLE_ID = Identifier.of(NoellesRoleIds.NAMESPACE, "assassin");

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
        return AssassinPlayerComponent.KEY.get(player).getCooldownTicks();
    }

    @Override
    public OptionalInt nominalTicks(ServerPlayerEntity player) {
        return OptionalInt.of(AssassinPlayerComponent.COOLDOWN_TICKS);
    }

    @Override
    public void setRemainingTicks(ServerPlayerEntity player, int ticks) {
        AssassinPlayerComponent.KEY.get(player).setCooldown(Math.max(0, ticks));
    }
}
