package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.FactionRoleDefinition;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import net.minecraft.util.Identifier;

/**
 * Defines the non-rollable Killer identity awarded to a promoted Wraith.
 * 定义冤魂晋升后获得且不会参与开局抽取的杀手身份。
 */
public final class SaboteurRole {
    public static final Identifier ID = SparkWitch.id("saboteur");
    public static final int COLOR = 0xC13838;

    private SaboteurRole() {
    }

    public static FactionRoleDefinition definition() {
        return FactionRoleDefinition.builder(ID, FactionIds.KILLER)
                .color(COLOR)
                .moodType(Role.MoodType.NONE)
                .maxSprintTime(WatheRoles.KILLER.getMaxSprintTime())
                .canSeeTime(WatheRoles.KILLER.canSeeTime())
                .appearanceCondition(context -> false)
                .nativeWatheFaction(Faction.KILLER)
                .build();
    }
}
