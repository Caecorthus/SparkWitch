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
 * {@code glUniform1fv}) plus the camera-relative view matrices. Positions are subtracted from the camera in doubles
 * before narrowing, so far-from-origin worlds keep sub-block precision. Layout (must match the shader):
 * {@code PulseData} = 16 x {x, y, z, radius, age s, duration s, expand s, kind};
 * {@code PlayerData} = 16 x {x, y, z, strength} (perceived body centres).
 * 面向 {@code sparkwitch_blind_echo} 的纯 uniform 打包：复用标量 {@code float[]}（原版以 {@code glUniform1fv} 上传）
 * 以及相机相对的视图矩阵。坐标先以 double 减去相机位置再收窄为 float，远离原点时仍保持亚方块精度。布局必须与着色器一致：
 * {@code PulseData} = 16 x {x, y, z, 半径, 已过秒数, 持续秒数, 扩张秒数, 类型}；
 * {@code PlayerData} = 16 x {x, y, z, 强度}（被感知身体中心）。
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
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final double ECHO_TIME_WRAP_SECONDS = 3600.0;

    private final float[] pulseData = new float[PULSE_FLOATS];
    private final float[] playerData = new float[PLAYER_FLOATS];
    private int pulseCount;
    private int playerCount;
    private final Matrix4f viewProjection = new Matrix4f();
    private final Matrix4f inverseViewProjection = new Matrix4f();

    /** A perceived body centre in world coordinates. / 世界坐标中的被感知身体中心。 */
    public record Body(double x, double y, double z, float strength) {
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
        int count = Math.min(bodies.size(), MAX_PLAYERS);
        for (int i = 0; i < count; i++) {
            Body body = bodies.get(i);
            int offset = i * PLAYER_STRIDE;
            playerData[offset] = (float) (body.x() - cameraX);
            playerData[offset + 1] = (float) (body.y() - cameraY);
            playerData[offset + 2] = (float) (body.z() - cameraZ);
            playerData[offset + 3] = Math.clamp(body.strength(), 0.0f, 1.0f);
        }
        playerCount = count;
        return count;
    }

    /**
     * {@code projection * viewRotation} and its inverse; the shader rebuilds camera-relative positions from depth.
     * {@code projection * viewRotation} 及其逆矩阵；着色器据此从深度重建相机相对坐标。
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

    public int pulseCount() {
        return pulseCount;
    }

    public int playerCount() {
        return playerCount;
    }

    public Matrix4f viewProjection() {
        return viewProjection;
    }

    public Matrix4f inverseViewProjection() {
        return inverseViewProjection;
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
