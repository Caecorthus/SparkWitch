package dev.caecorthus.sparkwitch.roles.neutral.koko;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Pure rules of 泽渡可可 Sawatari Koko ({@code sparkwitch:koko}, contributed in PR #223 and rebuilt in review), a
 * neutral streamer in colour {@code B5AFE2} (owner spec 2026-11-13).
 * <ul>
 *   <li>Go live (shared ability key, {@code sparkwitch:use_koko_stream}): 60 mana, 20 s. While live she earns 10
 *   traffic per second, her held instinct key outlines every other living player, and sprinting drains half the
 *   stamina. Everyone sees her through walls for the first 10 s.</li>
 *   <li>Corpse stream (right-click a corpse): no mana, the body glows for everyone for 50 s, +100 traffic, once per
 *   dead player. Both share one cooldown: 60 s at the deal, 45 s after a stream ends or a corpse is streamed.</li>
 *   <li>1000 traffic keeps Speed III on her; live with 1500 traffic she wins alone.</li>
 *   <li>Shop: one lightstick for 100 traffic (Speed II for 10 s, traffic gain doubled for those 10 s, 30 s between
 *   uses). No coins, no knife, no lockpick; she starts with one neutral master key.</li>
 *   <li>Mana follows the Murderous Witch's natural regeneration ({@code WitchManaRules}): 1 per 2 s up to 150, nothing
 *   at the deal, no task or kill reward.</li>
 * </ul>
 * She is a mana role, never a Witch-skill role: nothing of hers is drawn in the {@code gui.sparkwitch.skills} panel.
 * 泽渡可可（{@code sparkwitch:koko}，由 PR #223 贡献、审核时重建）的纯规则：中立直播主，身份色 {@code B5AFE2}（所有者规格
 * 2026-11-13）。
 * <ul>
 *   <li>开播（共享技能键，{@code sparkwitch:use_koko_stream}）：60 魔力，持续 20 秒。直播期间每秒 +10 流量，按住本能键可透视
 *   其他所有存活玩家，疾跑体力消耗减半；前 10 秒所有人都能隔墙看到她。</li>
 *   <li>尸体直播（右键对准尸体）：不耗魔力，该尸体对所有人高亮 50 秒，+100 流量，每具尸体只能一次。两者共用冷却：发牌后 60 秒，
 *   直播结束或尸体直播之后 45 秒。</li>
 *   <li>流量达到 1000 时保持速度 III；直播中流量达到 1500 时独自获胜。</li>
 *   <li>商店：应援棒 100 流量，限购一根（10 秒速度 II，这 10 秒内流量获取翻倍，每 30 秒可用一次）。没有金币、刀和撬锁器；
 *   开局持有一把中立万能钥匙。</li>
 *   <li>魔力恢复与杀意魔女相同（见 {@code WitchManaRules}）：每 2 秒 1 点、上限 150，开局为 0，没有任务或击杀奖励。</li>
 * </ul>
 * 她是魔力职业，但从来不是魔女技能职业：她的任何内容都不会出现在 {@code gui.sparkwitch.skills} 面板里。
 */
public final class KokoRules {
    public static final Identifier ROLE_ID = SparkWitch.id("koko");
    public static final Identifier LIGHTSTICK_ID = SparkWitch.id("koko_lightstick");
    /** Forced-cooldown store id for the shared stream cooldown. / 直播共用冷却的强制冷却存储 id。 */
    public static final Identifier STREAM_SKILL_ID = SparkWitch.id("koko_stream");
    /** Shop entry id of the lightstick; unique to her shop. / 应援棒的商店条目 id，只出现在她的商店。 */
    public static final String LIGHTSTICK_SHOP_ENTRY_ID = "sparkwitch:koko_lightstick";

    public static final int COLOR = 0xB5AFE2;
    /** Opaque outline colour of the players she sees while live. / 直播期间她看到的玩家描边颜色（不透明）。 */
    public static final int OUTLINE_COLOR = 0xFF000000 | COLOR;
    /**
     * Below Wathe's {@code skip()} (100), like the witch instinct: Last Stand, a hidden Survival Master, a floating
     * Hanna and every other hard skip still win.
     * 低于 Wathe 的 {@code skip()}（100），与魔女本能相同：背水一战、被遮挡的生存大师、浮游中的汉娜等硬跳过仍然优先。
     */
    public static final int OUTLINE_PRIORITY = 90;

    /** Sprint cap: Wathe's passenger 10 s. / 体力上限：Wathe 乘客的 10 秒。 */
    public static final int MAX_SPRINT_TICKS = 10 * 20;

    public static final int STREAM_MANA = 60;
    public static final int STREAM_TICKS = 20 * 20;
    /** How long everyone sees her through walls after she goes live. / 开播后所有人能隔墙看到她的时长。 */
    public static final int EXPOSED_TICKS = 10 * 20;
    public static final int STREAM_TRAFFIC_PER_SECOND = 10;
    public static final int INITIAL_COOLDOWN_TICKS = 60 * 20;
    public static final int COOLDOWN_TICKS = 45 * 20;
    /** Sprint drain while live: half of Wathe's. / 直播期间的疾跑消耗：Wathe 的一半。 */
    public static final float LIVE_SPRINT_DRAIN_MULTIPLIER = 0.5F;
    /** Wathe drains one sprinting tick per sprinting tick. / Wathe 每个疾跑刻消耗 1 点体力。 */
    public static final float WATHE_SPRINT_DRAIN_PER_TICK = 1.0F;
    /** "This side does not track that player's stamina". / 表示本端不跟踪该玩家的体力。 */
    public static final float NOT_CAPTURED = Float.NaN;

    public static final int CORPSE_GLOW_TICKS = 50 * 20;
    public static final int CORPSE_TRAFFIC = 100;

    public static final int SPEED_TRAFFIC = 1000;
    /** Speed III. / 速度 III。 */
    public static final int SPEED_AMPLIFIER = 2;
    /** Speed III is renewed once per second and outlives the next renewal. / 速度 III 每秒续一次，时长覆盖到下次续期。 */
    public static final int SPEED_REFRESH_TICKS = 20;
    public static final int SPEED_EFFECT_TICKS = 2 * SPEED_REFRESH_TICKS + 10;
    public static final int WIN_TRAFFIC = 1500;

    /** Lightstick: 100 traffic, one per round (owner 2026-11-13). / 应援棒：100 流量，每局限购一根（所有者 2026-11-13）。 */
    public static final int LIGHTSTICK_PRICE = 100;
    public static final int LIGHTSTICK_STOCK = 1;
    public static final int LIGHTSTICK_BUFF_TICKS = 10 * 20;
    /** Speed II. / 速度 II。 */
    public static final int LIGHTSTICK_SPEED_AMPLIFIER = 1;
    public static final int LIGHTSTICK_COOLDOWN_TICKS = 30 * 20;
    public static final int LIGHTSTICK_TRAFFIC_MULTIPLIER = 2;

    private KokoRules() {
    }

    public static boolean isKoko(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    /** What the server does with a go-live request. / 服务端如何处理一次开播请求。 */
    public enum StreamVerdict {
        /** Go live. / 开播。 */
        USE,
        /** Refuse and tell her she lacks mana. / 拒绝并提示魔力不足。 */
        NO_MANA,
        /** Refuse with the Fear feedback. / 拒绝并给出恐惧反馈。 */
        FEARED,
        /** Refuse silently. / 静默拒绝。 */
        REFUSE
    }

    /**
     * Pure gate for {@code sparkwitch:use_koko_stream}. Role, round and control gates refuse silently; then readiness
     * (also refused while live), then Fear, then mana, so a blocked or cooling request never reports a mana problem.
     * {@code sparkwitch:use_koko_stream} 的纯判定。职业、回合与受控门槛静默拒绝；其后依次是就绪（直播中同样拒绝）、恐惧、魔力，
     * 因此被拦截或冷却中的请求不会提示魔力问题。
     */
    public static StreamVerdict streamVerdict(boolean koko, boolean roundActive, boolean aliveParticipant,
                                              boolean controlled, boolean live, boolean ready, boolean feared,
                                              boolean hasMana) {
        if (!koko || !roundActive || !aliveParticipant || controlled || live || !ready) {
            return StreamVerdict.REFUSE;
        }
        if (feared) {
            return StreamVerdict.FEARED;
        }
        return hasMana ? StreamVerdict.USE : StreamVerdict.NO_MANA;
    }

    /** What the server does with a right-click on a corpse. / 服务端如何处理一次对尸体的右键。 */
    public enum CorpseVerdict {
        /** Stream the corpse. / 直播这具尸体。 */
        USE,
        /** Not hers to answer: the click goes on to whatever else handles it. / 不由她处理：点击交给其他处理者。 */
        PASS,
        /** Refuse with the Fear feedback. / 拒绝并给出恐惧反馈。 */
        FEARED,
        /** Refuse: she is live. / 拒绝：正在直播。 */
        LIVE,
        /** Refuse: the shared cooldown is running. / 拒绝：共用冷却中。 */
        COOLING,
        /** Refuse: she already streamed this player's corpse. / 拒绝：这名死者的尸体已经直播过。 */
        ALREADY_STREAMED
    }

    /**
     * Pure gate for the corpse stream. Role, round, control and aim gates pass the click on untouched; then Fear, the
     * running stream, the shared cooldown and the once-per-body rule each refuse with their own line. It costs no mana
     * (owner 2026-11-13).
     * 尸体直播的纯判定。职业、回合、受控与瞄准门槛把点击原样放行；其后恐惧、直播中、共用冷却与“每具尸体一次”各自给出拒绝提示。
     * 不消耗魔力（所有者 2026-11-13）。
     */
    public static CorpseVerdict corpseVerdict(boolean koko, boolean roundActive, boolean aliveParticipant,
                                              boolean controlled, boolean bodyInReach, boolean feared, boolean live,
                                              boolean ready, boolean alreadyStreamed) {
        if (!koko || !roundActive || !aliveParticipant || controlled || !bodyInReach) {
            return CorpseVerdict.PASS;
        }
        if (feared) {
            return CorpseVerdict.FEARED;
        }
        if (live) {
            return CorpseVerdict.LIVE;
        }
        if (!ready) {
            return CorpseVerdict.COOLING;
        }
        return alreadyStreamed ? CorpseVerdict.ALREADY_STREAMED : CorpseVerdict.USE;
    }

    /**
     * Whether a live second's traffic is due at world time {@code now}: true 1 s, 2 s, ... 19 s after the stream
     * started and never on the tick it ends. The first second is paid when she goes live, so a 20 s stream pays 20
     * times and every payment lands at least 1 s before the stream ends. Wathe's win check and this payment both run
     * at the end of the world tick in an order that is not fixed, so the check sees the new traffic either in the same
     * tick or in the next one; in both cases she is still live. {@code endTick <= 0} means no stream.
     * 世界时间 {@code now} 是否该结算一秒直播流量：开播后第 1、2……19 秒各一次，结束的那一刻从不结算。第一秒在开播时即结算，
     * 因此 20 秒直播共结算 20 次，且每次结算都至少早于直播结束 1 秒。Wathe 的胜负判定与本结算都在世界刻末尾运行、先后顺序
     * 不固定，因此判定会在同一刻或下一刻看到新流量；两种情况下她都仍在直播。
     * {@code endTick <= 0} 表示没有直播。
     */
    public static boolean pulseDue(long now, long endTick, int windowTicks) {
        if (endTick <= 0L) {
            return false;
        }
        long remaining = endTick - now;
        return remaining > 0L && remaining < windowTicks && remaining % 20L == 0L;
    }

    /** Traffic of one live second; doubled inside a lightstick window. / 直播一秒的流量；应援棒生效期间翻倍。 */
    public static int trafficPerSecond(boolean cheering) {
        return cheering ? STREAM_TRAFFIC_PER_SECOND * LIGHTSTICK_TRAFFIC_MULTIPLIER : STREAM_TRAFFIC_PER_SECOND;
    }

    public static boolean hasSpeed(int traffic) {
        return traffic >= SPEED_TRAFFIC;
    }

    /**
     * Her only win: alive, not swallowed by a Taotie, live and holding {@link #WIN_TRAFFIC} traffic.
     * 她唯一的胜利条件：存活、未被饕餮吞下、正在直播且流量达到 {@link #WIN_TRAFFIC}。
     */
    public static boolean wins(boolean koko, boolean playingAndAlive, boolean swallowed, boolean live, int traffic) {
        return koko && playingAndAlive && !swallowed && live && traffic >= WIN_TRAFFIC;
    }

    /**
     * Whether the half sprint drain applies this tick: a running round, alive in survival, her exact role, live.
     * 本刻是否适用疾跑消耗减半：对局进行中、以生存模式存活、精确职业为她、且正在直播。
     */
    public static boolean halvesSprintDrain(boolean running, boolean aliveAndSurvival, @Nullable Role role,
                                            boolean live) {
        return running && aliveAndSurvival && isKoko(role) && live;
    }

    /** Stamina is tracked for every server player and, on a client, only for the local one. / 服务端跟踪所有玩家，客户端只跟踪本地玩家。 */
    public static boolean tracksStaminaOnThisSide(boolean serverPlayer, boolean mainPlayer) {
        return serverPlayer || mainPlayer;
    }

    /**
     * Stamina after a sprinting tick with Wathe's drain halved (the Discoverer's rewrite with her multiplier): the
     * drain is read from Wathe's own result, so another source that already scaled it stacks multiplicatively; at
     * Wathe's zero floor the nominal drain is used. Never below {@code current}, so it only ever refunds.
     * 疾跑刻之后、把 Wathe 的消耗减半后的体力（探索者改写方式，换成她的倍率）：消耗量取自 Wathe 自身的结果，因此已被其他来源
     * 缩放过的消耗按乘法叠加；在 Wathe 的零下限处改用标称消耗。结果绝不低于 {@code current}，因此只会返还体力。
     */
    public static float halvedSprintStamina(float previous, int previousMax, int maxSprintTime, float current) {
        if (!Float.isFinite(previous) || previous <= 0.0F || maxSprintTime <= 0 || !Float.isFinite(current)) {
            return current;
        }
        float start = previousMax > 0 && maxSprintTime < previousMax ? Math.min(previous, maxSprintTime) : previous;
        if (current >= start) {
            return current;
        }
        float drain = current > 0.0F ? start - current : WATHE_SPRINT_DRAIN_PER_TICK;
        float halved = Math.max(start - drain * LIVE_SPRINT_DRAIN_MULTIPLIER, 0.0F);
        return Math.max(current, halved);
    }

    /** One key per living final Koko. / 每名存活的最终可可只发一把钥匙。 */
    public static boolean receivesMasterKey(boolean hasRole, boolean dead, @Nullable Role finalRole,
                                            boolean alreadyHoldsKey) {
        return hasRole && !dead && isKoko(finalRole) && !alreadyHoldsKey;
    }

    /**
     * Refusal of a lightstick purchase, in order: not a living participant, no free hotbar slot, not enough traffic;
     * null = no refusal. Every key takes no argument (Wathe shows deny reasons as plain translations).
     * 购买应援棒的拒绝原因，按顺序：不是存活的参与者、快捷栏没有空位、流量不足；null 表示不拒绝。所有键都不带参数
     * （Wathe 以纯翻译显示拒绝原因）。
     */
    public static @Nullable String lightstickDenial(boolean mayBuy, boolean hasRoom, int traffic) {
        if (!mayBuy) {
            return "shop.error.sparkwitch.koko.unavailable";
        }
        if (!hasRoom) {
            return "shop.error.sparkwitch.koko.hotbar_full";
        }
        return traffic >= LIGHTSTICK_PRICE ? null : "shop.error.sparkwitch.koko.not_enough_traffic";
    }
}
