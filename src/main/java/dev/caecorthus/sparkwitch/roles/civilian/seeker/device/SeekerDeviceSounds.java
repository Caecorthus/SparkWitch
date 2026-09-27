package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerDeviceKind;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSounds;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
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
        if (owner == null || owner.getWorld().isClient() || SeekerSounds.BATTERY_LOW == null) {
            return;
        }
        // The sound is positional: while a session moves the owner's camera (and so the sound listener) to the car or
        // camera, play it there, or a far-away owner would never hear it. Sent to the owner only.
        // 该声音是定位音：会话把拥有者的镜头（也就是声音监听点）移到小车或摄像头时，就在那里播放，否则远处的拥有者听不到。
        // 只发送给拥有者。
        Entity listener = sessionListener(owner);
        owner.networkHandler.sendPacket(new PlaySoundS2CPacket(Registries.SOUND_EVENT.getEntry(SeekerSounds.BATTERY_LOW),
                SoundCategory.PLAYERS, listener.getX(), listener.getY(), listener.getZ(), BATTERY_LOW_VOLUME,
                BATTERY_LOW_PITCH, owner.getRandom().nextLong()));
    }

    /**
     * Where the owner's client listens from: the focused car or camera during a matching session, else the body.
     * 拥有者客户端的收听位置：会话中为聚焦的小车或摄像头，否则为本体。
     */
    static Entity sessionListener(ServerPlayerEntity owner) {
        SeekerStatusComponent status = SeekerStatusComponent.KEY.getNullable(owner);
        SeekerSessionMode mode = status == null ? SeekerSessionMode.NONE : status.sessionMode();
        Entity focus = switch (mode) {
            case CAR -> SeekerDeviceService.findCar(owner);
            case CAMERA -> SeekerDeviceService.findCamera(owner);
            default -> null;
        };
        return focus != null && !focus.isRemoved() && focus.getWorld() == owner.getWorld() ? focus : owner;
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
