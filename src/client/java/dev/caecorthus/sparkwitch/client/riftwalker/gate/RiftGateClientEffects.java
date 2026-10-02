package dev.caecorthus.sparkwitch.client.riftwalker.gate;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateEntity;

/**
 * Client-side gate presentation (plan §5.3, D9): ambient particles from {@link #tick} (PORTAL drawn inward, role-colour
 * dust along the frame, rare WITCH sparks; kept sparse), the placed-model registration, and the witch-faction instinct
 * outline through walls via Wathe's {@code GetInstinctHighlight} event. {@link #tick} is installed into
 * {@code RiftGateClientBridge} by {@code RiftwalkerClient}. Owned by P6.
 * 客户端门表现（plan §5.3、D9）：{@link #tick} 中的环境粒子（PORTAL 向门心吸入、职业色尘沿门框飘、偶尔 WITCH 火花，数量节制）、
 * 放置模型注册，以及通过 Wathe {@code GetInstinctHighlight} 事件实现的魔女阵营本能隔墙描边。{@link #tick} 由
 * {@code RiftwalkerClient} 安装到 {@code RiftGateClientBridge}。归属 P6。
 */
public final class RiftGateClientEffects {
    private static boolean initialized;

    private RiftGateClientEffects() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        // TODO(P6): ModelLoadingPlugin (rift_gate_placed) and GetInstinctHighlight listener. / TODO(P6)：模型加载与本能描边。
    }

    /** Client tick of one gate entity. G0 stub: no-op. / 单个门实体的客户端 tick。G0 存根：空操作。 */
    public static void tick(RiftGateEntity gate) {
        // TODO(P6)
    }
}
