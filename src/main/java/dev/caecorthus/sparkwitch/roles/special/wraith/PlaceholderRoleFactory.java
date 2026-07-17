package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.FactionRoleDefinition;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;

/**
 * Builds non-rollable promotion identities until their role behavior is defined.
 * 在晋升职业行为尚未定义时构建不可随机抽取的身份。
 */
public final class PlaceholderRoleFactory {
    public static final int CIVILIAN_COLOR = 0x36E51B;
    public static final int KILLER_COLOR = 0xC13838;

    private PlaceholderRoleFactory() {
    }

    public static FactionRoleDefinition civilian(Identifier id) {
        return FactionRoleDefinition.builder(id, FactionIds.CIVILIAN)
                .color(CIVILIAN_COLOR)
                .moodType(Role.MoodType.NONE)
                .maxSprintTime(GameConstants.getInTicks(0, 10))
                .canSeeTime(false)
                .appearanceCondition(context -> false)
                .nativeWatheFaction(Faction.CIVILIAN)
                .build();
    }

    public static FactionRoleDefinition killer(Identifier id) {
        return FactionRoleDefinition.builder(id, FactionIds.KILLER)
                .color(KILLER_COLOR)
                .moodType(Role.MoodType.NONE)
                .maxSprintTime(-1)
                .canSeeTime(true)
                .appearanceCondition(context -> false)
                .nativeWatheFaction(Faction.KILLER)
                .build();
    }
}
