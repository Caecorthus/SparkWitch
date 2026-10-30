package dev.caecorthus.sparkwitch.roles.civilian.knight.duel;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** All Knight duel numbers and ids. / 骑士决斗的全部数值与 id。 */
public final class KnightDuelRules {
    public static final int COST = 150;
    public static final int DUEL_TICKS = 20 * 120;
    /** How often the weapon seal is re-raised and the duel state re-checked. / 武器封印重新抬高与决斗状态复查的间隔。 */
    public static final int REFRESH_INTERVAL_TICKS = 10;
    /** The Knight's duel Speed II. / 骑士决斗期间的速度 II。 */
    public static final int KNIGHT_SPEED_AMPLIFIER = 1;
    /**
     * How far past the seal's own floor a sealed item must still be cooling at the end before restore treats the
     * excess as someone else's penalty. Covers the phase skew between world time and the player's cooldown clock (a
     * seal written from the challenge packet runs one tick long); half a second of a foreign penalty is forgiven.
     * 决斗结束时，被封印物品的冷却须超出封印自身下限多少，恢复才把超出部分视为他人施加的惩罚。用于吸收世界时间与玩家冷却
     * 时钟之间的相位差（在挑战数据包中写入的封印会多出一刻）；他人惩罚中不足半秒的部分会被忽略。
     */
    public static final int SEAL_FLOOR_SLACK_TICKS = 10;
    /** Wathe's knife ray length. / Wathe 小刀射线长度。 */
    public static final double KNIFE_RANGE = 3.0D;
    public static final int TITLE_FADE_IN = 10;
    public static final int TITLE_STAY = 40;
    public static final int TITLE_FADE_OUT = 40;
    public static final Formatting KNIGHT_COLOR = Formatting.AQUA;
    public static final Formatting TARGET_COLOR = Formatting.RED;
    public static final String KNIGHT_TEAM = "sparkwitch_duel_knight";
    public static final String TARGET_TEAM = "sparkwitch_duel_target";
    public static final Identifier SURVIVAL_MASTER_ID = Identifier.of("noellesroles", "survival_master");
    public static final Identifier MASTER_KEY_ID = Identifier.of("noellesroles", "master_key");
    public static final Identifier NEUTRAL_MASTER_KEY_ID = Identifier.of("noellesroles", "neutral_master_key");
    /**
     * SparkStrength tablet, a Knight start item in the spec and not a weapon; kept as a plain id so SparkStrength stays
     * optional. / SparkStrength 平板：规格中骑士的开局物品，不是武器；以纯 id 保存，SparkStrength 仍为可选。
     */
    public static final Identifier TABLET_ID = Identifier.of("sparkstrength", "tablet");
    public static final Identifier MANBA_SOUND_ID = SparkWitch.id("duel.manba");
    public static final Identifier SF_SOUND_ID = SparkWitch.id("duel.sf");
    /**
     * SparkFactionAPI player-affect action asked before a challenge (the duel seals the target's items).
     * 发起挑战前询问 SparkFactionAPI 玩家影响策略所用的行为 id（决斗会封印对手的物品）。
     */
    public static final Identifier DUEL_ACTION_ID = SparkWitch.id("knight_duel");

    private KnightDuelRules() {
    }

    /**
     * What a sealed item's cooldown must read when the duel ends: its pre-duel remaining time minus the time the duel
     * held it, never below zero.
     * 决斗结束时被封印物品应有的冷却：决斗前剩余时间减去决斗占用的时间，最低为 0。
     */
    public static int restoredRemainingTicks(long naturalEndTick, long now) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, naturalEndTick - now));
    }

    /**
     * What a sealed item's cooldown must read when the duel ends, given what it reads now and the seal's own floor
     * (the duel time still left, since every re-raise targets the duel's end). A cooldown still running more than
     * {@link #SEAL_FLOOR_SLACK_TICKS} past that floor was raised by someone else during the duel (Control Expert stun,
     * Fiend aura, Abyss suppression, Saint Karma) and is kept when it is longer than the pre-duel remainder. A penalty
     * the seal already covered never wrote anything, so there is nothing of it to keep.
     * 决斗结束时被封印物品应有的冷却，依据其当前冷却与封印自身的下限（决斗剩余时间：每次重新抬高都以决斗结束为目标）。
     * 比该下限多出 {@link #SEAL_FLOOR_SLACK_TICKS} 以上仍在运行的冷却，是决斗期间他人施加的（控场专家眩晕、魔人光环、
     * 聆渊压制、圣徒业报），若长于决斗前剩余部分则保留。被封印覆盖的惩罚从未写入任何内容，因此无从保留。
     */
    public static int restoredRemainingTicks(long naturalEndTick, long now, int currentTicks, int sealFloorTicks) {
        int natural = restoredRemainingTicks(naturalEndTick, now);
        boolean raisedByOthers = (long) currentTicks > (long) Math.max(0, sealFloorTicks) + SEAL_FLOOR_SLACK_TICKS;
        return raisedByOthers ? Math.max(natural, currentTicks) : natural;
    }

    /**
     * Duration a pre-duel effect resumes with when the duel ends: an infinite one stays infinite, a timed one loses the
     * time the duel held it; 0 means it ran out.
     * 决斗结束时决斗前效果恢复的时长：无限效果保持无限，有时限的减去决斗占用的时间；0 表示已耗尽。
     */
    public static int restoredEffectTicks(int previousDuration, boolean infinite, int elapsed) {
        return infinite ? StatusEffectInstance.INFINITE : Math.max(0, previousDuration - elapsed);
    }
}
