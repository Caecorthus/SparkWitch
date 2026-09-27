package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure model of the curse's Slowness "chain surgery" (plan D5 / §3.8). Vanilla keeps one Slowness instance per entity
 * and merges a new one into it in place; a weaker but longer-lasting effect survives as a hidden instance underneath,
 * so the live effect is a chain, top first, whose amplifiers strictly fall and whose durations strictly grow. A blind
 * {@code removeStatusEffect} would drop the whole chain, including a Control Expert stun or another role's Slowness.
 * Here the chain is a list of {@link Node}s and only the curse's own node is taken out:
 * <ul>
 *   <li>our node on top: remove it; the hidden chain below becomes the live effect;</li>
 *   <li>a stronger foreign node on top: strip our node from the hidden chain and keep the top untouched;</li>
 *   <li>our node absent (merged away or already expired): leave everything as it is.</li>
 * </ul>
 * Known limitation: vanilla discards a weaker, shorter effect that ours fully covered, and a same-level effect that
 * outlasts ours merges into one node that is no longer ours; neither can be restored or told apart, so such a node is
 * left alone and simply runs out (every curse node lasts at most {@link TimeStealerRules#SLOWNESS_DURATION_TICKS}).
 * 诅咒缓慢“链手术”的纯模型（计划 D5 / §3.8）。原版每个实体只保留一个缓慢实例，新效果会原地合并进去；较弱但更持久的效果
 * 作为隐藏实例留在下面，因此实时效果是一条自顶向下的链，放大器严格递减、时长严格递增。直接 {@code removeStatusEffect}
 * 会删掉整条链，连同控场专家的眩晕或其他职业的缓慢。这里把链建模为 {@link Node} 列表，只取出诅咒自己的那一节：
 * 顶层是我们的 → 移除它，下面的隐藏链成为实时效果；顶层是更强的外来效果 → 从隐藏链中剔除我们的那一节，顶层不动；
 * 找不到我们的（已被合并或已到期）→ 一律不动。
 * 已知限制：被我们完全覆盖的更弱更短效果会被原版丢弃；持续更久的同级效果会与我们的合并成一节，此后不再属于我们。
 * 两者都无法恢复或区分，因此保持不动、自然到期（诅咒的每一节最多持续 {@link TimeStealerRules#SLOWNESS_DURATION_TICKS}）。
 */
final class SlownessChain {
    /**
     * Tick slack when matching our node: the component tick and the status-effect tick may run in either order within
     * a server tick, and purges from shop packets or death events run between ticks.
     * 匹配我们那一节时允许的 tick 误差：同一服务端 tick 内组件 tick 与状态效果 tick 的先后不定，
     * 来自商店数据包或死亡事件的清除也发生在 tick 之间。
     */
    static final int MATCH_TOLERANCE_TICKS = 2;

    private SlownessChain() {
    }

    /**
     * Whether a live node is the curse's own: same amplifier as the stage we applied and a remaining duration within
     * {@link #MATCH_TOLERANCE_TICKS} of what ours should have left. A node that lasts longer has been merged with (or
     * replaced by) a foreign effect and is no longer ours; nothing can be ours once our expected remainder is 0, and an
     * infinite (-1) node never is.
     * 实时节点是否属于诅咒：放大器与我们施加的阶段相同，且剩余时长与我们理应剩余的相差不超过 {@link #MATCH_TOLERANCE_TICKS}。
     * 更持久的节点已与外来效果合并（或被其替换），不再属于我们；我们理应剩余为 0 时没有任何节点属于我们。
     * 无限时长（-1）的节点永远不属于我们。
     */
    static boolean isOurs(int amplifier, int duration, int ourAmplifier, int ourExpectedRemaining) {
        return ourExpectedRemaining > 0
                && duration > 0
                && amplifier == ourAmplifier
                && Math.abs((long) duration - ourExpectedRemaining) <= MATCH_TOLERANCE_TICKS;
    }

    /**
     * Removes only the first node flagged {@link Node#ours()} (top first). The input is never modified.
     * 只移除第一个标记为 {@link Node#ours()} 的节点（自顶向下）。不修改输入。
     */
    static Result withoutOurs(List<Node> chain) {
        for (int index = 0; index < chain.size(); index++) {
            if (!chain.get(index).ours()) {
                continue;
            }
            List<Node> remaining = new ArrayList<>(chain);
            remaining.remove(index);
            return new Result(index == 0 ? Surgery.REMOVED_TOP : Surgery.STRIPPED_HIDDEN, index,
                    List.copyOf(remaining));
        }
        return new Result(Surgery.UNCHANGED, -1, List.copyOf(chain));
    }

    /** One Slowness instance of the chain. / 链中的一个缓慢实例。 */
    record Node(int amplifier, int duration, boolean ours) {
    }

    /** What the surgery did. / 手术的结果类型。 */
    enum Surgery {
        /** Our node was not found; the effect is left alone. / 未找到我们的节点，效果保持不动。 */
        UNCHANGED,
        /** Our node was on top; the hidden chain is now the live effect. / 我们的节点在顶层，隐藏链成为实时效果。 */
        REMOVED_TOP,
        /** A stronger foreign node stays on top; ours left its hidden chain. / 更强的外来节点保留在顶层，我们的节点已离开隐藏链。 */
        STRIPPED_HIDDEN
    }

    /**
     * The surgery outcome, the index of the removed node (-1 when unchanged), and the resulting chain, top first
     * (empty = no Slowness left).
     * 手术结果、被移除节点的下标（未改动时为 -1）与结果链（自顶向下；空表示不再有缓慢）。
     */
    record Result(Surgery surgery, int removedIndex, List<Node> chain) {
        boolean changed() {
            return surgery != Surgery.UNCHANGED;
        }
    }
}
