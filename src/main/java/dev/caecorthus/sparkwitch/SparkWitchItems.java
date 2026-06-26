package dev.caecorthus.sparkwitch;

import dev.caecorthus.sparkwitch.item.RitualSwordItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class SparkWitchItems {
    public static final Identifier RITUAL_SWORD_ID = SparkWitch.id("ritual_sword");
    private static Item ritualSword;

    private static boolean registered;

    private SparkWitchItems() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        ritualSword = Registry.register(
                Registries.ITEM,
                RITUAL_SWORD_ID,
                new RitualSwordItem(new Item.Settings().maxCount(1))
        );
        registered = true;
    }

    public static Item ritualSword() {
        if (ritualSword == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return ritualSword;
    }
}
