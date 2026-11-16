package dev.caecorthus.sparkwitch.client.specter;

import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterOtherworldComponent;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterParticles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/**
 * Yin-realm particles around every living player during the Otherworld, spawned client-side near the local player
 * only: a few sparse soul-fire flames around eye height and slowly falling joss paper
 * ({@code sparkwitch:joss_paper}) just overhead. Both spawn only in air blocks, with a hard per-tick cap and a bounded
 * number of position attempts, so cramped rooms get fewer particles instead of more work.
 * 里世界期间每个存活玩家周围的「阴间」粒子，仅在本地玩家附近由客户端生成：眼睛高度附近稀疏的几点灵魂火，以及头顶缓缓飘落的纸钱
 * （{@code sparkwitch:joss_paper}）。两者都只在空气方块中生成，每 tick 有硬上限且位置尝试次数有界，因此狭窄房间只会少生成，不会多做工。
 */
public final class SpecterOtherworldParticles {
    private static final int SOUL_FIRE_PER_TICK = 3;
    private static final int SOUL_FIRE_ATTEMPTS = 8;
    private static final double SOUL_FIRE_RADIUS = 12.0D;
    /** Vertical spread above and below the eye height. / 眼睛高度上下的垂直范围。 */
    private static final double SOUL_FIRE_EYE_SPREAD = 3.0D;
    private static final int JOSS_PAPER_PER_TICK = 2;
    private static final int JOSS_PAPER_ATTEMPTS = 6;
    private static final double JOSS_PAPER_RADIUS = 9.0D;
    /** Spawn height range above the player's feet. / 相对玩家脚部的生成高度范围。 */
    private static final double JOSS_PAPER_MIN_HEIGHT = 0.5D;
    private static final double JOSS_PAPER_MAX_HEIGHT = 3.5D;
    /** Largest horizontal drift per axis, in blocks per tick. / 每轴最大水平漂移（格/刻）。 */
    private static final double JOSS_PAPER_DRIFT = 0.006D;
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
        Random random = world.random;
        BlockPos.Mutable probe = new BlockPos.Mutable();
        int spawned = 0;
        for (int attempt = 0; attempt < SOUL_FIRE_ATTEMPTS && spawned < SOUL_FIRE_PER_TICK; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(random.nextDouble()) * SOUL_FIRE_RADIUS;
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;
            double y = player.getEyeY() + (random.nextDouble() * 2.0D - 1.0D) * SOUL_FIRE_EYE_SPREAD;
            if (!isAir(world, probe, x, y, z)) {
                continue;
            }
            world.addParticle(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    x,
                    y,
                    z,
                    (random.nextDouble() - 0.5D) * 0.02D,
                    0.01D + random.nextDouble() * 0.02D,
                    (random.nextDouble() - 0.5D) * 0.02D
            );
            spawned++;
        }
        spawned = 0;
        for (int attempt = 0; attempt < JOSS_PAPER_ATTEMPTS && spawned < JOSS_PAPER_PER_TICK; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(random.nextDouble()) * JOSS_PAPER_RADIUS;
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;
            double y = player.getY() + JOSS_PAPER_MIN_HEIGHT
                    + random.nextDouble() * (JOSS_PAPER_MAX_HEIGHT - JOSS_PAPER_MIN_HEIGHT);
            if (!isAir(world, probe, x, y, z)) {
                continue;
            }
            // The velocity is the paper's constant horizontal drift; it finds its own fall speed.
            // 该速度即纸钱恒定的水平漂移；下落速度由粒子自身决定。
            world.addParticle(
                    SpecterParticles.jossPaper(),
                    x,
                    y,
                    z,
                    (random.nextDouble() * 2.0D - 1.0D) * JOSS_PAPER_DRIFT,
                    0.0D,
                    (random.nextDouble() * 2.0D - 1.0D) * JOSS_PAPER_DRIFT
            );
            spawned++;
        }
    }

    private static boolean isAir(ClientWorld world, BlockPos.Mutable probe, double x, double y, double z) {
        return world.getBlockState(probe.set(x, y, z)).isAir();
    }
}
