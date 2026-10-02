package dev.caecorthus.sparkwitch.client.blind.render;

import dev.caecorthus.sparkwitch.client.blind.BlindPerceptionClientState;
import dev.caecorthus.sparkwitch.roles.civilian.blind.net.BlindPulseS2CPayload;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Pure uniform packing for {@code sparkwitch_blind_echo}: reused scalar {@code float[]} arrays (uploaded by vanilla as
 * {@code glUniform1fv}) plus the inverse camera-relative view matrix. Positions are subtracted from the camera in
 * doubles before narrowing, so far-from-origin worlds keep sub-block precision. Layout (must match the shader):
 * {@code PulseData} = 16 x {x, y, z, radius, age s, duration s, expand s, kind};
 * {@code PlayerData} = 16 x {x, y, z, strength} (perceived body centres); {@code PlayerHidden} = 16 x hidden weight;
 * {@code BlurRange} = blocks the blur passes must reach (0 = skip them).
 * 面向 {@code sparkwitch_blind_echo} 的纯 uniform 打包：复用标量 {@code float[]}（原版以 {@code glUniform1fv} 上传）
 * 以及相机相对视图矩阵的逆矩阵。坐标先以 double 减去相机位置再收窄为 float，远离原点时仍保持亚方块精度。布局必须与着色器一致：
 * {@code PulseData} = 16 x {x, y, z, 半径, 已过秒数, 持续秒数, 扩张秒数, 类型}；
 * {@code PlayerData} = 16 x {x, y, z, 强度}（被感知身体中心）；{@code PlayerHidden} = 16 x 被遮挡权重；
 * {@code BlurRange} = 模糊 pass 需要覆盖的格数（0 表示跳过）。
 */
public final class BlindEchoUniforms {
    public static final int MAX_PULSES = BlindPerceptionClientState.CAPACITY;
    public static final int PULSE_STRIDE = 8;
    public static final int PULSE_FLOATS = MAX_PULSES * PULSE_STRIDE;
    public static final int MAX_PLAYERS = 16;
    public static final int PLAYER_STRIDE = 4;
    public static final int PLAYER_FLOATS = MAX_PLAYERS * PLAYER_STRIDE;
    /** C4: a sound front reaches its radius in about 0.25 s. / C4：声波约 0.25 秒扩张到半径。 */
    public static final float WAVE_EXPAND_SECONDS = 0.25f;
    /** The radius-15 cane sweep expands a little slower so it still reads as a wave. / 半径 15 的盲杖扫描扩张稍慢，仍像声波。 */
    public static final float CANE_EXPAND_SECONDS = 0.6f;
    /**
     * The echo pass blends in the blurred halo only beyond this distance ({@code smoothstep(3.5, 10.0, range)} in
     * {@code sparkwitch_blind_echo.fsh}), so nearer hidden bodies need no blur at all.
     * 回声 pass 只在此距离之外混入模糊光晕（{@code sparkwitch_blind_echo.fsh} 中的 {@code smoothstep(3.5, 10.0, range)}），
     * 更近的被遮挡身体完全不需要模糊。
     */
    public static final float BLUR_NEEDED_BEYOND = 3.5f;
    /** Body pixels sit at most about half a block farther than the centre. / 身体像素比中心远约半格以内。 */
    public static final float BLUR_RANGE_MARGIN = 0.5f;
    /** Mirrors the old per-pixel ripple test: hidden once a block is this much nearer. / 与原逐像素涟漪判定一致。 */
    static final float HIDDEN_FROM = 0.4f;
    static final float HIDDEN_FULL = 1.0f;
    /** A hit this close to the body point is the body's own footing, not an occluder. / 距身体点这么近的命中视为脚下方块而非遮挡。 */
    static final float OCCLUDER_SLACK = 0.3f;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final double ECHO_TIME_WRAP_SECONDS = 3600.0;

    private final float[] pulseData = new float[PULSE_FLOATS];
    private final float[] playerData = new float[PLAYER_FLOATS];
    private final float[] playerHidden = new float[MAX_PLAYERS];
    private int pulseCount;
    private int playerCount;
    private float blurRange;
    private final Matrix4f viewProjection = new Matrix4f();
    private final Matrix4f inverseViewProjection = new Matrix4f();

