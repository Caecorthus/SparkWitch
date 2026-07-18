package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.jetbrains.annotations.Nullable;

/** Separates server-authoritative Poison Apple data from the single client-visible armed bit. */
public final class PoisonApplePlateNbt {
    public static final String STATE_KEY = "sparkwitch:poison_apple";
    public static final String ARMED_KEY = "sparkwitch:poison_apple_armed";
    private static final String PLACER_KEY = "Placer";
    private static final String TAKE_COUNT_KEY = "SuccessfulTakes";
    private static final String MATCH_KEY = "Match";

    private PoisonApplePlateNbt() {
    }

    public static void writePersistent(NbtCompound root, @Nullable PoisonApplePlateState state) {
        root.remove(STATE_KEY);
        if (state == null) {
            return;
        }
        NbtCompound stateNbt = new NbtCompound();
        stateNbt.putUuid(PLACER_KEY, state.placerUuid());
        stateNbt.putInt(TAKE_COUNT_KEY, state.successfulTakeCount());
        stateNbt.putUuid(MATCH_KEY, state.matchUuid());
        root.put(STATE_KEY, stateNbt);
    }

    public static @Nullable PoisonApplePlateState readPersistent(NbtCompound root) {
        if (!root.contains(STATE_KEY, NbtElement.COMPOUND_TYPE)) {
            return null;
        }
        NbtCompound stateNbt = root.getCompound(STATE_KEY);
        if (!stateNbt.containsUuid(PLACER_KEY) || !stateNbt.containsUuid(MATCH_KEY)) {
            return null;
        }
        return new PoisonApplePlateState(
                stateNbt.getUuid(PLACER_KEY),
                stateNbt.getInt(TAKE_COUNT_KEY),
                stateNbt.getUuid(MATCH_KEY)
        );
    }

    public static void stripForClient(NbtCompound root, boolean armed) {
        root.remove(STATE_KEY);
        root.putBoolean(ARMED_KEY, armed);
    }
}
