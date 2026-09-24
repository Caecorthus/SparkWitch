package dev.caecorthus.sparkwitch.roles.civilian.windspirit;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.FactionRoleDefinition;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;

/** Non-rollable civilian identity awarded by Wraith promotion. / 冤魂晋升授予的不可随机平民身份。 */
public final class WindSpiritRole {
    public static final Identifier ROLE_ID = SparkWitch.id("wind_spirit");
    public static final Identifier ID = ROLE_ID;
    public static final int COLOR = 0x59D8E6;
    public static final FactionRoleDefinition DEFINITION = FactionRoleDefinition.builder(ROLE_ID, FactionIds.CIVILIAN)
            .color(COLOR)
            .moodType(Role.MoodType.NONE)
            // 风精灵只通过冤魂晋升获得，职业定义本身也保持无限体力，避免 Wathe 重新套用好人体力条。
            .maxSprintTime(-1)
            .canSeeTime(false)
            .appearanceCondition(context -> false)
            .nativeWatheFaction(Faction.CIVILIAN)
            .build();

    private WindSpiritRole() {
    }
}
