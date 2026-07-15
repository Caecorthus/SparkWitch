package dev.caecorthus.sparkwitch.roles.civilian.orthopedist;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;

/** Registers the Orthopedist-owned Bone Setting effect. / 注册由骨科大夫模块持有的正骨效果。 */
public final class OrthopedistEffects {
    private static RegistryEntry<StatusEffect> boneSetting;
    private static boolean registered;

    private OrthopedistEffects() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        boneSetting = Registry.registerReference(
                Registries.STATUS_EFFECT,
                SparkWitch.id("bone_setting"),
                new BoneSettingEffect()
        );
        registered = true;
    }

    public static RegistryEntry<StatusEffect> boneSetting() {
        if (boneSetting == null) {
            throw new IllegalStateException("Orthopedist effects are not registered yet");
        }
        return boneSetting;
    }
}
