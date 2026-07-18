package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Authoritative two-take Poison Apple state, independent from Wathe's ordinary platter poison. */
public record PoisonApplePlateState(UUID placerUuid, int successfulTakeCount, UUID matchUuid) {
    public PoisonApplePlateState {
        Objects.requireNonNull(placerUuid, "placerUuid");
        Objects.requireNonNull(matchUuid, "matchUuid");
        successfulTakeCount = Math.max(0, Math.min(1, successfulTakeCount));
    }

    public static PoisonApplePlateState armed(UUID placerUuid, UUID matchUuid) {
        return new PoisonApplePlateState(placerUuid, 0, matchUuid);
    }

    public boolean belongsTo(@Nullable UUID currentMatchUuid) {
        return matchUuid.equals(currentMatchUuid);
    }

    public TakeResult onSuccessfulTake(@Nullable UUID currentMatchUuid) {
        if (!belongsTo(currentMatchUuid)) {
            return TakeResult.cleared();
        }
        if (successfulTakeCount == 0) {
            return new TakeResult(new PoisonApplePlateState(placerUuid, 1, matchUuid), null);
        }
        return new TakeResult(null, placerUuid);
    }

    public record TakeResult(
            @Nullable PoisonApplePlateState nextState,
            @Nullable UUID poisonerUuid
    ) {
        static TakeResult cleared() {
            return new TakeResult(null, null);
        }

        public boolean shouldPoison() {
            return poisonerUuid != null;
        }
    }
}
