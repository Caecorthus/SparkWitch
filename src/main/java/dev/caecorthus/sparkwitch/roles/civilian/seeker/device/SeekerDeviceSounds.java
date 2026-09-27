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

    /**
     * Owner-only warning, called by the component's battery step (WP-02) once when the battery first reaches 20% and
     * once at 10% per deploy. This is the only battery warning sound; the client overlay never plays one.
     * 仅向拥有者播放的警告，由组件电量步骤（WP-02）在每次部署中电量首次降至 20% 与 10% 时各调用一次。
     * 这是唯一的电量警告音；客户端叠加层从不播放。
     */
    public static void playBatteryLow(ServerPlayerEntity owner) {
    }
}
