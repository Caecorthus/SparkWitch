package dev.caecorthus.sparkwitch.roles.civilian.saint;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaintPlayerStateTest {
    @Test
    void ownsTheCompleteHellfireLifecycle() {
        SaintPlayerState state = new SaintPlayerState();

        assertTrue(state.initializeAbility());
        assertEquals(1200, state.hellfireCooldownTicks());
        assertFalse(state.initializeAbility());

        state.activateHellfire();
        assertTrue(state.isHellfireActive());
        assertEquals(300, state.hellfireActiveTicks());

        for (int tick = 0; tick < 300; tick++) {
            state.tickAbility();
        }

        assertFalse(state.isHellfireActive());
        assertEquals(1200, state.hellfireCooldownTicks());
    }

    @Test
    void abilityClearDoesNotEraseTheUuidOwnedKarmaMirror() {
        SaintPlayerState state = new SaintPlayerState();
        state.initializeAbility();
        state.updateKarma(true, 100);

        assertTrue(state.clearAbility());

        assertEquals(0, state.hellfireCooldownTicks());
        assertEquals(0, state.hellfireActiveTicks());
        assertTrue(state.isKarmaMarked());
        assertEquals(100, state.karmaCooldownTicks());
    }

    @Test
    void karmaMirrorNormalizesTicksAndRequestsOnlyUsefulSyncs() {
        SaintPlayerState state = new SaintPlayerState();

        assertTrue(state.updateKarma(true, 100));
        assertFalse(state.updateKarma(true, 99));
        assertTrue(state.updateKarma(true, 80));
        assertTrue(state.updateKarma(false, -20));

        assertFalse(state.isKarmaMarked());
        assertEquals(0, state.karmaCooldownTicks());
    }

    @Test
    void preservesTheExistingSaintNbtKeys() {
        SaintPlayerState original = new SaintPlayerState();
        original.activateHellfire();
        original.updateKarma(true, 400);
        NbtCompound tag = new NbtCompound();

        original.writeNbt(tag);
        SaintPlayerState restored = new SaintPlayerState();
        restored.readNbt(tag);

        assertEquals(300, restored.hellfireActiveTicks());
        assertTrue(restored.isKarmaMarked());
        assertEquals(400, restored.karmaCooldownTicks());
        assertTrue(tag.contains("SaintHellfireActiveTicks"));
        assertTrue(tag.contains("SaintKarmaMarked"));
        assertTrue(tag.contains("SaintKarmaCooldownTicks"));
    }

    @Test
    void roundTripsTheFourFieldOwnerOnlySyncSegment() {
        SaintPlayerState original = new SaintPlayerState();
        original.initializeAbility();
        original.updateKarma(true, 400);
        RegistryByteBuf ownerBuf = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        RegistryByteBuf hiddenBuf = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        try {
            original.writeSync(ownerBuf, true);
            SaintPlayerState restored = new SaintPlayerState();
            restored.readSync(ownerBuf);
            assertEquals(1200, restored.hellfireCooldownTicks());
            assertTrue(restored.isKarmaMarked());
            assertEquals(400, restored.karmaCooldownTicks());

            original.writeSync(hiddenBuf, false);
            SaintPlayerState hidden = new SaintPlayerState();
            hidden.readSync(hiddenBuf);
            assertTrue(hidden.isEmpty());
        } finally {
            ownerBuf.release();
            hiddenBuf.release();
        }
    }
}
