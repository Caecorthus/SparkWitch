package dev.caecorthus.sparkwitch;

import dev.caecorthus.sparkfactionapi.api.FactionCapabilities;
import dev.caecorthus.sparkfactionapi.api.FactionDefinition;
import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.FactionRoleDefinition;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.impl.PigGodRules;
import dev.caecorthus.sparkwitch.impl.WitchWinConditions;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.RoleAppearanceCondition;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;

public final class SparkWitchRoles {
    public static final Identifier GRAND_WITCH_ID = SparkWitch.id("grand_witch");
    public static final Identifier ACCOMPLICE_ID = SparkWitch.id("accomplice");
    public static final Identifier APPRENTICE_WITCH_ID = SparkWitch.id("apprentice_witch");
    public static final Identifier MURDEROUS_WITCH_ID = SparkWitch.id("murderous_witch");
    public static final Identifier PIG_GOD_ID = SparkWitch.id("pig_god");

    private static Role grandWitch;
    private static Role accomplice;
    private static Role apprenticeWitch;
    private static Role murderousWitch;
    private static Role pigGod;
    private static boolean registered;

    private SparkWitchRoles() {
    }

    public static synchronized void register() {
        if (registered) {
            appendAssassinGuessRoles();
            return;
        }
        registered = true;

        SparkFactionApi.bootstrap();
        SparkFactionApi.registerFaction(FactionDefinition.builder(SparkWitchFactions.WITCH)
                .color(0xE9D5F0)
                .translationKeyPrefix("faction.sparkwitch.witch")
                .capabilities(FactionCapabilities.builder()
                        // Witch members get only explicit bridges; they stay out of Wathe's native killer bucket.
                        // 魔女成员只获得显式桥接能力，不进入 wathe 原生杀手阵营桶。
                        .receivesKillerPassiveMoney(true)
                        .receivesKillRewards(true)
                        // Witch shooters opt into Wathe-style innocent-shot punishment.
                        // 魔女阵营开枪者接入 wathe 风格的射击无辜惩罚。
                        .isPunishableInnocentGunShooter(true)
                        .hasBlackoutImmunity(true)
                        .sharesCohort(true)
                        .canUseInstinct(true)
                        .instinctColor(0x36E51B)
                        .build())
                .winCondition(WitchWinConditions::checkWin)
                .build());
        SparkFactionApi.registerFaction(FactionDefinition.builder(SparkWitchFactions.MURDEROUS_WITCH)
                .color(0x7A3857)
                .translationKeyPrefix("faction.sparkwitch.murderous_witch")
                .capabilities(FactionCapabilities.builder()
                        // Murderous Witch stays a native neutral role; these switches only bridge explicit powers.
                        // 杀意魔女保持 wathe 原生中立职业，这里只桥接显式能力。
                        .receivesKillerPassiveMoney(true)
                        .receivesKillRewards(true)
                        .hasBlackoutImmunity(true)
                        .canUseInstinct(true)
                        .instinctColor(0xC13838)
                        .build())
                .build());

        grandWitch = SparkFactionApi.registerRole(FactionRoleDefinition.builder(GRAND_WITCH_ID, SparkWitchFactions.WITCH)
                .color(0xF2DFF7)
                .moodType(Role.MoodType.FAKE)
                .maxSprintTime(-1)
                .canSeeTime(false)
                .appearanceCondition(RoleAppearanceCondition.minPlayers(24))
                .build());
        accomplice = SparkFactionApi.registerRole(FactionRoleDefinition.builder(ACCOMPLICE_ID, SparkWitchFactions.WITCH)
                .color(0x7B6AA8)
                .moodType(Role.MoodType.FAKE)
                .maxSprintTime(-1)
                .canSeeTime(false)
                .appearanceCondition(RoleAppearanceCondition.minPlayers(24))
                .build());
        pigGod = SparkFactionApi.registerRole(FactionRoleDefinition.builder(PIG_GOD_ID, FactionIds.CIVILIAN)
                .color(PigGodRules.COLOR)
                .moodType(Role.MoodType.REAL)
                .maxSprintTime(GameConstants.getInTicks(0, 10))
                .canSeeTime(false)
                .appearanceCondition(RoleAppearanceCondition.minPlayers(24))
                .build());

        apprenticeWitch = WatheRoles.registerRole(new Role(
                APPRENTICE_WITCH_ID,
                0x75EDFA,
                true,
                false,
                Role.MoodType.REAL,
                GameConstants.getInTicks(0, 10),
                false,
                RoleAppearanceCondition.minPlayers(24)
        ));
        murderousWitch = WatheRoles.registerRole(new Role(
                MURDEROUS_WITCH_ID,
                0x7A3857,
                false,
                false,
                Role.MoodType.FAKE,
                -1,
                false,
                RoleAppearanceCondition.minPlayers(24)
        ));

        appendAssassinGuessRoles();
    }

    public static synchronized void refreshAssassinGuessRoleOrder() {
        ensureRegistered();
        appendAssassinGuessRoles();
    }

    public static Role grandWitch() {
        ensureRegistered();
        return grandWitch;
    }

    public static Role accomplice() {
        ensureRegistered();
        return accomplice;
    }

    public static Role apprenticeWitch() {
        ensureRegistered();
        return apprenticeWitch;
    }

    public static Role murderousWitch() {
        ensureRegistered();
        return murderousWitch;
    }

    public static Role pigGod() {
        ensureRegistered();
        return pigGod;
    }

    public static boolean isSparkWitchRole(Role role) {
        ensureRegistered();
        return role == grandWitch
                || role == accomplice
                || role == apprenticeWitch
                || role == murderousWitch
                || role == pigGod;
    }

    private static void ensureRegistered() {
        if (!registered) {
            register();
        }
    }

    private static void appendAssassinGuessRoles() {
        // Keep SparkWitch roles at the end of NoellesRoles/SparkTraits Assassin guess panels.
        // 让 SparkWitch 职业稳定显示在 NoellesRoles/SparkTraits 刺客猜身份面板末尾。
        WatheRoles.ROLES.removeIf(SparkWitchRoles::isRegisteredSparkWitchRole);
        WatheRoles.ROLES.add(apprenticeWitch);
        WatheRoles.ROLES.add(pigGod);
        WatheRoles.ROLES.add(murderousWitch);
        WatheRoles.ROLES.add(accomplice);
        WatheRoles.ROLES.add(grandWitch);
    }

    private static boolean isRegisteredSparkWitchRole(Role role) {
        return role == grandWitch
                || role == accomplice
                || role == apprenticeWitch
                || role == murderousWitch
                || role == pigGod;
    }
}