    /**
     * A perceived body centre in world coordinates. {@code hidden} (0..1) is how far its centre is behind a block on
     * the CPU line of sight (drives the ripple); {@code partlyHidden} is true when any of its sampled points is
     * (drives how far the blur passes must reach).
     * 世界坐标中的被感知身体中心。{@code hidden}（0..1）为 CPU 视线判定下其中心被方块遮挡的程度（决定涟漪）；
     * 任一采样点被遮挡时 {@code partlyHidden} 为真（决定模糊 pass 需要覆盖的距离）。
     */
    public record Body(double x, double y, double z, float strength, float hidden, boolean partlyHidden) {
    }

    /**
     * Packs the live pulses (oldest first, as {@link BlindPerceptionClientState#activePulses} returns them), keeping
     * the newest {@link #MAX_PULSES}; expired ones are skipped.
     * 打包仍有效的脉冲（与 {@link BlindPerceptionClientState#activePulses} 一样从旧到新），保留最新的 {@link #MAX_PULSES} 个；
     * 跳过已过期的。
     */
    public int packPulses(List<BlindPerceptionClientState.Pulse> pulses, long nowNanos,
                          double cameraX, double cameraY, double cameraZ) {
        Arrays.fill(pulseData, 0.0f);
        int count = 0;
        int first = Math.max(0, pulses.size() - MAX_PULSES);
        for (int i = first; i < pulses.size(); i++) {
            BlindPerceptionClientState.Pulse pulse = pulses.get(i);
            if (pulse == null || pulse.isExpired(nowNanos)) {
                continue;
            }
            int offset = count * PULSE_STRIDE;
            pulseData[offset] = (float) (pulse.x() - cameraX);
            pulseData[offset + 1] = (float) (pulse.y() - cameraY);
            pulseData[offset + 2] = (float) (pulse.z() - cameraZ);
            pulseData[offset + 3] = Math.max(0.0f, pulse.radius());
            pulseData[offset + 4] = pulse.ageSeconds(nowNanos);
            pulseData[offset + 5] = pulse.durationSeconds();
            pulseData[offset + 6] = expandSeconds(pulse.kind(), pulse.durationSeconds());
            pulseData[offset + 7] = pulse.kind();
            count++;
        }
        pulseCount = count;
        return count;
    }

