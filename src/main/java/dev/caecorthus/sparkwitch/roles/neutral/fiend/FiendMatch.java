package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.doctor4t.wathe.record.GameRecordManager;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Current Wathe match id binding Fiend state to one round. / 将魔人状态绑定到单一回合的当前 Wathe 对局 id。 */
final class FiendMatch {
    private FiendMatch() {
    }

    /** Server only; {@code null} between rounds. / 仅服务端；回合之间为 {@code null}。 */
    static @Nullable UUID currentId() {
        GameRecordManager.MatchRecord match = GameRecordManager.getCurrentMatch();
        return GameRecordManager.hasActiveMatch() && match != null ? match.getMatchId() : null;
    }
}
