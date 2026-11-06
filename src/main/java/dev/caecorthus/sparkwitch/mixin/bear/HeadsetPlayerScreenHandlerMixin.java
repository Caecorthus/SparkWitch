package dev.caecorthus.sparkwitch.mixin.bear;

import dev.caecorthus.sparkwitch.headset.HeadsetQuickMove;
import dev.caecorthus.sparkwitch.headset.HeadsetSlot;
import dev.caecorthus.sparkwitch.headset.HeadsetSlots;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Appends the headset slot (E1, design A) to every {@code PlayerScreenHandler} on BOTH sides, after vanilla's 0..45, so
 * it is handler index {@link HeadsetSlots#HANDLER_SLOT_ID} for the click packets, the slot sync and the creative
 * action, and routes a shift-click of an accepted item into it while it is empty (see {@link HeadsetQuickMove}). The
 * server stays authoritative: the client runs the same handler code only to predict. Nothing else in vanilla's
 * {@code quickMove} changes.
 * 在两端把耳机栏位（E1，方案 A）追加到每个 {@code PlayerScreenHandler} 的原版 0..45 之后，使其成为点击数据包、栏位同步与创造模式
 * 操作所用的界面下标 {@link HeadsetSlots#HANDLER_SLOT_ID}；并在其为空时把可接受物品的 Shift 点击送入其中（见
 * {@link HeadsetQuickMove}）。服务端保持权威：客户端运行同一界面代码只为预测。原版 {@code quickMove} 的其他部分不变。
 */
@Mixin(PlayerScreenHandler.class)
public abstract class HeadsetPlayerScreenHandlerMixin extends AbstractRecipeScreenHandler<CraftingRecipeInput, CraftingRecipe> {
    protected HeadsetPlayerScreenHandlerMixin(ScreenHandlerType<?> type, int syncId) {
        super(type, syncId);
    }

    @Inject(method = "<init>(Lnet/minecraft/entity/player/PlayerInventory;ZLnet/minecraft/entity/player/PlayerEntity;)V",
            at = @At("TAIL"))
    private void sparkwitch$appendHeadsetSlot(PlayerInventory inventory, boolean onServer, PlayerEntity owner,
                                              CallbackInfo ci) {
        addSlot(new HeadsetSlot(owner));
    }

    /**
     * Vanilla's own post-processing for a moved stack (empty the source, notify it, return the original copy); when
     * the headset slot cannot take it, vanilla's routing runs unchanged.
     * 移动物品后的处理与原版相同（清空来源、通知来源、返回原物品副本）；耳机栏位放不下时原版路由照常运行。
     */
    @Inject(method = "quickMove(Lnet/minecraft/entity/player/PlayerEntity;I)Lnet/minecraft/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private void sparkwitch$quickMoveIntoHeadset(PlayerEntity player, int slotIndex,
                                                 CallbackInfoReturnable<ItemStack> cir) {
        int headsetIndex = HeadsetSlots.HANDLER_SLOT_ID;
        if (slotIndex < 0 || slotIndex >= slots.size() || headsetIndex >= slots.size()
                || !(slots.get(headsetIndex) instanceof HeadsetSlot headset)) {
            return;
        }
        Slot source = slots.get(slotIndex);
        ItemStack stack = source.getStack();
        if (!HeadsetQuickMove.routesIntoHeadset(slotIndex, HeadsetSlots.accepts(stack), !headset.hasStack())) {
            return;
        }
        ItemStack original = stack.copy();
        if (!insertItem(stack, headsetIndex, headsetIndex + 1, false)) {
            return;
        }
        if (stack.isEmpty()) {
            source.setStack(ItemStack.EMPTY, original);
        } else {
            source.markDirty();
        }
        source.onTakeItem(player, stack);
        cir.setReturnValue(original);
    }
}
