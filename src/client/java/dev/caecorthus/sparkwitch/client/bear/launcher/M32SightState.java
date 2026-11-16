package dev.caecorthus.sparkwitch.client.bear.launcher;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Util;

/**
 * Client only: the M2A1 aim progress p of the local player's M32A1 ({@link M32SightPose#step}: 0 to 1 over 0.20 s
 * while {@link M32ScopeProfile#isActive}, 1 to 0 over 0.15 s after). It is integrated over real frame time on every
 * read, so the hand-FOV hook and the held-item hook may both advance it in one frame (and Iris's two hand passes too)
 * without changing its speed. It snaps to 0 while the local player has no launcher in the main hand, on a world change
 * and on disconnect ({@link #reset}), so another item never inherits the narrowed hand FOV or the aim pose.
 * Presentation only; render thread only; nothing is synced.
 * 仅客户端：本地玩家 M32A1 的 M2A1 瞄准进度 p（{@link M32SightPose#step}：{@link M32ScopeProfile#isActive} 期间 0.20 秒内从 0
 * 到 1，之后 0.15 秒内从 1 到 0）。每次读取都按真实帧时间积分，因此手部视场角钩子与手持物品钩子可以在同一帧内都推进它（Iris
 * 的两次手部渲染也一样），速度不变。本地玩家主手没有发射器、切换世界以及断线（{@link #reset}）时立即归 0，因此其他物品永远不会
 * 继承收窄的手部视场角或瞄准姿态。仅为表现；仅渲染线程；不同步任何内容。
 */
public final class M32SightState {
    /** Vanilla's hand FOV before death and submersion effects. / 原版手部视场角（未计死亡与水下效果）。 */
    public static final double VANILLA_HAND_FOV = 70.0;
    private static float progress;
    private static long lastNanos;
    private static ClientWorld world;
    private static double handFovDegrees = VANILLA_HAND_FOV;

    private M32SightState() {
    }

    /** Advances p to now and returns it. / 把 p 推进到当前时刻并返回。 */
    public static float progress() {
        long now = Util.getMeasuringTimeNano();
        double seconds = lastNanos == 0L ? 0.0 : (now - lastNanos) / 1.0E9;
        lastNanos = now;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.world != world
                || !M32ClientState.holdsLauncherInMainHand(player)) {
            world = client.world;
            progress = 0.0F;
            return 0.0F;
        }
        progress = M32SightPose.step(progress, M32ScopeProfile.isActive(), seconds);
        return progress;
    }

    /**
     * {@code GameRenderer#getFov} for the hand pass ({@code changingFov == false}): records the answer for this frame's
     * range scale and returns it; exactly {@code vanillaFov} while p is 0.
     * 手部渲染的 {@code GameRenderer#getFov}（{@code changingFov == false}）：记录本帧射程刻度尺使用的结果并返回；p 为 0 时严格等于
     * {@code vanillaFov}。
     */
    public static double handFov(double vanillaFov) {
        float p = progress();
        double fov = p > 0.0F ? M32SightPose.handFov(vanillaFov, p) : vanillaFov;
        handFovDegrees = fov;
        return fov;
    }

    /** The hand FOV the current hand pass is projected with. / 当前手部渲染所用的视场角。 */
    public static double handFovDegrees() {
        return handFovDegrees;
    }

    /** Disconnect: p back to 0. / 断线：p 归 0。 */
    public static void reset() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.isOnThread()) {
            client.execute(M32SightState::reset);
            return;
        }
        progress = 0.0F;
        lastNanos = 0L;
        world = null;
        handFovDegrees = VANILLA_HAND_FOV;
    }
}
