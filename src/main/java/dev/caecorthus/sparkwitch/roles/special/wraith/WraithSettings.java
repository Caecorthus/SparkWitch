package dev.caecorthus.sparkwitch.roles.special.wraith;

/** Persistent server settings controlling Wraith conversion in future rounds. */
public record WraithSettings(int chance, int minimum, int dividend) {
    public static final int DEFAULT_CHANCE = 75;
    public static final int DEFAULT_MINIMUM = 10;
    public static final int DEFAULT_DIVIDEND = 5;
    public static final WraithSettings DEFAULT = new WraithSettings(
            DEFAULT_CHANCE, DEFAULT_MINIMUM, DEFAULT_DIVIDEND);

    public WraithSettings {
        chance = Math.max(0, Math.min(100, chance));
        minimum = Math.max(0, minimum);
        dividend = Math.max(1, dividend);
    }
}
