package dev.caecorthus.sparkwitch.client.blind.gate;

import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Client wiring of the Blind's presentation gates. The render, outline, HUD, hand and sound vetoes are mixins in
 * {@code client/mixin/blind/BlindGate*} that ask {@link BlindClientGates} / {@link BlindAttuneDucking} per call; this
 * class installs the public {@code SparkWitchApi} feature gate and drives the per-tick parts (bumps, the Attune ducking
 * flag). Both re-check {@code BlindView.isActive} every
 * tick, so death, a role change, round end and disconnect switch them off without a listener. Registered by
 * {@code BlindClient} after its own tick, so a view flip has already reset the perception state when a bump adds to it.
 * 盲人展示闸门的客户端接线。渲染、描边、HUD、手部与声音否决由 {@code client/mixin/blind/BlindGate*} 中的 mixin
 * 在每次调用时询问 {@link BlindClientGates} / {@link BlindAttuneDucking}；本类安装公开的 {@code SparkWitchApi} 附加层闸门，
 * 并驱动按刻执行的部分（碰撞、凝神降音标记）。
 * 两者每刻都重新检查 {@code BlindView.isActive}，因此死亡、职业变化、回合结束与断开连接都会自动关闭它们。由
 * {@code BlindClient} 在其自身 tick 之后注册，因此视图翻转时感知状态已先被清空，碰撞才写入。
 */
public final class BlindClientGateWiring {
    private static boolean registered;

    private BlindClientGateWiring() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // Public seam for feature-like extras that other mods draw outside the feature loop (see the facade method).
        // 供其他模组在附加层循环之外绘制的类附加层物件使用的公开接口（见门面方法说明）。
        SparkWitchApi.installBlindFeatureGate(BlindClientGates::hidesFeaturesFromBlind);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            BlindBumpClient.tick(client);
            BlindAttuneDucking.tick(client);
        });
    }
}
