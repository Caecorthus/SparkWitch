package dev.caecorthus.sparkwitch.mixin.seeker;

import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Gated tickMovement HEAD sprint clear while the Seeker lock is active (common, remap = true).
 * TODO(WP-09): stub until the owning work package adds its injectors. / 待 WP-09 添加注入器。
 * 搜寻者锁定期间在 tickMovement HEAD 清除疾跑（通用配置，remap = true）。
 */
@Mixin(PlayerEntity.class)
public abstract class SeekerSprintLockMixin {
}
