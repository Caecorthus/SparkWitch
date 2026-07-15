package dev.caecorthus.sparkwitch.registry;

import dev.caecorthus.sparkfactionapi.api.FactionCapabilities;
import dev.caecorthus.sparkfactionapi.api.FactionDefinition;
import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.FactionRoleDefinition;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistRules;
import dev.caecorthus.sparkwitch.roles.civilian.perfumer.PerfumerRules;
import dev.caecorthus.sparkwitch.roles.civilian.piggod.PigGodRules;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintRules;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterRules;
import dev.caecorthus.sparkwitch.roles.killer.ninja.NinjaRules;
import dev.caecorthus.sparkwitch.win.WitchWinConditions;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.RoleAppearanceCondition;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Internal owner for SparkWitch role and faction registration; SparkWitchRoles remains the compatibility facade.
 * SparkWitch 职业与阵营注册的内部归属；SparkWitchRoles 继续作为兼容门面。
 */
public final class SparkWitchRoleRegistry {
    public static final Identifier GRAND_WITCH_ID = SparkWitch.id("grand_witch");
    public static final Identifier ACCOMPLICE_ID = SparkWitch.id("accomplice");
    public static final Identifier APPRENTICE_WITCH_ID = SparkWitch.id("apprentice_witch");
    public static final Identifier MURDEROUS_WITCH_ID = SparkWitch.id("murderous_witch");
    public static final Identifier PIG_GOD_ID = SparkWitch.id("pig_god");
    public static final Identifier SAINT_ID = SaintRules.SAINT_ROLE_ID;
    public static final Identifier PERFUMER_ID = SparkWitch.id("perfumer");
    public static final Identifier NINJA_ID = NinjaRules.ROLE_ID;
    public static final Identifier HUNTER_ID = HunterRules.ROLE_ID;
    public static final Identifier ORTHOPEDIST_ID = OrthopedistRules.ROLE_ID;

    private static Role grandWitch;
    private static Role accomplice;
    private static Role apprenticeWitch;
    private static Role murderousWitch;
    private static Role pigGod;
    private static Role saint;
    private static Role perfumer;
    private static Role ninja;
    private static Role hunter;
    private static Role orthopedist;
    private static boolean registered;

    private SparkWitchRoleRegistry() {
    }

    public static synchronized void register() {
        if (registered) {
            SparkWitchAssassinGuessOrder.appendToTail(assassinGuessRolesInOrder());
            return;
        }
        registered = true;

        SparkFactionApi.bootstrap();
        registerFactions();
        registerFactionApiRoles();
        registerNativeWatheRoles();

        SparkWitchAssassinGuessOrder.appendToTail(assassinGuessRolesInOrder());
    }

