package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecCooldowns;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRifleState;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Client only, pure: tells a real bolt cycle from a short forced lock on the local player's rifle cooldown. The entry
 * length cannot: SparkFactionAPI's exact forced writer (the Shriek Gun's 40 ticks, the AC shell's up to 8) restarts
 * the entry, so a forced lock on a ready rifle looks exactly like a bolt. The evidence is the rifle state instead.
 * Every server bolt ({@code UsecCooldowns.bolt}: an accepted shot, an attachment action or a cursor load) is written in
 * the same handler as a {@link UsecRifleState} change (a round fired, or a round chambered), and a forced lock never
 * touches the rifle. So an entry is a bolt when the synced state of the rifles the player carries changed within
 * {@link #EVIDENCE_WINDOW_TICKS} of the entry's start: the cooldown packet goes out at once and the slot update with
 * the next {@code sendContentUpdates}, a tick or two apart. Nothing new is sent or synced, and the server's cooldown
 * stays the only authority.
 * <p>
 * Times are ticks of the local player's own {@code ItemCooldownManager} clock, the one its entries start on. Rifle
 * states are compared as a multiset, so moving the rifle between slots is no change. Once an entry is confirmed it
 * stays a bolt (latched by its start tick), whatever changes later; an entry that starts later (a forced lock that
 * restarts a running bolt) needs its own evidence. {@code UsecCooldowns.Status} still caps a bolt at 40 ticks, so the
 * round-start grant (a new rifle under a 60 s lock) never reads as one.
 * 仅客户端，纯逻辑：区分本地玩家步枪冷却中的真实拉栓与短暂的强制锁定。条目时长无法区分：SparkFactionAPI 的精确强制写入
 * （尖啸枪的 40 刻、AC 炮弹最多 8 刻）会重置条目起点，因此落在就绪步枪上的强制锁定与拉栓完全一样。改用步枪状态作为证据：
 * 服务端的每次拉栓（{@code UsecCooldowns.bolt}：被接受的射击、配件动作或光标装填）都与一次 {@link UsecRifleState} 变化
 * （打出一发或推弹上膛）在同一处理器中写入，而强制锁定从不改动步枪。因此若玩家携带的步枪的同步状态在条目开始前后
 * {@link #EVIDENCE_WINDOW_TICKS} 刻内发生变化，该条目即为拉栓：冷却数据包立即发出，栏位更新随下一次
 * {@code sendContentUpdates} 发出，两者相差一两刻。不发送也不同步任何新内容，服务端冷却仍是唯一权威。
 * <p>
 * 时间均为本地玩家自身 {@code ItemCooldownManager} 时钟的刻，即其条目起点所用的时钟。步枪状态按多重集比较，因此在栏位间
 * 移动步枪不算变化。条目一旦确认就保持为拉栓（按起始刻锁存），之后无论发生什么变化；起点更晚的条目（重置进行中拉栓的强制
 * 锁定）需要自己的证据。{@code UsecCooldowns.Status} 仍把拉栓上限定为 40 刻，因此开局发枪（60 秒锁定下的新步枪）永远不算拉栓。
 */
public final class UsecBoltWatch {
    /**
     * Ticks between an entry's start and the rifle state change that proves it a bolt, either way round. Covers the
     * slot update trailing the cooldown packet by up to a server tick, plus a lagging server; a real bolt is usually
     * confirmed within 2 ticks. A forced lock is confirmed only by a coincidence: an unrelated rifle change (a
     * non-bolting attachment edit, a creative cursor load, a rifle gained or lost) within these ticks of its start. A
     * server hitch longer than the window leaves a real bolt reading as a lock.
     * 条目起点与证明其为拉栓的步枪状态变化之间允许的刻数（前后皆可）。覆盖栏位更新最多晚于冷却数据包一个服务端刻，外加服务端
     * 卡顿；真实拉栓通常在 2 刻内确认。强制锁定只会因巧合被确认：在其开始前后这些刻内恰好发生无关的步枪变化（不拉栓的配件改动、
     * 创造模式光标装填、获得或失去步枪）。服务端卡顿超过该窗口时，真实拉栓会显示为锁定。
     */
    public static final int EVIDENCE_WINDOW_TICKS = 6;

    private UsecBoltWatch() {
    }

    /** Whether a state change at {@code changedAt} proves the entry that started at {@code entryStart}. / 证据判定。 */
    static boolean evidenced(int entryStart, int changedAt) {
        return entryStart != Tracker.NONE && changedAt != Tracker.NONE
                && Math.abs((long) changedAt - entryStart) <= EVIDENCE_WINDOW_TICKS;
    }

    /** Rifle states as a multiset (count per state). / 步枪状态的多重集（每种状态的数量）。 */
    static Map<UsecRifleState, Integer> multiset(Collection<UsecRifleState> rifles) {
        Map<UsecRifleState, Integer> counts = new HashMap<>();
        for (UsecRifleState state : rifles) {
            counts.merge(state == null ? UsecRifleState.EMPTY : state, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * The local player's watch, ticked once per client tick and asked any number of times per frame. Client thread
     * only.
     * 本地玩家的观察器：每个客户端刻推进一次，每帧可查询任意次。仅客户端线程。
     */
    public static final class Tracker {
        static final int NONE = UsecCooldowns.Reading.NO_ENTRY;

        private boolean seen;
        private Map<UsecRifleState, Integer> rifles = Map.of();
        private int changedAt = NONE;
        private int boltStart = NONE;

        /**
         * End of every client tick: records a rifle state change at {@code handledAt} and latches the current entry
         * once it is proven a bolt. The first observation is the baseline, never a change.
         * 每个客户端刻末尾：在 {@code handledAt} 记录步枪状态变化，并在当前条目被证明为拉栓后锁存。首次观察只作为基准，不算变化。
         *
         * @param handledAt  the cooldown clock the packets seen now were handled at, the value an entry created with them
         *                   starts at / 现在所见数据包被处理时的冷却时钟，即随之创建的条目的起始值
         * @param rifles     the synced state of every rifle the player carries, any order / 玩家携带的每把步枪的同步状态，顺序不限
         * @param entryStart the rifle entry's start tick, or {@code UsecCooldowns.Reading.NO_ENTRY} / 步枪条目的起始刻
         */
        public void tick(int handledAt, Collection<UsecRifleState> rifles, int entryStart) {
            Map<UsecRifleState, Integer> current = multiset(rifles);
            if (seen && !current.equals(this.rifles)) {
                changedAt = handledAt;
            }
            this.rifles = current;
            seen = true;
            if (evidenced(entryStart, changedAt)) {
                boltStart = entryStart;
            }
        }

        /**
         * Whether the entry that started at {@code entryStart} is a bolt cycle, live: a state change not yet ticked
         * counts at {@code now} (between ticks, the clock it was handled at), so a frame between the slot update and the
         * next tick already gives the answer that tick will latch.
         * 起始于 {@code entryStart} 的条目是否为拉栓（实时）：尚未经过刻推进的状态变化按 {@code now} 计（两刻之间即其被处理时的
         * 时钟），因此栏位更新与下一刻之间的帧已给出该刻将锁存的结论。
         */
        public boolean isBolt(int now, Collection<UsecRifleState> rifles, int entryStart) {
            if (entryStart == NONE) {
                return false;
            }
            if (entryStart == boltStart) {
                return true;
            }
            int changed = seen && !multiset(rifles).equals(this.rifles) ? now : changedAt;
            return evidenced(entryStart, changed);
        }

        public void reset() {
            seen = false;
            rifles = Map.of();
            changedAt = NONE;
            boltStart = NONE;
        }
    }
}
