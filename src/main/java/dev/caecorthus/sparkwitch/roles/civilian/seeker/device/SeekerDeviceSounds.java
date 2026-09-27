package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server playback helpers for {@code SeekerSounds}: motor (throttled, fixed range), break, swallow crunch, power-down.
 * TODO(WP-03): implement. / 待 WP-03 实现。
 * {@code SeekerSounds} 的服务端播放辅助：电机声（节流、固定范围）、损坏声、吞噬声、断电声。
 */
public final class SeekerDeviceSounds {
    private SeekerDeviceSounds() {
    }

    public static void playMotor(SeekerCarEntity car) {
    }

    public static void playBreak(SeekerDeviceEntity device) {
    }

    public static void playSwallow(SeekerCarEntity car) {
    }

    public static void playBatteryDead(SeekerCarEntity car) {
    }

    /** Owner-only warning at the battery thresholds. / 电量阈值时仅向拥有者播放的警告。 */
    public static void playBatteryLow(ServerPlayerEntity owner) {
    }
}
