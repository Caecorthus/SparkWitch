package dev.caecorthus.sparkwitch.roles.civilian.seeker.taotie;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SeekerControlExpertBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsSeekerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCarState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCarEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceService;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.net.SeekerCarSwallowC2SPacket;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Frozen contract: the Taotie car swallow (all NoellesRoles calls live here or in {@link NoellesTaotieSeekerBridge}) and
 * the car return when that exact Taotie finally dies or loses the role. Server only. The device side (session end,
 * discard, item removal, state, sound, replay, messages) belongs to {@link SeekerDeviceService#swallowCar} and
 * {@link SeekerDeviceService#returnSwallowedCar}; this service validates, charges the Taotie's cooldown and decides
 * when to return. Refusals are silent so a probe never reveals a car's owner.
 * 冻结契约：饕餮吞车（所有 NoellesRoles 调用都在此处或 {@link NoellesTaotieSeekerBridge} 中）以及该饕餮最终死亡或失去
 * 身份时的小车归还。仅服务端。设备侧（结束会话、移除实体与物品、状态、音效、回放、消息）属于
 * {@link SeekerDeviceService#swallowCar} 与 {@link SeekerDeviceService#returnSwallowedCar}；本服务只负责校验、扣除饕餮
 * 冷却与决定何时归还。拒绝一律静默，试探无法暴露小车的拥有者。
 */
public final class SeekerTaotieService {
    private static boolean registered;

    private SeekerTaotieService() {
    }

    /**
     * Registers this service's own Taotie {@code KillPlayer.AFTER} listener (the Seeker lifecycle registers none for
     * the Taotie). Idempotent.
     * 注册本服务自己的饕餮 {@code KillPlayer.AFTER} 监听（搜寻者生命周期不为饕餮注册）。幂等。
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        KillPlayer.AFTER.register((victim, killer, deathReason) -> onKill(victim));
    }

    /**
     * {@code seeker_car_swallow} receiver (server thread). Validates in plan order ({@link SeekerTaotieRules#swallowDenial}),
     * then charges NoellesRoles' dynamic swallow cooldown BEFORE the device swallow, so repeated or racing requests
     * (including NoellesRoles' own player swallow in the same tick) fail on the cooldown. A device swallow that still
     * fails rolls the cooldown back.
     * {@code seeker_car_swallow} 接收器（服务端线程）。按计划顺序校验，然后在设备吞车之前扣除 NoellesRoles 的动态吞噬
     * 冷却，因此重复或竞争的请求（包括同一刻 NoellesRoles 自己的吞人）都会因冷却失败。设备吞车若仍失败则回滚冷却。
     */
    public static void handleSwallow(ServerPlayerEntity taotie, SeekerCarSwallowC2SPacket packet) {
        if (taotie == null || packet == null) {
            return;
        }
        Entity entity = taotie.getServerWorld().getEntityById(packet.carEntityId());
        SeekerCarEntity car = entity instanceof SeekerCarEntity candidate && candidate.isAlive() && !candidate.isRemoved()
                ? candidate
                : null;
        ServerPlayerEntity owner = car == null ? null : SeekerDeviceService.findOwner(car);
        GameWorldComponent game = GameWorldComponent.KEY.get(taotie.getWorld());
        if (!SeekerTaotieRules.swallowDenial(new ServerChecks(taotie, car, owner, game)).allowed()) {
            return;
        }
        NoellesTaotieSeekerBridge.consumeSwallowCooldown(taotie);
        if (!SeekerDeviceService.swallowCar(car, taotie)) {
            // Validation already required a ready cooldown, so the pre-swallow value was zero.
            // 校验已要求冷却就绪，因此吞车前的值为零。
            NoellesTaotieSeekerBridge.restoreSwallowCooldown(taotie, 0);
        }
    }

    /**
     * {@code KillPlayer.AFTER} for any victim: returns every car held by exactly this victim unless SparkTraits Last
     * Stand intercepted the death (an older SparkTraits without the query also reads as intercepted); the owner poll
     * then returns the car at the final death. Disconnects arrive here as Wathe ESCAPED kills.
     * 任意受害者的 {@code KillPlayer.AFTER}：归还恰由该受害者吞下的所有小车，除非 SparkTraits 最后一搏拦截了这次死亡
     * （缺少查询接口的旧版 SparkTraits 也视为已拦截）；此时由拥有者轮询在最终死亡时归还。断线会以 Wathe 的
     * ESCAPED 击杀到达此处。
     */
    public static void onKill(ServerPlayerEntity victim) {
        MinecraftServer server = victim == null ? null : victim.getServer();
        if (server == null || !GameWorldComponent.KEY.get(victim.getWorld()).isRunning()) {
            return;
        }
        UUID victimId = victim.getUuid();
        List<ServerPlayerEntity> holders = new ArrayList<>();
        for (ServerPlayerEntity owner : server.getPlayerManager().getPlayerList()) {
            SeekerStatusComponent component = SeekerStatusComponent.KEY.getNullable(owner);
            if (component != null && SeekerTaotieRules.killReturnsCar(component.carState(), component.carLostTo(),
                    victimId, false)) {
                holders.add(owner);
            }
        }
        // The SparkTraits reflective query runs only when this victim actually holds a car.
        // 只有受害者确实吞着小车时才执行 SparkTraits 反射查询。
        if (holders.isEmpty() || WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
            return;
        }
        for (ServerPlayerEntity owner : holders) {
            SeekerDeviceService.returnSwallowedCar(owner);
        }
    }

    /**
     * Called by the owner's component tick every 20 ticks while the car is SWALLOWED or PendingReturn is set. Sole
     * owner of both the return poll (that Taotie finally dead via {@code game.isPlayerDead} and not Last-Stand
     * pending, or no longer a Taotie) and the PendingReturn retry; the return itself is
     * {@link SeekerDeviceService#returnSwallowedCar}.
     * 当小车处于 SWALLOWED 或设置了 PendingReturn 时，由拥有者组件刻每 20 刻调用一次。独自负责归还轮询
     * （该饕餮经 {@code game.isPlayerDead} 最终死亡且不处于最后一搏待定，或不再是饕餮）与 PendingReturn 重试；
     * 归还本身由 {@link SeekerDeviceService#returnSwallowedCar} 执行。
     */
    public static void tick(ServerPlayerEntity owner, SeekerStatusComponent component) {
        if (owner == null || component == null || owner.getServer() == null) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(owner.getWorld());
        if (!game.isRunning()) {
            return;
        }
        MinecraftServer server = owner.getServer();
        UUID lostTo = component.carLostTo();
        if (SeekerTaotieRules.shouldAttemptReturn(component.carState(), component.pendingReturn(), lostTo,
                () -> SeekerTaotieRules.isFinalDeath(game.isPlayerDead(lostTo),
                        SparkTraitsSeekerBridge.isLastStandPending(server.getPlayerManager().getPlayer(lostTo))),
                () -> NoellesTaotieSeekerBridge.isTaotie(game, lostTo))) {
            SeekerDeviceService.returnSwallowedCar(owner);
        }
    }

    /** Lazily evaluated facts for one request. / 单次请求的按需求值事实。 */
    private record ServerChecks(ServerPlayerEntity taotie, @Nullable SeekerCarEntity car,
                                @Nullable ServerPlayerEntity owner, GameWorldComponent game)
            implements SeekerTaotieRules.SwallowChecks {
        @Override
        public boolean running() {
            return game.isRunning();
        }

        @Override
        public boolean exactTaotie() {
            return NoellesTaotieSeekerBridge.isTaotie(taotie);
        }

        @Override
        public boolean aliveParticipant() {
            return GameFunctions.isPlayerPlayingAndAlive(taotie)
                    && GameFunctions.isPlayerAliveAndSurvival(taotie)
                    && !WraithStateService.isActive(taotie);
        }

        @Override
        public boolean swallowed() {
            return NoellesTaotieSeekerBridge.isSwallowed(taotie);
        }

        @Override
        public boolean feared() {
            return GrandWitchFearService.isPlayerFeared(taotie);
        }

        @Override
        public boolean stunned() {
            return SeekerControlExpertBridge.isStunned(taotie);
        }

        @Override
        public boolean roleSkillBlocked() {
            return SparkTraitsKillerBridge.isRoleSkillBlocked(taotie);
        }

        @Override
        public int swallowCooldownTicks() {
            return NoellesTaotieSeekerBridge.swallowCooldown(taotie);
        }

        @Override
        public boolean liveCar() {
            return car != null && owner != null && owner != taotie && car.getWorld() == taotie.getWorld();
        }

        @Override
        public boolean carDeployed() {
            SeekerStatusComponent component = owner == null ? null : SeekerStatusComponent.KEY.getNullable(owner);
            return component != null
                    && car != null
                    && component.carState() == SeekerCarState.DEPLOYED
                    && component.carEntityId() == car.getId();
        }

        @Override
        public double squaredDistanceToCar() {
            return car == null ? Double.NaN : taotie.squaredDistanceTo(car);
        }

        @Override
        public int reachSquared() {
            return NoellesTaotieSeekerBridge.swallowDistanceSquared();
        }

        @Override
        public boolean canSeeCar() {
            return car != null && taotie.canSee(car);
        }

        @Override
        public boolean factionAllows() {
            // Our own veto: SparkFactionAPI's Taotie packet guard targets a lambda absent from pinned 1.7.6.
            // 自行调用否决：SparkFactionAPI 的饕餮数据包守卫指向固定 1.7.6 中不存在的 lambda。
            return owner != null && SparkFactionApi.canAffectPlayer(taotie, owner, SeekerRules.SWALLOW_ACTION_ID, game);
        }
    }
}
