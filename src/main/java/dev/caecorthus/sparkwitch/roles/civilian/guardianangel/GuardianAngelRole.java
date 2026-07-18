package dev.caecorthus.sparkwitch.roles.civilian.guardianangel;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.FactionRoleDefinition;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;

/** Non-rollable civilian identity awarded by Wraith promotion. / 冤魂晋升授予的不可随机平民身份。 */
public final class GuardianAngelRole {
    public static final Identifier ROLE_ID = GuardianAngelRules.ROLE_ID;
    public static final Identifier ID = ROLE_ID;
    public static final FactionRoleDefinition DEFINITION = FactionRoleDefinition.builder(ROLE_ID, FactionIds.CIVILIAN)
            .color(GuardianAngelRules.COLOR)
            .moodType(Role.MoodType.NONE)
            .maxSprintTime(-1)
            .canSeeTime(false)
            .appearanceCondition(context -> false)
            .nativeWatheFaction(Faction.CIVILIAN)
            .build();

    private GuardianAngelRole() {
    }
}
