package dev.caecorthus.sparkwitch.roles.killer.hunter;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Pure Hunter tuning and role-id based compatibility rules.
 * 猎人的纯数值与基于身份 ID 的兼容规则集中在这里。
 */
public final class HunterRules {
    public static final Identifier ROLE_ID = Identifier.of("sparkwitch", "hunter");
    public static final int COLOR = 0x5C4C34;

    public static final int SHOTGUN_PRICE = 75;
    public static final int SHELL_PRICE = 150;
    public static final int SLUG_PRICE = 50;
    public static final int TRAP_PRICE = 50;
    public static final int SHOTGUN_STOCK = 1;

    public static final int MAX_SHELLS = 2;
    public static final double SHOTGUN_RANGE = 8.0D;
    // The slug's single-ray range, longer than the buckshot spread (2026-10-29).
    // 独头弹单射线射程，长于霰弹散射（2026-10-29）。
    public static final double SLUG_RANGE = 15.0D;
    // Angular offset of each spread ray from the centre: up/down, left/right and the four diagonals (2026-11-01).
    // 散射射线相对中心射线的偏角：上下、左右与四角（2026-11-01）。
    public static final double SHOTGUN_SPREAD_DEGREES = 3.0D;
    // Shell types on the shotgun's shell stack, stored as the digit chars '0' / '1' (2026-10-29); NONE = empty gun.
    // 猎枪弹药栈中的弹药类型，以数字字符 '0' / '1' 存储（2026-10-29）；NONE 表示空枪。
    public static final int SHELL_NONE = -1;
    public static final int SHELL_BUCKSHOT = 0;
    public static final int SHELL_SLUG = 1;

    // Bundled shotgun audio (2026-10-29): the shot carries 16 blocks, the reload 8, by attenuation_distance in
    // sounds.json. / 猎枪自带音频（2026-10-29）：枪声 16 格、装填 8 格，由 sounds.json 的 attenuation_distance 决定。
    public static final Identifier SHOT_SOUND_ID = SparkWitch.id("item.double_barrel.shot");
    public static final Identifier LOAD_SOUND_ID = SparkWitch.id("item.double_barrel.load");
    public static final int FOLLOW_UP_COOLDOWN_TICKS = 4;
    public static final int EMPTY_COOLDOWN_TICKS = 20 * 30;
    // Round-start shotgun lock: 40 s (owner decision 2026-10-07), shorter than Wathe's 60 s revolver lock.
    // 猎枪开局锁定 40 秒（所有者 2026-10-07 决定），短于 Wathe 左轮的 60 秒开局锁定。
    public static final int SHOTGUN_INITIAL_COOLDOWN_TICKS = 40 * 20;
    public static final int SECOND_SHELL_WINDOW_TICKS = 20 * 10;
    // A shot into a SparkStrength junction box takes 70% of its durability (owner decision 2026-10-21), in
    // SparkStrength's unit where 10_000 = 100%, so two shots open a full box.
    // 一枪打中 SparkStrength 接线箱扣除其 70% 耐久（所有者 2026-10-21 决定），单位为 SparkStrength 的 10_000 = 100%，
    // 因此满耐久的箱子两枪即开。
    public static final int SHOTGUN_JUNCTION_BOX_DAMAGE = 7_000;

