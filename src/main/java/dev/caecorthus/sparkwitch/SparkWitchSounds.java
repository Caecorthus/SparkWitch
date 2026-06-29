package dev.caecorthus.sparkwitch;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class SparkWitchSounds {
    public static final Identifier PIG_CHASE_ID = SparkWitch.id("skill.pig_chase");
    public static SoundEvent PIG_CHASE;
    private static boolean registered;

    private SparkWitchSounds() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PIG_CHASE = Registry.register(Registries.SOUND_EVENT, PIG_CHASE_ID, SoundEvent.of(PIG_CHASE_ID));
    }
}
