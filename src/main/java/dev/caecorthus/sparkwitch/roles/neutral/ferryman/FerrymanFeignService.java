package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.compat.SparkStrengthCoronerCompat;
import dev.caecorthus.sparkwitch.roles.special.wraith.conversion.WraithBodyRoleAccess;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The 10 s feign death a marked attacker falls into: the attacker is moved into spectator, a fake corpse of them is
 * laid at their feet with a civilian cover role (so a body-role reader cannot identify the marked player) and both are
 * restored when the timer runs out. The player keeps a fake body only; their own entity stays server-side, so instinct
 * cannot outline them while they feign, and they wake up in place afterwards.
 * 被标记者陷入的 10 秒假死：攻击者被切成旁观、其脚下铺出具带平民掩护身份的假尸体（因此读取尸体身份者无法认出被标记者），
 * 计时结束后两者一并复原。玩家只留一具假尸体，本体仍在服务端，因此假死期间本能描不出他，结束后在原地起身。
 *
 * <p>Standalone by owner decision: this duplicates the shape of the Fiend's cycle without touching it.
 * 按所有者决定独立实现：复刻魔人那套流程的形态，但不改动它。</p>
 */
public final class FerrymanFeignService {
    private static final double GROUND_SNAP_DEPTH = 3.0;
    /** Beyond this drift the camera leaves the corpse and watches from the anchored body. / 超过该漂移则改为以自身视角观看。 */
    private static final double CORPSE_DRIFT_SQUARED = 0.75 * 0.75;
    /** A spectator flies through walls, so even a small drift is pulled back. / 旁观者会穿墙，因此轻微偏移也立即拉回。 */
    private static final double PINNED_DRIFT_SQUARED = 0.01 * 0.01;

