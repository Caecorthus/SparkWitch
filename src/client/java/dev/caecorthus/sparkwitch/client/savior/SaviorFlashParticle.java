package dev.caecorthus.sparkwitch.client.savior;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;

/**
 * The world muzzle flash, copied from Wathe's {@code FlashParticle.GunshotFactory}: fullbright, three-tick life,
 * alpha fading 1 → 0, 0.2 scale. Its sprite is {@code sparkwitch:gunshot}, declared by
 * {@code assets/sparkwitch/particles/gunshot.json}.
 * 世界枪口火焰，复制自 Wathe 的 {@code FlashParticle.GunshotFactory}：全亮、三刻寿命、透明度 1 → 0、0.2 大小。
 * 贴图是 {@code sparkwitch:gunshot}，由 {@code assets/sparkwitch/particles/gunshot.json} 声明。
 */
public final class SaviorFlashParticle extends SpriteBillboardParticle {
    private static final int MAX_AGE = 3;
    private static final float FLASH_SCALE = 0.2F;
    /** Packed full-bright light, same as Wathe's gunshot. / 打包的全亮光照，与 Wathe 枪口火焰相同。 */
    private static final int FULL_BRIGHT = 15_728_880;

    private SaviorFlashParticle(ClientWorld world, double x, double y, double z,
                                double velocityX, double velocityY, double velocityZ) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        this.maxAge = MAX_AGE;
        this.scale = FLASH_SCALE;
        this.setVelocity(velocityX, velocityY, velocityZ);
        this.setColor(1.0F, 1.0F, 1.0F);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getBrightness(float tickDelta) {
        return FULL_BRIGHT;
    }

    @Override
    public void tick() {
        super.tick();
        this.alpha = 1.0F - (float) this.age / (float) this.maxAge;
    }

    public static final class GunshotFactory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider sprites;

        public GunshotFactory(SpriteProvider sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            SaviorFlashParticle particle =
                    new SaviorFlashParticle(world, x, y, z, velocityX, velocityY, velocityZ);
            particle.setSprite(this.sprites);
            return particle;
        }
    }
}
