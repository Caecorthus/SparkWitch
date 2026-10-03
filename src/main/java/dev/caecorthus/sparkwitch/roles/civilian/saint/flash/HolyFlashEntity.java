package dev.caecorthus.sparkwitch.roles.civilian.saint.flash;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertTargeting;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheParticles;
import dev.doctor4t.wathe.index.WatheSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown Holy Flash. Bursts on its first block or entity impact like Wathe's grenade, but it is a role-owned entity
 * and never a {@code GrenadeEntity}, so grenade-only hooks (Judge, Bomb Maniac, Seeker device breaks, SparkStrength)
 * never fire. Server-authoritative: only the server picks victims and writes {@link HolyFlashComponent}; the client
 * merely predicts the flight. Deals no damage, breaks nothing, never outlives the round and is never saved.
 * 投出的圣光弹。与 Wathe 手雷一样在首次碰到方块或实体时爆开，但它是本职业自有实体而非 {@code GrenadeEntity}，
 * 因此只针对手雷的钩子（法官、炸弹狂、搜寻者设备损坏、SparkStrength）永不触发。由服务端权威决定：只有服务端挑选受害者
 * 并写入 {@link HolyFlashComponent}，客户端只预测飞行轨迹。不造成伤害、不破坏任何东西、永不跨出本局、从不保存。
 */
public final class HolyFlashEntity extends ThrownItemEntity {
    /** Pull a block-face burst back into the air cell so sight rays never start inside the hit block. / 把方块面上的爆点拉回空气格内，避免视线射线从被击中的方块内部出发。 */
    private static final double FACE_OFFSET = 0.1D;
    private static final float BURST_VOLUME = 3.0F;
    private static final float BURST_PITCH = 1.5F;
    private static final int SPARK_COUNT = 40;
    private static final int GLITTER_COUNT = 24;
    private static final double SPARK_SPREAD = 0.4D;
    private static final double SPARK_SPEED = 0.25D;

    public HolyFlashEntity(EntityType<? extends HolyFlashEntity> type, World world) {
        super(type, world);
    }

    public HolyFlashEntity(World world, LivingEntity owner) {
        super(HolyFlashEntities.holyFlash(), owner, world);
    }

    @Override
    public void tick() {
        // A flash still flying when the round stops is dropped at once; the finalize sweep covers unticked ones.
        // 对局停止时仍在飞行的圣光弹立即移除；未被 tick 的由收尾清理处理。
        if (!getWorld().isClient() && !GameWorldComponent.KEY.get(getWorld()).isRunning()) {
            discard();
            return;
        }
        super.tick();
    }

    /**
     * Spectators, dead, creative and active-Wraith players neither stop nor detonate the flash.
     * 旁观、死亡、创造模式与激活冤魂的玩家既不会挡下也不会引爆圣光弹。
     */
    @Override
    protected boolean canHit(Entity entity) {
        return super.canHit(entity)
                && (!(entity instanceof PlayerEntity player) || ControlExpertTargeting.isParticipant(player));
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!(getWorld() instanceof ServerWorld world) || isRemoved()) {
            return;
        }
        if (HolyFlashRules.isActivePhase(world)) {
            burst(world, burstCenter(hitResult));
        }
        discard();
    }

    private Vec3d burstCenter(HitResult hitResult) {
        if (hitResult instanceof BlockHitResult blockHit && hitResult.getType() == HitResult.Type.BLOCK) {
            return blockHit.getPos().add(Vec3d.of(blockHit.getSide().getVector()).multiply(FACE_OFFSET));
        }
        // Entity hit: the projectile's own pre-impact position is always in open air.
        // 命中实体：投射物自身撞击前的位置总在空气中。
        return getPos();
    }

    /**
     * Sound and particles always play (so the Blind can hear it). A flash whose thrower died or left mid-flight still
     * blinds: it is then judged without an actor (see {@link HolyFlashTargeting#canFlash}).
     * 音效与粒子总会播放（盲人也能听到）。投掷者在飞行途中死亡或离线时圣光弹仍会致盲，此时按无发起者规则判定
     * （见 {@link HolyFlashTargeting#canFlash}）。
     */
    private void burst(ServerWorld world, Vec3d center) {
        world.playSound(null, center.x, center.y, center.z, WatheSounds.ITEM_GRENADE_EXPLODE, SoundCategory.PLAYERS,
                BURST_VOLUME, BURST_PITCH);
        world.spawnParticles(WatheParticles.BIG_EXPLOSION, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        world.spawnParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, SPARK_COUNT,
                SPARK_SPREAD, SPARK_SPREAD, SPARK_SPREAD, SPARK_SPEED);
        world.spawnParticles(ParticleTypes.FIREWORK, center.x, center.y, center.z, GLITTER_COUNT,
                SPARK_SPREAD, SPARK_SPREAD, SPARK_SPREAD, SPARK_SPEED);
        ServerPlayerEntity thrower = getOwner() instanceof ServerPlayerEntity owner
                && ControlExpertTargeting.isParticipant(owner) ? owner : null;
        flashPlayers(world, thrower, center);
    }

    private static void flashPlayers(ServerWorld world, @Nullable ServerPlayerEntity thrower, Vec3d center) {
        for (ServerPlayerEntity target : world.getPlayers()) {
            Vec3d eye = target.getEyePos();
            double distance = eye.distanceTo(center);
            if (distance > HolyFlashRules.RADIUS
                    || !HolyFlashTargeting.canFlash(thrower, target)
                    || !HolyFlashTargeting.hasLineOfSight(world, center, eye)) {
                continue;
            }
            boolean facing = HolyFlashRules.isFacing(
                    HolyFlashTargeting.facingCos(target.getRotationVec(1.0F), eye, center));
            int ticks = HolyFlashRules.durationTicks(distance, facing);
            if (ticks > 0) {
                HolyFlashComponent.KEY.get(target).flash(ticks, center, facing);
            }
        }
    }

    /**
     * Round-end sweep, called at Wathe's finalize (the game-stop event carries no world).
     * 回合结束清理，在 Wathe 收尾时调用（游戏停止事件不携带世界）。
     */
    public static void discardAll(ServerWorld world) {
        for (HolyFlashEntity flash : world.getEntitiesByType(HolyFlashEntities.holyFlash(),
                EntityPredicates.VALID_ENTITY)) {
            flash.discard();
        }
    }

    @Override
    protected Item getDefaultItem() {
        return SparkWitchItems.holyFlash();
    }

    @Override
    public boolean shouldSave() {
        return false;
    }
}
