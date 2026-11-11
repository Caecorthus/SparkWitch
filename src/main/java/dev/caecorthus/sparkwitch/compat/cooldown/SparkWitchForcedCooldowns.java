package dev.caecorthus.sparkwitch.compat.cooldown;

import dev.caecorthus.sparkfactionapi.api.cooldown.ForcedCooldowns;
import dev.caecorthus.sparkfactionapi.api.cooldown.RoleSkillCooldownStore;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.compat.NoellesRoleIds;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.ApprenticePlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Purify.PurifyAbility;
import dev.caecorthus.sparkwitch.roles.civilian.athlete.AthleteComponent;
import dev.caecorthus.sparkwitch.roles.civilian.athlete.AthleteRules;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindComponent;
import dev.caecorthus.sparkwitch.roles.civilian.noa.NoaComponent;
import dev.caecorthus.sparkwitch.roles.civilian.noa.NoaRules;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindParticipants;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindRules;
import dev.caecorthus.sparkwitch.roles.civilian.blind.kit.BlindLoadoutService;
import dev.caecorthus.sparkwitch.roles.civilian.hanna.HannaComponent;
import dev.caecorthus.sparkwitch.roles.civilian.hanna.HannaRules;
import dev.caecorthus.sparkwitch.roles.civilian.junker.JunkerPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.junker.JunkerRules;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumRules;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryComponent;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryRules;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistRules;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowRules;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorRules;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianConstants;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeComponent;
import dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeRules;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurRules;
import dev.caecorthus.sparkwitch.roles.killer.hiro.HiroRules;
import dev.caecorthus.sparkwitch.roles.killer.hiro.returning.HiroReturnComponent;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterRules;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterSecondSkillComponent;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.WitchMaidenRules;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherRules;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanPlayerComponent;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanRules;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendMomentWorldComponent;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendParticipation;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.assassin.AssassinPlayerComponent;

import java.util.List;
import java.util.Set;

import static dev.caecorthus.sparkwitch.compat.cooldown.SimpleRoleSkillCooldownStore.anyGameStatus;
import static dev.caecorthus.sparkwitch.compat.cooldown.SimpleRoleSkillCooldownStore.whileActive;

/**
 * Registers SparkWitch's and NoellesRoles' cooldown counters with the SparkFactionAPI forced-cooldown contract
 * ({@code api.cooldown}), so features that force cooldowns on other players (penalties, auras) write them through
 * one monotonic API instead of each counter. Called once from {@code SparkWitch.onInitialize}. The store order is
 * observable (it is the {@code ForcedCooldowns.slots} order) and must stay stable. NoellesRoles is a hard dependency.
 * Stores that override a {@code RoleSkillCooldownStore} default keep their own class; the others are built here from
 * {@link SimpleRoleSkillCooldownStore}, each with its own round gate and write kind.
 * 将 SparkWitch 与 NoellesRoles 的冷却计数注册到 SparkFactionAPI 强制冷却契约（api.cooldown），使对他人强制冷却的功能
 * （惩罚、光环）经由统一的单调 API 写入，而不是逐个写各计数。由 SparkWitch.onInitialize 调用一次。存储顺序可观察
 * （即 ForcedCooldowns.slots 的顺序），必须保持稳定。NoellesRoles 是硬依赖。覆盖了 RoleSkillCooldownStore 默认方法的存储
 * 保留各自的类；其余存储在此由 SimpleRoleSkillCooldownStore 构建，各自声明回合门控与写入方式。
 */
public final class SparkWitchForcedCooldowns {
    static final Identifier ORTHOPEDIST_ID = SparkWitch.id("orthopedist");
    static final Identifier SABOTEUR_ID = SparkWitch.id("saboteur");
    static final Identifier NOELLES_ASSASSIN_ID = Identifier.of(NoellesRoleIds.NAMESPACE, "assassin");
    static final Identifier NOELLES_ASSASSIN_ROLE_ID = Identifier.of(NoellesRoleIds.NAMESPACE, "assassin");
    static final Identifier BLIND_ATTUNE_ID = SparkWitch.id("blind_attune");
    static final Identifier FIEND_DASH_ID = SparkWitch.id("fiend_dash");
    static final Identifier ATHLETE_DASH_ID = AthleteRules.DASH_SKILL_ID;
    static final Identifier APPRENTICE_PURIFY_ID = SparkWitch.id("apprentice_purify");
    static final Identifier MAGICIAN_ID = SparkWitch.id("magician");
    static final Identifier MINI_WITHER_TOGGLE_ID = SparkWitch.id("mini_wither_toggle");
    static final Identifier BLACK_RAVEN_CROW_ID = BlackRavenCrowRules.SKILL_ID;
    static final Identifier NOA_PAINT_ID = NoaRules.PAINT_SKILL_ID;
    static final Identifier RAGPICKER_SEARCH_ID = SparkWitch.id("ragpicker_search");
    static final Identifier DOMINATOR_SKILL_ID = SparkWitch.id("dominator_skill");
    static final Identifier NATSUME_HYPNOSIS_ID = NatsumeRules.HYPNOSIS_SKILL_ID;
    static final Identifier SPECTER_SKILL_ID = SparkWitch.id("specter_skill");
    static final Identifier SPECTER_OTHERWORLD_ID = SparkWitch.id("specter_otherworld");
    static final Identifier SAVIOR_DOMAIN_ID = SparkWitch.id("savior_domain");
    static final Identifier MEDIUM_SEANCE_ID = MediumRules.SEANCE_SKILL_ID;
    static final Identifier FERRYMAN_PARDON_ID = SparkWitch.id("ferryman_pardon");
    static final Identifier FERRYMAN_FERRY_ID = SparkWitch.id("ferryman_ferry");
    static final Identifier HANNA_FLOAT_ID = HannaRules.FLOAT_SKILL_ID;
    static final Identifier SHERRY_DEDUCTION_ID = SherryRules.DEDUCTION_SKILL_ID;
    static final Identifier SHERRY_MIGHTY_FORCE_ID = SherryRules.MIGHTY_FORCE_SKILL_ID;
    static final Identifier HIRO_RETURN_ID = HiroRules.RETURN_SKILL_ID;

    private static final Set<Identifier> NOELLES_ASSASSIN_ROLES = Set.of(NOELLES_ASSASSIN_ROLE_ID);

    private static boolean registered;

