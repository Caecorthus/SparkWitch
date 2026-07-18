package dev.caecorthus.sparkwitch.roles.special.wraith.runtime;

import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.silencer.SilencedPlayerComponent;

/**
 * Owns the player properties and status effects that make an active Wraith present in the world.
 * 负责让激活冤魂存在于世界中的玩家属性与状态效果。
 */
public final class WraithPresence {
    private static final Identifier NO_COLLISION = Identifier.of("noellesroles", "no_collision");

    private WraithPresence() {
    }

    static void apply(ServerPlayerEntity player, boolean restricted) {
        player.setInvulnerable(!VendettaInteractionService.isActiveVendetta(player));
        SilencedPlayerComponent.KEY.get(player).reset();
        refresh(player, StatusEffects.INVISIBILITY, 0);
        refresh(player, noCollisionEffect(), 0);
        if (restricted) {
            refresh(player, StatusEffects.SLOWNESS, 1);
            refresh(player, StatusEffects.BLINDNESS, 0);
        }
    }

    static void removeRestrictedEffects(ServerPlayerEntity player) {
        player.removeStatusEffect(StatusEffects.SLOWNESS);
        player.removeStatusEffect(StatusEffects.BLINDNESS);
    }

    static void clear(ServerPlayerEntity player) {
        player.removeStatusEffect(StatusEffects.INVISIBILITY);
        removeRestrictedEffects(player);
        player.removeStatusEffect(noCollisionEffect());
        player.setInvulnerable(false);
    }

    /**
     * Identifies effects that external cleanup must preserve for the current Wraith phase.
     * 标识外部清理在当前冤魂阶段必须保留的效果。
     */
    public static boolean shouldPreserve(PlayerEntity player, RegistryEntry<StatusEffect> effect) {
        if (!WraithStateService.isActive(player)) {
            return false;
        }
        if (effect.matches(StatusEffects.INVISIBILITY) || effect.matchesId(NO_COLLISION)) {
            return true;
        }
        return WraithStateService.isRestricted(player)
                && (effect.matches(StatusEffects.SLOWNESS) || effect.matches(StatusEffects.BLINDNESS));
    }

    private static void refresh(ServerPlayerEntity player, RegistryEntry<StatusEffect> effect, int amplifier) {
        StatusEffectInstance current = player.getStatusEffect(effect);
        if (current == null || !current.isInfinite() || current.getAmplifier() != amplifier) {
            player.addStatusEffect(new StatusEffectInstance(
                    effect, StatusEffectInstance.INFINITE, amplifier, false, false, false));
        }
    }

    private static RegistryEntry<StatusEffect> noCollisionEffect() {
        return Registries.STATUS_EFFECT.getEntry(NO_COLLISION).orElseThrow(() ->
                new IllegalStateException("Missing NoellesRoles status effect: " + NO_COLLISION));
    }
}
