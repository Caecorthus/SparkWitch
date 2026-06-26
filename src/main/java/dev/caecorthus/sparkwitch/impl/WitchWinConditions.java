package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkfactionapi.api.FactionWinContext;
import dev.caecorthus.sparkfactionapi.api.FactionWinResult;

public final class WitchWinConditions {
    private WitchWinConditions() {
    }

    public static FactionWinResult checkWin(FactionWinContext context) {
        return FactionWinResult.none();
    }
}
