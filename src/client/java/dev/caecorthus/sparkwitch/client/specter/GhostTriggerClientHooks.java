package dev.caecorthus.sparkwitch.client.specter;

import dev.caecorthus.sparkwitch.client.witchmaiden.PoisonAppleParticleClientHooks;
import dev.caecorthus.sparkwitch.roles.killer.specter.GhostTriggerEntity;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheParticles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

/**
 * Spawns the placed ghost trigger's poison particles with Wathe's own poison look and visibility: the gate is
 * {@link PoisonAppleParticleClientHooks#canSee} (killers, dead spectators, and roles whose {@code CanSeePoison} listener
 * returns true), and the cadence copies Wathe's poisoned bed/plate client tick ({@code nextBetween(0, 20) >= 17}, about
 * one tick in five). Other players never spawn the particles. Nothing runs outside a round: triggers are discarded
 * when the round stops.
 * 以 Wathe 自己的下毒外观与可见性生成已放置鬼魂触发器的毒药粒子：门槛为 {@link PoisonAppleParticleClientHooks#canSee}
 * （杀手、死亡旁观者，以及 {@code CanSeePoison} 监听返回 true 的职业），频率照搬 Wathe 下毒床/餐盘的客户端 tick
 * （{@code nextBetween(0, 20) >= 17}，约每 5 刻一次）。其他玩家从不生成这些粒子。回合外不运行：回合停止时触发器会被移除。
 */
public final class GhostTriggerClientHooks {
    /** Wathe's poison cadence: spawn when {@code nextBetween(0, 20)} reaches this. / Wathe 下毒粒子频率阈值。 */
    private static final int PARTICLE_MIN_ROLL = 17;
    private static final int PARTICLE_MAX_ROLL = 20;
    /**
     * Search box half-size in blocks. The client only holds triggers the server tracks to it (their 64-chunk tracking
     * range is capped by the server view distance), and Wathe's poison particle always spawns regardless of camera
     * distance, so this box is the only visibility bound; 96 blocks covers any carriage on screen.
     * 搜索盒半边长（方块）。客户端只持有服务端追踪给它的触发器（其 64 区块追踪范围受服务端视距限制），且 Wathe 毒药粒子
     * 不受相机距离限制总会生成，因此该盒是唯一的可见范围；96 格足以覆盖屏幕上的任何车厢。
     */
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
        ClientWorld world = client.world;
        if (viewer == null || world == null
                || !GameWorldComponent.KEY.get(world).isRunning()
                || !PoisonAppleParticleClientHooks.canSee(viewer)) {
            return;
        }
        Box range = viewer.getBoundingBox().expand(SEARCH_RANGE);
        for (GhostTriggerEntity trigger : world.getEntitiesByClass(GhostTriggerEntity.class, range, entity -> true)) {
            if (world.getRandom().nextBetween(0, PARTICLE_MAX_ROLL) < PARTICLE_MIN_ROLL) {
                continue;
            }
            world.addParticle(
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
}
