package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;

/** Registers the hidden Focused Footsteps effect. / 注册聚焦步伐的隐藏效果。 */
public final class FocusedFootstepsEffects {
    private static RegistryEntry<StatusEffect> focusedFootsteps;
    private static boolean registered;

    private FocusedFootstepsEffects() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        focusedFootsteps = Registry.registerReference(
                Registries.STATUS_EFFECT,
                SparkWitch.id("focused_footsteps"),
                new FocusedFootstepsEffect()
        );
        registered = true;
    }

    public static RegistryEntry<StatusEffect> focusedFootsteps() {
        if (focusedFootsteps == null) {
            throw new IllegalStateException("Focused Footsteps effect is not registered yet");
        }
        return focusedFootsteps;
    }
}
