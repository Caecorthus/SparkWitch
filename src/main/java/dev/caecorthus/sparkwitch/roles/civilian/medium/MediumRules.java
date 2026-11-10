package dev.caecorthus.sparkwitch.roles.civilian.medium;

import dev.caecorthus.sparkwitch.SparkWitch;
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
 * shared ability key and 招魂 on Role Skill 2. This class owns the shared constants and the death-tier maths.
 * 通灵师（{@code sparkwitch:medium}）是由 Wathe 平民分配抽取的普通任务平民，从不占用警位：普通平民基线
 * （真实情绪、乘客体力、不可见回合时间）、共享技能键上的通灵与职业技能 2 上的招魂。本类持有共享常量与死亡档位算法。
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
	/** 通灵 base price; every tier −50, floor 0. / 通灵基础价格；每档 −50，下限 0。 */
	public static final int SEANCE_BASE_PRICE = 300;
	/** 招魂 is a flat price; the death tier only discounts 通灵. / 招魂固定价格；死亡档位只给通灵降价。 */
	public static final int HAUNT_PRICE = 50;
	public static final int PRICE_TIER_STEP = 50;
	/** Every tier also shortens the 通灵 cooldown by 10 s (招魂 has no cooldown). / 每档把通灵冷却缩短 10 秒（招魂无冷却）。 */
	public static final int CD_TIER_STEP_TICKS = GameConstants.getInTicks(0, 10);
	/** Opening 通灵 cooldown (tier 0 at round start). / 通灵的开局冷却（开局死亡数为 0，档位为 0）。 */
	public static final int CD_OPENING_TICKS = GameConstants.getInTicks(0, 180);
	/** The 通灵 cooldown entered after a paid use; the tier is read when the cooldown starts. / 付费使用后进入的通灵冷却；档位在进入冷却那一刻读取。 */
	public static final int CD_AFTER_USE_TICKS = GameConstants.getInTicks(0, 60);
	/** The dead vote for this long after a paid 通灵. / 付费通灵后死者投票的窗口时长。 */
	public static final int SEANCE_WINDOW_TICKS = GameConstants.getInTicks(0, 20);
	/** Question length cap; the box simply refuses more (owner: the excess is never typed). / 提问字数上限；输入框直接不收多余字符。 */
	public static final int QUESTION_MAX_CHARS = 20;
	/** A 招魂-bound player's chat vote weighs 3. / 被招魂绑定玩家的投票记 3 票。 */
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

	/** 招魂's flat price; the tier never changes it. / 招魂的固定价格；档位不影响。 */
	public static int hauntPrice() {
		return HAUNT_PRICE;
	}

	/** A cooldown entering now lasts this long at the tier (floor 0). / 此刻进入的冷却在该档位下的时长（下限 0）。 */
	public static int cooldownTicks(int baseTicks, int tier) {
		return Math.max(0, baseTicks - CD_TIER_STEP_TICKS * Math.max(0, tier));
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
	 * Server-side question gate: strip formatting codes, trim, refuse empty and over-length. Null means refuse.
	 * 服务端提问门槛：去掉格式代码、去除首尾空白，拒绝空文本与超长文本。返回 null 表示拒绝。
	 */
	public static @Nullable String sanitizeQuestion(@Nullable String raw) {
		if (raw == null) {
			return null;
		}
		String question = raw.replace("§", "").strip();
		return question.isEmpty() || question.codePointCount(0, question.length()) > QUESTION_MAX_CHARS
				? null
				: question;
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
