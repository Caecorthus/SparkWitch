package dev.caecorthus.sparkwitch.client.usec;

/**
 * Local recoil camera kick after a sent shot. Purely visual: {@code UsecRecoilCameraMixin} adds it as an extra view
 * pitch to the camera transform, so the player's real rotation, the crosshair ray and every server check are
 * untouched (the shot's aim already left with the request). The kick rises fast and decays exponentially; while scoped
 * it is scaled by the FOV multiplier so it moves the same share of the screen at every magnification (1x-6x).
 * 开火后的本地后坐镜头抖动。纯视觉：{@code UsecRecoilCameraMixin} 将其作为额外的视角俯仰加到镜头变换上，因此玩家真实
 * 朝向、准星射线与所有服务端检查都不受影响（该次射击的朝向已随请求发出）。抖动快速上扬并指数衰减；开镜时按视野倍率
 * 缩放，使其在任何倍率（1-6 倍）下在屏幕上移动的比例相同。
 */
public final class UsecRecoil {
    /** Peak upward kick at 1x, in degrees. / 1 倍下的最大上扬角度。 */
    public static final float KICK_DEGREES = 2.5F;
    /** Ticks to reach the peak. / 到达峰值的刻数。 */
    public static final float RISE_TICKS = 1.5F;
    /** Exponential decay constant after the peak, in ticks. / 峰值后的指数衰减常数（刻）。 */
    public static final float DECAY_TICKS = 3.0F;
    /** The kick is over after this many ticks. / 超过此刻数后抖动结束。 */
    public static final float DURATION_TICKS = 14.0F;
    /** Smallest FOV scale applied to the kick. / 应用于抖动的最小视野缩放。 */
    public static final float MIN_FOV_SCALE = 0.05F;

    private static long clientTicks;
    private static long firedAtTick = Long.MIN_VALUE;

    private UsecRecoil() {
    }

    /**
     * Upward kick in degrees at {@code ticksSinceFire} (fractional) for the current FOV multiplier; 0 outside the kick.
     * 在 {@code ticksSinceFire}（可为小数）时、当前视野倍率下的上扬角度；抖动之外为 0。
     */
    public static float kickDegrees(float ticksSinceFire, float fovMultiplier) {
        if (!(ticksSinceFire >= 0.0F) || ticksSinceFire >= DURATION_TICKS) {
            return 0.0F;
        }
        float shape = ticksSinceFire < RISE_TICKS
                ? ticksSinceFire / RISE_TICKS
                : (float) Math.exp(-(ticksSinceFire - RISE_TICKS) / DECAY_TICKS);
        float scale = Float.isFinite(fovMultiplier) ? Math.max(MIN_FOV_SCALE, Math.min(1.0F, fovMultiplier)) : 1.0F;
        return KICK_DEGREES * scale * shape;
    }

    /** Client thread: a shot was sent with a loaded chamber. / 客户端线程：已发出且弹膛有弹的开火请求。 */
    public static void onShot() {
        firedAtTick = clientTicks;
    }

    /** End of every client tick. / 每个客户端刻末尾。 */
    public static void tick() {
        clientTicks++;
    }

    public static void reset() {
        firedAtTick = Long.MIN_VALUE;
    }

    /** Current kick for the camera mixin. / 供镜头 mixin 使用的当前抖动。 */
    public static float currentKickDegrees(float tickDelta, float fovMultiplier) {
        if (firedAtTick == Long.MIN_VALUE) {
            return 0.0F;
        }
        return kickDegrees((clientTicks - firedAtTick) + tickDelta, fovMultiplier);
    }
}