    /**
     * Packs at most {@link #MAX_PLAYERS} bodies in the given order (callers sort nearest first, see
     * {@link #keepNearest}).
     * 按给定顺序打包最多 {@link #MAX_PLAYERS} 个身体（调用方先按由近到远排序，见 {@link #keepNearest}）。
     */
    public int packPlayers(List<Body> bodies, double cameraX, double cameraY, double cameraZ) {
        Arrays.fill(playerData, 0.0f);
        Arrays.fill(playerHidden, 0.0f);
        int count = Math.min(bodies.size(), MAX_PLAYERS);
        double farthestOccluded = 0.0;
        for (int i = 0; i < count; i++) {
            Body body = bodies.get(i);
            int offset = i * PLAYER_STRIDE;
            double dx = body.x() - cameraX;
            double dy = body.y() - cameraY;
            double dz = body.z() - cameraZ;
            playerData[offset] = (float) dx;
            playerData[offset + 1] = (float) dy;
            playerData[offset + 2] = (float) dz;
            playerData[offset + 3] = Math.clamp(body.strength(), 0.0f, 1.0f);
            playerHidden[i] = Math.clamp(body.hidden(), 0.0f, 1.0f);
            if (body.partlyHidden()) {
                farthestOccluded = Math.max(farthestOccluded, Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
        }
        playerCount = count;
        blurRange = blurRangeFor(farthestOccluded);
        return count;
    }

    /**
     * Inverts {@code projection * viewRotation}; the shaders rebuild camera-relative positions from depth with it.
     * 求 {@code projection * viewRotation} 的逆矩阵；着色器据此从深度重建相机相对坐标。
     */
    public void setView(Matrix4fc projection, Matrix4fc viewRotation) {
        viewProjection.set(projection).mul(viewRotation);
        viewProjection.invert(inverseViewProjection);
    }

    public float[] pulseData() {
        return pulseData;
    }

    public float[] playerData() {
        return playerData;
    }

    public float[] playerHidden() {
        return playerHidden;
    }

    /** Blocks the blur passes must reach; 0 means they are skipped. / 模糊 pass 需要覆盖的格数；0 表示跳过。 */
    public float blurRange() {
        return blurRange;
    }

    public int pulseCount() {
        return pulseCount;
    }

    public int playerCount() {
        return playerCount;
    }

    public Matrix4f inverseViewProjection() {
        return inverseViewProjection;
    }

    /**
     * The blur reach for the farthest (partly) hidden body: its distance plus {@link #BLUR_RANGE_MARGIN}, or 0 (skip
     * both blur passes) while that stays within {@link #BLUR_NEEDED_BEYOND} (about 3 blocks), where the echo pass
     * never reads the halo.
     * 最远（部分）被遮挡身体的模糊覆盖距离：其距离加 {@link #BLUR_RANGE_MARGIN}；在 {@link #BLUR_NEEDED_BEYOND}（约 3 格）
     * 以内时回声 pass 不读取光晕，返回 0（跳过两个模糊 pass）。
     */
    public static float blurRangeFor(double farthestOccludedDistance) {
        if (!(farthestOccludedDistance + BLUR_RANGE_MARGIN > BLUR_NEEDED_BEYOND)) {
            return 0.0f;
        }
        return (float) (farthestOccludedDistance + BLUR_RANGE_MARGIN);
    }

    /**
     * Ripple weight of a body centre {@code targetDistance} away whose line of sight first hits a block at
     * {@code hitDistance} (infinite or NaN: nothing in the way); the same ramp the shader used per pixel.
     * 距离为 {@code targetDistance} 的身体中心、视线在 {@code hitDistance} 处首次命中方块（无穷或 NaN 表示无遮挡）时的涟漪权重；
     * 与着色器原先逐像素使用的渐变相同。
     */
    public static float hiddenWeight(double targetDistance, double hitDistance) {
        if (!Double.isFinite(hitDistance)) {
            return 0.0f;
        }
        double t = Math.clamp((targetDistance - hitDistance - HIDDEN_FROM) / (HIDDEN_FULL - HIDDEN_FROM), 0.0, 1.0);
        return (float) (t * t * (3.0 - 2.0 * t));
    }

    /**
     * Whether a block hit at {@code hitDistance} really stands between the camera and a body point
     * {@code targetDistance} away (not the block the body stands on or leans against).
     * 在 {@code hitDistance} 处命中的方块是否真的位于相机与距离为 {@code targetDistance} 的身体点之间（而非身体脚下或倚靠的方块）。
     */
    public static boolean occludes(double targetDistance, double hitDistance) {
        return Double.isFinite(hitDistance) && hitDistance < targetDistance - OCCLUDER_SLACK;
    }

    /** Wave expansion time per kind, never longer than half the pulse. / 各类型的扩张时间，不超过脉冲时长的一半。 */
    public static float expandSeconds(byte kind, float durationSeconds) {
        float expand = kind == BlindPulseS2CPayload.CANE ? CANE_EXPAND_SECONDS : WAVE_EXPAND_SECONDS;
        return Math.max(0.05f, Math.min(expand, durationSeconds * 0.5f));
    }

    /** Shader animation clock in seconds, wrapped to keep float precision. / 着色器动画时钟（秒），循环以保持浮点精度。 */
    public static float echoTime(long nowNanos) {
        return (float) ((Math.floorMod(nowNanos, (long) (ECHO_TIME_WRAP_SECONDS * NANOS_PER_SECOND)))
                / NANOS_PER_SECOND);
    }

    /** Sorts nearest first (by squared distance) and drops the rest beyond {@code max}. / 由近到远排序并丢弃超出 {@code max} 的部分。 */
    public static <T> void keepNearest(List<T> items, ToDoubleFunction<T> squaredDistance, int max) {
        items.sort(Comparator.comparingDouble(squaredDistance));
        while (items.size() > Math.max(0, max)) {
            items.removeLast();
        }
    }
}
