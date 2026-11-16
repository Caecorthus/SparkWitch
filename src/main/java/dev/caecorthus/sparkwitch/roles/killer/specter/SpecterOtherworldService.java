package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkwitch.item.tofana.TofanaRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowEntity;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowSessions;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorForcedShotScope;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanPardonService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.index.tag.WatheItemTags;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Otherworld debuff: while the world Otherworld window is open, every living non-killer-faction player gets Slowness I.
 * The effect is refreshed every half second with the remaining window length plus a small margin, so it expires on its
 * own just after the window closes; a longer enemy-applied Slowness instance is never overwritten or removed.
 * The window is world-level, so it keeps running after the Specter dies.
 * 里世界减益：世界里世界窗口开启期间，所有存活的非杀手阵营玩家获得缓慢 I。每半秒按剩余窗口时长加少量余量刷新，
 * 窗口结束后自然过期；其他来源更长/更强的缓慢不会被覆盖或移除。窗口是世界级的，幽魂死亡后仍继续。
 */
public final class SpecterOtherworldService {
    /** Slowness refresh interval; half a second. / 缓慢刷新间隔：半秒。 */
    private static final int REFRESH_INTERVAL_TICKS = 10;
    /** Extra ticks so the effect survives between sweeps and expires just after the window. / 余量：撑过两次刷新并略晚于窗口结束。 */
    private static final int SLOWNESS_MARGIN_TICKS = 5;
    /** Wathe's server cap for a gun target ({@code GunShootPayload$Receiver}). / Wathe 服务端枪械目标距离上限。 */
    private static final double GUN_TARGET_RANGE = 65.0;
    /**
     * NoellesRoles' Bodyguard substitution (external id, kept in step with {@code Noellesroles}): the Bodyguard dies in
     * place of the player the killer attacked, credited to that killer.
     * NoellesRoles 保镖替死（外部 id，与 {@code Noellesroles} 保持一致）：保镖代替被攻击者死亡，击杀记在该凶手名下。
     */
    static final Identifier BODYGUARD_SACRIFICE_REASON = Identifier.of("noellesroles", "bodyguard_sacrifice");
    /**
     * Deaths credited to a player who did not attack that victim, so the targeting rule has nothing to judge: the
     * environmental reasons (a push off the train keeps its attacker and is retried every tick until it lands), the
     * Bodyguard substitution (voiding it would make the protected, valid target unkillable) and the Tofana retaliation
     * on whoever attacked the holder.
     * 记在某玩家名下、但该玩家并未攻击这名受害者的死亡，索敌规则无从判定：环境死因（推下列车保留攻击者，且在落地前每刻重试）、
     * 保镖替死（作废它会让被保护的有效目标无法被杀）以及托法娜对攻击持有者之人的反噬。
     */
    private static final Set<Identifier> NON_ATTACK_REASONS = Set.of(
            GameConstants.DeathReasons.FELL_OUT_OF_TRAIN,
            GameConstants.DeathReasons.ESCAPED,
            GameConstants.DeathReasons.DROWNED,
            GameConstants.DeathReasons.MENTAL_BREAKDOWN,
            BODYGUARD_SACRIFICE_REASON,
            TofanaRules.DEATH_REASON_ID
    );
    private static boolean registered;

