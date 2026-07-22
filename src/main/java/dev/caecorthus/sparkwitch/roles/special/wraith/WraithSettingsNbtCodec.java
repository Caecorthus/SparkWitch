package dev.caecorthus.sparkwitch.roles.special.wraith;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

/** Pure NBT codec for persistent and per-round Wraith settings. */
public final class WraithSettingsNbtCodec {
    private static final String WORLD_CHANCE_KEY = "WraithChance";
    private static final String WORLD_MINIMUM_KEY = "WraithMinimum";
    private static final String WORLD_DIVIDEND_KEY = "WraithDividend";
    private static final String ROUND_CHANCE_KEY = "Chance";
    private static final String ROUND_MINIMUM_KEY = "Minimum";
    private static final String ROUND_DIVIDEND_KEY = "Dividend";

    private WraithSettingsNbtCodec() {
    }

    public static void writeWorld(NbtCompound tag, WraithSettings settings) {
        tag.putInt(WORLD_CHANCE_KEY, settings.chance());
        tag.putInt(WORLD_MINIMUM_KEY, settings.minimum());
        tag.putInt(WORLD_DIVIDEND_KEY, settings.dividend());
    }

    public static WraithSettings readWorld(NbtCompound tag) {
        return new WraithSettings(
                readIntOrDefault(tag, WORLD_CHANCE_KEY, WraithSettings.DEFAULT_CHANCE),
                readIntOrDefault(tag, WORLD_MINIMUM_KEY, WraithSettings.DEFAULT_MINIMUM),
                readIntOrDefault(tag, WORLD_DIVIDEND_KEY, WraithSettings.DEFAULT_DIVIDEND));
    }

    public static void writeRound(NbtCompound tag, WraithRoundSettingsSnapshot snapshot) {
        tag.putInt(ROUND_CHANCE_KEY, snapshot.chance());
        tag.putInt(ROUND_MINIMUM_KEY, snapshot.minimum());
        tag.putInt(ROUND_DIVIDEND_KEY, snapshot.dividend());
    }

    public static WraithRoundSettingsSnapshot readRound(NbtCompound tag, int startingPlayers) {
        return new WraithRoundSettingsSnapshot(
                startingPlayers,
                readIntOrDefault(tag, ROUND_CHANCE_KEY, WraithSettings.DEFAULT_CHANCE),
                readIntOrDefault(tag, ROUND_MINIMUM_KEY, WraithSettings.DEFAULT_MINIMUM),
                readIntOrDefault(tag, ROUND_DIVIDEND_KEY, WraithSettings.DEFAULT_DIVIDEND));
    }

    private static int readIntOrDefault(NbtCompound tag, String key, int fallback) {
        return tag.contains(key, NbtElement.NUMBER_TYPE) ? tag.getInt(key) : fallback;
    }
}
