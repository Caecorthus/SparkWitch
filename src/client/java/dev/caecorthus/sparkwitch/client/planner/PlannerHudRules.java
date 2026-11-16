package dev.caecorthus.sparkwitch.client.planner;

import dev.caecorthus.sparkwitch.client.fiend.FiendDashHudRules;
import dev.caecorthus.sparkwitch.roles.civilian.planner.PlannerRules;
import dev.caecorthus.sparkwitch.util.TickTime;

/**
 * Pure presentation rules of the Master Planner's owner-only two-row readout in the shared bottom-right corner: the
 * seconds until Wathe deals the next task on the upper row, the recommendation (shared ability key) on the lower row.
 * The skill row follows the order in which the server refuses a press for reasons the client can see (cooldown, then
 * coins), then names who a press would go to: the living player under the crosshair, or the planner herself. Inputs are
 * the local player's own synced state only (the ready tick against the client world clock, the next-task seconds the
 * server publishes, the Wathe shop balance), so nothing here decides anything: the server alone accepts a request.
 * Layout is the Fiend Dash / Hanna corner row ({@link FiendDashHudRules}: 5 px padding, one row up over an occupied
 * shared line, both rows lifted together above the hotbar column at narrow GUI widths). Role-owned presentation:
 * never the witch skill inventory panel ({@code gui.sparkwitch.skills}) and never the action bar. No Minecraft client
 * types, so it is unit-testable.
 * 规划大师仅本人可见、位于共享右下角的两行读数的纯展示规则：上行为距 Wathe 发下一个任务的秒数，下行为推荐（共享技能键）。
 * 技能行按服务端会因客户端可见原因拒绝按键的顺序排列（先冷却、后金币），之后写明按下会推荐给谁：准星下的存活玩家，或规划大师
 * 自己。输入只有本地玩家自己的同步状态（依据客户端世界时钟的就绪刻、服务端发布的下个任务秒数、Wathe 商店余额），因此这里不做
 * 任何判定：是否接受请求只由服务端决定。布局沿用魔人疾驰 / 汉娜的角落行（{@link FiendDashHudRules}：5 像素边距，共享行被占用
 * 时上移一行，GUI 较窄时两行一同抬升到快捷栏列之上）。属于职业自有展示：从不进入魔女技能背包面板
 * （{@code gui.sparkwitch.skills}），也从不使用动作栏。不含 Minecraft 客户端类型，可做单元测试。
 */
public final class PlannerHudRules {
    public static final String NEXT_TASK_KEY = "hud.sparkwitch.planner.next_task";
    public static final String NEXT_TASK_UNKNOWN_KEY = "hud.sparkwitch.planner.next_task.unknown";
    public static final String RECOMMEND_COOLDOWN_KEY = "hud.sparkwitch.planner.recommend.cooldown";
    public static final String RECOMMEND_NO_COINS_KEY = "hud.sparkwitch.planner.recommend.no_coins";
    public static final String RECOMMEND_READY_TARGET_KEY = "hud.sparkwitch.planner.recommend.ready_target";
    public static final String RECOMMEND_READY_SELF_KEY = "hud.sparkwitch.planner.recommend.ready_self";

    /** Opaque planner identity colour: a usable skill and the known next-task countdown. / 不透明的规划大师身份色。 */
    public static final int READY_COLOR = 0xFF000000 | PlannerRules.COLOR;
    /**
     * Same muted grey as the Hanna, Noa, Sherry and Fiend waiting lines (cooldown, not enough coins, unknown next task).
     * 与汉娜、诺亚、橘雪莉、魔人等待行相同的灰色（冷却、金币不足、下个任务未知）。
     */
    public static final int WAITING_COLOR = FiendDashHudRules.COOLDOWN_COLOR;
    public static final int ROW_GAP = FiendDashHudRules.ROW_GAP;

    private PlannerHudRules() {
    }

    /**
     * One row. {@code keyHint} puts the ability key name first; {@code namesTarget} puts the aimed player's visible name
     * next; {@code number} (negative = none) comes last and is either a countdown in whole seconds ({@code countdown})
     * or the fixed skill cost.
     * 一行。{@code keyHint} 表示第一个参数为技能按键名；{@code namesTarget} 表示其后为准星下玩家的可见名字；
     * {@code number}（负数表示没有）位于最后，要么是整秒倒计时（{@code countdown}），要么是固定的技能花费。
     */
    public record Line(String key, boolean keyHint, boolean namesTarget, int number, boolean countdown, int color) {
        public boolean hasNumber() {
            return number >= 0;
        }

        /**
         * The number this row is measured with: a countdown at two digits or more so the hotbar lift does not flip
         * when it drops a digit (see {@link FiendDashHudRules#MEASURE_SECONDS}); a cost as is.
         * 测量行宽所用的数字：倒计时至少按两位数测量，避免少一位数字时抬升来回跳动；花费按原值测量。
         */
        public int measuredNumber() {
            return countdown ? FiendDashHudRules.measuredSeconds(number) : number;
        }
    }

