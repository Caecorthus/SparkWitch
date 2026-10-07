package dev.caecorthus.sparkwitch.client.tarot;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The readings the server already sent to this Tarot Reader this round, so the result slip and the selector's stamps
 * repeat them. It stores only purchaser-only reading payloads and never derives a verdict from synced round state.
 * The latest reading per (mode, target) wins; {@code TarotDivinationClientState} clears the log whenever it clears the
 * divination snapshot.
 * 本局服务端已发给该塔罗牌师的占卜结果，供结果条与选择界面的印记重复展示。只保存仅购买者可见的结果数据包，
 * 从不根据同步的对局状态推断结论。同一 (模式, 目标) 以最新结果为准；{@code TarotDivinationClientState} 在清空占卜快照
 * 时同时清空本记录。
 */
public final class TarotReadingLog {
    private static final long MILLIS_PER_MINUTE = 60_000L;

    private final Map<Key, Reading> latest = new HashMap<>();
    private Reading newest;

    public Optional<Reading> latest(int mode, String target) {
        return Optional.ofNullable(latest.get(new Key(mode, target)));
    }

    /** The last reading recorded, whatever its target. 最近记录的一条结果，不论目标。 */
    public Optional<Reading> newest() {
        return Optional.ofNullable(newest);
    }

    public void record(Reading reading) {
        Objects.requireNonNull(reading, "reading");
        latest.put(new Key(reading.mode(), reading.target()), reading);
        newest = reading;
    }

    public void clear() {
        latest.clear();
        newest = null;
    }

    /** Whole minutes since the reading arrived, rounded down and never negative. 距收到结果的整分钟数，向下取整且不为负。 */
    public static int minutesAgo(long nowMs, long receivedAtMs) {
        long minutes = Math.max(0L, nowMs - receivedAtMs) / MILLIS_PER_MINUTE;
        return (int) Math.min(Integer.MAX_VALUE, minutes);
    }

    /**
     * One resolved reading. {@code target} is the selector's target string (role id or player UUID),
     * {@code displayName} the server-resolved player name (empty for identity), {@code positive} "assigned" or
     * "alive", and {@code receivedAtMs} the client's {@code Util.getMeasuringTimeMs()} on arrival.
     * 一条已判定的结果。{@code target} 为选择界面的目标字符串（职业 id 或玩家 UUID），{@code displayName} 为服务端解析的
     * 玩家名（身份占卜为空），{@code positive} 表示"曾分配"或"存活"，{@code receivedAtMs} 为客户端收到时的计时毫秒数。
     */
    public record Reading(int mode, String target, String displayName, boolean positive, long receivedAtMs) {
        public Reading {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(displayName, "displayName");
        }
    }

    private record Key(int mode, String target) {
    }
}
