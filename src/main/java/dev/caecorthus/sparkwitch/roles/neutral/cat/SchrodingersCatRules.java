package dev.caecorthus.sparkwitch.roles.neutral.cat;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Frozen 薛定谔之猫 (Schrödinger's Cat) contract, pure and side-effect free. The cat is a Wathe-native neutral. The
 * first lethal hit that really commits its death (every shield, vest, elixir and revival already had its turn) does
 * not kill it: it vanishes for {@link #SPECTRE_TICKS}, then gets up where it was hit with the killer's side (owner
 * 2026-11-03/04). An untransformed cat never wins and never blocks a win; a transformed one simply holds its new role.
 * 冻结的薛定谔之猫契约，纯函数、无副作用。猫是 Wathe 原生中立。第一次真正落定死亡的致命一击（所有护盾、防弹衣、药剂与
 * 复活都已先行结算）不会杀死它：它消失 {@link #SPECTRE_TICKS}，随后在被击中处起身并倒向凶手的阵营（所有者 2026-11-03/04）。
 * 未转化的猫永不获胜、也不阻挡任何胜利；转化后的猫只是持有新身份。
 */
public final class SchrodingersCatRules {
    public static final Identifier ROLE_ID = SparkWitch.id("schrodingers_cat");
    public static final int COLOR = 0xB07CD8;

    /** Coins for each finished task. / 每完成一个任务获得的金币。 */
    public static final int TASK_MONEY_REWARD = 25;

    /** The vanished window between the lethal hit and the new identity: 3 s. / 致命一击到获得新身份之间的消失时长：3 秒。 */
    public static final int SPECTRE_TICKS = GameConstants.getInTicks(0, 3);

    /** SparkFactionAPI replay cause of the role change. / 身份变更在 SparkFactionAPI 回放中的原因。 */
    public static final Identifier TRANSFORM_CAUSE = SparkWitch.id("schrodingers_cat_transform");

    /** Witch faction id, a literal like the Insider's Corrupt Cop id. / 魔女阵营 id，与内应的黑警 id 一样使用字面量。 */
    static final Identifier WITCH_FACTION_ID = Identifier.of("sparkwitch", "witch");

    /**
     * Deaths that kill the cat outright even with a killer: falling off the train (shoved or not) and the NoellesRoles
     * voodoo curse (PR author's spec, unchanged).
     * 即使有凶手也直接杀死猫的死因：掉出列车（无论是否被推）与 NoellesRoles 巫毒诅咒（沿用 PR 作者的规格）。
     */
    static final Set<Identifier> NO_TRANSFORM_REASONS = Set.of(
            GameConstants.DeathReasons.FELL_OUT_OF_TRAIN,
            Identifier.of("noellesroles", "voodoo")
    );

    /** The side a transformed cat joins. / 转化后的猫加入的阵营。 */
    public enum Side {
        /** Corrupt Cop or Insider killer: the cat becomes an Insider. / 黑警或内应所杀：猫变为内应。 */
        TEAM_JIAHAO,
        /** Any Witch-faction member killer: the cat becomes a Bewitched (owner 2026-11-04). / 魔女阵营成员所杀：猫变为魔化使（所有者 2026-11-04）。 */
        WITCH,
        /** Effective killer faction: the cat becomes a plain killer. / 有效阵营为杀手：猫变为普通杀手。 */
        KILLER,
        /** Effective civilian faction: the cat becomes a random unheld police role. / 有效阵营为乘客：猫变为随机未被占用的警职。 */
        CIVILIAN,
        /** Neutral, unknown or no killer: the cat dies. / 中立、未知或没有凶手：猫死亡。 */
        NONE
    }

    private SchrodingersCatRules() {
    }

    /**
     * The killer's side (owner 2026-11-04): Team Jiahao by exact role, then Witch-faction membership, then the killer's
     * EFFECTIVE faction, so a trait flip such as the Impostor counts (a civilian Impostor sides with the killers) and
     * the Apprentice Witch (an innocent role) sides with the civilians. Everything else is {@link Side#NONE}.
     * 凶手的阵营（所有者 2026-11-04）：先按精确职业判断嘉豪阵营，再判断魔女阵营成员，最后看凶手的有效阵营，因此内鬼等词条
     * 翻转同样生效（乘客内鬼站在杀手一边），预备魔女（无辜职业）站在乘客一边。其余一律为 {@link Side#NONE}。
     */
    public static Side sideOf(boolean teamJiahao, boolean witchMember, @Nullable Identifier effectiveFaction) {
        if (teamJiahao) {
            return Side.TEAM_JIAHAO;
        }
        if (witchMember || WITCH_FACTION_ID.equals(effectiveFaction)) {
            return Side.WITCH;
        }
        if (FactionIds.KILLER.equals(effectiveFaction)) {
            return Side.KILLER;
        }
        if (FactionIds.CIVILIAN.equals(effectiveFaction)) {
            return Side.CIVILIAN;
        }
        return Side.NONE;
    }

    /**
     * Whether a committing death turns into the vanish: a running round, another player as the killer (owner: deaths
     * with no killer still kill), a reason outside {@link #NO_TRANSFORM_REASONS} and a side to join. Forced kills
     * (bell toll, time curse…) count like any other (owner 2026-11-03).
     * 落定的死亡是否转为消失：对局进行中、凶手是另一名玩家（所有者：没有凶手的死亡照常致死）、死因不在
     * {@link #NO_TRANSFORM_REASONS} 中且有可加入的阵营。强制击杀（钟声、时间诅咒等）同样计入（所有者 2026-11-03）。
     */
    public static boolean transforms(boolean roundActive, boolean distinctKiller, @Nullable Identifier deathReason,
                                     Side side) {
        return roundActive && distinctKiller && deathReason != null && !NO_TRANSFORM_REASONS.contains(deathReason)
                && side != Side.NONE;
    }

    public static boolean isCatId(@Nullable Identifier roleId) {
        return ROLE_ID.equals(roleId);
    }
}
