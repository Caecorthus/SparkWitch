package dev.caecorthus.sparkwitch.client.riftwalker.session;

/**
 * Client side of the "inside a Rift Gate" session (plan §6.3–6.5, D10): the private 100% grayscale processor (outline
 * colours kept) with the Iris/failed-shader fallback, the ⬅️/➡️ fake hotbar with the "n/m" label and stay countdown
 * (red for the last 5 s), input interception and frozen movement, the fresh-Shift exit, and cleanup on disconnect,
 * join and respawn. Reads only the owner-synced {@code RiftSessionComponent}; sends {@code sparkwitch:rift_hop} and
 * {@code sparkwitch:rift_exit}. Owned by P3.
 * 「在裂隙门内」会话的客户端（plan §6.3–6.5、D10）：私有的 100% 黑白处理器（保留描边颜色）及光影包/着色器失败回退、
 * ⬅️/➡️ 假快捷栏与「n/m」标签和停留倒计时（最后 5 秒变红）、输入拦截与冻结移动、进门后的新 Shift 出门，以及断线、加入与
 * 重生时的清理。只读取仅同步给拥有者的 {@code RiftSessionComponent}；发送 {@code sparkwitch:rift_hop} 与
 * {@code sparkwitch:rift_exit}。归属 P3。
 */
public final class RiftSessionClient {
    private static boolean initialized;

    private RiftSessionClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        // TODO(P3): HUD, input, grayscale and connection-edge registrations. / TODO(P3)：HUD、输入、黑白与连接边界注册。
    }
}
