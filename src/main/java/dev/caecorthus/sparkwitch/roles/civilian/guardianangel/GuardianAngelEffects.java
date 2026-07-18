package dev.caecorthus.sparkwitch.roles.civilian.guardianangel;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;

/** Registers the role-owned hidden Guardian Shield effect. / 注册该职业持有的隐藏守护护盾效果。 */
public final class GuardianAngelEffects {
    private static RegistryEntry<StatusEffect> guardianShield;
    private static boolean registered;

    private GuardianAngelEffects() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        guardianShield = Registry.registerReference(
                Registries.STATUS_EFFECT,
                SparkWitch.id("guardian_shield"),
                new GuardianShieldEffect()
        );
        registered = true;
    }

    public static RegistryEntry<StatusEffect> guardianShield() {
        if (guardianShield == null) {
            throw new IllegalStateException("Guardian Angel effects are not registered yet");
        }
        return guardianShield;
    }
}
