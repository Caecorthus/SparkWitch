package dev.caecorthus.sparkwitch.client.specter;

import dev.caecorthus.sparkwitch.roles.killer.specter.GhostTriggerEntity;
import dev.doctor4t.wathe.api.event.CanSeePoison;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.index.WatheParticles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

/**
 * Spawns the placed ghost trigger's poison-style particles, using Wathe's own poison visibility gate: killers, dead
 * spectators, and roles whose {@link CanSeePoison} listener returns true see them; everyone else never receives them.
 * 在已放置的鬼魂触发器位置生成毒药粒子，沿用 Wathe 的下毒可见性门槛：杀手、死亡旁观者，以及
 * {@link CanSeePoison} 监听返回 true 的职业能看到；其他玩家完全收不到粒子。
 */
public final class GhostTriggerClientHooks {
    /** Matches the knife/bed poison particle cadence. / 与床/餐盘毒粒子相同的 1/20 刻。 */
    private static final int PARTICLE_CHANCE = 20;
    private static final int PARTICLE_ROLL = 17;
    /** Only loaded/tracked triggers matter; the entity is tracked within 64 blocks. / 只看已加载/已追踪的触发器。 */
    private static final double SEARCH_RANGE = 96.0D;
    private static boolean registered;

    private GhostTriggerClientHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(GhostTriggerClientHooks::tick);
    }

    private static void tick(MinecraftClient client) {
        PlayerEntity viewer = client.player;
        if (viewer == null || client.world == null || !canSeePoison(viewer)) {
            return;
        }
        Box range = viewer.getBoundingBox().expand(SEARCH_RANGE);
        for (GhostTriggerEntity trigger : client.world.getEntitiesByClass(
                GhostTriggerEntity.class,
                range,
                entity -> true
        )) {
            if (client.world.random.nextInt(PARTICLE_CHANCE) != PARTICLE_ROLL) {
                continue;
            }
            client.world.addParticle(
                    WatheParticles.POISON,
                    trigger.getX(),
                    trigger.getY() + 0.05D,
                    trigger.getZ(),
                    0.0D,
                    0.05D,
                    0.0D
            );
        }
    }

    private static boolean canSeePoison(PlayerEntity viewer) {
        return WatheClient.isKiller()
                || WatheClient.canSeeSpectatorInformation()
                || CanSeePoison.EVENT.invoker().visible(viewer);
    }
}
