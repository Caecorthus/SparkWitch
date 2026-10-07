package dev.caecorthus.sparkwitch.roles.civilian.judge;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.UUID;

/** Exact Swapper flight / rejected train-fall episodes, never a general last-attacker cache.
 * 只记录交换引起的悬空或已被拒绝的坠车因果，不猜最后攻击者。 */
public final class JudgeTrainFallAttribution {
    private static final JudgeFallEpisodeLedger<ServerWorld> FALLS = new JudgeFallEpisodeLedger<>();

    private JudgeTrainFallAttribution() {}

    public static void prune(ServerWorld world) {
        if (!active(world)) {
            clearWorld(world);
            return;
        }
        FALLS.prune(world, (id, episode) -> world.getPlayerByUuid(id) instanceof ServerPlayerEntity player
                && episode.remains(GameFunctions.isPlayerPlayingAndAlive(player), belowTrain(player), safeSupport(player)));
    }

    /** Invoked only AFTER a verified successful Swapper teleport, with that invocation's actor UUID.
     * 仅在已验证成功的交换传送返回后调用，保存该次调用的责任人 UUID。 */
    public static void swapped(ServerPlayerEntity victim, UUID swapper) {
        superseded(victim);
        if (active(victim.getServerWorld()) && GameFunctions.isPlayerPlayingAndAlive(victim)
                && (belowTrain(victim) || !safeSupport(victim))) {
            FALLS.put(victim.getServerWorld(), victim.getUuid(), swapper, true);
        }
    }

    /** A later successful teleport ends the old trajectory, even across worlds. / 后续成功传送终止旧轨迹，跨世界同样清理。 */
    public static void superseded(ServerPlayerEntity victim) {
        FALLS.clearVictim(victim.getUuid());
    }

    public static UUID currentCause(ServerPlayerEntity victim) {
        if (!active(victim.getServerWorld()) || !GameFunctions.isPlayerPlayingAndAlive(victim)) return null;
        JudgeFallEpisodeLedger.Episode episode = FALLS.get(victim.getServerWorld(), victim.getUuid());
        // A FALL damage callback runs on landing: do not erase its proven flight cause before the health guard.
        // FALL 伤害在着地时回调，不能在生命值拦截前因刚触地而抹除本段悬空归属。
        return episode == null ? null : episode.actor();
    }

    /** Runtime clears this at every new round, finalization, unload and server stop; no episode crosses rounds.
     * Runtime 在新回合、结算、卸载与停服时清理；因果不能跨回合。 */
    public static void clearWorld(ServerWorld world) {
        FALLS.clearWorld(world);
    }

    public static UUID resolve(ServerPlayerEntity victim, UUID freshPusher) {
        JudgeFallEpisodeLedger.Episode pending = FALLS.take(victim.getServerWorld(), victim.getUuid());
        UUID responsible = freshPusher != null ? freshPusher : pending == null ? null : pending.actor();
        // Only a Judge-rejected fall retains its exact cause for next tick; ordinary expiry/consumption is unchanged.
        // 仅被法官拒绝的坠车保留责任到下一次尝试，不改变普通推力过期或消费语义。
        if (responsible != null && JudgeRuntime.blocksKill(victim.getServerWorld(), responsible, victim.getUuid())) {
            FALLS.put(victim.getServerWorld(), victim.getUuid(), responsible, false);
        }
        return responsible;
    }

    private static boolean active(ServerWorld world) {
        return GameWorldComponent.KEY.get(world).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
    }

    private static boolean belowTrain(ServerPlayerEntity player) {
        Box playArea = MapVariablesWorldComponent.KEY.get(player.getWorld()).getPlayArea();
        return playArea != null && player.getY() < playArea.minY;
    }

    private static boolean safeSupport(ServerPlayerEntity player) {
        // Noelles writes onGround=true after swapping, even into air; probe actual collision support instead.
        // Noelles 交换后会无条件写 onGround=true，因此探测脚下真实碰撞支撑，不相信该标志。
        if (player.hasVehicle() || player.isTouchingWater() || player.isClimbing()
                || player.isFallFlying() || player.getAbilities().flying) return true;
        Box body = player.getBoundingBox();
        Box feet = new Box(body.minX + 0.001, body.minY - 0.05, body.minZ + 0.001,
                body.maxX - 0.001, body.minY, body.maxZ - 0.001);
        return player.getWorld().getBlockCollisions(player, feet).iterator().hasNext();
    }
}
