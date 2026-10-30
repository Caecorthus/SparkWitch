package dev.caecorthus.sparkwitch.compat.cooldown;

import dev.caecorthus.sparkfactionapi.api.cooldown.ForcedCooldowns;
import dev.caecorthus.sparkfactionapi.api.cooldown.RoleSkillCooldownStore;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.compat.NoellesRoleIds;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.ApprenticePlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.abilities.Purify.PurifyAbility;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindComponent;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindParticipants;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindRules;
import dev.caecorthus.sparkwitch.roles.civilian.blind.kit.BlindLoadoutService;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.orthopedist.OrthopedistRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowRules;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianConstants;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurRules;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.WitchMaidenRules;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherRules;
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
    static final Identifier APPRENTICE_PURIFY_ID = SparkWitch.id("apprentice_purify");
    static final Identifier MAGICIAN_ID = SparkWitch.id("magician");
    static final Identifier MINI_WITHER_TOGGLE_ID = SparkWitch.id("mini_wither_toggle");
    static final Identifier BLACK_RAVEN_CROW_ID = BlackRavenCrowRules.SKILL_ID;

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
                blackRavenCrow()
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
