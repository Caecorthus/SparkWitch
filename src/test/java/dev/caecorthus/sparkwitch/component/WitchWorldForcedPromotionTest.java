package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchWorldForcedPromotionTest {
    @Test
    void locksReplacePersistAndConsumeByPlayerUuid() {
        UUID player = UUID.randomUUID();
        ForcedWraithPromotionLocks original = new ForcedWraithPromotionLocks();

        assertTrue(original.set(player, SparkWitchRoles.WIND_SPIRIT_ID));
        assertFalse(original.set(player, SparkWitchRoles.WIND_SPIRIT_ID));
        assertTrue(original.set(player, SparkWitchRoles.CURSER_ID));
        assertEquals(SparkWitchRoles.CURSER_ID, original.get(player));

        NbtCompound tag = new NbtCompound();
        tag.put("ForcedWraithPromotions", original.toNbt());
        ForcedWraithPromotionLocks restored = new ForcedWraithPromotionLocks();
        restored.readFromNbt(tag, "ForcedWraithPromotions");

        assertEquals(SparkWitchRoles.CURSER_ID, restored.get(player));
        assertTrue(restored.clear(player));
        assertNull(restored.get(player));
        assertFalse(restored.clear(player));
    }

    @Test
    void clearAllExpiresUnconsumedNextRoundLocks() {
        ForcedWraithPromotionLocks locks = new ForcedWraithPromotionLocks();
        locks.set(UUID.randomUUID(), SparkWitchRoles.GUARDIAN_ANGEL_ID);
        locks.set(UUID.randomUUID(), SparkWitchRoles.CURSER_ID);

        locks.clearAll();

        assertTrue(locks.snapshot().isEmpty());
    }

    @Test
    void malformedEntriesDoNotDiscardOtherValidLocks() {
        UUID validPlayer = UUID.randomUUID();
        ForcedWraithPromotionLocks original = new ForcedWraithPromotionLocks();
        original.set(validPlayer, SparkWitchRoles.SABOTEUR_ID);
        NbtCompound tag = new NbtCompound();
        tag.put("ForcedWraithPromotions", original.toNbt());
        NbtCompound malformed = new NbtCompound();
        malformed.putString("Player", "not-a-uuid");
        malformed.putString("Role", SparkWitchRoles.CURSER_ID.toString());
        tag.getList("ForcedWraithPromotions", NbtElement.COMPOUND_TYPE).add(malformed);

        ForcedWraithPromotionLocks restored = new ForcedWraithPromotionLocks();
        restored.readFromNbt(tag, "ForcedWraithPromotions");
        assertEquals(SparkWitchRoles.SABOTEUR_ID, restored.get(validPlayer));
        assertEquals(1, restored.snapshot().size());
    }
}