    private FerrymanFeignService() {
    }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(FerrymanFeignService::tick);
        KillPlayer.AFTER.register(FerrymanFeignService::afterKill);
        ResetPlayer.EVENT.register(FerrymanFeignService::onReset);
    }

    public static boolean isFeigning(ServerPlayerEntity player) {
        return FerrymanFeignComponent.KEY.get(player).isFeigning();
    }

    /** True while {@code body} is the fake corpse of a player currently feigning. / 该遗体是当前假死玩家的假尸体时为 true。 */
    public static boolean isFakeBody(@Nullable PlayerBodyEntity body) {
        ServerPlayerEntity owner = bodyOwner(body);
        if (owner == null) {
            return false;
        }
        FerrymanFeignComponent component = FerrymanFeignComponent.KEY.get(owner);
        return component.isFeigning() && body.getUuid().equals(component.bodyId());
    }

    /**
     * A Vulture ate the fake corpse: the feigning player dies for real. The eat itself already ran (cooldown, win
     * credit, body discarded), so this only settles the death and gets the body up if another guard refused it.
     * 秃鹫吃掉了假尸体：假死玩家真正死亡。进食本身已经执行（冷却、胜利进度、尸体移除），这里只结算死亡；若其他守卫
     * 拒绝了这次死亡，则按普通假死结束起身。
     */
    public static void onBodyDevoured(@Nullable PlayerBodyEntity body, @Nullable ServerPlayerEntity vulture) {
        if (!isFakeBody(body)) {
            return;
        }
        ServerPlayerEntity owner = bodyOwner(body);
        if (owner == null) {
            return;
        }
        FerrymanFeignComponent component = FerrymanFeignComponent.KEY.get(owner);
        owner.setCameraEntity(owner);
        GameFunctions.killPlayer(owner, false, vulture, SparkWitchDeathReasons.FERRYMAN_FEIGN);
        if (component.isFeigning()) {
            revive(owner, component);
        }
    }

    private static @Nullable ServerPlayerEntity bodyOwner(@Nullable PlayerBodyEntity body) {
        if (body == null) {
            return null;
        }
        MinecraftServer server = body.getServer();
        return server == null ? null : server.getPlayerManager().getPlayer(body.getPlayerUuid());
    }

    /** Server: lays the fake corpse and pins the attacker to it for 10 s. / 服务端：铺出假尸体并把人钉在其上 10 秒。 */
    static boolean start(ServerPlayerEntity player) {
        FerrymanFeignComponent component = FerrymanFeignComponent.KEY.get(player);
        if (component.isFeigning() || player.isSpectator()) {
            return false;
        }
        ServerWorld world = player.getServerWorld();
        player.stopRiding();
        if (player.isSleeping()) {
            player.wakeUp();
        }
        player.clearActiveItem();
        player.closeHandledScreen();
        Vec3d anchor = groundAnchor(world, player);
        PlayerBodyEntity body = spawnCorpse(world, player, anchor);
        component.start(
                world.getTime() + FerrymanRules.FEIGN_TICKS,
                body == null ? null : body.getUuid(),
                anchor.x, anchor.y, anchor.z,
                player.interactionManager.getGameMode(),
                player.isInvulnerable()
        );
        player.setVelocity(Vec3d.ZERO);
        player.velocityModified = true;
        player.fallDistance = 0.0F;
        hold(player);
        return true;
    }

    private static void hold(ServerPlayerEntity player) {
        FerrymanFeignComponent component = FerrymanFeignComponent.KEY.get(player);
        if (player.interactionManager.getGameMode() != GameMode.SPECTATOR) {
            player.changeGameMode(GameMode.SPECTATOR);
        }
        Entity body = findBody(player.getServer(), component.bodyId());
        Vec3d anchor = new Vec3d(component.anchorX(), component.anchorY(), component.anchorZ());
        Entity camera = body != null && body.isAlive() && body.squaredDistanceTo(anchor) <= CORPSE_DRIFT_SQUARED
                ? body
                : player;
        if (player.getCameraEntity() != camera) {
            player.setCameraEntity(camera);
        }
        player.setVelocity(Vec3d.ZERO);
        if (camera == player && player.squaredDistanceTo(anchor) > PINNED_DRIFT_SQUARED) {
            player.teleport(player.getServerWorld(), anchor.x, anchor.y, anchor.z, java.util.Set.of(), 0.0F, 0.0F);
        }
    }

    private static void tick(MinecraftServer server) {
        long now = server.getOverworld().getTime();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            FerrymanInstinctVeilComponent.KEY.get(player).tickServer();
            FerrymanFeignComponent component = FerrymanFeignComponent.KEY.get(player);
            if (!component.isFeigning()) {
                continue;
            }
            if (now >= component.untilTick() || !player.isAlive()) {
                revive(player, component);
                continue;
            }
            hold(player);
        }
    }

    private static void revive(ServerPlayerEntity player, FerrymanFeignComponent component) {
        player.setCameraEntity(player);
        player.changeGameMode(component.previousMode());
        player.setInvulnerable(component.wasInvulnerable());
        player.teleport(player.getServerWorld(), component.anchorX(), component.anchorY(), component.anchorZ(),
                java.util.Set.of(), 0.0F, 0.0F);
        player.setVelocity(Vec3d.ZERO);
        player.velocityModified = true;
        player.fallDistance = 0.0F;
        discardBody(player.getServer(), component.bodyId());
        component.clear();
        // 5 s where instinct cannot outline the player who just got up (owner spec).
        // 起身后 5 秒内本能描不出他（所有者设定）。
        FerrymanInstinctVeilComponent.KEY.get(player).veil(FerrymanRules.FEIGN_INSTINCT_BLOCK_TICKS);
    }

    /**
     * A feigning player that really died (Vulture, disconnect, {@code /kill}): drop the corpse and the cycle.
     * 正在假死的玩家真正死亡（秃鹫、断线、{@code /kill}）：移除尸体并结束周期。
     */
    private static void afterKill(ServerPlayerEntity victim, ServerPlayerEntity killer,
                                  net.minecraft.util.Identifier deathReason) {
        if (victim == null) {
            return;
        }
        FerrymanFeignComponent component = FerrymanFeignComponent.KEY.get(victim);
        if (!component.isFeigning()) {
            return;
        }
        victim.setCameraEntity(victim);
        victim.setInvulnerable(component.wasInvulnerable());
        discardBody(victim.getServer(), component.bodyId());
        component.clear();
    }

    private static void onReset(ServerPlayerEntity player) {
        FerrymanFeignComponent component = FerrymanFeignComponent.KEY.get(player);
        if (!component.isFeigning()) {
            return;
        }
        discardBody(player.getServer(), component.bodyId());
        component.clear();
    }

    // ---- Corpse / 尸体 ----

    private static Vec3d groundAnchor(ServerWorld world, ServerPlayerEntity player) {
        Vec3d feet = player.getPos();
        if (player.isOnGround()) {
            return feet;
        }
        BlockHitResult hit = world.raycast(new RaycastContext(feet.add(0.0, 0.1, 0.0),
                feet.subtract(0.0, GROUND_SNAP_DEPTH, 0.0), RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? new Vec3d(feet.x, hit.getPos().y, feet.z) : feet;
    }

    private static @Nullable PlayerBodyEntity spawnCorpse(ServerWorld world, ServerPlayerEntity player, Vec3d anchor) {
        PlayerBodyEntity body = WatheEntities.PLAYER_BODY.create(world);
        if (body == null) {
            return null;
        }
        body.setPlayerUuid(player.getUuid());
        // Civilian cover: a body that reads the live role would tell a role reader who the marked player is.
        // 平民掩护：若尸体读出实时职业，读取死者身份的人就能认出被标记者。
        ((WraithBodyRoleAccess) body).sparkwitch$setDeathRole(WatheRoles.CIVILIAN.identifier());
        body.setDeathReason(SparkWitchDeathReasons.FERRYMAN_FEIGN);
        body.setDeathGameTime(world.getTime());
        float yaw = player.getHeadYaw();
        body.refreshPositionAndAngles(anchor.x, anchor.y, anchor.z, yaw, 0.0F);
        body.setYaw(yaw);
        body.setHeadYaw(yaw);
        body.bodyYaw = player.bodyYaw;
        body.prevYaw = yaw;
        body.prevHeadYaw = yaw;
        body.prevBodyYaw = player.bodyYaw;
        if (!world.spawnEntity(body)) {
            return null;
        }
        SparkStrengthCoronerCompat.recordBodyRole(body, WatheRoles.CIVILIAN.identifier());
        return body;
    }

    private static @Nullable Entity findBody(@Nullable MinecraftServer server, @Nullable UUID bodyId) {
        if (server == null || bodyId == null) {
            return null;
        }
        for (ServerWorld world : server.getWorlds()) {
            Entity entity = world.getEntity(bodyId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static void discardBody(@Nullable MinecraftServer server, @Nullable UUID bodyId) {
        Entity body = findBody(server, bodyId);
        if (body != null) {
            body.discard();
        }
    }
}
