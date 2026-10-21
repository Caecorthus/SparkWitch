package dev.caecorthus.sparkwitch.client.blackraven.crow;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowLandingRules;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;

/**
 * Client particle {@code sparkwitch:black_feather}: one of the two black feather sprites, picked at random, that keeps
 * its launch velocity exactly (no vanilla jitter, no gravity, no drag) and turns its horizontal velocity by
 * {@link BlackRavenCrowLandingRules#SWIRL_TURN} every tick. The server launches it tangentially on the swirl circle, so
 * it circles the landing player, spinning and fading out. Presentation only.
 * 客户端粒子 {@code sparkwitch:black_feather}：随机选取两张黑色羽毛贴图之一，精确保持发射速度（无原版抖动、无重力、无阻力），并每刻把水平速度
 * 转过 {@link BlackRavenCrowLandingRules#SWIRL_TURN}。服务端在环绕圆上切向发射它，因此它会绕落地的玩家旋转，自身打转并逐渐淡出。仅为表现。
 */
public final class BlackFeatherParticle extends SpriteBillboardParticle {
    private static final int BASE_AGE = 18;
    private static final int AGE_SPREAD = 10;
    private static final float BASE_SCALE = 0.1F;
    private static final float SCALE_SPREAD = 0.05F;
    private static final float SPIN = 0.3F;
    private static final double TURN_COS = Math.cos(BlackRavenCrowLandingRules.SWIRL_TURN);
    private static final double TURN_SIN = Math.sin(BlackRavenCrowLandingRules.SWIRL_TURN);

    private final float spin;

    private BlackFeatherParticle(ClientWorld world, double x, double y, double z, double velocityX, double velocityY,
                                 double velocityZ, SpriteProvider sprites) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        // The base constructor jitters the velocity; the swirl needs the exact launch. / 基类构造会抖动速度；环绕需要精确的发射速度。
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        this.gravityStrength = 0.0F;
        this.velocityMultiplier = 1.0F;
        this.collidesWithWorld = false;
        this.maxAge = BASE_AGE + random.nextInt(AGE_SPREAD);
        this.scale = BASE_SCALE + random.nextFloat() * SCALE_SPREAD;
        this.angle = random.nextFloat() * (float) (Math.PI * 2.0);
        this.prevAngle = angle;
        this.spin = (random.nextFloat() - 0.5F) * SPIN;
        setSprite(sprites);
    }

    @Override
    public void tick() {
        prevPosX = x;
        prevPosY = y;
        prevPosZ = z;
        prevAngle = angle;
        if (age++ >= maxAge) {
            markDead();
            return;
        }
        double turnedX = velocityX * TURN_COS - velocityZ * TURN_SIN;
        double turnedZ = velocityZ * TURN_COS + velocityX * TURN_SIN;
        velocityX = turnedX;
        velocityZ = turnedZ;
        move(velocityX, velocityY, velocityZ);
        angle += spin;
        int fadeTicks = maxAge / 3;
        int left = maxAge - age;
        alpha = left >= fadeTicks || fadeTicks <= 0 ? 1.0F : (float) left / fadeTicks;
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Factory registered for {@code sparkwitch:black_feather}. / 为 {@code sparkwitch:black_feather} 注册的工厂。 */
    public static final class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider sprites;

        public Factory(SpriteProvider sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            return new BlackFeatherParticle(world, x, y, z, velocityX, velocityY, velocityZ, sprites);
        }
    }
}
