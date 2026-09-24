package dev.caecorthus.sparkwitch.roles.killer.bellringer;

import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Current Wathe match id used to reject stale cross-round Bell Ringer state. / 用于拒绝跨局残留敲钟人状态的当前对局 id。 */
final class BellRingerMatch {
    private BellRingerMatch() {
    }

    static @Nullable UUID currentId() {
        GameRecordManager.MatchRecord match = GameRecordManager.getCurrentMatch();
        return GameRecordManager.hasActiveMatch() && match != null ? match.getMatchId() : null;
    }
}
