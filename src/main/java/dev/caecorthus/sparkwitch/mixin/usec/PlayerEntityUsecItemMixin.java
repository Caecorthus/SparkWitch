package dev.caecorthus.sparkwitch.mixin.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecDeathDrops;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecLoadoutService;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two hooks on the player drop method, which {@code ServerPlayerEntity} reaches through {@code super}, so every server
 * drop path is covered.
 * <ul>
 *   <li>HEAD: USEC bound items never become item entities (cursor on close with a full inventory, disconnect, creative
 *   throws, offer fallbacks); the decision lives in {@code UsecLoadoutService#interceptDrop}.</li>
 *   <li>RETURN: a revolver item entity created for a USEC is recorded for the same-tick death-revolver dedup in
 *   {@code UsecDeathDrops#noteDrop}. A drop another mod cancels at HEAD never reaches RETURN.</li>
 * </ul>
 * 玩家丢弃方法上的两个钩子；{@code ServerPlayerEntity} 通过 {@code super} 调用此方法，因此覆盖所有服务端丢弃路径。
 * HEAD：USEC 绑定物品永不成为物品实体（背包已满时关闭界面的光标、断线、创造模式丢弃、放入失败的回退），具体处理位于
 * {@code UsecLoadoutService#interceptDrop}。RETURN：为 USEC 生成的左轮物品实体会被记录，供 {@code UsecDeathDrops#noteDrop}
 * 做同刻死亡左轮去重。被其他模组在 HEAD 取消的丢弃不会到达 RETURN。
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityUsecItemMixin {
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"),
            cancellable = true)
    private void sparkwitch$keepUsecItemBound(
            ItemStack stack,
            boolean throwRandomly,
            boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> cir
    ) {
        if (UsecLoadoutService.interceptDrop((PlayerEntity) (Object) this, stack)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("RETURN"))
    private void sparkwitch$noteUsecRevolverDrop(
            ItemStack stack,
            boolean throwRandomly,
            boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> cir
    ) {
        UsecDeathDrops.noteDrop((PlayerEntity) (Object) this, cir.getReturnValue());
    }
}
