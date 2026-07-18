package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Weak loaded-plate index for round cleanup; plate NBT remains the authority. */
public final class PoisonApplePlateTracker {
    private static final Set<PoisonApplePlateAccess> ARMED_PLATES = Collections.newSetFromMap(
            Collections.synchronizedMap(new WeakHashMap<>())
    );

    private PoisonApplePlateTracker() {
    }

    public static void track(PoisonApplePlateAccess plate) {
        ARMED_PLATES.add(plate);
    }

    public static void untrack(PoisonApplePlateAccess plate) {
        ARMED_PLATES.remove(plate);
    }

    public static void clearLoadedPlates() {
        ArrayList<PoisonApplePlateAccess> snapshot;
        synchronized (ARMED_PLATES) {
            snapshot = new ArrayList<>(ARMED_PLATES);
            ARMED_PLATES.clear();
        }
        snapshot.forEach(PoisonApplePlateAccess::sparkwitch$clearPoisonApple);
    }
}
