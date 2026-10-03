package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Registers the Potion Gunner shell entity; must run before SparkWitchItems so item code can capture the type.
 * 注册药炮手炮弹实体；必须先于 SparkWitchItems 执行，以便物品代码可以获取该实体类型。
 */
public final class PotionGunnerEntities {
    private static EntityType<PotionShellEntity> potionShell;
    private static boolean registered;

    private PotionGunnerEntities() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        potionShell = Registry.register(
                Registries.ENTITY_TYPE,
                PotionGunnerRules.SHELL_ENTITY_ID,
                EntityType.Builder.<PotionShellEntity>create(PotionShellEntity::new, SpawnGroup.MISC)
                        .dimensions(0.25F, 0.25F)
                        .maxTrackingRange(8)
                        .trackingTickInterval(10)
                        .build(PotionGunnerRules.SHELL_ENTITY_ID.toString())
        );
        registered = true;
    }

    public static EntityType<PotionShellEntity> potionShell() {
        if (potionShell == null) {
            throw new IllegalStateException("Potion Gunner entities are not registered yet");
        }
        return potionShell;
    }
}
