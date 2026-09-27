package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerDeviceKind;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Frozen contract base for Seeker devices. Owner UUID and match id are server-only fields, never in the DataTracker,
 * so no client can learn who owns a device.
 * TODO(WP-03): implement self-checks, hit box, render distance and sweep. / 待 WP-03 实现自检、命中箱、渲染距离与清扫。
 * 搜寻者设备的冻结契约基类。拥有者 UUID 与对局 id 仅存在于服务端，从不进入 DataTracker，任何客户端都无法得知设备归属。
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

    /** Round-end sweep of every Seeker device in the world. / 回合结束时清扫该世界中的所有搜寻者设备。 */
    public static void discardAll(ServerWorld world) {
        // TODO(WP-03) / 待 WP-03 实现
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
