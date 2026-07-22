package dev.caecorthus.sparkwitch.roles.special.wraith;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WraithSettingsNbtTest {
    @Test
    void worldSettingsRoundTripAndLegacyTagsUseDefaults() {
        NbtCompound saved = new NbtCompound();
        WraithSettingsNbtCodec.writeWorld(saved, new WraithSettings(23, 4, 7));

        assertEquals(new WraithSettings(23, 4, 7), WraithSettingsNbtCodec.readWorld(saved));
        assertEquals(WraithSettings.DEFAULT, WraithSettingsNbtCodec.readWorld(new NbtCompound()));
    }

    @Test
    void roundSnapshotRoundTripsAndLegacyTagsUseDefaults() {
        NbtCompound saved = new NbtCompound();
        WraithSettingsNbtCodec.writeRound(saved, new WraithRoundSettingsSnapshot(17, 31, 12, 4));

        assertEquals(new WraithRoundSettingsSnapshot(17, 31, 12, 4),
                WraithSettingsNbtCodec.readRound(saved, 17));
        assertEquals(new WraithRoundSettingsSnapshot(10, 75, 10, 5),
                WraithSettingsNbtCodec.readRound(new NbtCompound(), 10));
    }
}
