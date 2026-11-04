package dev.caecorthus.sparkwitch.roles.civilian.discoverer;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Stable contract: 探索者 Discoverer, a passenger role built around open-air travel. Owner spec: Speed II in the open
 * air, 25% slower sprint drain, no passive income (+50 coins per finished task) and a shop selling one spyglass. Holds
 * the owner numbers, the literal shop id and side-neutral pure predicates; the services only read them.
 * 稳定契约：探索者，围绕露天行走设计的乘客职业。所有者设定：露天时速度 II、疾跑体力消耗慢 25%、没有被动收入
 * （每完成一个任务 +50 金币），商店出售一个望远镜。本类保存所有者数值、商店条目字面量 id 与两端通用的纯判定；各服务只读取。
 */
public final class DiscovererRules {

    public static final Identifier ROLE_ID = SparkWitch.id("discoverer");

    /** Owner-specified identity colour. / 所有者指定的身份色。 */
    public static final int COLOR = 0xAF9042;

    // ---- Open-air speed ----

    /** Speed II (amplifier 1). / 速度 II（等级参数 1）。 */
    public static final int OPEN_AIR_SPEED_AMPLIFIER = 1;

    /**
     * Effect length (the Emma pattern): with the refresh below, the Speed fades 1–2 s after the player steps inside.
     * 效果时长（沿用艾玛的写法）：配合下方的续期阈值，进入室内后速度在 1–2 秒内消失。
     */
    public static final int OPEN_AIR_SPEED_DURATION_TICKS = 40;

    /**
     * Re-applied once the remaining time is at or below this, so the effect is refreshed about once a second.
     * 剩余时长不超过此值时重新施加，因此大约每秒续期一次。
     */
    public static final int OPEN_AIR_SPEED_REFRESH_AT_OR_BELOW_TICKS = 20;

    // ---- Stamina ----

    /**
     * Sprint drain multiplier: 0.75 stamina per sprinting tick instead of Wathe's 1. The cap (the plain civilian 10 s)
     * and the regeneration stay Wathe's.
     * 疾跑消耗倍率：每个疾跑刻消耗 0.75 体力，而不是 Wathe 的 1。上限（普通平民的 10 秒）与恢复速度保持 Wathe 原样。
     */
    public static final double SPRINT_DRAIN_MULTIPLIER = 0.75D;

    /**
     * Wathe's hard-coded drain per sprinting tick ({@code PlayerEntityMixin#wathe$limitSprint}: {@code ticks - 1}).
     * Wathe 写死的每个疾跑刻消耗（{@code PlayerEntityMixin#wathe$limitSprint}：{@code ticks - 1}）。
     */
    public static final float WATHE_SPRINT_DRAIN_PER_TICK = 1.0F;

    /** "Nothing captured this tick" marker. / “本刻未捕获”的标记。 */
    public static final float NOT_CAPTURED = Float.NaN;

    // ---- Economy ----

    /** Coins per finished task; the role has no passive income. / 每完成一个任务的金币；该职业没有被动收入。 */
    public static final int TASK_REWARD = 50;

    // ---- Shop ----

    /** Bare shop-entry id, like the other SparkWitch shops. / 不带命名空间的商店条目 id，与其他 SparkWitch 商店一致。 */
    public static final String SPYGLASS_ENTRY_ID = "discoverer_spyglass";
    public static final int SPYGLASS_PRICE = 150;
    public static final int SPYGLASS_STOCK = 1;

    private DiscovererRules() {
    }

