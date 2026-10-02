package dev.caecorthus.sparkwitch.client.riftwalker.gate;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateEntity;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Client-side gate presentation (plan §5.3, D9): ambient particles from {@link #tick} (PORTAL drawn inward, role-colour
 * dust along the frame, rare WITCH sparks; kept sparse), the placed-model registration, and the witch-faction instinct
 * outline through walls via Wathe's {@code GetInstinctHighlight} event. {@link #tick} is installed into
 * {@code RiftGateClientBridge} by {@code RiftwalkerClient}. Decoration only: particles never carry gameplay, follow the
 * client's particle setting, stop past {@link RiftGatePresentationRules#PARTICLE_RANGE}, and an occupant's own gate stays
 * quiet so nothing flies into their lens. Owned by P6.
 * 客户端门表现（plan §5.3、D9）：{@link #tick} 中的环境粒子（PORTAL 向门心吸入、职业色尘沿门框飘、偶尔 WITCH 火花，数量节制）、
 * 放置模型注册，以及通过 Wathe {@code GetInstinctHighlight} 事件实现的魔女阵营本能隔墙描边。{@link #tick} 由
 * {@code RiftwalkerClient} 安装到 {@code RiftGateClientBridge}。纯装饰：粒子不承载玩法，遵循客户端粒子设置，超出范围不生成，
 * 门内者所在的门保持安静，不会有粒子冲向镜头。归属 P6。
 */
public final class RiftGateClientEffects {
    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final DustParticleEffect DUST = new DustParticleEffect(RiftGatePresentationRules.dustColor(),
            RiftGatePresentationRules.DUST_SCALE);
    private static boolean initialized;

    private RiftGateClientEffects() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        RiftGateModels.register();
        RiftGateInstinctHooks.register();
    }

    /** Client tick of one gate entity: sparse ambient particles. / 单个门实体的客户端 tick：少量环境粒子。 */
    public static void tick(RiftGateEntity gate) {
        MinecraftClient client = MinecraftClient.getInstance();
        World world = gate.getWorld();
        if (client.world != world || client.gameRenderer == null) {
            return;
        }
        double chanceScale = RiftGatePresentationRules.particleChanceScale(client.options.getParticles().getValue());
        if (chanceScale <= 0.0) {
            return;
        }
        Camera camera = client.gameRenderer.getCamera();
        if (camera == null || !camera.isReady()) {
            return;
        }
        double distanceSquared = camera.getPos().squaredDistanceTo(gate.getX(),
                gate.getY() + RiftGatePresentationRules.SWIRL_CENTER_UP, gate.getZ());
        if (!RiftGatePresentationRules.inParticleRange(distanceSquared)
                || RiftGatePresentationRules.quietForOccupant(RiftSessionService.isInside(client.player),
                distanceSquared)) {
            return;
        }
        Random random = gate.getRandom();
        Direction facing = gate.facing();
        if (random.nextDouble() < RiftGatePresentationRules.PORTAL_CHANCE * chanceScale) {
            spawnPortal(world, gate, facing, random);
        }
        if (random.nextDouble() < RiftGatePresentationRules.DUST_CHANCE * chanceScale) {
            spawnRing(world, gate, facing, random, DUST, 0.0);
        }
        if (random.nextDouble() < RiftGatePresentationRules.WITCH_CHANCE * chanceScale) {
            spawnRing(world, gate, facing, random, ParticleTypes.WITCH, 0.02);
        }
    }

    /**
     * Vanilla PORTAL starts at {@code pos + velocity + (0, 1, 0)} and ends at {@code pos}: aim pos at a swirl point
     * and set the velocity so it starts just outside the ring, in front of or behind the gate.
     * 原版 PORTAL 从 {@code pos + velocity + (0, 1, 0)} 出发、终点为 {@code pos}：pos 设为旋涡内一点，速度设为使其从门环外侧
     * （门前或门后）出发。
     */
    private static void spawnPortal(World world, RiftGateEntity gate, Direction facing, Random random) {
        double[] start = RiftGatePresentationRules.ringPoint(random.nextFloat() * TWO_PI);
        double depth = (random.nextBoolean() ? 1.0 : -1.0) * (0.25 + random.nextDouble() * 0.35);
        Vec3d from = RiftGatePresentationRules.toWorld(gate.getX(), gate.getY(), gate.getZ(), facing,
                start[0] * 1.1, start[1], depth);
        double[] end = RiftGatePresentationRules.swirlTarget(random.nextDouble() * 2.0 - 1.0,
                random.nextDouble() * 2.0 - 1.0);
        Vec3d to = RiftGatePresentationRules.toWorld(gate.getX(), gate.getY(), gate.getZ(), facing,
                end[0], end[1], 0.0);
        world.addParticle(ParticleTypes.PORTAL, to.x, to.y, to.z,
                from.x - to.x, from.y - to.y - 1.0, from.z - to.z);
    }

    /** One particle on the rune-stone ring, within the stones' depth. / 在符文石环上、石头厚度内生成一颗粒子。 */
    private static void spawnRing(World world, RiftGateEntity gate, Direction facing, Random random,
                                  ParticleEffect effect, double upwardSpeed) {
        double[] point = RiftGatePresentationRules.ringPoint(random.nextFloat() * TWO_PI);
        double depth = (random.nextDouble() * 2.0 - 1.0) * RiftGatePresentationRules.RING_HALF_DEPTH * 2.0;
        Vec3d at = RiftGatePresentationRules.toWorld(gate.getX(), gate.getY(), gate.getZ(), facing,
                point[0], point[1], depth);
        world.addParticle(effect, at.x, at.y, at.z, 0.0, upwardSpeed, 0.0);
    }
}
