package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

class PoisonApplePlateNbtTest {
    private static final UUID PLACER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MATCH = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void persistentStateRoundTripsAllAuthoritativeFields() {
        PoisonApplePlateState expected = PoisonApplePlateState.armed(PLACER, MATCH)
                .onSuccessfulTake(MATCH)
                .nextState();
        NbtCompound root = new NbtCompound();

        PoisonApplePlateNbt.writePersistent(root, expected);

        assertEquals(expected, PoisonApplePlateNbt.readPersistent(root));
    }

    @Test
    void chunkDataExposesOnlyTheArmedFlag() {
        NbtCompound root = new NbtCompound();
        PoisonApplePlateNbt.writePersistent(root, PoisonApplePlateState.armed(PLACER, MATCH));
        root.putString("unrelated", "kept");

        PoisonApplePlateNbt.stripForClient(root, true);

        assertFalse(root.contains(PoisonApplePlateNbt.STATE_KEY));
        assertTrue(root.getBoolean(PoisonApplePlateNbt.ARMED_KEY));
        assertEquals("kept", root.getString("unrelated"));
    }

    @Test
    void malformedPersistentStateFailsClosed() {
        NbtCompound root = new NbtCompound();
        root.put(PoisonApplePlateNbt.STATE_KEY, new NbtCompound());

        assertEquals(null, PoisonApplePlateNbt.readPersistent(root));
    }
}
