package dev.caecorthus.sparkwitch.compat;

import dev.caecorthus.sparkwitch.item.ceremonialsword.CeremonialSwordProtectionPolicy;
import dev.caecorthus.sparkwitch.item.tofana.TofanaRetaliationQueue;
import dev.doctor4t.wathe.api.event.KillPlayer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CeremonialSwordProtectionPolicyTest {
    private static final Identifier SWORD = Identifier.of("sparkwitch", "ceremonial_blade");

    @Test
    void onlyExactSwordDeathPierces() {
        assertTrue(CeremonialSwordProtectionPolicy.pierces(SWORD));
        for (Identifier other : List.of(Identifier.of("wathe", "knife"),
                Identifier.of("sparkwitch", "tofana_elixir"), Identifier.of("noellesroles", "voodoo"),
                Identifier.of("sparkwitch", "ceremonial_sword"), Identifier.of("other", "ceremonial_blade"))) {
            assertFalse(CeremonialSwordProtectionPolicy.pierces(other));
            assertTrue(CeremonialSwordProtectionPolicy.afterProtection(other).cancelled());
            assertTrue(CeremonialSwordProtectionPolicy.cancelsDeath(true, other));
            assertFalse(CeremonialSwordProtectionPolicy.cancelsDeath(false, other));
        }
        assertFalse(CeremonialSwordProtectionPolicy.pierces(null));
    }

    @Test
    void consumedSwordLayersContinueFirstNonNullListenersRatherThanReturningAllow() throws Exception {
        var effects = new ArrayList<String>();
        KillPlayer.Before[] listeners = List.of("iron_man", "whiskey", "guardian", "ninja").stream()
                .map(protection -> (KillPlayer.Before) (victim, killer, reason) -> {
                    effects.add(protection);
                    return CeremonialSwordProtectionPolicy.afterProtection(reason);
                }).toArray(KillPlayer.Before[]::new);
        // Execute the pinned provider's real aggregation, without mutating its global event registrations.
        var aggregate = KillPlayer.class.getDeclaredMethod("lambda$static$1", KillPlayer.Before[].class,
                ServerPlayerEntity.class, ServerPlayerEntity.class, Identifier.class);
        aggregate.setAccessible(true);
        assertNull(aggregate.invoke(null, listeners, null, null, SWORD));
        assertEquals(List.of("iron_man", "whiskey", "guardian", "ninja"), effects);
        effects.clear();
        assertNotNull(aggregate.invoke(null, listeners, null, null, Identifier.of("wathe", "knife")));
        assertEquals(List.of("iron_man"), effects);
        assertFalse(CeremonialSwordProtectionPolicy.cancelsDeath(true, SWORD));
        assertFalse(CeremonialSwordProtectionPolicy.cancelsDeath(false, SWORD));
    }

    @Test
    void traitProtectionStillCancelsAfterPiercedItemProtection() throws Exception {
        var effects = new ArrayList<String>();
        KillPlayer.Before[] listeners = {
                (victim, killer, reason) -> {
                    effects.add("item_consumed");
                    return CeremonialSwordProtectionPolicy.afterProtection(reason);
                },
                (victim, killer, reason) -> {
                    effects.add("trait_protection");
                    return KillPlayer.KillResult.cancel();
                },
                (victim, killer, reason) -> {
                    fail("A trait veto must still stop ordinary death processing");
                    return null;
                }
        };
        var aggregate = KillPlayer.class.getDeclaredMethod("lambda$static$1", KillPlayer.Before[].class,
                ServerPlayerEntity.class, ServerPlayerEntity.class, Identifier.class);
        aggregate.setAccessible(true);
        KillPlayer.KillResult result = (KillPlayer.KillResult) aggregate.invoke(null, listeners, null, null, SWORD);
        assertTrue(result.cancelled());
        assertEquals(List.of("item_consumed", "trait_protection"), effects);
    }

    @Test
    void consumedTofanaRetaliatesAfterHolderDiesWithoutInheritingPiercing() {
        var queue = new TofanaRetaliationQueue<String>();
        queue.enqueue("dead-holder", "sword-attacker");
        assertFalse(CeremonialSwordProtectionPolicy.cancelsDeath(true, SWORD));
        queue.tick(attempt -> fail("Retaliation must wait until the next tick"));
        var attempts = new ArrayList<TofanaRetaliationQueue.Attempt<String>>();
        queue.tick(attempts::add);
        assertEquals(List.of(new TofanaRetaliationQueue.Attempt<>("dead-holder", "sword-attacker")), attempts);
        assertTrue(CeremonialSwordProtectionPolicy.cancelsDeath(true,
                Identifier.of("sparkwitch", "tofana_elixir")));
        queue.tick(attempt -> fail("Retaliation must execute only once"));
    }
}
