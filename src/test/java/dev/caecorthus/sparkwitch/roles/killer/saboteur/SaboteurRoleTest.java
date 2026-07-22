package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaboteurRoleTest {
    @Test
    void preservesSaboteurRoleColorDistinctFromVendettaOrange() {
        assertTrue(SaboteurRole.COLOR == 0xE28743);
        assertFalse(SaboteurRole.COLOR == 0xFF8C00);
    }
}