    private SparkWitchForcedCooldowns() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        for (RoleSkillCooldownStore store : roleSkillStores()) {
            ForcedCooldowns.registerRoleSkillStore(store);
        }
        ForcedCooldowns.registerItemNominalProvider(new SparkWitchItemCooldownNominals());
        ForcedCooldowns.registerItemExemption(item -> isExemptItem(Registries.ITEM.getId(item)));
    }

    /** Registration order of the role-skill stores. / 职业技能存储的注册顺序。 */
    static List<RoleSkillCooldownStore> roleSkillStores() {
        return List.of(
                new WitchSkillCooldownStore(),
                new SaintHellfireCooldownStore(),
                orthopedist(),
                saboteur(),
                new NoellesAbilityCooldownStore(),
                new NoellesTaotieSwallowCooldownStore(),
                noellesAssassin(),
                // Appended last: earlier slots keep their positions. / 追加在末尾：之前各槽位置不变。
                blindAttune(),
                // Fiend Dash (owner decision 2026-10-04), appended after the Blind.
                // 魔人疾驰（所有者 2026-10-04 决定），追加在盲人之后。
                fiendDash(),
                // Apprentice Purify (owner decision 2026-10-06), appended after the Fiend Dash.
                // 预备魔女净化（所有者 2026-10-06 决定），追加在魔人疾驰之后。
                apprenticePurify(),
                // Magician (audit fix 2026-10-07), appended after the Apprentice Purify.
                // 魔术师（2026-10-07 审计修复），追加在预备魔女净化之后。
                magician(),
                // Witch Maiden Mini Wither toggle (owner 2026-10-20), appended after the Magician.
                // 巫女小凋灵切换（所有者 2026-10-20），追加在魔术师之后。
                miniWitherToggle(),
                // Black Raven Raven Form (plan 2026-10-21), appended after the Mini Wither.
                // 黑羽鸦化鸦（2026-10-21 计划），追加在小凋灵之后。
                blackRavenCrow(),
                // Athlete dash (PR #191), appended after the Raven Form. / 运动员冲刺（PR #191），追加在化鸦之后。
                athleteDash(),
                // Noa painting (PR #186), appended after the Athlete dash. / 诺亚作画（PR #186），追加在运动员冲刺之后。
                noaPaint(),
                // Ragpicker Search (owner decision 2026-11-04), appended after the Noa painting.
                // 拾荒者搜刮（所有者 2026-11-04 决定），追加在诺亚作画之后。
                ragpickerSearch(),
                // Dominator mark/release key (PR #192), appended after the Ragpicker Search.
                // 支配者标记/释放键（PR #192），追加在拾荒者搜刮之后。
                dominatorSkill(),
                // 夏目安安 hypnosis (PR #194), appended after the Dominator key.
                // 夏目安安催眠（PR #194），追加在支配者按键之后。
                natsumeHypnosis(),
                // Specter skill one mark/shriek key (PR #207), appended after 夏目安安's hypnosis.
                // 幽魂技能一标记/尖啸键（PR #207），追加在夏目安安催眠之后。
                specterSkill(),
                // Specter Otherworld (PR #207), store 20, appended after the Specter's skill-one store.
                // 幽魂里世界（PR #207），第 20 个存储，追加在幽魂技能一存储之后。
                specterOtherworld(),
                // Savior G domain (PR #200), store 21, appended after the Specter Otherworld.
                // 救世主 G 领域（PR #200），第 21 个存储，追加在幽魂里世界之后。
                saviorDomain(),
                // Medium 通灵 (PR #209), store 22, appended after the Savior domain.
                // 通灵师通灵（PR #209），第 22 个存储，追加在救世主领域之后。
                mediumSeance(),
                // Charon pardon (shared key G, PR #202), store 23, appended after the Medium's 通灵.
                // 卡戎宽恕（共享 G 键，PR #202），第 23 个存储，追加在通灵师通灵之后。
                ferrymanPardon(),
                // Charon ferry (Role Skill 2, PR #202), store 24, appended after the Charon's pardon.
                // 卡戎摆渡（职业技能 2，PR #202），第 24 个存储，追加在卡戎宽恕之后。
                ferrymanFerry(),
                // Tono Hanna's float (PR #212), store 25, appended after the Charon's ferry.
                // 远野汉娜浮游（PR #212），第 25 个存储，追加在卡戎摆渡之后。
                hannaFloat(),
                // 橘雪莉 推演 (shared key G, PR #206), store 26, appended after Tono Hanna's float.
                // 橘雪莉推演（共享 G 键，PR #206），第 26 个存储，追加在远野汉娜浮游之后。
                sherryDeduction(),
                // 橘雪莉 巨力魔法 (Role Skill 2, PR #206), store 27, appended after her 推演.
                // 橘雪莉巨力魔法（职业技能 2，PR #206），第 27 个存储，追加在她的推演之后。
                sherryMightyForce(),
                // 二阶堂希罗 死亡回归 (shared key G, 2026-11-11), store 28, appended after 橘雪莉's 巨力魔法.
                // 二阶堂希罗的死亡回归（共享 G 键，2026-11-11），第 28 个存储，追加在橘雪莉巨力魔法之后。
                hiroReturn()
        );
    }

    /**
     * 二阶堂希罗's 死亡回归 cooldown (2026-11-11), kept as a counting-down tick total in the owner-synced
     * {@code sparkwitch:nikaido_hiro_return} component. An ACTIVE round, the raw role and no running window: the
     * cooldown is written when the window ends, so a running window is never forced (Dominator / Specter / Charon
     * precedent). Remaining time includes the 90 s opening cooldown; the nominal is the 45 s cooldown after the skill
     * ends. The setter is exact (floored at 0) and syncs the owner. The skill has no item, so SparkFactionAPI's admin
     * {@code clearCooldown} (held item only) does not reach it. The Rope's interrupt cooldown is an item cooldown and
     * goes through the item nominal table instead.
     * 二阶堂希罗的死亡回归冷却（2026-11-11），以倒计时刻数保存在只同步给本人的 {@code sparkwitch:nikaido_hiro_return} 组件中。
     * ACTIVE 回合、真实职业且没有进行中的窗口：冷却在窗口结束时写入，因此进行中的窗口绝不强制（支配者、幽魂、卡戎先例）。剩余
     * 时间包含 90 秒开局冷却；标准冷却为技能结束后的 45 秒。setter 为精确写入（下限为 0）并同步给本人。该技能没有物品，因此
     * SparkFactionAPI 的管理员 {@code clearCooldown}（只清手持物品）触及不到它。绳索的打断冷却属于物品冷却，改由物品标准冷却表处理。
     */
    static SimpleRoleSkillCooldownStore hiroReturn() {
        return SimpleRoleSkillCooldownStore.exact(
                HIRO_RETURN_ID,
                whileActive((game, player) -> HiroRules.isHiro(game.getRole(player))
                        && !HiroReturnComponent.KEY.get(player).isWindowActive(player.getWorld().getTime())),
                player -> HiroReturnComponent.KEY.get(player).getCooldownTicks(),
                () -> HiroRules.RETURN_COOLDOWN_TICKS,
                (player, ticks) -> HiroReturnComponent.KEY.get(player).setCooldownTicks(ticks)
        );
    }

    /**
     * The Medium's 通灵 cooldown (PR #209), kept as counting-down ticks in the owner-synced {@code sparkwitch:medium}
     * component. An ACTIVE round and the raw role (the {@code MediumSkillService.mayUse} role and round gates).
     * Remaining time includes the 180 s round-start cooldown; the nominal is the untiered 60 s cooldown after a paid
     * 通灵 (the death tier only shortens the cooldown a real use starts). The cooldown starts at the paid submit, so a
     * running vote window is unaffected by a forced write. The setter is exact (floored at 0) and syncs the owner.
     * 招魂 has no cooldown and no store. The skill has no item, so SparkFactionAPI's admin {@code clearCooldown} (held
     * item only) does not reach it.
     * 通灵师通灵冷却（PR #209），以倒计时刻数保存在只同步给本人的 {@code sparkwitch:medium} 组件中。ACTIVE 回合且为真实职业
     * （与 MediumSkillService.mayUse 的职业与回合门控一致）。剩余时间包含 180 秒开局冷却；标准冷却为付费通灵后未按档位缩短的
     * 60 秒（死亡档位只缩短真实使用进入的冷却）。冷却在付费提交时开始，因此强制写入不影响进行中的投票窗口。setter 为精确写入
     * （下限为 0）并同步给本人。招魂没有冷却，也没有存储。该技能没有物品，因此 SparkFactionAPI 的管理员
     * {@code clearCooldown}（只清手持物品）触及不到它。
     */
    static SimpleRoleSkillCooldownStore mediumSeance() {
        return SimpleRoleSkillCooldownStore.exact(
                MEDIUM_SEANCE_ID,
                whileActive((game, player) -> MediumRules.isMedium(game.getRole(player))),
                player -> MediumPlayerComponent.KEY.get(player).getSeanceCooldownTicks(),
                () -> MediumRules.CD_AFTER_USE_TICKS,
                (player, ticks) -> MediumPlayerComponent.KEY.get(player).startSeanceCooldown(ticks)
        );
    }

    /**
     * The Orthopedist's Bone Setting cooldown, in any game status; its public setter is exact (floored at 0) and syncs
     * to the owner. Same self gate as {@code OrthopedistSkillService.use}, so a disguised Black Raven acting as
     * Orthopedist counts.
     * 骨科大夫正骨冷却，不限游戏状态；其公开 setter 为精确写入（下限为 0）并同步给本人。与 OrthopedistSkillService.use
     * 相同的自身门控，扮演骨科大夫的黑羽鸦同样计入。
     */
    static SimpleRoleSkillCooldownStore orthopedist() {
        return SimpleRoleSkillCooldownStore.exact(
                ORTHOPEDIST_ID,
                // Widened by the Black Raven acting overlay; getRole stays raw. / 黑羽鸦扮演覆盖层会放宽此判定；getRole 仍为真实身份。
                anyGameStatus(player -> GameWorldComponent.KEY.get(player.getWorld())
                        .isRole(player, SparkWitchRoles.orthopedist())),
                player -> OrthopedistPlayerComponent.KEY.get(player).getCooldownTicks(),
                () -> OrthopedistRules.POST_USE_COOLDOWN_TICKS,
                (player, ticks) -> OrthopedistPlayerComponent.KEY.get(player).setCooldownTicks(ticks)
        );
    }

    /**
     * The promoted Saboteur's Sabotage cooldown, in any game status, with the same gate as
     * {@code SaboteurAbilityService}; its public setter is exact and syncs to the owner.
     * 晋升破坏者的破坏技能冷却，不限游戏状态，门控与 SaboteurAbilityService 相同；其公开 setter 为精确写入并同步给本人。
     */
    static SimpleRoleSkillCooldownStore saboteur() {
        return SimpleRoleSkillCooldownStore.exact(
                SABOTEUR_ID,
                anyGameStatus(SaboteurRules::isActivePromotedSaboteur),
                player -> SaboteurPlayerComponent.KEY.get(player).getCooldownTicks(),
                () -> SaboteurRules.COOLDOWN_TICKS,
                (player, ticks) -> SaboteurPlayerComponent.KEY.get(player).setCooldownTicks(ticks)
        );
    }

    /**
     * NoellesRoles Assassin cooldown (pinned 1.7.6: {@code AssassinPlayerComponent.getCooldownTicks}, and
     * {@code setCooldown}, which writes exactly and syncs, so the store floors at 0), in any game status, for the raw
     * role or a disguised Black Raven's acting role ({@code ForcedCooldownRoles}).
     * NoellesRoles 刺客冷却（锁定 1.7.6：getCooldownTicks 与精确写入并同步的 setCooldown，因此由存储取下限 0），不限游戏状态，
     * 适用于真实职业或伪装中黑羽鸦的扮演职业（ForcedCooldownRoles）。
     */
    static SimpleRoleSkillCooldownStore noellesAssassin() {
        return SimpleRoleSkillCooldownStore.clampedAtZero(
                NOELLES_ASSASSIN_ID,
                anyGameStatus(player -> ForcedCooldownRoles.selfRoleIn(player, NOELLES_ASSASSIN_ROLES) != null),
                player -> AssassinPlayerComponent.KEY.get(player).getCooldownTicks(),
                () -> AssassinPlayerComponent.COOLDOWN_TICKS,
                (player, ticks) -> AssassinPlayerComponent.KEY.get(player).setCooldown(ticks)
        );
    }

    /**
     * The Blind's Attune cooldown (owner decision 2026-10-03), kept as an absolute ready tick in the owner-synced
     * {@code sparkwitch:blind} component. Same gate as {@code BlindAttuneService}: an ACTIVE round, the real role (the
     * Blind is never a disguise target) and a granted kit. Remaining time includes a running 10 s window, as the HUD
     * shows; the nominal is the 45 s cooldown after it. Every write only moves the ready tick later through
     * {@code setAttune}, which syncs the owner, so the window is never touched and nothing is shortened.
     * 盲人凝神冷却（所有者 2026-10-03 决定），以绝对就绪刻保存在仅同步本人的 {@code sparkwitch:blind} 组件中。门控与
     * BlindAttuneService 相同：ACTIVE 回合、真实职业（盲人不能被伪装）且道具已发放。剩余时间包含进行中的 10 秒窗口（与 HUD
     * 显示一致）；标准冷却为窗口之后的 45 秒。每次写入都只经 {@code setAttune} 把就绪刻推后（并同步本人），因此不改动窗口，
     * 也绝不缩短。
     */
    static SimpleRoleSkillCooldownStore blindAttune() {
        return SimpleRoleSkillCooldownStore.raisedReadyTick(
                BLIND_ATTUNE_ID,
                whileActive((game, player) ->
                        BlindParticipants.isActiveBlind(player) && BlindLoadoutService.isGranted(player)),
                player -> BlindComponent.KEY.get(player).attuneCooldownRemaining(player.getServerWorld().getTime()),
                () -> BlindRules.ATTUNE_COOLDOWN_TICKS,
                player -> BlindComponent.KEY.get(player),
                BlindComponent::attuneReadyTick,
                (state, ready) -> state.setAttune(state.attuneActiveUntilTick(), ready)
        );
    }

    /**
     * The Fiend's Dash cooldown (owner decision 2026-10-04), kept as an absolute ready tick in the
     * {@code sparkwitch:fiend_moment} world component, whose sync sends it only to the moment Fiend. It exists only while
     * the moment runs and dies with it. {@code FiendDashService}'s role and round gates: an ACTIVE round and the
     * role-gated moment Fiend. The nominal is the 30 s cooldown after each use. Every write only moves the ready tick
     * later through {@code setDashReadyTick}, which resyncs, so nothing is shortened and a running Speed IV is never
     * touched.
     * 魔人疾驰冷却（所有者 2026-10-04 决定），以绝对就绪刻保存在 {@code sparkwitch:fiend_moment} 世界组件中，其同步只发给
     * 时刻中的魔人。它只在时刻进行中存在，并随时刻一同结束。与 FiendDashService 的职业与回合门控一致：ACTIVE 回合且按职业
     * 判断的时刻中魔人。标准冷却为每次使用后的 30 秒。每次写入都只经 {@code setDashReadyTick} 把就绪刻推后（并重新同步），
     * 因此绝不缩短，也不改动进行中的速度 IV。
     */
    static SimpleRoleSkillCooldownStore fiendDash() {
        return SimpleRoleSkillCooldownStore.raisedReadyTick(
                FIEND_DASH_ID,
                whileActive((game, player) -> FiendParticipation.isMomentFiend(player)),
                player -> FiendMomentWorldComponent.get(player.getServerWorld()).dashCooldownTicks(),
                () -> FiendRules.DASH_COOLDOWN_TICKS,
                player -> FiendMomentWorldComponent.get(player.getServerWorld()),
                FiendMomentWorldComponent::dashReadyTick,
                FiendMomentWorldComponent::setDashReadyTick
        );
    }

    /**
     * The Athlete's dash cooldown, kept as an absolute ready tick in the owner-synced {@code sparkwitch:athlete} player
     * component. Same role and round gates as {@code AthleteDashService}: an ACTIVE round and the real Athlete. The
     * nominal is the 15 s cooldown after each dash; writes only move the ready tick later.
     * 运动员冲刺冷却，以绝对就绪刻保存在只同步给本人的 {@code sparkwitch:athlete} 玩家组件中。与 AthleteDashService 的
     * 职业与回合门控一致：ACTIVE 回合且为真实运动员。标准冷却为每次冲刺后的 15 秒；写入只会把就绪刻推后。
     */
    static SimpleRoleSkillCooldownStore athleteDash() {
        return SimpleRoleSkillCooldownStore.raisedReadyTick(
                ATHLETE_DASH_ID,
                whileActive((game, player) -> AthleteRules.isAthlete(game.getRole(player))),
                player -> AthleteComponent.KEY.get(player).dashCooldownRemaining(player.getServerWorld().getTime()),
                () -> AthleteRules.COOLDOWN_TICKS,
                player -> AthleteComponent.KEY.get(player),
                AthleteComponent::dashReadyTick,
                AthleteComponent::setDashReadyTick
        );
    }

    /**
     * The graduated Apprentice's Purify cooldown (owner decision 2026-10-06 D3), kept in the owner-synced
     * {@code sparkwitch:apprentice_player} component and separate from her shared witch skill cooldown. An ACTIVE
     * round, {@code PurifyAbility}'s raw role gate, and graduation: Purify exists only once she has graduated. The
     * nominal is the 20 s cooldown after each use. Every write goes through {@code raisePurifyCooldown}, which only
     * lengthens and resyncs.
     * 出师后预备魔女的净化冷却（所有者 2026-10-06 决定 D3），保存在只同步给本人的 {@code sparkwitch:apprentice_player} 组件中，
     * 与她共享的魔女技能冷却分开。ACTIVE 回合、与 PurifyAbility 一致的真实职业门控，并要求已出师：净化只在出师后存在。
     * 标准冷却为每次使用后的 20 秒。每次写入都经 {@code raisePurifyCooldown}，只会延长并重新同步。
     */
    static SimpleRoleSkillCooldownStore apprenticePurify() {
        return SimpleRoleSkillCooldownStore.clampedAtZero(
                APPRENTICE_PURIFY_ID,
                whileActive((game, player) -> game.getRole(player) == SparkWitchRoles.apprenticeWitch()
                        && ApprenticePlayerComponent.KEY.get(player).isGraduated()),
                player -> ApprenticePlayerComponent.KEY.get(player).getPurifyCooldownTicks(),
                () -> PurifyAbility.COOLDOWN_TICKS,
                (player, ticks) -> ApprenticePlayerComponent.KEY.get(player).raisePurifyCooldown(ticks)
        );
    }

    /**
     * The Magician's ability cooldown, kept as a counting-down tick total in the owner-synced {@code sparkwitch:magician}
     * component. An ACTIVE round and {@code MagicianAbility}'s raw role gate (by id, like the ability handler). It gates
     * starting a recording and starting a playback; a recording or playback already running is never interrupted. The
     * nominal is the 15 s playback cooldown after each use. Every write goes through {@code raiseCooldownTicks}, which
     * only lengthens and resyncs, and the playback cooldown never shortens a raised value.
     * 魔术师技能冷却，以倒计时刻数保存在只同步给本人的 {@code sparkwitch:magician} 组件中。ACTIVE 回合，且与 MagicianAbility
     * 一致按 id 判断真实职业。它阻止开始录制与开始播放；进行中的录制或播放不会被打断。标准冷却为每次使用后的 15 秒播放冷却。
     * 每次写入都经 {@code raiseCooldownTicks}，只会延长并重新同步，播放冷却也绝不缩短已抬高的值。
     */
    static SimpleRoleSkillCooldownStore magician() {
        return SimpleRoleSkillCooldownStore.clampedAtZero(
                MAGICIAN_ID,
                whileActive((game, player) -> {
                    Role role = game.getRole(player);
                    return role != null && SparkWitchRoles.MAGICIAN_ID.equals(role.identifier());
                }),
                player -> MagicianPlayerComponent.KEY.get(player).cooldownTicks(),
                () -> MagicianConstants.PLAYBACK_COOLDOWN_TICKS,
                (player, ticks) -> MagicianPlayerComponent.KEY.get(player).raiseCooldownTicks(ticks)
        );
    }

    /**
     * The Witch Maiden's Mini Wither toggle cooldown (owner 2026-10-20), kept in the owner-synced
     * {@code sparkwitch:mini_wither} component and separate from Focused Footsteps. An ACTIVE round, the exact raw
     * role, and the unlock: the toggle exists only after two tasks. The nominal is the 10 s cooldown after each summon
     * or dismiss. The setter is exact (floored at 0). The crimson skull is an item cooldown and goes through the item
     * nominal table instead.
     * 巫女小凋灵的切换冷却（所有者 2026-10-20），保存在只同步给本人的 {@code sparkwitch:mini_wither} 组件中，与聚焦步伐分开。
     * ACTIVE 回合、精确的真实职业并要求已解锁：切换只在完成两个任务后存在。标准冷却为每次召唤或收回后的 10 秒。setter 为
     * 精确写入（下限为 0）。赤首属于物品冷却，改由物品标准冷却表处理。
     */
    static SimpleRoleSkillCooldownStore miniWitherToggle() {
        return SimpleRoleSkillCooldownStore.exact(
                MINI_WITHER_TOGGLE_ID,
                whileActive((game, player) -> WitchMaidenRules.isWitchMaiden(game.getRole(player))
                        && MiniWitherPlayerComponent.KEY.get(player).isUnlocked()),
                player -> MiniWitherPlayerComponent.KEY.get(player).getToggleCooldownTicks(),
                () -> MiniWitherRules.TOGGLE_COOLDOWN_TICKS,
                (player, ticks) -> MiniWitherPlayerComponent.KEY.get(player).setToggleCooldown(ticks)
        );
    }

    /**
     * The Black Raven's Raven Form cooldown (plan 2026-10-21 §3.6), kept in the owner-synced
     * {@code sparkwitch:black_raven_crow} component. An ACTIVE round, the RAW role (never the disguise overlay) and
     * the two-task unlock. The nominal is the 45 s cooldown that starts when a session ends. The setter is exact
     * (floored at 0). A forced raise during a session keeps running and the session end only ever lengthens it. Raven
     * Form has no item, so SparkFactionAPI's admin {@code clearCooldown} (held-item only) does not reach it, like the
     * Mini Wither toggle.
     * 黑羽鸦化鸦冷却（2026-10-21 计划 §3.6），保存在只同步给本人的 {@code sparkwitch:black_raven_crow} 组件中。ACTIVE 回合、
     * 原始职业（绝不使用伪装覆盖层）并要求已完成两个任务解锁。标准冷却为会话结束后开始的 45 秒。setter 为精确写入（下限为 0）。
     * 会话期间被强制提高的冷却照常倒数，会话结束只会延长它。化鸦没有物品，因此 SparkFactionAPI 的管理员
     * {@code clearCooldown}（只清手持物品）触及不到它，与小凋灵切换相同。
     */
    static SimpleRoleSkillCooldownStore blackRavenCrow() {
        return SimpleRoleSkillCooldownStore.exact(
                BLACK_RAVEN_CROW_ID,
                whileActive((game, player) -> BlackRavenRules.isBlackRaven(game.getRole(player))
                        && BlackRavenCrowPlayerComponent.KEY.get(player).isUnlocked()),
                player -> BlackRavenCrowPlayerComponent.KEY.get(player).getCooldownTicks(),
                () -> BlackRavenCrowRules.COOLDOWN_TICKS,
                (player, ticks) -> BlackRavenCrowPlayerComponent.KEY.get(player).setCooldown(ticks)
        );
    }

    /**
     * Noa's painting cooldown, kept as an absolute ready tick in the owner-synced {@code sparkwitch:noa_player}
     * component. Same role and round gates as {@code NoaPaintService}: an ACTIVE round and the real Noa. The nominal is
     * the 60 s cooldown after each painting; writes only move the ready tick later.
     * 诺亚作画冷却，以绝对就绪刻保存在只同步给本人的 {@code sparkwitch:noa_player} 组件中。与 NoaPaintService 的职业与回合
     * 门控一致：ACTIVE 回合且为真实诺亚。标准冷却为每次作画后的 60 秒；写入只会把就绪刻推后。
     */
    static SimpleRoleSkillCooldownStore noaPaint() {
        return SimpleRoleSkillCooldownStore.raisedReadyTick(
                NOA_PAINT_ID,
                whileActive((game, player) -> NoaRules.isNoa(game.getRole(player))),
                player -> NoaComponent.KEY.get(player).paintCooldownRemaining(player.getServerWorld().getTime()),
                () -> NoaRules.COOLDOWN_TICKS,
                player -> NoaComponent.KEY.get(player),
                NoaComponent::paintReadyTick,
                NoaComponent::setPaintReadyTick
        );
    }

    /**
     * The Ragpicker's Search cooldown (owner decision 2026-11-04), kept as an absolute ready tick in the owner-synced
     * {@code sparkwitch:ragpicker_player} component. {@code JunkerService}'s role and round gates: an ACTIVE round and
     * the real role (the overlay is never read). Remaining time counts to the ready tick, so it includes the 45 s
     * round-start cooldown; the nominal is the 60 s cooldown after each successful search. Every write only moves the
     * ready tick later through {@code setSearchReadyTick}, which syncs the owner, so nothing is shortened and a running
     * search is never interrupted.
     * 拾荒者搜刮冷却（所有者 2026-11-04 决定），以绝对就绪刻保存在只同步给本人的 {@code sparkwitch:ragpicker_player}
     * 组件中。与 JunkerService 的职业与回合门控一致：ACTIVE 回合且为真实职业（从不读取覆盖层）。剩余时间计到就绪刻，
     * 因此包含 45 秒回合初始冷却；标准冷却为每次成功搜刮后的 60 秒。每次写入都只经 {@code setSearchReadyTick} 把就绪刻
     * 推后（并同步本人），因此绝不缩短，也不会打断进行中的搜刮。
     */
    static SimpleRoleSkillCooldownStore ragpickerSearch() {
        return SimpleRoleSkillCooldownStore.raisedReadyTick(
                RAGPICKER_SEARCH_ID,
                whileActive((game, player) -> JunkerRules.isRagpicker(game.getRole(player))),
                player -> JunkerPlayerComponent.KEY.get(player)
                        .searchCooldownRemainingTicks(player.getServerWorld().getTime()),
                () -> JunkerRules.SEARCH_COOLDOWN_TICKS,
                player -> JunkerPlayerComponent.KEY.get(player),
                JunkerPlayerComponent::getSearchReadyTick,
                JunkerPlayerComponent::setSearchReadyTick
        );
    }

    /**
     * The Dominator's G cooldown (PR #192), kept as a counting-down tick total in the owner-synced
     * {@code sparkwitch:dominator} component; it gates both marking and releasing. An ACTIVE round, the raw role (the
     * Dominator is never a Black Raven disguise target, so {@code DominatorSkillService}'s {@code isRole} gate reads
     * the same) and no active mark: the same counter gates the release press, and the mark's end writes the post-mark
     * cooldown, so a running mark is never forced (like the Kidnapper's carried body and a burning Hellfire).
     * {@code SimpleRoleSkillCooldownStore} has no {@code mayForce}, so that clause sits in the gate; SparkFactionAPI
     * leaves out a store for which either is false. Remaining time includes the 45 s round-start cooldown; the nominal
     * is the 10 s cooldown after each mark ends. The setter is exact (floored at 0) and syncs the owner. The skill has
     * no item, so SparkFactionAPI's admin {@code clearCooldown} (held item only) does not reach it, like Raven Form.
     * 支配者 G 键冷却（PR #192），以倒计时刻数保存在只同步给本人的 {@code sparkwitch:dominator} 组件中；它同时拦住标记与
     * 释放。ACTIVE 回合、真实职业（支配者不可能是黑羽鸦的伪装目标，因此 DominatorSkillService 的 isRole 门控读到的相同）
     * 且没有进行中的标记：同一计数也拦着释放键，且标记结束时会写入标记后冷却，因此进行中的标记绝不强制（与绑架者携尸、
     * 业火燃烧相同）。SimpleRoleSkillCooldownStore 没有 mayForce，因此该条件放在门控中；二者任一为假时 SparkFactionAPI
     * 都会略过该存储。剩余时间包含 45 秒回合初始冷却；标准冷却为每次标记结束后的 10 秒。setter 为精确写入（下限为 0）并
     * 同步给本人。该技能没有物品，因此 SparkFactionAPI 的管理员 {@code clearCooldown}（只清手持物品）触及不到它，与化鸦相同。
     */
    static SimpleRoleSkillCooldownStore dominatorSkill() {
        return SimpleRoleSkillCooldownStore.exact(
                DOMINATOR_SKILL_ID,
                whileActive((game, player) -> DominatorRules.isDominator(game.getRole(player))
                        && !DominatorPlayerComponent.KEY.get(player).hasActiveMark()),
                player -> DominatorPlayerComponent.KEY.get(player).getCooldownTicks(),
                () -> DominatorRules.MARK_END_COOLDOWN_TICKS,
                (player, ticks) -> DominatorPlayerComponent.KEY.get(player).setCooldownTicks(ticks)
        );
    }

    /**
     * 夏目安安's hypnosis cooldown (PR #194), kept as an absolute ready tick in the owner-synced
     * {@code sparkwitch:natsume_anan_player} component. {@code NatsumeService}'s role and round gates: an ACTIVE round
     * and the raw role (she is never a Black Raven disguise target). Remaining time counts to the ready tick, so it
     * includes the 60 s opening cooldown; the nominal is the 90 s cooldown after each cast. Every write only moves the
     * ready tick later through {@code setCooldownReadyTick}, which syncs the shown second to the owner. The skill has no
     * item, so SparkFactionAPI's admin {@code clearCooldown} (held item only) does not reach it.
     * 夏目安安催眠冷却（PR #194），以绝对就绪刻保存在只同步给本人的 {@code sparkwitch:natsume_anan_player} 组件中。与
     * NatsumeService 的职业与回合门控一致：ACTIVE 回合且为真实职业（她不可能是黑羽鸦的伪装目标）。剩余时间计到就绪刻，因此包含
     * 60 秒开局冷却；标准冷却为每次施放后的 90 秒。每次写入都只经 {@code setCooldownReadyTick} 把就绪刻推后（并把显示秒数同步给
     * 本人）。该技能没有物品，因此 SparkFactionAPI 的管理员 {@code clearCooldown}（只清手持物品）触及不到它。
     */
    static SimpleRoleSkillCooldownStore natsumeHypnosis() {
        return SimpleRoleSkillCooldownStore.raisedReadyTick(
                NATSUME_HYPNOSIS_ID,
                whileActive((game, player) -> NatsumeRules.isNatsume(game.getRole(player))),
                player -> NatsumeComponent.KEY.get(player).cooldownRemainingTicks(player.getServerWorld().getTime()),
                () -> NatsumeRules.COOLDOWN_TICKS,
                player -> NatsumeComponent.KEY.get(player),
                NatsumeComponent::cooldownReadyTick,
                NatsumeComponent::setCooldownReadyTick
        );
    }

    /**
     * The Specter's skill-one cooldown (PR #207), kept as a counting-down tick total in the owner-synced
     * {@code sparkwitch:specter} component; it gates both marking and the shriek. An ACTIVE round, the raw role (the
     * Specter is never a Black Raven disguise target, as for {@code SpecterRules.mayCast}) and no active mark: the same
     * counter gates the shriek press and an expired mark costs nothing, so a running mark is never forced (Dominator
     * precedent). Remaining time includes the 45 s round-start cooldown; the nominal is the 45 s cooldown after each
     * shriek. The setter is exact (floored at 0) and syncs the owner. The skill has no item, so SparkFactionAPI's admin
     * {@code clearCooldown} (held item only) does not reach it.
     * 幽魂技能一冷却（PR #207），以倒计时刻数保存在只同步给本人的 {@code sparkwitch:specter} 组件中；它同时拦住标记与尖啸。
     * ACTIVE 回合、真实职业（幽魂不可能是黑羽鸦的伪装目标，与 SpecterRules.mayCast 一致）且没有进行中的标记：同一计数也拦着
     * 尖啸键，且标记自然过期不进冷却，因此进行中的标记绝不强制（支配者先例）。剩余时间包含 45 秒回合初始冷却；标准冷却为每次
     * 尖啸后的 45 秒。setter 为精确写入（下限为 0）并同步给本人。该技能没有物品，因此 SparkFactionAPI 的管理员
     * {@code clearCooldown}（只清手持物品）触及不到它。
     */
    static SimpleRoleSkillCooldownStore specterSkill() {
        return SimpleRoleSkillCooldownStore.exact(
                SPECTER_SKILL_ID,
                whileActive((game, player) -> SpecterRules.isSpecter(game.getRole(player))
                        && !SpecterPlayerComponent.KEY.get(player).hasActiveMark()),
                player -> SpecterPlayerComponent.KEY.get(player).getCooldownTicks(),
                () -> SpecterRules.SHRIEK_COOLDOWN_TICKS,
                (player, ticks) -> SpecterPlayerComponent.KEY.get(player).setCooldownTicks(ticks)
        );
    }

    /**
     * The Specter's Otherworld (skill two, PR #207), kept as counting-down ticks in the owner-synced
     * {@code sparkwitch:specter_second_skill} component. An ACTIVE round and the raw role (the Specter is never a Black
     * Raven disguise target). Remaining time includes the 120 s unlock; during the 30 s window it is the rest of the
     * window plus the cooldown that starts when it ends. The nominal is that 120 s post-window cooldown. The setter
     * only ever lengthens and syncs the owner; during a window the forced time is held as the pending post-window
     * cooldown, so a running Otherworld is never cut short and the penalty still lands when it ends (as the witch-skill
     * deferred cooldown). The skill has no item, so SparkFactionAPI's admin {@code clearCooldown} (held item only)
     * does not reach it.
     * 幽魂里世界（技能二，PR #207），以倒计时刻数保存在只同步给本人的 {@code sparkwitch:specter_second_skill} 组件中。ACTIVE
     * 回合且为真实职业（幽魂不可能是黑羽鸦的伪装目标）。剩余时间包含 120 秒解锁；在 30 秒窗口期间为剩余窗口加上窗口结束时开始的
     * 冷却。标准冷却即该 120 秒窗口后冷却。setter 只会延长并同步本人；窗口期间被强制的时间作为挂起的窗口后冷却保存，因此进行中的
     * 里世界绝不会被提前结束，惩罚仍在窗口结束时生效（与魔女技能的延后冷却相同）。该技能没有物品，因此 SparkFactionAPI 的管理员
     * {@code clearCooldown}（只清手持物品）触及不到它。
     */
    static SimpleRoleSkillCooldownStore specterOtherworld() {
        return SimpleRoleSkillCooldownStore.exact(
                SPECTER_OTHERWORLD_ID,
                whileActive((game, player) -> SpecterRules.isSpecter(game.getRole(player))),
                player -> SpecterSecondSkillComponent.KEY.get(player).forcedCooldownRemainingTicks(),
                () -> SpecterRules.SECOND_SKILL_COOLDOWN_TICKS,
                (player, ticks) -> SpecterSecondSkillComponent.KEY.get(player).setForcedCooldownRemainingTicks(ticks)
        );
    }

    /**
     * The Savior's G domain cooldown (PR #200), kept as a counting-down tick total in the owner-synced
     * {@code sparkwitch:savior} component. {@code SaviorDomainService}'s role and round gates: an ACTIVE round and the
     * raw role; and no running domain, because the cast already wrote the post-cast cooldown and a running domain is
     * never forced (the Dominator's running mark precedent). Remaining time includes the 60 s round-start cooldown; the
     * nominal is the 60 s cooldown after each cast. The setter is exact (floored at 0) and syncs the owner. The skill
     * has no item, so SparkFactionAPI's admin {@code clearCooldown} (held item only) does not reach it.
     * 救世主 G 领域冷却（PR #200），以倒计时刻数保存在只同步给本人的 {@code sparkwitch:savior} 组件中。与
     * SaviorDomainService 的职业与回合门控一致：ACTIVE 回合且为真实职业；并且没有进行中的领域，因为施放时已写入施放后冷却，
     * 进行中的领域绝不强制（沿用支配者进行中标记的先例）。剩余时间包含 60 秒开局冷却；标准冷却为每次施放后的 60 秒。setter
     * 为精确写入（下限为 0）并同步给本人。该技能没有物品，因此 SparkFactionAPI 的管理员 {@code clearCooldown}（只清手持物品）
     * 触及不到它。
     */
    static SimpleRoleSkillCooldownStore saviorDomain() {
        return SimpleRoleSkillCooldownStore.exact(
                SAVIOR_DOMAIN_ID,
                whileActive((game, player) -> SaviorRules.isSavior(game.getRole(player))
                        && !SaviorPlayerComponent.KEY.get(player).isDomainActive()),
                player -> SaviorPlayerComponent.KEY.get(player).getCooldownTicks(),
                () -> SaviorRules.DOMAIN_COOLDOWN_TICKS,
                (player, ticks) -> SaviorPlayerComponent.KEY.get(player).setCooldownTicks(ticks)
        );
    }

    /**
     * The Charon's pardon cooldown (PR #202), kept as a counting-down tick total in the owner-synced
     * {@code sparkwitch:ferryman} component; it gates whether a lethal hit opens a pardon window at all. An ACTIVE
     * round, the raw role (as {@code FerrymanParticipation} reads it; a disguise overlay never widens it) and no open
     * window: a press inside the window writes the post-pardon cooldown, so a running window is never forced
     * (Dominator / Specter precedent). Remaining time includes the 60 s round-start cooldown; the nominal is the 10 s
     * cooldown after each pardon. The setter is exact (floored at 0) and syncs the owner. The skill has no item, so
     * SparkFactionAPI's admin {@code clearCooldown} (held item only) does not reach it; forced raises and extends
     * on role skills (the Abyss Listener's suppression, the Potion Gunner's AC shell) do.
     * 卡戎宽恕冷却（PR #202），以倒计时刻数保存在只同步给本人的 {@code sparkwitch:ferryman} 组件中；它决定致命一击能否开启
     * 宽恕窗口。ACTIVE 回合、真实职业（与 FerrymanParticipation 的读取方式一致，伪装覆盖层绝不放宽）且没有开启的窗口：窗口内
     * 按键会写入宽恕后冷却，因此进行中的窗口绝不强制（支配者、幽魂先例）。剩余时间包含 60 秒开局冷却；标准冷却为每次宽恕后的
     * 10 秒。setter 为精确写入（下限为 0）并同步给本人。该技能没有物品，因此 SparkFactionAPI 的管理员 {@code clearCooldown}
     * （只清手持物品）触及不到它；作用于职业技能的强制抬高与延长（聆渊者压制、药炮手 AC 弹）可以。
     */
    static SimpleRoleSkillCooldownStore ferrymanPardon() {
        return SimpleRoleSkillCooldownStore.exact(
                FERRYMAN_PARDON_ID,
                whileActive((game, player) -> FerrymanRules.isFerryman(game.getRole(player))
                        && !FerrymanPlayerComponent.KEY.get(player).isPardoning()),
                player -> FerrymanPlayerComponent.KEY.get(player).cooldownTicks(),
                () -> FerrymanRules.PARDON_COOLDOWN_TICKS,
                (player, ticks) -> FerrymanPlayerComponent.KEY.get(player).setCooldown(ticks)
        );
    }

    /**
     * The Charon's ferry cooldown (Role Skill 2, PR #202), kept as a counting-down tick total in the owner-synced
     * {@code sparkwitch:ferryman} component. An ACTIVE round and the raw role. It starts at 0 each round; the nominal
     * is the 5 s cooldown after each successful ferry. The setter is exact (floored at 0) and syncs the owner. The skill
     * has no item, so SparkFactionAPI's admin {@code clearCooldown} (held item only) does not reach it.
     * 卡戎摆渡冷却（职业技能 2，PR #202），以倒计时刻数保存在只同步给本人的 {@code sparkwitch:ferryman} 组件中。ACTIVE 回合且为
     * 真实职业。每局从 0 开始；标准冷却为每次成功摆渡后的 5 秒。setter 为精确写入（下限为 0）并同步给本人。该技能没有物品，因此
     * SparkFactionAPI 的管理员 {@code clearCooldown}（只清手持物品）触及不到它。
     */
    static SimpleRoleSkillCooldownStore ferrymanFerry() {
        return SimpleRoleSkillCooldownStore.exact(
                FERRYMAN_FERRY_ID,
                whileActive((game, player) -> FerrymanRules.isFerryman(game.getRole(player))),
                player -> FerrymanPlayerComponent.KEY.get(player).ferryCooldownTicks(),
                () -> FerrymanRules.FERRY_COOLDOWN_TICKS,
                (player, ticks) -> FerrymanPlayerComponent.KEY.get(player).setFerryCooldown(ticks)
        );
    }

    /**
     * Tono Hanna's float cooldown (PR #212), kept as an absolute ready tick in the {@code sparkwitch:hanna_player}
     * component (the ready tick syncs to its owner only). Same role and round gates as {@code HannaFloatService}: an
     * ACTIVE round and the real Hanna. Remaining time counts to the ready tick, so it includes the 40 s round-start
     * cooldown and, while she floats, the rest of the float (the cooldown starts when the float ends). The nominal is
     * the 45 s cooldown after a float; writes only move the ready tick later through {@code setFloatReadyTick}, so a
     * running float is never cut short. The skill has no item, so SparkFactionAPI's admin {@code clearCooldown} (held
     * item only) does not reach it.
     * 远野汉娜浮游冷却（PR #212），以绝对就绪刻保存在 {@code sparkwitch:hanna_player} 组件中（就绪刻只同步给本人）。与
     * HannaFloatService 的职业与回合门控一致：ACTIVE 回合且为真实汉娜。剩余时间计到就绪刻，因此包含 40 秒开局冷却，以及
     * 浮游期间尚未走完的浮游时间（冷却从浮游结束起算）。标准冷却为浮游结束后的 45 秒；写入只经 {@code setFloatReadyTick}
     * 把就绪刻推后，因此绝不打断进行中的浮游。该技能没有物品，因此 SparkFactionAPI 的管理员 {@code clearCooldown}
     * （只清手持物品）触及不到它。
     */
    static SimpleRoleSkillCooldownStore hannaFloat() {
        return SimpleRoleSkillCooldownStore.raisedReadyTick(
                HANNA_FLOAT_ID,
                whileActive((game, player) -> HannaRules.isHanna(game.getRole(player))),
                player -> HannaComponent.KEY.get(player).floatCooldownRemaining(player.getServerWorld().getTime()),
                () -> HannaRules.COOLDOWN_TICKS,
                player -> HannaComponent.KEY.get(player),
                HannaComponent::floatReadyTick,
                HannaComponent::setFloatReadyTick
        );
    }

    /**
     * 橘雪莉's 推演 cooldown (PR #206), kept as counting-down ticks in the owner-synced {@code sparkwitch:sherry}
     * component. An ACTIVE round and the raw role (the {@code SherryDeductionService.mayUse} role and round gates). The
     * remaining time includes the 45 s opening cooldown; the nominal is the 30 s cooldown after a paid deduction. The
     * setter is exact (floored at 0) and syncs the owner. The skill has no item, so SparkFactionAPI's admin
     * {@code clearCooldown} (held item only) does not reach it.
     * 橘雪莉推演冷却（PR #206），以倒计时刻数保存在只同步给本人的 {@code sparkwitch:sherry} 组件中。ACTIVE 回合且为真实职业
     * （与 SherryDeductionService.mayUse 的职业与回合门控一致）。剩余时间包含 45 秒开局冷却；标准冷却为付费推演后的 30 秒。
     * setter 为精确写入（下限为 0）并同步给本人。该技能没有物品，因此 SparkFactionAPI 的管理员 {@code clearCooldown}
     * （只清手持物品）触及不到它。
     */
    static SimpleRoleSkillCooldownStore sherryDeduction() {
        return SimpleRoleSkillCooldownStore.exact(
                SHERRY_DEDUCTION_ID,
                whileActive((game, player) -> SherryRules.isSherry(game.getRole(player))),
                player -> SherryComponent.KEY.get(player).getDeductionCooldownTicks(),
                () -> SherryRules.DEDUCTION_COOLDOWN_TICKS,
                (player, ticks) -> SherryComponent.KEY.get(player).startDeductionCooldown(ticks)
        );
    }

    /**
     * 橘雪莉's own 巨力魔法 cooldown (Role Skill 2, PR #206), in the same component. It only counts down once her
     * window has ended, so a raise during a live window lands when the window closes (the component keeps the larger
     * value). The nominal is the Apprentice's 45 s Mighty Force cooldown. Exact setter, owner sync, no item.
     * 橘雪莉自己的巨力魔法冷却（职业技能 2，PR #206），保存在同一组件中。只有窗口结束后才开始倒数，因此窗口期间被抬高的冷却
     * 在窗口结束时生效（组件保留较大值）。标准冷却为预备魔女巨力的 45 秒。精确写入、同步给本人、没有物品。
     */
    static SimpleRoleSkillCooldownStore sherryMightyForce() {
        return SimpleRoleSkillCooldownStore.exact(
                SHERRY_MIGHTY_FORCE_ID,
                whileActive((game, player) -> SherryRules.isSherry(game.getRole(player))),
                player -> SherryComponent.KEY.get(player).getMightyCooldownTicks(),
                () -> dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.MightyForce.MightyForceAbility
                        .COOLDOWN_TICKS,
                (player, ticks) -> SherryComponent.KEY.get(player).setMightyCooldownTicks(ticks)
        );
    }

    /**
     * The Seeker car is exempt: {@code SeekerCooldowns} is its frozen sole "max + exact" writer, driven only by the
     * Seeker state machine, and offers no write path to other features.
     * 搜寻者小车豁免：SeekerCooldowns 是其冻结的唯一“取最大 + 精确”写入方，只由搜寻者状态机驱动，不向其他功能开放写入。
     * The Knight's cross sword is exempt too: it has no cooldown at all, so forced raises must not lock it.
     * 骑士十字剑同样豁免：它完全没有冷却，强制抬高不得将其锁住。
     */
    static boolean isExemptItem(Identifier itemId) {
        // The Knight's cross sword never cools down (spec 无cd). / 骑士十字剑永不冷却（规格“无cd”）。
        return SparkWitchItems.SEEKER_CAR_ID.equals(itemId) || SparkWitchItems.CROSS_SWORD_ID.equals(itemId);
    }
}
