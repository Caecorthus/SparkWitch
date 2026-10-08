package dev.caecorthus.sparkwitch.client.mixin.usec;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRifleItem;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A1, client half of {@code mixin/usec/ServerPlayerInteractionManagerUsecScopeMixin}: vanilla's client
 * {@code interactItem} checks the cooldown inside the sequenced-packet lambda {@code method_41929} and answers PASS
 * without calling {@code ItemStack#use} (the packet is still sent). For {@link UsecRifleItem} only, that check reads
 * "not cooling down", so Shift + right-click and scoping work during the round-start lock and the bolt. Presentation
 * and intent only: the server applies the same rule on its own, and firing stays cooldown-gated there.
 * A1，{@code mixin/usec/ServerPlayerInteractionManagerUsecScopeMixin} 的客户端一半：原版客户端 {@code interactItem}
 * 在顺序数据包 lambda {@code method_41929} 中检查冷却，冷却时返回 PASS 且不调用 {@code ItemStack#use}（数据包仍会发出）。
 * 仅对 {@link UsecRifleItem}，这一处检查视为“未冷却”，因此开局锁定与拉栓期间也能 Shift + 右键跳转倍率并开镜。仅为表现与
 * 意图：服务端独立执行同样的规则，开火在服务端仍受冷却限制。
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class UsecRifleUseCooldownMixin {
    @WrapOperation(
            method = "method_41929(Lnet/minecraft/util/Hand;Lnet/minecraft/entity/player/PlayerEntity;Lorg/apache/commons/lang3/mutable/MutableObject;I)Lnet/minecraft/network/packet/Packet;",
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
