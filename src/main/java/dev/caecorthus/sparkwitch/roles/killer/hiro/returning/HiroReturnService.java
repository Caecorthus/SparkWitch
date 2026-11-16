package dev.caecorthus.sparkwitch.roles.killer.hiro.returning;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkfactionapi.api.cooldown.CooldownKind;
import dev.caecorthus.sparkfactionapi.api.cooldown.ForcedCooldowns;
import dev.caecorthus.sparkwitch.compat.NoellesSilenceBridge;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.item.rope.RopeStrangleService;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeKillAttribution;
import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelWorldComponent;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerInventoryRules;
import dev.caecorthus.sparkwitch.roles.killer.hiro.HiroRules;
import dev.caecorthus.sparkwitch.roles.killer.hiro.returning.HiroReturnComponent.RecordPoint;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.killer.ninja.NinjaGrappleService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 二阶堂希罗's 死亡回归 (Return by Death), server-authoritative (owner rules 2026-11-11).
 * <ul>
 *   <li>Cast ({@link #use}, ability key G): a living raw-role Hiro in an ACTIVE round who passes the caster gate, has
 *   no running window and no cooldown and can pay {@link HiroRules#RETURN_MANA_COST} mana records her position and
 *   opens a {@link HiroRules#RETURN_WINDOW_TICKS} window. Only she is told.</li>
 *   <li>While the window runs, every kill credited to her that really commits becomes a fake death
 *   ({@link HiroVictimFeignService}); each victim runs its own {@link HiroRules#VICTIM_FEIGN_TICKS} timer.</li>
 *   <li>A kill attempt on her by another player ({@link #onKillAttempt}, HEAD of Wathe's {@code killPlayer}) revives
 *   her fake-dead victims, also after the window ended and also when that attack kills her. Inside the window a
 *   cancellable attack is cancelled before any of her shields is touched and the Return fires, once per cast:
 *   {@link HiroRules#RETURN_REWARD_COINS} coins (none from an effective killer-team attacker), back to the record point,
 *   {@link HiroRules#RETURN_GLOW_TICKS} Glowing, a {@link HiroRules#RETURN_ITEM_LOCK_TICKS} all-item cooldown, her
 *   Rope strangle interrupted, the window closed and the {@link HiroRules#RETURN_COOLDOWN_TICKS} cooldown started.</li>
 *   <li>A window that ends un-attacked closes itself ({@link HiroReturnComponent#serverTick}); her real death closes
 *   it too. First use {@link HiroRules#OPENING_COOLDOWN_TICKS} after role assignment.</li>
 * </ul>
 * Lifecycle: a role assignment or a reset clears her own state. A Hiro who is re-roled or leaves keeps her fake-dead
 * victims: they keep their timers, die on expiry credited to her, and still get up if that player is attacked.
 * 二阶堂希罗的「死亡回归」，服务端权威（所有者 2026-11-11 规则）。施放（{@link #use}，技能键 G）：ACTIVE 回合中存活的真实身份
 * 希罗通过施放者门槛、没有进行中的窗口与冷却、且付得起 {@link HiroRules#RETURN_MANA_COST} 魔力时，记录自身位置并开启
 * {@link HiroRules#RETURN_WINDOW_TICKS} 的窗口，只有她本人得到提示。窗口期间，记在她头上且真正落定的击杀变成假死
 * （{@link HiroVictimFeignService}），每名受害者各自计时 {@link HiroRules#VICTIM_FEIGN_TICKS}。其他玩家对她的击杀尝试
 * （{@link #onKillAttempt}，Wathe {@code killPlayer} 的 HEAD）会让她的假死受害者复活，窗口结束后以及这次攻击杀死她时同样
 * 如此。窗口内可撤销的攻击会在她的任何护盾被触及之前取消，并触发回归（每次施放一次）：获得
 * {@link HiroRules#RETURN_REWARD_COINS} 金币（攻击者属于杀手阵营时没有）、回到记录点、发光
 * {@link HiroRules#RETURN_GLOW_TICKS}、全物品冷却 {@link HiroRules#RETURN_ITEM_LOCK_TICKS}、打断她的绳索勒杀、关闭窗口并
 * 进入 {@link HiroRules#RETURN_COOLDOWN_TICKS} 冷却。未遭攻击而结束的窗口自行关闭（{@link HiroReturnComponent#serverTick}），
 * 她真正死亡时窗口同样关闭。分配职业后 {@link HiroRules#OPENING_COOLDOWN_TICKS} 才能首次使用。
 * 生命周期：职业分配或重置会清除她自身的状态。被换职业或离线的希罗保留其假死受害者：他们继续各自计时、到期后记在她头上
 * 死亡，而该玩家遭到攻击时他们仍会起身。
 */
public final class HiroReturnService {
    static final String RECORDED_KEY = "message.sparkwitch.nikaido_hiro.return.recorded";
    static final String ACTIVE_KEY = "message.sparkwitch.nikaido_hiro.return.active";
    static final String COOLDOWN_KEY = "message.sparkwitch.nikaido_hiro.return.cooldown";
    static final String NO_MANA_KEY = "message.sparkwitch.nikaido_hiro.return.no_mana";
    static final String TRIGGERED_KEY = "message.sparkwitch.nikaido_hiro.return.triggered";
    static final String TRIGGERED_NO_COINS_KEY = "message.sparkwitch.nikaido_hiro.return.triggered.no_coins";
    static final String STAYED_KEY = "message.sparkwitch.nikaido_hiro.return.triggered.stayed";
    static final String REPLAY_CAST_KEY = "replay.skill.sparkwitch.nikaido_hiro.return";
    static final String REPLAY_TRIGGERED_KEY = "replay.skill.sparkwitch.nikaido_hiro.return.triggered";
    static final String REPLAY_TRIGGERED_BY_KEY = "replay.skill.sparkwitch.nikaido_hiro.return.triggered.by";
    /** Extra flag of the skill record written when the Return fires. / 回归触发时技能记录附带的标记。 */
    private static final String RETURNED_TAG = "returned";
    /**
     * Hiros whose all-item cooldown is raised at the end of this tick, after the handler that attacked her (or her own
     * item handler) wrote its cooldowns, so the lock is never lowered again. Server thread only.
     * 本刻结束时抬高全物品冷却的希罗：此时攻击她的处理器（或她自己的物品处理器）已写完冷却，锁定不会再被压低。仅服务端线程。
     */
    private static final Set<UUID> PENDING_ITEM_LOCKS = new LinkedHashSet<>();
    private static boolean registered;

    private HiroReturnService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                HiroReturnComponent.KEY.get(serverPlayer).assignForRole(role);
            }
        });
        ResetPlayer.EVENT.register(player -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                HiroReturnComponent.KEY.get(serverPlayer).clear();
                PENDING_ITEM_LOCKS.remove(serverPlayer.getUuid());
            }
        });
        HiroVictimFeignService.register();
        KillPlayer.AFTER.register(HiroReturnService::afterKill);
        ServerTickEvents.END_SERVER_TICK.register(HiroReturnService::flushItemLocks);
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> PENDING_ITEM_LOCKS.clear());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING_ITEM_LOCKS.clear());
        ReplayRegistry.registerSkillFormatter(HiroRules.RETURN_SKILL_ID, HiroReturnService::formatReplay);
    }

    /**
     * Cross-feature seam (stable signature): whether {@code player} is currently one of Hiro's fake-dead victims (alive
     * in Wathe, spectating its own fake corpse, waiting for its own timer). The Rope treats a kill that ended in this
     * state as a successful kill. Server truth; always false on a client.
     * 跨功能接缝（签名稳定）：{@code player} 此刻是否是希罗的假死受害者（在 Wathe 中仍存活、旁观自己的假尸体、等待各自的计时）。
     * 绳索把以此状态结束的击杀视为成功击杀。服务端判定；客户端一律为 false。
     */
    public static boolean isFeignedVictim(PlayerEntity player) {
        return HiroVictimFeignService.isFeigned(player);
    }

    // ---- Cast / 施放 ----

    /**
     * Server entry of the ability key ({@code sparkwitch:use_hiro_skill}, an empty request). Order: the caster gate
     * {@link #mayCast} (refused silently), a running window and the cooldown (she is told which), the Grand Witch Fear
     * re-check last (it tells her), then the mana cost. A refused press changes nothing and costs nothing.
     * 技能键的服务端入口（{@code sparkwitch:use_hiro_skill}，空请求）。顺序：施放者门槛 {@link #mayCast}（静默拒绝）、进行中的
     * 窗口与冷却（提示具体原因）、最后再检查大魔女恐惧（会提示），然后扣除魔力。被拒绝的按键不改变任何东西，也不消耗任何东西。
     */
    public static void use(ServerPlayerEntity caster) {
        if (caster == null || !mayCast(caster)) {
            return;
        }
        HiroReturnComponent component = HiroReturnComponent.KEY.get(caster);
        long now = caster.getWorld().getTime();
        switch (HiroReturnRules.castRefusal(component.isWindowActive(now), component.getCooldownTicks())) {
            case WINDOW_RUNNING -> {
                caster.sendMessage(Text.translatable(ACTIVE_KEY), true);
                return;
            }
            case COOLDOWN -> {
                caster.sendMessage(Text.translatable(COOLDOWN_KEY,
                        HiroReturnRules.displaySeconds(component.getCooldownTicks())), true);
                return;
            }
            case NONE -> {
            }
        }
        if (GrandWitchFearService.denyRoleSkillIfFeared(caster)) {
            return;
        }
        // spendMana refuses without spending when the mana system is off or the balance is short.
        // 魔力系统未启用或余额不足时，spendMana 拒绝且不扣除。
        if (!WitchPlayerComponent.KEY.get(caster).spendMana(HiroRules.RETURN_MANA_COST)) {
            caster.sendMessage(Text.translatable(NO_MANA_KEY, HiroRules.RETURN_MANA_COST), true);
            return;
        }
        component.openWindow(now + HiroRules.RETURN_WINDOW_TICKS, RecordPoint.of(caster));
        // Private feedback: an action-bar line and a quiet sound only she hears.
        // 私人反馈：动作栏提示与一声只有她听得到的轻响。
        caster.sendMessage(Text.translatable(RECORDED_KEY), true);
        caster.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.6F, 0.6F);
        GameRecordManager.recordSkillUse(caster, HiroRules.RETURN_SKILL_ID, null, null);
    }

    /**
     * Server caster gate (the Specter's {@code mayCast} shape): an ACTIVE round, the raw role, alive and playing, not
     * spectating or creative (so never while she is fake-dead herself), not swallowed, not held (Ninja hook,
     * Kidnapper, stun, Last Escape/Stand, Hunter root, Seeker session, a foreign camera), not silenced, not
     * role-skill-blocked by SparkTraits and not inside a Rift Gate. {@link #use} then applies the Fear re-check itself.
     * 服务端施放者门槛（沿用幽魂 {@code mayCast} 的形状）：回合处于 ACTIVE、真实身份、存活且参与对局、非旁观或创造（因此她自己
     * 假死时绝不能施放）、未被吞下、未被控制（忍者钩爪、绑匪、眩晕、脱险/背水、猎人定身、搜寻者会话、镜头被占用）、未被沉默、
     * 未被 SparkTraits 封锁职业技能，且不在裂隙门内。{@link #use} 随后自行再检查恐惧。
     */
    static boolean mayCast(ServerPlayerEntity caster) {
        return GameWorldComponent.KEY.get(caster.getWorld()).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && HiroRules.isLivingHiro(caster)
                && !NoellesTaotieSeekerBridge.isSwallowed(caster)
                && !NinjaGrappleService.isHeld(caster)
                && !NoellesSilenceBridge.isSilenced(caster)
                && !SparkTraitsKillerBridge.isRoleSkillBlocked(caster)
                && !RiftSessionService.isInside(caster);
    }

    // ---- Attacked / 遭到攻击 ----

    /**
     * HEAD of Wathe's 5-arg {@code killPlayer} ({@code mixin/hiro/GameFunctionsHiroReturnMixin}), before every shield
     * guard: returns true when the kill must be cancelled because the Return fired. Every victim that neither has a
     * running window nor fake-dead victims returns false at once, so no other role reaches this path. Deliberately not
     * gated on stun or control: a Hiro attacked while she strangles (the Rope holds both players as stunned) or while
     * she is stunned, feared or hypnotised still returns; only the cast is refused in those states.
     * Wathe 五参 {@code killPlayer} 的 HEAD（{@code mixin/hiro/GameFunctionsHiroReturnMixin}），早于所有护盾守卫：返回 true
     * 表示回归已触发、必须取消这次击杀。既没有进行中的窗口、也没有假死受害者的受害者立即返回 false，其他职业不会进入此路径。
     * 有意不以眩晕或受控为门槛：希罗在勒杀途中（绳索把双方都视为眩晕）或被眩晕、恐惧、催眠时遭到攻击仍会回归；这些状态下
     * 只有施放被拒绝。
     */
    public static boolean onKillAttempt(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer,
                                        @Nullable Identifier deathReason, boolean force) {
        if (victim == null) {
            return false;
        }
        UUID id = victim.getUuid();
        ServerWorld world = victim.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        HiroReturnComponent component = HiroReturnComponent.KEY.get(victim);
        boolean windowActive = component.isWindowActive(world.getTime())
                && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && HiroRules.isHiro(game.getRole(victim));
        if (!windowActive && HiroVictimFeignService.victimCount(id) == 0) {
            return false;
        }
        // A kill call on a player Wathe already counts dead is a no-op, not an attack.
        // 对 Wathe 已判定死亡的玩家调用击杀是空操作，不算攻击。
        if (!GameFunctions.isPlayerPlayingAndAlive(victim)) {
            return false;
        }
        UUID attacker = attackerOf(victim, killer);
        HiroReturnRules.Attack attack = HiroReturnRules.classifyAttack(id, attacker, deathReason, force,
                SparkTraitsKillerBridge.isTerminalDeathReason(deathReason) || SaviorRules.isTerminalKill(deathReason),
                isDueling(world, id));
        switch (HiroReturnRules.outcome(attack, windowActive)) {
            case RETURN -> {
                fireReturn(victim, component, attacker, game);
                return true;
            }
            case REVIVE_VICTIMS -> HiroVictimFeignService.reviveVictimsOf(world.getServer(), id);
            case IGNORE -> {
            }
        }
        return false;
    }

    /**
     * The other player behind this attempt: the {@code killer} argument, else the Judge-attributed cause of an indirect
     * kill (grenade, poison…); null when there is none or it is Hiro herself.
     * 这次尝试背后的其他玩家：{@code killer} 参数，否则为间接击杀（手雷、毒药……）的法官归因责任人；没有或就是希罗本人时为 null。
     */
    private static @Nullable UUID attackerOf(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer) {
        if (killer != null && killer != victim) {
            return killer.getUuid();
        }
        UUID responsible = JudgeKillAttribution.attemptResponsible(victim);
        return responsible == null || responsible.equals(victim.getUuid()) ? null : responsible;
    }

    private static boolean isDueling(ServerWorld world, UUID player) {
        KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(world);
        return duel.active() && duel.isDuelist(player);
    }

    /**
     * The Return (owner rule 6). The window closes first, so nothing below can fire a second one. When she cannot be
     * moved (another dimension, swallowed, inside a Rift Gate, Kidnapper-controlled, a Seeker session) the attack is
     * still cancelled and everything except the teleport happens; she is told she stayed.
     * 回归（所有者规则 6）。先关闭窗口，下面的任何步骤都无法再触发第二次。无法被移动时（位于其他维度、被吞下、在裂隙门内、
     * 被绑匪控制、搜寻者会话中）攻击仍被取消，除传送外的一切照常发生，并提示她留在了原地。
     */
    private static void fireReturn(ServerPlayerEntity hiro, HiroReturnComponent component, @Nullable UUID attacker,
                                   GameWorldComponent game) {
        MinecraftServer server = hiro.getServer();
        RecordPoint point = component.recordPoint();
        component.closeWindow(HiroRules.RETURN_COOLDOWN_TICKS);

        ServerPlayerEntity attackerPlayer = attacker == null || server == null
                ? null
                : server.getPlayerManager().getPlayer(attacker);
        // Anti-farm by the attacker's effective side (Impostor ally pays 0, Conscience killer pays); the raw role
        // only answers for an attributed attacker who is offline.
        // 防刷按攻击者的有效阵营判定（内鬼同伴不给金币，善良杀手给）；只有被归因但已离线的攻击者才按原始职业判定。
        Role attackerRole = attacker == null ? null : game.getRole(attacker);
        int coins = HiroReturnRules.rewardCoins(HiroReturnRules.attackerOnKillerTeam(
                attackerPlayer == null ? null : SparkFactionApi.resolveEffectiveFaction(attackerPlayer, game),
                attackerRole != null && attackerRole.canUseKiller()));
        if (coins > 0) {
            PlayerShopComponent.KEY.get(hiro).addToBalance(coins);
        }
        RopeStrangleService.interruptUser(hiro);
        boolean moved = teleportToRecordPoint(hiro, point);
        hiro.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, HiroRules.RETURN_GLOW_TICKS, 0,
                false, false, true));
        PENDING_ITEM_LOCKS.add(hiro.getUuid());
        int revived = HiroVictimFeignService.reviveVictimsOf(server, hiro.getUuid());

        MutableText line = coins > 0
                ? Text.translatable(TRIGGERED_KEY, coins)
                : Text.translatable(TRIGGERED_NO_COINS_KEY);
        if (!moved) {
            line = Text.empty().append(line).append(Text.literal(" ")).append(Text.translatable(STAYED_KEY));
        }
        hiro.sendMessage(line, true);
        hiro.playSoundToPlayer(SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.PLAYERS, 0.8F, 0.8F);

        NbtCompound extra = new NbtCompound();
        extra.putBoolean(RETURNED_TAG, true);
        extra.putInt("coins", coins);
        extra.putInt("revived", revived);
        extra.putBoolean("moved", moved);
        GameRecordManager.recordSkillUse(hiro, HiroRules.RETURN_SKILL_ID, attackerPlayer, extra);
    }

    private static boolean teleportToRecordPoint(ServerPlayerEntity hiro, @Nullable RecordPoint point) {
        if (point == null || !HiroReturnRules.mayTeleport(
                hiro.getWorld().getRegistryKey().getValue().equals(point.dimension()),
                hiro.isSpectator(),
                NoellesTaotieSeekerBridge.isSwallowed(hiro),
                RiftSessionService.isInside(hiro) || KidnapperControlComponent.KEY.get(hiro).isControlled()
                        || SeekerInventoryRules.isInSession(hiro))) {
            return false;
        }
        hiro.stopRiding();
        if (hiro.isSleeping()) {
            hiro.wakeUp();
        }
        hiro.teleport(hiro.getServerWorld(), point.x(), point.y(), point.z(), Set.of(), point.yaw(), point.pitch());
        hiro.setVelocity(Vec3d.ZERO);
        hiro.velocityModified = true;
        hiro.fallDistance = 0.0F;
        return true;
    }

    /**
     * All-item cooldown of the Return through SparkFactionAPI {@code ForcedCooldowns.raiseAll} ({@code ITEM} only:
     * exact, never shortened, role skills untouched, registry item exemptions kept), settled at
     * {@code END_SERVER_TICK} like the Fiend's aura. A self-inflicted lock, so no {@code canAffectPlayer} question.
     * 回归的全物品冷却经 SparkFactionAPI {@code ForcedCooldowns.raiseAll}（仅 {@code ITEM}：精确写入、绝不缩短、不动职业技能、
     * 保留注册表的物品豁免），与魔人光环一样在 {@code END_SERVER_TICK} 结算。这是施加于自身的锁定，因此无需询问
     * {@code canAffectPlayer}。
     */
    private static void flushItemLocks(MinecraftServer server) {
        if (PENDING_ITEM_LOCKS.isEmpty()) {
            return;
        }
        List<UUID> pending = List.copyOf(PENDING_ITEM_LOCKS);
        PENDING_ITEM_LOCKS.clear();
        for (UUID id : pending) {
            ServerPlayerEntity hiro = server.getPlayerManager().getPlayer(id);
            if (hiro != null && GameFunctions.isPlayerPlayingAndAlive(hiro)) {
                ForcedCooldowns.raiseAll(hiro, CooldownKind.ITEM, HiroRules.RETURN_ITEM_LOCK_TICKS);
            }
        }
    }

    // ---- Lifecycle / 生命周期 ----

    /**
     * Her real death ends the skill: the window closes and the cooldown starts. Her fake-dead victims are not touched
     * here; the attack that killed her already revived them at HEAD, and after a death without an attacker they keep
     * their own timers.
     * 她真正死亡即技能结束：关闭窗口并开始冷却。这里不处理她的假死受害者；杀死她的那次攻击已在 HEAD 让他们复活，而没有攻击者的
     * 死亡之后他们继续各自计时。
     */
    private static void afterKill(@Nullable ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer,
                                  @Nullable Identifier deathReason) {
        if (victim == null) {
            return;
        }
        HiroReturnComponent component = HiroReturnComponent.KEY.get(victim);
        if (component.windowEndTick() > 0L) {
            component.closeWindow(HiroRules.RETURN_COOLDOWN_TICKS);
        }
    }

    // ---- Replay / 回放 ----

    /** One skill id, two lines: the cast, and the Return it fired. / 一个技能 id，两种条目：施放，以及由它触发的回归。 */
    private static @Nullable Text formatReplay(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                               ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        var playerInfo = ReplayGenerator.getPlayerInfoCache(match);
        Text actor = ReplayGenerator.formatPlayerName(data.getUuid("actor"), playerInfo);
        if (!data.getBoolean(RETURNED_TAG)) {
            return Text.translatable(REPLAY_CAST_KEY, actor);
        }
        return data.containsUuid("target")
                ? Text.translatable(REPLAY_TRIGGERED_BY_KEY, actor,
                        ReplayGenerator.formatPlayerName(data.getUuid("target"), playerInfo))
                : Text.translatable(REPLAY_TRIGGERED_KEY, actor);
    }
}
