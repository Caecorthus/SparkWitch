package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.doctor4t.wathe.api.Faction;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WraithDeathSnapshotTest {
    @Test
    void snapshotOwnsOpaqueTraitDataAndCapturedDeathTime() {
        NbtCompound traits = new NbtCompound();
        traits.putString("marker", "before");
        WraithDeathSnapshot snapshot = new WraithDeathSnapshot(
                Identifier.of("test", "civilian"),
                Faction.CIVILIAN,
                null,
                traits,
                WraithState.Alignment.GOOD,
                false,
                47
        );

        traits.putString("marker", "after");
        NbtCompound returned = snapshot.traitSnapshot();
        returned.putString("marker", "mutated");

        assertEquals("before", snapshot.traitSnapshot().getString("marker"));
        assertEquals(47, snapshot.deathGameTime());
        assertFalse(snapshot.lastStandTriggeredBefore());
    }
}
