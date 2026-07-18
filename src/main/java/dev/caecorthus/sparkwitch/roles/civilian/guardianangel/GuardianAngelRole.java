package dev.caecorthus.sparkwitch.roles.civilian.guardianangel;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.FactionRoleDefinition;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import net.minecraft.util.Identifier;

/**
 * Defines the non-rollable Civilian identity awarded to a promoted Wraith.
 * 定义冤魂晋升后获得且不会参与开局抽取的平民身份。
 */
public final class GuardianAngelRole {
    public static final Identifier ID = SparkWitch.id("guardian_angel");
    public static final int COLOR = 0x36E51B;

    private GuardianAngelRole() {
    }

    public static FactionRoleDefinition definition() {
        return FactionRoleDefinition.builder(ID, FactionIds.CIVILIAN)
                .color(COLOR)
                .moodType(Role.MoodType.NONE)
                .maxSprintTime(WatheRoles.CIVILIAN.getMaxSprintTime())
                .canSeeTime(WatheRoles.CIVILIAN.canSeeTime())
                .appearanceCondition(context -> false)
                .nativeWatheFaction(Faction.CIVILIAN)
                .build();
    }
}
