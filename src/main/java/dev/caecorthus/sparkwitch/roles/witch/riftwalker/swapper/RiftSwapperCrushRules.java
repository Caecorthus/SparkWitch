package dev.caecorthus.sparkwitch.roles.witch.riftwalker.swapper;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerRules;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Pure decisions and tuning of the Swapper crush (D13, C4, C7, C8; plan §16, research 06). Kept free of Minecraft
 * state so it can be unit tested; {@link RiftSwapperCrushService} feeds it server-authoritative facts.
 * 交换者夹死（D13、C4、C7、C8；plan §16，调研 06）的纯判定与调参。不依赖 Minecraft 状态以便单元测试；
 * 由 {@link RiftSwapperCrushService} 输入服务端权威事实。
 */
public final class RiftSwapperCrushRules {
    /** Global replay event written before the kill (post-game only). / 处死前写入的全局回放事件（只在赛后显示）。 */
    public static final Identifier REPLAY_EVENT_ID = SparkWitch.id("portal_crush");
    public static final String REPLAY_KEY = "replay.global.sparkwitch.portal_crush";
    /** Actionbar for every target who is inside a gate. / 发给每个门内目标的动作栏提示。 */
    public static final String OCCUPANT_MESSAGE_KEY = "message.sparkwitch.riftwalker.swapper.crushed";
    public static final String DEATH_REASON_KEY = "death_reason.sparkwitch.portal_crushed";
    public static final String REPLAY_DEATH_KEY = "replay.death.sparkwitch.portal_crushed.died";

    /**
     * NoellesRoles' own Swapper cooldown ({@code GameConstants.getInTicks(1, 0)}), applied only on the should-not-happen
     * path where the forced kill was vetoed and the Swapper is put back where they stood.
     * NoellesRoles 自身的交换冷却（{@code GameConstants.getInTicks(1, 0)}），仅在强制处死被否决、交换者被送回原位这一
     * 不应发生的分支上使用。
     */
    public static final int VETOED_CRUSH_COOLDOWN_TICKS = 60 * 20;

    // --- Cue at the occupant's gate / 门口的演出 ---
    public static final float CRUNCH_VOLUME = 1.2F;
    public static final float CRUNCH_PITCH = 0.6F;
    public static final float SNAP_VOLUME = 1.0F;
    public static final float SNAP_PITCH = 0.7F;
    public static final int PORTAL_PARTICLES = 48;
    public static final int CRIT_PARTICLES = 16;

    /** Half the width of a standing player (vanilla 0.6). / 站立玩家宽度的一半（原版 0.6）。 */
    static final double PLAYER_HALF_WIDTH = 0.3;
    /**
     * Heights of the reachability ray: from the gate's centre ({@code GATE_HEIGHT / 2} above its base) to the middle of
     * the candidate's standing body, so a wall, pane or closed door between the gate opening and the cell rejects it.
     * 可达射线的高度：从门的中心（门底以上 {@code GATE_HEIGHT / 2}）到候选站立身体的中部；门口与该格之间有墙、
     * 玻璃板或关着的门时拒绝该格。
     */
    public static final double GATE_RAY_HEIGHT = RiftwalkerRules.GATE_HEIGHT / 2.0;
    public static final double BODY_RAY_HEIGHT = 0.9;
    /** Body point checked for fluid besides the eyes (Sabbath landing precedent). / 除眼睛外检查流体的身体高度。 */
    public static final double WAIST_HEIGHT = 0.5;

    private static final List<BodyOffset> BODY_OFFSETS = buildBodyOffsets();

    private RiftSwapperCrushRules() {
    }

    /** What the server does with one Swapper payload. / 服务端如何处理一次交换请求。 */
    public enum Outcome {
        /** Not ours: NoellesRoles runs (and performs its own early returns). / 与门无关：交给 NR（含其自身的提前返回）。 */
        PASS,
        /**
         * A target is a spectator outside any gate, dead or alive: silent no-op (C7; never NR's swap to a ghost camera).
         * 目标是门外的旁观者（无论死活）：静默取消（C7；绝不让 NR 把人换到幽灵视角的位置）。
         */
        CANCEL,
        /** The payload's first target is inside a gate: crush at that target's gate. / 第一个目标在门内：在其门前夹死。 */
        CRUSH_AT_FIRST,
        /** Only the second target is inside a gate: crush at that target's gate. / 仅第二个目标在门内：在其门前夹死。 */
        CRUSH_AT_SECOND;

        public boolean cancelsSwap() {
            return this != PASS;
        }

        public boolean crushes() {
            return this == CRUSH_AT_FIRST || this == CRUSH_AT_SECOND;
        }
    }

