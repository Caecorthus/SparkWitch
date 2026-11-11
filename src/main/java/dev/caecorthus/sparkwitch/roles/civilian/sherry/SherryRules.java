package dev.caecorthus.sparkwitch.roles.civilian.sherry;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * 橘雪莉 (Tachibana Sherry, PR #206) pure rules: a passenger detective with real sanity and 1.5x stamina who earns
 * coins and mana, deduces killers next to a corpse (推演, shared ability key), carries her own 巨力魔法 (Role Skill 2)
 * and hands out detective badges. Owner rules 2026-11-11: no Mighty Force exclusivity with the Apprentice, a deduction
 * only succeeds on a killer (everyone else is a failure), no mutual reveal with other roles, and a badge holder's death
 * reveals the killer periodically instead of naming them.
 * 橘雪莉（PR #206）的纯规则：真实理智、1.5 倍体力的乘客侦探，自然获得金币与魔力，在尸体旁推演凶手（推演，共享技能键），
 * 拥有自己的巨力魔法（职业技能 2），并可赠出侦探徽章。所有者规则 2026-11-11：巨力不与预备魔女互斥；推演只有选中杀手才算成功
 * （其余一律失败）；不与其他职业互相透视；徽章持有者死亡后周期性高亮凶手，而不是告知其名字与职业。
 */
public final class SherryRules {
    public static final Identifier ROLE_ID = SparkWitch.id("sherry");
    /** Identity colour, also the colour of her reveals. / 身份色，也是揭示高亮的颜色。 */
    public static final int COLOR = 0x65D0EE;

    /** Replay and forced-cooldown ids. / 回放与强制冷却使用的 ID。 */
    public static final Identifier DEDUCTION_SKILL_ID = SparkWitch.id("sherry_deduction");
    public static final Identifier MIGHTY_FORCE_SKILL_ID = SparkWitch.id("sherry_mighty_force");
    public static final Identifier BADGE_SKILL_ID = SparkWitch.id("sherry_badge");

    public static final Identifier BADGE_ITEM_ID = SparkWitch.id("sherry_badge");
    public static final Identifier NOTE_ITEM_ID = SparkWitch.id("sherry_note");
    public static final String BADGE_ENTRY_ID = "sparkwitch:sherry_badge";
    /** Custom-data key of the note: the dead badge holder's name. / 纸条自定义数据键：死去的徽章持有者名字。 */
    public static final String NOTE_VICTIM_KEY = "victim_name";

    /** 1.5x the passenger sprint time. / 乘客体力的 1.5 倍。 */
    public static final int MAX_SPRINT_TICKS = GameConstants.getInTicks(0, 15);

    /** Both skills start the round on this cooldown. / 两个技能开局都进入这一冷却。 */
    public static final int OPENING_COOLDOWN_TICKS = GameConstants.getInTicks(0, 45);

    // ---- Income ----
    public static final int PASSIVE_COINS = 5;
    public static final int PASSIVE_INTERVAL_TICKS = GameConstants.getInTicks(0, 10);
    public static final int TASK_COINS = 50;
    public static final int TASK_MANA = 10;
    /** One mana every 5 s (2 per 10 s), up to the default natural cap. / 每 5 秒 1 点魔力（每 10 秒 2 点），不超过默认自然上限。 */
    public static final int MANA_REGENERATION_INTERVAL_TICKS = GameConstants.getInTicks(0, 5);

    // ---- Sanity ----
    /** Below this light level Wathe's task drain is halved. / 亮度低于此值时 Wathe 的任务理智消耗减半。 */
    public static final int DIM_LIGHT_LEVEL = 10;
    public static final float DIM_LIGHT_DRAIN_SCALE = 0.5F;

    // ---- 推演 ----
    public static final int DEDUCTION_COST = 100;
    public static final int DEDUCTION_COOLDOWN_TICKS = GameConstants.getInTicks(0, 30);
    public static final double DEDUCTION_RANGE = 60.0D;
    /** A corpse must be this close and in front of her. / 尸体必须在此距离内且位于她面前。 */
    public static final double CORPSE_REACH = 3.0D;
    /** Half-angle of the "aiming at the corpse" cone, as a cosine (60 degrees). / “对准尸体”视锥半角的余弦（60 度）。 */
    public static final double CORPSE_AIM_COSINE = 0.5D;
    /** Within this distance of her feet the corpse counts whatever she looks at. / 距她脚下此距离内无论视线朝向都算对准。 */
    public static final double CORPSE_TOUCH_DISTANCE = 1.25D;
    public static final float WRONG_TARGET_MOOD_LOSS = 0.20F;
    public static final int WRONG_TARGET_EFFECT_TICKS = GameConstants.getInTicks(0, 3);
    /** Slowness II. / 缓慢 II。 */
    public static final int WRONG_TARGET_SLOWNESS_AMPLIFIER = 1;

    // ---- Reveals ----
    public static final int REVEAL_TICKS = GameConstants.getInTicks(0, 5);
    /** A deduced killer lights up every 20 s. / 被推演出的杀手每 20 秒亮一次。 */
    public static final int DEDUCTION_REVEAL_INTERVAL_TICKS = GameConstants.getInTicks(0, 20);
    /** The killer of a badge holder lights up every 40 s. / 杀死徽章持有者的凶手每 40 秒亮一次。 */
    public static final int BADGE_REVEAL_INTERVAL_TICKS = GameConstants.getInTicks(0, 40);
    /** Hard cap on simultaneously tracked targets (also the sync bound). / 同时追踪目标的硬上限（也是同步上限）。 */
    public static final int MAX_TRACKED = 32;

    // ---- 侦探徽章 ----
    public static final int BADGE_PRICE = 50;
    public static final int BADGE_STOCK = 2;
    /** Gold outline of a living badge holder, for Sherry only. / 存活徽章持有者的金色高亮，仅雪莉可见。 */
    public static final int BADGE_GLOW = 0xFFD700;

    /** Roster bound of the deduction panel. / 推演面板候选上限。 */
    public static final int MAX_CANDIDATES = 64;

    private SherryRules() {
    }

    public static boolean isSherryId(@Nullable Identifier roleId) {
        return ROLE_ID.equals(roleId);
    }

    public static boolean isSherry(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    /**
     * Owner rule 2026-11-11: only a target on the killer faction is a correct deduction; civilians, police, neutrals,
     * the witch faction and Team Jiahao are all failures and cost the same penalty.
     * 所有者规则 2026-11-11：只有杀手阵营的目标才算推演正确；平民、警察、中立、魔女阵营与嘉豪阵营一律失败并受同样的惩罚。
     */
    public static boolean isCorrectDeduction(@Nullable Identifier effectiveFaction) {
        return FactionIds.KILLER.equals(effectiveFaction);
    }

    /** Wathe's per-task drain constant for Sherry: halved in dim light. / 雪莉的单任务理智消耗：暗处减半。 */
    public static float scaleMoodDrain(float drain, boolean sherry, int lightLevel) {
        return sherry && lightLevel < DIM_LIGHT_LEVEL ? drain * DIM_LIGHT_DRAIN_SCALE : drain;
    }

    /**
     * Whether a corpse at {@code corpse} counts as aimed at: close enough to her {@code feet} to be standing over it
     * (a corpse lies on the floor, so that is measured from the feet, never from the eyes 1.62 above), or within reach
     * of her {@code eye} and inside the cone around {@code look}.
     * 位于 {@code corpse} 的尸体是否算“对准”：距她的 {@code feet} 近到就站在它旁边（尸体躺在地上，因此从脚下而不是从高出
     * 1.62 的眼睛量起），或在 {@code eye} 的触及范围内且位于 {@code look} 的视锥中。
     */
    public static boolean aimsAtCorpse(Vec3d eye, Vec3d look, Vec3d feet, Vec3d corpse) {
        if (corpse.squaredDistanceTo(feet) <= CORPSE_TOUCH_DISTANCE * CORPSE_TOUCH_DISTANCE) {
            return true;
        }
        Vec3d offset = corpse.subtract(eye);
        double distanceSquared = offset.lengthSquared();
        if (distanceSquared > CORPSE_REACH * CORPSE_REACH) {
            return false;
        }
        double lookLength = look.length();
        if (lookLength < 1.0E-6) {
            return false;
        }
        return offset.dotProduct(look) / (Math.sqrt(distanceSquared) * lookLength) >= CORPSE_AIM_COSINE;
    }

    /** Coins are never paid to a SparkTraits Impostor or on an unknown answer (Medium/Accountant precedent). */
    /** SparkTraits 内鬼或无法判定时不发任务金币（通灵师/会计先例）。 */
    public static boolean receivesTaskMoney(@Nullable Boolean impostor) {
        return Boolean.FALSE.equals(impostor);
    }

    /**
     * Shared earning gate: an ACTIVE round, the raw role, playing and alive, never an alive spectator or creative and
     * never a restricted Wraith.
     * 共用的收入门槛：对局 ACTIVE、真实职业、参与且存活，不是存活旁观者或创造模式，也不是受限冤魂。
     */
    public static boolean canEarn(boolean active, boolean playingAndAlive, @Nullable Role role, boolean spectator,
                                  boolean creative, boolean wraithRestricted) {
        return active && playingAndAlive && isSherry(role) && !spectator && !creative && !wraithRestricted;
    }
}
