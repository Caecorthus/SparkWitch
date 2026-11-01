package dev.caecorthus.sparkwitch.mixin.ferryman;

import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanFeignService;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.item.BodyBagItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A body bag cannot collect the fake corpse of a player feigning before the Ferryman (owner spec): the bag would carry
 * the marked player's own body away and hand it to the watchers. Vanilla behaviour is untouched for every other body;
 * only our own fake is refused, with FAIL so the bag keeps its charge.
 * 裹尸袋不能收取在摆渡人面前假死者的假尸体（所有者设定）：否则袋子会把被标记者自己的“身体”运走并交到旁观者手里。
 * 其他尸体保持原版行为；只有我们的假尸体被拒绝，返回 FAIL 使袋子不消耗。
 */
@Mixin(BodyBagItem.class)
public abstract class BodyBagItemFerrymanFeignMixin {
    @Inject(method = "useOnEntity", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$refuseFeigningBody(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand,
                                               CallbackInfoReturnable<ActionResult> cir) {
        if (entity instanceof PlayerBodyEntity body && FerrymanFeignService.isFakeBody(body)) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }
}
