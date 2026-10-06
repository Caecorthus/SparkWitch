package dev.caecorthus.sparkwitch.mixin.accessor;

import net.minecraft.server.network.ServerItemCooldownManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes only the owner of a server item-cooldown manager, so a removed cooldown can release that player's own timers.
 * 只暴露服务端物品冷却管理器的所属玩家，以便移除冷却时同时释放该玩家的自有计时。
 */
@Mixin(ServerItemCooldownManager.class)
public interface ServerItemCooldownManagerAccessor {
    @Accessor("player")
    ServerPlayerEntity sparkwitch$getPlayer();
}
