package dev.caecorthus.sparkwitch.roles.civilian.controlexpert;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.doctor4t.wathe.cca.GameWorldComponent;
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
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown Shock Device. Server-authoritative: only the server decides which players stop it and resolves the landing
 * stun; the client merely predicts the flight. It is a role-owned entity rather than a Wathe grenade, so grenade-only
 * trait and addon hooks never apply. It is non-lethal, never outlives the round and is never saved with the world.
 * 投出的电击装置。由服务端权威决定：只有服务端判定哪些玩家会挡下它并结算落点电击，客户端只预测飞行轨迹。
 * 它是本职业自有实体而非 Wathe 手雷，因此只针对手雷的词条与附属模组钩子永不生效。它不致命，永不跨出本局，
 * 也从不随世界保存。
 */
public final class ShockDeviceEntity extends ThrownItemEntity {
    private static final float TRAIL_CHANCE = 0.3F;
    private static final int SPARK_COUNT = 40;
    private static final double SPARK_SPREAD_HORIZONTAL = 0.75;
    private static final double SPARK_SPREAD_VERTICAL = 0.5;
    private static final double SPARK_SPEED = 0.1;
    /** The Taser's arc crackle without its crossbow pop. / 电击枪的电弧噼啪声，但不带弩“啪”声。 */
    private static final SoundEvent SHOCK_SOUND = SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE;
    private static final float SHOCK_VOLUME = 1.0F;
    private static final float SHOCK_PITCH = 1.6F;

    public ShockDeviceEntity(EntityType<? extends ShockDeviceEntity> type, World world) {
        super(type, world);
    }

    public ShockDeviceEntity(World world, LivingEntity owner) {
        super(ControlExpertEntities.shockDevice(), owner, world);
    }

    @Override
    public void tick() {
        // A device still flying when the round stops is dropped at once; the finalize sweep covers unticked ones.
        // 对局停止时仍在飞行的装置立即移除；未被 tick 的装置由收尾清理处理。
        if (!getWorld().isClient() && !GameWorldComponent.KEY.get(getWorld()).isRunning()) {
            discard();
            return;
        }
        // Seeker seam (server): a Seeker device in this tick's path breaks and the device lands here as on a collision.
        // 搜寻者接缝（服务端）：本刻路径上的搜寻者设备被打坏，装置按普通碰撞在此处落地。
        if (!getWorld().isClient()
                && SeekerDeviceHits.onShockDeviceSweep(this, getOwner(), getPos(), getPos().add(getVelocity()))) {
            onCollision(BlockHitResult.createMissed(getPos(), Direction.UP, getBlockPos()));
            return;
        }
        super.tick();
        if (getWorld().isClient() && random.nextFloat() < TRAIL_CHANCE) {
            getWorld().addParticle(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected boolean canHit(Entity entity) {
        return super.canHit(entity) && (!(entity instanceof PlayerEntity player) || collidesWith(player));
    }

    /**
     * Players the thrower may not affect (the thrower, Wraiths, spectators, dead or creative players, Last Escape,
     * Vendetta-isolated or faction-vetoed players) neither stop nor detonate the device. The veto is server-only, so
     * the client's flight prediction treats every player as transparent and the server's removal settles the landing.
     * 投掷者无法影响的玩家（投掷者本人、冤魂、旁观、死亡或创造模式玩家、最后逃脱、复仇者隔离或被阵营否决者）
     * 既不会挡下也不会引爆装置。否决判定仅在服务端进行，因此客户端的飞行预测把所有玩家视为透明，
     * 落点以服务端移除实体为准。
     */
    private boolean collidesWith(PlayerEntity player) {
        Entity owner = getOwner();
        return ControlExpertRules.shockDeviceCollides(isThrower(owner, player), () -> ownerCanAffect(owner, player));
    }

    private static boolean isThrower(@Nullable Entity owner, PlayerEntity player) {
        return owner != null && (owner == player || owner.getUuid().equals(player.getUuid()));
    }

    private static boolean ownerCanAffect(@Nullable Entity owner, PlayerEntity player) {
        return owner instanceof ServerPlayerEntity ce && player instanceof ServerPlayerEntity target
                && ControlExpertTargeting.canAffect(ce, target, ControlExpertRules.SHOCK_ACTION);
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!(getWorld() instanceof ServerWorld world) || isRemoved()) {
            return;
        }
        // A thrower who died, left or stopped participating mid-flight shocks nobody.
        // 投掷者在飞行途中死亡、离开或不再参与时，装置不会电击任何人。
        if (GameWorldComponent.KEY.get(world).isRunning()
                && getOwner() instanceof ServerPlayerEntity thrower
                && ControlExpertTargeting.isParticipant(thrower)) {
            shock(world, thrower, hitResult.getPos());
        }
        discard();
    }

    /**
     * Stuns every affectable participant whose bounding box overlaps the landing area. The sparks and sound are
     * identical whether nobody or many were stunned, so a landing reveals nothing about transparent players.
     * 电击碰撞箱与落点范围重叠的每名可被影响的参与者。无论无人还是多人被电击，火花与音效都完全相同，
     * 因此落点不会泄露任何关于透明玩家的信息。
     */
    private static void shock(ServerWorld world, ServerPlayerEntity thrower, Vec3d landing) {
        for (ServerPlayerEntity target : world.getPlayers()) {
            if (ControlExpertRules.inShockArea(landing, target.getBoundingBox())
                    && ControlExpertTargeting.canAffect(thrower, target, ControlExpertRules.SHOCK_ACTION)) {
                ControlExpertStun.apply(thrower, target, ControlExpertRules.SHOCK_TAIL_TICKS);
            }
        }
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, landing.x, landing.y + SPARK_SPREAD_VERTICAL, landing.z,
                SPARK_COUNT, SPARK_SPREAD_HORIZONTAL, SPARK_SPREAD_VERTICAL, SPARK_SPREAD_HORIZONTAL, SPARK_SPEED);
        world.playSound(null, landing.x, landing.y, landing.z, SHOCK_SOUND, SoundCategory.PLAYERS,
                SHOCK_VOLUME, SHOCK_PITCH);
    }

    /**
     * Round-end sweep, called at Wathe's finalize (the game-stop event carries no world): no device carries into the
     * lobby or the next round.
     * 回合结束清理，在 Wathe 收尾时调用（游戏停止事件不携带世界）：任何装置都不会带入大厅或下一局。
     */
    public static void discardAll(ServerWorld world) {
        for (ShockDeviceEntity device
                : world.getEntitiesByType(ControlExpertEntities.shockDevice(), EntityPredicates.VALID_ENTITY)) {
            device.discard();
        }
    }

    @Override
    protected Item getDefaultItem() {
        return SparkWitchItems.shockDevice();
    }

    @Override
    public boolean shouldSave() {
        return false;
    }
}
