package dev.caecorthus.sparkwitch.roles.special.wraith.progression;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithState;
import dev.doctor4t.wathe.api.Role;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithProgressionTest {
    @BeforeAll
    static void registerRoles() {
        SparkWitchRoles.register();
    }

    @Test
    void promotionStartsExactlyAtTheThirdCompletion() {
        assertFalse(WraithPromotionQueue.shouldQueuePromotion(false, false, 3));
        assertFalse(WraithPromotionQueue.shouldQueuePromotion(true, true, 3));
        assertFalse(WraithPromotionQueue.shouldQueuePromotion(true, false, 2));
        assertTrue(WraithPromotionQueue.shouldQueuePromotion(true, false, 3));
        assertTrue(WraithPromotionQueue.shouldQueuePromotion(true, false, 4));
    }

    @Test
    void promotionPoolsAndSelectionKeepTheirStableIdentityOrder() {
        assertEquals(List.of(
                SparkWitchRoles.windSpirit(),
                SparkWitchRoles.guardianAngel(),
                SparkWitchRoles.vendetta()
        ), WraithPromotionRoles.pool(WraithState.Alignment.GOOD));
        assertEquals(List.of(
                SparkWitchRoles.saboteur(),
                SparkWitchRoles.curser()
        ), WraithPromotionRoles.pool(WraithState.Alignment.KILLER));
        assertEquals(List.of(
                SparkWitchRoles.windSpirit(),
                SparkWitchRoles.guardianAngel()
        ), WraithPromotionRoles.pool(WraithState.Alignment.GOOD, false));

        Role goodPick = WraithPromotionRoles.pick(WraithState.Alignment.GOOD, new FixedIndexRandom(2));
        Role killerPick = WraithPromotionRoles.pick(WraithState.Alignment.KILLER, new FixedIndexRandom(1));
        assertEquals(SparkWitchRoles.vendetta(), goodPick);
        assertEquals(SparkWitchRoles.curser(), killerPick);
    }

    @Test
    void progressionOwnsOneTaskListenerAndOnePromotionTickListener() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/roles/special/wraith/progression/WraithProgression.java"
        ));
        assertEquals(1, occurrences(source, "TaskComplete.EVENT.register"));
        assertEquals(1, occurrences(source, "ServerTickEvents.END_SERVER_TICK.register"));
    }

    private static int occurrences(String source, String fragment) {
        return source.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
    }

    private static final class FixedIndexRandom extends Random {
        private final int index;

        private FixedIndexRandom(int index) {
            this.index = index;
        }

        @Override
        public int nextInt(int bound) {
            return index;
        }
    }
}
