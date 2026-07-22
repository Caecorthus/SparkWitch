package dev.caecorthus.sparkwitch.roles.special.wraith;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WraithSettingsTest {
    @Test
    void defaultsAndBoundariesAreNormalized() {
        assertEquals(new WraithSettings(75, 10, 5), WraithSettings.DEFAULT);
        assertEquals(new WraithSettings(0, 0, 1), new WraithSettings(-1, -1, 0));
        assertEquals(new WraithSettings(100, 4, 7), new WraithSettings(101, 4, 7));
    }

    @Test
    void snapshotKeepsRoundValuesAndUsesFloorCapacity() {
        WraithRoundSettingsSnapshot below = new WraithRoundSettingsSnapshot(9, 75, 10, 5);
        WraithRoundSettingsSnapshot atMinimum = new WraithRoundSettingsSnapshot(10, 0, 10, 4);

        assertEquals(0, below.capacity());
        assertEquals(2, atMinimum.capacity());
        assertEquals(0, atMinimum.chance());
    }
}
