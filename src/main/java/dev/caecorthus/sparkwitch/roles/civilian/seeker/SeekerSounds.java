package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Registers the Seeker sound events; every sounds.json entry points at an existing vanilla file (no new audio). The
 * motor uses a fixed broadcast range so only players about {@link SeekerRules#MOTOR_SOUND_RANGE} blocks away hear it.
 * Playback helpers live in {@code device/SeekerDeviceSounds}.
 * 注册搜寻者音效事件；sounds.json 中每个条目都指向现有的原版音频（不新增音频）。电机声使用固定广播范围，
 * 只有约 {@link SeekerRules#MOTOR_SOUND_RANGE} 格内的玩家能听到。播放辅助方法位于 {@code device/SeekerDeviceSounds}。
 */
public final class SeekerSounds {
    public static SoundEvent CAR_MOTOR;
    public static SoundEvent CAR_BREAK;
    public static SoundEvent CAMERA_BREAK;
    public static SoundEvent CAR_SWALLOW;
    public static SoundEvent BATTERY_LOW;
    public static SoundEvent BATTERY_DEAD;
    private static boolean registered;

    private SeekerSounds() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CAR_MOTOR = Registry.register(Registries.SOUND_EVENT, SeekerRules.CAR_MOTOR_SOUND_ID,
                SoundEvent.of(SeekerRules.CAR_MOTOR_SOUND_ID, SeekerRules.MOTOR_SOUND_RANGE));
        CAR_BREAK = variableRange(SeekerRules.CAR_BREAK_SOUND_ID);
        CAMERA_BREAK = variableRange(SeekerRules.CAMERA_BREAK_SOUND_ID);
        CAR_SWALLOW = variableRange(SeekerRules.CAR_SWALLOW_SOUND_ID);
        BATTERY_LOW = variableRange(SeekerRules.BATTERY_LOW_SOUND_ID);
        BATTERY_DEAD = variableRange(SeekerRules.BATTERY_DEAD_SOUND_ID);
    }

    private static SoundEvent variableRange(Identifier id) {
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
}
