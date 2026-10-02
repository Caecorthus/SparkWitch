package dev.caecorthus.sparkwitch.roles.civilian.blind.perception;

import dev.caecorthus.sparkwitch.roles.civilian.blind.net.BlindPulseS2CPayload;

/**
 * One listening Blind as the fan-out sees it; the server implementation wraps a {@code ServerPlayerEntity} and its
 * throttle. {@link #world()} is compared by identity. Server thread only.
 * 扇出所见的一个正在聆听的盲人；服务端实现包装 {@code ServerPlayerEntity} 及其节流状态。{@link #world()} 按引用比较。
 * 仅服务端线程。
 */
public interface BlindPerceiver {
    Object world();

    int entityId();

    double squaredDistanceTo(double x, double y, double z);

    /** Current maximum range in blocks (10 / 30 / 50 / 150). / 当前最大感知距离（格）。 */
    int perceptionRange();

    /** Re-checks the real role, life and round right before sending. / 发送前重新确认真实职业、存活与对局状态。 */
    boolean isActive();

    /** The Blind's world time, the throttle clock. / 盲人所在世界的时间，即节流时钟。 */
    long time();

    BlindEmitterThrottle throttle();

    void send(BlindPulseS2CPayload payload);
}
