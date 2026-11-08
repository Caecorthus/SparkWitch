package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke;

import dev.doctor4t.wathe.api.event.GameEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * Server lifecycle of BEAR smoke, registered once from {@code SparkWitchEvents}. Smoke never outlives its Wathe round:
 * it is cleared when a round finishes initializing (so off-match test smoke never reaches a new round) and when it
 * finishes finalizing (the same edges the Deep Dark Zone uses). Spawning itself is not gated on the round, so dev
 * tools can burst smoke off-match. A joining player gets the world's live smoke; a player who changes world (portal,
 * teleport, Wathe map vote) or respawns into another world is cleared first and then gets the new world's smoke.
 * Unloaded worlds and stopped servers are only forgotten (their players are gone).
 * BEAR 烟雾的服务端生命周期，由 {@code SparkWitchEvents} 注册一次。烟雾绝不跨越 Wathe 回合：回合完成初始化时清除
 * （局外测试的烟不会带入新一局），完成收尾时也清除（与深暗领域相同的节点）。生成本身不受回合限制，因此开发工具可以在局外
 * 放烟。进服的玩家会收到该世界的存活烟雾；换世界（传送门、传送、Wathe 地图投票）或重生到另一世界的玩家先被清除，再收到新世界
 * 的烟雾。卸载的世界与停止的服务端只需丢弃登记（其玩家已经离开）。
 */
public final class SmokeCloudLifecycle {
    private static boolean registered;

    private SmokeCloudLifecycle() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_WORLD_TICK.register(SmokeCloudService::tick);
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clear(world));
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clear(world));
        ServerWorldEvents.UNLOAD.register((server, world) -> SmokeCloudService.forgetWorld(world));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SmokeCloudService.forgetAll());
        // Fabric fires JOIN on the server thread after the game-join packet, so the smoke lands in the joiner's world.
        // Fabric 在服务端线程、游戏加入包之后触发 JOIN，因此烟雾会落到进服者的世界里。
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                SmokeCloudService.syncTo(handler.player, handler.player.getServerWorld(), false));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                SmokeCloudService.syncTo(player, destination, SmokeCloudService.hasSmoke(origin)));
        // A same-world respawn keeps the client's world and smoke; only a respawn into another world resyncs.
        // 同一世界内的重生保留客户端世界与烟雾；只有重生到另一世界时才重新同步。
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> onRespawn(oldPlayer, newPlayer));
    }

    private static void onRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        ServerWorld origin = oldPlayer.getServerWorld();
        ServerWorld destination = newPlayer.getServerWorld();
        if (origin != destination) {
            SmokeCloudService.syncTo(newPlayer, destination, SmokeCloudService.hasSmoke(origin));
        }
    }

    private static void clear(World world) {
        if (world instanceof ServerWorld serverWorld) {
            SmokeCloudService.clearAll(serverWorld);
        }
    }
}
