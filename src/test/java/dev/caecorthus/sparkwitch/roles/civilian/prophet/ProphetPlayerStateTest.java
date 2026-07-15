package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProphetPlayerStateTest {
    private static final UUID FIRST_BODY = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID SECOND_BODY = UUID.fromString("00000000-0000-0000-0000-000000000102");

    @Test
    void ownsTheWindowLifecycleAndRecordedBodies() {
        ProphetPlayerState state = new ProphetPlayerState();

        assertFalse(state.recordBody(FIRST_BODY));
        state.begin(2);
        assertTrue(state.isActive());
        assertEquals(2, state.remainingTicks());
        assertTrue(state.recordBody(FIRST_BODY));
        assertFalse(state.recordBody(FIRST_BODY));
        assertFalse(state.recordBody(null));
        assertTrue(state.containsBody(FIRST_BODY));

        assertEquals(ProphetPlayerState.TickOutcome.NONE, state.tick());
        assertEquals(ProphetPlayerState.TickOutcome.FINISHED, state.tick());
        assertFalse(state.isActive());
        assertFalse(state.containsBody(FIRST_BODY));
        assertTrue(state.isEmpty());
    }

    @Test
    void distinguishesPeriodicSyncFromNoOpAndNormalFinish() {
        ProphetPlayerState state = new ProphetPlayerState();
        state.begin(21);

        assertEquals(ProphetPlayerState.TickOutcome.SYNC, state.tick());
        assertEquals(ProphetPlayerState.TickOutcome.NONE, state.tick());
        for (int tick = 0; tick < 18; tick++) {
            state.tick();
        }
        assertEquals(ProphetPlayerState.TickOutcome.FINISHED, state.tick());
    }

    @Test
    void cancellationClearsActiveStateOnlyOnce() {
        ProphetPlayerState state = new ProphetPlayerState();
        state.begin(20);
        state.recordBody(FIRST_BODY);

        assertTrue(state.cancel());
        assertTrue(state.isEmpty());
        assertFalse(state.containsBody(FIRST_BODY));
        assertFalse(state.cancel());
        assertFalse(state.cancel());
    }

    @Test
    void ignoresMalformedSavedUuidsAndRoundTripsActiveState() {
        NbtCompound malformed = new NbtCompound();
        malformed.putInt("DeathOmenTicks", 400);
        NbtList bodies = new NbtList();
        bodies.add(NbtString.of("not-a-uuid"));
        bodies.add(NbtString.of(FIRST_BODY.toString()));
        malformed.put("DeathOmenBodyUuids", bodies);

        ProphetPlayerState restored = new ProphetPlayerState();
        restored.readNbt(malformed);
        assertEquals(400, restored.remainingTicks());
        assertTrue(restored.containsBody(FIRST_BODY));

        NbtCompound roundTrip = new NbtCompound();
        restored.writeNbt(roundTrip);
        ProphetPlayerState copy = new ProphetPlayerState();
        copy.readNbt(roundTrip);
        assertEquals(400, copy.remainingTicks());
        assertTrue(copy.containsBody(FIRST_BODY));
        assertTrue(roundTrip.contains("DeathOmenTicks"));
        assertTrue(roundTrip.contains("DeathOmenBodyUuids"));

        copy.readNbt(new NbtCompound());
        assertTrue(copy.isEmpty());
    }

    @Test
    void writesTheInsertionOrderedOwnerOnlySyncSegment() {
        ProphetPlayerState original = new ProphetPlayerState();
        original.begin(400);
        original.recordBody(FIRST_BODY);
        original.recordBody(SECOND_BODY);
        RegistryByteBuf ownerBuf = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        RegistryByteBuf roundTripBuf = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        RegistryByteBuf hiddenBuf = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        try {
            original.writeSync(ownerBuf, true);
            assertEquals(400, ownerBuf.readVarInt());
            assertEquals(2, ownerBuf.readVarInt());
            assertEquals(FIRST_BODY, ownerBuf.readUuid());
            assertEquals(SECOND_BODY, ownerBuf.readUuid());

            original.writeSync(roundTripBuf, true);
            ProphetPlayerState restored = new ProphetPlayerState();
            restored.readSync(roundTripBuf);
            assertEquals(400, restored.remainingTicks());
            assertTrue(restored.containsBody(FIRST_BODY));
            assertTrue(restored.containsBody(SECOND_BODY));

            original.writeSync(hiddenBuf, false);
            ProphetPlayerState hidden = new ProphetPlayerState();
            hidden.readSync(hiddenBuf);
            assertTrue(hidden.isEmpty());
        } finally {
            ownerBuf.release();
            roundTripBuf.release();
            hiddenBuf.release();
        }
    }
}
