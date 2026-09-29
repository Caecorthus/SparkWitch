package dev.caecorthus.sparkwitch.mixin.fiend;

import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendMomentService;
import dev.doctor4t.wathe.item.CrowbarItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fiend Moment crowbar: after a successful door pry in Wathe's {@code CrowbarItem#useOnBlock} (vanilla override, so the
 * selector keeps {@code remap = true}), the moment Fiend's crowbar cooldown becomes exactly 5 s. The anchor is the single
 * {@code DoorBlockEntity.blast()} call (a Wathe member, {@code remap = false} on its {@code @At}), which runs only on
 * success and after both of Wathe's cooldown writes, including SparkTraits' Conscience {@code @Redirect} of the second
 * one, so no second redirect is added and the Fiend's write lands last. Server only; every other user returns early.
 * 魔人时刻撬棍：在 Wathe {@code CrowbarItem#useOnBlock}（原版覆写，选择器保持 {@code remap = true}）成功撬开门之后，
 * 将时刻中魔人的撬棍冷却精确设为 5 秒。锚点是唯一一次 {@code DoorBlockEntity.blast()} 调用（Wathe 成员，其 {@code @At}
 * 使用 {@code remap = false}），它只在成功时执行，且位于 Wathe 两次冷却写入之后（包括 SparkTraits 良心对第二次写入的
 * {@code @Redirect}），因此不新增第二个重定向，魔人的写入最后生效。仅服务端；其他使用者一律提前返回。
 */
@Mixin(CrowbarItem.class)
public abstract class CrowbarItemFiendCooldownMixin {
    @Inject(
            method = "useOnBlock(Lnet/minecraft/item/ItemUsageContext;)Lnet/minecraft/util/ActionResult;",
            at = @At(value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/block_entity/DoorBlockEntity;blast()V",
                    shift = At.Shift.AFTER,
                    remap = false))
    private void sparkwitch$fiendMomentCrowbarCooldown(ItemUsageContext context,
                                                       CallbackInfoReturnable<ActionResult> cir) {
        if (!(context.getPlayer() instanceof ServerPlayerEntity player)) {
            return;
        }
        FiendMomentService.applyCrowbarCooldown(player, (Item) (Object) this);
    }
}
