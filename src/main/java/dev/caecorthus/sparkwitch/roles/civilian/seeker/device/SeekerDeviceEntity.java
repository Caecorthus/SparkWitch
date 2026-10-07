package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerDeviceKind;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerTargeting;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Frozen contract base for Seeker devices. Owner UUID and match id are server-only fields, never in the DataTracker,
 * so no client can learn who owns a device (the owner recognises its own devices through its owner-only component).
 * Devices never take vanilla damage: every break goes through an explicit seam into
 * {@code SeekerDeviceService.breakDevice}. They are ray-targetable ({@link #canHit()}) with a small margin, but
 * projectiles pass through them, players are never blocked or pushed, and nothing is saved. The server discards a
 * device on its own when the round, the owner of record, the match binding or the owner's reference to it is gone.
 * 搜寻者设备的冻结契约基类。拥有者 UUID 与对局 id 仅存在于服务端，从不进入 DataTracker，任何客户端都无法得知设备归属
 * （拥有者通过自己的仅拥有者组件识别自己的设备）。设备从不承受原版伤害：所有损坏都经显式接缝进入
 * {@code SeekerDeviceService.breakDevice}。它们可被射线选中（余量很小），但投射物会直接穿过，不阻挡也不推动玩家，
 * 且从不存盘。当对局、记录中的拥有者、对局绑定或拥有者对它的引用消失时，服务端会自行移除设备。
 */
public abstract class SeekerDeviceEntity extends Entity {
    @Nullable
    private UUID ownerUuid;
    @Nullable
    private String matchId;

    protected SeekerDeviceEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    public abstract SeekerDeviceKind kind();

    /** Aiming margin added to the hit box by ray weapons. / 射线武器瞄准时附加到命中箱的余量。 */
    public abstract double targetingMargin();

    /** Server only. / 仅服务端。 */
    @Nullable
    public UUID ownerUuid() {
        return ownerUuid;
    }

    /** Server only. / 仅服务端。 */
    @Nullable
    public String matchId() {
        return matchId;
    }

    /** Server only; called once at spawn. / 仅服务端；生成时调用一次。 */
    public void setOwner(UUID ownerUuid, @Nullable String matchId) {
        this.ownerUuid = ownerUuid;
        this.matchId = matchId;
    }

    /** Server only: whether this device belongs to {@code player}. / 仅服务端：该设备是否属于该玩家。 */
    public boolean isOwnedBy(@Nullable UUID player) {
        return player != null && player.equals(ownerUuid);
    }

    /** Round-end sweep of every Seeker device in the world. / 回合结束时清扫该世界中的所有搜寻者设备。 */
    public static void discardAll(ServerWorld world) {
        List<? extends SeekerDeviceEntity> devices = world.getEntitiesByType(
                TypeFilter.instanceOf(SeekerDeviceEntity.class), EntityPredicates.VALID_ENTITY);
        for (SeekerDeviceEntity device : devices) {
            device.discard();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!isRemoved() && getWorld() instanceof ServerWorld serverWorld && !passesServerSelfCheck(serverWorld)) {
            discard();
        }
    }

    /**
     * Server self-check: round running, owner online and still the owner of record, same match binding, and the
     * owner's component still references this entity (the deployed car, or any one of the owner's cameras; otherwise
     * it is an orphan). Silent: state bookkeeping for a lost car is WP-02's device-existence step.
     * 服务端自检：对局进行中、拥有者在线且仍为记录中的拥有者、对局绑定一致、拥有者组件仍引用本实体
     * （已部署的小车，或拥有者任意一台摄像头；否则为孤儿）。
     * 静默执行：丢失小车的状态处理属于 WP-02 的设备存在性步骤。
     */
    private boolean passesServerSelfCheck(ServerWorld world) {
        if (ownerUuid == null || !GameWorldComponent.KEY.get(world).isRunning()) {
            return false;
        }
        String currentMatch = SeekerTargeting.currentMatchId(world);
        if (currentMatch == null || !Objects.equals(currentMatch, matchId)) {
            return false;
        }
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerUuid);
        if (owner == null || !SeekerTargeting.isOwnerOfRecord(owner)) {
            return false;
        }
        SeekerStatusComponent status = SeekerStatusComponent.KEY.getNullable(owner);
        return status != null && SeekerDeviceService.isReferenced(status, this);
    }

    // ---- Hit geometry: ray-targetable with a small margin, never a projectile or collision obstacle ----

    @Override
    public boolean canHit() {
        return !isRemoved();
    }

    @Override
    public float getTargetingMargin() {
        return (float) targetingMargin();
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean isCollidable() {
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
    public boolean isImmuneToExplosion(Explosion explosion) {
        return true;
    }

    /** Every break goes through {@code SeekerDeviceService.breakDevice}. / 所有损坏都经显式接缝处理。 */
    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected MoveEffect getMoveEffect() {
        return MoveEffect.NONE;
    }

    @Override
    public boolean canUsePortals(boolean allowVehicles) {
        return false;
    }

    @Override
    public boolean doesRenderOnFire() {
        return false;
    }

    // ---- Persistence and rendering ----

    @Override
    public boolean shouldSave() {
        return false;
    }

    /** Small boxes would stop rendering ~16 blocks away; keep devices and outlines to 64. / 保持 64 格渲染距离。 */
    @Override
    public boolean shouldRender(double distance) {
        return distance < SeekerRules.DEVICE_RENDER_DISTANCE_SQUARED;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }
}
