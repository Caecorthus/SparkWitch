package dev.caecorthus.sparkwitch.mixin.ferryman;

import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanFeignService;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanMercyService;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.item.BodyBagItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The body bag's two Ferryman seams. First: it cannot collect the fake corpse of a player feigning before the
 * Ferryman (owner spec), because the bag would carry the marked player's own body away and hand it to the watchers.
 * Second: collecting a ferried body counts as destroying it, so its Ferryman is paid the second share and the bagger
 * is marked with 瞥视. Every other body keeps vanilla behaviour; only our fake is refused, with FAIL so the bag keeps
 * its charge.
 * 裹尸袋的两处摆渡人接缝。其一：不能收取在摆渡人面前假死者的假尸体（所有者设定），否则袋子会把被标记者自己的“身体”
 * 运走并交到旁观者手里。其二：收走已摆渡遗体视为破坏，摆渡人获得第二份报酬，收尸者被挂上「瞥视」。其他尸体保持原版
 * 行为；只有我们的假尸体被拒绝，返回 FAIL 使袋子不消耗。
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

    /**
     * Runs after the bag resolved the interaction: only an accepted result means the body was actually taken.
     * 袋子完成交互后运行：只有被接受的结果才表示遗体真的被收走了。
     */
    @Inject(method = "useOnEntity", at = @At("RETURN"))
    private void sparkwitch$payForBaggedBody(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand,
                                             CallbackInfoReturnable<ActionResult> cir) {
        ActionResult result = cir.getReturnValue();
        if (result == null || !result.isAccepted()) {
            return;
        }
        if (entity instanceof PlayerBodyEntity body && user instanceof ServerPlayerEntity bagger) {
            FerrymanMercyService.onBodyDestroyed(body, bagger);
        }
    }
}