    public static final int MAX_OWNED_TRAPS = 4;
    public static final int TRAP_ARM_TICKS = 10;
    public static final int TRAP_LIFESPAN_TICKS = 20 * 60 * 10;
    public static final int TRAP_ROOT_TICKS = 20 * 3;
    // Catching a trap locks every item in the victim's inventory for three seconds, matching the root (owner 2026-11-11).
    // 被夹住时受害者背包内所有物品进入三秒冷却，与禁锢时长一致（所有者 2026-11-11）。
    public static final int TRAP_VICTIM_ITEM_COOLDOWN_TICKS = 20 * 3;
    public static final int FRACTURE_LAYER_TICKS = 20 * 60;
    public static final int MAX_FRACTURE_LAYERS = 5;
    public static final double SLOW_PER_FRACTURE_LAYER = 0.20D;
    public static final int TRAP_POISON_TICKS = 20 * 40;
    // Class 1 dismantling is a channel: five seconds of held right-clicks in range, and a gap longer than the press
    // timeout restarts it (owner 2026-11-11). Every accepted press but the one that finishes the channel floors the
    // dismantler's carried items at the press timeout plus one tick, so they stay locked for exactly as long as the
    // channel can still be continued and unlock with it: one forced-cooldown write per press (a held button repeats
    // every 4 ticks) instead of one per tick, and no extra lock on top of the timeout after letting go.
    // 第一类拆除是引导：在距离内连续右键五秒，超过按键超时即重新开始（所有者 2026-11-11）。除完成引导的那一次外，每次有效按键
    // 都把拆除者携带物品的冷却抬到“按键超时 + 1 刻”，于是物品恰好在引导仍可延续的时间内保持锁定，并随引导一起解锁：每次按键
    // 写一次强制冷却（按住按键每 4 刻重复一次），而不是每刻一次，松手后也不会在超时之外再多锁一段。
    public static final int DISMANTLE_CHANNEL_TICKS = 5 * 20;
    public static final int DISMANTLE_PRESS_TIMEOUT_TICKS = 12;
    public static final int DISMANTLE_HELD_ITEM_COOLDOWN_TICKS = DISMANTLE_PRESS_TIMEOUT_TICKS + 1;
    public static final int POISONER_REWARD = 75;
    public static final int PLACER_REWARD = 50;
    // Dying within three seconds of being caught by a trap pays the placer (owner 2026-11-11).
    // 被夹住后三秒内死亡，给放置者 30 金币（所有者 2026-11-11）。
    public static final int TRAP_KILL_REWARD = 30;
    public static final int TRAP_KILL_REWARD_WINDOW_TICKS = 20 * 3;

    public static final Identifier VIGILANTE_ROLE_ID = Identifier.of("wathe", "vigilante");
    public static final Identifier VETERAN_ROLE_ID = Identifier.of("wathe", "veteran");
    public static final Identifier CORRUPT_COP_ROLE_ID = Identifier.of("noellesroles", "corrupt_cop");
    public static final Identifier ENGINEER_ROLE_ID = Identifier.of("noellesroles", "engineer");
    public static final Identifier GRAND_WITCH_ROLE_ID = Identifier.of("sparkwitch", "grand_witch");
    public static final Identifier ACCOMPLICE_ROLE_ID = Identifier.of("sparkwitch", "accomplice");
    public static final Identifier BEWITCHED_ROLE_ID = Identifier.of("sparkwitch", "bewitched");
    public static final Identifier MURDEROUS_WITCH_ROLE_ID = Identifier.of("sparkwitch", "murderous_witch");
    public static final Identifier INSIDER_ROLE_ID = Identifier.of("sparkwitch", "insider");
    public static final Identifier SAVIOR_ROLE_ID = Identifier.of("sparkwitch", "savior");
    public static final Identifier KNIGHT_ROLE_ID = Identifier.of("sparkwitch", "knight");
    public static final Identifier SEEKER_ROLE_ID = Identifier.of("sparkwitch", "seeker");
    public static final Identifier LOOSE_END_ROLE_ID = Identifier.of("wathe", "loose_end");
    public static final Identifier UNDERCOVER_ROLE_ID = Identifier.of("noellesroles", "undercover");

