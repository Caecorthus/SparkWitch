package dev.caecorthus.sparkwitch.roles.civilian.tarotreader;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TarotReaderRoundRoleHistoryTest {
    @Test
    void retainsRolesThatAppearedBeforeAMidRoundRoleChange() {
        Role original = role("original");
        Role replacement = role("replacement");
        TarotReaderRoundRoleHistory.clear();

        TarotReaderRoundRoleHistory.record(original);
        TarotReaderRoundRoleHistory.record(replacement);

        assertTrue(TarotReaderRoundRoleHistory.wasAssigned(original));
        assertTrue(TarotReaderRoundRoleHistory.wasAssigned(replacement));
    }

    @Test
    void roundCleanupRemovesThePreviousRoundsHistory() {
        Role previousRound = role("previous_round");
        TarotReaderRoundRoleHistory.clear();
        TarotReaderRoundRoleHistory.record(previousRound);

        TarotReaderRoundRoleHistory.clear();

        assertFalse(TarotReaderRoundRoleHistory.wasAssigned(previousRound));
    }

    private static Role role(String path) {
        return new Role(
                Identifier.of("sparkwitch_test", path),
                0,
                false,
                false,
                Role.MoodType.REAL,
                200,
                false
        );
    }
}
