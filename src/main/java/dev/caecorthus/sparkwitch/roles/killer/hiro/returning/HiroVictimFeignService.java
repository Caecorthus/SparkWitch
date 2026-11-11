package dev.caecorthus.sparkwitch.roles.killer.hiro.returning;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkStrengthCoronerCompat;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeKillAttribution;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerInventoryRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowForm;
import dev.caecorthus.sparkwitch.roles.killer.hiro.HiroRules;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The fake death of 二阶堂希罗's victims (owner rules 2026-11-11), server only. A kill credited to Hiro inside her
 * 死亡回归 window that really commits (every protection of the victim already had its say) is replaced at the commit
 * point of Wathe's {@code killPlayer} ({@code mixin/hiro/GameFunctionsHiroReturnMixin}): the victim stays alive in
 * Wathe (never marked dead, so every win check still counts it), goes into spectator mode with its camera on a fake
 * corpse that carries the real death reason and lies where the real corpse would, keeps its inventory, cannot act or
 * speak and cannot be killed again. Each victim has its own {@link HiroRules#VICTIM_FEIGN_TICKS} timer:
 * <ul>
 *   <li>Hiro is attacked while it runs ({@link HiroReturnService}) → the victim gets up at the place it fell;</li>
 *   <li>the timer ends → the victim is restored and the kill is replayed for real (killer Hiro, the original reason
 *   and {@code spawnBody}, forced) inside {@link #settlingVictim}, so rewards, records and {@code KillPlayer.AFTER}
 *   happen then and only then. A protection that still stops the replay leaves the victim alive.</li>
 *   <li>the victim disconnects → it dies for real at once the same way; the round ends or the player is reset → the
 *   state is just dropped.</li>
 * </ul>
 * The victim is told nothing while the death is undecided. Modelled on the Charon's and the Fiend's feigns without
 * touching either. Every other death returns from {@link #onDeathCommit} at once.
 * 二阶堂希罗受害者的假死（所有者 2026-11-11 规则），仅服务端。死亡回归窗口内记在希罗头上、且真正落定的击杀（受害者的所有
 * 保护都已结算）会在 Wathe {@code killPlayer} 的提交点被替换（{@code mixin/hiro/GameFunctionsHiroReturnMixin}）：受害者在
 * Wathe 中仍然存活（从不标记死亡，因此所有胜负判定仍把它算作存活），进入旁观模式、视角锁在一具假尸体上——假尸体带有真实
 * 死因并躺在真尸体本应出现的位置——保留物品栏，无法行动或说话，也不会再次被杀。每名受害者各自计时
 * {@link HiroRules#VICTIM_FEIGN_TICKS}：计时期间希罗遭到攻击（{@link HiroReturnService}）→ 受害者在倒下处起身；计时结束 →
 * 受害者被还原，并在 {@link #settlingVictim} 作用域内以强制方式重放这次击杀（凶手为希罗，沿用原死因与
 * {@code spawnBody}），奖励、记录与 {@code KillPlayer.AFTER} 此时才发生；若仍有保护拦下重放，受害者就此存活。受害者断线 →
 * 以同样方式立即真正死亡；回合结束或玩家被重置 → 仅清除状态。死亡未定期间不向受害者提示任何信息。形态参照卡戎与魔人的假死，
 * 但不改动二者。其他死亡在 {@link #onDeathCommit} 中立即返回。
 */
public final class HiroVictimFeignService {
    static final String REVIVED_KEY = "message.sparkwitch.nikaido_hiro.return.revived";
    private static final double GROUND_SNAP_DEPTH = 3.0;
    /** A corpse farther than this from where it was laid was moved; the camera leaves it. / 尸体偏离铺设位置超过此值即视为被移动，视角离开它。 */
    private static final double CORPSE_DRIFT_HORIZONTAL = 0.75;
    /** A corpse laid mid-air falls to the floor; a longer drop is not followed. / 半空铺设的尸体会落到地面；更长的下落不再跟随。 */
    private static final double CORPSE_DRIFT_VERTICAL = 4.0;
    /** A spectator flies through walls, so even a small drift is pulled back. / 旁观者会穿墙，因此轻微偏移也立即拉回。 */
    private static final double PINNED_DRIFT = 0.05;
    /** Victim UUID → feign. Written on the server thread; the voice thread only reads. / 受害者 UUID → 假死；仅服务端线程写入，语音线程只读。 */
    private static final Map<UUID, Feign> FEIGNS = new ConcurrentHashMap<>();
    /** Fake corpses that loaded after their feign ended; server thread only. / 假死结束后才加载的假尸体；仅服务端线程。 */
    private static final Set<UUID> STALE_BODIES = new LinkedHashSet<>();
    /**
     * Scope flag of the real-death replay: this victim's kill must reach Wathe's own commit instead of feigning again.
     * Server thread only.
     * 真正死亡重放的作用域标记：该受害者的击杀必须进入 Wathe 自身的提交，而不是再次假死。仅服务端线程。
     */
    private static @Nullable UUID settlingVictim;
    private static boolean registered;

    private HiroVictimFeignService() {
    }

    /**
     * One victim's fake death. {@code anchor*} is where the victim fell (snapped to the floor) with the facing to
     * restore; {@code body*} is where the fake corpse was laid.
     * 一名受害者的假死。{@code anchor*} 为受害者倒下的位置（贴合地面）及需恢复的朝向；{@code body*} 为假尸体的铺设位置。
     */
    record Feign(UUID victim, UUID hiro, Identifier deathReason, boolean spawnBody, long endTick, UUID bodyId,
                 RegistryKey<World> dimension, double anchorX, double anchorY, double anchorZ, float anchorYaw,
                 float anchorPitch, double bodyX, double bodyY, double bodyZ, GameMode previousMode) {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(HiroVictimFeignService::tick);
        KillPlayer.AFTER.register(HiroVictimFeignService::afterKill);
        ResetPlayer.EVENT.register(HiroVictimFeignService::onReset);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onDisconnect(server, handler.getPlayer()));
        ServerEntityEvents.ENTITY_LOAD.register(HiroVictimFeignService::onEntityLoad);
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clearAll());
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clearAll());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearAll());
    }

    // ---- Queries / 查询 ----

    /** Server truth; always false on a client (the state is never synced). / 服务端判定；客户端一律为 false（该状态从不同步）。 */
    public static boolean isFeigned(@Nullable PlayerEntity player) {
        return player != null && !player.getWorld().isClient() && FEIGNS.containsKey(player.getUuid());
    }

    /** Server truth by UUID; offline or unknown players are never fake-dead. / 按 UUID 判断；离线或未知玩家一律视为未假死。 */
    public static boolean isFeigned(@Nullable MinecraftServer server, @Nullable UUID player) {
        return server != null && player != null && FEIGNS.containsKey(player);
    }

    /**
     * Whether a camera change must be refused: a fake-dead victim may only watch from its own fake corpse or from
     * itself, never possess another entity's view (vanilla spectator attack) and drift onto it.
     * 是否必须拒绝视角切换：假死的受害者只能从自己的假尸体或自身观看，绝不能附身其他实体的视角（原版旁观攻击）并被带到其位置。
     */
    public static boolean vetoesCamera(ServerPlayerEntity player, @Nullable Entity camera) {
        Feign feign = FEIGNS.get(player.getUuid());
        return feign != null && camera != null && camera != player && !camera.getUuid().equals(feign.bodyId());
    }

    /**
     * HEAD of Wathe's {@code killPlayer}: a fake-dead victim cannot be killed again, forced kills included; only the
     * service's own replay of its real death passes.
     * Wathe {@code killPlayer} 的 HEAD：假死的受害者不会再次被杀，强制击杀同样如此；只有服务自身对其真正死亡的重放放行。
     */
    public static boolean blocksKill(@Nullable ServerPlayerEntity victim) {
        return victim != null && FEIGNS.containsKey(victim.getUuid()) && !victim.getUuid().equals(settlingVictim);
    }

    /** How many of {@code hiro}'s victims are fake-dead right now. / {@code hiro} 此刻有多少名受害者处于假死。 */
    public static int victimCount(@Nullable UUID hiro) {
        if (hiro == null || FEIGNS.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Feign feign : FEIGNS.values()) {
            if (feign.hiro().equals(hiro)) {
                count++;
            }
        }
        return count;
    }

    // ---- Start / 开始 ----

    /**
     * Commit point of Wathe's {@code killPlayer} (the alive branch's {@code changeGameMode(SPECTATOR)} call), after
     * every other interceptor there: returns true when the death became a fake death and must be cancelled. Credited to
     * Hiro means the {@code killer} argument is her or the Judge attribution of this attempt names her (grenade,
     * poison…). Nothing is refunded: a protection consumed earlier in this kill stays consumed.
     * Wathe {@code killPlayer} 的提交点（存活分支的 {@code changeGameMode(SPECTATOR)} 调用），在该处其他拦截器之后：返回 true
     * 表示这次死亡已变成假死、必须取消。“记在希罗头上”指 {@code killer} 参数是她，或本次尝试的法官归因指向她（手雷、毒药……）。
     * 不退还任何东西：本次击杀中先前被消耗的保护保持已消耗。
     */
    public static boolean onDeathCommit(ServerPlayerEntity victim, boolean spawnBody,
                                        @Nullable ServerPlayerEntity killer, @Nullable Identifier deathReason) {
        if (victim == null || victim.getUuid().equals(settlingVictim)) {
            return false;
        }
        ServerWorld world = victim.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
            return false;
        }
        ServerPlayerEntity hiro = creditedHiro(victim, killer, game);
        if (!HiroReturnRules.feignsDeath(false, hiro != null, hiro == victim, spawnBody, deathReason,
                hiro != null && isHoldable(victim))) {
            return false;
        }
        try {
            return start(victim, hiro, spawnBody, deathReason, game);
        } catch (RuntimeException exception) {
            // A failing feign never cancels the kill silently: the death commits for real.
            // 假死出错绝不会静默取消击杀：这次死亡照常真正提交。
            SparkWitch.LOGGER.error("Hiro victim feign failed for {}; the death commits.", victim.getUuid(), exception);
            return false;
        }
    }

    /**
     * The living raw-role Hiro with a running window this kill is credited to, or null.
     * 本次击杀所归属的、窗口进行中的存活真实身份希罗；没有则为 null。
     */
    private static @Nullable ServerPlayerEntity creditedHiro(ServerPlayerEntity victim,
                                                            @Nullable ServerPlayerEntity killer,
                                                            GameWorldComponent game) {
        if (hasRunningWindow(killer, game)) {
            return killer;
        }
        UUID responsible = JudgeKillAttribution.attemptResponsible(victim);
        MinecraftServer server = victim.getServer();
        ServerPlayerEntity attributed = responsible == null || server == null
                ? null
                : server.getPlayerManager().getPlayer(responsible);
        return hasRunningWindow(attributed, game) ? attributed : null;
    }

    private static boolean hasRunningWindow(@Nullable ServerPlayerEntity player, GameWorldComponent game) {
        return player != null
                && HiroRules.isHiro(game.getRole(player))
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && HiroReturnComponent.KEY.get(player).isWindowActive(player.getWorld().getTime());
    }

    private static boolean isHoldable(ServerPlayerEntity victim) {
        return HiroReturnRules.victimHoldable(
                GameFunctions.isPlayerAliveAndSurvival(victim),
                FEIGNS.containsKey(victim.getUuid()),
                NoellesTaotieSeekerBridge.isSwallowed(victim),
                RiftSessionService.isInside(victim) || KidnapperControlComponent.KEY.get(victim).isControlled()
                        || BlackRavenCrowForm.isInCrowForm(victim) || SeekerInventoryRules.isInSession(victim));
    }

    private static boolean start(ServerPlayerEntity victim, ServerPlayerEntity hiro, boolean spawnBody,
                                 @Nullable Identifier deathReason, GameWorldComponent game) {
        ServerWorld world = victim.getServerWorld();
        GameMode previousMode = victim.interactionManager.getGameMode();
        // What Wathe's spectator switch does before it lays the corpse, plus a dropped charge and a closed screen.
        // 与 Wathe 切换旁观后、铺设尸体前的处理一致，并放下蓄力中的物品、关闭打开的界面。
        victim.stopRiding();
        if (victim.isSleeping()) {
            victim.wakeUp();
        }
        victim.clearActiveItem();
        victim.closeHandledScreen();
        Identifier reason = deathReason == null ? dev.doctor4t.wathe.game.GameConstants.DeathReasons.GENERIC : deathReason;
        PlayerBodyEntity body = spawnCorpse(world, victim, reason, game.getRole(victim));
        if (body == null) {
            return false;
        }
        Vec3d anchor = groundAnchor(world, victim);
        Feign feign = new Feign(victim.getUuid(), hiro.getUuid(), reason, spawnBody,
                world.getTime() + HiroRules.VICTIM_FEIGN_TICKS, body.getUuid(), world.getRegistryKey(),
                anchor.x, anchor.y, anchor.z, victim.getYaw(), victim.getPitch(),
                body.getX(), body.getY(), body.getZ(), previousMode);
        FEIGNS.put(feign.victim(), feign);
        victim.setVelocity(Vec3d.ZERO);
        victim.velocityModified = true;
        victim.fallDistance = 0.0F;
        hold(victim, feign);
        syncCount(world.getServer(), feign.hiro());
        return true;
    }

    /**
     * Where the victim gets up or dies for real: its feet when it stands on the ground, otherwise the floor right
     * below it (killed mid-jump), so nobody is restored in mid-air. With no floor within {@link #GROUND_SNAP_DEPTH}
     * blocks the anchor stays where the victim is.
     * 受害者起身或真正死亡的位置：站在地面时为其脚下，否则为其正下方的地面（跳跃中被杀），因此不会有人在半空被还原。
     * {@link #GROUND_SNAP_DEPTH} 格内没有地面时锚点保持在受害者所在处。
     */
    private static Vec3d groundAnchor(ServerWorld world, ServerPlayerEntity victim) {
        Vec3d feet = victim.getPos();
        if (victim.isOnGround()) {
            return feet;
        }
        BlockHitResult hit = world.raycast(new RaycastContext(feet.add(0.0, 0.1, 0.0),
                feet.subtract(0.0, GROUND_SNAP_DEPTH, 0.0), RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, victim));
        return hit.getType() == HitResult.Type.BLOCK ? new Vec3d(feet.x, hit.getPos().y, feet.z) : feet;
    }

    /**
     * The fake corpse is laid exactly as Wathe's {@code killPlayer} lays the real one (one block ahead of the victim at
     * its height, head yaw, the real death reason, the death tick and the live role {@code setPlayerUuid} captures),
     * so nobody can tell it from the corpse this death would have left; the real corpse of a later replay appears on
     * the same spot. The fake mark stays server-only. SparkStrength's Coroner snapshot gets the same role a real
     * corpse would carry (fails closed without SparkStrength); its Coroner already ignores a corpse whose owner is
     * alive in Wathe.
     * 假尸体的铺设方式与 Wathe {@code killPlayer} 铺设真尸体完全一致（受害者前方一格、同高度、头部朝向、真实死因、死亡刻，以及
     * {@code setPlayerUuid} 捕获的实时身份），因此无人能把它与这次死亡本应留下的尸体区分开；之后重放产生的真尸体会出现在同一
     * 位置。假尸标记只留在服务端。SparkStrength 验尸官快照写入与真尸体相同的身份（缺少 SparkStrength 时失败即关闭）；其验尸官
     * 本就忽略主人在 Wathe 中仍存活的尸体。
     */
    private static @Nullable PlayerBodyEntity spawnCorpse(ServerWorld world, ServerPlayerEntity victim,
                                                          Identifier reason, @Nullable Role role) {
        PlayerBodyEntity body = WatheEntities.PLAYER_BODY.create(world);
        if (body == null) {
            return null;
        }
        body.setPlayerUuid(victim.getUuid());
        body.setDeathReason(reason);
        body.setDeathGameTime(world.getTime());
        Vec3d spawnPos = victim.getPos().add(victim.getRotationVector().normalize());
        body.refreshPositionAndAngles(spawnPos.getX(), victim.getY(), spawnPos.getZ(), victim.getHeadYaw(), 0.0F);
        body.setYaw(victim.getHeadYaw());
        body.setHeadYaw(victim.getHeadYaw());
        if (!world.spawnEntity(body)) {
            return null;
        }
        // Marked after the spawn: the load callback of an already marked corpse without a feign would queue it as stale.
        // 生成之后再标记：已标记但尚无假死记录的尸体会在加载回调中被当作残留尸体排队移除。
        HiroFakeBodies.mark(body);
        if (role != null) {
            SparkStrengthCoronerCompat.recordBodyRole(body, role.identifier());
        }
        return body;
    }

    // ---- Tick / 每刻 ----

    private static void tick(MinecraftServer server) {
        discardStaleBodies(server);
        if (FEIGNS.isEmpty()) {
            return;
        }
        for (Feign feign : List.copyOf(FEIGNS.values())) {
            try {
                tickFeign(server, feign);
            } catch (RuntimeException exception) {
                SparkWitch.LOGGER.error("Hiro victim feign tick failed for {}.", feign.victim(), exception);
            }
        }
    }

    private static void tickFeign(MinecraftServer server, Feign feign) {
        ServerPlayerEntity victim = server.getPlayerManager().getPlayer(feign.victim());
        if (victim == null) {
            // The disconnect hook settles a leaving victim; this only drops a record nothing can act on.
            // 离线的受害者由断线钩子结算；这里只清除已无法处理的记录。
            drop(server, feign);
            return;
        }
        ServerWorld world = victim.getServerWorld();
        boolean alive = GameFunctions.isPlayerPlayingAndAlive(victim);
        if (GameWorldComponent.KEY.get(world).getGameStatus() != GameWorldComponent.GameStatus.ACTIVE || !alive) {
            // Round over: the victim was alive for the result, so it just gets up; one Wathe already counts dead only
            // has its camera and corpse cleaned up.
            // 回合结束：受害者在结算时算作存活，因此直接起身；Wathe 已判定死亡的只清理视角与尸体。
            if (alive) {
                revive(server, feign, false);
            } else {
                drop(server, feign);
                victim.setCameraEntity(victim);
            }
            return;
        }
        if (HiroReturnRules.victimExpired(world.getTime(), feign.endTick())) {
            settle(server, feign, victim);
            return;
        }
        hold(victim, feign);
    }

    /**
     * Every feigning tick: spectator, camera on the corpse, still. The vanilla tick moves a spectating player onto its
     * camera entity, so the camera stays on the corpse only while the corpse lies where it was laid (a fall to the
     * floor is followed); once it is gone or moved, the victim watches from itself, held at its anchor.
     * 假死期间每刻执行：旁观模式，视角锁在尸体上，静止。原版逻辑会把旁观中的玩家移到其视角实体处，因此只有尸体仍躺在铺设位置
     * 时视角才留在尸体上（落到地面的过程会跟随）；尸体消失或被移动后，受害者改为以自身视角观看并固定在锚点。
     */
    private static void hold(ServerPlayerEntity victim, Feign feign) {
        if (victim.interactionManager.getGameMode() != GameMode.SPECTATOR) {
            victim.changeGameMode(GameMode.SPECTATOR);
        }
        Entity body = findBody(victim.getServer(), feign.bodyId());
        Entity camera = body != null && body.isAlive() && !corpseDrifted(body, feign) ? body : victim;
        if (victim.getCameraEntity() != camera) {
            victim.setCameraEntity(camera);
        }
        victim.setVelocity(Vec3d.ZERO);
        if (camera == victim && pinnedDrifted(victim, feign)) {
            teleportToAnchor(victim, feign);
        }
    }

    // ---- End / 结束 ----

    /**
     * Hiro was attacked: every victim of hers that is fake-dead now gets up (owner rule 5), with a short private
     * line. Returns how many did.
     * 希罗遭到攻击：她此刻所有假死的受害者起身（所有者规则 5），并收到一句简短的私人提示。返回起身人数。
     */
    static int reviveVictimsOf(@Nullable MinecraftServer server, @Nullable UUID hiro) {
        if (server == null || hiro == null || FEIGNS.isEmpty()) {
            return 0;
        }
        int revived = 0;
        for (Feign feign : List.copyOf(FEIGNS.values())) {
            if (feign.hiro().equals(hiro) && FEIGNS.containsKey(feign.victim())) {
                revive(server, feign, true);
                revived++;
            }
        }
        return revived;
    }

    private static void revive(MinecraftServer server, Feign feign, boolean notify) {
        FEIGNS.remove(feign.victim());
        ServerPlayerEntity victim = server.getPlayerManager().getPlayer(feign.victim());
        if (victim == null) {
            discardBody(server, feign.bodyId());
        } else {
            restore(victim, feign);
            if (notify) {
                victim.sendMessage(Text.translatable(REVIVED_KEY), true);
            }
        }
        syncCount(server, feign.hiro());
    }

    /**
     * The victim's own timer ran out (or it is leaving): it is restored at its anchor as a normal player, the fake
     * corpse is discarded, and the original kill is replayed at once, forced, with Hiro as the killer so
     * {@code KillPlayer.AFTER}, the kill rewards, the derringer reload and the death record see her. An offline Hiro
     * cannot be passed as the killer; the replay then runs inside the Judge attribution scope of her UUID, so
     * {@code JudgeKillAttribution.committedResponsible} still names her and nobody else is credited. If a late
     * protection still stops the replay, the victim simply lives.
     * 受害者自己的计时结束（或其正在离线）：在锚点还原为普通玩家，移除假尸体，并立即以强制方式重放原击杀，凶手为希罗，使
     * {@code KillPlayer.AFTER}、击杀奖励、德林加装填与死亡记录都记在她头上。希罗离线时无法作为凶手传入；此时重放在以她的
     * UUID 建立的法官归因作用域内运行，{@code JudgeKillAttribution.committedResponsible} 仍指向她，也不会记到别人头上。
     * 若仍有保护拦下重放，受害者就此存活。
     */
    private static void settle(MinecraftServer server, Feign feign, ServerPlayerEntity victim) {
        FEIGNS.remove(feign.victim());
        restore(victim, feign);
        syncCount(server, feign.hiro());
        ServerPlayerEntity hiro = server.getPlayerManager().getPlayer(feign.hiro());
        Runnable kill = () -> GameFunctions.killPlayer(victim, feign.spawnBody(), hiro, feign.deathReason(), true);
        UUID previous = settlingVictim;
        settlingVictim = feign.victim();
        try {
            if (hiro == null) {
                JudgeKillAttribution.runWith(victim.getServerWorld(), feign.hiro(), kill);
            } else {
                kill.run();
            }
        } finally {
            settlingVictim = previous;
        }
    }

    /** Back to a normal player at the anchor; the fake corpse is discarded. / 在锚点还原为普通玩家；移除假尸体。 */
    private static void restore(ServerPlayerEntity victim, Feign feign) {
        victim.setCameraEntity(victim);
        victim.changeGameMode(feign.previousMode());
        teleportToAnchor(victim, feign);
        victim.setVelocity(Vec3d.ZERO);
        victim.velocityModified = true;
        victim.fallDistance = 0.0F;
        discardBody(victim.getServer(), feign.bodyId());
    }

    /** Forgets a feign whose victim will not be restored. / 清除一条不会再还原受害者的假死记录。 */
    private static void drop(MinecraftServer server, Feign feign) {
        FEIGNS.remove(feign.victim());
        discardBody(server, feign.bodyId());
        syncCount(server, feign.hiro());
    }

    /**
     * Safety net: a fake-dead victim that really died through a path the HEAD guard does not see only needs its camera
     * and corpse cleaned up; Wathe's death already made it a spectator.
     * 兜底：经由 HEAD 守卫看不到的途径真正死亡的假死受害者，只需清理视角与尸体；Wathe 的死亡流程已让它成为旁观者。
     */
    private static void afterKill(@Nullable ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer,
                                  @Nullable Identifier deathReason) {
        Feign feign = victim == null ? null : FEIGNS.get(victim.getUuid());
        if (feign == null) {
            return;
        }
        drop(victim.getServer(), feign);
        victim.setCameraEntity(victim);
    }

    /** Round boundary: Wathe already reset the game mode and position. / 回合边界：Wathe 已重置游戏模式与位置。 */
    private static void onReset(ServerPlayerEntity player) {
        Feign feign = player == null ? null : FEIGNS.get(player.getUuid());
        if (feign == null) {
            return;
        }
        drop(player.getServer(), feign);
        player.setCameraEntity(player);
    }

    /**
     * Leaving mid-feign (owner rule 5): the victim dies for real now, credited to Hiro with the original reason, before
     * Wathe's own deferred {@code wathe:escaped} kill runs (which then finds a dead player). Restoring first also keeps
     * the saved player data from holding a living player in spectator mode if the replay is refused. Outside an ACTIVE
     * round the victim only gets up.
     * 假死中途离线（所有者规则 5）：受害者立即真正死亡，记在希罗头上并沿用原死因，早于 Wathe 自身延后执行的
     * {@code wathe:escaped} 击杀（届时该玩家已死亡）。先还原还能保证重放被拒绝时，保存的玩家数据不会让活着的玩家停留在旁观
     * 模式。不在 ACTIVE 回合时受害者只是起身。
     */
    private static void onDisconnect(MinecraftServer server, @Nullable ServerPlayerEntity player) {
        Feign feign = player == null ? null : FEIGNS.get(player.getUuid());
        if (feign == null) {
            return;
        }
        boolean active = GameWorldComponent.KEY.get(player.getServerWorld()).getGameStatus()
                == GameWorldComponent.GameStatus.ACTIVE;
        if (active && GameFunctions.isPlayerPlayingAndAlive(player)) {
            settle(server, feign, player);
        } else {
            revive(server, feign, false);
        }
    }

    /** Round boundary or server stop; Wathe discards every corpse at game stop itself. / 回合边界或服务器停止；Wathe 在对局停止时自行移除所有尸体。 */
    private static void clearAll() {
        FEIGNS.clear();
        STALE_BODIES.clear();
        HiroFakeBodies.clear();
        settlingVictim = null;
    }

    /** HUD data: the owner-synced count on Hiro's component. / HUD 数据：希罗组件上只同步给本人的计数。 */
    private static void syncCount(@Nullable MinecraftServer server, UUID hiro) {
        ServerPlayerEntity player = server == null ? null : server.getPlayerManager().getPlayer(hiro);
        if (player != null) {
            HiroReturnComponent.KEY.get(player).setFeignedVictimCount(victimCount(hiro));
        }
    }

    // ---- Corpse / 尸体 ----

    private static boolean corpseDrifted(Entity body, Feign feign) {
        double dx = body.getX() - feign.bodyX();
        double dz = body.getZ() - feign.bodyZ();
        return dx * dx + dz * dz > CORPSE_DRIFT_HORIZONTAL * CORPSE_DRIFT_HORIZONTAL
                || Math.abs(body.getY() - feign.bodyY()) > CORPSE_DRIFT_VERTICAL;
    }

    private static boolean pinnedDrifted(Entity victim, Feign feign) {
        double dx = victim.getX() - feign.anchorX();
        double dz = victim.getZ() - feign.anchorZ();
        return dx * dx + dz * dz > PINNED_DRIFT * PINNED_DRIFT
                || Math.abs(victim.getY() - feign.anchorY()) > PINNED_DRIFT;
    }

    /** Back at the anchor, facing the way the victim faced when it fell. / 回到锚点，并恢复倒下时的朝向。 */
    private static void teleportToAnchor(ServerPlayerEntity victim, Feign feign) {
        MinecraftServer server = victim.getServer();
        ServerWorld world = server == null ? null : server.getWorld(feign.dimension());
        victim.teleport(world == null ? victim.getServerWorld() : world, feign.anchorX(), feign.anchorY(),
                feign.anchorZ(), Set.of(), feign.anchorYaw(), feign.anchorPitch());
    }

    private static @Nullable Entity findBody(@Nullable MinecraftServer server, @Nullable UUID bodyId) {
        if (server == null || bodyId == null) {
            return null;
        }
        for (ServerWorld world : server.getWorlds()) {
            Entity entity = world.getEntity(bodyId);
            if (entity instanceof PlayerBodyEntity) {
                return entity;
            }
        }
        return null;
    }

    /**
     * Discards the corpse when it is loaded. One that cannot be found (its chunk unloaded) keeps its fake mark until
     * the round boundary, so {@link #onEntityLoad} discards it if it ever loads again instead of leaving an unmarked
     * corpse of a living player.
     * 尸体已加载时直接移除。找不到的尸体（所在区块已卸载）保留假尸标记直到回合边界，若之后再次加载则由 {@link #onEntityLoad}
     * 移除，绝不留下一具未标记的活人尸体。
     */
    private static void discardBody(@Nullable MinecraftServer server, @Nullable UUID bodyId) {
        Entity body = findBody(server, bodyId);
        if (body != null) {
            if (!body.isRemoved()) {
                body.discard();
            }
            HiroFakeBodies.forget(bodyId);
        }
    }

    /**
     * A marked corpse that loads while no victim is fake-dead on it is queued and discarded at the end of the tick
     * (never from inside the load callback).
     * 加载时已没有受害者在其上假死的带标记尸体会被排队，并在本刻结束时移除（绝不在加载回调内部移除）。
     */
    private static void onEntityLoad(Entity entity, ServerWorld world) {
        if (!(entity instanceof PlayerBodyEntity body) || !HiroFakeBodies.isFake(body)) {
            return;
        }
        Feign feign = FEIGNS.get(body.getPlayerUuid());
        if (feign == null || !body.getUuid().equals(feign.bodyId())) {
            STALE_BODIES.add(body.getUuid());
        }
    }

    private static void discardStaleBodies(MinecraftServer server) {
        if (STALE_BODIES.isEmpty()) {
            return;
        }
        for (UUID bodyId : List.copyOf(STALE_BODIES)) {
            STALE_BODIES.remove(bodyId);
            discardBody(server, bodyId);
        }
    }
}