    // Trap rule class 1 (police): every police role and any SparkTraits Impostor holder. This single class owns the
    // trap's police-side rules (visibility today; dismantling reuses it). The live SparkFactionAPI PoliceRoles registry
    // is checked alongside this fixed list, so later police variants are covered without editing it; corrupt_cop and the
    // Insider are Jiahao-factioned but still count. Owner 2026-11-11.
    // 夹子规则第一类（警察类）：所有警职与任何带 SparkTraits 内鬼词条的玩家。夹子所有警察侧规则都归这一类（目前是可见性，
    // 拆除也复用）。实时 SparkFactionAPI PoliceRoles 注册表与此固定名单一起检查，之后新增的警察变体无需改这里；
    // 黑警与内应属嘉豪阵营但仍算数。
    private static final Set<Identifier> TRAP_POLICE_IDS = Set.of(
            VIGILANTE_ROLE_ID,
            VETERAN_ROLE_ID,
            CORRUPT_COP_ROLE_ID,
            ENGINEER_ROLE_ID,
            INSIDER_ROLE_ID,
            SAVIOR_ROLE_ID,
            KNIGHT_ROLE_ID,
            SEEKER_ROLE_ID
    );
    // Trap rule class 2: every killer-faction role plus the Loose End and NoellesRoles' Undercover, which are
    // neutral/civilian-factioned but still count (instant dismantle, poisoning). For sight, class 2 uses the police
    // line-of-sight rule; only the killer faction adds the instinct x-ray, so the Loose End and the Undercover never
    // see a trap through a wall (the Undercover's SparkStrength thermal view shows players only). Owner 2026-11-11.
    // 夹子规则第二类：所有杀手阵营职业，外加亡命徒与 NoellesRoles 卧底（属中立/平民阵营但仍算数：瞬间拆除、可淬毒）。可见性上
    // 第二类走警察的视线规则；只有杀手阵营额外拥有本能透视，所以亡命徒与卧底永远不能隔墙看见捕兽夹（卧底在 SparkStrength 里的
    // 热成像只显示玩家）。所有者 2026-11-11。
    public static boolean isTrapKiller(@Nullable Identifier roleId, boolean killerFaction) {
        return killerFaction || LOOSE_END_ROLE_ID.equals(roleId) || UNDERCOVER_ROLE_ID.equals(roleId);
    }

    // Fixed instinct viewers (the Bewitched is accomplice-like, C2); special accomplices join through
    // isInstinctTrapViewer, which reads the live registry.
    // 固定的本能观察者（魔化使属于共犯类，C2）；特殊共犯经 isInstinctTrapViewer 读取实时注册表加入。
    private static final Set<Identifier> INSTINCT_VIEWERS = Set.of(
            GRAND_WITCH_ROLE_ID,
            ACCOMPLICE_ROLE_ID,
            BEWITCHED_ROLE_ID,
            MURDEROUS_WITCH_ROLE_ID
    );
    private HunterRules() {
    }

    public static boolean canReload(int loadedShells, boolean coolingDown, long currentTick, long reloadWindowUntil) {
        if (coolingDown || loadedShells < 0 || loadedShells >= MAX_SHELLS) {
            return false;
        }
        return loadedShells == 0 || currentTick <= reloadWindowUntil;
    }

    public static long reloadWindowAfterLoading(
            int previouslyLoadedShells,
            long currentTick,
            long existingWindowUntil
    ) {
        return previouslyLoadedShells == 0 ? currentTick + SECOND_SHELL_WINDOW_TICKS : existingWindowUntil;
    }

    public static int cooldownAfterShot(int remainingShells) {
        return remainingShells <= 0 ? EMPTY_COOLDOWN_TICKS : FOLLOW_UP_COOLDOWN_TICKS;
    }

    /**
     * Stored shell stack (bottom to top) cleaned for use: only '0' / '1' digits, at most {@link #MAX_SHELLS} from the
     * bottom, so a hand-edited stack can neither overfill the gun nor name an unknown shell.
     * 清理后的已存弹药栈（自下而上）：只保留 '0' / '1'，自底部起最多 {@link #MAX_SHELLS} 发，手动编辑的数据既不能超装，
     * 也不能写入未知弹药。
     */
    public static String sanitizeShellStack(String stored) {
        StringBuilder shells = new StringBuilder(MAX_SHELLS);
        for (int index = 0; index < stored.length() && shells.length() < MAX_SHELLS; index++) {
            char shell = stored.charAt(index);
            if (shell == '0' + SHELL_BUCKSHOT || shell == '0' + SHELL_SLUG) {
                shells.append(shell);
            }
        }
        return shells.toString();
    }

