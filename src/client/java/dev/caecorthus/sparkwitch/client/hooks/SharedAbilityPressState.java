package dev.caecorthus.sparkwitch.client.hooks;

/** Non-destructive queue fed by the shared role key's real press events. */
final class SharedAbilityPressState {
    private int pendingPresses;

    void record() {
        if (pendingPresses < Integer.MAX_VALUE) {
            pendingPresses++;
        }
    }

    boolean consume() {
        if (pendingPresses <= 0) {
            return false;
        }
        pendingPresses--;
        return true;
    }

    void reset() {
        pendingPresses = 0;
    }
}
