package dev.caecorthus.sparkwitch.roles.civilian.windspirit;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pure, record-only ledger behind {@code sparkwitch:wind_spirit_fall}: which promoted Wind Spirit's wind charge last
 * caught each player, and until which world tick a train-fall death still counts as following it. One entry per
 * victim, the latest knock wins; a fall death consumes it once. It never grants kill credit.
 * Window: {@link #WINDOW_TICKS} (10 s, the same span as the Fire Poker push credit). It is time-based on purpose:
 * vanilla's own "landed since the explosion" state is cleared by the client's on-ground move packets, which can arrive
 * before a laggy client applies the knockback, so it would drop real knock-offs. A knocked player who lands back on
 * the train and falls off on their own within the window still counts.
 * {@code sparkwitch:wind_spirit_fall} 背后的纯记录账本：每名玩家最近一次被哪位晋升风精灵的风弹击中，以及到哪个世界刻为止
 * 坠车死亡仍算作其后果。每名受害者一条，最近一次击中为准；坠车死亡只消费一次。从不给予击杀归属。
 * 窗口为 {@link #WINDOW_TICKS}（10 秒，与烧火棍推人归因相同）。刻意按时间判定：原版“爆炸后已落地”状态由客户端的着地移动包
 * 清除，而延迟高的客户端可能在应用击退前就发出这些包，会漏掉真实的击落。被击中后落回列车、又在窗口内自行坠车的玩家仍会计入。
 */
public final class WindSpiritKnockLedger {
    public static final int WINDOW_TICKS = 200;

    private final Map<UUID, Knock> knocks = new HashMap<>();

    /** Records that {@code windSpirit}'s charge caught {@code victim} at {@code now}; a self-knock is ignored. */
    public void knock(UUID victim, UUID windSpirit, long now) {
        if (victim.equals(windSpirit)) {
            return;
        }
        knocks.values().removeIf(knock -> knock.isExpired(now));
        knocks.put(victim, new Knock(windSpirit, now + WINDOW_TICKS));
    }

    /**
     * Removes the victim's entry and returns its Wind Spirit when the entry is still inside the window.
     * 移除受害者的记录；仍在窗口内时返回对应的风精灵。
     */
    public @Nullable UUID take(UUID victim, long now) {
        Knock knock = knocks.remove(victim);
        return knock == null || knock.isExpired(now) ? null : knock.windSpirit();
    }

    /** Drops a reset or dead victim; a Wind Spirit leaving keeps its knocks. / 移除被重置或已死亡的受害者；风精灵离开不影响其记录。 */
    public void clearVictim(UUID victim) {
        knocks.remove(victim);
    }

    public void clearAll() {
        knocks.clear();
    }

    int size() {
        return knocks.size();
    }

    private record Knock(UUID windSpirit, long expiresAt) {
        boolean isExpired(long now) {
            return now > expiresAt;
        }
    }
}
