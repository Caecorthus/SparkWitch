package dev.caecorthus.sparkwitch.roles.civilian.windspirit;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.FactionRoleDefinition;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;

/** Non-rollable civilian identity awarded by Wraith promotion. / 冤魂晋升授予的不可随机平民身份。 */
public final class WindSpiritRole {
    public static final Identifier ROLE_ID = SparkWitch.id("wind_spirit");
    public static final Identifier ID = ROLE_ID;
    public static final FactionRoleDefinition DEFINITION = FactionRoleDefinition.builder(ROLE_ID, FactionIds.CIVILIAN)
            .color(0x36E51B)
            .moodType(Role.MoodType.NONE)
            .maxSprintTime(GameConstants.getInTicks(0, 10))
            .canSeeTime(false)
            .appearanceCondition(context -> false)
            .nativeWatheFaction(Faction.CIVILIAN)
            .build();

    private WindSpiritRole() {
    }
}
