package dev.caecorthus.sparkwitch.mixin.usec;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRifleItem;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A1: the USEC rifle scopes while its cooldown runs (the 60 s round-start lock, the 2 s bolt). Vanilla's server
 * {@code interactItem} answers PASS before {@code ItemStack#use} while the held item cools down; for
 * {@link UsecRifleItem} only, that one check reads "not cooling down", so {@code use} (which only scopes) runs and
 * the server's {@code isUsingItem} matches the client's. Firing is a separate payload that {@code UsecFireRules}
 * still refuses while the cooldown runs; every other item keeps vanilla's check.
 * A1：USEC 步枪在冷却期间（开局 60 秒锁定、2 秒拉栓）也能开镜。原版服务端 {@code interactItem} 在手持物品冷却时于
 * {@code ItemStack#use} 之前返回 PASS；仅对 {@link UsecRifleItem}，这一处检查视为“未冷却”，于是 {@code use}（只负责开镜）
 * 照常执行，服务端的 {@code isUsingItem} 与客户端一致。开火是独立的数据包，冷却期间仍由 {@code UsecFireRules} 拒绝；
 * 其他物品保持原版检查。
 */
@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionManagerUsecScopeMixin {
    @WrapOperation(
            method = "interactItem(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/ItemCooldownManager;isCoolingDown(Lnet/minecraft/item/Item;)Z"
            )
    )
    private boolean sparkwitch$scopeUsecRifleWhileCooling(ItemCooldownManager manager, Item item,
                                                           Operation<Boolean> original) {
        return !(item instanceof UsecRifleItem) && original.call(manager, item);
    }
}
