package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Frozen (G0) server-side record of one live gate in {@link RiftGateRegistryComponent}: the source of truth for hops,
 * projectile exits and the tablet console, independent of whether the entity is currently loaded. {@code number} is
 * the per-round placement sequence (1, 2, 3, …), stable and never reused after a close (C9); {@code pos} is the
 * entity position (bottom centre); {@code facing} is horizontal; {@code createdTick} is server world time.
 * 冻结（G0）的服务端门记录，存于 {@link RiftGateRegistryComponent}：跳门、投掷物出口与平板控制台的唯一真相，与实体当前是否
 * 加载无关。{@code number} 为本局放置序号（1、2、3……），固定且关闭后不复用（C9）；{@code pos} 为实体位置（底部中心）；
 * {@code facing} 为水平方向；{@code createdTick} 为服务端世界时间。
 */
public record RiftGateRecord(int number, UUID entityId, Vec3d pos, Direction facing, UUID placer, long createdTick) {
}
