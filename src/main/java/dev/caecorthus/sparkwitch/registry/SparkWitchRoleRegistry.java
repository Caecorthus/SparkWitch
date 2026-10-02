package dev.caecorthus.sparkwitch.registry;

import dev.caecorthus.sparkfactionapi.api.FactionCapabilities;
import dev.caecorthus.sparkfactionapi.api.FactionDefinition;
import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.FactionRoleDefinition;
import dev.caecorthus.sparkfactionapi.api.PoliceRoles;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchFactions;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeRules;
import dev.caecorthus.sparkwitch.roles.civilian.judge.PoliceSlotAssignmentService;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertRules;
import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaRules;
import dev.caecorthus.sparkwitch.roles.civilian.guardianangel.GuardianAngelRole;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistRules;
import dev.caecorthus.sparkwitch.roles.civilian.perfumer.PerfumerRules;
import dev.caecorthus.sparkwitch.roles.civilian.piggod.PigGodRules;
import dev.caecorthus.sparkwitch.roles.civilian.prophet.ProphetRules;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintRules;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.FisherRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.tarotreader.TarotReaderRules;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaRole;
import dev.caecorthus.sparkwitch.roles.civilian.windspirit.WindSpiritRole;
import dev.caecorthus.sparkwitch.roles.killer.bellringer.BellRingerRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterRules;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperRules;
import dev.caecorthus.sparkwitch.roles.killer.ninja.NinjaRules;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurRole;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerRules;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.WitchMaidenRules;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendRules;
import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderRules;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithRole;
import dev.caecorthus.sparkwitch.roles.witch.curser.CurserRole;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerRole;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerRules;
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
    public static final Identifier EMMA_ID = EmmaRules.ROLE_ID;
    public static final Identifier GRAND_WITCH_ID = SparkWitch.id("grand_witch");
    public static final Identifier ACCOMPLICE_ID = SparkWitch.id("accomplice");
    public static final Identifier APPRENTICE_WITCH_ID = SparkWitch.id("apprentice_witch");
    public static final Identifier MURDEROUS_WITCH_ID = SparkWitch.id("murderous_witch");
    public static final Identifier PIG_GOD_ID = SparkWitch.id("pig_god");
    public static final Identifier PROPHET_ID = ProphetRules.ROLE_ID;
    public static final Identifier SAINT_ID = SaintRules.SAINT_ROLE_ID;
    public static final Identifier PERFUMER_ID = SparkWitch.id("perfumer");
    public static final Identifier NINJA_ID = NinjaRules.ROLE_ID;
    public static final Identifier BLACK_RAVEN_ID = BlackRavenRules.ROLE_ID;
    public static final Identifier HUNTER_ID = HunterRules.ROLE_ID;
    public static final Identifier ORTHOPEDIST_ID = OrthopedistRules.ROLE_ID;
    public static final Identifier KIDNAPPER_ID = KidnapperRules.ROLE_ID;
    public static final Identifier TAROT_READER_ID = SparkWitch.id("tarot_reader");
    public static final Identifier WRAITH_ID = SparkWitch.id("wraith");
    public static final Identifier WIND_SPIRIT_ID = WindSpiritRole.ID;
    public static final Identifier GUARDIAN_ANGEL_ID = GuardianAngelRole.ID;
    public static final Identifier VENDETTA_ID = VendettaRole.ID;
    public static final Identifier SABOTEUR_ID = SaboteurRole.ID;
    public static final Identifier WITCH_MAIDEN_ID = WitchMaidenRules.ROLE_ID;
    public static final Identifier CURSER_ID = CurserRole.ID;
    public static final Identifier BELL_RINGER_ID = BellRingerRules.ROLE_ID;
    public static final Identifier TIME_STEALER_ID = TimeStealerRules.ROLE_ID;
    public static final Identifier JUDGE_ID = JudgeRules.ROLE_ID;
    public static final Identifier CONTROL_EXPERT_ID = ControlExpertRules.ROLE_ID;
    public static final Identifier SEEKER_ID = SeekerRules.ROLE_ID;
    public static final Identifier FISHER_ID = FisherRules.ROLE_ID;
    public static final Identifier FIEND_ID = FiendRules.ROLE_ID;
    public static final Identifier INSIDER_ID = InsiderRules.ROLE_ID;
    public static final Identifier RIFTWALKER_ID = RiftwalkerRules.ROLE_ID;

    private static Role emma;
    private static Role grandWitch;
    private static Role accomplice;
    private static Role apprenticeWitch;
    private static Role murderousWitch;
    private static Role pigGod;
    private static Role prophet;
    private static Role saint;
    private static Role perfumer;
    private static Role ninja;
    private static Role blackRaven;
    private static Role hunter;
    private static Role orthopedist;
    private static Role kidnapper;
    private static Role tarotReader;
    private static Role wraith;
    private static Role windSpirit;
    private static Role guardianAngel;
    private static Role vendetta;
    private static Role saboteur;
    private static Role witchMaiden;
    private static Role curser;
    private static Role bellRinger;
    private static Role timeStealer;
    private static Role judge;
    private static Role controlExpert;
    private static Role seeker;
    private static Role fisher;
    private static Role fiend;
    private static Role insider;
    private static Role riftwalker;
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
        PoliceRoles.register(JUDGE_ID);
        PoliceRoles.register(PoliceSlotAssignmentService.EMMA_ID);
        // Police classification only: SparkStrength appends its tablet, cop slots come from PoliceSlotAssignmentService.
        // 仅作警察分类：SparkStrength 据此追加平板，警位由 PoliceSlotAssignmentService 分配。
        PoliceRoles.register(CONTROL_EXPERT_ID);
        // Police classification only, like the Control Expert; cop slots come from PoliceSlotAssignmentService.
        // 与控场专家相同，仅作警察分类；警位由 PoliceSlotAssignmentService 分配。
        PoliceRoles.register(SEEKER_ID);
        // Police classification only, for the SparkStrength tablet's police channel (D4), as SparkStrength does for the
        // Corrupt Cop; the Insider stays a Wathe neutral and never takes a cop slot.
        // 仅作警察分类，用于 SparkStrength 平板的警察频道（D4），与 SparkStrength 对黑警的处理相同；内应仍是 Wathe 中立，
        // 从不占用警位。
        PoliceRoles.register(INSIDER_ID);
        WatheRoles.SPECIAL_ROLES.add(WraithRole.ROLE);
        wraith = WatheRoles.registerRole(WraithRole.ROLE);

        SparkWitchAssassinGuessOrder.appendToTail(assassinGuessRolesInOrder());
    }

    public static synchronized void refreshAssassinGuessRoleOrder() {
        ensureRegistered();
        SparkWitchAssassinGuessOrder.appendToTail(assassinGuessRolesInOrder());
    }

    public static Role emma() {
        ensureRegistered();
        return emma;
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

    public static Role prophet() {
        ensureRegistered();
        return prophet;
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

    public static Role blackRaven() {
        ensureRegistered();
        return blackRaven;
    }

    public static Role hunter() {
        ensureRegistered();
        return hunter;
    }

    public static Role orthopedist() {
        ensureRegistered();
        return orthopedist;
    }

    public static Role kidnapper() {
        ensureRegistered();
        return kidnapper;
    }

    public static Role tarotReader() {
        ensureRegistered();
        return tarotReader;
    }

    public static Role wraith() {
        ensureRegistered();
        return wraith;
    }

    public static Role windSpirit() {
        ensureRegistered();
        return windSpirit;
    }

    public static Role guardianAngel() {
        ensureRegistered();
        return guardianAngel;
    }

    public static Role vendetta() {
        ensureRegistered();
        return vendetta;
    }

    public static Role saboteur() {
        ensureRegistered();
        return saboteur;
    }

    public static Role witchMaiden() {
        ensureRegistered();
        return witchMaiden;
    }

    public static Role curser() {
        ensureRegistered();
        return curser;
    }

    public static Role bellRinger() {
        ensureRegistered();
        return bellRinger;
    }

    public static Role timeStealer() {
        ensureRegistered();
        return timeStealer;
    }

    public static Role judge() {
        ensureRegistered();
        return judge;
    }

    public static Role controlExpert() {
        ensureRegistered();
        return controlExpert;
    }

    public static Role seeker() {
        ensureRegistered();
        return seeker;
    }

    public static Role fisher() {
        ensureRegistered();
        return fisher;
    }

    public static Role fiend() {
        ensureRegistered();
        return fiend;
    }

    public static Role insider() {
        ensureRegistered();
        return insider;
    }

    public static Role riftwalker() {
        ensureRegistered();
        return riftwalker;
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
        emma = SparkFactionApi.registerRole(FactionRoleDefinition.builder(EMMA_ID, FactionIds.CIVILIAN)
                .color(EmmaRules.COLOR)
                .moodType(Role.MoodType.REAL)
                .maxSprintTime(GameConstants.getInTicks(0, 10))
                .canSeeTime(false)
                .nativeWatheFaction(Faction.CIVILIAN)
                .build());
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
                // Exclude natural selection; explicit recruitment still assigns this registered role.
                // 排除自然抽选；主动招募仍可直接赋予这个已注册职业。
                .appearanceCondition(context -> false)
                .build());
        // Special accomplice (accomplice-variant pool), registered right after the Accomplice rather than appended:
        // nothing may be registered after the Insider (InsiderRegistrationContractTest), so, like the Curser, it sits
        // mid-list. The SparkWitch assassin-guess tail is re-sorted, so the shifted Wathe role index is harmless.
        // 特殊共犯（共犯变体池），紧接在共犯之后注册而非追加到末尾：内应之后禁止再注册职业
        // （InsiderRegistrationContractTest），因此与诅咒者一样插在中间。SparkWitch 刺客猜测尾部会重新排序，
        // 因此 Wathe 职业下标的偏移无害。
        riftwalker = SparkFactionApi.registerRole(RiftwalkerRole.DEFINITION);
        windSpirit = SparkFactionApi.registerRole(WindSpiritRole.DEFINITION);
        guardianAngel = SparkFactionApi.registerRole(GuardianAngelRole.DEFINITION);
        vendetta = SparkFactionApi.registerRole(VendettaRole.DEFINITION);
        saboteur = SparkFactionApi.registerRole(SaboteurRole.DEFINITION);
        curser = SparkFactionApi.registerRole(CurserRole.DEFINITION);
        pigGod = SparkFactionApi.registerRole(FactionRoleDefinition.builder(PIG_GOD_ID, FactionIds.CIVILIAN)
                .color(PigGodRules.COLOR)
                .moodType(Role.MoodType.REAL)
                .maxSprintTime(GameConstants.getInTicks(0, 10))
                .canSeeTime(false)
                .nativeWatheFaction(Faction.CIVILIAN)
                .build());
        saint = SparkFactionApi.registerRole(FactionRoleDefinition.builder(SAINT_ID, FactionIds.CIVILIAN)
                .color(SaintRules.COLOR)
                .moodType(Role.MoodType.REAL)
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
        prophet = SparkFactionApi.registerRole(FactionRoleDefinition.builder(PROPHET_ID, FactionIds.CIVILIAN)
                .color(ProphetRules.ROLE_COLOR)
                .moodType(Role.MoodType.REAL)
                .maxSprintTime(GameConstants.getInTicks(0, 10))
                .canSeeTime(false)
                .nativeWatheFaction(Faction.CIVILIAN)
                .build());
        tarotReader = SparkFactionApi.registerRole(FactionRoleDefinition.builder(TAROT_READER_ID, FactionIds.CIVILIAN)
                .color(TarotReaderRules.COLOR)
                .moodType(Role.MoodType.REAL)
                .maxSprintTime(GameConstants.getInTicks(0, 10))
                .canSeeTime(false)
                .appearanceCondition(RoleAppearanceCondition.ALWAYS)
                .nativeWatheFaction(Faction.CIVILIAN)
                .build());
        judge = SparkFactionApi.registerRole(FactionRoleDefinition.builder(JUDGE_ID, FactionIds.CIVILIAN)
                .color(JudgeRules.ROLE_COLOR)
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
        blackRaven = SparkFactionApi.registerRole(FactionRoleDefinition.builder(BLACK_RAVEN_ID, FactionIds.KILLER)
                .color(BlackRavenRules.COLOR)
                .moodType(Role.MoodType.FAKE)
                .maxSprintTime(-1)
                .canSeeTime(true)
                .nativeWatheFaction(Faction.KILLER)
                .build());
        witchMaiden = SparkFactionApi.registerRole(FactionRoleDefinition.builder(WITCH_MAIDEN_ID, FactionIds.KILLER)
                .color(WitchMaidenRules.COLOR)
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
        // Like Ninja, the default one-player spawn group makes this a one-per-round killer candidate.
        // 与忍者相同，默认单人分配组保证绑架者每局至多一人。
        kidnapper = SparkFactionApi.registerRole(FactionRoleDefinition.builder(KIDNAPPER_ID, FactionIds.KILLER)
                .color(KidnapperRules.COLOR)
                .moodType(Role.MoodType.FAKE)
                .maxSprintTime(-1)
                .canSeeTime(true)
                .nativeWatheFaction(Faction.KILLER)
                .build());
        // Appended last so existing role registration order stays unchanged; the default one-player spawn
        // group keeps the Bell Ringer to at most one per round, like Ninja and Kidnapper.
        // 追加在最后以保持既有职业注册顺序不变；与忍者、绑架者相同，默认单人分配组保证敲钟人每局至多一人。
        bellRinger = SparkFactionApi.registerRole(FactionRoleDefinition.builder(BELL_RINGER_ID, FactionIds.KILLER)
                .color(BellRingerRules.COLOR)
                .moodType(Role.MoodType.FAKE)
                .maxSprintTime(-1)
                .canSeeTime(true)
                .nativeWatheFaction(Faction.KILLER)
                .build());
        // Appended last so existing registration order stays unchanged; Vigilante-equivalent civilian parameters.
        // 追加在最后以保持既有注册顺序不变；参数与义警等同的平民职业。
        controlExpert = SparkFactionApi.registerRole(FactionRoleDefinition.builder(CONTROL_EXPERT_ID, FactionIds.CIVILIAN)
                .color(ControlExpertRules.COLOR)
                .moodType(Role.MoodType.REAL)
                .maxSprintTime(GameConstants.getInTicks(0, 10))
                .canSeeTime(false)
                .nativeWatheFaction(Faction.CIVILIAN)
                .build());
        // Appended last so existing registration order stays unchanged; Vigilante-equivalent civilian parameters.
        // Drawn only when the SparkStrength tablet item exists (Q7); a forced Seeker still works without it.
        // 追加在最后以保持既有注册顺序不变；参数与义警等同的平民职业。仅当 SparkStrength 平板物品存在时
        // 才会被抽到（Q7）；被强制指定时即使没有平板也能运作。
        seeker = SparkFactionApi.registerRole(FactionRoleDefinition.builder(SEEKER_ID, FactionIds.CIVILIAN)
                .color(SeekerRules.COLOR)
                .moodType(Role.MoodType.REAL)
                .maxSprintTime(GameConstants.getInTicks(0, 10))
                .canSeeTime(false)
                .nativeWatheFaction(Faction.CIVILIAN)
                .appearanceCondition(context -> SeekerRules.shouldAppear())
                .build());
        // Appended last so existing registration order stays unchanged; the default one-player spawn group
        // keeps the Time Stealer to one killer slot and at most one per round, like the Bell Ringer.
        // 追加在最后以保持既有注册顺序不变；与敲钟人相同，默认单人分配组使窃时者只占一个杀手位且每局至多一人。
        timeStealer = SparkFactionApi.registerRole(FactionRoleDefinition.builder(TIME_STEALER_ID, FactionIds.KILLER)
                .color(TimeStealerRules.COLOR)
                .moodType(Role.MoodType.FAKE)
                .maxSprintTime(-1)
                .canSeeTime(true)
                .nativeWatheFaction(Faction.KILLER)
                .build());
        // Appended last so existing registration order stays unchanged; an ordinary task-funded civilian.
        // 追加在最后以保持既有注册顺序不变；依靠任务赚钱的普通平民职业。
        fisher = SparkFactionApi.registerRole(FactionRoleDefinition.builder(FISHER_ID, FactionIds.CIVILIAN)
                .color(FisherRules.COLOR)
                .moodType(Role.MoodType.REAL)
                .maxSprintTime(GameConstants.getInTicks(0, 10))
                .canSeeTime(false)
                .nativeWatheFaction(Faction.CIVILIAN)
                .build());
        // Appended last so existing registration order stays unchanged; a Wathe-native neutral (FAKE mood, no tasks),
        // drawn only in rounds with 18+ players (the same population test as Wathe's own player-count condition).
        // Never a Witch-skill role: kept out of isRegisteredSparkWitchRole.
        // 追加在最后以保持既有注册顺序不变；Wathe 原生中立（伪装情绪、无任务），仅在 18 人及以上对局中抽取
        // （与 Wathe 自带人数条件的判断相同）。不是魔女技能职业：不加入 isRegisteredSparkWitchRole。
        fiend = SparkFactionApi.registerRole(FactionRoleDefinition.builder(FIEND_ID, FactionIds.NEUTRAL)
                .color(FiendRules.COLOR)
                .moodType(Role.MoodType.FAKE)
                .maxSprintTime(-1)
                .canSeeTime(false)
                .nativeWatheFaction(Faction.NEUTRAL)
                .appearanceCondition(context -> context.getTotalPlayerCount() >= FiendRules.MIN_PLAYERS)
                .build());
        // Appended last so existing registration order stays unchanged; a Wathe-native neutral with the Corrupt Cop's
        // profile (FAKE mood with tasks, civilian stamina, round clock). Never drawn at random: the Insider is paired with
        // a drawn Corrupt Cop after neutral assignment (D1). Never a Witch-skill role: kept out of isRegisteredSparkWitchRole.
        // 追加在最后以保持既有注册顺序不变；Wathe 原生中立，参数与黑警相同（伪装情绪但有任务、平民体力、可见回合时间）。
        // 从不随机抽取：中立分配完成后才与已抽到的黑警配对（D1）。不是魔女技能职业：不加入 isRegisteredSparkWitchRole。
        insider = SparkFactionApi.registerRole(FactionRoleDefinition.builder(INSIDER_ID, FactionIds.NEUTRAL)
                .color(InsiderRules.COLOR)
                .moodType(Role.MoodType.FAKE)
                .maxSprintTime(GameConstants.getInTicks(0, 10))
                .canSeeTime(true)
                .nativeWatheFaction(Faction.NEUTRAL)
                .appearanceCondition(context -> false)
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
                prophet,
                orthopedist,
                saint,
                perfumer,
                pigGod,
                tarotReader,
                fisher,
                judge,
                ninja,
                blackRaven,
                witchMaiden,
                hunter,
                kidnapper,
                bellRinger,
                timeStealer,
                murderousWitch,
                accomplice,
                riftwalker,
                grandWitch,
                emma,
                controlExpert,
                seeker,
                fiend,
                insider,
                windSpirit,
                guardianAngel,
                vendetta,
                saboteur,
                curser
        );
    }

    private static boolean isRegisteredSparkWitchRole(Role role) {
        return role == emma
                || role == grandWitch
                || role == accomplice
                || role == apprenticeWitch
                || role == prophet
                || role == murderousWitch
                || role == pigGod
                || role == saint
                || role == perfumer
                || role == tarotReader
                || role == ninja
                || role == kidnapper
                || role == blackRaven
                || role == witchMaiden
                || role == bellRinger
                // Shared skill path (Witches' Sabbath) only; grants no witch skill panel access (the panel reads the
                // role's accomplice-variant hooks, D13). 仅用于共享技能路径（魔女集会）；不授予魔女技能面板资格
                // （面板读取本职业的特殊共犯回调，D13）。
                || role == riftwalker;
    }
}
