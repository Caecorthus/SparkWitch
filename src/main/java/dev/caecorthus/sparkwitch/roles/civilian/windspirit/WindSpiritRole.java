package dev.caecorthus.sparkwitch.roles.civilian.windspirit;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.FactionRoleDefinition;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;

/**
 * Defines the non-rollable Civilian identity awarded to a promoted Wraith.
 * 定义冤魂晋升后获得且不会参与开局抽取的平民身份。
 */
public final class WindSpiritRole {
    public static final Identifier ID = SparkWitch.id("wind_spirit");
    public static final int COLOR = 0x36E51B;

    private WindSpiritRole() {
    }

    /**
     * Returns the definition that the central role registry must register exactly once.
     * 返回仅应由中央职业注册表注册一次的职业定义。
     */
    public static FactionRoleDefinition definition() {
        return FactionRoleDefinition.builder(ID, FactionIds.CIVILIAN)
                .color(COLOR)
                .moodType(Role.MoodType.NONE)
                .maxSprintTime(-1)
                .canSeeTime(false)
                .appearanceCondition(context -> false)
                .nativeWatheFaction(Faction.CIVILIAN)
                .build();
    }
}
