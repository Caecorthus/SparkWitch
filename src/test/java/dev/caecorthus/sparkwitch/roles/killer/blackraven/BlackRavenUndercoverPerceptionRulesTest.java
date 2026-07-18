package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class BlackRavenUndercoverPerceptionRulesTest {
    private static final UUID MATCH_ID = UUID.fromString("9ac9484c-c49d-4634-a8d8-bac60c232554");
    private static final UUID TARGET_ID = UUID.fromString("cc468b0a-d994-4b55-86cd-09ec393ee1bc");

    @Test
    void undercoverDisplaysTheOnlyKillerRoleAbsentFromTheMatch() {
        Role undercover = role("noellesroles:undercover", 0xC0C0C0, true, false);
        Role ordinaryKiller = role("noellesroles:poisoner", 0x1E5014, false, true);
        Role conscienceKillerBaseRole = role("noellesroles:bomber", 0x323232, false, true);
        Role absentKiller = role("sparkwitch:black_raven", 0x51445F, false, true);
        Role civilian = role("wathe:civilian", 0x36E51B, true, false);

        Role displayed = BlackRavenUndercoverPerceptionRules.resolveDisplayedRole(
                undercover,
                List.of(civilian, ordinaryKiller, absentKiller, conscienceKillerBaseRole),
                List.of(undercover, ordinaryKiller, conscienceKillerBaseRole),
                MATCH_ID,
                TARGET_ID
        );

        assertSame(absentKiller, displayed);
    }

    @Test
    void undercoverSelectionIsStableWhenRoleRegistrationOrderChanges() {
        Role undercover = role("noellesroles:undercover", 0xC0C0C0, true, false);
        Role absentKillerA = role("noellesroles:assassin", 0x8B0000, false, true);
        Role absentKillerB = role("sparkwitch:witch_maiden", 0xB04A8B, false, true);

        Role first = BlackRavenUndercoverPerceptionRules.resolveDisplayedRole(
                undercover,
                List.of(absentKillerA, absentKillerB),
                List.of(undercover),
                MATCH_ID,
                TARGET_ID
        );
        Role reordered = BlackRavenUndercoverPerceptionRules.resolveDisplayedRole(
                undercover,
                List.of(absentKillerB, absentKillerA),
                List.of(undercover),
                MATCH_ID,
                TARGET_ID
        );

        assertSame(first, reordered);
        assertNotSame(undercover, first);
    }

    @Test
    void completedSnapshotKeepsTheTargetButUsesTheFalseKillerIdentity() {
        Role undercover = role("noellesroles:undercover", 0xC0C0C0, true, false);
        Role absentKiller = role("noellesroles:phantom", 0x500505, false, true);

        BlackRavenIdentitySnapshot snapshot = BlackRavenUndercoverPerceptionRules.createSnapshot(
                MATCH_ID,
                TARGET_ID,
                "HiddenPassenger",
                undercover,
                List.of(absentKiller),
                List.of(undercover)
        );

        assertEquals(TARGET_ID, snapshot.targetUuid());
        assertEquals("HiddenPassenger", snapshot.playerName());
        assertEquals("noellesroles:phantom", snapshot.roleId());
        assertEquals(0x500505, snapshot.roleColor());
    }

    @Test
    void nonUndercoverKeepsItsRealIdentity() {
        Role realRole = role("wathe:civilian", 0x36E51B, true, false);

        Role displayed = BlackRavenUndercoverPerceptionRules.resolveDisplayedRole(
                realRole,
                List.of(WatheRoles.KILLER),
                List.of(realRole),
                MATCH_ID,
                TARGET_ID
        );

        assertSame(realRole, displayed);
    }

    @Test
    void exhaustedPoolFallsBackToBaseKillerInsteadOfRevealingUndercover() {
        Role undercover = role("noellesroles:undercover", 0xC0C0C0, true, false);
        Role assignedKiller = role("noellesroles:poisoner", 0x1E5014, false, true);

        Role displayed = BlackRavenUndercoverPerceptionRules.resolveDisplayedRole(
                undercover,
                List.of(assignedKiller),
                List.of(undercover, assignedKiller),
                MATCH_ID,
                TARGET_ID
        );

        assertSame(WatheRoles.KILLER, displayed);
        assertNotSame(undercover, displayed);
    }

    private static Role role(String id, int color, boolean innocent, boolean killer) {
        return new Role(
                Identifier.of(id),
                color,
                innocent,
                killer,
                Role.MoodType.NONE,
                -1,
                false
        );
    }
}
