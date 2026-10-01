package dev.caecorthus.sparkwitch.client.mixin.fisher;

import dev.caecorthus.sparkwitch.client.fisher.SwordfishClientUse;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.swordfish.SwordfishItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client networking stays outside the common item; vanilla sends release before this target payload.
 * 客户端网络代码不进入公共物品类；原版先发送松手，再发送这里的选靶数据包。 */
@Mixin(SwordfishItem.class)
public abstract class SwordfishItemMixin {
    @Inject(method = "onStoppedUsing", at = @At("TAIL"))
    private void sparkwitch$submitSwordfishTarget(ItemStack stack, World world, LivingEntity user,
                                                 int remainingUseTicks, CallbackInfo ci) {
        SwordfishClientUse.onStoppedUsing(stack, world, user, remainingUseTicks, stack.getMaxUseTime(user));
    }
}