    /**
     * The view gates: the planner's own client (raw role), playing and alive, never an alive spectator (Rift occupant)
     * and never Taotie-swallowed (the server refuses both silently, so no "press G" then), in an ACTIVE round once
     * Wathe's round-start fade is over, with Wathe's train HUD shown and F1 off. The confirmed-server gate is the
     * dispatcher layer's.
     * 视图门槛：规划大师自己的客户端（真实职业）、在局存活，不是存活旁观者（裂隙门内），也未被饕餮吞下（服务端会静默拒绝这两种
     * 情况，因此此时不显示“按 G”）；回合为 ACTIVE 且 Wathe 开局淡入结束；Wathe 列车 HUD 显示且未按 F1。已确认服务器门槛由
     * 分发层负责。
     */
    public static boolean showsHud(boolean hudHidden, boolean gameActive, int fade, boolean trainHudActive,
                                   boolean planner, boolean playingAndAlive, boolean spectator, boolean swallowed) {
        return !hudHidden && gameActive && fade <= 0 && trainHudActive && planner && playingAndAlive && !spectator
                && !swallowed;
    }

    /**
     * Upper row: the whole seconds the server published until Wathe deals the next task, or "unknown" while none was
     * published yet ({@code -1}, or any negative value).
     * 上行：服务端发布的距 Wathe 发下一个任务的整秒数；尚未发布时（{@code -1} 或任何负数）显示“未知”。
     */
    public static Line nextTaskLine(int nextTaskSeconds) {
        if (nextTaskSeconds < 0) {
            return new Line(NEXT_TASK_UNKNOWN_KEY, false, false, -1, false, WAITING_COLOR);
        }
        return new Line(NEXT_TASK_KEY, false, false, nextTaskSeconds, true, READY_COLOR);
    }

    /**
     * Lower row, in the server's refusal order for what the client can see: the cooldown in whole seconds rounded up,
     * else the cost she cannot pay, else ready with the key, the aimed player's name when there is one, and the cost.
     * The target is only a preview: the server re-checks reach, line of sight and the target's tasks.
     * 下行，按服务端对客户端可见原因的拒绝顺序：向上取整的冷却秒数；否则为付不起的花费；否则为带按键、（有目标时）准星下玩家
     * 名字与花费的就绪提示。目标只是预览：距离、视线与目标任务由服务端重新校验。
     */
    public static Line skillLine(int cooldownTicks, int balance, boolean hasTarget) {
        if (cooldownTicks > 0) {
            return new Line(RECOMMEND_COOLDOWN_KEY, false, false, TickTime.ceilSeconds(cooldownTicks), true,
                    WAITING_COLOR);
        }
        if (balance < PlannerRules.SKILL_COST) {
            return new Line(RECOMMEND_NO_COINS_KEY, false, false, PlannerRules.SKILL_COST, false, WAITING_COLOR);
        }
        if (hasTarget) {
            return new Line(RECOMMEND_READY_TARGET_KEY, true, true, PlannerRules.SKILL_COST, false, READY_COLOR);
        }
        return new Line(RECOMMEND_READY_SELF_KEY, true, false, PlannerRules.SKILL_COST, false, READY_COLOR);
    }

    /**
     * Whether the skill row would name a target, so the per-frame crosshair pick runs only then (ready and affordable).
     * 技能行是否会写出目标名字；只有此时（就绪且付得起）才逐帧计算准星目标。
     */
    public static boolean wantsTarget(int cooldownTicks, int balance) {
        return cooldownTicks <= 0 && balance >= PlannerRules.SKILL_COST;
    }

    /** Left x of a right-aligned row. / 右对齐行的左侧 x。 */
    public static int rowX(int scaledWidth, int textWidth) {
        return FiendDashHudRules.rowX(scaledWidth, textWidth);
    }

    /**
     * Top y of the lower (skill) row. Both rows lift together: {@code measuredWidth} is the wider of the two measured
     * rows. {@code sharedLineOccupied} is the shared witch skill line (defensive: the planner has no witch skill) or
     * the Saint's Karma line, which any killer of a Hellfire Saint can carry and which is drawn flush in the corner for
     * a role {@code SaintHudLayoutRules} does not reserve (the Hanna rule); both rows then move up one row.
     * 下行（技能行）的顶部 y。两行一同抬升：{@code measuredWidth} 为两行测量宽度中的较宽者。{@code sharedLineOccupied}
     * 指共享魔女技能行（防御性处理：规划大师没有魔女技能）或圣徒业报行——任何在地狱火期间击杀圣徒的人都可能带有，对
     * {@code SaintHudLayoutRules} 未预留的职业紧贴角落绘制（汉娜的规则）；此时两行一同上移一行。
     */
    public static int lowerRowY(int scaledWidth, int scaledHeight, int measuredWidth, int fontHeight,
                                boolean sharedLineOccupied) {
        return FiendDashHudRules.rowY(scaledWidth, scaledHeight, measuredWidth, fontHeight, sharedLineOccupied);
    }

    /** Top y of the upper (next-task) row, one row and the gap above the lower one. / 上行（下个任务）的顶部 y。 */
    public static int upperRowY(int lowerRowY, int fontHeight) {
        return lowerRowY - fontHeight - ROW_GAP;
    }
}
