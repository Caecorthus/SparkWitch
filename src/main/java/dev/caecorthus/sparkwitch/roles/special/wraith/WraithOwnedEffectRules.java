package dev.caecorthus.sparkwitch.roles.special.wraith;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

/**
 * Identifies effects that external cleanup must preserve during each Wraith phase.
 * 标识外部清理在冤魂各阶段必须保留的自有状态效果。
 */
public final class WraithOwnedEffectRules {
    private static final Identifier NO_COLLISION = Identifier.of("noellesroles", "no_collision");

    private WraithOwnedEffectRules() {
    }

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
}
