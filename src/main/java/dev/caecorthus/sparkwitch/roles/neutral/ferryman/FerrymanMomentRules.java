package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendWinExclusion;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

/**
 * Gates of the Soul Moment: whether it may start, who may rise as a Lost Soul, how many rise, which souls still count
 * and how many players are still outside it. Liveness is always Wathe's own answer
 * ({@link GameFunctions#isPlayerPlayingAndAlive}), never the game mode: a Taotie-swallowed soul or a Rift Gate occupant
 * is an alive spectator and stays in the moment.
 * 亡魂时刻的判定：能否开始、谁能作为迷失的灵魂复活、复活几名、哪些灵魂仍然有效，以及时刻之外还有多少存活者。存活与否
 * 一律以 Wathe 自身的结果（{@link GameFunctions#isPlayerPlayingAndAlive}）为准，从不看游戏模式：被饕餮吞下的灵魂或
 * 传送门内的占位者是“存活的旁观者”，仍留在时刻中。
 */
public final class FerrymanMomentRules {
    private FerrymanMomentRules() {
    }

    public static boolean canStart(boolean active, int eligibleSouls) {
        return !active && eligibleSouls > 0;
    }

    /**
     * How many Lost Souls one purchase raises: one per {@link FerrymanRules#SOUL_PLAYERS_PER_BODY} round participants
     * (players holding a Wathe role, dead or alive), never more than the bodies waiting.
     * 一次购买复活的迷失灵魂数量：本局参与者（持有 Wathe 职业的玩家，无论死活）每
     * {@link FerrymanRules#SOUL_PLAYERS_PER_BODY} 人一具，不超过等待中的遗体数。
     */
    public static int soulTarget(int roundParticipants, int eligibleSouls) {
        return Math.min(FerrymanRules.soulCount(roundParticipants), Math.max(0, eligibleSouls));
    }

    /**
     * Who may rise: a ferried participant that Wathe holds dead, that is not a Charon, whose death SparkTraits Last
     * Stand has not taken over, and that is not an active Wraith.
     * 谁能复活：已被摆渡、Wathe 记为死亡、不是摆渡人、死亡未被 SparkTraits 背水一战接管，且不是活跃冤魂的参与者。
     *
     * <p>Wraith default (owner answer pending): a ferried civilian that became a Wraith stays in Wathe's dead set but
     * is already back in play with Wraith state (muted, harmless, promotable), so reviving it would leave a Lost Soul
     * that cannot fight and whose role a Wraith promotion could overwrite. It is skipped, failing closed; switching to
     * "clear the Wraith state and revive" changes only the {@code activeWraith} term below.
     * 冤魂默认处理（待所有者答复）：变成冤魂的已摆渡平民仍在 Wathe 死者集合中，但已带着冤魂状态（禁言、无害、可晋升）
     * 回到场上；若复活它，迷失的灵魂将无法战斗，职业还可能被冤魂晋升覆盖。因此失败关闭、直接跳过；日后改为“清除冤魂
     * 状态再复活”只需修改下方的 {@code activeWraith} 一项。</p>
     */
    public static boolean isEligibleSoul(boolean ferried, boolean participant, boolean deadInWathe, boolean ferryman,
                                         boolean lastStandHeld, boolean activeWraith) {
        return ferried && participant && deadInWathe && !ferryman && !lastStandHeld && !activeWraith;
    }

    /**
     * A Lost Soul is alive exactly when Wathe says so; game mode and health are never read.
     * 迷失的灵魂是否存活完全以 Wathe 为准；从不读取游戏模式与血量。
     */
    public static boolean isSoulAlive(@Nullable ServerPlayerEntity soul) {
        return soul != null && GameFunctions.isPlayerPlayingAndAlive(soul);
    }

    /**
     * Owner decision D1 (and the Black Raven, Stowaway and untransformed Schrödinger's Cat settlement rules): a
     * player counts as a living outsider only through {@link FiendWinExclusion#countsAsAlive}, exactly like the Witch
     * win, so a dormant Fiend that only a train fall can kill never keeps the moment (which blocks every faction win
     * until its outsiders are gone) open until the clock runs out.
     * 所有者决定 D1（以及黑羽鸦、偷渡客与未转化薛定谔之猫的结算规则）：玩家只有经过
     * {@link FiendWinExclusion#countsAsAlive} 才算存活的外部者，与魔女胜利一致；因此只有坠车才能杀死的休眠魔人不会让
     * 时刻（在外部者清零前阻断一切阵营胜利）一直拖到时间耗尽。
     */
    public static boolean isLivingOutsider(boolean countsAsAlive, boolean owner, boolean soul) {
        return countsAsAlive && !owner && !soul;
    }

    /** Living players that are neither the owner nor a Lost Soul. / 既不是拥有者也不是迷失灵魂的存活玩家。 */
    public static int outsidersAlive(ServerWorld world, FerrymanMomentWorldComponent moment) {
        int alive = 0;
        for (ServerPlayerEntity player : world.getPlayers()) {
            boolean countsAsAlive = FiendWinExclusion.countsAsAlive(GameFunctions.isPlayerPlayingAndAlive(player), player);
            if (isLivingOutsider(countsAsAlive, moment.owns(player.getUuid()), moment.isSoul(player.getUuid()))) {
                alive++;
            }
        }
        return alive;
    }

    /** What one moment tick does once its owner was checked. / 检查拥有者之后，时刻每 tick 的处理。 */
    public enum TickAction {
        /** The Charon is gone: the souls die with him. / 卡戎已不在：灵魂随他而去。 */
        END_WITH_SOULS,
        /** No soul was ever verified alive: a revival failure, never a loss. / 没有灵魂被确认复活：复活失败而非战败。 */
        END_WITHOUT_DEATH,
        /** Every soul is down: the Charon dies with them. / 灵魂全部倒下：卡戎随之死亡。 */
        CHARON_FOLLOWS,
        CONTINUE
    }

    public static TickAction tickAction(boolean ownerActive, boolean soulsEverAlive, int soulsAlive) {
        if (!ownerActive) {
            return TickAction.END_WITH_SOULS;
        }
        if (!soulsEverAlive) {
            return TickAction.END_WITHOUT_DEATH;
        }
        return soulsAlive <= 0 ? TickAction.CHARON_FOLLOWS : TickAction.CONTINUE;
    }
}
