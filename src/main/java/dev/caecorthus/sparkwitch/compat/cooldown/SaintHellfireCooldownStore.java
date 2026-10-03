package dev.caecorthus.sparkwitch.compat.cooldown;

import dev.caecorthus.sparkfactionapi.api.cooldown.RoleSkillCooldownStore;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintPlayerState;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.OptionalInt;

/**
 * Saint's Hellfire cooldown, kept in {@link SaintPlayerState} inside the shared player component. It is never forced
 * while Hellfire burns, because the burn's end writes the post cooldown.
 * 圣徒业火冷却，存放在共享玩家组件内的 SaintPlayerState 中。业火燃烧期间不强制，因为燃烧结束会写入其后冷却。
 */
final class SaintHellfireCooldownStore implements RoleSkillCooldownStore {
    static final Identifier ID = SparkWitch.id("saint_hellfire");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public boolean appliesTo(ServerPlayerEntity player) {
        // Same raw role read as SaintAbilityService. / 与 SaintAbilityService 相同的真实职业读取。
        return SaintRules.isSaint(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    @Override
    public int remainingTicks(ServerPlayerEntity player) {
        return state(player).hellfireCooldownTicks();
    }

    @Override
    public OptionalInt nominalTicks(ServerPlayerEntity player) {
        return OptionalInt.of(SaintRules.HELLFIRE_POST_COOLDOWN_TICKS);
    }

    @Override
    public void setRemainingTicks(ServerPlayerEntity player, int ticks) {
        raise(player, ticks);
    }

    @Override
    public boolean mayForce(ServerPlayerEntity player) {
        return ForcedCooldownMath.mayForceSaintHellfire(state(player).isHellfireActive());
    }

    @Override
    public boolean raiseTo(ServerPlayerEntity player, int ticks) {
        return raise(player, ticks);
    }

    @Override
    public boolean extendBy(ServerPlayerEntity player, int ticks) {
        return ticks > 0 && raise(player, ForcedCooldownMath.saturatingAdd(remainingTicks(player), ticks));
    }

    private static boolean raise(ServerPlayerEntity player, int ticks) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        if (!component.getSaintState().raiseHellfireCooldown(ticks)) {
            return false;
        }
        component.sync();
        return true;
    }

    private static SaintPlayerState state(ServerPlayerEntity player) {
        return WitchPlayerComponent.KEY.get(player).getSaintState();
    }
}
