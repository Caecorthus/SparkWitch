package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;
import dev.caecorthus.sparkwitch.roles.civilian.usec.net.UsecBulletImpactsS2CPacket;

import java.util.BitSet;
import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * Client only, pure state (no {@code MinecraftClient}): the visual-only USEC bullet cracks (D13), driven through
 * vanilla's per-breaker block-damage overlay. Each cracked position owns one reserved fake breaker id (vanilla keeps one
 * block per breaker id); a repeat hit on the same position reuses that id, keeps the higher stage and restarts the
 * hold. A crack holds its stage for {@link UsecRules#CRACK_HOLD_TICKS}, then heals one stage every
 * {@link UsecRules#CRACK_HEAL_STEP_TICKS} and is cleared with stage {@link #CLEAR_STAGE} after stage 0, so stage s lives
 * {@code HOLD + s * STEP} ticks (14 s at most). Every stage change is re-sent, so no entry goes un-refreshed for 400
 * ticks (vanilla's expiry). At most {@link #MAX_CRACKS} positions are tracked; a new one past the cap evicts the least
 * recently hit. Never touches a real block. Client thread only.
 * 仅客户端，纯状态（不依赖 {@code MinecraftClient}）：纯视觉的 USEC 子弹裂痕（D13），借助原版按破坏者区分的方块破坏覆盖层绘制。
 * 每个出现裂痕的位置占用一个保留的假破坏者 id（原版每个破坏者 id 只对应一个方块）；同一位置再次被击中时沿用该 id，保留
 * 较高级别并重新开始保持计时。裂痕先保持 {@link UsecRules#CRACK_HOLD_TICKS} 刻，之后每 {@link UsecRules#CRACK_HEAL_STEP_TICKS}
 * 刻恢复一级，0 级之后以 {@link #CLEAR_STAGE} 清除，因此 s 级裂痕存在 {@code HOLD + s * STEP} 刻（最多 14 秒）。每次级别变化都会
 * 重新发送，所以没有条目会 400 刻（原版过期时间）未刷新。最多跟踪 {@link #MAX_CRACKS} 个位置；超出上限的新位置会淘汰最久
 * 未被击中的那个。从不触碰真实方块。仅客户端线程。
 */
public final class UsecCrackTracker {
    /** Tracked positions at most; also the size of the reserved breaker id range. / 最多跟踪的位置数，也是保留破坏者 id 段的大小。 */
    public static final int MAX_CRACKS = 256;
    /**
     * Stable contract: the reserved fake breaker ids are {@code FIRST_BREAKER_ID} down to {@link #LAST_BREAKER_ID}
     * (the negated ASCII "USEC", minus up to 255). Entity network ids are positive, so these never collide with a real
     * breaker (a player mining, or a server {@code BlockBreakingProgressS2CPacket}).
     * 稳定契约：保留的假破坏者 id 为 {@code FIRST_BREAKER_ID} 向下到 {@link #LAST_BREAKER_ID}（ASCII "USEC" 取负，再减至多 255）。
     * 实体网络 id 都是正数，因此不会与真实破坏者（正在挖掘的玩家或服务端 {@code BlockBreakingProgressS2CPacket}）冲突。
     */
    public static final int FIRST_BREAKER_ID = -0x55534543;
    public static final int LAST_BREAKER_ID = FIRST_BREAKER_ID - (MAX_CRACKS - 1);
    /** Vanilla removes a breaker's entry for any stage outside 0..9; -1 is the canonical clear. / 原版对 0..9 以外的级别移除条目；-1 为标准清除值。 */
    public static final int CLEAR_STAGE = -1;

    /**
     * Where stage changes go: {@code WorldRenderer#setBlockBreakingInfo} in game, a recorder in tests.
     * 级别变化的去处：游戏中为 {@code WorldRenderer#setBlockBreakingInfo}，测试中为记录器。
     */
    @FunctionalInterface
    public interface Sink {
        void setBreakingInfo(int breakerId, long packedPos, int stage);
    }

    private static final class Crack {
        final int slot;
        final long pos;
        int hitStage;
        long hitTick;
        int shownStage;

        Crack(int slot, long pos) {
            this.slot = slot;
            this.pos = pos;
        }
    }

    /** Insertion order is hit order: the first entry is the least recently hit. / 插入顺序即命中顺序：第一个条目是最久未被击中的。 */
    private final LinkedHashMap<Long, Crack> cracks = new LinkedHashMap<>();
    private final BitSet usedSlots = new BitSet(MAX_CRACKS);
    private long now;

    /**
     * One impact: shows {@code stage} (clamped to 0..9) at {@code packedPos}, or the crack's current stage when that is
     * higher, and restarts the hold.
     * 一次命中：在 {@code packedPos} 显示 {@code stage}（钳制到 0..9），若该裂痕当前级别更高则保留当前级别，并重新开始保持计时。
     */
    public void hit(long packedPos, int stage, Sink sink) {
        int clamped = Math.max(UsecBulletImpactsS2CPacket.MIN_STAGE, Math.min(UsecBulletImpactsS2CPacket.MAX_STAGE, stage));
        Crack crack = cracks.remove(packedPos);
        if (crack == null) {
            if (cracks.size() >= MAX_CRACKS) {
                evictOldest(sink);
            }
            crack = new Crack(allocateSlot(), packedPos);
            crack.shownStage = CLEAR_STAGE;
        }
        crack.hitStage = Math.max(crack.shownStage, clamped);
        crack.hitTick = now;
        crack.shownStage = crack.hitStage;
        cracks.put(packedPos, crack);
        // Always re-sent, so vanilla's last-update tick is refreshed too. / 总是重新发送，同时刷新原版的最后更新刻。
        sink.setBreakingInfo(breakerId(crack.slot), packedPos, crack.shownStage);
    }

    /** Advances one client tick: heals and clears due cracks. / 前进一个客户端刻：恢复并清除到期的裂痕。 */
    public void tick(Sink sink) {
        now++;
        if (cracks.isEmpty()) {
            return;
        }
        for (Iterator<Crack> iterator = cracks.values().iterator(); iterator.hasNext(); ) {
            Crack crack = iterator.next();
            int stage = stageAt(crack.hitStage, now - crack.hitTick);
            if (stage == crack.shownStage) {
                continue;
            }
            if (stage < 0) {
                iterator.remove();
                usedSlots.clear(crack.slot);
                sink.setBreakingInfo(breakerId(crack.slot), crack.pos, CLEAR_STAGE);
                continue;
            }
            crack.shownStage = stage;
            sink.setBreakingInfo(breakerId(crack.slot), crack.pos, stage);
        }
    }

    /** Clears every tracked crack (world change, disconnect, round end). / 清除所有跟踪的裂痕（换世界、断线、回合结束）。 */
    public void clear(Sink sink) {
        if (cracks.isEmpty()) {
            return;
        }
        for (Crack crack : cracks.values()) {
            sink.setBreakingInfo(breakerId(crack.slot), crack.pos, CLEAR_STAGE);
        }
        cracks.clear();
        usedSlots.clear();
    }

    public int size() {
        return cracks.size();
    }

    public boolean isEmpty() {
        return cracks.isEmpty();
    }

    /**
     * The stage shown {@code ageTicks} after a hit at {@code hitStage}: held for the hold, then one stage lower per heal
     * step; negative means cleared.
     * 以 {@code hitStage} 命中后经过 {@code ageTicks} 刻显示的级别：保持期内不变，之后每个恢复步降一级；负数表示已清除。
     */
    public static int stageAt(int hitStage, long ageTicks) {
        if (ageTicks < UsecRules.CRACK_HOLD_TICKS) {
            return hitStage;
        }
        long healed = 1 + (ageTicks - UsecRules.CRACK_HOLD_TICKS) / UsecRules.CRACK_HEAL_STEP_TICKS;
        return (int) Math.max(CLEAR_STAGE, hitStage - healed);
    }

    public static boolean isReservedBreakerId(int breakerId) {
        return breakerId <= FIRST_BREAKER_ID && breakerId >= LAST_BREAKER_ID;
    }

    static int breakerId(int slot) {
        return FIRST_BREAKER_ID - slot;
    }

    private int allocateSlot() {
        int slot = usedSlots.nextClearBit(0);
        usedSlots.set(slot);
        return slot;
    }

    private void evictOldest(Sink sink) {
        Iterator<Crack> iterator = cracks.values().iterator();
        Crack oldest = iterator.next();
        iterator.remove();
        usedSlots.clear(oldest.slot);
        sink.setBreakingInfo(breakerId(oldest.slot), oldest.pos, CLEAR_STAGE);
    }
}
