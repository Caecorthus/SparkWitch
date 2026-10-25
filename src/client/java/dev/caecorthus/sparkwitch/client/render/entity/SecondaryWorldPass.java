package dev.caecorthus.sparkwitch.client.render.entity;

/**
 * Entity render protocol, part 3 (2026-10-25): a second {@code WorldRenderer#render} in the same frame, today only the
 * USEC scope's picture-in-picture lens. Run it through {@link #run}: while it runs,
 * {@code SecondaryWorldPassFrameClockMixin} makes the client frame clock report {@link #FROZEN_FRAME_TICKS} as the last
 * frame's duration. Animation that advances by frame time (EMF's {@code frame_time}, which Fresh Animations smooths and
 * accumulates every frame) then evaluates the frame's state again instead of advancing it a second time; otherwise
 * every animated entity plays at double speed while the extra pass runs. {@link EntityModelCapture} never records
 * inside such a pass. Render thread only; nests.
 * 实体渲染协议第 3 部分（2026-10-25）：同一帧内的第二次 {@code WorldRenderer#render}，目前只有 USEC 瞄准镜的画中画镜内画面。
 * 必须经由 {@link #run} 执行：执行期间 {@code SecondaryWorldPassFrameClockMixin} 让客户端帧时钟把上一帧时长报告为
 * {@link #FROZEN_FRAME_TICKS}。按帧时长推进的动画（EMF 的 {@code frame_time}，Fresh Animations 每帧用它做平滑与累加）
 * 因此只是再次求值本帧状态，而不会再推进一次；否则额外 pass 期间所有带动画的实体都会以双倍速度播放。
 * {@link EntityModelCapture} 从不在此类 pass 中录制。仅限渲染线程；可嵌套。
 */
public final class SecondaryWorldPass {
    /**
     * The last-frame duration, in ticks, reported during a pass: EMF itself reports 1e-9 s while the game is paused,
     * because packs divide by it, so this is never 0.
     * pass 期间报告的上一帧时长（刻）：游戏暂停时 EMF 自己报告 1e-9 秒，因为资源包会用它做除数，所以这里从不为 0。
     */
    public static final float FROZEN_FRAME_TICKS = 2.0E-8F;
    private static int depth;

    private SecondaryWorldPass() {
    }

    /** True while a secondary world pass runs. / 第二次世界渲染执行期间为真。 */
    public static boolean isActive() {
        return depth > 0;
    }

    /** Runs {@code pass} as a secondary world pass. / 以第二次世界渲染的身份执行 {@code pass}。 */
    public static void run(Runnable pass) {
        depth++;
        try {
            pass.run();
        } finally {
            depth--;
        }
    }

    /**
     * What the frame clock reports for a real last-frame duration of {@code ticks}.
     * 真实上一帧时长为 {@code ticks} 时，帧时钟报告的值。
     */
    public static float frameDuration(float ticks) {
        return depth > 0 ? FROZEN_FRAME_TICKS : ticks;
    }
}
