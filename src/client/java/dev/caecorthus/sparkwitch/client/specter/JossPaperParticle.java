package dev.caecorthus.sparkwitch.client.specter;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;

/**
 * Client particle {@code sparkwitch:joss_paper}: a paper coin that tumbles slowly to the ground. The launch velocity is
 * kept exactly as the horizontal drift (no vanilla jitter); the particle eases to its own terminal fall speed, sways on
 * a per-particle sine, rolls slowly and flips through the three sprites (face-on, tilted, edge-on, tilted, ...). It
 * fades in, fades out at the end of its life, and fades quickly once it lands. Lit by the world but never below block
 * light {@link #MIN_BLOCK_LIGHT}, so it stays readable in dark corners. Presentation only.
 * 客户端粒子 {@code sparkwitch:joss_paper}：缓慢翻滚飘落的纸钱。发射速度被精确保留为水平漂移（无原版抖动）；粒子逐渐达到自己的终端下落
 * 速度，按各自相位的正弦左右摇摆，缓慢自转，并在三张贴图间翻面（正面、倾斜、侧面、倾斜……）。出生时淡入，寿命末尾淡出，落地后快速淡出。
 * 受世界光照影响，但方块光不低于 {@link #MIN_BLOCK_LIGHT}，因此在暗角仍清晰可见。仅为表现。
 */
public final class JossPaperParticle extends SpriteBillboardParticle {
    private static final int BASE_AGE = 80;
    private static final int AGE_SPREAD = 61;
    private static final float BASE_SCALE = 0.08F;
    private static final float SCALE_SPREAD = 0.04F;
    /** Terminal fall speed range in blocks per tick. / 终端下落速度范围（格/刻）。 */
    private static final double BASE_FALL_SPEED = 0.02D;
    private static final double FALL_SPEED_SPREAD = 0.02D;
    /** Share of the gap to the terminal speed closed each tick. / 每刻向终端速度靠拢的比例。 */
    private static final double FALL_EASE = 0.1D;
    /** Peak sway speed range in blocks per tick. / 摇摆速度峰值范围（格/刻）。 */
    private static final double BASE_SWAY_SPEED = 0.012D;
    private static final double SWAY_SPEED_SPREAD = 0.014D;
    private static final int BASE_SWAY_PERIOD = 40;
    private static final int SWAY_PERIOD_SPREAD = 31;
    /** Roll speed bounds in radians per tick, either direction. / 自转速度范围（弧度/刻），方向随机。 */
    private static final float BASE_ROLL = 0.015F;
    private static final float ROLL_SPREAD = 0.035F;
    /** Ticks each tumble frame is held. / 每个翻面帧保持的刻数。 */
    private static final int BASE_FLIP_TICKS = 6;
    private static final int FLIP_TICKS_SPREAD = 5;
    /** Sprite indices of one full tumble. / 一次完整翻面的贴图序列。 */
    private static final int[] FLIP_FRAMES = {0, 1, 2, 1};
    private static final int LAST_SPRITE = 2;
    private static final int FADE_IN_TICKS = 5;
    private static final int FADE_OUT_TICKS = 20;
    private static final int LANDED_FADE_TICKS = 12;
    private static final float MAX_ALPHA = 0.9F;
    private static final int MIN_BLOCK_LIGHT = 10;

    private final SpriteProvider sprites;
    private final double driftX;
    private final double driftZ;
    private final double fallSpeed;
    private final double swayX;
    private final double swayZ;
    private final double swayStep;
    private final double swayPhase;
    private final float roll;
    private final int flipTicks;
    private final int flipOffset;
    private int landedAge = -1;

    private JossPaperParticle(ClientWorld world, double x, double y, double z, double velocityX, double velocityY,
                              double velocityZ, SpriteProvider sprites) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        this.sprites = sprites;
        // The base constructor jitters the velocity; the drift is the exact launch. / 基类构造会抖动速度；漂移取精确的发射速度。
        this.driftX = velocityX;
        this.driftZ = velocityZ;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        this.gravityStrength = 0.0F;
        this.velocityMultiplier = 1.0F;
        this.maxAge = BASE_AGE + random.nextInt(AGE_SPREAD);
        this.scale = BASE_SCALE + random.nextFloat() * SCALE_SPREAD;
        this.fallSpeed = BASE_FALL_SPEED + random.nextDouble() * FALL_SPEED_SPREAD;
        double swayDirection = random.nextDouble() * Math.PI * 2.0D;
        double swaySpeed = BASE_SWAY_SPEED + random.nextDouble() * SWAY_SPEED_SPREAD;
        this.swayX = Math.cos(swayDirection) * swaySpeed;
        this.swayZ = Math.sin(swayDirection) * swaySpeed;
        this.swayStep = Math.PI * 2.0D / (BASE_SWAY_PERIOD + random.nextInt(SWAY_PERIOD_SPREAD));
        this.swayPhase = random.nextDouble() * Math.PI * 2.0D;
        this.roll = (BASE_ROLL + random.nextFloat() * ROLL_SPREAD) * (random.nextBoolean() ? 1.0F : -1.0F);
        this.flipTicks = BASE_FLIP_TICKS + random.nextInt(FLIP_TICKS_SPREAD);
        this.flipOffset = random.nextInt(FLIP_FRAMES.length * flipTicks);
        this.angle = random.nextFloat() * (float) (Math.PI * 2.0);
        this.prevAngle = angle;
        this.alpha = 0.0F;
        applyFlipSprite();
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
        if (landedAge < 0) {
            double sway = Math.cos(swayPhase + age * swayStep);
            velocityX = driftX + swayX * sway;
            velocityZ = driftZ + swayZ * sway;
            velocityY += (-fallSpeed - velocityY) * FALL_EASE;
            move(velocityX, velocityY, velocityZ);
            angle += roll;
            applyFlipSprite();
            if (onGround) {
                landedAge = age;
            }
        }
        float visible = Math.min(1.0F, (float) age / FADE_IN_TICKS);
        visible = Math.min(visible, (float) (maxAge - age) / FADE_OUT_TICKS);
        if (landedAge >= 0) {
            visible = Math.min(visible, 1.0F - (float) (age - landedAge) / LANDED_FADE_TICKS);
        }
        if (visible <= 0.0F) {
            markDead();
            return;
        }
        alpha = visible * MAX_ALPHA;
    }

    private void applyFlipSprite() {
        int frame = FLIP_FRAMES[(age + flipOffset) / flipTicks % FLIP_FRAMES.length];
        setSprite(sprites.getSprite(frame, LAST_SPRITE));
    }

    @Override
    protected int getBrightness(float tint) {
        // Packed light: block light in the low word, sky light in the high word, each level << 4.
        // 打包光照：低位字为方块光，高位字为天空光，各自为等级 << 4。
        int light = super.getBrightness(tint);
        int block = Math.max(light & 0xFFFF, MIN_BLOCK_LIGHT << 4);
        return light & 0xFFFF0000 | block;
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Factory registered for {@code sparkwitch:joss_paper}. / 为 {@code sparkwitch:joss_paper} 注册的工厂。 */
    public static final class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider sprites;

        public Factory(SpriteProvider sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            return new JossPaperParticle(world, x, y, z, velocityX, velocityY, velocityZ, sprites);
        }
    }
}
