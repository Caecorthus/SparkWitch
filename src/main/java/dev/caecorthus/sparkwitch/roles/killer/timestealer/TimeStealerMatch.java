package dev.caecorthus.sparkwitch.roles.killer.timestealer;

import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Current Wathe match id used to reject stale cross-round Time Stealer state. / 用于拒绝跨局残留窃时者状态的当前对局 id。 */
final class TimeStealerMatch {
    private TimeStealerMatch() {
    }

    static @Nullable UUID currentId() {
        GameRecordManager.MatchRecord match = GameRecordManager.getCurrentMatch();
        return GameRecordManager.hasActiveMatch() && match != null ? match.getMatchId() : null;
    }
}
