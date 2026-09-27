package dev.caecorthus.sparkwitch.mixin.seeker;

import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Server drop guard: Seeker device items never drop; nothing drops while viewing.
 * TODO(WP-07): stub until the owning work package adds its injectors. / 待 WP-07 添加注入器。
 * 服务端丢弃守卫：搜寻者设备物品永不丢弃；观看期间禁止一切丢弃。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class SeekerItemDropMixin {
}
