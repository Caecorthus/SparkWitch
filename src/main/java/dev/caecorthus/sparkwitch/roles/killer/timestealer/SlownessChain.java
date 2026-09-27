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
 * Known limitations: vanilla keeps no provenance on an effect, so ownership is inferred (see {@link #isOurs}).
 * A same-or-weaker, same-or-shorter effect that ours fully covered is discarded by vanilla, and a same-level foreign
 * effect whose remainder is within one tick of ours and carries the same flags merges into a node that cannot be told
 * apart from ours; such a node is removed together with ours, losing at most the time ours had left. A same-level
 * effect that outlasts ours by more than a tick, or one with different flags, makes the node foreign: it is left alone
 * and our share simply runs out (every curse node lasts at most {@link TimeStealerRules#SLOWNESS_DURATION_TICKS}).
 * 诅咒缓慢“链手术”的纯模型（计划 D5 / §3.8）。原版每个实体只保留一个缓慢实例，新效果会原地合并进去；较弱但更持久的效果
 * 作为隐藏实例留在下面，因此实时效果是一条自顶向下的链，放大器严格递减、时长严格递增。直接 {@code removeStatusEffect}
 * 会删掉整条链，连同控场专家的眩晕或其他职业的缓慢。这里把链建模为 {@link Node} 列表，只取出诅咒自己的那一节：
 * 顶层是我们的 → 移除它，下面的隐藏链成为实时效果；顶层是更强的外来效果 → 从隐藏链中剔除我们的那一节，顶层不动；
 * 找不到我们的（已被合并或已到期）→ 一律不动。
 * 已知限制：原版效果不记录来源，因此归属只能推断（见 {@link #isOurs}）。被我们完全覆盖的同级或更弱、同样短或更短的效果
 * 会被原版丢弃；剩余时长与我们相差不超过一 tick 且标志相同的同级外来效果会合并成无法与我们区分的一节，
 * 这样的节点会随我们的一起移除，损失至多为我们剩余的时间。比我们多持续一 tick 以上、或标志不同的同级效果会使该节点
 * 成为外来节点：保持不动，我们的份额自然到期（诅咒的每一节最多持续 {@link TimeStealerRules#SLOWNESS_DURATION_TICKS}）。
 */
final class SlownessChain {
    /**
     * Clock skew allowed when matching our node, in ticks. Our remaining duration is derived from world time, while the
     * effect counts down in the victim's own tick; at any component tick the two agree exactly, and a purge that runs
     * elsewhere in the tick (another entity's kill, a packet) sees them at most one tick apart in either direction.
     * Kept at 1 so a foreign same-level Slowness that extended our node by two or more ticks is never taken for ours.
     * 匹配我们那一节时允许的时钟偏差（tick）。我们的剩余时长由世界时间推算，而效果在受害者自己的 tick 中倒数；
     * 在组件 tick 时两者完全一致，在该 tick 其他位置（其他实体造成的击杀、数据包）执行的清除最多相差一 tick（任一方向）。
     * 取 1，使得把我们那一节延长了两 tick 以上的同级外来缓慢永远不会被当成我们的。
     */
    static final int MATCH_TOLERANCE_TICKS = 1;

    private SlownessChain() {
    }

    /**
     * Whether a live node is the curse's own. Vanilla keeps no provenance on a status effect and this role may not add
     * any (no mixin or access widener), so ownership is inferred from what the curse's application leaves behind and
     * only ticking can change: the amplifier of the stage we applied, a remaining duration within
     * {@link #MATCH_TOLERANCE_TICKS} of what ours should have left (a foreign merge only ever lengthens a node), and the
     * curse's own flags (ambient off, particles off, icon on), which a foreign merge with different flags overwrites.
     * Nothing can be ours once our expected remainder is 0, and an infinite (-1) node never is. Anything ambiguous is
     * treated as foreign and left to expire, since our node is bounded and a foreign one may not be.
     * 实时节点是否属于诅咒。原版状态效果不记录来源，本职业也不能添加（不允许 mixin 或访问加宽器），因此只能依据诅咒施加后
     * 留下、且只有计时会改变的特征推断：与我们施加的阶段相同的放大器；剩余时长与我们理应剩余的相差不超过
     * {@link #MATCH_TOLERANCE_TICKS}（外来合并只会延长节点）；以及诅咒自己的标志（非环境、无粒子、有图标），
     * 标志不同的外来合并会覆盖它们。我们理应剩余为 0 时没有任何节点属于我们，无限时长（-1）的节点永远不属于我们。
     * 任何存疑的节点都按外来处理并任其到期，因为我们的节点有界，而外来节点未必。
     */
    static boolean isOurs(int amplifier, int duration, boolean ambient, boolean showParticles, boolean showIcon,
            int ourAmplifier, int ourExpectedRemaining) {
        return ourExpectedRemaining > 0
                && duration > 0
                && amplifier == ourAmplifier
                && Math.abs((long) duration - ourExpectedRemaining) <= MATCH_TOLERANCE_TICKS
                && hasCurseFlags(ambient, showParticles, showIcon);
    }

    /**
     * The flags {@code TimeTheftSlowness.apply} gives the curse's Slowness: ambient off, particles off, icon on.
     * {@code TimeTheftSlowness.apply} 赋予诅咒缓慢的标志：非环境、无粒子、有图标。
     */
    static boolean hasCurseFlags(boolean ambient, boolean showParticles, boolean showIcon) {
        return !ambient && !showParticles && showIcon;
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
