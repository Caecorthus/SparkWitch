package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.FactionRoleDefinition;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;

/** Non-rollable killer identity awarded by Wraith promotion. / 冤魂晋升授予的不可随机杀手身份。 */
public final class SaboteurRole {
    public static final Identifier ROLE_ID = SparkWitch.id("saboteur");
    public static final Identifier ID = ROLE_ID;
    public static final FactionRoleDefinition DEFINITION = FactionRoleDefinition.builder(ROLE_ID, FactionIds.KILLER)
            .color(0xC13838)
            .moodType(Role.MoodType.NONE)
            .maxSprintTime(-1)
            .canSeeTime(true)
            .appearanceCondition(context -> false)
            .nativeWatheFaction(Faction.KILLER)
            .build();

    private SaboteurRole() {
    }
}
