package dev.caecorthus.sparkwitch.util;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Owner rule (2026-10-04) for the heavy weapons any holder may use: the Anti-Tank Launcher (with its shells) and the
 * Shriek Gun here, and the SparkStrength M67. Use never checks the role. A living participant of an ACTIVE match fires
 * a full match shot. A non-participant fires a presentation-only shot that plays its sound and particles but hits no
 * one: this covers no running match, the STARTING/STOPPING transitions, and a lobby player while a match is ACTIVE. A
 * dead participant of an ACTIVE match, including a Wraith, is refused, so nothing from inside a live match leaks. The
 * bound-item rules (never dropped, stripped from non-owners) bind only match participants; everyone else is a free
 * holder whose copy no sweep touches.
 * 所有者规则（2026-10-04），适用于任何持有者都能使用的重武器：本模组的反坦克炮筒（含炮弹）与啸音铳，以及 SparkStrength 的
 * M67。使用从不检查职业。ACTIVE 对局中存活的参与者打出完整的对局射击。非参与者打出仅表现的射击：播放声音与粒子，但不命中
 * 任何人；这包括没有对局、STARTING/STOPPING 过渡阶段，以及对局 ACTIVE 时身在大厅的玩家。ACTIVE 对局中已死亡的参与者
 * （含冤魂）被拒绝，因此进行中的对局里不会漏出任何表现。绑定物品规则（永不掉落、从非持有职业者身上收走）只约束对局参与者；
 * 其他人都是自由持有者，任何清扫都不触碰其物品。
 */
public final class OffMatchUse {
    private OffMatchUse() {
    }

    /** How one use resolves. / 单次使用的结算方式。 */
    public enum Mode {
        /** Full in-match behaviour (effects, devices, rewards, replay). / 完整的对局内行为（效果、设备、奖励、回放）。 */
        MATCH,
        /** Sound and particles only; no player, device or replay is touched. / 仅声音与粒子；不触碰任何玩家、设备或回放。 */
        PRESENTATION,
        /** A dead participant of an ACTIVE match. / ACTIVE 对局中已死亡的参与者。 */
        REFUSED
    }

    /**
     * Pure decision. {@code liveParticipant} is the caller's own "alive and playing" test (the Shriek Gun also
     * excludes spectators and Wraiths); it is read only for a participant of an ACTIVE round.
     * 纯判定。{@code liveParticipant} 是调用方自己的“存活且在局”判定（啸音铳还排除旁观者与冤魂）；只对 ACTIVE 对局的参与者
     * 读取。
     */
    public static Mode mode(boolean roundActive, boolean participant, boolean liveParticipant) {
        if (!roundActive || !participant) {
            return Mode.PRESENTATION;
        }
        return liveParticipant ? Mode.MATCH : Mode.REFUSED;
    }

    /**
     * {@link #mode(boolean, boolean, boolean)} from Wathe state, with Wathe {@code isPlayerPlayingAndAlive} as the live
     * test. / 依据 Wathe 状态的 {@link #mode(boolean, boolean, boolean)}，以 Wathe {@code isPlayerPlayingAndAlive} 作为存活判定。
     */
    public static Mode mode(PlayerEntity player) {
        return mode(player, GameFunctions.isPlayerPlayingAndAlive(player));
    }

    /** As {@link #mode(PlayerEntity)} with the caller's live test. / 同 {@link #mode(PlayerEntity)}，使用调用方的存活判定。 */
    public static Mode mode(PlayerEntity player, boolean liveParticipant) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return mode(game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                game.hasAnyRole(player.getUuid()), liveParticipant);
    }

    /**
     * Bound-item scope: a participant (alive or dead) of a running match, ACTIVE or STOPPING. Everyone else is a free
     * holder: the periodic sweeps neither strip nor grant their copies, and a refused drop goes back into their
     * inventory. Wathe's own resets still clear inventories at match start and end.
     * 绑定物品的约束范围：进行中对局（ACTIVE 或 STOPPING）的参与者（存活或已死亡）。其他人都是自由持有者：定期清扫既不收走也
     * 不补发其物品，被拒绝的丢弃会放回其背包。Wathe 自身的重置仍会在对局开始与结束时清空背包。
     */
    public static boolean isMatchParticipant(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return game.isRunning() && game.hasAnyRole(player.getUuid());
    }
}
