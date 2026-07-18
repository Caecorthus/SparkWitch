package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PoisonApplePlateTrackerTest {
    @BeforeEach
    @AfterEach
    void clearTracker() {
        PoisonApplePlateTracker.clearLoadedPlates();
    }

    @Test
    void roundCleanupClearsEveryTrackedLoadedPlateOnce() {
        FakePlate first = new FakePlate();
        FakePlate second = new FakePlate();
        PoisonApplePlateTracker.track(first);
        PoisonApplePlateTracker.track(second);

        PoisonApplePlateTracker.clearLoadedPlates();
        PoisonApplePlateTracker.clearLoadedPlates();

        assertEquals(1, first.clearCalls);
        assertEquals(1, second.clearCalls);
    }

    private static final class FakePlate implements PoisonApplePlateAccess {
        private int clearCalls;

        @Override
        public boolean sparkwitch$isPoisonAppleArmed() {
            return true;
        }

        @Override
        public boolean sparkwitch$armPoisonApple(UUID placerUuid, UUID matchUuid) {
            return true;
        }

        @Override
        public UUID sparkwitch$recordSuccessfulTake(UUID matchUuid) {
            return null;
        }

        @Override
        public void sparkwitch$clearPoisonApple() {
            clearCalls++;
        }

        @Override
        public void sparkwitch$clearIfMatchChanged(UUID matchUuid) {
        }
    }
}
