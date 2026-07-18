package dev.caecorthus.sparkwitch.roles.special.wraith;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WraithPromotionRolesTest {
    @Test
    void everyPromotionIdentityUsesSparkWitchNamespace() {
        List.of(
                WraithPromotionRoles.WIND_SPIRIT_ID,
                WraithPromotionRoles.GUARDIAN_ANGEL_ID,
                WraithPromotionRoles.VENDETTA_ID,
                WraithPromotionRoles.SABOTEUR_ID,
                WraithPromotionRoles.CURSER_ID
        ).forEach(id -> assertEquals("sparkwitch", id.getNamespace()));
    }
}