    public static synchronized void refreshAssassinGuessRoleOrder() {
        ensureRegistered();
        SparkWitchAssassinGuessOrder.appendToTail(assassinGuessRolesInOrder());
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

    public static Role saint() {
        ensureRegistered();
        return saint;
    }

    public static Role perfumer() {
        ensureRegistered();
        return perfumer;
    }

    public static Role ninja() {
        ensureRegistered();
        return ninja;
    }

    public static Role hunter() {
        ensureRegistered();
        return hunter;
    }

    public static Role orthopedist() {
        ensureRegistered();
        return orthopedist;
    }

    public static boolean isSparkWitchRole(Role role) {
        ensureRegistered();
        return isRegisteredSparkWitchRole(role);
    }

    public static List<Role> assassinGuessRoles() {
        ensureRegistered();
        return assassinGuessRolesInOrder();
    }

    private static void registerFactions() {
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
    }

    private static void registerFactionApiRoles() {
        grandWitch = SparkFactionApi.registerRole(FactionRoleDefinition.builder(GRAND_WITCH_ID, SparkWitchFactions.WITCH)
                .color(0xF2DFF7)
                .moodType(Role.MoodType.FAKE)
                .maxSprintTime(-1)
                .canSeeTime(true)
                .appearanceCondition(RoleAppearanceCondition.minPlayers(18))
                .build());
        accomplice = SparkFactionApi.registerRole(FactionRoleDefinition.builder(ACCOMPLICE_ID, SparkWitchFactions.WITCH)
                .color(0x6B338A)
                .moodType(Role.MoodType.FAKE)
                .maxSprintTime(-1)
                .canSeeTime(true)
                .appearanceCondition(RoleAppearanceCondition.minPlayers(18))
                .build());
        pigGod = SparkFactionApi.registerRole(FactionRoleDefinition.builder(PIG_GOD_ID, FactionIds.CIVILIAN)
                .color(PigGodRules.COLOR)
                .moodType(Role.MoodType.REAL)
                .maxSprintTime(GameConstants.getInTicks(0, 10))
                .canSeeTime(false)
                .nativeWatheFaction(Faction.CIVILIAN)
                .build());
        saint = SparkFactionApi.registerRole(FactionRoleDefinition.builder(SAINT_ID, FactionIds.CIVILIAN)
                .color(SaintRules.COLOR)
                .moodType(Role.MoodType.NONE)
                .maxSprintTime(GameConstants.getInTicks(0, 10))
                .canSeeTime(false)
                .nativeWatheFaction(Faction.CIVILIAN)
                .build());
        orthopedist = SparkFactionApi.registerRole(FactionRoleDefinition.builder(ORTHOPEDIST_ID, FactionIds.CIVILIAN)
                .color(OrthopedistRules.COLOR)
                .moodType(Role.MoodType.REAL)
                .maxSprintTime(GameConstants.getInTicks(0, 10))
                .canSeeTime(false)
                .nativeWatheFaction(Faction.CIVILIAN)
                .build());
        perfumer = SparkFactionApi.registerRole(FactionRoleDefinition.builder(PERFUMER_ID, FactionIds.CIVILIAN)
                .color(PerfumerRules.ROLE_COLOR)
                .moodType(Role.MoodType.REAL)
                .maxSprintTime(GameConstants.getInTicks(0, 10))
                .canSeeTime(false)
                .nativeWatheFaction(Faction.CIVILIAN)
                .build());
        // Wathe's special-killer selector consumes each registered non-vanilla role candidate once,
        // so the default spawn group of one is also Ninja's one-per-round maximum.
        // Wathe 的特殊杀手分配器每局只消费一次非原版职业候选，默认单人组即为忍者每局至多一人。
        ninja = SparkFactionApi.registerRole(FactionRoleDefinition.builder(NINJA_ID, FactionIds.KILLER)
                .color(NinjaRules.COLOR)
                .moodType(Role.MoodType.FAKE)
                .maxSprintTime(-1)
                .canSeeTime(true)
                .nativeWatheFaction(Faction.KILLER)
                .build());
        hunter = SparkFactionApi.registerRole(FactionRoleDefinition.builder(HUNTER_ID, FactionIds.KILLER)
                .color(HunterRules.COLOR)
                .moodType(Role.MoodType.FAKE)
                .maxSprintTime(-1)
                .canSeeTime(true)
                .nativeWatheFaction(Faction.KILLER)
                .appearanceCondition(context -> HunterOrthopedistPairingRules.canRandomHunterAppear(
                        context.gameComponent().isRoleEnabled(orthopedist),
                        context.totalPlayerCount()
                ))
                .build());
    }

    private static void registerNativeWatheRoles() {
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
    }

    private static void ensureRegistered() {
        if (!registered) {
            register();
        }
    }

    private static List<Role> assassinGuessRolesInOrder() {
        return List.of(
                apprenticeWitch,
                orthopedist,
                saint,
                perfumer,
                pigGod,
                ninja,
                hunter,
                murderousWitch,
                accomplice,
                grandWitch
        );
    }

    private static boolean isRegisteredSparkWitchRole(Role role) {
        return role == grandWitch
                || role == accomplice
                || role == apprenticeWitch
                || role == murderousWitch
                || role == pigGod
                || role == saint
                || role == perfumer
                || role == ninja;
    }
}