    public static boolean isDiscoverer(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    /**
     * Gate for the open-air Speed and the task pay, the USEC/Knight/Control Expert {@code canEarn} shape: an ACTIVE
     * round, playing and alive in Wathe, exactly the Discoverer, survival (so a Taotie-swallowed player or a Rift
     * occupant, both alive spectators, is refused) and not a restricted Wraith.
     * 露天速度与任务收入的门槛，沿用 USEC、骑士与控场专家的 {@code canEarn}：对局 ACTIVE、在 Wathe 中参与且存活、身份恰为
     * 探索者、生存模式（因此被饕餮吞下的玩家与裂隙门内的玩家这类存活旁观者都被拒绝）、不是受限冤魂。
     */
    public static boolean receivesPassives(boolean active, boolean playingAndAlive, @Nullable Role role,
                                           boolean spectator, boolean creative, boolean wraithRestricted) {
        return active && playingAndAlive && isDiscoverer(role) && !spectator && !creative && !wraithRestricted;
    }

    /**
     * Pays only on a confirmed non-Impostor: SparkTraits pays an Impostor's task income itself, so the two never stack.
     * An absent SparkTraits reads as FALSE (pay); an unknown answer (null) never pays.
     * 只在确认不是内鬼时发放：SparkTraits 自行支付内鬼的任务收入，两者绝不叠加。未安装 SparkTraits 视为 FALSE（发放）；
     * 未知答案（null）从不发放。
     */
    public static boolean receivesTaskMoney(@Nullable Boolean impostor) {
        return Boolean.FALSE.equals(impostor);
    }

    /**
     * Whether to (re)apply the open-air Speed: no Speed, a weaker one (vanilla keeps it as the hidden effect and
     * restores it when ours runs out), or ours running low. A stronger or infinite Speed is left alone.
     * 是否（重新）施加露天速度：没有速度、已有更弱的速度（原版会把它存为隐藏效果，在本效果结束后恢复），或本效果即将结束。
     * 更强或无限时长的速度不做改动。
     */
    public static boolean refreshesSpeed(boolean present, int amplifier, boolean infinite, int duration) {
        if (!present || amplifier < OPEN_AIR_SPEED_AMPLIFIER) {
            return true;
        }
        return amplifier == OPEN_AIR_SPEED_AMPLIFIER && !infinite
                && duration <= OPEN_AIR_SPEED_REFRESH_AT_OR_BELOW_TICKS;
    }

    /**
     * Stamina side gate: every server player, and on a client only the local player (whose HUD and sprint state this
     * stamina drives). / 体力端侧门槛：服务端的所有玩家；客户端只处理本地玩家（其 HUD 与疾跑状态由这份体力驱动）。
     */
    public static boolean tracksStaminaOnThisSide(boolean serverPlayer, boolean mainPlayer) {
        return serverPlayer || mainPlayer;
    }

    /**
     * Same gate as Wathe's own stamina tick (running round, alive and survival) plus the exact Discoverer role.
     * 与 Wathe 自身体力刻相同的门槛（对局进行中、存活且生存模式），外加身份恰为探索者。
     */
    public static boolean slowsSprintDrain(boolean running, boolean aliveAndSurvival, @Nullable Role role) {
        return running && aliveAndSurvival && isDiscoverer(role);
    }

    /**
     * Stamina after a sprinting tick, rewritten from Wathe's {@code max(start - 1, 0)} to {@code max(start - 0.75, 0)}.
     * {@code start} is the captured value after Wathe's own clamp (only when the cap shrank this tick). The drain is
     * read from Wathe's result ({@code start - current}), so another source that already scaled it (e.g. the
     * Orthopedist's Bone Setting) stacks multiplicatively; at Wathe's zero floor, where the clamp hides the drain, the
     * nominal {@link #WATHE_SPRINT_DRAIN_PER_TICK} is used. Never below {@code current}, so it only ever refunds.
     * 疾跑刻之后的体力：把 Wathe 的 {@code max(start - 1, 0)} 改写为 {@code max(start - 0.75, 0)}。{@code start} 为捕获值经
     * Wathe 自身上限截断之后的值（仅在本刻上限缩小时截断）。消耗量取自 Wathe 的结果（{@code start - current}），因此已被其他
     * 来源缩放过的消耗（如正骨师的正骨效果）按乘法叠加；在 Wathe 的零下限处，截断掩盖了消耗量，改用标称值
     * {@link #WATHE_SPRINT_DRAIN_PER_TICK}。结果绝不低于 {@code current}，因此只会返还体力。
     */
    public static float slowedSprintStamina(float previous, int previousMax, int maxSprintTime, float current) {
        if (!Float.isFinite(previous) || previous <= 0.0F || maxSprintTime <= 0 || !Float.isFinite(current)) {
            return current;
        }
        float start = previousMax > 0 && maxSprintTime < previousMax ? Math.min(previous, maxSprintTime) : previous;
        if (current >= start) {
            return current;
        }
        float drain = current > 0.0F ? start - current : WATHE_SPRINT_DRAIN_PER_TICK;
        float slowed = Math.max(start - drain * (float) SPRINT_DRAIN_MULTIPLIER, 0.0F);
        return Math.max(current, slowed);
    }
}
