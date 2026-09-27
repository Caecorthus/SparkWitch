package dev.caecorthus.sparkwitch.client.seeker.remote;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCarEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerCarCorrectS2CPacket;

/**
 * Owner-client car simulation from raw KeyBinding state (never ticks a second KeyboardInput); installs SeekerCarMovement's client drive hook.
 * TODO(WP-10a): implement. / 待 WP-10a 实现。
 * 基于 KeyBinding 原始状态的拥有者客户端小车模拟（绝不再 tick 第二个 KeyboardInput）；安装 SeekerCarMovement 的客户端驾驶钩子。
 */
public final class SeekerCarClientDriver {
    private SeekerCarClientDriver() {
    }

    public static void onCorrection(SeekerCarCorrectS2CPacket packet) {
    }

    public static boolean isLocallyDriven(SeekerCarEntity car) {
        return false;
    }
}
