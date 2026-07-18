package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.vendetta.VendettaKnifeClientUse;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaKnifeItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps client networking out of the common item class. / 避免公共物品类直接引用客户端网络 API。 */
@Mixin(VendettaKnifeItem.class)
public abstract class VendettaKnifeItemMixin {
    @Inject(method = "onStoppedUsing", at = @At("TAIL"))
    private void sparkwitch$submitVendettaKnifeTarget(
            ItemStack stack,
            World world,
            LivingEntity user,
            int remainingUseTicks,
            CallbackInfo ci
    ) {
        VendettaKnifeClientUse.onStoppedUsing(
                stack, world, user, remainingUseTicks, stack.getMaxUseTime(user));
    }
}
