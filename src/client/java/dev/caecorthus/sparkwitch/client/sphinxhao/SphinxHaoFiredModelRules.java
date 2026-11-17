package dev.caecorthus.sparkwitch.client.sphinxhao;

import org.jetbrains.annotations.Nullable;

/**
 * Pure rules of the body-only "fired" gun model ({@code sparkwitch:sphinx_hao_fired}). It shows while the holder's
 * spring fist is alive, and for the local shooter also during a short pending window from its own use of a loaded gun
 * until the server's answer arrives, so the held fist never doubles the flying one. The pending window ends after
 * {@link #PENDING_TICKS} ticks, or as soon as the stack reads unloaded AND the fist has arrived (the fist then keeps the
 * fired model; clearing on "unloaded" alone would flash the empty model when the slot update beats the spawn packet).
 * 只显示枪身的“已开火”手枪模型（{@code sparkwitch:sphinx_hao_fired}）的纯规则。持有者的弹簧拳头存活时显示；对本地射手而言，
 * 从其使用已装填手枪起到服务端回应到达之间的短暂待定窗口也显示，因此手中的拳头不会与飞出的拳头重复。待定窗口在
 * {@link #PENDING_TICKS} 刻后结束，或在物品已读为空膛且拳头已到达时立即结束（此后由拳头维持已开火模型；若只按“空膛”清除，
 * 栏位更新先于生成包到达时会闪现空膛模型）。
 */
public final class SphinxHaoFiredModelRules {
    /** Longest wait for the server's answer to a local shot. / 本地射击等待服务端回应的最长时间。 */
    public static final int PENDING_TICKS = 10;

    private SphinxHaoFiredModelRules() {
    }

    /** Whether the fired model shows. / 是否显示已开火模型。 */
    public static boolean showsFired(boolean fistAlive, @Nullable Long pendingTick, long now) {
        return fistAlive || pendingTick != null && fresh(pendingTick, now);
    }

    /** Whether a pending entry is done. / 待定条目是否结束。 */
    public static boolean clearsPending(long pendingTick, long now, boolean loaded, boolean fistAlive) {
        return !fresh(pendingTick, now) || !loaded && fistAlive;
    }

    /** 0..PENDING_TICKS since the use; a clock that went backwards (another world) is stale. / 时钟倒退（换世界）视为过期。 */
    static boolean fresh(long pendingTick, long now) {
        long age = now - pendingTick;
        return age >= 0 && age <= PENDING_TICKS;
    }
}
