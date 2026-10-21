package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The dropped Homing Feather marker ({@code sparkwitch:homing_feather}, owner decision Q7): never saved, never
 * summonable, visible to every client, drifts down like a feather and rests on blocks, and cannot be picked up (it is
 * no item entity), hit, targeted, pushed, burnt, blown up or moved by pistons or fluids. Physics run on both sides
 * ({@link BlackRavenCrowFeatherRules}), the tracker corrects the client. The owner is server-only state; the server
 * self-check ({@link BlackRavenCrowFeatherService#tickMarker}) removes a marker its session no longer names and reports
 * its rest position into the owner's component.
 * 已丢出的归羽标记（{@code sparkwitch:homing_feather}，所有者决定 Q7）：从不存盘、不可召唤、对所有客户端可见，像羽毛一样飘落并停在方块上，
 * 不能被拾取（它不是物品实体）、命中、选中、推动、燃烧、炸毁，也不受活塞或流体推动。物理在两端运行，追踪器纠正客户端。主人仅为服务端状态；
 * 服务端自检会移除会话不再指向的标记，并把静止位置写入主人的组件。
 */
public class BlackRavenFeatherEntity extends Entity {
    private @Nullable UUID owner;
    private Vec3d reportedPos = Vec3d.ZERO;

    public BlackRavenFeatherEntity(EntityType<? extends BlackRavenFeatherEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
    }

    /** Server only. / 仅服务端。 */
    public @Nullable UUID ownerUuid() {
        return owner;
    }

    void setOwner(UUID owner) {
        this.owner = owner;
    }

    Vec3d reportedPos() {
        return reportedPos;
    }

    void setReportedPos(Vec3d pos) {
        reportedPos = pos;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3d velocity = BlackRavenCrowFeatherRules.preMoveVelocity(getVelocity(), hasNoGravity());
        if (BlackRavenCrowFeatherRules.movesThisTick(isOnGround(), velocity.lengthSquared(), age)) {
            setVelocity(velocity);
            move(MovementType.SELF, velocity);
            setVelocity(BlackRavenCrowFeatherRules.postMoveVelocity(getVelocity(), isOnGround()));
        }
        if (!getWorld().isClient() && !isRemoved()) {
            BlackRavenCrowFeatherService.tickMarker(this);
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean handleAttack(Entity attacker) {
        return true;
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluids() {
        return false;
    }

    @Override
    public PistonBehavior getPistonBehavior() {
        return PistonBehavior.IGNORE;
    }

    @Override
    public boolean isImmuneToExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public boolean canUsePortals(boolean allowVehicles) {
        return false;
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < BlackRavenCrowFeatherRules.RENDER_DISTANCE_SQUARED;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }
}
