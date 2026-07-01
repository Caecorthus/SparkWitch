package dev.caecorthus.sparkwitch;

import dev.caecorthus.sparkwitch.item.CeremonialSwordItem;
import dev.caecorthus.sparkwitch.item.CapsuleItem;
import dev.caecorthus.sparkwitch.item.FlashlightItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class SparkWitchItems {
    public static final Identifier CEREMONIAL_SWORD_ID = SparkWitch.id("ceremonial_sword");
    public static final Identifier FIRE_POKER_ID = SparkWitch.id("fire_poker");
    public static final Identifier CAPSULE_ID = SparkWitch.id("capsule");
    public static final Identifier FLASHLIGHT_ID = SparkWitch.id("flashlight");
    private static Item ceremonialSword;
    private static Item firePoker;
    private static Item capsule;
    private static Item flashlight;

    private static boolean registered;

    private SparkWitchItems() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        ceremonialSword = Registry.register(
                Registries.ITEM,
                CEREMONIAL_SWORD_ID,
                new CeremonialSwordItem(CeremonialSwordItem.createSettings())
        );
        firePoker = Registry.register(
                Registries.ITEM,
                FIRE_POKER_ID,
                new Item(new Item.Settings().maxCount(1))
        );
        capsule = Registry.register(
                Registries.ITEM,
                CAPSULE_ID,
                new CapsuleItem(new Item.Settings().maxCount(1))
        );
        flashlight = Registry.register(
                Registries.ITEM,
                FLASHLIGHT_ID,
                new FlashlightItem(new Item.Settings().maxCount(1))
        );
        registered = true;
    }

    public static Item ceremonialSword() {
        if (ceremonialSword == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return ceremonialSword;
    }

    public static Item firePoker() {
        if (firePoker == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return firePoker;
    }

    public static Item capsule() {
        if (capsule == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return capsule;
    }

    public static Item flashlight() {
        if (flashlight == null) {
            throw new IllegalStateException("SparkWitch items are not registered yet");
        }
        return flashlight;
    }
}