    /** Top shell of a cleaned stack, or {@link #SHELL_NONE}. / 清理后弹药栈的栈顶弹药，空时为 {@link #SHELL_NONE}。 */
    public static int topShell(String shells) {
        return shells.isEmpty() ? SHELL_NONE : shells.charAt(shells.length() - 1) - '0';
    }

    /**
     * Buckshot kill count (2026-11-01): one victim per full three pellet rays that hit, rounded down, so 3–5 rays kill
     * one, 6–8 kill two, all 9 kill three, and 1–2 rays kill none; never more than the distinct victims hit.
     * 霰弹击杀人数（2026-11-01）：每满 3 条命中的弹丸射线击杀 1 人，向下取整，即 3–5 条击杀 1 人、6–8 条击杀 2 人、
     * 9 条击杀 3 人、1–2 条不击杀；不超过被命中的不同受害者人数。
     */
    public static int buckshotKillCount(int hitRays, int victims) {
        return Math.max(0, Math.min(hitRays / 3, victims));
    }

    public static TrapVisibility trapVisibility(
            @Nullable Identifier roleId,
            boolean throughWallViewer,
            boolean killerFactionViewer,
            boolean policeViewer,
            boolean hasLineOfSight,
            boolean instinctActive
    ) {
        return trapVisibility(roleId, throughWallViewer, killerFactionViewer, policeViewer,
                () -> hasLineOfSight, () -> instinctActive);
    }

    /**
     * Same matrix with lazy inputs: line of sight (a raycast) and instinct are read only for the viewers whose answer
     * depends on them, at most once each. Line of sight only ever separates {@code DIRECT_ONLY} from {@code HIDDEN}.
     * {@code throughWallViewer} is the dead in-match spectator, the only passive through-wall viewer since owner rule
     * 2026-11-11. {@code killerFactionViewer} and the witch side see by line of sight and through walls while instinct
     * is held, which is how a killer keeps the old x-ray. {@code policeViewer} is everyone else who may see a trap at
     * all, by line of sight only: class 1, and the two class-2 roles outside the killer faction.
     * 输入惰性求值的同一矩阵：视线（射线检测）与本能仅在答案依赖它们的观察者上读取，且各至多一次。视线只区分
     * {@code DIRECT_ONLY} 与 {@code HIDDEN}。{@code throughWallViewer} 指死亡观战者，自所有者 2026-11-11 规则起是唯一被动
     * 隔墙可见的观察者。{@code killerFactionViewer} 与魔女方靠视线看见，按住本能时隔墙可见，杀手由此保留旧的透视。
     * {@code policeViewer} 是其余能看见捕兽夹的人，只靠视线：第一类，以及第二类中不属于杀手阵营的两个职业。
     */
    public static TrapVisibility trapVisibility(
            @Nullable Identifier roleId,
            boolean throughWallViewer,
            boolean killerFactionViewer,
            boolean policeViewer,
            BooleanSupplier hasLineOfSight,
            BooleanSupplier instinctActive
    ) {
        if (throughWallViewer) {
            return TrapVisibility.THROUGH_WALL;
        }
        if (isInstinctTrapViewer(roleId) || killerFactionViewer) {
            if (instinctActive.getAsBoolean()) {
                return TrapVisibility.THROUGH_WALL;
            }
            return hasLineOfSight.getAsBoolean() ? TrapVisibility.DIRECT_ONLY : TrapVisibility.HIDDEN;
        }
        if (policeViewer && hasLineOfSight.getAsBoolean()) {
            return TrapVisibility.DIRECT_ONLY;
        }
        return TrapVisibility.HIDDEN;
    }

