package dev.caecorthus.sparkwitch.roles.special.wraith;

/** Immutable settings captured at round start. */
public record WraithRoundSettingsSnapshot(int startingPlayers, int chance, int minimum, int dividend) {
    public WraithRoundSettingsSnapshot {
        WraithSettings normalized = new WraithSettings(chance, minimum, dividend);
        startingPlayers = Math.max(0, startingPlayers);
        chance = normalized.chance();
        minimum = normalized.minimum();
        dividend = normalized.dividend();
    }

    public int capacity() {
        return startingPlayers < minimum ? 0 : startingPlayers / dividend;
    }
}
