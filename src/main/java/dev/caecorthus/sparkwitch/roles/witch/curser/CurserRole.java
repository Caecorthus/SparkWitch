package dev.caecorthus.sparkwitch.roles.witch.curser;

import dev.caecorthus.sparkfactionapi.api.FactionRoleDefinition;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;

/** Non-rollable Witch identity awarded by Wraith promotion. / 冤魂晋升授予的不可随机魔女身份。 */
public final class CurserRole {
    public static final Identifier ROLE_ID = SparkWitch.id("curser");
    public static final Identifier ID = ROLE_ID;
    public static final int COLOR = 0xA968D5;
    public static final FactionRoleDefinition DEFINITION =
            FactionRoleDefinition.builder(ROLE_ID, SparkWitchFactions.WITCH)
                    .color(COLOR)
                    .moodType(Role.MoodType.NONE)
                    .maxSprintTime(-1)
                    .canSeeTime(true)
                    .appearanceCondition(context -> false)
                    .build();

    private CurserRole() {
    }
}
