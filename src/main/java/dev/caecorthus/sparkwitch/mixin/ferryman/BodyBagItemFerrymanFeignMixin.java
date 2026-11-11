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
 * its charge. Client/server authority: Wathe's {@code useOnEntity} also runs on the bagger's client, which cannot know
 * the body is fake (that stays server-only) and discards it, spends a charge and sets the cooldown before the server
 * refuses; the server therefore sends its truth back ({@link FerrymanFeignService#resyncRefusedBodyBag}).
 * 裹尸袋的两处摆渡人接缝。其一：不能收取在摆渡人面前假死者的假尸体（所有者设定），否则袋子会把被标记者自己的“身体”
 * 运走并交到旁观者手里。其二：收走已摆渡遗体视为破坏，摆渡人获得第二份报酬，收尸者被挂上「瞥视」。其他尸体保持原版
 * 行为；只有我们的假尸体被拒绝，返回 FAIL 使袋子不消耗。客户端/服务端权威：Wathe 的 {@code useOnEntity} 也会在收尸者
 * 客户端运行，客户端无从得知尸体是假的（该信息只在服务端），会在服务端拒绝前移除尸体、用掉次数并设置冷却；因此服务端把
 * 真实状态发回（{@link FerrymanFeignService#resyncRefusedBodyBag}）。
 */
@Mixin(BodyBagItem.class)
public abstract class BodyBagItemFerrymanFeignMixin {
    @Inject(method = "useOnEntity", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$refuseFeigningBody(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand,
                                               CallbackInfoReturnable<ActionResult> cir) {
        if (entity instanceof PlayerBodyEntity body && FerrymanFeignService.isFakeBody(body)) {
            if (user instanceof ServerPlayerEntity bagger) {
                FerrymanFeignService.resyncRefusedBodyBag(bagger, body, stack);
            }
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
