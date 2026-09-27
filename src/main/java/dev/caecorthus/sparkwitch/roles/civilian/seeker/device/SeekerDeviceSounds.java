package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerDeviceKind;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSounds;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;

/**
 * Server playback helpers for {@code SeekerSounds}: motor (throttled by the car, fixed range), break, swallow crunch,
 * power-down, and the owner-only battery warning. Every call is a server-side no-op on the client, so a stray client
 * call can never play a sound only one player hears.
 * {@code SeekerSounds} 的服务端播放辅助：电机声（由小车节流、固定范围）、损坏声、吞噬声、断电声，以及仅拥有者可听的
 * 电量警告。客户端上的调用一律为空操作，因此误调用不会产生只有单个玩家听到的声音。
 */
public final class SeekerDeviceSounds {
    static final float BREAK_VOLUME = 0.7F;
    static final float SWALLOW_VOLUME = 0.8F;
    static final float SWALLOW_PITCH = 0.8F;
    static final float BATTERY_DEAD_VOLUME = 0.6F;
    static final float BATTERY_LOW_VOLUME = 0.8F;
    static final float BATTERY_LOW_PITCH = 1.6F;

    private SeekerDeviceSounds() {
    }

    /**
     * Quiet motor at the car, audible within the fixed {@link SeekerRules#MOTOR_SOUND_RANGE}; the car throttles calls.
     * 小车位置的轻微电机声，在固定范围内可听；由小车负责节流。
     */
    public static void playMotor(SeekerCarEntity car) {
        play(car, SeekerSounds.CAR_MOTOR, SeekerRules.MOTOR_SOUND_VOLUME, 0.95F + car.getRandom().nextFloat() * 0.1F);
    }

    public static void playBreak(SeekerDeviceEntity device) {
        SoundEvent sound = device.kind() == SeekerDeviceKind.CAR ? SeekerSounds.CAR_BREAK : SeekerSounds.CAMERA_BREAK;
        play(device, sound, BREAK_VOLUME, 0.9F + device.getRandom().nextFloat() * 0.2F);
    }

    public static void playSwallow(SeekerCarEntity car) {
        play(car, SeekerSounds.CAR_SWALLOW, SWALLOW_VOLUME, SWALLOW_PITCH);
    }

    public static void playBatteryDead(SeekerCarEntity car) {
        play(car, SeekerSounds.BATTERY_DEAD, BATTERY_DEAD_VOLUME, 1.0F);
    }

    /**
     * Owner-only warning, called by the component's battery step (WP-02) once when the battery first reaches 20% and
     * once at 10% per deploy. This is the only battery warning sound; the client overlay never plays one.
     * 仅向拥有者播放的警告，由组件电量步骤（WP-02）在每次部署中电量首次降至 20% 与 10% 时各调用一次。
     * 这是唯一的电量警告音；客户端叠加层从不播放。
     */
    public static void playBatteryLow(ServerPlayerEntity owner) {
        if (owner == null || SeekerSounds.BATTERY_LOW == null) {
            return;
        }
        owner.playSoundToPlayer(SeekerSounds.BATTERY_LOW, SoundCategory.PLAYERS, BATTERY_LOW_VOLUME,
                BATTERY_LOW_PITCH);
    }

    private static void play(Entity source, SoundEvent sound, float volume, float pitch) {
        World world = source.getWorld();
        if (sound == null || world.isClient()) {
            return;
        }
        // Null "except" player: everyone in range hears it, including the owner's body if it is close.
        // except 为 null：范围内所有人都能听到，包括距离够近的拥有者本体。
        world.playSound(null, source.getX(), source.getY(), source.getZ(), sound, SoundCategory.NEUTRAL, volume,
                pitch);
    }
}
