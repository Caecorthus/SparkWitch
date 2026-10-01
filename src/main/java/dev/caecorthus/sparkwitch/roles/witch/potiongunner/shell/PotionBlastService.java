package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell;

import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeKillAttribution;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerBreakSource;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheParticles;
import dev.doctor4t.wathe.index.WatheSounds;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Detonates a shell: grenade-style sound and particles, Seeker device breaks, target resolution, the shell effect
 * (inside Judge attribution), and the per-hit gold reward.
 * 引爆炮弹：手雷式音效与粒子、搜寻者设备破坏、目标判定、炮弹效果（在审判官归因内执行）以及每人奖励金币。
 */
public final class PotionBlastService {
    /** Wathe grenade presentation values ({@code GrenadeEntity#onCollision}). / Wathe 手雷的表现参数。 */
    private static final float EXPLODE_VOLUME = 5.0F;
    private static final float EXPLODE_PITCH_SPREAD = 0.1F;
    private static final int DEBRIS_COUNT = 100;
    private static final double PARTICLE_LIFT = 0.1;
    private static final double SMOKE_SPEED = 0.2;
    private static final double DEBRIS_SPEED = 1.0;
    public static final String HIT_REWARD_KEY = "message.sparkwitch.potion_gunner.hit_reward";

    private static boolean registered;

    private PotionBlastService() {
    }

    /** Nothing to hook: blasts are driven by the shell entity itself. / 无需注册：爆炸由炮弹实体自身驱动。 */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
    }

    /**
     * Server only and only while the round runs; the shell entity calls it once and discards itself afterwards.
     * The order is fixed: presentation, Seeker devices (never the gunner's own, {@code mayBreak}-gated while the
     * gunner is online), targets, the effect, then the reward checked against the gunner's state after the effect
     * (a gunner whose own TR killed them is paid nothing).
     * 仅服务端、仅在对局进行中；由炮弹实体调用一次，之后自行移除。顺序固定：表现、搜寻者设备（从不打坏药炮手自己的设备，
     * 药炮手在线时经 {@code mayBreak} 校验）、目标、效果，最后按效果之后的药炮手状态结算奖励（被自己 TR 炸死的药炮手不得钱）。
     */
    public static void detonate(PotionShellEntity shell, Vec3d center) {
        if (shell == null || center == null || !(shell.getWorld() instanceof ServerWorld world)
                || !GameWorldComponent.KEY.get(world).isRunning()) {
            return;
        }
        PotionShellType type = shell.shellTypeOrNull();
        if (type == null) {
            return;
        }
        UUID gunnerUuid = shell.gunnerUuid();
        ServerPlayerEntity gunner = shell.getOwner() instanceof ServerPlayerEntity player ? player : null;

        present(world, shell, center);
        SeekerDeviceHits.onBlast(world, center, PotionBlastRings.half(type.size()), gunner,
                SeekerBreakSource.POTION_SHELL);

        List<PotionBlastHit> hits = PotionBlastResolver.resolve(world, center, type, gunnerUuid, gunner);
        PotionBlastContext context = new PotionBlastContext(world, center, type, gunnerUuid, gunner, hits);
        // Same cause bridge as the Wathe grenade, so a Judge sentence on the gunner covers TR kills.
        // 与 Wathe 手雷相同的归因桥：审判官对药炮手的判决同样覆盖 TR 击杀。
        if (gunnerUuid != null) {
            JudgeKillAttribution.runWith(world, gunnerUuid, () -> PotionShellEffects.apply(context));
        } else {
            PotionShellEffects.apply(context);
        }
        payReward(world, gunner, PotionBlastRewards.rewardedHits(hits));
    }

    private static void present(ServerWorld world, PotionShellEntity shell, Vec3d center) {
        double y = center.y + PARTICLE_LIFT;
        world.playSound(null, center.x, center.y, center.z, WatheSounds.ITEM_GRENADE_EXPLODE, SoundCategory.PLAYERS,
                EXPLODE_VOLUME, 1.0F + shell.getRandom().nextFloat() * EXPLODE_PITCH_SPREAD - EXPLODE_PITCH_SPREAD / 2.0F);
        // The flash is forced to every player (long range), so a distant gunner sees the impact; smoke and debris
        // keep vanilla's normal range like the grenade. / 闪光强制发送给所有玩家（远距离），远处的药炮手也能看到落点。
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            world.spawnParticles(viewer, WatheParticles.BIG_EXPLOSION, true, center.x, y, center.z, 1, 0.0, 0.0, 0.0,
                    0.0);
        }
        world.spawnParticles(ParticleTypes.SMOKE, center.x, y, center.z, DEBRIS_COUNT, 0.0, 0.0, 0.0, SMOKE_SPEED);
        world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, shell.getStack().copyWithCount(1)),
                center.x, y, center.z, DEBRIS_COUNT, 0.0, 0.0, 0.0, DEBRIS_SPEED);
    }

    private static void payReward(ServerWorld world, @Nullable ServerPlayerEntity gunner, int rewardedHits) {
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        boolean online = gunner != null && !gunner.isRemoved();
        int amount = PotionBlastRewards.amount(
                online,
                online && GameFunctions.isPlayerPlayingAndAlive(gunner) && !gunner.isSpectator(),
                online && PotionGunnerRules.isPotionGunner(game.getRole(gunner)),
                rewardedHits);
        if (amount <= 0) {
            return;
        }
        // addToBalance syncs the owner's shop component. / addToBalance 会同步拥有者的商店组件。
        PlayerShopComponent.KEY.get(gunner).addToBalance(amount);
        gunner.sendMessage(Text.translatable(HIT_REWARD_KEY, rewardedHits, amount)
                .withColor(PotionGunnerRules.COLOR), true);
    }
}
