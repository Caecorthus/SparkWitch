package dev.caecorthus.sparkwitch.roles.civilian.savior;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Keeps every living Savior's mood (the sanity bar) pinned at full. Wathe's mood setter clamps REAL roles to [-1, 1],
 * so 1.0 is the top of the bar; writing it every server tick also cancels the pistol's innocent-kill mood cost.
 * 把每名存活的救世主的心情（理智条）每刻钉在满值。Wathe 的 mood setter 对 REAL 身份钳制到 [-1,1]，1.0 即满；
 * 每服务端刻写入也会抵消手枪误杀理智惩罚。
 */
public final class SaviorSanityService {
    private static final float FULL_MOOD = 1.0F;
    private static boolean registered;

    private SaviorSanityService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(SaviorSanityService::tick);
    }

    private static void tick(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            GameWorldComponent game = GameWorldComponent.KEY.get(world);
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (game.getRole(player) == SparkWitchRoles.savior()
                        && !game.isPlayerDead(player.getUuid())) {
                    PlayerMoodComponent.KEY.get(player).setMood(FULL_MOOD);
                }
            }
        }
    }
}
