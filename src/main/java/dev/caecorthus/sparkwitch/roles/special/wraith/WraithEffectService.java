package dev.caecorthus.sparkwitch.roles.special.wraith;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Applies and removes only persistent status effects owned by Wraith. */
final class WraithEffectService {
    private static final Identifier NO_COLLISION = Identifier.of("noellesroles", "no_collision");

    private WraithEffectService() {
    }

    static void apply(ServerPlayerEntity player, boolean restricted) {
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

    static void removeAll(ServerPlayerEntity player) {
        player.removeStatusEffect(StatusEffects.INVISIBILITY);
        removeRestrictedEffects(player);
        player.removeStatusEffect(noCollisionEffect());
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
