package dev.caecorthus.sparkwitch.client.specter;

import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterOtherworldComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;

/**
 * Soul-torch particles around every living player during the Otherworld. The target is a steady density of
 * 0.5 particles per block in the local 20-block radius (the only area a client can render anyway): the per-tick spawn
 * count is area × 0.5 ÷ the soul-flame lifetime, capped for frame safety.
 * 里世界期间每个存活玩家周围的灵魂火把粒子。目标是在本地 20 格半径内维持每方块 0.5 个粒子的稳态密度
 * （客户端本来也只渲染附近）；每 tick 生成数 = 面积 × 0.5 ÷ 灵魂火焰寿命，并设帧率保护上限。
 */
public final class SpecterOtherworldParticles {
    private static final double RADIUS = 20.0D;
    /** Soul flame lifetime approximation in ticks. / 灵魂火焰粒子寿命近似值（tick）。 */
    private static final float PARTICLE_LIFETIME_TICKS = 20.0F;
    private static final float PARTICLES_PER_BLOCK = 0.5F;
    private static final int MAX_PER_TICK = 40;
    private static boolean registered;

    private SpecterOtherworldParticles() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(SpecterOtherworldParticles::tick);
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) {
            return;
        }
        if (!GameWorldComponent.KEY.get(world).isRunning()
                || !SpecterOtherworldComponent.KEY.get(world).isActive()
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || player.isSpectator()) {
            return;
        }
        double area = Math.PI * RADIUS * RADIUS;
        int count = Math.min(MAX_PER_TICK,
                Math.max(1, Math.round((float) (area * PARTICLES_PER_BLOCK / PARTICLE_LIFETIME_TICKS))));
        for (int index = 0; index < count; index++) {
            double angle = world.random.nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(world.random.nextDouble()) * RADIUS;
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;
            double y = player.getY() + (world.random.nextDouble() - 0.5D) * 16.0D;
            world.addParticle(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    x,
                    y,
                    z,
                    (world.random.nextDouble() - 0.5D) * 0.02D,
                    0.01D + world.random.nextDouble() * 0.02D,
                    (world.random.nextDouble() - 0.5D) * 0.02D
            );
        }
    }
}
