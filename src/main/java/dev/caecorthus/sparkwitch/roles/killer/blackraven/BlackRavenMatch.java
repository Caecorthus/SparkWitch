package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

final class BlackRavenMatch {
    private BlackRavenMatch() {
    }

    static @Nullable UUID currentId() {
        GameRecordManager.MatchRecord match = GameRecordManager.getCurrentMatch();
        return GameRecordManager.hasActiveMatch() && match != null ? match.getMatchId() : null;
    }
}