    private SpecterOtherworldService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(SpecterOtherworldService::tick);
    }

    /** Immediate pass when the window opens; the periodic sweep handles the rest. / 窗口开启时立即刷一次，其余靠周期检查。 */
    public static void refresh(ServerWorld world) {
        applySlowness(world, SpecterOtherworldComponent.KEY.get(world).getActiveTicks());
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % REFRESH_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerWorld world : server.getWorlds()) {
            applySlowness(world, SpecterOtherworldComponent.KEY.get(world).getActiveTicks());
        }
    }

    /**
     * Otherworld targeting rule (stable contract, read by {@code GameFunctionsSpecterOtherworldTargetMixin}, the two
     * receiver seams below and the Raven Form kill link): while the world Otherworld window is open, a non-forced kill
     * credited to a Specter only lands on a player who is not effective-killer faction
     * ({@link SpecterRules#isEffectiveKiller}) and is either cowardly or at effective real sanity 25 or below
     * ({@link SpecterRules#isLowSanity}), the same players the Specter sees framed. Any other victim takes no damage
     * and spends no shield or protection, whatever weapon delivered the blow (knife, gun, bat, grenade, poison, a
     * thrown item from another mod). The rule is judged when the kill resolves, so a grenade or poison follows the
     * window state at that moment, not at the throw. Not part of the rule: a forced kill; a self-kill; the
     * {@link #NON_ATTACK_REASONS}; a shot the Dominator forces out of a Specter (the Dominator's attack, not the
     * Specter's); the replay of a kill a Charon's pardon held (it already passed this rule once); and spectating,
     * creative or role-less victims.
     * 里世界索敌规则（稳定契约，由 {@code GameFunctionsSpecterOtherworldTargetMixin}、下方两个接收器接缝与化鸦击杀链接读取）：
     * 世界里世界窗口开启时，记在幽魂名下的非强制击杀只能落在不属于有效杀手阵营（{@link SpecterRules#isEffectiveKiller}），
     * 且处于怯懦或有效真实理智不高于 25（{@link SpecterRules#isLowSanity}）的玩家身上，也就是幽魂看到有边框的那批玩家。
     * 其他受害者不受伤，也不消耗任何护盾或保护，无论致命一击来自小刀、枪、球棒、手雷、毒，还是其他模组投出的武器。规则在
     * 击杀结算时判定，因此手雷与毒以结算那一刻的窗口状态为准，而不是投出时。不参与该规则的有：强制击杀；自杀；
     * {@link #NON_ATTACK_REASONS}；支配者强迫幽魂开出的那一枪（那是支配者的攻击，不是幽魂的）；摆渡人宽恕扣住的击杀的
     * 重放（它已经通过过一次本规则）；以及旁观、创造与没有职业的受害者。
     */
    public static boolean blocksKill(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer,
                                     @Nullable Identifier deathReason, boolean force) {
        if (force
                || killer == null
                || killer == victim
                || isNonAttackReason(deathReason)
                || DominatorForcedShotScope.isFiring(killer)
                || FerrymanPardonService.isReplayingHeldKill()
                || !SpecterOtherworldComponent.KEY.get(killer.getServerWorld()).isActive()) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(killer.getServerWorld());
        if (!SpecterRules.isSpecter(game.getRole(killer))) {
            return false;
        }
        // Spectators/creative players and role-less test targets are not part of the targeting rule.
        // 旁观/创造模式与没有职业的测试目标不参与索敌限制。
        if (victim.isSpectator()
                || GameFunctions.isPlayerSpectatingOrCreative(victim)
                || game.getRole(victim) == null) {
            return false;
        }
        return !isOtherworldTarget(victim);
    }

    /**
     * Server knife receiver seam ({@code SpecterOtherworldKnifeMixin}, HEAD 900 ahead of every knife guard): true only
     * when a living Specter holding the Wathe knife stabs, inside an open Otherworld, a living non-spectator player the
     * targeting rule rejects (or that player's Raven Form crow). Such a stab is dropped with no side effects (no
     * record, sound, Veteran use or cooldown); every other stab, including every other non-player target, goes through
     * Wathe's receiver and all its guards.
     * 服务端刀刺接收器接缝（{@code SpecterOtherworldKnifeMixin}，HEAD 900，先于所有刀守卫）：仅当里世界开启时，手持 Wathe
     * 小刀的存活幽魂刺向一名被索敌规则拒绝的存活非旁观玩家（或该玩家化鸦形态的乌鸦）时为 true。这样的刺击被无副作用地丢弃
     * （不记录、无音效、不消耗老兵次数、不写冷却）；其他所有刺击（包括其他所有非玩家目标）照常走 Wathe 接收器及其全部守卫。
     */
    public static boolean rejectsKnifeStab(@Nullable ServerPlayerEntity attacker, int targetId) {
        if (!isOtherworldAttacker(attacker)
                || !(attacker.getMainHandStack().isOf(WatheItems.KNIFE)
                || attacker.getOffHandStack().isOf(WatheItems.KNIFE))) {
            return false;
        }
        ServerPlayerEntity victim = livingVictim(attacker, targetId);
        return victim != null && blocksKill(victim, attacker, GameConstants.DeathReasons.KNIFE, false);
    }

    /**
     * Server gun receiver seam ({@code SpecterOtherworldGunMixin}, HEAD 900 ahead of every gun guard): true only when a
     * living Specter with a Wathe gun in the main hand shoots, inside an open Otherworld, a living non-spectator player
     * within Wathe's gun range that the targeting rule rejects (or that player's Raven Form crow). Such a shot is
     * dropped with no side effects: without this, Wathe would still run the shot-an-innocent branch and take the
     * revolver, spend a derringer, record the use and write the cooldown for a kill the rule then voids. Every other
     * shot, a miss included, goes through Wathe's receiver and all its guards. Thrown and delayed weapons have no such
     * seam: they are spent as usual and only their kill is judged.
     * 服务端枪械接收器接缝（{@code SpecterOtherworldGunMixin}，HEAD 900，先于所有枪械守卫）：仅当里世界开启时，主手持 Wathe
     * 枪械的存活幽魂射击一名位于 Wathe 枪械射程内、被索敌规则拒绝的存活非旁观玩家（或该玩家化鸦形态的乌鸦）时为 true。
     * 这样的射击被无副作用地丢弃：否则 Wathe 仍会执行误杀无辜分支并收走左轮、消耗德林加、记录使用并写入冷却，而这次击杀
     * 随后会被规则作废。其他所有射击（包括打空）照常走 Wathe 接收器及其全部守卫。投掷类与延迟类武器没有这样的接缝：
     * 它们照常消耗，只有击杀本身受规则判定。
     */
    public static boolean rejectsGunShot(@Nullable ServerPlayerEntity shooter, int targetId) {
        if (!isOtherworldAttacker(shooter) || !shooter.getMainHandStack().isIn(WatheItemTags.GUNS)) {
            return false;
        }
        Entity aimed = shooter.getServerWorld().getEntityById(targetId);
        ServerPlayerEntity victim = livingVictim(shooter, targetId);
        return victim != null
                && aimed != null
                && aimed.distanceTo(shooter) < GUN_TARGET_RANGE
                && blocksKill(victim, shooter, GameConstants.DeathReasons.GUN, false);
    }

    /** A living, non-spectating player inside an open Otherworld. / 里世界开启时存活且非旁观的玩家。 */
    private static boolean isOtherworldAttacker(@Nullable ServerPlayerEntity attacker) {
        return attacker != null
                && SpecterOtherworldComponent.KEY.get(attacker.getServerWorld()).isActive()
                && !attacker.isSpectator()
                && GameFunctions.isPlayerPlayingAndAlive(attacker);
    }

    /**
     * The living, non-spectating player a receiver would hit for {@code targetId}: that player, or the owner of a
     * Raven Form crow (the crow receivers hand the weapon its owner). Null for every other entity.
     * 接收器会以 {@code targetId} 命中的存活非旁观玩家：该玩家本人，或化鸦乌鸦的主人（乌鸦接收器会把主人交给武器）。
     * 其他实体返回 null。
     */
    private static @Nullable ServerPlayerEntity livingVictim(ServerPlayerEntity attacker, int targetId) {
        Entity entity = attacker.getServerWorld().getEntityById(targetId);
        ServerPlayerEntity victim = entity instanceof BlackRavenCrowEntity crow
                ? BlackRavenCrowSessions.ownerOf(crow)
                : entity instanceof ServerPlayerEntity player ? player : null;
        return victim == null || victim.isSpectator() || !GameFunctions.isPlayerPlayingAndAlive(victim)
                ? null
                : victim;
    }

    /** See {@link #NON_ATTACK_REASONS}. / 见 {@link #NON_ATTACK_REASONS}。 */
    static boolean isNonAttackReason(@Nullable Identifier deathReason) {
        return deathReason != null && NON_ATTACK_REASONS.contains(deathReason);
    }

    /** Not effective killer, and cowardly or at low effective real sanity. / 非有效杀手，且怯懦或有效真实理智偏低。 */
    private static boolean isOtherworldTarget(ServerPlayerEntity victim) {
        if (SpecterRules.isEffectiveKiller(victim)) {
            return false;
        }
        return SpecterCowardiceComponent.KEY.get(victim).isCoward() || SpecterRules.isLowSanity(victim);
    }

    private static void applySlowness(ServerWorld world, int remainingTicks) {
        if (remainingTicks <= 0) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!isNonKillerParticipant(player, game)) {
                continue;
            }
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOWNESS,
                    remainingTicks + SLOWNESS_MARGIN_TICKS,
                    0,
                    true,
                    false,
                    true
            ));
        }
    }

    /**
     * Alive, not creative/spectating, has a role that is not the Specter itself, and not SparkFactionAPI effective
     * killer faction (a Conscience killer is slowed, an Impostor is not; the caster never slows itself).
     * 存活、非创造/旁观、有职业且不是幽魂本身，并且不属于 SFA 有效杀手阵营（善良杀手会被减速，内鬼不会；施放者不会减速自己）。
     */
    private static boolean isNonKillerParticipant(ServerPlayerEntity player, GameWorldComponent game) {
        if (player.isSpectator()
                || GameFunctions.isPlayerSpectatingOrCreative(player)
                || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return false;
        }
        var role = game.getRole(player);
        return role != null && !SpecterRules.isSpecter(role) && !SpecterRules.isEffectiveKiller(player);
    }
}
