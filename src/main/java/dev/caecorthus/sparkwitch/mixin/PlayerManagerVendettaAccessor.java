package dev.caecorthus.sparkwitch.mixin;

import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Persists a disconnected entity after deferred Wathe escape or round reset mutates its saved state.
 * 延迟逃脱或回合重置修改离线实体后，将结果写回该玩家的存档。
 */
@Mixin(PlayerManager.class)
public interface PlayerManagerVendettaAccessor {
    @Invoker("savePlayerData")
    void sparkwitch$savePlayerData(ServerPlayerEntity player);
}
