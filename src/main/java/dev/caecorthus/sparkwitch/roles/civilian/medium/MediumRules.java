package dev.caecorthus.sparkwitch.roles.civilian.medium;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.caecorthus.sparkwitch.compat.SparkTraitsAbyssListenerBridge;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Medium (通灵师, {@code sparkwitch:medium}) is a plain task civilian drawn by Wathe's civilian assignment,
 * never through a police slot: the plain civilian baseline (REAL mood, passenger stamina, no round clock), 通灵 on the
 * shared ability key, 幽灵的加护 (Ghost's Blessing) on the SparkWitch skill key and 招魂 from the inventory screen.
 * This class owns the shared constants and the death-tier maths.
 * 通灵师（{@code sparkwitch:medium}）是由 Wathe 平民分配抽取的普通任务平民，从不占用警位：普通平民基线
 * （真实情绪、乘客体力、不可见回合时间）、共享技能键上的通灵、SparkWitch 技能键上的幽灵的加护，以及背包界面里的招魂。
 * 本类持有共享常量与死亡档位算法。
 */
public final class MediumRules {
    // ---- Role ----
    public static final Identifier ROLE_ID = SparkWitch.id("medium");
    /** Ghost-fire teal. / 鬼火青绿。 */
    public static final int COLOR = 0x54E0B8;

    // ---- Economy (owner: start at 0, +50 per task, no passive income; USEC/Accountant task-pay model) ----
    public static final int INITIAL_MONEY = 0;
    public static final int TASK_REWARD = 50;

    // ---- Skills (owner 2026-11-10) ----
    /** Replay skill ids. / 回放技能 id。 */
    public static final Identifier SEANCE_SKILL_ID = SparkWitch.id("medium_seance_skill");
    public static final Identifier HAUNT_SKILL_ID = SparkWitch.id("medium_haunt_skill");
    /**
     * 幽灵的加护. One id, two replay entries: the paid cast (no extra data) and the grant ({@code blessing} index and
     * {@code random} flag).
     * 幽灵的加护。同一 id 两条回放记录：付费施放（无附加数据）与获得加护（{@code blessing} 序号与 {@code random} 标记）。
     */
    public static final Identifier BLESSING_SKILL_ID = SparkWitch.id("medium_blessing_skill");
    /** 通灵 base price; every tier −50, floor 0. / 通灵基础价格；每档 −50，下限 0。 */
    public static final int SEANCE_BASE_PRICE = 300;
    /** 招魂 is a flat price; the death tier only discounts 通灵. / 招魂固定价格；死亡档位只给通灵降价。 */
    public static final int HAUNT_PRICE = 50;
    public static final int PRICE_TIER_STEP = 50;
    /** 加护 always costs 通灵's live price plus this, so never less than this. / 加护始终比通灵的实时价格贵这么多，因此不会低于此值。 */
    public static final int BLESSING_PRICE_EXTRA = 100;
    /** Every tier also shortens both cooldowns by 10 s (招魂 has no cooldown). / 每档把两个冷却各缩短 10 秒（招魂无冷却）。 */
    public static final int CD_TIER_STEP_TICKS = GameConstants.getInTicks(0, 10);
    /** Opening 通灵 cooldown (tier 0 at round start). / 通灵的开局冷却（开局死亡数为 0，档位为 0）。 */
    public static final int CD_OPENING_TICKS = GameConstants.getInTicks(0, 180);
    /** Opening 加护 cooldown. / 加护的开局冷却。 */
    public static final int BLESSING_CD_OPENING_TICKS = GameConstants.getInTicks(0, 300);
    /** The 通灵 cooldown entered after a paid use; the tier is read when the cooldown starts. / 付费使用后进入的通灵冷却；档位在进入冷却那一刻读取。 */
    public static final int CD_AFTER_USE_TICKS = GameConstants.getInTicks(0, 60);
    /**
     * A paid 通灵 OR 加护 starts both cooldowns; 加护's is 通灵's plus this, so the tier can never take it below this.
     * 付费通灵或加护都会同时开始两个冷却；加护冷却 = 通灵冷却 + 此值，因此档位无法把它降到此值以下。
     */
    public static final int BLESSING_CD_EXTRA_TICKS = GameConstants.getInTicks(0, 120);
    /** The dead vote for this long after a paid 通灵 or 加护. / 付费通灵或加护后死者投票的窗口时长。 */
    public static final int SEANCE_WINDOW_TICKS = GameConstants.getInTicks(0, 20);
    /** Vote choice indices of a 通灵 (question) window. / 通灵（提问）窗口的投票选项序号。 */
    public static final int VOTE_NO = 0;
    public static final int VOTE_YES = 1;
    /** Speed I for this long. / 速度 I 的持续时间。 */
    public static final int BLESSING_SPEED_TICKS = GameConstants.getInTicks(0, 40);
    /**
     * What the 回满 san blessing asks {@code PlayerMoodComponent.setMood} for: above every known cap, so the setter's
     * own clamp lands on the true maximum (Wathe 1.0; SparkTraits Steady redirects that clamp to 1.25).
     * 回满 san 加护传给 {@code PlayerMoodComponent.setMood} 的值：高于所有已知上限，由 setter 自身的钳制落到真正的最大值
     * （Wathe 为 1.0；SparkTraits 沉稳把该钳制重定向为 1.25）。
     */
    public static final float BLESSING_SANITY_REQUEST = 2.0F;
    /** Question length cap; the box simply refuses more (owner: the excess is never typed). / 提问字数上限；输入框直接不收多余字符。 */
    public static final int QUESTION_MAX_CHARS = 20;
    /** A 招魂-bound player's vote weighs 3. / 被招魂绑定玩家的投票记 3 票。 */
    public static final int BIND_VOTE_WEIGHT = 3;

    private MediumRules() {
    }

    /**
     * Current discount tier: every floor(total/6) deaths (floor division; a total under 6 is one tier per death, the
     * divisor never drops below 1). Owner 2026-11-10: everything floors.
     * 当前折扣档位：每 floor(总人数/6) 个死亡升一档（向下取整；总人数不足 6 时每死一人升一档，除数不低于 1）。
     * 用户 2026-11-10：一律向下取整。
     */
    public static int tier(int totalPlayers, long deaths) {
        int divisor = Math.max(1, totalPlayers / 6);
        return (int) Math.min(deaths / divisor, Integer.MAX_VALUE);
    }

    /** 通灵's price at the tier: base −50/tier, floor 0. / 该档位下通灵的价格：基础 −50/档，下限 0。 */
    public static int seancePrice(int tier) {
        return Math.max(0, SEANCE_BASE_PRICE - PRICE_TIER_STEP * Math.max(0, tier));
    }

    /** 加护's price at the tier: 通灵's price there plus 100. / 该档位下加护的价格：该档位通灵价格 +100。 */
    public static int blessingPrice(int tier) {
        return seancePrice(tier) + BLESSING_PRICE_EXTRA;
    }

    /** 招魂's flat price; the tier never changes it. / 招魂的固定价格；档位不影响。 */
    public static int hauntPrice() {
        return HAUNT_PRICE;
    }

    /** A cooldown entering now lasts this long at the tier (floor 0). / 此刻进入的冷却在该档位下的时长（下限 0）。 */
    public static int cooldownTicks(int baseTicks, int tier) {
        return Math.max(0, baseTicks - CD_TIER_STEP_TICKS * Math.max(0, tier));
    }

    /** The 通灵 cooldown a paid use (of either skill) starts at the tier. / 任一技能付费使用后在该档位下开始的通灵冷却。 */
    public static int seanceCooldownAfterUse(int tier) {
        return cooldownTicks(CD_AFTER_USE_TICKS, tier);
    }

    /** The 加护 cooldown a paid use (of either skill) starts at the tier. / 任一技能付费使用后在该档位下开始的加护冷却。 */
    public static int blessingCooldownAfterUse(int tier) {
        return seanceCooldownAfterUse(tier) + BLESSING_CD_EXTRA_TICKS;
    }

    /** Deaths among the round roster. / 本局名单中的死亡人数。 */
    public static int deaths(GameWorldComponent game) {
        int deaths = 0;
        for (UUID uuid : game.getAllPlayers()) {
            if (game.isPlayerDead(uuid)) {
                deaths++;
            }
        }
        return deaths;
    }

    /** The live discount tier from the roster and its deaths. / 按本局名单与死亡数实时计算的折扣档位。 */
    public static int currentTier(GameWorldComponent game) {
        return tier(game.getAllPlayers().size(), deaths(game));
    }

    /** Owner rule: "本局死者数量过半" means strictly more than half. / 用户规则：「本局死者数量过半」指严格超过一半。 */
    public static boolean deathsOverHalf(GameWorldComponent game) {
        int total = game.getAllPlayers().size();
        return total > 0 && deaths(game) * 2 > total;
    }

    /**
     * Server-side question gate: drop formatting marks and control characters, trim, refuse empty and over-length.
     * Null means refuse.
     * 服务端提问门槛：去掉格式符与控制字符、去除首尾空白，拒绝空文本与超长文本。返回 null 表示拒绝。
     */
    public static @Nullable String sanitizeQuestion(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        // The question is shown to every dead player and written to the replay, so formatting marks, control
        // characters (line breaks; only a crafted packet carries them) and invisible format characters (zero-width,
        // bidi overrides) are dropped.
        // 提问会展示给所有死者并写入回放，因此去掉格式符、控制字符（换行；只有伪造的数据包才带）以及不可见的格式字符
        // （零宽字符、双向覆盖符）。
        StringBuilder cleaned = new StringBuilder(raw.length());
        raw.codePoints()
                .filter(codePoint -> codePoint != '§' && !Character.isISOControl(codePoint)
                        && Character.getType(codePoint) != Character.FORMAT)
                .forEach(cleaned::appendCodePoint);
        String question = cleaned.toString().strip();
        return question.isEmpty() || question.codePointCount(0, question.length()) > QUESTION_MAX_CHARS
                ? null
                : question;
    }

    /**
     * Side-neutral half of the 招魂 candidate rule, shared with the client's inventory list: a round member other than
     * the caster, alive or dead. The server additionally requires the target online in the caster's world and not yet
     * bound.
     * 招魂候选规则中两端通用的部分，与客户端背包列表共用：除施放者外的本局成员，无论生死。服务端另外要求目标在施放者所在
     * 世界在线且尚未绑定。
     */
    public static boolean mayHaunt(@Nullable UUID caster, @Nullable UUID target, @Nullable GameWorldComponent game) {
        return caster != null && target != null && game != null && !target.equals(caster)
                && game.getAllPlayers().contains(target);
    }

    /**
     * Who may answer a 通灵 or vote on a 加护: a dead member of the running round (side-neutral; the roster and the dead
     * set are synced). A living player, an overflow spectator without a role and anyone outside a running round is
     * refused.
     * 谁可以回答通灵或为加护投票：进行中对局的本局死者（两端通用；名单与死亡集合均已同步）。存活玩家、没有身份的旁观者以及
     * 非进行中对局的任何人都被拒绝。
     */
    public static boolean mayVote(@Nullable GameWorldComponent game, @Nullable UUID uuid) {
        return game != null && uuid != null && game.isRunning() && game.hasAnyRole(uuid) && game.isPlayerDead(uuid);
    }

    /**
     * The full voter gate for a player entity, used by both sides: {@link #mayVote(GameWorldComponent, UUID)}, not
     * a Wraith and not held by a SparkTraits Last Stand. A Wraith, restricted or promoted (owner 2026-11-10: Wraiths
     * are not asked), is in Wathe's dead set but is back on the train and the shared ability key belongs to its own
     * play, so it is neither asked nor interrupted by the vote screen. A Last Stand player is in the dead set only for
     * the few seconds of the hold and then lives on, so it must never see the question or cast a vote (an absent
     * SparkTraits reads as "not held").
     * 针对玩家实体的完整投票门槛，两端通用：{@link #mayVote(GameWorldComponent, UUID)}、不是冤魂、且未处于 SparkTraits
     * 背水一战的保持期。冤魂无论受限还是已晋升（所有者 2026-11-10：通灵不包括冤魂），虽在 Wathe 的死亡集合中，却已回到
     * 列车上，共享技能键归其自身使用，因此既不被提问，也不会被投票界面打断。背水一战的玩家只在保持期的几秒内位于死亡集合中，
     * 随后继续存活，因此绝不能看到提问或投票（未安装 SparkTraits 视为「未保持」）。
     */
    public static boolean mayVote(@Nullable PlayerEntity player) {
        return player != null
                && mayVote(GameWorldComponent.KEY.get(player.getWorld()), player.getUuid())
                && !SparkWitchApi.isWraithActive(player)
                && !SparkWitchApi.isWraithPromoted(player)
                && !SparkTraitsAbyssListenerBridge.isLastStandPending(player);
    }

    public static boolean isMediumId(@Nullable Identifier roleId) {
        return ROLE_ID.equals(roleId);
    }

    public static boolean isMedium(@Nullable Role role) {
        return role != null && isMediumId(role.identifier());
    }

    /**
     * Gate for the task pay, the USEC/Accountant {@code canEarn} shape: an ACTIVE round, playing and alive in Wathe,
     * survival (so a Taotie-swallowed player or a Rift occupant, both alive spectators, is refused) and not a
     * restricted Wraith.
     * 任务收入的门槛，沿用 USEC 与会计的 {@code canEarn}：对局 ACTIVE、在 Wathe 中参与且存活、生存模式（被饕餮吞下的
     * 玩家与裂隙门内的玩家这类存活旁观者都被拒绝）、不是受限冤魂。
     */
    public static boolean canEarn(boolean active, boolean playingAndAlive, @Nullable Role role, boolean spectator,
                                  boolean creative, boolean wraithRestricted) {
        return isMedium(role) && active && playingAndAlive && !spectator && !creative && !wraithRestricted;
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
     * The player's live Wathe role is the Medium (either side; the client reads the synced role).
     * 玩家当前 Wathe 职业为通灵师（两端均可用；客户端读取同步的职业）。
     */
    public static boolean isMedium(@Nullable PlayerEntity player) {
        return player != null && isMedium(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    /** The player {@code uuid} holds the Medium role in {@code world}. / {@code world} 中 {@code uuid} 玩家的职业为通灵师。 */
    public static boolean isMedium(@Nullable World world, @Nullable UUID uuid) {
        return world != null && uuid != null && isMedium(GameWorldComponent.KEY.get(world).getRole(uuid));
    }
}