    /**
     * Grand Witch, every accomplice (plain, special or Bewitched) and the Murderous Witch see traps through walls with
     * instinct.
     * 大魔女、所有共犯（普通、特殊或魔化使）和杀意魔女在开启本能时可隔墙看到捕兽夹。
     */
    public static boolean isInstinctTrapViewer(@Nullable Identifier roleId) {
        return roleId != null
                && (INSTINCT_VIEWERS.contains(roleId) || WitchFactionRules.isAccompliceVariantId(roleId));
    }

    /**
     * How a sneaking player with line of sight takes a trap apart (owner rule 2026-11-11). Class 2 removes it at once
     * and wins over class 1, so a killer who is also on the police list never has to channel; class 1 channels; anyone
     * else cannot dismantle at all (the witch side included, as before).
     * 潜行且直视的玩家如何拆除夹子（所有者 2026-11-11 规则）。第二类瞬间拆除且优先于第一类，因此同时在警察名单上的杀手不必
     * 引导；第一类需要引导；其余玩家完全不能拆除（魔女方照旧不能）。
     */
    public static TrapDismantle trapDismantle(boolean trapKiller, boolean trapPolice) {
        if (trapKiller) {
            return TrapDismantle.INSTANT;
        }
        return trapPolice ? TrapDismantle.CHANNEL : TrapDismantle.NONE;
    }

    /** A channel lives while the last press is at most the press timeout old. / 上次按键未超过按键超时，引导即存续。 */
    public static boolean isDismantleChannelAlive(long now, long lastPressTick) {
        return now - lastPressTick <= DISMANTLE_PRESS_TIMEOUT_TICKS;
    }

    /** A channel is complete once it has run its full length. / 引导跑满全程即完成。 */
    public static boolean isDismantleChannelComplete(long now, long startTick) {
        return now - startTick >= DISMANTLE_CHANNEL_TICKS;
    }

    /**
     * The trap-kill reward goes to a known placer other than the victim, and only for a death inside the catch window.
     * 夹杀奖励只发给已知且并非受害者本人的放置者，且仅限在夹住时间窗内的死亡。
     */
    public static boolean paysTrapKillReward(
            boolean caughtWithinWindow,
            @Nullable UUID placerUuid,
            @Nullable UUID victimUuid
    ) {
        return caughtWithinWindow && placerUuid != null && !placerUuid.equals(victimUuid);
    }

    /**
     * The police half of {@link #trapVisibility}: {@code registeredPolice} is the live SparkFactionAPI PoliceRoles
     * answer, so every police variant counts even when this build does not name it.
     * {@link #trapVisibility} 的警察一半：{@code registeredPolice} 是实时 SparkFactionAPI PoliceRoles 的结果，因此即使本
     * 构建没有点名，任何警察变体也算数。
     */
    public static boolean isTrapPolice(@Nullable Identifier roleId, boolean registeredPolice) {
        return registeredPolice || (roleId != null && TRAP_POLICE_IDS.contains(roleId));
    }

    /**
     * Active Wraiths stay outside living players' traps; only the bound killer's own trap may catch its Vendetta.
     * 激活冤魂不会触发生者的捕兽夹；仅绑定凶手自己放置的捕兽夹可以夹住其仇杀客。
     */
    public static boolean canTrapCatch(boolean aliveAndSurvival, boolean activeWraith, boolean ownerIsBoundKiller) {
        return aliveAndSurvival && (!activeWraith || ownerIsBoundKiller);
    }

    public static TrapPoisonRewards trapPoisonRewards() {
        return new TrapPoisonRewards(POISONER_REWARD, PLACER_REWARD);
    }

    public enum TrapDismantle {
        NONE,
        INSTANT,
        CHANNEL
    }

    public enum TrapVisibility {
        HIDDEN,
        DIRECT_ONLY,
        THROUGH_WALL
    }

    public record TrapPoisonRewards(int poisoner, int placer) {
        public int samePlayerTotal() {
            return poisoner + placer;
        }
    }
}
