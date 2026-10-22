package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkStrengthCoronerCompat;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendFeignRules.Trigger;
import dev.caecorthus.sparkwitch.roles.special.wraith.conversion.WraithBodyRoleAccess;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import dev.doctor4t.wathe.api.WatheRoles;
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
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Feign death (owner request 2026-10-16), server only. A dormant Fiend fakes death for 5 s once 60 s have passed since
 * the round start or its last revival; a kill the dormant immunity blocks ({@link #onBlockedKill}, any reason) starts
 * one at once that lasts 20 s. A feigning Fiend leaves a Wathe fake corpse (a random common reason, or the blocked
 * kill's own reason) and goes into spectator mode with its camera on that corpse, the SparkTraits Depression / Jester
 * pattern: it stays playing and alive in Wathe (never marked dead), so it keeps its dormant immunity, its role and
 * every win exclusion, and the vanilla spectator rule hides it from every other client. It cannot move, act, buy or
 * speak until it revives at its anchor and the corpse is discarded. If a Vulture eats that corpse during the feign
 * ({@link #onBodyDevoured}), the Fiend dies at once to {@link SparkWitchDeathReasons#FIEND_DEVOURED}. Feigning stops
 * for good once the Fiend Moment starts (the Fiend is no longer dormant). Every other player is untouched: each entry
 * returns at once unless the player is a dormant Fiend or already feigning.
 * 假死（所有者 2026-10-16 需求），仅服务端。休眠魔人在开局或上次复活后满 60 秒时假死 5 秒；被休眠免疫挡下的击杀
 * （{@link #onBlockedKill}，任意死因）会立即触发一次持续 20 秒的假死。假死的魔人留下一具 Wathe 假尸体（随机常见死因，
 * 或被挡下击杀自身的死因），并进入旁观模式、视角锁定在该尸体上，沿用 SparkTraits 抑郁 / 小丑的做法：它在 Wathe 中仍在
 * 参与且存活（从不标记为死亡），因此保留休眠免疫、职业与所有胜负排除，原版旁观规则会让其他客户端都看不到它。它在原地
 * 复活、假尸体被移除之前无法移动、行动、购买或说话。若秃鹫在假死期间吃掉这具尸体（{@link #onBodyDevoured}），魔人立即以
 * {@link SparkWitchDeathReasons#FIEND_DEVOURED} 死亡。魔人时刻开始后不再假死（魔人不再休眠）。其他玩家不受影响：除休眠魔人
 * 或正在假死的玩家外，每个入口都会立即返回。
 */
public final class FiendFeignService {
    /** A corpse farther than this from the anchor was moved; the camera leaves it. / 尸体离锚点超过此距离即视为被移动，视角离开它。 */
    private static final double CORPSE_DRIFT = 0.5;
    /** A camera-less Fiend is pulled back past this. / 失去尸体视角的魔人偏移超过此距离即被拉回。 */
    private static final double PINNED_DRIFT = 0.05;
    private static final double GROUND_SNAP_DEPTH = 3.0;
    /** Fake corpses that loaded after their feign ended; server thread only. / 假死结束后才加载的假尸体；仅服务端线程。 */
    private static final Set<UUID> STALE_BODIES = new LinkedHashSet<>();
    private static boolean registered;

    private FiendFeignService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(FiendFeignService::tick);
        KillPlayer.AFTER.register(FiendFeignService::afterKill);
        ResetPlayer.EVENT.register(FiendFeignService::onReset);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onDisconnect(handler.getPlayer()));
        ServerEntityEvents.ENTITY_LOAD.register(FiendFeignService::onEntityLoad);
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clearBodies());
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clearBodies());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearBodies());
        // SparkTraits reads terminal reasons at kill time, so a Last Stand never turns the devoured death into a
        // revive; registering at server start is early enough and idempotent.
        // SparkTraits 在击杀时读取终结原因，背水一战因此不会把被吞食的死亡变成复活；服务端启动时注册即足够早且幂等。
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
                SparkTraitsKillerBridge.registerTerminalDeathReason(SparkWitchDeathReasons.FIEND_DEVOURED));
    }

    /** Server truth for a feigning player. / 服务端判断玩家是否正在假死。 */
    public static boolean isFeigning(@Nullable ServerPlayerEntity player) {
        return player != null && FiendFeignComponent.get(player).state().isFeigning();
    }

    /** Server truth by UUID; offline or unknown players are never feigning. / 按 UUID 判断；离线或未知玩家一律视为未假死。 */
    public static boolean isFeigning(@Nullable MinecraftServer server, @Nullable UUID player) {
        return server != null && player != null && isFeigning(server.getPlayerManager().getPlayer(player));
    }

    /**
     * Whether a camera change must be refused: a feigning Fiend may only watch from its own fake corpse or from itself,
     * never possess another entity's view (vanilla spectator attack) and drift onto it.
     * 是否必须拒绝视角切换：假死的魔人只能从自己的假尸体或自身观看，绝不能附身其他实体的视角（原版旁观攻击）并被带到其位置。
     */
    public static boolean vetoesCamera(ServerPlayerEntity fiend, @Nullable Entity camera) {
        FiendFeignState state = FiendFeignComponent.get(fiend).state();
        return state.isFeigning() && camera != null && camera != fiend && !camera.getUuid().equals(state.bodyId());
    }

    /**
     * Called by {@link FiendImmunityService} for every kill it cancels: a lethal hit brings the feign forward and makes
     * it last 20 s. A Fiend already feigning, or one busy elsewhere, is left as it is.
     * 由 {@link FiendImmunityService} 在每次取消击杀时调用：致命命中让假死提前到来并持续 20 秒。已在假死或忙于其他状态的魔人
     * 保持原状。
     */
    static void onBlockedKill(ServerPlayerEntity fiend, @Nullable Identifier deathReason) {
        if (!canStartNow(fiend)) {
            return;
        }
        FiendFeignState state = FiendFeignComponent.get(fiend).state();
        if (state.isLaunched()) {
            // Its feign already waits for the landing. / 其假死已在等待落地。
            return;
        }
        if (FiendFeignRules.isLaunched(fiend.velocityModified, fiend.getVelocity().lengthSquared())) {
            long now = fiend.getServerWorld().getTime();
            ensureScheduled(state, FiendMatch.currentId(), now);
            state.launch(now, deathReason);
            return;
        }
        start(fiend, Trigger.LETHAL_HIT, deathReason);
    }

    /**
     * Gun-hit Speed III: given now, or at the revival when the Fiend is feigning (it would expire unseen otherwise).
     * 枪击的速度 III：立即给予；若魔人正在假死则在复活时给予（否则会在旁观期间白白耗尽）。
     */
    static void grantGunSpeed(ServerPlayerEntity fiend) {
        FiendFeignState state = FiendFeignComponent.get(fiend).state();
        if (state.isFeigning() || state.isLaunched()) {
            state.deferSpeed();
            return;
        }
        addGunSpeed(fiend);
    }

    /**
     * NoellesRoles Vulture eat seam ({@code mixin/fiend/NoellesVultureFiendFakeBodyMixin}), after the eat completed: if
     * the eaten corpse is the fake corpse of a Fiend that is feigning on it, that Fiend dies for real, credited to the
     * Vulture, without a second corpse. Any other body returns at once.
     * NoellesRoles 秃鹫进食接缝（{@code mixin/fiend/NoellesVultureFiendFakeBodyMixin}），在进食完成后调用：若被吃掉的是
     * 正在其上假死的魔人的假尸体，该魔人真正死亡，记为秃鹫所杀，且不再留下尸体。其他尸体立即返回。
     */
    public static void onBodyDevoured(@Nullable PlayerBodyEntity body, @Nullable ServerPlayerEntity vulture) {
        if (body == null || !FiendFakeBodies.isFake(body)) {
            return;
        }
        FiendFakeBodies.forget(body.getUuid());
        MinecraftServer server = body.getServer();
        ServerPlayerEntity fiend = server == null ? null : server.getPlayerManager().getPlayer(body.getPlayerUuid());
        if (fiend == null) {
            return;
        }
        FiendFeignComponent component = FiendFeignComponent.get(fiend);
        FiendFeignState state = component.state();
        if (!state.isFeigning() || !body.getUuid().equals(state.bodyId())) {
            return;
        }
        fiend.setCameraEntity(fiend);
        GameFunctions.killPlayer(fiend, false, vulture, SparkWitchDeathReasons.FIEND_DEVOURED, true);
        if (state.isFeigning()) {
            // Another guard refused the death (afterKill never ran): get up as from a normal feign.
            // 其他守卫拒绝了这次死亡（afterKill 未执行）：按普通假死结束那样起身。
            revive(fiend, component, fiend.getServerWorld().getTime(), true);
        }
    }

    /** Round boundary or server stop; Wathe discards every corpse at game stop itself. / 回合边界或服务器停止；Wathe 在对局停止时自行移除所有尸体。 */
    private static void clearBodies() {
        FiendFakeBodies.clear();
        STALE_BODIES.clear();
    }

    private static void tick(MinecraftServer server) {
        discardStaleBodies(server);
        UUID match = FiendMatch.currentId();
        for (ServerWorld world : server.getWorlds()) {
            // An empty world has nothing to tick: skip its defensive copy. / 空世界无可推进：跳过其防御性拷贝。
            if (world.getPlayers().isEmpty()) {
                continue;
            }
            boolean active = GameWorldComponent.KEY.get(world).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
            long now = world.getTime();
            for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
                try {
                    tickPlayer(player, active, match, now);
                } catch (RuntimeException exception) {
                    SparkWitch.LOGGER.error("Fiend feign tick failed for {}.", player.getUuid(), exception);
                }
            }
        }
    }

    private static void tickPlayer(ServerPlayerEntity player, boolean active, @Nullable UUID match, long now) {
        FiendFeignComponent component = FiendFeignComponent.get(player);
        FiendFeignState state = component.state();
        boolean dormant = active && match != null && FiendParticipation.isDormantFiend(player);
        if (state.isFeigning()) {
            if (!dormant || !state.isFor(match)) {
                // Round over, re-roled or stale: get up (a deferred gun Speed III is dropped) and stop the cycle; a Fiend
                // Wathe already counts dead only gets its camera and corpse cleaned up.
                // 回合结束、换职业或状态过期：起身（延后的枪击速度 III 作废）并停止周期；Wathe 已判定死亡的魔人只清理视角与尸体。
                if (GameFunctions.isPlayerPlayingAndAlive(player)) {
                    revive(player, component, now, false);
                } else {
                    releaseDead(player, state);
                }
                state.clear();
                component.sync();
            } else if (now >= state.endTick()) {
                revive(player, component, now, true);
            } else {
                hold(player, state);
            }
            return;
        }
        if (!dormant) {
            if (state.isScheduled()) {
                state.clear();
                component.sync();
            }
            return;
        }
        if (!state.isFor(match)) {
            state.clear();
            state.schedule(match, FiendFeignRules.nextDueTick(now));
            component.sync();
            return;
        }
        if (state.isLaunched()) {
            // A lethal hit launched it: the 20 s feign starts once it lands, unless a fall killed it first.
            // 致命命中把它击飞：20 秒假死在其落地后开始，除非它先摔死。
            if (FiendFeignRules.launchSettled(now, state.launchedAt(), player.isOnGround(),
                    FiendFeignRules.isFalling(player.fallDistance))) {
                Identifier reason = state.launchReason();
                state.clearLaunch();
                if (canStartNow(player)) {
                    start(player, Trigger.LETHAL_HIT, reason);
                } else if (state.takePendingSpeed()) {
                    // Busy on landing, so no feign: the deferred gun Speed III is due now, not at a later revival.
                    // 落地时正忙，因此不假死：延后的枪击速度 III 现在就给，而不是留到之后的复活。
                    addGunSpeed(player);
                }
            }
            return;
        }
        // A falling Fiend (pushed off the train) is not frozen mid-air: the periodic feign waits for the landing.
        // 正在坠落的魔人（被推下列车）不会被冻在半空：定时假死等它落地后再触发。
        if (FiendFeignRules.periodicDue(now, state.dueTick()) && !FiendFeignRules.isFalling(player.fallDistance)
                && canStartNow(player)) {
            start(player, Trigger.PERIODIC, null);
        }
    }

    /** Binds the cycle to this match, scheduling the first feign when it was not. / 将周期绑定到本对局；尚未绑定时排定首次假死。 */
    private static void ensureScheduled(FiendFeignState state, @Nullable UUID match, long now) {
        if (match != null && !state.isFor(match)) {
            state.clear();
            state.schedule(match, FiendFeignRules.nextDueTick(now));
        }
    }

    private static boolean canStartNow(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        return FiendFeignRules.canStart(
                FiendParticipation.isDormantFiend(player),
                GameWorldComponent.KEY.get(world).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                        && FiendMatch.currentId() != null,
                GameFunctions.isPlayerAliveAndSurvival(player),
                FiendFeignComponent.get(player).state().isFeigning(),
                NoellesTaotieSeekerBridge.isSwallowed(player),
                RiftSessionService.isInside(player) || KidnapperControlComponent.KEY.get(player).isControlled()
        );
    }

    private static void start(ServerPlayerEntity fiend, Trigger trigger, @Nullable Identifier hitReason) {
        ServerWorld world = fiend.getServerWorld();
        UUID match = FiendMatch.currentId();
        if (match == null) {
            return;
        }
        long now = world.getTime();
        FiendFeignComponent component = FiendFeignComponent.get(fiend);
        FiendFeignState state = component.state();
        ensureScheduled(state, match, now);
        fiend.stopRiding();
        if (fiend.isSleeping()) {
            fiend.wakeUp();
        }
        // Drop a charging knife or bat without a release, and any open screen (the shop).
        // 放下正在蓄力的刀或球棒（不触发释放），并关闭打开的界面（商店）。
        fiend.clearActiveItem();
        fiend.closeHandledScreen();
        Vec3d anchor = groundAnchor(world, fiend);
        PlayerBodyEntity body = spawnCorpse(world, fiend, anchor,
                FiendFeignRules.corpseReason(trigger, hitReason, ThreadLocalRandom.current()));
        state.start(now + trigger.durationTicks(), body == null ? null : body.getUuid(), anchor.x, anchor.y, anchor.z,
                fiend.getYaw(), fiend.getPitch(), fiend.isInvulnerable(), fiend.interactionManager.getGameMode());
        fiend.setVelocity(Vec3d.ZERO);
        fiend.velocityModified = true;
        fiend.fallDistance = 0.0F;
        hold(fiend, state);
        component.sync();
    }

    /**
     * Where the feign happens: the Fiend's feet when it stands on the ground, otherwise the floor right below it (a hit
     * mid-jump or stepping off a bunk), so the corpse never drops away from a mid-air anchor. With no floor within
     * {@link #GROUND_SNAP_DEPTH} blocks (falling off the train) the anchor stays where the Fiend is.
     * 假死发生的位置：魔人站在地面时为其脚下，否则为其正下方的地面（跳跃中或走下床铺时被击中），因此尸体绝不会从半空的锚点
     * 掉落。{@link #GROUND_SNAP_DEPTH} 格内没有地面时（正坠下列车）锚点保持在魔人所在处。
     */
    private static Vec3d groundAnchor(ServerWorld world, ServerPlayerEntity fiend) {
        Vec3d feet = fiend.getPos();
        if (fiend.isOnGround()) {
            return feet;
        }
        BlockHitResult hit = world.raycast(new RaycastContext(feet.add(0.0, 0.1, 0.0),
                feet.subtract(0.0, GROUND_SNAP_DEPTH, 0.0), RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, fiend));
        return hit.getType() == HitResult.Type.BLOCK ? new Vec3d(feet.x, hit.getPos().y, feet.z) : feet;
    }

    /**
     * The fake corpse lies exactly where the Fiend stands, facing its head yaw (the NoellesRoles Jester placement, not
     * Wathe's one block forward): the spectating Fiend is moved onto its camera entity every tick, so a corpse pushed
     * through a train wall must never carry it out. The fake mark stays server-only.
     * 假尸体正好躺在魔人所站之处、朝向其头部朝向（沿用 NoellesRoles 小丑的摆放，而非 Wathe 的前移一格）：旁观中的魔人
     * 每刻都会被移到其视角实体处，因此尸体绝不能越过车厢墙把它带出去。假尸标记只留在服务端。
     */
    private static @Nullable PlayerBodyEntity spawnCorpse(ServerWorld world, ServerPlayerEntity fiend, Vec3d anchor,
                                                          Identifier reason) {
        PlayerBodyEntity body = WatheEntities.PLAYER_BODY.create(world);
        if (body == null) {
            return null;
        }
        body.setPlayerUuid(fiend.getUuid());
        // Civilian cover role (Magician decoy precedent): a real dormant Fiend leaves no corpse, so the live "Fiend" role
        // on a weapon-reason corpse would tell every body-role reader (the NoellesRoles Coroner) who the living Fiend is.
        // 平民掩护身份（沿用魔术师诱饵）：真正的休眠魔人不会留下尸体，若武器死因的尸体显示实时的「魔人」身份，
        // 所有能读取尸体身份者（NoellesRoles 验尸官）都会得知活着的魔人是谁。
        ((WraithBodyRoleAccess) body).sparkwitch$setDeathRole(WatheRoles.CIVILIAN.identifier());
        body.setDeathReason(reason);
        body.setDeathGameTime(world.getTime());
        float yaw = fiend.getHeadYaw();
        body.refreshPositionAndAngles(anchor.x, anchor.y, anchor.z, yaw, 0.0F);
        // Seed every yaw so the corpse renderer does not interpolate a head twist on the first frame.
        // 设置全部朝向，避免尸体渲染器在第一帧插值出头部扭转。
        body.setYaw(yaw);
        body.setHeadYaw(yaw);
        body.bodyYaw = fiend.bodyYaw;
        body.prevYaw = yaw;
        body.prevHeadYaw = yaw;
        body.prevBodyYaw = fiend.bodyYaw;
        if (!world.spawnEntity(body)) {
            return null;
        }
        FiendFakeBodies.mark(body);
        // The same cover for SparkStrength's Coroner snapshot (fails closed without SparkStrength).
        // 同样的掩护身份写入 SparkStrength 验尸官快照（缺少 SparkStrength 时失败即关闭）。
        SparkStrengthCoronerCompat.recordBodyRole(body, WatheRoles.CIVILIAN.identifier());
        return body;
    }

    /**
     * Every feigning tick: spectator, camera on the corpse, invulnerable and still. The vanilla tick moves a spectating
     * player onto its camera entity, so the camera stays on the corpse only while the corpse lies at the anchor; once
     * it is gone or moved (dragged, pushed by the Seeker car, launched), the Fiend watches from itself, held at the
     * anchor.
     * 假死期间每刻执行：旁观模式，视角锁在尸体上，无敌且静止。原版逻辑会把旁观中的玩家移到其视角实体处，因此只有尸体仍躺在
     * 锚点时视角才留在尸体上；尸体消失或被移动（拖走、被搜查车推开、被击飞）后，魔人改为以自身视角观看并固定在锚点。
     */
    private static void hold(ServerPlayerEntity fiend, FiendFeignState state) {
        if (fiend.interactionManager.getGameMode() != GameMode.SPECTATOR) {
            fiend.changeGameMode(GameMode.SPECTATOR);
        }
        Entity body = findBody(fiend.getServer(), state.bodyId());
        Entity camera = body != null && body.isAlive() && !drifted(body, state, CORPSE_DRIFT) ? body : fiend;
        if (fiend.getCameraEntity() != camera) {
            fiend.setCameraEntity(camera);
        }
        // No invulnerable flag: a spectator already is, and a flag saved by a mid-feign crash would outlive the round.
        // 不设置无敌标记：旁观者本就无敌，而假死中途崩溃时保存下来的标记会延续到回合之后。
        fiend.setVelocity(Vec3d.ZERO);
        // Pinned tightly: a spectator flies through walls, so even a small drift is pulled back at once.
        // 严格固定：旁观者可以穿墙，即使轻微偏移也立即拉回。
        if (camera == fiend && drifted(fiend, state, PINNED_DRIFT)) {
            teleportToAnchor(fiend, state);
        }
    }

    /**
     * Gets up at the anchor: camera back, the saved game mode and invulnerability, the corpse discarded, any deferred
     * gun Speed III applied, and the next periodic feign scheduled 60 s later.
     * 在锚点起身：视角复位，恢复保存的游戏模式与无敌状态，移除假尸体，补上延后的枪击速度 III，并在 60 秒后排定下一次定时假死。
     */
    private static void revive(ServerPlayerEntity fiend, FiendFeignComponent component, long now,
                               boolean grantDeferredSpeed) {
        FiendFeignState state = component.state();
        boolean speed = grantDeferredSpeed && state.hasPendingSpeed();
        fiend.setCameraEntity(fiend);
        fiend.changeGameMode(state.previousMode());
        fiend.setInvulnerable(state.wasInvulnerable());
        teleportToAnchor(fiend, state);
        fiend.setVelocity(Vec3d.ZERO);
        fiend.velocityModified = true;
        fiend.fallDistance = 0.0F;
        discardBody(fiend.getServer(), state.bodyId());
        state.finish(FiendFeignRules.nextDueTick(now));
        if (speed) {
            addGunSpeed(fiend);
        }
        component.sync();
    }

    /**
     * A feigning player that really died (Vulture, disconnect, {@code /kill}): restore the camera and invulnerability,
     * drop the corpse and the cycle. Wathe's death already made it a spectator.
     * 正在假死的玩家真正死亡（秃鹫、断线、{@code /kill}）：恢复视角与无敌状态，移除尸体并结束周期。Wathe 的死亡流程已让它
     * 成为旁观者。
     */
    private static void afterKill(ServerPlayerEntity victim, ServerPlayerEntity killer, Identifier deathReason) {
        if (victim == null) {
            return;
        }
        FiendFeignComponent component = FiendFeignComponent.get(victim);
        FiendFeignState state = component.state();
        if (!state.isFeigning() && !state.isScheduled()) {
            return;
        }
        if (state.isFeigning()) {
            releaseDead(victim, state);
        }
        state.clear();
        component.sync();
    }

    /** Camera, invulnerability and corpse back for a Fiend that will not get up. / 为不会起身的魔人恢复视角、无敌状态并移除尸体。 */
    private static void releaseDead(ServerPlayerEntity player, FiendFeignState state) {
        player.setCameraEntity(player);
        player.setInvulnerable(state.wasInvulnerable());
        discardBody(player.getServer(), state.bodyId());
    }

    /** Round boundary: Wathe already reset the game mode and position. / 回合边界：Wathe 已重置游戏模式与位置。 */
    private static void onReset(ServerPlayerEntity player) {
        FiendFeignComponent component = FiendFeignComponent.get(player);
        FiendFeignState state = component.state();
        if (!state.isFeigning() && !state.isScheduled()) {
            return;
        }
        if (state.isFeigning()) {
            releaseDead(player, state);
        }
        state.clear();
        component.sync();
    }

    /**
     * Leaving mid-feign: get up first, so the saved player data never keeps a living player in spectator mode; Wathe's
     * own disconnect handling then decides the death.
     * 假死中途离线：先起身，确保保存的玩家数据不会让活着的玩家停留在旁观模式；之后由 Wathe 自身的断线处理决定死亡。
     */
    private static void onDisconnect(@Nullable ServerPlayerEntity player) {
        if (player == null || !isFeigning(player)) {
            return;
        }
        FiendFeignComponent component = FiendFeignComponent.get(player);
        revive(player, component, player.getServerWorld().getTime(), false);
        component.state().clear();
    }

    private static void addGunSpeed(ServerPlayerEntity fiend) {
        fiend.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, FiendRules.GUN_SPEED_TICKS,
                FiendRules.GUN_SPEED_AMPLIFIER, false, false, true));
    }

    private static boolean drifted(Entity entity, FiendFeignState state, double limit) {
        double dx = entity.getX() - state.anchorX();
        double dz = entity.getZ() - state.anchorZ();
        return dx * dx + dz * dz > limit * limit || Math.abs(entity.getY() - state.anchorY()) > limit;
    }

    private static void teleportToAnchor(ServerPlayerEntity fiend, FiendFeignState state) {
        fiend.teleport(fiend.getServerWorld(), state.anchorX(), state.anchorY(), state.anchorZ(), Set.of(),
                state.anchorYaw(), state.anchorPitch());
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
     * Discards the corpse when it is loaded. One that cannot be found (its chunk unloaded) keeps its fake mark until the
     * round boundary, so {@link #onEntityLoad} discards it if it ever loads again instead of leaving an unmarked corpse
     * of a living player.
     * 尸体已加载时直接移除。找不到的尸体（所在区块已卸载）保留假尸标记直到回合边界，若之后再次加载则由 {@link #onEntityLoad}
     * 移除，绝不留下一具未标记的活人尸体。
     */
    private static void discardBody(@Nullable MinecraftServer server, @Nullable UUID bodyId) {
        Entity body = findBody(server, bodyId);
        if (body != null) {
            if (!body.isRemoved()) {
                body.discard();
            }
            FiendFakeBodies.forget(bodyId);
        }
    }

    /**
     * A marked corpse that loads while no Fiend is feigning on it is queued and discarded at the end of the tick (never
     * from inside the load callback).
     * 加载时已没有魔人在其上假死的带标记尸体会被排队，并在本刻结束时移除（绝不在加载回调内部移除）。
     */
    private static void onEntityLoad(Entity entity, ServerWorld world) {
        if (!(entity instanceof PlayerBodyEntity body) || !FiendFakeBodies.isFake(body)) {
            return;
        }
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(body.getPlayerUuid());
        FiendFeignState state = owner == null ? null : FiendFeignComponent.get(owner).state();
        if (state == null || !state.isFeigning() || !body.getUuid().equals(state.bodyId())) {
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