    /**
     * {@code swapWouldRun} = NoellesRoles' own gate (actor is a Swapper, alive, not swallowed; both targets present and
     * not swallowed). C4: either target inside → crush (the first inside target in payload order hosts the body);
     * otherwise any spectator target, dead or alive → silent cancel (the patched widget can send a just-killed player
     * while the client's Wathe alive list lags, and NR would move the living pick to that ghost's camera); otherwise
     * NR's swap runs untouched.
     * {@code swapWouldRun} 即 NoellesRoles 自身的前置条件（执行者是交换者、存活、未被吞；两个目标都在且未被吞）。C4：任一目标
     * 在门内 → 夹死（按数据包顺序第一个门内目标所在的门放尸体）；否则任一目标是旁观者（无论死活）→ 静默取消（客户端 Wathe
     * 存活列表滞后时，打过补丁的界面可能发送刚死亡的玩家，NR 会把活着的目标换到该幽灵视角的位置）；否则 NR 照常交换。
     */
    public static Outcome decide(boolean swapWouldRun, boolean firstInside, boolean secondInside,
                                 boolean firstSpectator, boolean secondSpectator) {
        if (!swapWouldRun) {
            return Outcome.PASS;
        }
        if (firstInside) {
            return Outcome.CRUSH_AT_FIRST;
        }
        if (secondInside) {
            return Outcome.CRUSH_AT_SECOND;
        }
        if (firstSpectator || secondSpectator) {
            return Outcome.CANCEL;
        }
        return Outcome.PASS;
    }

    /**
     * Gate-local feet offsets tried in order for the Swapper's body: one block straight in front first, then the
     * diagonal front cells, then right beside the gate (a gate placed facing a wall has no free front cell), and only
     * then two blocks out; the same at one block up, then one block down. Every candidate keeps a standing player's box
     * clear of the gate slab so the body never eats the right-click that enters the gate. The server additionally
     * requires a clear collider ray from the gate opening ({@code RiftSwapperBodyCell}), so no cell behind a wall wins.
     * 交换者尸体按顺序尝试的门本地脚底偏移：先正前方一格，再前方斜格，再紧贴门的两侧（面朝墙放置的门前方没有空格），
     * 最后才是前方两格；然后同样的位置上移一格、下移一格。每个候选都让站立玩家的碰撞箱避开门板，尸体不会挡住进门的右键。
     * 服务端还要求从门口到该格的碰撞射线畅通（{@code RiftSwapperBodyCell}），因此墙后的格子永远不会被选中。
     */
    public static List<BodyOffset> bodyOffsets() {
        return BODY_OFFSETS;
    }

    /**
     * Clearance between a standing player at {@code offset} and the gate slab (blocks): the larger of the gap in front
     * of the slab and the gap beside it. / 候选位置与门板的间隙（格）：取门板前方间隙与侧方间隙中较大者。
     */
    static double slabClearance(BodyOffset offset) {
        double front = offset.forward() - PLAYER_HALF_WIDTH - RiftwalkerRules.GATE_DEPTH / 2.0;
        double side = Math.abs(offset.lateral()) - PLAYER_HALF_WIDTH - RiftwalkerRules.GATE_WIDTH / 2.0;
        return Math.max(front, side);
    }

    /**
     * World x/z delta of a gate-local offset for a horizontal facing ({@code facingX}, {@code facingZ}); lateral is
     * measured to the gate's right ({@code (-facingZ, facingX)}).
     * 给定水平朝向（{@code facingX}、{@code facingZ}）时门本地偏移对应的世界 x/z 增量；侧向以门的右侧
     * （{@code (-facingZ, facingX)}）为正。
     */
    public static double worldDeltaX(BodyOffset offset, int facingX, int facingZ) {
        return facingX * offset.forward() - facingZ * offset.lateral();
    }

    public static double worldDeltaZ(BodyOffset offset, int facingX, int facingZ) {
        return facingZ * offset.forward() + facingX * offset.lateral();
    }

    private static List<BodyOffset> buildBodyOffsets() {
        double[][] horizontal = {{1.0, 0.0}, {1.0, 1.0}, {1.0, -1.0}, {0.0, 1.0}, {0.0, -1.0},
                {2.0, 0.0}, {2.0, 1.0}, {2.0, -1.0}};
        double[] vertical = {0.0, 1.0, -1.0};
        BodyOffset[] offsets = new BodyOffset[horizontal.length * vertical.length];
        int index = 0;
        for (double up : vertical) {
            for (double[] cell : horizontal) {
                offsets[index++] = new BodyOffset(cell[0], cell[1], up);
            }
        }
        return List.of(offsets);
    }

    /** Gate-local offset: {@code forward} along the gate's front normal, {@code lateral} to its right. / 门本地偏移。 */
    public record BodyOffset(double forward, double lateral, double up) {
    }
}
