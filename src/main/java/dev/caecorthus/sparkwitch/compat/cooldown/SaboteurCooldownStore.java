package dev.caecorthus.sparkwitch.compat.cooldown;

import dev.caecorthus.sparkfactionapi.api.cooldown.RoleSkillCooldownStore;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurRules;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.OptionalInt;

/**
 * The promoted Saboteur's Sabotage cooldown; its public setter is exact and syncs to the owner.
 * 晋升破坏者的破坏技能冷却；其公开 setter 为精确写入并同步给本人。
 */
final class SaboteurCooldownStore implements RoleSkillCooldownStore {
    static final Identifier ID = SparkWitch.id("saboteur");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public boolean appliesTo(ServerPlayerEntity player) {
        // Same gate as SaboteurAbilityService. / 与 SaboteurAbilityService 相同的门控。
        return SaboteurRules.isActivePromotedSaboteur(player);
    }

    @Override
    public int remainingTicks(ServerPlayerEntity player) {
        return SaboteurPlayerComponent.KEY.get(player).getCooldownTicks();
    }

    @Override
    public OptionalInt nominalTicks(ServerPlayerEntity player) {
        return OptionalInt.of(SaboteurRules.COOLDOWN_TICKS);
    }

    @Override
    public void setRemainingTicks(ServerPlayerEntity player, int ticks) {
        SaboteurPlayerComponent.KEY.get(player).setCooldownTicks(ticks);
    }
}
