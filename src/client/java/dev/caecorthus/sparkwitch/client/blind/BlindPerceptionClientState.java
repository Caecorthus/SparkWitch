package dev.caecorthus.sparkwitch.client.blind;

import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindRules;
import dev.caecorthus.sparkwitch.roles.civilian.blind.net.BlindPulseS2CPayload;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import java.util.ArrayList;
import java.util.List;

/**
 * Stable client contract: the local Blind's perception memory. Pure and side-effect free (no Minecraft client access):
 * every call takes the current time in nanoseconds (use {@code Util.getMeasuringTimeNano()}) so tests can drive it.
 * Holds a ring buffer of the latest {@link #CAPACITY} pulses (the oldest is overwritten) and the perceived players
 * ({@code entityId → expiry}). The emitter of a SOUND/VOICE pulse is perceived for {@code PLAYER_PULSE_TICKS}; every id
 * of a CANE pulse for its duration, at most {@code CANE_ACTIVE_TICKS} (the server's in-window re-scan sends late
 * entrants as a zero-radius CANE pulse lasting the rest of the window, which is not stored as a pulse); the local
 * player itself never is. Render-thread only.
 * 稳定客户端契约：本地盲人的感知记忆。纯状态、无副作用（不访问 Minecraft 客户端）：所有调用都传入当前纳秒时间
 * （使用 {@code Util.getMeasuringTimeNano()}），便于测试驱动。保存最近 {@link #CAPACITY} 个脉冲的环形缓冲（覆盖最旧的）
 * 以及被感知的玩家（{@code 实体 id → 到期时间}）。SOUND/VOICE 脉冲的发声者被感知 {@code PLAYER_PULSE_TICKS}；
 * CANE 脉冲列出的每个 id 被感知该脉冲的时长，至多 {@code CANE_ACTIVE_TICKS}（服务端在窗口内补扫时，以持续到窗口结束的
 * 零半径 CANE 脉冲发送新进入者，该脉冲不作为脉冲保存）；本地玩家自己永远不会。仅限渲染线程。
 */
public final class BlindPerceptionClientState {
    public static final int CAPACITY = 16;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final BlindPerceptionClientState SHARED = new BlindPerceptionClientState();

    /**
     * One pulse in world coordinates. {@code startNanos} is the local receive time.
     * 世界坐标中的一个脉冲。{@code startNanos} 为本地接收时间。
     */
    public record Pulse(double x, double y, double z, float radius, long startNanos, float durationSeconds, byte kind) {
        public long endNanos() {
            return startNanos + secondsToNanos(durationSeconds);
        }

        public boolean isExpired(long nowNanos) {
            return nowNanos >= endNanos();
        }

        public float ageSeconds(long nowNanos) {
            return (float) (Math.max(0L, nowNanos - startNanos) / NANOS_PER_SECOND);
        }
    }

    private final Pulse[] ring = new Pulse[CAPACITY];
    private int next;
    private final Int2LongOpenHashMap perceivedUntil = new Int2LongOpenHashMap();

    /** The client-wide instance used by the receiver, renderer and gates. / 接收器、渲染与闸门共用的客户端实例。 */
    public static BlindPerceptionClientState get() {
        return SHARED;
    }

    /**
     * Records a server pulse and updates the perceived players; {@code localPlayerEntityId} is never perceived.
     * 记录服务端脉冲并更新被感知的玩家；{@code localPlayerEntityId} 永远不会被感知。
     */
    public void accept(BlindPulseS2CPayload payload, long nowNanos, int localPlayerEntityId) {
        if (payload == null) {
            return;
        }
        float seconds = ticksToSeconds(payload.durationTicks());
        if (payload.radius() > 0.0f) {
            push(new Pulse(payload.x(), payload.y(), payload.z(), payload.radius(), nowNanos, seconds, payload.kind()));
        }
        byte kind = payload.kind();
        if ((kind == BlindPulseS2CPayload.SOUND || kind == BlindPulseS2CPayload.VOICE) && payload.hasEmitter()) {
            perceiveIfOther(payload.emitterEntityId(), ticksToSeconds(BlindRules.PLAYER_PULSE_TICKS), nowNanos,
                    localPlayerEntityId);
        } else if (kind == BlindPulseS2CPayload.CANE) {
            float caneSeconds = ticksToSeconds(Math.min(BlindRules.CANE_ACTIVE_TICKS, payload.durationTicks()));
            for (int id : payload.playerEntityIds()) {
                perceiveIfOther(id, caneSeconds, nowNanos, localPlayerEntityId);
            }
        }
    }

    /** A client-only pulse (e.g. a bump); perceives nobody. / 仅客户端的脉冲（如碰撞）；不感知任何人。 */
    public void addLocalPulse(double x, double y, double z, float radius, float seconds, byte kind, long nowNanos) {
        push(new Pulse(x, y, z, radius, nowNanos, Math.max(0.0f, seconds), kind));
    }

    /** Live pulses, oldest first; expired slots are dropped. / 仍有效的脉冲，按从旧到新；过期的会被清除。 */
    public List<Pulse> activePulses(long nowNanos) {
        List<Pulse> active = new ArrayList<>(CAPACITY);
        for (int i = 0; i < CAPACITY; i++) {
            int slot = (next + i) % CAPACITY;
            Pulse pulse = ring[slot];
            if (pulse == null) {
                continue;
            }
            if (pulse.isExpired(nowNanos)) {
                ring[slot] = null;
            } else {
                active.add(pulse);
            }
        }
        return active;
    }

    /** Max semantics: never shortens an existing perception. / 取最大值：不会缩短已有的感知。 */
    public void perceive(int entityId, float seconds, long nowNanos) {
        if (seconds <= 0.0f) {
            return;
        }
        long until = nowNanos + secondsToNanos(seconds);
        long current = perceivedUntil.getOrDefault(entityId, Long.MIN_VALUE);
        if (until > current) {
            perceivedUntil.put(entityId, until);
        }
    }

    public boolean isPerceived(int entityId, long nowNanos) {
        return perceivedUntil.containsKey(entityId) && nowNanos < perceivedUntil.get(entityId);
    }

    /** Drops expired perceptions and pulses. / 清除过期的感知与脉冲。 */
    public void prune(long nowNanos) {
        perceivedUntil.int2LongEntrySet().removeIf(entry -> nowNanos >= entry.getLongValue());
        activePulses(nowNanos);
    }

    public void reset() {
        for (int i = 0; i < CAPACITY; i++) {
            ring[i] = null;
        }
        next = 0;
        perceivedUntil.clear();
    }

    private void push(Pulse pulse) {
        ring[next] = pulse;
        next = (next + 1) % CAPACITY;
    }

    private void perceiveIfOther(int entityId, float seconds, long nowNanos, int localPlayerEntityId) {
        if (entityId != BlindPulseS2CPayload.NO_EMITTER && entityId != localPlayerEntityId) {
            perceive(entityId, seconds, nowNanos);
        }
    }

    public static float ticksToSeconds(int ticks) {
        return Math.max(0, ticks) / 20.0f;
    }

    private static long secondsToNanos(float seconds) {
        return Math.round(seconds * NANOS_PER_SECOND);
    }
}
