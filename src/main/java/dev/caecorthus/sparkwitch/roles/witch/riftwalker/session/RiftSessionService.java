package dev.caecorthus.sparkwitch.roles.witch.riftwalker.session;

import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsSeekerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone.DeepDarkZoneStandingService;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftGateUser;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftGateUsers;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerMatch;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerRules;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerStatusProbes;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateCloseReason;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateEntity;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateRecord;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateRegistry;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Frozen (G0) server API of the "inside a Rift Gate" session (plan §6, §7): entry, hop, exit, forced exits, the
 * living-spectator body model and its loophole guards, the re-entry cooldown, the payload guard and voice mute. Every
 * C2S request is re-validated here on the server thread; the client never predicts entry. Read-only queries
 * ({@link #isInside}, {@link #currentGate}, {@link #occupantNames}) are implemented by G0 from
 * {@link RiftSessionComponent} so other packages can rely on them now. Owned by P2.
 * 「在裂隙门内」会话的冻结（G0）服务端 API（plan §6、§7）：进门、跳门、出门、强制出门、活着的旁观者模型及其漏洞防护、
 * 再次进门冷却、数据包拦截与语音静音。每个 C2S 请求都在服务端线程重新校验；客户端从不预测进门。只读查询
 * （{@link #isInside}、{@link #currentGate}、{@link #occupantNames}）由 G0 基于 {@link RiftSessionComponent} 实现，
 * 其他包现在即可依赖。归属 P2。
 *
 * <p>Body model (D3): an occupant is an ALIVE spectator held at the gate anchor (NR Taotie pattern). Wathe's weapons
 * skip spectators, so knife, gun, grenade and bat cannot reach it; the SparkFactionAPI policy denies every other
 * player-affect action except the Swapper (P9's crush), poison (D3) and the bell toll / time-stolen terminal deaths
 * (C13). Game-mode ownership: SPECTATOR is handed back
 * only on a living release (see {@link RiftSessionRules#mayRestoreMode}); death, Last Stand, Depression, disconnect
 * and round reset never touch the mode, so a corpse is never revived.
 * 本体模型（D3）：门内玩家是被固定在门锚点的「活着的旁观者」（NR 饕餮模式）。Wathe 的武器跳过旁观者，刀、枪、手雷、
 * 球棒都打不到；SparkFactionAPI 策略拒绝其他所有玩家影响行为，只放行交换者（P9 的夹死）、毒（D3）以及敲钟与窃时两种终结死亡
 * （C13）。游戏模式归属：
 * 只有存活释放时才交还旁观模式（见 {@link RiftSessionRules#mayRestoreMode}）；死亡、背水一战、抑郁、断线与对局重置
 * 从不改动模式，因此绝不会复活尸体。
 */
public final class RiftSessionService {
    /** Global, never 0; unique per player per entry is all the C2S echo needs. / 全局递增且不为 0。 */
    private static final AtomicInteger NEXT_SESSION_ID = new AtomicInteger();
    private static boolean registered;

    private RiftSessionService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        RiftSessionLifecycle.register();
        RiftSessionGuards.register();
        RiftSessionAffectPolicy.register();
    }

    /**
     * Right-click entry request from {@link RiftGateEntity#interact} (D2). Returns the interaction result
     * ({@code SUCCESS} when the session began, {@code FAIL} with an actionbar reason otherwise).
     * 来自 {@link RiftGateEntity#interact} 的右键进门请求（D2）。返回交互结果（会话开始为 {@code SUCCESS}，否则
     * {@code FAIL} 并在动作栏说明原因）。
     */
    public static ActionResult tryEnter(ServerPlayerEntity player, RiftGateEntity gate) {
        if (player == null || gate == null) {
            return ActionResult.FAIL;
        }
        RiftSessionComponent session = RiftSessionComponent.KEY.getNullable(player);
        if (session == null) {
            return ActionResult.FAIL;
        }
        ServerWorld world = player.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        String matchId = RiftwalkerMatch.currentMatchId(world);
        if (!session.inside() && session.readyAtTick() > 0L && !RiftwalkerMatch.matches(session.matchId(), matchId)) {
            // A cooldown from another match never blocks this one. / 其他对局的冷却从不阻挡本局。
            session.clear();
        }
        RiftGateUser user = RiftGateUsers.classify(player);
        Optional<RiftGateRecord> record = registeredRecord(world, gate);
        WitchPlayerComponent witch = WitchPlayerComponent.KEY.getNullable(player);
        RiftSessionRules.EntryFacts facts = new RiftSessionRules.EntryFacts(
                user,
                game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE && matchId != null,
                isParticipant(player),
                session.inside(),
                isControlled(player),
                RiftwalkerStatusProbes.isHunterRooted(player),
                RiftwalkerStatusProbes.isCaptureStunned(player),
                player.interactionManager.getGameMode(),
                record.isPresent(),
                gate.getBoundingBox().squaredMagnitude(player.getEyePos())
                        <= RiftwalkerRules.ENTRY_REACH * RiftwalkerRules.ENTRY_REACH,
                session.cooldownRemainingTicks(),
                witch != null && witch.hasManaSystem(),
                witch == null ? 0 : witch.getMana());
        RiftSessionRules.EntryDenial denial = RiftSessionRules.entryDenial(facts);
        if (denial != null) {
            sendEntryDenial(player, denial, user, session);
            return ActionResult.FAIL;
        }
        // C1: charged per ENTRY, only after every other gate passed; hops inside are free.
        // C1：按进门次数计费，且只在其他条件全部通过后扣除；门内跳转免费。
        if (user.paysMana() && (witch == null || !witch.spendMana(user.entryFee()))) {
            sendEntryDenial(player, RiftSessionRules.EntryDenial.NOT_ENOUGH_MANA, user, session);
            return ActionResult.FAIL;
        }
        if (!begin(player, world, session, user, record.get(), matchId)) {
            if (user.paysMana() && witch != null) {
                witch.addMana(user.entryFee());
            }
            player.sendMessage(Text.translatable(RiftSessionRules.UNAVAILABLE_KEY), true);
            return ActionResult.FAIL;
        }
        return ActionResult.SUCCESS;
    }

    /**
     * {@code sparkwitch:rift_hop}: {@code direction} is -1 (previous) or +1 (next) in the hop ring; anything else is
     * ignored. Stale {@code sessionId}s are ignored; hops are throttled ({@code HOP_THROTTLE_TICKS}, D11), silent and
     * free (C1). The target is re-validated live (record and loaded entity); a broken one is skipped.
     * {@code sparkwitch:rift_hop}：{@code direction} 为 -1（上一扇）或 +1（下一扇）；其他值忽略。过期会话 id 忽略；跳门有节流
     * （D11）、静默且免费（C1）。目标实时重新校验（记录与已加载实体），失效的目标被跳过。
     */
    public static void handleHop(ServerPlayerEntity player, int sessionId, int direction) {
        if (player == null || !RiftHopRing.isDirection(direction)) {
            return;
        }
        RiftSessionComponent session = RiftSessionComponent.KEY.getNullable(player);
        if (session == null || !session.inside() || sessionId == 0 || sessionId != session.sessionId()) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        long now = world.getTime();
        // The tick decides every forced exit; a hop runs only while the session still holds.
        // 所有强制出门都由逐刻判定；只有会话仍然有效时才执行跳门。
        if (now < session.nextHopTick() || RiftSessionRules.tickExitReason(tickFacts(player, session, world, now)) != null) {
            return;
        }
        List<RiftGateRecord> gates = RiftGateRegistry.gates(world);
        List<Integer> ring = numbers(gates);
        for (int number : RiftHopRing.hopOrder(ring, session.gateNumber(), direction)) {
            Optional<RiftGateRecord> target = gates.stream().filter(gate -> gate.number() == number).findFirst();
            if (target.isEmpty() || RiftGateRegistry.entity(world, number).isEmpty()) {
                continue;
            }
            RiftGateRecord gate = target.get();
            RiftSessionBody.teleport(player, world, gate.pos(), gate.facing().asRotation(), 0.0F, false);
            session.moveToGate(gate.number(), RiftHopRing.ringIndex(ring, gate.number()), ring.size(), gate.pos(),
                    gate.facing(), now + RiftwalkerRules.HOP_THROTTLE_TICKS);
            RiftSessionBody.refreshTracking(player, world);
            return;
        }
        player.sendMessage(Text.translatable(RiftSessionRules.NO_OTHER_GATE_KEY), true);
    }

    /**
     * {@code sparkwitch:rift_exit}: always honoured for the current session (Shift), even while stunned; stale ids are
     * ignored. With no safe exit cell the occupant stays inside and is told so.
     * {@code sparkwitch:rift_exit}：当前会话的出门请求始终受理（Shift），即使被眩晕；过期 id 忽略。没有安全出口时留在门内并收到提示。
     */
    public static void handleExit(ServerPlayerEntity player, int sessionId) {
        if (player == null) {
            return;
        }
        RiftSessionComponent session = RiftSessionComponent.KEY.getNullable(player);
        if (session == null || !session.inside() || sessionId == 0 || sessionId != session.sessionId()) {
            return;
        }
        end(player, session, RiftExitReason.PLAYER, null, null, null);
    }

    /**
     * Called by {@code RiftGateRegistry.close} after a record was removed: force-exit every occupant of that gate at
     * {@code removed.pos()} with {@link RiftExitReason#GATE_CLOSED} (cooldown when {@code reason.appliesExitCooldown()}),
     * and refresh other occupants' ring index/size.
     * 由 {@code RiftGateRegistry.close} 在记录移除后调用：在 {@code removed.pos()} 处以 {@link RiftExitReason#GATE_CLOSED}
     * 强制该门内所有人出门（{@code reason.appliesExitCooldown()} 时上冷却），并刷新其他门内玩家的环序号与环大小。
     */
    public static void onGateRemoved(ServerWorld world, RiftGateRecord removed, RiftGateCloseReason reason) {
        if (world == null || removed == null) {
            return;
        }
        List<Integer> ring = numbers(RiftGateRegistry.gates(world));
        for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
            RiftSessionComponent session = RiftSessionComponent.KEY.getNullable(player);
            if (session == null || !session.inside() || !world.getRegistryKey().equals(session.sessionWorld())) {
                continue;
            }
            if (session.gateNumber() == removed.number()) {
                // A blocked exit keeps the session; the tick retries at the stored anchor (the gate is gone).
                // 出口被堵时保留会话；逐刻会在保存的锚点处重试（门已不存在）。
                end(player, session, RiftExitReason.GATE_CLOSED, reason, removed.pos(), removed.facing());
            } else {
                session.updateRing(RiftHopRing.ringIndex(ring, session.gateNumber()), ring.size());
            }
        }
    }

    /**
     * Per-tick upkeep from {@link RiftSessionComponent#serverTick}: the forced-exit checks
     * ({@link RiftSessionRules#tickExitReason}), the anchor hold, the ring view and the once-per-second stay resync.
     * Cheap early-out when not inside (the cooldown needs no ticking: both sides compute it from absolute/synced ticks).
     * 来自 {@link RiftSessionComponent#serverTick} 的每 tick 维护：强制出门检查、锚点保持、环视图与每秒一次的停留同步。
     * 不在门内时直接返回（冷却无需逐刻处理：两端分别由绝对 tick / 同步值计算）。
     */
    public static void tick(ServerPlayerEntity player) {
        RiftSessionComponent session = player == null ? null : RiftSessionComponent.KEY.getNullable(player);
        if (session == null || !session.inside()) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        long now = world.getTime();
        RiftExitReason reason = RiftSessionRules.tickExitReason(tickFacts(player, session, world, now));
        if (reason != null && end(player, session, reason, null, null, null)) {
            return;
        }
        // Still inside (no reason, or a forced release found no safe cell yet and retries next tick).
        // 仍在门内（无出门原因，或强制释放尚未找到安全格，下一刻重试）。
        holdAnchor(player, session, world);
        List<Integer> ring = numbers(RiftGateRegistry.gates(world));
        session.updateRing(RiftHopRing.ringIndex(ring, session.gateNumber()), ring.size());
        session.syncStaySecondsIfChanged();
    }

    /** Ends the session (if any) for {@code reason}; returns whether one ended. / 结束会话；返回是否确有会话结束。 */
    public static boolean forceExit(ServerPlayerEntity player, RiftExitReason reason) {
        if (player == null || reason == null) {
            return false;
        }
        RiftSessionComponent session = RiftSessionComponent.KEY.getNullable(player);
        return session != null && end(player, session, reason, null, null, null);
    }

    /** Both sides (the client knows only its own state). / 双端（客户端只知道自己的状态）。 */
    public static boolean isInside(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        RiftSessionComponent session = RiftSessionComponent.KEY.getNullable(player);
        return session != null && session.inside();
    }

    /**
     * Client prediction for {@code RiftGateEntity#interact} (B-6): whether the local player's right-click on a gate is
     * an entry attempt ({@link RiftSessionRules#claimsRightClick}) from synced state (RAW role, Wathe liveness, own
     * session, mana). Any failure reads as "no", so the held item is used instead. Never decides an entry.
     * {@code RiftGateEntity#interact} 的客户端预测（B-6）：依据同步状态（原始职业、Wathe 存活、自己的会话、魔力）判断本地玩家
     * 对门的右键是否为进门尝试（{@link RiftSessionRules#claimsRightClick}）。任何失败都视为「否」，此时改用手中物品。从不决定进门。
     */
    public static boolean claimsRightClick(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        try {
            RiftSessionComponent session = RiftSessionComponent.KEY.getNullable(player);
            WitchPlayerComponent witch = WitchPlayerComponent.KEY.getNullable(player);
            boolean participant = GameFunctions.isPlayerPlayingAndAlive(player)
                    && GameFunctions.isPlayerAliveAndSurvival(player)
                    && !WraithStateService.isActive(player);
            return RiftSessionRules.claimsRightClick(RiftGateUsers.classify(player), participant,
                    session != null && session.inside(),
                    session == null ? 0 : session.cooldownRemainingTicks(),
                    witch != null && witch.hasManaSystem(), witch == null ? 0 : witch.getMana());
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    /** Server: the gate number the player is inside, if any. / 服务端：玩家所在门的编号（若在门内）。 */
    public static OptionalInt currentGate(ServerPlayerEntity player) {
        RiftSessionComponent session = player == null ? null : RiftSessionComponent.KEY.getNullable(player);
        return session != null && session.inside() ? OptionalInt.of(session.gateNumber()) : OptionalInt.empty();
    }

    /**
     * Server: profile names of the players currently inside gate {@code gateNumber} of this world, sorted (D16: the
     * console shows who is inside).
     * 服务端：当前位于本世界 {@code gateNumber} 号门内的玩家名，已排序（D16：控制台显示门内是谁）。
     */
    public static List<String> occupantNames(ServerWorld world, int gateNumber) {
        List<String> names = new ArrayList<>();
        for (ServerPlayerEntity player : world.getPlayers()) {
            OptionalInt gate = currentGate(player);
            if (gate.isPresent() && gate.getAsInt() == gateNumber) {
                names.add(player.getGameProfile().getName());
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(names);
    }

    /**
     * External seam for {@code RiftSessionNetworkHandlerMixin} (server thread): every network teleport of an occupant
     * that is a foreign move ({@link RiftSessionRules#isForeignTeleport}) is remembered; the next tick ends the session
     * as BODY_MOVED if the body landed beyond the tolerance, otherwise snaps it back. Neither client-side drift
     * (spectator flight) nor a reset vanilla issues while handling that player's own move packet
     * ({@code duringOwnMovePacket}, B-1) sets this, so a modified client is always snapped back to the anchor and can
     * never "move itself out" of a gate.
     * 供 {@code RiftSessionNetworkHandlerMixin} 调用的外部接缝（服务端线程）：门内玩家的每次属于外部移动的网络传送
     * （{@link RiftSessionRules#isForeignTeleport}）都会被记下；下一刻若本体落在容差之外则以 BODY_MOVED 结束会话，否则拉回锚点。
     * 客户端漂移（旁观飞行）与原版在处理该玩家自己的移动包时发出的重置（{@code duringOwnMovePacket}，B-1）都不会设置此标记，
     * 因此修改过的客户端总会被拉回锚点，永远无法「自己飞出」门。
     */
    public static void onTeleportRequested(ServerPlayerEntity player, boolean duringOwnMovePacket) {
        if (player == null
                || !RiftSessionRules.isForeignTeleport(RiftSessionBody.isOwnTeleport(), duringOwnMovePacket)) {
            return;
        }
        RiftSessionComponent session = RiftSessionComponent.KEY.getNullable(player);
        if (session != null && session.inside()) {
            session.markForeignMove();
        }
    }

    // ---- Internals / 内部实现 ----

    private static boolean begin(ServerPlayerEntity player, ServerWorld world, RiftSessionComponent session,
                                 RiftGateUser user, RiftGateRecord gate, String matchId) {
        GameMode previous = player.interactionManager.getGameMode();
        Vec3d origin = player.getPos();
        long now = world.getTime();
        if (player.isUsingItem()) {
            // Clear, never release: releasing a Wathe knife would stab. / 只清除、绝不释放：释放 Wathe 小刀会触发刺击。
            player.clearActiveItem();
        }
        player.setSprinting(false);
        if (player.currentScreenHandler != player.playerScreenHandler) {
            player.closeHandledScreen();
        }
        if (player.isSleeping()) {
            player.wakeUp();
        }
        player.stopRiding();
        List<Integer> ring = numbers(RiftGateRegistry.gates(world));
        // Client authority order: the owner sync (inside=true) is sent BEFORE the SPECTATOR switch, so the client's input
        // lock is up before it can ever act as a spectator (noclip, spectator menu, fly-speed scroll). The flag also goes
        // up before the teleport, so the move is never mistaken for a foreign one and every guard already applies.
        // 客户端权威顺序：拥有者同步（inside=true）先于切换旁观模式发出，客户端在能以旁观者身份行动（穿墙、旁观菜单、滚轮调飞行速度）
        // 之前就已锁定输入。标记也先于传送设置，传送不会被误判为外部移动，所有防护也已生效。
        session.beginSession(NEXT_SESSION_ID.updateAndGet(id -> id == Integer.MAX_VALUE ? 1 : id + 1), user,
                gate.number(), RiftHopRing.ringIndex(ring, gate.number()), ring.size(), gate.pos(), gate.facing(),
                world.getRegistryKey(), origin, previous, now + user.stayTicks(), user.stayTicks(), matchId);
        if (!player.changeGameMode(GameMode.SPECTATOR)) {
            // Nothing moved yet: drop the session again (no cooldown; the caller refunds the fee).
            // 尚未移动任何东西：撤销会话（不上冷却；调用方退还费用）。
            session.clear();
            return false;
        }
        // Committed entry only (never a refusal or the rollback above): Deep Dark Zone exposure never carries into a gate
        // (owner 2026-10-04), so the ×15 drain and the pseudo task end now, with the zero synced to the owner.
        // 仅在进门已提交时（从不在拒绝或上面的回滚时）：深暗领域的暴露不会带进门内（所有者 2026-10-04），
        // 因此 ×15 理智下降与临时任务此刻结束，零值同步给拥有者。
        DeepDarkZoneStandingService.clearExposure(player);
        RiftSessionBody.teleport(player, world, gate.pos(), gate.facing().asRotation(), 0.0F, false);
        RiftSessionBody.refreshTracking(player, world);
        RiftSessionBody.cue(world, gate.pos(), true);
        return true;
    }

    /**
     * The one way a session ends. CLEAR_ONLY reasons only drop the state; a release searches a safe cell at the gate
     * (front, ring, entry origin; forced exits may relax the entity-overlap probe once), teleports while still
     * spectator, ends the session, then applies the ownership rule. Returns false when the session survives (no safe
     * cell): the player's own exit just tells them; a forced exit is retried by the next tick.
     * 结束会话的唯一途径。CLEAR_ONLY 原因只清除状态；释放时在门处寻找安全格（正前方、周围一圈、进门前位置；强制出门可放宽一次
     * 实体重叠检查），仍为旁观模式时传送，结束会话，再应用归属规则。会话保留（没有安全格）时返回 false：玩家自己出门只提示；
     * 强制出门由下一刻重试。
     */
    private static boolean end(ServerPlayerEntity player, RiftSessionComponent session, RiftExitReason reason,
                               @Nullable RiftGateCloseReason closeReason, @Nullable Vec3d gatePos,
                               @Nullable Direction gateFacing) {
        if (!session.inside()) {
            return false;
        }
        if (reason == RiftExitReason.ROUND_END) {
            session.clear();
            return true;
        }
        ServerWorld world = player.getServerWorld();
        RiftSessionRules.BodyAction action = RiftSessionRules.bodyAction(reason);
        Vec3d exitGate = gatePos != null ? gatePos : session.anchor();
        Direction exitFacing = gateFacing != null ? gateFacing : session.anchorFacing();
        if (action == RiftSessionRules.BodyAction.RELEASE_AT_GATE) {
            Vec3d spot = exitGate == null ? null : findExit(player, world, session, reason, exitGate, exitFacing);
            if (spot == null) {
                player.sendMessage(Text.translatable(RiftSessionRules.EXIT_BLOCKED_KEY), true);
                return false;
            }
            RiftSessionBody.teleport(player, world, spot, 0.0F, 0.0F, true);
        }
        GameMode previous = session.previousMode();
        boolean restore = RiftSessionRules.mayRestoreMode(action, new RiftSessionRules.BodyFacts(
                GameFunctions.isPlayerPlayingAndAlive(player) && player.isAlive(),
                player.isSpectator(),
                player.getCameraEntity() == player,
                NoellesTaotieSeekerBridge.isSwallowed(player),
                SparkTraitsSeekerBridge.isLastStandPending(player),
                previous));
        long readyAt = RiftSessionRules.appliesCooldown(reason, closeReason)
                ? world.getTime() + Math.max(0, RiftSessionRules.reentryCooldownTicks(session.sessionUser(),
                RiftGateUsers.classify(player)))
                : 0L;
        // Server state ends before the mode changes, so no guard or policy still treats the released body as inside; the
        // owner sync (inside=false) goes out only AFTER the mode is restored, so the client never drops its input lock
        // while it is still a spectator.
        // 服务端状态先于模式切换结束，任何防护或策略都不会再把已释放的本体当作门内玩家；拥有者同步（inside=false）在模式恢复
        // 之后才发出，客户端在仍是旁观者时绝不会解除输入锁。
        session.endSessionWithoutSync(readyAt);
        if (restore && previous != null) {
            player.changeGameMode(previous);
        }
        session.syncOwner();
        if (action != RiftSessionRules.BodyAction.CLEAR_ONLY) {
            RiftSessionBody.settle(player);
            RiftSessionBody.refreshTracking(player, world);
        }
        if (action == RiftSessionRules.BodyAction.RELEASE_AT_GATE) {
            RiftSessionBody.cue(world, exitGate, false);
        }
        String message = RiftSessionRules.exitMessageKey(reason);
        if (message != null && action != RiftSessionRules.BodyAction.CLEAR_ONLY) {
            player.sendMessage(Text.translatable(message), true);
        }
        return true;
    }

    @Nullable
    private static Vec3d findExit(ServerPlayerEntity player, ServerWorld world, RiftSessionComponent session,
                                  RiftExitReason reason, Vec3d gatePos, @Nullable Direction facing) {
        Vec3d origin = world.getRegistryKey().equals(session.sessionWorld()) ? session.entryOrigin() : null;
        List<Vec3d> candidates = RiftExitSearch.candidates(gatePos, facing == null ? Direction.NORTH : facing, origin);
        // B-2: ring cells must be reachable from the gate opening; the pre-entry position is exempt.
        // B-2：圈内格必须能从门口到达；进门前位置不受此限。
        Vec3d spot = RiftExitSearch.select(candidates, feet -> RiftExitSafety.isSafe(player, world, feet,
                RiftExitSearch.sweepStart(gatePos, feet, origin), false));
        if (spot == null && RiftSessionRules.mayRelaxEntityOverlap(reason)) {
            spot = RiftExitSearch.select(candidates, feet -> RiftExitSafety.isSafe(player, world, feet,
                    RiftExitSearch.sweepStart(gatePos, feet, origin), true));
        }
        return spot;
    }

    /**
     * Keeps the body on its anchor. A foreign teleport within the tolerance and any client drift (spectator flight,
     * velocity) are snapped back with a look-preserving teleport; a foreign teleport beyond the tolerance never gets
     * here (the tick already ended the session as BODY_MOVED).
     * 把本体保持在锚点上。容差内的外部传送与任何客户端漂移（旁观飞行、速度）都以保留视角的传送拉回；超出容差的外部传送不会
     * 到达这里（逐刻已以 BODY_MOVED 结束会话）。
     */
    private static void holdAnchor(ServerPlayerEntity player, RiftSessionComponent session, ServerWorld world) {
        session.clearForeignMove();
        Vec3d anchor = session.anchor();
        if (anchor == null) {
            return;
        }
        if (anchor.squaredDistanceTo(player.getPos()) > RiftSessionRules.ANCHOR_EPSILON_SQUARED) {
            RiftSessionBody.teleport(player, world, anchor, 0.0F, 0.0F, true);
        } else if (player.getVelocity().lengthSquared() > 0.0) {
            RiftSessionBody.settle(player);
        }
    }

    private static RiftSessionRules.TickFacts tickFacts(ServerPlayerEntity player, RiftSessionComponent session,
                                                        ServerWorld world, long now) {
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        Vec3d anchor = session.anchor();
        return new RiftSessionRules.TickFacts(
                game.isRunning(),
                RiftwalkerMatch.matches(session.matchId(), RiftwalkerMatch.currentMatchId(world)),
                world.getRegistryKey().equals(session.sessionWorld()),
                !player.isAlive() || game.isPlayerDead(player.getUuid()),
                NoellesTaotieSeekerBridge.isSwallowed(player),
                SparkTraitsSeekerBridge.isLastStandPending(player),
                player.getCameraEntity() == player,
                player.isSpectator(),
                RiftGateUsers.classify(player) == session.sessionUser(),
                RiftGateRegistry.byNumber(world, session.gateNumber()).isPresent(),
                session.foreignMove() && (anchor == null
                        || anchor.squaredDistanceTo(player.getPos()) > RiftSessionRules.BODY_MOVE_TOLERANCE_SQUARED),
                now >= session.stayDeadlineTick());
    }

    /** The gate's live record, only when it is this exact entity in this world. / 门的实时记录，仅当实体与世界都对应时。 */
    private static Optional<RiftGateRecord> registeredRecord(ServerWorld world, RiftGateEntity gate) {
        if (gate.isRemoved() || gate.getWorld() != world) {
            return Optional.empty();
        }
        return RiftGateRegistry.byNumber(world, gate.gateNumber())
                .filter(record -> record.entityId().equals(gate.getUuid()));
    }

    /**
     * Living participant: playing and alive, not spectator, not creative, not an active Wraith (dead but adventure).
     * 存活参与者：参赛且存活、非旁观、非创造、非激活冤魂（已死亡但处于冒险模式）。
     */
    private static boolean isParticipant(ServerPlayerEntity player) {
        return GameFunctions.isPlayerPlayingAndAlive(player)
                && player.isAlive()
                && !player.isSpectator()
                && !player.isCreative()
                && !WraithStateService.isActive(player);
    }

    /**
     * Another authority holds the body or the player is locked: Taotie, Last Stand, Last Escape, Kidnapper control,
     * Control Expert stun, a Seeker session, or a foreign camera.
     * 本体被其他机制持有或玩家被锁定：饕餮、背水一战、最后逃亡、绑架者控制、控场专家眩晕、搜寻者会话或镜头被他人占用。
     */
    private static boolean isControlled(ServerPlayerEntity player) {
        KidnapperControlComponent kidnap = KidnapperControlComponent.KEY.getNullable(player);
        return NoellesTaotieSeekerBridge.isSwallowed(player)
                || SparkTraitsSeekerBridge.isLastStandPending(player)
                || SparkTraitsKillerBridge.isLastEscapeActive(player)
                || kidnap != null && kidnap.isControlled()
                || ControlExpertStun.isStunned(player)
                || SeekerRemoteSessionService.isLocked(player)
                || player.getCameraEntity() != player;
    }

    private static void sendEntryDenial(ServerPlayerEntity player, RiftSessionRules.EntryDenial denial,
                                        RiftGateUser user, RiftSessionComponent session) {
        String key = denial.messageKey();
        if (key == null) {
            return;
        }
        Text text = switch (denial) {
            case COOLDOWN -> Text.translatable(key, RiftSessionRules.secondsCeil(session.cooldownRemainingTicks()));
            case NOT_ENOUGH_MANA -> Text.translatable(key, user.entryFee());
            default -> Text.translatable(key);
        };
        player.sendMessage(text, true);
    }

    private static List<Integer> numbers(List<RiftGateRecord> gates) {
        List<Integer> numbers = new ArrayList<>(gates.size());
        for (RiftGateRecord gate : gates) {
            numbers.add(gate.number());
        }
        return numbers;
    }
}
